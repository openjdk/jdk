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

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.security.DerUtils;
import jdk.test.lib.util.JarUtils;
import sun.security.util.KnownOIDs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class ML_DSA {
    public static void main(String[] args) throws Exception {

        JarUtils.createJarFile(Path.of("a.jar"), Path.of("."),
                Files.write(Path.of("a"), new byte[10]));

        genKeyAndSign("ML-DSA-44", "M4");
        genKeyAndSign("ML-DSA-65", "M6");
        genKeyAndSign("ML-DSA-87", "M8");

        SecurityTools.jarsigner("-verify a.jar -verbose -certs")
                .shouldHaveExitValue(0)
                .shouldContain("jar verified");

        try (var jf = new JarFile("a.jar")) {

            var je = jf.getJarEntry("a");
            jf.getInputStream(je).readAllBytes();
            Asserts.assertEquals(3, je.getCertificates().length);

            checkDigestAlgorithm(jf, "M4", KnownOIDs.SHA_512);
            checkDigestAlgorithm(jf, "M6", KnownOIDs.SHA_512);
            checkDigestAlgorithm(jf, "M8", KnownOIDs.SHA_512);
        }
    }

    static void genKeyAndSign(String alg, String alias) throws Exception {
        SecurityTools.keytool("-keystore ks -storepass changeit -genkeypair -alias "
                        + alias + " -keyalg " + alg + " -dname CN=" + alias)
                .shouldHaveExitValue(0);
        SecurityTools.jarsigner("-keystore ks -storepass changeit a.jar " + alias)
                .shouldHaveExitValue(0);
    }

    static void checkDigestAlgorithm(JarFile jf, String alias, KnownOIDs expectAlg)
            throws Exception {
        var p7 = jf.getInputStream(jf.getEntry("META-INF/" + alias + ".DSA"))
                .readAllBytes();
        // SignedData - digestAlgorithms
        DerUtils.checkAlg(p7, "10100", expectAlg);
        // SignedData - signerInfos - digestAlgorithm
        DerUtils.checkAlg(p7, "104020", expectAlg);
        // SignedData - signerInfos - signedAttrs - CMSAlgorithmProtection - digestAlgorithm
        DerUtils.checkAlg(p7, "1040321000", expectAlg);
    }
}
