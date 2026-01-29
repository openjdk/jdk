/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.JPackageCommand.RuntimeImageType.RUNTIME_TYPE_FAKE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.MacSignVerify.SpctlType;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;

/**
 * Tests generation of dmg and pkg with --mac-sign and related arguments. Test
 * will generate pkg and verifies its signature. It verifies that dmg is not
 * signed, but runtime image inside dmg is signed.
 *
 * <p>
 * Note: Specific UNICODE signing is not tested, since it is shared code with
 * app image signing and it will be covered by SigningPackageTest.
 *
 * <p>
 * Prerequisites: Keychains with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN} and
 * {@link SigningBase.StandardKeychain#SINGLE}.
 */

/*
 * @test
 * @summary jpackage with --type pkg,dmg --runtime-image --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningPackageTest.java
 * @compile -Xlint:all -Werror SigningRuntimeImagePackageTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningRuntimeImagePackageTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningRuntimeImagePackageTest {

    @Test
    @ParameterSupplier
    public static void test(RuntimeTestSpec spec) {
        spec.test();
    }

    @Test
    public static void testBundleSignedRuntime() {

        Slot<Path> predefinedRuntime = Slot.createEmpty();

        var signRuntime = runtimeImageSignOption();

        new PackageTest().addRunOnceInitializer(() -> {
            predefinedRuntime.set(createRuntime(Optional.of(signRuntime), RuntimeType.BUNDLE));
        }).addInitializer(cmd -> {
            cmd.ignoreDefaultRuntime(true);
            cmd.removeArgumentWithValue("--input");
            cmd.setArgumentValue("--runtime-image", predefinedRuntime.get());
        }).addInstallVerifier(cmd -> {
            MacSignVerify.verifyAppImageSigned(cmd, signRuntime.certRequest());
        }).run();
    }

    public static Collection<Object[]> test() {

        List<RuntimeTestSpec> data = new ArrayList<>();

        for (var runtimeSpec : List.of(
                Map.entry(RuntimeType.IMAGE, false /* unsigned */),
                Map.entry(RuntimeType.BUNDLE, false /* unsigned */),
                Map.entry(RuntimeType.BUNDLE, true /* signed */)
        )) {
            var runtimeType = runtimeSpec.getKey();
            var signRuntime = runtimeSpec.getValue();

            Optional<SignKeyOptionWithKeychain> runtimeSignOption;
            if (signRuntime) {
                // Sign the runtime bundle with the key not used in the jpackage command line being tested.
                // This way we can test if jpackage keeps or replaces the signature of
                // the predefined runtime bundle when backing it in the pkg or dmg installer.
                runtimeSignOption = Optional.of(runtimeImageSignOption());
            } else {
                runtimeSignOption = Optional.empty();
            }

            for (var signPackage : SigningPackageTest.TestSpec.testCases(false)) {
                data.add(new RuntimeTestSpec(runtimeSignOption, runtimeType, signPackage));
            }
        }

        return data.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    enum RuntimeType {
        IMAGE,
        BUNDLE,
        ;
    }

    record RuntimeTestSpec(
            Optional<SignKeyOptionWithKeychain> signRuntime,
            RuntimeType runtimeType,
            SigningPackageTest.TestSpec signPackage) {

        RuntimeTestSpec {
            Objects.requireNonNull(signRuntime);
            Objects.requireNonNull(runtimeType);
            Objects.requireNonNull(signPackage);
        }

        @Override
        public String toString() {
            var runtimeToken = new StringBuilder();
            runtimeToken.append("runtime={").append(runtimeType);
            signRuntime.ifPresent(v -> {
                runtimeToken.append(", ").append(v);
            });
            runtimeToken.append('}');
            return Stream.of(runtimeToken, signPackage).map(Objects::toString).collect(Collectors.joining("; "));
        }

        Optional<ResolvableCertificateRequest> packagedAppImageSignIdentity() {
            if (runtimeType == RuntimeType.IMAGE) {
                return signPackage.appImageSignOption().map(SignKeyOption::certRequest);
            } else {
                return signPackage.appImageSignOption().or(() -> {
                    return signRuntime.map(SignKeyOptionWithKeychain::signKeyOption);
                }).map(SignKeyOption::certRequest);
            }
        }

        void test() {

            Slot<Path> predefinedRuntime = Slot.createEmpty();

            var test = signPackage.initTest().addRunOnceInitializer(() -> {
                predefinedRuntime.set(createRuntime(signRuntime, runtimeType));
            }).addInitializer(cmd -> {
                cmd.ignoreDefaultRuntime(true);
                cmd.removeArgumentWithValue("--input");
                cmd.setArgumentValue("--runtime-image", predefinedRuntime.get());
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

    private static SignKeyOptionWithKeychain runtimeImageSignOption() {
        // Sign the runtime bundle with the key not used in the jpackage command line being tested.
        // This way we can test if jpackage keeps or replaces the signature of
        // the predefined runtime bundle when backing it in the pkg or dmg installer.
        return new SignKeyOptionWithKeychain(
                SignKeyOption.Type.SIGN_KEY_USER_SHORT_NAME,
                SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD,
                SigningBase.StandardKeychain.MAIN.keychain());
    }

    private static Path createRuntime(Optional<SignKeyOptionWithKeychain> signRuntime, RuntimeType runtimeType) {
        if (runtimeType == RuntimeType.IMAGE && signRuntime.isEmpty()) {
            return JPackageCommand.createInputRuntimeImage(RUNTIME_TYPE_FAKE);
        } else {
            Slot<Path> runtimeBundle = Slot.createEmpty();

            MacSign.withKeychain(keychain -> {
                var runtimeBundleBuilder = MacHelper.buildRuntimeBundle();
                signRuntime.ifPresent(signingOption -> {
                    runtimeBundleBuilder.mutator(signingOption::setTo);
                });
                runtimeBundle.set(runtimeBundleBuilder.type(RUNTIME_TYPE_FAKE).create());
            }, SigningBase.StandardKeychain.MAIN.keychain());

            if (runtimeType == RuntimeType.IMAGE) {
                return MacBundle.fromPath(runtimeBundle.get()).orElseThrow().homeDir();
            } else {
                // Verify the runtime bundle is properly signed/unsigned.
                signRuntime.map(SignKeyOptionWithKeychain::certRequest).ifPresentOrElse(certRequest -> {
                    MacSignVerify.assertSigned(runtimeBundle.get(), certRequest);
                    var signOrigin = MacSignVerify.findSpctlSignOrigin(SpctlType.EXEC, runtimeBundle.get()).orElse(null);
                    TKit.assertEquals(certRequest.name(), signOrigin,
                            String.format("Check [%s] has sign origin as expected", runtimeBundle.get()));
                }, () -> {
                    MacSignVerify.assertAdhocSigned(runtimeBundle.get());
                });
                return runtimeBundle.get();
            }
        }
    }
}
