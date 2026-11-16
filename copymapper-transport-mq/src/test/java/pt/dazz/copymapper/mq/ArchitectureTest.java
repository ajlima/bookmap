package pt.dazz.copymapper.mq;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Checks the dependency structure of the main classes of copymapper-transport-mq. */
class ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pt.dazz.copymapper.mq");

    @Test
    void hasNoPackageCycles() {
        slices().matching("pt.dazz.(**)").should().beFreeOfCycles().check(MAIN_CLASSES);
    }
}
