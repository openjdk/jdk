/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static jdk.incubator.jpackage.internal.LinuxAppBundler.ICON_PNG;
import static jdk.incubator.jpackage.internal.OverridableResource.createResource;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public class LinuxAppImageBuilder extends AbstractAppImageBuilder {

    final static String DEFAULT_ICON = "java32.png";

    private final ApplicationLayout appLayout;

    private static ApplicationLayout createAppLayout(Map<String, Object> params,
            Path imageOutDir) {
        return ApplicationLayout.linuxAppImage().resolveAt(
                imageOutDir.resolve(APP_NAME.fetchFrom(params)));
    }

    public LinuxAppImageBuilder(Map<String, Object> params, Path imageOutDir)
            throws IOException {
        super(params, createAppLayout(params, imageOutDir).runtimeDirectory());

        appLayout = createAppLayout(params, imageOutDir);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    public static String getLauncherName(Map<String, ? super Object> params) {
        return APP_NAME.fetchFrom(params);
    }

    private Path getLauncherCfgPath(Map<String, ? super Object> params) {
        return appLayout.appDirectory().resolve(
                APP_NAME.fetchFrom(params) + ".cfg");
    }

    @Override
    public Path getAppDir() {
        return appLayout.appDirectory();
    }

    @Override
    public Path getAppModsDir() {
        return appLayout.appModsDirectory();
    }

    @Override
    protected String getCfgAppDir() {
        return Path.of("$ROOTDIR").resolve(
                ApplicationLayout.linuxAppImage().appDirectory()).toString()
                + File.separator;
    }

    @Override
    protected String getCfgRuntimeDir() {
        return Path.of("$ROOTDIR").resolve(
              ApplicationLayout.linuxAppImage().runtimeDirectory()).toString();
    }

    @Override
    public void prepareApplicationFiles(Map<String, ? super Object> params)
            throws IOException {
        appLayout.roots().stream().forEach(dir -> {
            try {
                IOUtils.writableOutputDir(dir);
            } catch (PackagerException pe) {
                throw new RuntimeException(pe);
            }
        });

        // create the primary launcher
        createLauncherForEntryPoint(params, null);

        // create the additional launchers, if any
        List<Map<String, ? super Object>> entryPoints
                = StandardBundlerParam.ADD_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            createLauncherForEntryPoint(AddLauncherArguments.merge(params,
                    entryPoint, ICON.getID(), ICON_PNG.getID()), params);
        }

        // Copy class path entries to Java folder
        copyApplication(params);
    }

    @Override
    public void prepareJreFiles(Map<String, ? super Object> params)
            throws IOException {}

    private void createLauncherForEntryPoint(Map<String, ? super Object> params,
            Map<String, ? super Object> mainParams) throws IOException {
        // Copy executable to launchers folder
        Path executableFile = appLayout.launchersDirectory().resolve(getLauncherName(params));
        try (InputStream is_launcher =
                getResourceAsStream("jpackageapplauncher")) {
            writeEntry(is_launcher, executableFile);
        }

        executableFile.toFile().setExecutable(true, false);
        executableFile.toFile().setWritable(true, true);

        writeCfgFile(params, getLauncherCfgPath(params).toFile());

        var iconResource = createIconResource(DEFAULT_ICON, ICON_PNG, params,
                mainParams);
        if (iconResource != null) {
            Path iconTarget = appLayout.destktopIntegrationDirectory().resolve(
                    APP_NAME.fetchFrom(params) + IOUtils.getSuffix(Path.of(
                    DEFAULT_ICON)));
            iconResource.saveToFile(iconTarget);
        }
    }

    private void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        for (RelativeFileSet appResources :
                APP_RESOURCES_LIST.fetchFrom(params)) {
            if (appResources == null) {
                throw new RuntimeException("Null app resources?");
            }
            File srcdir = appResources.getBaseDirectory();
            for (String fname : appResources.getIncludedFiles()) {
                copyEntry(appLayout.appDirectory(), srcdir, fname);
            }
        }
    }

}
