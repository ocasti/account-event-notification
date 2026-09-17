package co.cobre.notifications;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces, for this module's test code (including {@code boot}, {@code acceptance} and
 * {@code architecture}), the conventions the application tests are written under: exact-value
 * stubs and verifications (no lax Mockito matchers), no reflection, and test names shaped as
 * {@code should<Result>When<Condition>}.
 */
class TestConventionsTest {

    private static final String NAMING_CONVENTION = "should[A-Z]\\w*When[A-Z]\\w*";

    private static final DescribedPredicate<JavaMethodCall> CALLS_LAX_MOCKITO_MATCHER = DescribedPredicate.describe(
        "calls a lax Mockito argument matcher",
        call -> {
            var owner = call.getTarget().getOwner().getFullName();
            var name = call.getTarget().getName();
            var isMockitoMatchersOwner = "org.mockito.ArgumentMatchers".equals(owner) || "org.mockito.Mockito".equals(owner);
            var isLaxMatcherName = name.startsWith("any") || "isA".equals(name) || "argThat".equals(name);
            return isMockitoMatchersOwner && isLaxMatcherName;
        });

    private static final JavaClasses testClasses = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
        .importPackages("co.cobre.notifications");

    @Test
    void shouldRejectLaxMatcherWhenTestCallsMockitoArgumentMatchers() {
        ArchRule rule = noClasses().should().callMethodWhere(CALLS_LAX_MOCKITO_MATCHER);

        rule.check(testClasses);
    }

    @Test
    void shouldRejectReflectionWhenTestCodeDependsOnReflectionApi() {
        ArchRule rule = noClasses()
            .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.test.util.ReflectionTestUtils");

        rule.check(testClasses);
    }

    @Test
    void shouldFollowNamingConventionWhenMethodIsAnnotatedWithTest() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(Test.class)
            .should().haveNameMatching(NAMING_CONVENTION);

        rule.check(testClasses);
    }

    @Test
    void shouldFollowNamingConventionWhenMethodIsAnnotatedWithParameterizedTest() {
        ArchRule rule = methods()
            .that().areAnnotatedWith(ParameterizedTest.class)
            .should().haveNameMatching(NAMING_CONVENTION);

        rule.check(testClasses);
    }
}
