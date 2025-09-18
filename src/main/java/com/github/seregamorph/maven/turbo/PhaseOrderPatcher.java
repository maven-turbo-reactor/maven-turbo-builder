package com.github.seregamorph.maven.turbo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * @author Sergey Chernov
 */
class PhaseOrderPatcher {
/*
    If test-jar is not supported:
    original phases of the default lifecycle [
        "validate",
        "initialize",
        "generate-sources",
        "process-sources",
        "generate-resources",
        "process-resources",
        "compile",
        "process-classes",

     |==>
     |  "generate-test-sources",
     |  "process-test-sources",
     |  "generate-test-resources",
     |  "process-test-resources",
     |  "test-compile",
     |  "process-test-classes",
     |  "test",
     |
     |  // moved before "*test*" phases
     |= "prepare-package",
     |= "package",

        "pre-integration-test",
        "integration-test",
        "post-integration-test",
        "verify",
        "install",
        "deploy"
    ]

    If test-jar is supported
    original phases of the default lifecycle [
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
     |==>
     |  "test",
     |
     |  // moved before "test" phase
     |= "prepare-package",
     |= "package",

        "pre-integration-test",
        "integration-test",
        "post-integration-test",
        "verify",
        "install",
        "deploy"
    ]
*/

    /**
     * Reorders Maven DefaultLifecycles (List of String phases) - for Maven 3,
     * or List of MojoExecution - for Maven 4
     */
    static <T> void reorderPhases(TurboBuilderConfig config, List<T> phaseItems, Function<T, String> phaseExtractor) {
        List<T> packageItems = new ArrayList<>();
        int firstTestItemIndex = -1;
        for (int i = 0; i < phaseItems.size(); i++) {
            T lifecycleItem = phaseItems.get(i);
            String lifecyclePhase = phaseExtractor.apply(lifecycleItem);
            if (firstTestItemIndex < 0
                && (config.isTurboTestCompile() ? isTest(lifecyclePhase) : isAnyTest(lifecyclePhase))) {
                firstTestItemIndex = i;
            }
            if (isPackage(lifecyclePhase)) {
                packageItems.add(lifecycleItem);
            }
        }
        // the list of MojoExecution may miss package items
        if (firstTestItemIndex > -1) {
            phaseItems.removeAll(packageItems);
            phaseItems.addAll(firstTestItemIndex, packageItems);
        }
    }

    static boolean isPackage(String phase) {
        return Arrays.asList("prepare-package", "package")
            .contains(phase);
    }

    private static boolean isTest(String phase) {
        // "test"
        return "test".equals(phase);
    }

    static boolean isAnyTest(String phase) {
        // Before Maven 4
        // "generate-test-sources", "process-test-sources", "generate-test-resources", "process-test-resources",
        // "test-compile", "process-test-classes", "test", "pre-integration-test", "integration-test",
        // "post-integration-test"
        // Since Maven 4 also:
        // "after:resources", "after:test-resources"
        return "test".equals(phase)
            || phase.contains(":test") // since maven 4
            || phase.contains("-test-")
            || phase.startsWith("test-")
            || phase.endsWith("-test");
    }

    private PhaseOrderPatcher() {
    }
}
