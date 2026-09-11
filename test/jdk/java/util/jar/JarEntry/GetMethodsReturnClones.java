/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;

import jdk.security.jarsigner.JarSigner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @test
 * @bug 6337925
 * @summary Ensure that callers cannot modify the internal JarEntry cert and
 *          codesigner arrays.
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @run junit GetMethodsReturnClones
 */
class GetMethodsReturnClones {


    private static final String ENTRY_NAME = "foobar.txt";
    private static final String SIGNER_NAME = "DUMMY";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_TYPE = "RSA";

    private static List<JarEntry> jarEntries;

    /*
     * Creates a signed JAR file and initializes the "jarEntries"
     */
    @BeforeAll()
    static void setupJarEntries() throws Exception {
        Path unsigned = createJar();
        Path signed = signJar(unsigned);
        System.err.println("created signed JAR file at " + signed.toAbsolutePath());
        List<JarEntry> entries = new ArrayList<>();
        try (JarFile jf = new JarFile(signed.toFile(), true)) {
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                entries.add(je);
                try (InputStream is = jf.getInputStream(je)) {
                    // we just read. this will throw a SecurityException
                    // if a signature/digest check fails.
                    var _ = is.readAllBytes();
                }
            }
        }
        jarEntries = entries;
    }

    /*
     * For entries in the signed JAR file, this test verifies that if a non-null
     * array is returned by the JarEntry.getCertificates() method, then any subsequent
     * updates to that returned array do not propagate back to the original array.
     */
    @Test
    void testCertificatesArray() {
        for (JarEntry je : jarEntries) {
            Certificate[] certs = je.getCertificates();
            System.err.println("Certificates for " + je.getName() + " " + Arrays.toString(certs));
            if (isSignatureRelated(je)) {
                // we don't expect this entry to be signed
                assertNull(certs, "JarEntry.getCertificates() returned non-null for " + je.getName());
                continue;
            }
            assertNotNull(certs, "JarEntry.getCertificates() returned null for " + je.getName());
            assertNotNull(certs[0], "Certificate is null");

            certs[0] = null; // intentionally update the returned array
            certs = je.getCertificates(); // now get the certs again
            assertNotNull(certs, "JarEntry.getCertificates() returned null for " + je.getName());
            // verify that the newly returned array doesn't have the overwritten value
            assertNotNull(certs[0], "Internal certificates array was modified");
        }
    }

    /*
     * For entries in the signed JAR file, this test verifies that if a non-null
     * array is returned by the JarEntry.getCodeSigners() method, then any subsequent
     * updates to that returned array do not propagate back to the original array.
     */
    @Test
    void testCodeSignersArray() {
        for (JarEntry je : jarEntries) {
            CodeSigner[] signers = je.getCodeSigners();
            System.err.println("CodeSigners for " + je.getName() + " " + Arrays.toString(signers));
            if (isSignatureRelated(je)) {
                // we don't expect this entry to be signed
                assertNull(signers, "JarEntry.getCodeSigners() returned non-null for " + je.getName());
                continue;
            }
            assertNotNull(signers, "JarEntry.getCodeSigners() returned null for " + je.getName());
            assertNotNull(signers[0], "CodeSigner is null");

            signers[0] = null; // intentionally update the array
            signers = je.getCodeSigners(); // now get the codesigners again
            assertNotNull(signers, "JarEntry.getCodeSigners() returned null for " + je.getName());
            // verify that the newly returned array doesn't have the overwritten value
            assertNotNull(signers[0], "CodeSigner is null");
        }
    }

    private static Path createJar() throws IOException {
        final Path unsigned = Path.of("unsigned.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(unsigned))) {
            out.putNextEntry(new JarEntry(ENTRY_NAME));
            out.write("hello world".getBytes(US_ASCII));
        }
        return unsigned;
    }

    private static Path signJar(final Path unsigned) throws Exception {
        final Path signed = Path.of("signed.jar");
        final JarSigner signer = new JarSigner.Builder(privateKeyEntry())
                .signerName(SIGNER_NAME)
                .digestAlgorithm(DIGEST_ALGORITHM)
                .signatureAlgorithm(SIGNATURE_ALGORITHM)
                .build();
        try (ZipFile zip = new ZipFile(unsigned.toFile());
             OutputStream out = Files.newOutputStream(signed)) {
            signer.sign(zip, out);
        }
        return signed;
    }

    private static KeyStore.PrivateKeyEntry privateKeyEntry() throws Exception {
        final CertAndKeyGen gen = new CertAndKeyGen(KEY_TYPE, SIGNATURE_ALGORITHM);
        gen.generate(4096);
        final long oneDayInSecs = TimeUnit.SECONDS.convert(1, TimeUnit.DAYS);
        Certificate cert = gen.getSelfCertificate(new X500Name("cn=duke"), oneDayInSecs);
        return new KeyStore.PrivateKeyEntry(gen.getPrivateKey(), new Certificate[] {cert});
    }

    private static boolean isSignatureRelated(final JarEntry entry) {
        final String entryName = entry.getName();
        return entryName.equals("META-INF/" + SIGNER_NAME + ".SF")
                || entryName.equals("META-INF/" + SIGNER_NAME + ".RSA");
    }
}
