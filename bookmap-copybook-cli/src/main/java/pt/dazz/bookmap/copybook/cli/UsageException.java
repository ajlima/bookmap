package pt.dazz.bookmap.copybook.cli;

/**
 * Tells that the command line or the options are not correct (exit code 2).
 */
final class UsageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UsageException(String message) {
        super(message);
    }
}
