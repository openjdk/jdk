/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import jdk.security.jarsigner.JarSigner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/*
 * @test
 * @bug 8378003
 * @summary Verify that JarURLConnection.getCertificates() and
 *          JarURLConnection.getJarEntry().getCodeSigners() returns the
 *          expected results for entries in a signed JAR file
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.tools.keytool
 * @run junit ${test.main.class}
 */
class JarURLConnectionCertsAndCodeSigners {

    private static final String JAR_ENTRY_NAME = "foo-bar";
    private static final String CERT_SUBJECT = "CN=duke";
    private static Path SIGNED_JAR;

    @BeforeAll
    static void beforeAll() throws Exception {
        final KeyStore.PrivateKeyEntry key = generatePrivateKey();
        SIGNED_JAR = createSignedJar(key);
    }

    /*
     * Verifies that JarURLConnection.getCertificates() returns the correct
     * certificates for entries in a signed JAR file.
     */
    @Test
    void testCertificates() throws Exception {
        final URI uri = new URI("jar:" + SIGNED_JAR.toUri() + "!/" + JAR_ENTRY_NAME);
        System.err.println("running test against signed JAR entry: " + uri);
        final URLConnection urlConn = uri.toURL().openConnection();
        assertInstanceOf(JarURLConnection.class, urlConn, "unexpected URLConnection type");
        final JarURLConnection jarURLConn = (JarURLConnection) urlConn;
        try (InputStream is = jarURLConn.getInputStream()) {
            is.readAllBytes();
        }
        Certificate[] prevIterationCerts = null;
        for (int i = 1; i <= 2; i++) {
            final Certificate[] certs = jarURLConn.getCertificates();
            assertNotNull(certs, "null certificates for signed JAR entry: " + uri);
            assertNotEquals(0, certs.length, "empty certificates for signed JAR entry: " + uri);
            assertInstanceOf(X509Certificate.class, certs[0], "unexpected certificate type");
            final String subject = ((X509Certificate) certs[0]).getSubjectX500Principal().getName();
            assertEquals(CERT_SUBJECT, subject, "unexpected subject in certificate");
            if (i > 1) {
                // verify that each call to getCertificates() returns
                // a new instance of the array.
                // intentional identity check
                assertNotSame(prevIterationCerts, certs, "getCertificates() did not return" +
                        " a new array");
            }
            prevIterationCerts = certs;
        }
    }

    /*
     * Verifies that JarURLConnection.getJarEntry().getCodeSigners() returns the correct
     * codesigners for entries in a signed JAR file.
     */
    @Test
    void testCodeSigners() throws Exception {
        final URI uri = new URI("jar:" + SIGNED_JAR.toUri() + "!/" + JAR_ENTRY_NAME);
        System.err.println("running test against signed JAR entry: " + uri);
        final URLConnection urlConn = uri.toURL().openConnection();
        assertInstanceOf(JarURLConnection.class, urlConn, "unexpected URLConnection type");
        final JarURLConnection jarURLConn = (JarURLConnection) urlConn;
        try (InputStream is = jarURLConn.getInputStream()) {
            is.readAllBytes();
        }
        CodeSigner[] prevIterationCodeSigners = null;
        for (int i = 1; i <= 2; i++) {
            final CodeSigner[] codeSigners = jarURLConn.getJarEntry().getCodeSigners();
            assertNotNull(codeSigners, "null codesigners for signed JAR entry: " + uri);
            assertNotEquals(0, codeSigners.length, "empty codesigners for signed JAR entry: " + uri);
            final List<? extends Certificate> certs = codeSigners[0].getSignerCertPath().getCertificates();
            assertNotNull(certs, "null certificates from codesigner");
            assertNotEquals(0, certs.size(), "empty certificates from codesigner");
            assertInstanceOf(X509Certificate.class, certs.getFirst(), "unexpected certificate type");
            final String subject = ((X509Certificate) certs.getFirst()).getSubjectX500Principal().getName();
            assertEquals(CERT_SUBJECT, subject, "unexpected subject in certificate");
            if (i > 1) {
                // verify that each call to getCodeSigners() returns
                // a new instance of the array.
                // intentional identity check
                assertNotSame(prevIterationCodeSigners, codeSigners, "getCodeSigners() did not"
                        + " return a new array");
            }
            prevIterationCodeSigners = codeSigners;
        }
    }

    private static KeyStore.PrivateKeyEntry generatePrivateKey() throws Exception {
        final CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");
        gen.generate(1048); // Small key size makes test run faster
        final long oneDay = TimeUnit.DAYS.toSeconds(1);
        final Certificate cert = gen.getSelfCertificate(new X500Name(CERT_SUBJECT), oneDay);
        return new KeyStore.PrivateKeyEntry(gen.getPrivateKey(), new Certificate[]{cert});
    }

    private static Path createSignedJar(final KeyStore.PrivateKeyEntry privateKey)
            throws Exception {

        // first create a unsigned JAR
        final Path unsignedJar = Path.of("test-8377985-unsigned.jar");
        final Manifest manifest = new Manifest();
        final Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.putValue("Manifest-Version", "1.0");
        try (OutputStream os = Files.newOutputStream(unsignedJar);
             JarOutputStream jaros = new JarOutputStream(os, manifest)) {
            jaros.putNextEntry(new JarEntry(JAR_ENTRY_NAME));
            jaros.write(new byte[]{0x42});
            jaros.closeEntry();
        }

        // use a JarSigner to sign the JAR
        final JarSigner signer = new JarSigner.Builder(privateKey)
                .signerName("abcdef")
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .build();

        final Path signedJar = Path.of("test-8377985-signed.jar");
        try (ZipFile zip = new ZipFile(unsignedJar.toFile());
             OutputStream out = Files.newOutputStream(signedJar)) {
            signer.sign(zip, out);
        }
        return signedJar;
    }
}
