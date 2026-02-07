/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.provider;

import sun.security.jca.JCAUtil;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.util.DerOutputStream;
import sun.security.util.KeyChoices;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

public class ML_DSA_Impls {

    private static final int SEED_LEN = 32;

    public static byte[] seedToExpanded(String pname, byte[] seed) {
        var impl = new ML_DSA(name2int(pname));
        var sk = impl.generateKeyPairInternal(seed).privateKey();
        try {
            return impl.skEncode(sk);
        } finally {
            sk.destroy();
        }
    }

    public static NamedX509Key privKeyToPubKey(NamedPKCS8Key npk) {
        var dsa = new ML_DSA(name2int(npk.getParams().getName()));
        return new NamedX509Key(npk.getAlgorithm(),
                npk.getParams().getName(),
                dsa.pkEncode(dsa.privKeyToPubKey(dsa.skDecode(npk.getExpanded()))));
    }

    static int name2int(String pname) {
        if (pname.endsWith("44")) {
            return 2;
        } else if (pname.endsWith("65")) {
            return 3;
        } else if (pname.endsWith("87")) {
            return 5;
        } else {
            // should not happen
            throw new ProviderException("Unknown name " + pname);
        }
    }

    public sealed static class KPG
        extends NamedKeyPairGenerator permits KPG2, KPG3, KPG5 {

        public KPG() {
            // ML-DSA-65 is default
            super("ML-DSA", "ML-DSA-65", "ML-DSA-44", "ML-DSA-87");
        }

        public KPG(String pname) {
            super("ML-DSA", pname);
        }

        @Override
        protected byte[][] implGenerateKeyPair(String pname, SecureRandom random) {
            byte[] seed = new byte[SEED_LEN];
            var r = random != null ? random : JCAUtil.getDefSecureRandom();
            r.nextBytes(seed);

            ML_DSA mlDsa = new ML_DSA(name2int(pname));
            ML_DSA.ML_DSA_KeyPair kp = mlDsa.generateKeyPairInternal(seed);
            var expanded = mlDsa.skEncode(kp.privateKey());

            try {
                return new byte[][]{
                        mlDsa.pkEncode(kp.publicKey()),
                        KeyChoices.writeToChoice(
                                KeyChoices.getPreferred("mldsa"),
                                seed, expanded),
                        expanded
                };
            } finally {
                kp.privateKey().destroy();
                Arrays.fill(seed, (byte) 0);
            }
        }
    }

    public final static class KPG2 extends KPG {
        public KPG2() {
            super("ML-DSA-44");
        }
    }

    public final static class KPG3 extends KPG {
        public KPG3() {
            super("ML-DSA-65");
        }
    }

    public final static class KPG5 extends KPG {
        public KPG5() {
            super("ML-DSA-87");
        }
    }

    public sealed static class KF extends NamedKeyFactory permits KF2, KF3, KF5 {
        public KF() {
            super("ML-DSA", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");
        }
        public KF(String pname) {
            super("ML-DSA", pname);
        }

        @Override
        protected byte[] implExpand(String pname, byte[] input)
                throws InvalidKeyException {
            return KeyChoices.choiceToExpanded(pname, SEED_LEN, input,
                    ML_DSA_Impls::seedToExpanded);
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            var nk = toNamedKey(key);
            if (nk instanceof NamedPKCS8Key npk) {
                var type = KeyChoices.getPreferred("mldsa");
                if (KeyChoices.typeOfChoice(npk.getRawBytes()) != type) {
                    var encoding = KeyChoices.choiceToChoice(
                            type,
                            npk.getParams().getName(),
                            SEED_LEN, npk.getRawBytes(),
                            ML_DSA_Impls::seedToExpanded);
                    nk = NamedPKCS8Key.internalCreate(
                            npk.getAlgorithm(),
                            npk.getParams().getName(),
                            encoding,
                            npk.getExpanded().clone());
                    if (npk != key) { // npk is neither input or output
                        npk.destroy();
                    }
                }
            }
            return nk;
        }
    }

    public final static class KF2 extends KF {
        public KF2() {
            super("ML-DSA-44");
        }
    }

    public final static class KF3 extends KF {
        public KF3() {
            super("ML-DSA-65");
        }
    }

    public final static class KF5 extends KF {
        public KF5() {
            super("ML-DSA-87");
        }
    }

    public sealed static class SIG extends NamedSignature
            permits SIG2, SIG3, SIG5 {

        private boolean isInternal = false;
        private boolean isDeterministic = false;
        private boolean useExternalMu = false;

        public SIG() {
            super("ML-DSA", new KF());
        }

        public SIG(String pname) {
            super("ML-DSA", new KF(pname));
        }

        @Override
        protected void engineSetParameter(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            super.engineSetParameter(params);
            isInternal = isDeterministic = useExternalMu = false;
            for (var f : sps.features()) {
                switch (f) {
                    case "internal" -> isInternal = true;
                    case "deterministic" -> isDeterministic = true;
                    case "externalMu" -> useExternalMu = true;
                    default -> throw new InvalidAlgorithmParameterException(
                            "Unknown feature: " + f);
                }
            }
            if (!isInternal && useExternalMu) {
                throw new InvalidAlgorithmParameterException(
                        "externalMu requires internal");
            }
        }

        private byte[] m(byte[] msg) {
            if (isInternal) {
                return msg;
            }
            var ctxLen = sps.context() != null ? sps.context().length : 0;
            var oid = new byte[0];
            if (sps.preHash() != null) {
                oid = new DerOutputStream().putOID(ObjectIdentifier.of(
                        KnownOIDs.findMatch(sps.preHash()))).toByteArray();
            }
            var m = new byte[msg.length + 2 + ctxLen + oid.length];
            m[0] = sps.preHash() != null ? (byte)1 : (byte)0;
            m[1] = (byte) ctxLen;
            if (ctxLen > 0) {
                System.arraycopy(sps.context(), 0, m, 2, ctxLen);
            }
            if (oid.length > 0) {
                System.arraycopy(oid, 0, m, ctxLen + 2, oid.length);
            }
            System.arraycopy(msg, 0, m, ctxLen + 2 + oid.length, msg.length);
            return m;
        }

        @Override
        protected byte[] implSign(String pname, byte[] skBytes, Object sk2,
                byte[] msg, SecureRandom sr) throws SignatureException {
            byte[] rnd = new byte[32];
            if (!isDeterministic) {
                var r = sr != null ? sr : JCAUtil.getDefSecureRandom();
                r.nextBytes(rnd);
            }
            if (useExternalMu && msg.length != 64) {
                throw new SignatureException(
                        "input must be 64 bytes in externalMu mode");
            }
            var size = name2int(pname);
            var mlDsa = new ML_DSA(size);
            ML_DSA.ML_DSA_Signature sig = mlDsa.signInternal(
                    useExternalMu, m(msg), rnd, skBytes);
            return mlDsa.sigEncode(sig);
        }

        @Override
        protected boolean implVerify(String pname, byte[] pkBytes,
                Object pk2, byte[] msg, byte[] sigBytes)
                throws SignatureException {
            if (useExternalMu && msg.length != 64) {
                throw new SignatureException(
                        "input must be 64 bytes in externalMu mode");
            }
            var size = name2int(pname);
            var mlDsa = new ML_DSA(size);
            return mlDsa.verifyInternal(pkBytes, useExternalMu, m(msg), sigBytes);
        }

        @Override
        protected Object implCheckPublicKey(String pname, byte[] pk)
            throws InvalidKeyException {

            ML_DSA mlDsa = new ML_DSA(name2int(pname));
            return mlDsa.checkPublicKey(pk);
        }

        @Override
        protected Object implCheckPrivateKey(String pname, byte[] sk)
            throws InvalidKeyException {

            ML_DSA mlDsa = new ML_DSA(name2int(pname));
            return mlDsa.checkPrivateKey(sk);
        }
    }

    public final static class SIG2 extends SIG {
        public SIG2() {
            super("ML-DSA-44");
        }
    }

    public final static class SIG3 extends SIG {
        public SIG3() {
            super("ML-DSA-65");
        }
    }

    public final static class SIG5 extends SIG {
        public SIG5() {
            super("ML-DSA-87");
        }
    }
}
