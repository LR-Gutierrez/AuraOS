# ChangeJournal API — Especificación para Backend

## Visión General

El **ChangeJournal** es un sistema de registro inmutable (append-only) que captura cada operación de creación, modificación o eliminación de datos en el cliente. El backend debe implementar los mismos endpoints para que ambos lados puedan sincronizar cambios de forma bidireccional con resolución de conflictos a nivel de campo (**field-level merge**).

### Flujo de sincronización

```
Cliente                          Servidor
   │                                │
   ├── POST /api/v1/changes/push ──►│  Envía cambios locales no sincronizados
   │◄── PushChangesResponse         │  Confirma recepción
   │                                │
   ├── GET /api/v1/changes/pull ───►│  Pide cambios desde el último sync
   │◄── ChangesResponse             │  Recibe cambios de otros dispositivos
   │                                │
   └── aplica field-level merge ────┘
```

---

## 1. Endpoint: Push Changes

Sube los cambios locales del cliente al servidor.

```
POST /api/v1/changes/push
Authorization: Bearer <token>
Content-Type: application/json
```

### Request Body

```json
{
  "changes": [
    {
      "entityType": "branch",
      "entityId": "abc-123",
      "operation": "UPDATE",
      "data": {
        "id": "abc-123",
        "name": "Sucursal Centro",
        "address": "Av. Reforma 123",
        "motorcycleCapacity": 10,
        "lightVehicleCapacity": 50,
        "heavyVehicleCapacity": 15,
        "motorcycleRate": 1.0,
        "lightVehicleRate": 2.0,
        "heavyVehicleRate": 5.0,
        "motorcycleOvernightRate": 2.0,
        "lightVehicleOvernightRate": 4.0,
        "heavyVehicleOvernightRate": 10.0,
        "openTimeWeekday": "07:00",
        "closeTimeWeekday": "23:00",
        "openTimeWeekend": "09:00",
        "closeTimeWeekend": "22:00",
        "currency": "USD",
        "createdAt": "2025-01-01T00:00:00Z",
        "updatedAt": "2025-06-05T12:00:00Z",
        "favorite": false
      },
      "changedFields": {
        "address": "Av. Reforma 123",
        "lightVehicleRate": 2.0
      },
      "timestamp": 1749139200000,
      "deviceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    },
    {
      "entityType": "membership",
      "entityId": "mem-456",
      "operation": "CREATE",
      "data": {
        "id": "mem-456",
        "memberName": "Juan Pérez",
        "tier": "premium",
        "startDate": "2025-06-01",
        "endDate": "2025-07-01",
        "isActive": true,
        "branchId": "abc-123",
        "cardUuid": null,
        "createdAt": null,
        "updatedAt": null
      },
      "changedFields": null,
      "timestamp": 1749139201000,
      "deviceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  ]
}
```

### Response (Success — 200)

```json
{
  "success": true,
  "conflicts": [
    {
      "entityType": "branch",
      "entityId": "abc-123",
      "localTimestamp": 1749139200000,
      "remoteTimestamp": 1749139100000,
      "resolvedFields": {
        "address": "Av. Reforma 123"
      }
    }
  ]
}
```

### Campos del Change

| Campo | Tipo | Requerido | Descripción |
|---|---|---|---|
| `entityType` | `string` | Sí | Tipo de entidad: `"branch"`, `"membership"`, `"vehicle_entry"` |
| `entityId` | `string` | Sí | ID único de la entidad afectada |
| `operation` | `string` | Sí | `"CREATE"`, `"UPDATE"`, o `"DELETE"` |
| `data` | `object` | Sí | Snapshot COMPLETO de la entidad después del cambio (para DELETE, el último snapshot conocido) |
| `changedFields` | `object` o `null` | No | Mapa con SOLO los campos que cambiaron (sus valores nuevos). `null` para CREATE y DELETE. |
| `timestamp` | `number` | Sí | Epoch millis del momento en que ocurrió el cambio en el cliente |
| `deviceId` | `string` | Sí | UUID del dispositivo que originó el cambio |

### Lógica del servidor (push)

1. Por cada cambio recibido:
   - **CREATE**: Si la entidad ya existe (mismo `entityType` + `entityId`), comparar timestamps. El más reciente gana. Si no existe, crearla.
   - **UPDATE**: Aplicar **field-level merge** (ver sección 3). Si no hay conflictos, aplicar `data` completo.
   - **DELETE**: Si existe, eliminar la entidad (tombstone). Si no existe, ignorar.
2. Guardar el cambio en la tabla `change_journal` del servidor para que otros clientes puedan pulsearlo después.
3. Responder con los conflictos detectados (si los hay).

### Response (Error — 4xx/5xx)

```json
{
  "error": "mensaje de error"
}
```

---

## 2. Endpoint: Pull Changes

El cliente solicita los cambios que ocurrieron en el servidor desde un timestamp dado, excluyendo los que él mismo originó.

```
GET /api/v1/changes/pull?since=1749139000000&deviceId=a1b2c3d4-e5f6-7890-abcd-ef1234567890
Authorization: Bearer <token>
```

### Query Parameters

| Parámetro | Tipo | Requerido | Descripción |
|---|---|---|---|
| `since` | `number` | No | Timestamp (epoch millis) del último sync. Si se omite, devuelve todos los cambios disponibles. |
| `deviceId` | `string` | Sí | UUID del dispositivo que pregunta. El servidor NO debe devolver cambios originados por este dispositivo. |

### Response (200)

```json
{
  "changes": [
    {
      "id": 1,
      "entityType": "branch",
      "entityId": "def-789",
      "operation": "UPDATE",
      "data": {
        "id": "def-789",
        "name": "Sucursal Norte",
        "address": "Calle 45 #67",
        ...
      },
      "changedFields": {
        "name": "Sucursal Norte"
      },
      "timestamp": 1749139500000,
      "deviceId": "x9y8z7w6-v5u4-3210-abcd-ef1234567890"
    }
  ],
  "syncTimestamp": 1749139600000
}
```

### Campos del Response

| Campo | Tipo | Descripción |
|---|---|---|
| `changes` | `array` | Lista de cambios desde `since` (excluyendo los del `deviceId` solicitante) |
| `syncTimestamp` | `number` | Timestamp actual del servidor para que el cliente guarde como su último sync |

Cada `change` en el array tiene la misma estructura que los `ChangePayload` del push (incluyendo `id` interno del servidor).

---

## 3. Estrategia de Merge (Field-Level)

Tanto el servidor (en push) como el cliente (en pull) deben implementar la misma lógica de merge.

### Reglas

1. **CREATE sin conflicto**: Si la entidad no existe, se crea con `data`.
2. **CREATE con conflicto**: Si ya existe, se comparan los `timestamp`. El más reciente gana (LWW).
3. **UPDATE sin conflicto**: Si no hay cambios locales más recientes, se aplica `data` completo.
4. **UPDATE con conflicto**: Se comparan **campo por campo**:
   - Por cada campo en `changedFields` del cambio entrante:
     - Si el mismo campo NO fue modificado por el otro lado (o el cambio local es más viejo) → se aplica el valor entrante
     - Si el mismo campo fue modificado por el otro lado con un timestamp más reciente → se conserva el valor local (el más reciente gana)
   - Campos que SOLO existen en un lado se aplican sin conflicto
5. **DELETE**: Siempre gana sobre cualquier UPDATE/CREATE (tombstone). Si ambos lados eliminaron, es no-op.

### Ejemplo de merge

```
Estado inicial: Branch { name: "Centro", address: "Av. 1", rate: 2.0 }

Cliente (timestamp 100): UPDATE address → "Av. 2"
Servidor (timestamp 200): UPDATE rate → 3.0

Resultado del merge:
- address: gana cliente (el campo no tocado por servidor)
- rate: gana servidor (timestamp 200 > 100)
- name: se conserva "Centro" (no cambió en ningún lado)

→ Branch { name: "Centro", address: "Av. 2", rate: 3.0 }
```

---

## 4. Tabla en Base de Datos del Servidor

El servidor debe tener una tabla `change_journal` con esta estructura:

```sql
CREATE TABLE IF NOT EXISTS change_journal (
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     VARCHAR(255) NOT NULL,
    operation     VARCHAR(10) NOT NULL,   -- CREATE | UPDATE | DELETE
    data          JSONB NOT NULL,
    changed_fields JSONB,                  -- nullable, solo para UPDATE
    timestamp     BIGINT NOT NULL,
    device_id     VARCHAR(36) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_change_journal_entity ON change_journal (entity_type, entity_id);
CREATE INDEX idx_change_journal_timestamp ON change_journal (timestamp);
CREATE INDEX idx_change_journal_device ON change_journal (device_id);
CREATE INDEX idx_change_journal_synced ON change_journal (synced);

-- Para controlar qué cambios aplicar a las entidades principales
ALTER TABLE change_journal ADD COLUMN IF NOT EXISTS applied BOOLEAN DEFAULT FALSE;
ALTER TABLE change_journal ADD COLUMN IF NOT EXISTS synced BOOLEAN DEFAULT FALSE;
```

### Tabla de entidades

El servidor debe mantener sus propias tablas de negocio (`branches`, `memberships`, `vehicle_entries`) que se actualizan cuando se aplican los cambios del journal.

Cada entidad debe tener un campo `last_modified_at BIGINT` para comparar timestamps durante el merge.

---

## 5. Comportamiento del Servidor ante Push

Cuando el servidor recibe un push de cambios, debe:

1. Para cada `ChangePayload`:
   a. Buscar en `change_journal` si hay cambios más recientes para la misma entidad (mismo `entityType` + `entityId`) desde OTRO dispositivo (`deviceId != payload.deviceId`).
   b. Si no hay conflictos: aplicar el cambio, actualizar la entidad en la tabla de negocio, insertar en `change_journal`.
   c. Si hay conflictos: hacer field-level merge, aplicar el resultado, insertar en `change_journal` con el resultado del merge.
2. Identificar los conflictos y devolverlos en el response.
3. Responder con `{ success: true, conflicts: [...] }`.

---

## 6. Comportamiento del Servidor ante Pull

Cuando el servidor recibe un pull:

1. Obtener todos los cambios de `change_journal` donde:
   - `timestamp > since` (si `since` no es nulo)
   - `device_id != deviceId` (no devolver cambios del mismo dispositivo)
2. Ordenar por `timestamp ASC`
3. Responder con los cambios + `syncTimestamp` (el timestamp actual del servidor)

---

## 7. Entity Types y sus Fields

### `branch`

| Campo | Tipo | Ejemplo |
|---|---|---|
| `id` | string | `"abc-123"` |
| `name` | string | `"Sucursal Centro"` |
| `address` | string? | `"Av. Reforma 123"` |
| `motorcycleCapacity` | int | `10` |
| `lightVehicleCapacity` | int | `50` |
| `heavyVehicleCapacity` | int | `15` |
| `motorcycleRate` | double | `1.0` |
| `lightVehicleRate` | double | `2.0` |
| `heavyVehicleRate` | double | `5.0` |
| `motorcycleOvernightRate` | double | `2.0` |
| `lightVehicleOvernightRate` | double | `4.0` |
| `heavyVehicleOvernightRate` | double | `10.0` |
| `openTimeWeekday` | string | `"07:00"` |
| `closeTimeWeekday` | string | `"23:00"` |
| `openTimeWeekend` | string | `"09:00"` |
| `closeTimeWeekend` | string | `"22:00"` |
| `currency` | string | `"USD"` |
| `createdAt` | string | `"2025-01-01T00:00:00Z"` |
| `updatedAt` | string | `"2025-06-05T12:00:00Z"` |
| `favorite` | bool | `false` |

### `membership`

| Campo | Tipo | Ejemplo |
|---|---|---|
| `id` | string | `"mem-456"` |
| `memberName` | string | `"Juan Pérez"` |
| `tier` | string | `"premium"` |
| `startDate` | string | `"2025-06-01"` |
| `endDate` | string | `"2025-07-01"` |
| `isActive` | bool | `true` |
| `branchId` | string | `"abc-123"` |
| `cardUuid` | string? | `null` |
| `createdAt` | string? | `null` |
| `updatedAt` | string? | `null` |

### `vehicle_entry`

| Campo | Tipo | Ejemplo |
|---|---|---|
| `id` | string | `"veh-789"` |
| `plate` | string | `"ABC-1234"` |
| `vehicleType` | string | `"light"` |
| `isVip` | bool | `false` |
| `membershipId` | string? | `"mem-456"` |
| `branchId` | string | `"abc-123"` |
| `platePhotoUrl` | string? | `"https://..."` |
| `frontPhotoUrl` | string? | `"https://..."` |
| `rearPhotoUrl` | string? | `"https://..."` |
| `leftPhotoUrl` | string? | `"https://..."` |
| `rightPhotoUrl` | string? | `"https://..."` |
| `createdAt` | string | `"2025-06-05T10:00:00Z"` |
| `exitedAt` | string? | `null` |
| `duration` | string? | `null` |
| `fee` | double? | `null` |

---

## 8. Notas para el Backend

- **Timestamp drift**: Los timestamps son generados por el cliente (`System.currentTimeMillis()`). El servidor debe confiar en ellos para la resolución de conflictos. Si hay skew entre relojes, considerar usar un Hybrid Logical Clock (HLC) en el futuro.
- **Idempotencia**: El servidor debe manejar el mismo cambio múltiples veces (ej. si el cliente reenvía). Usar `deviceId + entityType + entityId + timestamp` como clave única para detectar duplicados.
- **Paginación**: Para el pull, si hay muchos cambios, considerar paginación (`limit` + `offset`) en una versión futura.
- **Limpieza**: Cambios viejos (>30 días) pueden ser eliminados o archivados.
- **Seguridad**: Validar que el `entityId` pertenece a un recurso al que el usuario autenticado tiene acceso.
