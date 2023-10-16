/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.security.tools.keytool
 * @summary JARs with pending block files (where .RSA comes before .SF) should verify correctly
 */

import jdk.security.jarsigner.JarSigner;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Collections;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SignedJarPendingBlock {

    public static void main(String[] args) throws Exception {
        Path jar = createJarFile();
        Path signed = signJarFile(jar);
        Path pendingBlocks = moveBlockFirst(signed);
        Path invalid = invalidate(pendingBlocks);

        // 1: Regular signed JAR with no pending blocks should verify
        checkSigned(signed);

        // 2: Signed jar with pending blocks should verify
        checkSigned(pendingBlocks);

        // 3: Invalid signed jar with pending blocks should throw SecurityException
        try {
            checkSigned(invalid);
            throw new Exception("Expected invalid digest to be detected");
        } catch (SecurityException se) {
            // Ignore
        }
    }

    private static void checkSigned(Path b) throws Exception {
        try (JarFile jf = new JarFile(b.toFile(), true)) {

            JarEntry je = jf.getJarEntry("a.txt");
            try (InputStream in = jf.getInputStream(je)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }

    /**
     * Invalidate signed file by modifying the contents of "a.txt"
     */
    private static Path invalidate(Path s) throws Exception{
        Path invalid = Path.of("pending-block-file-invalidated.jar");

        try (ZipFile zip = new ZipFile(s.toFile());
            ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(invalid))) {

            for (ZipEntry ze : Collections.list(zip.entries())) {
                String name = ze.getName();
                out.putNextEntry(new ZipEntry(name));

                if (name.equals("a.txt")) {
                    // Change the contents of a.txt to trigger SignatureException
                    out.write("b".getBytes(StandardCharsets.UTF_8));
                } else {
                    try (InputStream in = zip.getInputStream(ze)) {
                        in.transferTo(out);
                    }
                }
            }
        }
        return invalid;
    }

    private static Path moveBlockFirst(Path s) throws Exception {
        Path b = Path.of("pending-block-file-blockfirst.jar");
        try (ZipFile in = new ZipFile(s.toFile());
            ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(b))) {

            copy("META-INF/MANIFEST.MF", in, out);

            // Switch the order of the RSA and SF files
            copy("META-INF/SIGNER.RSA", in, out);
            copy("META-INF/SIGNER.SF", in, out);

            copy("a.txt", in, out);
        }
        return b;
    }

    /**
     * Copy an entry from a ZipFile to a ZipOutputStream
     */
    private static void copy(String name, ZipFile in, ZipOutputStream out) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        try (InputStream is = in.getInputStream(in.getEntry(name))) {
            is.transferTo(out);
        }
    }

    private static Path signJarFile(Path j) throws Exception {
        Path s = Path.of("pending-block-file-signed.jar");

        Files.deleteIfExists(Path.of("ks"));

        sun.security.tools.keytool.Main.main(
                ("-keystore ks -storepass changeit -keypass changeit -dname" +
                        " CN=SIGNER" +" -alias r -genkeypair -keyalg rsa").split(" "));

        char[] pass = "changeit".toCharArray();

        KeyStore ks = KeyStore.getInstance(new File("ks"), pass);

        KeyStore.PrivateKeyEntry pke = (KeyStore.PrivateKeyEntry)
                ks.getEntry("r", new KeyStore.PasswordProtection(pass));

        JarSigner signer = new JarSigner.Builder(pke)
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .signerName("SIGNER")
                .build();

        try (ZipFile in = new ZipFile(j.toFile());
            OutputStream out = Files.newOutputStream(s)) {
            signer.sign(in, out);
        }

        return s;
    }

    /**
     * Create a jar file with single entry "a.txt" containing "a"
     */
    private static Path createJarFile() throws Exception {
        Path jar = Path.of("pending-block-file.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar),manifest)) {
            out.putNextEntry(new JarEntry("a.txt"));
            out.write("a".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }
}
