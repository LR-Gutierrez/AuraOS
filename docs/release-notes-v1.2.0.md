## v1.2.0 — Crystal Edition

### 🎨 Glassmorphism Redesign & Login Rework

- Rediseño completo del login con glassmorphism (fondos translúcidos, bordes satinados)
- Selectores de color temáticos con chips animados
- Traducción completa al español (ES)
- Refactor de tarjetas a estilo glass en toda la app
- Nuevo sistema de atributos personalizados para theme switching day/night
- Optimizaciones de rendimiento y corrección de crashes en NFC, biometricos y sync offline

### 🏗️ Arquitectura

- Local-First con Room + ChangeJournal CDC
- MVVM con LiveData y ViewBinding
- Sincronización asíncrona con FCM y cola de reintentos offline
- Autenticación biométrica con Android Keystore + fallback offline

### Stack

- Kotlin 100% | XML Views | AGP 9.2.1
- minSdk 24 | targetSdk 36 (Android 16)
