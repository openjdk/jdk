/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.SOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.APP_CONTENT;
import static jdk.jpackage.internal.StandardBundlerParam.OUTPUT_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.TEMP_ROOT;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.FileUtils;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

/*
 * AbstractAppImageBuilder
 *     This is sub-classed by each of the platform dependent AppImageBuilder
 * classes, and contains resource processing code common to all platforms.
 */

public abstract class AbstractAppImageBuilder {

    private final Path root;
    protected final ApplicationLayout appLayout;

    public AbstractAppImageBuilder(Path root) {
        this.root = root;
        appLayout = ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT.resolveAt(root);
    }

    public InputStream getResourceAsStream(String name) {
        return ResourceLocator.class.getResourceAsStream(name);
    }

    public abstract void prepareApplicationFiles(
            Map<String, ? super Object> params) throws IOException;

    protected void writeCfgFile(Map<String, ? super Object> params) throws
            IOException {
        new CfgFile(new Application.Unsupported() {
            @Override
            public String version() {
                return VERSION.fetchFrom(params);
            }
        }, new Launcher.Unsupported() {
            @Override
            public Optional<LauncherStartupInfo> startupInfo() {
                return toSupplier(() -> new LauncherFromParams().create(params).startupInfo()).get();
            }

            @Override
            public String name() {
                return APP_NAME.fetchFrom(params);
            }
        }).create(ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT, appLayout);
    }

    protected void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        Path inputPath = SOURCE_DIR.fetchFrom(params);
        if (inputPath != null) {
            inputPath = inputPath.toAbsolutePath();

            List<Path> excludes = new ArrayList<>();

            for (var path : List.of(TEMP_ROOT.fetchFrom(params), OUTPUT_DIR.fetchFrom(params), root)) {
                if (Files.isDirectory(path)) {
                    path = path.toAbsolutePath();
                    if (path.startsWith(inputPath) && !Files.isSameFile(path, inputPath)) {
                        excludes.add(path);
                    }
                }
            }

            FileUtils.copyRecursive(inputPath,
                    appLayout.appDirectory().toAbsolutePath(), excludes);
        }

        AppImageFile.save(root, params);

        List<Path> items = APP_CONTENT.fetchFrom(params);
        for (Path item : items) {
            FileUtils.copyRecursive(item,
                appLayout.contentDirectory().resolve(item.getFileName()));
        }
    }
}
