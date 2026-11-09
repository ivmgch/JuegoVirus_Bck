# Backend: Simulación Viral por Cercanía 🦠⚡

Backend de alta concurrencia y baja latencia para el **Juego de Simulación Viral por Cercanía en Campus Universitario**. Este servicio actúa como la **fuente de la verdad** centralizada, procesando la telemetría de proximidad física emitida por dispositivos móviles mediante Bluetooth Low Energy (BLE) y resolviendo en tiempo real el modelo matemático de crecimiento logístico continuo.

---

## 📐 Modelo Matemático del Sistema

El núcleo del backend implementa la solución analítica de la ecuación diferencial logística de Verhulst:

$$\frac{dP}{dt} = r P \left( 1 - \frac{P}{k} \right) \quad \Longrightarrow \quad P(t) = \frac{k}{A \cdot e^{-rt} + 1}$$

### Variables Gestionadas en el Servidor:
* **$k$ (Capacidad de carga poblacional):** Límite asintótico de contagio. Se calcula dinámicamente mediante una ventana deslizante de 2 minutos sobre los *Heartbeats* pasivos de los dispositivos conectados almacenados en **Redis (Sorted Sets)**:
  $$k = |\{ \text{jugadores con } t_{\text{heartbeat}} \ge t_{\text{actual}} - 120s \}|$$
* **$P(0)$ (Condición inicial):** Cantidad de "pacientes cero" asignados al iniciar la partida.
* **$A$ (Constante de integración):** Evaluada en $t = 0$:
  $$A = \frac{k - P(0)}{P(0)}$$
  Si $k$ varía bruscamente por la entrada o salida de estudiantes, el servidor recalcula $A$ manteniendo continuo el número actual de infectados para evitar saltos discretos en la curva.
* **$r$ (Tasa de crecimiento intrínseca):** Modulada en tiempo real según la densidad de interacciones BLE reportadas en la ventana de los últimos 60 segundos:
  $$r = r_{\text{base}} + \Delta r \cdot \left(\frac{\text{Interacciones BLE}}{\text{Interacciones de Referencia}}\right)$$
* **Punto de Inflexión ($k/2$):** Momento donde la aceleración del contagio es máxima ($\frac{d^2P}{dt^2} = 0$). Al alcanzarse, el worker en segundo plano dispara una alerta crítica multicast vía **Firebase Cloud Messaging (FCM)**.

---

## 🏛️ Arquitectura del Sistema

```
                  ┌──────────────────────────────────────────────┐
                  │          Android App (Jetpack Compose)       │
                  │   BLE Central / Peripheral + Vico Charts     │
                  └──────────────┬───────────────────────────────┘
                                 │
                 HTTP REST       │       WebSockets (STOMP)
             (Sesión / Join)     │   (Heartbeats / Reportes / Tablero)
                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT 3.3.5 / JAVA 21 LTS                    │
│                                                                        │
│  ┌───────────────────────┐             ┌─────────────────────────────┐ │
│  │   SessionController   │             │   GameWebSocketController   │ │
│  └───────────┬───────────┘             └──────────────┬──────────────┘ │
│              │                                        │                │
│              ▼                                        ▼                │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              InfectionService & MathService                      │  │
│  └──────────────────┬───────────────────────────────┬───────────────┘  │
│                     │                               │                  │
│                     ▼                               ▼                  │
│       ┌───────────────────────────┐   ┌───────────────────────────┐    │
│       │   PostgreSQL / H2 (JPA)   │   │   Redis (Active Players)  │    │
│       │  Partidas, Nodos, Audit   │   │  ZSET Sliding Window: k, r │    │
│       └───────────────────────────┘   └───────────────────────────┘    │
│                                                     │                  │
│  ┌────────────────────────────────────────┐         │                  │
│  │       GameSimulationScheduler          │◄────────┘                  │
│  │  - Broadcast BOARD_UPDATE (cada 5s)    │                            │
│  │  - Chequeo de Inflexión FCM (k/2)      │──► [ Firebase Admin FCM ]  │
│  └────────────────────────────────────────┘                            │
└────────────────────────────────────────────────────────────────────────┘
```

### Arquitectura Dual Resiliente:
1. **Modo Desarrollo Zero-Friction (Por Defecto):**
   * Base de datos en memoria **H2** (en modo de compatibilidad PostgreSQL).
   * Repositorio en memoria concurrente (`ConcurrentHashMap` + `ConcurrentLinkedQueue`) en caso de que Redis no esté iniciado.
   * Permite clonar y ejecutar el backend inmediatamente con cero dependencias instaladas.
2. **Modo Producción / Contenedores:**
   * **PostgreSQL 16** para persistencia transaccional ACID de auditoría y sesiones.
   * **Redis 7** nativo con estructuras `ZSET` para expiración automática por tiempo Unix epoch ($O(\log N)$).
   * Orquestado con `docker-compose.yml`.

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** Java 21 LTS (con soporte para Virtual Threads de Project Loom activado).
* **Framework:** Spring Boot 3.3.5.
* **Persistencia:** Spring Data JPA, Hibernate ORM, PostgreSQL Driver, H2 Database.
* **Caché en Memoria:** Spring Data Redis, Lettuce Client.
* **Tiempo Real:** Spring WebSocket con STOMP Broker integrado.
* **Notificaciones Push:** Firebase Admin SDK (FCM).
* **Testing:** JUnit 5, AssertJ, Spring Boot Test.
* **Herramienta de Construcción:** Apache Maven.

---

## 📡 Contratos de Red (API REST & WebSockets)

### 1. API RESTful (HTTP)

#### Crear Partida
* **Método:** `POST /api/v1/sessions`
* **Request:**
  ```json
  {
    "sessionCode": "CAMPUS-CENTRAL",
    "initialP0": 2
  }
  ```
* **Response (`201 Created` o `200 OK` si ya existe):**
  ```json
  {
    "id": 1,
    "sessionCode": "CAMPUS-CENTRAL",
    "status": "ACTIVE",
    "initialInfectedP0": 2,
    "carryingCapacityK": 4.0,
    "integrationConstantA": 1.0
  }
  ```

#### Unirse a Partida (Registro de Hardware)
* **Método:** `POST /api/v1/sessions/{id}/join`
* **Request:**
  ```json
  {
    "deviceMacHash": "a1b2c3d4e5f67890abcdef...",
    "fcmToken": "fcm_device_token_opcional"
  }
  ```
* **Response (`200 OK`):**
  ```json
  {
    "playerUuid": "470ab5e9-3373-4047-b387-27d9c05d0def",
    "sessionId": 1,
    "assignedStatus": "PATIENT_ZERO",
    "joinedAt": "2026-11-09T03:19:31.720Z"
  }
  ```

#### Health Check
* **Método:** `GET /api/v1/sessions/health`
* **Response (`200 OK`):**
  ```json
  {
    "status": "UP",
    "service": "viral-simulation-backend",
    "timestamp": "2026-11-09T03:20:51.465Z"
  }
  ```

---

### 2. Protocolo WebSockets (STOMP)

* **Endpoints de Conexión:**
  * Nativo Android / OkHttp: `ws://<HOST>:8080/ws-viral-native`
  * Navegador / SockJS: `http://<HOST>:8080/ws-viral`

#### Canales de Entrada (Cliente ➔ Servidor)

| Destino | Frecuencia | Descripción | Payload de Ejemplo |
| :--- | :--- | :--- | :--- |
| `/app/telemetry/heartbeat` | Cada 30 s | Mantiene viva la presencia en $k$ y reporta densidad BLE | `{"sessionId": 1, "playerUuid": "...", "bleNearbyCount": 3}` |
| `/app/exposure/report` | Por evento | Reporte de exposición a infectado (>15s a <2m) | `{"sessionId": 1, "healthyPlayerUuid": "...", "infectedBeaconUuid": "...", "exposureDurationSeconds": 16, "estimatedDistanceMeters": 1.2}` |

#### Canales de Salida (Servidor ➔ Clientes)

* **Tablero de Simulación (`Broadcast` cada 5 s):**
  * Tópico: `/topic/board/{sessionId}`
  * Consumido por el cliente Android para alimentar la gráfica de Vico y el tablero:
    ```json
    {
      "sessionId": 1,
      "k": 45.0,
      "a": 8.0,
      "r": 0.052,
      "totalInfected": 5,
      "totalHealthy": 40,
      "theoreticalInfectedPt": 5.23,
      "timestampMs": 1794194380000
    }
    ```

* **Alerta Unicast de Infección Confirmada:**
  * Cola Unicast: `/user/queue/state`
  * Despachado al jugador recién contagiado para activar feedback visual (glitch carmesí) y háptico (taquicardia):
    ```json
    {
      "playerUuid": "470ab5e9-3373-4047-b387-27d9c05d0def",
      "newStatus": "INFECTED",
      "infectedByBeaconUuid": "uuid-del-vector-infectante",
      "hapticFeedbackPattern": "TACHYCARDIA_FAST",
      "timestamp": "2026-11-09T03:21:00Z"
    }
    ```

---

## 🚀 Guía de Instalación y Ejecución

### Prerrequisitos:
* **Java 21 JDK** instalado y en el `PATH`.
* **Maven 3.9+** (o el wrapper incluido).
* **Docker & Docker Compose** (opcional, para PostgreSQL y Redis persistentes).

### Opción 1: Ejecución Inmediata (Zero Configuration)
El proyecto está configurado con H2 y fallback en memoria activado por defecto:
```bash
git clone https://github.com/ivmgch/JuegoVirus_Bck.git
cd JuegoVirus_Bck
mvn spring-boot:run
```
El servidor quedará escuchando en `http://localhost:8080` (o `http://<TU_IP_LOCAL>:8080`).

Consola de Base de Datos H2 Web:
* URL: `http://localhost:8080/h2-console`
* JDBC URL: `jdbc:h2:mem:viral_db`
* Usuario: `sa`
* Contraseña: *(vacía)*

### Opción 2: Ejecución con Docker Compose (PostgreSQL + Redis)
1. Iniciar los contenedores:
   ```bash
   docker compose up -d
   ```
2. Ejecutar la aplicación con el perfil de Docker:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=docker
   ```

### Ejecución de Pruebas Unitarias:
```bash
mvn test
```
Incluye pruebas matemáticas rigurosas en `MathServiceTest.java`:
* Solución continua de $P(t)$ con $t=0$, $t=10$, $t=\infty$.
* Recálculo suave de la constante de integración $A$.
* Modulación de $r$ según densidad BLE.
* Detección del punto de inflexión $k/2$.

---

## 📱 Conexión con la Aplicación Android

La aplicación móvil Android (`Virus`) se conecta a través de Retrofit y OkHttp WebSocket. 
* Si se ejecuta en **Emulador Android**: Utilizar la IP local de la máquina anfitriona (ejemplo: `192.168.1.18:8080`) o ejecutar:
  ```bash
  adb reverse tcp:8080 tcp:8080
  ```
* Si se ejecuta en un **Dispositivo Físico**: Asegurarse de que el teléfono y el PC anfitrión estén en la misma red Wi-Fi y configurar la IP local en el panel integrado de la app.

---

## 📄 Licencia y Autores
Desarrollado para la simulación distribuida de modelos epidemiológicos en tiempo real utilizando IoT y conectividad móvil de proximidad física BLE.
* **Autor:** ivmgch (Universidad de Cundinamarca)
