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

/**
 * Utilities to setup identities and keychains for sign testing.
 * <p>
 * Identity is a pair of a private key and a certificate created with this
 * private key. It is used for signing. Keychain is a storage for identities.
 *
 * <ul>
 * <li>"/usr/bin/security" command line tool manages keychains and identities.
 * <li>"/usr/bin/codesign" command line tool uses identities to sign files and
 * folders.
 * <li>"Keychain Access" is GUI application to manage keychains and identities.
 * </ul>
 *
 * <ol>
 * <h1>How do you create an identity for testing?</h1>
 * <li>Create a private RSA key.
 * <li>Create a self-signed certificate using the key from step #1.
 * <li>Save both the private key and the certificate from steps #1 and #2 in a
 * keychain.
 * <li>Trust the certificate from step #2.
 * </ol>
 *
 * There are few options each above steps can be executed (applies to macOS Sequoia).
 *
 * <h2>1. Create a private RSA key</h2>
 *
 * <h3>1.1. Use openssl</h3>
 *
 * This is the obvious approach given openssl will be used to create a
 * certificate from the step #2. You can create a key and a certificate with a
 * single openssl command. However the catch is importing the private key in a
 * keystore.
 * <p>
 * All private keys imported in a keychain with {@code /usr/bin/security import}
 * command have "Imported Private Key" label. There is no other tool to change
 * key labels but "Keychain Access".
 * <p>
 * On top of that the internal name of the key can NOT be changed at all. The
 * internal name of the key is the name assigned by /usr/bin/security command
 * when it imports it. It derives it from the name of a file from which it
 * imports the key. E.g. say "identity.pem" file stores an identity, then
 *
 * <pre>
 * /usr/bin/security identity.pem -f pemseq -k foo.keychain
 * </pre>
 *
 * command will import a private key with internal name "identity" and name
 * "Imported Private Key" in "foo.keychain" keychain. You will see "Imported
 * Private Key" in the list of keys in "Keychain Access" app, but when you will
 * use this key with /usr/bin/codesign it will prompt to enter keychain password
 * to access "identity" key. Even after you give a better name to this key
 * instead of "Imported Private Key", /usr/bin/codesign will keep referring this
 * key as "identity".
 *
 * <h3>1.2. Use /usr/bin/security</h3>
 *
 * Use
 *
 * <pre>
 * /usr/bin/security create-keypair -a rsa -s 2048 -k foo.keychain -A "My key"
 * </pre>
 *
 * command to create RSA key pair in "foo.keychain" keychain. Both private and
 * public keys will be named "My key". This way, you can get an adequately
 * identified private key in a keychain.
 * <p>
 * You can extract the key to use with openssl for certificate creation with
 * {@code /usr/bin/security export} command.
 *
 * <h2>2. Create a self-signed certificate</h2>
 *
 * No brainier, use openssl. If a private key was created with
 * {@code /usr/bin/security create-keypair} command it needs to be extracted
 * from the keychain in a format suitable for openssl. This can be achieved with
 * the following command:
 *
 * <pre>
 * /usr/bin/security export -f pkcs12 -k foo.keychain -t privKeys -P "" -o key.p12
 * </pre>
 *
 * The above command will save all private keys in a "foo.keychain" keychain in
 * a PKCS#12-encoded "key.p12" file with an empty passphrase. <blockquote> Note
 * #1: Given there is no way to extract specific private key from a keystore
 * make sure it contains a single private key. </blockquote> <blockquote> Note
 * #2: You can't extract private key in PEM format, i.e.
 * {@code /usr/bin/security export -f pemseq -t privKeys} command will fail.
 * </blockquote>
 * <p>
 * The inferior alternative is to use the GUI "Certificate Assistant" from
 * "Keychain Access".
 *
 * <h2>3. Save both private key and the certificate from steps #1 and #2 in a
 * keychain</h2>
 *
 * Assume private key has been created using
 * {@code /usr/bin/security create-keypair} command and is already in a keychain
 * you only need to import a certificate created in step #2. If the certificate
 * was saved in "cert.pem" file in step #2, the following command:
 *
 * <pre>
 * /usr/bin/security import cert.pem -f pemseq -k foo.keychain
 * </pre>
 *
 * will import a certificate from "cert.pem" file in "foo.keychain" keychain.
 *
 * <h2>4. Trust the certificate from step #2</h2>
 *
 * An untrusted certificate can NOT be used with /usr/bin/codesign. Use
 *
 * <pre>
 * /usr/bin/security security add-trusted-cert -k foo.keychain cert.pem
 * </pre>
 *
 * command to add trusted certificate from "cert.pem" file in "foo.keychain"
 * keychain. If the certificate is already in the keychain it will be marked
 * trusted.
 * <p>
 * This step can not be automated as there is no way to avoid entering a user
 * password.
 * <p>
 * Running this command with sudo doesn't help - it will ask for password twice
 * (unless you configure sudo not to ask a passowrd, in this case it will ask
 * once).
 * <p>
 * Running this command from ssh session will fail with the following error
 * message:
 *
 * <pre>
 * SecTrustSettingsSetTrustSettings: The authorization was denied since no user interaction was possible.
 * </pre>
 *
 * User interaction can NOT be avoided for this step. This is a deliberate
 * security constrained according to <a href=
 * "https://developer.apple.com/documentation/macos-release-notes/macos-big-sur-11_0_1-release-notes#Security"
 * target="_blank">Big Sur Release Notes</a> and
 * <a href="https://developer.apple.com/forums/thread/671582" target=
 * "_blank">Apple Developer Forum</a>.
 *
 * <p>
 * Another automation bottleneck is using /usr/bin/codesign with a trusted
 * certificate. For signing /usr/bin/codesign requires trusted certificate and
 * access to the corresponding private key. The first time it requests access to
 * the key it will trigger authorization dialog requesting access to the private
 * key:
 * <p>
 * <img src=
 * "https://developer.apple.com/forums/content/attachment/2d8b6237-b47c-43fe-bf2e-73717bb4f421"
 * alt="Request authorization to private key dialog">
 * <p>
 * Even if the private key has /usr/bin/codesign in the access control list or
 * if the key is configured to allow access of any application the dialogue will
 * be triggered. See <a href=
 * "https://stackoverflow.com/questions/3864770/how-do-i-add-authorizations-to-code-sign-an-app-from-new-keychain-without-any-hu?rq=3"
 * target="_blank">Discussion of the authorization dialog at Stackoverflow</a>.
 * After pressing the "Always Allow" button, the dialog will not be triggered
 * for subsequent invocations of /usr/bin/codesign with the identity.
 * <p>
 * If user interaction is impossible (ssh session), codesign will fail with the
 * following cryptic error:
 *
 * <pre>
 * AppImageMacSignTest.app/Contents/runtime/Contents/Home/lib/libnet.dylib: errSecInternalComponent
 * </pre>
 */

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
                final var certFile = certsDir.resolve("cert.pem");
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

                final var openssl = Executor.of("openssl", "req", "-x509", "-utf8",
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
