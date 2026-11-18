// Atala coach: checks the caller, applies the daily cap, asks the model, returns
// { reply, proposal? }. The plan itself is only changed in the app, after the runner taps Apply.
import { createClient } from "npm:@supabase/supabase-js@2";

// The model is reached through fal's OpenAI-compatible router with the FAL_KEY secret.
// Gemini Flash-Lite by default (the cheapest Gemini); set the COACH_MODEL secret to use another model without redeploying.
const MODEL = (Deno.env.get("COACH_MODEL") ?? "").trim() || "google/gemini-2.5-flash-lite";
const MODEL_URL = "https://fal.run/openrouter/router/openai/v1/chat/completions";

type ToolCall = { id: string; type: "function"; function: { name: string; arguments: string } };
type ChatTurn = {
  role: "system" | "user" | "assistant" | "tool";
  content: string | null;
  tool_calls?: ToolCall[];
  tool_call_id?: string;
};

/** An error from the model endpoint, with its HTTP status (0 when the endpoint could not be reached). */
class ModelError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
const DAILY_CAP = 150;
const MAX_HISTORY = 30;
const MAX_MESSAGE_CHARS = 4000;
const MAX_TOOL_ROUNDS = 3;
const MAX_BODY_CHARS = 120_000;
const MAX_WORKOUTS = 80;
const MAX_RECENT_RUNS = 12;
/** Whole budget for the model, all rounds included. The app waits 45 s. */
const DEADLINE_MS = 40_000;
const REQUEST_TIMEOUT_MS = 30_000;

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

/** Every failure is `{ "error": "<code>" }` (plus `message` when it helps), never a bare status. */
function fail(code: string, status: number, message?: string): Response {
  return json(message ? { error: code, message } : { error: code }, status);
}

// ---------- Input ----------

interface ContextWorkout {
  id: string;
  date: string;
  kind: string;
  title: string;
  distanceKm?: number;
  durationMin?: number;
  status?: string;
  weekday?: string;
}

interface CoachContext {
  firstName?: string;
  coachName?: string;
  tone?: string;
  goal?: string;
  raceDate?: string | null;
  targetSeconds?: number | null;
  paces?: Record<string, unknown>;
  today?: string;
  weekday?: string;
  workouts?: ContextWorkout[];
  recentRuns?: unknown[];
}

interface ProposalChange {
  workoutId: string;
  newDate?: string;
  newKind?: string;
  newTitle?: string;
  newDistanceKm?: number;
  newDurationMin?: number;
  remove?: boolean;
}

interface Proposal {
  summary: string;
  changes: ProposalChange[];
}

const KINDS = ["easy", "intervals", "tempo", "long", "racePace", "strength", "stretching"];

const TONES: Record<string, string> = {
  kind: "Kind: gentle, reassuring and encouraging.",
  direct: "Direct: straight to the point, few words, no sugar-coating, still respectful.",
  playful: "Playful: light and a little cheeky, never at the runner's expense.",
};

function isIsoDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function shortText(value: unknown, max: number): string | undefined {
  if (typeof value !== "string") return undefined;
  const text = value.trim();
  return text ? text.slice(0, max) : undefined;
}

function finiteNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

/** Keeps only the fields the coach uses, with bounded sizes: the context comes from the phone. */
function cleanContext(raw: unknown): CoachContext {
  const input = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const workouts: ContextWorkout[] = [];
  if (Array.isArray(input.workouts)) {
    for (const item of input.workouts.slice(0, MAX_WORKOUTS)) {
      const id = shortText(item?.id, 64);
      const date = item?.date;
      const kind = shortText(item?.kind, 24);
      if (!id || !isIsoDate(date) || !kind) continue;
      workouts.push({
        id,
        date,
        kind,
        title: shortText(item?.title, 80) ?? kind,
        distanceKm: finiteNumber(item?.distanceKm),
        durationMin: finiteNumber(item?.durationMin),
        status: shortText(item?.status, 16),
        weekday: shortText(item?.weekday, 12),
      });
    }
  }
  const recentRuns: unknown[] = [];
  if (Array.isArray(input.recentRuns)) {
    for (const item of input.recentRuns.slice(0, MAX_RECENT_RUNS)) {
      if (!item || typeof item !== "object") continue;
      const run: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(item as Record<string, unknown>).slice(0, 12)) {
        if (typeof value === "string") run[key.slice(0, 24)] = value.slice(0, 80);
        else if (typeof value === "number" && Number.isFinite(value)) run[key.slice(0, 24)] = value;
      }
      recentRuns.push(run);
    }
  }
  const paces: Record<string, unknown> = {};
  if (input.paces && typeof input.paces === "object") {
    for (const [key, value] of Object.entries(input.paces as Record<string, unknown>).slice(0, 8)) {
      if (typeof value === "string") paces[key.slice(0, 16)] = value.slice(0, 16);
    }
  }
  return {
    firstName: shortText(input.firstName, 40),
    coachName: shortText(input.coachName, 40),
    tone: shortText(input.tone, 16),
    goal: shortText(input.goal, 60),
    raceDate: isIsoDate(input.raceDate) ? input.raceDate : null,
    targetSeconds: finiteNumber(input.targetSeconds) ?? null,
    paces,
    today: isIsoDate(input.today) ? input.today : new Date().toISOString().slice(0, 10),
    weekday: shortText(input.weekday, 12),
    workouts,
    recentRuns,
  };
}

function buildMessages(raw: unknown): ChatTurn[] | string {
  if (!Array.isArray(raw) || raw.length === 0) return "messages must be a non-empty array";
  const recent = raw.slice(-MAX_HISTORY);
  const out: ChatTurn[] = [];
  for (const item of recent) {
    const role = item?.role === "user" ? "user" : item?.role === "coach" || item?.role === "assistant" ? "assistant" : null;
    const text = typeof item?.text === "string" ? item.text.trim() : "";
    if (!role || !text) continue;
    if (text.length > MAX_MESSAGE_CHARS) return `a message is longer than ${MAX_MESSAGE_CHARS} characters`;
    if (out.length === 0 && role !== "user") continue; // the conversation must start with the runner
    const last = out[out.length - 1];
    if (last && last.role === role && typeof last.content === "string") {
      last.content = `${last.content}\n\n${text}`;
    } else {
      out.push({ role, content: text });
    }
  }
  if (out.length === 0 || out[out.length - 1].role !== "user") return "the last message must come from the user";
  return out;
}

// ---------- Prompt and tool ----------

function systemPrompt(context: CoachContext): string {
  const coachName = (context.coachName ?? "").trim() || "Coach";
  const firstName = (context.firstName ?? "").trim() || "the runner";
  const tone = TONES[context.tone ?? "kind"] ?? TONES.kind;

  const role = `You are ${coachName}, the personal running coach of ${firstName} inside the Atala app. ${firstName} writes to you in a chat on their phone, usually between two things, and wants an answer they can act on.

How you talk
- Tone chosen by the runner: ${tone}
- Write in English, in plain sentences, the way a good coach texts: usually two to four short sentences. No headings, no bullet lists, no markdown, no emoji.
- Be concrete: use the sessions, dates, distances and paces from the data below rather than general advice. Paces are in min/km, distances in km.
- You only reply when the runner writes to you, and you answer what they asked.

What you know
The runner's goal, paces, upcoming sessions and recent runs are given below as JSON. That is all you know about their training; if something is not there, say so simply instead of guessing.

Health comes first
You are a coach, not a health professional, so you never diagnose or name a condition and you never recommend medication. When the runner mentions pain, an injury, or symptoms that worry them, tell them warmly that it is worth having it looked at by a doctor or physiotherapist, and offer to lighten or move the coming sessions in the meantime. Chest pain, fainting or trouble breathing means stop and seek medical help now.

Changing the plan
You cannot edit the plan yourself. When the runner asks to move, skip, shorten or lighten a session, or tells you about something coming up that clashes with one (travel, a busy day, tiredness, illness, a niggle), call propose_plan_change with the smallest change that solves it. The app shows your proposal as a card with "Apply" and "Keep my plan"; nothing changes until they tap Apply, so talk about it as a suggestion. Keep the cautious logic of the plan: avoid two hard sessions (intervals, tempo, race pace, long run) on consecutive days, keep at least one easy or rest day before a long run, never move or alter the race itself, and do not add volume to make up for a missed session. A missed session is simply missed. If the request is ambiguous (which session? which day?), ask one short question instead of proposing.

Scope
You talk about running, training, recovery, motivation around running, and how to use the app. For anything else, say kindly that it is outside what you can help with here and bring the conversation back to their running.`;

  const data = {
    today: context.today ?? null,
    weekday: context.weekday ?? null,
    runner: firstName,
    goal: context.goal ?? null,
    raceDate: context.raceDate ?? null,
    targetSeconds: context.targetSeconds ?? null,
    paces: context.paces ?? null,
    workouts: context.workouts ?? [],
    recentRuns: context.recentRuns ?? [],
  };

  return `${role}

Training data (JSON). "today" and "weekday" are the runner's current day. "workouts" covers the last two weeks and roughly the next three; each has its date and weekday. Use the "id" of a workout when you propose a change, and work out dates from "today" rather than guessing.
${JSON.stringify(data)}`;
}

const TOOL_NAME = "propose_plan_change";

const proposeTool = {
  type: "function",
  function: {
  name: TOOL_NAME,
  description:
    "Propose a change to the runner's training plan. Use it when the runner asks to move, skip, shorten or lighten sessions, or warns about an upcoming conflict, tiredness, illness or pain. The app shows the proposal as a card; the plan only changes if the runner taps Apply. Only reference workouts listed in the training data, by their id.",
  parameters: {
    type: "object",
    properties: {
      summary: {
        type: "string",
        description: "One or two short sentences shown under the changes, e.g. what stays the same this week.",
      },
      changes: {
        type: "array",
        minItems: 1,
        items: {
          type: "object",
          properties: {
            workoutId: { type: "string", description: "The id of an existing workout from the training data." },
            newDate: { type: "string", description: "New day for the session, ISO format yyyy-MM-dd." },
            newKind: { type: "string", enum: KINDS, description: "New session type, e.g. easy to replace a hard session." },
            newTitle: { type: "string", description: "New short title, e.g. \"Easy run\". Give one when newKind changes." },
            newDistanceKm: { type: "number", description: "New distance in km." },
            newDurationMin: { type: "integer", description: "New duration in minutes." },
            remove: { type: "boolean", description: "True to drop the session from the plan." },
          },
          required: ["workoutId"],
        },
      },
    },
    required: ["summary", "changes"],
  },
  },
};

/** Returns a clean proposal, or the list of problems to send back to the model. */
function validateProposal(input: unknown, context: CoachContext): { proposal?: Proposal; errors: string[] } {
  const errors: string[] = [];
  const known = new Map<string, ContextWorkout>();
  for (const workout of context.workouts ?? []) {
    if (workout && typeof workout.id === "string") known.set(workout.id.toLowerCase(), workout);
  }

  const data = (input ?? {}) as Record<string, unknown>;
  const summary = typeof data.summary === "string" ? data.summary.trim() : "";
  if (!summary) errors.push("summary is required");
  if (!Array.isArray(data.changes) || data.changes.length === 0) {
    errors.push("changes must contain at least one change");
    return { errors };
  }

  const changes: ProposalChange[] = [];
  data.changes.forEach((rawChange: unknown, index: number) => {
    const raw = (rawChange ?? {}) as Record<string, unknown>;
    const label = `changes[${index}]`;
    const workout = typeof raw.workoutId === "string" ? known.get(raw.workoutId.toLowerCase()) : undefined;
    if (!workout) {
      errors.push(`${label}.workoutId does not match any workout id in the training data`);
      return;
    }
    if (workout.kind === "race") {
      errors.push(`${label}: the race itself cannot be changed`);
      return;
    }
    const change: ProposalChange = { workoutId: workout.id };
    if (raw.remove === true) {
      change.remove = true;
      changes.push(change);
      return;
    }
    if (raw.newDate !== undefined && raw.newDate !== null) {
      if (!isIsoDate(raw.newDate)) errors.push(`${label}.newDate must be a real date in yyyy-MM-dd format`);
      else if (context.today && isIsoDate(context.today) && raw.newDate < context.today) {
        errors.push(`${label}.newDate is in the past (today is ${context.today})`);
      } else change.newDate = raw.newDate;
    }
    if (raw.newKind !== undefined && raw.newKind !== null) {
      if (typeof raw.newKind !== "string" || !KINDS.includes(raw.newKind)) {
        errors.push(`${label}.newKind must be one of ${KINDS.join(", ")}`);
      } else change.newKind = raw.newKind;
    }
    if (typeof raw.newTitle === "string" && raw.newTitle.trim()) change.newTitle = raw.newTitle.trim().slice(0, 60);
    if (raw.newDistanceKm !== undefined && raw.newDistanceKm !== null) {
      const km = Number(raw.newDistanceKm);
      if (!Number.isFinite(km) || km < 0 || km > 60) errors.push(`${label}.newDistanceKm must be between 0 and 60`);
      else change.newDistanceKm = Math.round(km * 10) / 10;
    }
    if (raw.newDurationMin !== undefined && raw.newDurationMin !== null) {
      const minutes = Number(raw.newDurationMin);
      if (!Number.isFinite(minutes) || minutes < 0 || minutes > 400) errors.push(`${label}.newDurationMin must be between 0 and 400`);
      else change.newDurationMin = Math.round(minutes);
    }
    if (Object.keys(change).length === 1) {
      errors.push(`${label} changes nothing: give a new date, kind, title, distance or duration, or remove: true`);
      return;
    }
    changes.push(change);
  });

  if (errors.length > 0) return { errors };
  return { proposal: { summary, changes }, errors };
}

// ---------- Model call ----------

async function callModel(falKey: string, conversation: ChatTurn[], signal: AbortSignal): Promise<ChatTurn & { finish?: string }> {
  let response: Response;
  try {
    response = await fetch(MODEL_URL, {
      method: "POST",
      headers: { "Authorization": `Key ${falKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: MODEL,
        messages: conversation,
        tools: [proposeTool],
        max_tokens: 1200,
        temperature: 0.6,
      }),
      signal: AbortSignal.any([signal, AbortSignal.timeout(REQUEST_TIMEOUT_MS)]),
    });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new ModelError(0, error instanceof Error ? error.name : "network");
  }
  if (!response.ok) {
    await response.body?.cancel();
    throw new ModelError(response.status, "model endpoint error");
  }
  const data = await response.json();
  const choice = data?.choices?.[0];
  const message = choice?.message;
  if (!message || typeof message !== "object") throw new ModelError(502, "no message in the model response");
  const toolCalls: ToolCall[] = Array.isArray(message.tool_calls)
    ? message.tool_calls.filter((call: ToolCall) => call?.function && typeof call.function.name === "string")
    : [];
  return {
    role: "assistant",
    content: typeof message.content === "string" ? message.content : null,
    tool_calls: toolCalls.length > 0 ? toolCalls : undefined,
    finish: typeof choice?.finish_reason === "string" ? choice.finish_reason : undefined,
  };
}

async function askCoach(
  falKey: string,
  messages: ChatTurn[],
  context: CoachContext,
  signal: AbortSignal,
): Promise<{ reply: string; proposal?: Proposal }> {
  const conversation: ChatTurn[] = [{ role: "system", content: systemPrompt(context) }, ...messages];
  let proposal: Proposal | undefined;
  let spokenText = "";

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const answer = await callModel(falKey, conversation, signal);
    const text = (answer.content ?? "").trim();
    const toolCalls = answer.tool_calls ?? [];

    if (toolCalls.length === 0) {
      return { reply: text || spokenText || proposal?.summary || "", proposal };
    }

    if (text) spokenText = text;
    conversation.push({ role: "assistant", content: answer.content, tool_calls: toolCalls });

    for (const call of toolCalls) {
      if (call.function.name.toLowerCase() !== TOOL_NAME) {
        conversation.push({ role: "tool", tool_call_id: call.id, content: `Unknown tool. The only tool is ${TOOL_NAME}.` });
        continue;
      }
      let input: unknown;
      try {
        input = JSON.parse(call.function.arguments || "{}");
      } catch {
        conversation.push({ role: "tool", tool_call_id: call.id, content: "The arguments were not valid JSON. Call the tool again with valid JSON." });
        continue;
      }
      const checked = validateProposal(input, context);
      if (!checked.proposal) {
        conversation.push({
          role: "tool",
          tool_call_id: call.id,
          content: `The proposal was not shown. Fix these and call the tool again: ${checked.errors.join("; ")}`,
        });
        continue;
      }
      proposal = checked.proposal;
      conversation.push({
        role: "tool",
        tool_call_id: call.id,
        content:
          "The proposal is now shown to the runner as a card with Apply and Keep my plan. Nothing has changed yet. Write the short message that goes with it; do not call the tool again.",
      });
    }
  }

  return { reply: spokenText || proposal?.summary || "", proposal };
}

// ---------- Handler ----------

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return fail("method_not_allowed", 405);

  // Without the fal key the app silently uses its on-phone replies.
  const falKey = (Deno.env.get("FAL_KEY") ?? "").trim();
  if (!falKey) return fail("coach_not_configured", 503);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    console.error("coach: missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
    return fail("server_not_configured", 500);
  }

  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) return fail("unauthorized", 401);

  try {
    return await handle(req, token, supabaseUrl, serviceKey, falKey);
  } catch (error) {
    console.error("coach: unexpected error", error instanceof Error ? error.message : "unknown");
    return fail("server_error", 500);
  }
});

async function handle(
  req: Request,
  token: string,
  supabaseUrl: string,
  serviceKey: string,
  falKey: string,
): Promise<Response> {
  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const user = userData?.user;
  if (userError || !user) return fail("unauthorized", 401);

  const declared = Number(req.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > MAX_BODY_CHARS * 4) return fail("payload_too_large", 413);
  const rawBody = await req.text();
  if (rawBody.length > MAX_BODY_CHARS) return fail("payload_too_large", 413);

  let payload: { messages?: unknown; context?: unknown };
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return fail("invalid_json", 400);
  }
  if (!payload || typeof payload !== "object") return fail("invalid_request", 400, "the body must be a JSON object");
  const messages = buildMessages(payload.messages);
  if (typeof messages === "string") return fail("invalid_request", 400, messages);
  const context = cleanContext(payload.context);

  // Daily cap, counted atomically in the database.
  const day = new Date().toISOString().slice(0, 10);
  const { data: used, error: usageError } = await admin.rpc("increment_coach_usage", { p_user_id: user.id, p_day: day });
  if (usageError) {
    console.error("coach: usage counter failed", usageError.message);
    return fail("server_error", 500);
  }
  if (typeof used === "number" && used > DAILY_CAP) return fail("daily_limit", 429);

  const deadline = new AbortController();
  const timer = setTimeout(() => deadline.abort(), DEADLINE_MS);
  let result: { reply: string; proposal?: Proposal };
  try {
    result = await askCoach(falKey, messages, context, deadline.signal);
  } catch (error) {
    if (deadline.signal.aborted) {
      console.error("coach: model took too long");
      return fail("timeout", 504);
    }
    if (error instanceof ModelError) {
      // 401/403: the key is wrong or revoked. Same answer as a missing key, so the app falls back.
      if (error.status === 401 || error.status === 403) {
        console.error("coach: the fal key was refused", error.status);
        return fail("coach_not_configured", 503);
      }
      if (error.status === 429) {
        console.error("coach: model rate limited");
        return fail("busy", 503);
      }
      if (error.status === 0) {
        console.error("coach: model unreachable");
        return fail("upstream_unreachable", 502);
      }
      console.error("coach: model error", error.status);
      return fail("upstream_error", 502);
    }
    console.error("coach: unexpected model error", error instanceof Error ? error.name : "unknown");
    return fail("server_error", 500);
  } finally {
    clearTimeout(timer);
  }

  if (!result.reply) return fail("empty_reply", 502);

  // Keep the exchange so the conversation can be restored on another phone.
  const lastUser = messages[messages.length - 1];
  const userText = typeof lastUser.content === "string" ? lastUser.content : "";
  let messageId: string | undefined;
  const { data: stored, error: storeError } = await admin
    .from("coach_messages")
    .insert([
      { user_id: user.id, role: "user", body: userText, proposal: null },
      { user_id: user.id, role: "coach", body: result.reply, proposal: result.proposal ?? null },
    ])
    .select("id, role");
  if (storeError) console.error("coach: could not store messages", storeError.message);
  else messageId = stored?.find((row: { id: string; role: string }) => row.role === "coach")?.id;

  const body: Record<string, unknown> = { reply: result.reply };
  if (result.proposal) body.proposal = result.proposal;
  if (messageId) body.messageId = messageId;
  return json(body);
}
