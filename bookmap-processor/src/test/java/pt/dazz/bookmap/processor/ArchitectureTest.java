package pt.dazz.bookmap.processor;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Checks the dependency structure of the main classes of bookmap-processor. */
class ArchitectureTest {

    private static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("pt.dazz.bookmap.processor");

    /**
     * The module depends only on the packages of this list. Thus it does not depend on Spring,
     * Quarkus, Micronaut, Guice, or a middleware library.
     */
    @Test
    void dependsOnlyOnTheAllowedPackages() {
        classes().should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "javax.annotation.processing..", "javax.lang.model..", "javax.tools..",
                        "com.palantir.javapoet..", "pt.dazz.bookmap..")
                .check(MAIN_CLASSES);
    }

    @Test
    void hasNoPackageCycles() {
        slices().matching("pt.dazz.(**)").should().beFreeOfCycles().check(MAIN_CLASSES);
    }
}
