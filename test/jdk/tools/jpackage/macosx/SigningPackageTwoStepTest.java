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

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.SignKeyOption;
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
 * Prerequisites: A keychain with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN}.
 */

/*
 * @test
 * @summary jpackage with --type pkg,dmg --app-image
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @key jpackagePlatformPackage
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build SigningPackageTwoStepTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTwoStepTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningPackageTwoStepTest {

    @Test
    @ParameterSupplier
    public static void test(TestSpec spec) {
        MacSign.withKeychain(toConsumer(keychain -> {
            spec.test(keychain);
        }), SigningBase.StandardKeychain.MAIN.keychain());
    }

    public record TestSpec(Optional<SignKeyOption> signAppImage, Map<PackageType, SignKeyOption> signPackage) {

        public TestSpec {
            Objects.requireNonNull(signAppImage);
            Objects.requireNonNull(signPackage);

            if ((signAppImage.isEmpty() && signPackage.isEmpty()) || !PackageType.MAC.containsAll(signPackage.keySet())) {
                // Unexpected package types.
                throw new IllegalArgumentException();
            }

            // Ensure stable result of toString() call.
            if (!SortedMap.class.isInstance(signPackage)) {
                signPackage = new TreeMap<>(signPackage);
            }
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();

            signAppImage.ifPresent(signOption -> {
                sb.append(String.format("app-image=%s", signOption));
            });

            if (!sb.isEmpty() && !signPackage.isEmpty()) {
                sb.append("; ");
            }

            if (!signPackage.isEmpty()) {
                sb.append(signPackage);
            }

            return sb.toString();
        }

        boolean signNativeBundle() {
            return signPackage.isEmpty();
        }

        static Builder build() {
            return new Builder();
        }

        static class Builder {

            TestSpec create() {
                return new TestSpec(Optional.ofNullable(signAppImage), signPackage);
            }

            Builder certRequest(SigningBase.StandardCertificateRequest v) {
                return certRequest(v.spec());
            }

            Builder certRequest(MacSign.CertificateRequest v) {
                certRequest = Objects.requireNonNull(v);
                return this;
            }

            Builder signIdentityType(SignKeyOption.Type v) {
                signIdentityType = Objects.requireNonNull(v);
                return this;
            }

            Builder signAppImage() {
                signAppImage = createSignKeyOption();
                return this;
            }

            Builder signPackage(PackageType type) {
                Objects.requireNonNull(type);
                signPackage.put(type, createSignKeyOption());
                return this;
            }

            Builder signPackage() {
                PackageType.MAC.forEach(this::signPackage);
                return this;
            }

            private SignKeyOption createSignKeyOption() {
                return new SignKeyOption(signIdentityType, certRequest);
            }

            private MacSign.CertificateRequest certRequest = SigningBase.StandardCertificateRequest.CODESIGN.spec();
            private SignKeyOption.Type signIdentityType = SignKeyOption.Type.SIGN_KEY_IDENTITY;

            private SignKeyOption signAppImage;
            private Map<PackageType, SignKeyOption> signPackage = new HashMap<>();
        }

        void test(MacSign.ResolvedKeychain keychain) {

            var appImageCmd = JPackageCommand.helloAppImage().setFakeRuntime();
            MacHelper.useKeychain(appImageCmd, keychain);
            signAppImage.ifPresent(signOption -> {
                signOption.setTo(appImageCmd);
            });

            var test = new PackageTest();

            signAppImage.map(SignKeyOption::certRequest).ifPresent(certRequest -> {
                // The predefined app image is signed, verify bundled app image is signed too.
                test.addInstallVerifier(cmd -> {
                    MacSignVerify.verifyAppImageSigned(cmd, certRequest, keychain);
                });
            });

            Optional.ofNullable(signPackage.get(PackageType.MAC_PKG)).map(SignKeyOption::certRequest).ifPresent(certRequest -> {
                test.forTypes(PackageType.MAC_PKG, () -> {
                    test.addBundleVerifier(cmd -> {
                        MacSignVerify.verifyPkgSigned(cmd, certRequest, keychain);
                    });
                });
            });

            test.forTypes(signPackage.keySet()).addRunOnceInitializer(() -> {
                appImageCmd.setArgumentValue("--dest", TKit.createTempDirectory("appimage")).execute(0);
            }).addInitializer(cmd -> {
                MacHelper.useKeychain(cmd, keychain);
                cmd.addArguments("--app-image", appImageCmd.outputBundle());
                cmd.removeArgumentWithValue("--input");
                Optional.ofNullable(signPackage.get(cmd.packageType())).ifPresent(signOption -> {
                    signOption.setTo(cmd);
                });

                if (signAppImage.isPresent()) {
                    // Predefined app image is signed. Expect a warning.
                    cmd.validateOutput(JPackageStringBundle.MAIN.cannedFormattedString(
                            "warning.per.user.app.image.signed",
                            PackageFile.getPathInAppImage(Path.of(""))));
                } else if (cmd.packageType() == PackageType.MAC_PKG && signPackage.containsKey(cmd.packageType())) {
                    // Create signed ".pkg" bundle from the unsigned predefined app image. Expect a warning.
                    cmd.validateOutput(JPackageStringBundle.MAIN.cannedFormattedString("warning.unsigned.app.image", "pkg"));
                }
            })
            .run();
        }
    }

    public static Collection<Object[]> test() {

        List<TestSpec.Builder> data = new ArrayList<>();

        Stream.of(SignKeyOption.Type.values()).flatMap(signIdentityType -> {
            return Stream.of(
                    // Sign both predefined app image and native package.
                    TestSpec.build().signIdentityType(signIdentityType)
                            .signAppImage()
                            .signPackage()
                            .certRequest(SigningBase.StandardCertificateRequest.PKG)
                            .signPackage(PackageType.MAC_PKG),

                    // Don't sign predefined app image, sign native package.
                    TestSpec.build().signIdentityType(signIdentityType)
                            .signPackage()
                            .certRequest(SigningBase.StandardCertificateRequest.PKG)
                            .signPackage(PackageType.MAC_PKG),

                    // Sign predefined app image, don't sign native package.
                    TestSpec.build().signIdentityType(signIdentityType).signAppImage()
            );
        }).forEach(data::add);

        return data.stream().map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }
}
