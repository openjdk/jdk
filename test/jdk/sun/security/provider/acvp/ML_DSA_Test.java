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

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;
import java.util.List;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-dsa.html
public class ML_DSA_Test implements Launcher.Test {

    @Override
    public List<String> supportedAlgs() {
        return List.of("ML-DSA");
    }

    @Override
    public void run(JSONValue kat, Provider provider) throws Exception {
        switch (kat.get("mode").asString()) {
            case "keyGen" -> mldsaGen(kat, provider);
            case "sigGen" -> mldsaSign(kat, provider);
            case "sigVer" -> mldsaVerify(kat, provider);
            default -> throw new UnsupportedOperationException();
        }
    }

    static void mldsaGen(JSONValue kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-DSA")
                : KeyPairGenerator.getInstance("ML-DSA", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-DSA")
                : KeyFactory.getInstance("ML-DSA", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var np = new NamedParameterSpec(pname);
            System.out.println(">> " + pname);
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                g.initialize(np, new RandomSource(xeh(c.get("seed").asString())));
                var kp = g.generateKeyPair();
                var pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                var sk = f.getKeySpec(kp.getPrivate(), EncodedKeySpec.class).getEncoded();
                Asserts.assertEqualsByteArray(pk, xeh(c.get("pk").asString()));
                Asserts.assertEqualsByteArray(sk, xeh(c.get("sk").asString()));
            }
            System.out.println();
        }
    }

    static void mldsaSign(JSONValue kat, Provider p) throws Exception {
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
                    public byte[] getEncoded() { return xeh(c.get("sk").asString()); }
                };
                var sr = new RandomSource(det ? new byte[32] : xeh(c.get("rnd").asString()));
                s.initSign(sk, sr);
                s.update(xeh(c.get("message").asString()));
                var sig = s.sign();
                Asserts.assertEqualsByteArray(sig, xeh(c.get("signature").asString()));
            }
            System.out.println();
        }
    }

    static void mldsaVerify(JSONValue kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var pk = new PublicKey() {
                public String getAlgorithm() { return pname; }
                public String getFormat() { return "RAW"; }
                public byte[] getEncoded() { return xeh(t.get("pk").asString()); }
            };
            System.out.println(">> " + pname + " verify");
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                s.initVerify(pk);
                s.update(xeh(c.get("message").asString()));
                var out = s.verify(xeh(c.get("signature").asString()))
                        == Boolean.parseBoolean(c.get("testPassed").asString());
                Asserts.assertTrue(out);
            }
            System.out.println();
        }
    }

    /////////////

    static byte[] xeh(String s) {
        return HexFormat.of().parseHex(s);
    }

    static class RandomSource extends SecureRandom {
        private byte[] buffer;
        private int offset;
        public RandomSource(byte[]... data) {
            var os = new ByteArrayOutputStream();
            for (byte[] b : data) {
                os.writeBytes(b);
            }
            buffer = os.toByteArray();
            offset = 0;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (bytes.length > buffer.length - offset) {
                throw new IllegalStateException("Not enough bytes");
            }
            System.arraycopy(buffer, offset, bytes, 0, bytes.length);
            offset += bytes.length;
        }
    }
}
