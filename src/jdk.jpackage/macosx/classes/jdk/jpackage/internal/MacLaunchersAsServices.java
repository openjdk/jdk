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

import java.nio.file.Path;
import java.util.List;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.util.PathUtils;

/**
 * Helper to install launchers as services using "launchd".
 */
public final class MacLaunchersAsServices extends UnixLaunchersAsServices {

    MacLaunchersAsServices(BuildEnv env, MacPackage pkg) {
        super(env.appImageDir(), pkg.app(), List.of(), launcher -> {
            return new MacLauncherAsService(env, pkg, launcher);
        });
    }

    public static Path getServicePListFileName(String bundleIdentifier,
            String launcherName) {
        String baseName = launcherName.replaceAll("[\\s]", "_");
        return Path.of(bundleIdentifier + "-" + baseName + ".plist");
    }

    private static class MacLauncherAsService extends UnixLauncherAsService {

        MacLauncherAsService(BuildEnv env, MacPackage pkg, Launcher launcher) {
            super(pkg.app(), launcher, env.createResource("launchd.plist.template").setCategory(I18N
                    .getString("resource.launchd-plist-file")));

            plistFilename = getServicePListFileName(pkg.app().bundleIdentifier(), getName());

            // It is recommended to set value of "label" property in launchd
            // .plist file equal to the name of this .plist file without the suffix.
            String label = PathUtils.replaceSuffix(plistFilename.getFileName(), "").toString();

            getResource()
                    .setPublicName(plistFilename)
                    .addSubstitutionDataEntry("LABEL", label)
                    .addSubstitutionDataEntry("APPLICATION_LAUNCHER",
                            pkg.asInstalledPackageApplicationLayout().orElseThrow().launchersDirectory().resolve(getName()).toString());
        }

        @Override
        Path descriptorFilePath(Path root) {
            return root.resolve("Library/LaunchDaemons").resolve(plistFilename);
        }

        private final Path plistFilename;
    }
}
