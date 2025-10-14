/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal.cli;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.fromOptionName;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.LauncherShortcut;

/**
 * jpackage options in the app image (".jpackage.xml") file
 */
public final class StandardAppImageFileOption {

    private StandardAppImageFileOption() {
    }


    /**
     * The name of the main launcher.
     */
    public static final OptionValue<String> MAIN_LAUNCHER = stringOption("main-launcher")
            .toOptionValueBuilder().id(StandardOption.NAME.id()).create();

    /**
     * The name of an additional launcher.
     */
    public static final OptionValue<String> ADD_LAUNCHER = stringOption("name")
            .toOptionValueBuilder().id(StandardOption.NAME.id()).create();

    /**
     * The version of the application.
     */
    public static final OptionValue<String> APP_VERSION = stringOption("app-version")
            .toOptionValueBuilder().id(StandardOption.APP_VERSION.id()).create();

    /**
     * Fully-qualified name of the main class of the main launcher.
     */
    public static final OptionValue<String> MAIN_CLASS = stringOption("main-class")
            .toOptionValueBuilder().id(StandardOption.APPCLASS.id()).create();

    /**
     * Is a launcher should be installed as a service.
     */
    public static final OptionValue<Boolean> LAUNCHER_AS_SERVICE = booleanOption("service")
            .toOptionValueBuilder().id(StandardOption.LAUNCHER_AS_SERVICE.id()).create();


    //
    // Linux-specific
    //

    /**
     * Configuration of the shortcut for a launcher. Linux-only.
     */
    public static final OptionValue<LauncherShortcut> LINUX_LAUNCHER_SHORTCUT = launcherShortcutOption("linux-shortcut")
            .toOptionValueBuilder().id(StandardOption.LINUX_SHORTCUT_HINT.id()).create();


    //
    // Windows-specific
    //

    /**
     * Configuration of the desktop shortcut for a launcher. Windows-only.
     */
    public static final OptionValue<LauncherShortcut> WIN_LAUNCHER_DESKTOP_SHORTCUT = launcherShortcutOption("win-shortcut")
            .toOptionValueBuilder().id(StandardOption.WIN_SHORTCUT_HINT.id()).create();

    /**
     * Configuration of the start menu shortcut for a launcher. Windows-only.
     */
    public static final OptionValue<LauncherShortcut> WIN_LAUNCHER_MENU_SHORTCUT = launcherShortcutOption("win-menu")
            .toOptionValueBuilder().id(StandardOption.WIN_MENU_HINT.id()).create();


    //
    // macOS-specific
    //

    /**
     * Is an application is for the App Store. macOS-only.
     */
    public static final OptionValue<Boolean> MAC_APP_STORE = booleanOption("app-store")
            .outOfScope(StandardBundlingOperation.values())
            .inScope(StandardBundlingOperation.ofPlatform(OperatingSystem.MACOS).toList())
            .toOptionValueBuilder().id(StandardOption.MAC_APP_STORE.id()).create();

    /**
     * Is an application image is signed. macOS-only.
     */
    public static final OptionValue<Boolean> MAC_SIGNED =
            booleanOption("signed")
            .outOfScope(StandardBundlingOperation.values())
            .inScope(StandardBundlingOperation.ofPlatform(OperatingSystem.MACOS).toList())
            .toOptionValueBuilder().id(StandardOption.MAC_SIGN.id()).create();

    public static Options parseAppProperties(Path appImageFile, Map<String, String> properties, OperatingSystem os) {
        return parseProperties(appImageFile, properties, os, MAIN_LAUNCHER, APP_VERSION, MAIN_CLASS, MAC_SIGNED, MAC_APP_STORE);
    }

    public static Options parseAddLauncherProperties(Path appImageFile, Map<String, String> properties, OperatingSystem os) {
        return parseProperties(appImageFile, properties, os, ADD_LAUNCHER, LAUNCHER_AS_SERVICE, LINUX_LAUNCHER_SHORTCUT, WIN_LAUNCHER_DESKTOP_SHORTCUT, WIN_LAUNCHER_MENU_SHORTCUT);
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .description("")
                .scope(fromOptionName(name))
                .valuePattern("");
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).mutate(StandardOption.stringOptionMutator());
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).mutate(StandardOption.booleanOptionMutator());
    }

    private static OptionSpecBuilder<LauncherShortcut> launcherShortcutOption(String name) {
        return option(name, LauncherShortcut.class).mutate(StandardOption.launcherShortcutOptionMutator());
    }

    private static Options parseProperties(Path appImageFile, Map<String, String> properties, OperatingSystem os, OptionValue<?>... options) {
        var scope = StandardBundlingOperation.ofPlatform(os).collect(toSet());

        var context = new StandardOptionContext(os).forFile(appImageFile);

        return Options.of(Stream.of(options).filter(option -> {
            return !Collections.disjoint(option.getSpec().scope(), scope);
        }).map(option -> {
            var spec = context.mapOptionSpec(option.getSpec());
            var strValue = Optional.ofNullable(properties.get(spec.name().name()));
            return strValue.map(v -> {
                return spec.converter().orElseThrow().convert(spec.name(), StringToken.of(v)).orElseThrow();
            }).map(v -> {
                return Map.entry(option, v);
            });
        }).filter(Optional::isPresent).map(Optional::get).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
