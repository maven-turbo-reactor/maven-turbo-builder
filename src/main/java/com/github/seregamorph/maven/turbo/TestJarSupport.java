package com.github.seregamorph.maven.turbo;

import java.util.List;
import org.apache.maven.plugin.MojoExecution;

/**
 * @author Sergey Chernov
 */
class TestJarSupport {

    static boolean hasTestJar(List<MojoExecution> mojoExecutions) {
        // test-jar is a special case, because package phase is now executed before compiling tests;
        // hence make an exception
        for (MojoExecution mojoExecution : mojoExecutions) {
            if ("org.apache.maven.plugins".equals(mojoExecution.getGroupId())
                && "maven-jar-plugin".equals(mojoExecution.getArtifactId())
                && "test-jar".equals(mojoExecution.getGoal())) {
                return true;
            }
        }
        return false;
    }

    private TestJarSupport() {
    }
}
