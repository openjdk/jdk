/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Launcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.AppImageFile2.LauncherInfo;
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
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.SOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.VENDOR;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.getPredefinedAppImage;
import static jdk.jpackage.internal.StandardBundlerParam.isRuntimeInstaller;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.function.ThrowingFunction;

final class FromParams {

    static ApplicationBuilder createApplicationBuilder(Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherMapper, ApplicationLayout appLayout)
            throws ConfigException, IOException {

        var appBuilder = new ApplicationBuilder()
                .name(APP_NAME.fetchFrom(params))
                .description(DESCRIPTION.fetchFrom(params))
                .version(VERSION.fetchFrom(params))
                .vendor(VENDOR.fetchFrom(params))
                .copyright(COPYRIGHT.fetchFrom(params))
                .srcDir(SOURCE_DIR.fetchFrom(params))
                .contentDirs(APP_CONTENT.fetchFrom(params));

        var isRuntimeInstaller = isRuntimeInstaller(params);

        if (isRuntimeInstaller) {
            appBuilder.appImageLayout(RuntimeLayout.DEFAULT);
        } else {
            appBuilder.appImageLayout(appLayout);
        }

        var predefinedAppImage = getPredefinedAppImage(params);

        if (isRuntimeInstaller) {
        } else if (predefinedAppImage != null) {
            var appIMafeFile = AppImageFile2.load(predefinedAppImage, appLayout);
            appBuilder.initFromAppImage(appIMafeFile, launcherInfo -> {
                var launcherParams = mapLauncherInfo(launcherInfo);
                return launcherMapper.apply(mergeParams(params, launcherParams));
            });
        } else {
            var launchers = createLaunchers(params, launcherMapper);

            var runtimeBuilderBuilder = new RuntimeBuilderBuilder()
                    .modulePath(MODULE_PATH.fetchFrom(params));

            Path predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
            if (predefinedRuntimeImage != null) {
                runtimeBuilderBuilder.forRuntime(predefinedRuntimeImage);
            } else {
                var startupInfos = launchers.asList().stream()
                        .map(Launcher::startupInfo)
                        .map(Optional::orElseThrow).toList();
                runtimeBuilderBuilder.forNewRuntime(startupInfos)
                        .addModules(ADD_MODULES.fetchFrom(params))
                        .limitModules(LIMIT_MODULES.fetchFrom(params))
                        .options(JLINK_OPTIONS.fetchFrom(params))
                        .appy();
            }

            appBuilder.launchers(launchers).runtimeBuilder(runtimeBuilderBuilder.create());
        }

        return appBuilder;
    }

    static PackageBuilder createPackageBuilder(
            Map<String, ? super Object> params, Application app,
            PackageType type) throws ConfigException {
        return new PackageBuilder(app, type)
                .name(INSTALLER_NAME.fetchFrom(params))
                .description(DESCRIPTION.fetchFrom(params))
                .version(VERSION.fetchFrom(params))
                .aboutURL(ABOUT_URL.fetchFrom(params))
                .licenseFile(Optional.ofNullable(LICENSE_FILE.fetchFrom(params)).map(Path::of).orElse(null))
                .predefinedAppImage(getPredefinedAppImage(params))
                .installDir(Optional.ofNullable(INSTALL_DIR.fetchFrom(params)).map(Path::of).orElse(null));
    }

    static <T extends Application> BundlerParamInfo<T> createApplicationBundlerParam(
            ThrowingFunction<Map<String, ? super Object>, T> valueFunc) {
        return BundlerParamInfo.createBundlerParam("target.application", params -> {
            var app = valueFunc.apply(params);
            params.put(APPLICATION.getID(), app);
            return app;
        });
    }

    static <T extends jdk.jpackage.internal.model.Package> BundlerParamInfo<T> createPackageBundlerParam(
            ThrowingFunction<Map<String, ? super Object>, T> valueFunc) {
        return BundlerParamInfo.createBundlerParam("target.package", params -> {
            var pkg = valueFunc.apply(params);
            params.put(PACKAGE.getID(), pkg);
            return pkg;
        });
    }

    private static ApplicationLaunchers createLaunchers(
            Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherMapper) {
        var launchers = Optional.ofNullable(ADD_LAUNCHERS.fetchFrom(params)).orElseGet(List::of);

        var mainLauncher = launcherMapper.apply(params);
        var additionalLaunchers = launchers.stream().map(launcherParams -> {
            return launcherMapper.apply(mergeParams(params, launcherParams));
        }).toList();

        return new ApplicationLaunchers(mainLauncher, additionalLaunchers);
    }

    private static Map<String, ? super Object> mapLauncherInfo(LauncherInfo launcherInfo) {
        Map<String, ? super Object> launcherParams = new HashMap<>();
        launcherParams.put(APP_NAME.getID(), launcherInfo.name());
        launcherParams.put(LAUNCHER_AS_SERVICE.getID(), Boolean.toString(launcherInfo.service()));
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
        return AddLauncherArguments.merge(mainParams, launcherParams, ICON.getID(), ADD_LAUNCHERS
                .getID(), FILE_ASSOCIATIONS.getID());
    }

    static final BundlerParamInfo<Application> APPLICATION = createApplicationBundlerParam(null);
    static final BundlerParamInfo<jdk.jpackage.internal.model.Package> PACKAGE = createPackageBundlerParam(null);
}
