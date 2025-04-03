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

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import jdk.jpackage.internal.util.function.ExceptionBox;
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
 * There are few options each above steps can be executed (applies to macOS
 * Sequoia).
 *
 * <h2>1. Create a private RSA key</h2>
 *
 * <h3>1.1. Create a private RSA key with openssl</h3>
 *
 * This is the obvious approach given openssl will be used to create a
 * certificate. You can create a key and a certificate with a single openssl
 * command. However the catch is importing the private key in a keystore.
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
 * /usr/bin/security import identity.pem -f pemseq -k foo.keychain
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
 * <h3>1.2. Create a private RSA key with /usr/bin/security</h3>
 *
 * Use
 *
 * <pre>
 * /usr/bin/security create-keypair -a rsa -s 2048 -k foo.keychain -T /usr/bin/security -T /usr/bin/codesign -T /usr/bin/productbuild "My key"
 * </pre>
 *
 * command to create RSA key pair in "foo.keychain" keychain. Both private and
 * public keys will be named "My key". This way, you can get an adequately
 * identified private key in a keychain.
 * <p>
 * You will need to run an additional command to allow access to the private key
 * without being asked for the keychain password:
 *
 * <pre>
 * /usr/bin/security set-key-partition-list -S apple-tool:,apple: -s -k ${KEYCHAIN-PASSWORD} foo.keychain
 * </pre>
 *
 * The only combination that suppresses popping up authorization dialog asking
 * for keychain password to access a private key when signing files with
 * /usr/bin/codesign and /usr/bin/productbuild commands is the above command and
 * {@code -T /usr/bin/security -T /usr/bin/codesign -T /usr/bin/productbuild}
 * arguments in {@code /usr/bin/security create-keypair} command.
 *
 * If you use {@code -A} instead of
 * {@code -T /usr/bin/security -T /usr/bin/codesign -T /usr/bin/productbuild}
 * hoping it will grant blank permission to any app to use the key it will not
 * work.
 *
 * Running the subsequent {@code /usr/bin/security set-key-partition-list...}
 * command will not help to suppress popping up the authorization dialog.
 *
 * <p>
 * The correlation between
 * {@code -T /usr/bin/security -T /usr/bin/codesign -T /usr/bin/productbuild}
 * arguments of {@code /usr/bin/security create-keypair} or
 * {@code /usr/bin/security import} command and
 * {@code /usr/bin/security set-key-partition-list...} command is not explicitly
 * documented and is not mentioned on <a href=
 * "https://stackoverflow.com/questions/20205162/user-interaction-is-not-allowed-trying-to-sign-an-osx-app-using-codesign"
 * target="_blank">Stack Overflow</a> or
 * <a href="https://developer.apple.com/forums/thread/666107" target=
 * "_blank">Apple Developer Forum</a>.
 *
 * <p>
 * Note #1: If user interaction is impossible (ssh session) and
 * /usr/bin/codesign is not correctly configured in the access control list of a
 * private key it attempts to use it will fail with the following cryptic error:
 *
 * <pre>
 * AppImageMacSignTest.app/Contents/runtime/Contents/Home/lib/libnet.dylib: errSecInternalComponent
 * </pre>
 *
 * <p>
 * Note #2: For the same cause /usr/bin/product build fails with a better error message:
 *
 * <pre>
 * SignData failed: Error Domain=NSOSStatusErrorDomain Code=-25308 ... (errKCInteractionNotAllowed / errSecInteractionNotAllowed: / Interaction is not allowed with the Security Server.)
 * </pre>
 *
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
 * a PKCS#12-encoded "key.p12" file with an empty passphrase.
 * <p>
 * Note #1: Given there is no way to extract specific private key from a
 * keystore make sure it contains a single private key.
 * <p>
 * Note #2: You can't extract private key in PEM format, i.e.
 * {@code /usr/bin/security export -f pemseq -t privKeys} command will fail.
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
 * command to add trusted certificate from "cert.pem" file to "foo.keychain"
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
 */

public final class MacSign {

    public record KeychainWithCertsSpec(Keychain keychain, List<CertificateRequest> certificateRequests) {

        public KeychainWithCertsSpec {
            Objects.requireNonNull(keychain);
            Objects.requireNonNull(certificateRequests);
            certificateRequests.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(keychain);
            if (!certificateRequests.isEmpty()) {
                sb.append("; certs=").append(certificateRequests);
            }
            return sb.toString();
        }

        public static final class Builder {

            public Builder name(String v) {
                keychainBuilder.name(v);
                return this;
            }

            public Builder password(String v) {
                keychainBuilder.password(v);
                return this;
            }

            public Builder addCert(CertificateRequest v) {
                certs.add(Objects.requireNonNull(v));
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

        public Path path() {
            final var path = Path.of(name);
            if (path.isAbsolute()) {
                return path;
            } else {
                return Path.of(System.getProperty("user.home")).resolve("Library/Keychains").resolve(name + "-db");
            }
        }

        public static final class Builder {

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
                return Optional.ofNullable(name).orElse("jpackageTest.keychain");
            }

            private String validatedPassword() {
                return Optional.ofNullable(password).orElse("test");
            }

            private String name;
            private String password;
        }

        Keychain create() {
            final var exec = createExecutor("create-keychain");
            final var result = exec.saveOutput().executeWithoutExitCodeCheck();
            if (result.getExitCode() == 48 && result.getFirstLineOfOutput().endsWith("A keychain with the same name already exists.")) {
                delete();
                exec.saveOutput(false).execute();
            } else {
                result.assertExitCodeIsZero();
            }

            // Ensure the keychain is unlocked.
            unlock();

            // Set auto-lock timeout to unlimited and remove auto-lock on sleep.
            security("set-keychain-settings", name).execute();
            return this;
        }

        Keychain delete() {
            security("delete-keychain", name).execute();
            return this;
        }

        Keychain unlock() {
            createExecutor("unlock-keychain").execute();
            return this;
        }

        boolean exists() {
            return Files.exists(path());
        }

        Keychain createKeyPair(String name) {
            security("create-keypair",
                    "-a", "rsa",
                    "-s", "2048",
                    "-k", this.name,
                    "-T", "/usr/bin/security",
                    "-T", "/usr/bin/codesign",
                    "-T", "/usr/bin/productbuild").execute();
            security("set-key-partition-list", "-S", "apple-tool:,apple:", "-s", "-k", password, this.name).execute();
            return this;
        }

        List<X509Certificate> findCertificates() {
            final var in = new ByteArrayInputStream(
                    security("find-certificate", "-ap", name).saveOutput().execute().getOutput().stream().collect(joining("\n")).getBytes(StandardCharsets.UTF_8));
            return toFunction(CERT_FACTORY::generateCertificates).apply(in).stream().map(X509Certificate.class::cast).toList();
        }

        public static void addToSearchList(Collection<Keychain> keychains) {
            security("list-keychains", "-d", "user", "-s", "login.keychain")
            .addArguments(keychains.stream().map(Keychain::name).toList())
            .execute();
        }

        private Executor createExecutor(String command) {
            return security(command, "-p", password, name);
        }
    }

    record ResolvedCertificateRequest(CertificateRequest request, X509Certificate cert, VerifyStatus verifyStatus) {
        ResolvedCertificateRequest {
            Objects.requireNonNull(request);
            Objects.requireNonNull(cert);
            Objects.requireNonNull(verifyStatus);
        }

        enum VerifyStatus {
            VERIFY_OK,
            VERIFY_ERROR,
            UNVERIFIED
        }

        ResolvedCertificateRequest(X509Certificate cert) {
            this(new CertificateRequest(cert), cert, VerifyStatus.UNVERIFIED);
        }

        ResolvedCertificateRequest copyVerified(boolean verifySuccess) {
            return new ResolvedCertificateRequest(request, cert,
                    verifySuccess ? VerifyStatus.VERIFY_OK : VerifyStatus.VERIFY_ERROR);
        }
    }

    record CertificateStats(List<ResolvedCertificateRequest> allResolvedCertificateRequests,
            List<X509Certificate> allCertificates, List<CertificateRequest> knownCertificateRequests,
            Map<X509Certificate, Throwable> unmappedCertificates) {

        private static CertificateStats get(KeychainWithCertsSpec spec) {
            return CACHE.computeIfAbsent(spec, CertificateStats::create);
        }

        Map<CertificateRequest, List<X509Certificate>> mapKnownCertificateRequests() {
            return knownCertificateRequests.stream().collect(groupingBy(x -> x, mapping(certificateRequest -> {
                return allResolvedCertificateRequests.stream().filter(v -> {
                    return v.request().equals(certificateRequest);
                }).map(ResolvedCertificateRequest::cert);
            }, flatMapping(x -> x, toList()))));
        }

        Set<CertificateRequest> verifyFailedCertificateRequests() {
            return knownCertificateRequests.stream().filter(certificateRequest -> {
                return allResolvedCertificateRequests.stream().anyMatch(v -> {
                    return v.request().equals(certificateRequest) && v.verifyStatus() == ResolvedCertificateRequest.VerifyStatus.VERIFY_ERROR;
                });
            }).collect(toSet());
        }

        Set<CertificateRequest> unmappedCertificateRequests() {
            return Comm.compare(Set.copyOf(knownCertificateRequests),
                    allResolvedCertificateRequests.stream().map(ResolvedCertificateRequest::request).collect(toSet())).unique1();
        }

        private static CertificateStats create(KeychainWithCertsSpec spec) {
            final var allCertificates = spec.keychain().findCertificates();
            final List<ResolvedCertificateRequest> allResolvedCertificateRequests = new ArrayList<>();
            final Map<X509Certificate, Throwable> unmappedCertificates = new HashMap<>();

            withTempDirectory(workDir -> {
                for (final var cert : allCertificates) {
                    ResolvedCertificateRequest resolvedCertificateRequest;
                    try {
                        resolvedCertificateRequest = new ResolvedCertificateRequest(cert);
                    } catch (RuntimeException ex) {
                        final Throwable t;
                        if (ex instanceof ExceptionBox) {
                            t = ex.getCause();
                        } else {
                            t = ex;
                        }
                        unmappedCertificates.put(cert, t);
                        continue;
                    }

                    if (spec.certificateRequests().contains(resolvedCertificateRequest.request)) {
                        final var certFile = workDir.resolve(CertificateHash.of(cert).toString() + ".pem");
                        final var verifySuccess = verifyCertificate(resolvedCertificateRequest, spec.keychain(), certFile);
                        resolvedCertificateRequest = resolvedCertificateRequest.copyVerified(verifySuccess);
                    }

                    allResolvedCertificateRequests.add(resolvedCertificateRequest);
                }
            });

            return new CertificateStats(allResolvedCertificateRequests, List.copyOf(allCertificates),
                    List.copyOf(spec.certificateRequests()), unmappedCertificates);
        }

        private static final Map<KeychainWithCertsSpec, CertificateStats> CACHE = new ConcurrentHashMap<>();
    }

    record PemData(String label, byte[] data) {
        PemData {
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

        static PemData of(X509Certificate cert) {
            return new PemData("CERTIFICATE", toSupplier(cert::getEncoded).get());
        }

        void save(Path path, OpenOption... options) {
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

    public record CertificateHash(byte[] value) {
        public CertificateHash {
            Objects.requireNonNull(value);
            if (value.length != 20) {
                throw new IllegalArgumentException("Invalid SHA-1 hash");
            }
        }

        public static CertificateHash of(X509Certificate cert) {
            return new CertificateHash(toSupplier(() -> {
                final MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update(cert.getEncoded());
                return md.digest();
            }).get());
        }

        @Override
        public String toString() {
            return FORMAT.formatHex(value);
        }

        private static final HexFormat FORMAT = HexFormat.of().withUpperCase();
    }

    public enum CertificateType {
        CODE_SIGN(List.of(
                "basicConstraints=critical,CA:false",
                "keyUsage=critical,digitalSignature"
        ), CODE_SIGN_EXTENDED_KEY_USAGE, "codeSign"),
        // https://www.apple.com/certificateauthority/pdf/Apple_Developer_ID_CPS_v1.1.pdf
        // https://security.stackexchange.com/questions/17909/how-to-create-an-apple-installer-package-signing-certificate
        // https://github.com/zschuessler/AXtendedKey/blob/32c8ccec3df7e78fe521d09c48bd20558b3a4a24/src/axtended_key/services/certificate_manager.py#L109C102-L109C115
        INSTALLER(List.of(
                "basicConstraints=critical,CA:false",
                "keyUsage=critical,digitalSignature",
                // Apple-specific extension for self-distributed apps
                // https://oid-base.com/get/1.2.840.113635.100.6.1.14
                "1.2.840.113635.100.6.1.14=critical,DER:0500"
        ), INSTALLER_EXTENDED_KEY_USAGE,
                // Should be "pkgSign", but with this policy `security verify-cert` command fails.
                "basic");

        CertificateType(List<String> otherExtensions, List<String> extendedKeyUsage, String verifyPolicy) {
            this.otherExtensions = otherExtensions;
            this.extendedKeyUsage = extendedKeyUsage;
            this.verifyPolicy = verifyPolicy;
        }

        boolean isTypeOf(X509Certificate cert) {
            return toSupplier(() -> cert.getExtendedKeyUsage().containsAll(extendedKeyUsage)).get();
        }

        List<String> extensions() {
            return Stream.concat(otherExtensions.stream(),
                    Stream.of("extendedKeyUsage=" + Stream.concat(Stream.of("critical"), extendedKeyUsage.stream()).collect(joining(",")))).toList();
        }

        String verifyPolicy() {
            return verifyPolicy;
        }

        private final List<String> otherExtensions;
        private final List<String> extendedKeyUsage;
        private final String verifyPolicy;
    }

    public record CertificateRequest(String name, CertificateType type, int days) implements Comparable<CertificateRequest>{
        public CertificateRequest {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
        }

        CertificateRequest(X509Certificate cert) {
            this(getSubjectCN(cert), getType(cert), getDurationInDays(cert));
        }

        @Override
        public int compareTo(CertificateRequest o) {
            return COMPARATOR.compare(this, o);
        }

        public static final class Builder {

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
            private int days = 365;
        }

        private static String getSubjectCN(X509Certificate cert) {
            final var principal = cert.getSubjectX500Principal();
            final var ldapName = toSupplier(() -> new LdapName(principal.getName())).get();
            return ldapName.getRdns().stream().filter(rdn -> {
                return rdn.getType().equalsIgnoreCase("CN");
            }).map(Rdn::getValue).map(Object::toString).distinct().reduce((x, y) -> {
                throw new IllegalArgumentException(String.format(
                        "Certificate with hash=%s has multiple subject common names: [%s], [%s]", CertificateHash.of(cert), x, y));
            }).orElseThrow(() -> {
                throw new IllegalArgumentException(String.format(
                        "Certificate with hash=%s doesn't have subject common name", CertificateHash.of(cert)));
            });
        }

        private static CertificateType getType(X509Certificate cert) {
            return Stream.of(CertificateType.values()).filter(certType -> {
                return certType.isTypeOf(cert);
            }).reduce((x, y) -> {
                throw new IllegalArgumentException(String.format(
                        "Ambiguous type of a certificate with hash=%s: [%s], [%s]", CertificateHash.of(cert), x, y));
            }).orElseThrow(() -> {
                throw new IllegalArgumentException(String.format(
                        "Unrecognized type of a certificate with hash=%s", CertificateHash.of(cert)));
            });
        }

        private static int getDurationInDays(X509Certificate cert) {
            final var notBefore = cert.getNotBefore();
            final var notAfter = cert.getNotAfter();
            return (int)TimeUnit.DAYS.convert(notAfter.getTime() - notBefore.getTime(), TimeUnit.MILLISECONDS);
        }

        private static final Comparator<CertificateRequest> COMPARATOR =
                Comparator.comparing(CertificateRequest::name)
                .thenComparing(Comparator.comparing(CertificateRequest::type))
                .thenComparing(Comparator.comparingInt(CertificateRequest::days));
    }

    /**
     * Creates keychains and signing identities from the given configuration.
     * <p>
     * It will create a single private key and unique certificate using this key for
     * every unique {@linkplain CertificateRequest} instance.
     * <p>
     * Created certificates will be imported into the keychains, and every
     * certificate will be marked as trusted.
     * <p>
     * The user will be prompted to enter the user login password as
     * many times as the number of unique certificates this function will create.
     *
     * @param specs the keychains and signing identities configuration
     */
    public static void setUp(List<KeychainWithCertsSpec> specs) {
        validate(specs);

        if (!OPENSSL.isAbsolute()) {
            final var opensslVer = Executor.of(OPENSSL.toString(), "version").saveFirstLineOfOutput().execute().getFirstLineOfOutput();
            TKit.trace(String.format("openssl version: %s", opensslVer));
        }

        traceSigningEnvironment(specs);

        // Reset keychain search list to defaults.
        Keychain.addToSearchList(List.of());

        final var mainKeychain = specs.getFirst().keychain();

        // Init basic keychain from scratch.
        // This will create the keychain file and the key pair.
        mainKeychain.create().createKeyPair("jpackage test key");

        // Use the same private key to create certificates in additional keychains.
        for (final var keychainSpec : specs.subList(1, specs.size())) {
            final var keychainFile = keychainSpec.keychain().path();
            TKit.trace(String.format("Create keychain in [%s] file from [%s] keychain", keychainFile, mainKeychain.name()));
            try {
                Files.copy(mainKeychain.path(), keychainFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        withTempDirectory(dir -> {
            // Create certificates.
            final var certPemFiles = createCertificates(mainKeychain, specs, dir);

            final Map<Path, Keychain> trustConfig = new HashMap<>();

            for (final var certPemFile : certPemFiles.entrySet()) {
                // Import the certificate in all keychains it belongs to.
                final var keychains = findKeychains(certPemFile.getKey(), specs).toList();
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
            trustCertificates(trustConfig);
        });

        Keychain.addToSearchList(specs.stream().map(KeychainWithCertsSpec::keychain).toList());
    }

    /**
     * Destroys the given signing configuration.
     * <p>
     * It will remove specified keychains from the keychain search list and delete
     * the keychain files.
     *
     * @param specs the keychains and signing identities configuration
     */
    public static void tearDown(List<KeychainWithCertsSpec> specs) {
        validate(specs);
        Keychain.addToSearchList(List.of());
        specs.forEach(spec -> {
            spec.keychain().delete();
        });
    }

    /**
     * Returns {@code true} if the given signing configuration is deployed and in
     * good standing.
     *
     * @param specs the keychains and signing identities configuration
     * @return {@code true} if the given signing configuration is deployed and in
     *         good standing
     */
    public static boolean isDeployed(List<KeychainWithCertsSpec> specs) {
        validate(specs);
        traceSigningEnvironment(specs);

        final var missingKeychain = specs.stream().map(KeychainWithCertsSpec::keychain).filter(Predicate.not(Keychain::exists)).peek(keychain -> {
            TKit.trace(String.format("Missing [%s] keychain file", keychain.path()));
        }).findAny().isPresent();

        final var specsWithExistingKeychains = specs.stream().filter(spec -> {
            return spec.keychain().exists();
        }).toList();

        final var certificateStats = specsWithExistingKeychains.stream().collect(
                toMap(KeychainWithCertsSpec::keychain, CertificateStats::get));

        for (final var keychain : specsWithExistingKeychains.stream().map(KeychainWithCertsSpec::keychain).toList()) {
            TKit.trace(String.format("In [%s] keychain:", keychain.name()));
            final var certificateStat = certificateStats.get(keychain);
            final var resolvedCertificateRequests = certificateStat.allResolvedCertificateRequests().stream()
                    .sorted(Comparator.comparing(ResolvedCertificateRequest::request)).toList();
            for (final var resolvedCertificateRequest : resolvedCertificateRequests) {
                TKit.trace(String.format("  Certificate with hash=%s: %s[%s]",
                        CertificateHash.of(resolvedCertificateRequest.cert()),
                        resolvedCertificateRequest.request(),
                        resolvedCertificateRequest.verifyStatus()));
            }

            for (final var unmappedCertificate : certificateStat.unmappedCertificates().entrySet()) {
                TKit.trace(String.format("  Failed to create certificate request from the certificate with hash=%s: %s",
                        CertificateHash.of(unmappedCertificate.getKey()), unmappedCertificate.getValue()));
            }

            for (final var unmappedCertificateRequest : certificateStat.unmappedCertificateRequests().stream().sorted().toList()) {
                TKit.trace(String.format("  Missing certificate for %s certificate request", unmappedCertificateRequest));
            }
        }

        final var missingCertificates = certificateStats.values().stream().anyMatch(stat -> {
            return !stat.unmappedCertificateRequests().isEmpty();
        });

        final var invalidCertificates = certificateStats.values().stream().anyMatch(stat -> {
            return !stat.verifyFailedCertificateRequests().isEmpty();
        });

        return !missingKeychain && !missingCertificates && !invalidCertificates;
    }

    public static Map<CertificateRequest, X509Certificate> mapCertificateRequests(KeychainWithCertsSpec spec) {
        return CertificateStats.get(spec).mapKnownCertificateRequests().entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            return e.getValue().stream().reduce((x, y) -> {
                throw new IllegalStateException(String.format(
                        "Certificates with hash=%s and hash=%s map into %s certificate request in [%s] keychain",
                        CertificateHash.of(x), CertificateHash.of(y), e.getKey(), spec.keychain().name()));
            }).orElseThrow(() -> {
                throw new IllegalStateException(String.format(
                        "A certificate matching %s certificate request not found in [%s] keychain",
                        e.getKey(), spec.keychain().name()));
            });
        }));
    }

    private static void validate(List<KeychainWithCertsSpec> specs) {
        specs.stream().map(KeychainWithCertsSpec::keychain).map(Keychain::name).collect(toMap(x -> x, x -> x, (x, y) -> {
            throw new IllegalArgumentException(String.format("Multiple keychains with the same name [%s]", x));
        }));

        specs.stream().forEach(spec -> {
            spec.certificateRequests().stream().collect(toMap(x -> x, x -> x, (x, y) -> {
                throw new IllegalArgumentException(String.format(
                        "Multiple certificate requests with the same specification %s in one keychain [%s]", x, spec.keychain().name()));
            }));
        });
    }

    private static boolean verifyCertificate(ResolvedCertificateRequest resolvedCertificateRequest, Keychain keychain, Path certFile) {
        PemData.of(resolvedCertificateRequest.cert()).save(certFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        for (final var mode : List.of("-q", "-v")) {
            final var ok = security("verify-cert", "-L", "-n", mode,
                    "-c", certFile.normalize().toString(),
                    "-k", keychain.name(),
                    "-p", resolvedCertificateRequest.request().type().verifyPolicy()).executeWithoutExitCodeCheck().getExitCode() == 0;
            if (ok) {
                return true;
            }
        }
        return false;
    }

    private static void traceSigningEnvironment(Collection<KeychainWithCertsSpec> specs) {
        TKit.trace("Signing environment:");
        int specIdx = 1;
        for (final var spec : specs) {
            TKit.trace(String.format("[%d/%d] %s", specIdx++, specs.size(), spec.keychain()));
            int certRequestIdx = 1;
            for (final var certRequest : spec.certificateRequests()) {
                TKit.trace(String.format("  [%d/%d] %s", certRequestIdx++, spec.certificateRequests().size(), certRequest));
            }
        }
    }

    private static Stream<Keychain> findKeychains(CertificateRequest certificateRequest,
            Collection<KeychainWithCertsSpec> specs) {
        Objects.requireNonNull(certificateRequest);
        return specs.stream().filter(spec -> {
            return spec.certificateRequests().contains(certificateRequest);
        }).map(KeychainWithCertsSpec::keychain);
    }

    private static void withTempDirectory(ThrowingConsumer<Path> callback) {
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

    private static Map<CertificateRequest, Path> createCertificates(Keychain privateKeySource,
            Collection<KeychainWithCertsSpec> specs, Path outputPemDir) {

        Objects.requireNonNull(privateKeySource);
        Objects.requireNonNull(specs);
        specs.forEach(Objects::requireNonNull);
        Objects.requireNonNull(outputPemDir);

        final Map<CertificateRequest, X509Certificate> createdCertificates = new HashMap<>();

        withTempDirectory(tmpDir -> {
            final var cfgFile = tmpDir.resolve("cert.cfg");
            final var certFile = tmpDir.resolve("cert.pem");
            final var keyFilePKCS12 = tmpDir.resolve("key.p12");
            final var keyFilePEM = tmpDir.resolve("key.pem");

            security("export",
                    "-f", "pkcs12",
                    "-k", privateKeySource.name(),
                    "-t", "privKeys",
                    "-P", "",
                    "-o", keyFilePKCS12.normalize().toString()).execute();

            // This step is needed only for LibreSSL variant of openssl command which can't take
            // private key in PKCS#12 format.
            Executor.of(OPENSSL.toString(), "pkcs12",
                    "-nodes",
                    "-in", keyFilePKCS12.normalize().toString(),
                    "-password", "pass:",
                    "-out", keyFilePEM.normalize().toString()).dumpOutput().execute();

            final var keyFile = keyFilePEM;

            for (final var spec : specs) {
                for (final var certificateRequest : spec.certificateRequests()) {
                    var cert = createdCertificates.get(certificateRequest);
                    if (cert == null) {
                        TKit.createTextFile(cfgFile, Stream.of(
                                "[ req ]",
                                "distinguished_name = req_name",
                                "prompt=no",
                                "[ req_name ]",
                                "CN=" + certificateRequest.name()
                        ));

                        final var openssl = Executor.of(OPENSSL.toString(), "req",
                                "-x509", "-utf8", "-sha256", "-nodes",
                                "-new", // Prevents LibreSSL variant of openssl command from hanging
                                "-days", Integer.toString(certificateRequest.days()),
                                "-key", keyFile.normalize().toString(),
                                "-config", cfgFile.normalize().toString(),
                                "-out", certFile.normalize().toString());

                        certificateRequest.type().extensions().forEach(ext -> {
                            openssl.addArgument("-addext");
                            openssl.addArgument(ext);
                        });

                        openssl.dumpOutput().execute();

                        try (final var in = Files.newInputStream(certFile)) {
                            cert = (X509Certificate)CERT_FACTORY.generateCertificate(in);
                            createdCertificates.put(certificateRequest, cert);

                            final var certHash = CertificateHash.of(cert);
                            final var certPemFile = outputPemDir.resolve(certHash.toString() + ".pem");
                            Files.copy(certFile, certPemFile);
                        }
                    }
                }
            }
        });

        return createdCertificates.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            final var certHash = CertificateHash.of(e.getValue());
            return outputPemDir.resolve(certHash.toString() + ".pem");
        }));
    }

    private static void trustCertificates(Map<Path, Keychain> config) {
        if (config.isEmpty()) {
            throw new IllegalArgumentException();
        }

        final var exec = Executor.of("osascript", SIGN_UTILS_SCRIPT.toString(), "trust-certs",
                config.keySet().iterator().next().getParent().toAbsolutePath().toString());

        exec.addArguments(config.entrySet().stream().map(e -> {
            return Stream.of(e.getValue().name(), e.getKey().getFileName().toString());
        }).flatMap(x -> x).toList());

        exec.dumpOutput().execute();
    }

    static Executor security(String... args) {
        return Executor.of("security").dumpOutput().addArguments(args);
    }

    private static final CertificateFactory CERT_FACTORY = toSupplier(() -> {
        return CertificateFactory.getInstance("X.509");
    }).get();

    // Code Signing
    private static final List<String> CODE_SIGN_EXTENDED_KEY_USAGE = List.of("1.3.6.1.5.5.7.3.3");
    // Apple Custom Extended Key Usage (EKU) packageSign
    // https://oid-base.com/get/1.2.840.113635.100.4.13
    private static final List<String> INSTALLER_EXTENDED_KEY_USAGE = List.of("1.2.840.113635.100.4.13");

    private static final Path SIGN_UTILS_SCRIPT = TKit.TEST_SRC_ROOT.resolve("resources/sign-utils.applescript").normalize();

    // macOS comes with /usr/bin/openssl which is LibreSSL
    // If you install openssl with Homebrew, it will be installed in /usr/local/bin/openssl,
    // and the PATH env variable will be altered to pick up openssl from Homebrew.
    // However, jtreg will alter the value of the PATH env variable and
    // /usr/bin/openssl will preempt /usr/local/bin/openssl.
    // To workaround this jtreg behavior support specifying path to openssl command.
    private static final Path OPENSSL = Path.of(Optional.ofNullable(TKit.getConfigProperty("openssl")).orElse("openssl"));
}
