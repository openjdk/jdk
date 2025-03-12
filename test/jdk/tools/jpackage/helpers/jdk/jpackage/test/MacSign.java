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
package jdk.jpackage.test;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

public final class MacSign {

    public record KeychainWithCertsSpec(Keychain keychain, List<CertificateRequest> certificateRequests,
            List<CertificateWithPrivateKey> certificates) {

        public KeychainWithCertsSpec {
            Objects.requireNonNull(keychain);
            Objects.requireNonNull(certificates);
            Objects.requireNonNull(certificateRequests);
            certificateRequests.forEach(Objects::requireNonNull);
            if (!certificates.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        public KeychainWithCertsSpec(Keychain keychain, List<CertificateRequest> certificateRequests) {
            this(keychain, certificateRequests, new ArrayList<>());
        }

        public boolean setUp(boolean overwriteExisting) {
            final boolean created = keychain.create(overwriteExisting);
            if (created) {
                certificates.addAll(certificateRequests.stream().map(CertificateRequest::createCertificate).toList());
                certificates.stream().map(CertificateWithPrivateKey::asPemData).forEachOrdered(keychain::importEntities);
            }
            return created;
        }

        public void tearDown() {
            keychain.delete();
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(keychain);
            if (!certificateRequests.isEmpty()) {
                sb.append("; certs=").append(certificateRequests);
            }
            if (!certificates.isEmpty()) {
                sb.append("; cached-certs=").append(certificates);
            }
            return sb.toString();
        }

        public final static class Builder {

            public Builder name(String v) {
                keychainBuilder.name(v);
                return this;
            }

            public Builder password(String v) {
                keychainBuilder.password(v);
                return this;
            }

            public Builder addCert(CertificateRequest v) {
                certs.add(v);
                return this;
            }

            public Builder addCert(CertificateRequest.Builder v) {
                return addCert(v.create());
            }

            public KeychainWithCertsSpec create() {
                final var keychain = keychainBuilder.create();
                return new KeychainWithCertsSpec(keychain, List.copyOf(certs));
            }

            private Keychain.Builder keychainBuilder = new Keychain.Builder();
            private List<CertificateRequest> certs = new ArrayList<>();
        }
    }

    public record Keychain(String name, String password) {
        public Keychain {
            Objects.requireNonNull(name);
            Objects.requireNonNull(password);
        }

        public final static class Builder {

            public Builder name(String v) {
                name = v;
                return this;
            }

            public Builder password(String v) {
                password = v;
                return this;
            }

            public Keychain create() {
                return new Keychain(validatedName(), validatedPassword());
            }

            private String validatedName() {
                return Optional.ofNullable(name).orElse("jpackagerTest.keychain");
            }

            private String validatedPassword() {
                return Optional.ofNullable(password).orElse("test");
            }

            private String name;
            private String password;
        }

        public boolean create(boolean overwriteExisting) {
            final var exec = createExecutor("create-keychain");
            final var result = exec.saveOutput().executeWithoutExitCodeCheck();
            boolean created = true;
            if (result.getExitCode() == 48 && result.getFirstLineOfOutput().endsWith("A keychain with the same name already exists.")) {
                if (overwriteExisting) {
                    delete();
                    exec.saveOutput(false).execute();
                } else {
                    created = false;
                }
            } else {
                result.assertExitCodeIsZero();
            }

            // Ensure the keychain is unlocked.
            unlock();

            // Set auto-lock timeout to unlimited and remove auto-lock on sleep.
            Executor.of("security", "set-keychain-settings", name).dumpOutput().execute();
            return created;
        }

        public Keychain delete() {
            Executor.of("security", "delete-keychain", name).dumpOutput().execute();
            return this;
        }
        
        public Keychain unlock() {
            createExecutor("unlock-keychain").execute();
            return this;
        }

        public static void addToSearchList(Collection<Keychain> keychains) {
            Executor.of("security", "list-keychains", "-d", "user", "-s", "login.keychain")
                    .addArguments(keychains.stream().map(Keychain::name).toList())
                    .dumpOutput().execute();
        }

        public Keychain importCertificate(CertificateRequest certRequest) {
            return importEntities(certRequest.createCertificate().asPemData());
        }

        public Keychain importEntities(Stream<PemData> entries) {
            withTempDirectory(dir -> {
                final var entitiesFile = dir.resolve("container.pem");
                Files.deleteIfExists(entitiesFile);
                entries.forEachOrdered(e -> {
                    e.save(entitiesFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                });
                Executor.of("security",
                        "import", entitiesFile.normalize().toString(),
                        "-k", name,
                        "-f", "pemseq",
                        "-t", "agg",
                        "-A").dumpOutput().execute();
            });
            return this;
        }

        private Executor createExecutor(String command) {
            return Executor.of("security", command, "-p", password, name).dumpOutput();
        }
    }

    public enum CertificateType {
        CODE_SIGN(List.of(
                "basicConstraints=critical,CA:false",
                "keyUsage=critical,digitalSignature",
                // Code Signing
                "extendedKeyUsage=critical,1.3.6.1.5.5.7.3.3"
        )),
        // https://www.apple.com/certificateauthority/pdf/Apple_Developer_ID_CPS_v1.1.pdf
        // https://security.stackexchange.com/questions/17909/how-to-create-an-apple-installer-package-signing-certificate
        // https://github.com/zschuessler/AXtendedKey/blob/32c8ccec3df7e78fe521d09c48bd20558b3a4a24/src/axtended_key/services/certificate_manager.py#L109C102-L109C115
        INSTALLER(List.of(
                "basicConstraints=critical,CA:false",
                "keyUsage=critical,digitalSignature",
                // Apple Custom Extended Key Usage (EKU) packageSign
                // https://oid-base.com/get/1.2.840.113635.100.4.13
                "extendedKeyUsage=critical,1.2.840.113635.100.4.13",
                // Apple-specific extension for self-distributed apps
                // https://oid-base.com/get/1.2.840.113635.100.6.1.14
                "1.2.840.113635.100.6.1.14=critical,DER:0500"
        ));

        CertificateType(List<String> extensions) {
            this.extensions = extensions;
        }

        final List<String> extensions;
    }

    public record CertificateWithPrivateKey(X509Certificate cert, PrivateKey key) {
        public CertificateWithPrivateKey {
            Objects.requireNonNull(cert);
            Objects.requireNonNull(key);
        }

        Stream<PemData> asPemData() {
            return Stream.of(PemData.of(key), PemData.of(cert));
        }
    }

    public record CertificateRequest(String name, CertificateType type, int days) {
        public CertificateRequest {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
        }

        public final static class Builder {

            public Builder userName(String v) {
                userName = v;
                return this;
            }

            public Builder commonName(String v) {
                commonName = v;
                return this;
            }

            public Builder type(CertificateType v) {
                type = v;
                return this;
            }

            public Builder days(int v) {
                days = v;
                return this;
            }

            public CertificateRequest create() {
                return new CertificateRequest(validatedCN(), type, days);
            }

            private String validatedUserName() {
                return Objects.requireNonNull(userName);
            }

            private String validatedCN() {
                return Optional.ofNullable(commonName).orElseGet(() -> {
                    switch (type) {
                        case CODE_SIGN -> {
                            return "Developer ID Application: " + validatedUserName();
                        }
                        case INSTALLER -> {
                            return "Developer ID Installer: " + validatedUserName();
                        }
                        default -> {
                            throw new UnsupportedOperationException();
                        }
                    }
                });
            }

            private String userName;
            private String commonName; // CN
            private CertificateType type = CertificateType.CODE_SIGN;
            private int days = 3650;
        }

        public CertificateWithPrivateKey createCertificate() {

            final var certWithKey = new CertificateWithPrivateKey[1];

            withTempDirectory(certsDir -> {
                final var cfgFile = certsDir.resolve("cert.cfg");
                final var certFile = certsDir.resolve("cert.crt");
                final var keyFile = certsDir.resolve("key.pem");

                final var key = KEY_GEN.genKeyPair().getPrivate();

                PemData.of(key).save(keyFile);

                Files.write(cfgFile, List.of(
                        "[ req ]",
                        "distinguished_name = req_name",
                        "prompt=no",
                        "[ req_name ]",
                        "CN=" + name
                ));

                final var openssl = Executor.of("openssl", "req", "-x509",
                        "-days", Integer.toString(days), "-sha256", "-nodes",
                        "-key", keyFile.normalize().toString(),
                        "-config", cfgFile.normalize().toString(),
                        "-out", certFile.normalize().toString());

                type.extensions.forEach(ext -> {
                    openssl.addArgument("-addext");
                    openssl.addArgument(ext);
                });

                openssl.dumpOutput().execute();

                try (final var in = Files.newInputStream(certFile)) {
                    certWithKey[0] = new CertificateWithPrivateKey(
                            (X509Certificate)CERT_FACTORY.generateCertificate(in), key);
                }

            });

            return certWithKey[0];
        }

        private final static CertificateFactory CERT_FACTORY = toSupplier(() -> {
            return CertificateFactory.getInstance("X.509");
        }).get();

        private final static KeyPairGenerator KEY_GEN = toSupplier(() -> {
            final var keygen = KeyPairGenerator.getInstance("RSA");
            keygen.initialize(2048);
            return keygen;
        }).get();
    }

    public record PemData(String label, byte[] data) {
        public PemData {
            Objects.requireNonNull(label);
            Objects.requireNonNull(data);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(frame("BEGIN " + label));
            sb.append(ENCODER.encodeToString(data));
            sb.append("\n");
            sb.append(frame("END " + label));
            return sb.toString();
        }

        public static PemData of(X509Certificate cert) {
            return new PemData("CERTIFICATE", toSupplier(cert::getEncoded).get());
        }

        public static PemData of(PrivateKey key) {
            return new PemData("PRIVATE KEY", toSupplier(key::getEncoded).get());
        }

        public void save(Path path, OpenOption... options) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, toString(), options);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static String frame(String str) {
            return String.format("-----%s-----\n", Objects.requireNonNull(str));
        }

        private final static Base64.Encoder ENCODER = Base64.getMimeEncoder(64, "\n".getBytes());
    }

    public static void withTempDirectory(ThrowingConsumer<Path> callback) {
        try {
            final var dir = Files.createTempDirectory("jdk.jpackage.test");
            try {
                ThrowingConsumer.toConsumer(callback).accept(dir);
            } finally {
                TKit.deleteDirectoryRecursive(dir, "");
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
