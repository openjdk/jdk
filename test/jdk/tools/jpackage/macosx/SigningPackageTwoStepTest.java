/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageFile;
import jdk.jpackage.test.PackageTest;
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

    @Test
    public static void testBundleSignedAppImage() {

        var appImageCmd = JPackageCommand.helloAppImage();

        var predefinedAppImageSignOption = predefinedAppImageSignOption();

        new PackageTest().addRunOnceInitializer(() -> {
            createPredefinedAppImage(appImageCmd, Optional.of(predefinedAppImageSignOption));
        }).usePredefinedAppImage(appImageCmd).addInitializer(cmd -> {
            configureOutputValidator(cmd, true, false);
        }).addInstallVerifier(cmd -> {
            MacSignVerify.verifyAppImageSigned(cmd, predefinedAppImageSignOption.certRequest());
        }).run();
    }

    public static Collection<Object[]> test() {

        List<TwoStepsTestSpec> data = new ArrayList<>();

        for (var signAppImage : List.of(true, false)) {
            Optional<SignKeyOptionWithKeychain> appImageSignOption;
            if (signAppImage) {
                // Sign the predefined app image bundle with the key not used in the jpackage command line being tested.
                // This way we can test if jpackage keeps or replaces the signature of
                // the predefined app image bundle when backing it in the pkg or dmg installer.
                appImageSignOption = Optional.of(predefinedAppImageSignOption());
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

    record TwoStepsTestSpec(Optional<SignKeyOptionWithKeychain> signAppImage, SigningPackageTest.TestSpec signPackage) {

        TwoStepsTestSpec {
            Objects.requireNonNull(signAppImage);
            Objects.requireNonNull(signPackage);
        }

        @Override
        public String toString() {
            return Stream.of(
                    String.format("app-image=%s", signAppImage.map(Objects::toString).orElse("unsigned")),
                    signPackage.toString()
            ).collect(Collectors.joining("; "));
        }

        Optional<ResolvableCertificateRequest> packagedAppImageSignIdentity() {
            return signAppImage.map(SignKeyOptionWithKeychain::certRequest);
        }

        void test() {

            var appImageCmd = JPackageCommand.helloAppImage();

            var test = signPackage.initTest().addRunOnceInitializer(() -> {
                createPredefinedAppImage(appImageCmd, signAppImage);
            }).usePredefinedAppImage(appImageCmd).addInitializer(cmd -> {
                configureOutputValidator(cmd,
                        signAppImage.isPresent(),
                        (cmd.packageType() == PackageType.MAC_PKG) && signPackage.packageSignOption().isPresent());
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

    private static SignKeyOptionWithKeychain predefinedAppImageSignOption() {
        // Sign the predefined app image bundle with the key not used in the jpackage command line being tested.
        // This way we can test if jpackage keeps or replaces the signature of the input app image bundle.
        return new SignKeyOptionWithKeychain(
                SignKeyOption.Type.SIGN_KEY_USER_SHORT_NAME,
                SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD,
                SigningBase.StandardKeychain.MAIN.keychain());
    }

    private static void createPredefinedAppImage(JPackageCommand appImageCmd, Optional<SignKeyOptionWithKeychain> signAppImage) {
        Objects.requireNonNull(appImageCmd);
        Objects.requireNonNull(signAppImage);

        appImageCmd.setFakeRuntime().setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

        signAppImage.ifPresentOrElse(signOption -> {
            signOption.setTo(appImageCmd);

            MacSign.withKeychain(_ -> {
                appImageCmd.execute(0);
            }, signOption.keychain());

            // Verify that the predefined app image is signed.
            MacSignVerify.verifyAppImageSigned(appImageCmd, signOption.certRequest());
        }, () -> {
            appImageCmd.execute(0);
        });
    }

    private static void configureOutputValidator(JPackageCommand cmd, boolean signAppImage, boolean signPackage) {
        var signedPredefinedAppImageWarning = JPackageStringBundle.MAIN.cannedFormattedString(
                "warning.per.user.app.image.signed",
                PackageFile.getPathInAppImage(Path.of("")));

        var signedInstallerFromUnsignedPredefinedAppImageWarning =
                JPackageStringBundle.MAIN.cannedFormattedString("warning.unsigned.app.image", "pkg");

        // The warnings are mutually exclusive
        final Optional<CannedFormattedString> expected;
        final List<CannedFormattedString> unexpected = new ArrayList<>();

        if (signAppImage) {
            expected = Optional.of(signedPredefinedAppImageWarning);
        } else {
            unexpected.add(signedPredefinedAppImageWarning);
            if (signPackage) {
                expected = Optional.of(signedInstallerFromUnsignedPredefinedAppImageWarning);
            } else {
                expected = Optional.empty();
                unexpected.add(signedInstallerFromUnsignedPredefinedAppImageWarning);
            }
        }

        expected.ifPresent(cmd::validateOut);
        unexpected.forEach(str -> {
            cmd.validateOut(TKit.assertTextStream(cmd.getValue(str)).negate());
        });
    }
}
