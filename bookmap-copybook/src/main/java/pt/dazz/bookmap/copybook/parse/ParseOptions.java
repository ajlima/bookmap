package pt.dazz.bookmap.copybook.parse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Gives the options of the parse step.
 *
 * @param format the reference format
 * @param charset the charset of the copybook files
 * @param copyDirs the directories that hold {@code COPY} members, in search order
 * @param recordName the name of the synthetic record, or {@code null} for the file name
 */
public record ParseOptions(SourceFormat format, Charset charset, List<Path> copyDirs,
                           String recordName) {

    /**
     * Creates the options.
     *
     * @param format the format
     * @param charset the charset
     * @param copyDirs the copy directories
     * @param recordName the record name
     */
    public ParseOptions {
        copyDirs = List.copyOf(copyDirs);
    }

    /**
     * Gives the default options: fixed format, UTF-8, no copy directory, and the record name
     * from the file name.
     *
     * @return the default options
     */
    public static ParseOptions defaults() {
        return new ParseOptions(SourceFormat.FIXED, StandardCharsets.UTF_8, List.of(), null);
    }
}
