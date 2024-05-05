/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALLER_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALL_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;

public abstract class MacBaseInstallerBundler extends AbstractBundler {

    private final BundlerParamInfo<Path> APP_IMAGE_TEMP_ROOT =
            new StandardBundlerParam<>(
            "mac.app.imageRoot",
            Path.class,
            params -> {
                Path imageDir = IMAGES_ROOT.fetchFrom(params);
                try {
                    if (!IOUtils.exists(imageDir)) {
                        Files.createDirectories(imageDir);
                    }
                    return Files.createTempDirectory(
                            imageDir, "image-");
                } catch (IOException e) {
                    return imageDir.resolve(getID()+ ".image");
                }
            },
            (s, p) -> Path.of(s));

    public static final BundlerParamInfo<String> SIGNING_KEY_USER =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.MAC_SIGNING_KEY_NAME.getId(),
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> SIGNING_KEYCHAIN =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.MAC_SIGNING_KEYCHAIN.getId(),
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> INSTALLER_SIGN_IDENTITY =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.MAC_INSTALLER_SIGN_IDENTITY.getId(),
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> MAC_INSTALLER_NAME =
            new StandardBundlerParam<> (
            "mac.installerName",
            String.class,
            params -> {
                String nm = INSTALLER_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

     // Returns full path to installation directory
     static String getInstallDir(
            Map<String, ? super Object>  params, boolean defaultOnly) {
        String returnValue = INSTALL_DIR.fetchFrom(params);
        if (defaultOnly && returnValue != null) {
            Log.info(I18N.getString("message.install-dir-ignored"));
            returnValue = null;
        }
        if (returnValue == null) {
            if (StandardBundlerParam.isRuntimeInstaller(params)) {
                returnValue = "/Library/Java/JavaVirtualMachines";
            } else {
               returnValue = "/Applications";
            }
        }
        return returnValue;
    }

    // Returns display name of installation directory. Display name is used to
    // show user installation location and for well known (default only) we will
    // use "Applications" or "JavaVirtualMachines".
    static String getInstallDirDisplayName(
            Map<String, ? super Object>  params) {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return "JavaVirtualMachines";
        } else {
            return "Applications";
        }
    }

    public MacBaseInstallerBundler() {
        appImageBundler = new MacAppBundler()
                .setDependentTask(true);
    }

    protected void validateAppImageAndBundeler(
            Map<String, ? super Object> params) throws ConfigException {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            Path applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
            if (!IOUtils.exists(applicationImage)) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(
                                "message.app-image-dir-does-not-exist"),
                                PREDEFINED_APP_IMAGE.getID(),
                                applicationImage.toString()),
                        MessageFormat.format(I18N.getString(
                                "message.app-image-dir-does-not-exist.advice"),
                                PREDEFINED_APP_IMAGE.getID()));
            }
            if (APP_NAME.fetchFrom(params) == null) {
                throw new ConfigException(
                        I18N.getString("message.app-image-requires-app-name"),
                        I18N.getString(
                            "message.app-image-requires-app-name.advice"));
            }
            if (AppImageFile.load(applicationImage).isSigned()) {
                if (!Files.exists(
                        PackageFile.getPathInAppImage(applicationImage))) {
                    Log.info(MessageFormat.format(I18N.getString(
                            "warning.per.user.app.image.signed"),
                            PackageFile.getPathInAppImage(applicationImage)));
                }
            } else {
                if (Optional.ofNullable(
                        SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                    // if signing bundle with app-image, warn user if app-image
                    // is not already signed.
                    Log.info(MessageFormat.format(I18N.getString(
                            "warning.unsigned.app.image"), getID()));
                }
            }
        } else {
            appImageBundler.validate(params);
        }
    }

    protected Path prepareAppBundle(Map<String, ? super Object> params)
            throws PackagerException, IOException {
        Path appDir;
        Path appImageRoot = APP_IMAGE_TEMP_ROOT.fetchFrom(params);
        Path predefinedImage =
                StandardBundlerParam.getPredefinedAppImage(params);
        if (predefinedImage != null) {
            appDir = appImageRoot.resolve(APP_NAME.fetchFrom(params) + ".app");
            IOUtils.copyRecursive(predefinedImage, appDir,
                    LinkOption.NOFOLLOW_LINKS);

            // Create PackageFile if predefined app image is not signed
            if (!StandardBundlerParam.isRuntimeInstaller(params) &&
                    !AppImageFile.load(predefinedImage).isSigned()) {
                new PackageFile(APP_NAME.fetchFrom(params)).save(
                        ApplicationLayout.macAppImage().resolveAt(appDir));
                // We need to re-sign app image after adding ".package" to it.
                // We only do this if app image was not signed which means it is
                // signed with ad-hoc signature. App bundles with ad-hoc
                // signature are sealed, but without a signing identity, so we
                // need to re-sign it after modification.
                MacAppImageBuilder.signAppBundle(params, appDir, "-", null, null);
            }
        } else {
            appDir = appImageBundler.execute(params, appImageRoot);
        }

        return appDir;
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    private final Bundler appImageBundler;
}
