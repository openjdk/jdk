/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.LauncherShortcut;

/**
 * jpackage options in the app image (".jpackage.xml") file
 */
public final class StandardAppImageFileOption {

    private StandardAppImageFileOption() {
    }


    public enum AppImageFileOptionScope implements OptionScope {
        APP,
        LAUNCHER,
        ;

        public Stream<? extends OptionValue<?>> options(OperatingSystem os) {
            var scope = StandardBundlingOperation.ofPlatform(os).collect(toSet());
            return options().filter(ov -> {
                return !Collections.disjoint(ov.getSpec().scope(), scope);
            });
        }

        public Stream<? extends OptionValue<?>> options() {
            return Utils.getOptionsWithSpecs(StandardAppImageFileOption.class).filter(ov -> {
                return ov.getSpec().scope().contains(AppImageFileOptionScope.this);
            });
        }

        public Options parse(Path appImageFile, Map<String, String> properties, OperatingSystem os) throws InvalidOptionValueException {
            return parseProperties(appImageFile, properties, os, options().map(OptionValue::getOption));
        }
    }


    private enum MandatoryOption implements OptionScope {
        VALUE;
    }


    /**
     * The name of a launcher.
     */
    public static final OptionValue<String> LAUNCHER_NAME = stringOption("name")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .inScope(MandatoryOption.VALUE)
            .toOptionValueBuilder().id(StandardOption.NAME.id()).create();

    /**
     * The version of the application.
     */
    public static final OptionValue<String> APP_VERSION = stringOption("app-version")
            .inScope(AppImageFileOptionScope.APP)
            .inScope(MandatoryOption.VALUE)
            .toOptionValueBuilder().id(StandardOption.APP_VERSION.id()).create();

    /**
     * Should install a launcher as a service?
     */
    public static final OptionValue<Boolean> LAUNCHER_AS_SERVICE = booleanOption("service")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .toOptionValueBuilder().id(StandardOption.LAUNCHER_AS_SERVICE.id()).create();

    /**
     * The description of a launcher.
     */
    public static final OptionValue<String> DESCRIPTION = stringOption("description")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .inScope(MandatoryOption.VALUE)
            .toOptionValueBuilder().id(StandardOption.DESCRIPTION.id()).create();


    //
    // Linux-specific
    //

    /**
     * Configuration of the shortcut for a launcher. Linux-only.
     */
    public static final OptionValue<LauncherShortcut> LINUX_LAUNCHER_SHORTCUT = launcherShortcutOption("linux-shortcut")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .toOptionValueBuilder().id(StandardOption.LINUX_SHORTCUT_HINT.id()).create();


    //
    // Windows-specific
    //

    /**
     * Configuration of the desktop shortcut for a launcher. Windows-only.
     */
    public static final OptionValue<LauncherShortcut> WIN_LAUNCHER_DESKTOP_SHORTCUT = launcherShortcutOption("win-shortcut")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .toOptionValueBuilder().id(StandardOption.WIN_SHORTCUT_HINT.id()).create();

    /**
     * Configuration of the start menu shortcut for a launcher. Windows-only.
     */
    public static final OptionValue<LauncherShortcut> WIN_LAUNCHER_MENU_SHORTCUT = launcherShortcutOption("win-menu")
            .inScope(AppImageFileOptionScope.LAUNCHER)
            .toOptionValueBuilder().id(StandardOption.WIN_MENU_HINT.id()).create();


    //
    // macOS-specific
    //

    /**
     * Fully-qualified name of the main class of the main launcher.
     */
    public static final OptionValue<String> MAC_MAIN_CLASS = stringOption("main-class")
            .inScope(AppImageFileOptionScope.APP)
            .inScope(MandatoryOption.VALUE)
            .mutate(setPlatformScope(OperatingSystem.MACOS))
            .validator(StandardValidator.IS_CLASSNAME)
            // In case of validation failure, report it with
            // an exception of type InvalidOptionValueException with empty message.
            .validatorExceptionFactory(
                    OptionValueExceptionFactory.build(InvalidOptionValueException::new)
                            .messageFormatter((_, _) -> "") // just a stub
                            .create()
            )
            .validatorExceptionFormatString("") // just a stub, not used
            .toOptionValueBuilder().id(StandardOption.APPCLASS.id()).create();

    /**
     * Is an application is for the App Store. macOS-only.
     */
    public static final OptionValue<Boolean> MAC_APP_STORE = booleanOption("app-store")
            .inScope(AppImageFileOptionScope.APP)
            .mutate(setPlatformScope(OperatingSystem.MACOS))
            .toOptionValueBuilder().id(StandardOption.MAC_APP_STORE.id()).create();

    public static final class InvalidOptionValueException extends RuntimeException {

        InvalidOptionValueException(String str, Throwable t) {
            super(str, t);
        }

        private static final long serialVersionUID = 1L;
    }


    public static final class MissingMandatoryOptionException extends RuntimeException {

        MissingMandatoryOptionException(String str) {
            super(str);
        }

        private static final long serialVersionUID = 1L;
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

    private static Options parseProperties(
            Path appImageFile,
            Map<String, String> properties,
            OperatingSystem os,
            Stream<Option> options) throws InvalidOptionValueException, MissingMandatoryOptionException {

        var scope = StandardBundlingOperation.ofPlatform(os).collect(toSet());

        var context = new StandardOptionContext(os).forFile(appImageFile);

        return Options.of(options.filter(option -> {
            return !Collections.disjoint(option.spec().scope(), scope);
        }).map(option -> {
            var spec = context.mapOptionSpec(option.spec());
            var strValue = Optional.ofNullable(properties.get(spec.name().name()));

            if (strValue.isEmpty() && spec.scope().contains(MandatoryOption.VALUE)) {
                throw new MissingMandatoryOptionException(String.format("Missing mandatory '%s' property", spec.name().name()));
            }

            return strValue.map(v -> {
                return spec.converter().orElseThrow().convert(spec.name(), StringToken.of(v)).orElseThrow();
            }).map(v -> {
                return Map.entry(option, v);
            });
        }).filter(Optional::isPresent).map(Optional::get).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static <T> Consumer<OptionSpecBuilder<T>> setPlatformScope(OperatingSystem os) {
        return builder -> {
            builder.outOfScope(StandardBundlingOperation.values()).inScope(StandardBundlingOperation.ofPlatform(os).toList());
        };
    }
}
