import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { equipos } from '../../api/equipos.js';
import { Cargando, ErrorDeCarga } from '../../comun/Estados.jsx';
import { CampoDeArea, CampoDeTexto, ErrorDelFormulario } from '../../comun/Formulario.jsx';
import { useNotificaciones } from '../../comun/Notificaciones.jsx';
import { repartirErrores } from '../../comun/errores.js';
import { useRecurso } from '../../comun/useRecurso.js';

/** El alta y la edicion de un equipo. Mismo molde que el de competidores. */
const CAMPOS = ['name', 'description', 'coach'];

export default function FormularioDeEquipo() {
    const { id } = useParams();
    const editando = Boolean(id);
    const navegar = useNavigate();
    const avisos = useNotificaciones();

    const [formulario, setFormulario] = useState({ name: '', description: '', coach: '' });
    const [erroresDeCampo, setErroresDeCampo] = useState({});
    const [errorGeneral, setErrorGeneral] = useState(null);
    const [guardando, setGuardando] = useState(false);

    const cargar = useCallback(
        () => (editando ? equipos.buscarPorId(id) : Promise.resolve(null)),
        [id, editando]
    );
    const { datos, cargando, error, recargar } = useRecurso(cargar, [id]);

    useEffect(() => {
        if (datos) {
            setFormulario({
                name: datos.name,
                description: datos.description,
                coach: datos.coach
            });
        }
    }, [datos]);

    const cambiar = (campo) => (valor) => {
        setFormulario((actual) => ({ ...actual, [campo]: valor }));
        setErroresDeCampo((actuales) => ({ ...actuales, [campo]: undefined }));
    };

    const enviar = async (evento) => {
        evento.preventDefault();
        setGuardando(true);
        setErroresDeCampo({});
        setErrorGeneral(null);

        try {
            const guardado = editando
                ? await equipos.actualizar(id, formulario)
                : await equipos.crear(formulario);

            avisos.exito(editando ? 'Equipo actualizado' : `${guardado.name} quedo registrado`);
            navegar(`/equipos/${guardado.id}`);
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
                <Link to="/equipos">Equipos</Link> / {editando ? datos.name : 'Nuevo'}
            </nav>

            <header className="pagina-encabezado">
                <div>
                    <h1>{editando ? 'Editar equipo' : 'Nuevo equipo'}</h1>
                    <p className="pagina-bajada">
                        Los integrantes se agregan despues, desde el detalle del equipo.
                    </p>
                </div>
            </header>

            <form className="tarjeta formulario" onSubmit={enviar}>
                <ErrorDelFormulario mensaje={errorGeneral} />

                <CampoDeTexto
                    etiqueta="Nombre del equipo"
                    nombre="name"
                    valor={formulario.name}
                    alCambiar={cambiar('name')}
                    error={erroresDeCampo.name}
                    requerido
                    required
                    maxLength={100}
                />

                <CampoDeTexto
                    etiqueta="Responsable"
                    nombre="coach"
                    valor={formulario.coach}
                    alCambiar={cambiar('coach')}
                    error={erroresDeCampo.coach}
                    ayuda="Quien responde por el equipo ante la organizacion"
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
                    filas={4}
                />

                <div className="formulario-botones">
                    <Link
                        to={editando ? `/equipos/${id}` : '/equipos'}
                        className="boton boton-secundario"
                    >
                        Cancelar
                    </Link>
                    <button type="submit" className="boton boton-primario" disabled={guardando}>
                        {guardando ? 'Guardando...' : (editando ? 'Guardar cambios' : 'Crear equipo')}
                    </button>
                </div>
            </form>
        </div>
    );
}
