-- Atala sync support: server-side timestamps, run tombstones, ordered coach history,
-- and the answer given to a coach proposal. Additive only.

-- 1. `updated_at` is always the server clock, whatever the phone sends.
create function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

revoke all on function public.set_updated_at() from public, anon, authenticated;

create trigger profiles_set_updated_at
  before insert or update on public.profiles
  for each row execute function public.set_updated_at();

create trigger plans_set_updated_at
  before insert or update on public.plans
  for each row execute function public.set_updated_at();

create trigger runs_set_updated_at
  before insert or update on public.runs
  for each row execute function public.set_updated_at();

-- 2. A deleted run stays as a tombstone, so every phone of the account removes it too.
alter table public.runs add column deleted_at timestamptz;

-- 3. The two rows of one exchange share `created_at`: `seq` breaks the tie (order by created_at, seq).
alter table public.coach_messages add column seq bigint generated always as identity;

-- 4. What the runner answered to a proposal. The app may change this column and nothing else.
alter table public.coach_messages
  add column proposal_status text not null default 'pending'
  check (proposal_status in ('pending', 'applied', 'declined'));

revoke update on public.coach_messages from authenticated, anon;
grant update (proposal_status) on public.coach_messages to authenticated;

create policy "coach_messages_update_own" on public.coach_messages for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
