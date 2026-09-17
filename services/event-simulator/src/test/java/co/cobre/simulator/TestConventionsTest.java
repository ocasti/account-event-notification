package co.cobre.simulator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class TestConventionsTest {

    private static final String TEST_NAME_PATTERN = "^should[A-Z]\\w*When[A-Z]\\w*$";

    private static final JavaClasses TEST_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
        .importPackages("co.cobre.simulator");

    @Test
    void shouldRejectLooseMockitoMatchersWhenTestCodeStubsOrVerifies() {
        noClasses()
            .should().callMethodWhere(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(HasName.Predicates.name("org.mockito.ArgumentMatchers"))))
            .because("stubs and verifications use the exact values of the test, or an ArgumentCaptor with assertions")
            .check(TEST_CLASSES);
    }

    @Test
    void shouldRejectReflectionWhenTestCodeReachesIntoTheSystemUnderTest() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("java.lang.reflect..", "org.springframework.test.util..")
            .because("tests exercise behaviour through the public API only")
            .check(TEST_CLASSES);
    }

    @Test
    void shouldFollowTheNamingPatternWhenAMethodIsATest() {
        var offenders = TEST_CLASSES.stream()
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(TestConventionsTest::isTestMethod)
            .map(JavaMethod::getFullName)
            .filter(name -> !name.substring(name.lastIndexOf('.') + 1, name.indexOf('(')).matches(TEST_NAME_PATTERN))
            .toList();

        assertThat(offenders).as("test methods named should<Result>When<Condition>").isEmpty();
    }

    private static boolean isTestMethod(JavaMethod method) {
        return method.isAnnotatedWith("org.junit.jupiter.api.Test")
            || method.isAnnotatedWith("org.junit.jupiter.params.ParameterizedTest");
    }
}
