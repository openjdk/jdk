/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingBiConsumer.toBiConsumer;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static jdk.jpackage.test.PackageType.LINUX;
import static jdk.jpackage.test.PackageType.MAC_PKG;
import static jdk.jpackage.test.PackageType.NATIVE;
import static jdk.jpackage.test.PackageType.WINDOWS;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;


/**
 * Instance of PackageTest is for configuring and running a single jpackage
 * command to produce platform specific package bundle.
 *
 * Provides methods to hook up custom configuration of jpackage command and
 * verification of the output bundle.
 */
public final class PackageTest extends RunnablePackageTest {

    public PackageTest() {
        isPackageTypeEnabled = PackageType::isEnabled;
        jpackageFactory = JPackageCommand::new;
        packageHandlers = new HashMap<>();
        disabledInstallers = new HashSet<>();
        disabledUninstallers = new HashSet<>();
        excludeTypes = new HashSet<>();
        handlers = NATIVE.stream().collect(Collectors.toMap(v -> v, v -> new Handler()));
        forTypes();
        setExpectedExitCode(0);
        setExpectedInstallExitCode(0);
        namedInitializers = new HashSet<>();
    }

    public PackageTest excludeTypes(PackageType... types) {
        excludeTypes.addAll(Stream.of(types).collect(Collectors.toSet()));
        return forTypes(currentTypes);
    }

    public PackageTest excludeTypes(Collection<PackageType> types) {
        return excludeTypes(types.toArray(PackageType[]::new));
    }

    public PackageTest forTypes(PackageType... types) {
        Collection<PackageType> newTypes;
        if (types.length == 0) {
            newTypes = NATIVE;
        } else {
            newTypes = Stream.of(types).collect(Collectors.toSet());
        }
        currentTypes = newTypes.stream()
                .filter(handlers.keySet()::contains)
                .filter(isPackageTypeEnabled)
                .filter(Predicate.not(excludeTypes::contains))
                .collect(Collectors.toUnmodifiableSet());
        return this;
    }

    public PackageTest forTypes(Collection<PackageType> types) {
        return forTypes(types.toArray(PackageType[]::new));
    }

    public PackageTest notForTypes(PackageType... types) {
        return notForTypes(List.of(types));
    }

    public PackageTest notForTypes(Collection<PackageType> types) {
        Set<PackageType> workset = new HashSet<>(currentTypes);
        workset.removeAll(types);
        return forTypes(workset);
    }

    public PackageTest setExpectedExitCode(int v) {
        expectedJPackageExitCode = v;
        return this;
    }

    public PackageTest setExpectedInstallExitCode(int... v) {
        expectedInstallExitCodes = IntStream.of(v).mapToObj(Integer::valueOf).collect(Collectors.toSet());
        return this;
    }

    public PackageTest ignoreBundleOutputDir() {
        return ignoreBundleOutputDir(true);
    }

    public PackageTest ignoreBundleOutputDir(boolean v) {
        ignoreBundleOutputDir = v;
        return this;
    }

    private PackageTest addInitializer(ThrowingConsumer<JPackageCommand> v, String id) {
        Objects.requireNonNull(v);
        if (id != null) {
            if (namedInitializers.contains(id)) {
                return this;
            }

            namedInitializers.add(id);
        }
        currentTypes.forEach(type -> handlers.get(type).addInitializer(toConsumer(v)));
        return this;
    }

    private PackageTest addRunOnceInitializer(ThrowingRunnable v, String id) {
        Objects.requireNonNull(v);
        return addInitializer(new ThrowingConsumer<JPackageCommand>() {
            @Override
            public void accept(JPackageCommand unused) throws Throwable {
                if (!executed) {
                    executed = true;
                    v.run();
                }
            }

            private boolean executed;
        }, id);
    }

    public PackageTest addInitializer(ThrowingConsumer<JPackageCommand> v) {
        return addInitializer(v, null);
    }

    public PackageTest addRunOnceInitializer(ThrowingRunnable v) {
        return addRunOnceInitializer(v, null);
    }

    public PackageTest addBundleVerifier(ThrowingBiConsumer<JPackageCommand, Executor.Result> v) {
        Objects.requireNonNull(v);
        currentTypes.forEach(type -> handlers.get(type).addBundleVerifier(toBiConsumer(v)));
        return this;
    }

    public PackageTest addBundleVerifier(ThrowingConsumer<JPackageCommand> v) {
        Objects.requireNonNull(v);
        return addBundleVerifier((cmd, unused) -> toConsumer(v).accept(cmd));
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            Predicate<String> pred, String predLabel) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(pred);
        return addBundleVerifier(cmd -> {
            final String value;
            if (isOfType(cmd, LINUX)) {
                value = LinuxHelper.getBundleProperty(cmd, propertyName);
            } else if (isOfType(cmd, WINDOWS)) {
                value = WindowsHelper.getMsiProperty(cmd, propertyName);
            } else {
                throw new IllegalStateException();
            }
            TKit.assertTrue(pred.test(value), String.format(
                    "Check value of %s property %s [%s]", propertyName,
                    predLabel, value));
        });
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            String expectedPropertyValue) {
        return addBundlePropertyVerifier(propertyName,
                expectedPropertyValue::equals, "is");
    }

    public PackageTest addBundleDesktopIntegrationVerifier(boolean integrated) {
        forTypes(LINUX, () -> {
            LinuxHelper.addBundleDesktopIntegrationVerifier(this, integrated);
        });
        return this;
    }

    public PackageTest addInstallVerifier(ThrowingConsumer<JPackageCommand> v) {
        currentTypes.forEach(type -> handlers.get(type).addInstallVerifier(
                toConsumer(v)));
        return this;
    }

    public PackageTest addUninstallVerifier(ThrowingConsumer<JPackageCommand> v) {
        currentTypes.forEach(type -> handlers.get(type).addUninstallVerifier(
                toConsumer(v)));
        return this;
    }

    public PackageTest disablePackageInstaller() {
        currentTypes.forEach(disabledInstallers::add);
        return this;
    }

    public PackageTest disablePackageUninstaller() {
        currentTypes.forEach(disabledUninstallers::add);
        return this;
    }

    public PackageTest createMsiLog(boolean v) {
        createMsiLog = v;
        return this;
    }

    static void withFileAssociationsTestRuns(FileAssociations fa,
            ThrowingBiConsumer<FileAssociations.TestRun, List<Path>> consumer) {
        Objects.requireNonNull(consumer);
        for (var testRun : fa.getTestRuns()) {
            TKit.withTempDirectory("fa-test-files", tempDir -> {
                List<Path> testFiles = StreamSupport.stream(testRun.getFileNames().spliterator(), false).map(fname -> {
                    return tempDir.resolve(fname + fa.getSuffix()).toAbsolutePath().normalize();
                }).toList();

                testFiles.forEach(toConsumer(Files::createFile));

                if (TKit.isLinux()) {
                    testFiles.forEach(LinuxHelper::initFileAssociationsTestFile);
                }

                consumer.accept(testRun, testFiles);
            });
        }
    }

    PackageTest addHelloAppFileAssociationsVerifier(FileAssociations fa) {
        Objects.requireNonNull(fa);

        // Setup test app to have valid jpackage command line before
        // running check of type of environment.
        addHelloAppInitializer(null);

        forTypes(LINUX, () -> {
            LinuxHelper.addFileAssociationsVerifier(this, fa);
        });

        String noActionMsg = "Not running file associations test";
        if (GraphicsEnvironment.isHeadless()) {
            TKit.trace(String.format(
                    "%s because running in headless environment", noActionMsg));
            return this;
        }

        addInstallVerifier(cmd -> {
            if (cmd.isFakeRuntime(noActionMsg) || cmd.isPackageUnpacked(noActionMsg)) {
                return;
            }

            withFileAssociationsTestRuns(fa, (testRun, testFiles) -> {
                final Path appOutput = testFiles.get(0).getParent()
                        .resolve(HelloApp.OUTPUT_FILENAME);
                Files.deleteIfExists(appOutput);

                List<String> expectedArgs = testRun.openFiles(testFiles);
                TKit.waitForFileCreated(appOutput, 7);

                // Wait a little bit after file has been created to
                // make sure there are no pending writes into it.
                Thread.sleep(3000);
                HelloApp.verifyOutputFile(appOutput, expectedArgs,
                        Collections.emptyMap());
            });

            if (isOfType(cmd, WINDOWS)) {
                // Verify context menu label in registry.
                String progId = WindowsHelper.queryRegistryValue(
                        String.format("HKEY_LOCAL_MACHINE\\SOFTWARE\\Classes\\%s", fa.getSuffix()), "");
                TKit.assertNotNull(progId, "context menu progId found");
                String contextMenuLabel = WindowsHelper.queryRegistryValue(
                        String.format("HKEY_CLASSES_ROOT\\%s\\shell\\open", progId), "");
                TKit.assertNotNull(contextMenuLabel, "context menu label found");
                String appName = cmd.getArgumentValue("--name");
                TKit.assertTrue(String.format("Open with %s", appName).equals(contextMenuLabel), "context menu label text");
            }
        });

        return this;
    }

    public PackageTest forTypes(Collection<PackageType> types, Runnable action) {
        final var oldTypes = Set.of(currentTypes.toArray(PackageType[]::new));
        try {
            forTypes(types);
            action.run();
        } finally {
            forTypes(oldTypes);
        }
        return this;
    }

    public PackageTest forTypes(PackageType type, Runnable action) {
        return forTypes(List.of(type), action);
    }

    public PackageTest notForTypes(Collection<PackageType> types, Runnable action) {
        Set<PackageType> workset = new HashSet<>(currentTypes);
        workset.removeAll(types);
        return forTypes(workset, action);
    }

    public PackageTest notForTypes(PackageType type, Runnable action) {
        return notForTypes(List.of(type), action);
    }

    public PackageTest configureHelloApp() {
        return configureHelloApp(null);
    }

    public PackageTest configureHelloApp(String javaAppDesc) {
        addHelloAppInitializer(javaAppDesc);
        addInstallVerifier(HelloApp::executeLauncherAndVerifyOutput);
        return this;
    }

    public PackageTest addHelloAppInitializer(String javaAppDesc) {
        addInitializer(
                cmd -> new HelloApp(JavaAppDesc.parse(javaAppDesc)).addTo(cmd),
                "HelloApp");
        return this;
    }

    public static class Group extends RunnablePackageTest {
        public Group(PackageTest... tests) {
            typeHandlers = Stream.of(PackageType.values()).map(type -> {
                return Map.entry(type, Stream.of(tests).map(test -> {
                    return test.createPackageTypeHandler(type);
                }).filter(Optional::isPresent).map(Optional::orElseThrow).toList());
            }).filter(e -> {
                return !e.getValue().isEmpty();
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        protected void runAction(Action... action) {
            typeHandlers.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(Map.Entry::getValue).forEachOrdered(handlers -> {
                        runAction(handlers, List.of(action));
                    });
        }

        private static void runAction(List<Consumer<Action>> handlers, List<Action> actions) {
            if (actions.contains(Action.UNINSTALL)) {
                final var listIterator = handlers.listIterator(handlers.size());
                while (listIterator.hasPrevious()) {
                    final var handler = listIterator.previous();
                    actions.forEach(handler::accept);
                }
            } else {
                handlers.forEach(handler -> actions.forEach(handler::accept));
            }
        }

        private final Map<PackageType, List<Consumer<Action>>> typeHandlers;
    }

    PackageTest packageHandlers(PackageHandlers v) {
        Objects.requireNonNull(v);
        currentTypes.forEach(type -> packageHandlers.put(type, v));
        return this;
    }

    PackageTest isPackageTypeEnabled(Predicate<PackageType> v) {
        Objects.requireNonNull(v);
        isPackageTypeEnabled = v;
        return this;
    }

    PackageTest jpackageFactory(Supplier<JPackageCommand> v) {
        Objects.requireNonNull(v);
        jpackageFactory = v;
        return this;
    }

    record PackageHandlers(Function<JPackageCommand, Integer> installHandler,
            Consumer<JPackageCommand> uninstallHandler,
            Optional<? extends BiFunction<JPackageCommand, Path, Path>> unpackHandler) {

        PackageHandlers(Function<JPackageCommand, Integer> installHandler,
                Consumer<JPackageCommand> uninstallHandler,
                BiFunction<JPackageCommand, Path, Path> unpackHandler) {
            this(installHandler, uninstallHandler, Optional.of(unpackHandler));
        }

        PackageHandlers {
            Objects.requireNonNull(installHandler);
            Objects.requireNonNull(uninstallHandler);
            Objects.requireNonNull(unpackHandler);
        }

        PackageHandlers copyWithNopInstaller() {
            return new PackageHandlers(cmd -> 0, uninstallHandler, unpackHandler);
        }

        PackageHandlers copyWithNopUninstaller() {
            return new PackageHandlers(installHandler, cmd -> {}, unpackHandler);
        }

        int install(JPackageCommand cmd) {
            return installHandler.apply(cmd);
        }

        Path unpack(JPackageCommand cmd, Path unpackDir) {
            return unpackHandler.orElseThrow().apply(cmd, unpackDir);
        }

        void uninstall(JPackageCommand cmd) {
            uninstallHandler.accept(cmd);
        }
    }

    @Override
    protected void runActions(List<Action[]> actions) {
        createPackageTypeHandlers().forEach(
                handler -> actions.forEach(
                        action -> List.of(action).forEach(handler::accept)));
    }

    @Override
    protected void runAction(Action... action) {
        throw new UnsupportedOperationException();
    }

    private Optional<Consumer<Action>> createPackageTypeHandler(PackageType type) {
        Objects.requireNonNull(type);
        return Optional.ofNullable(handlers.get(type)).filter(Predicate.not(Handler::isVoid)).map(h -> {
            return createPackageTypeHandler(type, h);
        });
    }

    private List<Consumer<Action>> createPackageTypeHandlers() {
        if (handlers.keySet().stream().noneMatch(isPackageTypeEnabled)) {
            PackageType.throwSkippedExceptionIfNativePackagingUnavailable();
        }
        return Stream.of(PackageType.values()).sorted()
                .map(this::createPackageTypeHandler)
                .filter(Optional::isPresent)
                .map(Optional::orElseThrow)
                .toList();
    }

    private record PackageTypePipeline(PackageType type, int expectedJPackageExitCode,
            Set<Integer> expectedInstallExitCodes, PackageHandlers packageHandlers, Handler handler,
            JPackageCommand cmd, State state) implements Consumer<Action> {

        PackageTypePipeline {
            Objects.requireNonNull(type);
            Objects.requireNonNull(expectedInstallExitCodes);
            if (expectedInstallExitCodes.isEmpty()) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(packageHandlers);
            Objects.requireNonNull(handler);
            Objects.requireNonNull(cmd);
            Objects.requireNonNull(state);
        }

        PackageTypePipeline(PackageType type, int expectedJPackageExitCode,
                Set<Integer> expectedInstallExitCodes, PackageHandlers packageHandlers,
                Handler handler, JPackageCommand cmd) {
            this(type, expectedJPackageExitCode, expectedInstallExitCodes,
                    packageHandlers, handler, cmd, new State());
        }

        @Override
        public void accept(Action action) {
            switch(analizeAction(action)) {
                case SKIP_NO_PACKAGE_HANDLER -> {
                    TKit.trace(String.format("No handler of [%s] action for %s command",
                            action, cmd.getPrintableCommandLine()));
                    return;
                }
                case SKIP -> {
                    TKit.trace(String.format("Skip [%s] action of %s command",
                            action, cmd.getPrintableCommandLine()));
                    return;
                }
                case PROCESS -> {
                }
            }

            switch (action) {
                case UNPACK -> {
                    cmd.setUnpackedPackageLocation(null);
                    final var unpackRootDir = TKit.createTempDirectory(
                            String.format("unpacked-%s", type.getType()));
                    final Path unpackDir = packageHandlers.unpack(cmd, unpackRootDir);
                    if (!unpackDir.startsWith(TKit.workDir())) {
                        state.deleteUnpackDirs.add(unpackDir);
                    }
                    cmd.setUnpackedPackageLocation(unpackDir);
                }

                case INSTALL -> {
                    cmd.setUnpackedPackageLocation(null);
                    final int actualInstallExitCode = packageHandlers.install(cmd);
                    state.actualInstallExitCode = Optional.of(actualInstallExitCode);
                    TKit.assertTrue(expectedInstallExitCodes.contains(actualInstallExitCode),
                            String.format("Check installer exit code %d is one of %s", actualInstallExitCode, expectedInstallExitCodes));
                }

                case UNINSTALL -> {
                    cmd.setUnpackedPackageLocation(null);
                    packageHandlers.uninstall(cmd);
                }

                case CREATE -> {
                    cmd.setUnpackedPackageLocation(null);
                    handler.processAction(action, cmd, expectedJPackageExitCode);
                }

                case INITIALIZE -> {
                    handler.processAction(action, cmd, expectedJPackageExitCode);
                }

                case FINALIZE -> {
                    state.deleteUnpackDirs.forEach(TKit::deleteDirectoryRecursive);
                    state.deleteUnpackDirs.clear();
                }

                default -> {
                    handler.processAction(action, cmd.createImmutableCopy(), expectedJPackageExitCode);
                }
            }
        }

        private enum ActionAction {
            PROCESS,
            SKIP,
            SKIP_NO_PACKAGE_HANDLER
        }

        private ActionAction analizeAction(Action action) {
            Objects.requireNonNull(action);

            if (jpackageFailed()) {
                return ActionAction.SKIP;
            }

            switch (action) {
                case CREATE -> {
                    state.packageActions.add(action);
                }
                case INSTALL -> {
                    state.packageActions.add(action);
                    state.packageActions.remove(Action.UNPACK);
                }
                case UNINSTALL -> {
                    state.packageActions.add(action);
                    if (installFailed()) {
                        return ActionAction.SKIP;
                    }
                }
                case UNPACK -> {
                    state.packageActions.add(action);
                    state.packageActions.remove(Action.INSTALL);
                    if (unpackNotSupported()) {
                        return ActionAction.SKIP_NO_PACKAGE_HANDLER;
                    }
                }
                case VERIFY_INSTALL -> {
                    if (unpackNotSupported()) {
                        return ActionAction.SKIP;
                    }

                    if (installFailed()) {
                        return ActionAction.SKIP;
                    }
                }
                case VERIFY_UNINSTALL -> {
                    if (installFailed() && processed(Action.UNINSTALL)) {
                        return ActionAction.SKIP;
                    }
                }
                default -> {
                    // NOP
                }
            }

            return ActionAction.PROCESS;
        }

        private boolean processed(Action action) {
            Objects.requireNonNull(action);
            return state.packageActions.contains(action);
        }

        private boolean installFailed() {
            return processed(Action.INSTALL) && state.actualInstallExitCode.orElseThrow() != 0;
        }

        private boolean jpackageFailed() {
            return processed(Action.CREATE) && expectedJPackageExitCode != 0;
        }

        private boolean unpackNotSupported() {
            return processed(Action.UNPACK) && packageHandlers.unpackHandler().isEmpty();
        }

        private static final class State {
            private Optional<Integer> actualInstallExitCode = Optional.empty();
            private final Set<Action> packageActions = new HashSet<>();
            private final List<Path> deleteUnpackDirs = new ArrayList<>();
        }
    }

    private Consumer<Action> createPackageTypeHandler(PackageType type, Handler handler) {
        final var cmd = jpackageFactory.get();
        cmd.setDefaultInputOutput().setDefaultAppName();
        if (BUNDLE_OUTPUT_DIR != null && !ignoreBundleOutputDir) {
            cmd.setArgumentValue("--dest", BUNDLE_OUTPUT_DIR.toString());
        }
        type.applyTo(cmd);
        return new PackageTypePipeline(type, expectedJPackageExitCode,
                expectedInstallExitCodes, getPackageHandlers(type), handler.copy(), cmd);
    }

    private record Handler(List<Consumer<JPackageCommand>> initializers,
            List<BiConsumer<JPackageCommand, Executor.Result>> bundleVerifiers,
            List<Consumer<JPackageCommand>> installVerifiers,
            List<Consumer<JPackageCommand>> uninstallVerifiers) {

        Handler() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        Handler copy() {
            return new Handler(List.copyOf(initializers), List.copyOf(bundleVerifiers),
                    List.copyOf(installVerifiers), List.copyOf(uninstallVerifiers));
        }

        boolean isVoid() {
            return initializers.isEmpty();
        }

        void addInitializer(Consumer<JPackageCommand> v) {
            initializers.add(v);
        }

        void addBundleVerifier(BiConsumer<JPackageCommand, Executor.Result> v) {
            bundleVerifiers.add(v);
        }

        void addInstallVerifier(Consumer<JPackageCommand> v) {
            installVerifiers.add(v);
        }

        void addUninstallVerifier(Consumer<JPackageCommand> v) {
            uninstallVerifiers.add(v);
        }

        public void processAction(Action action, JPackageCommand cmd, int expectedJPackageExitCode) {
            switch (action) {
                case INITIALIZE -> {
                    initializers.forEach(v -> v.accept(cmd));
                    if (cmd.isImagePackageType()) {
                        throw new UnsupportedOperationException();
                    }
                    cmd.executePrerequisiteActions();
                }

                case CREATE -> {
                    Executor.Result result = cmd.execute(expectedJPackageExitCode);
                    if (expectedJPackageExitCode == 0) {
                        TKit.assertFileExists(cmd.outputBundle());
                    } else {
                        cmd.nullableOutputBundle().ifPresent(outputBundle -> {
                            TKit.assertPathExists(outputBundle, false);
                        });
                    }
                    verifyPackageBundle(cmd, result, expectedJPackageExitCode);
                }

                case VERIFY_INSTALL -> {
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageInstalled(cmd);
                    }
                }

                case VERIFY_UNINSTALL -> {
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageUninstalled(cmd);
                    }
                }

                case PURGE -> {
                    var bundle = cmd.outputBundle();
                    if (toSupplier(() -> TKit.deleteIfExists(bundle)).get()) {
                        TKit.trace(String.format("Deleted [%s] package", bundle));
                    }
                }

                default -> {
                    // NOP
                }
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd,
                Executor.Result result, int expectedJPackageExitCode) {
            if (expectedJPackageExitCode == 0) {
                if (isOfType(cmd, LINUX)) {
                    LinuxHelper.verifyPackageBundleEssential(cmd);
                }
            }
            bundleVerifiers.forEach(v -> v.accept(cmd, result));
        }

        private void verifyPackageInstalled(JPackageCommand cmd) {
            final String formatString;
            if (cmd.isPackageUnpacked()) {
                formatString = "Verify unpacked: %s";
            } else {
                formatString = "Verify installed: %s";
            }
            TKit.trace(String.format(formatString, cmd.getPrintableCommandLine()));

            Optional.ofNullable(cmd.unpackedPackageDirectory()).ifPresent(
                    unpackedDir -> {
                        verifyRootCountInUnpackedPackage(cmd, unpackedDir);
                    });

            if (!cmd.isRuntime()) {
                if (isOfType(cmd, WINDOWS) && !cmd.isPackageUnpacked("Not verifying desktop integration")) {
                    // Check main launcher
                    WindowsHelper.verifyDesktopIntegration(cmd, null);
                    // Check additional launchers
                    cmd.addLauncherNames().forEach(name -> {
                        WindowsHelper.verifyDesktopIntegration(cmd, name);
                    });
                }
            }

            if (isOfType(cmd, LauncherAsServiceVerifier.SUPPORTED_PACKAGES)) {
                LauncherAsServiceVerifier.verify(cmd);
            }

            cmd.assertAppLayout();

            installVerifiers.forEach(v -> v.accept(cmd));
        }

        private void verifyRootCountInUnpackedPackage(JPackageCommand cmd,
                Path unpackedDir) {

            final boolean withServices = !cmd.isRuntime()
                    && !LauncherAsServiceVerifier.getLaunchersAsServices(cmd).isEmpty();

            final long expectedRootCount;
            if (isOfType(cmd, WINDOWS)) {
                // On Windows it is always two entries:
                // installation home directory and MSI file
                expectedRootCount = 2;
            } else if (withServices && isOfType(cmd, MAC_PKG)) {
                expectedRootCount = 2;
            } else if (isOfType(cmd, LINUX)) {
                Set<Path> roots = new HashSet<>();
                roots.add(Path.of("/").resolve(Path.of(cmd.getArgumentValue(
                        "--install-dir", () -> "/opt")).getName(0)));
                if (withServices) {
                    // /lib/systemd
                    roots.add(Path.of("/lib"));
                }
                if (cmd.hasArgument("--license-file")) {
                    switch (cmd.packageType()) {
                        case LINUX_RPM -> {
                            // License file is in /usr/share/licenses subtree
                            roots.add(Path.of("/usr"));
                        }

                        case LINUX_DEB -> {
                            Path installDir = cmd.appInstallationDirectory();
                            if (installDir.equals(Path.of("/"))
                                    || installDir.startsWith("/usr")) {
                                // License file is in /usr/share/doc subtree
                                roots.add(Path.of("/usr"));
                            }
                        }

                        default -> {
                            throw new UnsupportedOperationException();
                        }
                    }
                }
                expectedRootCount = roots.size();
            } else {
                expectedRootCount = 1;
            }

            try ( var files = Files.list(unpackedDir)) {
                TKit.assertEquals(expectedRootCount, files.count(),
                        String.format(
                                "Check the package has %d top installation directories",
                                expectedRootCount));
            } catch (IOException ex) {
                rethrowUnchecked(ex);
            }
        }

        private void verifyPackageUninstalled(JPackageCommand cmd) {
            TKit.trace(String.format("Verify uninstalled: %s",
                    cmd.getPrintableCommandLine()));
            if (!cmd.isRuntime()) {
                TKit.assertPathExists(cmd.appLauncherPath(), false);

                if (isOfType(cmd, WINDOWS)) {
                    // Check main launcher
                    WindowsHelper.verifyDesktopIntegration(cmd, null);
                    // Check additional launchers
                    cmd.addLauncherNames().forEach(name -> {
                        WindowsHelper.verifyDesktopIntegration(cmd, name);
                    });
                }
            }

            Path appInstallDir = cmd.appInstallationDirectory();
            if (isOfType(cmd, LINUX) && Path.of("/").equals(appInstallDir)) {
                ApplicationLayout appLayout = cmd.appLayout();
                TKit.assertPathExists(appLayout.runtimeDirectory(), false);
            } else {
                TKit.assertPathExists(appInstallDir, false);
            }

            if (isOfType(cmd, LauncherAsServiceVerifier.SUPPORTED_PACKAGES)) {
                LauncherAsServiceVerifier.verifyUninstalled(cmd);
            }

            uninstallVerifiers.forEach(v -> v.accept(cmd));
        }
    }

    private PackageHandlers getDefaultPackageHandlers(PackageType type) {
        switch (type) {
            case LINUX_DEB -> {
                return LinuxHelper.createDebPackageHandlers();
            }
            case LINUX_RPM -> {
                return LinuxHelper.createRpmPackageHandlers();
            }
            case WIN_MSI -> {
                return WindowsHelper.createMsiPackageHandlers(createMsiLog);
            }
            case WIN_EXE -> {
                return WindowsHelper.createExePackageHandlers(createMsiLog);
            }
            case MAC_DMG -> {
                return MacHelper.createDmgPackageHandlers();
            }
            case MAC_PKG -> {
                return MacHelper.createPkgPackageHandlers();
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private PackageHandlers getPackageHandlers(PackageType type) {
        Objects.requireNonNull(type);

        var reply = Optional.ofNullable(packageHandlers.get(type)).orElseGet(() -> {
            if (TKit.isLinux() && !PackageType.LINUX.contains(type)) {
                throw new IllegalArgumentException();
            } else if (TKit.isWindows() && !PackageType.WINDOWS.contains(type)) {
                throw new IllegalArgumentException();
            } else if (TKit.isOSX() && !PackageType.MAC.contains(type)) {
                throw new IllegalArgumentException();
            } else {
                return getDefaultPackageHandlers(type);
            }
        });

        if (disabledInstallers.contains(type)) {
            reply = reply.copyWithNopInstaller();
        }

        if (disabledUninstallers.contains(type)) {
            reply = reply.copyWithNopUninstaller();
        }

        return reply;
    }

    private static boolean isOfType(JPackageCommand cmd, PackageType packageTypes) {
        return isOfType(cmd, Set.of(packageTypes));
    }

    private static boolean isOfType(JPackageCommand cmd, Set<PackageType> packageTypes) {
        return Optional.ofNullable(cmd.packageType()).map(packageTypes::contains).orElse(false);
    }

    private Collection<PackageType> currentTypes;
    private Set<PackageType> excludeTypes;
    private int expectedJPackageExitCode;
    private Set<Integer> expectedInstallExitCodes;
    private final Map<PackageType, Handler> handlers;
    private final Set<String> namedInitializers;
    private final Map<PackageType, PackageHandlers> packageHandlers;
    private final Set<PackageType> disabledInstallers;
    private final Set<PackageType> disabledUninstallers;
    private Predicate<PackageType> isPackageTypeEnabled;
    private Supplier<JPackageCommand> jpackageFactory;
    private boolean ignoreBundleOutputDir;
    private boolean createMsiLog;

    private static final Path BUNDLE_OUTPUT_DIR;

    static {
        final String propertyName = "output";
        String val = TKit.getConfigProperty(propertyName);
        if (val == null) {
            BUNDLE_OUTPUT_DIR = null;
        } else {
            BUNDLE_OUTPUT_DIR = Path.of(val).toAbsolutePath();

            if (!Files.isDirectory(BUNDLE_OUTPUT_DIR)) {
                throw new IllegalArgumentException(String.format("Invalid value of %s sytem property: [%s]. Should be existing directory",
                        TKit.getConfigPropertyName(propertyName),
                        BUNDLE_OUTPUT_DIR));
            }
        }
    }
}
