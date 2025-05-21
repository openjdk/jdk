/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;

import jdk.jpackage.test.ApplicationLayout;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.Executor;
import jdk.jpackage.internal.MacBaseInstallerBundler;
import jdk.jpackage.internal.MacAppImageBuilder;

/**
 * Tests generation of dmg and pkg with --mac-sign and related arguments.
 * Test will generate pkg and verifies its signature. It verifies that dmg
 * is not signed, but runtime image inside dmg is signed.
 *
 * Note: Specific UNICODE signing is not tested, since it is shared code
 * with app image signing and it will be covered by SigningPackageTest.
 *
 * Following combinations are tested:
 * 1) "--runtime-image" points to unsigned JDK bundle and --mac-sign is not
 * provided. Expected result: runtime image ad-hoc signed.
 * 2) "--runtime-image" points to unsigned JDK bundle and --mac-sign is
 * provided. Expected result: Everything is signed with provided certificate.
 * 3) "--runtime-image" points to signed JDK bundle and --mac-sign is not
 * provided. Expected result: runtime image is signed with original certificate.
 * 4) "--runtime-image" points to signed JDK bundle and --mac-sign is provided.
 * Expected result: runtime image is signed with provided certificate.
 * 5) "--runtime-image" points to JDK image and --mac-sign is not provided.
 * Expected result: runtime image ad-hoc signed.
 * 6) "--runtime-image" points to JDK image and --mac-sign is provided.
 * Expected result: Everything is signed with provided certificate.
 *
 * This test requires that the machine is configured with test certificate for
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
 * @summary jpackage with --type pkg,dmg --runtime-image --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @key jpackagePlatformPackage
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build SigningRuntimeImagePackageTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningRuntimeImagePackageTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningRuntimeImagePackageTest {

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

    private static void verifyRuntimeImageInDMG(JPackageCommand cmd,
                                                boolean isRuntimeImageSigned,
                                                int JDKBundleCertIndex) {
        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            Path launcherPath = ApplicationLayout.platformAppImage()
                    .resolveAt(dmgImage).launchersDirectory().resolve("libjli.dylib");
            // We will be called with all folders in DMG since JDK-8263155, but
            // we only need to verify app.
            if (dmgImage.endsWith(cmd.name() + ".jdk")) {
                SigningBase.verifyCodesign(launcherPath, isRuntimeImageSigned,
                        JDKBundleCertIndex);
                SigningBase.verifyCodesign(dmgImage, isRuntimeImageSigned,
                        JDKBundleCertIndex);
                if (isRuntimeImageSigned) {
                    SigningBase.verifySpctl(dmgImage, "exec", JDKBundleCertIndex);
                }
            }
        });
    }

    private static boolean isPKGSigned(JPackageCommand cmd) {
        return cmd.hasArgument("--mac-signing-key-user-name") ||
               cmd.hasArgument("--mac-installer-sign-identity");
    }

    private static int getCertIndex(JPackageCommand cmd) {
        if (cmd.hasArgument("--mac-signing-key-user-name")) {
            String devName = cmd.getArgumentValue("--mac-signing-key-user-name");
            return SigningBase.getDevNameIndex(devName);
        } else {
            return SigningBase.CertIndex.INVALID_INDEX.value();
        }
    }

    private static Path getRuntimeImagePath(boolean useJDKBundle,
                                            boolean isRuntimeImageSigned,
                                            int JDKBundleCertIndex) throws IOException {
        final Path runtimeImageDir =
                TKit.createTempDirectory("runtimeimage").resolve("data");

        new Executor()
            .setToolProvider(JavaTool.JLINK)
            .dumpOutput()
            .addArguments(
                "--output", runtimeImageDir.toString(),
                "--add-modules", "java.desktop",
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages")
            .execute();

        if (useJDKBundle) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", "Foo");
            params.put("runtime-image", runtimeImageDir);

            final Path runtimeBundleDir =
                TKit.createTempDirectory("runtimebundle").resolve("data");

            MacBaseInstallerBundler.convertJDKImageToJDKBundle(runtimeBundleDir,
                runtimeImageDir, params);

            if (isRuntimeImageSigned) {
                params = new LinkedHashMap<>();
                params.put("name", "Foo");
                params.put("runtime-image", runtimeBundleDir);
                params.put("mac-sign", Boolean.TRUE);
                params.put("mac-signing-keychain",
                        SigningBase.getKeyChain());
                params.put("mac-signing-key-user-name",
                        SigningBase.getDevName(JDKBundleCertIndex));

                MacAppImageBuilder.doSigning(params, runtimeBundleDir, false);
            }

            return runtimeBundleDir;
        } else {
            return runtimeImageDir;
        }
    }

    @Test
    // useJDKBundle  - If "true" predefined runtime image will be converted to
    //                 JDK bundle. If "false" JDK image will be used.
    // JDKBundleCert - Certificate to sign JDK bundle before calling jpackage.
    // signCert      - Certificate to sign bundle produced by jpackage.
    // 1) unsigned JDK bundle and --mac-sign is not provided
    @Parameter({"true", "INVALID_INDEX", "INVALID_INDEX"})
    // 2) unsigned JDK bundle and --mac-sign is provided
    @Parameter({"true", "INVALID_INDEX", "ASCII_INDEX"})
    // 3) signed JDK bundle and --mac-sign is not provided
    @Parameter({"true", "UNICODE_INDEX", "INVALID_INDEX"})
    // 4) signed JDK bundle and --mac-sign is provided
    @Parameter({"true", "UNICODE_INDEX", "ASCII_INDEX"})
    // 5) JDK image and --mac-sign is not provided
    @Parameter({"false", "INVALID_INDEX", "INVALID_INDEX"})
    // 6) JDK image and --mac-sign is provided
    @Parameter({"false", "INVALID_INDEX", "ASCII_INDEX"})
    public static void test(boolean useJDKBundle,
                            SigningBase.CertIndex JDKBundleCert,
                            SigningBase.CertIndex signCert) throws Exception {
        final int JDKBundleCertIndex = JDKBundleCert.value();
        final int signCertIndex = signCert.value();

        final boolean isRuntimeImageSigned =
            (JDKBundleCertIndex != SigningBase.CertIndex.INVALID_INDEX.value());
        final boolean isSigned =
            (signCertIndex != SigningBase.CertIndex.INVALID_INDEX.value());

        new PackageTest()
                .forTypes(PackageType.MAC)
                .addInitializer(cmd -> {
                    cmd.addArguments("--runtime-image",
                        getRuntimeImagePath(useJDKBundle,
                            isRuntimeImageSigned, JDKBundleCertIndex));
                    // Remove --input parameter from jpackage command line as we don't
                    // create input directory in the test and jpackage fails
                    // if --input references non existant directory.
                    cmd.removeArgumentWithValue("--input");

                    if (isSigned) {
                        cmd.addArguments("--mac-sign",
                                "--mac-signing-keychain", SigningBase.getKeyChain());
                        cmd.addArguments("--mac-signing-key-user-name",
                                         SigningBase.getDevName(signCertIndex));
                    }
                })
                .forTypes(PackageType.MAC_PKG)
                .addBundleVerifier(SigningRuntimeImagePackageTest::verifyPKG)
                .forTypes(PackageType.MAC_DMG)
                .addBundleVerifier(SigningRuntimeImagePackageTest::verifyDMG)
                .addBundleVerifier(cmd -> {
                    int certIndex = SigningBase.CertIndex.INVALID_INDEX.value();
                    if (isSigned)
                        certIndex = signCertIndex;
                    else if (isRuntimeImageSigned)
                        certIndex = JDKBundleCertIndex;
                    verifyRuntimeImageInDMG(cmd, isRuntimeImageSigned || isSigned,
                        certIndex);
                })
                .run();
    }
}
