/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageFile;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/**
 * Tests packaging of a signed/unsigned predefined app image into a
 * signed/unsigned .pkg or .dmg package.
 *
 * <p>
 * Prerequisites: Keychains with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN} and
 * {@link SigningBase.StandardKeychain#SINGLE}.
 */

/*
 * @test
 * @summary jpackage with --type pkg,dmg --app-image
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningPackageTest.java
 * @compile -Xlint:all -Werror SigningPackageTwoStepTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTwoStepTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningPackageTwoStepTest {

    @Test
    @ParameterSupplier
    public static void test(TwoStepsTestSpec spec) {
        spec.test();
    }

    public static Collection<Object[]> test() {

        List<TwoStepsTestSpec> data = new ArrayList<>();

        for (var signAppImage : List.of(true, false)) {
            Optional<SignKeyOption> appImageSignOption;
            if (signAppImage) {
                // Sign the predefined app image bundle with the key not used in the jpackage command line being tested.
                // This way we can test if jpackage keeps or replaces the signature of
                // the predefined app image bundle when backing it in the pkg or dmg installer.
                appImageSignOption = Optional.of(new SignKeyOption(
                        SignKeyOption.Type.SIGN_KEY_USER_NAME,
                        SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD.resolveIn(SigningBase.StandardKeychain.MAIN)));
            } else {
                appImageSignOption = Optional.empty();
            }

            for (var signPackage : SigningPackageTest.TestSpec.testCases(false)) {
                data.add(new TwoStepsTestSpec(appImageSignOption, signPackage));
            }
        }

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    record TwoStepsTestSpec(Optional<SignKeyOption> signAppImage, SigningPackageTest.TestSpec signPackage) {

        TwoStepsTestSpec {
            Objects.requireNonNull(signAppImage);
            Objects.requireNonNull(signPackage);
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<>();
            signAppImage.ifPresent(v -> {
                tokens.add(String.format("app-image=%s", v));
            });
            tokens.add(signPackage);
            return tokens.stream().map(Objects::toString).collect(Collectors.joining("; "));
        }

        Optional<ResolvableCertificateRequest> packagedAppImageSignIdentity() {
            return signAppImage.map(SignKeyOption::certRequest);
        }

        void test() {

            var appImageCmd = JPackageCommand.helloAppImage().setFakeRuntime();
            signAppImage.ifPresent(signOption -> {
                MacSign.withKeychain(keychain -> {
                    MacHelper.useKeychain(appImageCmd, keychain);
                    signOption.setTo(appImageCmd);
                }, SigningBase.StandardKeychain.MAIN.keychain());
            });

            var test = signPackage.initTest().addRunOnceInitializer(() -> {
                appImageCmd.setArgumentValue("--dest", TKit.createTempDirectory("appimage")).execute(0);
                signAppImage.map(SignKeyOption::certRequest).ifPresent(certRequest -> {
                    // The predefined app image is signed, verify that.
                    MacSignVerify.verifyAppImageSigned(appImageCmd, certRequest);
                });
            }).usePredefinedAppImage(appImageCmd).addInitializer(cmd -> {
                if (signAppImage.isPresent()) {
                    // Predefined app image is signed. Expect a warning.
                    cmd.validateOutput(JPackageStringBundle.MAIN.cannedFormattedString(
                            "warning.per.user.app.image.signed",
                            PackageFile.getPathInAppImage(Path.of(""))));
                } else if (cmd.packageType() == PackageType.MAC_PKG && signPackage.packageSignOption().isPresent()) {
                    // Create signed ".pkg" bundle from the unsigned predefined app image. Expect a warning.
                    cmd.validateOutput(JPackageStringBundle.MAIN.cannedFormattedString("warning.unsigned.app.image", "pkg"));
                }
            }).addInstallVerifier(cmd -> {
                packagedAppImageSignIdentity().ifPresent(certRequest -> {
                    MacSignVerify.verifyAppImageSigned(cmd, certRequest);
                });
            });

            MacSign.withKeychain(_ -> {
                test.run();
            }, signPackage.keychain());
        }
    }
}
