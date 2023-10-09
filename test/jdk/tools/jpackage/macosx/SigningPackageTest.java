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
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTest
 */
public class SigningPackageTest {

    private static void verifyPKG(JPackageCommand cmd) {
        Path outputBundle = cmd.outputBundle();
        SigningBase.verifyPkgutil(outputBundle, getCertIndex(cmd));
        SigningBase.verifySpctl(outputBundle, "install", getCertIndex(cmd));
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
                SigningBase.verifyCodesign(launcherPath, true, getCertIndex(cmd));
                SigningBase.verifyCodesign(dmgImage, true, getCertIndex(cmd));
                SigningBase.verifySpctl(dmgImage, "exec", getCertIndex(cmd));
            }
        });
    }

    private static int getCertIndex(JPackageCommand cmd) {
        String devName = cmd.getArgumentValue("--mac-signing-key-user-name");
        return SigningBase.getDevNameIndex(devName);
    }

    @Test
    @Parameter("0")
    @Parameter("1")
    public static void test(int certIndex) throws Exception {
        SigningCheck.checkCertificates(certIndex);

        new PackageTest()
                .configureHelloApp()
                .forTypes(PackageType.MAC)
                .addInitializer(cmd -> {
                    cmd.addArguments("--mac-sign",
                            "--mac-signing-key-user-name", SigningBase.getDevName(certIndex),
                            "--mac-signing-keychain", SigningBase.getKeyChain());
                })
                .forTypes(PackageType.MAC_PKG)
                .addBundleVerifier(SigningPackageTest::verifyPKG)
                .forTypes(PackageType.MAC_DMG)
                .addBundleVerifier(SigningPackageTest::verifyDMG)
                .addBundleVerifier(SigningPackageTest::verifyAppImageInDMG)
                .run();
    }
}
