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
package javax.crypto.spec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

/**
 * Specifies a set of parameters used by a {@code Cipher} for the
 * Hybrid Public Key Encryption (HPKE) algorithm. HPKE is defined
 * in https://datatracker.ietf.org/doc/rfc9180/. The <a href=
 * "{@docRoot}/../specs/security/standard-names.html#cipher-algorithms">
 * standard algorithm name</a> of the cipher is "HPKE".
 * <p>
 * In HPKE, The sender is always initialized with the recipient's public key
 * in encrypt mode, and the recipient is always initialized with its own
 * private key in decrypt mode.
 * <p>
 * The HPKE cipher initialization can include an optional
 * {@code HPKEParameterSpec} object.
 * <p>
 * An {@code HPKEParameterSpec} object can be created in two ways. The
 * {@link #of()} method returns one whose identifiers for the KEM, KDF, and
 * AEAD algorithms used by HPKE will be determined by the type of key provided
 * to the {@code init()} method. The {@link #of(int, int, int)} method returns
 * one whose KEM, KDF, and AEAD algorithms are determined by the numeric
 * identifiers provided. The identifiers provided to this method must not be zero.
 * <p>
 * After an {@code HPKEParameterSpec} object is created, it can call several
 * other methods to generate a new {@code HPKEParameterSpec} object with
 * different features:
 * <ul>
 * <li>
 * An application-supplied information can be provided using the
 * {@link #info(byte[])} method by both parties.
 * <li>
 * If HPKE modes {@code mode_auth} or {@code mode_auth_psk} are used,
 * the keys for authentication must be provided using the
 * {@link #authKey(Key)} method. Precisely, the sender must call this method
 * with its own private key and the recipient must call it with the sender's
 * public key.
 * <li>
 * If HPKE modes {@code mode_psk} or {@code mode_auth_psk} are used,
 * the pre-shared key for authentication and its identifier must be provided
 * using the {@link #psk(SecretKey, byte[])} method by both parties.The key
 * and the identifier must not be empty.
 * <li>
 * In HPKE, a shared secret is negotiated during the KEM step and a key
 * encapsulation message must be transmitted from the sender to the recipient
 * so that the recipient can recover this shared secret. On the sender side,
 * the key encapsulation message can be retrieved using the {@link Cipher#getIV()}
 * method after the cipher is initialized. On the recipient side, the key
 * encapsulation message is provided using the {@link #encapsulation(byte[])}
 * method.
 * </ul>
 * For successful interoperability, both parties need to supply identical
 * {@code info}, {@code psk}, and {@code psk_id} or matching authentication
 * keys if provided.
 * <p>
 * If the sender cipher is initialized without parameters, it assumes a
 * default parameters object is used, which is equivalent to
 * {@code HPKEParameterSpec.of()}. The recipient cipher can also be initialized
 * with a {@code new IvParameterSpec(encap)} object, which is equivalent to
 * {@code HPKEParameterSpec.of().encapsulation(encap)}. In either case, the
 * cipher always work in {@code mode_base} mode with an empty {@code info}.
 *
 * <p>
 * Example:
 * {@snippet lang = java:
 * var g = KeyPairGenerator.getInstance("X25519");
 * var kp = g.generateKeyPair();
 * var sender = Cipher.getInstance("HPKE");
 * var ps = HPKEParameterSpec.of()
 *         .info("this_info".getBytes(StandardCharsets.UTF_8));
 * sender.init(Cipher.ENCRYPT_MODE, kp.getPublic(), ps);
 * var receiver = Cipher.getInstance("HPKE");
 * var pr = HPKEParameterSpec.of()
 *         .info("this_info".getBytes(StandardCharsets.UTF_8))
 *         .encapsulation(sender.getIV());
 * receiver.init(Cipher.DECRYPT_MODE, kp.getPrivate(), pr);
 * var msg = "Hello World".getBytes(StandardCharsets.UTF_8);
 * var ct = sender.doFinal(msg);
 * var pt = receiver.doFinal(ct);
 *
 * assert Arrays.equals(msg, pt);
 * }
 *
 * @implNote
 * In the HPKE implementation in the SunJCE provider included in this JDK
 * implementation, {@code HPKEParameterSpec.of()} chooses the following
 * KEM, KDF, and AEAD algorithms depending on the key used:
 * <ul>
 * <li>For EC key on the secp256r1 curve, the kem_id is 0x10, and the kdf_id is 0x1.
 * <li>For EC key on the secp384r1 curve, the kem_id is 0x11, and the kdf_id is 0x2.
 * <li>For EC key on the secp521r1 curve, the kem_id is 0x12, and the kdf_id is 0x3.
 * <li>For XDH key on the x25519 curve, the kem_id is 0x20, and the kdf_id is 0x1.
 * <li>For XDH key on the x448 curve, the kem_id is 0x21, and the kdf_id is 0x3.
 * </ul>
 * The aead_id is always 0x2. Other keys are not supported.
 * <p>
 * The {@code mode_auth} and {@code mode_auth_psk} modes are not supported.
 */
public final class HPKEParameterSpec implements AlgorithmParameterSpec {

    private final int kem_id; // 0 is determined by key later
    private final int kdf_id; // 0 is determined by key later
    private final int aead_id; // 0 is determined by key later
    private final byte[] info; // never null, can be empty
    private final SecretKey psk; // null if not used
    private final byte[] psk_id; // never null, can be empty
    private final Key kS; // null if not used
    private final byte[] encapsulation; // null if none

    // Note: this constructor does not clone array arguments.
    private HPKEParameterSpec(int kem_id, int kdf_id, int aead_id, byte[] info,
            SecretKey psk, byte[] psk_id, Key kS, byte[] encapsulation) {
        this.kem_id = kem_id;
        this.kdf_id = kdf_id;
        this.aead_id = aead_id;
        this.info = info;
        this.psk = psk;
        this.psk_id = psk_id;
        this.kS = kS;
        this.encapsulation = encapsulation;
    }

    /**
     * A factory method to create an empty {@code HPKEParameterSpec} in
     * {@code mode_base} mode with an empty {@code info}. The KEM, KDF,
     * and AEAD algorithm identifiers are not specified and will be
     * determined by the key used in cipher initialization.
     *
     * @return a new {@code HPKEParameterSpec} object
     */
    public static HPKEParameterSpec of() {
        return new HPKEParameterSpec(0, 0, 0, new byte[0], null, new byte[0], null, null);
    }

    /**
     * A factory method to create a new {@code HPKEParameterSpec} object with
     * specified KEM, KDF, and AEAD algorithm identifiers in {@code mode_base}
     * mode with an empty {@code info}.
     *
     * @param kem_id identifier for KEM, must not be zero
     * @param kdf_id identifier for KDF, must not be zero
     * @param aead_id identifier for AEAD, must not be zero
     * @return a new {@code HPKEParameterSpec} object
     * @throws InvalidAlgorithmParameterException if any of the provided
     *      identifiers is zero
     */
    public static HPKEParameterSpec of(int kem_id, int kdf_id, int aead_id)
            throws InvalidAlgorithmParameterException {
        if (kem_id < 1 || kem_id > 65535
                || kdf_id < 1 || kdf_id > 65535
                || aead_id < 1 || aead_id > 65535) {
            throw new InvalidAlgorithmParameterException();
        }
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id, new byte[0], null, new byte[0], null, null);
    }

    /**
     * Creates a new {@code HPKEParameterSpec} object with a different
     * {@code info} value.
     *
     * @param info application-specific info. Must not be {@code null}.
     *      The contents of the array are copied to protect
     *      against subsequent modification.
     * @return a new {@code HPKEParameterSpec} object
     * @throws NullPointerException if {@code info} is {@code null}
     */
    public HPKEParameterSpec info(byte[] info) {
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                Objects.requireNonNull(info).clone(), psk, psk_id, kS, encapsulation);
    }

    /**
     * Creates a new {@code HPKEParameterSpec} object with different
     * {@code psk} value and {@code psk_id} values.
     *
     * @param psk pre-shared key. Set to {@code null} if no pre-shared key is used.
     * @param psk_id identifier for PSK. Set to empty if no pre-shared key is used.
     *               Must not be {@code null}. The contents of the array are copied
     *               to protect against subsequent modification.
     * @return a new {@code HPKEParameterSpec} object
     * @throws NullPointerException if {@code psk_id} is {@code null}
     * @throws InvalidAlgorithmParameterException if {@code psk} and {@code psk_id} are
     *      not consistent, i.e. {@code psk} is not {@code null} but
     *      {@code psk_id} is empty, or {@code psk} is {@code null} but
     *      {@code psk_id} is not empty.
     */
    public HPKEParameterSpec psk(SecretKey psk, byte[] psk_id)
            throws InvalidAlgorithmParameterException {
        Objects.requireNonNull(psk_id);
        if (psk == null && psk_id.length != 0
                || psk != null && psk_id.length == 0) {
            throw new InvalidAlgorithmParameterException("psk and psk_id do not match");
        }
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                info, psk, psk_id.clone(), kS, encapsulation);
    }

    /**
     * Creates a new {@code HPKEParameterSpec} object with a different
     * key encapsulation message value that will be used by the recipient.
     *
     * @param encapsulation the key encapsulation message. If set to
     *      {@code null}, the previous key encapsulation message is cleared.
     *      The contents of the array are copied to protect against
     *      subsequent modification.
     *
     * @return a new {@code HPKEParameterSpec} object
     */
    public HPKEParameterSpec encapsulation(byte[] encapsulation) {
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                info, psk, psk_id, kS,
                encapsulation == null ? null : encapsulation.clone());
    }

    /**
     * Creates a new {@code HPKEParameterSpec} object with a different
     * authentication key value.
     *
     * @param kS the authentication key. If set to {@code null}, the previous
     *          authentication key is cleared.
     * @return a new {@code HPKEParameterSpec} object
     */
    public HPKEParameterSpec authKey(Key kS) {
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                info, psk, psk_id, kS, encapsulation);
    }

    /**
     * {@return the identifier for KEM, 0 if determined by key type}
     */
    public int kem_id() {
        return kem_id;
    }

    /**
     * {@return the identifier for KDF, 0 if determined by key type}
     */
    public int kdf_id() {
        return kdf_id;
    }

    /**
     * {@return the identifier for AEAD, 0 if determined by key type}
     */
    public int aead_id() {
        return aead_id;
    }

    /**
     * {@return a copy of the application-specific info, empty if none}
     */
    public byte[] info() {
        return info.clone();
    }

    /**
     * {@return pre-shared key, {@code null} if none}
     */
    public SecretKey psk() {
        return psk;
    }

    /**
     * {@return a copy of the identifier for PSK, empty if none}
     */
    public byte[] psk_id() {
        return psk_id.clone();
    }

    /**
     * {@return the key for authentication, {@code null} if none}
     */
    public Key authKey() {
        return kS;
    }

    /**
     * {@return a copy of the key encapsulation message, {@code null} if none}
     */
    public byte[] encapsulation() {
        return encapsulation == null ? null : encapsulation.clone();
    }
}
