package pt.dazz.bookmap.copybook;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
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

    /**
     * A known cycle: {@code ast.DataItem} uses {@code parse.Picture}, and {@code parse} uses the
     * {@code ast} classes.
     * The rule ignores only this dependency, so a new cycle fails the test.
     */
    @Test
    void hasNoPackageCycles() {
        slices().matching("pt.dazz.(**)").should().beFreeOfCycles()
                .ignoreDependency(resideInAPackage("pt.dazz.bookmap.copybook.ast"), name("pt.dazz.bookmap.copybook.parse.Picture"))
                .check(MAIN_CLASSES);
    }
}
