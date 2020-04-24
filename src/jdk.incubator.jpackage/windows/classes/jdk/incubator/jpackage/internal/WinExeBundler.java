/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.*;

public class WinExeBundler extends AbstractBundler {

    static {
        System.loadLibrary("jpackage");
    }

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.WinResources");

    public static final BundlerParamInfo<WinAppBundler> APP_BUNDLER
            = new WindowsBundlerParam<>(
                    "win.app.bundler",
                    WinAppBundler.class,
                    params -> new WinAppBundler(),
                    null);

    public static final BundlerParamInfo<File> EXE_IMAGE_DIR
            = new WindowsBundlerParam<>(
                    "win.exe.imageDir",
                    File.class,
                    params -> {
                        File imagesRoot = IMAGES_ROOT.fetchFrom(params);
                        if (!imagesRoot.exists()) {
                            imagesRoot.mkdirs();
                        }
                        return new File(imagesRoot, "win-exe.image");
                    },
                    (s, p) -> null);

    private final static String EXE_WRAPPER_NAME = "msiwrapper.exe";

    @Override
    public String getName() {
        return I18N.getString("exe.bundler.name");
    }

    @Override
    public String getID() {
        return "exe";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean platformInstaller) {
        return msiBundler.supported(platformInstaller);
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        return msiBundler.validate(params);
    }

    public File bundle(Map<String, ? super Object> params, File outdir)
            throws PackagerException {

        IOUtils.writableOutputDir(outdir.toPath());

        File exeImageDir = EXE_IMAGE_DIR.fetchFrom(params);

        // Write msi to temporary directory.
        File msi = msiBundler.bundle(params, exeImageDir);

        try {
            new ScriptRunner()
            .setDirectory(msi.toPath().getParent())
            .setResourceCategoryId("resource.post-msi-script")
            .setScriptNameSuffix("post-msi")
            .setEnvironmentVariable("JpMsiFile", msi.getAbsolutePath().toString())
            .run(params);

            return buildEXE(params, msi, outdir);
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private File buildEXE(Map<String, ? super Object> params, File msi,
            File outdir) throws IOException {

        Log.verbose(MessageFormat.format(
                I18N.getString("message.outputting-to-location"),
                outdir.getAbsolutePath()));

        // Copy template msi wrapper next to msi file
        final Path exePath = IOUtils.replaceSuffix(msi.toPath(), ".exe");
        try (InputStream is = OverridableResource.readDefault(EXE_WRAPPER_NAME)) {
            Files.copy(is, exePath);
        }

        new ExecutableRebrander().addAction((resourceLock) -> {
            // Embed msi in msi wrapper exe.
            embedMSI(resourceLock, msi.getAbsolutePath());
        }).rebrandInstaller(params, exePath);

        Path dstExePath = Paths.get(outdir.getAbsolutePath(),
                exePath.getFileName().toString());
        Files.deleteIfExists(dstExePath);

        Files.copy(exePath, dstExePath);

        Log.verbose(MessageFormat.format(
                I18N.getString("message.output-location"),
                outdir.getAbsolutePath()));

        return dstExePath.toFile();
    }

    private final WinMsiBundler msiBundler = new WinMsiBundler();

    private static native int embedMSI(long resourceLock, String msiPath);
}
