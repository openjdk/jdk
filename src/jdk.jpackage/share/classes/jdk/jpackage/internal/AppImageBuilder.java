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


class AppImageBuilder {

    AppImageBuilder(Application app) {
        this.app = app;
        this.appLayout = app.appLayout();
        this.withAppImageFile = true;
    }

    AppImageBuilder(Package pkg) {
        this.app = pkg.app();
        this.appLayout = pkg.packageLayout();
        this.withAppImageFile = false;
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

        copyRecursive(app.mainSrcDir(), resolvedAppLayout.appDirectory(), workshop);

        for (var srcDir : Optional.ofNullable(app.additionalSrcDirs()).orElseGet(List::of)) {
            copyRecursive(srcDir,
                    resolvedAppLayout.contentDirectory().resolve(srcDir.getFileName()),
                    workshop);
        }

        if (withAppImageFile) {
            new AppImageFile2(app).save(workshop.appImageDir());
        }

        for (var launcher : app.launchers()) {
            // Copy executable to launchers folder
            Path executableFile = resolvedAppLayout.launchersDirectory().resolve(launcher.executableName());
            try (var in = launcher.executableResource()) {
                Files.createDirectories(executableFile.getParent());
                Files.copy(in, executableFile);
            }

            var asFile = executableFile.toFile();

            asFile.setExecutable(true, false);

            // Create corresponding .cfg file
            new CfgFile(app, launcher).create(appLayout, resolvedAppLayout);
        }
    }

    private final boolean withAppImageFile;
    protected final Application app;
    protected final ApplicationLayout appLayout;
}
