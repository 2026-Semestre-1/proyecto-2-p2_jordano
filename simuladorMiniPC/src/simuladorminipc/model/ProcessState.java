package simuladorminipc.model;

/**
 * All possible states of a process in the OS simulator lifecycle.
 *
 * <p>State transitions are enforced by
 * {@link simuladorminipc.process.StateManager}.</p>
 */
public enum ProcessState {

    NEW             ("Nuevo"),
    READY           ("Listo"),
    RUNNING         ("En Ejecución"),
    BLOCKED         ("Bloqueado"),
    SUSPENDED_READY ("Susp. Listo"),
    SUSPENDED_BLOCKED("Susp. Bloqueado"),
    TERMINATED      ("Finalizado");

    private final String displayName;

    /**
     * Associates a Spanish display name with this state.
     *
     * @param displayName localised label shown in the GUI
     */
    ProcessState(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the localised display name shown in the GUI.
     *
     * @return non-null, non-empty Spanish label
     */
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
