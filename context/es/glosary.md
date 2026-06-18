# Candidato · Glosario (enviar con el Día 1)

> El vocabulario compartido para el desafío. Estos son los términos que los briefs asumen que conoces. Cuando algo aquí sea ambiguo, toma una decisión y escríbela en `DECISIONS.md` — ese juicio es parte de lo que evaluamos.
>

## Cuentas y saldos

- **Cuenta (Account)** — Una cuenta denominada en USD que pertenece a un **Cliente** o un **Sub-Cliente**. Contiene saldos e instrucciones de entrada (a dónde puede llegar el dinero). El dinero siempre se registra en USD en los libros, independientemente del riel por el que llegó.
- **Cliente / Sub-Cliente (Client / Sub-Client)** — Un Cliente es un cliente de Kira (por ejemplo, Northwind Coffee Co.). Un Sub-Cliente es una cuenta *bajo* un Cliente — los clientes de Kira sirven a sus propios clientes, por lo que un Cliente puede tener muchas cuentas Sub-Cliente.
- **Cuenta ómnibus (Omnibus account)** — La cuenta de nivel superior del Cliente que agrega fondos; los saldos individuales de Sub-Clientes se registran dentro de ella. Los fondos se agrupan en el banco pero se mantienen distintos en tu libro contable.
- **Saldo (derivado) (Balance — derived)** — Un saldo **nunca es un número almacenado y mutado**. Se *deriva* sumando las entradas del libro contable para una cuenta. El libro es la fuente de verdad; el saldo es una consulta.
- **Pendiente vs. Disponible (Pending vs. Available)** — Los fondos *pendientes* se han visto pero aún no son utilizables (por ejemplo, un depósito esperando confirmaciones en cadena). Los fondos *disponibles* se pueden gastar. Un pago solo puede tomar del saldo **disponible**. Mantenerlos distintos es obligatorio.

## Movimiento de dinero

- **Transferencia (Transfer)** — Dinero moviéndose dentro o fuera de una cuenta. Definida por una **dirección** (`inbound` / `outbound` / `internal`) y un **tipo** (`fiat` o `crypto`).
- **Inbound / Outbound / Internal** — *Inbound*: dinero que llega (un depósito). *Outbound*: dinero que sale (un pago). *Internal*: un movimiento entre dos cuentas de Kira que nunca sale de la plataforma.
- **Riel fiat (Fiat rail)** — Una red tradicional de movimiento de dinero: **ACH**, **Wire**, **SWIFT**, **FedNow**. Cada uno liquida a una velocidad diferente y tiene distintos modos de fallo. *En este desafío los rieles fiat están simulados detrás de una abstracción de proveedor.*
- **Pata cripto (Crypto leg)** — El lado en cadena de una transferencia, usando **stablecoins** (**USDC**, **USDT**) en **Solana**, **Polygon** o **Tron**. *En este desafío la pata cripto es real, en testnet (Solana devnet / Polygon Amoy).*
- **Stablecoin** — Un token vinculado al USD (USDC / USDT). Nota: el mismo token tiene **diferente precisión decimal por cadena** — nunca lo asumas.
- **Contraparte (Counterparty)** — Quien está del otro lado de una transferencia: el emisor externo de un depósito entrante, o el proveedor que recibe un pago saliente.

## Rampas y ruteo

- **Rampa (Ramp)** — Conversión entre fiat y cripto. **Off-ramp**: stablecoin entra → USD acreditado a la cuenta (aplicar comisiones). **On-ramp**: USD → stablecoin enviado hacia fuera.
- **Ruta (Route)** — Una regla permanente de la forma *"cuando llega X, envía automáticamente Y."* Las rutas son lo que hace del motor un motor de *orquestación*: un depósito entrante puede disparar automáticamente uno o más pagos salientes.
- **El flujo Northwind (The Northwind flow)** — El escenario de referencia que debes soportar de extremo a extremo: una contraparte envía **5.000 USDC en Solana** → detectado en cadena → confirmaciones → **off-ramp** (comisiones aplicadas, USD acreditado) → una **ruta se dispara** → paga a un proveedor **$4.200 vía ACH** + envía **600 USDT en Polygon** → validado, idempotente, reconciliado al cierre del día.

## Comisiones

- **Comisión (Fee)** — Cada comisión se **detalla** como su propia entrada en el libro contable — nunca se pliega silenciosamente en un monto. Tres componentes:
    - **Comisión de plataforma (Platform fee)** — un porcentaje por volumen.
    - **Costo fijo de paso (Fixed pass-through)** — un costo fijo por transacción que se traspasa.
    - **Markup del cliente (Client markup)** *(opcional)* — un margen extra que el Cliente agrega encima.

## Conceptos del libro contable

- **Doble entrada (Double-entry)** — Cada movimiento se registra como entradas equilibradas (débitos = créditos). El dinero nunca se crea ni se destruye en los libros — solo se mueve entre cuentas.
- **Append-only (Solo adición)** — Las entradas se escriben una vez y nunca se actualizan ni se eliminan. Las correcciones son *nuevas* entradas compensatorias. Esto te da un historial completo y auditable.
- **Entrada / Posting** — Una línea única en el libro: un monto, una cuenta, una dirección y a qué se refiere. Los saldos y las comisiones son todos solo entradas.
- **Reconciliación (Reconciliation)** — La verificación al cierre del día de que tu libro coincide con el mundo exterior (un extracto bancario / verdad en cadena). Dado que el libro es append-only, la reconciliación es una **consulta**, no un proceso manual.
- **Los dos tipos de discrepancia** — *Liquidado-sin-entrada (Settled-with-no-entry)*: el riel/cadena movió dinero pero tu libro no tiene registro. *Entrada-nunca-confirmada (Entry-never-confirmed)*: tu libro registró un movimiento que nunca se liquidó realmente. Un buen trabajo de reconciliación detecta **ambos**.

## Web3 / en cadena

- **Confirmación / umbral de confirmación (Confirmation / confirmation threshold)** — El número de bloques que deben construirse encima de una transacción antes de tratarla como final. Un depósito **no es dinero hasta que se confirma** — acredita solo el saldo *disponible* una vez que se alcanza el umbral.
- **Reorganización (Reorg)** — Cuando una cadena descarta bloques minados recientemente y los reconstruye. Una transacción que viste puede desaparecer — que es exactamente por qué importan las confirmaciones.
- **Decimales por cadena / unidades menores (Per-chain decimals / minor units)** — El número de decimales que usa un token, que difiere por cadena. Siempre almacena dinero como **unidades menores enteras** (o decimales de alta precisión) — nunca un `float`.
- **Testnet** — Una cadena de bloques que no es de producción para pruebas (Solana **devnet**, Polygon **Amoy**) donde los tokens no tienen valor real. La pata cripto del desafío corre aquí.
- **tx hash** — El identificador único de una transacción en cadena. Una clave de idempotencia natural para depósitos cripto entrantes.

## Confiabilidad e integración

- **Idempotencia (Idempotency)** — Realizar la misma operación más de una vez tiene el mismo efecto que hacerla una vez. Una solicitud reintentada o un webhook entregado dos veces nunca debe **mover dinero dos veces**.
- **Clave de idempotencia (Idempotency key)** — El identificador usado para deduplicar una operación (por ejemplo, un `tx hash` entrante, un header `Idempotency-Key` saliente). Define la tuya con precisión.
- **Ventana de crash (Crash window)** — La brecha peligrosa entre escribir en tu libro y llamar al proveedor externo (o viceversa). Si el proceso muere en el medio, debes poder recuperarte a un resultado correcto, exactamente una vez.
- **Abstracción de proveedor (Vendor / provider abstraction)** — La interfaz que oculta un banco/riel específico detrás de un puerto común, para que agregar un 3er proveedor sea un **cambio de configuración**, no una reescritura. El desafío incluye **dos proveedores fiat simulados con formas deliberadamente distintas** detrás de una abstracción.
- **Webhook** — Un callback HTTP entrante de un proveedor/cadena informando que algo ocurrió (por ejemplo, una liquidación). Trátelos como **no confiables**: verifica firmas, rechaza replays, y maneja duplicados y entregas fuera de orden.

> Cualquier cosa no definida aquí es una decisión deliberada para que tomes. Elige un valor por defecto sensato, regístralo en `DECISIONS.md`, y sigue adelante.
>
