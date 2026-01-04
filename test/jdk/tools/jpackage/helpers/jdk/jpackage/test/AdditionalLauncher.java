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

import static jdk.jpackage.test.LauncherShortcut.LINUX_SHORTCUT;
import static jdk.jpackage.test.LauncherShortcut.WIN_DESKTOP_SHORTCUT;
import static jdk.jpackage.test.LauncherShortcut.WIN_START_MENU_SHORTCUT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.LauncherShortcut.StartupDirectory;
import jdk.jpackage.test.LauncherVerifier.Action;

public final class AdditionalLauncher {

    public AdditionalLauncher(String name) {
        this.name = Objects.requireNonNull(name);
        setPersistenceHandler(null);
    }

    public String name() {
        return name;
    }

    public AdditionalLauncher withVerifyActions(Action... actions) {
        verifyActions.addAll(List.of(actions));
        return this;
    }

    public AdditionalLauncher withoutVerifyActions(Action... actions) {
        verifyActions.removeAll(List.of(actions));
        return this;
    }

    public AdditionalLauncher setDefaultArguments(String... v) {
        defaultArguments = new ArrayList<>(List.of(v));
        return this;
    }

    public AdditionalLauncher addDefaultArguments(String... v) {
        if (defaultArguments == null) {
            return setDefaultArguments(v);
        }

        defaultArguments.addAll(List.of(v));
        return this;
    }

    public AdditionalLauncher setJavaOptions(String... v) {
        javaOptions = new ArrayList<>(List.of(v));
        return this;
    }

    public AdditionalLauncher addJavaOptions(String... v) {
        if (javaOptions == null) {
            return setJavaOptions(v);
        }

        javaOptions.addAll(List.of(v));
        return this;
    }

    public AdditionalLauncher setProperty(String name, Object value) {
        rawProperties.put(Objects.requireNonNull(name), Objects.requireNonNull(value.toString()));
        return this;
    }

    public AdditionalLauncher removeProperty(String name) {
        rawProperties.remove(Objects.requireNonNull(name));
        return this;
    }

    public AdditionalLauncher setShortcuts(boolean menu, boolean desktop) {
        if (TKit.isLinux()) {
            setShortcut(LINUX_SHORTCUT, desktop);
        } else if (TKit.isWindows()) {
            setShortcut(WIN_DESKTOP_SHORTCUT, desktop);
            setShortcut(WIN_START_MENU_SHORTCUT, menu);
        }
        return this;
    }

    public AdditionalLauncher setShortcut(LauncherShortcut shortcut, StartupDirectory value) {
        if (value != null) {
            setProperty(shortcut.propertyName(), value.asStringValue());
        } else {
            setProperty(shortcut.propertyName(), false);
        }
        return this;
    }

    public AdditionalLauncher setShortcut(LauncherShortcut shortcut, boolean value) {
        if (value) {
            setShortcut(shortcut, StartupDirectory.DEFAULT);
        } else {
            setShortcut(shortcut, null);
        }
        return this;
    }

    public AdditionalLauncher removeShortcut(LauncherShortcut shortcut) {
        rawProperties.remove(shortcut.propertyName());
        return this;
    }

    public AdditionalLauncher setIcon(Path iconPath) {
        if (iconPath.equals(NO_ICON)) {
            throw new IllegalArgumentException();
        }

        icon = iconPath;
        return this;
    }

    public AdditionalLauncher setNoIcon() {
        icon = NO_ICON;
        return this;
    }

    public AdditionalLauncher setPersistenceHandler(
            ThrowingBiConsumer<Path, Collection<Map.Entry<String, String>>, ? extends Exception> handler) {
        if (handler != null) {
            createFileHandler = ThrowingBiConsumer.toBiConsumer(handler);
        } else {
            createFileHandler = TKit::createPropertiesFile;
        }
        return this;
    }

    public void applyTo(JPackageCommand cmd) {
        cmd.addPrerequisiteAction(this::initialize);
        cmd.addVerifyAction(createVerifierAsConsumer(), JPackageCommand.ActionRole.LAUNCHER_VERIFIER);
    }

    public void applyTo(PackageTest test) {
        test.addInitializer(this::initialize);
        test.addInstallVerifier(createVerifierAsConsumer());
    }

    public final void verifyRemovedInUpgrade(PackageTest test) {
        test.addInstallVerifier(cmd -> {
            createVerifier().verify(cmd, LauncherVerifier.Action.VERIFY_UNINSTALLED);
        });
    }

    private LauncherVerifier createVerifier() {
        return new LauncherVerifier(name, Optional.ofNullable(javaOptions),
                Optional.ofNullable(defaultArguments), Optional.ofNullable(icon), rawProperties);
    }

    private ThrowingConsumer<JPackageCommand, ? extends Exception> createVerifierAsConsumer() {
        return cmd -> {
            createVerifier().verify(cmd, verifyActions.stream().sorted(Comparator.comparing(Action::ordinal)).toArray(Action[]::new));
        };
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

    public static PropertyFile getAdditionalLauncherProperties(
            JPackageCommand cmd, String launcherName) {
        var result = Slot.<PropertyFile>createEmpty();
        forEachAdditionalLauncher(cmd, (name, propertiesFilePath) -> {
            if (name.equals(launcherName)) {
                result.set(new PropertyFile(propertiesFilePath));
            }
        });
        return result.get();
    }

    private void initialize(JPackageCommand cmd) throws IOException {
        final Path propsFile = TKit.createTempFile(name + ".properties");

        cmd.addArguments("--add-launcher", String.format("%s=%s", name, propsFile));

        Map<String, String> properties = new HashMap<>();
        if (defaultArguments != null) {
            properties.put("arguments", JPackageCommand.escapeAndJoin(defaultArguments));
        }

        if (javaOptions != null) {
            properties.put("java-options", JPackageCommand.escapeAndJoin(javaOptions));
        }

        if (icon != null) {
            final String iconPath;
            if (icon.equals(NO_ICON)) {
                iconPath = "";
            } else {
                iconPath = icon.toAbsolutePath().toString().replace('\\', '/');
            }
            properties.put("icon", iconPath);
        }

        properties.putAll(rawProperties);

        createFileHandler.accept(propsFile, properties.entrySet());
    }

    private List<String> javaOptions;
    private List<String> defaultArguments;
    private Path icon;
    private final String name;
    private final Map<String, String> rawProperties = new HashMap<>();
    private BiConsumer<Path, Collection<Map.Entry<String, String>>> createFileHandler;
    private final Set<Action> verifyActions = new HashSet<>(Action.VERIFY_DEFAULTS);

    static final Path NO_ICON = Path.of("");
}
