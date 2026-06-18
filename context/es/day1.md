# Candidato · Día 1 — Entender el problema

> **Entregable 1 (Plan y Comprensión) · Parte 1 de 2 · ~2–3 hs**
> Hoy se trata de entender, no de implementar. Todavía no se requiere código.
>

## Enfoque

Lee el Resumen y el glosario. Luego comienza tu **documento de diseño** (`DESIGN.md`). Queremos ver que comprendes el problema de *negocio* — libros perfectos a través de rieles que liquidan diferente — antes de modelar una sola tabla.

Desarrolla por escrito:

- **El dominio con tus palabras.** ¿Qué es una Cuenta, una Transferencia, una Rampa, una Ruta, una Comisión — y cómo fluye el flujo de Northwind a través de ellos? Enmarca el problema de negocio, no solo la API.
- **El modelo de libro contable.** ¿Cómo modelarías un libro **de doble entrada, append-only** donde los saldos son *derivados* (no mutados) y las comisiones son en sí mismas entradas? Esboza las tablas/relaciones principales.
- **Dónde el dinero no puede correr.** Nombra el o los puntos donde podría ocurrir un saldo negativo o un doble gasto, y tu primer instinto para aplicar el invariante donde no pueda haber condición de carrera.

> 📝 **Nota**
> Captura preguntas abiertas y supuestos en `DECISIONS.md` a medida que avanzas. No los responderemos ahora (ver Día 2) — tomar una decisión razonada *es* la señal.
>

✅ **Definición de Hecho — Día 1**

- [ ]  `DESIGN.md` existe y explica el dominio en términos de negocio, no solo endpoints.
- [ ]  Un **modelo de datos de doble entrada** en primera pasada está esbozado: cuentas, entradas/postings, saldo-como-derivado, comisiones-como-entradas.
- [ ]  Has nombrado al menos un riesgo de concurrencia/consistencia y tu protección prevista.
- [ ]  `DECISIONS.md` está iniciado, con tus supuestos escritos.
- [ ]  El dinero se concibe como unidades menores enteras / decimal — nunca float — y anotas las diferencias de decimales por cadena.