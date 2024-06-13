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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import static jdk.jpackage.internal.Functional.ThrowingBiFunction.toBiFunction;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.MENU_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.VENDOR;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.getPredefinedAppImage;
import static jdk.jpackage.internal.StandardBundlerParam.isRuntimeInstaller;

interface Application {

    String name();

    String description();

    String version();

    String vendor();

    Path predefinedRuntimeImage();

    RuntimeBuilder runtimeBuilder();

    default Path appImageDirName() {
        switch (OperatingSystem.current()) {
            case MACOS -> {
                return Path.of(name() + ".app");
            }
            default -> {
                return Path.of(name());
            }
        }
    }

    Launcher mainLauncher();

    List<Launcher> additionalLaunchers();

    default boolean isService() {
        return allLaunchers().stream().filter(Launcher::isService).findAny().isPresent();
    }

    default ApplicationLayout appLayout() {
        if (mainLauncher() == null) {
            return ApplicationLayout.javaRuntime();
        } else {
            return ApplicationLayout.platformAppImage();
        }
    }

    default List<Launcher> allLaunchers() {
        return Optional.ofNullable(mainLauncher()).map(main -> {
            return Stream.concat(Stream.of(main), additionalLaunchers().stream()).toList();
        }).orElse(List.of());
    }

    static record Impl(String name, String description, String version, String vendor,
            Path predefinedRuntimeImage, RuntimeBuilder runtimeBuilder, Launcher mainLauncher,
            List<Launcher> additionalLaunchers) implements Application {

    }

    static class Proxy implements Application {

        Proxy(Application target) {
            this.target = target;
        }

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public String version() {
            return target.version();
        }

        @Override
        public String vendor() {
            return target.vendor();
        }

        @Override
        public Path predefinedRuntimeImage() {
            return target.predefinedRuntimeImage();
        }

        @Override
        public RuntimeBuilder runtimeBuilder() {
            return target.runtimeBuilder();
        }

        @Override
        public Launcher mainLauncher() {
            return target.mainLauncher();
        }

        @Override
        public List<Launcher> additionalLaunchers() {
            return target.additionalLaunchers();
        }

        private final Application target;
    }

    static Application createFromParams(Map<String, ? super Object> params,
            Function<Map<String, ? super Object>, Launcher> launcherSupplier) throws ConfigException {
        var name = APP_NAME.fetchFrom(params);
        var description = DESCRIPTION.fetchFrom(params);
        var version = VERSION.fetchFrom(params);
        var vendor = VENDOR.fetchFrom(params);
        var predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);

        var predefinedAppImage = getPredefinedAppImage(params);
        if (name == null && predefinedAppImage == null) {
            // Can happen when no name is given, and using a foreign app-image
            throw new ConfigException(I18N.getString("error.no.name"), I18N.getString(
                    "error.no.name.advice"));
        }

        RuntimeBuilder runtimeBuilder;

        Launcher mainLauncher;
        List<Launcher> additionalLaunchers;
        if (isRuntimeInstaller(params)) {
            runtimeBuilder = null;
            mainLauncher = null;
            additionalLaunchers = List.of();
        } else if (predefinedAppImage != null) {
            runtimeBuilder = null;

            AppImageFile appImage = AppImageFile.load(predefinedAppImage);

            version = appImage.getAppVersion();

            mainLauncher = launcherSupplier.apply(mergeParams(params, Map.of(APP_NAME.getID(),
                    appImage.getLauncherName(), DESCRIPTION.getID(), description)));
            additionalLaunchers = appImage.getAddLaunchers().stream().map(li -> {
                return launcherSupplier.apply(mergeParams(params, Map.of(APP_NAME.getID(), li
                        .getName(), SHORTCUT_HINT.getID(), li.isShortcut(), MENU_HINT.getID(), li
                        .isMenu(), LAUNCHER_AS_SERVICE.getID(), li.isService())));
            }).toList();
        } else {
            var launchers = Optional.ofNullable(ADD_LAUNCHERS.fetchFrom(params)).orElseGet(
                    Collections::emptyList);
            mainLauncher = launcherSupplier.apply(params);
            additionalLaunchers = launchers.stream().map(launcherParams -> {
                return launcherSupplier.apply(mergeParams(params, launcherParams));
            }).toList();

            var startupInfos = Stream.concat(Stream.of(mainLauncher), additionalLaunchers.stream()).map(
                    Launcher::startupInfo).toList();
            runtimeBuilder = RuntimeBuilder.createFromParams(params, startupInfos);
        }

        return new Impl(name, description, version, vendor, predefinedRuntimeImage, runtimeBuilder,
                mainLauncher, additionalLaunchers);
    }

    private static Map<String, ? super Object> mergeParams(Map<String, ? super Object> mainParams,
            Map<String, ? super Object> launcherParams) {
        if (!launcherParams.containsKey(DESCRIPTION.getID())) {
            launcherParams = new HashMap<>(launcherParams);
            launcherParams.put(DESCRIPTION.getID(), String.format("%s (%s)", DESCRIPTION.fetchFrom(
                    mainParams), APP_NAME.fetchFrom(launcherParams)));
        }
        return AddLauncherArguments.merge(mainParams, launcherParams, ICON.getID(), ADD_LAUNCHERS
                .getID(), FILE_ASSOCIATIONS.getID());
    }

    final static String PARAM_ID = "target.application";

    static final StandardBundlerParam<Application> TARGET_APPLICATION = new StandardBundlerParam<>(
            PARAM_ID, Application.class, params -> {
                return toBiFunction(Application::createFromParams).apply(params,
                        Launcher::createFromParams);
            }, null);
}
