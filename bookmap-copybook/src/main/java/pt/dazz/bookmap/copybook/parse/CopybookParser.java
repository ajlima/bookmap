package pt.dazz.bookmap.copybook.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.diag.Diagnostics;

/**
 * Parses a copybook: source lines, tokens, {@code COPY} expansion, and data items.
 */
public final class CopybookParser {

    private CopybookParser() {
    }

    /**
     * Parses a copybook file. The source name is the file name.
     *
     * @param file the copybook file
     * @param options the parse options
     * @return the result
     */
    public static ParseResult parse(Path file, ParseOptions options) {
        String sourceName = file.getFileName().toString();
        try {
            return parse(sourceName, Files.readString(file, options.charset()), options);
        } catch (IOException e) {
            Diagnostics diagnostics = new Diagnostics();
            diagnostics.error(sourceName, 1, 1, "cannot read " + sourceName + ": " + e);
            return new ParseResult(null, diagnostics.all());
        }
    }

    /**
     * Parses copybook text.
     *
     * @param sourceName the source name, for diagnostics and the default record name
     * @param content the copybook text
     * @param options the parse options
     * @return the result
     */
    public static ParseResult parse(String sourceName, String content, ParseOptions options) {
        Diagnostics diagnostics = new Diagnostics();
        List<Token> tokens = Lexer.tokenize(
            SourceReader.read(sourceName, content, options.format(), diagnostics), diagnostics);
        tokens = CopyExpander.expand(tokens, baseName(sourceName), options, diagnostics);
        String recordName = options.recordName() != null
            ? options.recordName() : baseName(sourceName);
        Copybook copybook = DataParser.parse(sourceName, tokens, recordName, diagnostics);
        return new ParseResult(diagnostics.hasErrors() ? null : copybook, diagnostics.all());
    }

    /** Gives the file name without the extension, in upper case. */
    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName).toUpperCase(Locale.ROOT);
    }
}
