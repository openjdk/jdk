/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathUtils;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;


final class AppImageBuilder {

    static final class Builder {

        Builder launcherCallback(LauncherCallback v) {
            launcherCallback = v;
            return this;
        }

        Builder excludeDirFromCopying(Path path) {
            Objects.requireNonNull(path);

            if (excludeCopyDirs == null) {
                excludeCopyDirs = new ArrayList<>();
            }
            excludeCopyDirs.add(path);
            return this;
        }

        AppImageBuilder create(Application app) {
            return new AppImageBuilder(app, excludeCopyDirs, launcherCallback);
        }

        AppImageBuilder create(Package pkg) {
            return new AppImageBuilder(pkg, excludeCopyDirs, launcherCallback);
        }

        private List<Path> excludeCopyDirs;
        private LauncherCallback launcherCallback;
    }

    static Builder build() {
        return new Builder();
    }

    private AppImageBuilder(Application app, ApplicationLayout appLayout,
            List<Path> excludeCopyDirs, LauncherCallback launcherCallback,
            boolean withAppImageFile) {
        Objects.requireNonNull(app);
        Objects.requireNonNull(appLayout);

        this.app = app;
        this.appLayout = appLayout;
        this.withAppImageFile = withAppImageFile;
        this.launcherCallback = launcherCallback;
        this.excludeCopyDirs = Optional.ofNullable(excludeCopyDirs).orElseGet(List::of);
    }

    private AppImageBuilder(Application app, List<Path> excludeCopyDirs,
            LauncherCallback launcherCallback) {
        this(app, app.asApplicationLayout(), excludeCopyDirs, launcherCallback, true);
    }

    private AppImageBuilder(Package pkg, List<Path> excludeCopyDirs,
            LauncherCallback launcherCallback) {
        this(pkg.app(), pkg.asPackageApplicationLayout(), excludeCopyDirs,
                launcherCallback, false);
    }

    private static void copyRecursive(Path srcDir, Path dstDir,
            List<Path> excludeDirs) throws IOException {
        srcDir = srcDir.toAbsolutePath();

        List<Path> excludes = new ArrayList<>();

        for (var path : excludeDirs) {
            if (Files.isDirectory(path)) {
                if (path.startsWith(srcDir) && !Files.isSameFile(path, srcDir)) {
                    excludes.add(path);
                }
            }
        }

        FileUtils.copyRecursive(srcDir, dstDir.toAbsolutePath(), excludes);
    }

    void execute(BuildEnv env) throws IOException, PackagerException {
        var resolvedAppLayout = appLayout.resolveAt(env.appImageDir());

        app.runtimeBuilder().createRuntime(resolvedAppLayout);
        if (app.isRuntime()) {
            return;
        }

        var excludeCandidates = Stream.concat(
                excludeCopyDirs.stream(),
                Stream.of(env.buildRoot(), env.appImageDir())
        ).map(Path::toAbsolutePath).toList();

        if (app.srcDir() != null) {
            copyRecursive(app.srcDir(), resolvedAppLayout.appDirectory(), excludeCandidates);
        }

        for (var srcDir : Optional.ofNullable(app.contentDirs()).orElseGet(List::of)) {
            copyRecursive(srcDir,
                    resolvedAppLayout.contentDirectory().resolve(srcDir.getFileName()),
                    excludeCandidates);
        }

        if (withAppImageFile) {
            new AppImageFile2(app).save(resolvedAppLayout);
        }

        for (var launcher : app.launchers()) {
            // Create corresponding .cfg file
            new CfgFile(app, launcher).create(appLayout, resolvedAppLayout);

            // Copy executable to launchers folder
            Path executableFile = resolvedAppLayout.launchersDirectory().resolve(
                    launcher.executableNameWithSuffix());
            try (var in = launcher.executableResource()) {
                Files.createDirectories(executableFile.getParent());
                Files.copy(in, executableFile);
            }

            if (launcherCallback != null) {
                launcherCallback.onLauncher(app, new LauncherContext(launcher,
                        env, resolvedAppLayout, executableFile));
            }

            executableFile.toFile().setExecutable(true);
        }
    }

    static OverridableResource createLauncherIconResource(Application app,
            Launcher launcher,
            Function<String, OverridableResource> resourceSupplier) {
        final String defaultIconName = launcher.defaultIconResourceName();
        final String resourcePublicName = launcher.executableName() + PathUtils.getSuffix(Path.of(
                defaultIconName));

        var iconType = IconType.getLauncherIconType(launcher.icon());
        if (iconType == IconType.NO_ICON) {
            return null;
        }

        OverridableResource resource = resourceSupplier.apply(defaultIconName)
                .setCategory("icon")
                .setExternal(launcher.icon())
                .setPublicName(resourcePublicName);

        if (iconType == IconType.DEFAULT_OR_RESOURCEDIR_ICON && app.mainLauncher() != launcher) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (toSupplier(() -> resource.saveToFile(nullPath)).get() != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                return createLauncherIconResource(app, app.mainLauncher(),
                        resourceSupplier).setLogPublicName(resourcePublicName);
            }
        }

        return resource;
    }

    static interface LauncherCallback {
        default public void onLauncher(Application app, LauncherContext ctx)
                throws IOException, PackagerException {
            var iconResource = createLauncherIconResource(app, ctx.launcher,
                    ctx.env::createResource);
            if (iconResource != null) {
                onLauncher(app, ctx, iconResource);
            }
        }

        default public void onLauncher(Application app, LauncherContext ctx,
                OverridableResource launcherIcon) throws IOException, PackagerException {
        }
    }

    static record LauncherContext(Launcher launcher, BuildEnv env,
            ApplicationLayout resolvedAppLayout, Path launcherExecutable) {
    }

    private enum IconType {
        DEFAULT_OR_RESOURCEDIR_ICON, CUSTOM_ICON, NO_ICON;

        public static IconType getLauncherIconType(Path iconPath) {
            if (iconPath == null) {
                return DEFAULT_OR_RESOURCEDIR_ICON;
            }
            if (iconPath.toFile().getName().isEmpty()) {
                return NO_ICON;
            }
            return CUSTOM_ICON;
        }
    }

    private final boolean withAppImageFile;
    private final Application app;
    private final ApplicationLayout appLayout;
    private final List<Path> excludeCopyDirs;
    private final LauncherCallback launcherCallback;
}
