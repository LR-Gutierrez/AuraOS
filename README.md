# AuraOS — SmartPark OS

**Sistema de gestión de estacionamiento local-first** diseñado para reemplazar cuadernos analógicos en estacionamientos comerciales de gran escala. Opera en terreno hostil (latencia, intermitencia de red) sin perder un solo registro.

---

## Índice

- [¿Por qué AuraOS?](#por-qué-auraos)
- [Stack Tecnológico](#stack-tecnológico)
- [Arquitectura](#arquitectura)
- [Walkthrough de la App](#walkthrough-de-la-app)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Desarrollo](#desarrollo)

---

## ¿Por qué AuraOS?

Los estacionamientos comerciales siguen usando libretas físicas para registrar ingresos y salidas. Esto genera:

- Fugas de capital (ingresos no reportados)
- Errores de cálculo manual de tarifas
- Nula trazabilidad ante reclamos por daños
- Dependencia del operador para recordar membresías vencidas

AuraOS resuelve esto con un **sistema blindado, local-first, con cámara, NFC, OCR y un motor de auditoría que no perdona un centavo**.

**Capacidad:** ~70 puestos (50 livianos + 15 camiones). Regla de maniobra: 1 camión = 3 livianos.

**Monetización:** Tarifa plana $2/día. Membresías premium $50/mes.

---

## Stack Tecnológico

### Lenguaje

| Decisión | Por qué |
|---|---|
| **100% Kotlin** | Null-safety, coroutines, sealed classes para estados, interoperabilidad total con Java. Cero archivos .java en el proyecto. |
| **minSdk 24 / targetSdk 36** | Android 7.0 (Nougat) es el piso real del parque actual de dispositivos ruggedizados de bajo costo. compileSdk 36 (Android 16) da acceso a las APIs más recientes. |

### UI

| Decisión | Por qué |
|---|---|
| **XML Views + ViewBinding** | El operador necesita respuesta táctil inmediata sin latencia de framework. Jetpack Compose impone overhead de composición que en dispositivos de gama baja se siente. ViewBinding da type-safety sin reflexión. |
| **Material Components** | Tema dual DayNight listo. Componentes auditados (BottomAppBar, CollapsingToolbar, ChipGroup, etc.) sin reinventar la rueda. |
| **Glassmorphism (Crystal/Midnight)** | Diferenciación de marca High-Ticket. El diseño inspirado en Apple HIG da una percepción de software premium. Los modos atmósfericos se switchean con `?attr/...` sin tocar layouts. |

### Arquitectura

| Decisión | Por qué |
|---|---|
| **MVVM con LiveData** | Patrón clásico de Android. LiveData es lifecycle-aware, no requiere manejo manual de suscripciones. ViewModels rotan con la configuración sin perder estado. |
| **ViewModelProvider + Factory** | Sin Hilt/Dagger. El proyecto es mediano y la inyección manual mantiene la trazabilidad explicita de cada dependencia sin graph de DI oculto. |
| **Intents entre Activities + TabNavigator** | Sin Jetpack Navigation Component. Cada pantalla es independiente y autocontenida. El TabNavigator casero con animaciones slide da control total sobre el backstack. |

### Persistencia y Red

| Decisión | Por qué |
|---|---|
| **Room** | ORM con verificación en compile-time de consultas SQL. Soporte nativo de coroutines (`.suspend`), migrations progresivas y tipo DAO limpio. |
| **Retrofit + OkHttp + Gson** | Estándar de facto para comunicación REST. OkHttp maneja timeouts, interceptors de logging y conexiones pooling. Gson convierte JSON plano sin esquemas. |
| **ChangeJournal (CDC propio)** | No hay SDK que haga field-level merge offline-first. Se construyó un sistema de Change Data Capture append-only que registra cada CREATE/UPDATE/DELETE. En sync, servidor y cliente hacen merge campo por campo (no Last-Writer-Wins total). |

### Hardware

| Decisión | Por qué |
|---|---|
| **NFC (Kiosk)** | Los miembros premium usan tarjetas NFC como llave de acceso. El teléfono del operador actúa como lector de tags. Si no hay red, la entrada se encola (NfcSyncManager) y se reenvía al reconectar. |
| **Cámara (5 fotos + OCR)** | Trazabilidad legal: cada ingreso captura placa + 4 ángulos de carrocería en calidad 4K. OCR reconoce la placa automáticamente desde la foto. |
| **Biométrico (Huella + Android Keystore)** | Login sin contraseñas. El par RSA se genera en Keystore hardware-backed. El servidor reta al dispositivo con un challenge firmado. Si el keystore se invalida (ej. se agregó una nueva huella), requiere re-inscripción. |

### Sincronización

| Decisión | Por qué |
|---|---|
| **Debounced auto-sync** | Cada mutación gatilla sync tras 800ms de inactividad (SyncCoordinator). Sin llamadas innecesarias si el operador está editando varios campos seguidos. |
| **Cola offline por tipo** | NfcSyncManager (entradas NFC sin red), ExitSyncManager (salidas offline), ChangeJournalSyncManager (CDCs pendientes). Cada uno retry independiente. |
| **ConnectivityObserver** | `registerDefaultNetworkCallback` evita falsos positivos de redes no predeterminadas. El banner de estado se muestra/oculta con VISIBLE/GONE. |

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     Activities (UI Layer)                    │
│  LoginActivity | HomeActivity | DashboardActivity            │
│  KioskActivity | ExitActivity | BranchListActivity           │
│  MembershipListActivity | AnalyticsActivity | AuditActivity  │
│  ProfileActivity | ...                                      │
│                    ↓ (ViewModelProvider + Factory)           │
├─────────────────────────────────────────────────────────────┤
│                   ViewModels (MVVM)                          │
│  LoginViewModel | BranchViewModel | MembershipViewModel      │
│  ExitViewModel | AuditViewModel | DashboardViewModel         │
│  HistoryViewModel                                            │
│                    ↓ (suspend + LiveData)                    │
├─────────────────────────────────────────────────────────────┤
│                  Repositories (Data Layer)                   │
│  LoginRepository | BranchRepository | MembershipRepository   │
│  VehicleEntryRepository | DashboardRepository                │
│       ↓ API call / cache write / CDC journal                │
├───────────────┬──────────────────────────┬──────────────────┤
│   Room (local)│  Retrofit (remote)       │  ChangeJournal   │
│   BranchDao   │  AuthApi / BranchApi     │  CDC append-only │
│   Membership  │  MembershipApi           │  recordCreate    │
│   VehicleEntry│  ParkingApi / Dashboard  │  recordUpdate    │
│   PendingNfc  │  SyncApi                 │  recordDelete    │
│   PendingExit │  ChangeJournalApi        │  field-level     │
│   Dashboard   │                          │  merge conflict  │
└───────────────┴──────────────────────────┴──────────────────┘
```

### Flujo de datos típico

1. Usuario crea una sucursal en `BranchFormActivity`
2. `BranchViewModel.createBranch()` → `BranchRepository.create()`
3. El repo llama a `POST /api/v1/branches` + `branchDao.insert()` + `changeJournal.recordCreate()`
4. Si hay red: API responde 200, todo ok
5. Si no hay red: `catch(IOException)`, el dato queda en Room + journal
6. `SyncCoordinator` detectó la mutación → espera 800ms → llama a `SyncManager.sync()`
7. `SyncManager` primero corre `ChangeJournalSyncManager` (push cambios locales, pull cambios remotos, field-level merge)
8. Luego corre el sync tradicional (full pull de branches/memberships/entries)

---

## Walkthrough de la App

### 1. Login / Auth

- **LoginActivity** extiende `AppCompatActivity` directamente (no tiene banner de conectividad).
- Soporta dos modos:
  - **Quick login** — email + contraseña guardados en `EncryptedSharedPreferences`
  - **Full login** — formulario completo para nuevos usuarios
- **BiometricPrompt**: Si el usuario ya se inscribió (RSA key pair en Android Keystore), al abrir la app se lanza el diálogo biométrico automáticamente. El challenge firmado se envía a `POST /api/v1/biometric-login`.
- **Offline fallback**: Si no hay red, `LocalAuthDataSource` verifica contra el hash almacenado.

### 2. Dashboard (`DashboardActivity`)

Pantalla principal post-login con:

- **Barra de ocupación** con % usado, tendencia, ingresos del día
- **Membresías activas** / total
- **Alertas críticas** (membresías por vencer, capacidad crítica)
- **Navegación rápida** a: Sucursales, Membresías, Salida, Kiosko, Auditoría
- **Bottom nav** con 4 tabs: Dashboard | Registrar | Analytics | Perfil

### 3. Home / Registrar Ingreso (`HomeActivity`)

Flujo principal de captura:

1. Seleccionar **sucursal** (dialog)
2. Seleccionar **tipo de vehículo**: Moto / Liviano / Pesado
3. Capturar **5 fotos**: Placa + Frente + Trasera + Izquierda + Derecha (cámara)
4. **OCR**: la foto de placa se envía a `POST /ocr/plate` y autocompleta el número
5. Seleccionar **membresía** (opcional — si aplica, se descuenta de la membresía)
6. Confirmar → `POST /api/v1/vehicle-entries` con multipart
7. El `VehicleEntryRepository` guarda en Room y registra CDC en ChangeJournal
8. Si la API falla por red, el entry queda en cola local

### 4. Kiosko (`KioskActivity` + `KioskEntryActivity`)

Modo rápido para entrada sin cámara. Dos caminos:

- **Ticket**: `KioskEntryActivity`, formulario ligero con captura de placa (foto + OCR) y confirmación
- **NFC**: Escanea tarjeta NFC del miembro, busca membresía por `cardUuid`, registra entrada con placa `"NFC"` y tipo por defecto `"light"`
  - Si no hay red, la entrada se encola en `PendingNfcEntryEntity` y `NfcSyncManager` la reenvía después

### 5. Salida (`ExitActivity`)

- Lista de ingresos activos de la sucursal seleccionada
- Búsqueda por placa
- Preview de fotos (dialog)
- Al seleccionar una entrada activa: muestra duración, calcula tarifa, confirma salida
- `PATCH /api/v1/vehicle-entries/{id}/exit` → Room + CDC (`recordUpdate` con campos `exitedAt`, `duration`, `fee`)

### 6. Sucursales (`BranchListActivity` + `BranchFormActivity`)

CRUD completo de sucursales:

- Lista con KPIs (capacidad, flota activa, ocupación)
- Crear/editar: nombre, dirección, capacidades por tipo (steppers), tarifas (diurna/nocturna), horarios (weekday/weekend), moneda
- Eliminar con confirmación (long-press)
- Favorita por defecto

### 7. Membresías (`MembershipListActivity` + `MembershipFormActivity`)

CRUD de membresías premium ($50/mes):

- Lista con tier, expiry, estado activo/inactivo
- Crear: nombre, tier, fecha fin, escaneo NFC opcional
- Registrar/desvincular tarjeta NFC
- Eliminar con confirmación

### 8. Analytics / Historial (`AnalyticsActivity`)

- Filtros: Hoy / Todo / Rango de fechas
- Búsqueda por placa
- Ordenar por tarifa o duración
- Lista paginada con fotos en preview
- Resumen: total ingresos, promedio duración, etc.

### 9. Auditoría (`AuditActivity`)

Motor inviolable de cierre de caja:

- Resumen del período: ingresos por entry, renovaciones de membresías
- Historial de cierres anteriores
- Ejecuta cierre contable separando ingresos diarios de membresías
- Reporte compartible

### 10. Perfil (`ProfileActivity`)

- Avatar con upload (cámara/gallery)
- Activar/desactivar biometric login
- Switch de tema: Crystal ↔ Midnight
- Logout

---

## Estructura del Proyecto

```
app/src/main/java/com/beta/auraos/
├── BaseActivity.kt              # Connectivity banner + ChangeJournal wiring
├── HomeActivity.kt              # Entry registration (5 photos + OCR)
├── KioskActivity.kt             # Kiosk mode (NFC + ticket entry)
├── KioskEntryActivity.kt        # Lightweight kiosk entry form
├── DashboardActivity.kt         # Main dashboard after login
├── AnalyticsActivity.kt         # History & analytics
├── ProfileActivity.kt           # User profile & settings
├── ui/
│   ├── login/                   # LoginActivity + ViewModel + FormState
│   ├── exit/                    # ExitActivity + ViewModel
│   ├── audit/                   # AuditActivity + ViewModel + AuditEngine
│   ├── branches/                # Branch list/form + ViewModel
│   ├── memberships/             # Membership list/form + ViewModel
│   ├── dashboard/               # DashboardViewModel
│   ├── history/                 # HistoryViewModel
│   └── widget/                  # ConnectivityBannerView
├── data/
│   ├── BranchRepository.kt      # Branch CRUD + Room + CDC journal
│   ├── MembershipRepository.kt   # Membership CRUD + card ops + CDC journal
│   ├── VehicleEntryRepository.kt # Entry CRUD + exit + analytics + CDC
│   ├── DashboardRepository.kt   # Dashboard fetch + local computation
│   ├── LoginRepository.kt       # Auth + biometric + session
│   ├── LoginDataSource.kt       # Remote auth calls
│   ├── Result.kt                # Sealed Success/Error
│   ├── model/                   # Data classes (Branch, Membership, etc.)
│   ├── remote/                  # Retrofit APIs (Auth, Branch, etc.)
│   ├── local/
│   │   ├── room/                # Room DB (entities, DAOs, migrations)
│   │   ├── ChangeJournal.kt     # CDC facade (recordCreate/Update/Delete)
│   │   ├── ChangeJournalSyncManager.kt  # Push/pull/merge CDC
│   │   ├── SyncCoordinator.kt   # Debounced auto-sync after mutations
│   │   ├── SyncManager.kt       # Full sync (journals + entities)
│   │   ├── ConnectivityObserver.kt  # Network state tracking
│   │   ├── NfcSyncManager.kt    # Offline NFC entry queue
│   │   ├── ExitSyncManager.kt   # Offline exit queue
│   │   ├── SessionManager.kt    # EncryptedSharedPreferences
│   │   ├── BiometricKeyManager.kt  # Android Keystore RSA
│   │   └── DashboardCalculator.kt  # Local analytics computation
│   └── audit/                   # AuditEngine, FeeCalculator, AuditRepository
└── util/                        # TabNavigator, DateUtils, PhotoUrlBuilder, etc.
```

---

## Desarrollo

### Requisitos

- Android Studio Ladybug (2024.2) o superior
- JDK 17+
- Gradle 9.4.1+
- Android SDK 36

### Build

```bash
git clone <repo-url>
cd AuraOS
./gradlew assembleDebug
```

### Configuración

La URL del backend se define en `data/remote/ApiClient.kt`:

```kotlin
private const val BASE_URL = "http://192.168.0.143:3000/"
```

Cambiar según entorno. El backend actual es Node.js con endpoints REST.

### Version Catalog

Todas las dependencias se gestionan centralizadamente en `gradle/libs.versions.toml`. Para agregar una nueva dependencia:

1. Agregar la versión en `[versions]`
2. Agregar el library coordinates en `[libraries]`
3. Referenciar en `app/build.gradle.kts` con `implementation(libs.nombre)`

### Migraciones Room

Las migrations son manuales. Cada cambio de esquema requiere:

1. Nueva constante `MIGRATION_X_Y` en `AppDatabase.kt`
2. Incrementar `version` en `@Database`
3. No usar `fallbackToDestructiveMigration()`
