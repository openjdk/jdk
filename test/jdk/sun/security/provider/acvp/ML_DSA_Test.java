/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import jdk.test.lib.json.JSONValue;
import jdk.test.lib.security.FixedSecureRandom;
import sun.security.provider.ML_DSA_Impls;

import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-dsa.html
public class ML_DSA_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {

        // We only have ML-DSA test for internal functions, which
        // is equivalent to the FIP 204 draft.
        ML_DSA_Impls.version = ML_DSA_Impls.Version.DRAFT;

        var mode = kat.get("mode").asString();
        switch (mode) {
            case "keyGen" -> keyGenTest(kat, provider);
            case "sigGen" -> sigGenTest(kat, provider);
            case "sigVer" -> sigVerTest(kat, provider);
            default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static NamedParameterSpec genParams(String pname) {
       return switch (pname) {
            case "ML-DSA-44" -> NamedParameterSpec.ML_DSA_44;
            case "ML-DSA-65" -> NamedParameterSpec.ML_DSA_65;
            case "ML-DSA-87" -> NamedParameterSpec.ML_DSA_87;
            default -> throw new RuntimeException("Unknown params: " + pname);

        };
    }

    static void keyGenTest(JSONValue kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-DSA")
                : KeyPairGenerator.getInstance("ML-DSA", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-DSA")
                : KeyFactory.getInstance("ML-DSA", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var np = genParams(pname);
            System.out.println(">> " + pname);
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                g.initialize(np, new FixedSecureRandom(toByteArray(c.get("seed").asString())));
                var kp = g.generateKeyPair();
                var pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                var sk = f.getKeySpec(kp.getPrivate(), EncodedKeySpec.class).getEncoded();
                Asserts.assertEqualsByteArray(pk, toByteArray(c.get("pk").asString()));
                Asserts.assertEqualsByteArray(sk, toByteArray(c.get("sk").asString()));
            }
            System.out.println();
        }
    }

    static void sigGenTest(JSONValue kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var det = Boolean.parseBoolean(t.get("deterministic").asString());
            System.out.println(">> " + pname + " sign");
            for (var c : t.get("tests").asArray()) {
                System.out.print(Integer.parseInt(c.get("tcId").asString()) + " ");
                var sk = new PrivateKey() {
                    public String getAlgorithm() { return pname; }
                    public String getFormat() { return "RAW"; }
                    public byte[] getEncoded() { return toByteArray(c.get("sk").asString()); }
                };
                var sr = new FixedSecureRandom(
                        det ? new byte[32] : toByteArray(c.get("rnd").asString()));
                s.initSign(sk, sr);
                s.update(toByteArray(c.get("message").asString()));
                var sig = s.sign();
                Asserts.assertEqualsByteArray(
                        sig, toByteArray(c.get("signature").asString()));
            }
            System.out.println();
        }
    }

    static void sigVerTest(JSONValue kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var pk = new PublicKey() {
                public String getAlgorithm() { return pname; }
                public String getFormat() { return "RAW"; }
                public byte[] getEncoded() { return toByteArray(t.get("pk").asString()); }
            };
            System.out.println(">> " + pname + " verify");
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                // Only ML-DSA sigVer has negative tests
                var expected = Boolean.parseBoolean(c.get("testPassed").asString());
                var actual = true;
                try {
                    s.initVerify(pk);
                    s.update(toByteArray(c.get("message").asString()));
                    actual = s.verify(toByteArray(c.get("signature").asString()));
                } catch (InvalidKeyException | SignatureException e) {
                    actual = false;
                }
                Asserts.assertEQ(expected, actual);
            }
            System.out.println();
        }
    }
}
