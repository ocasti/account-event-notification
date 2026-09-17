package co.cobre.simulator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestConventionsTest {

    private static final JavaClasses TEST_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
        .importPackages("co.cobre.simulator");

    @Test
    void shouldFollowNamingConventionWhenCheckingAllTestMethods() {
        var violations = TEST_CLASSES.stream()
            .filter(javaClass -> javaClass.getPackageName().equals("co.cobre.simulator"))
            .filter(javaClass -> !javaClass.getSimpleName().equals("TestConventionsTest"))
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(method -> method.isAnnotatedWith("org.junit.jupiter.api.Test")
                || method.isAnnotatedWith("org.junit.jupiter.params.ParameterizedTest"))
            .filter(method -> !method.getName().matches("^should[A-Z]\\w*When[A-Z]\\w*$"))
            .toList();

        assertThat(violations)
            .as("All @Test and @ParameterizedTest methods must follow naming convention: should<Resultado>When<Condición>")
            .isEmpty();
    }
}
