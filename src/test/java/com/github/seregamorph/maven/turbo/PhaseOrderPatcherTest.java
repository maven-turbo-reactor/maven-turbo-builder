package com.github.seregamorph.maven.turbo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * @author Sergey Chernov
 */
class PhaseOrderPatcherTest {

    private static final List<String> originalMaven3Phases = List.of(
        "validate",
        "initialize",
        "generate-sources",
        "process-sources",
        "generate-resources",
        "process-resources",
        "compile",
        "process-classes",
        "generate-test-sources",
        "process-test-sources",
        "generate-test-resources",
        "process-test-resources",
        "test-compile",
        "process-test-classes",
        "test",
        "prepare-package",
        "package",
        "pre-integration-test",
        "integration-test",
        "post-integration-test",
        "verify",
        "install",
        "deploy"
    );

    @Test
    public void shouldReorderMaven3PhasesNoTestJarSupported() {
        var phases = new ArrayList<>(originalMaven3Phases);
        var beforeReorderingPhases1 = PhaseOrderPatcher.reorderPhases(false, phases, Function.identity());
        var reorderedMaven3Phases = List.of(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",
            "prepare-package",
            "package",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy"
        );
        assertEquals(originalMaven3Phases, beforeReorderingPhases1);
        assertEquals(reorderedMaven3Phases, phases);
        // repeated reorder should be no-op
        var beforeReorderingPhases2 = PhaseOrderPatcher.reorderPhases(false, phases, Function.identity());
        assertEquals(reorderedMaven3Phases, beforeReorderingPhases2);
        assertEquals(reorderedMaven3Phases, phases);
    }

    @Test
    public void shouldReorderMaven3PhasesTestJarSupported() {
        var phases = new ArrayList<>(originalMaven3Phases);
        var beforeReorderingPhases1 = PhaseOrderPatcher.reorderPhases(true, phases, Function.identity());
        var reorderedMaven3Phases = List.of(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "prepare-package",
            "package",
            "test",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy"
        );
        assertEquals(originalMaven3Phases, beforeReorderingPhases1);
        assertEquals(reorderedMaven3Phases, phases);
        // repeated reorder should be no-op
        var beforeReorderingPhases2 = PhaseOrderPatcher.reorderPhases(true, phases, Function.identity());
        assertEquals(reorderedMaven3Phases, beforeReorderingPhases2);
        assertEquals(reorderedMaven3Phases, phases);
    }

    private static final List<String> originalMaven4Phases = List.of(
        "before:clean",
        "clean",
        "after:clean",
        "before:all",
        "before:initialize",
        "before:validate",
        "validate",
        "after:validate",
        "initialize",
        "after:initialize",
        "before:build",
        "before:sources",
        "sources",
        "after:sources",
        "before:resources",
        "resources",
        "after:resources",
        "before:compile",
        "compile",
        "after:compile",
        "before:ready",
        "ready",
        "after:ready",
        "before:test-sources",
        "test-sources",
        "after:test-sources",
        "before:test-resources",
        "test-resources",
        "after:test-resources",
        "before:test-compile",
        "test-compile",
        "after:test-compile",
        "before:test",
        "test",
        "after:test",
        "before:unit-test",
        "unit-test",
        "after:unit-test",
        "before:package",
        "package",
        "after:package",
        "build",
        "after:build",
        "before:verify",
        "before:integration-test",
        "integration-test",
        "after:integration-test",
        "verify",
        "after:verify",
        "before:install",
        "install",
        "after:install",
        "before:deploy",
        "deploy",
        "after:deploy",
        "all",
        "after:all",
        "before:site",
        "site",
        "after:site",
        "before:site-deploy",
        "site-deploy",
        "after:site-deploy"
    );

    @Test
    public void shouldReorderMaven4PhasesNoTestJarSupported() {
        var phases = new ArrayList<>(originalMaven4Phases);
        var beforeReorderingPhases1 = PhaseOrderPatcher.reorderPhases(false, phases, Function.identity());
        var reorderedMaven4Phases = List.of(
            "before:clean",
            "clean",
            "after:clean",
            "before:all",
            "before:initialize",
            "before:validate",
            "validate",
            "after:validate",
            "initialize",
            "after:initialize",
            "before:build",
            "before:sources",
            "sources",
            "after:sources",
            "before:resources",
            "resources",
            "after:resources",
            "before:compile",
            "compile",
            "after:compile",
            "before:ready",
            "ready",
            "after:ready",
            "before:package",
            "package",
            "after:package",
            "before:test-sources",
            "test-sources",
            "after:test-sources",
            "before:test-resources",
            "test-resources",
            "after:test-resources",
            "before:test-compile",
            "test-compile",
            "after:test-compile",
            "before:test",
            "test",
            "after:test",
            "before:unit-test",
            "unit-test",
            "after:unit-test",
            "build",
            "after:build",
            "before:verify",
            "before:integration-test",
            "integration-test",
            "after:integration-test",
            "verify",
            "after:verify",
            "before:install",
            "install",
            "after:install",
            "before:deploy",
            "deploy",
            "after:deploy",
            "all",
            "after:all",
            "before:site",
            "site",
            "after:site",
            "before:site-deploy",
            "site-deploy",
            "after:site-deploy"
        );
        assertEquals(originalMaven4Phases, beforeReorderingPhases1);
        assertEquals(reorderedMaven4Phases, phases);
        // repeated reorder should be no-op
        var beforeReorderingPhases2 = PhaseOrderPatcher.reorderPhases(false, phases, Function.identity());
        assertEquals(reorderedMaven4Phases, beforeReorderingPhases2);
        assertEquals(reorderedMaven4Phases, phases);
    }

    @Test
    public void shouldReorderMaven4PhasesTestJarSupported() {
        var phases = new ArrayList<>(originalMaven4Phases);
        var beforeReorderingPhases1 = PhaseOrderPatcher.reorderPhases(true, phases, Function.identity());
        var reorderedMaven4Phases = List.of(
            "before:clean",
            "clean",
            "after:clean",
            "before:all",
            "before:initialize",
            "before:validate",
            "validate",
            "after:validate",
            "initialize",
            "after:initialize",
            "before:build",
            "before:sources",
            "sources",
            "after:sources",
            "before:resources",
            "resources",
            "after:resources",
            "before:compile",
            "compile",
            "after:compile",
            "before:ready",
            "ready",
            "after:ready",
            "before:test-sources",
            "test-sources",
            "after:test-sources",
            "before:test-resources",
            "test-resources",
            "after:test-resources",
            "before:test-compile",
            "test-compile",
            "after:test-compile",
            "before:package",
            "package",
            "after:package",
            "before:test",
            "test",
            "after:test",
            "before:unit-test",
            "unit-test",
            "after:unit-test",
            "build",
            "after:build",
            "before:verify",
            "before:integration-test",
            "integration-test",
            "after:integration-test",
            "verify",
            "after:verify",
            "before:install",
            "install",
            "after:install",
            "before:deploy",
            "deploy",
            "after:deploy",
            "all",
            "after:all",
            "before:site",
            "site",
            "after:site",
            "before:site-deploy",
            "site-deploy",
            "after:site-deploy"
        );
        assertEquals(originalMaven4Phases, beforeReorderingPhases1);
        assertEquals(reorderedMaven4Phases, phases);
        // repeated reorder should be no-op
        var beforeReorderingPhases2 = PhaseOrderPatcher.reorderPhases(true, phases, Function.identity());
        assertEquals(reorderedMaven4Phases, beforeReorderingPhases2);
        assertEquals(reorderedMaven4Phases, phases);
    }
}
