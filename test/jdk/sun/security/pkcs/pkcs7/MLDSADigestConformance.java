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
 * @bug 8349732
 * @summary ML-DSA digest alg conformance check
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import sun.security.pkcs.PKCS7;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

public class MLDSADigestConformance {

    static String[] ALL_KEY_ALGS = {"ML-DSA-44", "ML-DSA-65", "ML-DSA-87"};
    static String[] ALL_DIGEST_ALGS = {
            "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512",
            "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512",
            "SHAKE128-256", "SHAKE256-512"};
    static Map<String, List<String>> SUPPORTED = Map.of(
            "ML-DSA-44", List.of("SHA-256", "SHA-384", "SHA-512",
                    "SHA3-256", "SHA3-384", "SHA3-512",
                    "SHAKE128-256", "SHAKE256-512"),
            "ML-DSA-65", List.of("SHA-384", "SHA-512",
                    "SHA3-384", "SHA3-512", "SHAKE256-512"),
            "ML-DSA-87", List.of("SHA-512", "SHA3-512", "SHAKE256-512")
    );

    public static void main(String[] args) throws Exception {
        testSig("ML-DSA-44");
        testSig("ML-DSA-65");
        testSig("ML-DSA-87");
    }

    static void testSig(String keyAlg) throws Exception {
        System.out.println("Testing " + keyAlg);
        var cag = new CertAndKeyGen(keyAlg, keyAlg);
        cag.generate(keyAlg);
        var sk = cag.getPrivateKey();
        var certs = new X509Certificate[] {
                cag.getSelfCertificate(new X500Name("CN=Me"), 1000)
        };
        var count = testDigest(keyAlg, sk, certs, null);
        System.out.println("   digestAlg default: " + count);
        Asserts.assertEQ(count, 1);
        for (var da : ALL_DIGEST_ALGS) {
            count = testDigest(keyAlg, sk, certs, da);
            System.out.println("   digestAlg " + da + ": " + count);
            if (SUPPORTED.get(keyAlg).contains(da)) {
                Asserts.assertEQ(count, 1);
            } else {
                Asserts.assertEQ(count, 0);
            }
        }
    }

    static int testDigest(String keyAlg, PrivateKey sk,
            X509Certificate[] certs, String digestAlg) throws Exception {
        var content = "hello".getBytes(StandardCharsets.UTF_8);
        var p7 = PKCS7.generateSignedData(keyAlg, null,
                sk, certs,
                content, true, false,
                digestAlg == null ? null : AlgorithmId.get(digestAlg),
                null);
        try {
            return new PKCS7(p7).verify(null).length;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }
}
