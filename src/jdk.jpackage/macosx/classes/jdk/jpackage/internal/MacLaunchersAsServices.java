/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.Launcher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import jdk.jpackage.internal.util.PathUtils;

/**
 * Helper to install launchers as services using "launchd".
 */
public final class MacLaunchersAsServices extends UnixLaunchersAsServices {

    private MacLaunchersAsServices(BuildEnv env, Package pkg) throws IOException {
        super(env, pkg.app(), List.of(), launcher -> {
            return new MacLauncherAsService(env, pkg, launcher);
        });
    }

    static ShellCustomAction create(Map<String, Object> params,
            Path outputDir) throws IOException {

        // Order is important!
        var pkg = FromParams.PACKAGE.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        if (pkg.isRuntimeInstaller()) {
            return null;
        }
        return Optional.of(new MacLaunchersAsServices(env, pkg)).filter(Predicate.not(
                MacLaunchersAsServices::isEmpty)).orElse(null);
    }

    public static Path getServicePListFileName(String packageName,
            String launcherName) {
        String baseName = launcherName.replaceAll("[\\s]", "_");
        return Path.of(packageName + "-" + baseName + ".plist");
    }

    private static class MacLauncherAsService extends UnixLauncherAsService {

        MacLauncherAsService(BuildEnv env, Package pkg, Launcher launcher) {
            super(launcher, env.createResource("launchd.plist.template").setCategory(I18N
                    .getString("resource.launchd-plist-file")));

            plistFilename = getServicePListFileName(pkg.packageName(), getName());

            // It is recommended to set value of "label" property in launchd
            // .plist file equal to the name of this .plist file without the suffix.
            String label = PathUtils.replaceSuffix(plistFilename.getFileName(), "").toString();

            getResource()
                    .setPublicName(plistFilename)
                    .addSubstitutionDataEntry("LABEL", label)
                    .addSubstitutionDataEntry("APPLICATION_LAUNCHER",
                            pkg.asInstalledPackageApplicationLayout().launchersDirectory().resolve(
                                    getName()).toString());
        }

        @Override
        Path descriptorFilePath(Path root) {
            return root.resolve("Library/LaunchDaemons").resolve(plistFilename);
        }

        private final Path plistFilename;
    }
}
