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

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.jpackage.internal.PackagingPipeline.ApplicationImageTaskAction;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.CustomLauncherIcon;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathUtils;


final class ApplicationImageUtils {

    static Optional<OverridableResource> createLauncherIconResource(Application app,
            Launcher launcher,
            Function<String, OverridableResource> resourceSupplier) {
        final String defaultIconName = launcher.defaultIconResourceName();
        final String resourcePublicName = launcher.executableName() + PathUtils.getSuffix(Path.of(defaultIconName));

        if (!launcher.hasIcon()) {
            return Optional.empty();
        }

        OverridableResource resource = resourceSupplier.apply(defaultIconName)
                .setCategory("icon")
                .setPublicName(resourcePublicName);

        launcher.icon().flatMap(CustomLauncherIcon::fromLauncherIcon).map(CustomLauncherIcon::path).ifPresent(resource::setExternal);

        if (launcher.hasDefaultIcon() && app.mainLauncher().orElseThrow() != launcher) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (toSupplier(() -> resource.saveToFile(nullPath)).get() != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                return createLauncherIconResource(
                        app, app.mainLauncher().orElseThrow(),
                        resourceSupplier
                ).map(r -> r.setLogPublicName(resourcePublicName));
            }
        }

        return Optional.of(resource);
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createWriteRuntimeAction() {
        return env -> {
            env.app().runtimeBuilder().orElseThrow().createRuntime(env.resolvedLayout());
        };
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createWriteAppImageFileAction() {
        return env -> {
            new AppImageFile(env.app()).save(env.resolvedLayout());
        };
    }

    static ApplicationImageTaskAction<Application, ApplicationLayout> createCopyContentAction(Supplier<List<Path>> excludeCopyDirs) {
        return env -> {
            var excludeCandidates = Stream.concat(
                    excludeCopyDirs.get().stream(),
                    Stream.of(env.env().buildRoot(), env.env().appImageDir())
            ).map(Path::toAbsolutePath).toList();

            env.app().srcDir().ifPresent(toConsumer(srcDir -> {
                copyRecursive(srcDir, env.resolvedLayout().appDirectory(), excludeCandidates);
            }));

            for (var srcDir : env.app().contentDirs()) {
                copyRecursive(srcDir,
                        env.resolvedLayout().contentDirectory().resolve(srcDir.getFileName()),
                        excludeCandidates);
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

    private static void copyRecursive(Path srcDir, Path dstDir, List<Path> excludeDirs) throws IOException {
        srcDir = srcDir.toAbsolutePath();

        List<Path> excludes = new ArrayList<>();

        for (var path : excludeDirs) {
            if (Files.isDirectory(path)) {
                if (path.startsWith(srcDir) && !Files.isSameFile(path, srcDir)) {
                    excludes.add(path);
                }
            }
        }

        FileUtils.copyRecursive(srcDir, dstDir.toAbsolutePath(), excludes, LinkOption.NOFOLLOW_LINKS);
    }
}
