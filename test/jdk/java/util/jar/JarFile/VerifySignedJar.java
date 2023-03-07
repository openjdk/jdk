/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /test/lib
 * @modules java.base/sun.security.x509
 * @modules java.base/sun.security.tools.keytool
 * @bug 4419266 4842702
 * @summary Make sure verifying signed Jar doesn't throw SecurityException
 */
import jdk.security.jarsigner.JarSigner;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static jdk.test.lib.Utils.runAndCheckException;


public class VerifySignedJar {

    public static void main(String[] args) throws Exception {

        Path j = createJar();
        Path s = signJar(j, keyEntry("cn=duke"));

        try (JarFile jf = new JarFile(s.toFile())) {

            for (JarEntry e: Collections.list(jf.entries())) {
                // Reading entry to trigger verification
                jf.getInputStream(e).transferTo(OutputStream.nullOutputStream());
                // Check that all regular files are signed by duke
                if (!e.getName().startsWith("META-INF/")) {
                    checkSignedBy(e, "cn=duke");
                }
            }

            // Read ZIP and JAR entries by name
            Objects.requireNonNull(jf.getEntry("getprop.class"));
            Objects.requireNonNull(jf.getJarEntry("getprop.class"));

            // Make sure we throw NPE on null parameters
            runAndCheckException(() -> jf.getEntry(null), NullPointerException.class);
            runAndCheckException(() -> jf.getJarEntry(null), NullPointerException.class);
            runAndCheckException(() -> jf.getInputStream(null), NullPointerException.class);

        } catch (SecurityException se) {
            throw new Exception("Got SecurityException when verifying signed " +
                "jar:" + se);
        }
    }

    // Check that a JAR entry is signed by an expected DN
    private static void checkSignedBy(JarEntry e, String expectedDn) throws Exception {
        Certificate[] certs = e.getCertificates();
        if (certs == null || certs.length == 0) {
            throw new Exception("JarEntry has no certificates: " + e.getName());
        }

        if (certs[0] instanceof X509Certificate x) {
            String name = x.getSubjectX500Principal().getName();
            if (!name.equalsIgnoreCase(expectedDn)) {
                throw new Exception("Expected entry signed by %s, was %s".formatted(name, expectedDn));
            }
        } else {
            throw new Exception("Expected JarEntry.getCertificate to return X509Certificate");
        }
    }

    private static Path createJar() throws Exception {
        Path j = Path.of("unsigned.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(j))){
            out.putNextEntry(new JarEntry("getprop.class"));
            out.write(new byte[] {(byte) 0XCA, (byte) 0XFE, (byte) 0XBA, (byte) 0XBE});
        }
        return j;
    }

    private static Path signJar(Path j, KeyStore.PrivateKeyEntry entry) throws Exception {
        Path s = Path.of("signed.jar");

        JarSigner signer = new JarSigner.Builder(entry)
                .signerName("zigbert")
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .build();

        try (ZipFile zip = new ZipFile(j.toFile());
            OutputStream out = Files.newOutputStream(s)) {
            signer.sign(zip, out);
        }

        return s;
    }

    private static KeyStore.PrivateKeyEntry keyEntry(String dname) throws Exception {

        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");

        gen.generate(1048); // Small key size makes test run faster

        var oneDay = TimeUnit.DAYS.toSeconds(1);
        Certificate cert = gen.getSelfCertificate(new X500Name(dname), oneDay);

        return new KeyStore.PrivateKeyEntry(gen.getPrivateKey(),
                new Certificate[] {cert});
    }
}
