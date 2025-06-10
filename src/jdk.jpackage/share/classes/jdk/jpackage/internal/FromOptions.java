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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.JLinkRuntimeBuilder.ensureBaseModuleInModulePath;
import static jdk.jpackage.internal.OptionUtils.isRuntimeInstaller;
import static jdk.jpackage.internal.cli.StandardOption.ABOUT_URL;
import static jdk.jpackage.internal.cli.StandardOption.ADDITIONAL_LAUNCHERS;
import static jdk.jpackage.internal.cli.StandardOption.ADD_MODULES;
import static jdk.jpackage.internal.cli.StandardOption.APP_CONTENT;
import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.COPYRIGHT;
import static jdk.jpackage.internal.cli.StandardOption.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardOption.ICON;
import static jdk.jpackage.internal.cli.StandardOption.INPUT;
import static jdk.jpackage.internal.cli.StandardOption.INSTALL_DIR;
import static jdk.jpackage.internal.cli.StandardOption.JLINK_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOption.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardOption.LICENSE_FILE;
import static jdk.jpackage.internal.cli.StandardOption.MODULE_PATH;
import static jdk.jpackage.internal.cli.StandardOption.NAME;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.VENDOR;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import jdk.jpackage.internal.cli.OptionIdentifier;
import jdk.jpackage.internal.cli.OptionValue;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;

final class FromOptions {

    static ApplicationBuilderBuilder buildApplicationBuilder() {
        return new ApplicationBuilderBuilder();
    }

    static PackageBuilder createPackageBuilder(Options optionValues, Application app, PackageType type) {

        final var builder = new PackageBuilder(app, type);

        NAME.ifPresentIn(optionValues, builder::name);
        DESCRIPTION.ifPresentIn(optionValues, builder::description);
        APP_VERSION.ifPresentIn(optionValues, builder::version);
        ABOUT_URL.ifPresentIn(optionValues, builder::aboutURL);
        LICENSE_FILE.ifPresentIn(optionValues, builder::licenseFile);
        PREDEFINED_APP_IMAGE.ifPresentIn(optionValues, builder::predefinedAppImage);
        PREDEFINED_RUNTIME_IMAGE.ifPresentIn(optionValues, builder::predefinedAppImage);
        INSTALL_DIR.ifPresentIn(optionValues, builder::installDir);

        return builder;
    }

    static Optional<LauncherShortcut> findLauncherShortcut(
            OptionValue<LauncherShortcut> shortcutOption,
            Options mainOptions,
            Options launcherOptions) {

        Objects.requireNonNull(shortcutOption);
        Objects.requireNonNull(mainOptions);
        Objects.requireNonNull(launcherOptions);

        Optional<LauncherShortcut> launcherValue;
        if (mainOptions == launcherOptions) {
            // The main launcher
            launcherValue = Optional.empty();
        } else {
            launcherValue = shortcutOption.findIn(launcherOptions);
        }

        return launcherValue.or(() -> {
            return shortcutOption.findIn(mainOptions);
        });
    }


    static final class ApplicationBuilderBuilder {

        private ApplicationBuilderBuilder() {
        }

        ApplicationBuilder create(Options optionValues,
                Function<Options, Launcher> launcherMapper, ApplicationLayout appLayout) {

            final Optional<RuntimeLayout> thePredefinedRuntimeLayout;
            if (PREDEFINED_RUNTIME_IMAGE.containsIn(optionValues)) {
                thePredefinedRuntimeLayout = Optional.ofNullable(
                        predefinedRuntimeLayout).or(() -> Optional.of(RuntimeLayout.DEFAULT));
            } else {
                thePredefinedRuntimeLayout = Optional.empty();
            }

            return createApplicationBuilder(
                    optionValues,
                    launcherMapper,
                    appLayout,
                    Optional.ofNullable(runtimeLayout).orElse(RuntimeLayout.DEFAULT),
                    thePredefinedRuntimeLayout);
        }

        /**
         * Sets the layout of the predefined runtime image.
         * @param v the layout of the predefined runtime image. Null is permitted.
         * @return this
         */
        ApplicationBuilderBuilder predefinedRuntimeLayout(RuntimeLayout v) {
            predefinedRuntimeLayout = v;
            return this;
        }

        /**
         * Sets the layout of a runtime bundle.
         * @param v the layout of a runtime bundle. Null is permitted.
         * @return this
         */
        ApplicationBuilderBuilder runtimeLayout(RuntimeLayout v) {
            runtimeLayout = v;
            return this;
        }

        private RuntimeLayout runtimeLayout;
        private RuntimeLayout predefinedRuntimeLayout;
    }


    private static ApplicationBuilder createApplicationBuilder(Options optionValues,
            Function<Options, Launcher> launcherMapper,
            ApplicationLayout appLayout, RuntimeLayout runtimeLayout,
            Optional<RuntimeLayout> predefinedRuntimeLayout) {

        final var appBuilder = new ApplicationBuilder();

        final var isRuntimeInstaller = isRuntimeInstaller(optionValues);

        final var predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.findIn(optionValues);

        NAME.findIn(optionValues).or(() -> {
            if (isRuntimeInstaller) {
                return predefinedRuntimeImage.map(Path::getFileName).map(Path::toString);
            } else {
                return Optional.empty();
            }
        }).ifPresent(appBuilder::name);
        DESCRIPTION.ifPresentIn(optionValues, appBuilder::description);
        APP_VERSION.ifPresentIn(optionValues, appBuilder::version);
        VENDOR.ifPresentIn(optionValues, appBuilder::vendor);
        COPYRIGHT.ifPresentIn(optionValues, appBuilder::copyright);
        INPUT.ifPresentIn(optionValues, appBuilder::srcDir);
        APP_CONTENT.ifPresentIn(optionValues, appBuilder::contentDirs);

        final var predefinedRuntimeDirectory = predefinedRuntimeLayout.flatMap(
                layout -> predefinedRuntimeImage.map(layout::resolveAt)).map(RuntimeLayout::runtimeDirectory);

        if (isRuntimeInstaller) {
            appBuilder.appImageLayout(runtimeLayout);
        } else {
            appBuilder.appImageLayout(appLayout);

            if (PREDEFINED_APP_IMAGE.containsIn(optionValues)) {
                final var appImageFile = AppImageFile.load(appLayout.resolveAt(PREDEFINED_APP_IMAGE.getFrom(optionValues)));
                // Reset name and version if they were set. These values must be picked from the app image.
                appBuilder.name(null).version(null);

                var addLauncherOptionValues = optionValues
                        //
                        // For backward compatibility, descriptions of the additional
                        // launchers in the predefined app image will be set to
                        // the application description, if available, or to the name
                        // of the main launcher in the predefined app image.
                        //
                        // All launchers in the predefined app image will have the same description.
                        // This is wrong and should be revised.
                        //
                        .copyWithDefaultValue(DESCRIPTION, appImageFile.getLauncherName())
                        // Hide the icon from the additional launchers.
                        // This icon is for the native bundle, not for the launchers from the predefined app image.
                        .copyWithout(ICON);

                appBuilder.initFromExternalApplication(appImageFile, launcherInfo -> {
                    return launcherMapper.apply(mapLauncherInfo(addLauncherOptionValues, launcherInfo));
                });
            } else {
                final var launchers = createLaunchers(optionValues, launcherMapper);

                final var runtimeBuilderBuilder = new RuntimeBuilderBuilder();

                runtimeBuilderBuilder.modulePath(ensureBaseModuleInModulePath(MODULE_PATH.findIn(optionValues).orElseGet(List::of)));

                if (!APP_VERSION.containsIn(optionValues)) {
                    // Version is not specified explicitly. Try to get it from the app's module.
                    launchers.mainLauncher().startupInfo().ifPresent(startupInfo -> {
                        if (startupInfo instanceof LauncherModularStartupInfo modularStartupInfo) {
                            modularStartupInfo.moduleVersion().ifPresent(moduleVersion -> {
                                appBuilder.version(moduleVersion);
                                Log.verbose(I18N.format("message.module-version",
                                        moduleVersion, modularStartupInfo.moduleName()));
                            });
                        }
                    });
                }

                predefinedRuntimeDirectory.ifPresentOrElse(runtimeBuilderBuilder::forRuntime, () -> {
                    final var startupInfos = launchers.asList().stream()
                            .map(Launcher::startupInfo)
                            .map(Optional::orElseThrow).toList();
                    final var jlinkOptionsBuilder = runtimeBuilderBuilder.forNewRuntime(startupInfos);
                    ADD_MODULES.findIn(optionValues).map(Set::copyOf).ifPresent(jlinkOptionsBuilder::addModules);
                    JLINK_OPTIONS.ifPresentIn(optionValues, jlinkOptionsBuilder::options);
                    jlinkOptionsBuilder.apply();
                });

                appBuilder.launchers(launchers).runtimeBuilder(runtimeBuilderBuilder.create());
            }
        }

        return appBuilder;
    }

    private static ApplicationLaunchers createLaunchers(Options optionValues, Function<Options, Launcher> launcherMapper) {
        var launchers = ADDITIONAL_LAUNCHERS.getFrom(optionValues);

        var mainLauncher = launcherMapper.apply(optionValues);

        //
        // Additional launcher should:
        //  - Use description from the main launcher by default.
        //
        var mainLauncherDefaults = Options.of(Map.of(DESCRIPTION.id(), mainLauncher.description()));

        var additionalLaunchers = launchers.stream().map(launcherOptionValues -> {
            return launcherOptionValues.copyWithParent(mainLauncherDefaults);
        }).map(launcherMapper).toList();

        return new ApplicationLaunchers(mainLauncher, additionalLaunchers);
    }

    private static Options mapLauncherInfo(LauncherInfo launcherInfo) {
        Map<OptionIdentifier, ? super Object> optionValues = new HashMap<>();
        optionValues.put(NAME.id(), launcherInfo.name());
        optionValues.put(LAUNCHER_AS_SERVICE.id(), launcherInfo.service());
        return Options.concat(Options.of(optionValues), launcherInfo.extra());
    }

    private static Options mapLauncherInfo(Options optionValues, LauncherInfo launcherInfo) {
        return Options.concat(mapLauncherInfo(launcherInfo), optionValues);
    }
}
