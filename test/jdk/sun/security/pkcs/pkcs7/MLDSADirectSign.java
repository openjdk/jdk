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

/*
 * @test
 * @bug 8393741
 * @summary ML-DSA CMS has no requirement on digest algorithm when
 *          signed attributes are not present
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.tools.keytool
 *          java.base/sun.security.util
 *          java.base/sun.security.x509
 * @library /test/lib
 */

import jdk.security.jarsigner.JarSigner;
import jdk.test.lib.Asserts;
import jdk.test.lib.security.DerUtils;
import jdk.test.lib.util.JarUtils;
import sun.security.pkcs.PKCS7;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.util.KnownOIDs;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MLDSADirectSign {

    public static void main(String[] args) throws Exception {

        CertAndKeyGen cag = new CertAndKeyGen("ML-DSA-65", "ML-DSA-65");
        cag.generate("ML-DSA-65");
        PrivateKey sk = cag.getPrivateKey();
        X509Certificate cert = cag.getSelfCertificate(new X500Name("CN=Me"), 1000);

        JarSigner signer = new JarSigner.Builder(sk, CertificateFactory
                .getInstance("X.509").generateCertPath(List.of(cert)))
                .build();

        // Create and sign a JAR file
        JarUtils.createJarFile(Path.of("a.jar"),
                Path.of("."),
                Files.writeString(Path.of("x"), "hello"));
        try (ZipFile in = new ZipFile("a.jar");
                OutputStream out = new FileOutputStream("b.jar")) {
            signer.sign(in, out);
        }

        // Re-sign the JAR using directSign. jarsigner no longer supports
        // signing in this way but still support verifying it. We have to
        // create the signature block manually.
        byte[] sf;
        try (ZipFile jf = new ZipFile("b.jar")) {
            ZipEntry je = jf.getEntry("META-INF/SIGNER.SF");
            sf = jf.getInputStream(je).readAllBytes();
        }
        byte[] p7 = PKCS7.generateSignedData("ML-DSA-65", null,
                sk, new X509Certificate[] { cert },
                sf, true,
                true,  // directSign
                AlgorithmId.get("SHA-1"), // use SHA-1 as digestAlgorithm
                null);

        // Confirm SHA-1 is put inside.
        DerUtils.checkAlg(p7, "10100", KnownOIDs.SHA_1);

        // Create the new signed JAR file.
        JarUtils.updateJar("b.jar", "c.jar",
                Map.of("META-INF/SIGNER.DSA", p7));

        // Confirm JAR is treated signed and SHA-1 is not rejected,
        try (JarFile jf = new JarFile("c.jar")) {
            JarEntry je = jf.getJarEntry("x");
            jf.getInputStream(je).readAllBytes();
            Asserts.assertEQ(1, je.getCodeSigners().length);
        }

        // although the policy already disliked SHA-1
        Asserts.assertTrue(Security.getProperty("jdk.jar.disabledAlgorithms")
                .contains("SHA1 denyAfter 2019"));
    }
}
