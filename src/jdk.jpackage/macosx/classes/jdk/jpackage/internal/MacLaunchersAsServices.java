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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import static jdk.jpackage.internal.MacAppImageBuilder.MAC_CF_BUNDLE_IDENTIFIER;
import static jdk.jpackage.internal.OverridableResource.createResource;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import jdk.jpackage.internal.util.PathUtils;

/**
 * Helper to install launchers as services using "launchd".
 */
public final class MacLaunchersAsServices extends UnixLaunchersAsServices {

    private MacLaunchersAsServices(PlatformPackage thePackage,
            Map<String, Object> params) throws IOException {
        super(thePackage, List.of(), params, li -> {
            return new Launcher(thePackage, li.getName(), params);
        });
    }

    static ShellCustomAction create(Map<String, Object> params,
            Path outputDir) throws IOException {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return null;
        }
        return Optional.of(new MacLaunchersAsServices(new PlatformPackage() {
            @Override
            public String name() {
                return MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params);
            }

            @Override
            public Path sourceRoot() {
                return outputDir;
            }

            @Override
            public ApplicationLayout sourceApplicationLayout() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApplicationLayout installedApplicationLayout() {
                return ApplicationLayout.macAppImage().resolveAt(Path.of(
                        MacBaseInstallerBundler.getInstallDir(params, false),
                        APP_NAME.fetchFrom(params) + ".app"));
            }
        }, params)).filter(Predicate.not(MacLaunchersAsServices::isEmpty)).orElse(
                null);
    }

    public static Path getServicePListFileName(String packageName,
            String launcherName) {
        String baseName = launcherName.replaceAll("[\\s]", "_");
        return Path.of(packageName + "-" + baseName + ".plist");
    }

    private static class Launcher extends UnixLauncherAsService {

        Launcher(PlatformPackage thePackage, String name,
                Map<String, Object> mainParams) {
            super(name, mainParams, createResource("launchd.plist.template",
                    mainParams).setCategory(I18N.getString(
                            "resource.launchd-plist-file")));

            plistFilename = getServicePListFileName(thePackage.name(), getName());

            // It is recommended to set value of "label" property in launchd
            // .plist file equal to the name of this .plist file without the suffix.
            String label = PathUtils.replaceSuffix(plistFilename.getFileName(), "").toString();

            getResource()
                    .setPublicName(plistFilename)
                    .addSubstitutionDataEntry("LABEL", label)
                    .addSubstitutionDataEntry("APPLICATION_LAUNCHER",
                            thePackage.installedApplicationLayout().launchersDirectory().resolve(
                                    getName()).toString());
        }

        @Override
        Path descriptorFilePath(Path root) {
            return root.resolve("Library/LaunchDaemons").resolve(plistFilename);
        }

        private final Path plistFilename;
    }
}
