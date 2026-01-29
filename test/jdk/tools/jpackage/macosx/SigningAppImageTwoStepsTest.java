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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/**
 * Tests signing of a signed/unsigned predefined app image.
 *
 * <p>
 * Prerequisites: Keychains with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN} and
 * {@link SigningBase.StandardKeychain#SINGLE}.
 */

/*
 * @test
 * @summary jpackage with --type app-image --app-image "appImage" --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningAppImageTwoStepsTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningAppImageTwoStepsTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningAppImageTwoStepsTest {

    @Test
    @ParameterSupplier
    public static void test(TestSpec spec) {
        spec.test();
    }

    public record TestSpec(Optional<SignKeyOptionWithKeychain> signAppImage, SignKeyOptionWithKeychain sign) {

        public TestSpec {
            Objects.requireNonNull(signAppImage);
            Objects.requireNonNull(sign);
        }

        @Override
        public String toString() {
            return Stream.of(
                    String.format("app-image=%s", signAppImage.map(Objects::toString).orElse("unsigned")),
                    sign.toString()
            ).collect(Collectors.joining("; "));
        }

        static Builder build() {
            return new Builder();
        }

        static class Builder {

            TestSpec create() {
                return new TestSpec(Optional.ofNullable(signAppImage), sign);
            }

            Builder certRequest(SigningBase.StandardCertificateRequest v) {
                certRequest = Objects.requireNonNull(v);
                return this;
            }

            Builder signIdentityType(SignKeyOption.Type v) {
                signIdentityType = Objects.requireNonNull(v);
                return this;
            }

            Builder sign() {
                sign = createSignKeyOption();
                return this;
            }

            Builder signAppImage() {
                signAppImage = createSignKeyOption();
                return this;
            }

            private SignKeyOptionWithKeychain createSignKeyOption() {
                return new SignKeyOptionWithKeychain(
                        signIdentityType,
                        certRequest,
                        SigningBase.StandardKeychain.MAIN.keychain());
            }

            private SigningBase.StandardCertificateRequest certRequest = SigningBase.StandardCertificateRequest.CODESIGN;
            private SignKeyOption.Type signIdentityType = SignKeyOption.Type.SIGN_KEY_IDENTITY;

            private SignKeyOptionWithKeychain signAppImage;
            private SignKeyOptionWithKeychain sign;
        }

        void test() {
            var appImageCmd = JPackageCommand.helloAppImage()
                    .setFakeRuntime()
                    .setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

            // Add an additional launcher
            AdditionalLauncher testAL = new AdditionalLauncher("testAL");
            testAL.applyTo(appImageCmd);

            signAppImage.ifPresentOrElse(signOption -> {
                MacSign.withKeychain(keychain -> {
                    signOption.addTo(appImageCmd);
                    appImageCmd.execute();
                    MacSignVerify.verifyAppImageSigned(appImageCmd, signOption.certRequest());
                }, signOption.keychain());
            }, appImageCmd::execute);

            var cmd = new JPackageCommand()
                    .setPackageType(PackageType.IMAGE)
                    .addArguments("--app-image", appImageCmd.outputBundle())
                    .mutate(sign::addTo);

            cmd.executeAndAssertHelloAppImageCreated();
            MacSignVerify.verifyAppImageSigned(cmd, sign.certRequest());
        }
    }

    public static Collection<Object[]> test() {

        List<TestSpec> data = new ArrayList<>();

        for (var appImageSign : withAndWithout(SignKeyOption.Type.SIGN_KEY_IDENTITY)) {
            var builder = TestSpec.build();
            appImageSign.ifPresent(signIdentityType -> {
                // Sign the input app image bundle with the key not used in the jpackage command line being tested.
                // This way we can test if jpackage keeps or replaces the signature of the input app image bundle.
                builder.signIdentityType(signIdentityType)
                        .certRequest(SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD)
                        .signAppImage();
            });
            for (var signIdentityType : SignKeyOption.Type.defaultValues()) {
                builder.signIdentityType(signIdentityType)
                        .certRequest(SigningBase.StandardCertificateRequest.CODESIGN);
                data.add(builder.sign().create());
            }
        }

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static <T> List<Optional<T>> withAndWithout(T value) {
        return List.of(Optional.empty(), Optional.of(value));
    }
}
