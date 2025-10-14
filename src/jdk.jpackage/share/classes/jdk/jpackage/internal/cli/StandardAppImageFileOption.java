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


    public record AppImageFileOption<T>(OptionIdentifier id, OptionSpec<T> spec) {

        public AppImageFileOption {
            Objects.requireNonNull(id);
            Objects.requireNonNull(spec);
        }

        public String propertyName() {
            return spec.name().name();
        }
    }


    /**
     * The name of the main launcher.
     */
    public static final AppImageFileOption<String> MAIN_LAUNCHER = create(
            StandardOption.NAME, stringOption("main-launcher"));

    /**
     * The name of an additional launcher.
     */
    public static final AppImageFileOption<String> ADD_LAUNCHER = create(
            StandardOption.NAME, stringOption("name"));

    /**
     * The version of the application.
     */
    public static final AppImageFileOption<String> APP_VERSION = create(
            StandardOption.APP_VERSION, stringOption("app-version"));

    /**
     * Fully-qualified name of the main class of the main launcher.
     */
    public static final AppImageFileOption<String> MAIN_CLASS = create(
            StandardOption.APPCLASS, stringOption("main-class"));

    /**
     * Is a launcher should be installed as a service.
     */
    public static final AppImageFileOption<Boolean> LAUNCHER_AS_SERVICE = create(
            StandardOption.LAUNCHER_AS_SERVICE, booleanOption("service"));


    //
    // Linux-specific
    //

    /**
     * Configuration of the shortcut for a launcher. Linux-only.
     */
    public static final AppImageFileOption<LauncherShortcut> LINUX_LAUNCHER_SHORTCUT = create(
            StandardOption.LINUX_SHORTCUT_HINT, launcherShortcutOption("linux-shortcut"));


    //
    // Windows-specific
    //

    /**
     * Configuration of the desktop shortcut for a launcher. Windows-only.
     */
    public static final AppImageFileOption<LauncherShortcut> WIN_LAUNCHER_DESKTOP_SHORTCUT = create(
            StandardOption.WIN_SHORTCUT_HINT, launcherShortcutOption("win-shortcut"));

    /**
     * Configuration of the start menu shortcut for a launcher. Windows-only.
     */
    public static final AppImageFileOption<LauncherShortcut> WIN_LAUNCHER_MENU_SHORTCUT = create(
            StandardOption.WIN_MENU_HINT, launcherShortcutOption("win-menu"));


    //
    // macOS-specific
    //

    /**
     * Is an application is for the App Store. macOS-only.
     */
    public static final AppImageFileOption<Boolean> MAC_APP_STORE = create(
            StandardOption.MAC_APP_STORE,
            booleanOption("app-store")
                    .outOfScope(StandardBundlingOperation.values())
                    .inScope(StandardBundlingOperation.ofPlatform(OperatingSystem.MACOS).toList()));

    /**
     * Is an application image is signed. macOS-only.
     */
    public static final AppImageFileOption<Boolean> MAC_SIGNED = create(
            StandardOption.MAC_SIGN,
            booleanOption("signed")
                    .outOfScope(StandardBundlingOperation.values())
                    .inScope(StandardBundlingOperation.ofPlatform(OperatingSystem.MACOS).toList()));

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

    private static Options parseProperties(Path appImageFile, Map<String, String> properties, OperatingSystem os, AppImageFileOption<?>... options) {
        var scope = StandardBundlingOperation.ofPlatform(os).collect(toSet());

        var activeOptions = Stream.of(options).filter(option -> {
            return !Collections.disjoint(option.spec().scope(), scope);
        }).collect(toMap(option -> {
            return option.propertyName();
        }, x -> x));

        var context = new StandardOptionContext(os).forFile(appImageFile);

        return Options.of(properties.entrySet().stream().map(e -> {
            return Optional.ofNullable(activeOptions.get(e.getKey())).map(option -> {
                var optionSpec = context.mapOptionSpec(option.spec());
                return Map.entry(option.id(), optionSpec.converter().orElseThrow().convert(optionSpec.name(), StringToken.of(e.getValue())).orElseThrow());
            });
        }).filter(Optional::isPresent).map(Optional::get).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static <T> AppImageFileOption<T> create(OptionValue<T> optionValue, OptionSpecBuilder<T> specBuilder) {
        return new AppImageFileOption<>(optionValue.id(), specBuilder.createOptionSpec());
    }
}

