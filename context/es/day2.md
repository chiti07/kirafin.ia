# Candidato · Día 2 — Finalizar el plan

> **Entregable 1 (Plan y Comprensión) · Parte 2 de 2 · ~2–3 hs**
> Termina el documento de diseño. Este es tu último día de solo diseño — el desarrollo comienza mañana.
>

## Enfoque

Convierte el boceto de ayer en un diseño desde el cual un compañero de equipo pueda construir. Muestra el pensamiento, no la implementación.

**Abstracción y límites.** Define la abstracción de proveedor (vendor abstraction) de modo que agregar un 3er banco proveedor sea un cambio de configuración, no una reescritura. Dibuja los límites entre API / dominio / worker. Dos proveedores fiat simulados con formas distintas deben estar detrás de una sola interfaz.

**Fallo y crash-consistency.** Nombra la ventana de crash (crash window) entre la escritura en el libro contable y la llamada al proveedor, y explica exactamente cómo se recupera. Define tus claves de idempotencia (idempotency keys) con precisión — qué hace que una solicitud reintentada o un webhook entregado dos veces sea seguro.

**Reconciliación como consulta.** Explica cómo la reconciliación de cierre de día contra un extracto bancario/en cadena es simplemente una consulta sobre tus entradas append-only, y cuáles son los dos tipos de discrepancia (settled-with-no-entry; entry-never-confirmed).

**Trade-offs (decisiones de diseño).** Expresa las decisiones que estás tomando y por qué — y qué estás explícitamente dejando fuera.

---

✅ **Definición de Hecho — Día 2 (Entregable 1 completo)**

- [ ]  `DESIGN.md` está completo: modelo de datos, estrategia de idempotencia, recuperación ante crashes, abstracción de proveedores, enfoque de reconciliación y trade-offs nombrados.
- [ ]  Un revisor podría entender tu sistema y sus invariantes sin ver ningún código.
- [ ]  `DECISIONS.md` refleja las decisiones clave y los supuestos detrás de ellas.
- [ ]  Entrega el Entregable 1 (push al repo) antes del final de tu ventana del Día 2.

---

> 📝 **Nota**
> Aún no hay retroalimentación — a propósito. El Entregable 1 debe reflejar tu propio juicio, sin ayuda externa. Recibirás nuestra retroalimentación después del Entregable 2, así que comprométete con tu diseño y avanza.
>