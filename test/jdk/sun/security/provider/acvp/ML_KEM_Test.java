/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.crypto.provider.ML_KEM_Impls;
import jdk.test.lib.Asserts;
import jdk.test.lib.json.JSONValue;
import jdk.test.lib.security.FixedSecureRandom;
import sun.security.util.DerOutputStream;

import javax.crypto.KEM;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.NamedParameterSpec;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-kem.html
public class ML_KEM_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {
        var mode = kat.get("mode").asString();
        switch (mode) {
            case "keyGen" -> keyGenTest(kat, provider);
            case "encapDecap" -> encapDecapTest(kat, provider);
            default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static NamedParameterSpec genParams(String pname) {
       return switch (pname) {
            case "ML-KEM-512" -> NamedParameterSpec.ML_KEM_512;
            case "ML-KEM-768" -> NamedParameterSpec.ML_KEM_768;
            case "ML-KEM-1024" -> NamedParameterSpec.ML_KEM_1024;
            default -> throw new RuntimeException("Unknown params: " + pname);
        };
    }

    static void keyGenTest(JSONValue kat, Provider p) throws Exception {
        var g = p == null
                ? KeyPairGenerator.getInstance("ML-KEM")
                : KeyPairGenerator.getInstance("ML-KEM", p);
        var f = p == null
                ? KeyFactory.getInstance("ML-KEM")
                : KeyFactory.getInstance("ML-KEM", p);
        for (var t : kat.get("testGroups").asArray()) {
            var pname = t.get("parameterSet").asString();
            var np = genParams(pname);
            System.out.println(">> " + pname);
            for (var c : t.get("tests").asArray()) {
                System.out.print(c.get("tcId").asString() + " ");
                var seed = toByteArray(c.get("d").asString() + c.get("z").asString());
                g.initialize(np, new FixedSecureRandom(seed));
                var kp = g.generateKeyPair();
                var pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                Asserts.assertEqualsByteArray(toByteArray(c.get("ek").asString()), pk);
                Asserts.assertEqualsByteArray(
                        toByteArray(c.get("dk").asString()),
                        ML_KEM_Impls.seedToExpanded(pname, seed));
            }
            System.out.println();
        }
    }

    static void encapDecapTest(JSONValue kat, Provider p) throws Exception {
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
                        public byte[] getEncoded() { return toByteArray(c.get("ek").asString()); }
                    };
                    var e = g.newEncapsulator(
                            ek, new FixedSecureRandom(toByteArray(c.get("m").asString())));
                    var enc = e.encapsulate();
                    Asserts.assertEqualsByteArray(
                            toByteArray(c.get("c").asString()), enc.encapsulation());
                    Asserts.assertEqualsByteArray(
                            toByteArray(c.get("k").asString()), enc.key().getEncoded());
                }
                System.out.println();
            } else if (function.equals("decapsulation")) {
                var dk = new PrivateKey() {
                    public String getAlgorithm() { return pname; }
                    public String getFormat() { return "RAW"; }
                    public byte[] getEncoded() { return oct(toByteArray(t.get("dk").asString())); }
                };
                for (var c : t.get("tests").asArray()) {
                    System.out.print(c.get("tcId").asString() + " ");
                    var d = g.newDecapsulator(dk);
                    var k = d.decapsulate(toByteArray(c.get("c").asString()));
                    Asserts.assertEqualsByteArray(toByteArray(c.get("k").asString()), k.getEncoded());
                }
                System.out.println();
            }
        }
    }

    static byte[] oct(byte[] in) {
        return new DerOutputStream().putOctetString(in).toByteArray();
    }
}
