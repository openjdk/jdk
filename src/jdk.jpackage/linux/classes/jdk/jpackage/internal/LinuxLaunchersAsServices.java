/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.AppImageFile.LauncherInfo;
import static jdk.jpackage.internal.OverridableResource.createResource;
import static jdk.jpackage.internal.ShellCustomAction.escapedInstalledLauncherPath;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;

/**
 * Helper to install launchers as services.
 */
final class LinuxLaunchersAsServices extends ShellCustomAction {

    private static final String COMMANDS_INSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_INSTALL";
    private static final String COMMANDS_UNINSTALL = "LAUNCHER_AS_SERVICE_COMMANDS_UNINSTALL";
    private static final String SCRIPTS = "LAUNCHER_AS_SERVICE_SCRIPTS";

    private LinuxLaunchersAsServices(PlatformPackage thePackage,
            Map<String, ? super Object> params) throws IOException {

        this.thePackage = thePackage;

        // Read launchers information
        launchers = AppImageFile.getLaunchers(PREDEFINED_APP_IMAGE.fetchFrom(
                params), params).stream().filter(LauncherInfo::isService).map(
                li -> new Launcher(li.getName(), params)).toList();
    }

    static LinuxLaunchersAsServices create(PlatformPackage thePackage,
            Map<String, ? super Object> params) throws IOException {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return null;
        }
        return new LinuxLaunchersAsServices(thePackage, params);
    }

    @Override
    List<String> requiredPackages() {
        if (launchers.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of("systemd", "coreutils" /* /usr/bin/wc */,
                "grep");
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

        final List<String> installedUnitFiles = launchers.stream().map(
                launcher -> launcher.unitFilePath(Path.of("/")).toString()).toList();

        Function<String, String> strigifier = cmd -> {
            return stringifyShellCommands(Stream.of(List.of(
                    cmd), installedUnitFiles).flatMap(x -> x.stream()).collect(
                    Collectors.joining(" ")));
        };

        data.put(SCRIPTS, stringifyTextFile("service_utils.sh"));

        data.put(COMMANDS_INSTALL, strigifier.apply("register_units"));
        data.put(COMMANDS_UNINSTALL, strigifier.apply("unregister_units"));

        for (var launcher : launchers) {
            launcher.createUnitFile();
        }

        return data;
    }

    private class Launcher extends LauncherAsService {

        Launcher(String name, Map<String, ? super Object> mainParams) {
            super(name, mainParams, createResource("unit-template.service",
                    mainParams).setCategory(I18N.getString(
                            "resource.systemd-unit-file")));

            String baseName = getName().replaceAll("[\\s]", "_") + ".service";
            unitFilename = Path.of(thePackage.name() + "-" + baseName);

            getResource()
                    .setPublicName(unitFilename)
                    .addSubstitutionDataEntry("APPLICATION_LAUNCHER",
                            escapedInstalledLauncherPath(thePackage, getName()));
        }

        void createUnitFile() throws IOException {
            getResource().saveToFile(unitFilePath(thePackage.sourceRoot()));
        }

        Path unitFilePath(Path root) {
            return root.resolve("lib/systemd/system").resolve(unitFilename);
        }

        private final Path unitFilename;
    }

    private final PlatformPackage thePackage;
    private final List<Launcher> launchers;
}
