import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { carreras } from '../../api/carreras.js';
import { Cargando, ErrorDeCarga } from '../../comun/Estados.jsx';
import {
    CampoDeArea,
    CampoDeSeleccion,
    CampoDeTexto,
    ErrorDelFormulario
} from '../../comun/Formulario.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { repartirErrores } from '../../comun/errores.js';
import {
    TIPO_DE_CARRERA,
    desdeCampoDeFecha,
    opcionesDe,
    paraCampoDeFecha
} from '../../comun/formato.js';
import { useRecurso } from '../../comun/useRecurso.js';

/**
 * El alta y la edicion de una carrera.
 *
 * LAS DOS FECHAS SON EL PUNTO DELICADO
 * La carrera tiene largada y cierre de inscripciones, y hay una regla que las
 * relaciona: el cierre tiene que ser ANTERIOR a la largada. Esa validacion, en el
 * backend, es un @AssertTrue de clase, asi que su mensaje no llega asociado a
 * ningun campo: llega con la clave del metodo que la verifica.
 *
 * Por eso el formulario usa repartirErrores, que manda al campo lo que tiene
 * nombre de campo y junta el resto arriba. Sin eso, alguien que pone las fechas al
 * reves veria el formulario rechazado sin un solo mensaje en pantalla.
 *
 * EL FORMATO DE LAS FECHAS TAMBIEN TIENE TRAMPA
 * El campo datetime-local del navegador entrega "2026-12-20T10:00", sin segundos,
 * y el LocalDateTime de Java los quiere. Las funciones de formato.js hacen la
 * traduccion en los dos sentidos.
 */
const CAMPOS = [
    'name', 'description', 'scheduledAt', 'startLocation', 'finishLocation',
    'distanceMeters', 'maxParticipants', 'type', 'registrationDeadline'
];

const VACIO = {
    name: '',
    description: '',
    scheduledAt: '',
    startLocation: '',
    finishLocation: '',
    distanceMeters: '1000',
    maxParticipants: '8',
    type: '',
    registrationDeadline: ''
};

export default function FormularioDeCarrera() {
    const { id } = useParams();
    const editando = Boolean(id);
    const navegar = useNavigate();
    const avisos = useNotificaciones();

    const [formulario, setFormulario] = useState(VACIO);
    const [erroresDeCampo, setErroresDeCampo] = useState({});
    const [errorGeneral, setErrorGeneral] = useState(null);
    const [guardando, setGuardando] = useState(false);

    const cargar = useCallback(
        () => (editando ? carreras.buscarPorId(id) : Promise.resolve(null)),
        [id, editando]
    );
    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    useEffect(() => {
        if (datos) {
            setFormulario({
                name: datos.name,
                description: datos.description,
                scheduledAt: paraCampoDeFecha(datos.scheduledAt),
                startLocation: datos.startLocation,
                finishLocation: datos.finishLocation,
                distanceMeters: String(datos.distanceMeters),
                maxParticipants: String(datos.maxParticipants),
                type: datos.type,
                registrationDeadline: paraCampoDeFecha(datos.registrationDeadline)
            });
        }
    }, [datos]);

    const cambiar = (campo) => (valor) => {
        setFormulario((actual) => ({ ...actual, [campo]: valor }));
        setErroresDeCampo((actuales) => ({ ...actuales, [campo]: undefined }));
        // El error general se limpia con cualquier cambio: casi siempre viene de
        // una regla que compara dos campos, y tocar cualquiera de los dos puede
        // haberla resuelto.
        setErrorGeneral(null);
    };

    const enviar = async (evento) => {
        evento.preventDefault();
        setGuardando(true);
        setErroresDeCampo({});
        setErrorGeneral(null);

        const cuerpo = {
            ...formulario,
            distanceMeters: Number(formulario.distanceMeters),
            maxParticipants: Number(formulario.maxParticipants),
            scheduledAt: desdeCampoDeFecha(formulario.scheduledAt),
            registrationDeadline: desdeCampoDeFecha(formulario.registrationDeadline)
        };

        try {
            const guardada = editando
                ? await carreras.actualizar(id, cuerpo)
                : await carreras.crear(cuerpo);

            avisos.exito(
                editando
                    ? 'Carrera actualizada'
                    : 'La carrera quedo en borrador. Abri las inscripciones cuando este lista.'
            );
            navegar(`/carreras/${guardada.id}`);
        } catch (fallo) {
            const { porCampo, general } = repartirErrores(fallo, CAMPOS);
            setErroresDeCampo(porCampo);
            setErrorGeneral(general);
            setGuardando(false);
        }
    };

    if (cargando) {
        return <Cargando mensaje="Preparando el formulario" />;
    }

    if (error) {
        return <ErrorDeCarga mensaje={error.message} alReintentar={recargar} />;
    }

    return (
        <div className="pagina pagina-angosta">
            <nav className="miga">
                <Link to="/carreras">Carreras</Link> / {editando ? datos.name : 'Nueva'}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{editando ? 'Editar carrera' : 'Nueva carrera'}</h1>
                    <p className="pagina-bajada">
                        {editando
                            ? 'Una carrera se puede editar mientras no haya largado.'
                            : 'Se crea en borrador: nadie se puede inscribir hasta que abras las inscripciones.'}
                    </p>
                </div>
            </header>

            <form className="tarjeta formulario" onSubmit={enviar}>
                <ErrorDelFormulario mensaje={errorGeneral} />

                <CampoDeTexto
                    etiqueta="Nombre de la carrera"
                    nombre="name"
                    valor={formulario.name}
                    alCambiar={cambiar('name')}
                    error={erroresDeCampo.name}
                    requerido
                    required
                    maxLength={150}
                />

                <CampoDeArea
                    etiqueta="Descripcion"
                    nombre="description"
                    valor={formulario.description}
                    alCambiar={cambiar('description')}
                    error={erroresDeCampo.description}
                    ayuda="Hasta 500 caracteres"
                    requerido
                    required
                    maxLength={500}
                />

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Fecha y hora de largada"
                        nombre="scheduledAt"
                        tipo="datetime-local"
                        valor={formulario.scheduledAt}
                        alCambiar={cambiar('scheduledAt')}
                        error={erroresDeCampo.scheduledAt}
                        requerido
                        required
                    />
                    <CampoDeTexto
                        etiqueta="Cierre de inscripciones"
                        nombre="registrationDeadline"
                        tipo="datetime-local"
                        valor={formulario.registrationDeadline}
                        alCambiar={cambiar('registrationDeadline')}
                        error={erroresDeCampo.registrationDeadline}
                        ayuda="Tiene que ser antes de la largada"
                        requerido
                        required
                    />
                </div>

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Lugar de largada"
                        nombre="startLocation"
                        valor={formulario.startLocation}
                        alCambiar={cambiar('startLocation')}
                        error={erroresDeCampo.startLocation}
                        requerido
                        required
                        maxLength={150}
                    />
                    <CampoDeTexto
                        etiqueta="Lugar de llegada"
                        nombre="finishLocation"
                        valor={formulario.finishLocation}
                        alCambiar={cambiar('finishLocation')}
                        error={erroresDeCampo.finishLocation}
                        requerido
                        required
                        maxLength={150}
                    />
                </div>

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Distancia (metros)"
                        nombre="distanceMeters"
                        tipo="number"
                        min="1"
                        valor={formulario.distanceMeters}
                        alCambiar={cambiar('distanceMeters')}
                        error={erroresDeCampo.distanceMeters}
                        requerido
                        required
                    />
                    <CampoDeTexto
                        etiqueta="Cupo de participantes"
                        nombre="maxParticipants"
                        tipo="number"
                        min="2"
                        max="50"
                        valor={formulario.maxParticipants}
                        alCambiar={cambiar('maxParticipants')}
                        error={erroresDeCampo.maxParticipants}
                        ayuda="Entre 2 y 50"
                        requerido
                        required
                    />
                    <CampoDeSeleccion
                        etiqueta="Tipo"
                        nombre="type"
                        valor={formulario.type}
                        alCambiar={cambiar('type')}
                        opciones={opcionesDe(TIPO_DE_CARRERA)}
                        error={erroresDeCampo.type}
                        requerido
                        required
                    />
                </div>

                <div className="formulario-botones">
                    <Link
                        to={editando ? `/carreras/${id}` : '/carreras'}
                        className="boton boton-secundario"
                    >
                        Cancelar
                    </Link>
                    <button type="submit" className="boton boton-primario" disabled={guardando}>
                        {guardando ? 'Guardando...' : (editando ? 'Guardar cambios' : 'Crear carrera')}
                    </button>
                </div>
            </form>
        </div>
    );
}
