import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { competidores } from '../../api/competidores.js';
import { equipos } from '../../api/equipos.js';
import { Cargando, ErrorDeCarga } from '../../comun/Estados.jsx';
import {
    CampoDeSeleccion,
    CampoDeTexto,
    ErrorDelFormulario
} from '../../comun/Formulario.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { repartirErrores } from '../../comun/errores.js';
import { TIPO_DE_COMPETIDOR, opcionesDe } from '../../comun/formato.js';
import { useRecurso } from '../../comun/useRecurso.js';

/**
 * El alta y la edicion de un competidor, en un solo componente.
 *
 * POR QUE UNO Y NO DOS
 * Son el mismo formulario: los mismos campos, las mismas reglas, los mismos
 * mensajes. Lo unico que cambia es de donde salen los valores iniciales y a que
 * endpoint se manda. Separarlos en dos archivos garantiza que dentro de un mes
 * uno tenga un campo que el otro no, y que el error se descubra editando.
 *
 * La ruta decide: con :id es edicion, sin :id es alta.
 *
 * DE DONDE SALEN LOS MENSAJES DE ERROR
 * De la API. Las anotaciones de validacion del backend ya traen el texto escrito
 * en castellano ("El peso tiene que ser mayor que cero"), asi que repetir esas
 * reglas aca en JavaScript solo lograria dos versiones del mismo mensaje que se
 * van a desincronizar. Lo unico que hace la validacion del navegador es lo que el
 * enunciado dice que tiene que hacer: mejorar la experiencia, no reemplazar a la
 * del servidor. Por eso los campos llevan "required" y tipos correctos, y la
 * decision final es siempre del backend.
 */
const CAMPOS = [
    'name', 'nickname', 'type', 'dateOfBirth',
    'weightKg', 'heightCm', 'countryOfOrigin', 'teamId'
];

const VACIO = {
    name: '',
    nickname: '',
    type: '',
    dateOfBirth: '',
    weightKg: '',
    heightCm: '',
    countryOfOrigin: '',
    teamId: ''
};

export default function FormularioDeCompetidor() {
    const { id } = useParams();
    const editando = Boolean(id);
    const navegar = useNavigate();
    const avisos = useNotificaciones();

    const [formulario, setFormulario] = useState(VACIO);
    const [erroresDeCampo, setErroresDeCampo] = useState({});
    const [errorGeneral, setErrorGeneral] = useState(null);
    const [guardando, setGuardando] = useState(false);

    // Los equipos se piden para el desplegable. size alto y una sola pagina: es un
    // selector, no un listado, y paginar un desplegable no le sirve a nadie.
    const cargar = useCallback(async () => {
        const [listaDeEquipos, competidor] = await Promise.all([
            equipos.listar({ status: 'ACTIVE', size: 100, sort: 'name,asc' }),
            editando ? competidores.buscarPorId(id) : Promise.resolve(null)
        ]);
        return { listaDeEquipos, competidor };
    }, [id, editando]);

    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    // Cuando llegan los datos del competidor, se vuelcan al formulario. Es un
    // efecto y no un valor inicial porque en el primer dibujado todavia no
    // llegaron: useState solo mira su argumento la primera vez.
    useEffect(() => {
        if (datos?.competidor) {
            const c = datos.competidor;
            setFormulario({
                name: c.name,
                nickname: c.nickname,
                type: c.type,
                dateOfBirth: c.dateOfBirth ?? '',
                weightKg: String(c.weightKg),
                heightCm: String(c.heightCm),
                countryOfOrigin: c.countryOfOrigin,
                teamId: c.teamId ?? ''
            });
        }
    }, [datos]);

    const cambiar = (campo) => (valor) => {
        setFormulario((actual) => ({ ...actual, [campo]: valor }));
        // El error de un campo desaparece apenas se lo toca. Dejarlo puesto
        // mientras el usuario corrige da la impresion de que la correccion no
        // sirvio de nada.
        setErroresDeCampo((actuales) => ({ ...actuales, [campo]: undefined }));
    };

    const enviar = async (evento) => {
        evento.preventDefault();
        setGuardando(true);
        setErroresDeCampo({});
        setErrorGeneral(null);

        const cuerpo = {
            ...formulario,
            // El equipo es opcional: la cadena vacia del desplegable tiene que
            // viajar como null, o Spring intentaria convertirla a UUID y fallaria
            // con un error que no le dice nada al usuario.
            teamId: formulario.teamId === '' ? null : formulario.teamId,
            weightKg: formulario.weightKg === '' ? null : Number(formulario.weightKg),
            heightCm: formulario.heightCm === '' ? null : Number(formulario.heightCm)
        };

        try {
            const guardado = editando
                ? await competidores.actualizar(id, cuerpo)
                : await competidores.crear(cuerpo);

            avisos.exito(editando ? 'Competidor actualizado' : `${guardado.nickname} quedo inscripto en la liga`);
            navegar(`/competidores/${guardado.id}`);
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

    const opcionesDeEquipo = datos.listaDeEquipos.content.map((equipo) => ({
        valor: equipo.id,
        texto: equipo.name
    }));

    return (
        <div className="pagina pagina-angosta">
            <nav className="miga">
                <Link to="/competidores">Competidores</Link> / {editando ? datos.competidor.nickname : 'Nuevo'}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{editando ? 'Editar competidor' : 'Nuevo competidor'}</h1>
                    <p className="pagina-bajada">
                        {editando
                            ? 'Los cambios quedan registrados en la bitacora.'
                            : 'El apodo tiene que ser unico en toda la liga.'}
                    </p>
                </div>
            </header>

            <form className="tarjeta formulario" onSubmit={enviar} noValidate={false}>
                <ErrorDelFormulario mensaje={errorGeneral} />

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Nombre"
                        nombre="name"
                        valor={formulario.name}
                        alCambiar={cambiar('name')}
                        error={erroresDeCampo.name}
                        requerido
                        required
                        maxLength={150}
                    />
                    <CampoDeTexto
                        etiqueta="Apodo"
                        nombre="nickname"
                        valor={formulario.nickname}
                        alCambiar={cambiar('nickname')}
                        error={erroresDeCampo.nickname}
                        ayuda="Es el nombre con el que se lo ve en las carreras"
                        requerido
                        required
                        maxLength={100}
                    />
                </div>

                <div className="formulario-fila">
                    <CampoDeSeleccion
                        etiqueta="Categoria"
                        nombre="type"
                        valor={formulario.type}
                        alCambiar={cambiar('type')}
                        opciones={opcionesDe(TIPO_DE_COMPETIDOR)}
                        error={erroresDeCampo.type}
                        requerido
                        required
                    />
                    <CampoDeTexto
                        etiqueta="Fecha de nacimiento"
                        nombre="dateOfBirth"
                        tipo="date"
                        valor={formulario.dateOfBirth}
                        alCambiar={cambiar('dateOfBirth')}
                        error={erroresDeCampo.dateOfBirth}
                        requerido
                        required
                    />
                </div>

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Peso (kg)"
                        nombre="weightKg"
                        tipo="number"
                        step="0.01"
                        min="0.01"
                        valor={formulario.weightKg}
                        alCambiar={cambiar('weightKg')}
                        error={erroresDeCampo.weightKg}
                        requerido
                        required
                    />
                    <CampoDeTexto
                        etiqueta="Altura (cm)"
                        nombre="heightCm"
                        tipo="number"
                        step="0.01"
                        min="0.01"
                        valor={formulario.heightCm}
                        alCambiar={cambiar('heightCm')}
                        error={erroresDeCampo.heightCm}
                        requerido
                        required
                    />
                </div>

                <div className="formulario-fila">
                    <CampoDeTexto
                        etiqueta="Origen"
                        nombre="countryOfOrigin"
                        valor={formulario.countryOfOrigin}
                        alCambiar={cambiar('countryOfOrigin')}
                        error={erroresDeCampo.countryOfOrigin}
                        ayuda="Pais o region de procedencia"
                        requerido
                        required
                        maxLength={100}
                    />
                    <CampoDeSeleccion
                        etiqueta="Equipo"
                        nombre="teamId"
                        valor={formulario.teamId}
                        alCambiar={cambiar('teamId')}
                        opciones={opcionesDeEquipo}
                        vacio="Sin equipo (compite individualmente)"
                        error={erroresDeCampo.teamId}
                        ayuda="Se puede dejar vacio y asignarlo despues desde el equipo"
                    />
                </div>

                <div className="formulario-botones">
                    <Link
                        to={editando ? `/competidores/${id}` : '/competidores'}
                        className="boton boton-secundario"
                    >
                        Cancelar
                    </Link>
                    <button type="submit" className="boton boton-primario" disabled={guardando}>
                        {guardando ? 'Guardando...' : (editando ? 'Guardar cambios' : 'Crear competidor')}
                    </button>
                </div>
            </form>
        </div>
    );
}
