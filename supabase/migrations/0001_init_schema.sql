-- RedUrbana — schema inicial de Supabase
--
-- Cubre los dos backends documentados como pendientes en docs/ARQUITECTURA.md:
--   - Sección 14: crowdsourcing de ubicación (crowd_pings, vehicle_group_estimates)
--   - Módulos vacíos domain-favorites / domain-alerts / domain-user
--     (favorite_routes, service_alerts, profiles), usando Supabase Auth
--     (auth.users) como identidad en vez de una tabla de usuario propia.
--
-- Este archivo ya se corrió una vez a mano en el SQL Editor de Supabase (el
-- proyecto real ya tiene estas tablas) — se recreó acá porque el archivo
-- nunca había quedado commiteado. Es idempotente (create ... if not exists
-- / drop policy if exists), correrlo de nuevo no rompe nada.

create extension if not exists pgcrypto with schema extensions;

-- =============================================================================
-- 1. profiles — identidad del usuario (domain-user)
-- =============================================================================
-- Supabase Auth (auth.users) maneja email/password/OAuth. Esta tabla solo
-- guarda los datos de perfil propios de la app que no viven en auth.users.

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text,
    created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own"
    on public.profiles for select
    to authenticated
    using (auth.uid() = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
    on public.profiles for update
    to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- Crea automáticamente la fila de perfil cuando alguien se registra.
-- security definer + search_path fijo: evita que un search_path hostil
-- redirija las referencias a public.profiles.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, new.raw_user_meta_data ->> 'display_name');
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- =============================================================================
-- 2. favorite_routes — domain-favorites (ManageFavoritesUseCase: add/remove/reorder)
-- =============================================================================

create table if not exists public.favorite_routes (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    route_id text not null,
    position integer not null default 0,
    created_at timestamptz not null default now(),
    unique (user_id, route_id)
);

create index if not exists favorite_routes_user_id_idx on public.favorite_routes (user_id);

alter table public.favorite_routes enable row level security;

drop policy if exists "favorite_routes_all_own" on public.favorite_routes;
create policy "favorite_routes_all_own"
    on public.favorite_routes for all
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- =============================================================================
-- 3. service_alerts — domain-alerts, refleja ServiceAlert
--    (domain-transport/model/TransportModels.kt: AlertType, AlertSeverity)
-- =============================================================================

create table if not exists public.service_alerts (
    id uuid primary key default gen_random_uuid(),
    type text not null check (type in ('DETOUR', 'CLOSURE', 'PROTEST', 'ACCIDENT', 'STRIKE')),
    affected_routes text[] not null default '{}',
    message text not null,
    severity text not null check (severity in ('INFO', 'WARNING', 'SEVERE')),
    started_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index if not exists service_alerts_affected_routes_idx on public.service_alerts using gin (affected_routes);
create index if not exists service_alerts_started_at_idx on public.service_alerts (started_at desc);

alter table public.service_alerts enable row level security;

-- Las alertas son información pública del servicio de transporte: cualquiera
-- las puede leer. Solo el backend (service_role, que se salta RLS) las
-- escribe hoy — no hay panel de administración todavía.
drop policy if exists "service_alerts_select_all" on public.service_alerts;
create policy "service_alerts_select_all"
    on public.service_alerts for select
    to anon, authenticated
    using (true);

-- =============================================================================
-- 4. crowd_pings — pings anónimos de ubicación (docs/ARQUITECTURA.md #14)
-- =============================================================================
-- session_id es un TripSessionId de sesión de viaje, deliberadamente SIN
-- referencia a auth.users: es la pieza central de que el crowdsourcing sea
-- anónimo (ver comentario en CrowdsourcingModels.kt). No agregar una FK acá.
--
-- Nadie (ni anon ni authenticated) puede leer esta tabla: son pings crudos
-- de ubicación real de personas. Solo el backend de agregación (service_role,
-- todavía no construido — ver sección 14 "Pendiente") los lee para calcular
-- vehicle_group_estimates.

create table if not exists public.crowd_pings (
    id bigint generated always as identity primary key,
    session_id text not null,
    candidate_route_id text,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    speed_kmh real not null check (speed_kmh >= 0),
    bearing_degrees real not null check (bearing_degrees >= 0 and bearing_degrees < 360),
    recorded_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index if not exists crowd_pings_session_id_idx on public.crowd_pings (session_id);
create index if not exists crowd_pings_recorded_at_idx on public.crowd_pings (recorded_at);
create index if not exists crowd_pings_candidate_route_id_idx on public.crowd_pings (candidate_route_id);

alter table public.crowd_pings enable row level security;

-- Cualquiera (con o sin sesión) puede reportar un ping: el opt-in y el
-- anonimato ya los garantiza el cliente Android (LocationReporter +
-- OnBusHeuristic), no una cuenta autenticada.
drop policy if exists "crowd_pings_insert_anyone" on public.crowd_pings;
create policy "crowd_pings_insert_anyone"
    on public.crowd_pings for insert
    to anon, authenticated
    with check (true);

-- Sin política de select/update/delete para anon/authenticated a propósito:
-- los pings crudos quedan inaccesibles para todos salvo service_role.

-- =============================================================================
-- 5. vehicle_group_estimates — salida agregada del backend de crowdsourcing
--    (CrowdSourcingRepository.observeGroupEstimates)
-- =============================================================================

create table if not exists public.vehicle_group_estimates (
    id uuid primary key default gen_random_uuid(),
    route_id text not null,
    estimated_latitude double precision not null check (estimated_latitude between -90 and 90),
    estimated_longitude double precision not null check (estimated_longitude between -180 and 180),
    estimated_bearing_degrees real not null check (estimated_bearing_degrees >= 0 and estimated_bearing_degrees < 360),
    estimated_speed_kmh real not null check (estimated_speed_kmh >= 0),
    contributing_ping_count integer not null check (contributing_ping_count >= 0),
    confidence_percent smallint not null check (confidence_percent between 0 and 100),
    last_updated timestamptz not null default now()
);

create index if not exists vehicle_group_estimates_route_id_idx on public.vehicle_group_estimates (route_id);

alter table public.vehicle_group_estimates enable row level security;

-- Estimaciones ya calculadas: información pública equivalente a la posición
-- de un colectivo, cualquiera la puede leer.
drop policy if exists "vehicle_group_estimates_select_all" on public.vehicle_group_estimates;
create policy "vehicle_group_estimates_select_all"
    on public.vehicle_group_estimates for select
    to anon, authenticated
    using (true);

-- Sin política de insert/update/delete para anon/authenticated a propósito:
-- solo el backend de agregación (service_role) calcula y escribe estas filas.
