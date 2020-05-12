/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public abstract class MacBaseInstallerBundler extends AbstractBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.MacResources");

    // This could be generalized more to be for any type of Image Bundler
    public static final BundlerParamInfo<MacAppBundler> APP_BUNDLER =
            new StandardBundlerParam<>(
            "mac.app.bundler",
            MacAppBundler.class,
            params -> new MacAppBundler(),
            (s, p) -> null);

    public final BundlerParamInfo<File> APP_IMAGE_TEMP_ROOT =
            new StandardBundlerParam<>(
            "mac.app.imageRoot",
            File.class,
            params -> {
                File imageDir = IMAGES_ROOT.fetchFrom(params);
                if (!imageDir.exists()) imageDir.mkdirs();
                try {
                    return Files.createTempDirectory(
                            imageDir.toPath(), "image-").toFile();
                } catch (IOException e) {
                    return new File(imageDir, getID()+ ".image");
                }
            },
            (s, p) -> new File(s));

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

    public static final BundlerParamInfo<String> MAC_INSTALL_DIR =
            new StandardBundlerParam<>(
            "mac-install-dir",
            String.class,
             params -> {
                 String dir = INSTALL_DIR.fetchFrom(params);
                 return (dir != null) ? dir : "/Applications";
             },
            (s, p) -> s
    );

    public static final BundlerParamInfo<String> INSTALLER_NAME =
            new StandardBundlerParam<> (
            "mac.installerName",
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

    protected void validateAppImageAndBundeler(
            Map<String, ? super Object> params) throws ConfigException {
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) != null) {
            File applicationImage = PREDEFINED_APP_IMAGE.fetchFrom(params);
            if (!applicationImage.exists()) {
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
        } else {
            APP_BUNDLER.fetchFrom(params).validate(params);
        }
    }

    protected File prepareAppBundle(Map<String, ? super Object> params)
            throws PackagerException {
        File predefinedImage =
                StandardBundlerParam.getPredefinedAppImage(params);
        if (predefinedImage != null) {
            return predefinedImage;
        }
        File appImageRoot = APP_IMAGE_TEMP_ROOT.fetchFrom(params);

        return APP_BUNDLER.fetchFrom(params).doBundle(
                params, appImageRoot, true);
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    public static String findKey(String key, String keychainName,
            boolean verbose) {
        if (Platform.getPlatform() != Platform.MAC) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos)) {
            List<String> searchOptions = new ArrayList<>();
            searchOptions.add("security");
            searchOptions.add("find-certificate");
            searchOptions.add("-c");
            searchOptions.add(key);
            searchOptions.add("-a");
            if (keychainName != null && !keychainName.isEmpty()) {
                searchOptions.add(keychainName);
            }

            ProcessBuilder pb = new ProcessBuilder(searchOptions);

            IOUtils.exec(pb, false, ps);
            Pattern p = Pattern.compile("\"alis\"<blob>=\"([^\"]+)\"");
            Matcher m = p.matcher(baos.toString());
            if (!m.find()) {
                Log.error(MessageFormat.format(I18N.getString(
                        "error.cert.not.found"), key, keychainName));
                return null;
            }
            String matchedKey = m.group(1);
            if (m.find()) {
                Log.error(MessageFormat.format(I18N.getString(
                        "error.multiple.certs.found"), key, keychainName));
                return null;
            }
            Log.verbose("Using key '" + matchedKey + "'");
            return matchedKey;
        } catch (IOException ioe) {
            Log.verbose(ioe);
            return null;
        }
    }
}
