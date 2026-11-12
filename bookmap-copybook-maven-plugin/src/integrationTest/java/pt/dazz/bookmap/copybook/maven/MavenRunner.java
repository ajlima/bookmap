package pt.dazz.bookmap.copybook.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads a Maven distribution and runs it on a project. */
final class MavenRunner {

    /** The result of a Maven run. */
    record Result(int exitCode, String output) {
    }

    private static final Path DISTRIBUTIONS = Path.of(System.getProperty("maven.distributions"));
    private static final Path LOCAL_REPOSITORY =
        Path.of(System.getProperty("maven.localRepository"));
    static final String BOOKMAP_VERSION = System.getProperty("bookmap.version");
    private static final Path BOOKMAP_REPOSITORY =
        Path.of(System.getProperty("bookmap.repository"));

    private final Path mvn;

    MavenRunner(String version) {
        this.mvn = install(version);
    }

    /** Removes the BookMap artifacts from the shared local repository, once for each class. */
    static void forgetBookmapArtifacts() throws IOException {
        Path bookmap = LOCAL_REPOSITORY.resolve("pt/dazz/bookmap");
        if (Files.exists(bookmap)) {
            try (Stream<Path> files = Files.walk(bookmap)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    Result run(Path project, String... arguments) throws IOException, InterruptedException {
        Path settings = project.resolve("it-settings.xml");
        Files.writeString(settings, settings());
        List<String> command = new ArrayList<>(List.of(mvn.toString(), "-B", "-e",
            "-s", settings.toString(), "-Dmaven.repo.local=" + LOCAL_REPOSITORY,
            "-Dbookmap.version=" + BOOKMAP_VERSION));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile())
            .redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().remove("MAVEN_OPTS");
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("Maven did not stop in 10 minutes");
        }
        String text = new String(output, StandardCharsets.UTF_8);
        System.out.println(text);
        return new Result(process.exitValue(), text);
    }

    private static String settings() {
        String url = BOOKMAP_REPOSITORY.toUri().toString();
        String repository = """
                    <id>bookmap-integration-test</id>
                    <url>%s</url>
                    <releases><enabled>true</enabled></releases>
                    <snapshots><enabled>true</enabled><updatePolicy>always</updatePolicy></snapshots>
            """.formatted(url);
        return """
            <settings>
              <profiles>
                <profile>
                  <id>bookmap</id>
                  <repositories>
                    <repository>
            %s        </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
            %s        </pluginRepository>
                  </pluginRepositories>
                </profile>
              </profiles>
              <activeProfiles>
                <activeProfile>bookmap</activeProfile>
              </activeProfiles>
            </settings>
            """.formatted(repository, repository);
    }

    private static synchronized Path install(String version) {
        Path home = DISTRIBUTIONS.resolve("apache-maven-" + version);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path mvn = home.resolve(windows ? "bin/mvn.cmd" : "bin/mvn");
        if (Files.isExecutable(mvn)) {
            return mvn;
        }
        URI uri = URI.create("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/"
            + version + "/apache-maven-" + version + "-bin.zip");
        try (InputStream in = uri.toURL().openStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path target = DISTRIBUTIONS.resolve(entry.getName()).normalize();
                if (!target.startsWith(DISTRIBUTIONS)) {
                    throw new IOException("bad entry in the Maven archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!mvn.toFile().setExecutable(true)) {
            throw new IllegalStateException("cannot make " + mvn + " executable");
        }
        return mvn;
    }
}
