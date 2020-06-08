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
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import static jdk.incubator.jpackage.internal.OverridableResource.createResource;
import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

import jdk.incubator.jpackage.internal.resources.ResourceLocator;


/*
 * AbstractAppImageBuilder
 *     This is sub-classed by each of the platform dependent AppImageBuilder
 * classes, and contains resource processing code common to all platforms.
 */

public abstract class AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MainResources");

    private final Path root;

    public AbstractAppImageBuilder(Map<String, Object> unused, Path root) {
        this.root = root;
    }

    public InputStream getResourceAsStream(String name) {
        return ResourceLocator.class.getResourceAsStream(name);
    }

    public abstract void prepareApplicationFiles(
            Map<String, ? super Object> params) throws IOException;
    public abstract void prepareJreFiles(
            Map<String, ? super Object> params) throws IOException;
    public abstract Path getAppDir();
    public abstract Path getAppModsDir();

    public Path getRuntimeRoot() {
        return this.root;
    }

    protected void copyEntry(Path appDir, File srcdir, String fname)
            throws IOException {
        Path dest = appDir.resolve(fname);
        Files.createDirectories(dest.getParent());
        File src = new File(srcdir, fname);
        if (src.isDirectory()) {
            IOUtils.copyRecursive(src.toPath(), dest);
        } else {
            Files.copy(src.toPath(), dest);
        }
    }

    public void writeCfgFile(Map<String, ? super Object> params,
            File cfgFileName) throws IOException {
        cfgFileName.getParentFile().mkdirs();
        cfgFileName.delete();

        LauncherData launcherData = StandardBundlerParam.LAUNCHER_DATA.fetchFrom(
                params);

        try (PrintStream out = new PrintStream(cfgFileName)) {

            out.println("[Application]");
            out.println("app.name=" + APP_NAME.fetchFrom(params));
            out.println("app.version=" + VERSION.fetchFrom(params));
            out.println("app.runtime=" + getCfgRuntimeDir());

            for (var path : launcherData.classPath()) {
                out.println("app.classpath=" + getCfgAppDir()
                        + path.toString().replace("\\", "/"));
            }

            // The main app is required to be a jar, modular or unnamed.
            if (launcherData.isModular()) {
                out.println("app.mainmodule=" + launcherData.moduleName() + "/"
                        + launcherData.qualifiedClassName());
            } else {
                // If the app is contained in an unnamed jar then launch it the
                // legacy way and the main class string must be
                // of the format com/foo/Main
                if (launcherData.mainJarName() != null) {
                    out.println("app.classpath=" + getCfgAppDir()
                            + launcherData.mainJarName().toString());
                }

                out.println("app.mainclass=" + launcherData.qualifiedClassName());
            }

            out.println();
            out.println("[JavaOptions]");
            List<String> jvmargs = JAVA_OPTIONS.fetchFrom(params);
            for (String arg : jvmargs) {
                out.println("java-options=" + arg);
            }
            Path modsDir = getAppModsDir();

            if (modsDir != null && modsDir.toFile().exists()) {
                out.println("java-options=" + "--module-path");
                out.println("java-options=" + getCfgAppDir().replace("\\","/") + "mods");
            }

            out.println();
            out.println("[ArgOptions]");
            List<String> args = ARGUMENTS.fetchFrom(params);
            for (String arg : args) {
                out.println("arguments=" + arg);
            }
        }
    }

    protected void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        Path inputPath = StandardBundlerParam.SOURCE_DIR.fetchFrom(params);
        if (inputPath != null) {
            IOUtils.copyRecursive(SOURCE_DIR.fetchFrom(params), getAppDir());
        }
    }

    File getRuntimeImageDir(File runtimeImageTop) {
        return runtimeImageTop;
    }

    protected String getCfgAppDir() {
        return "$ROOTDIR" + File.separator
                + getAppDir().getFileName() + File.separator;
    }

    protected String getCfgRuntimeDir() {
        return "$ROOTDIR" + File.separator + "runtime";
    }

    String getCfgClassPath(String classpath) {
        String cfgAppDir = getCfgAppDir();

        StringBuilder sb = new StringBuilder();
        for (String path : classpath.split("[:;]")) {
            if (path.length() > 0) {
                sb.append(cfgAppDir);
                sb.append(path);
                sb.append(File.pathSeparator);
            }
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public static OverridableResource createIconResource(String defaultIconName,
            BundlerParamInfo<File> iconParam, Map<String, ? super Object> params,
            Map<String, ? super Object> mainParams) throws IOException {

        if (mainParams != null) {
            params = AddLauncherArguments.merge(mainParams, params, ICON.getID(),
                    iconParam.getID());
        }

        final String resourcePublicName = APP_NAME.fetchFrom(params)
                + IOUtils.getSuffix(Path.of(defaultIconName));

        IconType iconType = getLauncherIconType(params);
        if (iconType == IconType.NoIcon) {
            return null;
        }

        OverridableResource resource = createResource(defaultIconName, params)
                .setCategory("icon")
                .setExternal(iconParam.fetchFrom(params))
                .setPublicName(resourcePublicName);

        if (iconType == IconType.DefaultOrResourceDirIcon && mainParams != null) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (resource.saveToFile(nullPath)
                    != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                resource = createIconResource(defaultIconName, iconParam,
                        mainParams, null).setLogPublicName(resourcePublicName);
            }
        }

        return resource;
    }

    private enum IconType { DefaultOrResourceDirIcon, CustomIcon, NoIcon };

    private static IconType getLauncherIconType(Map<String, ? super Object> params) {
        File launcherIcon = ICON.fetchFrom(params);
        if (launcherIcon == null) {
            return IconType.DefaultOrResourceDirIcon;
        }

        if (launcherIcon.getName().isEmpty()) {
            return IconType.NoIcon;
        }

        return IconType.CustomIcon;
    }
}
