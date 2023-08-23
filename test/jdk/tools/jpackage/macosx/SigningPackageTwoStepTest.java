/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;

/**
 * Note: Testing unsgined app image is done to verify support for per-user
 * configuration by checking for PackageFile.
 * Tests generation of dmg and pkg from signed or unsigned predefined app image.
 * Test will generate pkg and verifies its signature. It verifies that dmg
 * is not signed, but app image inside dmg is signed or unsigned. This test
 * requires that the machine is configured with test certificate for
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
 * @summary jpackage with --type pkg,dmg --app-image
 * @library ../helpers
 * @library /test/lib
 * @library base
 * @key jpackagePlatformPackage
 * @build SigningBase
 * @build SigningCheck
 * @build jtreg.SkippedException
 * @build jdk.jpackage.test.*
 * @build SigningPackageTwoStepTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTwoStepTest
 */
public class SigningPackageTwoStepTest {

    private static void verifyPKG(JPackageCommand cmd) {
        if (!cmd.hasArgument("--mac-sign")) {
            return; // Nothing to check if not signed
        }

        Path outputBundle = cmd.outputBundle();
        SigningBase.verifyPkgutil(outputBundle, SigningBase.DEFAULT_INDEX);
        SigningBase.verifySpctl(outputBundle, "install", SigningBase.DEFAULT_INDEX);
    }

    private static void verifyDMG(JPackageCommand cmd) {
        // DMG always unsigned, so we will check it
        Path outputBundle = cmd.outputBundle();
        SigningBase.verifyDMG(outputBundle);
    }

    private static void verifyAppImageInDMG(JPackageCommand cmd) {
        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            // We will be called with all folders in DMG since JDK-8263155, but
            // we only need to verify app.
            if (dmgImage.endsWith(cmd.name() + ".app")) {
                boolean isSigned = cmd.hasArgument("--mac-sign");
                Path launcherPath = ApplicationLayout.platformAppImage()
                    .resolveAt(dmgImage).launchersDirectory().resolve(cmd.name());
                SigningBase.verifyCodesign(launcherPath, isSigned, SigningBase.DEFAULT_INDEX);
                SigningBase.verifyCodesign(dmgImage, isSigned, SigningBase.DEFAULT_INDEX);
                if (isSigned) {
                    SigningBase.verifySpctl(dmgImage, "exec", SigningBase.DEFAULT_INDEX);
                }
            }
        });
    }

    @Test
    @Parameter("true")
    @Parameter("false")
    public static void test(boolean signAppImage) throws Exception {
        SigningCheck.checkCertificates(0);

        Path appimageOutput = TKit.createTempDirectory("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--dest", appimageOutput);
        if (signAppImage) {
            appImageCmd.addArguments("--mac-sign")
                    .addArguments("--mac-signing-key-user-name",
                            SigningBase.getDevName(0))
                    .addArguments("--mac-signing-keychain",
                            SigningBase.getKeyChain());
        }

        new PackageTest()
                .addRunOnceInitializer(() -> appImageCmd.execute())
                .forTypes(PackageType.MAC)
                .addInitializer(cmd -> {
                    cmd.addArguments("--app-image", appImageCmd.outputBundle());
                    cmd.removeArgumentWithValue("--input");
                    if (signAppImage) {
                        cmd.addArguments("--mac-sign",
                                "--mac-signing-key-user-name",
                                SigningBase.getDevName(0),
                                "--mac-signing-keychain",
                                SigningBase.getKeyChain());
                    }
                })
                .forTypes(PackageType.MAC_PKG)
                .addBundleVerifier(SigningPackageTwoStepTest::verifyPKG)
                .forTypes(PackageType.MAC_DMG)
                .addBundleVerifier(SigningPackageTwoStepTest::verifyDMG)
                .addBundleVerifier(SigningPackageTwoStepTest::verifyAppImageInDMG)
                .run();
    }
}
