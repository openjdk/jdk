/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.ssl.HKDF;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.DecapsulateException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KEM;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.AlgorithmParameters;
import java.security.AsymmetricKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECKey;
import java.security.interfaces.XECKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class HPKE extends CipherSpi {

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
            throw new IllegalStateException("No AEAD cipher");
        }
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        if (state == ENCRYPT_AND_EXPORT || state == AFTER_FINAL) {
            return impl.aead.cipher.getOutputSize(inputLen);
        } else {
            throw new IllegalStateException("No AEAD cipher");
        }
    }

    @Override
    protected byte[] engineGetIV() {
        return state == BEGIN ? null : impl.iv;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
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
    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        impl = new Impl(opmode);
        if (params == null) {
            impl.init(key, HPKEParameterSpec.of(), random);
        } else if (params instanceof IvParameterSpec iv) {
            impl.init(key, HPKEParameterSpec.of().encapsulation(iv.getIV()), random);
        } else if (params instanceof HPKEParameterSpec hps) {
            impl.init(key, hps, random);
        } else {
            throw new InvalidAlgorithmParameterException("Unsupported params type: " + params.getClass());
        }
        if (impl.hasEncrypt()) {
            impl.aead.start(impl.opmode, impl.context.k, impl.context.ComputeNonce());
            state = ENCRYPT_AND_EXPORT;
        } else {
            state = EXPORT_ONLY;
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException();
    }

    // state is ENCRYPT_AND_EXPORT after this call succeeds
    private void maybeReinitInternalCipher() {
        if (state == BEGIN) {
            throw new IllegalStateException();
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
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
        maybeReinitInternalCipher();
        return impl.aead.cipher.update(input, inputOffset, inputLen, output, outputOffset);
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
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
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
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        maybeReinitInternalCipher();
        impl.context.IncrementSeq();
        state = AFTER_FINAL;
        return impl.aead.cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
    }

    //@Override
    protected SecretKey engineExportKey(byte[] context, String algorithm, int length) {
        if (state == BEGIN) {
            throw new IllegalStateException("State: " + state);
        } else {
            return impl.context.Export(context, algorithm, length);
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
                    case 1 -> {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        Nk = 16;
                    }
                    case 2 -> {
                        cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        Nk = 32;
                    }
                    case 3 -> {
                        cipher = Cipher.getInstance("ChaCha20-Poly1305");
                        Nk = 32;
                    }
                    case 65535 -> {
                        cipher = null;
                        Nk = -1;
                    }
                    default -> throw new InvalidAlgorithmParameterException();
                }
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new ProviderException(e);
            }
            Nn = 12; Nt = 16;
        }

        void start(int opmode, SecretKey key, byte[] nonce) {
            try {
                if (id == 3) {
                    cipher.init(opmode, key, new IvParameterSpec(nonce));
                } else {
                    cipher.init(opmode, key, new GCMParameterSpec(Nt * 8, nonce));
                }
            } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
                throw new ProviderException(e);
            }
        }
    }

    private static class Impl {

        final int opmode;

        HPKEParameterSpec params;
        Context context;
        AEAD aead;

        byte[] suite_id;
        HKDF hkdf;
        int kdfNh;

        byte[] iv; // sender side

        class Context {
            final SecretKey k; // null if only export
            final byte[] base_nonce;
            final SecretKey exporter_secret;

            long seq = 0;

            public Context(SecretKey sk, byte[] base_nonce,
                    SecretKey exporter_secret) {
                this.k = sk;
                this.base_nonce = base_nonce;
                this.exporter_secret = exporter_secret;
            }

            SecretKey Export(byte[] exporter_context, String algorithm, int L) {
                try {
                    return new SecretKeySpec(DHKEM.LabeledExpand(hkdf, suite_id, exporter_secret,
                            "sec".getBytes(StandardCharsets.UTF_8),
                            exporter_context, L), algorithm);
                } catch (InvalidKeyException e) {
                    throw new ProviderException("Internal error", e);
                }
            }

            private byte[] ComputeNonce() {
                var result = I2OSP(seq, aead.Nn);
                for (var i = 0; i < result.length; i++) {
                    result[i] ^= base_nonce[i];
                }
                return result;
            }

            private void IncrementSeq() {
                if (seq == Long.MAX_VALUE) {
                    // Should check if (seq >= (1 << (8*aead.Nn)) - 1), but
                    // when Nn == 12 this is too big
                    throw new ProviderException("MessageLimitReachedError");
                }
                seq++;
            }
        }

        public Impl(int opmode) {
            this.opmode = opmode;
        }

        public boolean hasEncrypt() {
            return params.aead_id() != 65535;
        }

        public void init(Key key, HPKEParameterSpec p, SecureRandom rand)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (opmode != Cipher.ENCRYPT_MODE && opmode != Cipher.DECRYPT_MODE) {
                throw new UnsupportedOperationException("Can only be used for encryption and decryption");
            }
            setParams(key, p);
            SecretKey shared_secret;
            if (opmode == Cipher.ENCRYPT_MODE) {
                if (!(key instanceof PublicKey pk)) {
                    throw new InvalidKeyException("Decrypt with private key");
                }
                if (p.encapsulation() != null) {
                    throw new InvalidAlgorithmParameterException(
                            "Must not provide key encapsulation message on sender side");
                }
                checkMatch(pk, params.kem_id());
                KEM.Encapsulator e;
                if (p.authKey() == null) {
                    e = kem().newEncapsulator(pk, rand);
                } else {
                    if (p.authKey() instanceof PrivateKey) {
                        throw new UnsupportedOperationException("auth mode not supported");
                    } else {
                        throw new InvalidAlgorithmParameterException("Cannot auth with public key");
                    }
                }
                var enc = e.encapsulate();
                iv = enc.encapsulation();
                shared_secret = enc.key();
            } else {
                if (!(key instanceof PrivateKey sk)) {
                    throw new InvalidKeyException("Encrypt with public key");
                }
                checkMatch(sk, params.kem_id());
                try {
                    KEM.Decapsulator d;
                    if (p.authKey() == null) {
                        d = kem().newDecapsulator(sk);
                    } else {
                        if (p.authKey() instanceof PublicKey) {
                            throw new UnsupportedOperationException("auth mode not supported");
                        } else {
                            throw new InvalidAlgorithmParameterException("Cannot auth with private key");
                        }
                    }
                    if (p.encapsulation() == null) {
                        throw new InvalidAlgorithmParameterException(
                                "Must provide key encapsulation message on recipient side");
                    }
                    shared_secret = d.decapsulate(p.encapsulation());
                } catch (DecapsulateException e) {
                    throw new InvalidAlgorithmParameterException(e);
                }
            }

            var usePSK = usePSK(params.psk(), params.psk_id());
            int mode = params.authKey() == null ? (usePSK ? 1 : 0) : (usePSK ? 3 : 2);
            context = KeySchedule(mode, shared_secret,
                    params.info(),
                    params.psk(),
                    params.psk_id());
        }

        private static void checkMatch(AsymmetricKey k, int kem_id)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            if (k instanceof ECKey eckey) {
                if ((ECUtil.equals(eckey.getParams(), CurveDB.P_256) && kem_id == 0x10)
                        || (ECUtil.equals(eckey.getParams(), CurveDB.P_384) && kem_id == 0x11)
                        || (ECUtil.equals(eckey.getParams(), CurveDB.P_521) && kem_id == 0x12)) {
                    return;
                } else {
                    var name = ECUtil.getCurveName(eckey.getParams());
                    throw new InvalidAlgorithmParameterException(name + " does not match " + kem_id);
                }
            } else if (k instanceof XECKey xk && xk.getParams() instanceof NamedParameterSpec ns) {
                var name = ns.getName();
                if ((name.equalsIgnoreCase("x25519") && kem_id == 0x20)
                        || (name.equalsIgnoreCase("x448") && kem_id == 0x21)) {
                    return;
                } else {
                    throw new InvalidAlgorithmParameterException(name + " does not match " + kem_id);
                }
            } else {
                throw new InvalidKeyException(k.getClass() + " does not match " + kem_id);
            }
        }

        private KEM kem() {
            try {
                return KEM.getInstance("DHKEM");
            } catch (NoSuchAlgorithmException e) {
                throw new ProviderException(e);
            }
        }

        private int paramsFromKey(Key k) throws InvalidKeyException {
            if (k instanceof ECKey eckey) {
                if (ECUtil.equals(eckey.getParams(), CurveDB.P_256)) {
                    return 0x10;
                } else if (ECUtil.equals(eckey.getParams(), CurveDB.P_384)) {
                    return 0x11;
                } else if (ECUtil.equals(eckey.getParams(), CurveDB.P_521)) {
                    return 0x12;
                }
            } else if (k instanceof XECKey xkey
                    && xkey.getParams() instanceof NamedParameterSpec ns) {
                if (ns.getName().equalsIgnoreCase("X25519")) {
                    return 0x20;
                } else if (ns.getName().equalsIgnoreCase("X448")) {
                    return 0x21;
                }
            }
            throw new InvalidKeyException("Unsupported key");
        }

        private void setParams(Key key, HPKEParameterSpec p)
                throws InvalidKeyException, InvalidAlgorithmParameterException {
            this.params = p;
            if (p.kem_id() == 0) {
                int kem_id = paramsFromKey(key);
                int kdf_id = switch (kem_id) {
                    case 0x10, 0x20 -> 0x1;
                    case 0x11 -> 0x2;
                    case 0x12, 0x21 -> 0x3;
                    default -> throw new InvalidAlgorithmParameterException();
                };
                int aead_id = 0x2;
                params = HPKEParameterSpec.of(kem_id, kdf_id, aead_id)
                        .info(p.info())
                        .psk(p.psk(), p.psk_id())
                        .authKey(p.authKey())
                        .encapsulation(p.encapsulation());
            } else {
                params = p;
            }
            checkDisabledAlgorithms(params);
            suite_id = concat(
                    "HPKE".getBytes(StandardCharsets.UTF_8),
                    DHKEM.I2OSP(params.kem_id(), 2),
                    DHKEM.I2OSP(params.kdf_id(), 2),
                    DHKEM.I2OSP(params.aead_id(), 2));
            var kdfAlg = switch (params.kdf_id()) {
                case 1 -> "SHA-256";
                case 2 -> "SHA-384";
                case 3 -> "SHA-512";
                default -> throw new InvalidAlgorithmParameterException();
            };
            kdfNh = switch (params.kdf_id()) {
                case 1 -> 32;
                case 2 -> 48;
                case 3 -> 64;
                default -> throw new InvalidAlgorithmParameterException();
            };
            try {
                hkdf = new HKDF(kdfAlg);
            } catch (NoSuchAlgorithmException e) {
                throw new InvalidAlgorithmParameterException("Cannot find HKDF for " + kdfAlg, e);
            }
            aead = new AEAD(params.aead_id());
        }

        private static int[][][] disabledIdentifiers;
        static {
            disabledIdentifiers = new int[3][][];
            List<int[]> disabledKEMs = new ArrayList<>();
            List<int[]> disabledKDFs = new ArrayList<>();
            List<int[]> disabledAEADs = new ArrayList<>();
            @SuppressWarnings("removal")
            String property = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {
                        @Override
                        public String run() {
                            return Security.getProperty("jdk.hpke.disabledAlgorithms");
                        }
                    });
            if (property != null) {
                for (String rule : property.split(",")) {
                    if (rule == null) {
                        continue;
                    }
                    rule = rule.trim();
                    if (rule.isEmpty()) {
                        continue;
                    }
                    int pos1 = rule.indexOf("=");
                    int pos2 = rule.indexOf("-", pos1);
                    if (pos1 == -1) {
                        throw new IllegalArgumentException(
                                "Invalid jdk.hpke.disabledAlgorithms: " + property);
                    }
                    int[] range = new int[2];
                    try {
                        if (pos2 == -1) {
                            range[0] = range[1] = Integer.parseInt(rule.substring(pos1 + 1).trim());
                        } else {
                            range[0] = Integer.parseInt(rule.substring(pos1 + 1, pos2).trim());
                            range[1] = Integer.parseInt(rule.substring(pos2 + 1).trim());
                            if (range[0] > range[1]) {
                                throw new IllegalArgumentException(
                                        "Invalid jdk.hpke.disabledAlgorithms: " + property);
                            }
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "Invalid jdk.hpke.disabledAlgorithms: " + property, e);
                    }
                    switch (rule.substring(0, pos1).trim()) {
                        case "kem_id" -> disabledKEMs.add(range);
                        case "kdf_id" -> disabledKDFs.add(range);
                        case "aead_id" -> disabledAEADs.add(range);
                        default -> throw new IllegalArgumentException(
                                "Invalid jdk.hpke.disabledAlgorithms: " + property);
                    }
                }
            }
            disabledIdentifiers[0] = disabledKEMs.toArray(new int[0][]);
            disabledIdentifiers[1] = disabledKDFs.toArray(new int[0][]);
            disabledIdentifiers[2] = disabledAEADs.toArray(new int[0][]);
        }

        private static void checkDisabledAlgorithms(HPKEParameterSpec params)
                throws InvalidAlgorithmParameterException {
            checkDisabled("kem_id", disabledIdentifiers[0], params.kem_id());
            checkDisabled("kdf_id", disabledIdentifiers[1], params.kdf_id());
            checkDisabled("aead_id", disabledIdentifiers[2], params.aead_id());
        }

        private static void checkDisabled(String label, int[][] ranges, int id)
                throws InvalidAlgorithmParameterException {
            for (int[] range : ranges) {
                if (id >= range[0] && id <= range[1]) {
                    throw new InvalidAlgorithmParameterException(
                            "Disabled " + label + ": " + id);
                }
            }
        }

        private Context KeySchedule(int mode,
                SecretKey shared_secret,
                byte[] info,
                SecretKey psk,
                byte[] psk_id) {
            try {
                var psk_id_hash = DHKEM.LabeledExtract(hkdf, suite_id, null,
                        "psk_id_hash".getBytes(StandardCharsets.UTF_8), psk_id).getEncoded();
                var info_hash = DHKEM.LabeledExtract(hkdf, suite_id, null,
                        "info_hash".getBytes(StandardCharsets.UTF_8), info).getEncoded();
                var key_schedule_context = concat(new byte[]{(byte) mode}, psk_id_hash, info_hash);
                var secret = DHKEM.LabeledExtract(hkdf, suite_id, Objects.requireNonNull(shared_secret.getEncoded()),
                        "secret".getBytes(StandardCharsets.UTF_8),
                        psk == null ? new byte[0] : Objects.requireNonNull(psk.getEncoded()));

                var exporter_secret = new SecretKeySpec(DHKEM.LabeledExpand(hkdf, suite_id, secret,
                        "exp".getBytes(StandardCharsets.UTF_8), key_schedule_context, kdfNh), "EXPORTER_SECRET");

                if (hasEncrypt()) {
                    // ChaCha20-Poly1305 does not care about algorithm name
                    var key = new SecretKeySpec(DHKEM.LabeledExpand(hkdf, suite_id, secret, "key".getBytes(StandardCharsets.UTF_8),
                            key_schedule_context, aead.Nk), "AES");
                    var base_nonce = DHKEM.LabeledExpand(hkdf, suite_id, secret, "base_nonce".getBytes(StandardCharsets.UTF_8),
                            key_schedule_context, aead.Nn);
                    return new Context(key, base_nonce, exporter_secret);
                } else {
                    return new Context(null, null, exporter_secret);
                }
            } catch (InvalidKeyException e) {
                throw new ProviderException("Internal error", e);
            }
        }
    }

    private static boolean usePSK(SecretKey psk, byte[] psk_id) {
        return psk != null;
    }

    private static byte[] concat(byte[]... inputs) {
        var o = new ByteArrayOutputStream();
        Arrays.stream(inputs).forEach(o::writeBytes);
        return o.toByteArray();
    }

    private static byte[] I2OSP(long n, int w) {
        var full = BigInteger.valueOf(n).toByteArray();
        var fullLen = full.length;
        if (fullLen == w) {
            return full;
        } else if (fullLen > w) {
            return Arrays.copyOfRange(full, fullLen - w, fullLen);
        } else {
            var result = new byte[w];
            System.arraycopy(full, 0, result, w - fullLen, fullLen);
            return result;
        }
    }
}
