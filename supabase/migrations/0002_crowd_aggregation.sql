-- RedUrbana — backend de agregación de crowdsourcing (docs/ARQUITECTURA.md #14)
--
-- Hasta ahora crowd_pings se llenaba pero nadie lo leía: vehicle_group_estimates
-- (lo que sí lee CrowdsourcedTransportProvider del lado Android) se quedaba
-- vacío para siempre. Esto agrega la pieza que faltaba: una función que
-- agrupa los pings recientes por línea y calcula una posición estimada, más
-- un cron job de pg_cron que la corre sola cada 1 minuto.
--
-- Corre siempre como el rol dueño de la función (security definer), nunca
-- desde el cliente — las políticas de RLS de las dos tablas (0001) siguen
-- sin darle acceso directo a anon/authenticated a ninguna de las dos.
--
-- LIMITACIÓN CONOCIDA: crowd_pings no distingue vehículo/ramal, solo línea
-- (candidate_route_id). Si dos colectivos DE LA MISMA línea reportan pings a
-- la vez (ej. yendo en sentidos opuestos), este promedio los mezcla en un
-- punto que no es la posición real de ninguno de los dos. Aceptable como
-- primera versión mientras la adopción es baja — con pocos usuarios activos
-- es raro tener 2+ reporters simultáneos en la misma línea. Si eso cambia,
-- hace falta clustering espacial (ej. DBSCAN) en vez de un promedio simple.
--
-- Igual que 0001: idempotente, se puede volver a correr sin romper nada.

-- En Supabase normalmente alcanza con esto. Si da error de permisos, activar
-- la extensión "pg_cron" a mano desde Database → Extensions en el dashboard
-- y volver a correr el resto del archivo.
create extension if not exists pg_cron;

create or replace function public.aggregate_vehicle_group_estimates()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    with recent_pings as (
        -- Ventana de 2 minutos: bastante más que el intervalo de reporte del
        -- cliente (5s, ver LocationReporter.kt) para tolerar algo de
        -- latencia de red, pero corta para que la posición calculada siga
        -- representando "dónde está el colectivo AHORA", no un histórico.
        select *
        from public.crowd_pings
        where candidate_route_id is not null
          and recorded_at >= now() - interval '2 minutes'
    ),
    aggregated as (
        select
            candidate_route_id as route_id,
            avg(latitude) as avg_lat,
            avg(longitude) as avg_lng,
            avg(speed_kmh) as avg_speed,
            -- Promedio circular de rumbo: un promedio aritmético directo de
            -- grados falla cerca del corte 0°/360° (ej. 350° y 10° darían
            -- 180°, el opuesto exacto en vez de ~0°). Se promedia el vector
            -- unitario (seno, coseno) de cada rumbo y se vuelve a grados
            -- con atan2 — el enfoque estándar para promediar ángulos.
            degrees(
                atan2(
                    avg(sin(radians(bearing_degrees))),
                    avg(cos(radians(bearing_degrees)))
                )
            ) as avg_bearing,
            count(*) as ping_count
        from recent_pings
        group by candidate_route_id
    )
    -- Se borra todo y se reinserta en vez de upsert: así una línea que ya no
    -- tiene pings recientes (el colectivo salió de servicio, o nadie la está
    -- reportando ahora mismo) desaparece sola en la próxima corrida, en vez
    -- de quedar mostrando una posición vieja como si fuera actual.
    delete from public.vehicle_group_estimates;

    insert into public.vehicle_group_estimates (
        route_id, estimated_latitude, estimated_longitude,
        estimated_bearing_degrees, estimated_speed_kmh,
        contributing_ping_count, confidence_percent, last_updated
    )
    select
        route_id,
        avg_lat,
        avg_lng,
        case when avg_bearing < 0 then avg_bearing + 360 else avg_bearing end, -- atan2 puede devolver negativo
        avg_speed,
        ping_count,
        -- Confianza simple, sin inventar precisión que no hay: escala con
        -- cuántos reportes independientes sostienen la estimación, tope 100.
        least(100, ping_count * 25)::smallint,
        now()
    from aggregated;
end;
$$;

-- Se borra el job anterior si ya existía antes de re-programarlo (mismo
-- criterio idempotente que el resto de este archivo).
select cron.unschedule(jobid)
from cron.job
where jobname = 'aggregate-vehicle-group-estimates';

select cron.schedule(
    'aggregate-vehicle-group-estimates',
    '* * * * *', -- cada 1 minuto: la granularidad mínima estándar de pg_cron
    $$ select public.aggregate_vehicle_group_estimates(); $$
);
