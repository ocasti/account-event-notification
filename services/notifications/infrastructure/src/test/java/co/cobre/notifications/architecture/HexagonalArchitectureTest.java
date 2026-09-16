package co.cobre.notifications.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class HexagonalArchitectureTest {

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("co.cobre.notifications");

    @Test
    void domain_should_not_depend_on_application_or_infrastructure() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "org.springframework..",
                "jakarta..",
                "com.fasterxml..",
                "tools.jackson.."
            );
        rule.check(classes);
    }

    @Test
    void application_should_not_depend_on_infrastructure_or_framework() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..infrastructure..",
                "org.springframework..",
                "jakarta..",
                "com.fasterxml..",
                "tools.jackson.."
            );
        rule.check(classes);
    }

    @Test
    void entities_should_reside_in_persistence_entity() {
        ArchRule rule = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..infrastructure.persistence..");
        rule.check(classes);
    }

    @Test
    void infrastructure_rest_should_not_depend_on_infrastructure_persistence() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..infrastructure.rest..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.persistence..")
            .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * Tests that infrastructure.worker does not depend on infrastructure.rest or
     * infrastructure.security, keeping the worker process isolated from the api process.
     */
    @Test
    void infrastructure_worker_should_not_depend_on_infrastructure_rest_or_security() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..infrastructure.worker..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..infrastructure.rest..",
                "..infrastructure.security.."
            )
            .allowEmptyShould(true);
        rule.check(classes);
    }

    /**
     * Tests that infrastructure.rest does not depend on infrastructure.worker,
     * keeping the api process isolated from the worker process.
     */
    @Test
    void infrastructure_rest_should_not_depend_on_infrastructure_worker() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..infrastructure.rest..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.worker..")
            .allowEmptyShould(true);
        rule.check(classes);
    }
}
