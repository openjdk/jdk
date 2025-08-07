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

import java.io.IOException;
import java.io.UncheckedIOException;
import jdk.jpackage.internal.PackagingPipeline.AppImageBuildEnv;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.CopyAppImageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinLauncher;

final class WinPackagingPipeline {

    enum WinAppImageTaskID implements TaskID {
        REBRAND_LAUNCHERS
    }

    static PackagingPipeline.Builder build() {
        return PackagingPipeline.buildStandard()
                .appImageLayoutForPackaging(Package::appImageLayout)
                .task(CopyAppImageTaskID.COPY).noaction().add()
                .task(WinAppImageTaskID.REBRAND_LAUNCHERS)
                        .addDependency(BuildApplicationTaskID.LAUNCHERS)
                        .addDependent(PrimaryTaskID.BUILD_APPLICATION_IMAGE)
                        .applicationAction(WinPackagingPipeline::rebrandLaunchers).add();
    }

    private static void rebrandLaunchers(AppImageBuildEnv<WinApplication, ApplicationLayout> env)
            throws IOException, PackagerException {
        for (var launcher : env.app().launchers()) {
            final var iconTarget = createLauncherIconResource(env.app(), launcher, env.env()::createResource).map(iconResource -> {
                var iconDir = env.env().buildRoot().resolve("icons");
                var theIconTarget = iconDir.resolve(launcher.executableName() + ".ico");
                try {
                    if (null == iconResource.saveToFile(theIconTarget)) {
                        theIconTarget = null;
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                return theIconTarget;
            });

            var launcherExecutable = env.resolvedLayout().launchersDirectory().resolve(
                    launcher.executableNameWithSuffix());

            // Update branding of launcher executable
            new ExecutableRebrander(env.app(),
                    (WinLauncher) launcher, env.env()::createResource).execute(
                            env.env(), launcherExecutable, iconTarget);
        }
    }

    static final ApplicationLayout APPLICATION_LAYOUT = ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT;
}
