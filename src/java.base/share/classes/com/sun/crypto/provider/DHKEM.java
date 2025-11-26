/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Serial;
import java.math.BigInteger;
import java.security.AsymmetricKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.DecapsulateException;
import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sun.security.jca.JCAUtil;
import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.InternalPrivateKey;
import sun.security.util.NamedCurve;
import sun.security.util.SliceableSecretKey;

// Implementing DHKEM defined inside https://www.rfc-editor.org/rfc/rfc9180.html,
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
                           PrivateKey skS, PublicKey pkS, // sender keys
                           PrivateKey skR, PublicKey pkR) // receiver keys
                implements EncapsulatorSpi, DecapsulatorSpi {

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            Objects.checkFromToIndex(from, to, params.nsecret);
            Objects.requireNonNull(algorithm, "null algorithm");
            KeyPair kpE = params.generateKeyPair(secureRandom);
            PrivateKey skE = kpE.getPrivate();
            PublicKey pkE = kpE.getPublic();
            byte[] pkEm = params.serializePublicKey(pkE);
            byte[] pkRm = params.serializePublicKey(pkR);
            try {
                SecretKey key;
                if (skS == null) {
                    byte[] kem_context = concat(pkEm, pkRm);
                    key = params.deriveKey(algorithm, from, to, kem_context,
                            params.dh(skE, pkR));
                } else {
                    byte[] pkSm = params.serializePublicKey(pkS);
                    byte[] kem_context = concat(pkEm, pkRm, pkSm);
                    key = params.deriveKey(algorithm, from, to, kem_context,
                            params.dh(skE, pkR), params.dh(skS, pkR));
                }
                return new KEM.Encapsulated(key, pkEm, null);
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new ProviderException("internal error", e);
            }
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation,
                int from, int to, String algorithm) throws DecapsulateException {
            Objects.checkFromToIndex(from, to, params.nsecret);
            Objects.requireNonNull(algorithm, "null algorithm");
            Objects.requireNonNull(encapsulation, "null encapsulation");
            if (encapsulation.length != params.npk) {
                throw new DecapsulateException("incorrect encapsulation size");
            }
            try {
                PublicKey pkE = params.deserializePublicKey(encapsulation);
                byte[] pkRm = params.serializePublicKey(pkR);
                if (pkS == null) {
                    byte[] kem_context = concat(encapsulation, pkRm);
                    return params.deriveKey(algorithm, from, to, kem_context,
                            params.dh(skR, pkE));
                } else {
                    byte[] pkSm = params.serializePublicKey(pkS);
                    byte[] kem_context = concat(encapsulation, pkRm, pkSm);
                    return params.deriveKey(algorithm, from, to, kem_context,
                            params.dh(skR, pkE), params.dh(skR, pkS));
                }
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (IOException | InvalidKeyException e) {
                throw new DecapsulateException("Cannot decapsulate", e);
            } catch (Exception e) {
                throw new ProviderException("internal error", e);
            }
        }

        @Override
        public int engineSecretSize() {
            return params.nsecret;
        }

        @Override
        public int engineEncapsulationSize() {
            return params.npk;
        }
    }

    // Not really a random. For KAT test only. It generates key pair from ikm.
    public static class RFC9180DeriveKeyPairSR extends SecureRandom {

        @Serial
        private static final long serialVersionUID = 0L;

        private final byte[] ikm;

        public RFC9180DeriveKeyPairSR(byte[] ikm) {
            super(null, null); // lightest constructor
            this.ikm = ikm;
        }

        private KeyPair derive(Params params) {
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
                "ECDH", "EC", CurveDB.P_256, "HKDF-SHA256"),

        P384(0x11, 48, 48, 2 * 48 + 1,
                "ECDH", "EC", CurveDB.P_384, "HKDF-SHA384"),

        P521(0x12, 64, 66, 2 * 66 + 1,
                "ECDH", "EC", CurveDB.P_521, "HKDF-SHA512"),

        X25519(0x20, 32, 32, 32,
                "XDH", "XDH", NamedParameterSpec.X25519, "HKDF-SHA256"),

        X448(0x21, 64, 56, 56,
                "XDH", "XDH", NamedParameterSpec.X448, "HKDF-SHA512"),
        ;

        private final int kem_id;
        private final int nsecret;
        private final int nsk;
        private final int npk;
        private final String kaAlgorithm;
        private final String keyAlgorithm;
        private final AlgorithmParameterSpec spec;
        private final String hkdfAlgorithm;

        private final byte[] suiteId;

        Params(int kem_id, int nsecret, int nsk, int npk,
                String kaAlgorithm, String keyAlgorithm, AlgorithmParameterSpec spec,
                String hkdfAlgorithm) {
            this.kem_id = kem_id;
            this.spec = spec;
            this.nsecret = nsecret;
            this.nsk = nsk;
            this.npk = npk;
            this.kaAlgorithm = kaAlgorithm;
            this.keyAlgorithm = keyAlgorithm;
            this.hkdfAlgorithm = hkdfAlgorithm;
            suiteId = concat(KEM, i2OSP(kem_id, 2));
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

        private byte[] serializePublicKey(PublicKey k) {
            if (isEC()) {
                ECPoint w = ((ECPublicKey) k).getW();
                return ECUtil.encodePoint(w, ((NamedCurve) spec).getCurve());
            } else {
                byte[] uArray = ((XECPublicKey) k).getU().toByteArray();
                ArrayUtil.reverse(uArray);
                return Arrays.copyOf(uArray, npk);
            }
        }

        private PublicKey deserializePublicKey(byte[] data)
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

        private SecretKey dh(PrivateKey skE, PublicKey pkR)
                throws NoSuchAlgorithmException, InvalidKeyException {
            KeyAgreement ka = KeyAgreement.getInstance(kaAlgorithm);
            ka.init(skE);
            ka.doPhase(pkR, true);
            return ka.generateSecret("Generic");
        }

        // The final shared secret derivation of either the encapsulator
        // or the decapsulator. The key slicing is implemented inside.
        // Throws UOE if a slice of the key cannot be found.
        private SecretKey deriveKey(String alg, int from, int to,
                byte[] kem_context, SecretKey... dhs)
                throws NoSuchAlgorithmException {
            if (from == 0 && to == nsecret) {
                return extractAndExpand(kem_context, alg, dhs);
            } else {
                // First get shared secrets in "Generic" and then get a slice
                // of it in the requested algorithm.
                var fullKey = extractAndExpand(kem_context, "Generic", dhs);
                if ("RAW".equalsIgnoreCase(fullKey.getFormat())) {
                    byte[] km = fullKey.getEncoded();
                    if (km == null) {
                        // Should not happen if format is "RAW"
                        throw new UnsupportedOperationException("Key extract failed");
                    } else {
                        try {
                            return new SecretKeySpec(km, from, to - from, alg);
                        } finally {
                            Arrays.fill(km, (byte)0);
                        }
                    }
                } else if (fullKey instanceof SliceableSecretKey ssk) {
                    return ssk.slice(alg, from, to);
                } else {
                    throw new UnsupportedOperationException("Cannot extract key");
                }
            }
        }

        private SecretKey extractAndExpand(byte[] kem_context, String alg, SecretKey... dhs)
                throws NoSuchAlgorithmException {
            var kdf = KDF.getInstance(hkdfAlgorithm);
            var builder = labeledExtract(suiteId, EAE_PRK);
            for (var dh : dhs) builder.addIKM(dh);
            try {
                return kdf.deriveKey(alg,
                        labeledExpand(builder, suiteId, SHARED_SECRET, kem_context, nsecret));
            } catch (InvalidAlgorithmParameterException e) {
                throw new ProviderException(e);
            }
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
            var kdf = KDF.getInstance(hkdfAlgorithm);
            var builder = labeledExtract(suiteId, DKP_PRK).addIKM(ikm);
            if (isEC()) {
                NamedCurve curve = (NamedCurve) spec;
                BigInteger sk = BigInteger.ZERO;
                int counter = 0;
                while (sk.signum() == 0 || sk.compareTo(curve.getOrder()) >= 0) {
                    if (counter > 255) {
                        // So unlucky and should not happen
                        throw new ProviderException("DeriveKeyPairError");
                    }
                    byte[] bytes = kdf.deriveData(labeledExpand(builder,
                            suiteId, CANDIDATE, i2OSP(counter, 1), nsk));
                    // bitmask is defined to be 0xFF for P-256 and P-384, and 0x01 for P-521
                    if (this == Params.P521) {
                        bytes[0] = (byte) (bytes[0] & 0x01);
                    }
                    sk = new BigInteger(1, (bytes));
                    counter = counter + 1;
                }
                PrivateKey k = deserializePrivateKey(sk.toByteArray());
                return new KeyPair(getPublicKey(k), k);
            } else {
                byte[] sk = kdf.deriveData(labeledExpand(builder,
                        suiteId, SK, EMPTY, nsk));
                PrivateKey k = deserializePrivateKey(sk);
                return new KeyPair(getPublicKey(k), k);
            }
        }

        private PrivateKey deserializePrivateKey(byte[] data) throws Exception {
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
        return new Handler(params, getSecureRandom(secureRandom), null, null, null, pk);
    }

    // AuthEncap is not public KEM API
    public EncapsulatorSpi engineNewAuthEncapsulator(PublicKey pkR, PrivateKey skS,
            AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (pkR == null || skS == null) {
            throw new InvalidKeyException("input key is null");
        }
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(pkR);
        return new Handler(params, getSecureRandom(secureRandom),
                skS, params.getPublicKey(skS), null, pkR);
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
        return new Handler(params, null, null, null, sk, params.getPublicKey(sk));
    }

    // AuthDecap is not public KEM API
    public DecapsulatorSpi engineNewAuthDecapsulator(
            PrivateKey skR, PublicKey pkS, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (skR == null || pkS == null) {
            throw new InvalidKeyException("input key is null");
        }
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("no spec needed");
        }
        Params params = paramsFromKey(skR);
        return new Handler(params, null, null, pkS, skR, params.getPublicKey(skR));
    }

    private Params paramsFromKey(AsymmetricKey k) throws InvalidKeyException {
        var p = k.getParams();
        if (p instanceof ECParameterSpec ecp) {
            if (ECUtil.equals(ecp, CurveDB.P_256)) {
                return Params.P256;
            } else if (ECUtil.equals(ecp, CurveDB.P_384)) {
                return Params.P384;
            } else if (ECUtil.equals(ecp, CurveDB.P_521)) {
                return Params.P521;
            }
        } else if (p instanceof NamedParameterSpec ns) {
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

    // I2OSP(n, w) as defined in RFC 9180 Section 3.
    // In DHKEM and HPKE, number is always <65536
    // and converted to at most 2 bytes.
    public static byte[] i2OSP(int n, int w) {
        assert n < 65536;
        assert w == 1 || w == 2;
        if (w == 1) {
            return new byte[] { (byte) n };
        } else {
            return new byte[] { (byte) (n >> 8), (byte) n };
        }
    }

    // Create a LabeledExtract builder with labels.
    // You can add more IKM and salt into the result.
    public static HKDFParameterSpec.Builder labeledExtract(
            byte[] suiteId, byte[] label) {
        return HKDFParameterSpec.ofExtract()
                .addIKM(HPKE_V1).addIKM(suiteId).addIKM(label);
    }

    // Create a labeled info from info and labels
    private static byte[] labeledInfo(
            byte[] suiteId, byte[] label, byte[] info, int length) {
        return concat(i2OSP(length, 2), HPKE_V1, suiteId, label, info);
    }

    // LabeledExpand from a builder
    public static HKDFParameterSpec labeledExpand(
            HKDFParameterSpec.Builder builder,
            byte[] suiteId, byte[] label, byte[] info, int length) {
        return builder.thenExpand(
                labeledInfo(suiteId, label, info, length), length);
    }

    // LabeledExpand from a prk
    public static HKDFParameterSpec labeledExpand(
            SecretKey prk, byte[] suiteId, byte[] label, byte[] info, int length) {
        return HKDFParameterSpec.expandOnly(
                prk, labeledInfo(suiteId, label, info, length), length);
    }
}
