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

package javax.crypto.spec;

import javax.crypto.SecretKey;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parameters for the combined Extract, Expand, or Extract-then-Expand
 * operations of the HMAC-based Key Derivation Function (HKDF). The HKDF
 * function is defined in <a href="http://tools.ietf.org/html/rfc5869">RFC
 * 5869</a>.
 * <p>
 * In the Extract and Extract-then-Expand cases, users may call the {@code
 * addIKM} and/or {@code addSalt} methods repeatedly (and chain these calls).
 * This provides for use-cases where a portion of the input keying material
 * (IKM) resides in a non-extractable {@code SecretKey} and the whole IKM
 * cannot be provided as a single object. The same feature is available for
 * salts.
 * <p>
 * The above feature is particularly useful for "labeled" HKDF Extract used in
 * TLS 1.3 and HPKE, where the IKM consists of concatenated components, which
 * may include both byte arrays and (possibly non-extractable) secret keys.
 * <p>
 * Examples:
 * {@snippet lang = java:
 * // this usage depicts the initialization of an HKDF-Extract AlgorithmParameterSpec
 * AlgorithmParameterSpec derivationSpec =
 *             HKDFParameterSpec.ofExtract()
 *                              .addIKM(label)
 *                              .addIKM(ikm)
 *                              .addSalt(salt).extractOnly();
 *}
 * {@snippet lang = java:
 * // this usage depicts the initialization of an HKDF-Expand AlgorithmParameterSpec
 * AlgorithmParameterSpec derivationSpec =
 *             HKDFParameterSpec.expandOnly(prk, info, 32);
 *}
 * {@snippet lang = java:
 * // this usage depicts the initialization of an HKDF-ExtractExpand AlgorithmParameterSpec
 * AlgorithmParameterSpec derivationSpec =
 *             HKDFParameterSpec.ofExtract()
 *                              .addIKM(ikm)
 *                              .addSalt(salt).thenExpand(info, 32);
 *}
 *
 * @spec https://www.rfc-editor.org/info/rfc5869
 *      RFC 5869: HMAC-based Extract-and-Expand Key Derivation Function (HKDF)
 * @see javax.crypto.KDF
 * @since 25
 */
public interface HKDFParameterSpec extends AlgorithmParameterSpec {

    /**
     * This {@code Builder} builds {@code Extract} and {@code ExtractThenExpand}
     * objects.
     * <p>
     * The {@code Builder} is initialized via the {@code ofExtract} method of
     * {@code HKDFParameterSpec}. As stated in the class description,
     * {@code addIKM} and/or {@code addSalt} may be called as needed. Finally,
     * an object is "built" by calling either {@code extractOnly} or
     * {@code thenExpand} for {@code Extract} and {@code ExtractThenExpand}
     * use-cases respectively. Note that the {@code Builder} is not
     * thread-safe.
     */
    final class Builder {

        private List<SecretKey> ikms = new ArrayList<>();
        private List<SecretKey> salts = new ArrayList<>();

        private Builder() {}

        /**
         * Builds an {@code Extract} object from the current state of the
         * {@code Builder}.
         *
         * @return an immutable {@code Extract} object
         */
        public Extract extractOnly() {
            return new Extract(ikms, salts);
        }

        /**
         * Builds an {@code ExtractThenExpand} object from the current state of
         * the {@code Builder}.
         *
         * @implNote HKDF implementations will enforce that the length
         *         is not greater than 255 * HMAC length. HKDF implementations
         *         will also enforce that a {code null} info value is treated as
         *         zero-length byte array.
         *
         * @param info
         *         the optional context and application specific information
         *         (may be {@code null}); the byte array is cloned to prevent
         *         subsequent modification
         * @param length
         *         the length of the output keying material (must be greater
         *         than 0)
         *
         * @return an immutable {@code ExtractThenExpand} object
         *
         * @throws IllegalArgumentException
         *         if {@code length} is not greater than 0
         */
        public ExtractThenExpand thenExpand(byte[] info, int length) {
            return new ExtractThenExpand(
                    extractOnly(), info,
                    length);
        }

        /**
         * Adds input keying material (IKM) to the builder.
         * <p>
         * Users may call {@code addIKM} multiple times when the input keying
         * material value is to be assembled piece-meal or if part of the IKM is
         * to be supplied by a hardware crypto device. The {@code ikms()}
         * method of the {@code Extract} or {@code ExtractThenExpand} object
         * that is subsequently built returns the assembled input keying
         * material as a list of {@code SecretKey} objects.
         *
         * @param ikm
         *         the input keying material (IKM) value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *         if the {@code ikm} argument is null
         */
        public Builder addIKM(SecretKey ikm) {
            Objects.requireNonNull(ikm, "ikm must not be null");
            ikms.add(ikm);
            return this;
        }

        /**
         * Adds input keying material (IKM) to the builder. Note that an
         * {@code ikm} byte array of length zero will be discarded.
         * <p>
         * Users may call {@code addIKM} multiple times when the input keying
         * material value is to be assembled piece-meal or if part of the IKM is
         * to be supplied by a hardware crypto device. The {@code ikms()}
         * method of the {@code Extract} or {@code ExtractThenExpand} object
         * that is subsequently built returns the assembled input keying
         * material as a list of {@code SecretKey} objects.
         *
         * @param ikm
         *         the input keying material (IKM) value; the {@code ikm}
         *         byte array will be converted to a {@code SecretKeySpec},
         *         which means that the byte array will be cloned inside the
         *         {@code SecretKeySpec} constructor
         *
         * @return this builder
         *
         * @throws NullPointerException
         *         if the {@code ikm} argument is null
         */
        public Builder addIKM(byte[] ikm) {
            Objects.requireNonNull(ikm, "ikm must not be null");
            if (ikm.length != 0) {
                return addIKM(new SecretKeySpec(ikm, "Generic"));
            } else {
                return this;
            }
        }

        /**
         * Adds a salt to the builder.
         * <p>
         * Users may call {@code addSalt} multiple times when the salt value is
         * to be assembled piece-meal or if part of the salt is to be supplied
         * by a hardware crypto device. The {@code salts()} method of the
         * {@code Extract} or {@code ExtractThenExpand} object that is
         * subsequently built returns the assembled salt as a list of
         * {@code SecretKey} objects.
         *
         * @param salt
         *         the salt value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *         if the {@code salt} is null
         */
        public Builder addSalt(SecretKey salt) {
            Objects.requireNonNull(salt, "salt must not be null");
            salts.add(salt);
            return this;
        }

        /**
         * Adds a salt to the builder. Note that a {@code salt} byte array of
         * length zero will be discarded.
         * <p>
         * Users may call {@code addSalt} multiple times when the salt value is
         * to be assembled piece-meal or if part of the salt is to be supplied
         * by a hardware crypto device. The {@code salts()} method of the
         * {@code Extract} or {@code ExtractThenExpand} object that is
         * subsequently built returns the assembled salt as a list of
         * {@code SecretKey} objects.
         *
         * @param salt
         *         the salt value; the {@code salt} byte array will be
         *         converted to a {@code SecretKeySpec}, which means that the
         *         byte array will be cloned inside the {@code SecretKeySpec}
         *         constructor
         *
         * @return this builder
         *
         * @throws NullPointerException
         *         if the {@code salt} is null
         */
        public Builder addSalt(byte[] salt) {
            Objects.requireNonNull(salt, "salt must not be null");
            if (salt.length != 0) {
                return addSalt(new SecretKeySpec(salt, "Generic"));
            } else {
                return this;
            }
        }
    }

    /**
     * Returns a {@code Builder} for building {@code Extract} and
     * {@code ExtractThenExpand} objects.
     *
     * @return a new {@code Builder}
     */
    static Builder ofExtract() {
        return new Builder();
    }

    /**
     * Creates an {@code Expand} object.
     *
     * @implNote HKDF implementations will enforce that the length is
     *         not greater than 255 * HMAC length. Implementations will also
     *         enforce that the prk argument is at least as many bytes as the
     *         HMAC length. Implementations will also enforce that a {code null}
     *         info value is treated as zero-length byte array.
     *
     * @param prk
     *         the pseudorandom key (PRK); must not be {@code null}
     * @param info
     *         the optional context and application specific information (may be
     *         {@code null}); the byte array is cloned to prevent subsequent
     *         modification
     * @param length
     *         the length of the output keying material (must be greater than
     *         0)
     *
     * @return an {@code Expand} object
     *
     * @throws NullPointerException
     *         if the {@code prk} argument is {@code null}
     * @throws IllegalArgumentException
     *         if {@code length} is not greater than 0
     */
    static Expand expandOnly(SecretKey prk, byte[] info, int length) {
        if (prk == null) {
            throw new NullPointerException("prk must not be null");
        }
        return new Expand(prk, info, length);
    }

    /**
     * Defines the input parameters of an Extract operation as defined in <a
     * href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    final class Extract implements HKDFParameterSpec {

        // HKDF-Extract(salt, IKM) -> PRK
        private final List<SecretKey> ikms;
        private final List<SecretKey> salts;

        private Extract(List<SecretKey> ikms, List<SecretKey> salts) {
            this.ikms = List.copyOf(ikms);
            this.salts = List.copyOf(salts);
        }

        /**
         * Returns an unmodifiable {@code List} of input keying material values
         * in the order they were added. Returns an empty list if there are no
         * input keying material values.
         * <p>
         * Input keying material values added by {@link Builder#addIKM(byte[])}
         * are converted to a {@code SecretKeySpec} object. Empty arrays are
         * discarded.
         *
         * @implNote An HKDF implementation should concatenate the input
         *         keying materials into a single value to be used in
         *         HKDF-Extract.
         *
         * @return the unmodifiable {@code List} of input keying material
         *         values
         */
        public List<SecretKey> ikms() {
            return ikms;
        }

        /**
         * Returns an unmodifiable {@code List} of salt values in the order they
         * were added. Returns an empty list if there are no salt values.
         * <p>
         * Salt values added by {@link Builder#addSalt(byte[])} are converted to
         * a {@code SecretKeySpec} object. Empty arrays are discarded.
         *
         * @implNote An HKDF implementation should concatenate the salts
         *         into a single value to be used in HKDF-Extract.
         *
         * @return the unmodifiable {@code List} of salt values
         */
        public List<SecretKey> salts() {
            return salts;
        }

    }

    /**
     * Defines the input parameters of an Expand operation as defined in <a
     * href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    final class Expand implements HKDFParameterSpec {

        // HKDF-Expand(PRK, info, L) -> OKM
        private final SecretKey prk;
        private final byte[] info;
        private final int length;

        /**
         * Constructor that may be used to initialize an {@code Expand} object
         *
         * @param prk
         *         the pseudorandom key (PRK); in the case of
         *         {@code ExtractThenExpand}, the {@code prk} argument may be
         *         {@null} since the output of extract phase is used
         * @param info
         *         the optional context and application specific information
         *         (may be {@code null}); the byte array is cloned to prevent
         *         subsequent modification
         * @param length
         *         the length of the output keying material
         *
         * @throws IllegalArgumentException
         *         if {@code length} not greater than 0
         */
        private Expand(SecretKey prk, byte[] info, int length) {
            // a null prk argument could be indicative of ExtractThenExpand
            this.prk = prk;
            this.info = (info == null) ? null : info.clone();
            if (!(length > 0)) {
                throw new IllegalArgumentException("length must be > 0");
            }
            this.length = length;
        }

        /**
         * Returns the pseudorandom key (PRK).
         *
         * @return the pseudorandom key
         */
        public SecretKey prk() {
            return prk;
        }

        /**
         * Returns the optional context and application specific information.
         *
         * @return a clone of the optional context and application specific
         *         information, or {@code null} if not specified
         */
        public byte[] info() {
            return (info == null) ? null : info.clone();
        }

        /**
         * Returns the length of the output keying material.
         *
         * @return the length of the output keying material
         */
        public int length() {
            return length;
        }

    }

    /**
     * Defines the input parameters of an Extract-then-Expand operation as
     * defined in <a href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    final class ExtractThenExpand implements HKDFParameterSpec {
        private final Extract ext;
        private final Expand exp;

        /**
         * Constructor that may be used to initialize an
         * {@code ExtractThenExpand} object
         *
         * @param ext
         *         a pre-generated {@code Extract}
         * @param info
         *         the optional context and application specific information
         *         (may be {@code null}); the byte array is cloned to prevent
         *         subsequent modification
         * @param length
         *         the length of the output keying material
         *
         * @throws IllegalArgumentException
         *         if {@code length} is not greater than 0
         */
        private ExtractThenExpand(Extract ext, byte[] info, int length) {
            Objects.requireNonNull(ext, "Extract object must not be null");
            this.ext = ext;
            // - null prk argument is ok here (it's a signal)
            // - {@code Expand} constructor can deal with a null info
            // - length is checked in {@code Expand} constructor
            this.exp = new Expand(null, info, length);
        }

        /**
         * Returns an unmodifiable {@code List} of input keying material values
         * in the order they were added. Returns an empty list if there are no
         * input keying material values.
         * <p>
         * Input keying material values added by {@link Builder#addIKM(byte[])}
         * are converted to a {@code SecretKeySpec} object. Empty arrays are
         * discarded.
         *
         * @implNote An HKDF implementation should concatenate the input
         *         keying materials into a single value to be used in the
         *         HKDF-Extract phase.
         *
         * @return the unmodifiable {@code List} of input keying material
         *         values
         */
        public List<SecretKey> ikms() {
            return ext.ikms();
        }

        /**
         * Returns an unmodifiable {@code List} of salt values in the order they
         * were added. Returns an empty list if there are no salt values.
         * <p>
         * Salt values added by {@link Builder#addSalt(byte[])} are converted to
         * a {@code SecretKeySpec} object. Empty arrays are discarded.
         *
         * @implNote An HKDF implementation should concatenate the salts
         *         into a single value to be used in the HKDF-Extract phase.
         *
         * @return the unmodifiable {@code List} of salt values
         *
         */
        public List<SecretKey> salts() {
            return ext.salts();
        }

        /**
         * Returns the optional context and application specific information.
         *
         * @return a clone of the optional context and application specific
         *         information, or {@code null} if not specified
         */
        public byte[] info() {
            return exp.info();
        }

        /**
         * Returns the length of the output keying material.
         *
         * @return the length of the output keying material
         */
        public int length() {
            return exp.length();
        }

    }

}
