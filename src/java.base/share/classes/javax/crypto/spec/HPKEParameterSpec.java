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
package javax.crypto.spec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.AsymmetricKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;
import java.util.HexFormat;
import java.util.Objects;

/**
 * This immutable class specifies the set of parameters used with a {@code Cipher} for the
 * <a href="https://www.rfc-editor.org/info/rfc9180">Hybrid Public Key Encryption</a>
 * (HPKE) algorithm. The <a href=
 * "{@docRoot}/../specs/security/standard-names.html#cipher-algorithms">
 * standard algorithm name</a> for the cipher is "HPKE".
 * <p>
 * In HPKE, the sender's {@code Cipher} is always initialized with the
 * recipient's public key in {@linkplain Cipher#ENCRYPT_MODE encrypt mode},
 * while the recipient's {@code Cipher} object is initialized with its own
 * private key in {@linkplain Cipher#DECRYPT_MODE decrypt mode}.
 * <p>
 * An {@code HPKEParameterSpec} object must be provided at HPKE
 * {@linkplain Cipher#init(int, Key, AlgorithmParameterSpec) cipher initialization}.
 * <p>
 * {@link #of(int, int, int)} creates an {@code HPKEParameterSpec} instance with
 * specified KEM, KDF, and AEAD algorithm identifiers.
 * The terms "KEM algorithm identifiers", "KDF algorithm identifiers", and
 * "AEAD algorithm identifiers" refer to their respective numeric values
 * (specifically, {@code kem_id}, {@code kdf_id}, and {@code aead_id}) as
 * defined in <a href="https://www.rfc-editor.org/rfc/rfc9180.html#section-7">Section 7</a>
 * of RFC 9180 and the
 * <a href="https://www.iana.org/assignments/hpke/hpke.xhtml">IANA HPKE page</a>.
 * <p>
 * Once an {@code HPKEParameterSpec} object is created, additional methods
 * are available to generate new {@code HPKEParameterSpec} objects with
 * different features:
 * <ul>
 * <li>
 * Application-supplied information can be provided using the
 * {@link #info(byte[])} method by both sides.
 * <li>
 * To use the HPKE mode {@code mode_auth}, the asymmetric keys for authentication
 * must be provided using the {@link #authKey(AsymmetricKey)} method. Precisely,
 * the sender must call this method with its own private key and the recipient
 * must call it with the sender's public key.
 * <li>
 * To use the HPKE mode {@code mode_psk}, the pre-shared key for authentication
 * and its identifier must be provided using the {@link #psk(SecretKey, byte[])}
 * method by both sides.
 * <li>
 * To use the HPKE mode {@code mode_auth_psk}, both {@link #authKey(AsymmetricKey)}
 * and {@link #psk(SecretKey, byte[])} methods must be called as described above.
 * <li>
 * In HPKE, a shared secret is negotiated during the KEM step and a key
 * encapsulation message must be transmitted from the sender to the recipient
 * so that the recipient can recover this shared secret. On the sender side,
 * after the cipher is initialized, the key encapsulation message can be
 * retrieved directly using the {@link Cipher#getIV()} method. On the recipient
 * side, the key encapsulation message must be provided using the
 * {@link #encapsulation(byte[])} method.
 * </ul>
 * For successful interoperability, both sides need to have identical algorithm
 * identifiers, and supply identical
 * {@code info}, {@code psk}, and {@code psk_id} or matching authentication
 * keys if provided. For details about HPKE modes, refer to
 * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#section-5">Section 5</a>
 * of RFC 9180.
 * <p>
 * If an HPKE cipher is initialized without parameters, an
 * {@code InvalidKeyException} will be thrown.
 * <p>
 * At HPKE cipher initialization, if no HPKE implementation supports the provided
 * key type, an {@code InvalidKeyException} should be thrown. If the provided
 * {@code HPKEParameterSpec} is not supported by any HPKE implementation,
 * an {@code InvalidAlgorithmParameterException} will be thrown. For example:
 * <ul>
 * <li> An algorithm identifier is unsupported or does not match the provided key type.
 * <li> An attempt to use {@code authKey(key)} is made with an incompatible key.
 * <li> An attempt to use {@code authKey(key)} is made but the selected KEM
 *      does not support authentication.
 * </ul>
 * After initialization, both the sender and receiver can encrypt or decrypt
 * multiple plaintexts in sequence using repeated calls to {@code doFinal},
 * optionally preceded by one or more {@code update} calls. Each {@code doFinal}
 * call performs a complete HPKE encryption or decryption operation using a
 * distinct nonce derived from an internal sequence counter, as specified by
 * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#section-5.2">Section 5.2</a>
 * of RFC 9180.
 * <p>
 * As with any AEAD cipher, each {@code doFinal} call on the server side must
 * correspond to exactly one complete ciphertext, and the number and order of
 * calls must match on both sides. Unlike raw AEAD usage, however, an HPKE
 * cipher manages nonce generation internally, and there is no need for the
 * application to reinitialize the cipher with a new IV for each message.
 * This simplifies usage while ensuring nonce uniqueness and preserving AEAD
 * security guarantees.
 * <p>
 * Example:
 * {@snippet lang=java class="PackageSnippets" region="hpke-spec-example"}
 *
 * @implNote This class has defined constants for some of the standard algorithm
 * identifiers. For example, {@link #KEM_DHKEM_P_256_HKDF_SHA256},
 * {@link #KDF_HKDF_SHA256}, and {@link #AEAD_AES_128_GCM}. An implementation
 * may support all, some, or none of the algorithm identifiers defined here.
 * It may also support additional identifiers not listed here, including private
 * or experimental values.
 *
 * @spec https://www.rfc-editor.org/info/rfc9180
 *      RFC 9180: Hybrid Public Key Encryption
 * @spec security/standard-names.html
 *      Java Security Standard Algorithm Names
 * @since 25
 */
public final class HPKEParameterSpec implements AlgorithmParameterSpec {

    /**
     * KEM algorithm identifier for DHKEM(P-256, HKDF-SHA256) as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-encapsulation-mechanism">Section 7.1 of RFC 9180</a>.
     */
    public static final int KEM_DHKEM_P_256_HKDF_SHA256 = 0x10;

    /**
     * KEM algorithm identifier for DHKEM(P-384, HKDF-SHA384) as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-encapsulation-mechanism">Section 7.1 of RFC 9180</a>.
     */
    public static final int KEM_DHKEM_P_384_HKDF_SHA384 = 0x11;

    /**
     * KEM algorithm identifier for DHKEM(P-521, HKDF-SHA512) as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-encapsulation-mechanism">Section 7.1 of RFC 9180</a>.
     */
    public static final int KEM_DHKEM_P_521_HKDF_SHA512 = 0x12;

    /**
     * KEM algorithm identifier for DHKEM(X25519, HKDF-SHA256) as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-encapsulation-mechanism">Section 7.1 of RFC 9180</a>.
     */
    public static final int KEM_DHKEM_X25519_HKDF_SHA256 = 0x20;

    /**
     * KEM algorithm identifier for DHKEM(X448, HKDF-SHA512) as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-encapsulation-mechanism">Section 7.1 of RFC 9180</a>.
     */
    public static final int KEM_DHKEM_X448_HKDF_SHA512 = 0x21;

    /**
     * KDF algorithm identifier for HKDF-SHA256 as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-derivation-functions-kd">Section 7.2 of RFC 9180</a>.
     */
    public static final int KDF_HKDF_SHA256 = 0x1;

    /**
     * KDF algorithm identifier for HKDF-SHA384 as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-derivation-functions-kd">Section 7.2 of RFC 9180</a>.
     */
    public static final int KDF_HKDF_SHA384 = 0x2;

    /**
     * KDF algorithm identifier for HKDF-SHA512 as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-key-derivation-functions-kd">Section 7.2 of RFC 9180</a>.
     */
    public static final int KDF_HKDF_SHA512 = 0x3;

    /**
     * AEAD algorithm identifier for AES-128-GCM as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-authenticated-encryption-wi">Section 7.3 of RFC 9180</a>.
     */
    public static final int AEAD_AES_128_GCM = 0x1;

    /**
     * AEAD algorithm identifier for AES-256-GCM as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-authenticated-encryption-wi">Section 7.3 of RFC 9180</a>.
     */
    public static final int AEAD_AES_256_GCM = 0x2;

    /**
     * AEAD algorithm identifier for ChaCha20Poly1305 as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-authenticated-encryption-wi">Section 7.3 of RFC 9180</a>.
     */
    public static final int AEAD_CHACHA20_POLY1305 = 0x3;

    /**
     * AEAD algorithm identifier for Export-only as defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html#name-authenticated-encryption-wi">Section 7.3 of RFC 9180</a>.
     */
    public static final int EXPORT_ONLY = 0xffff;

    private final int kem_id;
    private final int kdf_id;
    private final int aead_id;
    private final byte[] info; // never null, can be empty
    private final SecretKey psk; // null if not used
    private final byte[] psk_id; // never null, can be empty
    private final AsymmetricKey kS; // null if not used
    private final byte[] encapsulation; // null if none

    // Note: this constructor does not clone array arguments.
    private HPKEParameterSpec(int kem_id, int kdf_id, int aead_id, byte[] info,
            SecretKey psk, byte[] psk_id, AsymmetricKey kS, byte[] encapsulation) {
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
     * A factory method to create a new {@code HPKEParameterSpec} object with
     * specified KEM, KDF, and AEAD algorithm identifiers in {@code mode_base}
     * mode with an empty {@code info}.
     *
     * @param kem_id identifier for KEM, must be between 0 and 65535 (inclusive)
     * @param kdf_id identifier for KDF, must be between 0 and 65535 (inclusive)
     * @param aead_id identifier for AEAD, must be between 0 and 65535 (inclusive)
     * @return a new {@code HPKEParameterSpec} object
     * @throws IllegalArgumentException if any input value
     *      is out of range (must be between 0 and 65535, inclusive).
     */
    public static HPKEParameterSpec of(int kem_id, int kdf_id, int aead_id) {
        if (kem_id < 0 || kem_id > 65535) {
            throw new IllegalArgumentException("Invalid kem_id: " + kem_id);
        }
        if (kdf_id < 0 || kdf_id > 65535) {
            throw new IllegalArgumentException("Invalid kdf_id: " + kdf_id);
        }
        if (aead_id < 0 || aead_id > 65535) {
            throw new IllegalArgumentException("Invalid aead_id: " + aead_id);
        }
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                new byte[0], null, new byte[0], null, null);
    }

    /**
     * Creates a new {@code HPKEParameterSpec} object with a different
     * {@code info} value.
     * <p>
     * For interoperability, RFC 9180 Section 7.2.1 recommends limiting
     * this value to a maximum of 64 bytes.
     *
     * @param info application-supplied information. Must not be {@code null}.
     *      If set to empty, the previous info is cleared.
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
     * <p>
     * For interoperability, RFC 9180 Section 7.2.1 recommends limiting both
     * values to a maximum of 64 bytes.
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
    public HPKEParameterSpec authKey(AsymmetricKey kS) {
        return new HPKEParameterSpec(kem_id, kdf_id, aead_id,
                info, psk, psk_id, kS, encapsulation);
    }

    /**
     * {@return the identifier for KEM }
     */
    public int kem_id() {
        return kem_id;
    }

    /**
     * {@return the identifier for KDF }
     */
    public int kdf_id() {
        return kdf_id;
    }

    /**
     * {@return the identifier for AEAD }
     */
    public int aead_id() {
        return aead_id;
    }

    /**
     * {@return a copy of the application-supplied information, empty if none}
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
    public AsymmetricKey authKey() {
        return kS;
    }

    /**
     * {@return a copy of the key encapsulation message, {@code null} if none}
     */
    public byte[] encapsulation() {
        return encapsulation == null ? null : encapsulation.clone();
    }

    @Override
    public String toString() {
        return "HPKEParameterSpec{" +
                "kem_id=" + kem_id +
                ", kdf_id=" + kdf_id +
                ", aead_id=" + aead_id +
                ", info=" + bytesToString(info) +
                ", " + (psk == null
                        ? (kS == null ? "mode_base" : "mode_auth")
                        : (kS == null ? "mode_psk" : "mode_auth_psk")) + "}";
    }

    // Returns a human-readable format of a byte array.
    private static String bytesToString(byte[] input) {
        if (input.length == 0) {
            return "(empty)";
        } else {
            for (byte b : input) {
                if (b < 0x20 || b > 0x7E || b == '"') {
                    // Non-ASCII and control characters are not friendly to human
                    // eyes. `"` also excluded to avoid character escaping.
                    return HexFormat.of().formatHex(input);
                }
            }
            // Simple readable string
            return HexFormat.of().formatHex(input)
                    + " (\"" + new String(input, StandardCharsets.US_ASCII) + "\")";
        }
    }
}
