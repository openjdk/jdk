/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278851
 * @summary Check that jar entry with at least one non-disabled digest
 *          algorithm in manifest is treated as signed
 * @modules java.base/sun.security.tools.keytool
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build jdk.test.lib.util.JarUtils
 *        jdk.test.lib.security.SecurityUtils
 * @run main/othervm JarWithOneNonDisabledDigestAlg
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.cert.CertPathValidatorException;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import jdk.security.jarsigner.JarSigner;

import jdk.test.lib.util.JarUtils;
import sun.security.util.ConstraintsParameters;
import sun.security.util.DisabledAlgorithmConstraints;
import sun.security.util.JarConstraintsParameters;

public class JarWithOneNonDisabledDigestAlg {

    private static final String PASS = "changeit";
    private static final Path TESTFILE1 = Path.of("testfile1");
    private static final Path TESTFILE2 = Path.of("testfile2");
    private static final Path UNSIGNED_JAR = Path.of("unsigned.jar");
    private static final Path SIGNED_JAR = Path.of("signed.jar");
    private static final Path SIGNED_TWICE_JAR = Path.of("signed2.jar");
    private static final Path MULTI_SIGNED_JAR = Path.of("multi-signed.jar");
    private static final Path CURRENT_DIR = Path.of(".");

    public static void main(String[] args) throws Exception {
        // Sanity check: Assert that MD5 is disabled, SHA-256 enabled
        checkDigestAlgorithmPermits();

        // Create an unsigned JAR with a single file
        Files.write(TESTFILE1, TESTFILE1.toString().getBytes());
        JarUtils.createJarFile(UNSIGNED_JAR, CURRENT_DIR, TESTFILE1);

        // Generate a keystore with two different signers
        genkeypair("-alias SIGNER1");
        genkeypair("-alias SIGNER2");
        KeyStore ks = loadKeyStore();

        // Sign JAR twice with same signer but different digest algorithms
        // so that each entry in manifest file contains two digest values.
        // Note that MD5 is a disabled digest algorithm, while SHA-256 is not
        signJarFile(ks, "SIGNER1", "MD5", UNSIGNED_JAR, SIGNED_JAR);
        signJarFile(ks, "SIGNER1", "SHA256", SIGNED_JAR, SIGNED_TWICE_JAR);
        checkThatJarIsSigned(SIGNED_TWICE_JAR, Map.of(TESTFILE1.toString(), 1));

        // add another file to the JAR
        Files.write(TESTFILE2, TESTFILE2.toString().getBytes());
        JarUtils.updateJarFile(SIGNED_TWICE_JAR, CURRENT_DIR, TESTFILE2);

        // Sign the updated JAR, now with a different signer and with an enabled digest alg
        signJarFile(ks, "SIGNER2", "SHA256", SIGNED_TWICE_JAR, MULTI_SIGNED_JAR);

        // TESTFILE1 should have two signers and TESTFILE2 should have one signer.
        checkThatJarIsSigned(MULTI_SIGNED_JAR,
                Map.of(TESTFILE1.toString(), 2,
                        TESTFILE2.toString(), 1)
        );
    }

    private static void checkDigestAlgorithmPermits() throws Exception {
        ConstraintsParameters cp = new JarConstraintsParameters(Collections.emptyList(), new Date());
        DisabledAlgorithmConstraints jarConstraints = DisabledAlgorithmConstraints.jarConstraints();
        try {
            jarConstraints.permits("MD5", cp, false);
            throw new Exception("This test assumes that MD5 is disabled");
        } catch (CertPathValidatorException e) {
            // Ignore
        }
        try {
            jarConstraints.permits("SHA256", cp, false);
        } catch (CertPathValidatorException e) {
            throw new Exception("This test assumes that SHA256 is enabled");
        }
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream fis = Files.newInputStream(Path.of("keystore"))) {
            ks.load(fis, PASS.toCharArray());
        }
        return ks;
    }

    private static KeyStore.PrivateKeyEntry getEntry(KeyStore ks, String alias)
        throws Exception {

        return (KeyStore.PrivateKeyEntry)
            ks.getEntry(alias,
                new KeyStore.PasswordProtection(PASS.toCharArray()));
    }

    private static void genkeypair(String cmd) throws Exception {
        cmd = "-genkeypair -keystore keystore -storepass " + PASS +
              " -keypass " + PASS + " -keyalg rsa -sigalg SHA256withRSA " +
              "-dname CN=Duke " + cmd;
        sun.security.tools.keytool.Main.main(cmd.split(" "));
    }

    private static void signJarFile(KeyStore ks, String alias,
        String digestAlg, Path inputFile, Path outputFile)
        throws Exception {

        JarSigner signer = new JarSigner.Builder(getEntry(ks, alias))
                 .digestAlgorithm(digestAlg)
                 .signerName(alias)
                 .build();

        try (ZipFile in = new ZipFile(inputFile.toFile());
            OutputStream out = Files.newOutputStream(outputFile)) {
            signer.sign(in, out);
        }
    }

    private static void checkThatJarIsSigned(Path jarFile, Map<String, Integer> expected)
        throws Exception {

        try (JarFile jf = new JarFile(jarFile.toFile(), true)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || isSigningRelated(entry.getName())) {
                    continue;
                }
                try (InputStream is = jf.getInputStream(entry)) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
                CodeSigner[] signers = entry.getCodeSigners();
                if (!expected.containsKey(entry.getName())) {
                    throw new Exception("Unexpected entry " + entry.getName());
                }
                int expectedSigners = expected.get(entry.getName());
                int actualSigners = signers == null ? 0 : signers.length;

                if (expectedSigners != actualSigners) {
                    throw new Exception("Unexpected number of signers " +
                        "for " + entry.getName() + ": " + actualSigners +
                        ", expected " + expectedSigners);
                }
            }
        }
    }

    private static boolean isSigningRelated(String name) {
        name = name.toUpperCase(Locale.ENGLISH);
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        name = name.substring(9);
        if (name.indexOf('/') != -1) {
            return false;
        }
        return name.endsWith(".SF")
            || name.endsWith(".DSA")
            || name.endsWith(".RSA")
            || name.endsWith(".EC")
            || name.equals("MANIFEST.MF");
    }
}
