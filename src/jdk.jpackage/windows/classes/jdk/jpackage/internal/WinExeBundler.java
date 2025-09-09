/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.WinExePackage;

@SuppressWarnings("restricted")
public class WinExeBundler extends AbstractBundler {

    static {
        System.loadLibrary("jpackage");
    }

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

    @Override
    public Path execute(Map<String, ? super Object> params, Path outdir)
            throws PackagerException {

        // Order is important!
        var pkg = WinFromParams.MSI_PACKAGE.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        IOUtils.writableOutputDir(outdir);

        Path msiDir = env.buildRoot().resolve("msi");
        toRunnable(() -> Files.createDirectories(msiDir)).run();

        // Write msi to temporary directory.
        Path msi = msiBundler.execute(params, msiDir);

        try {
            new ScriptRunner()
            .setDirectory(msi.getParent())
            .setResourceCategoryId("resource.post-msi-script")
            .setScriptNameSuffix("post-msi")
            .setEnvironmentVariable("JpMsiFile", msi.toAbsolutePath().toString())
            .run(env, pkg.packageName());

            var exePkg = new WinExePackageBuilder(pkg).icon(ICON.fetchFrom(params)).create();
            return buildEXE(env, exePkg, msi, outdir);
        } catch (IOException|ConfigException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private Path buildEXE(BuildEnv env, WinExePackage pkg, Path msi,
            Path outdir) throws IOException {

        Log.verbose(I18N.format("message.outputting-to-location", outdir.toAbsolutePath()));

        // Copy template msi wrapper next to msi file
        final Path exePath = msi.getParent().resolve(pkg.packageFileNameWithSuffix());

        env.createResource("msiwrapper.exe")
                .setCategory(I18N.getString("resource.installer-exe"))
                .setPublicName("installer.exe")
                .saveToFile(exePath);

        new ExecutableRebrander(pkg, env::createResource, resourceLock -> {
            // Embed msi in msi wrapper exe.
            embedMSI(resourceLock, msi.toAbsolutePath().toString());
        }).execute(env, exePath, pkg.icon());

        Path dstExePath = outdir.resolve(exePath.getFileName());

        Files.copy(exePath, dstExePath, StandardCopyOption.REPLACE_EXISTING);

        dstExePath.toFile().setExecutable(true);

        Log.verbose(I18N.format("message.output-location", outdir.toAbsolutePath()));

        return dstExePath;
    }

    private final WinMsiBundler msiBundler = new WinMsiBundler();

    private static native int embedMSI(long resourceLock, String msiPath);
}
