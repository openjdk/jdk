/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.WinExePackage;

final record WinExePackager(BuildEnv env, WinExePackage pkg, Path outputDir, Path msiOutputDir) implements Consumer<PackagingPipeline.Builder> {

    WinExePackager {
        Objects.requireNonNull(env);
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(msiOutputDir);
    }

    enum ExePackageTaskID implements TaskID {
        RUN_POST_MSI_USER_SCRIPT,
        WRAP_MSI_IN_EXE
    }

    @Override
    public void accept(PackagingPipeline.Builder pipelineBuilder) {
        pipelineBuilder
                .task(ExePackageTaskID.RUN_POST_MSI_USER_SCRIPT)
                        .action(this::runPostMsiScript)
                        .addDependency(PackageTaskID.CREATE_PACKAGE_FILE)
                        .add()
                .task(ExePackageTaskID.WRAP_MSI_IN_EXE)
                        .action(this::wrapMsiInExe)
                        .addDependency(ExePackageTaskID.RUN_POST_MSI_USER_SCRIPT)
                        .addDependent(PrimaryTaskID.PACKAGE)
                        .add();
    }

    private Path msi() {
        return msiOutputDir.resolve(pkg.msiPackage().packageFileNameWithSuffix());
    }

    private void runPostMsiScript() throws IOException {
        new ScriptRunner()
        .setDirectory(msiOutputDir)
        .setResourceCategoryId("resource.post-msi-script")
        .setScriptNameSuffix("post-msi")
        .setEnvironmentVariable("JpMsiFile", msi().toAbsolutePath().toString())
        .run(env, pkg.msiPackage().packageName());
    }

    private void wrapMsiInExe() throws IOException {

        final var msi = msi();

        // Copy template msi wrapper next to msi file
        final Path exePath = msi.getParent().resolve(pkg.packageFileNameWithSuffix());

        env.createResource("msiwrapper.exe")
                .setCategory(I18N.getString("resource.installer-exe"))
                .setPublicName("installer.exe")
                .saveToFile(exePath);

        new ExecutableRebrander(pkg, env::createResource, resourceLock -> {
            // Embed msi in msi wrapper exe.
            WinExeBundler.embedMSI(resourceLock, msi.toAbsolutePath().toString());
        }).execute(env, exePath, pkg.icon());

        Path dstExePath = outputDir.resolve(exePath.getFileName());

        Files.createDirectories(dstExePath.getParent());
        Files.copy(exePath, dstExePath, StandardCopyOption.REPLACE_EXISTING);

        dstExePath.toFile().setExecutable(true);
    }
}
