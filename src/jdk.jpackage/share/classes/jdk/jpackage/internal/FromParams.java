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

import static jdk.jpackage.internal.Arguments.CLIOptions.LINUX_SHORTCUT_HINT;
import static jdk.jpackage.internal.Arguments.CLIOptions.WIN_MENU_HINT;
import static jdk.jpackage.internal.Arguments.CLIOptions.WIN_SHORTCUT_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.ABOUT_URL;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_MODULES;
import static jdk.jpackage.internal.StandardBundlerParam.APP_CONTENT;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.COPYRIGHT;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALLER_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALL_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.JLINK_OPTIONS;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.LICENSE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.LIMIT_MODULES;
import static jdk.jpackage.internal.StandardBundlerParam.MODULE_PATH;
import static jdk.jpackage.internal.StandardBundlerParam.NAME;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.SOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.VENDOR;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.hasPredefinedAppImage;
import static jdk.jpackage.internal.StandardBundlerParam.isRuntimeInstaller;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.ParseUtils;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.function.ThrowingFunction;

final class FromParams {

    static <T extends Launcher> ApplicationBuilder createApplicationBuilder(Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherMapper,
            BiFunction<T, Launcher, T> launcherOverrideCtor,
            ApplicationLayout appLayout) throws ConfigException, IOException {
        return createApplicationBuilder(params, launcherMapper, launcherOverrideCtor, appLayout, RuntimeLayout.DEFAULT, Optional.of(RuntimeLayout.DEFAULT));
    }

    static <T extends Launcher> ApplicationBuilder createApplicationBuilder(Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherMapper,
            BiFunction<T, Launcher, T> launcherOverrideCtor,
            ApplicationLayout appLayout, RuntimeLayout runtimeLayout,
            Optional<RuntimeLayout> predefinedRuntimeLayout) throws ConfigException, IOException {

        final var appBuilder = new ApplicationBuilder();

        APP_NAME.copyInto(params, appBuilder::name);
        DESCRIPTION.copyInto(params, appBuilder::description);
        appBuilder.version(VERSION.fetchFrom(params));
        VENDOR.copyInto(params, appBuilder::vendor);
        COPYRIGHT.copyInto(params, appBuilder::copyright);
        SOURCE_DIR.copyInto(params, appBuilder::srcDir);
        APP_CONTENT.copyInto(params, appBuilder::contentDirs);

        final var isRuntimeInstaller = isRuntimeInstaller(params);

        final var predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.findIn(params);

        final var predefinedRuntimeDirectory = predefinedRuntimeLayout.flatMap(
                layout -> predefinedRuntimeImage.map(layout::resolveAt)).map(RuntimeLayout::runtimeDirectory);

        if (isRuntimeInstaller) {
            appBuilder.appImageLayout(runtimeLayout);
        } else {
            appBuilder.appImageLayout(appLayout);

            if (hasPredefinedAppImage(params)) {
                final var appImageFile = PREDEFINED_APP_IMAGE_FILE.fetchFrom(params);
                appBuilder.initFromExternalApplication(appImageFile, launcherInfo -> {
                    var launcherParams = mapLauncherInfo(appImageFile, launcherInfo);
                    return launcherMapper.apply(mergeParams(params, launcherParams));
                });
            } else {
                final var launchers = createLaunchers(params, launcherMapper);

                final var runtimeBuilderBuilder = new RuntimeBuilderBuilder();

                runtimeBuilderBuilder.modulePath(MODULE_PATH.fetchFrom(params));

                predefinedRuntimeDirectory.ifPresentOrElse(runtimeBuilderBuilder::forRuntime, () -> {
                    final var startupInfos = launchers.asList().stream()
                            .map(Launcher::startupInfo)
                            .map(Optional::orElseThrow).toList();
                    final var jlinkOptionsBuilder = runtimeBuilderBuilder.forNewRuntime(startupInfos);
                    ADD_MODULES.copyInto(params, jlinkOptionsBuilder::addModules);
                    LIMIT_MODULES.copyInto(params, jlinkOptionsBuilder::limitModules);
                    JLINK_OPTIONS.copyInto(params, jlinkOptionsBuilder::options);
                    jlinkOptionsBuilder.apply();
                });

                final var normalizedLaunchers = ApplicationBuilder.normalizeIcons(launchers, RESOURCE_DIR.findIn(params), launcherOverrideCtor);

                appBuilder.launchers(normalizedLaunchers).runtimeBuilder(runtimeBuilderBuilder.create());
            }
        }

        return appBuilder;
    }

    static PackageBuilder createPackageBuilder(
            Map<String, ? super Object> params, Application app,
            PackageType type) throws ConfigException {

        final var builder = new PackageBuilder(app, type);

        builder.name(INSTALLER_NAME.fetchFrom(params));
        DESCRIPTION.copyInto(params, builder::description);
        VERSION.copyInto(params, builder::version);
        ABOUT_URL.copyInto(params, builder::aboutURL);
        LICENSE_FILE.findIn(params).map(Path::of).ifPresent(builder::licenseFile);
        PREDEFINED_APP_IMAGE.findIn(params).ifPresent(builder::predefinedAppImage);
        PREDEFINED_RUNTIME_IMAGE.findIn(params).ifPresent(builder::predefinedAppImage);
        INSTALL_DIR.findIn(params).map(Path::of).ifPresent(builder::installDir);

        return builder;
    }

    static <T extends Application> BundlerParamInfo<T> createApplicationBundlerParam(
            ThrowingFunction<Map<String, ? super Object>, T> ctor) {
        return BundlerParamInfo.createBundlerParam(Application.class, ctor);
    }

    static <T extends jdk.jpackage.internal.model.Package> BundlerParamInfo<T> createPackageBundlerParam(
            ThrowingFunction<Map<String, ? super Object>, T> ctor) {
        return BundlerParamInfo.createBundlerParam(jdk.jpackage.internal.model.Package.class, ctor);
    }

    static Optional<jdk.jpackage.internal.model.Package> getCurrentPackage(Map<String, ? super Object> params) {
        return Optional.ofNullable((jdk.jpackage.internal.model.Package)params.get(
                jdk.jpackage.internal.model.Package.class.getName()));
    }

    static Optional<LauncherShortcut> findLauncherShortcut(
            BundlerParamInfo<String> shortcutParam,
            Map<String, ? super Object> mainParams,
            Map<String, ? super Object> launcherParams) {

        Optional<String> launcherValue;
        if (launcherParams == mainParams) {
            // The main launcher
            launcherValue = Optional.empty();
        } else {
            launcherValue = shortcutParam.findIn(launcherParams);
        }

        return launcherValue.map(ParseUtils::parseLauncherShortcutForAddLauncher).or(() -> {
            return Optional.ofNullable(mainParams.get(shortcutParam.getID())).map(toFunction(value -> {
                if (value instanceof Boolean) {
                    return new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT);
                } else {
                    try {
                        return ParseUtils.parseLauncherShortcutForMainLauncher((String)value);
                    } catch (IllegalArgumentException ex) {
                        throw I18N.buildConfigException("error.invalid-option-value", value, "--" + shortcutParam.getID()).create();
                    }
                }
            }));
        });
    }

    private static ApplicationLaunchers createLaunchers(
            Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherMapper) {
        var launchers = ADD_LAUNCHERS.findIn(params).orElseGet(List::of);

        var mainLauncher = launcherMapper.apply(params);
        var additionalLaunchers = launchers.stream().map(launcherParams -> {
            return launcherMapper.apply(mergeParams(params, launcherParams));
        }).toList();

        return new ApplicationLaunchers(mainLauncher, additionalLaunchers);
    }

    private static Map<String, ? super Object> mapLauncherInfo(ExternalApplication appImageFile, LauncherInfo launcherInfo) {
        Map<String, ? super Object> launcherParams = new HashMap<>();
        launcherParams.put(NAME.getID(), launcherInfo.name());
        if (!appImageFile.getLauncherName().equals(launcherInfo.name())) {
            // This is not the main launcher, accept the value
            // of "launcher-as-service" from the app image file (.jpackage.xml).
            launcherParams.put(LAUNCHER_AS_SERVICE.getID(), Boolean.toString(launcherInfo.service()));
        }
        launcherParams.putAll(launcherInfo.extra());
        return launcherParams;
    }

    private static Map<String, ? super Object> mergeParams(Map<String, ? super Object> mainParams,
            Map<String, ? super Object> launcherParams) {
        if (!launcherParams.containsKey(DESCRIPTION.getID())) {
            launcherParams = new HashMap<>(launcherParams);
// FIXME: this is a good improvement but it fails existing tests
//            launcherParams.put(DESCRIPTION.getID(), String.format("%s (%s)", DESCRIPTION.fetchFrom(
//                    mainParams), APP_NAME.fetchFrom(launcherParams)));
            launcherParams.put(DESCRIPTION.getID(), DESCRIPTION.fetchFrom(mainParams));
        }
        return AddLauncherArguments.merge(mainParams, launcherParams, ICON.getID(),
                ADD_LAUNCHERS.getID(), FILE_ASSOCIATIONS.getID(), WIN_MENU_HINT.getId(),
                WIN_SHORTCUT_HINT.getId(), LINUX_SHORTCUT_HINT.getId());
    }

    static final BundlerParamInfo<Application> APPLICATION = createApplicationBundlerParam(null);
}
