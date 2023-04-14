/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.crypto.provider;

import sun.security.jca.JCAUtil;
import sun.security.ssl.HKDF;
import sun.security.util.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;

// Implementing DHKEM defined inside https://www.rfc-editor.org/rfc/rfc9180.html,
// without the AuthEncap and AuthDecap functions
public class DHKEM implements KEMSpi {

    private record Handler(Params params, SecureRandom secureRandom,
                           PrivateKey skR, PublicKey pkR)
                implements EncapsulatorSpi, DecapsulatorSpi {

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            KeyPair kpE = params.generateKeyPair(secureRandom);
            PrivateKey skE = kpE.getPrivate();
            PublicKey pkE = kpE.getPublic();
            byte[] pkEm = params.SerializePublicKey(pkE);
            byte[] pkRm = params.SerializePublicKey(pkR);
            byte[] kem_context = concat(pkEm, pkRm);
            try {
                byte[] dh = params.DH(skE, pkR);
                byte[] key = params.ExtractAndExpand(dh, kem_context);
                return new KEM.Encapsulated(
                        new SecretKeySpec(key, from, to - from, algorithm),
                        pkEm, null);
            } catch (Exception e) {
                throw new ProviderException(e);
            }
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation,
                int from, int to, String algorithm) throws DecapsulateException {
            try {
                PublicKey pkE = params.DeserializePublicKey(encapsulation);
                byte[] dh = params.DH(skR, pkE);
                byte[] pkRm = params.SerializePublicKey(pkR);
                byte[] kem_context = concat(encapsulation, pkRm);
                byte[] key = params.ExtractAndExpand(dh, kem_context);
                return new SecretKeySpec(key, from, to, algorithm);
            } catch (IOException | InvalidKeyException e) {
                throw new DecapsulateException("Cannot decapsulate", e);
            } catch (Exception e) {
                throw new ProviderException();
            }
        }

        @Override
        public int engineSecretSize() {
            return params.Nsecret;
        }

        @Override
        public int engineEncapsulationSize() {
            return params.Npk;
        }
    }

    // Not really a random. For KAT test only. It generates key pair from ikm.
    public static class RFC9180DeriveKeyPairSR extends SecureRandom {

        static final long serialVersionUID = 0L;

        private final byte[] ikm;

        public RFC9180DeriveKeyPairSR(byte[] ikm) {
            super(null, null); // lightest constructor
            this.ikm = ikm;
        }

        public KeyPair derive(Params params) {
            try {
                return params.deriveKeyPair(ikm);
            } catch (Exception e) {
                throw new UnsupportedOperationException(e);
            }
        }

        public KeyPair derive(int kem_id) {
            Params params = Arrays.stream(Params.values())
                    .filter(p -> p.kem_id == kem_id)
                    .findFirst()
                    .orElseThrow();
            return derive(params);
        }
    }

    private enum Params {

        P256(0x10, 32, 32, 2 * 32 + 1,
                "ECDH", "EC", CurveDB.lookup("secp256r1"), "SHA-256"),

        P384(0x11, 48, 48, 2 * 48 + 1,
                "ECDH", "EC", CurveDB.lookup("secp384r1"), "SHA-384"),

        P521(0x12, 64, 66, 2 * 66 + 1,
                "ECDH", "EC", CurveDB.lookup("secp521r1"), "SHA-512"),

        X25519(0x20, 32, 32, 32,
                "XDH", "XDH", NamedParameterSpec.X25519, "SHA-256"),

        X448(0x21, 64, 56, 56,
                "XDH", "XDH", NamedParameterSpec.X448, "SHA-512"),
        ;

        private final int kem_id;
        private final int Nsecret;
        private final int Nsk;
        private final int Npk;
        private final String kaAlgorithm;
        private final String keyAlgorithm;
        private final AlgorithmParameterSpec spec;
        private final String hkdfAlgorithm;

        private final byte[] suiteId;

        Params(int kem_id, int Nsecret, int Nsk, int Npk,
                String kaAlgorithm, String keyAlgorithm, AlgorithmParameterSpec spec,
                String hkdfAlgorithm) {
            this.kem_id = kem_id;
            this.spec = spec;
            this.Nsecret = Nsecret;
            this.Nsk = Nsk;
            this.Npk = Npk;
            this.kaAlgorithm = kaAlgorithm;
            this.keyAlgorithm = keyAlgorithm;
            this.hkdfAlgorithm = hkdfAlgorithm;
            suiteId = concat("KEM".getBytes(StandardCharsets.UTF_8),
                    I2OSP(kem_id, 2));
        }

        private boolean isEC() {
            return this == P256 || this == P384 || this == P521;
        }

        private KeyPair generateKeyPair(SecureRandom sr) {
            if (sr instanceof RFC9180DeriveKeyPairSR r9) {
                return r9.derive(this);
            }
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance(keyAlgorithm);
                g.initialize(spec, sr);
                return g.generateKeyPair();
            } catch (Exception e) {
                throw new ProviderException(e);
            }
        }

        private byte[] SerializePublicKey(PublicKey k) {
            if (isEC()) {
                ECPoint w = ((ECPublicKey) k).getW();
                return ECUtil.encodePoint(w, ((NamedCurve) spec).getCurve());
            } else {
                byte[] uArray = ((XECPublicKey) k).getU().toByteArray();
                return Arrays.copyOf(reverse(uArray), Npk);
            }
        }

        private static byte[] reverse(byte[] arr) {
            int len = arr.length;
            byte[] result = new byte[len];
            for (int i = 0; i < len; i++) {
                result[i] = arr[len - 1 - i];
            }
            return result;
        }

        private PublicKey DeserializePublicKey(byte[] data)
                throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
            KeySpec keySpec;
            if (isEC()) {
                NamedCurve curve = (NamedCurve) this.spec;
                keySpec = new ECPublicKeySpec(
                        ECUtil.decodePoint(data, curve.getCurve()), curve);
            } else {
                keySpec = new XECPublicKeySpec(
                        this.spec, new BigInteger(1, reverse(data)));
            }
            return KeyFactory.getInstance(keyAlgorithm).generatePublic(keySpec);
        }

        private byte[] DH(PrivateKey skE, PublicKey pkR)
                throws NoSuchAlgorithmException, InvalidKeyException {
            KeyAgreement ka = KeyAgreement.getInstance(kaAlgorithm);
            ka.init(skE);
            ka.doPhase(pkR, true);
            return ka.generateSecret();
        }

        private byte[] ExtractAndExpand(byte[] dh, byte[] kem_context)
                throws NoSuchAlgorithmException, InvalidKeyException {
            HKDF kdf = new HKDF(hkdfAlgorithm);
            SecretKey eae_prk = LabeledExtract(kdf, suiteId, null,
                    "eae_prk".getBytes(StandardCharsets.UTF_8), dh);
            return LabeledExpand(kdf, suiteId, eae_prk,
                    "shared_secret".getBytes(StandardCharsets.UTF_8),
                    kem_context, Nsecret);
        }

        private PublicKey getPublicKey(PrivateKey sk)
                throws InvalidKeyException {
            if (!(sk instanceof InternalPrivateKey)) {
                try {
                    KeyFactory kf = KeyFactory.getInstance(keyAlgorithm, "SunEC");
                    sk = (PrivateKey) kf.translateKey(sk);
                } catch (Exception e) {
                    throw new InvalidKeyException("Error translating key", e);
                }
            }
            if (sk instanceof InternalPrivateKey ik) {
                try {
                    return ik.calculatePublicKey();
                } catch (UnsupportedOperationException e) {
                    throw new InvalidKeyException("Error retrieving key", e);
                }
            } else {
                // Should not happen, unless SunEC goes wrong
                throw new ProviderException("Unknown key");
            }
        }

        // For KAT tests only. See RFC9180DeriveKeyPairSR.
        public KeyPair deriveKeyPair(byte[] ikm) throws Exception {
            HKDF kdf = new HKDF(hkdfAlgorithm);
            SecretKey dkp_prk = LabeledExtract(kdf, suiteId, null,
                    "dkp_prk".getBytes(StandardCharsets.UTF_8), ikm);
            if (isEC()) {
                NamedCurve curve = (NamedCurve) spec;
                BigInteger sk = BigInteger.ZERO;
                int counter = 0;
                while (sk.signum() == 0 || sk.compareTo(curve.getOrder()) >= 0) {
                    if (counter > 255) {
                        throw new RuntimeException();
                    }
                    byte[] bytes = LabeledExpand(kdf, suiteId, dkp_prk,
                            "candidate".getBytes(StandardCharsets.UTF_8),
                            I2OSP(counter, 1), Nsk);
                    // bitmask is defined to be 0xFF for P-256 and P-384, and 0x01 for P-521
                    if (this == Params.P521) {
                        bytes[0] = (byte) (bytes[0] & 0x01);
                    }
                    sk = new BigInteger(1, (bytes));
                    counter = counter + 1;
                }
                PrivateKey k = DeserializePrivateKey(
                        Params.reverse(ECUtil.sArray(sk, curve)));
                return new KeyPair(getPublicKey(k), k);
            } else {
                byte[] sk = LabeledExpand(kdf, suiteId, dkp_prk,
                        "sk".getBytes(StandardCharsets.UTF_8),
                        "".getBytes(StandardCharsets.UTF_8),
                        Nsk);
                PrivateKey k = DeserializePrivateKey(sk);
                return new KeyPair(getPublicKey(k), k);
            }
        }

        private PrivateKey DeserializePrivateKey(byte[] data) throws Exception {
            KeySpec keySpec = isEC()
                    ? new ECPrivateKeySpec(new BigInteger(1, (data)), (NamedCurve) spec)
                    : new XECPrivateKeySpec(spec, data);
            return KeyFactory.getInstance(keyAlgorithm).generatePrivate(keySpec);
        }
    }

    private static SecureRandom getSecureRandom(SecureRandom userSR) {
        return userSR != null ? userSR : JCAUtil.getSecureRandom();
    }

    @Override
    public EncapsulatorSpi engineNewEncapsulator(
            PublicKey pk, AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(pk);
        return new Handler(params, getSecureRandom(secureRandom), null, pk);
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(PrivateKey sk, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(sk);
        return new Handler(params, null, sk, params.getPublicKey(sk));
    }

    private Params paramsFromKey(Key k) throws InvalidKeyException {
        if (k instanceof ECKey eckey) {
            if (ECUtil.equals(eckey.getParams(), CurveDB.lookup("secp256r1"))) {
                return Params.P256;
            } else if (ECUtil.equals(eckey.getParams(), CurveDB.lookup("secp384r1"))) {
                return Params.P384;
            } else if (ECUtil.equals(eckey.getParams(), CurveDB.lookup("secp521r1"))) {
                return Params.P521;
            }
        } else if (k instanceof XECKey xkey
                && xkey.getParams() instanceof NamedParameterSpec ns) {
            if (ns.getName().equals("X25519")) {
                return Params.X25519;
            } else if (ns.getName().equals("X448")) {
                return Params.X448;
            }
        }
        throw new InvalidKeyException("Unsupported key");
    }

    private static byte[] concat(byte[]... inputs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        Arrays.stream(inputs).forEach(o::writeBytes);
        return o.toByteArray();
    }

    private static byte[] I2OSP(int n, int w) {
        assert n < 256;
        assert w == 1 || w == 2;
        if (w == 1) {
            return new byte[] { (byte) n };
        } else {
            return new byte[] { (byte) (n >> 8), (byte) n };
        }
    }

    private static SecretKey LabeledExtract(HKDF kdf, byte[] suite_id,
            byte[] salt, byte[] label, byte[] ikm) throws InvalidKeyException {
        return kdf.extract(salt,
                new SecretKeySpec(
                        concat("HPKE-v1".getBytes(StandardCharsets.UTF_8), suite_id, label, ikm),
                        "IKM"),
                    "HKDF-PRK");
    }

    private static byte[] LabeledExpand(HKDF kdf, byte[] suite_id,
            SecretKey prk, byte[] label, byte[] info, int L)
            throws InvalidKeyException {
        byte[] labeled_info = concat(I2OSP(L, 2), "HPKE-v1".getBytes(StandardCharsets.UTF_8),
                suite_id, label, info);
        return kdf.expand(prk, labeled_info, L, "NONE").getEncoded();
    }
}
