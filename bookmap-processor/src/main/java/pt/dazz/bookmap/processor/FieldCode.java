package pt.dazz.bookmap.processor;

import static pt.dazz.bookmap.processor.CodecTypes.DECIMAL;
import static pt.dazz.bookmap.processor.CodecTypes.FIELD_CONTEXT;
import static pt.dazz.bookmap.processor.CodecTypes.USAGE;
import static pt.dazz.bookmap.processor.CodecTypes.numberAttributes;

import com.palantir.javapoet.CodeBlock;
import pt.dazz.bookmap.cobol.Usage;
import pt.dazz.bookmap.processor.CodecMembers.Names;
import pt.dazz.bookmap.processor.HelperMethods.Helper;
import pt.dazz.bookmap.processor.model.FieldModel;

/**
 * Generates the code that writes and reads one field: text, numbers, edited numbers, dates and
 * times, enums, and converted values. The code uses the local variables {@code value},
 * {@code target}, {@code writer}, {@code reader}, and {@code ctx}.
 */
final class FieldCode {

    private final CodecMembers members;
    private final HelperMethods helpers;

    FieldCode(CodecMembers members, HelperMethods helpers) {
        this.members = members;
        this.helpers = helpers;
    }

    /** Gives the statement that writes the field of {@code value}. */
    CodeBlock write(FieldModel field) {
        Names n = members.names(field);
        CodeBlock value = CodeBlock.of("value.$N()", field.getter());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("writer.writeText($N, $L, $L)", n.descriptor(), value,
                field.length());
            case INT, LONG -> writeNumber(field, CodeBlock.of("$T.valueOf($L)", DECIMAL, value));
            case INTEGER -> writeNumber(field, helpers.call(Helper.DECIMAL_OF_INTEGER, value));
            case LONG_BOXED -> writeNumber(field, helpers.call(Helper.DECIMAL_OF_LONG, value));
            case BIG_DECIMAL -> writeNumber(field, value);
            case LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME -> field.usage() == Usage.COMP3
                ? CodeBlock.of("writer.writePackedTemporal($N, $L, $L, $L, $N)", n.descriptor(),
                    value, field.length(), field.signed(), n.formatter())
                : CodeBlock.of("writer.writeTemporal($N, $L, $L, $N)", n.descriptor(), value,
                    field.length(), n.formatter());
            case ENUM -> {
                CodeBlock text = CodeBlock.of("$N($L, $N)", n.enumMethods().write(), value,
                    n.descriptor());
                yield field.enumModel().numeric() ? writeNumber(field, text)
                    : CodeBlock.of("writer.writeText($N, $L, $L)", n.descriptor(), text,
                        field.length());
            }
            case CONVERTED -> CodeBlock.of("writer.writeConverted($N, $L, $N, $L)",
                n.descriptor(), value, n.converter(), fieldContext(field));
        };
    }

    private CodeBlock writeNumber(FieldModel field, CodeBlock decimal) {
        Names n = members.names(field);
        return field.edited() != null
            ? CodeBlock.of("writer.writeEdited($N, $L, $N)", n.descriptor(), decimal, n.picture())
            : CodeBlock.of("writer.writeNumber($N, $L, $L)", n.descriptor(), decimal,
                numberAttributes(field));
    }

    /** Gives the expression that reads the field. */
    CodeBlock read(FieldModel field) {
        Names n = members.names(field);
        CodeBlock number = field.edited() != null
            ? CodeBlock.of("reader.readEdited($N, $N)", n.descriptor(), n.picture())
            : CodeBlock.of("reader.readNumber($N, $L)", n.descriptor(), numberAttributes(field));
        CodeBlock descriptor = CodeBlock.of("$N", n.descriptor());
        return switch (field.kind()) {
            case STRING -> CodeBlock.of("reader.readText($N, $L)", n.descriptor(), field.length());
            case INT -> helpers.call(Helper.INT_VALUE, number, descriptor);
            case INTEGER -> helpers.call(Helper.INTEGER_VALUE, number, descriptor);
            case LONG -> helpers.call(Helper.LONG_VALUE, number, descriptor);
            case LONG_BOXED -> helpers.call(Helper.LONG_BOXED_VALUE, number, descriptor);
            case BIG_DECIMAL -> number;
            case LOCAL_DATE -> readTemporal("Date", field);
            case LOCAL_TIME -> readTemporal("Time", field);
            case LOCAL_DATE_TIME -> readTemporal("DateTime", field);
            case ENUM -> CodeBlock.of("$N($L, $N)", n.enumMethods().read(),
                field.enumModel().numeric() ? number
                    : CodeBlock.of("reader.readText($N, $L)", n.descriptor(), field.length()),
                n.descriptor());
            case CONVERTED -> {
                CodeBlock converted = CodeBlock.of("reader.readConverted($N, $N, $L)",
                    n.descriptor(), n.converter(), fieldContext(field));
                yield field.type().getKind().isPrimitive()
                    ? helpers.call(Helper.NOT_NULL, converted, descriptor) : converted;
            }
        };
    }

    /** Reads a date or time field. A packed field calls {@code readPacked<type>}. */
    private CodeBlock readTemporal(String type, FieldModel field) {
        Names n = members.names(field);
        return field.usage() == Usage.COMP3
            ? CodeBlock.of("reader.$N($N, $L, $L, $N)", "readPacked" + type, n.descriptor(),
                field.length(), field.signed(), n.formatter())
            : CodeBlock.of("reader.$N($N, $L, $N)", "read" + type, n.descriptor(),
                field.length(), n.formatter());
    }

    private static CodeBlock fieldContext(FieldModel field) {
        return CodeBlock.of("new $T($S, $L, $L, $L, $T.$N, $S, $L, ctx.charset())", FIELD_CONTEXT,
            field.name(), field.length(), field.scale(), field.signed(), USAGE,
            field.usage().name(), field.pattern(), field.byteLength());
    }
}
