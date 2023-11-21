/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.nio.file.Path;
import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;

/**
 * Tests generation of dmg and pkg with --mac-sign and related arguments.
 * Test will generate pkg and verifies its signature. It verifies that dmg
 * is not signed, but app image inside dmg is signed. This test requires that
 * the machine is configured with test certificate for
 * "Developer ID Installer: jpackage.openjdk.java.net" in
 * jpackagerTest keychain with
 * always allowed access to this keychain for user which runs test.
 * note:
 * "jpackage.openjdk.java.net" can be over-ridden by systerm property
 * "jpackage.mac.signing.key.user.name", and
 * "jpackagerTest" can be over-ridden by system property
 * "jpackage.mac.signing.keychain"
 */

/*
 * @test
 * @summary jpackage with --type pkg,dmg --mac-sign
 * @library ../helpers
 * @library /test/lib
 * @library base
 * @key jpackagePlatformPackage
 * @build SigningBase
 * @build SigningCheck
 * @build jtreg.SkippedException
 * @build jdk.jpackage.test.*
 * @build SigningPackageTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTest
 */
public class SigningPackageTest {

    private static boolean isAppImageSigned(JPackageCommand cmd) {
        return cmd.hasArgument("--mac-signing-key-user-name") ||
               cmd.hasArgument("--mac-app-image-sign-identity");
    }

    private static boolean isPKGSigned(JPackageCommand cmd) {
        return cmd.hasArgument("--mac-signing-key-user-name") ||
               cmd.hasArgument("--mac-installer-sign-identity");
    }

    private static void verifyPKG(JPackageCommand cmd) {
        Path outputBundle = cmd.outputBundle();
        SigningBase.verifyPkgutil(outputBundle, isPKGSigned(cmd), getCertIndex(cmd));
        if (isPKGSigned(cmd)) {
            SigningBase.verifySpctl(outputBundle, "install", getCertIndex(cmd));
        }
    }

    private static void verifyDMG(JPackageCommand cmd) {
        Path outputBundle = cmd.outputBundle();
        SigningBase.verifyDMG(outputBundle);
    }

    private static void verifyAppImageInDMG(JPackageCommand cmd) {
        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            Path launcherPath = ApplicationLayout.platformAppImage()
                    .resolveAt(dmgImage).launchersDirectory().resolve(cmd.name());
            // We will be called with all folders in DMG since JDK-8263155, but
            // we only need to verify app.
            if (dmgImage.endsWith(cmd.name() + ".app")) {
                SigningBase.verifyCodesign(launcherPath, isAppImageSigned(cmd),
                                           getCertIndex(cmd));
                SigningBase.verifyCodesign(dmgImage, isAppImageSigned(cmd),
                                           getCertIndex(cmd));
                if (isAppImageSigned(cmd)) {
                    SigningBase.verifySpctl(dmgImage, "exec", getCertIndex(cmd));
                }
            }
        });
    }

    private static int getCertIndex(JPackageCommand cmd) {
        if (cmd.hasArgument("--mac-signing-key-user-name")) {
            String devName = cmd.getArgumentValue("--mac-signing-key-user-name");
            return SigningBase.getDevNameIndex(devName);
        } else {
            // Signing-indentity
            return Integer.valueOf(SigningBase.UNICODE_INDEX);
        }
    }

    @Test
    // ("signing-key or sign-identity", "sign app-image", "sign pkg", "certificate index"})
    // Signing-key and ASCII certificate
    @Parameter({"true", "true", "true", SigningBase.ASCII_INDEX})
    // Signing-key and UNICODE certificate
    @Parameter({"true", "true", "true", SigningBase.UNICODE_INDEX})
    // Signing-indentity and UNICODE certificate
    @Parameter({"false", "true", "true", SigningBase.UNICODE_INDEX})
    // Signing-indentity, but sign app-image only and UNICODE certificate
    @Parameter({"false", "true", "false", SigningBase.UNICODE_INDEX})
    // Signing-indentity, but sign pkg only and UNICODE certificate
    @Parameter({"false", "false", "true", SigningBase.UNICODE_INDEX})
    public static void test(String... testArgs) throws Exception {
        boolean signingKey = Boolean.parseBoolean(testArgs[0]);
        boolean signAppImage = Boolean.parseBoolean(testArgs[1]);
        boolean signPKG = Boolean.parseBoolean(testArgs[2]);
        int certIndex = Integer.parseInt(testArgs[3]);

        SigningCheck.checkCertificates(certIndex);

        new PackageTest()
                .configureHelloApp()
                .forTypes(PackageType.MAC)
                .addInitializer(cmd -> {
                    cmd.addArguments("--mac-sign",
                            "--mac-signing-keychain", SigningBase.getKeyChain());
                    if (signingKey) {
                        cmd.addArguments("--mac-signing-key-user-name",
                                         SigningBase.getDevName(certIndex));
                    } else {
                        if (signAppImage) {
                            cmd.addArguments("--mac-app-image-sign-identity",
                                             SigningBase.getAppCert(certIndex));
                        }
                        if (signPKG) {
                            cmd.addArguments("--mac-installer-sign-identity",
                                             SigningBase.getInstallerCert(certIndex));
                        }
                    }
                })
                .forTypes(PackageType.MAC_PKG)
                .addBundleVerifier(SigningPackageTest::verifyPKG)
                .forTypes(PackageType.MAC_DMG)
                .addInitializer(cmd -> {
                    if (!signingKey) {
                        // jpackage throws expected error with
                        // --mac-installer-sign-identity and DMG type
                        cmd.removeArgumentWithValue("--mac-installer-sign-identity");
                        // In case of not signing app image and DMG we need to
                        // remove signing completely, otherwise we will default
                        // to --mac-signing-key-user-name once
                        // --mac-installer-sign-identity is removed.
                        if (!signAppImage) {
                            cmd.removeArgumentWithValue("--mac-signing-keychain");
                            cmd.removeArgument("--mac-sign");
                        }
                    }
                })
                .addBundleVerifier(SigningPackageTest::verifyDMG)
                .addBundleVerifier(SigningPackageTest::verifyAppImageInDMG)
                .run();
    }
}
