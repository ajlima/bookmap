package pt.daaz.bookmap;

/**
 * Marks a book that checks its own response.
 * The client calls {@link #validate()} after each response.
 */
public interface Validatable {

    /**
     * Checks the response data.
     * Throw a {@link pt.daaz.bookmap.exception.BusinessException} when the data is not correct.
     */
    void validate();
}
