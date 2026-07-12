package com.example.inventoryquest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture rules. These encode the readme's structural promises: constructor
 * injection only, a clean presentation/orchestration/domain layering, and package-by-feature with
 * no cyclic entanglement between features.
 */
class ArchitectureTest {

    private static final String ROOT = "com.example.inventoryquest";
    private static final String[] DOMAIN_FEATURES = {
            "..item..", "..inventory..", "..mountain..", "..player..",
            "..crafting..", "..combat..", "..trade.."
    };

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void constructorInjectionOnly_noAutowiredFields() {
        noFields().should().beAnnotatedWith(Autowired.class)
                .as("use constructor injection, never field @Autowired")
                .check(classes);
    }

    @Test
    void controllersDoNotTouchRepositoriesDirectly() {
        noClasses().that().resideInAPackage(ROOT + ".web..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .as("controllers go through services, not repositories")
                .check(classes);
    }

    @Test
    void domainFeaturesDoNotDependOnOrchestrationOrWeb() {
        // Scoped to our own packages — Spring's org.springframework.web.* is not our web layer.
        noClasses().that().resideInAnyPackage(DOMAIN_FEATURES)
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".web..", ROOT + ".game..")
                .as("features stay below the game/web layers")
                .check(classes);
    }

    @Test
    void featurePackagesAreFreeOfCycles() {
        slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
