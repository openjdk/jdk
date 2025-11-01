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

package com.sun.crypto.provider;

import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.Hybrid;
import sun.security.util.NamedCurve;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.List;
import java.util.Map;

import static sun.security.util.SecurityConstants.PROVIDER_VER;

/**
 * The DH provider is a KEM abstraction layer over traditional DH based
 * key exchange. It models DH/ECDH/XDH as KEMs, like post-quantum algorithms,
 * so DH/ECDH/XDH can be used in hybrid key exchange, alongside post-quantum
 * KEMs.
 */
public class DH implements KEMSpi {

    // DH in its own private provider so we always getInstance from here.
    public static final Provider PROVIDER = new ProviderImpl();

    private static class ProviderImpl extends Provider {
        @java.io.Serial
        private static final long serialVersionUID = 0L;
        private ProviderImpl() {
            super("InternalJCE", PROVIDER_VER, "");
            put("KEM.DH", DH.class.getName());

            // Hybrid KeyPairGenerator/KeyFactory/KEM

            // The order of shares in the concatenation for group name
            // X25519MLKEM768 has been reversed. This is due to historical
            // reasons.
            var attrs = Map.of("name", "X25519MLKEM768", "left", "ML-KEM-768",
                    "right", "X25519");
            putService(new HybridService(this, "KeyPairGenerator",
                    "X25519MLKEM768", "sun.security.util.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "X25519MLKEM768", "sun.security.util.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "X25519MLKEM768", "sun.security.util.Hybrid$KeyFactoryImpl",
                    null, attrs));

            attrs = Map.of("name", "SecP256r1MLKEM768", "left", "secp256r1",
                    "right", "ML-KEM-768");
            putService(new HybridService(this, "KeyPairGenerator",
                    "SecP256r1MLKEM768", "sun.security.util.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "SecP256r1MLKEM768", "sun.security.util.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "SecP256r1MLKEM768", "sun.security.util.Hybrid$KeyFactoryImpl",
                    null, attrs));

            attrs = Map.of("name", "SecP384r1MLKEM1024", "left", "secp384r1",
                    "right", "ML-KEM-1024");
            putService(new HybridService(this, "KeyPairGenerator",
                    "SecP384r1MLKEM1024", "sun.security.util.Hybrid$KeyPairGeneratorImpl",
                    null, attrs));
            putService(new HybridService(this, "KEM",
                    "SecP384r1MLKEM1024", "sun.security.util.Hybrid$KEMImpl",
                    null, attrs));
            putService(new HybridService(this, "KeyFactory",
                    "SecP384r1MLKEM1024", "sun.security.util.Hybrid$KeyFactoryImpl",
                    null, attrs));
        }
    }

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

    static final class Handler
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
            return params.Nsecret;
        }

        @Override
        public int engineEncapsulationSize() {
            return params.Npk;
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from,
                int to, String algorithm) throws DecapsulateException {
            if (encapsulation.length != params.Npk) {
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
            if (from == 0 && to == params.Nsecret) {
                return key;
            } else if ("RAW".equalsIgnoreCase(key.getFormat())) {
                byte[] km = key.getEncoded();
                if (km == null) {
                    // Should not happen if format is "RAW"
                    throw new UnsupportedOperationException("Key extract failed");
                } else {
                    return new SecretKeySpec(km, from, to - from,
                            key.getAlgorithm());
                }
            } else {
                throw new UnsupportedOperationException("Cannot extract key");
            }
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
                "XDH", "XDH", NamedParameterSpec.X448),
        ;
        private final int Nsecret;
        private final int Npk;
        private final String kaAlgorithm;
        private final String keyAlgorithm;
        private final AlgorithmParameterSpec spec;


        Params(int Nsecret, int Npk, String kaAlgorithm, String keyAlgorithm,
                AlgorithmParameterSpec spec) {
            this.spec = spec;
            this.Nsecret = Nsecret;
            this.Npk = Npk;
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
                return Arrays.copyOf(uArray, Npk);
            }
        }

        private PublicKey DeserializePublicKey(byte[] data) throws
                IOException, NoSuchAlgorithmException, InvalidKeySpecException {
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

        private SecretKey DH(String alg, PrivateKey skE, PublicKey pkR)
                throws NoSuchAlgorithmException, InvalidKeyException {
            KeyAgreement ka = KeyAgreement.getInstance(kaAlgorithm);
            ka.init(skE);
            ka.doPhase(pkR, true);
            return ka.generateSecret(alg);
        }
    }

    private static class HybridService extends Provider.Service {

        HybridService(Provider p, String type, String algo, String cn,
                List<String> aliases, Map<String, String> attrs) {
            super(p, type, algo, cn, aliases, attrs);
        }

        @Override
        public Object newInstance(Object ctrParamObj)
                throws NoSuchAlgorithmException {
            String type = getType();
            return switch (type) {
                case "KeyPairGenerator" -> new Hybrid.KeyPairGeneratorImpl(
                        getAttribute("left"), getAttribute("right"));
                case "KeyFactory" -> new Hybrid.KeyFactoryImpl(
                        getAttribute("left"), getAttribute("right"));
                case "KEM" -> new Hybrid.KEMImpl(
                        getAttribute("left"), getAttribute("right"));
                default -> throw new NoSuchAlgorithmException(
                        "Unexpected value: " + type);
            };
        }
    }
}
