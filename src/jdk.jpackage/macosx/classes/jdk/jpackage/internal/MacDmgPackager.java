/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.PathUtils.normalizedAbsolutePathString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathGroup;

record MacDmgPackager(BuildEnv env, MacDmgPackage pkg, Path outputDir,
        MacDmgSystemEnvironment sysEnv) implements Consumer<PackagingPipeline.Builder> {

    MacDmgPackager {
        Objects.requireNonNull(env);
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(outputDir);
        Objects.requireNonNull(sysEnv);
    }

    @Override
    public void accept(PackagingPipeline.Builder pipelineBuilder) {
        pipelineBuilder
                .excludeDirFromCopying(outputDir)
                .task(DmgPackageTaskID.COPY_DMG_CONTENT)
                        .action(this::copyDmgContent)
                        .addDependent(PackageTaskID.CREATE_PACKAGE_FILE)
                        .add()
                .task(PackageTaskID.CREATE_CONFIG_FILES)
                        .action(this::prepareConfigFiles)
                        .add()
                .task(PackageTaskID.CREATE_PACKAGE_FILE)
                        .action(this::buildDMG)
                        .add();
    }

    enum DmgPackageTaskID implements TaskID {
        COPY_DMG_CONTENT
    }

    Path volumePath() {
        return dmgWorkdir().resolve(volumeName());
    }

    String volumeName() {
        return pkg.app().name();
    }

    String createVolumeUrlLocation() throws IOException {
        final var volumeParentDir = volumePath().getParent();
        Files.createDirectories(volumeParentDir);
        // The URL should end with '/' and it should be real path (no symbolic links).
        return volumeParentDir.toRealPath().resolve(volumePath().getFileName()).toUri().toString() + File.separator;
    }

    Path volumeScript() {
        return env.configDir().resolve(pkg.app().name() + "-dmg-setup.scpt");
    }

    Path volumeBackground() {
        return env.configDir().resolve(pkg.app().name() + "-background.tiff");
    }

    Path volumeIcon() {
        return env.configDir().resolve(pkg.app().name() + "-volume.icns");
    }

    Path licenseFile() {
        return env.configDir().resolve(pkg.app().name() + "-license.plist");
    }

    Path protoDmg() {
        return dmgWorkdir().resolve("proto.dmg");
    }

    Path protoCopyDmg() {
        return dmgWorkdir().resolve("proto-copy.dmg");
    }

    Path bgImageFileInMountedDmg() {
        return volumePath().resolve(".background/background.tiff");
    }

    private Path dmgWorkdir() {
        return env.buildRoot().resolve("dmg-workdir");
    }

    private void copyDmgContent() throws IOException {
        final var srcFolder = env.appImageDir();
        for (Path path : pkg.content()) {
            FileUtils.copyRecursive(path, srcFolder.resolve(path.getFileName()));
        }
    }

    private void prepareDMGSetupScript() throws IOException {
        Path dmgSetup = volumeScript();
        Log.verbose(MessageFormat.format(
                I18N.getString("message.preparing-dmg-setup"),
                dmgSetup.toAbsolutePath().toString()));

        // Prepare DMG setup script
        Map<String, String> data = new HashMap<>();

        // We need to use URL for DMG to find it. We cannot use volume name, since
        // user might have open DMG with same volume name already.
        data.put("DEPLOY_VOLUME_URL", createVolumeUrlLocation());

        // Full path to background image, so we can find it.
        data.put("DEPLOY_BG_FILE", bgImageFileInMountedDmg().toAbsolutePath().toString());

        data.put("DEPLOY_VOLUME_PATH", volumePath().toAbsolutePath().toString());
        data.put("DEPLOY_APPLICATION_NAME", pkg.app().name());

        String targetItem = pkg.relativeInstallDir().getFileName().toString();
        data.put("DEPLOY_TARGET", targetItem);

        data.put("DEPLOY_INSTALL_LOCATION", pkg.installDir().getParent().toString());

        // "DEPLOY_INSTALL_LOCATION_DISPLAY_NAME" is the label for the default destination directory
        // for DMG bundle on the right side from the "copy" arrow in the dialog
        // that pops up when user clicks on a .dmg file.
        data.put("DEPLOY_INSTALL_LOCATION_DISPLAY_NAME", getInstallDirDisplayName());

        env.createResource(DEFAULT_DMG_SETUP_SCRIPT)
                .setCategory(I18N.getString("resource.dmg-setup-script"))
                .setSubstitutionData(data)
                .saveToFile(dmgSetup);
    }

    private void prepareLicense() throws IOException {
        final var licFile = pkg.licenseFile();
        if (licFile.isEmpty()) {
            return;
        }

        byte[] licenseContentOriginal =
                Files.readAllBytes(licFile.orElseThrow());
        String licenseInBase64 =
                Base64.getEncoder().encodeToString(licenseContentOriginal);

        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_LICENSE_TEXT", licenseInBase64);

        env.createResource(DEFAULT_LICENSE_PLIST)
                .setCategory(I18N.getString("resource.license-setup"))
                .setSubstitutionData(data)
                .saveToFile(licenseFile());
    }

    private void prepareConfigFiles() throws IOException {

        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                    .setCategory(I18N.getString("resource.dmg-background"))
                    .saveToFile(volumeBackground());

        env.createResource(TEMPLATE_BUNDLE_ICON)
                .setCategory(I18N.getString("resource.volume-icon"))
                .setExternal(pkg.icon().orElse(null))
                .saveToFile(volumeIcon());

        prepareLicense();

        prepareDMGSetupScript();
    }

    private String getInstallDirDisplayName() {
        final var defaultInstallDir = new PackageBuilder(pkg.app(), pkg.type()).defaultInstallDir().orElseThrow();
        if (defaultInstallDir.equals(pkg.installDir())) {
            // Return "Applications" for "/Applications/foo.app"
            return defaultInstallDir.getParent().getFileName().toString();
        } else {
            // If we returning full path we need to replace '/' with ':'.
            // In this case macOS will display link name as "/Users/USER/MyCompany/MyApp".
            return pkg.installDir().getParent().toString().replace('/', ':');
        }
    }

    private void buildDMG() throws IOException {
        boolean copyAppImage = false;

        Path protoDMG = protoDmg();
        Path finalDMG = outputDir.resolve(pkg.packageFileNameWithSuffix());

        Path srcFolder = env.appImageDir();

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.creating-dmg-file"), finalDMG.toAbsolutePath()));

        try {
            Files.deleteIfExists(finalDMG);
        } catch (IOException ex) {
            throw new IOException(MessageFormat.format(I18N.getString(
                    "message.dmg-cannot-be-overwritten"),
                    finalDMG.toAbsolutePath()));
        }

        Files.createDirectories(protoDMG.getParent());
        Files.createDirectories(finalDMG.getParent());

        String hdiUtilVerbosityFlag = env.verbose() ?
                "-verbose" : "-quiet";

        // create temp image
        ProcessBuilder pb = new ProcessBuilder(
                sysEnv.hdiutil().toString(),
                "create",
                hdiUtilVerbosityFlag,
                "-srcfolder", normalizedAbsolutePathString(srcFolder),
                "-volname", volumeName(),
                "-ov", normalizedAbsolutePathString(protoDMG),
                "-fs", "HFS+",
                "-format", "UDRW");
        try {
            IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
        } catch (IOException ex) {
            Log.verbose(ex); // Log exception

            // Creating DMG from entire app image failed, so lets try to create empty
            // DMG and copy files manually. See JDK-8248059.
            copyAppImage = true;

            long size = new PathGroup(Map.of(new Object(), srcFolder)).sizeInBytes();
            size += 50 * 1024 * 1024; // Add extra 50 megabytes. Actually DMG size will
            // not be bigger, but it will able to hold additional 50 megabytes of data.
            // We need extra room for icons and background image. When we providing
            // actual files to hdiutil, it will create DMG with ~50 megabytes extra room.
            pb = new ProcessBuilder(
                sysEnv.hdiutil().toString(),
                "create",
                hdiUtilVerbosityFlag,
                "-size", String.valueOf(size),
                "-volname", volumeName(),
                "-ov", normalizedAbsolutePathString(protoDMG),
                "-fs", "HFS+");
            new RetryExecutor()
                .setMaxAttemptsCount(10)
                .setAttemptTimeoutMillis(3000)
                .setWriteOutputToFile(true)
                .execute(pb);
        }

        // mount temp image
        pb = new ProcessBuilder(
                sysEnv.hdiutil().toString(),
                "attach",
                normalizedAbsolutePathString(protoDMG),
                hdiUtilVerbosityFlag,
                "-mountroot", protoDMG.getParent().toString());
        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);

        final Path mountedVolume = volumePath();

        // Copy app image, since we did not create DMG with it, but instead we created
        // empty one.
        if (copyAppImage) {
            FileUtils.copyRecursive(srcFolder, mountedVolume);
        }

        try {
            // background image
            final var bgImageFile = bgImageFileInMountedDmg();
            Files.createDirectories(bgImageFile.getParent());
            IOUtils.copyFile(volumeBackground(), bgImageFile);

            // We will not consider setting background image and creating link
            // to install-dir in DMG as critical error, since it can fail in
            // headless environment.
            try {
                pb = new ProcessBuilder(sysEnv.osascript().toString(),
                        normalizedAbsolutePathString(volumeScript()));
                IOUtils.exec(pb, 180); // Wait 3 minutes. See JDK-8248248.
            } catch (IOException ex) {
                Log.verbose(ex);
            }

            // volume icon
            Path volumeIconFile = mountedVolume.resolve(".VolumeIcon.icns");
            IOUtils.copyFile(volumeIcon(), volumeIconFile);

            // Indicate that we want a custom icon
            // NB: attributes of the root directory are ignored
            // when creating the volume
            // Therefore we have to do this after we mount image
            if (sysEnv.setFileUtility().isPresent()) {
                //can not find utility => keep going without icon
                try {
                    volumeIconFile.toFile().setWritable(true);
                    // The "creator" attribute on a file is a legacy attribute
                    // but it seems Finder excepts these bytes to be
                    // "icnC" for the volume icon
                    // (might not work on Mac 10.13 with old XCode)
                    pb = new ProcessBuilder(
                            sysEnv.setFileUtility().orElseThrow().toString(),
                            "-c", "icnC",
                            normalizedAbsolutePathString(volumeIconFile));
                    IOUtils.exec(pb);
                    volumeIconFile.toFile().setReadOnly();

                    pb = new ProcessBuilder(
                            sysEnv.setFileUtility().orElseThrow().toString(),
                            "-a", "C",
                            normalizedAbsolutePathString(mountedVolume));
                    IOUtils.exec(pb);
                } catch (IOException ex) {
                    Log.error(ex.getMessage());
                    Log.verbose("Cannot enable custom icon using SetFile utility");
                }
            } else {
                Log.verbose(I18N.getString("message.setfile.dmg"));
            }

        } finally {
            // Detach the temporary image
            pb = new ProcessBuilder(
                    sysEnv.hdiutil().toString(),
                    "detach",
                    hdiUtilVerbosityFlag,
                    normalizedAbsolutePathString(mountedVolume));
            // "hdiutil detach" might not work right away due to resource busy error, so
            // repeat detach several times.
            RetryExecutor retryExecutor = new RetryExecutor();
            // Image can get detach even if we got resource busy error, so stop
            // trying to detach it if it is no longer attached.
            retryExecutor.setExecutorInitializer(exec -> {
                if (!Files.exists(mountedVolume)) {
                    retryExecutor.abort();
                }
            });
            try {
                // 10 times with 6 second delays.
                retryExecutor.setMaxAttemptsCount(10).setAttemptTimeoutMillis(6000)
                        .execute(pb);
            } catch (IOException ex) {
                if (!retryExecutor.isAborted()) {
                    // Now force to detach if it still attached
                    if (Files.exists(mountedVolume)) {
                        pb = new ProcessBuilder(
                                sysEnv.hdiutil().toString(),
                                "detach",
                                "-force",
                                hdiUtilVerbosityFlag,
                                normalizedAbsolutePathString(mountedVolume));
                        IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
                    }
                }
            }
        }

        // Compress it to a new image
        pb = new ProcessBuilder(
                sysEnv.hdiutil().toString(),
                "convert",
                normalizedAbsolutePathString(protoDMG),
                hdiUtilVerbosityFlag,
                "-format", "UDZO",
                "-o", normalizedAbsolutePathString(finalDMG));
        try {
            new RetryExecutor()
                .setMaxAttemptsCount(10)
                .setAttemptTimeoutMillis(3000)
                .execute(pb);
        } catch (Exception ex) {
            // Convert might failed if something holds file. Try to convert copy.
            Path protoCopyDMG = protoCopyDmg();
            Files.copy(protoDMG, protoCopyDMG);
            try {
                pb = new ProcessBuilder(
                        sysEnv.hdiutil().toString(),
                        "convert",
                        normalizedAbsolutePathString(protoCopyDMG),
                        hdiUtilVerbosityFlag,
                        "-format", "UDZO",
                        "-o", normalizedAbsolutePathString(finalDMG));
                IOUtils.exec(pb, false, null, true, Executor.INFINITE_TIMEOUT);
            } finally {
                Files.deleteIfExists(protoCopyDMG);
            }
        }

        //add license if needed
        if (pkg.licenseFile().isPresent()) {
            pb = new ProcessBuilder(
                    sysEnv.hdiutil().toString(),
                    "udifrez",
                    normalizedAbsolutePathString(finalDMG),
                    "-xml",
                    normalizedAbsolutePathString(licenseFile())
            );
            new RetryExecutor()
                .setMaxAttemptsCount(10)
                .setAttemptTimeoutMillis(3000)
                .execute(pb);
        }

        try {
            //Delete the temporary image
            Files.deleteIfExists(protoDMG);
        } catch (IOException ex) {
            // Don't care if fails
        }

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.output-to-location"),
                pkg.app().name(), normalizedAbsolutePathString(finalDMG)));

    }

    // Background image name in resources
    private static final String DEFAULT_BACKGROUND_IMAGE = "background_dmg.tiff";
    private static final String DEFAULT_DMG_SETUP_SCRIPT = "DMGsetup.scpt";
    private static final String TEMPLATE_BUNDLE_ICON = "JavaApp.icns";

    private static final String DEFAULT_LICENSE_PLIST="lic_template.plist";
}
