/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.test.Functional.ThrowingBiConsumer;
import static jdk.jpackage.test.Functional.ThrowingBiConsumer.toBiConsumer;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import static jdk.jpackage.test.Functional.ThrowingConsumer.toConsumer;
import jdk.jpackage.test.Functional.ThrowingRunnable;
import static jdk.jpackage.test.Functional.ThrowingSupplier.toSupplier;
import static jdk.jpackage.test.Functional.rethrowUnchecked;
import static jdk.jpackage.test.PackageType.LINUX;
import static jdk.jpackage.test.PackageType.LINUX_DEB;
import static jdk.jpackage.test.PackageType.LINUX_RPM;
import static jdk.jpackage.test.PackageType.MAC_DMG;
import static jdk.jpackage.test.PackageType.MAC_PKG;
import static jdk.jpackage.test.PackageType.NATIVE;
import static jdk.jpackage.test.PackageType.WINDOWS;
import static jdk.jpackage.test.PackageType.WIN_EXE;
import static jdk.jpackage.test.PackageType.WIN_MSI;


/**
 * Instance of PackageTest is for configuring and running a single jpackage
 * command to produce platform specific package bundle.
 *
 * Provides methods to hook up custom configuration of jpackage command and
 * verification of the output bundle.
 */
public final class PackageTest extends RunnablePackageTest {

    public PackageTest() {
        excludeTypes = new HashSet<>();
        forTypes();
        setExpectedExitCode(0);
        namedInitializers = new HashSet<>();
        handlers = currentTypes.stream()
                .collect(Collectors.toMap(v -> v, v -> new Handler()));
        packageHandlers = createDefaultPackageHandlers();
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
        if (types == null || types.length == 0) {
            newTypes = NATIVE;
        } else {
            newTypes = Stream.of(types).collect(Collectors.toSet());
        }
        currentTypes = newTypes.stream()
                .filter(PackageType::isSupported)
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

    private PackageTest addInitializer(ThrowingConsumer<JPackageCommand> v,
            String id) {
        if (id != null) {
            if (namedInitializers.contains(id)) {
                return this;
            }

            namedInitializers.add(id);
        }
        currentTypes.forEach(type -> handlers.get(type).addInitializer(
                toConsumer(v)));
        return this;
    }

    private PackageTest addRunOnceInitializer(ThrowingRunnable v, String id) {
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

    public PackageTest addBundleVerifier(
            ThrowingBiConsumer<JPackageCommand, Executor.Result> v) {
        currentTypes.forEach(type -> handlers.get(type).addBundleVerifier(
                toBiConsumer(v)));
        return this;
    }

    public PackageTest addBundleVerifier(ThrowingConsumer<JPackageCommand> v) {
        return addBundleVerifier((cmd, unused) -> toConsumer(v).accept(cmd));
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            Predicate<String> pred, String predLabel) {
        return addBundleVerifier(cmd -> {
            final String value;
            if (TKit.isLinux()) {
                value = LinuxHelper.getBundleProperty(cmd, propertyName);
            } else if (TKit.isWindows()) {
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
        currentTypes.forEach(
                type -> packageHandlers.get(type).installHandler = cmd -> {});
        return this;
    }

    public PackageTest disablePackageUninstaller() {
        currentTypes.forEach(
                type -> packageHandlers.get(type).uninstallHandler = cmd -> {});
        return this;
    }

    static void withFileAssociationsTestRuns(FileAssociations fa,
            ThrowingBiConsumer<FileAssociations.TestRun, List<Path>> consumer) {
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

            if (TKit.isWindows()) {
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
        Set<PackageType> oldTypes = Set.of(currentTypes.toArray(
                PackageType[]::new));
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
            handlers = Stream.of(tests)
                    .map(PackageTest::createPackageTypeHandlers)
                    .flatMap(List<Consumer<Action>>::stream)
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        protected void runAction(Action... action) {
            if (Set.of(action).contains(Action.UNINSTALL)) {
                ListIterator<Consumer<Action>> listIterator = handlers.listIterator(
                        handlers.size());
                while (listIterator.hasPrevious()) {
                    var handler = listIterator.previous();
                    List.of(action).forEach(handler::accept);
                }
            } else {
                handlers.forEach(handler -> List.of(action).forEach(handler::accept));
            }
        }

        private final List<Consumer<Action>> handlers;
    }

    final static class PackageHandlers {
        Consumer<JPackageCommand> installHandler;
        Consumer<JPackageCommand> uninstallHandler;
        BiFunction<JPackageCommand, Path, Path> unpackHandler;
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

    private List<Consumer<Action>> createPackageTypeHandlers() {
        return NATIVE.stream()
                .map(type -> {
                    Handler handler = handlers.entrySet().stream()
                        .filter(entry -> !entry.getValue().isVoid())
                        .filter(entry -> entry.getKey() == type)
                        .map(entry -> entry.getValue())
                        .findAny().orElse(null);
                    Map.Entry<PackageType, Handler> result = null;
                    if (handler != null) {
                        result = Map.entry(type, handler);
                    }
                    return result;
                })
                .filter(Objects::nonNull)
                .map(entry -> createPackageTypeHandler(
                        entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private Consumer<Action> createPackageTypeHandler(
            PackageType type, Handler handler) {
        return toConsumer(new ThrowingConsumer<Action>() {
            @Override
            public void accept(Action action) throws Throwable {
                if (terminated) {
                    throw new IllegalStateException();
                }

                if (action == Action.FINALIZE) {
                    if (unpackDir != null) {
                        if (Files.isDirectory(unpackDir)
                                && !unpackDir.startsWith(TKit.workDir())) {
                            TKit.deleteDirectoryRecursive(unpackDir);
                        }
                        unpackDir = null;
                    }
                    terminated = true;
                }

                boolean skip = false;

                if (unhandledAction != null) {
                    switch (unhandledAction) {
                        case CREATE:
                            skip = true;
                            break;
                        case UNPACK:
                        case INSTALL:
                            skip = (action == Action.VERIFY_INSTALL);
                            break;
                        case UNINSTALL:
                            skip = (action == Action.VERIFY_UNINSTALL);
                            break;
                    }
                }

                if (skip) {
                    TKit.trace(String.format("Skip [%s] action of %s command",
                            action, cmd.getPrintableCommandLine()));
                    return;
                }

                final Supplier<JPackageCommand> curCmd = () -> {
                    if (Set.of(Action.INITIALIZE, Action.CREATE).contains(action)) {
                        return cmd;
                    } else {
                        return cmd.createImmutableCopy();
                    }
                };

                switch (action) {
                    case UNPACK: {
                        cmd.setUnpackedPackageLocation(null);
                        handleAction(action,
                                packageHandlers.get(type).unpackHandler,
                                handler -> {
                                    unpackDir = TKit.createTempDirectory(
                                            String.format("unpacked-%s",
                                                    type.getName()));
                                    unpackDir = handler.apply(cmd, unpackDir);
                                    cmd.setUnpackedPackageLocation(unpackDir);
                                });
                        break;
                    }

                    case INSTALL: {
                        cmd.setUnpackedPackageLocation(null);
                        handleAction(action,
                                packageHandlers.get(type).installHandler,
                                handler -> {
                                    handler.accept(curCmd.get());
                                });
                        break;
                    }

                    case UNINSTALL: {
                        handleAction(action,
                                packageHandlers.get(type).uninstallHandler,
                                handler -> {
                                    handler.accept(curCmd.get());
                                });
                        break;
                    }

                    case CREATE:
                        cmd.setUnpackedPackageLocation(null);
                        handler.accept(action, curCmd.get());
                        handleAction(action,
                                (expectedJPackageExitCode == 0) ? Boolean.TRUE : null,
                                handler -> {
                                });
                        return;

                    default:
                        handler.accept(action, curCmd.get());
                        break;
                }

                Optional.ofNullable(unhandledAction).ifPresent(v -> {
                    TKit.trace(String.format(
                            "No handler of [%s] action for %s command", v,
                            cmd.getPrintableCommandLine()));
                });
            }

            private <T> void handleAction(Action action, T handler,
                    ThrowingConsumer<T> consumer) throws Throwable {
                if (handler == null) {
                    unhandledAction = action;
                } else {
                    unhandledAction = null;
                    consumer.accept(handler);
                }
            }

            private Path unpackDir;
            private Action unhandledAction;
            private boolean terminated;
            private final JPackageCommand cmd = Functional.identity(() -> {
                JPackageCommand result = new JPackageCommand();
                result.setDefaultInputOutput().setDefaultAppName();
                if (BUNDLE_OUTPUT_DIR != null) {
                    result.setArgumentValue("--dest", BUNDLE_OUTPUT_DIR.toString());
                }
                type.applyTo(result);
                return result;
            }).get();
        });
    }

    private class Handler implements BiConsumer<Action, JPackageCommand> {

        Handler() {
            initializers = new ArrayList<>();
            bundleVerifiers = new ArrayList<>();
            installVerifiers = new ArrayList<>();
            uninstallVerifiers = new ArrayList<>();
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

        @Override
        public void accept(Action action, JPackageCommand cmd) {
            switch (action) {
                case INITIALIZE:
                    initializers.forEach(v -> v.accept(cmd));
                    if (cmd.isImagePackageType()) {
                        throw new UnsupportedOperationException();
                    }
                    cmd.executePrerequisiteActions();
                    break;

                case CREATE:
                    Executor.Result result = cmd.execute(expectedJPackageExitCode);
                    if (expectedJPackageExitCode == 0) {
                        TKit.assertFileExists(cmd.outputBundle());
                    } else {
                        cmd.nullableOutputBundle().ifPresent(outputBundle -> {
                            TKit.assertPathExists(outputBundle, false);
                        });
                    }
                    verifyPackageBundle(cmd, result);
                    break;

                case VERIFY_INSTALL:
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageInstalled(cmd);
                    }
                    break;

                case VERIFY_UNINSTALL:
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageUninstalled(cmd);
                    }
                    break;

                case PURGE:
                    if (expectedJPackageExitCode == 0) {
                        var bundle = cmd.outputBundle();
                        if (toSupplier(() -> TKit.deleteIfExists(bundle)).get()) {
                            TKit.trace(String.format("Deleted [%s] package",
                                    bundle));
                        }
                    }
                    break;
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd,
                Executor.Result result) {
            if (expectedJPackageExitCode == 0) {
                if (LINUX.contains(cmd.packageType())) {
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
                if (WINDOWS.contains(cmd.packageType())
                        && !cmd.isPackageUnpacked(
                                "Not verifying desktop integration")) {
                    // Check main launcher
                    WindowsHelper.verifyDesktopIntegration(cmd, null);
                    // Check additional launchers
                    cmd.addLauncherNames().forEach(name -> {
                        WindowsHelper.verifyDesktopIntegration(cmd, name);
                    });
                }
            }

            if (LauncherAsServiceVerifier.SUPPORTED_PACKAGES.contains(
                    cmd.packageType())) {
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
            if (WINDOWS.contains(cmd.packageType())) {
                // On Windows it is always two entries:
                // installation home directory and MSI file
                expectedRootCount = 2;
            } else if (withServices && MAC_PKG.equals(cmd.packageType())) {
                expectedRootCount = 2;
            } else if (LINUX.contains(cmd.packageType())) {
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

                if (WINDOWS.contains(cmd.packageType())) {
                    // Check main launcher
                    WindowsHelper.verifyDesktopIntegration(cmd, null);
                    // Check additional launchers
                    cmd.addLauncherNames().forEach(name -> {
                        WindowsHelper.verifyDesktopIntegration(cmd, name);
                    });
                }
            }

            Path appInstallDir = cmd.appInstallationDirectory();
            if (TKit.isLinux() && Path.of("/").equals(appInstallDir)) {
                ApplicationLayout appLayout = cmd.appLayout();
                TKit.assertPathExists(appLayout.runtimeDirectory(), false);
            } else {
                TKit.assertPathExists(appInstallDir, false);
            }

            if (LauncherAsServiceVerifier.SUPPORTED_PACKAGES.contains(
                    cmd.packageType())) {
                LauncherAsServiceVerifier.verifyUninstalled(cmd);
            }

            uninstallVerifiers.forEach(v -> v.accept(cmd));
        }

        private final List<Consumer<JPackageCommand>> initializers;
        private final List<BiConsumer<JPackageCommand, Executor.Result>> bundleVerifiers;
        private final List<Consumer<JPackageCommand>> installVerifiers;
        private final List<Consumer<JPackageCommand>> uninstallVerifiers;
    }

    private static Map<PackageType, PackageHandlers> createDefaultPackageHandlers() {
        HashMap<PackageType, PackageHandlers> handlers = new HashMap<>();
        if (TKit.isLinux()) {
            handlers.put(LINUX_DEB, LinuxHelper.createDebPackageHandlers());
            handlers.put(LINUX_RPM, LinuxHelper.createRpmPackageHandlers());
        }

        if (TKit.isWindows()) {
            handlers.put(WIN_MSI, WindowsHelper.createMsiPackageHandlers());
            handlers.put(WIN_EXE, WindowsHelper.createExePackageHandlers());
        }

        if (TKit.isOSX()) {
            handlers.put(MAC_DMG,  MacHelper.createDmgPackageHandlers());
            handlers.put(MAC_PKG,  MacHelper.createPkgPackageHandlers());
        }

        return handlers;
    }

    private Collection<PackageType> currentTypes;
    private Set<PackageType> excludeTypes;
    private int expectedJPackageExitCode;
    private Map<PackageType, Handler> handlers;
    private Set<String> namedInitializers;
    private Map<PackageType, PackageHandlers> packageHandlers;

    private final static File BUNDLE_OUTPUT_DIR;

    static {
        final String propertyName = "output";
        String val = TKit.getConfigProperty(propertyName);
        if (val == null) {
            BUNDLE_OUTPUT_DIR = null;
        } else {
            BUNDLE_OUTPUT_DIR = new File(val).getAbsoluteFile();

            if (!BUNDLE_OUTPUT_DIR.isDirectory()) {
                throw new IllegalArgumentException(String.format("Invalid value of %s sytem property: [%s]. Should be existing directory",
                        TKit.getConfigPropertyName(propertyName),
                        BUNDLE_OUTPUT_DIR));
            }
        }
    }
}
