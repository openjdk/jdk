/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Functional.ThrowingBiConsumer;
import static jdk.jpackage.test.Functional.ThrowingFunction.toFunction;

public class AdditionalLauncher {

    public AdditionalLauncher(String name) {
        this.name = name;
        this.rawProperties = new ArrayList<>();
        setPersistenceHandler(null);
    }

    final public AdditionalLauncher setDefaultArguments(String... v) {
        defaultArguments = new ArrayList<>(List.of(v));
        return this;
    }

    final public AdditionalLauncher addDefaultArguments(String... v) {
        if (defaultArguments == null) {
            return setDefaultArguments(v);
        }

        defaultArguments.addAll(List.of(v));
        return this;
    }

    final public AdditionalLauncher setJavaOptions(String... v) {
        javaOptions = new ArrayList<>(List.of(v));
        return this;
    }

    final public AdditionalLauncher addJavaOptions(String... v) {
        if (javaOptions == null) {
            return setJavaOptions(v);
        }

        javaOptions.addAll(List.of(v));
        return this;
    }

    final public AdditionalLauncher addRawProperties(
            Map.Entry<String, String>... v) {
        return addRawProperties(List.of(v));
    }

    final public AdditionalLauncher addRawProperties(
            Collection<Map.Entry<String, String>> v) {
        rawProperties.addAll(v);
        return this;
    }

    final public AdditionalLauncher setShortcuts(boolean menu, boolean shortcut) {
        withMenuShortcut = menu;
        withShortcut = shortcut;
        return this;
    }

    final public AdditionalLauncher setIcon(Path iconPath) {
        if (iconPath == NO_ICON) {
            throw new IllegalArgumentException();
        }

        icon = iconPath;
        return this;
    }

    final public AdditionalLauncher setNoIcon() {
        icon = NO_ICON;
        return this;
    }

    final public AdditionalLauncher setPersistenceHandler(
            ThrowingBiConsumer<Path, List<Map.Entry<String, String>>> handler) {
        if (handler != null) {
            createFileHandler = ThrowingBiConsumer.toBiConsumer(handler);
        } else {
            createFileHandler = TKit::createPropertiesFile;
        }
        return this;
    }

    final public void applyTo(JPackageCommand cmd) {
        cmd.addPrerequisiteAction(this::initialize);
        cmd.addVerifyAction(this::verify);
    }

    final public void applyTo(PackageTest test) {
        test.addInitializer(this::initialize);
        test.addInstallVerifier(this::verify);
    }

    static void forEachAdditionalLauncher(JPackageCommand cmd,
            BiConsumer<String, Path> consumer) {
        var argIt = cmd.getAllArguments().iterator();
        while (argIt.hasNext()) {
            if ("--add-launcher".equals(argIt.next())) {
                // <launcherName>=<propFile>
                var arg = argIt.next();
                var items = arg.split("=", 2);
                consumer.accept(items[0], Path.of(items[1]));
            }
        }
    }

    static PropertyFile getAdditionalLauncherProperties(
            JPackageCommand cmd, String launcherName) {
        PropertyFile shell[] = new PropertyFile[1];
        forEachAdditionalLauncher(cmd, (name, propertiesFilePath) -> {
            if (name.equals(launcherName)) {
                shell[0] = toFunction(PropertyFile::new).apply(
                        propertiesFilePath);
            }
        });
        return Optional.of(shell[0]).get();
    }

    private void initialize(JPackageCommand cmd) {
        Path propsFile = TKit.workDir().resolve(name + ".properties");
        if (Files.exists(propsFile)) {
            // File with the given name exists, pick another name that
            // will not reference existing file.
            try {
                propsFile = TKit.createTempFile(propsFile);
                TKit.deleteIfExists(propsFile);
            } catch (IOException ex) {
                Functional.rethrowUnchecked(ex);
            }
        }

        cmd.addArguments("--add-launcher", String.format("%s=%s", name,
                    propsFile));

        List<Map.Entry<String, String>> properties = new ArrayList<>();
        if (defaultArguments != null) {
            properties.add(Map.entry("arguments",
                    JPackageCommand.escapeAndJoin(defaultArguments)));
        }

        if (javaOptions != null) {
            properties.add(Map.entry("java-options",
                    JPackageCommand.escapeAndJoin(javaOptions)));
        }

        if (icon != null) {
            final String iconPath;
            if (icon == NO_ICON) {
                iconPath = "";
            } else {
                iconPath = icon.toAbsolutePath().toString().replace('\\', '/');
            }
            properties.add(Map.entry("icon", iconPath));
        }

        if (withShortcut != null) {
            if (TKit.isLinux()) {
                properties.add(Map.entry("linux-shortcut", withShortcut.toString()));
            } else if (TKit.isWindows()) {
                properties.add(Map.entry("win-shortcut", withShortcut.toString()));
            }
        }

        if (TKit.isWindows() && withMenuShortcut != null)  {
            properties.add(Map.entry("win-menu", withMenuShortcut.toString()));
        }

        properties.addAll(rawProperties);

        createFileHandler.accept(propsFile, properties);
    }

    private static Path iconInResourceDir(JPackageCommand cmd,
            String launcherName) {
        Path resourceDir = cmd.getArgumentValue("--resource-dir", () -> null,
                Path::of);
        if (resourceDir != null) {
            Path icon = resourceDir.resolve(
                    Optional.ofNullable(launcherName).orElseGet(() -> cmd.name())
                    + TKit.ICON_SUFFIX);
            if (Files.exists(icon)) {
                return icon;
            }
        }
        return null;
    }

    private void verifyIcon(JPackageCommand cmd) throws IOException {
        var verifier = new LauncherIconVerifier().setLauncherName(name);

        if (TKit.isOSX()) {
            // On Mac should be no icon files for additional launchers.
            verifier.applyTo(cmd);
            return;
        }

        boolean withLinuxDesktopFile = false;

        final Path effectiveIcon = Optional.ofNullable(icon).orElseGet(
                () -> iconInResourceDir(cmd, name));
        while (effectiveIcon != NO_ICON) {
            if (effectiveIcon != null) {
                withLinuxDesktopFile = Boolean.FALSE != withShortcut;
                verifier.setExpectedIcon(effectiveIcon);
                break;
            }

            Path customMainLauncherIcon = cmd.getArgumentValue("--icon",
                    () -> iconInResourceDir(cmd, null), Path::of);
            if (customMainLauncherIcon != null) {
                withLinuxDesktopFile = Boolean.FALSE != withShortcut;
                verifier.setExpectedIcon(customMainLauncherIcon);
                break;
            }

            verifier.setExpectedDefaultIcon();
            break;
        }

        if (TKit.isLinux() && !cmd.isImagePackageType()) {
            if (effectiveIcon != NO_ICON && !withLinuxDesktopFile) {
                withLinuxDesktopFile = (Boolean.FALSE != withShortcut) &&
                        Stream.of("--linux-shortcut").anyMatch(cmd::hasArgument);
                verifier.setExpectedDefaultIcon();
            }
            Path desktopFile = LinuxHelper.getDesktopFile(cmd, name);
            if (withLinuxDesktopFile) {
                TKit.assertFileExists(desktopFile);
            } else {
                TKit.assertPathExists(desktopFile, false);
            }
        }

        verifier.applyTo(cmd);
    }

    private void verifyShortcuts(JPackageCommand cmd) throws IOException {
        if (TKit.isLinux() && !cmd.isImagePackageType()
                && withShortcut != null) {
            Path desktopFile = LinuxHelper.getDesktopFile(cmd, name);
            if (withShortcut) {
                TKit.assertFileExists(desktopFile);
            } else {
                TKit.assertPathExists(desktopFile, false);
            }
        }
    }

    protected void verify(JPackageCommand cmd) throws IOException {
        verifyIcon(cmd);
        verifyShortcuts(cmd);

        Path launcherPath = cmd.appLauncherPath(name);

        TKit.assertExecutableFileExists(launcherPath);

        if (!cmd.canRunLauncher(String.format(
                "Not running %s launcher", launcherPath))) {
            return;
        }

        var appVerifier = HelloApp.assertApp(launcherPath)
                .addDefaultArguments(Optional
                        .ofNullable(defaultArguments)
                        .orElseGet(() -> List.of(cmd.getAllArgumentValues("--arguments"))))
                .addJavaOptions(Optional
                        .ofNullable(javaOptions)
                        .orElseGet(() -> List.of(cmd.getAllArgumentValues(
                        "--java-options"))).stream().map(
                        str -> resolveVariables(cmd, str)).toList());

        appVerifier.executeAndVerifyOutput();
    }

    public static final class PropertyFile {

        PropertyFile(Path path) throws IOException {
            data = Files.readAllLines(path).stream().map(str -> {
                return str.split("=", 2);
            }).collect(
                    Collectors.toMap(tokens -> tokens[0], tokens -> tokens[1],
                            (oldValue, newValue) -> {
                                return newValue;
                            }));
        }

        public boolean isPropertySet(String name) {
            Objects.requireNonNull(name);
            return data.containsKey(name);
        }

        public Optional<String> getPropertyValue(String name) {
            Objects.requireNonNull(name);
            return Optional.of(data.get(name));
        }

        public Optional<Boolean> getPropertyBooleanValue(String name) {
            Objects.requireNonNull(name);
            return Optional.ofNullable(data.get(name)).map(Boolean::parseBoolean);
        }

        private final Map<String, String> data;
    }

    private static String resolveVariables(JPackageCommand cmd, String str) {
        var map = Map.of(
                "$APPDIR", cmd.appLayout().appDirectory(),
                "$ROOTDIR",
                cmd.isImagePackageType() ? cmd.outputBundle() : cmd.appInstallationDirectory(),
                "$BINDIR", cmd.appLayout().launchersDirectory());
        for (var e : map.entrySet()) {
            str = str.replaceAll(Pattern.quote(e.getKey()),
                    Matcher.quoteReplacement(e.getValue().toString()));
        }
        return str;
    }

    private List<String> javaOptions;
    private List<String> defaultArguments;
    private Path icon;
    private final String name;
    private final List<Map.Entry<String, String>> rawProperties;
    private BiConsumer<Path, List<Map.Entry<String, String>>> createFileHandler;
    private Boolean withMenuShortcut;
    private Boolean withShortcut;

    private final static Path NO_ICON = Path.of("");
}
