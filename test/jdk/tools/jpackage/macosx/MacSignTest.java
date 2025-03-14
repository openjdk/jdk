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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
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
import jdk.jpackage.test.TKit;

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
        BASIC("jpackageTest.keychain", cert().userName("jpackage.openjdk.java.net").create())
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

        static Stream<Keychain> findKeychains(CertificateRequest certificateRequest) {
            Objects.requireNonNull(certificateRequest);
            return Stream.of(values()).filter(v -> {
                return v.spec.certificateRequests().contains(certificateRequest);
            }).map(StandardKeychain::keychain);
        }

        final KeychainWithCertsSpec spec;
    }

    public static void setUp() {
        final var keychainSpecs = Stream.of(StandardKeychain.values()).map(StandardKeychain::spec).toList();

        TKit.trace("Signing environment:");
        for (int i = 0; i != keychainSpecs.size(); ++i) {
            TKit.trace(String.format("[%d/%d] %s", i + 1, keychainSpecs.size(), keychainSpecs.get(i)));
        }

        // Reset keychain search list to defaults.
        Keychain.addToSearchList(List.of());

        // Init basic keychain from scratch.
        // This will create the keychain file and the key pair.
        StandardKeychain.BASIC.keychain().create().createKeyPair("jpackage test key");

        // Use the same private key to create certificates in additional keychains.
        for (final var keychainSpec : Stream.of(StandardKeychain.values()).filter(Predicate.isEqual(StandardKeychain.BASIC).negate()).map(StandardKeychain::spec).toList()) {
            final var keychainFile = keychainSpec.keychain().path();
            TKit.trace(String.format("Copy basic keychain in [%s] file", keychainFile));
            try {
                Files.copy(StandardKeychain.BASIC.keychain().path(), keychainFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        MacSign.withTempDirectory(dir -> {
            // Create certificates.
            final var certPemFiles = MacSign.createCertificates(StandardKeychain.BASIC.keychain(), keychainSpecs, dir);

            final Map<Path, Keychain> trustConfig = new HashMap<>();

            for (final var certPemFile : certPemFiles.entrySet()) {
                // Import the certificate in all keychains it belongs to.
                final var keychains = StandardKeychain.findKeychains(certPemFile.getKey()).toList();
                keychains.forEach(keychain -> {
                    MacSign.security("import", certPemFile.getValue().normalize().toString(),
                            "-k", keychain.name(),
                            "-f", "pemseq",
                            "-t", "agg",
                            "-A").execute();
                });

                trustConfig.put(certPemFile.getValue(), keychains.getFirst());
            }

            // Trust certificates.
            MacSign.trustCertificates(trustConfig);
        });

        Keychain.addToSearchList(keychainSpecs.stream().map(KeychainWithCertsSpec::keychain).toList());
    }

    public static void tearDown() {
        Stream.of(StandardKeychain.values()).map(StandardKeychain::spec).forEachOrdered(KeychainWithCertsSpec::tearDown);
    }

    public record SignTestSpec(List<StandardKeychain> searchList, Optional<String> keychainName,
            Optional<String> signIdentity, Optional<String> keyUserName, List<CannedFormattedString> expectedErrors) {

        final static class Builder {

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
