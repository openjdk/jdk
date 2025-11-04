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

/*
 * @test
 * @bug 8298387
 * @summary signing with ML-DSA
 * @library /test/lib
 * @modules java.base/sun.security.util
 */

import jdk.security.jarsigner.JarSigner;
import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.security.DerUtils;
import jdk.test.lib.util.JarUtils;
import sun.security.util.KnownOIDs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public class ML_DSA {

    static List<String> SIGNERS = List.of("44", "65", "87");

    public static void main(String[] args) throws Exception {
        prepare();
        testAPI();
        testTool(); // call this last, it modified a.jar.
    }

    private static void prepare() throws Exception {
        for (var signer : SIGNERS) {
            SecurityTools.keytool("-keystore ks -storepass changeit -genkeypair -alias "
                            + signer + " -keyalg ML-DSA-" + signer + " -dname CN=" + signer)
                    .shouldHaveExitValue(0);
        }
        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."),
                Files.write(Path.of("a"), new byte[10]));
    }

    static void testAPI() throws Exception {
        var pass = "changeit".toCharArray();
        var ks = KeyStore.getInstance(new File("ks"), pass);
        for (var signer : SIGNERS) {
            var jsb = new JarSigner.Builder((KeyStore.PrivateKeyEntry)
                    ks.getEntry(signer, new KeyStore.PasswordProtection(pass)));
            try (var zf = new ZipFile("a.jar");
                 var of = Files.newOutputStream(Path.of(signer + ".jar"))) {
                jsb.signerName(signer).build().sign(zf, of);
            }
            try (var jf = new JarFile(signer + ".jar")) {
                var je = jf.getJarEntry("a");
                jf.getInputStream(je).readAllBytes();
                Asserts.assertEquals(1, je.getCertificates().length);
                checkDigestAlgorithm(jf, signer, KnownOIDs.SHA_512);
            }
        }
    }

    static void testTool() throws Exception {
        for (var signer : SIGNERS) {
            SecurityTools.jarsigner("-keystore ks -storepass changeit a.jar " + signer)
                    .shouldHaveExitValue(0);
        }
        SecurityTools.jarsigner("-verify a.jar -verbose -certs")
                .shouldHaveExitValue(0)
                .shouldContain("jar verified");

        try (var jf = new JarFile("a.jar")) {
            var je = jf.getJarEntry("a");
            jf.getInputStream(je).readAllBytes();
            Asserts.assertEquals(3, je.getCertificates().length);
            for (var signer : SIGNERS) {
                checkDigestAlgorithm(jf, signer, KnownOIDs.SHA_512);
            }
        }
    }

    static void checkDigestAlgorithm(JarFile jf, String alias, KnownOIDs digAlg)
            throws Exception {
        var p7 = jf.getInputStream(jf.getEntry("META-INF/" + alias + ".DSA"))
                .readAllBytes();
        // SignedData - digestAlgorithms
        DerUtils.checkAlg(p7, "10100", digAlg);
        // SignedData - signerInfos - digestAlgorithm
        DerUtils.checkAlg(p7, "104020", digAlg);
        // SignedData - signerInfos - signatureAlgorithm
        DerUtils.checkAlg(p7, "104040", KnownOIDs.valueOf("ML_DSA_" + alias));
        // SignedData - signerInfos - signedAttrs - CMSAlgorithmProtection - digestAlgorithm
        DerUtils.checkAlg(p7, "1040321000", digAlg);
        // SignedData - signerInfos - signedAttrs - CMSAlgorithmProtection - signatureAlgorithm
        DerUtils.checkAlg(p7, "1040321010", KnownOIDs.valueOf("ML_DSA_" + alias));
    }
}
