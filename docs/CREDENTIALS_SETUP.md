# Setup de credenciales — RedUrbana

Este proyecto NUNCA debe tener tokens secretos en el repositorio. Así se
configura cada máquina de desarrollo:

## 1. Token secreto de Mapbox (MAPBOX_DOWNLOADS_TOKEN)

Necesario para que Gradle pueda descargar el SDK de Mapbox (repositorio
privado). Se obtiene en https://console.mapbox.com/account/access-tokens/
(scope "Downloads:Read").

Agregar en `~/.gradle/gradle.properties` (NO en la carpeta del proyecto):

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## 2. Token público de Mapbox (pk.*)

Es el que usa la app en runtime para pedir los tiles del mapa. Conceptualmente
es seguro en el cliente, pero **GitHub push protection lo bloquea igual**
(lo detecta como "Mapbox Secret Access Token" sin importar el prefijo
`pk.`), así que `app/src/main/res/values/mapbox.xml` está en `.gitignore` y
no vive en el repo.

Setup en cada máquina:

```bash
cp app/src/main/res/values/mapbox.xml.example app/src/main/res/values/mapbox.xml
```

Y completar `mapbox_access_token` con el valor real desde
https://console.mapbox.com/account/access-tokens/. Si se rota, solo hay que
reemplazar el valor en ese archivo local.

## 3. Google Maps API key (legacy)

Si en algún momento se vuelve a Google Maps, la key va en
`local.properties` (ya ignorado por git) como `MAPS_API_KEY=...` y se
referencia desde el manifest vía `manifestPlaceholders`.

## 4. Supabase (URL + publishable key)

Backend de crowdsourcing/favoritos/alertas/perfil — ver
`supabase/README.md` para el schema y cómo aplicarlo.

Igual que el token público de Mapbox, la publishable key de Supabase
(`sb_publishable_*`) es segura en el repo: ya está en
`app/src/main/res/values/supabase.xml`. Lo que la protege es Row Level
Security (`supabase/migrations/0001_init_schema.sql`), no el secreto de esa
key.

La **service_role key** (si algún día hace falta, ej. para un backend de
agregación) es distinta y NUNCA va al repo — esa sí se salta RLS por
completo.
