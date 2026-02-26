/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.test.AdditionalLauncher.forEachAdditionalLauncher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

/**
 * jpackage command line with prerequisite actions. Prerequisite actions can be
 * anything. The simplest is to compile test application and pack in a jar for
 * use on jpackage command line.
 */
public class JPackageCommand extends CommandArguments<JPackageCommand> {

    @SuppressWarnings("this-escape")
    public JPackageCommand() {
        toolProviderSource = new ToolProviderSource();
        prerequisiteActions = new Actions();
        verifyActions = new Actions();
        excludeStandardAsserts(StandardAssert.MAIN_LAUNCHER_DESCRIPTION);
        removeOldOutputBundle = true;
    }

    private JPackageCommand(JPackageCommand cmd, boolean immutable) {
        args.addAll(cmd.args);
        toolProviderSource = cmd.toolProviderSource.copy();
        saveConsoleOutput = cmd.saveConsoleOutput;
        discardStdout = cmd.discardStdout;
        discardStderr = cmd.discardStderr;
        suppressOutput = cmd.suppressOutput;
        ignoreDefaultRuntime = cmd.ignoreDefaultRuntime;
        ignoreDefaultVerbose = cmd.ignoreDefaultVerbose;
        removeOldOutputBundle = cmd.removeOldOutputBundle;
        this.immutable = immutable;
        prerequisiteActions = new Actions(cmd.prerequisiteActions);
        verifyActions = new Actions(cmd.verifyActions);
        standardAsserts = cmd.standardAsserts;
        readOnlyPathAsserts = cmd.readOnlyPathAsserts;
        validators = cmd.validators;
        executeInDirectory = cmd.executeInDirectory;
        winMsiLogFile = cmd.winMsiLogFile;
        unpackedPackageDirectory = cmd.unpackedPackageDirectory;
    }

    JPackageCommand createImmutableCopy() {
        return new JPackageCommand(this, true);
    }

    JPackageCommand createMutableCopy() {
        return new JPackageCommand(this, false);
    }

    public JPackageCommand setArgumentValue(String argName, String newValue) {
        verifyMutable();

        String prevArg = null;
        ListIterator<String> it = args.listIterator();
        while (it.hasNext()) {
            String value = it.next();
            if (prevArg != null && prevArg.equals(argName)) {
                if (newValue != null) {
                    it.set(newValue);
                } else {
                    it.remove();
                    it.previous();
                    it.remove();
                }
                return this;
            }
            prevArg = value;
        }

        if (newValue != null) {
            addArguments(argName, newValue);
        }

        return this;
    }

    public JPackageCommand setArgumentValue(String argName, Path newValue) {
        return setArgumentValue(argName, newValue.toString());
    }

    public JPackageCommand removeArgumentWithValue(String argName) {
        return setArgumentValue(argName, (String)null);
    }

    public JPackageCommand removeArgument(String argName) {
        args = args.stream().filter(arg -> !arg.equals(argName)).collect(
                Collectors.toList());
        return this;
    }

    public boolean hasArgument(String argName) {
        return args.contains(argName);
    }

    public <T> T getArgumentValue(String argName,
            Function<JPackageCommand, T> defaultValueSupplier,
            Function<String, T> stringConverter) {
        String prevArg = null;
        for (String arg : args) {
            if (prevArg != null && prevArg.equals(argName)) {
                return stringConverter.apply(arg);
            }
            prevArg = arg;
        }
        if (defaultValueSupplier != null) {
            return defaultValueSupplier.apply(this);
        }
        return null;
    }

    public <T> T getArgumentValue(String argName,
            Supplier<T> defaultValueSupplier,
            Function<String, T> stringConverter) {
        return getArgumentValue(argName,
                Optional.ofNullable(defaultValueSupplier).map(supplier -> {
                    return (Function<JPackageCommand, T>)unused -> supplier.get();
                }).orElse(null), stringConverter);
    }

    public String getArgumentValue(String argName,
            Supplier<String> defaultValueSupplier) {
        return getArgumentValue(argName, defaultValueSupplier, v -> v);
    }

    public String getArgumentValue(String argName) {
        return getArgumentValue(argName, (Supplier<String>)null);
    }

    public String[] getAllArgumentValues(String argName) {
        List<String> values = new ArrayList<>();
        String prevArg = null;
        for (String arg : args) {
            if (prevArg != null && prevArg.equals(argName)) {
                values.add(arg);
            }
            prevArg = arg;
        }
        return values.toArray(String[]::new);
    }

    public JPackageCommand addArguments(String name, Path value) {
        return addArguments(name, value.toString());
    }

    public JPackageCommand mutate(Consumer<JPackageCommand> mutator) {
        return mutate(List.of(mutator));
    }

    public JPackageCommand mutate(Iterable<Consumer<JPackageCommand>> mutators) {
        for (var mutator : mutators) {
            mutator.accept(this);
        }
        return this;
    }

    public boolean isImagePackageType() {
        return PackageType.IMAGE == getArgumentValue("--type",
                () -> null, PACKAGE_TYPES::get);
    }

    public PackageType packageType() {
        // Don't try to be in sync with jpackage defaults. Keep it simple:
        // if no `--type` explicitely set on the command line, consider
        // this is operator's fault.
        return getArgumentValue("--type",
                () -> {
                    throw new IllegalStateException("Package type not set");
                }, PACKAGE_TYPES::get);
    }

    public Path outputDir() {
        var path = getArgumentValue("--dest", () -> Path.of("."), Path::of);
        return Optional.ofNullable(executeInDirectory).map(base -> {
            return base.resolve(path);
        }).orElse(path);
    }

    public Path inputDir() {
        return getArgumentValue("--input", () -> null, Path::of);
    }

    public String version() {
        return getArgumentValue("--app-version", () -> "1.0");
    }

    public String name() {
        return nameFromAppImage().or(this::nameFromBasicArgs).or(this::nameFromRuntimeImage).orElseThrow();
    }

    public String installerName() {
        verifyIsOfType(PackageType.NATIVE);
        return nameFromBasicArgs().or(this::nameFromAppImage).or(this::nameFromRuntimeImage).orElseThrow();
    }

    private Optional<String> nameFromAppImage() {
        return Optional.ofNullable(getArgumentValue("--app-image"))
                .map(Path::of).map(AppImageFile::load).map(AppImageFile::mainLauncherName);
    }

    private Optional<String> nameFromRuntimeImage() {
        return Optional.ofNullable(getArgumentValue("--runtime-image"))
                .map(Path::of).map(Path::getFileName).map(Path::toString);
    }

    private Optional<String> nameFromBasicArgs() {
        return Optional.ofNullable(getArgumentValue("--name")).or(
                () -> Optional.ofNullable(getArgumentValue("--main-class")));
    }

    public boolean isRuntime() {
        return  hasArgument("--runtime-image")
                && !hasArgument("--main-jar")
                && !hasArgument("--module")
                && !hasArgument("--app-image");
    }

    public JPackageCommand setDefaultInputOutput() {
        setArgumentValue("--input", TKit.workDir().resolve("input"));
        setArgumentValue("--dest", TKit.workDir().resolve("output"));
        return this;
    }

    public JPackageCommand setInputToEmptyDirectory() {
        if (Files.exists(inputDir())) {
            setArgumentValue("--input", TKit.createTempDirectory("input"));
        }
        return this;
    }

    public JPackageCommand setFakeRuntime() {
        verifyMutable();
        addPrerequisiteAction(cmd -> {
            cmd.setArgumentValue("--runtime-image", createInputRuntimeImage(RuntimeImageType.RUNTIME_TYPE_FAKE));
        });

        return this;
    }

    public JPackageCommand usePredefinedAppImage(Path predefinedAppImagePath) {
        return setArgumentValue("--app-image", Objects.requireNonNull(predefinedAppImagePath))
                .removeArgumentWithValue("--input");
    }

    JPackageCommand addPrerequisiteAction(ThrowingConsumer<JPackageCommand, ? extends Exception> action) {
        prerequisiteActions.add(action);
        return this;
    }

    JPackageCommand addVerifyAction(ThrowingConsumer<JPackageCommand, ? extends Exception> action) {
        return addVerifyAction(action, ActionRole.DEFAULT);
    }

    enum ActionRole {
        DEFAULT,
        LAUNCHER_VERIFIER,
        ;
    }

    JPackageCommand addVerifyAction(ThrowingConsumer<JPackageCommand, ? extends Exception> action, ActionRole actionRole) {
        verifyActions.add(action, actionRole);
        return this;
    }

    Stream<ThrowingConsumer<JPackageCommand, ? extends Exception>> getVerifyActionsWithRole(ActionRole actionRole) {
        return verifyActions.actionsWithRole(actionRole);
    }

    /**
     * Shorthand for {@code helloAppImage(null)}.
     */
    public static JPackageCommand helloAppImage() {
        JavaAppDesc javaAppDesc = null;
        return helloAppImage(javaAppDesc);
    }

    /**
     * Creates new JPackageCommand instance configured with the test Java app.
     * For the explanation of `javaAppDesc` parameter, see documentation for
     * #JavaAppDesc.parse() method.
     *
     * @param javaAppDesc Java application description
     * @return this
     */
    public static JPackageCommand helloAppImage(String javaAppDesc) {
        final JavaAppDesc appDesc;
        if (javaAppDesc == null) {
            appDesc = null;
        } else {
            appDesc = JavaAppDesc.parse(javaAppDesc);
        }
        return helloAppImage(appDesc);
    }

    public static JPackageCommand helloAppImage(JavaAppDesc javaAppDesc) {
        JPackageCommand cmd = new JPackageCommand();
        cmd.setDefaultInputOutput().setDefaultAppName();
        cmd.setPackageType(PackageType.IMAGE);
        new HelloApp(javaAppDesc).addTo(cmd);
        return cmd;
    }

    public enum RuntimeImageType {

        /**
         * Runtime suitable for running the default "Hello" test app.
         */
        RUNTIME_TYPE_HELLO_APP,

        /**
         * Fake runtime.
         */
        RUNTIME_TYPE_FAKE,

        ;
    }

    public static Path createInputRuntimeImage() {
        return createInputRuntimeImage(RuntimeImageType.RUNTIME_TYPE_HELLO_APP);
    }

    public static Path createInputRuntimeImage(RuntimeImageType role) {
        Objects.requireNonNull(role);

        final Path runtimeImageDir;
        switch (role) {

            case RUNTIME_TYPE_FAKE -> {
                Consumer<Path> createBulkFile = ThrowingConsumer.toConsumer(path -> {
                    Files.createDirectories(path.getParent());
                    try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                        byte[] bytes = new byte[4 * 1024];
                        new SecureRandom().nextBytes(bytes);
                        out.write(bytes);
                    }
                });

                runtimeImageDir = TKit.createTempDirectory("fake_runtime");

                TKit.trace(String.format("Init fake runtime in [%s] directory", runtimeImageDir));

                if (TKit.isOSX()) {
                    // Make MacAppImageBuilder happy
                    createBulkFile.accept(runtimeImageDir.resolve(Path.of("lib/jli/libjli.dylib")));
                }

                // Make sure fake runtime takes some disk space.
                // Package bundles with 0KB size are unexpected and considered
                // an error by PackageTest.
                createBulkFile.accept(runtimeImageDir.resolve(Path.of("lib", "bulk")));
            }

            case RUNTIME_TYPE_HELLO_APP -> {
                if (JPackageCommand.DEFAULT_RUNTIME_IMAGE != null && !isFakeRuntime(DEFAULT_RUNTIME_IMAGE)) {
                    runtimeImageDir = JPackageCommand.DEFAULT_RUNTIME_IMAGE;
                } else {
                    runtimeImageDir = TKit.createTempDirectory("runtime-image").resolve("data");

                    new Executor().setToolProvider(JavaTool.JLINK)
                            .dumpOutput()
                            .addArguments(
                                    "--output", runtimeImageDir.toString(),
                                    "--add-modules", "java.desktop",
                                    "--strip-debug",
                                    "--no-header-files",
                                    "--no-man-pages")
                            .execute();
                }
            }

            default -> {
                throw ExceptionBox.reachedUnreachable();
            }
        }

        return runtimeImageDir;
    }

    public JPackageCommand setPackageType(PackageType type) {
        verifyMutable();
        type.applyTo(this);
        return this;
    }

    public JPackageCommand setDefaultAppName() {
        return setArgumentValue("--name", TKit.getCurrentDefaultAppName());
    }

    /**
     * Returns path to output bundle of configured jpackage command.
     *
     * If this is build image command, returns path to application image directory.
     *
     * Special case for masOS. If this is sign app image command, returns value
     * of "--app-image".
     */
    public Path outputBundle() {
        final String bundleName;
        if (isImagePackageType()) {
            String dirName;
            if (!TKit.isOSX()) {
                dirName = name();
            } else if (MacHelper.signPredefinedAppImage(this)) {
                // Request to sign external app image, not to build a new one
                dirName = getArgumentValue("--app-image");
            } else {
                dirName = name() + ".app";
            }
            bundleName = dirName;
        } else if (TKit.isLinux()) {
            bundleName = LinuxHelper.getBundleName(this);
        } else if (TKit.isWindows()) {
            bundleName = WindowsHelper.getBundleName(this);
        } else if (TKit.isOSX()) {
            bundleName = MacHelper.getBundleName(this);
        } else {
            throw TKit.throwUnknownPlatformError();
        }

        return outputDir().resolve(bundleName);
    }

    Optional<Path> nullableOutputBundle() {
        try {
            return Optional.ofNullable(outputBundle());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * Returns application layout.
     *
     * If this is build image command, returns application image layout of the
     * output bundle relative to output directory. Otherwise returns layout of
     * installed application relative to the root directory.
     *
     * If this command builds Java runtime, not an application, returns
     * corresponding layout.
     */
    public ApplicationLayout appLayout() {
        ApplicationLayout layout = onLinuxPackageInstallDir(null,
                installDir -> {
                    String packageName = LinuxHelper.getPackageName(this);
                    // Convert '/usr' to 'usr'. It will be set to proper root in
                    // subsequent ApplicationLayout.resolveAt() call.
                    return ApplicationLayout.linuxUsrTreePackageImage(Path.of(
                            "/").relativize(installDir), packageName);
                });

        if (layout != null) {
        } else if (isRuntime()) {
            layout = ApplicationLayout.platformJavaRuntime();
        } else {
            layout = ApplicationLayout.platformAppImage();
        }

        if (isImagePackageType()) {
            return layout.resolveAt(outputBundle());
        }

        return layout.resolveAt(pathToUnpackedPackageFile(
                appInstallationDirectory()));
    }

    /**
     * Returns path to package file in unpacked package directory or the given
     * path if the package is not unpacked.
     */
    public Path pathToUnpackedPackageFile(Path path) {
        Path unpackDir = unpackedPackageDirectory();
        if (unpackDir == null) {
            return path;
        }
        return unpackDir.resolve(TKit.removeRootFromAbsolutePath(path));
    }

    /**
     * Returns path to package file from the path in unpacked package directory
     * or the given path if the package is not unpacked.
     */
    public Path pathToPackageFile(Path path) {
        Path unpackDir = unpackedPackageDirectory();
        if (unpackDir == null) {
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException(String.format(
                        "Path [%s] is not absolute", path));
            }
            return path;
        }

        if (!path.startsWith(unpackDir)) {
            throw new IllegalArgumentException(String.format(
                    "Path [%s] doesn't start with [%s] path", path, unpackDir));
        }

        return Path.of("/").resolve(unpackDir.relativize(path));
    }

    Path unpackedPackageDirectory() {
        verifyIsOfType(PackageType.NATIVE);
        return unpackedPackageDirectory;
    }

    /**
     * Returns path to directory where application will be installed or null if
     * this is build image command.
     *
     * E.g. on Linux for app named Foo default the function will return
     * `/opt/foo`.
     * On Linux for install directory in `/usr` tree the function returns `/`.
     *
     */
    public Path appInstallationDirectory() {
        if (isImagePackageType()) {
            return null;
        }

        if (TKit.isLinux()) {
            return onLinuxPackageInstallDir(installDir -> installDir.resolve(
                    LinuxHelper.getPackageName(this)),
                    installDir -> Path.of("/"));
        }

        if (TKit.isWindows()) {
            return WindowsHelper.getInstallationDirectory(this);
        }

        if (TKit.isOSX()) {
            return MacHelper.getInstallationDirectory(this);
        }

        throw TKit.throwUnknownPlatformError();
    }

    /**
     * Returns path to application's Java runtime.
     * If the command will package Java runtime only, returns correct path to
     * runtime directory.
     *
     * E.g.:
     * [jpackage --name Foo --type rpm] -> `/opt/foo/lib/runtime`
     * [jpackage --name Foo --type app-image --dest bar] -> `bar/Foo/lib/runtime`
     * [jpackage --name Foo --type rpm --runtime-image java] -> `/opt/foo`
     */
    public Path appRuntimeDirectory() {
        return appLayout().runtimeDirectory();
    }

    /**
     * Returns the name of the main launcher. It will read the name of the main
     * launcher from the external app image if such is specified.
     *
     * @return the name of the main launcher
     *
     * @throws IllegalArgumentException if the command is configured for packaging
     *                                  Java runtime
     */
    public String mainLauncherName() {
        verifyNotRuntime();
        return name();
    }

    boolean isMainLauncher(String launcherName) {
        return launcherName == null || mainLauncherName().equals(launcherName);
    }

    /**
     * Returns path for application launcher with the given name.
     *
     * E.g.: [jpackage --name Foo --type rpm] -> `/opt/foo/bin/Foo`
     * [jpackage --name Foo --type app-image --dest bar] ->
     * `bar/Foo/bin/Foo`
     *
     * @param launcherName name of launcher or {@code null} for the main
     * launcher
     *
     * @throws IllegalArgumentException if the command is configured for
     * packaging Java runtime
     */
    public Path appLauncherPath(String launcherName) {
        verifyNotRuntime();
        if (launcherName == null) {
            launcherName = mainLauncherName();
        }

        if (TKit.isWindows()) {
            launcherName = launcherName + ".exe";
        }

        return appLayout().launchersDirectory().resolve(launcherName);
    }

    /**
     * Shorthand for {@code appLauncherPath(null)}.
     */
    public Path appLauncherPath() {
        return appLauncherPath(null);
    }

    /**
     * Returns names of additional launchers or an empty list if none configured.
     * <p>
     * If {@code lookupInPrederfinedAppImage} is {@code true} and the command is
     * configured with an external app image, it will read names of the additional
     * launchers from the external app image.
     *
     * @param lookupInPrederfinedAppImage if to read names of additional launchers
     *                                    from an external app image
     *
     * @return the names of additional launchers
     */
    public List<String> addLauncherNames(boolean lookupInPrederfinedAppImage) {
        if (isRuntime()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        if (lookupInPrederfinedAppImage) {
            Optional.ofNullable(getArgumentValue("--app-image"))
                    .map(Path::of)
                    .map(AppImageFile::load)
                    .map(AppImageFile::addLaunchers)
                    .map(Map::keySet)
                    .ifPresent(names::addAll);
        }
        forEachAdditionalLauncher(this, (launcherName, propFile) -> {
            names.add(launcherName);
        });
        return Collections.unmodifiableList(names);
    }

    /**
     * Returns names of all launchers.
     * <p>
     * If the list is not empty, the first element is {@code null} referencing the
     * main launcher. In the case of runtime packaging, the list is empty.
     *
     * @return the names of all launchers
     */
    public List<String> launcherNames(boolean lookupInPrederfinedAppImage) {
        if (isRuntime()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        names.add(null);
        names.addAll(addLauncherNames(lookupInPrederfinedAppImage));
        return Collections.unmodifiableList(names);
    }

    JPackageCommand verifyNotRuntime() {
        if (isRuntime()) {
            throw new UnsupportedOperationException("Java runtime packaging");
        }
        return this;
    }

    /**
     * Returns path to .cfg file of the given application launcher.
     *
     * E.g.:
     * [jpackage --name Foo --type rpm] -> `/opt/foo/lib/app/Foo.cfg`
     * [jpackage --name Foo --type app-image --dest bar] -> `bar/Foo/lib/app/Foo.cfg`
     *
     * @param launcher name of launcher or {@code null} for the main launcher
     *
     * @throws IllegalArgumentException if the command is configured for
     * packaging Java runtime
     */
    public Path appLauncherCfgPath(String launcherName) {
        verifyNotRuntime();
        if (launcherName == null) {
            launcherName = mainLauncherName();
        }
        return appLayout().appDirectory().resolve(launcherName + ".cfg");
    }

    public boolean isFakeRuntime(String msg) {
        if (isFakeRuntime(appRuntimeDirectory())) {
            // Fake runtime
            Path runtimeDir = appRuntimeDirectory();
            TKit.trace(String.format(
                    "%s because application runtime directory [%s] is incomplete",
                    msg, runtimeDir));
            return true;
        }
        return false;
    }

    private static boolean isFakeRuntime(Path runtimeDir) {
        final Collection<Path> criticalRuntimeFiles;
        if (TKit.isWindows()) {
            criticalRuntimeFiles = WindowsHelper.CRITICAL_RUNTIME_FILES;
        } else if (TKit.isLinux()) {
            criticalRuntimeFiles = LinuxHelper.CRITICAL_RUNTIME_FILES;
        } else if (TKit.isOSX()) {
            criticalRuntimeFiles = MacHelper.CRITICAL_RUNTIME_FILES;
        } else {
            throw TKit.throwUnknownPlatformError();
        }

        return !criticalRuntimeFiles.stream().map(runtimeDir::resolve).allMatch(
                Files::exists);
    }

    public boolean canRunLauncher(String msg) {
        if (isFakeRuntime(msg)) {
            return false;
        }

        if (isPackageUnpacked()) {
            return Boolean.FALSE != onLinuxPackageInstallDir(null, installDir -> {
                TKit.trace(String.format(
                    "%s because the package in [%s] directory is not installed ",
                    msg, installDir));
                return Boolean.FALSE;
            });
        }

        return true;
    }

    public boolean isPackageUnpacked(String msg) {
        if (isPackageUnpacked()) {
            TKit.trace(String.format(
                    "%s because package was unpacked, not installed", msg));
            return true;
        }
        return false;
    }

    public boolean isPackageUnpacked() {
        return unpackedPackageDirectory != null;
    }

    public static void useToolProviderByDefault(ToolProvider jpackageToolProvider) {
        TKit.state().setProperty(DefaultToolProviderKey.VALUE, Objects.requireNonNull(jpackageToolProvider));
    }

    public static void useToolProviderByDefault() {
        useToolProviderByDefault(JavaTool.JPACKAGE.asToolProvider());
    }

    public static void useExecutableByDefault() {
        TKit.state().setProperty(DefaultToolProviderKey.VALUE, null);
    }

    public JPackageCommand useToolProvider(boolean v) {
        verifyMutable();
        if (v) {
            toolProviderSource.useDefaultToolProvider();
        } else {
            toolProviderSource.useProcess();
        }
        return this;
    }

    public JPackageCommand useToolProvider(ToolProvider v) {
        verifyMutable();
        toolProviderSource.useToolProvider(v);
        return this;
    }

    public JPackageCommand setDirectory(Path v) {
        verifyMutable();
        executeInDirectory = v;
        return this;
    }

    public JPackageCommand saveConsoleOutput(boolean v) {
        verifyMutable();
        saveConsoleOutput = v;
        return this;
    }

    public JPackageCommand discardStdout(boolean v) {
        verifyMutable();
        discardStdout = v;
        return this;
    }

    public JPackageCommand discardStderr(boolean v) {
        verifyMutable();
        discardStderr = v;
        return this;
    }

    public JPackageCommand dumpOutput(boolean v) {
        verifyMutable();
        suppressOutput = !v;
        return this;
    }

    public JPackageCommand ignoreDefaultRuntime(boolean v) {
        verifyMutable();
        ignoreDefaultRuntime = v;
        return this;
    }

    public JPackageCommand ignoreFakeRuntime() {
        return ignoreDefaultRuntime(Optional.ofNullable(DEFAULT_RUNTIME_IMAGE)
                .map(JPackageCommand::isFakeRuntime).orElse(false));
    }

    public JPackageCommand ignoreDefaultVerbose(boolean v) {
        verifyMutable();
        ignoreDefaultVerbose = v;
        return this;
    }

    /**
     * Configures this instance to optionally remove the existing output bundle
     * before running the jpackage command.
     *
     * @param v {@code true} to remove existing output bundle before running the
     *          jpackage command, and {@code false} otherwise
     * @return this
     */
    public JPackageCommand removeOldOutputBundle(boolean v) {
        verifyMutable();
        removeOldOutputBundle = v;
        return this;
    }

    /**
     * Returns {@code true} if this instance will remove existing output bundle
     * before running the jpackage command, and {@code false} otherwise.
     */
    public boolean isRemoveOldOutputBundle() {
        return removeOldOutputBundle;
    }

    public JPackageCommand validateOut(TKit.TextStreamVerifier validator) {
        new JPackageOutputValidator().add(validator).applyTo(this);
        return this;
    }

    public JPackageCommand validateErr(TKit.TextStreamVerifier validator) {
        new JPackageOutputValidator().stderr().add(validator).applyTo(this);
        return this;
    }

    public JPackageCommand validateResult(Consumer<Executor.Result> validator) {
        Objects.requireNonNull(validator);
        saveConsoleOutput(true);
        validators.add(validator);
        return this;
    }

    @FunctionalInterface
    public interface CannedArgument {
        public String value(JPackageCommand cmd);
    }

    public static CannedArgument cannedArgument(Function<JPackageCommand, Object> supplier, String label) {
        Objects.requireNonNull(supplier);
        Objects.requireNonNull(label);
        return new CannedArgument() {
            @Override
            public String value(JPackageCommand cmd) {
                return supplier.apply(cmd).toString();
            }

            @Override
            public String toString( ) {
                return label;
            }
        };
    }

    public static CannedFormattedString makeError(CannedFormattedString v) {
        return v.addPrefix("message.error-header");
    }

    public static CannedFormattedString makeError(String key, Object ... args) {
        return makeError(JPackageStringBundle.MAIN.cannedFormattedString(key, args));
    }

    public static CannedFormattedString makeAdvice(CannedFormattedString v) {
        return v.addPrefix("message.advice-header");
    }

    public static CannedFormattedString makeAdvice(String key, Object ... args) {
        return makeAdvice(JPackageStringBundle.MAIN.cannedFormattedString(key, args));
    }

    public String getValue(CannedFormattedString str) {
        return new CannedFormattedString(str.formatter(), str.key(), Stream.of(str.args()).map(arg -> {
            if (arg instanceof CannedArgument cannedArg) {
                return cannedArg.value(this);
            } else {
                return arg;
            }
        }).toArray()).getValue();
    }

    public JPackageCommand validateOut(CannedFormattedString... strings) {
        new JPackageOutputValidator().expectMatchingStrings(strings).applyTo(this);
        return this;
    }

    public JPackageCommand validateErr(CannedFormattedString... strings) {
        new JPackageOutputValidator().stderr().expectMatchingStrings(strings).applyTo(this);
        return this;
    }

    public boolean isWithToolProvider() {
        return toolProviderSource.toolProvider().isPresent();
    }

    public JPackageCommand executePrerequisiteActions() {
        prerequisiteActions.run();
        return this;
    }

    Executor createExecutor() {
        Executor exec = new Executor()
                .saveOutput(saveConsoleOutput).dumpOutput(!suppressOutput)
                .discardStdout(discardStdout).discardStderr(discardStderr)
                .setDirectory(executeInDirectory)
                .addArguments(args);

        toolProviderSource.toolProvider().ifPresentOrElse(exec::setToolProvider, () -> {
                    exec.setExecutable(JavaTool.JPACKAGE);
                    if (TKit.isWindows()) {
                        exec.setWindowsTmpDir(System.getProperty("java.io.tmpdir"));
                    }
                });

        return exec;
    }

    public Executor.Result executeIgnoreExitCode() {
        return execute(OptionalInt.empty());
    }

    public Executor.Result execute() {
        return execute(0);
    }

    public Executor.Result execute(int expectedExitCode) {
        return execute(OptionalInt.of(expectedExitCode));
    }

    private Executor.Result execute(OptionalInt expectedExitCode) {
        verifyMutable();
        executePrerequisiteActions();

        nullableOutputBundle().filter(_ -> {
            return !(TKit.isOSX() && MacHelper.signPredefinedAppImage(this)) && removeOldOutputBundle;
        }).ifPresent(path -> {
            ThrowingRunnable.toRunnable(() -> {
                if (Files.isDirectory(path)) {
                    TKit.deleteDirectoryRecursive(path,
                            String.format("Delete [%s] folder before running jpackage", path));
                } else if (TKit.deleteIfExists(path)) {
                    TKit.trace(String.format("Deleted [%s] file before running jpackage", path));
                }
            }).run();
        });

        Path resourceDir = getArgumentValue("--resource-dir", () -> null, Path::of);
        if (resourceDir != null && Files.isDirectory(resourceDir)) {
            TKit.trace(String.format("Files in [%s] resource dir:",
                    resourceDir));
            try (var files = Files.walk(resourceDir, 1)) {
                files.sequential()
                        .filter(Predicate.not(resourceDir::equals))
                        .map(path -> String.format("[%s]", path.getFileName()))
                        .forEachOrdered(TKit::trace);
                TKit.trace("Done");
            } catch (IOException ex) {
                TKit.trace(String.format(
                        "Failed to list files in [%s] resource directory: %s",
                        resourceDir, ex));
            }
        }

        if (expectedExitCode.isPresent() && expectedExitCode.orElseThrow() == 0
                && !isImagePackageType()) {
            ConfigFilesStasher.INSTANCE.accept(this);
        }

        final var copy = createMutableCopy().adjustArgumentsBeforeExecution();

        final var directoriesAssert = new ReadOnlyPathsAssert(copy);

        Executor.Result result;
        if (expectedExitCode.isEmpty()) {
            result = copy.createExecutor().executeWithoutExitCodeCheck();
        } else {
            result = copy.createExecutor().execute(expectedExitCode.orElseThrow());
        }

        directoriesAssert.updateAndAssert();

        if (expectedExitCode.isPresent() && expectedExitCode.orElseThrow() == 0
                && isImagePackageType()) {
            ConfigFilesStasher.INSTANCE.accept(this);
        }

        for (final var validator: validators) {
            validator.accept(result);
        }

        if (result.getExitCode() == 0 && expectedExitCode.isPresent()) {
            verifyActions.run();
        }

        return result;
    }

    public Executor.Result executeAndAssertHelloAppImageCreated() {
        Executor.Result result = executeAndAssertImageCreated();
        LauncherVerifier.executeMainLauncherAndVerifyOutput(this);
        return result;
    }

    public Executor.Result executeAndAssertImageCreated() {
        Executor.Result result = execute();
        assertImageCreated();
        return result;
    }

    public JPackageCommand assertImageCreated() {
        verifyIsOfType(PackageType.IMAGE);
        runStandardAsserts();
        return this;
    }

    public static enum Macro {
        APPDIR(cmd -> {
            return cmd.appLayout().appDirectory().toString();
        }),
        BINDIR(cmd -> {
            return cmd.appLayout().launchersDirectory().toString();
        }),
        ROOTDIR(cmd -> {
            return (cmd.isImagePackageType() ? cmd.outputBundle() : cmd.appInstallationDirectory()).toString();
        });

        private Macro(Function<JPackageCommand, String> getValue) {
            this.getValue = Objects.requireNonNull(getValue);
        }

        String value(JPackageCommand cmd) {
            return getValue.apply(cmd);
        }

        private final Function<JPackageCommand, String> getValue;
    }

    public String macroValue(Macro macro) {
        return macro.value(this);
    }

    private static final class ReadOnlyPathsAssert {
        ReadOnlyPathsAssert(JPackageCommand cmd) {
            this.asserts = cmd.readOnlyPathAsserts.stream().map(a -> {
                return a.getPaths(cmd).stream().map(dir -> {
                    return Map.entry(a, dir);
                });
            }).flatMap(x -> x).collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));
            snapshots = createSnapshots();
        }

        void updateAndAssert() {
            final var newSnapshots = createSnapshots();
            for (final var a : asserts.keySet().stream().sorted().toList()) {
                final var snapshotGroup = snapshots.get(a);
                final var newSnapshotGroup = newSnapshots.get(a);
                for (int i = 0; i < snapshotGroup.size(); i++) {
                    snapshotGroup.get(i).assertEquals(newSnapshotGroup.get(i),
                            String.format("Check jpackage didn't modify ${%s}=[%s]", a, asserts.get(a).get(i)));
                }
            }
        }

        private Map<ReadOnlyPathAssert, List<TKit.PathSnapshot>> createSnapshots() {
            return asserts.entrySet().stream()
                    .map(e -> {
                        return Map.entry(e.getKey(), e.getValue().stream().map(TKit.PathSnapshot::new).toList());
                    }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private final Map<ReadOnlyPathAssert, List<Path>> asserts;
        private final Map<ReadOnlyPathAssert, List<TKit.PathSnapshot>> snapshots;
    }

    public static enum ReadOnlyPathAssert {
        APP_IMAGE(new Builder("--app-image").enable(cmd -> {
            // External app image should be R/O unless it is an app image signing on macOS.
            return !(TKit.isOSX() && MacHelper.signPredefinedAppImage(cmd));
        }).create()),
        APP_CONTENT(new Builder("--app-content").multiple().create()),
        RESOURCE_DIR(new Builder("--resource-dir").create()),
        MAC_DMG_CONTENT(new Builder("--mac-dmg-content").multiple().create()),
        RUNTIME_IMAGE(new Builder("--runtime-image").create());

        ReadOnlyPathAssert(Function<JPackageCommand, List<Path>> getPaths) {
            this.getPaths = getPaths;
        }

        List<Path> getPaths(JPackageCommand cmd) {
            return getPaths.apply(cmd).stream().toList();
        }

        private static final class Builder {

            Builder(String argName) {
                this.argName = Objects.requireNonNull(argName);
            }

            Builder multiple() {
                multiple = true;
                return this;
            }

            Builder enable(Predicate<JPackageCommand> v) {
                enable = v;
                return this;
            }

            Function<JPackageCommand, List<Path>> create() {
                return cmd -> {
                    if (!cmd.hasArgument(argName) || (enable != null && !enable.test(cmd))) {
                        return List.of();
                    } else {
                        final List<Optional<Path>> dirs;
                        if (multiple) {
                            dirs = Stream.of(cmd.getAllArgumentValues(argName))
                                    .flatMap(Builder::tokenizeValue)
                                    .map(Builder::toExistingFile).toList();
                        } else {
                            dirs = Optional.ofNullable(cmd.getArgumentValue(argName))
                                    .map(Builder::toExistingFile).map(List::of).orElseGet(List::of);
                        }

                        final var mutablePaths = Stream.of("--temp", "--dest")
                                .map(cmd::getArgumentValue)
                                .filter(Objects::nonNull)
                                .map(Builder::toExistingFile)
                                .flatMap(Optional::stream)
                                .collect(toSet());

                        return dirs.stream()
                                .flatMap(Optional::stream)
                                .filter(Predicate.not(mutablePaths::contains))
                                .toList();
                    }
                };
            }

            private static Optional<Path> toExistingFile(String path) {
                Objects.requireNonNull(path);
                try {
                    return Optional.of(Path.of(path)).filter(Files::exists).map(Path::toAbsolutePath);
                } catch (InvalidPathException ex) {
                    return Optional.empty();
                }
            }

            private static Stream<String> tokenizeValue(String str) {
                return Stream.of(str.split(","));
            }

            private Predicate<JPackageCommand> enable;
            private final String argName;
            private boolean multiple;
        }

        private final Function<JPackageCommand, List<Path>> getPaths;
    }

    public JPackageCommand setReadOnlyPathAsserts(ReadOnlyPathAssert... asserts) {
        verifyMutable();
        readOnlyPathAsserts = Set.of(asserts);
        return this;
    }

    public JPackageCommand excludeReadOnlyPathAssert(ReadOnlyPathAssert... asserts) {
        var asSet = Set.of(asserts);
        return setReadOnlyPathAsserts(readOnlyPathAsserts.stream().filter(Predicate.not(
                asSet::contains)).toArray(ReadOnlyPathAssert[]::new));
    }

    public static enum StandardAssert {
        APP_IMAGE_FILE(JPackageCommand::assertAppImageFile),
        PACKAGE_FILE(JPackageCommand::assertPackageFile),
        NO_MAIN_LAUNCHER_IN_RUNTIME(cmd -> {
            if (cmd.isRuntime()) {
                TKit.assertPathExists(convertFromRuntime(cmd).appLauncherPath(), false);
            }
        }),
        NO_MAIN_LAUNCHER_CFG_FILE_IN_RUNTIME(cmd -> {
            if (cmd.isRuntime()) {
                TKit.assertPathExists(convertFromRuntime(cmd).appLauncherCfgPath(null), false);
            }
        }),
        MAIN_LAUNCHER_FILES(cmd -> {
            if (!cmd.isRuntime()) {
                new LauncherVerifier(cmd).verify(cmd,
                        LauncherVerifier.Action.VERIFY_INSTALLED,
                        LauncherVerifier.Action.VERIFY_MAC_ENTITLEMENTS);
            }
        }),
        MAIN_LAUNCHER_DESCRIPTION(cmd -> {
            if (!cmd.isRuntime()) {
                new LauncherVerifier(cmd).verify(cmd, LauncherVerifier.Action.VERIFY_DESCRIPTION);
            }
        }),
        MAIN_JAR_FILE(cmd -> {
            Optional.ofNullable(cmd.getArgumentValue("--main-jar", () -> null)).ifPresent(mainJar -> {
                TKit.assertFileExists(cmd.appLayout().appDirectory().resolve(mainJar));
            });
        }),
        RUNTIME_DIRECTORY(cmd -> {
            TKit.assertDirectoryExists(cmd.appRuntimeDirectory());
            if (TKit.isOSX()) {
                var libjliPath = cmd.appRuntimeDirectory().resolve("Contents/MacOS/libjli.dylib");
                TKit.assertFileExists(libjliPath);
            }
        }),
        MAC_BUNDLE_STRUCTURE(cmd -> {
            if (TKit.isOSX()) {
                MacHelper.verifyBundleStructure(cmd);
            }
        }),
        MAC_BUNDLE_UNSIGNED_SIGNATURE(cmd -> {
            if (TKit.isOSX() && !MacHelper.appImageSigned(cmd)) {
                MacHelper.verifyUnsignedBundleSignature(cmd);
            }
        }),
        MAC_RUNTIME_PLIST_JDK_KEY(cmd -> {
            if (TKit.isOSX()) {
                var appLayout = cmd.appLayout();
                var plistPath = appLayout.runtimeDirectory().resolve("Contents/Info.plist");
                var keyName = "JavaVM";
                var keyValue = MacHelper.readPList(plistPath).findDictValue(keyName);
                if (cmd.isRuntime() || Files.isDirectory(appLayout.runtimeHomeDirectory().resolve("bin"))) {
                    // There are native launchers in the runtime
                    TKit.assertTrue(keyValue.isPresent(), String.format(
                            "Check the runtime plist file [%s] contains '%s' key",
                            plistPath, keyName));
                } else {
                    TKit.assertTrue(keyValue.isEmpty(), String.format(
                            "Check the runtime plist file [%s] contains NO '%s' key",
                            plistPath, keyName));
                }
            }
        }),
        PREDEFINED_APP_IMAGE_COPY(cmd -> {
            Optional.ofNullable(cmd.getArgumentValue("--app-image")).filter(_ -> {
                return !TKit.isOSX() || !MacHelper.signPredefinedAppImage(cmd);
            }).filter(_ -> {
                // Don't examine the contents of the output app image if this is Linux package installing in the "/usr" subtree.
                return Optional.<Boolean>ofNullable(cmd.onLinuxPackageInstallDir(null, _ -> false)).orElse(true);
            }).map(Path::of).ifPresent(predefinedAppImage -> {

                TKit.trace(String.format(
                        "Check contents of the predefined app image [%s] copied verbatim",
                        predefinedAppImage));

                var outputAppImageDir = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());

                try (var walk = Files.walk(predefinedAppImage)) {
                    var filteredWalk = walk;
                    if (!cmd.expectAppImageFile()) {
                        var appImageFile = AppImageFile.getPathInAppImage(predefinedAppImage);
                        // Exclude ".jpackage.xml" as it should no be in the output bundle.
                        var pred = Predicate.<Path>isEqual(appImageFile).negate();
                        if (TKit.isOSX()) {
                            // On MacOS exclude files that can be signed as their digests change.
                            pred = pred.and(path -> {
                                return MacHelper.isVerbatimCopyFromPredefinedAppImage(cmd, path);
                            });
                        }

                        filteredWalk = walk.filter(pred);
                    }

                    var verbatimPaths = filteredWalk.collect(toCollection(TreeSet::new));

                    // Remove nonempty directories from the collection of paths copied verbatim.
                    verbatimPaths.removeAll(verbatimPaths.stream().map(Path::getParent).toList());

                    verbatimPaths.forEach(ThrowingConsumer.toConsumer(p -> {
                        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                            TKit.assertDirectoryExists(p);
                        } else {
                            TKit.assertSameFileContent(p, outputAppImageDir.resolve(predefinedAppImage.relativize(p)));
                        }
                    }));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }

                TKit.trace("Done");
            });
        }),
        LINUX_APPLAUNCHER_LIB(cmd -> {
            if (TKit.isLinux() && !cmd.isRuntime()) {
                TKit.assertFileExists(cmd.appLayout().libapplauncher());
            }
        }),
        ;

        StandardAssert(Consumer<JPackageCommand> action) {
            this.action = action;
        }

        private static JPackageCommand convertFromRuntime(JPackageCommand cmd) {
            var copy = cmd.createMutableCopy();
            copy.removeArgumentWithValue("--runtime-image");
            if (!copy.hasArgument("--name")) {
                copy.addArguments("--name", cmd.nameFromRuntimeImage().orElseThrow());
            }
            return copy;
        }

        private final Consumer<JPackageCommand> action;
    }

    public JPackageCommand setStandardAsserts(StandardAssert ... asserts) {
        verifyMutable();
        standardAsserts = Set.of(asserts);
        return this;
    }

    public JPackageCommand excludeStandardAsserts(StandardAssert... asserts) {
        var asSet = Set.of(asserts);
        return setStandardAsserts(standardAsserts.stream().filter(Predicate.not(
                asSet::contains)).toArray(StandardAssert[]::new));
    }

    JPackageCommand runStandardAsserts() {
        for (var standardAssert : standardAsserts.stream().sorted().toList()) {
            standardAssert.action.accept(this);
        }
        return this;
    }

    private boolean expectAppImageFile() {
        if (isRuntime()) {
            return false;
        }

        if (TKit.isOSX()) {
            if (MacHelper.signPredefinedAppImage(this)) {
                // Request to sign external app image, ".jpackage.xml" file should exist.
                return true;
            }

            if (!isImagePackageType() && hasArgument("--app-image")) {
                // Build native macOS package from an external app image.
                // If the external app image is signed, ".jpackage.xml" file should be kept, otherwise removed.
                return MacHelper.isBundleSigned(Path.of(getArgumentValue("--app-image")));
            }
        }

        return isImagePackageType();
    }

    private void assertAppImageFile() {
        final Path lookupPath = AppImageFile.getPathInAppImage(Path.of(""));

        if (!expectAppImageFile()) {
            assertFileNotInAppImage(lookupPath);
        } else {
            assertFileInAppImage(lookupPath);

            final Path rootDir = isImagePackageType() ? outputBundle() :
                pathToUnpackedPackageFile(appInstallationDirectory());

            final AppImageFile aif = AppImageFile.load(rootDir);

            if (TKit.isOSX()) {
                var expectedValue = hasArgument("--mac-app-store");
                var actualValue = aif.macAppStore();
                TKit.assertEquals(expectedValue, actualValue,
                    "Check for unexpected value of <app-store> property in app image file");
            }

            // Don't compare the add launchers configured on the command line with the
            // add launchers listed in the `.jpackage.xml` file if the latter comes from
            // a predefined app image.
            if (!hasArgument("--app-image")) {
                TKit.assertStringListEquals(
                        addLauncherNames(false).stream().sorted().toList(),
                        aif.addLaunchers().keySet().stream().sorted().toList(),
                        "Check additional launcher names");
            }
        }
    }

    private void assertPackageFile() {
        final Path lookupPath = PackageFile.getPathInAppImage(Path.of(""));

        if (isRuntime() || isImagePackageType() || TKit.isLinux()) {
            assertFileNotInAppImage(lookupPath);
        } else {
            if (TKit.isOSX() && hasArgument("--app-image")) {
                String appImage = getArgumentValue("--app-image");
                if (MacHelper.isBundleSigned(Path.of(appImage))) {
                    assertFileNotInAppImage(lookupPath);
                } else {
                    assertFileInAppImage(lookupPath);
                }
            } else {
                assertFileInAppImage(lookupPath);
            }
        }
    }

    public void assertFileInAppImage(Path expectedPath) {
        assertFileInAppImage(expectedPath.getFileName(), expectedPath);
    }

    public void assertFileNotInAppImage(Path filename) {
        assertFileInAppImage(filename, null);
    }

    private void assertFileInAppImage(Path filename, Path expectedPath) {
        if (expectedPath != null) {
            if (expectedPath.isAbsolute() || !expectedPath.getFileName().equals(filename.getFileName())) {
                throw new IllegalArgumentException();
            }
        }

        if (filename.getNameCount() > 1) {
            assertFileInAppImage(filename.getFileName(), expectedPath);
            return;
        }

        final Path rootDir;
        if (TKit.isOSX() && MacHelper.signPredefinedAppImage(this)) {
            rootDir = Path.of(getArgumentValue("--app-image"));
        } else if (isImagePackageType()) {
            rootDir = outputBundle();
        } else {
            rootDir = pathToUnpackedPackageFile(appInstallationDirectory());
        }

        try ( Stream<Path> walk = ThrowingSupplier.toSupplier(() -> {
            if (TKit.isLinux() && rootDir.equals(Path.of("/"))) {
                // Installed package with split app image on Linux. Iterate
                // through package file list instead of the entire file system.
                return LinuxHelper.getPackageFiles(this);
            } else {
                return Files.walk(rootDir);
            }
        }).get()) {
            List<String> files = walk.filter(path -> filename.equals(
                    path.getFileName())).map(Path::toString).toList();

            if (expectedPath == null) {
                TKit.assertStringListEquals(List.of(), files, String.format(
                        "Check there are no files with [%s] name in the package",
                        filename));
            } else {
                List<String> expected = List.of(
                        rootDir.resolve(expectedPath).toString());
                TKit.assertStringListEquals(expected, files, String.format(
                        "Check there is only one file with [%s] name in the package",
                        filename));
            }
        }
    }

    JPackageCommand setUnpackedPackageLocation(Path path) {
        verifyMutable();
        verifyIsOfType(PackageType.NATIVE);
        unpackedPackageDirectory = path;
        return this;
    }

    JPackageCommand winMsiLogFile(Path v) {
        verifyMutable();
        if (!TKit.isWindows()) {
            throw new UnsupportedOperationException();
        }
        this.winMsiLogFile = v;
        return this;
    }

    public Optional<Path> winMsiLogFile() {
        if (!TKit.isWindows()) {
            throw new UnsupportedOperationException();
        }
        return Optional.ofNullable(winMsiLogFile);
    }

    public Optional<Stream<String>> winMsiLogFileContents() {
        return winMsiLogFile().map(ThrowingFunction.toFunction(msiLog -> {
            // MSI log files are UTF16LE-encoded
            return Files.lines(msiLog, StandardCharsets.UTF_16LE);
        }));
    }

    private JPackageCommand adjustArgumentsBeforeExecution() {
        verifyMutable();
        if (!isWithToolProvider()) {
            // if jpackage is launched as a process then set the jlink.debug system property
            // to allow the jlink process to print exception stacktraces on any failure
            addArgument("-J-Djlink.debug=true");
        }
        if (!hasArgument("--runtime-image") && !hasArgument("--jlink-options") && !hasArgument("--app-image") && DEFAULT_RUNTIME_IMAGE != null && !ignoreDefaultRuntime) {
            addArguments("--runtime-image", DEFAULT_RUNTIME_IMAGE);
        }

        if (!hasArgument("--verbose") && TKit.verboseJPackage() && !ignoreDefaultVerbose) {
            addArgument("--verbose");
        }

        return this;
    }

    public String getPrintableCommandLine() {
        return createExecutor().getPrintableCommandLine();
    }

    @Override
    public String toString() {
        return getPrintableCommandLine();
    }

    public final JPackageCommand verifyIsOfType(PackageType ... types) {
        return verifyIsOfType(Set.of(types));
    }

    public final JPackageCommand verifyIsOfType(Iterable<PackageType> types) {
        return verifyIsOfType(StreamSupport.stream(types.spliterator(), false).collect(toSet()));
    }

    public JPackageCommand verifyIsOfType(Set<PackageType> types) {
        Objects.requireNonNull(types);
        if (!hasArgument("--type")) {
            if (!isImagePackageType()) {
                if ((TKit.isLinux() && types.equals(PackageType.LINUX)) || (TKit.isWindows() && types.equals(PackageType.WINDOWS))) {
                    return this;
                }

                if (TKit.isOSX() && types.equals(PackageType.MAC)) {
                    return this;
                }
            } else if (types.equals(Set.of(PackageType.IMAGE))) {
                return this;
            }
        }

        if (!types.contains(packageType())) {
            throw new UnsupportedOperationException(String.format("Unsupported operation for type [%s]", packageType().getType()));
        }

        return this;
    }

    public CfgFile readLauncherCfgFile() {
        return readLauncherCfgFile(null);
    }

    public CfgFile readLauncherCfgFile(String launcherName) {
        verifyIsOfType(PackageType.IMAGE);
        if (isRuntime()) {
            return null;
        }
        return ThrowingFunction.toFunction(CfgFile::load).apply(
                appLauncherCfgPath(launcherName));
    }

    public List<String> readRuntimeReleaseFile() {
        verifyIsOfType(PackageType.IMAGE);
        Path release = appLayout().runtimeHomeDirectory().resolve("release");
        try {
            return Files.readAllLines(release);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static String escapeAndJoin(String... args) {
        return escapeAndJoin(List.of(args));
    }

    public static String escapeAndJoin(List<String> args) {
        Pattern whitespaceRegexp = Pattern.compile("\\s");

        return args.stream().map(v -> {
            String str = v;
            // Escape backslashes.
            str = str.replace("\\", "\\\\");
            // Escape quotes.
            str = str.replace("\"", "\\\"");
            // If value contains whitespace characters, put the value in quotes
            if (whitespaceRegexp.matcher(str).find()) {
                str = "\"" + str + "\"";
            }
            return str;
        }).collect(Collectors.joining(" "));
    }

    public static String stripTimestamp(String str) {
        final var m = TIMESTAMP_REGEXP.matcher(str);
        if (m.find()) {
            return str.substring(m.end());
        } else {
            return str;
        }
    }

    public static boolean withTimestamp(String str) {
        return TIMESTAMP_REGEXP.matcher(str).find();
    }

    @Override
    protected boolean isMutable() {
        return !immutable;
    }

    private <T> T onLinuxPackageInstallDir(Function<Path, T> anyInstallDirConsumer,
            Function<Path, T> usrInstallDirConsumer) {
        if (TKit.isLinux()) {
            Path installDir = Path.of(getArgumentValue("--install-dir",
                    () -> "/opt"));
            if (Set.of("/usr", "/usr/local").contains(installDir.toString())) {
                if (usrInstallDirConsumer != null) {
                    return usrInstallDirConsumer.apply(installDir);
                }
            } else if (anyInstallDirConsumer != null) {
                return anyInstallDirConsumer.apply(installDir);
            }
        }
        return null;
    }

    private final class Actions implements Runnable {
        Actions() {
            actions = new ArrayList<>();
        }

        Actions(Actions other) {
            this();
            actions.addAll(other.actions);
        }

        void add(ThrowingConsumer<JPackageCommand, ? extends Exception> action) {
            add(action, ActionRole.DEFAULT);
        }

        void add(ThrowingConsumer<JPackageCommand, ? extends Exception> action, ActionRole role) {
            verifyMutable();
            actions.add(new Action(action, role));
        }

        Stream<ThrowingConsumer<JPackageCommand, ? extends Exception>> actionsWithRole(ActionRole role) {
            Objects.requireNonNull(role);
            return actions.stream().filter(action -> {
                return Objects.equals(action.role(), role);
            }).map(Action::impl);
        }

        private static final class Action implements Consumer<JPackageCommand> {

            Action(ThrowingConsumer<JPackageCommand, ? extends Exception> impl, ActionRole role) {
                this.impl = Objects.requireNonNull(impl);
                this.role = Objects.requireNonNull(role);
            }

            ActionRole role() {
                return role;
            }

            ThrowingConsumer<JPackageCommand, ? extends Exception> impl() {
                return impl;
            }

            @Override
            public void accept(JPackageCommand cmd) {
                if (!executed) {
                    executed = true;
                    ThrowingConsumer.toConsumer(impl).accept(cmd);
                }
            }

            private final ActionRole role;
            private final ThrowingConsumer<JPackageCommand, ? extends Exception> impl;
            private boolean executed;
        }

        @Override
        public void run() {
            verifyMutable();
            actions.forEach(action -> action.accept(JPackageCommand.this));
        }

        private final List<Action> actions;
    }

    private static final class ToolProviderSource {

        ToolProviderSource copy() {
            return new ToolProviderSource(this);
        }

        void useDefaultToolProvider() {
            customToolProvider = null;
            mode = Mode.USE_TOOL_PROVIDER;
        }

        void useToolProvider(ToolProvider tp) {
            customToolProvider = Objects.requireNonNull(tp);
            mode = Mode.USE_TOOL_PROVIDER;
        }

        void useProcess() {
            customToolProvider = null;
            mode = Mode.USE_PROCESS;
        }

        Optional<ToolProvider> toolProvider() {
            switch (mode) {
                case USE_PROCESS -> {
                    return Optional.empty();
                }
                case USE_TOOL_PROVIDER -> {
                    if (customToolProvider != null) {
                        return Optional.of(customToolProvider);
                    } else {
                        return TKit.state().findProperty(DefaultToolProviderKey.VALUE).map(ToolProvider.class::cast).or(() -> {
                            return Optional.of(JavaTool.JPACKAGE.asToolProvider());
                        });
                    }
                }
                case INHERIT_DEFAULTS -> {
                    return TKit.state().findProperty(DefaultToolProviderKey.VALUE).map(ToolProvider.class::cast);
                }
                default -> {
                    throw ExceptionBox.reachedUnreachable();
                }
            }
        }

        ToolProviderSource() {
            mode = Mode.INHERIT_DEFAULTS;
        }

        private ToolProviderSource(ToolProviderSource other) {
            this.customToolProvider = other.customToolProvider;
            this.mode = other.mode;
        }

        private enum Mode {
            INHERIT_DEFAULTS,
            USE_PROCESS,
            USE_TOOL_PROVIDER
        }

        private ToolProvider customToolProvider;
        private Mode mode;
    }

    private final ToolProviderSource toolProviderSource;
    private boolean saveConsoleOutput;
    private boolean discardStdout;
    private boolean discardStderr;
    private boolean suppressOutput;
    private boolean ignoreDefaultRuntime;
    private boolean ignoreDefaultVerbose;
    private boolean removeOldOutputBundle;
    private boolean immutable;
    private final Actions prerequisiteActions;
    private final Actions verifyActions;
    private Path executeInDirectory;
    private Path winMsiLogFile;
    private Path unpackedPackageDirectory;
    private Set<ReadOnlyPathAssert> readOnlyPathAsserts = Set.of(ReadOnlyPathAssert.values());
    private Set<StandardAssert> standardAsserts = Set.of(StandardAssert.values());
    private List<Consumer<Executor.Result>> validators = new ArrayList<>();

    private enum DefaultToolProviderKey {
        VALUE
    }

    private static final Map<String, PackageType> PACKAGE_TYPES = Stream.of(PackageType.values()).collect(toMap(PackageType::getType, x -> x));

    // Set the property to the path of run-time image to speed up
    // building app images and platform bundles by avoiding running jlink.
    // The value of the property will be automatically appended to
    // jpackage command line if the command line doesn't have
    // `--runtime-image` parameter set.
    public static final Path DEFAULT_RUNTIME_IMAGE = Optional.ofNullable(TKit.getConfigProperty("runtime-image")).map(Path::of).orElse(null);

    // [HH:mm:ss.SSS]
    private static final Pattern TIMESTAMP_REGEXP = Pattern.compile(
            "^\\[\\d\\d:\\d\\d:\\d\\d.\\d\\d\\d\\] ");
}
