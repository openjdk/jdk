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

import sun.security.util.CurveDB;
import sun.security.util.ECUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.DecapsulateException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.AsymmetricKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

public class HPKE extends CipherSpi {

    private static final byte[] HPKE = new byte[]
            {'H', 'P', 'K', 'E'};
    private static final byte[] SEC = new byte[]
            {'s', 'e', 'c'};
    private static final byte[] PSK_ID_HASH = new byte[]
            {'p', 's', 'k', '_', 'i', 'd', '_', 'h', 'a', 's', 'h'};
    private static final byte[] INFO_HASH = new byte[]
            {'i', 'n', 'f', 'o', '_', 'h', 'a', 's', 'h'};
    private static final byte[] SECRET = new byte[]
            {'s', 'e', 'c', 'r', 'e', 't'};
    private static final byte[] EXP = new byte[]
            {'e', 'x', 'p'};
    private static final byte[] KEY = new byte[]
            {'k', 'e', 'y'};
    private static final byte[] BASE_NONCE = new byte[]
            {'b', 'a', 's', 'e', '_', 'n', 'o', 'n', 'c', 'e'};

    private static final int BEGIN = 1;
    private static final int EXPORT_ONLY = 2; // init done with aead_id == 65535
    private static final int ENCRYPT_AND_EXPORT = 3; // int done with AEAD
    private static final int AFTER_FINAL = 4; // after doFinal, need reinit internal cipher

    private int state = BEGIN;
    private Impl impl;

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        throw new NoSuchAlgorithmException(mode);
    }

    @Override
    protected void engineSetPadding(String padding) throws NoSuchPaddingException {
        throw new NoSuchPaddingException(padding);
    }

    @Override
    protected int engineGetBlockSize() {
        if (state == ENCRYPT_AND_EXPORT || state == AFTER_FINAL) {
            return impl.aead.cipher.getBlockSize();
        } else {
            return 0;
        }
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        if (state == ENCRYPT_AND_EXPORT || state == AFTER_FINAL) {
            return impl.aead.cipher.getOutputSize(inputLen);
        } else {
            return 0;
        }
    }

    @Override
    protected byte[] engineGetIV() {
        return state == BEGIN ? null : impl.params.encapsulation();
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        if (state == BEGIN) {
            return null;
        }
        try {
            var result = AlgorithmParameters.getInstance("HPKE");
            result.init(impl.params);
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException("Cannot find implementations", e);
        } catch (InvalidParameterSpecException e) {
            throw new ProviderException("Parameters not supported", e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            // Parent spec says "throws InvalidKeyException if the given key
            // requires algorithm parameters that cannot be determined from
            // the given key"
            throw new InvalidKeyException(e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        impl = new Impl(opmode);
        if (!(key instanceof AsymmetricKey ak)) {
            throw new InvalidKeyException("Not an asymmetric key");
        }
        if (params == null) {
            impl.init(ak, HPKEParameterSpec.of(), random);
        } else if (params instanceof HPKEParameterSpec hps) {
            impl.init(ak, hps, random);
        } else {
            throw new InvalidAlgorithmParameterException(
                    "Unsupported params type: " + params.getClass());
        }
        if (impl.hasEncrypt()) {
            impl.aead.start(impl.opmode, impl.context.k, impl.context.ComputeNonce());
            state = ENCRYPT_AND_EXPORT;
        } else {
            state = EXPORT_ONLY;
        }
    }

    @Override
    protected void engineInit(int opmode, Key key,
            AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        try {
            engineInit(opmode, key, params.getParameterSpec(HPKEParameterSpec.class), random);
        } catch (InvalidParameterSpecException e) {
            throw new InvalidAlgorithmParameterException("Cannot extract HPKEParameterSpec", e);
        }
    }

    // state is ENCRYPT_AND_EXPORT after this call succeeds
    private void maybeReinitInternalCipher() {
        if (state == BEGIN) {
            throw new IllegalStateException("Illegal state: " + state);
        }
        if (state == EXPORT_ONLY) {
            throw new UnsupportedOperationException();
        }
        if (state == AFTER_FINAL) {
            impl.aead.start(impl.opmode, impl.context.k, impl.context.ComputeNonce());
            state = ENCRYPT_AND_EXPORT;
        }
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        maybeReinitInternalCipher();
        return impl.aead.cipher.update(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
            byte[] output, int outputOffset) throws ShortBufferException {
        maybeReinitInternalCipher();
        return impl.aead.cipher.update(
                input, inputOffset, inputLen, output, outputOffset);
    }

    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        maybeReinitInternalCipher();
        impl.aead.cipher.updateAAD(src, offset, len);
    }

    @Override
    protected void engineUpdateAAD(ByteBuffer src) {
        maybeReinitInternalCipher();
        impl.aead.cipher.updateAAD(src);
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        maybeReinitInternalCipher();
        impl.context.IncrementSeq();
        state = AFTER_FINAL;
        if (input == null) { // a bug in doFinal(null, ?, ?)
            return impl.aead.cipher.doFinal();
        } else {
            return impl.aead.cipher.doFinal(input, inputOffset, inputLen);
        }
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
            byte[] output, int outputOffset) throws ShortBufferException,
            IllegalBlockSizeException, BadPaddingException {
        maybeReinitInternalCipher();
        impl.context.IncrementSeq();
        state = AFTER_FINAL;
        return impl.aead.cipher.doFinal(
                input, inputOffset, inputLen, output, outputOffset);
    }

    //@Override
    protected SecretKey engineExportKey(String algorithm, byte[] context, int length) {
        if (state == BEGIN) {
            throw new IllegalStateException("State: " + state);
        } else {
            return impl.context.ExportKey(algorithm, context, length);
        }
    }

    //@Override
    protected byte[] engineExportData(byte[] context, int length) {
        if (state == BEGIN) {
            throw new IllegalStateException("State: " + state);
        } else {
            return impl.context.ExportData(context, length);
        }
    }

    private static class AEAD {
        final Cipher cipher;
        final int Nk, Nn, Nt;
        final int id;
        public AEAD(int id) throws InvalidAlgorithmParameterException {
            this.id = id;
            try {
                switch (id) {
                    case HPKEParameterSpec.AEAD_AES_128_GCM -> {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        Nk = 16;
                    }
                    case HPKEParameterSpec.AEAD_AES_256_GCM -> {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        Nk = 32;
                    }
                    case HPKEParameterSpec.AEAD_CHACHA20_POLY1305 -> {
                        cipher = Cipher.getInstance("ChaCha20-Poly1305");
                        Nk = 32;
                    }
                    case HPKEParameterSpec.EXPORT_ONLY -> {
                        cipher = null;
                        Nk = -1;
                    }
                    default -> throw new InvalidAlgorithmParameterException(
                            "Unknown aead_id: " + id);
                }
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new ProviderException("Internal error", e);
            }
            Nn = 12; Nt = 16;
        }

        void start(int opmode, SecretKey key, byte[] nonce) {
            try {
                if (id == HPKEParameterSpec.AEAD_CHACHA20_POLY1305) {
                    cipher.init(opmode, key, new IvParameterSpec(nonce));
                } else {
                    cipher.init(opmode, key, new GCMParameterSpec(Nt * 8, nonce));
                }
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
                throw new ProviderException("Internal error", e);
            }
        }
    }

    private static class Impl {

        final int opmode;

        HPKEParameterSpec params;
        Context context;
        AEAD aead;

        byte[] suite_id;
        String kdfAlg;
        int kdfNh;

        class Context {
            final SecretKey k; // null if only export
            final byte[] base_nonce;
            final SecretKey exporter_secret;

            byte[] seq = new byte[aead.Nn];

            public Context(SecretKey sk, byte[] base_nonce,
                    SecretKey exporter_secret) {
                this.k = sk;
                this.base_nonce = base_nonce;
                this.exporter_secret = exporter_secret;
            }

            SecretKey ExportKey(String algorithm, byte[] exporter_context, int L) {
                if (exporter_context == null) {
                    throw new IllegalArgumentException("Null exporter_context");
                }
                try {
                    var kdf = KDF.getInstance(kdfAlg);
                    return kdf.deriveKey(algorithm, DHKEM.labeledExpand(
                            exporter_secret, suite_id, SEC, exporter_context, L));
                } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
                    // algorithm not accepted by HKDF, L too big or too small
                    throw new IllegalArgumentException("Invalid input", e);
                }
            }

            byte[] ExportData(byte[] exporter_context, int L) {
                if (exporter_context == null) {
                    throw new IllegalArgumentException("Null exporter_context");
                }
                try {
                    var kdf = KDF.getInstance(kdfAlg);
                    return kdf.deriveData(DHKEM.labeledExpand(
                            exporter_secret, suite_id, SEC, exporter_context, L));
                } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
                    // algorithm not accepted by HKDF, L too big or too small
                    throw new IllegalArgumentException("Invalid input", e);
                }
            }

            private byte[] ComputeNonce() {
                var result = new byte[aead.Nn];
                for (var i = 0; i < result.length; i++) {
                    result[i] = (byte)(seq[i] ^ base_nonce[i]);
                }
                return result;
            }

            private void IncrementSeq() {
                for (var i = seq.length - 1; i >= 0; i--) {
                    if ((seq[i] & 0xff) == 0xff) {
                        seq[i] = 0;
                    } else {
                        seq[i]++;
                        return;
                    }
                }
                // seq >= (1 << (8*aead.Nn)) - 1 when this method is called
                throw new ProviderException("MessageLimitReachedError");
            }
        }

        public Impl(int opmode) {
            this.opmode = opmode;
        }

        public boolean hasEncrypt() {
            return params.aead_id() != 65535;
        }

        // Section 7.2.1 of RFC 9180 has restrictions on size of psk, psk_id,
        // info, and exporter_context (~2^61 for HMAC-SHA256 and ~2^125 for
        // HMAC-SHA384 and HMAC-SHA512). This method does not pose any
        // restrictions.
        public void init(AsymmetricKey key, HPKEParameterSpec p, SecureRandom rand)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (opmode != Cipher.ENCRYPT_MODE && opmode != Cipher.DECRYPT_MODE) {
                throw new UnsupportedOperationException(
                        "Can only be used for encryption and decryption");
            }
            setParams(key, p);
            SecretKey shared_secret;
            if (opmode == Cipher.ENCRYPT_MODE) {
                if (!(key instanceof PublicKey pk)) {
                    throw new InvalidKeyException(
                            "Cannot encrypt with private key");
                }
                if (p.encapsulation() != null) {
                    throw new InvalidAlgorithmParameterException(
                            "Must not provide key encapsulation message on sender side");
                }
                checkMatch(pk, params.kem_id());
                KEM.Encapsulated enc;
                if (p.authKey() == null) {
                    var e = kem().newEncapsulator(pk, rand);
                    enc = e.encapsulate();
                } else if (p.authKey() instanceof PrivateKey skS) {
                    // AuthEncap not public KEM API but it's internally supported
                    var e = new DHKEM().engineNewAuthEncapsulator(pk, skS, null, rand);
                    enc = e.engineEncapsulate(0, e.engineSecretSize(), "Generic");
                } else {
                    throw new InvalidAlgorithmParameterException(
                            "Cannot auth with public key");
                }
                params = params.encapsulation(enc.encapsulation());
                shared_secret = enc.key();
            } else {
                if (!(key instanceof PrivateKey sk)) {
                    throw new InvalidKeyException("Cannot decrypt with public key");
                }
                checkMatch(sk, params.kem_id());
                try {
                    var encap = p.encapsulation();
                    if (encap == null) {
                        throw new InvalidAlgorithmParameterException(
                                "Must provide key encapsulation message on recipient side");
                    }
                    if (p.authKey() == null) {
                        var d = kem().newDecapsulator(sk);
                        shared_secret = d.decapsulate(encap);
                    } else if (p.authKey() instanceof PublicKey pkS) {
                        // AuthDecap not public KEM API but it's internally supported
                        var d = new DHKEM().engineNewAuthDecapsulator(sk, pkS, null);
                        shared_secret = d.engineDecapsulate(
                                encap, 0, d.engineSecretSize(), "Generic");
                    } else {
                        throw new InvalidAlgorithmParameterException(
                                "Cannot auth with private key");
                    }
                } catch (DecapsulateException e) {
                    throw new InvalidAlgorithmParameterException(e);
                }
            }

            var usePSK = usePSK(params.psk());
            int mode = params.authKey() == null ? (usePSK ? 1 : 0) : (usePSK ? 3 : 2);
            context = KeySchedule(mode, shared_secret,
                    params.info(),
                    params.psk(),
                    params.psk_id());
        }

        private static void checkMatch(AsymmetricKey k, int kem_id)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            var p = k.getParams();
            if (p instanceof ECParameterSpec ecp) {
                if ((!ECUtil.equals(ecp, CurveDB.P_256)
                        || kem_id != HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256)
                        && (!ECUtil.equals(ecp, CurveDB.P_384)
                        || kem_id != HPKEParameterSpec.KEM_DHKEM_P_384_HKDF_SHA384)
                        && (!ECUtil.equals(ecp, CurveDB.P_521)
                        || kem_id != HPKEParameterSpec.KEM_DHKEM_P_521_HKDF_SHA512)) {
                    var name = ECUtil.getCurveName(ecp);
                    throw new InvalidAlgorithmParameterException(
                            name + " does not match " + kem_id);
                }
            } else if (p instanceof NamedParameterSpec ns) {
                var name = ns.getName();
                if ((!name.equalsIgnoreCase("x25519")
                        || kem_id != HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256)
                        && (!name.equalsIgnoreCase("x448")
                        || kem_id != HPKEParameterSpec.KEM_DHKEM_X448_HKDF_SHA512)) {
                    throw new InvalidAlgorithmParameterException(
                            name + " does not match " + kem_id);
                }
            } else {
                throw new InvalidKeyException(
                        k.getClass() + " does not match " + kem_id);
            }
        }

        private KEM kem() {
            try {
                return KEM.getInstance("DHKEM");
            } catch (NoSuchAlgorithmException e) {
                throw new ProviderException("Internal error", e);
            }
        }

        private int kemIdFromKey(AsymmetricKey k) throws InvalidKeyException {
            var p = k.getParams();
            if (p instanceof ECParameterSpec ecp) {
                if (ECUtil.equals(ecp, CurveDB.P_256)) {
                    return HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256;
                } else if (ECUtil.equals(ecp, CurveDB.P_384)) {
                    return HPKEParameterSpec.KEM_DHKEM_P_384_HKDF_SHA384;
                } else if (ECUtil.equals(ecp, CurveDB.P_521)) {
                    return HPKEParameterSpec.KEM_DHKEM_P_521_HKDF_SHA512;
                }
            } else if (p instanceof NamedParameterSpec ns) {
                if (ns.getName().equalsIgnoreCase("X25519")) {
                    return HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256;
                } else if (ns.getName().equalsIgnoreCase("X448")) {
                    return HPKEParameterSpec.KEM_DHKEM_X448_HKDF_SHA512;
                }
            }
            throw new InvalidKeyException("Unsupported key");
        }

        private void setParams(AsymmetricKey key, HPKEParameterSpec p)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (p.kem_id() == -1 || p.kdf_id() == -1 || p.aead_id() == -1) {
                if (opmode == Cipher.DECRYPT_MODE) {
                    throw new InvalidAlgorithmParameterException(
                            "Algorithm identifiers must be provided on receiver");
                }
                var kem_id = p.kem_id() != -1
                        ? p.kem_id()
                        : kemIdFromKey(key);
                var kdf_id = p.kdf_id() != -1
                        ? p.kdf_id()
                        : switch (kem_id) {
                    case HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256,
                         HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256
                            -> HPKEParameterSpec.KDF_HKDF_SHA256;
                    case HPKEParameterSpec.KEM_DHKEM_P_384_HKDF_SHA384
                            -> HPKEParameterSpec.KDF_HKDF_SHA384;
                    case HPKEParameterSpec.KEM_DHKEM_P_521_HKDF_SHA512,
                         HPKEParameterSpec.KEM_DHKEM_X448_HKDF_SHA512
                            -> HPKEParameterSpec.KDF_HKDF_SHA512;
                    default -> throw new InvalidAlgorithmParameterException(
                            "Unsupported kem_id: " + params.kem_id());
                };
                var aead_id = p.aead_id() != -1
                        ? p.aead_id()
                        : HPKEParameterSpec.AEAD_AES_256_GCM;
                params = HPKEParameterSpec.of(kem_id, kdf_id, aead_id)
                        .info(p.info())
                        .psk(p.psk(), p.psk_id())
                        .authKey(p.authKey())
                        .encapsulation(p.encapsulation());
            } else {
                params = p;
            }
            suite_id = concat(
                    HPKE,
                    DHKEM.I2OSP(params.kem_id(), 2),
                    DHKEM.I2OSP(params.kdf_id(), 2),
                    DHKEM.I2OSP(params.aead_id(), 2));
            kdfAlg = switch (params.kdf_id()) {
                case HPKEParameterSpec.KDF_HKDF_SHA256 -> "HKDF-SHA256";
                case HPKEParameterSpec.KDF_HKDF_SHA384 -> "HKDF-SHA384";
                case HPKEParameterSpec.KDF_HKDF_SHA512 -> "HKDF-SHA512";
                default -> throw new InvalidAlgorithmParameterException(
                        "Unsupported kdf_id: " + params.kdf_id());
            };
            kdfNh = switch (params.kdf_id()) {
                case HPKEParameterSpec.KDF_HKDF_SHA256 -> 32;
                case HPKEParameterSpec.KDF_HKDF_SHA384 -> 48;
                case HPKEParameterSpec.KDF_HKDF_SHA512 -> 64;
                default -> throw new InvalidAlgorithmParameterException(
                        "Unsupported kdf_id: " + params.kdf_id());
            };
            aead = new AEAD(params.aead_id());
        }

        private Context KeySchedule(int mode,
                SecretKey shared_secret,
                byte[] info,
                SecretKey psk,
                byte[] psk_id) {
            try {
                var psk_id_hash_x = DHKEM.labeledExtract(suite_id, PSK_ID_HASH)
                        .addIKM(psk_id).extractOnly();
                var info_hash_x = DHKEM.labeledExtract(suite_id, INFO_HASH)
                        .addIKM(info).extractOnly();

                // deriveData must and can be called because all info to
                // thw builder are just byte arrays. Any KDF impl can handle this.
                var kdf = KDF.getInstance(kdfAlg);
                var key_schedule_context = concat(new byte[]{(byte) mode},
                        kdf.deriveData(psk_id_hash_x),
                        kdf.deriveData(info_hash_x));

                var secret_x_builder = DHKEM.labeledExtract(suite_id, SECRET);
                if (psk != null) {
                    secret_x_builder.addIKM(psk);
                }
                secret_x_builder.addSalt(shared_secret);

                // A new KDF object must be created because secret_x_builder
                // might contain provider-specific keys which the previous
                // KDF (provider already chosen) cannot handle.
                kdf = KDF.getInstance(kdfAlg);
                var exporter_secret = kdf.deriveKey("Generic", DHKEM.labeledExpand(
                        secret_x_builder, suite_id, EXP, key_schedule_context, kdfNh));

                if (hasEncrypt()) {
                    // ChaCha20-Poly1305 does not care about algorithm name
                    var key = kdf.deriveKey("AES", DHKEM.labeledExpand(secret_x_builder,
                            suite_id, KEY, key_schedule_context, aead.Nk));
                    // deriveData must be called because we need to increment nonce
                    var base_nonce = kdf.deriveData(DHKEM.labeledExpand(secret_x_builder,
                            suite_id, BASE_NONCE, key_schedule_context, aead.Nn));
                    return new Context(key, base_nonce, exporter_secret);
                } else {
                    return new Context(null, null, exporter_secret);
                }
            } catch (InvalidAlgorithmParameterException
                     | NoSuchAlgorithmException | UnsupportedOperationException e) {
                throw new ProviderException("Internal error", e);
            }
        }
    }

    private static boolean usePSK(SecretKey psk) {
        return psk != null;
    }

    private static byte[] concat(byte[]... inputs) {
        var o = new ByteArrayOutputStream();
        Arrays.stream(inputs).forEach(o::writeBytes);
        return o.toByteArray();
    }
}
