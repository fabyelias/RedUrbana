# Supabase — RedUrbana

Proyecto: `klmtzimhkfllpftzbeci`. URL y publishable key ya están en
`app/src/main/res/values/supabase.xml` (ver `docs/CREDENTIALS_SETUP.md`
sección 4 sobre por qué eso es seguro).

## Cómo aplicar el schema

1. Dashboard de Supabase → **SQL Editor**.
2. Pegar el contenido de `migrations/0001_init_schema.sql`. **Run.**
3. Pegar el contenido de `migrations/0002_crowd_aggregation.sql`. **Run.**

Los dos son idempotentes: se pueden volver a correr sin romper nada si ya
se aplicaron antes (usan `create ... if not exists` / `drop ... if exists`).

Si en algún momento se prefiere usar Supabase CLI en vez de pegar el SQL a
mano (`supabase link` + `supabase db push`), este mismo archivo ya sigue la
convención de carpetas que espera el CLI (`supabase/migrations/`).

## Qué crea

| Tabla | Para qué | Quién lee | Quién escribe |
|---|---|---|---|
| `profiles` | Perfil de usuario (domain-user) | dueño de la fila | trigger automático al registrarse + dueño (update) |
| `favorite_routes` | Favoritos (domain-favorites, `ManageFavoritesUseCase`) | dueño de la fila | dueño de la fila |
| `service_alerts` | Alertas de servicio (`ServiceAlert` en domain-transport) | público (anon + authenticated) | solo `service_role` |
| `crowd_pings` | Pings anónimos de ubicación (docs/ARQUITECTURA.md §14) | solo `service_role` | cualquiera (anon + authenticated), insert-only |
| `vehicle_group_estimates` | Estimaciones agregadas por vehículo (`CrowdSourcingRepository.observeGroupEstimates`) | público (anon + authenticated) | solo `service_role` |

## Backend de agregación (0002)

`aggregate_vehicle_group_estimates()` corre sola cada 1 minuto vía
`pg_cron`: agrupa los `crowd_pings` de los últimos 2 minutos por línea,
promedia posición/rumbo/velocidad, y reescribe `vehicle_group_estimates`.
Limitación conocida (documentada en el archivo): no distingue vehículo/ramal
dentro de una misma línea — con dos colectivos reportando a la vez en la
misma línea, el promedio no representa a ninguno de los dos con precisión.
Aceptable mientras la adopción sea baja; si crece, hace falta clustering
espacial en vez de un promedio simple.

## Deliberadamente fuera de este cambio

- **El cliente Kotlin** (agregar `supabase-kt` como dependencia, implementar
  `LocalCrowdSourcingRepository` → uno real, wiring en `CrowdSourcingModule`,
  pantallas de login). Este cambio es solo el schema + políticas de la base;
  avisar si se quiere seguir con la integración Android.
- `crowd_pings.session_id` no tiene FK a `auth.users` a propósito: es lo que
  mantiene el crowdsourcing anónimo (ver comentario en
  `CrowdsourcingModels.kt`). No agregarla en futuras migraciones sin releer
  esa sección de la arquitectura.
