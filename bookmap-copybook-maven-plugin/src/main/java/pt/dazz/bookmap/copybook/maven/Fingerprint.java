package pt.dazz.bookmap.copybook.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Computes a SHA-256 hash over all inputs of the generation. The goal stores the hash, and
 * skips the next run if the hash does not change.
 */
final class Fingerprint {

    private final MessageDigest digest;

    private Fingerprint() {
        digest = digest("SHA-256");
    }

    /**
     * Gives a message digest. Each Java platform must support SHA-256, so the failure cannot
     * occur for that algorithm.
     *
     * @param algorithm the algorithm name
     * @return the message digest
     * @throws IllegalStateException if the algorithm is not available
     */
    static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Computes the hash of the inputs, in this order: the plugin version, the parameters, the
     * copybooks, the options files, and the copy directories. Each copybook and options file
     * adds its path relative to the source directory and its content. Each copy directory
     * adds its path relative to the source directory, and the name and the content of each
     * file in it. A missing copy directory adds only its path.
     *
     * @param pluginVersion the plugin version
     * @param parameters the parameter values, in a fixed order; a value can be {@code null}
     * @param sourceDir the source directory
     * @param copybooks the copybook files, sorted
     * @param optionsFiles the options files, sorted
     * @param copyDirs the copy directories, in search order
     * @return the hash, as 64 hexadecimal characters
     * @throws IOException if a file cannot be read
     */
    static String of(String pluginVersion, List<String> parameters, Path sourceDir,
                     List<Path> copybooks, List<Path> optionsFiles, List<Path> copyDirs)
        throws IOException {
        Fingerprint fingerprint = new Fingerprint();
        fingerprint.text(pluginVersion);
        for (String parameter : parameters) {
            fingerprint.text(parameter == null ? "\u0000null" : parameter);
        }
        for (Path file : copybooks) {
            fingerprint.file(sourceDir, file);
        }
        fingerprint.text("--options");
        for (Path file : optionsFiles) {
            fingerprint.file(sourceDir, file);
        }
        fingerprint.text("--copy-dirs");
        for (Path dir : copyDirs) {
            fingerprint.text(relative(sourceDir, dir));
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        fingerprint.file(dir, file);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(fingerprint.digest.digest());
    }

    private void file(Path base, Path file) throws IOException {
        text(relative(base, file));
        bytes(Files.readAllBytes(file));
    }

    private void text(String value) {
        bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Adds the length before the bytes, so that two values cannot run together. */
    private void bytes(byte[] value) {
        digest.update(Integer.toString(value.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(value);
    }

    private static String relative(Path base, Path file) {
        return base.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
            .toString().replace('\\', '/');
    }
}
