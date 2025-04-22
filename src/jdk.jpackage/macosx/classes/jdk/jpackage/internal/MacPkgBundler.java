/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.MacAppImageBuilder.APP_STORE;
import static jdk.jpackage.internal.MacApplicationBuilder.isValidBundleIdentifier;
import static jdk.jpackage.internal.StandardBundlerParam.SIGN_BUNDLE;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.PackagerException;

public class MacPkgBundler extends MacBaseInstallerBundler {

    public static final
            BundlerParamInfo<String> DEVELOPER_ID_INSTALLER_SIGNING_KEY =
            new BundlerParamInfo<>(
            "mac.signing-key-developer-id-installer",
            String.class,
            params -> {
                    String user = SIGNING_KEY_USER.fetchFrom(params);
                    String keychain = SIGNING_KEYCHAIN.fetchFrom(params);
                    String result = null;
                    if (APP_STORE.fetchFrom(params)) {
                        result = MacCertificate.findCertificateKey(
                            "3rd Party Mac Developer Installer: ",
                            user, keychain);
                    }
                    // if either not signing for app store or couldn't find
                    if (result == null) {
                        result = MacCertificate.findCertificateKey(
                            "Developer ID Installer: ", user, keychain);
                    }

                    return result;
                },
            (s, p) -> s);

    @Override
    public String getName() {
        return I18N.getString("pkg.bundler.name");
    }

    @Override
    public String getID() {
        return "pkg";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);

            final var pkgPkg = MacFromParams.PKG_PACKAGE.fetchFrom(params);

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            String identifier = ((MacApplication)pkgPkg.app()).bundleIdentifier();
            if (identifier == null) {
                throw new ConfigException(
                        I18N.getString("message.app-image-requires-identifier"),
                        I18N.getString(
                            "message.app-image-requires-identifier.advice"));
            }
            if (!isValidBundleIdentifier(identifier)) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(
                        "message.invalid-identifier"), identifier),
                        I18N.getString("message.invalid-identifier.advice"));
            }

            // reject explicitly set sign to true and no valid signature key
            if (Optional.ofNullable(
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                if (!SIGNING_KEY_USER.getIsDefaultValue(params)) {
                    String signingIdentity =
                            DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                    if (signingIdentity == null) {
                        throw new ConfigException(
                                I18N.getString("error.explicit-sign-no-cert"),
                                I18N.getString(
                                "error.explicit-sign-no-cert.advice"));
                    }
                }

                // No need to validate --mac-installer-sign-identity, since it is
                // pass through option.
            }

            // hdiutil is always available so there's no need
            // to test for availability.

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

        final var pkg = MacFromParams.PKG_PACKAGE.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        final var packager = MacPkgPackager.build().outputDir(outputParentDir).pkg(pkg).env(env);

        return packager.execute();
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return true;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

}
