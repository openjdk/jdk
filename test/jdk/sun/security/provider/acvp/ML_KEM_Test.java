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

import javax.crypto.KEM;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-kem.html
public class ML_KEM_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {
        switch (kat.get("mode").asString()) {
            case "keyGen" -> mlkemGen(kat, provider);
            case "encapDecap" -> mlkemEnc(kat, provider);
            default -> throw new UnsupportedOperationException();
        }
    }

    static void mlkemGen(JSONValue kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-KEM")
                : KeyPairGenerator.getInstance("ML-KEM", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-KEM")
                : KeyFactory.getInstance("ML-KEM", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var np = new NamedParameterSpec(pname);
            System.out.println(">> " + pname);
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                g.initialize(np, new RandomSource(
                        xeh(c.get("d").asString()), xeh(c.get("z").asString())));
                var kp = g.generateKeyPair();
                var pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                var sk = f.getKeySpec(kp.getPrivate(), EncodedKeySpec.class).getEncoded();
                Asserts.assertEqualsByteArray(pk, xeh(c.get("ek").asString()));
                Asserts.assertEqualsByteArray(sk, xeh(c.get("dk").asString()));
            }
            System.out.println();
        }
    }

    static void mlkemEnc(JSONValue kat, Provider p) throws Exception {
        var g = p == null
                ? KEM.getInstance("ML-KEM")
                : KEM.getInstance("ML-KEM", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var function = t.get("function").asString();
            System.out.println(">> " + pname + " " + function);
            if (function.equals("encapsulation")) {
                for (var c : t.get("tests").asArray()) {
                    System.out.print(c.get("tcId").asString() + " ");
                    var ek = new PublicKey() {
                        public String getAlgorithm() { return pname; }
                        public String getFormat() { return "RAW"; }
                        public byte[] getEncoded() { return xeh(c.get("ek").asString()); }
                    };
                    var e = g.newEncapsulator(
                            ek, new RandomSource(xeh(c.get("m").asString())));
                    var enc = e.encapsulate();
                    Asserts.assertEqualsByteArray(enc.encapsulation(), xeh(c.get("c").asString()));
                    Asserts.assertEqualsByteArray(
                            enc.key().getEncoded(), xeh(c.get("k").asString()));
                }
                System.out.println();
            } else if (function.equals("decapsulation")) {
                var dk = new PrivateKey() {
                    public String getAlgorithm() { return pname; }
                    public String getFormat() { return "RAW"; }
                    public byte[] getEncoded() { return xeh(t.get("dk").asString()); }
                };
                for (var c : t.get("tests").asArray()) {
                    System.out.print(c.get("tcId").asString() + " ");
                    var d = g.newDecapsulator(dk);
                    var k = d.decapsulate(xeh(c.get("c").asString()));
                    Asserts.assertEqualsByteArray(k.getEncoded(), xeh(c.get("k").asString()));
                }
                System.out.println();
            }
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
