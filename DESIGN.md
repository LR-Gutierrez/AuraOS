# DESIGN.md

## Propósito
Este archivo define el sistema visual base para **AURA OS** y sirve como contexto de diseño para cualquier IA, diseñador o desarrollador que deba generar interfaces, componentes, pantallas, estilos o código visual coherente con la marca.

La guía incluye dos temas oficiales:
- **Midnight Edition**: tema oscuro por defecto.
- **Crystal Edition**: tema claro.

La prioridad general del sistema es mantener una estética de **inmaterialidad**, **transparencia**, **profundidad óptica** y **legibilidad alta**, con una ejecución refinada de glassmorphism inspirada en interfaces premium de sistemas operativos modernos.

---

## Principios de diseño

### 1. Inmaterialidad
La interfaz no debe sentirse como una capa pesada sobre el contenido. Debe percibirse como una serie de lentes, paneles o superficies translúcidas que coexisten con el fondo.

### 2. Transparencia y refracción
Las superficies deben apoyarse en blur, bordes internos suaves, contraste tipográfico y variaciones de tono antes que en bloques sólidos pesados.

### 3. Jerarquía espacial
La profundidad debe construirse con separación visual, desenfoque, brillo de bordes, contraste y elevación óptica; no con sombras agresivas ni skeuomorfismo recargado.

### 4. Legibilidad primero
Aunque el sistema usa transparencia, el texto, estados interactivos, inputs y elementos críticos deben conservar contraste suficiente en todo momento.

### 5. Curvatura generosa
La identidad visual depende de radios amplios, cápsulas, superelipses y formas suaves. Evitar esquinas rígidas o geometría demasiado dura.

---

## Tema 1: Midnight Edition

### Resumen conceptual
**Midnight Edition** es el tema oscuro por defecto de AURA OS. Debe transmitir profundidad nocturna, tecnología premium y un glassmorphism sobrio, manteniendo el color del fondo visible sin que la interfaz pierda claridad.

### Tokens
```yaml
name: AURA OS
colors:
  surface: '#121317'
  surface-dim: '#121317'
  surface-bright: '#38393d'
  surface-container-lowest: '#0d0e12'
  surface-container-low: '#1a1b1f'
  surface-container: '#1e1f23'
  surface-container-high: '#292a2e'
  surface-container-highest: '#343539'
  on-surface: '#e3e2e7'
  on-surface-variant: '#c0c6d6'
  inverse-surface: '#e3e2e7'
  inverse-on-surface: '#2f3034'
  outline: '#8b91a0'
  outline-variant: '#414754'
  surface-tint: '#aac7ff'
  primary: '#aac7ff'
  on-primary: '#003064'
  primary-container: '#3e90ff'
  on-primary-container: '#002957'
  inverse-primary: '#005db8'
  secondary: '#c8c6c8'
  on-secondary: '#303032'
  secondary-container: '#474649'
  on-secondary-container: '#b6b4b7'
  tertiary: '#c6c6c7'
  on-tertiary: '#2f3131'
  tertiary-container: '#909191'
  on-tertiary-container: '#282a2a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#aac7ff'
  on-primary-fixed: '#001b3e'
  on-primary-fixed-variant: '#00468d'
  secondary-fixed: '#e4e2e4'
  secondary-fixed-dim: '#c8c6c8'
  on-secondary-fixed: '#1b1b1d'
  on-secondary-fixed-variant: '#474649'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c7'
  on-tertiary-fixed: '#1a1c1c'
  on-tertiary-fixed-variant: '#454747'
  background: '#121317'
  on-background: '#e3e2e7'
  surface-variant: '#343539'
typography:
  display-xl:
    fontFamily: Inter
    fontSize: 60px
    fontWeight: '700'
    lineHeight: 72px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 41px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
  body-lg:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 13px
    letterSpacing: 0.06em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 8px
  container-padding: 24px
  stack-gap: 16px
  grid-gutter: 20px
```

### Dirección visual
- Base oscura carbón con sensación nocturna y tecnológica.
- El fondo debe seguir respirando a través de capas translúcidas.
- El azul primario funciona como color de acción, foco y progreso.
- El contraste principal debe depender de texto claro sobre superficies oscuras translúcidas.

### Color y uso
- **Surface principal:** usar una base oscura similar a `#1C1C1E` con opacidad reducida cuando se construyan paneles glass.
- **Accent:** usar azul vibrante para botones primarios, controles activos, cursores, selección y progreso.
- **Texto:** contenido principal en alto contraste; contenido secundario en menor opacidad o usando `on-surface-variant`.

### Tipografía
- Usar **Inter** en toda la interfaz.
- Headers grandes con tracking ligeramente cerrado.
- Labels pequeñas pueden usar tracking más amplio para ritmo visual y claridad.
- Mantener jerarquía por tamaño, peso y contraste, no solo por color.

### Layout y espaciado
- Usar escala base de 8px.
- Padding interno recomendado para contenedores grandes: `24px` o más.
- Favorecer espacios negativos amplios para que el blur y la transparencia respiren.
- El layout debe sentirse fluido, moderno y estable.

### Elevación y profundidad
Para superficies glass del tema oscuro, aplicar estas reglas visuales siempre que sea posible:
- **Surface:** `#1C1C1E` al 60% de opacidad.
- **Atmosphere:** `backdrop-blur: 30px`.
- **Edge:** borde interno de `1px` con blanco al 15%.
- **Highlight:** sombra interna superior sutil, equivalente a `Y: 1`, `Blur: 0`, blanco al 10%.

### Formas
- Todos los contenedores principales y botones deben tener radios de **28px o más**.
- Preferir estética de cápsula, superelipse o esquinas continuas.
- Elementos secundarios no deben bajar de `12px` de radio.

### Componentes
#### Buttons
- Primarios: fondo accent sólido, texto de alto contraste, apariencia pulida tipo cápsula.
- Secundarios: usar especificación glass del tema Midnight, sin competir visualmente con CTA principal.

#### Cards y containers
- Cada card debe sentirse como un lente.
- Evitar anidar glass sobre glass repetidamente; usar separación tonal, espaciado o divisores sutiles.

#### Inputs
- Se ven como superficies glass retraídas.
- En foco, aumentar opacidad del borde.
- Cursor y selección siempre con el color accent.

#### Lists y menus
- Padding vertical sugerido: `16px` por item.
- Hover o seleccionado: subir ligeramente la luminosidad u opacidad del relleno; no cambiar drásticamente el color.

#### Checkboxes y radios
- Objetos glass miniatura.
- Activos: relleno accent.
- Inactivos: trazo claro o gris acorde al fondo.

---

## Tema 2: Crystal Edition

### Resumen conceptual
**Crystal Edition** es el tema claro del sistema. Debe sentirse limpio, luminoso, aireado y premium, con prioridad absoluta en claridad visual, sofisticación óptica y buen rendimiento en entornos brillantes.

### Tokens
```yaml
name: Crystal Edition
colors:
  surface: '#f9f9fb'
  surface-dim: '#d9dadc'
  surface-bright: '#f9f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f5'
  surface-container: '#eeeef0'
  surface-container-high: '#e8e8ea'
  surface-container-highest: '#e2e2e4'
  on-surface: '#1a1c1d'
  on-surface-variant: '#4c4546'
  inverse-surface: '#2f3132'
  inverse-on-surface: '#f0f0f2'
  outline: '#7e7576'
  outline-variant: '#cfc4c5'
  surface-tint: '#5e5e5e'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1b1b'
  on-primary-container: '#848484'
  inverse-primary: '#c6c6c6'
  secondary: '#5e5e61'
  on-secondary: '#ffffff'
  secondary-container: '#e1dfe2'
  on-secondary-container: '#626265'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#001a41'
  on-tertiary-container: '#2480ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c6'
  on-primary-fixed: '#1b1b1b'
  on-primary-fixed-variant: '#474747'
  secondary-fixed: '#e3e2e5'
  secondary-fixed-dim: '#c7c6c9'
  on-secondary-fixed: '#1b1b1e'
  on-secondary-fixed-variant: '#464649'
  tertiary-fixed: '#d8e2ff'
  tertiary-fixed-dim: '#adc6ff'
  on-tertiary-fixed: '#001a41'
  on-tertiary-fixed-variant: '#004493'
  background: '#f9f9fb'
  on-background: '#1a1c1d'
  surface-variant: '#e2e2e4'
typography:
  display-xl:
    fontFamily: Inter
    fontSize: 60px
    fontWeight: '700'
    lineHeight: 72px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '600'
    lineHeight: 41px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
  body-lg:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 13px
    letterSpacing: 0.06em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 8px
  container-padding: 32px
  element-gap: 16px
  section-margin: 48px
```

### Dirección visual
- La interfaz debe sentirse como cristal pulido y luz atmosférica.
- Debe minimizar el ruido cromático y priorizar blancos suaves, grises limpios y contraste negro preciso.
- Debe funcionar especialmente bien en pantallas brillantes y contextos exteriores.

### Color y uso
- **Surface base:** gris casi blanco, limpio y poco fatigante, similar a `#F5F5F7`.
- **Ink principal:** negro sólido para texto principal e iconografía crítica.
- **Accent:** azul sistémico sobrio, usado con moderación para estados interactivos.

### Tipografía
- Usar **Inter** como tipografía base.
- Jerarquía basada en peso y tamaño, no en saturación cromática.
- Títulos display con tracking cerrado y presencia firme.
- Labels y metadatos con tracking ligeramente amplio cuando sea necesario.

### Layout y espaciado
- La composición debe sentirse aireada.
- Escala base de `8px`.
- Contenedores de alto nivel con padding interno de `32px`.
- El espacio negativo es funcional y ayuda a separar lentes sin divisores pesados.

### Elevación y profundidad
Para superficies glass del tema claro, aplicar estas reglas visuales:
- **Base:** superficie gris suave.
- **Lens:** relleno blanco al 15% con `backdrop-blur: 25px`.
- **Highlight:** borde interno de `1.5px` con blanco al 30%.
- **Grounding shadow:** sombra muy suave equivalente a `Y: 4`, `Blur: 20`, negro al 4%.

### Formas
- Contenedores principales con radio de **28px** y estética de superelipse.
- Mantener curvatura amigable y pulida en toda la interfaz.
- Evitar intersecciones duras o esquinas afiladas.

### Componentes
#### Buttons
- **Glass Action:** relleno 15% y borde 30%; en hover, aumentar el fill a 25%.
- **High Contrast:** fondo negro sólido con texto blanco para acciones primarias.

#### Cards y containers
- Todo card debe usar `backdrop-blur: 25px`.
- Padding interno mínimo de `24px` para proteger el borde óptico.

#### Inputs
- Renderizar como glass hueco.
- El borde define claramente el área interactiva.
- En foco, incrementar opacidad del borde y profundizar el blur.

#### Lists
- Evitar divisores horizontales duros.
- Separar con espacio o tiles individuales cuando la densidad sea baja.
- En listas densas, usar hover blanco sutil alrededor de 10%.

#### Feedback elements
- Modales y overlays deben usar blur de fondo a pantalla completa, aprox. `10px`, para aislar el contexto activo.

---

## Reglas compartidas para IA
Estas reglas deben aplicarse siempre, independientemente del tema:

- No diseñar interfaces planas y pesadas si el sistema pide efecto lente o vidrio.
- No usar sombras oscuras intensas como recurso principal de jerarquía.
- No abusar de colores saturados fuera del accent y estados semánticos.
- No usar bordes duros, radios pequeños ni cajas visualmente rígidas.
- No llenar la pantalla con demasiados paneles; priorizar aire, separación y enfoque.
- Mantener consistencia entre tokens, componentes y narrativa visual.
- Si hay duda entre decoración y claridad, priorizar claridad.
- Las acciones primarias deben ser obvias, pero sin romper la elegancia general del sistema.

---

## Guía de implementación para generación de UI
Cuando una IA genere interfaces usando este archivo, debe seguir estas decisiones por defecto:

1. Usar **Midnight Edition** como tema por defecto, salvo que se solicite explícitamente modo claro.
2. Aplicar **Crystal Edition** cuando el contexto requiera interfaces luminosas, premium, limpias o tipo daylight.
3. Utilizar **Inter** en todos los niveles tipográficos.
4. Aplicar radios grandes de forma consistente, especialmente en cards, modales, botones e inputs.
5. Construir jerarquía con blur, stroke interno, espaciado, contraste y tamaño tipográfico.
6. Reservar el accent para foco, CTA, progreso, selección y estados activos.
7. Mantener layouts limpios, fluidos y con espacio negativo generoso.
8. Evitar patrones visuales demasiado enterprise, densos o utilitarios si rompen la identidad glass premium.

---

## Preferencias visuales resumidas
- Estilo general: glassmorphism premium, sobrio, refinado.
- Sensación: ligera, atmosférica, tecnológica, calmada.
- Bordes: internos, suaves, brillantes cuando corresponda.
- Sombras: mínimas y ópticas, no teatrales.
- Color: contenido, funcional y elegante.
- Tipografía: sistemática, clara, precisa.
- Forma: hiperrredondeada, orgánica, moderna.
- Espaciado: generoso y rítmico.

---

## Instrucción final para cualquier IA
Si vas a generar una interfaz, componente, landing, dashboard, app móvil, panel o sistema de diseño usando este archivo como contexto, debes respetar la identidad visual de **AURA OS** y escoger entre **Midnight Edition** y **Crystal Edition** sin mezclar decisiones incompatibles entre ambos temas. La salida debe sentirse premium, coherente, altamente legible y visualmente ligera.
