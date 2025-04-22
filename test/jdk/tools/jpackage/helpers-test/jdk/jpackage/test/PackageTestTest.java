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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.PackageTest.PackageHandlers;
import jdk.jpackage.test.RunnablePackageTest.Action;

public class PackageTestTest extends JUnitAdapter {

    private interface Verifiable {
        void verify();
    }

    private static class CallbackFactory {

        CallbackFactory(int tickCount) {
            this.tickCount = tickCount;
        }

        CountingInstaller createInstaller(int exitCode) {
            return new CountingInstaller(tickCount, exitCode);
        }

        CountingConsumer createUninstaller() {
            return new CountingConsumer(tickCount, "uninstall");
        }

        CountingUnpacker createUnpacker() {
            return new CountingUnpacker(tickCount);
        }

        CountingConsumer createInitializer() {
            return new CountingConsumer(tickCount, "init");
        }

        CountingRunnable createRunOnceInitializer() {
            return new CountingRunnable(tickCount, "once-init");
        }

        CountingConsumer createInstallVerifier() {
            return new CountingConsumer(tickCount, "on-install");
        }

        CountingConsumer createUninstallVerifier() {
            return new CountingConsumer(tickCount, "on-uninstall");
        }

        CountingConsumer createBundleVerifier() {
            return new CountingConsumer(tickCount, "on-bundle");
        }

        CountingBundleVerifier createBundleVerifier(int jpackageExitCode) {
            return new CountingBundleVerifier(tickCount, jpackageExitCode);
        }

        private final int tickCount;
    }

    private static final int ERROR_EXIT_CODE_JPACKAGE = 35;
    private static final int ERROR_EXIT_CODE_INSTALL = 27;

    private static final CallbackFactory NEVER = new CallbackFactory(0);
    private static final CallbackFactory ONCE = new CallbackFactory(1);
    private static final CallbackFactory TWICE = new CallbackFactory(2);

    enum BundleVerifier {
        ONCE_SUCCESS(ONCE),
        ONCE_FAIL(ONCE),
        NEVER(PackageTestTest.NEVER),
        ONCE_SUCCESS_EXIT_CODE(ONCE, 0),
        ONCE_FAIL_EXIT_CODE(ONCE, ERROR_EXIT_CODE_JPACKAGE),
        NEVER_EXIT_CODE(PackageTestTest.NEVER, 0);

        BundleVerifier(CallbackFactory factory) {
            specSupplier = () -> new BundleVerifierSpec(Optional.of(factory.createBundleVerifier()), Optional.empty());
        }

        BundleVerifier(CallbackFactory factory, int jpackageExitCode) {
            specSupplier = () -> new BundleVerifierSpec(Optional.empty(),
                    Optional.of(factory.createBundleVerifier(jpackageExitCode)));
        }

        BundleVerifierSpec spec() {
            return specSupplier.get();
        }

        private final Supplier<BundleVerifierSpec> specSupplier;
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

        protected int tickCount() {
            return ticks;
        }

        protected static String getDescription(TickCounter o) {
            return "tk=" + o.expectedTicks;
        }

        private int ticks;
        protected final int expectedTicks;
    }

    private static class CountingConsumer extends TickCounter implements ThrowingConsumer<JPackageCommand> {

        @Override
        public void accept(JPackageCommand cmd) {
            tick();
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", label, TickCounter.getDescription(this));
        }

        CountingConsumer(int expectedTicks, String label) {
            super(expectedTicks);
            this.label = Objects.requireNonNull(label);
        }

        private final String label;
    }

    private static class CountingRunnable extends TickCounter implements ThrowingRunnable {

        @Override
        public void run() {
            tick();
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", label, TickCounter.getDescription(this));
        }

        CountingRunnable(int expectedTicks, String label) {
            super(expectedTicks);
            this.label = Objects.requireNonNull(label);
        }

        private final String label;
    }

    private static class CountingBundleVerifier extends TickCounter implements ThrowingBiConsumer<JPackageCommand, Executor.Result> {

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

        CountingBundleVerifier(int expectedTicks, int expectedJPackageExitCode) {
            super(expectedTicks);
            this.expectedJPackageExitCode = expectedJPackageExitCode;
        }

        private int jpackageExitCode;
        private final int expectedJPackageExitCode;
    }

    private static final class CountingInstaller extends TickCounter implements Function<JPackageCommand, Integer> {

        @Override
        public Integer apply(JPackageCommand cmd) {
            tick();
            return exitCode;
        }

        @Override
        public String toString() {
            return String.format("install(exit=%d, %s)", exitCode, TickCounter.getDescription(this));
        }

        CountingInstaller(int expectedTicks, int exitCode) {
            super(expectedTicks);
            this.exitCode = exitCode;
        }

        private final int exitCode;
    }

    private static class CountingUnpacker extends TickCounter implements BiFunction<JPackageCommand, Path, Path> {

        @Override
        public Path apply(JPackageCommand cmd, Path path) {
            tick();
            try {
                Files.createDirectories(path.resolve("mockup-installdir"));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            unpackPaths.add(path);
            return path;
        }

        @Override
        public String toString() {
            return String.format("unpack(%s)", TickCounter.getDescription(this));
        }

        CountingUnpacker(int expectedTicks) {
            super(expectedTicks);
        }

        List<Path> unpackPaths() {
            return unpackPaths;
        }

        private final List<Path> unpackPaths = new ArrayList<>();
    }

    record BundleVerifierSpec(Optional<CountingConsumer> verifier, Optional<CountingBundleVerifier> verifierWithExitCode) {
        BundleVerifierSpec {
            if (verifier.isPresent() == verifierWithExitCode.isPresent()) {
                throw new IllegalArgumentException();
            }
        }

        Verifiable apply(PackageTest test) {
            verifier.ifPresent(test::addBundleVerifier);
            verifierWithExitCode.ifPresent(test::addBundleVerifier);
            return verifier.map(Verifiable.class::cast).orElseGet(verifierWithExitCode::orElseThrow);
        }

        @Override
        public String toString() {
            return verifier.map(Verifiable.class::cast).orElseGet(verifierWithExitCode::orElseThrow).toString();
        }
    }

    record PackageHandlersSpec(CountingInstaller installer, CountingConsumer uninstaller,
            Optional<CountingUnpacker> unpacker, int installExitCode) {

        PackageHandlers createPackageHandlers(Consumer<Verifiable> verifiableAccumulator) {
            List.of(installer, uninstaller).forEach(verifiableAccumulator::accept);
            unpacker.ifPresent(verifiableAccumulator::accept);
            return new PackageHandlers(installer, uninstaller::accept, unpacker);
        }
    }

    record TestSpec(PackageType type, PackageHandlersSpec handlersSpec,
            List<CountingConsumer> initializers, List<BundleVerifierSpec> bundleVerifierSpecs,
            List<CountingConsumer> installVerifiers, List<CountingConsumer> uninstallVerifiers,
            int expectedJPackageExitCode, int actualJPackageExitCode, List<Action> actions) {

        PackageTest createTest(Consumer<Verifiable> verifiableAccumulator) {
            return createTest(handlersSpec.createPackageHandlers(verifiableAccumulator));
        }

        PackageTest createTest(PackageHandlers handlers) {
            return new PackageTest().jpackageFactory(() -> {
                return new JPackageCommand() {
                    @Override
                    public Path outputBundle() {
                        return outputDir().resolve("mockup-bundle" + super.packageType().getSuffix());
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
                        return new Executor.Result(actualJPackageExitCode,
                                this::getPrintableCommandLine).assertExitCodeIs(expectedExitCode);
                    }
                };
            }).setExpectedExitCode(expectedJPackageExitCode)
                    .setExpectedInstallExitCode(handlersSpec.installExitCode)
                    .isPackageTypeEnabled(type -> true)
                    .forTypes().packageHandlers(handlers);
        }

        void configureInitializers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var initializer : initializers) {
                verifiableAccumulator.accept(initializer);
                test.addInitializer(initializer);
            }
        }

        void configureBundleVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifierSpec : bundleVerifierSpecs) {
                verifiableAccumulator.accept(verifierSpec.apply(test));
            }
        }

        void configureInstallVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifier : installVerifiers) {
                verifiableAccumulator.accept(verifier);
                test.addInstallVerifier(verifier);
            }
        }

        void configureUninstallVerifiers(PackageTest test, Consumer<Verifiable> verifiableAccumulator) {
            for (final var verifier : uninstallVerifiers) {
                verifiableAccumulator.accept(verifier);
                test.addUninstallVerifier(verifier);
            }
        }

        void run(PackageTest test) {
            final boolean expectedSuccess = (expectedJPackageExitCode == actualJPackageExitCode);

            TKit.assertAssert(expectedSuccess, () -> {
                test.run(actions.toArray(Action[]::new));
            });
        }

        List<Verifiable> run() {
            return run(Optional.empty());
        }

        List<Verifiable> run(Consumer<PackageTest> customConfigure) {
            return run(Optional.of(customConfigure));
        }

        private List<Verifiable> run(Optional<Consumer<PackageTest>> customConfigure) {
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
            return verifiers;
        }
    }

    private static final class TestSpecBuilder {

        TestSpecBuilder type(PackageType v) {
            type = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder install(CallbackFactory v) {
            install = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder uninstall(CallbackFactory v) {
            uninstall = Objects.requireNonNull(v);
            return this;
        }

        TestSpecBuilder unpack(CallbackFactory v) {
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
            install(NEVER);
            uninstall(NEVER);
            if (willHaveBundle()) {
                overrideNonNullUnpack(ONCE);
            } else {
                overrideNonNullUnpack(NEVER);
            }
            initializers(ONCE);
            if (expectedJPackageExitCode != actualJPackageExitCode) {
                bundleVerifiers(BundleVerifier.NEVER.spec());
            } else if (expectedJPackageExitCode == 0) {
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS.spec());
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS_EXIT_CODE.spec());
            } else {
                bundleVerifiers(BundleVerifier.ONCE_FAIL.spec());
                if (expectedJPackageExitCode == ERROR_EXIT_CODE_JPACKAGE) {
                    bundleVerifiers(BundleVerifier.ONCE_FAIL_EXIT_CODE.spec());
                }
            }
            uninstallVerifiers(NEVER);
            if (willVerifyUnpack()) {
                installVerifiers(ONCE);
            } else {
                installVerifiers(NEVER);
            }
            return this;
        }

        TestSpecBuilder doCreateUnpackInstallUninstall() {
            actions(Action.CREATE, Action.UNPACK, Action.VERIFY_INSTALL, Action.INSTALL,
                    Action.VERIFY_INSTALL, Action.UNINSTALL, Action.VERIFY_UNINSTALL);
            initializers(ONCE);
            uninstallVerifiers(NEVER);
            if (willHaveBundle()) {
                overrideNonNullUnpack(ONCE);
                install(ONCE);
                if (installExitCode == 0) {
                    uninstall(ONCE);
                    uninstallVerifiers(ONCE);
                } else {
                    uninstall(NEVER);
                }
            } else {
                overrideNonNullUnpack(NEVER);
                install(NEVER);
                uninstall(NEVER);
            }

            if (expectedJPackageExitCode != actualJPackageExitCode) {
                bundleVerifiers(BundleVerifier.NEVER.spec());
                installVerifiers(NEVER);
            } else if (expectedJPackageExitCode == 0) {
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS.spec());
                bundleVerifiers(BundleVerifier.ONCE_SUCCESS_EXIT_CODE.spec());
                if (installExitCode == 0) {
                    if (willVerifyUnpack()) {
                        installVerifiers(TWICE);
                    } else {
                        installVerifiers(ONCE);
                    }
                } else {
                    if (willVerifyUnpack()) {
                        installVerifiers(ONCE);
                    } else {
                        installVerifiers(NEVER);
                    }
                }
            } else {
                bundleVerifiers(BundleVerifier.ONCE_FAIL.spec());
                if (expectedJPackageExitCode == ERROR_EXIT_CODE_JPACKAGE) {
                    bundleVerifiers(BundleVerifier.ONCE_FAIL_EXIT_CODE.spec());
                }
                installVerifiers(NEVER);
            }
            return this;
        }

        TestSpecBuilder addInitializers(CallbackFactory... v) {
            initializers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addBundleVerifiers(BundleVerifierSpec... v) {
            bundleVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addInstallVerifiers(CallbackFactory... v) {
            installVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder addUninstallVerifiers(CallbackFactory... v) {
            uninstallVerifiers.addAll(List.of(v));
            return this;
        }

        TestSpecBuilder initializers(CallbackFactory... v) {
            initializers.clear();
            return addInitializers(v);
        }

        TestSpecBuilder bundleVerifiers(BundleVerifierSpec... v) {
            bundleVerifiers.clear();
            return addBundleVerifiers(v);
        }

        TestSpecBuilder installVerifiers(CallbackFactory... v) {
            installVerifiers.clear();
            return addInstallVerifiers(v);
        }

        TestSpecBuilder uninstallVerifiers(CallbackFactory... v) {
            uninstallVerifiers.clear();
            return addUninstallVerifiers(v);
        }

        TestSpec create() {
            final var handlersSpec = new PackageHandlersSpec(
                    install.createInstaller(installExitCode), uninstall.createUninstaller(),
                    Optional.ofNullable(unpack).map(CallbackFactory::createUnpacker), installExitCode);
            return new TestSpec(type, handlersSpec,
                    initializers.stream().map(CallbackFactory::createInitializer).toList(),
                    bundleVerifiers,
                    installVerifiers.stream().map(CallbackFactory::createInstallVerifier).toList(),
                    uninstallVerifiers.stream().map(CallbackFactory::createUninstallVerifier).toList(),
                    expectedJPackageExitCode,
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

        private void overrideNonNullUnpack(CallbackFactory v) {
            if (unpack != null) {
                unpack(v);
            }
        }

        private PackageType type = PackageType.LINUX_RPM;
        private CallbackFactory install = NEVER;
        private CallbackFactory uninstall = NEVER;
        private CallbackFactory unpack = NEVER;
        private int installExitCode;
        private final List<CallbackFactory> initializers = new ArrayList<>();
        private final List<BundleVerifierSpec> bundleVerifiers = new ArrayList<>();
        private final List<CallbackFactory> installVerifiers = new ArrayList<>();
        private final List<CallbackFactory> uninstallVerifiers = new ArrayList<>();
        private int expectedJPackageExitCode;
        private int actualJPackageExitCode;
        private final List<Action> actions = new ArrayList<>();
    }

    @Test
    @ParameterSupplier
    public void test(TestSpec spec) {
        spec.run();
    }

    public static List<Object[]> test() {
        List<TestSpec> data = new ArrayList<>();

        for (boolean withUnpack : List.of(false, true)) {
            for (int actualJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_INSTALL)) {
                for (int expectedJPackageExitCode : List.of(0, 1, ERROR_EXIT_CODE_INSTALL)) {
                    data.add(new TestSpecBuilder()
                            .unpack(withUnpack ? ONCE : null)
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
                                .unpack(withUnpack ? ONCE : null)
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
                .uninstall(ONCE)
                .initializers(ONCE)
                .bundleVerifiers(BundleVerifier.NEVER.spec())
                .installVerifiers(TWICE)
                .uninstallVerifiers(ONCE)
                .create());

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    @Test
    @ParameterSupplier
    public void testDisableInstallerUninstaller(TestSpec spec, boolean disableInstaller, boolean disableUninstaller) {
        spec.run(test -> {
            if (disableInstaller) {
                test.disablePackageInstaller();
            }
            if (disableUninstaller) {
                test.disablePackageUninstaller();
            }
        });
    }

    public static List<Object[]> testDisableInstallerUninstaller() {
        List<Object[]> data = new ArrayList<>();

        for (boolean disableInstaller : List.of(true, false)) {
            for (boolean disableUninstaller : List.of(true, false)) {
                if (disableInstaller || disableUninstaller) {
                    final var builder = new TestSpecBuilder().doCreateUnpackInstallUninstall();
                    if (disableInstaller) {
                        builder.install(NEVER);
                    }
                    if (disableUninstaller) {
                        builder.uninstall(NEVER);
                    }
                    data.add(new Object[] { builder.create(), disableInstaller, disableUninstaller });
                }
            }
        }

        return data;
    }

    private static List<Path> getUnpackPaths(Collection<Verifiable> verifiers) {
        return verifiers.stream()
                .filter(CountingUnpacker.class::isInstance)
                .map(CountingUnpacker.class::cast)
                .map(CountingUnpacker::unpackPaths)
                .reduce((x , y) -> {
                    throw new UnsupportedOperationException();
                }).orElseThrow();
    }

    @Test
    public void testUnpackTwice() {
        final var testSpec = new TestSpecBuilder()
                .actions(Action.CREATE, Action.UNPACK, Action.VERIFY_INSTALL, Action.UNPACK, Action.VERIFY_INSTALL)
                .unpack(TWICE)
                .initializers(ONCE)
                .installVerifiers(TWICE)
                .create();

        final var unpackPaths = getUnpackPaths(testSpec.run());

        TKit.assertEquals(2, unpackPaths.size(), "Check the bundle was unpacked in different directories");

        unpackPaths.forEach(dir -> {
            TKit.assertTrue(dir.startsWith(TKit.workDir()), "Check unpack directory is inside of the test work directory");
        });
    }

    @Test
    public void testDeleteUnpackDirs() {
        final int unpackActionCount = 4;
        final var testSpec = new TestSpecBuilder()
                .actions(Action.UNPACK, Action.UNPACK, Action.UNPACK, Action.UNPACK)
                .unpack(new CallbackFactory(unpackActionCount) {
                    @Override
                    CountingUnpacker createUnpacker() {
                        return new CountingUnpacker(unpackActionCount) {
                            @Override
                            public Path apply(JPackageCommand cmd, Path path) {
                                switch (tickCount()) {
                                    case 0 -> {
                                    }

                                    case 2 -> {
                                        path = path.resolve("foo");
                                    }

                                    case 1, 3 -> {
                                        try {
                                            path = Files.createTempDirectory("jpackage-test");
                                        } catch (IOException ex) {
                                            throw new UncheckedIOException(ex);
                                        }
                                    }

                                    default -> {
                                        throw new IllegalStateException();
                                    }
                                }
                                return super.apply(cmd, path);
                            }
                        };
                    }
                })
                .initializers(ONCE)
                .create();

        final var unpackPaths = getUnpackPaths(testSpec.run());

        TKit.assertEquals(unpackActionCount, unpackPaths.size(), "Check the bundle was unpacked in different directories");

        // Unpack directories within the test work directory must exist.
        TKit.assertDirectoryExists(unpackPaths.get(0));
        TKit.assertDirectoryExists(unpackPaths.get(2));

        // Unpack directories outside of the test work directory must be deleted.
        TKit.assertPathExists(unpackPaths.get(1), false);
        TKit.assertPathExists(unpackPaths.get(3), false);
    }

    @Test
    public void testRunOnceInitializer() {
        final var testSpec = new TestSpecBuilder().doCreateAndUnpack().unpack(TWICE).create();

        final var initializer = TWICE.createInitializer();
        final var runOnceInitializer = ONCE.createRunOnceInitializer();
        testSpec.run(test -> {
            test.forTypes(PackageType.LINUX_RPM, PackageType.WIN_MSI)
                    .addRunOnceInitializer(runOnceInitializer)
                    .addInitializer(initializer);
        });

        initializer.verify();
        runOnceInitializer.verify();
    }

    @Test
    @Parameter("0")
    @Parameter("1")
    public void testPurge(int jpackageExitCode) {

        Path[] outputBundle = new Path[1];

        final var builder = new TestSpecBuilder();

        builder.actions(Action.CREATE).initializers(new CallbackFactory(1) {
            @Override
            CountingConsumer createInitializer() {
                    return new CountingConsumer(1, "custom-init") {
                        @Override
                        public void accept(JPackageCommand cmd) {
                            outputBundle[0] = cmd.outputBundle();
                            super.accept(cmd);
                        }
                    };
                }
            }).create().run();
        TKit.assertFileExists(outputBundle[0]);

        builder.actions(Action.PURGE).initializers(ONCE).jpackageExitCode(jpackageExitCode).create().run();
        TKit.assertPathExists(outputBundle[0], false);
    }

    @Test
    public void testPackageTestOrder() {

        Set<PackageType> packageTypes = new LinkedHashSet<>();

        final var initializer = new CountingConsumer(PackageType.NATIVE.size(), "custom-init") {
            @Override
            public void accept(JPackageCommand cmd) {
                packageTypes.add(new JPackageCommand().setArgumentValue(
                        "--type", cmd.getArgumentValue("--type")).packageType());
                super.accept(cmd);
            }
        };

        new TestSpecBuilder().actions(Action.CREATE).create().run(test -> {
            test.forTypes().addInitializer(initializer);
        });

        initializer.verify();

        final var expectedOrder = PackageType.NATIVE.stream()
                .sorted().map(PackageType::name).toList();
        final var actualOrder = packageTypes.stream().map(PackageType::name).toList();

        TKit.assertStringListEquals(expectedOrder, actualOrder, "Check the order or packaging");
    }
}
