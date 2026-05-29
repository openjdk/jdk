/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8007755 8374808
 * @library /test/lib
 * @summary Support the logical grouping of keystores
 * @run junit DKSTest
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DomainLoadStoreParameter;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Load and store entries in domain keystores

public class DKSTest {

    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String USER_DIR = System.getProperty("user.dir", ".");
    private static final Path SCRATCH_FOLDER = Path.of(".");
    private static final String CERT = Paths.get(
            TEST_SRC, "..", "..", "pkcs12", "trusted.pem").toAbsolutePath().toString();
    private static final String CONFIG = Paths.get(
            TEST_SRC, "domains.cfg").toUri().toString();
    private static final char[] KEYSTORE_PASSWORD = "test123".toCharArray();
    private static final Map<String, KeyStore.ProtectionParameter> PASSWORDS =
            new HashMap<>() {{
                put("keystore",
                        new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
                put("policy_keystore",
                        new KeyStore.PasswordProtection(
                                "Alias.password".toCharArray()));
                put("pw_keystore",
                        new KeyStore.PasswordProtection("test12".toCharArray()));
                put("eckeystore1",
                        new KeyStore.PasswordProtection("password".toCharArray()));
                put("truststore",
                        new KeyStore.PasswordProtection("changeit".toCharArray()));
                put("empty",
                        new KeyStore.PasswordProtection("passphrase".toCharArray()));
            }};

    private static final Map<String, KeyStore.ProtectionParameter>
            WRONG_PASSWORDS = new HashMap<>() {{
                put("policy_keystore",
                        new KeyStore.PasswordProtection(
                                "wrong".toCharArray()));
                put("pw_keystore",
                        new KeyStore.PasswordProtection("wrong".toCharArray()));
                put("eckeystore1",
                        new KeyStore.PasswordProtection("wrong".toCharArray()));
            }};

    /*
     * domain keystore: keystores with wrong passwords
     */
    @Test
    public void keystoresWithWrongPasswordTest() throws Exception {
            final URI config = new URI(CONFIG + "#keystores");
            final KeyStore ks = KeyStore.getInstance("DKS");

            var e = assertThrows(IOException.class,
                    () -> ks.load(new DomainLoadStoreParameter(
                            config, WRONG_PASSWORDS)));
            assertTrue(causedBy(e, UnrecoverableKeyException.class),
                    "Unexpected cause");
            System.out.println("Expected cause: " + e);
    }

    /*
     * domain keystore: system
     */
    @Test
    public void keystoreSystemTest() throws Exception {
        final URI config = new URI(CONFIG + "#system");
        final KeyStore keystore = KeyStore.getInstance("DKS");
        // load entries
        keystore.load(new DomainLoadStoreParameter(config, PASSWORDS));
        final int expected = keystore.size();
        System.out.println("\nLoading domain keystore: " + config + "\t[" +
                           expected + " entries]");
        DKSSystemPropertiesTest.checkEntries(keystore, expected);
    }

    /*
     * domain keystore: system_plus
     */
    @Test
    public void keystoreSystemPlusTest() throws Exception {
        final URI config = new URI(CONFIG + "#system_plus");
        final KeyStore keystore = KeyStore.getInstance("DKS");
        // load entries
        keystore.load(new DomainLoadStoreParameter(config, PASSWORDS));
        final int expected = keystore.size();
        System.out.println("\nLoading domain keystore: " + config + "\t[" +
                           expected + " entries]");
        DKSSystemPropertiesTest.checkEntries(keystore, expected);
    }

    /*
     * domain keystore: empty
     */
    @Test
    public void keystoreEmptyTest() throws Exception {
        final KeyStore empty = KeyStore.getInstance("JKS");
        empty.load(null, null);

        try (OutputStream outStream =
                     new FileOutputStream(new File(USER_DIR, "empty.jks"))) {
            empty.store(outStream, "passphrase".toCharArray());
        }
        final URI config = new URI(CONFIG + "#empty");
        final KeyStore keystore = KeyStore.getInstance("DKS");
        // load entries
        keystore.load(new DomainLoadStoreParameter(config, PASSWORDS));
        System.out.println("\nLoading domain keystore: " + config +
                           "\t[0 entries]");
        DKSSystemPropertiesTest.checkEntries(keystore, 0);

    }

    /*
     * domain keystore: keystores
     */
    @Test
    public void multipleKeystoresTest() throws Exception {

        URI config = new URI(CONFIG + "#keystores");
        int expected = 2 + 1 + 1;
        KeyStore keystore = KeyStore.getInstance("DKS");
        // load entries
        keystore.load(new DomainLoadStoreParameter(config, PASSWORDS));
        System.out.println("\nLoading domain keystore: " + config + "\t[" +
                           expected + " entries]");
        DKSSystemPropertiesTest.checkEntries(keystore, expected);
        // set a new trusted certificate entry
        Certificate cert = loadCertificate(CERT);
        String alias = "pw_keystore tmp-cert";
        System.out.println("Setting new trusted certificate entry: " + alias);
        keystore.setEntry(alias,
                new KeyStore.TrustedCertificateEntry(cert), null);
        expected++;
        // store entries
        config = new URI(CONFIG + "#keystores_tmp");
        System.out.println("Storing domain keystore: " + config + "\t[" +
                           expected + " entries]");
        keystore.store(new DomainLoadStoreParameter(config, PASSWORDS));
        keystore = KeyStore.getInstance("DKS");
        // reload entries
        keystore.load(new DomainLoadStoreParameter(config, PASSWORDS));
        System.out.println("Reloading domain keystore: " + config + "\t[" +
                           expected + " entries]");
        DKSSystemPropertiesTest.checkEntries(keystore, expected);
        // get the new trusted certificate entry
        System.out.println("Getting new trusted certificate entry: " + alias);
        assertTrue(keystore.isCertificateEntry(alias),
                "Error: cannot retrieve certificate entry: " + alias);

        keystore.setEntry(alias,
                new KeyStore.TrustedCertificateEntry(cert), null);
    }

    @Test
    public void keystoreGetKeyTest() throws Exception {

        final SecretKey expected = generateSecretKey();
        final SecretKey expected2 = generateSecretKey();
        final KeyStore dks = generateKeystores(expected, expected2);

        SecretKey actual =
                (SecretKey) dks.getKey("firstPkcs12 alias1",
                        KEYSTORE_PASSWORD);
        assertNotNull(actual);
        assertArrayEquals(expected.getEncoded(), actual.getEncoded());

        actual = (SecretKey) dks.getKey("alias2", KEYSTORE_PASSWORD);
        assertNotNull(actual);
        assertArrayEquals(expected2.getEncoded(), actual.getEncoded());

        assertNull(dks.getKey("missingAlias", KEYSTORE_PASSWORD));
    }

    @Test
    public void keystoreContainsAliasTest() throws Exception {

        final KeyStore dks = generateKeystores(
                generateSecretKey(),
                generateSecretKey());

        assertTrue(dks.containsAlias("firstPkcs12 alias1"));
        assertTrue(dks.containsAlias("alias2"));
        assertFalse(dks.containsAlias("missingAlias"));
        assertFalse(dks.containsAlias("unknown alias2"));
    }

    @Test
    public void keystoreIsKeyEntryTest() throws Exception {

        final KeyStore dks = generateKeystores(
                generateSecretKey(),
                generateSecretKey());

        assertTrue(dks.isKeyEntry("firstPkcs12 alias1"));
        assertTrue(dks.isKeyEntry("alias2"));
        assertFalse(dks.isKeyEntry("missingAlias"));
        assertFalse(dks.isKeyEntry("unknown alias2"));
    }

    @Test
    public void keystoreIsCertificateEntryTest() throws Exception {

        final KeyStore dks = generateKeyStoreFromPKCS12(generateSecretKey(),
                loadCertificate(CERT));

        assertTrue(dks.isCertificateEntry("firstPkcs12 alias1"));
        assertFalse(dks.isCertificateEntry("missingAlias"));
        assertFalse(dks.isCertificateEntry("unknown alias2"));
    }

    @Test
    public void keystoreGetCertificateAliasEntryTest() throws Exception {

        final Certificate certificate = loadCertificate(CERT);
        final KeyStore dks = generateKeyStoreFromPKCS12(generateSecretKey(),
                certificate);

        assertEquals("alias1", dks.getCertificateAlias(certificate));
    }

    @Test
    public void keystoreGetCertificateTest() throws Exception {

        final Certificate certificate = loadCertificate(CERT);
        final KeyStore dks = generateKeyStoreFromPKCS12(generateSecretKey(),
                certificate);

        assertEquals(certificate, dks.getCertificate("alias1"));
    }

    private KeyStore generateKeystores(final SecretKey secretKey,
                                       final SecretKey secretKey2)
            throws Exception {

        final Path firstPkcs12 =
                generatePKCS12("firstPkcs12.p12", "alias1",
                        KEYSTORE_PASSWORD, secretKey);
        final Path secondPkcs12 =
                generatePKCS12("secondPkcs12.p12", "alias2",
                        KEYSTORE_PASSWORD, secretKey2);
        final Path thirdPkcs12 =
                generatePKCS12("thirdPkcs12.p12", null,
                        null, null);

        final Map<String, KeyStore.ProtectionParameter> protectionParameters =
                new LinkedHashMap<>();
        protectionParameters.put("firstPkcs12",
                new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
        protectionParameters.put("secondPkcs12",
                new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));


        final KeyStore dks = KeyStore.getInstance("DKS");
        final Path config = createDomainConfig(Map.of(
                "firstPkcs12", firstPkcs12,
                "secondPkcs12", secondPkcs12,
                "thirdPkcs12", thirdPkcs12));
        dks.load(new DomainLoadStoreParameter(config.toUri(),
                protectionParameters));

        return dks;
    }

    private KeyStore generateKeyStoreFromPKCS12(final SecretKey secretKey,
                                                final Certificate certificate) throws Exception {

        // keystore with cert
        final KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(null, null);
        keystore.setEntry("alias1",
                new KeyStore.TrustedCertificateEntry(certificate), null);
        final Path firstPkcs12 = SCRATCH_FOLDER.resolve("firstPkcs12.p12");
        try (OutputStream output = Files.newOutputStream(firstPkcs12)) {
            keystore.store(output, KEYSTORE_PASSWORD);
        }

        final Path secondPkcs12 =
                generatePKCS12("secondPkcs12.p12", "alias2",
                        KEYSTORE_PASSWORD, secretKey);
        final Path thirdPkcs12 =
                generatePKCS12("thirdPkcs12.p12", null,
                        null, null);

        final Map<String, KeyStore.ProtectionParameter> protectionParameters =
                new LinkedHashMap<>();
        protectionParameters.put("firstPkcs12",
                new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));
        protectionParameters.put("secondPkcs12",
                new KeyStore.PasswordProtection(KEYSTORE_PASSWORD));


        final KeyStore dks = KeyStore.getInstance("DKS");
        final Path config = createDomainConfig(Map.of(
                "firstPkcs12", firstPkcs12,
                "secondPkcs12", secondPkcs12,
                "thirdPkcs12", thirdPkcs12));
        dks.load(new DomainLoadStoreParameter(config.toUri(),
                protectionParameters));

        return dks;
    }

    private SecretKey generateSecretKey() throws NoSuchAlgorithmException {
        final KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    private Path createDomainConfig(Map<String, Path> parts) throws Exception {
        final StringBuilder config = new StringBuilder();
        config.append("domain TestDomain keystoreType=\"PKCS12\" {\n");
        for (final Map.Entry<String, Path> entry : parts.entrySet()) {
            config.append("    keystore ")
                    .append(entry.getKey())
                    .append(" keystoreURI=\"")
                    .append(entry.getValue().toUri())
                    .append("\";\n");
        }
        config.append("};\n");

        Path path = SCRATCH_FOLDER.resolve("domain.cfg");
        Files.writeString(path, config.toString());
        return path;
    }

    private Path generatePKCS12(final String fileName,
                                final String alias,
                                final char[] entryPassword,
                                final SecretKey secretKey) throws Exception {
        final KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(null, null);

        if (alias != null) {
            keystore.setEntry(alias, new KeyStore.SecretKeyEntry(secretKey),
                    new KeyStore.PasswordProtection(entryPassword));
        }

        final Path path = SCRATCH_FOLDER.resolve(fileName);
        try (OutputStream output = Files.newOutputStream(path)) {
            keystore.store(output, KEYSTORE_PASSWORD);
        }
        return path;
    }

    private static Certificate loadCertificate(String certFile)
            throws Exception {
        X509Certificate cert = null;
        try (FileInputStream certStream = new FileInputStream(certFile)) {
            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");
            return factory.generateCertificate(certStream);
        }
    }

    // checks if an exception was caused by specified exception class
    private static boolean causedBy(Exception e, Class klass) {
        Throwable cause = e;
        while ((cause = cause.getCause()) != null) {
            if (cause.getClass().equals(klass)) {
                return true;
            }
        }
        return false;
    }
}
