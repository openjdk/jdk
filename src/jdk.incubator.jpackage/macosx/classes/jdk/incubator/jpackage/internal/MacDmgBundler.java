/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.text.MessageFormat;
import java.util.*;
import static jdk.incubator.jpackage.internal.MacAppImageBuilder.ICON_ICNS;
import static jdk.incubator.jpackage.internal.MacAppImageBuilder.MAC_CF_BUNDLE_IDENTIFIER;
import static jdk.incubator.jpackage.internal.OverridableResource.createResource;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public class MacDmgBundler extends MacBaseInstallerBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MacResources");

    // Background image name in resources
    static final String DEFAULT_BACKGROUND_IMAGE = "background_dmg.tiff";
    // Backround image name and folder under which it will be stored in DMG
    static final String BACKGROUND_IMAGE_FOLDER =".background";
    static final String BACKGROUND_IMAGE = "background.tiff";
    static final String DEFAULT_DMG_SETUP_SCRIPT = "DMGsetup.scpt";
    static final String TEMPLATE_BUNDLE_ICON = "java.icns";

    static final String DEFAULT_LICENSE_PLIST="lic_template.plist";

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX =
            new StandardBundlerParam<> (
            "mac.dmg.installerName.suffix",
            String.class,
            params -> "",
            (s, p) -> s);

    public File bundle(Map<String, ? super Object> params,
            File outdir) throws PackagerException {
        Log.verbose(MessageFormat.format(I18N.getString("message.building-dmg"),
                APP_NAME.fetchFrom(params)));

        IOUtils.writableOutputDir(outdir.toPath());

        File appImageDir = APP_IMAGE_TEMP_ROOT.fetchFrom(params);
        try {
            appImageDir.mkdirs();

            if (prepareAppBundle(params) != null &&
                    prepareConfigFiles(params)) {
                File configScript = getConfig_Script(params);
                if (configScript.exists()) {
                    Log.verbose(MessageFormat.format(
                            I18N.getString("message.running-script"),
                            configScript.getAbsolutePath()));
                    IOUtils.run("bash", configScript);
                }

                return buildDMG(params, outdir);
            }
            return null;
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private static final String hdiutil = "/usr/bin/hdiutil";

    private void prepareDMGSetupScript(Map<String, ? super Object> params)
                                                                    throws IOException {
        File dmgSetup = getConfig_VolumeScript(params);
        Log.verbose(MessageFormat.format(
                I18N.getString("message.preparing-dmg-setup"),
                dmgSetup.getAbsolutePath()));

        // We need to use URL for DMG to find it. We cannot use volume name, since
        // user might have open DMG with same volume name already. Url should end with
        // '/' and it should be real path (no symbolic links).
        File imageDir = IMAGES_ROOT.fetchFrom(params);
        if (!imageDir.exists()) imageDir.mkdirs(); // Create it, since it does not exist
        Path rootPath = Path.of(imageDir.toString()).toRealPath();
        Path volumePath = rootPath.resolve(APP_NAME.fetchFrom(params));
        String volumeUrl = volumePath.toUri().toString() + File.separator;

        // Provide full path to backround image, so we can find it.
        Path bgFile = Path.of(rootPath.toString(), APP_NAME.fetchFrom(params),
                              BACKGROUND_IMAGE_FOLDER, BACKGROUND_IMAGE);

        //prepare config for exe
        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_VOLUME_URL", volumeUrl);
        data.put("DEPLOY_BG_FILE", bgFile.toString());
        data.put("DEPLOY_VOLUME_PATH", volumePath.toString());
        data.put("DEPLOY_APPLICATION_NAME", APP_NAME.fetchFrom(params));

        data.put("DEPLOY_INSTALL_LOCATION", MAC_INSTALL_DIR.fetchFrom(params));
        data.put("DEPLOY_INSTALL_NAME", MAC_INSTALL_DIR.fetchFrom(params));

        createResource(DEFAULT_DMG_SETUP_SCRIPT, params)
                .setCategory(I18N.getString("resource.dmg-setup-script"))
                .setSubstitutionData(data)
                .saveToFile(dmgSetup);
    }

    private File getConfig_VolumeScript(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-dmg-setup.scpt");
    }

    private File getConfig_VolumeBackground(
            Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-background.tiff");
    }

    private File getConfig_VolumeIcon(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-volume.icns");
    }

    private File getConfig_LicenseFile(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-license.plist");
    }

    private void prepareLicense(Map<String, ? super Object> params) {
        try {
            String licFileStr = LICENSE_FILE.fetchFrom(params);
            if (licFileStr == null) {
                return;
            }

            File licFile = new File(licFileStr);
            byte[] licenseContentOriginal =
                    Files.readAllBytes(licFile.toPath());
            String licenseInBase64 =
                    Base64.getEncoder().encodeToString(licenseContentOriginal);

            Map<String, String> data = new HashMap<>();
            data.put("APPLICATION_LICENSE_TEXT", licenseInBase64);

            createResource(DEFAULT_LICENSE_PLIST, params)
                    .setCategory(I18N.getString("resource.license-setup"))
                    .setSubstitutionData(data)
                    .saveToFile(getConfig_LicenseFile(params));

        } catch (IOException ex) {
            Log.verbose(ex);
        }
    }

    private boolean prepareConfigFiles(Map<String, ? super Object> params)
            throws IOException {

        createResource(DEFAULT_BACKGROUND_IMAGE, params)
                    .setCategory(I18N.getString("resource.dmg-background"))
                    .saveToFile(getConfig_VolumeBackground(params));

        createResource(TEMPLATE_BUNDLE_ICON, params)
                .setCategory(I18N.getString("resource.volume-icon"))
                .setExternal(ICON_ICNS.fetchFrom(params))
                .saveToFile(getConfig_VolumeIcon(params));

        createResource(null, params)
                .setCategory(I18N.getString("resource.post-install-script"))
                .saveToFile(getConfig_Script(params));

        prepareLicense(params);

        prepareDMGSetupScript(params);

        return true;
    }

    // name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-post-image.sh");
    }

    // Location of SetFile utility may be different depending on MacOS version
    // We look for several known places and if none of them work will
    // try ot find it
    private String findSetFileUtility() {
        String typicalPaths[] = {"/Developer/Tools/SetFile",
                "/usr/bin/SetFile", "/Developer/usr/bin/SetFile"};

        String setFilePath = null;
        for (String path: typicalPaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                setFilePath = path;
                break;
            }
        }

        // Validate SetFile, if Xcode is not installed it will run, but exit with error
        // code
        if (setFilePath != null) {
            try {
                ProcessBuilder pb = new ProcessBuilder(setFilePath, "-h");
                Process p = pb.start();
                int code = p.waitFor();
                if (code == 0) {
                    return setFilePath;
                }
            } catch (Exception ignored) {}

            // No need for generic find attempt. We found it, but it does not work.
            // Probably due to missing xcode.
            return null;
        }

        // generic find attempt
        try {
            ProcessBuilder pb = new ProcessBuilder("xcrun", "-find", "SetFile");
            Process p = pb.start();
            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            BufferedReader br = new BufferedReader(isr);
            String lineRead = br.readLine();
            if (lineRead != null) {
                File f = new File(lineRead);
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        } catch (IOException ignored) {}

        return null;
    }

    private File buildDMG(
            Map<String, ? super Object> params, File outdir)
            throws IOException {
        File imagesRoot = IMAGES_ROOT.fetchFrom(params);
        if (!imagesRoot.exists()) imagesRoot.mkdirs();

        File protoDMG = new File(imagesRoot,
                APP_NAME.fetchFrom(params) +"-tmp.dmg");
        File finalDMG = new File(outdir, INSTALLER_NAME.fetchFrom(params)
                + INSTALLER_SUFFIX.fetchFrom(params) + ".dmg");

        File srcFolder = APP_IMAGE_TEMP_ROOT.fetchFrom(params);
        File predefinedImage =
                StandardBundlerParam.getPredefinedAppImage(params);
        if (predefinedImage != null) {
            srcFolder = predefinedImage;
        }

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.creating-dmg-file"), finalDMG.getAbsolutePath()));

        protoDMG.delete();
        if (finalDMG.exists() && !finalDMG.delete()) {
            throw new IOException(MessageFormat.format(I18N.getString(
                    "message.dmg-cannot-be-overwritten"),
                    finalDMG.getAbsolutePath()));
        }

        protoDMG.getParentFile().mkdirs();
        finalDMG.getParentFile().mkdirs();

        String hdiUtilVerbosityFlag = VERBOSE.fetchFrom(params) ?
                "-verbose" : "-quiet";

        // create temp image
        ProcessBuilder pb = new ProcessBuilder(
                hdiutil,
                "create",
                hdiUtilVerbosityFlag,
                "-srcfolder", srcFolder.getAbsolutePath(),
                "-volname", APP_NAME.fetchFrom(params),
                "-ov", protoDMG.getAbsolutePath(),
                "-fs", "HFS+",
                "-format", "UDRW");
        IOUtils.exec(pb);

        // mount temp image
        pb = new ProcessBuilder(
                hdiutil,
                "attach",
                protoDMG.getAbsolutePath(),
                hdiUtilVerbosityFlag,
                "-mountroot", imagesRoot.getAbsolutePath());
        IOUtils.exec(pb, false, null, true);

        File mountedRoot = new File(imagesRoot.getAbsolutePath(),
                    APP_NAME.fetchFrom(params));

        try {
            // volume icon
            File volumeIconFile = new File(mountedRoot, ".VolumeIcon.icns");
            IOUtils.copyFile(getConfig_VolumeIcon(params),
                    volumeIconFile);

            // background image
            File bgdir = new File(mountedRoot, BACKGROUND_IMAGE_FOLDER);
            bgdir.mkdirs();
            IOUtils.copyFile(getConfig_VolumeBackground(params),
                    new File(bgdir, BACKGROUND_IMAGE));

            // Indicate that we want a custom icon
            // NB: attributes of the root directory are ignored
            // when creating the volume
            // Therefore we have to do this after we mount image
            String setFileUtility = findSetFileUtility();
            if (setFileUtility != null) {
                //can not find utility => keep going without icon
                try {
                    volumeIconFile.setWritable(true);
                    // The "creator" attribute on a file is a legacy attribute
                    // but it seems Finder excepts these bytes to be
                    // "icnC" for the volume icon
                    // (might not work on Mac 10.13 with old XCode)
                    pb = new ProcessBuilder(
                            setFileUtility,
                            "-c", "icnC",
                            volumeIconFile.getAbsolutePath());
                    IOUtils.exec(pb);
                    volumeIconFile.setReadOnly();

                    pb = new ProcessBuilder(
                            setFileUtility,
                            "-a", "C",
                            mountedRoot.getAbsolutePath());
                    IOUtils.exec(pb);
                } catch (IOException ex) {
                    Log.error(ex.getMessage());
                    Log.verbose("Cannot enable custom icon using SetFile utility");
                }
            } else {
                Log.verbose(I18N.getString("message.setfile.dmg"));
            }

            // We will not consider setting background image and creating link to
            // /Application folder in DMG as critical error, since it can fail in
            // headless enviroment.
            try {
                pb = new ProcessBuilder("osascript",
                        getConfig_VolumeScript(params).getAbsolutePath());
                IOUtils.exec(pb);
            } catch (IOException ex) {
                Log.verbose(ex);
            }
        } finally {
            // Detach the temporary image
            pb = new ProcessBuilder(
                    hdiutil,
                    "detach",
                    "-force",
                    hdiUtilVerbosityFlag,
                    mountedRoot.getAbsolutePath());
            IOUtils.exec(pb);
        }

        // Compress it to a new image
        pb = new ProcessBuilder(
                hdiutil,
                "convert",
                protoDMG.getAbsolutePath(),
                hdiUtilVerbosityFlag,
                "-format", "UDZO",
                "-o", finalDMG.getAbsolutePath());
        IOUtils.exec(pb);

        //add license if needed
        if (getConfig_LicenseFile(params).exists()) {
            //hdiutil unflatten your_image_file.dmg
            pb = new ProcessBuilder(
                    hdiutil,
                    "unflatten",
                    finalDMG.getAbsolutePath()
            );
            IOUtils.exec(pb);

            //add license
            pb = new ProcessBuilder(
                    hdiutil,
                    "udifrez",
                    finalDMG.getAbsolutePath(),
                    "-xml",
                    getConfig_LicenseFile(params).getAbsolutePath()
            );
            IOUtils.exec(pb);

            //hdiutil flatten your_image_file.dmg
            pb = new ProcessBuilder(
                    hdiutil,
                    "flatten",
                    finalDMG.getAbsolutePath()
            );
            IOUtils.exec(pb);

        }

        //Delete the temporary image
        protoDMG.delete();

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.output-to-location"),
                APP_NAME.fetchFrom(params), finalDMG.getAbsolutePath()));

        return finalDMG;
    }


    //////////////////////////////////////////////////////////////////////////
    // Implement Bundler
    //////////////////////////////////////////////////////////////////////////

    @Override
    public String getName() {
        return I18N.getString("dmg.bundler.name");
    }

    @Override
    public String getID() {
        return "dmg";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);

            //run basic validation to ensure requirements are met
            //we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return isSupported();
    }

    public final static String[] required =
            {"/usr/bin/hdiutil", "/usr/bin/osascript"};
    public static boolean isSupported() {
        try {
            for (String s : required) {
                File f = new File(s);
                if (!f.exists() || !f.canExecute()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDefault() {
        return true;
    }

}
