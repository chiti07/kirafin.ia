# Candidato · Resumen y Reglas (enviar con el Día 1)

> **5 días. ~2–3 horas/día. Diséñalo, constrúyelo, despliégalo en vivo, luego preséntalo.**
>

## 1. Quiénes somos

**Kira Fintech** ([kirafin.ai](http://kirafin.ai)) construye **infraestructura de Cuentas Virtuales con base en EE.UU.** Detrás de una única API limpia, coordinamos bancos asociados y movemos dinero a través de **rieles fiat** (ACH, Wire, SWIFT, FedNow) y **stablecoins** (USDC y USDT en Solana, Polygon y Tron).

La parte difícil no es mover el dinero. Es **mantener los libros perfectos mientras el dinero cruza rieles que liquidan a velocidades distintas, en unidades diferentes, con distintos modos de fallo** — y nunca perder, duplicar ni extraviar un solo centavo.

Términos que necesitarás (el resto está en el **Glosario** que compartiremos):

- **Cuenta** — Denominada en USD, para un Cliente o Sub-Cliente. Contiene saldos e instrucciones de entrada.
- **Transferencia** — Dinero que entra o sale de una cuenta. Dirección (`inbound`/`outbound`/`internal`) + tipo (`fiat` o `crypto`).
- **Ramp (rampa)** — **Off-ramp**: stablecoin entra → USD acreditado. **On-ramp**: USD → stablecoin sale.
- **Route (ruta)** — Una regla permanente: "cuando llega X, envía automáticamente Y."
- **Fees (comisiones)** — Comisión de plataforma (% por volumen) + costo fijo de paso + markup opcional del cliente, siempre desglosadas.

## 2. La misión

Construir el **motor de libro contable y orquestación** detrás de la API de Kira para nuestro cliente **Northwind Coffee Co.** — y hacerlo lo suficientemente real para abrir una URL y ver el dinero moverse.

El flujo que debes soportar, de extremo a extremo:

> Una contraparte envía **5.000 USDC en Solana**. Lo detectas en cadena, esperas confirmaciones, ejecutas el **off-ramp** (aplicas comisiones, acreditas USD). Una **ruta se dispara** y paga a un proveedor **$4.200 vía ACH**; un segundo pago envía **600 USDT en Polygon**. Cada paso es validado, idempotente y reconciliado al cierre del día.
>

Eres dueño de todo: **backend, UI full-stack, Web3 (testnet real) y despliegue.** No hay pistas opcionales. Estamos contratando a alguien que entrega una funcionalidad de extremo a extremo.

## 3. Las reglas que no se doblan

Todo lo demás es tu decisión. Estas no son negociables:

- **Sin saldos negativos. Jamás.** Ni siquiera bajo una avalancha de pagos concurrentes. Aplícalo donde no pueda haber condición de carrera.
- **Idempotencia en todo.** Una solicitud reintentada o un webhook entregado dos veces nunca debe mover dinero dos veces.
- **Sin punto flotante para dinero.** Solo unidades menores enteras o decimales de alta precisión. Un `float` en un saldo es una deducción automática.

> ⚠️ **Advertencia**
Tres cosas te van a picar si las ignoras: los **decimales por cadena** difieren, **un depósito no es dinero hasta que se confirma** (las reorganizaciones son reales), y **los crashes ocurren entre la escritura en el libro y la llamada al proveedor.** Diseña para los tres. *Los rieles fiat son simulados (danos dos proveedores con formas distintas detrás de una abstracción); la parte cripto es real en testnet.*
>

Te entregamos un glosario como documentación. Todo lo demás — el repositorio, el stack, la infraestructura, el `docker-compose` local — lo construyes desde cero. Las decisiones interesantes son tuyas — y tuyas para defender.

## 4. Cómo está estructurada la semana

Lanzamos **un brief por día** — no recibirás toda la semana el Día 1. Eso es deliberado: queremos ver cómo planificas con lo que tienes. Los tres entregables:

| Días | Entregable | La pregunta |
| --- | --- | --- |
| 1–2 | **Plan y Comprensión** | ¿Entiendes el problema antes de tocar código? *(No damos retroalimentación en esta etapa — a propósito.)* |
| 3–4 | **Build Técnico — desplegado en vivo** | ¿Funciona de extremo a extremo, en una URL que podamos abrir? |
| 5 | **Presentación Final** (llamada de 50 min) | ¿Puedes hacernos confiar en el sistema? |

## 5. Usar IA — lo fomentamos

Usa lo que te haga más efectivo, **incluyendo asistentes de IA** — así trabajamos aquí. Nos importa el juicio y el resultado, no si escribiste cada línea.

Si usas IA, **hazte dueño del resultado**: entiéndelo, valídalo contra tus propias pruebas y las reglas anteriores, y prepárate para explicar dónde ayudó, dónde se equivocó y dónde lo corregiste. Los candidatos más sólidos tratan la IA como un ingeniero junior brillante — la dirigen, la revisan y se responsabilizan de lo que se entrega. En la llamada final podríamos pedirte que la uses en vivo.

## 6. Cómo evaluamos (resumen)

Un agente automatizado primero verifica las barreras objetivas (sin floats, idempotencia, sin saldos negativos). El equipo luego juzga arquitectura, el documento de diseño, pruebas, dominio, infraestructura, seguridad, tu presentación final y el cambio en vivo. Medimos **corrección por unidad de complejidad** — no líneas de código. **El trabajo excepcional más allá del alcance requerido gana un bono sobre el 100%** (ver tus briefs de los Días 4/5 para qué cuenta).

## 7. Entrega

Un **repositorio Git** (leemos el historial de commits). Un `README.md` con configuración en un comando, cómo correr las pruebas y la **URL en vivo**. Un `.env.example` (nunca commits de secretos) y datos semilla que reproducen el flujo de Northwind. Mantén un `DESIGN.md` y un `DECISIONS.md` en curso (estilo ADR liviano) — leemos ambos.

> Si algo aquí no es claro, eso es parte de la prueba. Toma una decisión, escríbela en `DECISIONS.md`, y sigue adelante. Estamos emocionados de ver cómo piensas sobre el dinero. 🚀
>