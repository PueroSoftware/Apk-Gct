# GeoCellTrack - Aplicación de Monitoreo y Rastreo para Android

## 1. Resumen del Proyecto

GeoCellTrack es una aplicación Android robusta diseñada para el monitoreo y rastreo de dispositivos en tiempo real. La solución está construida con una arquitectura profesional, enfocada en la eficiencia, la resiliencia y la seguridad, ideal para la gestión de agentes en campo o el seguimiento de activos.

La aplicación recolecta un amplio espectro de metadatos del dispositivo, los envía a una infraestructura en la nube basada en Firebase y cuenta con una capa de inteligencia artificial (vía Groq) para el análisis de anomalías.

---

## 2. Arquitectura y Tecnologías

El proyecto está estructurado en capas bien definidas para garantizar la mantenibilidad y escalabilidad.

### Capa de Presentación (UI)
- **MainActivity:** Gestiona la autenticación de usuarios mediante **Email/Contraseña**, con una lógica que se adapta al tipo de dispositivo (teléfono vs. tablet).
- **PowerActivity:** Actúa como una pantalla de "pre-vuelo", encargada de solicitar los permisos críticos (`Ubicación`, `Teléfono`, `Actividad`) antes de iniciar el rastreo.
- **RunningActivity:** Es el centro de monitoreo en tiempo real. Muestra datos vivos del servicio (como el cronómetro) y los análisis generados por la IA.

### Capa de Lógica y Servicios (Background)
- **TrackerService:** Es el corazón de la aplicación. Un `Foreground Service` "blindado" que asegura la recolección continua de datos.
- **BootReceiver:** Garantiza la persistencia del rastreo, reiniciando el `TrackerService` automáticamente cuando el dispositivo se enciende.
- **DetectedActivityReceiver:** Recibe actualizaciones del estado del dispositivo (quieto, en movimiento) para optimizar el consumo de batería.

### Tecnologías Clave
- **Lenguaje:** Java
- **UI:** AndroidX (AppCompat, Material Components), ViewBinding
- **Base de Datos (Backend):**
    - **Firebase Realtime Database:** Para almacenar los datos de rastreo (series temporales).
    - **Firestore:** Para almacenar los perfiles de usuario.
- **Autenticación:** Firebase Authentication (Email/Contraseña).
- **Red:** OkHttp (para la comunicación con Groq).
- **Inteligencia Artificial:** Groq Cloud con el modelo Llama 3.

---

## 3. Características Destacadas

- **Servicio de Rastreo Persistente:** Gracias al `Foreground Service` y `WakeLock`, el rastreo sobrevive a los modos de ahorro de energía y al cierre de la app.
- **Reinicio Automático:** El `BootReceiver` asegura que el monitoreo se reanude tras un reinicio del dispositivo.
- **Optimización de Batería:** El `ActivityRecognitionClient` ajusta dinámicamente la frecuencia de rastreo, consumiendo menos batería cuando el dispositivo está quieto.
- **Manejo de Datos Offline ("Caja Negra"):** Si la app pierde la conexión a Internet, los datos no se pierden. Se guardan localmente en `SharedPreferences` y se reenvían automáticamente cuando la conexión se restablece.
- **Documentación UML:** El proyecto incluye diagramas de arquitectura y de flujo (`.puml`) para una fácil comprensión del sistema.

---

## 4. Próximos Pasos (Roadmap)

- **Análisis IA Avanzado:** Expandir la integración con Groq para detectar patrones más complejos y generar alertas proactivas.
- **Geocercas (Geofencing):** Implementar la capacidad de definir zonas geográficas y recibir notificaciones cuando un dispositivo entre o salga de ellas.
- **Interfaz Web de Monitoreo:** Desarrollar un dashboard web que consuma los datos de Firebase para visualizar la ubicación de todos los agentes en un mapa en tiempo real.
