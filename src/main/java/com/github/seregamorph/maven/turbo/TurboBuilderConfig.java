package com.github.seregamorph.maven.turbo;

import org.apache.maven.execution.MavenSession;

import javax.inject.Inject;

/**
 * @author Sergey Chernov
 */
public class TurboBuilderConfig {

    private final boolean supportTestJar;

    @Inject
    public TurboBuilderConfig(MavenSession session) {
        String supportTestJar = session.getSystemProperties().getProperty("supportTestJar");
        this.supportTestJar = MavenPropertyUtils.isEmptyOrTrue(supportTestJar);
    }

    TurboBuilderConfig(boolean supportTestJar) {
        this.supportTestJar = supportTestJar;
    }

    public boolean isSupportTestJar() {
        return supportTestJar;
    }

    @Override
    public String toString() {
        return "TurboBuilderConfig{" +
            "supportTestJar=" + supportTestJar +
            '}';
    }
}
