package co.cobre.notifications.architecture;

import co.cobre.notifications.application.UseCase;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal architecture tests.
 */
public class HexagonalArchitectureTest {

    private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("co.cobre.notifications");

    /**
     * Tests that domain does not depend on application, infrastructure, or framework.
     */
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

    /**
     * Tests that application does not depend on infrastructure or framework.
     */
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


    /**
     * Tests that JPA entities reside in persistence.entity package.
     */
    @Test
    void entities_should_reside_in_persistence_entity() {
        ArchRule rule = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..infrastructure.persistence.entity..");
        rule.check(classes);
    }

    /**
     * Tests that infrastructure.rest does not depend on infrastructure.persistence.
     */
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
}
