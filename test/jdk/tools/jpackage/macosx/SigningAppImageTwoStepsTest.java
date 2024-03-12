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

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.AdditionalLauncher;

/**
 * Tests generation of app image and then signs generated app image with --mac-sign
 * and related arguments. Test will generate app image and verify signature of main
 * launcher and app bundle itself. This test requires that machine is configured with
 * test certificate for "Developer ID Application: jpackage.openjdk.java.net" or
 * alternately "Developer ID Application: " + name specified by system property:
 * "jpackage.mac.signing.key.user.name" in the jpackagerTest keychain
 * (or alternately the keychain specified with the system property
 * "jpackage.mac.signing.keychain". If this certificate is self-signed, it must
 * have be set to always allowed access to this keychain" for user which runs test.
 * (If cert is real (not self signed), the do not set trust to allow.)
 */

/*
 * @test
 * @summary jpackage with --type app-image --app-image "appImage" --mac-sign
 * @library ../helpers
 * @library /test/lib
 * @library base
 * @build SigningBase
 * @build SigningCheck
 * @build jtreg.SkippedException
 * @build jdk.jpackage.test.*
 * @build SigningAppImageTwoStepsTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningAppImageTwoStepsTest
 */
public class SigningAppImageTwoStepsTest {

    @Test
    // ({"sign or not", "signing-key or sign-identity"})
    // Sign and signing-key
    @Parameter({"true", "true"})
    // Sign and sign-identity
    @Parameter({"true", "false"})
    // Unsigned
    @Parameter({"false", "true"})
    public void test(String... testArgs) throws Exception {
        boolean signAppImage = Boolean.parseBoolean(testArgs[0]);
        boolean signingKey = Boolean.parseBoolean(testArgs[1]);

        SigningCheck.checkCertificates(SigningBase.DEFAULT_INDEX);

        Path appimageOutput = TKit.createTempDirectory("appimage");

        // Generate app image. Signed or unsigned based on test
        // parameter. We should able to sign predfined app images
        // which are signed or unsigned.
        JPackageCommand appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--dest", appimageOutput);
        if (signAppImage) {
            appImageCmd.addArguments("--mac-sign",
                    "--mac-signing-keychain",
                    SigningBase.getKeyChain());
            if (signingKey) {
                appImageCmd.addArguments("--mac-signing-key-user-name",
                    SigningBase.getDevName(SigningBase.DEFAULT_INDEX));
            } else {
                appImageCmd.addArguments("--mac-app-image-sign-identity",
                    SigningBase.getAppCert(SigningBase.DEFAULT_INDEX));
            }
        }

        // Add addtional launcher
        AdditionalLauncher testAL = new AdditionalLauncher("testAL");
        testAL.applyTo(appImageCmd);

        // Generate app image
        appImageCmd.executeAndAssertHelloAppImageCreated();

        // Double check if it is signed or unsigned based on signAppImage
        SigningBase.verifyAppImageSignature(appImageCmd, signAppImage, "testAL");

        // Sign app image
        JPackageCommand cmd = new JPackageCommand();
        cmd.setPackageType(PackageType.IMAGE)
            .addArguments("--app-image", appImageCmd.outputBundle().toAbsolutePath())
            .addArguments("--mac-sign")
            .addArguments("--mac-signing-keychain", SigningBase.getKeyChain());
        if (signingKey) {
            cmd.addArguments("--mac-signing-key-user-name",
                SigningBase.getDevName(SigningBase.DEFAULT_INDEX));
        } else {
            cmd.addArguments("--mac-app-image-sign-identity",
                SigningBase.getAppCert(SigningBase.DEFAULT_INDEX));
        }
        cmd.executeAndAssertImageCreated();

        // Should be signed app image
        SigningBase.verifyAppImageSignature(appImageCmd, true, "testAL");
    }
}
