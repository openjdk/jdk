/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build jdk.test.lib.util.JarUtils
 *        jdk.test.lib.security.SecurityUtils
 * @run main/othervm JarWithOneNonDisabledDigestAlg
 */

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import jdk.security.jarsigner.JarSigner;

import jdk.test.lib.util.JarUtils;
import jdk.test.lib.security.SecurityUtils;

public class JarWithOneNonDisabledDigestAlg {

    private static final String PASS = "changeit";
    private static final String TESTFILE1 = "testfile1";
    private static final String TESTFILE2 = "testfile2";

    public static void main(String[] args) throws Exception {
        SecurityUtils.removeFromDisabledAlgs("jdk.jar.disabledAlgorithms",
            List.of("SHA1"));
        Files.write(Path.of(TESTFILE1), TESTFILE1.getBytes());
        JarUtils.createJarFile(Path.of("unsigned.jar"), Path.of("."),
            Path.of(TESTFILE1));

        genkeypair("-alias SHA1 -sigalg SHA1withRSA");
        genkeypair("-alias SHA256 -sigalg SHA256withRSA");

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream("keystore")) {
            ks.load(fis, PASS.toCharArray());
        }

        // Sign JAR twice with same signer but different digest algorithms
        // so that each entry in manifest file contains two digest values.
        signJarFile(ks, "SHA1", "MD5", "unsigned.jar", "signed.jar");
        signJarFile(ks, "SHA1", "SHA1", "signed.jar", "signed2.jar");
        checkThatJarIsSigned("signed2.jar", false);

        // add another file to the JAR
        Files.write(Path.of(TESTFILE2), "testFile2".getBytes());
        JarUtils.updateJarFile(Path.of("signed2.jar"), Path.of("."),
            Path.of(TESTFILE2));

        // Sign again with different signer (SHA256) and SHA-1 digestalg.
        // TESTFILE1 should have two signers and TESTFILE2 should have one
        // signer.
        signJarFile(ks, "SHA256", "SHA1", "signed2.jar", "multi-signed.jar");

        checkThatJarIsSigned("multi-signed.jar", true);
    }

    private static KeyStore.PrivateKeyEntry getEntry(KeyStore ks, String alias)
        throws Exception {

        return (KeyStore.PrivateKeyEntry)
            ks.getEntry(alias,
                new KeyStore.PasswordProtection(PASS.toCharArray()));
    }

    private static void genkeypair(String cmd) throws Exception {
        cmd = "-genkeypair -keystore keystore -storepass " + PASS +
              " -keypass " + PASS + " -keyalg rsa -dname CN=Duke " + cmd;
        sun.security.tools.keytool.Main.main(cmd.split(" "));
    }

    private static void signJarFile(KeyStore ks, String alias,
        String digestAlg, String inputFile, String outputFile)
        throws Exception {

        JarSigner signer = new JarSigner.Builder(getEntry(ks, alias))
                 .digestAlgorithm(digestAlg)
                 .signerName(alias)
                 .build();

        try (ZipFile in = new ZipFile(inputFile);
            FileOutputStream out = new FileOutputStream(outputFile)) {
            signer.sign(in, out);
        }
    }

    private static void checkThatJarIsSigned(String jarFile, boolean multi)
        throws Exception {

        try (JarFile jf = new JarFile(jarFile, true)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || isSigningRelated(entry.getName())) {
                    continue;
                }
                InputStream is = jf.getInputStream(entry);
                while (is.read() != -1);
                CodeSigner[] signers = entry.getCodeSigners();
                if (signers == null) {
                    throw new Exception("JarEntry " + entry.getName() +
                        " is not signed");
                } else if (multi) {
                    if (entry.getName().equals(TESTFILE1) &&
                        signers.length != 2) {
                        throw new Exception("Unexpected number of signers " +
                            "for " + entry.getName() + ": " + signers.length);
                    } else if (entry.getName().equals(TESTFILE2) &&
                        signers.length != 1) {
                        throw new Exception("Unexpected number of signers " +
                            "for " + entry.getName() + ": " + signers.length);
                    }
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
