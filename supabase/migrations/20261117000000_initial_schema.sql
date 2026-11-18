-- Atala initial schema: one user owns their own rows, nothing else.

create table public.profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  first_name text,
  coach_id integer,
  answers jsonb not null default '{}'::jsonb,
  settings jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table public.plans (
  user_id uuid primary key references auth.users (id) on delete cascade,
  plan jsonb not null,
  updated_at timestamptz not null default now()
);

create table public.runs (
  id uuid primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  run jsonb not null,
  run_date timestamptz,
  updated_at timestamptz not null default now()
);
create index runs_user_id_idx on public.runs (user_id, run_date desc);

create table public.coach_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null check (role in ('user', 'coach')),
  body text not null,
  proposal jsonb,
  created_at timestamptz not null default now()
);
create index coach_messages_user_id_idx on public.coach_messages (user_id, created_at desc);

-- Invisible daily cap on coach messages. Written only by the edge function (service role).
create table public.coach_usage (
  user_id uuid not null references auth.users (id) on delete cascade,
  day date not null,
  count integer not null default 0,
  primary key (user_id, day)
);

alter table public.profiles enable row level security;
alter table public.plans enable row level security;
alter table public.runs enable row level security;
alter table public.coach_messages enable row level security;
alter table public.coach_usage enable row level security;

-- profiles
create policy "profiles_select_own" on public.profiles for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "profiles_insert_own" on public.profiles for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "profiles_update_own" on public.profiles for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "profiles_delete_own" on public.profiles for delete to authenticated
  using ((select auth.uid()) = user_id);

-- plans
create policy "plans_select_own" on public.plans for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "plans_insert_own" on public.plans for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "plans_update_own" on public.plans for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "plans_delete_own" on public.plans for delete to authenticated
  using ((select auth.uid()) = user_id);

-- runs
create policy "runs_select_own" on public.runs for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "runs_insert_own" on public.runs for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "runs_update_own" on public.runs for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "runs_delete_own" on public.runs for delete to authenticated
  using ((select auth.uid()) = user_id);

-- coach_messages: the app may read and delete its history; rows are written by the edge function.
create policy "coach_messages_select_own" on public.coach_messages for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "coach_messages_insert_own" on public.coach_messages for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "coach_messages_delete_own" on public.coach_messages for delete to authenticated
  using ((select auth.uid()) = user_id);

-- coach_usage: read-only for its owner, so the counter cannot be reset from the app.
create policy "coach_usage_select_own" on public.coach_usage for select to authenticated
  using ((select auth.uid()) = user_id);

-- Atomic "count one more message today" used by the coach edge function.
create function public.increment_coach_usage(p_user_id uuid, p_day date)
returns integer
language sql
security invoker
set search_path = ''
as $$
  insert into public.coach_usage (user_id, day, count)
  values (p_user_id, p_day, 1)
  on conflict (user_id, day) do update set count = public.coach_usage.count + 1
  returning count;
$$;

revoke all on function public.increment_coach_usage(uuid, date) from public, anon, authenticated;
grant execute on function public.increment_coach_usage(uuid, date) to service_role;
