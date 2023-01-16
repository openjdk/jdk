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
 * @summary JARs with pending signature files (where .RSA comes before .SF) should verify correctly
 */

import jdk.security.jarsigner.JarSigner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class PendingBlocksJar {

    public static void main(String[] args) throws Exception {
        File jar = createJarFile();
        File signed = signJarFile(jar);
        File pendingBlocks = moveBlockFirst(signed);
        File invalid = invalidate(pendingBlocks);

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

    private static void checkSigned(File b) throws Exception {
        try(JarFile jf = new JarFile(b, true)) {

            JarEntry je = jf.getJarEntry("a.txt");
            try(InputStream in = jf.getInputStream(je)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }

    /**
     * Invalidate signed file by modifying the contents of "a.txt"
     */
    private static File invalidate(File s) throws Exception{
        File invalid = File.createTempFile("pending-block-file-invalidated-", ".jar");

        try(ZipFile zip = new ZipFile(s);
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(invalid))) {

            for(ZipEntry ze : Collections.list(zip.entries())) {
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

    private static File moveBlockFirst(File s) throws Exception {
        File b = File.createTempFile("pending-block-file-blockfirst-", ".jar");
        try(ZipFile in = new ZipFile(s);
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(b))) {

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
        try(InputStream is = in.getInputStream(in.getEntry(name))) {
            is.transferTo(out);
        }
    }

    private static File signJarFile(File f) throws Exception {
        File s = File.createTempFile("pending-block-file-signed-", ".jar");

        new File("ks").delete();

        sun.security.tools.keytool.Main.main(
                ("-keystore ks -storepass changeit -keypass changeit -dname" +
                        " CN=SIGNER" +" -alias r -genkeypair -keyalg rsa").split(" "));

        char[] pass = "changeit".toCharArray();

        KeyStore ks = KeyStore.getInstance(
                new File("ks"), pass);
        PrivateKey pkr = (PrivateKey)ks.getKey("r", pass);

        CertPath cp = CertificateFactory.getInstance("X.509")
                .generateCertPath(Arrays.asList(ks.getCertificateChain("r")));

        JarSigner signer = new JarSigner.Builder(pkr, cp)
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .signerName("SIGNER")
                .build();

        try(ZipFile in = new ZipFile(f);
            OutputStream out = new FileOutputStream(s)) {
            signer.sign(in, out);
        }

        return s;
    }

    /**
     * Create a jar file with single entry "a.txt" containing "a"
     */
    private static File createJarFile() throws Exception {
        File f = File.createTempFile("pending-block-file-", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try(JarOutputStream out = new JarOutputStream(new FileOutputStream(f), manifest)) {
            out.putNextEntry(new JarEntry("a.txt"));
            out.write("a".getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }
}
