/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8189189
 * @summary Test ECDH primitive operations
 * @library /test/lib
 * @run main ECDHPrimitive
 */
public class ECDHPrimitive {


    private static final Map<String, String> NAME_MAP = Map.of(
            "P-256", "secp256r1",
            "P-384", "secp384r1",
            "P-521", "secp521r1"
    );

    public static void main(String[] args) throws Exception {
        Path testFile = Path.of(System.getProperty("test.src"), "KAS_ECC_CDH_PrimitiveTest.txt");

        ECParameterSpec ecParams = null;

        try (BufferedReader in = Files.newBufferedReader(testFile)) {
            Map<String, byte[]> values = new HashMap<>();
            String line = in.readLine();
            while (line != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) {
                    // ignore
                } else if (line.startsWith("[")) {
                    // change curve name
                    StringTokenizer tok = new StringTokenizer(line, "[]");
                    String name = tok.nextToken();
                    String curveName = lookupName(name);

                    if (curveName == null) {
                        System.out.println("Unknown curve: " + name
                                + ". Skipping test");
                        ecParams = null;
                    } else {
                        AlgorithmParameters params
                                = AlgorithmParameters.getInstance("EC");

                        params.init(new ECGenParameterSpec(curveName));
                        ecParams = params.getParameterSpec(
                                ECParameterSpec.class);
                        System.out.println("Testing curve: " + curveName);
                    }

                } else if (line.startsWith("ZIUT")) {
                    addKeyValue(line, values);
                    if (ecParams != null) {
                        runTest(ecParams, values);
                    }
                } else {
                    addKeyValue(line, values);
                }

                line = in.readLine();
            }
        }
    }

    private static void runTest(ECParameterSpec ecParams,
                                Map<String, byte[]> values) throws Exception {

        byte[] xArr = values.get("QCAVSx");
        BigInteger x = new BigInteger(1, xArr);
        byte[] yArr = values.get("QCAVSy");
        BigInteger y = new BigInteger(1, yArr);
        ECPoint w = new ECPoint(x, y);
        ECPublicKeySpec pubSpec = new ECPublicKeySpec(w, ecParams);

        byte[] dArr = values.get("dIUT");
        BigInteger d = new BigInteger(1, dArr);
        ECPrivateKeySpec priSpec = new ECPrivateKeySpec(d, ecParams);

        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey pub = kf.generatePublic(pubSpec);
        PrivateKey pri = kf.generatePrivate(priSpec);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(pri);
        ka.doPhase(pub, true);
        byte[] secret = ka.generateSecret();

        byte[] expectedSecret = values.get("ZIUT");
        Asserts.assertEqualsByteArray(secret, expectedSecret, "Incorrect secret value");
        int testIndex = values.get("COUNT")[0];
        System.out.println("Test " + testIndex + " passed.");
    }

    private static void addKeyValue(String line, Map<String, byte[]> values) {
        StringTokenizer tok = new StringTokenizer(line, " =");
        String key = tok.nextToken();
        String value = tok.nextToken();
        byte[] valueArr;
        if (value.length() <= 2) {
            valueArr = new byte[1];
            valueArr[0] = Byte.parseByte(value, 10);
        } else {
            valueArr = HexFormat.of().parseHex(value);
        }

        values.put(key, valueArr);
    }

    private static String lookupName(String name) {
        return NAME_MAP.get(name);
    }
}