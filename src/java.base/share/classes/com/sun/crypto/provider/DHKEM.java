/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import sun.security.jca.JCAUtil;
import sun.security.ssl.HKDF;
import sun.security.util.*;

// Implementing DHKEM defined inside https://www.rfc-editor.org/rfc/rfc9180.html,
// without the AuthEncap and AuthDecap functions
public class DHKEM implements KEMSpi {

    private static final byte[] KEM = new byte[]
            {'K', 'E', 'M'};
    private static final byte[] EAE_PRK = new byte[]
            {'e', 'a', 'e', '_', 'p', 'r', 'k'};
    private static final byte[] SHARED_SECRET = new byte[]
            {'s', 'h', 'a', 'r', 'e', 'd', '_', 's', 'e', 'c', 'r', 'e', 't'};
    private static final byte[] DKP_PRK = new byte[]
            {'d', 'k', 'p', '_', 'p', 'r', 'k'};
    private static final byte[] CANDIDATE = new byte[]
            {'c', 'a', 'n', 'd', 'i', 'd', 'a', 't', 'e'};
    private static final byte[] SK = new byte[]
            {'s', 'k'};
    private static final byte[] HPKE_V1 = new byte[]
            {'H', 'P', 'K', 'E', '-', 'v', '1'};
    private static final byte[] EMPTY = new byte[0];

    private record Handler(Params params, SecureRandom secureRandom,
                           PrivateKey skR, PublicKey pkR)
                implements EncapsulatorSpi, DecapsulatorSpi {

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            Objects.checkFromToIndex(from, to, params.Nsecret);
            Objects.requireNonNull(algorithm, "null algorithm");
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
                throw new ProviderException("internal error", e);
            }
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation,
                int from, int to, String algorithm) throws DecapsulateException {
            Objects.checkFromToIndex(from, to, params.Nsecret);
            Objects.requireNonNull(algorithm, "null algorithm");
            Objects.requireNonNull(encapsulation, "null encapsulation");
            if (encapsulation.length != params.Npk) {
                throw new DecapsulateException("incorrect encapsulation size");
            }
            try {
                PublicKey pkE = params.DeserializePublicKey(encapsulation);
                byte[] dh = params.DH(skR, pkE);
                byte[] pkRm = params.SerializePublicKey(pkR);
                byte[] kem_context = concat(encapsulation, pkRm);
                byte[] key = params.ExtractAndExpand(dh, kem_context);
                return new SecretKeySpec(key, from, to - from, algorithm);
            } catch (IOException | InvalidKeyException e) {
                throw new DecapsulateException("Cannot decapsulate", e);
            } catch (Exception e) {
                throw new ProviderException("internal error", e);
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
                "ECDH", "EC", CurveDB.P_256, "SHA-256"),

        P384(0x11, 48, 48, 2 * 48 + 1,
                "ECDH", "EC", CurveDB.P_384, "SHA-384"),

        P521(0x12, 64, 66, 2 * 66 + 1,
                "ECDH", "EC", CurveDB.P_521, "SHA-512"),

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
            suiteId = concat(KEM, I2OSP(kem_id, 2));
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
                throw new ProviderException("internal error", e);
            }
        }

        private byte[] SerializePublicKey(PublicKey k) {
            if (isEC()) {
                ECPoint w = ((ECPublicKey) k).getW();
                return ECUtil.encodePoint(w, ((NamedCurve) spec).getCurve());
            } else {
                byte[] uArray = ((XECPublicKey) k).getU().toByteArray();
                ArrayUtil.reverse(uArray);
                return Arrays.copyOf(uArray, Npk);
            }
        }

        private PublicKey DeserializePublicKey(byte[] data)
                throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
            KeySpec keySpec;
            if (isEC()) {
                NamedCurve curve = (NamedCurve) this.spec;
                keySpec = new ECPublicKeySpec(
                        ECUtil.decodePoint(data, curve.getCurve()), curve);
            } else {
                data = data.clone();
                ArrayUtil.reverse(data);
                keySpec = new XECPublicKeySpec(
                        this.spec, new BigInteger(1, data));
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
            SecretKey eae_prk = LabeledExtract(kdf, suiteId, null, EAE_PRK, dh);
            return LabeledExpand(kdf, suiteId, eae_prk, SHARED_SECRET,
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
            SecretKey dkp_prk = LabeledExtract(kdf, suiteId, null, DKP_PRK, ikm);
            if (isEC()) {
                NamedCurve curve = (NamedCurve) spec;
                BigInteger sk = BigInteger.ZERO;
                int counter = 0;
                while (sk.signum() == 0 || sk.compareTo(curve.getOrder()) >= 0) {
                    if (counter > 255) {
                        throw new RuntimeException();
                    }
                    byte[] bytes = LabeledExpand(kdf, suiteId, dkp_prk,
                            CANDIDATE, I2OSP(counter, 1), Nsk);
                    // bitmask is defined to be 0xFF for P-256 and P-384, and 0x01 for P-521
                    if (this == Params.P521) {
                        bytes[0] = (byte) (bytes[0] & 0x01);
                    }
                    sk = new BigInteger(1, (bytes));
                    counter = counter + 1;
                }
                PrivateKey k = DeserializePrivateKey(sk.toByteArray());
                return new KeyPair(getPublicKey(k), k);
            } else {
                byte[] sk = LabeledExpand(kdf, suiteId, dkp_prk, SK, EMPTY, Nsk);
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
        if (pk == null) {
            throw new InvalidKeyException("input key is null");
        }
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(pk);
        return new Handler(params, getSecureRandom(secureRandom), null, pk);
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(PrivateKey sk, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (sk == null) {
            throw new InvalidKeyException("input key is null");
        }
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(sk);
        return new Handler(params, null, sk, params.getPublicKey(sk));
    }

    private Params paramsFromKey(Key k) throws InvalidKeyException {
        if (k instanceof ECKey eckey) {
            if (ECUtil.equals(eckey.getParams(), CurveDB.P_256)) {
                return Params.P256;
            } else if (ECUtil.equals(eckey.getParams(), CurveDB.P_384)) {
                return Params.P384;
            } else if (ECUtil.equals(eckey.getParams(), CurveDB.P_521)) {
                return Params.P521;
            }
        } else if (k instanceof XECKey xkey
                && xkey.getParams() instanceof NamedParameterSpec ns) {
            if (ns.getName().equalsIgnoreCase("X25519")) {
                return Params.X25519;
            } else if (ns.getName().equalsIgnoreCase("X448")) {
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
                new SecretKeySpec(concat(HPKE_V1, suite_id, label, ikm), "IKM"),
                    "HKDF-PRK");
    }

    private static byte[] LabeledExpand(HKDF kdf, byte[] suite_id,
            SecretKey prk, byte[] label, byte[] info, int L)
            throws InvalidKeyException {
        byte[] labeled_info = concat(I2OSP(L, 2), HPKE_V1,
                suite_id, label, info);
        return kdf.expand(prk, labeled_info, L, "NONE").getEncoded();
    }
}
