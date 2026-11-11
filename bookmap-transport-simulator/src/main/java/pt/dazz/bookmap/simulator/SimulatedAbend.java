package pt.dazz.bookmap.simulator;

/**
 * Throw this exception from a simulated operation to simulate a CICS abend. The simulator
 * transport translates it to a {@code RemoteAbendException} with the same abend code.
 */
public class SimulatedAbend extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The simulated abend code, for example {@code ASRA}. */
    private final String abendCode;

    /**
     * Creates an abend with a default message.
     *
     * @param abendCode the abend code, for example {@code ASRA}
     */
    public SimulatedAbend(String abendCode) {
        this(abendCode, "Simulated abend " + abendCode);
    }

    /**
     * Creates an abend with a message.
     *
     * @param abendCode the abend code, for example {@code ASRA}
     * @param message the message for the remote abend exception
     */
    public SimulatedAbend(String abendCode, String message) {
        super(message);
        this.abendCode = abendCode;
    }

    /**
     * Returns the abend code.
     *
     * @return the abend code
     */
    public String abendCode() {
        return abendCode;
    }
}
