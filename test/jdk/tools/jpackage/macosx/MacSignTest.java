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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.Keychain;
import jdk.jpackage.test.MacSign.KeychainWithCertsSpec;

/*
 * @test
 * @summary jpackage signing on macOS
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror MacSignTest.java
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacSignTest
 */
public class MacSignTest {

    public enum StandardKeychain {
        BASIC("jpackageTest.keychain", cert().userName("jpackage.openjdk.java.net").create()),
        UNICODE("jpackageTest-unicode.keychain", cert().userName("jpackage.openjdk.java.net (ö)").create()),
        SAME_NAME("jpackageTest-same-name.keychain",
                cert().userName("jpackage.openjdk.java.net").create(),
                cert().days(100).userName("jpackage.openjdk.java.net").create()),
        ;

        Keychain keychain() {
            return spec.keychain();
        }

        KeychainWithCertsSpec spec() {
            return spec;
        }

        StandardKeychain(String keychainName, CertificateRequest cert, CertificateRequest... otherCerts) {
            final var builder = keychain(keychainName).addCert(cert);
            List.of(otherCerts).forEach(builder::addCert);
            this.spec = builder.create();
        }

        private static KeychainWithCertsSpec.Builder keychain(String name) {
            return new KeychainWithCertsSpec.Builder().name(name);
        }

        private static CertificateRequest.Builder cert() {
            return new CertificateRequest.Builder();
        }

        final KeychainWithCertsSpec spec;
    }

    public static void setUp() {
        MacSign.setUp(Stream.of(StandardKeychain.values()).map(StandardKeychain::spec).toList());
    }

    public static void tearDown() {
        MacSign.tearDown(Stream.of(StandardKeychain.values()).map(StandardKeychain::spec).toList());
    }

    public record SignTestSpec(List<StandardKeychain> searchList, Optional<String> keychainName,
            Optional<String> signIdentity, Optional<String> keyUserName, List<CannedFormattedString> expectedErrors) {

        static final class Builder {

            Builder withKeychainName(String v) {
                keychainName = v;
                return withKeychainName(keychainName == null);
            }

            Builder withKeychainName(boolean v) {
                withKeychainName = v;
                return this;
            }

            Builder keyUserName(String v) {
                keyUserName = v;
                return this;
            }

            Builder signIdentity(String v) {
                signIdentity = v;
                return this;
            }

            public Builder addToSearchList(StandardKeychain v) {
                searchList.add(v);
                return this;
            }

            Builder errors(List<CannedFormattedString> v) {
                expectedErrors.addAll(v);
                return this;
            }

            Builder errors(CannedFormattedString... v) {
                return errors(List.of(v));
            }

            Builder error(String key, Object ... args) {
                return errors(JPackageStringBundle.MAIN.cannedFormattedString(key, args));
            }

            SignTestSpec create() {
                return new SignTestSpec(List.copyOf(searchList),
                        withKeychainName ? Optional.ofNullable(keychainName).or(() -> {
                            return Optional.of(searchList.getFirst().keychain().name());
                        }) : Optional.empty(),
                        Optional.ofNullable(signIdentity),
                        Optional.ofNullable(keyUserName),
                        List.copyOf(expectedErrors));
            }

            private final List<StandardKeychain> searchList = new ArrayList<>();
            private String keychainName;
            private boolean withKeychainName;
            private String keyUserName;
            private String signIdentity;
            private final List<CannedFormattedString> expectedErrors = new ArrayList<>();
        }

        public SignTestSpec {
            Objects.requireNonNull(searchList);
            searchList.forEach(Objects::requireNonNull);

            Objects.requireNonNull(keychainName);

            Objects.requireNonNull(signIdentity);
            Objects.requireNonNull(keyUserName);
            if (signIdentity.isPresent() == keyUserName.isPresent()) {
                throw new IllegalArgumentException();
            }

            Objects.requireNonNull(expectedErrors);
            expectedErrors.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append("keychain-search-list=").append(searchList);
            keychainName.ifPresent(v -> sb.append("; keychain=").append(v));
            signIdentity.ifPresent(v -> sb.append("; sign-identity=").append(v));
            keyUserName.ifPresent(v -> sb.append("; sign-user-name=").append(v));
            if (!expectedErrors.isEmpty()) {
                sb.append("; ").append(expectedErrors);
            }
            return sb.toString();
        }

        Session createSession() {
            return new Session();
        }

        final class Session implements Closeable {

            Session configure(JPackageCommand cmd) {
                cmd.addArguments("--mac-sign");
                keychainName.ifPresent(v -> cmd.addArguments("--mac-signing-keychain",v));
                signIdentity.ifPresent(v -> cmd.addArguments("--mac-app-image-sign-identity",v));
                keyUserName.ifPresent(v -> cmd.addArguments("--mac-signing-key-user-name",v));

                if (!expectedErrors.isEmpty()) {
                    cmd.validateOutput(expectedErrors.toArray(CannedFormattedString[]::new));
                }

                return this;
            }

            Session executeAndVerify(JPackageCommand cmd) {
                final Executor.Result result;
                if (expectedErrors.isEmpty()) {
                    result = cmd.executeAndAssertHelloAppImageCreated();
                } else {
                    result = cmd.execute(1);
                }
                return verify(cmd, result);
            }

            Session verify(JPackageCommand cmd, Executor.Result result) {
                return this;
            }

            private Session() {
                Keychain.addToSearchList(searchList.stream().map(StandardKeychain::keychain).toList());
            }

            @Override
            public void close() {
                Keychain.addToSearchList(List.of());
            }
        }
    }

    @Test
    @ParameterSupplier
    public void testAppImage(SignTestSpec spec) {
        final var cmd = JPackageCommand.helloAppImage().ignoreDefaultRuntime(true);
        try (final var session = spec.createSession()) {
            session.configure(cmd).executeAndVerify(cmd);
        }
    }

    public static Collection<Object[]> testAppImage() {
        return Stream.of(
                testSpec().addToSearchList(StandardKeychain.BASIC).keyUserName("jpackage.openjdk.java.net")
        ).map(SignTestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static SignTestSpec.Builder testSpec() {
        return new SignTestSpec.Builder();
    }
}
