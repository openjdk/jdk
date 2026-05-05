/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.PackagingPipeline.ApplicationImageTaskAction;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.CustomLauncherIcon;
import jdk.jpackage.internal.model.DefaultLauncherIcon;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.ResourceDirLauncherIcon;
import jdk.jpackage.internal.util.RootedPath;


final class ApplicationImageUtils {

    static Optional<OverridableResource> createLauncherIconResource(Launcher launcher,
            Function<String, OverridableResource> resourceSupplier) {

        return launcher.icon().map(icon -> {
            var resource = LauncherBuilder.createLauncherIconResource(launcher, resourceSupplier);

            switch (icon) {
                case DefaultLauncherIcon _ -> {
                    resource.setSourceOrder(OverridableResource.Source.DefaultResource);
                }
                case ResourceDirLauncherIcon v -> {
                    resource.setSourceOrder(OverridableResource.Source.ResourceDir);
                    resource.setPublicName(v.name());
                }
                case CustomLauncherIcon v -> {
                    resource.setSourceOrder(OverridableResource.Source.External);
                    resource.setExternal(v.path());
                }
            }

            return resource;
        });
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createWriteRuntimeAction() {
        return env -> {
            env.app().runtimeBuilder().orElseThrow().create(env.resolvedLayout());
        };
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createWriteAppImageFileAction() {
        return env -> {
            new AppImageFile(env.app()).save(env.resolvedLayout());
        };
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createCopyContentAction() {
        return env -> {
            for (var e : List.of(
                    Map.entry(env.app().appDirSources(), env.resolvedLayout().appDirectory()),
                    Map.entry(env.app().contentDirSources(), env.resolvedLayout().contentDirectory())
            )) {
                RootedPath.copy(e.getKey().stream(), e.getValue(),
                        StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            }
        };
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createWriteLaunchersAction() {
        return env -> {
            for (var launcher : env.app().launchers()) {
                // Create corresponding .cfg file
                new CfgFile(env.app(), launcher).create(env.resolvedLayout());

                // Copy executable to launchers folder
                Path executableFile = env.resolvedLayout().launchersDirectory().resolve(
                        launcher.executableNameWithSuffix());
                try (var in = launcher.executableResource()) {
                    Files.createDirectories(executableFile.getParent());
                    Files.copy(in, executableFile);
                }

                // Make it executable for everyone. It is essential to make the launcher executable for others
                // on macOS. Otherwise, launchers in installed DMG or PKG packages will be
                // unavailable for anyone but the user who installed them.
                executableFile.toFile().setExecutable(true, false);
            }
        };
    }
}
