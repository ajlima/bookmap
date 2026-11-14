package pt.dazz.bookmap.processor.model;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.cobol.Sign;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.codec.EditedPicture;

/**
 * Describes one elementary field of a book. All conventions are already resolved.
 *
 * @param ownerSimpleName the simple name of the class that declares the field
 * @param name the Java field name
 * @param order the position in the book
 * @param length the number of characters for text, or the total number of digits for a number
 * @param direction the message in which the field appears
 * @param usage the storage format
 * @param scale the number of implied decimal places
 * @param signed {@code true} if the number has a sign
 * @param sign the position and the format of the sign of a {@code DISPLAY} number
 * @param pattern the date or time pattern
 * @param kind the category of the Java type
 * @param type the Java type of the field
 * @param getter the name of the getter method
 * @param setter the name of the setter method
 * @param converter the custom converter class, or {@code null} if the field has no converter
 * @param byteLength the number of bytes of the field in the book
 * @param enumModel the COBOL values of an enum field, or {@code null} for another kind
 * @param edited the edited picture of a number, or {@code null} for a field without one
 */
public record FieldModel(String ownerSimpleName, String name, int order, int length,
    Direction direction, Usage usage, int scale, boolean signed, Sign sign, String pattern,
    JavaKind kind,
    TypeMirror type, String getter, String setter, TypeElement converter, int byteLength,
    EnumModel enumModel, EditedPicture edited)
    implements MemberModel {

    @Override
    public int areaLength() {
        return byteLength;
    }

    /**
     * Gives the category of the Java type of a field.
     */
    public enum JavaKind {
        /** {@code String}. */
        STRING,
        /** {@code int}. */
        INT,
        /** {@code Integer}. */
        INTEGER,
        /** {@code long}. */
        LONG,
        /** {@code Long}. */
        LONG_BOXED,
        /** {@code BigDecimal}. */
        BIG_DECIMAL,
        /** {@code LocalDate}. */
        LOCAL_DATE,
        /** {@code LocalTime}. */
        LOCAL_TIME,
        /** {@code LocalDateTime}. */
        LOCAL_DATE_TIME,
        /** An enum with {@code @CobolValue} constants, without a converter. */
        ENUM,
        /** Any type with a custom converter. */
        CONVERTED
    }
}
