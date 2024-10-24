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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


final class AppImageBuilder {

    static final class Builder {

        Builder launcherCallback(LauncherCallback v) {
            launcherCallback = v;
            return this;
        }

        AppImageBuilder create(Application app) {
            return new AppImageBuilder(app, app.appLayout(), launcherCallback);
        }

        AppImageBuilder create(Package pkg) {
            return new AppImageBuilder(pkg, launcherCallback);
        }

        private LauncherCallback launcherCallback;
    }

    static Builder build() {
        return new Builder();
    }

    private AppImageBuilder(Application app, ApplicationLayout appLayout, LauncherCallback launcherCallback) {
        this.app = app;
        this.appLayout = appLayout;
        this.withAppImageFile = true;
        this.launcherCallback = launcherCallback;
    }

    private AppImageBuilder(Package pkg, LauncherCallback launcherCallback) {
        this(pkg.app(), pkg.packageLayout(), launcherCallback);
    }

    private static void copyRecursive(Path srcDir, Path dstDir, Workshop workshop) throws IOException {
        srcDir = srcDir.toAbsolutePath();

        List<Path> excludes = new ArrayList<>();

        for (var path : List.of(workshop.buildRoot(), workshop.appImageDir())) {
            if (Files.isDirectory(path)) {
                path = path.toAbsolutePath();
                if (path.startsWith(srcDir) && !Files.isSameFile(path, srcDir)) {
                    excludes.add(path);
                }
            }
        }

        IOUtils.copyRecursive(srcDir, dstDir.toAbsolutePath() /*, excludes */);
    }

    void execute(Workshop workshop) throws IOException, PackagerException {
        var resolvedAppLayout = appLayout.resolveAt(workshop.appImageDir());

        app.runtimeBuilder().createRuntime(resolvedAppLayout);
        if (app.isRuntime()) {
            return;
        }

        if (app.srcDir() != null) {
            copyRecursive(app.srcDir(), resolvedAppLayout.appDirectory(), workshop);
        }

        for (var srcDir : Optional.ofNullable(app.contentDirs()).orElseGet(List::of)) {
            copyRecursive(srcDir,
                    resolvedAppLayout.contentDirectory().resolve(srcDir.getFileName()),
                    workshop);
        }

        if (withAppImageFile) {
            new AppImageFile2(app).save(workshop.appImageDir());
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
                        workshop, resolvedAppLayout, executableFile));
            }

            executableFile.toFile().setExecutable(true);
        }
    }

    static interface LauncherCallback {
        default public void onLauncher(Application app, LauncherContext ctx) throws IOException, PackagerException {
            var iconResource = app.createLauncherIconResource(ctx.launcher,
                    ctx.workshop::createResource);
            if (iconResource != null) {
                onLauncher(app, ctx, iconResource);
            }
        }

        default public void onLauncher(Application app, LauncherContext ctx,
                OverridableResource launcherIcon) throws IOException, PackagerException {
        }
    }

    static record LauncherContext(Launcher launcher, Workshop workshop,
            ApplicationLayout resolvedAppLayout, Path launcherExecutable) {
    }

    private final boolean withAppImageFile;
    private final Application app;
    private final ApplicationLayout appLayout;
    private final LauncherCallback launcherCallback;
}
