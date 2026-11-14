package pt.dazz.bookmap.copybook;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Checks the dependency structure of the main classes of bookmap-copybook. */
class ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pt.dazz.bookmap.copybook");

    /**
     * The module depends only on the packages of this list. Thus it does not depend on Spring,
     * Quarkus, Micronaut, Guice, or a middleware library.
     */
    @Test
    void dependsOnlyOnTheAllowedPackages() {
        classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "javax.lang.model..", "com.fasterxml.jackson..",
                        "com.palantir.javapoet..", "pt.dazz.bookmap..")
                .check(MAIN_CLASSES);
    }

    /** No package of the module is in a cycle. */
    @Test
    void hasNoPackageCycles() {
        slices().matching("pt.dazz.(**)").should().beFreeOfCycles()
                .check(MAIN_CLASSES);
    }
}
