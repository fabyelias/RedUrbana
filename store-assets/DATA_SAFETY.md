# Formulario "Seguridad de los datos" de Play Console — respuestas

Esto no es una página pública: es la guía de qué tildar en el formulario de
Play Console (Play Console → tu app → Política → Seguridad de los datos),
basada en lo que el código de la app realmente hace (ver
`store-assets/privacy-policy.html` para el detalle completo).

## 1. ¿Tu app recopila o comparte alguno de los tipos de datos requeridos?

**Sí.**

## 2. Tipos de datos

### Ubicación

- **Ubicación aproximada** — Recopilada: Sí. Compartida: Sí (solo si el
  usuario activa modo Auto o el reporte de viaje). Procesada efímeramente: Sí,
  si aplica. Opcional: **Sí**, el usuario puede negar el permiso de ubicación
  a nivel de Android y usar igual las partes de la app que no lo requieren.
  Finalidad: **Funcionalidad de la app** (mostrar el mapa, calcular rutas).

- **Ubicación precisa** — Recopilada: Sí. Compartida: Sí (solo si el usuario
  activa modo Auto o el reporte de viaje — en ese caso se comparte de forma
  **anónima**, con un identificador de sesión aleatorio, no vinculado a
  cuenta ni identidad). Procesada efímeramente: Sí, se borra al terminar el
  viaje. Opcional: **Sí**. Finalidad: **Funcionalidad de la app**.

### Ningún otro tipo de dato

La app **no** recolecta: nombre, email, teléfono, identificadores de
usuario/dispositivo persistentes, contactos, fotos, archivos, historial de
navegación, información financiera, salud, mensajes, ni datos de uso/
diagnóstico con terceros de analítica/publicidad — no hay SDKs de ese tipo
integrados.

## 3. ¿Todos los usuarios pueden pedir que se borren los datos?

No aplica un flujo de "borrar mi cuenta" porque **no hay cuentas**: los
únicos datos compartidos (posición en modo Auto / reportes de viaje) usan un
identificador de sesión aleatorio que se descarta automáticamente al
terminar cada viaje — no hay nada persistente que borrar a pedido.

## 4. ¿Los datos se cifran en tránsito?

**Sí** — toda la comunicación con Mapbox y Supabase es HTTPS.

## 5. ¿La app cumple con la Family Policy / está dirigida a niños?

**No**, RedUrbana no está dirigida específicamente a niños (categoría
general, información de transporte público).

## URLs a completar en la ficha de Play Console

- **Política de Privacidad** (campo obligatorio): la URL pública del
  artifact publicado — recordá compartirlo como público desde el menú de
  compartir de la página antes de pegarlo en Play Console.
- **Términos de Servicio** (campo opcional en Play Console, recomendado):
  la URL pública del artifact de Términos y Condiciones.
