// Vicrun coach: checks the caller, applies the daily cap, asks Claude, returns
// { reply, proposal? }. The plan itself is only changed in the app, after the runner taps Apply.
import Anthropic from "npm:@anthropic-ai/sdk@^1.10.0";
import { createClient } from "npm:@supabase/supabase-js@2";

const MODEL = "claude-sonnet-5-5";
const DAILY_CAP = 150;
const MAX_HISTORY = 30;
const MAX_MESSAGE_CHARS = 4000;
const MAX_TOOL_ROUNDS = 3;

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
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

function buildMessages(raw: unknown): Anthropic.Beta.BetaMessageParam[] | string {
  if (!Array.isArray(raw) || raw.length === 0) return "messages must be a non-empty array";
  const recent = raw.slice(-MAX_HISTORY);
  const out: Anthropic.Beta.BetaMessageParam[] = [];
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

function systemPrompt(context: CoachContext): Anthropic.Beta.BetaTextBlockParam[] {
  const coachName = (context.coachName ?? "").trim() || "Coach";
  const firstName = (context.firstName ?? "").trim() || "the runner";
  const tone = TONES[context.tone ?? "kind"] ?? TONES.kind;

  const role = `You are ${coachName}, the personal running coach of ${firstName} inside the Vicrun app. ${firstName} writes to you in a chat on their phone, usually between two things, and wants an answer they can act on.

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
    runner: firstName,
    goal: context.goal ?? null,
    raceDate: context.raceDate ?? null,
    targetSeconds: context.targetSeconds ?? null,
    paces: context.paces ?? null,
    workouts: context.workouts ?? [],
    recentRuns: context.recentRuns ?? [],
  };

  return [
    { type: "text", text: role },
    {
      type: "text",
      text: `Training data (JSON). "workouts" covers the last two sessions and roughly the next three weeks; use the "id" of a workout when you propose a change.\n${JSON.stringify(data)}`,
      cache_control: { type: "ephemeral" },
    },
  ];
}

const proposeTool: Anthropic.Beta.BetaTool = {
  name: "propose_plan_change",
  description:
    "Propose a change to the runner's training plan. Use it when the runner asks to move, skip, shorten or lighten sessions, or warns about an upcoming conflict, tiredness, illness or pain. The app shows the proposal as a card; the plan only changes if the runner taps Apply. Only reference workouts listed in the training data, by their id.",
  input_schema: {
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

async function askCoach(
  client: Anthropic,
  messages: Anthropic.Beta.BetaMessageParam[],
  context: CoachContext,
): Promise<{ reply: string; proposal?: Proposal }> {
  const conversation = [...messages];
  const system = systemPrompt(context);
  let proposal: Proposal | undefined;
  let spokenText = "";

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const response = await client.beta.messages.create({
      model: MODEL,
      max_tokens: 8000,
      output_config: { effort: "low" },
      // If the model declines for a policy reason, the API retries once on its fallback model.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      system,
      tools: [proposeTool],
      messages: conversation,
    });

    if (response.stop_reason === "refusal") {
      return { reply: "That's outside what I can help with here. Tell me about your running and I'm all yours." };
    }

    const text = response.content
      .filter((block): block is Anthropic.Beta.BetaTextBlock => block.type === "text")
      .map((block) => block.text)
      .join("\n")
      .trim();
    const toolUses = response.content.filter(
      (block): block is Anthropic.Beta.BetaToolUseBlock => block.type === "tool_use",
    );

    if (response.stop_reason !== "tool_use" || toolUses.length === 0) {
      return { reply: text || spokenText || proposal?.summary || "", proposal };
    }

    if (text) spokenText = text;
    conversation.push({ role: "assistant", content: response.content });

    const results: Anthropic.Beta.BetaToolResultBlockParam[] = [];
    for (const toolUse of toolUses) {
      if (toolUse.name.toLowerCase() !== proposeTool.name) {
        results.push({ type: "tool_result", tool_use_id: toolUse.id, is_error: true, content: `Unknown tool. The only tool is ${proposeTool.name}.` });
        continue;
      }
      const checked = validateProposal(toolUse.input, context);
      if (!checked.proposal) {
        results.push({
          type: "tool_result",
          tool_use_id: toolUse.id,
          is_error: true,
          content: `The proposal was not shown. Fix these and call the tool again: ${checked.errors.join("; ")}`,
        });
        continue;
      }
      proposal = checked.proposal;
      results.push({
        type: "tool_result",
        tool_use_id: toolUse.id,
        content:
          "The proposal is now shown to the runner as a card with Apply and Keep my plan. Nothing has changed yet. Write the short message that goes with it; do not call the tool again.",
      });
    }
    conversation.push({ role: "user", content: results });
  }

  return { reply: spokenText || proposal?.summary || "", proposal };
}

// ---------- Handler ----------

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anthropicKey = Deno.env.get("ANTHROPIC_API_KEY");
  if (!supabaseUrl || !serviceKey || !anthropicKey) {
    console.error("coach: missing SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY or ANTHROPIC_API_KEY");
    return json({ error: "not_configured" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!token) return json({ error: "unauthorized" }, 401);

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const user = userData?.user;
  if (userError || !user) return json({ error: "unauthorized" }, 401);

  let payload: { messages?: unknown; context?: CoachContext };
  try {
    payload = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }
  const messages = buildMessages(payload?.messages);
  if (typeof messages === "string") return json({ error: "invalid_request", message: messages }, 400);
  const context: CoachContext = payload?.context && typeof payload.context === "object" ? payload.context : {};
  if (!Array.isArray(context.workouts)) context.workouts = [];
  if (JSON.stringify(context).length > 60000) return json({ error: "invalid_request", message: "context is too large" }, 400);

  // Daily cap, counted atomically in the database.
  const day = new Date().toISOString().slice(0, 10);
  const { data: used, error: usageError } = await admin.rpc("increment_coach_usage", { p_user_id: user.id, p_day: day });
  if (usageError) {
    console.error("coach: usage counter failed", usageError.message);
    return json({ error: "server_error" }, 500);
  }
  if (typeof used === "number" && used > DAILY_CAP) return json({ error: "daily_limit" }, 429);

  let result: { reply: string; proposal?: Proposal };
  try {
    const client = new Anthropic({ apiKey: anthropicKey });
    result = await askCoach(client, messages, context);
  } catch (error) {
    if (error instanceof Anthropic.RateLimitError) {
      console.error("coach: model rate limited");
      return json({ error: "busy" }, 503);
    }
    if (error instanceof Anthropic.APIConnectionError) {
      console.error("coach: model unreachable", error.message);
      return json({ error: "upstream_unreachable" }, 502);
    }
    if (error instanceof Anthropic.APIError) {
      console.error("coach: model error", error.status, error.message);
      return json({ error: "upstream_error" }, 502);
    }
    console.error("coach: unexpected error", error);
    return json({ error: "server_error" }, 500);
  }

  if (!result.reply) return json({ error: "empty_reply" }, 502);

  const lastUser = messages[messages.length - 1];
  const userText = typeof lastUser.content === "string" ? lastUser.content : "";
  const { error: storeError } = await admin.from("coach_messages").insert([
    { user_id: user.id, role: "user", body: userText },
    { user_id: user.id, role: "coach", body: result.reply, proposal: result.proposal ?? null },
  ]);
  if (storeError) console.error("coach: could not store messages", storeError.message);

  return json(result.proposal ? { reply: result.reply, proposal: result.proposal } : { reply: result.reply });
});
