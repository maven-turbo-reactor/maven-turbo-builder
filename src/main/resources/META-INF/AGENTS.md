# Maven Turbo Builder: notes for AI agents

This file is for AI coding agents working in a project that uses the Maven Turbo Builder extension
(`com.github.seregamorph:maven-turbo-builder`). Docs: https://github.com/maven-turbo-reactor/maven-turbo-builder

## How to tell if it is active

- `.mvn/extensions.xml` declares `com.github.seregamorph:maven-turbo-builder`.
- The builder is selected with `-b turbo` (or `-bturbo` in `.mvn/maven.config`). With `mvnd`, use
  `-Dmvnd.builder=turbo`. Without the builder flag, the extension does nothing and Maven builds as usual.
- The build log shows `Turbo builder: package and test phases are reordered 🏎`.

## What changes compared to a standard Maven build

1. **`package` runs before tests.** In each module, `prepare-package` and `package` run before the
   `*test*` phases (`generate-test-sources` ... `test`). The jar is built from main classes before test
   classes are compiled.
2. **Downstream modules start early.** A module starts as soon as its upstream modules finish `package`. It
   does not wait for their `test`, `integration-test`, `verify`, `install`, or `deploy`. So a downstream
   module can build while an upstream module still runs its tests.
3. **Priority scheduling.** Modules with more downstream dependencies are built first.

Check the real phase order of a module:
```shell
mvn org.codehaus.mojo:buildplan-maven-plugin:list -b turbo
```

## Implications when you edit the build

- **A plugin bound to `package` (or `prepare-package`) cannot use test output.** Test classes, surefire
  reports and `jacoco.exec` do not exist yet at that point. Bind such goals to `verify` or a later phase.
- **`test-jar` is handled automatically.** If a module runs `maven-jar-plugin:test-jar`, test compilation
  stays before `package` and only the `test` phase moves after it. To get this order in all modules, pass
  `-DturboTestCompile=true`.
- **A module that reads files written by upstream tests** (for example `jacoco:report-aggregate`) must
  wait for the full upstream build. Set this in that module's `pom.xml`:
  ```xml
  <properties>
      <turboWaitFullUpstream>true</turboWaitFullUpstream>
  </properties>
  ```
- **A module whose downstream modules must wait for all its phases**: set `skipTurboSignal` to `true` in
  its `<properties>`.
- `-DturboWaitFullUpstream=true` or `-DskipTurboSignal=true` on the command line applies to all modules.
- `-am` (`--also-make`) and `-rf` (`--resume-from`) work as usual.

## Troubleshooting

If a build passes with the default builder but fails with `-b turbo`, the cause is usually one of these:

- A goal bound to `package` or earlier needs test output. Move it to `verify` or later.
- A downstream module reads files that an upstream module writes after `package`. Set
  `turboWaitFullUpstream` in the downstream module.
- A downstream module depends on an upstream artifact that is attached after `package`. Set
  `skipTurboSignal` in the upstream module.

To confirm that the turbo builder causes the failure, run the same command without `-b turbo` (or with
`-b multithreaded` if `.mvn/maven.config` sets `-bturbo`). Do not remove the extension to fix a build
without asking the user.
