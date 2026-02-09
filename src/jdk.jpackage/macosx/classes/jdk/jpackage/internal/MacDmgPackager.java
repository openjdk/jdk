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

import static jdk.jpackage.internal.util.PathUtils.normalizedAbsolutePathString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathGroup;
import jdk.jpackage.internal.util.RootedPath;

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

    Path licensePListFile() {
        return env.configDir().resolve(pkg.app().name() + "-license.plist");
    }

    private Path finalDmg() {
        return outputDir.resolve(pkg.packageFileNameWithSuffix());
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
        RootedPath.copy(pkg.dmgRootDirSources().stream(), srcFolder);
    }

    private Executor hdiutil(String... args) {
        return Executor.of(sysEnv.hdiutil().toString()).args(args).storeOutputInFiles();
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

    private void prepareConfigFiles() throws IOException {

        env.createResource(DEFAULT_BACKGROUND_IMAGE)
                    .setCategory(I18N.getString("resource.dmg-background"))
                    .saveToFile(volumeBackground());

        env.createResource(TEMPLATE_BUNDLE_ICON)
                .setCategory(I18N.getString("resource.volume-icon"))
                .setExternal(pkg.icon().orElse(null))
                .saveToFile(volumeIcon());

        if (pkg.licenseFile().isPresent()) {
            MacDmgLicense.prepareLicensePListFile(pkg.licenseFile().get(), licensePListFile());
        }

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

    private String hdiUtilVerbosityFlag() {
        return env.verbose() ? "-verbose" : "-quiet";
    }

    private void buildDMG() throws IOException {
        boolean copyAppImage = false;

        final Path protoDMG = protoDmg();
        final Path finalDMG = finalDmg();

        final Path srcFolder = env.appImageDir();

        Files.createDirectories(protoDMG.getParent());
        Files.createDirectories(finalDMG.getParent());

        final String hdiUtilVerbosityFlag = hdiUtilVerbosityFlag();

        // create temp image
        try {
            hdiutil("create",
                    hdiUtilVerbosityFlag,
                    "-srcfolder", normalizedAbsolutePathString(srcFolder),
                    "-volname", volumeName(),
                    "-ov", normalizedAbsolutePathString(protoDMG),
                    "-fs", "HFS+",
                    "-format", "UDRW").executeExpectSuccess();
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
            hdiutil(
                    "create",
                    hdiUtilVerbosityFlag,
                    "-size", String.valueOf(size),
                    "-volname", volumeName(),
                    "-ov", normalizedAbsolutePathString(protoDMG),
                    "-fs", "HFS+"
            ).retry()
                    .setMaxAttemptsCount(10)
                    .setAttemptTimeout(3, TimeUnit.SECONDS)
                    .execute();
        }

        final Path mountedVolume = volumePath();

        // mount temp image
        hdiutil("attach",
                normalizedAbsolutePathString(protoDMG),
                hdiUtilVerbosityFlag,
                "-mountroot", mountedVolume.getParent().toString()).executeExpectSuccess();

        // Copy app image, since we did not create DMG with it, but instead we created
        // empty one.
        if (copyAppImage) {
            FileUtils.copyRecursive(srcFolder, mountedVolume, LinkOption.NOFOLLOW_LINKS);
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
                Executor.of(
                        sysEnv.osascript().toString(),
                        normalizedAbsolutePathString(volumeScript())
                )
                // Wait 3 minutes. See JDK-8248248.
                .timeout(3, TimeUnit.MINUTES)
                .executeExpectSuccess();
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
                    Executor.of(
                            sysEnv.setFileUtility().orElseThrow().toString(),
                            "-c", "icnC",
                            normalizedAbsolutePathString(volumeIconFile)
                    ).executeExpectSuccess();
                    volumeIconFile.toFile().setReadOnly();

                    Executor.of(
                            sysEnv.setFileUtility().orElseThrow().toString(),
                            "-a", "C",
                            normalizedAbsolutePathString(mountedVolume)
                    ).executeExpectSuccess();
                } catch (IOException ex) {
                    Log.error(ex.getMessage());
                    Log.verbose("Cannot enable custom icon using SetFile utility");
                }
            } else {
                Log.verbose(I18N.getString("message.setfile.dmg"));
            }

        } finally {
            // Detach the temporary image
            detachVolume();
        }

        // Compress it to a new image
        convertProtoDmg();

        //add license if needed
        if (pkg.licenseFile().isPresent()) {
            hdiutil(
                    "udifrez",
                    normalizedAbsolutePathString(finalDMG),
                    "-xml",
                    normalizedAbsolutePathString(licensePListFile())
            ).retry()
                    .setMaxAttemptsCount(10)
                    .setAttemptTimeout(3, TimeUnit.SECONDS)
                    .execute();
        }

        try {
            //Delete the temporary image
            Files.deleteIfExists(protoDMG);
        } catch (IOException ex) {
            // Don't care if fails
        }
    }

    private void detachVolume() throws IOException {
        var mountedVolume = volumePath();

        // "hdiutil detach" might not work right away due to resource busy error, so
        // repeat detach several times.
        Globals.instance().objectFactory().<Void, IOException>retryExecutor(IOException.class).setExecutable(context -> {

            List<String> cmdline = new ArrayList<>();
            cmdline.add("detach");

            if (context.isLastAttempt()) {
                // The last attempt, force detach.
                cmdline.add("-force");
            }

            cmdline.addAll(List.of(
                    hdiUtilVerbosityFlag(),
                    normalizedAbsolutePathString(mountedVolume)
            ));

            // The image can get detached even if we get a resource busy error,
            // so execute the detach command without checking the exit code.
            var result = hdiutil(cmdline.toArray(String[]::new)).execute();

            if (result.getExitCode() == 0 || !Files.exists(mountedVolume)) {
                // Detached successfully!
                return null;
            } else {
                throw result.unexpected();
            }
        }).setMaxAttemptsCount(10).setAttemptTimeout(6, TimeUnit.SECONDS).execute();
    }

    private void convertProtoDmg() throws IOException {

        Function<Path, Executor> convert = srcDmg -> {
            return hdiutil(
                    "convert",
                    normalizedAbsolutePathString(srcDmg),
                    hdiUtilVerbosityFlag(),
                    "-format", "UDZO",
                    "-o", normalizedAbsolutePathString(finalDmg()));
        };

        // Convert it to a new image.
        try {
            convert.apply(protoDmg()).retry()
                .setMaxAttemptsCount(10)
                .setAttemptTimeout(3, TimeUnit.SECONDS)
                .execute();
        } catch (IOException ex) {
            Log.verbose(ex);
            // Something holds the file, try to convert a copy.
            Path copyDmg = protoCopyDmg();
            Files.copy(protoDmg(), copyDmg);
            try {
                convert.apply(copyDmg).executeExpectSuccess();
            } finally {
                Files.deleteIfExists(copyDmg);
            }
        }
    }

    // Background image name in resources
    private static final String DEFAULT_BACKGROUND_IMAGE = "background_dmg.tiff";
    private static final String DEFAULT_DMG_SETUP_SCRIPT = "DMGsetup.scpt";
    private static final String TEMPLATE_BUNDLE_ICON = "JavaApp.icns";
}
