/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.jpackage.test;

import static jdk.jpackage.test.TKit.assertAssert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.PackageTest.PackageHandlers;
import jdk.jpackage.test.RunnablePackageTest.Action;

public class PackageTestTest extends JUnitAdapter {

    private interface Verifiable {
        void verify();
    }

    enum Callback {
        ONCE(1),
        TWICE(2),
        NEVER(0);

        Callback(int tickCount) {
            this.tickCount = tickCount;
        }

        RecordingConsumer createInitializer() {
            return new RecordingConsumer(tickCount, "init");
        }

        RecordingInstaller createInstaller(int exitCode) {
            return new RecordingInstaller(tickCount, exitCode);
        }

        RecordingConsumer createUninstaller() {
            return new RecordingConsumer(tickCount, "uninstall");
        }

        RecordingConsumer createInstallVerifier() {
            return new RecordingConsumer(tickCount, "on-install");
        }

        RecordingConsumer createUninstallVerifier() {
            return new RecordingConsumer(tickCount, "on-uninstall");
        }

        RecordingUnpacker createUnpacker() {
            return new RecordingUnpacker(tickCount);
        }

        private final int tickCount;
    }

    private final static int ERROR_EXIT_CODE_JPACKAGE = 35;
    private final static int ERROR_EXIT_CODE_INSTALL = 27;

    enum BundleVerifier implements BiConsumer<PackageTest, Consumer<Verifiable>> {
        ONCE_SUCCESS,
        ONCE_FAIL,
        NEVER,
        ONCE_SUCCESS_EXIT_CODE,
        ONCE_FAIL_EXIT_CODE,
        NEVER_EXIT_CODE;

        @Override
        public void accept(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            final int expectedTickCount;
            if (Set.of(NEVER, NEVER_EXIT_CODE).contains(this)) {
                expectedTickCount = 0;
            } else {
                expectedTickCount = 1;
            }

            if (Set.of(ONCE_SUCCESS, ONCE_FAIL, NEVER).contains(this)) {
                final var verifier = new RecordingConsumer(expectedTickCount, "on-bundle");
                test.addBundleVerifier(verifier::accept);
                verifiableAccumulator.accept(verifier);
            } else {
                final int jpackageExitCode;
                if (this == ONCE_FAIL_EXIT_CODE) {
                    jpackageExitCode = ERROR_EXIT_CODE_JPACKAGE;
                } else {
                    jpackageExitCode = 0;
                }
                final var verifier = new RecordingBundleVerifier(expectedTickCount, jpackageExitCode);
                test.addBundleVerifier(verifier);
                verifiableAccumulator.accept(verifier);
            }
        }
    }

    private static class TickCounter implements Verifiable {

        TickCounter(int expectedTicks) {
            this.expectedTicks = expectedTicks;
        }

        void tick() {
            ticks++;
        }

        @Override
        public void verify() {
            switch (expectedTicks) {
                case 0 -> {
                    TKit.assertEquals(expectedTicks, ticks, String.format("%s: never called", this));
                }
                case 1 -> {
                    TKit.assertEquals(expectedTicks, ticks, String.format("%s: called once", this));
                }
                case 2 -> {
                    TKit.assertEquals(expectedTicks, ticks, String.format("%s: called twice", this));
                }
                default -> {
                    TKit.assertEquals(expectedTicks, ticks, toString());
                }
            }
        }

        static String getDescription(TickCounter o) {
            return "tk=" + o.expectedTicks;
        }

        private int ticks;
        protected final int expectedTicks;
    }

    private final static class RecordingConsumer extends TickCounter implements ThrowingConsumer<JPackageCommand> {

        @Override
        public void accept(JPackageCommand cmd) {
            tick();
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", label, TickCounter.getDescription(this));
        }

        RecordingConsumer(int expectedTicks, String label) {
            super(expectedTicks);
            this.label = Objects.requireNonNull(label);
        }

        private final String label;
    }

    private final static class RecordingBundleVerifier extends TickCounter implements ThrowingBiConsumer<JPackageCommand, Executor.Result> {

        @Override
        public void accept(JPackageCommand cmd, Executor.Result result) {
            tick();
            jpackageExitCode = result.exitCode();
        }

        @Override
        public void verify() {
            super.verify();
            if (expectedTicks > 0) {
                TKit.assertEquals(expectedJPackageExitCode, jpackageExitCode, String.format("%s: run jpackage", this));
            }
        }

        @Override
        public String toString() {
            return String.format("on-bundle-ex(exit=%d, %s)", expectedJPackageExitCode, TickCounter.getDescription(this));
        }

        RecordingBundleVerifier(int expectedTicks, int expectedJPackageExitCode) {
            super(expectedTicks);
            this.expectedJPackageExitCode = expectedJPackageExitCode;
        }

        private int jpackageExitCode;
        private final int expectedJPackageExitCode;
    }

    private final static class RecordingInstaller extends TickCounter implements Function<JPackageCommand, Integer> {

        @Override
        public Integer apply(JPackageCommand cmd) {
            tick();
            return exitCode;
        }

        @Override
        public String toString() {
            return String.format("install(exit=%d, %s)", exitCode, TickCounter.getDescription(this));
        }

        RecordingInstaller(int expectedTicks, int exitCode) {
            super(expectedTicks);
            this.exitCode = exitCode;
        }

        private final int exitCode;
    }

    private final static class RecordingUnpacker extends TickCounter implements BiFunction<JPackageCommand, Path, Path> {

        @Override
        public Path apply(JPackageCommand cmd, Path path) {
            tick();
            try {
                Files.createDirectories(path.resolve("mockup-installdir"));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return path;
        }

        @Override
        public String toString() {
            return String.format("unpack(%s)", TickCounter.getDescription(this));
        }

        RecordingUnpacker(int expectedTicks) {
            super(expectedTicks);
        }
    }

    record PackageHandlersSpec(Callback install, Callback uninstall,
            Optional<Callback> unpack, int installExitCode) {
        PackageHandlers createPackageHandlers(Consumer<Verifiable> verifiableAccumulator) {
            final var installer = install.createInstaller(installExitCode);
            final var uninstaller = uninstall.createUninstaller();
            final var unpacker = unpack.map(u -> u.createUnpacker());

            List.of(installer, uninstaller).forEach(verifiableAccumulator::accept);
            unpacker.ifPresent(verifiableAccumulator::accept);

            return new PackageHandlers(installer, uninstaller::accept, unpacker);
        }
    }

    record TestSpec(PackageType type, PackageHandlersSpec handlersSpec,
            List<Callback> initializers, List<BundleVerifier> bundleVerifiers,
            List<Callback> installVerifiers, List<Callback> uninstallVerifiers,
            int expectedJPackageExitCode, int actualJPackageExitCode, List<Action> actions) {

        PackageTest createTest(Consumer<Verifiable> verifiableAccumulator) {
            final var handlers = handlersSpec.createPackageHandlers(verifiableAccumulator);
            return new PackageTest(packageType -> true, Map.of(type, handlers), () -> {
                return new JPackageCommand() {
                    @Override
                    public Path outputBundle() {
                        return outputDir().resolve("mockup-bundle");
                    }

                    @Override
                    public PackageType packageType() {
                        return null;
                    }

                    @Override
                    JPackageCommand assertAppLayout() {
                        return this;
                    }

                    @Override
                    JPackageCommand createImmutableCopy() {
                        return this;
                    }

                    @Override
                    public void verifyIsOfType(PackageType ... types) {
                    }

                    @Override
                    public String getPrintableCommandLine() {
                        return "'mockup jpackage'";
                    }

                    @Override
                    public Executor.Result execute(int expectedExitCode) {
                        final var outputBundle = outputBundle();
                        try {
                            Files.createDirectories(outputBundle.getParent());
                            if (actualJPackageExitCode == 0) {
                                Files.createFile(outputBundle);
                            }
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                        return new Executor.Result(actualJPackageExitCode, null,
                                this::getPrintableCommandLine).assertExitCodeIs(expectedExitCode);
                    }
                };
            }).setExpectedExitCode(expectedJPackageExitCode).setExpectedInstallExitCode(handlersSpec.installExitCode);
        }

        void configureInitializers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var initializerSpec : initializers) {
                final var initializer = initializerSpec.createInitializer();
                verifiableAccumulator.accept(initializer);
                test.addInitializer(initializer);
            }
        }

        void configureBundleVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifierSpec : bundleVerifiers) {
                verifierSpec.accept(test, verifiableAccumulator);
            }
        }

        void configureInstallVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifierSpec : installVerifiers) {
                final var verifier = verifierSpec.createInstallVerifier();
                verifiableAccumulator.accept(verifier);
                test.addInstallVerifier(verifier);
            }
        }

        void configureUninstallVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifierSpec : uninstallVerifiers) {
                final var verifier = verifierSpec.createUninstallVerifier();
                verifiableAccumulator.accept(verifier);
                test.addUninstallVerifier(verifier);
            }
        }

        void run(PackageTest test) {
            final boolean expectedSuccess = (expectedJPackageExitCode == actualJPackageExitCode);

            assertAssert(expectedSuccess, () -> {
                test.run(actions.toArray(Action[]::new));
            });
        }

        void run(Optional<Consumer<PackageTest>> customConfigure) {
            final List<Verifiable> verifiers = new ArrayList<>();

            final var test = createTest(verifiers::add);
            test.forTypes(type);
            configureInitializers(test, verifiers::add);
            configureBundleVerifiers(test, verifiers::add);
            configureInstallVerifiers(test, verifiers::add);
            configureUninstallVerifiers(test, verifiers::add);
            customConfigure.ifPresent(callback -> callback.accept(test));
            run(test);
            verifiers.forEach(Verifiable::verify);
        }
    }

    private final static class TestSpecBuilder {

        TestSpecBuilder type(PackageType v) {
            type = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder install(Callback v) {
            install = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder uninstall(Callback v) {
            uninstall = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder unpack(Callback v) {
            unpack = v;
            return this;
        }

        TestSpecBuilder installExitCode(int v) {
            installExitCode = v;
            return this;
        }

        TestSpecBuilder jpackageExitCode(int v) {
            return expectedJPackageExitCode(v).actualJPackageExitCode(v);
        }

        TestSpecBuilder expectedJPackageExitCode(int v) {
            expectedJPackageExitCode = v;
            return this;
        }

        TestSpecBuilder actualJPackageExitCode(int v) {
            actualJPackageExitCode = v;
            return this;
        }

        TestSpecBuilder addActions(Action... v) {
            actions.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder actions(Action... v) {
            actions.clear();
            return addActions(v);
        }

        TestSpecBuilder doCreateAndUnpack() {
            actions(Action.CREATE_AND_UNPACK);
            install(Callback.NEVER);
            uninstall(Callback.NEVER);
            if (willHaveBundle()) {
                overrideNonNullUnpack(Callback.ONCE);
            } else {
                overrideNonNullUnpack(Callback.NEVER);
            }
            initializers(Callback.ONCE);
            if (expectedJPackageExitCode != actualJPackageExitCode) {
                bundleVerifiers(BundleVerifier.NEVER);
            } else if (expectedJPackageExitCode == 0) {
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS);
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS_EXIT_CODE);
            } else {
                bundleVerifiers(BundleVerifier.ONCE_FAIL);
                if (expectedJPackageExitCode == ERROR_EXIT_CODE_JPACKAGE) {
                    bundleVerifiers(BundleVerifier.ONCE_FAIL_EXIT_CODE);
                }
            }
            uninstallVerifiers(Callback.NEVER);
            if (willVerifyUnpack()) {
                installVerifiers(Callback.ONCE);
            } else {
                installVerifiers(Callback.NEVER);
            }
            return this;
        }

        TestSpecBuilder doCreateUnpackInstallUninstall() {
            actions(Action.CREATE, Action.UNPACK, Action.VERIFY_INSTALL, Action.INSTALL,
                    Action.VERIFY_INSTALL, Action.UNINSTALL, Action.VERIFY_UNINSTALL);
            initializers(Callback.ONCE);
            uninstallVerifiers(Callback.NEVER);
            if (willHaveBundle()) {
                overrideNonNullUnpack(Callback.ONCE);
                install(Callback.ONCE);
                if (installExitCode == 0) {
                    uninstall(Callback.ONCE);
                    uninstallVerifiers(Callback.ONCE);
                } else {
                    uninstall(Callback.NEVER);
                }
            } else {
                overrideNonNullUnpack(Callback.NEVER);
                install(Callback.NEVER);
                uninstall(Callback.NEVER);
            }

            if (expectedJPackageExitCode != actualJPackageExitCode) {
                bundleVerifiers(BundleVerifier.NEVER);
                installVerifiers(Callback.NEVER);
            } else if (expectedJPackageExitCode == 0) {
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS);
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS_EXIT_CODE);
                if (installExitCode == 0) {
                    if (willVerifyUnpack()) {
                        installVerifiers(Callback.TWICE);
                    } else {
                        installVerifiers(Callback.ONCE);
                    }
                } else {
                    if (willVerifyUnpack()) {
                        installVerifiers(Callback.ONCE);
                    } else {
                        installVerifiers(Callback.NEVER);
                    }
                }
            } else {
                bundleVerifiers(BundleVerifier.ONCE_FAIL);
                if (expectedJPackageExitCode == ERROR_EXIT_CODE_JPACKAGE) {
                    bundleVerifiers(BundleVerifier.ONCE_FAIL_EXIT_CODE);
                }
                installVerifiers(Callback.NEVER);
            }
            return this;
        }

        TestSpecBuilder addInitializers(Callback... v) {
            initializers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addBundleVerifiers(BundleVerifier... v) {
            bundleVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addInstallVerifiers(Callback... v) {
            installVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addUninstallVerifiers(Callback... v) {
            uninstallVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder initializers(Callback... v) {
            initializers.clear();
            return addInitializers(v);
        }

        TestSpecBuilder bundleVerifiers(BundleVerifier... v) {
            bundleVerifiers.clear();
            return addBundleVerifiers(v);
        }

        TestSpecBuilder installVerifiers(Callback... v) {
            installVerifiers.clear();
            return addInstallVerifiers(v);
        }

        TestSpecBuilder uninstallVerifiers(Callback... v) {
            uninstallVerifiers.clear();
            return addUninstallVerifiers(v);
        }

        TestSpec create() {
            final var handlersSpec = new PackageHandlersSpec(install, uninstall,
                    Optional.ofNullable(unpack), installExitCode);
            return new TestSpec(type, handlersSpec, initializers, bundleVerifiers,
                    installVerifiers, uninstallVerifiers, expectedJPackageExitCode,
                    actualJPackageExitCode, actions);
        }

        boolean willVerifyCreate() {
            return actions.contains(Action.CREATE) && actualJPackageExitCode == 0 && expectedJPackageExitCode == actualJPackageExitCode;
        }

        boolean willHaveBundle() {
            return !actions.contains(Action.CREATE) || willVerifyCreate();
        }

        boolean willVerifyUnpack() {
            return actions.contains(Action.UNPACK) && willHaveBundle() && unpack != null;
        }

        boolean willVerifyInstall() {
            return (actions.contains(Action.INSTALL) && installExitCode == 0) && willHaveBundle();
        }

        private void overrideNonNullUnpack(Callback v) {
            if (unpack != null) {
                unpack(v);
            }
        }

        private PackageType type = PackageType.LINUX_RPM;
        private Callback install = Callback.ONCE;
        private Callback uninstall = Callback.ONCE;
        private Callback unpack = Callback.ONCE;
        private int installExitCode;
        private final List<Callback> initializers = new ArrayList<>();
        private final List<BundleVerifier> bundleVerifiers = new ArrayList<>();
        private final List<Callback> installVerifiers = new ArrayList<>();
        private final List<Callback> uninstallVerifiers = new ArrayList<>();
        private int expectedJPackageExitCode;
        private int actualJPackageExitCode;
        private final List<Action> actions = new ArrayList<>();
    }

    @Test
    @ParameterSupplier("test")
    public void test(TestSpec spec) {
        spec.run(Optional.empty());
    }

    public static List<Object[]> test() {
        List<TestSpec> data = new ArrayList<>();

        for (boolean withUnpack : List.of(false, true)) {
            for (int actualJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_INSTALL)) {
                for (int expectedJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_INSTALL)) {
                    data.add(new TestSpecBuilder()
                            .unpack(withUnpack ? Callback.ONCE : null)
                            .actualJPackageExitCode(actualJPackageExitCode)
                            .expectedJPackageExitCode(expectedJPackageExitCode)
                            .doCreateAndUnpack().create());
                }
            }
        }

        for (boolean withUnpack : List.of(false, true)) {
            for (int installExitCode : List.of(0, 1, ERROR_EXIT_CODE_INSTALL)) {
                for (int actualJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_JPACKAGE)) {
                    for (int expectedJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_JPACKAGE)) {
                        data.add(new TestSpecBuilder()
                                .unpack(withUnpack ? Callback.ONCE : null)
                                .installExitCode(installExitCode)
                                .actualJPackageExitCode(actualJPackageExitCode)
                                .expectedJPackageExitCode(expectedJPackageExitCode)
                                .doCreateUnpackInstallUninstall().create());
                    }
                }
            }
        }

        data.add(new TestSpecBuilder()
                .actions(Action.VERIFY_INSTALL, Action.UNINSTALL, Action.VERIFY_INSTALL, Action.VERIFY_UNINSTALL)
                .install(Callback.NEVER)
                .unpack(Callback.NEVER)
                .uninstall(Callback.ONCE)
                .initializers(Callback.ONCE)
                .bundleVerifiers(BundleVerifier.NEVER)
                .installVerifiers(Callback.TWICE)
                .uninstallVerifiers(Callback.ONCE)
                .create());

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    @Test
    @ParameterSupplier("testDisableInstallerUninstaller")
    public void testDisableInstallerUninstaller(TestSpec spec, boolean disableInstaller, boolean disableUninstaller) {
        spec.run(Optional.of(test -> {
            if (disableInstaller) {
                test.disablePackageInstaller();
            }
            if (disableUninstaller) {
                test.disablePackageUninstaller();
            }
        }));
    }

    public static List<Object[]> testDisableInstallerUninstaller() {
        List<Object[]> data = new ArrayList<>();

        for (boolean disableInstaller : List.of(true, false)) {
            for (boolean disableUninstaller : List.of(true, false)) {
                if (disableInstaller || disableUninstaller) {
                    final var builder = new TestSpecBuilder().doCreateUnpackInstallUninstall();
                    if (disableInstaller) {
                        builder.install(Callback.NEVER);
                    }
                    if (disableUninstaller) {
                        builder.uninstall(Callback.NEVER);
                    }
                    data.add(new Object[] { builder.create(), disableInstaller, disableUninstaller });
                }
            }
        }

        return data;
    }
}
