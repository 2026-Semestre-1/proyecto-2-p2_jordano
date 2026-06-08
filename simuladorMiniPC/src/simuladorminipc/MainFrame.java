package simuladorminipc;

/**
 * Punto de entrada del simulador MiniPC.
 * <p>
 * NetBeans lanza esta clase segun {@code main.class} configurado en
 * {@code nbproject/project.properties}. Su unico proposito es delegar
 * la ejecucion a la interfaz JavaFX principal.
 * </p>
 *
 * @see simuladorminipc.fx.SimuladorApp
 * @author Jordano Escalante
 */
public class MainFrame {

    /**
     * Punto de entrada de la aplicacion.
     * Delega a {@link simuladorminipc.fx.SimuladorApp#main(String[])}.
     *
     * @param args argumentos de linea de comandos (ignorados)
     */
    public static void main(String[] args) {
        simuladorminipc.fx.SimuladorApp.main(args);
    }
}
