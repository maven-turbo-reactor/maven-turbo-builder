package com.github.seregamorph.maven.turbo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Chernov
 */
class DefaultLifecyclePatcher {
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
*/

    static void patchDefaultLifecycle(TurboBuilderConfig config, List<String> phases) {
        List<String> packagePhases = new ArrayList<>();
        int firstTestPhaseIndex = -1;
        for (int i = 0; i < phases.size(); i++) {
            String lifecyclePhase = phases.get(i);
            if (firstTestPhaseIndex < 0
                    && (config.isSupportTestJar() ? isTest(lifecyclePhase) : isAnyTest(lifecyclePhase))) {
                firstTestPhaseIndex = i;
            }
            if (isPackage(lifecyclePhase)) {
                packagePhases.add(lifecyclePhase);
            }
        }
        assert firstTestPhaseIndex != -1;
        phases.removeAll(packagePhases);
        phases.addAll(firstTestPhaseIndex, packagePhases);
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
        // "generate-test-sources", "process-test-sources", "generate-test-resources", "process-test-resources",
        // "test-compile", "process-test-classes", "test", "pre-integration-test", "integration-test",
        // "post-integration-test"
        return "test".equals(phase)
                || phase.contains("-test-")
                || phase.startsWith("test-")
                || phase.endsWith("-test");
    }

    private DefaultLifecyclePatcher() {
    }
}
