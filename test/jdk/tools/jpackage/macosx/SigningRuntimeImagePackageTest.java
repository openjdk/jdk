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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

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
 * "jpackage.openjdk.java.net" can be over-ridden by system property
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

    private static JPackageCommand addSignOptions(JPackageCommand cmd, int certIndex) {
        if (certIndex != SigningBase.CertIndex.INVALID_INDEX.value()) {
            cmd.addArguments(
                    "--mac-sign",
                    "--mac-signing-keychain", SigningBase.getKeyChain(),
                    "--mac-signing-key-user-name", SigningBase.getDevName(certIndex));
        }
        return cmd;
    }

    private static Path createInputRuntimeImage() throws IOException {

        final Path runtimeImageDir;

        if (JPackageCommand.DEFAULT_RUNTIME_IMAGE != null) {
            runtimeImageDir = JPackageCommand.DEFAULT_RUNTIME_IMAGE;
        } else {
            runtimeImageDir = TKit.createTempDirectory("runtime-image").resolve("data");

            new Executor().setToolProvider(JavaTool.JLINK)
                    .dumpOutput()
                    .addArguments(
                            "--output", runtimeImageDir.toString(),
                            "--add-modules", "java.desktop",
                            "--strip-debug",
                            "--no-header-files",
                            "--no-man-pages")
                    .execute();
        }

        return runtimeImageDir;
    }

    private static Path createInputRuntimeBundle(int certIndex) throws IOException {

        final var runtimeImage = createInputRuntimeImage();

        final var runtimeBundleWorkDir = TKit.createTempDirectory("runtime-bundle");

        final var unpackadeRuntimeBundleDir = runtimeBundleWorkDir.resolve("unpacked");

        var cmd = new JPackageCommand()
                .useToolProvider(true)
                .ignoreDefaultRuntime(true)
                .dumpOutput(true)
                .setPackageType(PackageType.MAC_DMG)
                .setArgumentValue("--name", "foo")
                .addArguments("--runtime-image", runtimeImage)
                .addArguments("--dest", runtimeBundleWorkDir);

        addSignOptions(cmd, certIndex);

        cmd.execute();

        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            if (dmgImage.endsWith(cmd.appInstallationDirectory().getFileName())) {
                Executor.of("cp", "-R")
                        .addArgument(dmgImage)
                        .addArgument(unpackadeRuntimeBundleDir)
                        .execute(0);
            }
        });

        return unpackadeRuntimeBundleDir;
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
                            SigningBase.CertIndex jdkBundleCert,
                            SigningBase.CertIndex signCert) throws Exception {

        final Path inputRuntime[] = new Path[1];

        new PackageTest()
                .addRunOnceInitializer(() -> {
                    if (useJDKBundle) {
                        inputRuntime[0] = createInputRuntimeBundle(jdkBundleCert.value());
                    } else {
                        inputRuntime[0] = createInputRuntimeImage();
                    }
                })
                .addInitializer(cmd -> {
                    cmd.addArguments("--runtime-image", inputRuntime[0]);
                    // Remove --input parameter from jpackage command line as we don't
                    // create input directory in the test and jpackage fails
                    // if --input references non existent directory.
                    cmd.removeArgumentWithValue("--input");
                    addSignOptions(cmd, signCert.value());
                })
                .addInstallVerifier(cmd -> {
                    final var certIndex = Stream.of(signCert, jdkBundleCert)
                            .filter(Predicate.isEqual(SigningBase.CertIndex.INVALID_INDEX).negate())
                            .findFirst().orElse(SigningBase.CertIndex.INVALID_INDEX).value();

                    final var signed = certIndex != SigningBase.CertIndex.INVALID_INDEX.value();

                    final var unfoldedBundleDir = cmd.appRuntimeDirectory();

                    final var libjli = unfoldedBundleDir.resolve("Contents/MacOS/libjli.dylib");

                    SigningBase.verifyCodesign(libjli, signed, certIndex);
                    SigningBase.verifyCodesign(unfoldedBundleDir, signed, certIndex);
                    if (signed) {
                        SigningBase.verifySpctl(unfoldedBundleDir, "exec", certIndex);
                    }
                })
                .run();
    }
}
