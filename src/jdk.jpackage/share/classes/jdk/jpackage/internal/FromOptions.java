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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.ApplicationBuilder.normalizeIcons;
import static jdk.jpackage.internal.JLinkRuntimeBuilder.ensureBaseModuleInModulePath;
import static jdk.jpackage.internal.OptionUtils.isRuntimeInstaller;
import static jdk.jpackage.internal.cli.StandardOption.ABOUT_URL;
import static jdk.jpackage.internal.cli.StandardOption.ADDITIONAL_LAUNCHERS;
import static jdk.jpackage.internal.cli.StandardOption.ADD_MODULES;
import static jdk.jpackage.internal.cli.StandardOption.APP_CONTENT;
import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.COPYRIGHT;
import static jdk.jpackage.internal.cli.StandardOption.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardOption.INPUT;
import static jdk.jpackage.internal.cli.StandardOption.INSTALL_DIR;
import static jdk.jpackage.internal.cli.StandardOption.JLINK_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOption.LICENSE_FILE;
import static jdk.jpackage.internal.cli.StandardOption.MODULE_PATH;
import static jdk.jpackage.internal.cli.StandardOption.NAME;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.RESOURCE_DIR;
import static jdk.jpackage.internal.cli.StandardOption.VENDOR;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.RootedPath;

final class FromOptions {

    static ApplicationBuilderBuilder buildApplicationBuilder() {
        return new ApplicationBuilderBuilder();
    }

    static PackageBuilder createPackageBuilder(Options options, Application app, PackageType type) {

        final var builder = new PackageBuilder(app, type);

        NAME.ifPresentIn(options, builder::name);
        DESCRIPTION.ifPresentIn(options, builder::description);
        APP_VERSION.ifPresentIn(options, builder::version);
        ABOUT_URL.ifPresentIn(options, builder::aboutURL);
        LICENSE_FILE.ifPresentIn(options, builder::licenseFile);
        PREDEFINED_APP_IMAGE.ifPresentIn(options, builder::predefinedAppImage);
        PREDEFINED_RUNTIME_IMAGE.ifPresentIn(options, builder::predefinedAppImage);
        INSTALL_DIR.ifPresentIn(options, builder::installDir);

        return builder;
    }


    static final class ApplicationBuilderBuilder {

        private ApplicationBuilderBuilder() {
        }

        <T extends Launcher> ApplicationBuilder create(Options options,
                Function<Options, Launcher> launcherCtor,
                BiFunction<T, Launcher, T> launcherOverrideCtor,
                ApplicationLayout appLayout) {

            final Optional<RuntimeLayout> thePredefinedRuntimeLayout;
            if (PREDEFINED_RUNTIME_IMAGE.containsIn(options)) {
                thePredefinedRuntimeLayout = Optional.ofNullable(
                        predefinedRuntimeLayout).or(() -> Optional.of(RuntimeLayout.DEFAULT));
            } else {
                thePredefinedRuntimeLayout = Optional.empty();
            }

            final var transfomer = new OptionsTransformer(options, appLayout);
            final var appBuilder = createApplicationBuilder(
                    transfomer.appOptions(),
                    launcherCtor,
                    launcherOverrideCtor,
                    appLayout,
                    Optional.ofNullable(runtimeLayout).orElse(RuntimeLayout.DEFAULT),
                    thePredefinedRuntimeLayout);

            transfomer.externalApp().ifPresent(appBuilder::externalApplication);

            return appBuilder;
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


    private static <T extends Launcher> ApplicationBuilder createApplicationBuilder(Options options,
            Function<Options, Launcher> launcherCtor,
            BiFunction<T, Launcher, T> launcherOverrideCtor,
            ApplicationLayout appLayout, RuntimeLayout runtimeLayout,
            Optional<RuntimeLayout> predefinedRuntimeLayout) {

        final var appBuilder = new ApplicationBuilder();

        final var isRuntimeInstaller = isRuntimeInstaller(options);

        final var predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.findIn(options);

        final var predefinedRuntimeDirectory = predefinedRuntimeLayout.flatMap(layout -> {
            return predefinedRuntimeImage.map(layout::resolveAt);
        }).map(RuntimeLayout::runtimeDirectory);

        NAME.findIn(options).or(() -> {
            if (isRuntimeInstaller) {
                return predefinedRuntimeImage.map(Path::getFileName).map(Path::toString);
            } else {
                return Optional.empty();
            }
        }).ifPresent(appBuilder::name);
        DESCRIPTION.ifPresentIn(options, appBuilder::description);
        APP_VERSION.ifPresentIn(options, appBuilder::version);
        VENDOR.ifPresentIn(options, appBuilder::vendor);
        COPYRIGHT.ifPresentIn(options, appBuilder::copyright);
        INPUT.ifPresentIn(options, appBuilder::appDirSources);
        APP_CONTENT.findIn(options).map((List<Collection<RootedPath>> v) -> {
            // Reverse the order of content sources.
            // If there are multiple source files for the same
            // destination file, only the first will be used.
            // Reversing the order of content sources makes it use the last file
            // from the original list of source files for the given destination file.
            return v.reversed().stream().flatMap(Collection::stream).toList();
        }).ifPresent(appBuilder::contentDirSources);

        if (isRuntimeInstaller) {
            appBuilder.appImageLayout(runtimeLayout);
        } else {
            appBuilder.appImageLayout(appLayout);

            final var launchers = createLaunchers(options, launcherCtor);

            if (PREDEFINED_APP_IMAGE.containsIn(options)) {
                appBuilder.launchers(launchers);
            } else {
                appBuilder.launchers(normalizeIcons(launchers, RESOURCE_DIR.findIn(options), launcherOverrideCtor));

                final var runtimeBuilderBuilder = new RuntimeBuilderBuilder();

                runtimeBuilderBuilder.modulePath(ensureBaseModuleInModulePath(MODULE_PATH.findIn(options).orElseGet(List::of)));

                if (!APP_VERSION.containsIn(options)) {
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
                    ADD_MODULES.findIn(options).map(Set::copyOf).ifPresent(jlinkOptionsBuilder::addModules);
                    JLINK_OPTIONS.ifPresentIn(options, jlinkOptionsBuilder::options);
                    jlinkOptionsBuilder.apply();
                });

                appBuilder.runtimeBuilder(runtimeBuilderBuilder.create());
            }
        }

        return appBuilder;
    }

    private static ApplicationLaunchers createLaunchers(Options options, Function<Options, Launcher> launcherCtor) {
        var launchers = ADDITIONAL_LAUNCHERS.getFrom(options);

        var mainLauncher = launcherCtor.apply(options);

        //
        // Additional launcher should:
        //  - Use description from the main launcher by default.
        //
        var mainLauncherDefaults = Options.of(Map.of(DESCRIPTION, mainLauncher.description()));

        var additionalLaunchers = launchers.stream().map(launcherOptions -> {
            return launcherOptions.copyWithParent(mainLauncherDefaults);
        }).map(launcherCtor).toList();

        return new ApplicationLaunchers(mainLauncher, additionalLaunchers);
    }
}
