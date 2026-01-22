/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;

/**
 * Tests bundling of .pkg and .dmg packages with various signing options.
 *
 * <p>
 * Prerequisites: Keychains with self-signed certificates as specified in
 * {@link SigningBase.StandardKeychain#MAIN} and
 * {@link SigningBase.StandardKeychain#SINGLE}.
 */


/*
 * @test
 * @summary jpackage with --type pkg,dmg --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningPackageTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @requires (jpackage.test.SQETest != null)
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTest.test
 *  --jpt-space-subst=*
 *  --jpt-include=({--mac-signing-key-user-name:*CODESIGN},*{--mac-signing-key-user-name:*PKG},*MAC_DMG+MAC_PKG)
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */

/*
 * @test
 * @summary jpackage with --type pkg,dmg --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror SigningPackageTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningPackageTest.test
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class SigningPackageTest {

    @Test
    @ParameterSupplier
    public static void test(TestSpec spec) {
        MacSign.withKeychain(_ -> {
            spec.test();
        }, spec.keychain());
    }

    public static Collection<Object[]> test() {
        return TestSpec.testCases(true).stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    record TestSpec(
            Optional<SignKeyOption> appImageSignOption,
            Optional<SignKeyOption> packageSignOption,
            Set<PackageType> packageTypes) {

        TestSpec {
            Objects.requireNonNull(appImageSignOption);
            Objects.requireNonNull(packageSignOption);
            Objects.requireNonNull(packageTypes);

            if (appImageSignOption.isEmpty() && packageSignOption.isEmpty()) {
                // No signing.
                throw new IllegalArgumentException();
            }

            if (packageTypes.isEmpty() || !PackageType.MAC.containsAll(packageTypes)) {
                // Invalid package types.
                throw new IllegalArgumentException();
            }

            if (packageSignOption.isPresent()) {
                if (!packageTypes.contains(PackageType.MAC_PKG)) {
                    // .pkg installer should be signed, but .pkg type is missing.
                    throw new IllegalArgumentException();
                }

                if (appImageSignOption.isEmpty()) {
                    if (packageSignOption.get().type() != SignKeyOption.Type.SIGN_KEY_IDENTITY) {
                        // They request to sign the .pkg installer without
                        // the "--mac-installer-sign-identity" option,
                        // but didn't specify a signing option for the packaged app image.
                        // This is wrong because only the "--mac-installer-sign-identity" option
                        // allows signing a .pkg installer without signing its packaged app image.
                        throw new IllegalArgumentException();
                    }
                } else if (appImageSignOption.get().type() != packageSignOption.get().type()) {
                    // Signing option types should be the same.
                    throw new IllegalArgumentException();
                }
            }

            if (!(packageTypes instanceof SequencedSet)) {
                packageTypes = new TreeSet<>(packageTypes);
            }
        }

        TestSpec(
                Optional<SignKeyOption> appImageSignOption,
                Optional<SignKeyOption> packageSignOption,
                PackageType... packageTypes) {
            this(appImageSignOption, packageSignOption, Set.of(packageTypes));
        }

        @Override
        public String toString() {
            return Stream.of(
                    signKeyOptions(),
                    Stream.of(packageTypes.stream().map(Object::toString).collect(Collectors.joining("+")))
            ).flatMap(x -> x).map(Object::toString).collect(Collectors.joining(", "));
        }

        Stream<SignKeyOption> signKeyOptions() {
            return Stream.concat(appImageSignOption.stream(), packageSignOption.stream());
        }

        Optional<ResolvableCertificateRequest> bundleSignIdentity(PackageType type) {
            switch (type) {
                case MAC_DMG -> {
                    return Optional.empty();
                }
                case MAC_PKG -> {
                    return packageSignOption.map(SignKeyOption::certRequest);
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        }

        void test() {
            initTest().configureHelloApp().addInstallVerifier(cmd -> {
                appImageSignOption.map(SignKeyOption::certRequest).ifPresent(signIdentity -> {
                    MacSignVerify.verifyAppImageSigned(cmd, signIdentity);
                });
            }).run();
        }

        PackageTest initTest() {
            return new PackageTest().forTypes(packageTypes).mutate(test -> {
                appImageSignOption.ifPresent(signOption -> {
                    test.addInitializer(signOption::setTo);
                });
                packageSignOption.ifPresent(signOption -> {
                    test.forTypes(PackageType.MAC_PKG, () -> {
                        test.addInitializer(signOption::setTo);
                    });
                });
            }).addBundleVerifier(cmd -> {
                bundleSignIdentity(cmd.packageType()).ifPresent(signIdentity -> {
                    MacSignVerify.verifyPkgSigned(cmd, signIdentity);
                });
            }).addInitializer(MacHelper.useKeychain(keychain())::accept);
        }

        MacSign.ResolvedKeychain keychain() {
            return SigningBase.StandardKeychain.MAIN.keychain();
        }

        static List<TestSpec> testCases(boolean withUnicode) {

            List<TestSpec> data = new ArrayList<>();

            List<List<SigningBase.StandardCertificateRequest>> certRequestGroups;
            if (withUnicode) {
                certRequestGroups = List.of(
                        List.of(SigningBase.StandardCertificateRequest.CODESIGN, SigningBase.StandardCertificateRequest.PKG),
                        List.of(SigningBase.StandardCertificateRequest.CODESIGN_UNICODE, SigningBase.StandardCertificateRequest.PKG_UNICODE)
                );
            } else {
                certRequestGroups = List.of(
                        List.of(SigningBase.StandardCertificateRequest.CODESIGN, SigningBase.StandardCertificateRequest.PKG)
                );
            }

            for (var certRequests : certRequestGroups) {
                for (var signIdentityType : SignKeyOption.Type.defaultValues()) {
                    var keychain = SigningBase.StandardKeychain.MAIN.keychain();
                    var appImageSignKeyOption = new SignKeyOption(signIdentityType, certRequests.getFirst(), keychain);
                    var pkgSignKeyOption = new SignKeyOption(signIdentityType, certRequests.getLast(), keychain);

                    switch (signIdentityType) {
                        case SIGN_KEY_IDENTITY -> {
                            // Use "--mac-installer-sign-identity" and "--mac-app-image-sign-identity" signing options.
                            // They allows to sign the packaged app image and the installer (.pkg) separately.
                            data.add(new TestSpec(Optional.of(appImageSignKeyOption), Optional.empty(), PackageType.MAC));
                            data.add(new TestSpec(Optional.empty(), Optional.of(pkgSignKeyOption), PackageType.MAC_PKG));
                        }
                        case SIGN_KEY_USER_SHORT_NAME -> {
                            // Use "--mac-signing-key-user-name" signing option with short user name or implicit signing option.
                            // It signs both the packaged app image and the installer (.pkg).
                            // Thus, if the installer is not signed, it can be used only with .dmg packaging.
                            data.add(new TestSpec(Optional.of(appImageSignKeyOption), Optional.empty(), PackageType.MAC_DMG));
                        }
                        case SIGN_KEY_USER_FULL_NAME -> {
                            // Use "--mac-signing-key-user-name" signing option with the full user name.
                            // Like SIGN_KEY_USER_SHORT_NAME, jpackage will try to use it to sign both
                            // the packaged app image and the installer (.pkg).
                            // It will fail to sign the installer, though, because the signing identity is unsuitable.
                            // That is why, use it with .dmg packaging only and not with .pkg packaging.
                            data.add(new TestSpec(Optional.of(appImageSignKeyOption), Optional.empty(), PackageType.MAC_DMG));
                            continue;
                        }
                        default -> {
                            // SignKeyOption.Type.defaultValues() should return
                            // such a sequence that makes this code location unreachable.
                            throw ExceptionBox.reachedUnreachable();
                        }
                    }
                    data.add(new TestSpec(Optional.of(appImageSignKeyOption), Optional.of(pkgSignKeyOption), PackageType.MAC));
                }
            }

            return data;
        }
    }
}
