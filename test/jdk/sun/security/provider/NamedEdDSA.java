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

/*
 * @test
 * @modules java.base/sun.security.ec.ed
 *          java.base/sun.security.ec.point
 *          java.base/sun.security.jca
 *          java.base/sun.security.provider
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import sun.security.ec.ed.EdDSAOperations;
import sun.security.ec.ed.EdDSAParameters;
import sun.security.ec.point.AffinePoint;
import sun.security.jca.JCAUtil;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.NamedKeyPairGenerator;
import sun.security.provider.NamedSignature;

import java.security.*;
import java.security.spec.EdDSAParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.List;

public class NamedEdDSA {

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("Named", "0", "");
            put("KeyPairGenerator.EdDSA", KPG.class.getName());
            put("KeyPairGenerator.Ed25519", KPG.S2.class.getName());
            put("KeyPairGenerator.Ed448", KPG.S4.class.getName());
            put("KeyFactory.EdDSA", KF.class.getName());
            put("KeyFactory.Ed25519", KF.S2.class.getName());
            put("KeyFactory.Ed448", KF.S4.class.getName());
            put("Signature.EdDSA", SIG.class.getName());
            put("Signature.Ed25519", SIG.S2.class.getName());
            put("Signature.Ed448", SIG.S4.class.getName());
        }
    }

    public static class SIG extends NamedSignature {
        public SIG() {
            super("EdDSA", "Ed25519", "Ed448");
        }

        protected SIG(String pname) {
            super("EdDSA", pname);
        }

        public static class S2 extends SIG {
            public S2() {
                super("Ed25519");
            }
        }

        public static class S4 extends SIG {
            public S4() {
                super("Ed448");
            }
        }

        @Override
        public byte[] implSign(String name, byte[] sk, Object sk2, byte[] msg, SecureRandom sr) throws SignatureException {
            return getOps(name).sign(plain, sk, msg);
        }

        @Override
        public boolean implVerify(String name, byte[] pk, Object pk2, byte[] msg, byte[] sig) throws SignatureException {
            return getOps(name).verify(plain, (AffinePoint) pk2, pk, msg, sig);
        }

        @Override
        public Object implCheckPublicKey(String name, byte[] pk) throws InvalidKeyException {
            return getOps(name).decodeAffinePoint(InvalidKeyException::new, pk);
        }
    }

    public static class KF extends NamedKeyFactory {
        public KF() {
            super("EdDSA", "Ed25519", "Ed448");
        }

        protected KF(String pname) {
            super("EdDSA", pname);
        }

        public static class S2 extends KF {
            public S2() {
                super("Ed25519");
            }
        }

        public static class S4 extends KF {
            public S4() {
                super("Ed448");
            }
        }
    }

    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            super("EdDSA", "Ed25519", "Ed448");
        }

        protected KPG(String pname) {
            super("EdDSA", pname);
        }

        public static class S2 extends KPG {
            public S2() {
                super("Ed25519");
            }
        }

        public static class S4 extends KPG {
            public S4() {
                super("Ed448");
            }
        }

        @Override
        public byte[][] implGenerateKeyPair(String pname, SecureRandom sr) {
            sr = sr == null ? JCAUtil.getDefSecureRandom() : sr;
            var op = getOps(pname);
            var sk = op.generatePrivate(sr);
            var point = op.computePublic(sk);
            byte[] encodedPoint = point.getY().toByteArray();
            reverse(encodedPoint);
            // array may be too large or too small, depending on the value
            encodedPoint = Arrays.copyOf(encodedPoint, op.getParameters().getKeyLength());
            // set the high-order bit of the encoded point
            byte msb = (byte) (point.isXOdd() ? 0x80 : 0);
            encodedPoint[encodedPoint.length - 1] |= msb;
            return new byte[][] { encodedPoint, sk };
        }

        private static void swap(byte[] arr, int i, int j) {
            byte tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }

        private static void reverse(byte [] arr) {
            int i = 0;
            int j = arr.length - 1;

            while (i < j) {
                swap(arr, i, j);
                i++;
                j--;
            }
        }
    }

    private static EdDSAOperations getOps(String pname) {
        var op = switch (pname) {
            case "Ed25519" -> e2;
            case "Ed448" -> e4;
            default -> throw new AssertionError("unknown pname " + pname);
        };
        return op;
    }

    static final EdDSAParameterSpec plain = new EdDSAParameterSpec(false);
    static final EdDSAOperations e2, e4;
    static {
        try {
            e2 = new EdDSAOperations(EdDSAParameters.getBySize(AssertionError::new, 255));
            e4 = new EdDSAOperations(EdDSAParameters.getBySize(AssertionError::new, 448));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        var ps = List.of(new ProviderImpl(), Security.getProvider("SunEC"));
        for (var p1 : ps) {
            for (var p2 : ps) {
                for (var p3 : ps) {
                    test(p1, p2, p3);
                }
            }
        }
    }

    static void test(Provider p1, Provider p2, Provider p3) throws Exception {
        System.out.println(p1.getName() + " " + p2.getName() + " " + p3.getName());
        var g = KeyPairGenerator.getInstance("EdDSA", p1);
        g.initialize(NamedParameterSpec.ED448);
        var kp = g.generateKeyPair();
        var s1 = Signature.getInstance("EdDSA", p2);
        var s2 = Signature.getInstance("EdDSA", p3);
        var f1 = KeyFactory.getInstance("EdDSA", p2);
        var f2 = KeyFactory.getInstance("EdDSA", p3);
        s1.initSign((PrivateKey) f1.translateKey(kp.getPrivate()));
        var sig = s1.sign();
        s2.initVerify((PublicKey) f2.translateKey(kp.getPublic()));
        Asserts.assertTrue(s2.verify(sig));
    }
}
