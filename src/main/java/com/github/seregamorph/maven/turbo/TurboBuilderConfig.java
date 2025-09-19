package com.github.seregamorph.maven.turbo;

import org.apache.maven.execution.MavenSession;

/**
 * @author Sergey Chernov
 */
public class TurboBuilderConfig {

    private final boolean turboTestCompile;

    static TurboBuilderConfig fromSession(MavenSession session) {
        String turboTestCompile = MavenPropertyUtils.getProperty(session, "turboTestCompile");
        return new TurboBuilderConfig(MavenPropertyUtils.isTrue(turboTestCompile));
    }

    TurboBuilderConfig(boolean turboTestCompile) {
        this.turboTestCompile = turboTestCompile;
    }

    public boolean isTurboTestCompile() {
        return turboTestCompile;
    }

    @Override
    public String toString() {
        return "TurboBuilderConfig{" +
            "turboTestCompile=" + turboTestCompile +
            '}';
    }
}
