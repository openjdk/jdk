/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.AdditionalLauncher;

/**
 * Tests generation of app image with --mac-sign and related arguments. Test will
 * generate app image and verify signature of main launcher and app bundle itself.
 * This test requires that machine is configured with test certificate for
 * "Developer ID Application: jpackage.openjdk.java.net" or alternately
 * "Developer ID Application: " + name specified by system property:
 * "jpackage.mac.signing.key.user.name"
 * in the jpackagerTest keychain (or alternately the keychain specified with
 * the system property "jpackage.mac.signing.keychain".
 * If this certificate is self-signed, it must have be set to
 * always allowed access to this keychain" for user which runs test.
 * (If cert is real (not self signed), the do not set trust to allow.)
 */

/*
 * @test
 * @summary jpackage with --type app-image --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build SigningAppImageTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningAppImageTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningAppImageTest {

    @Test
    // ({"sign or not", "signing-key or sign-identity", "certificate index"})
    // Sign, signing-key and ASCII certificate
    @Parameter({"true", "true", "ASCII_INDEX"})
    // Sign, signing-key and UNICODE certificate
    @Parameter({"true", "true", "UNICODE_INDEX"})
    // Sign, signing-indentity and UNICODE certificate
    @Parameter({"true", "false", "UNICODE_INDEX"})
    // Unsigned
    @Parameter({"false", "true", "INVALID_INDEX"})
    public void test(boolean doSign, boolean signingKey, SigningBase.CertIndex certEnum) throws Exception {
        final var certIndex = certEnum.value();

        JPackageCommand cmd = JPackageCommand.helloAppImage();
        if (doSign) {
            cmd.addArguments("--mac-sign",
                    "--mac-signing-keychain",
                    SigningBase.getKeyChain());
            if (signingKey) {
                cmd.addArguments("--mac-signing-key-user-name",
                        SigningBase.getDevName(certIndex));
            } else {
                cmd.addArguments("--mac-app-image-sign-identity",
                        SigningBase.getAppCert(certIndex));
            }
        }
        AdditionalLauncher testAL = new AdditionalLauncher("testAL");
        testAL.applyTo(cmd);
        cmd.executeAndAssertHelloAppImageCreated();

        Path launcherPath = cmd.appLauncherPath();
        SigningBase.verifyCodesign(launcherPath, doSign, certIndex);

        Path testALPath = launcherPath.getParent().resolve("testAL");
        SigningBase.verifyCodesign(testALPath, doSign, certIndex);

        Path appImage = cmd.outputBundle();
        SigningBase.verifyCodesign(appImage, doSign, certIndex);
        if (doSign) {
            SigningBase.verifySpctl(appImage, "exec", certIndex);
        }
    }
}
