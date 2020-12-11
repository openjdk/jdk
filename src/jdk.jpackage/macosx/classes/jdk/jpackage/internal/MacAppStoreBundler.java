/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import static jdk.jpackage.internal.StandardBundlerParam.VERBOSE;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.MacAppBundler.DEVELOPER_ID_APP_SIGNING_KEY;
import static jdk.jpackage.internal.MacAppBundler.DEFAULT_ICNS_ICON;
import static jdk.jpackage.internal.MacAppBundler.BUNDLE_ID_SIGNING_PREFIX;

public class MacAppStoreBundler extends MacBaseInstallerBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    private static final String TEMPLATE_BUNDLE_ICON_HIDPI = "java.icns";

    public static final BundlerParamInfo<String> MAC_APP_STORE_APP_SIGNING_KEY =
            new StandardBundlerParam<>(
            "mac.signing-key-app",
            String.class,
            params -> {
                String result = MacBaseInstallerBundler.findKey(
                        "3rd Party Mac Developer Application: ",
                        SIGNING_KEY_USER.fetchFrom(params),
                        SIGNING_KEYCHAIN.fetchFrom(params),
                        VERBOSE.fetchFrom(params));
                if (result != null) {
                    MacCertificate certificate = new MacCertificate(result);

                    if (!certificate.isValid()) {
                        Log.error(MessageFormat.format(
                                I18N.getString("error.certificate.expired"),
                                result));
                    }
                }

                return result;
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_APP_STORE_PKG_SIGNING_KEY =
            new StandardBundlerParam<>(
            "mac.signing-key-pkg",
            String.class,
            params -> {
                String result = MacBaseInstallerBundler.findKey(
                        "3rd Party Mac Developer Installer: ",
                        SIGNING_KEY_USER.fetchFrom(params),
                        SIGNING_KEYCHAIN.fetchFrom(params),
                        VERBOSE.fetchFrom(params));

                if (result != null) {
                    MacCertificate certificate = new MacCertificate(result);

                    if (!certificate.isValid()) {
                        Log.error(MessageFormat.format(
                                I18N.getString("error.certificate.expired"),
                                result));
                    }
                }

                return result;
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX =
            new StandardBundlerParam<> (
            "mac.app-store.installerName.suffix",
            String.class,
            params -> "-MacAppStore",
            (s, p) -> s);

    public Path bundle(Map<String, ? super Object> params,
            Path outdir) throws PackagerException {
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.building-bundle"), APP_NAME.fetchFrom(params)));

        IOUtils.writableOutputDir(outdir);

        // first, load in some overrides
        // icns needs @2 versions, so load in the @2 default
        params.put(DEFAULT_ICNS_ICON.getID(), TEMPLATE_BUNDLE_ICON_HIDPI);

        // now we create the app
        Path appImageDir = APP_IMAGE_TEMP_ROOT.fetchFrom(params);
        try {
            Files.createDirectories(appImageDir);

            try {
                MacAppImageBuilder.addNewKeychain(params);
            } catch (InterruptedException e) {
                Log.error(e.getMessage());
            }
            // first, make sure we don't use the local signing key
            params.put(DEVELOPER_ID_APP_SIGNING_KEY.getID(), null);
            Path appLocation = prepareAppBundle(params);

            String signingIdentity =
                    MAC_APP_STORE_APP_SIGNING_KEY.fetchFrom(params);
            String identifierPrefix =
                    BUNDLE_ID_SIGNING_PREFIX.fetchFrom(params);
            MacAppImageBuilder.prepareEntitlements(params);

            MacAppImageBuilder.signAppBundle(params, appLocation,
                    signingIdentity, identifierPrefix,
                    MacAppImageBuilder.getConfig_Entitlements(params));
            MacAppImageBuilder.restoreKeychainList(params);

            ProcessBuilder pb;

            // create the final pkg file
            Path finalPKG = outdir.resolve(MAC_INSTALLER_NAME.fetchFrom(params)
                    + INSTALLER_SUFFIX.fetchFrom(params)
                    + ".pkg");
            Files.createDirectories(outdir);

            String installIdentify =
                    MAC_APP_STORE_PKG_SIGNING_KEY.fetchFrom(params);

            List<String> buildOptions = new ArrayList<>();
            buildOptions.add("/usr/bin/productbuild");
            buildOptions.add("--component");
            buildOptions.add(appLocation.toString());
            buildOptions.add("/Applications");
            buildOptions.add("--sign");
            buildOptions.add(installIdentify);
            buildOptions.add("--product");
            buildOptions.add(appLocation + "/Contents/Info.plist");
            String keychainName = SIGNING_KEYCHAIN.fetchFrom(params);
            if (keychainName != null && !keychainName.isEmpty()) {
                buildOptions.add("--keychain");
                buildOptions.add(keychainName);
            }
            buildOptions.add(finalPKG.toAbsolutePath().toString());

            pb = new ProcessBuilder(buildOptions);

            IOUtils.exec(pb);
            return finalPKG;
        } catch (PackagerException pe) {
            throw pe;
        } catch (Exception ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Implement Bundler
    ///////////////////////////////////////////////////////////////////////

    @Override
    public String getName() {
        return I18N.getString("store.bundler.name");
    }

    @Override
    public String getID() {
        return "mac.appStore";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);

            // hdiutil is always available so there's no need to test for
            // availability.
            // run basic validation to ensure requirements are met

            // we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            // reject explicitly set to not sign
            if (!Optional.ofNullable(MacAppImageBuilder.
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
                throw new ConfigException(
                        I18N.getString("error.must-sign-app-store"),
                        I18N.getString("error.must-sign-app-store.advice"));
            }

            // make sure we have settings for signatures
            if (MAC_APP_STORE_APP_SIGNING_KEY.fetchFrom(params) == null) {
                throw new ConfigException(
                        I18N.getString("error.no-app-signing-key"),
                        I18N.getString("error.no-app-signing-key.advice"));
            }
            if (MAC_APP_STORE_PKG_SIGNING_KEY.fetchFrom(params) == null) {
                throw new ConfigException(
                        I18N.getString("error.no-pkg-signing-key"),
                        I18N.getString("error.no-pkg-signing-key.advice"));
            }

            // things we could check...
            // check the icons, make sure it has hidpi icons
            // check the category,
            // make sure it fits in the list apple has provided
            // validate bundle identifier is reverse dns
            // check for \a+\.\a+\..

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
    public Path execute(Map<String, ? super Object> params,
            Path outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        // return (!runtimeInstaller &&
        //         Platform.getPlatform() == Platform.MAC);
        return false; // mac-app-store not yet supported
    }

    @Override
    public boolean isDefault() {
        return false;
    }

}
