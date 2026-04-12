package com.github.seregamorph.maven.turbo;

import java.util.Objects;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;

/**
 * @author Sergey Chernov
 */
final class MojoUtils {

    static String getMojoPhase(MojoExecution mojoExecution) {
        String phase = mojoExecution.getLifecyclePhase();
        if (phase == null) {
            MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
            if (mojoDescriptor != null) {
                phase = mojoDescriptor.getPhase();
            }
        }
        return Objects.toString(phase, "none");
    }

    private MojoUtils() {
    }
}
