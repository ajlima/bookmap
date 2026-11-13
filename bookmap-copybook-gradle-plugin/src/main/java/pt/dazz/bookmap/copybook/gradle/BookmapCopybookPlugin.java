package pt.dazz.bookmap.copybook.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * The BookMap copybook plugin ({@code pt.dazz.bookmap.copybook}). It adds the {@code bookmap}
 * extension and the task {@code generateCopybookClasses}. With the {@code java} plugin, it
 * adds the generated classes to the {@code main} source set.
 */
public class BookmapCopybookPlugin implements Plugin<Project> {

    /** The name of the generation task. */
    public static final String TASK_NAME = "generateCopybookClasses";

    /**
     * Creates the plugin. Gradle calls this constructor.
     */
    public BookmapCopybookPlugin() {
    }

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        CopybooksSpec spec = project.getExtensions()
            .create("bookmap", BookmapExtension.class).getCopybooks();
        spec.getSourceDir().convention(layout.getProjectDirectory().dir("src/main/copybook"));
        spec.getOutputDir().convention(
            layout.getBuildDirectory().dir("generated/sources/copybook/main"));
        spec.getLombok().convention(false);
        spec.getPackagePerCopybook().convention(false);

        TaskProvider<GenerateCopybookClasses> task = project.getTasks().register(TASK_NAME,
            GenerateCopybookClasses.class, t -> {
                t.setGroup("build");
                t.setDescription("Generates the BookMap classes from the COBOL copybooks.");
                t.getSourceDir().set(spec.getSourceDir());
                t.getIncludes().set(spec.getIncludes());
                t.getExcludes().set(spec.getExcludes());
                t.getCopyDirs().from(spec.getCopyDirs());
                t.getPackageName().set(spec.getPackageName());
                t.getLombok().set(spec.getLombok());
                t.getPackagePerCopybook().set(spec.getPackagePerCopybook());
                t.getMaxRecordLength().set(spec.getMaxRecordLength());
                t.getOutputDir().set(spec.getOutputDir());
            });

        project.getPlugins().withType(JavaPlugin.class, java -> project.getExtensions()
            .getByType(SourceSetContainer.class).named("main",
                main -> main.getJava().srcDir(task)));
    }
}
