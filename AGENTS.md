# 🌌 AURA OS Context Document: SmartPark OS
> **Internal AI System Context & Architecture Guidelines**
> Target Framework: Native Android (Kotlin Only) | UI: XML View System (Material Components)

---

## 1. 🎭 Filosofía de Diseño & Identidad Visual (Doctrina AURA)
El agente de IA debe aplicar de forma estricta la filosofía **AURA**, basada en la **Transparencia y la Inmaterialidad**. El diseño está orientado a la venta de software de alto valor (High-Ticket) y se rige bajo las directrices del *Apple Human Interface Guidelines (HIG)* adaptadas a Android Nativo.

### Estética Core
- **Glassmorphism:** Uso mandatorio de fondos translúcidos, desenfoques y bordes grabados que imiten el cristal esmerilado.
- **Geometría:** Esquinas marcadamente redondeadas utilizando radios de `28dp` o superiores para emular curvas de tipo Superellipse.
- **Tipografía & Estructura:** Jerarquía visual sumamente limpia utilizando la familia San Francisco (`sans-serif` / `sans-serif-black` en Android).
- **Empatía con el Operador:** El software está pensado para sustituir cuadernos analógicos. La interfaz debe ser intuitiva, rápida, actuar como una aliada servicial y no generar fatiga visual.

### Modos Atmosféricos (Day/Night Theme-Driven)
El sistema conmuta dinámicamente sus recursos a través de atributos personalizados (`?attr/...`) declarados en `attrs.xml` para soportar dos configuraciones estéticas:

1. **Crystal Edition (Light Mode):**
   - Entorno aireado, blanco translúcido, fondos satinados claros y sombras de elevación suaves.
   - Fondo de tarjeta (`@drawable/bg_glass_card`): Sólido blanco con 15% de opacidad (`#26FFFFFF`) y un borde sutil oscuro con 10% de opacidad (`#1A000000`).
   - `android:windowLightStatusBar = true`

2. **Midnight Edition (Dark Mode):**
   - Entorno inmersivo con negros profundos y degradados atmosféricos oscuros.
   - Fondo de tarjeta (`@drawable/bg_glass_card_dark`): Sólido negro con 20% de opacidad (`#33000000`) y un borde sutil blanco reflejante con 15% de opacidad (`#26FFFFFF`).
   - `android:windowLightStatusBar = false`

---

## 2. 🏗️ Arquitectura Técnica de la Aplicación

Cualquier propuesta de código debe acatarse estrictamente al stack tecnológico actual del proyecto:

### Stack Tecnológico
- **Language:** 100% Kotlin. **PROHIBIDO** generar archivos fuentes o fragmentos en Java.
- **Build System:** Gradle con Kotlin DSL (`build.gradle.kts`) coordinado mediante Version Catalog (`libs.versions.toml`).
- **Android SDK:** `compileSdk 36` (Android 16), `targetSdk 36`, `minSdk 24` (Android 7.0).
- **Android Gradle Plugin (AGP):** `9.2.1`

### Arquitectura de UI & Lógica
- **UI Framework:** Sistema tradicional de **Views (XML Layouts, AppCompatActivity)**. **NO UTILIZAR** Jetpack Compose.
- **View Access:** Implementación estricta de **ViewBinding** para la manipulación e interacción segura con las vistas desde las actividades y fragmentos.
- **Patrón de Arquitectura:** **MVVM** clásico. Las actividades (ej. `LoginActivity`) actúan únicamente como observadoras pasivas de flujos `LiveData` expuestos por sus respectivos ViewModels (ej. `LoginViewModel`), obtenidos mediante `ViewModelProvider`.
- **System Integration:** Uso de `enableEdgeToEdge()` para una inmersión completa del layout detrás de las barras del sistema transparentes.

---

## 3. 💼 Contexto de Negocio & Lógica de SmartPark OS
El sistema está enfocado en resolver la sustitución de registros manuales en un estacionamiento comercial de gran escala, eliminando de raíz las fugas de capital y los errores de cálculo humanos.

- **Capacidad Total:** ~70 puestos disponibles en tiempo real.
- **Segmentación de Flota:** Capacidad física dividida dinámicamente entre 50 puestos para vehículos livianos y 15 puestos para camiones de gran escala (logística tipo Coca-Cola / Pepsi).
- **Mapeo Dinámico de Espacio:** Regla matemática estricta de maniobra: **1 Camión = 3 Puestos Livianos**. El inventario debe recalcular la disponibilidad física restante dinámicamente cuando ingresa un vehículo de gran escala.
- **Monetización:** - Tarifa Plana Diaria: `$2`.
  - Membresías Premium Mensuales: `$50`.

### Requerimientos Funcionales Core
1. **Módulo Asset Protection:** Flujo de registro blindado con motor OCR para lectura automática de placas y captura fotográfica en calidad 4K de la carrocería en los 4 ángulos (Trazabilidad Legal ante reclamos por daños).
2. **Dashboard de Inventario (Croquis):** Mapa visual e interactivo de ocupación espacial que refleje de manera fluida qué puestos están bloqueados por camiones o vehículos livianos.
3. **Motor de Auditoría:** Cierre de caja automatizado e inviolable que separe contablemente los ingresos diarios de las renovaciones de membresías, lanzando alertas proactivas al operador cuando expiren las cuentas de `$50`.
4. **Data Strategy:** Enfoque primordial **Local-First** para garantizar operatividad al 100% sin latencia para el operador del cuaderno, respaldado por un sistema asíncrono de sincronización en la nube (**Cloud Sync**).

---

## 4. 📁 Estructura del Proyecto

### Source Tree (`app/src/main/`)
```
java/com/beta/auraos/
├── data/
│   ├── LoginDataSource.kt        # Fuente de datos de login (mock/local)
│   ├── LoginRepository.kt        # Repositorio que orquesta el login
│   ├── Result.kt                 # Sellado genérico Success/Error
│   └── model/
│       └── LoggedInUser.kt       # Modelo de usuario autenticado
├── ui/login/
│   ├── LoginActivity.kt          # Activity con ViewBinding + LiveData observers
│   ├── LoginFormState.kt         # Estado del formulario (validaciones)
│   ├── LoginViewModel.kt         # ViewModel con lógica de login
│   ├── LoginViewModelFactory.kt  # Factory para el ViewModel
│   └── LoggedInUserView.kt       # Estado UI del usuario logueado
└── HomeActivity.kt               # Pantalla principal post-login
```

### Resource Tree (`app/src/main/res/`)
```
res/
├── drawable/                     # VectorDrawables XML + shapes
│   ├── bg_glass_card.xml         # Fondo glassmorphism light
│   ├── bg_glass_card_dark.xml    # Fondo glassmorphism dark
│   ├── bg_hollow_input.xml       # Input field shape hueco
│   ├── bg_primary_button.xml     # Botón primario sólido negro
│   ├── baseline_fingerprint_24.xml  # Icono biometrico
│   ├── ic_crystal_foreground.xml # Foreground del adaptive icon
│   ├── ic_launcher_*.xml         # Default launcher assets
│   ├── ic_logo_crystal_light.xml # Logo light (white bg, black lambda)
│   ├── ic_logo_midnight_dark.xml # Logo dark (black bg, white lambda)
│   └── login_background_*.jpg    # Backgrounds day/night
├── layout/                       # Layouts base (phone)
│   ├── activity_login.xml        # Login con glass card + formulario
│   ├── activity_home.xml         # Home con CollapsingToolbarLayout + FAB
│   └── content_scrolling.xml     # Scrolling content (NestedScrollView)
├── layout-w936dp/                # Landscape/tablet (>936dp width)
│   ├── activity_login.xml        # Login centrado en 840dp container
│   └── content_scrolling.xml     # ConstraintLayout wrapping NestedScrollView
├── layout-w1240dp/               # Large tablet (>1240dp width)
│   ├── activity_login.xml        # Login directo sin container extra
│   └── content_scrolling.xml     # NestedScrollView con margins 200dp
├── menu/
│   └── menu_home.xml             # Settings menu item
├── mipmap-{density}/             # WebP launcher icons (hdpi through xxxhdpi)
├── mipmap-anydpi-v26/            # Adaptive icon XML definitions
│   ├── ic_crystal.xml            # App icon (branded)
│   ├── ic_crystal_round.xml      # Round icon (branded)
│   ├── ic_launcher.xml           # Default launcher
│   └── ic_launcher_round.xml     # Default round launcher
├── values/
│   ├── attrs.xml                  # Atributos custom (appLogoCustom, etc.)
│   ├── colors.xml                 # Paleta de colores base
│   ├── dimens.xml                 # Dimensiones base
│   ├── strings.xml                # Todos los strings de la app
│   └── themes.xml                 # Temas Crystal Edition
├── values-land/                   # Landscape overrides
├── values-night/                  # Midnight Edition overrides
│   └── themes.xml                 # Temas oscuros + atributos custom
├── values-w600dp/                 # Small tablet overrides
├── values-w1240dp/                # Large tablet overrides
└── xml/
    ├── backup_rules.xml           # Backup config
    └── data_extraction_rules.xml  # Data extraction config
```

---

## 5. 🎨 Sistema de Atributos Personalizados (Custom Attributes)

Definidos en `res/values/attrs.xml` y utilizados via `?attr/...` para switching day/night:

| Atributo | Formato | Uso |
|---|---|---|
| `appLogoCustom` | `reference` | Drawable del logo (light/dark) |
| `loginBackgroundCustom` | `reference` | Drawable del fondo login |
| `glassCardBackgroundCustom` | `reference` | Drawable del vidrio |
| `textPrimaryCustom` | `color` | Color texto primario |
| `textSecondaryCustom` | `color` | Color texto secundario |
| `dividerColorCustom` | `color` | Color divisores |

**Regla:** Cada atributo se define en `attrs.xml` con un `format`, se asigna en ambos `values/themes.xml` y `values-night/themes.xml`, y se referencia en layouts con `?attr/nombre`.

---

## 6. 🧩 Convenciones de Código

- **Naming:** Lower camelCase para funciones/variables, PascalCase para clases. Resources en snake_case.
- **ViewBinding:** Siempre inflar con `ActivityLoginBinding.inflate(layoutInflater)` y usar `binding.root` como content view.
- **ViewModel:** Instanciar con `ViewModelProvider(this, viewModelFactory())`, nunca con constructor directo.
- **LiveData:** Actividades observan con `.observe(this@Activity) { }`, nunca con ciclo de vida manual.
- **Extension functions:** Colocar en el mismo archivo que su clase de uso (ej. `EditText.afterTextChanged` en `LoginActivity.kt`).
- **Login flow:** `LoginActivity` → valida credenciales → `LoginViewModel.login()` → `LoginRepository.login()` → `LoginDataSource.login()` → resultado vía `LiveData<LoginResult>`.
- **Edge-to-edge:** Siempre llamar `enableEdgeToEdge()` en `onCreate` antes de `setContentView`.
- **Navegación:** Intents explícitos entre activities (ej. `startActivity(Intent(this, HomeActivity::class.java))`).
- **Resource naming:** Prefijos `bg_` para backgrounds/shapes, `ic_` para iconos, `activity_`/`content_`/`fragment_` para layouts, `menu_` para menús.
