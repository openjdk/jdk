/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.NamedCurve;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

/**
 * The DHasKEM class presents a KEM abstraction layer over traditional
 * DH-based key exchange, which can be used for either straight
 * ECDH/XDH or TLS hybrid key exchanges.
 *
 * This class can be alongside standard full post-quantum KEMs
 * when hybrid implementations are required.
 */
public class DHasKEM implements KEMSpi {

    @Override
    public EncapsulatorSpi engineNewEncapsulator(
            PublicKey publicKey, AlgorithmParameterSpec spec,
            SecureRandom secureRandom) throws InvalidKeyException {
        return new Handler(publicKey, null, secureRandom);
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey,
            AlgorithmParameterSpec spec) throws InvalidKeyException {
        return new Handler(null, privateKey, null);
    }

    private static final class Handler
            implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {
        private final PublicKey pkR;
        private final PrivateKey skR;
        private final SecureRandom sr;
        private final Params params;

        Handler(PublicKey pk, PrivateKey sk, SecureRandom sr)
                throws InvalidKeyException {
            this.pkR = pk;
            this.skR = sk;
            this.sr = sr;
            this.params = paramsFromKey(pk == null ? sk : pk);
        }

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to,
                String algorithm) {
            KeyPair kpE = params.generateKeyPair(sr);
            PrivateKey skE = kpE.getPrivate();
            PublicKey pkE = kpE.getPublic();
            byte[] pkEm = params.SerializePublicKey(pkE);
            try {
                SecretKey dh = params.DH(algorithm, skE, pkR);
                return new KEM.Encapsulated(
                        sub(dh, from, to),
                        pkEm, null);
            } catch (Exception e) {
                throw new ProviderException("internal error", e);
            }
        }

        @Override
        public int engineSecretSize() {
            return params.secretLen;
        }

        @Override
        public int engineEncapsulationSize() {
            return params.publicKeyLen;
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from,
                int to, String algorithm) throws DecapsulateException {
            if (encapsulation.length != params.publicKeyLen) {
                throw new DecapsulateException("incorrect encapsulation size");
            }
            try {
                PublicKey pkE = params.DeserializePublicKey(encapsulation);
                SecretKey dh = params.DH(algorithm, skR, pkE);
                return sub(dh, from, to);
            } catch (IOException | InvalidKeyException e) {
                throw new DecapsulateException("Cannot decapsulate", e);
            } catch (Exception e) {
                throw new ProviderException("internal error", e);
            }
        }

        private SecretKey sub(SecretKey key, int from, int to) {
            if (from == 0 && to == params.secretLen) {
                return key;
            }

            // Key slicing should never happen. Otherwise, there might be
            // a programming error.
            throw new AssertionError(
                    "Unexpected key slicing: from=" + from + ", to=" + to);
        }

        // This KEM is designed to be able to represent every ECDH and XDH
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
                if (ns.getName().equalsIgnoreCase(
                        NamedParameterSpec.X25519.getName())) {
                    return Params.X25519;
                } else if (ns.getName().equalsIgnoreCase(
                        NamedParameterSpec.X448.getName())) {
                    return Params.X448;
                }
            }
            throw new InvalidKeyException("Unsupported key");
        }
    }

    private enum Params {

        P256(32, 2 * 32 + 1,
                "ECDH", "EC", CurveDB.P_256),

        P384(48, 2 * 48 + 1,
                "ECDH", "EC", CurveDB.P_384),

        P521(66, 2 * 66 + 1,
                "ECDH", "EC", CurveDB.P_521),

        X25519(32, 32,
                "XDH", "XDH", NamedParameterSpec.X25519),

        X448(56, 56,
                "XDH", "XDH", NamedParameterSpec.X448);

        private final int secretLen;
        private final int publicKeyLen;
        private final String kaAlgorithm;
        private final String keyAlgorithm;
        private final AlgorithmParameterSpec spec;

        Params(int secretLen, int publicKeyLen, String kaAlgorithm,
                String keyAlgorithm, AlgorithmParameterSpec spec) {
            this.spec = spec;
            this.secretLen = secretLen;
            this.publicKeyLen = publicKeyLen;
            this.kaAlgorithm = kaAlgorithm;
            this.keyAlgorithm = keyAlgorithm;
        }

        private boolean isEC() {
            return this == P256 || this == P384 || this == P521;
        }

        private KeyPair generateKeyPair(SecureRandom sr) {
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
                return Arrays.copyOf(uArray, publicKeyLen);
            }
        }

        private PublicKey DeserializePublicKey(byte[] data) throws
                IOException, NoSuchAlgorithmException,
                InvalidKeySpecException {
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
            return KeyFactory.getInstance(keyAlgorithm).
                    generatePublic(keySpec);
        }

        private SecretKey DH(String alg, PrivateKey skE, PublicKey pkR)
                throws NoSuchAlgorithmException, InvalidKeyException {
            KeyAgreement ka = KeyAgreement.getInstance(kaAlgorithm);
            ka.init(skE);
            ka.doPhase(pkR, true);
            return ka.generateSecret(alg);
        }
    }
}
