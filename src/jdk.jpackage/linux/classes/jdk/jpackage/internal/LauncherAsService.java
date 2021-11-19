/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.AppImageFile.LauncherInfo;
import static jdk.jpackage.internal.OverridableResource.createResource;
import static jdk.jpackage.internal.ShellCustomAction.escapedInstalledLauncherPath;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_LAUNCHERS;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;

/**
 * Helper to install launchers as services.
 */
final class LauncherAsService extends ShellCustomAction {

    private static final String COMMANDS_INSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_INSTALL";
    private static final String COMMANDS_UNINSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_UNINSTALL";
    private static final String SCRIPTS = "LAUNCHER_AS_SERVICE_SCRIPTS";

    private LauncherAsService(PlatformPackage thePackage,
            Map<String, ? super Object> params) throws IOException {

        List<Map<String, ? super Object>> allLaunchers = ADD_LAUNCHERS.fetchFrom(
                params, false);

        this.thePackage = thePackage;

        // Read launchers information from predefine app image
        if (allLaunchers == null && PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            launchers = AppImageFile.getLaunchers(
                    PREDEFINED_APP_IMAGE.fetchFrom(params), params).stream().filter(
                    LauncherInfo::isService).map(
                            li -> new Launcher(li.getName(), params)).toList();
        } else {
            launchers = new ArrayList<>();
            if (LAUNCHER_AS_SERVICE.fetchFrom(params)) {
                this.launchers.add(new Launcher(APP_NAME.fetchFrom(params),
                        params));
            }
            for (var launcherParams : Optional.ofNullable(allLaunchers).orElse(
                    Collections.emptyList())) {
                if (LAUNCHER_AS_SERVICE.fetchFrom(launcherParams)) {
                    launchers.add(new Launcher(
                            APP_NAME.fetchFrom(launcherParams), params));
                }
            }
        }

        unitFileData = Map.of("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(
                params));
    }

    static LauncherAsService create(PlatformPackage thePackage,
            Map<String, ? super Object> params) throws IOException {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return null;
        }
        return new LauncherAsService(thePackage, params);
    }

    @Override
    List<String> requiredPackages() {
        if (launchers.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of("systemd");
    }

    @Override
    List<String> replacementStringIds() {
        return List.of(COMMANDS_INSTALL, COMMANDS_UNINSTALL, SCRIPTS);
    }

    @Override
    Map<String, String> create() throws IOException {
        Map<String, String> data = new HashMap<>();

        if (launchers.isEmpty()) {
            return data;
        }

        data.put(SCRIPTS, stringifyTextFile("service_utils.sh"));

        List<String> installedUnitFiles = launchers.stream().map(
                launcher -> launcher.unitFilePath(Path.of("/")).toString()).toList();
        data.put(COMMANDS_INSTALL, stringifyShellCommands(Stream.of(List.of(
                "register_units"), installedUnitFiles).flatMap(x -> x.stream()).toList()));
        data.put(COMMANDS_UNINSTALL, stringifyShellCommands(Stream.of(List.of(
                "unregister_units"), installedUnitFiles).flatMap(x -> x.stream()).toList()));

        for (var launcher: launchers) {
            launcher.createUnitFile();
        }

        return data;
    }

    private class Launcher {
        Launcher(String name, Map<String, ? super Object> mainParams) {
            this.name = name;
            unitFilename = Path.of(thePackage.name() + "-" + name.replaceAll(
                    "[\\s]", "_") + ".service");
            unitFileResource = createResource("unit-template.service", mainParams)
                    .setCategory(I18N.getString("resource.systemd-unit-file"))
                    .setPublicName(unitFilename);
        }

        void createUnitFile() throws IOException {
            Map<String, String> data = new HashMap<>(unitFileData);
            data.put("APPLICATION_LAUNCHER", escapedInstalledLauncherPath(
                    thePackage, name));
            unitFileResource.setSubstitutionData(data).saveToFile(unitFilePath(
                    thePackage.sourceRoot()));
        }

        Path unitFilePath(Path root) {
            return root.resolve("lib/systemd/system").resolve(
                    unitFilename);
        }

        private final String name;
        private final Path unitFilename;
        private final OverridableResource unitFileResource;
    }

    private final PlatformPackage thePackage;
    private final Map<String, String> unitFileData;
    private final List<Launcher> launchers;
}
