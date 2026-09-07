# La interfaz gráfica

Una aplicación de una sola página hecha con React y Vite, servida por nginx. No
toca la base de datos: todo lo que muestra lo pide a la API con el token que le
dio Keycloak.

## Cómo se levanta

Con el resto del sistema, que es la forma normal:

```bash
docker compose up -d
```

Queda en **http://localhost:3000** (o en el `WEB_PORT` que tengas en el `.env`).

Para trabajar sobre la interfaz con recarga automática, sin reconstruir la imagen
en cada cambio:

```bash
cd frontend
npm install
npm run dev
```

Eso levanta el sitio en **http://localhost:5173** contra el mismo backend que ya
está corriendo en Docker. Los dos puertos están declarados en el realm de
Keycloak y en el CORS de la API, así que no hay que tocar nada más.

## Usuarios

Los tres del realm: `admin`, `organizer` y `viewer`. Las contraseñas están en
`keycloak/README.md` y no se repiten acá ni se muestran en la pantalla de
entrada: el enunciado pide explícitamente que la interfaz no exponga
contraseñas.

## Cómo está organizado

```
src/
├── api/        un archivo por dominio, igual que los paquetes del backend
├── auth/       la sesión, los permisos y el portero de las rutas
├── comun/      las piezas que usan todas las pantallas
├── paginas/    una carpeta por sección
├── App.jsx     el mapa de direcciones
└── estilos.css una sola hoja para todo
```

La división es la que pide el enunciado en "frontend responsibilities": páginas,
componentes reutilizables, capa de acceso a la API, manejo de la sesión y
protección de rutas.

| Carpeta | Qué resuelve |
|---|---|
| `api/cliente.js` | El único lugar que llama a `fetch`: pone el token, lo renueva y traduce los errores |
| `auth/AuthProvider.jsx` | Quién entró, con qué roles, y de dónde sale el token |
| `auth/permisos.js` | Qué rol puede hacer qué, para esconder los botones |
| `auth/RutaProtegida.jsx` | Distingue "no sé todavía", "no entraste" y "no te alcanza el rol" |
| `comun/Estados.jsx` | Cargando, vacío y error: los tres estados de cualquier pantalla |
| `comun/Formulario.jsx` | Campos con etiqueta, error al pie y accesibles con el teclado |
| `comun/useRecurso.js` | Pedir datos sin dejar indicadores de carga colgados |

## Las pantallas

| Ruta | Pantalla | Quién entra |
|---|---|---|
| `/entrar` | Entrada (manda a Keycloak) | cualquiera |
| `/` | Panel con el resumen de la liga | autenticados |
| `/competidores` | Listado con búsqueda, filtros y paginación | autenticados |
| `/competidores/:id` | Ficha con estadísticas y cambio de estado | autenticados |
| `/competidores/nuevo` y `/:id/editar` | Alta y edición | administrador |
| `/equipos` y `/equipos/:id` | Listado, detalle y gestión de integrantes | autenticados |
| `/equipos/nuevo` y `/:id/editar` | Alta y edición | administrador |
| `/carreras` y `/carreras/:id` | Agenda y detalle con el avance de estado | autenticados |
| `/carreras/nueva` y `/:id/editar` | Alta y edición | administrador y organizador |
| `/carreras/:id/inscripciones` | Anotar, aprobar y rechazar | administrador y organizador |
| `/carreras/:id/resultados` | Carga de resultados y quiénes faltan | administrador y organizador |
| `/posiciones` | Tabla de posiciones, individual y por equipos | autenticados |
| `/bitacora` | Auditoría con filtros por usuario, acción y fechas | administrador |
| `/perfil` | Lo que la API reconoce de tu sesión | autenticados |
| `/acceso-denegado` y cualquier ruta inexistente | 403 y 404 | — |

## Tres decisiones que conviene conocer

**El token vive en memoria, no en `localStorage`.** Cualquier script que llegue a
ejecutarse en la página puede leer `localStorage` entero y llevarse una
credencial válida. En memoria, el token muere al cerrar la pestaña, y la sesión
se recupera al recargar preguntándole a Keycloak si su cookie sigue viva
(*check-sso* silencioso). El costo es esa consulta de ida y vuelta en cada
recarga.

**No hay formulario de usuario y contraseña.** El botón "Entrar" manda el
navegador a Keycloak, que valida y devuelve un token. La contraseña nunca pasa
por este código, así que no hay forma de registrarla o perderla por error. Es el
mismo modelo del backend, que tampoco tiene `/api/auth/login`.

**Las direcciones no están compiladas adentro del paquete.** Vite reemplaza las
variables de entorno al *construir*, así que si la URL de la API entrara por ahí,
la imagen de Docker solo serviría para el entorno donde se armó. En su lugar,
`index.html` carga `/config.js`, que el contenedor reescribe al arrancar con las
variables del compose (`docker/config.sh`). La misma imagen sirve para cualquier
entorno.

## Lo que hace nginx

Una sola línea de `nginx.conf` es la importante:

```nginx
try_files $uri $uri/ /index.html;
```

En una aplicación de una sola página, `/carreras/3f2a…` no existe como archivo:
la inventa el router adentro del navegador. Sin esa línea, entrar directo a esa
dirección —o simplemente apretar F5 estando ahí— devuelve 404. Es el error que
nunca aparece en desarrollo, porque el servidor de Vite ya hace lo mismo, y
rompe todo lo que no sea la raíz apenas se arma la imagen.
