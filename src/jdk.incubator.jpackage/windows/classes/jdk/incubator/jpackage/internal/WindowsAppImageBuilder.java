/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static jdk.incubator.jpackage.internal.OverridableResource.createResource;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public class WindowsAppImageBuilder extends AbstractAppImageBuilder {

    static {
        System.loadLibrary("jpackage");
    }

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.WinResources");

    private final static String LIBRARY_NAME = "applauncher.dll";
    private final static String REDIST_MSVCR = "vcruntimeVS_VER.dll";
    private final static String REDIST_MSVCP = "msvcpVS_VER.dll";

    private final static String TEMPLATE_APP_ICON ="java48.ico";

    private static final String EXECUTABLE_PROPERTIES_TEMPLATE =
            "WinLauncher.template";

    private final Path root;
    private final Path appDir;
    private final Path appModsDir;
    private final Path runtimeDir;
    private final Path mdir;
    private final Path binDir;

    public static final BundlerParamInfo<Boolean> REBRAND_EXECUTABLE =
            new WindowsBundlerParam<>(
            "win.launcher.rebrand",
            Boolean.class,
            params -> Boolean.TRUE,
            (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<File> ICON_ICO =
            new StandardBundlerParam<>(
            "icon.ico",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".ico")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-ico"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final StandardBundlerParam<Boolean> CONSOLE_HINT =
            new WindowsBundlerParam<>(
            Arguments.CLIOptions.WIN_CONSOLE_HINT.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null
            || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    public WindowsAppImageBuilder(Map<String, Object> params, Path imageOutDir)
            throws IOException {
        super(params,
                imageOutDir.resolve(APP_NAME.fetchFrom(params) + "/runtime"));

        Objects.requireNonNull(imageOutDir);

        this.root = imageOutDir.resolve(APP_NAME.fetchFrom(params));
        this.appDir = root.resolve("app");
        this.appModsDir = appDir.resolve("mods");
        this.runtimeDir = root.resolve("runtime");
        this.mdir = runtimeDir.resolve("lib");
        this.binDir = root;
        Files.createDirectories(appDir);
        Files.createDirectories(runtimeDir);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    private static String getLauncherName(Map<String, ? super Object> params) {
        return APP_NAME.fetchFrom(params) + ".exe";
    }

    // Returns launcher resource name for launcher we need to use.
    public static String getLauncherResourceName(
            Map<String, ? super Object> params) {
        if (CONSOLE_HINT.fetchFrom(params)) {
            return "jpackageapplauncher.exe";
        } else {
            return "jpackageapplauncherw.exe";
        }
    }

    public static String getLauncherCfgName(
            Map<String, ? super Object> params) {
        return "app/" + APP_NAME.fetchFrom(params) +".cfg";
    }

    private File getConfig_ExecutableProperties(
           Map<String, ? super Object> params) {
        return new File(getConfigRoot(params),
                APP_NAME.fetchFrom(params) + ".properties");
    }

    File getConfigRoot(Map<String, ? super Object> params) {
        return CONFIG_ROOT.fetchFrom(params);
    }

    @Override
    public Path getAppDir() {
        return appDir;
    }

    @Override
    public Path getAppModsDir() {
        return appModsDir;
    }

    @Override
    public void prepareApplicationFiles(Map<String, ? super Object> params)
            throws IOException {
        try {
            IOUtils.writableOutputDir(root);
            IOUtils.writableOutputDir(binDir);
        } catch (PackagerException pe) {
            throw new RuntimeException(pe);
        }
        AppImageFile.save(root, params);

        // create the .exe launchers
        createLauncherForEntryPoint(params, null);

        // copy the jars
        copyApplication(params);

        // copy in the needed libraries
        try (InputStream is_lib = getResourceAsStream(LIBRARY_NAME)) {
            Files.copy(is_lib, binDir.resolve(LIBRARY_NAME));
        }

        copyMSVCDLLs();

        // create the additional launcher(s), if any
        List<Map<String, ? super Object>> entryPoints =
                StandardBundlerParam.ADD_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            createLauncherForEntryPoint(AddLauncherArguments.merge(params,
                    entryPoint, ICON.getID(), ICON_ICO.getID()), params);
        }
    }

    @Override
    public void prepareJreFiles(Map<String, ? super Object> params)
        throws IOException {}

    private void copyMSVCDLLs() throws IOException {
        AtomicReference<IOException> ioe = new AtomicReference<>();
        try (Stream<Path> files = Files.list(runtimeDir.resolve("bin"))) {
            files.filter(p -> Pattern.matches(
                    "^(vcruntime|msvcp|msvcr|ucrtbase|api-ms-win-).*\\.dll$",
                    p.toFile().getName().toLowerCase()))
                 .forEach(p -> {
                    try {
                        Files.copy(p, binDir.resolve((p.toFile().getName())));
                    } catch (IOException e) {
                        ioe.set(e);
                    }
                });
        }

        IOException e = ioe.get();
        if (e != null) {
            throw e;
        }
    }

    private void validateValueAndPut(
            Map<String, String> data, String key,
            BundlerParamInfo<String> param,
            Map<String, ? super Object> params) {
        String value = param.fetchFrom(params);
        if (value.contains("\r") || value.contains("\n")) {
            Log.error("Configuration Parameter " + param.getID()
                    + " contains multiple lines of text, ignore it");
            data.put(key, "");
            return;
        }
        data.put(key, value);
    }

    protected void prepareExecutableProperties(
           Map<String, ? super Object> params) throws IOException {

        Map<String, String> data = new HashMap<>();

        // mapping Java parameters in strings for version resource
        validateValueAndPut(data, "COMPANY_NAME", VENDOR, params);
        validateValueAndPut(data, "FILE_DESCRIPTION", DESCRIPTION, params);
        validateValueAndPut(data, "FILE_VERSION", VERSION, params);
        data.put("INTERNAL_NAME", getLauncherName(params));
        validateValueAndPut(data, "LEGAL_COPYRIGHT", COPYRIGHT, params);
        data.put("ORIGINAL_FILENAME", getLauncherName(params));
        validateValueAndPut(data, "PRODUCT_NAME", APP_NAME, params);
        validateValueAndPut(data, "PRODUCT_VERSION", VERSION, params);

        createResource(EXECUTABLE_PROPERTIES_TEMPLATE, params)
                .setCategory(I18N.getString("resource.executable-properties-template"))
                .setSubstitutionData(data)
                .saveToFile(getConfig_ExecutableProperties(params));
    }

    private void createLauncherForEntryPoint(Map<String, ? super Object> params,
            Map<String, ? super Object> mainParams) throws IOException {

        var iconResource = createIconResource(TEMPLATE_APP_ICON, ICON_ICO, params,
                mainParams);
        Path iconTarget = null;
        if (iconResource != null) {
            iconTarget = binDir.resolve(APP_NAME.fetchFrom(params) + ".ico");
            if (null == iconResource.saveToFile(iconTarget)) {
                iconTarget = null;
            }
        }

        writeCfgFile(params, root.resolve(
                getLauncherCfgName(params)).toFile());

        prepareExecutableProperties(params);

        // Copy executable to bin folder
        Path executableFile = binDir.resolve(getLauncherName(params));

        try (InputStream is_launcher =
                getResourceAsStream(getLauncherResourceName(params))) {
            writeEntry(is_launcher, executableFile);
        }

        File launcher = executableFile.toFile();
        launcher.setWritable(true, true);

        // Update branding of EXE file
        if (REBRAND_EXECUTABLE.fetchFrom(params)) {
            try {
                String tempDirectory = WindowsDefender.getUserTempDirectory();
                if (Arguments.CLIOptions.context().userProvidedBuildRoot) {
                    tempDirectory =
                            TEMP_ROOT.fetchFrom(params).getAbsolutePath();
                }
                if (WindowsDefender.isThereAPotentialWindowsDefenderIssue(
                        tempDirectory)) {
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.potential.windows.defender.issue"),
                            tempDirectory));
                }

                launcher.setWritable(true);

                if (iconTarget != null) {
                    iconSwap(iconTarget.toAbsolutePath().toString(),
                            launcher.getAbsolutePath());
                }

                File executableProperties =
                        getConfig_ExecutableProperties(params);

                if (executableProperties.exists()) {
                    if (versionSwap(executableProperties.getAbsolutePath(),
                            launcher.getAbsolutePath()) != 0) {
                        throw new RuntimeException(MessageFormat.format(
                                I18N.getString("error.version-swap"),
                                executableProperties.getAbsolutePath()));
                    }
                }
            } finally {
                executableFile.toFile().setExecutable(true);
                executableFile.toFile().setReadOnly();
            }
        }
    }

    private void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        List<RelativeFileSet> appResourcesList =
                APP_RESOURCES_LIST.fetchFrom(params);
        if (appResourcesList == null) {
            throw new RuntimeException("Null app resources?");
        }
        for (RelativeFileSet appResources : appResourcesList) {
            if (appResources == null) {
                throw new RuntimeException("Null app resources?");
            }
            File srcdir = appResources.getBaseDirectory();
            for (String fname : appResources.getIncludedFiles()) {
                copyEntry(appDir, srcdir, fname);
            }
        }
    }

    private static native int iconSwap(String iconTarget, String launcher);

    private static native int versionSwap(String executableProperties,
            String launcher);

}
