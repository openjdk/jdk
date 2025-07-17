/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Package;

/**
 * Helper to install launchers as services using "systemd".
 */
public final class LinuxLaunchersAsServices extends UnixLaunchersAsServices {

    private LinuxLaunchersAsServices(BuildEnv env, Package pkg) throws IOException {
        super(env.appImageDir(), pkg.app(), REQUIRED_PACKAGES, launcher -> {
            return new LauncherImpl(env, pkg, launcher);
        });
    }

    @Override
    protected List<String> replacementStringIds() {
        return LINUX_REPLACEMENT_STRING_IDS;
    }

    @Override
    protected Map<String, String> createImpl() throws IOException {
        var data = super.createImpl();
        if (!data.isEmpty()) {
            data.put(COMMON_SCRIPTS, stringifyTextFile("common_utils.sh"));
        }
        return data;
    }

    static ShellCustomAction create(BuildEnv env, Package pkg) throws IOException {
        if (pkg.isRuntimeInstaller()) {
            return ShellCustomAction.nop(LINUX_REPLACEMENT_STRING_IDS);
        }
        return new LinuxLaunchersAsServices(env, pkg);
    }

    public static Path getServiceUnitFileName(String packageName, String launcherName) {
        String baseName = launcherName.replaceAll("[\\s]", "_");
        return Path.of(packageName + "-" + baseName + ".service");
    }

    private static final class LauncherImpl extends UnixLauncherAsService {

        LauncherImpl(BuildEnv env, Package pkg, Launcher launcher) {
            super(pkg.app(), launcher, env.createResource("unit-template.service").setCategory(
                    I18N.getString("resource.systemd-unit-file")));

            unitFilename = getServiceUnitFileName(pkg.packageName(), launcher.executableName());

            getResource().setPublicName(unitFilename).addSubstitutionDataEntry(
                    "APPLICATION_LAUNCHER",
                    Enquoter.forPropertyValues().applyTo(
                            pkg.asInstalledPackageApplicationLayout().orElseThrow().launchersDirectory().resolve(getName()).toString()));
        }

        @Override
        Path descriptorFilePath(Path root) {
            return root.resolve("lib/systemd/system").resolve(unitFilename);
        }

        private final Path unitFilename;
    }

    private static final List<String> REQUIRED_PACKAGES = List.of("systemd",
            "coreutils" /* /usr/bin/wc */, "grep");

    private static final String COMMON_SCRIPTS = "COMMON_SCRIPTS";

    private static final List<String> LINUX_REPLACEMENT_STRING_IDS;

    static {
        ArrayList<String> buf = new ArrayList<>();
        buf.addAll(UnixLaunchersAsServices.REPLACEMENT_STRING_IDS);
        buf.add(COMMON_SCRIPTS);

        LINUX_REPLACEMENT_STRING_IDS = Collections.unmodifiableList(buf);
    }
}
