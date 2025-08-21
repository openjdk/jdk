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

import static jdk.jpackage.internal.ApplicationImageUtils.createLauncherIconResource;
import jdk.jpackage.internal.PackagingPipeline.AppImageBuildEnv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.resources.ResourceLocator;

final class LinuxPackagingPipeline {

    enum LinuxAppImageTaskID implements TaskID {
        LAUNCHER_LIB,
        LAUNCHER_ICONS
    }

    static PackagingPipeline.Builder build() {
        return PackagingPipeline.buildStandard()
                .task(LinuxAppImageTaskID.LAUNCHER_LIB)
                        .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE)
                        .applicationAction(LinuxPackagingPipeline::writeLauncherLib).add()
                .task(LinuxAppImageTaskID.LAUNCHER_ICONS)
                        .addDependent(BuildApplicationTaskID.CONTENT)
                        .applicationAction(LinuxPackagingPipeline::writeLauncherIcons).add();
    }

    private static void writeLauncherLib(
            AppImageBuildEnv<Application, LinuxApplicationLayout> env) throws IOException {

        final var launcherLib = env.resolvedLayout().libAppLauncher();
        try (var in = ResourceLocator.class.getResourceAsStream("libjpackageapplauncheraux.so")) {
            Files.createDirectories(launcherLib.getParent());
            Files.copy(in, launcherLib);
        }
    }

    private static void writeLauncherIcons(
            AppImageBuildEnv<Application, ApplicationLayout> env) throws IOException {

        for (var launcher : env.app().launchers()) {
            createLauncherIconResource(env.app(), launcher, env.env()::createResource).ifPresent(iconResource -> {
                String iconFileName = launcher.executableName() + ".png";
                Path iconTarget = env.resolvedLayout().desktopIntegrationDirectory().resolve(iconFileName);
                try {
                    iconResource.saveToFile(iconTarget);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
    }

    static final LinuxApplicationLayout APPLICATION_LAYOUT = LinuxApplicationLayout.create(
            ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT, Path.of("lib/libapplauncher.so"));
}
