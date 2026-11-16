package pt.dazz.copymapper.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.math.BigDecimal;
import pt.dazz.copymapper.cobol.Sign;
import pt.dazz.copymapper.processor.model.FieldModel;

/**
 * The types of the runtime API that a generated codec uses, and the code of the number
 * attributes of a field.
 */
final class CodecTypes {

    static final ClassName BOOK_CODEC = ClassName.get("pt.dazz.copymapper.spi", "BookCodec");
    static final ClassName BOOK_WRITER = ClassName.get("pt.dazz.copymapper.codec", "BookWriter");
    static final ClassName BOOK_READER = ClassName.get("pt.dazz.copymapper.codec", "BookReader");
    static final ClassName FORMATS = ClassName.get("pt.dazz.copymapper.codec", "Formats");
    static final ClassName EDITED_PICTURE = ClassName.get("pt.dazz.copymapper.codec",
        "EditedPicture");
    static final ClassName EMPTY_SLOT = ClassName.get("pt.dazz.copymapper.codec", "EmptySlot");
    static final ClassName CODEC_CONTEXT = ClassName.get("pt.dazz.copymapper.convert",
        "CodecContext");
    static final ClassName FIELD_CONTEXT = ClassName.get("pt.dazz.copymapper.convert",
        "FieldContext");
    static final ClassName USAGE = ClassName.get("pt.dazz.copymapper.cobol", "Usage");
    static final ClassName MAPPING_EXCEPTION = ClassName.get("pt.dazz.copymapper.exception",
        "MappingException");
    static final ClassName INVALID_VALUE_EXCEPTION = ClassName.get(
        "pt.dazz.copymapper.exception", "InvalidValueException");
    static final ClassName GENERATED = ClassName.get("javax.annotation.processing",
        "Generated");
    static final ClassName DECIMAL = ClassName.get(BigDecimal.class);
    static final TypeName BYTES = TypeName.get(byte[].class);

    private CodecTypes() {
    }

    /**
     * Gives the arguments of a number. The sign comes only when it is not the default. Thus,
     * the codecs of the books without a {@code SIGN} clause do not change.
     */
    static CodeBlock numberAttributes(FieldModel field) {
        CodeBlock attributes = CodeBlock.of("$L, $L, $L, $T.$N", field.length(), field.scale(),
            field.signed(), USAGE, field.usage().name());
        return field.sign() == Sign.TRAILING ? attributes
            : CodeBlock.of("$L, $T.$N", attributes, Sign.class, field.sign().name());
    }
}
