/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.incubator.jpackage.internal.AppImageFile;
import static jdk.jpackage.test.PackageType.*;

/**
 * Instance of PackageTest is for configuring and running a single jpackage
 * command to produce platform specific package bundle.
 *
 * Provides methods to hook up custom configuration of jpackage command and
 * verification of the output bundle.
 */
public final class PackageTest {

    /**
     * Default test configuration for jpackage command. Default jpackage command
     * initialization includes:
     * <li>Set --input and --dest parameters.
     * <li>Set --name parameter. Value of the parameter is the name of the first
     * class with main function found in the callers stack. Defaults can be
     * overridden with custom initializers set with subsequent addInitializer()
     * function calls.
     */
    public PackageTest() {
        action = DEFAULT_ACTION;
        excludeTypes = new HashSet<>();
        forTypes();
        setExpectedExitCode(0);
        handlers = new HashMap<>();
        namedInitializers = new HashSet<>();
        currentTypes.forEach(v -> handlers.put(v, new Handler(v)));
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
            newTypes = PackageType.NATIVE;
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
        currentTypes.stream().forEach(type -> handlers.get(type).addInitializer(
                ThrowingConsumer.toConsumer(v)));
        return this;
    }

    public PackageTest addInitializer(ThrowingConsumer<JPackageCommand> v) {
        return addInitializer(v, null);
    }

    public PackageTest addBundleVerifier(
            BiConsumer<JPackageCommand, Executor.Result> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addBundleVerifier(v));
        return this;
    }

    public PackageTest addBundleVerifier(ThrowingConsumer<JPackageCommand> v) {
        return addBundleVerifier(
                (cmd, unused) -> ThrowingConsumer.toConsumer(v).accept(cmd));
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            BiConsumer<String, String> pred) {
        return addBundleVerifier(cmd -> {
            pred.accept(propertyName,
                    LinuxHelper.getBundleProperty(cmd, propertyName));
        });
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            String expectedPropertyValue) {
        return addBundlePropertyVerifier(propertyName, (unused, v) -> {
            TKit.assertEquals(expectedPropertyValue, v, String.format(
                    "Check value of %s property is [%s]", propertyName, v));
        });
    }

    public PackageTest addBundleDesktopIntegrationVerifier(boolean integrated) {
        forTypes(LINUX, () -> {
            LinuxHelper.addBundleDesktopIntegrationVerifier(this, integrated);
        });
        return this;
    }

    public PackageTest addInstallVerifier(ThrowingConsumer<JPackageCommand> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addInstallVerifier(
                        ThrowingConsumer.toConsumer(v)));
        return this;
    }

    public PackageTest addUninstallVerifier(ThrowingConsumer<JPackageCommand> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addUninstallVerifier(
                        ThrowingConsumer.toConsumer(v)));
        return this;
    }

    static void withTestFileAssociationsFile(FileAssociations fa,
            ThrowingConsumer<Path> consumer) {
        final String testFileDefaultName = String.join(".", "test",
                fa.getSuffix());
        TKit.withTempFile(testFileDefaultName, fa.getSuffix(), testFile -> {
            if (TKit.isLinux()) {
                LinuxHelper.initFileAssociationsTestFile(testFile);
            }
            consumer.accept(testFile);
        });
    }

    PackageTest addHelloAppFileAssociationsVerifier(FileAssociations fa,
            String... faLauncherDefaultArgs) {

        // Setup test app to have valid jpackage command line before
        // running check of type of environment.
        addInitializer(cmd -> new HelloApp(null).addTo(cmd), "HelloApp");

        String noActionMsg = "Not running file associations test";
        if (GraphicsEnvironment.isHeadless()) {
            TKit.trace(String.format(
                    "%s because running in headless environment", noActionMsg));
            return this;
        }

        addInstallVerifier(cmd -> {
            if (cmd.isFakeRuntime(noActionMsg)) {
                return;
            }

            withTestFileAssociationsFile(fa, testFile -> {
                testFile = testFile.toAbsolutePath().normalize();

                final Path appOutput = testFile.getParent()
                        .resolve(HelloApp.OUTPUT_FILENAME);
                Files.deleteIfExists(appOutput);

                TKit.trace(String.format("Use desktop to open [%s] file",
                        testFile));
                Desktop.getDesktop().open(testFile.toFile());
                TKit.waitForFileCreated(appOutput, 7);

                List<String> expectedArgs = new ArrayList<>(List.of(
                        faLauncherDefaultArgs));
                expectedArgs.add(testFile.toString());

                // Wait a little bit after file has been created to
                // make sure there are no pending writes into it.
                Thread.sleep(3000);
                HelloApp.verifyOutputFile(appOutput, expectedArgs);
            });
        });

        forTypes(PackageType.LINUX, () -> {
            LinuxHelper.addFileAssociationsVerifier(this, fa);
        });

        return this;
    }

    PackageTest forTypes(Collection<PackageType> types, Runnable action) {
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

    PackageTest forTypes(PackageType type, Runnable action) {
        return forTypes(List.of(type), action);
    }

    PackageTest notForTypes(Collection<PackageType> types, Runnable action) {
        Set<PackageType> workset = new HashSet<>(currentTypes);
        workset.removeAll(types);
        return forTypes(workset, action);
    }

    PackageTest notForTypes(PackageType type, Runnable action) {
        return notForTypes(List.of(type), action);
    }

    public PackageTest configureHelloApp() {
        return configureHelloApp(null);
    }

    public PackageTest configureHelloApp(String encodedName) {
        addInitializer(
                cmd -> new HelloApp(JavaAppDesc.parse(encodedName)).addTo(cmd));
        addInstallVerifier(HelloApp::executeLauncherAndVerifyOutput);
        return this;
    }

    public void run() {
        List<Handler> supportedHandlers = handlers.values().stream()
                .filter(entry -> !entry.isVoid())
                .collect(Collectors.toList());

        if (supportedHandlers.isEmpty()) {
            // No handlers with initializers found. Nothing to do.
            return;
        }

        Supplier<JPackageCommand> initializer = new Supplier<>() {
            @Override
            public JPackageCommand get() {
                JPackageCommand cmd = new JPackageCommand().setDefaultInputOutput();
                if (bundleOutputDir != null) {
                    cmd.setArgumentValue("--dest", bundleOutputDir.toString());
                }
                cmd.setDefaultAppName();
                return cmd;
            }
        };

        supportedHandlers.forEach(handler -> handler.accept(initializer.get()));
    }

    public PackageTest setAction(Action value) {
        action = value;
        return this;
    }

    public Action getAction() {
        return action;
    }

    private class Handler implements Consumer<JPackageCommand> {

        Handler(PackageType type) {
            if (!PackageType.NATIVE.contains(type)) {
                throw new IllegalArgumentException(
                        "Attempt to configure a test for image packaging");
            }
            this.type = type;
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
        public void accept(JPackageCommand cmd) {
            type.applyTo(cmd);

            initializers.stream().forEach(v -> v.accept(cmd));
            cmd.executePrerequisiteActions();

            switch (action) {
                case CREATE:
                    Executor.Result result = cmd.execute();
                    result.assertExitCodeIs(expectedJPackageExitCode);
                    if (expectedJPackageExitCode == 0) {
                        TKit.assertFileExists(cmd.outputBundle());
                    } else {
                        TKit.assertPathExists(cmd.outputBundle(), false);
                    }
                    verifyPackageBundle(cmd.createImmutableCopy(), result);
                    break;

                case VERIFY_INSTALL:
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageInstalled(cmd.createImmutableCopy());
                    }
                    break;

                case VERIFY_UNINSTALL:
                    if (expectedJPackageExitCode == 0) {
                        verifyPackageUninstalled(cmd.createImmutableCopy());
                    }
                    break;
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd,
                Executor.Result result) {
            if (expectedJPackageExitCode == 0) {
                if (PackageType.LINUX.contains(cmd.packageType())) {
                    LinuxHelper.verifyPackageBundleEssential(cmd);
                }
            }
            bundleVerifiers.stream().forEach(v -> v.accept(cmd, result));
        }

        private void verifyPackageInstalled(JPackageCommand cmd) {
            TKit.trace(String.format("Verify installed: %s",
                    cmd.getPrintableCommandLine()));
            TKit.assertDirectoryExists(cmd.appRuntimeDirectory());
            if (!cmd.isRuntime()) {
                TKit.assertExecutableFileExists(cmd.appLauncherPath());

                if (PackageType.WINDOWS.contains(cmd.packageType())) {
                    new WindowsHelper.AppVerifier(cmd);
                }
            }

            TKit.assertPathExists(AppImageFile.getPathInAppImage(
                    cmd.appInstallationDirectory()), false);

            installVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private void verifyPackageUninstalled(JPackageCommand cmd) {
            TKit.trace(String.format("Verify uninstalled: %s",
                    cmd.getPrintableCommandLine()));
            if (!cmd.isRuntime()) {
                TKit.assertPathExists(cmd.appLauncherPath(), false);

                if (PackageType.WINDOWS.contains(cmd.packageType())) {
                    new WindowsHelper.AppVerifier(cmd);
                }
            }

            TKit.assertPathExists(cmd.appInstallationDirectory(), false);

            uninstallVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private final PackageType type;
        private final List<Consumer<JPackageCommand>> initializers;
        private final List<BiConsumer<JPackageCommand, Executor.Result>> bundleVerifiers;
        private final List<Consumer<JPackageCommand>> installVerifiers;
        private final List<Consumer<JPackageCommand>> uninstallVerifiers;
    }

    private Collection<PackageType> currentTypes;
    private Set<PackageType> excludeTypes;
    private int expectedJPackageExitCode;
    private Map<PackageType, Handler> handlers;
    private Set<String> namedInitializers;
    private Action action;

    /**
     * Test action.
     */
    static public enum Action {
        /**
         * Create bundle.
         */
        CREATE,
        /**
         * Verify bundle installed.
         */
        VERIFY_INSTALL,
        /**
         * Verify bundle uninstalled.
         */
        VERIFY_UNINSTALL;

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', '-');
        }
    };
    private final static Action DEFAULT_ACTION;
    private final static File bundleOutputDir;

    static {
        final String propertyName = "output";
        String val = TKit.getConfigProperty(propertyName);
        if (val == null) {
            bundleOutputDir = null;
        } else {
            bundleOutputDir = new File(val).getAbsoluteFile();

            if (!bundleOutputDir.isDirectory()) {
                throw new IllegalArgumentException(String.format(
                        "Invalid value of %s sytem property: [%s]. Should be existing directory",
                        TKit.getConfigPropertyName(propertyName),
                        bundleOutputDir));
            }
        }
    }

    static {
        final String propertyName = "action";
        String action = Optional.ofNullable(TKit.getConfigProperty(propertyName)).orElse(
                Action.CREATE.toString()).toLowerCase();
        DEFAULT_ACTION = Stream.of(Action.values()).filter(
                a -> a.toString().equals(action)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException(String.format(
                                "Unrecognized value of %s property: [%s]",
                                TKit.getConfigPropertyName(propertyName), action)));
    }
}
