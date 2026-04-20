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
    public void shouldReorderPhasesNoTestJarSupported() {
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
    public void shouldReorderPhasesTestJarSupported() {
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
}
