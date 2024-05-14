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

import jdk.internal.javac.PreviewFeature;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameters for the combined Extract-Only, Expand-Only, or Extract-then-Expand
 * operations of the HMAC-based Key Derivation Function (HKDF). The HKDF
 * function is defined in <a href="http://tools.ietf.org/html/rfc5869">RFC
 * 5869</a>.
 *
 * @since 23
 */
@PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
public interface HKDFParameterSpec extends KDFParameterSpec {

    /**
     * This builder helps with the mutation required by the {@code Extract}
     * scenario.
     */
    @PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
    final class Builder {

        List<SecretKey> ikms = new ArrayList<>();
        List<SecretKey> salts = new ArrayList<>();

        Builder() {}

        /**
         * Creates a {@code Builder} for an {@code Extract}.
         *
         * @return a {@code Builder} to mutate
         */
        private Builder createBuilder() {
            return this;
        }

        /**
         * Builds an {@code Extract}.
         *
         * @return an immutable {@code Extract}
         */
        public Extract extractOnly() {
            return new Extract(ikms, salts);
        }

        /**
         * Builds an {@code ExtractThenExpand}.
         *
         * @param info
         *     the optional context and application specific information (may be
         *     {@code null}; the byte[] is copied to prevent subsequent
         *     modification
         * @param length
         *     the length of the output key material
         *
         * @return an {@code ExtractThenExpand}
         *
         * @throws IllegalArgumentException
         *     if {@code length} is not &gt; 0
         */
        public ExtractThenExpand thenExpand(byte[] info, int length) {
            return new ExtractThenExpand(
                extractOnly(), info,
                length);
        }

        /**
         * {@code addIKM} may be called when the input key material value is
         * to be assembled piece-meal or if part of the IKM is to be supplied by
         * a hardware crypto device. This method appends to the existing list of
         * values or creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the IKM
         * but the actual value of the IKM is not yet available.
         *
         * @param ikm
         *     the input key material value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *     if the {@code ikm} is null
         */
        public Builder addIKM(SecretKey ikm) {
            if (ikm != null) {
                ikms.add(ikm);
            } else {
                throw new NullPointerException("ikm must not be null");
            }
            return this;
        }

        /**
         * {@code addIKM} may be called when the input key material value is
         * to be assembled piece-meal or if part of the IKM is to be supplied by
         * a hardware crypto device. This method appends to the existing list of
         * values or creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the IKM
         * but the actual value of the IKM is not yet available.
         *
         * @param ikm
         *     the input key material value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *     if the {@code ikm} is null
         */
        public Builder addIKM(byte[] ikm) {
            if(ikm == null) {
                throw new NullPointerException("ikm must not be null or empty");
            }
            if (ikm.length != 0) {
                return addIKM(new SecretKeySpec(ikm, "Generic"));
            } else {
                return this;
            }
        }

        /**
         * {@code addSalt} may be called when the salt value is to be assembled
         * piece-meal or if part of the salt is to be supplied by a hardware
         * crypto device. This method appends to the existing list of values or
         * creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the salt
         * but the actual value of the salt is not yet available.
         *
         * @param salt
         *     the salt value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *     if the {@code salt} is null
         */
        public Builder addSalt(SecretKey salt) {
            if (salt != null) {
                salts.add(salt);
            } else {
                throw new NullPointerException("salt must not be null");
            }
            return this;
        }

        /**
         * {@code addSalt} may be called when the salt value is to be assembled
         * piece-meal or if part of the salt is to be supplied by a hardware
         * crypto device. This method appends to the existing list of values or
         * creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the salt
         * but the actual value of the salt is not yet available.
         *
         * @param salt
         *     the salt value
         *
         * @return this builder
         *
         * @throws NullPointerException
         *     if the {@code salt} is null
         */
        public Builder addSalt(byte[] salt) {
            if(salt == null) {
                throw new NullPointerException(
                    "salt must not be null or empty");
            }
            if (salt.length != 0) {
                return addSalt(new SecretKeySpec(salt, "Generic"));
            } else {
                return this;
            }
        }
    }

    /**
     * Returns a builder for building {@code Extract}-Only and {@code ExtractThenExpand} objects.
     * <p>
     * Note: one or more of the methods {@code addIKM} or {@code addSalt} should
     * be called next, before calling build methods, such as
     * {@code Builder.extractOnly()}
     *
     * @return a {@code Builder} to mutate
     */
    static Builder buildExtract() {
        return new Builder().createBuilder();
    }

    /**
     * Defines the input parameters of an {@code Expand}-Only object
     *
     * @param prk
     *     the pseudorandom key; must not be {@code null} in the Expand-Only
     *     case
     * @param info
     *     the optional context and application specific information (may be
     *     {@code null}); the byte[] is copied to prevent subsequent
     *     modification
     * @param length
     *     the length of the output key material (must be &gt; 0 and &lt; 255 * HMAC
     *     length)
     *
     * @return a new {@code Expand} object
     *
     * @throws NullPointerException
     *     if {@code prk} is {@code null}
     * @throws IllegalArgumentException
     *     if {@code length} is not > 0
     */
    static Expand expandOnly(SecretKey prk, byte[] info, int length) {
        if (prk == null) {
            throw new NullPointerException("prk must not be null");
        }
        return new Expand(prk, info, length);
    }

    /**
     * Defines the input parameters of an Extract-Only operation as defined in <a
     * href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    @PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
    final class Extract implements HKDFParameterSpec {

        // HKDF-Extract(salt, IKM) -> PRK
        private final List<SecretKey> ikms;
        private final List<SecretKey> salts;

        private Extract() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        private Extract(List<SecretKey> ikms, List<SecretKey> salts) {
            this.ikms = List.copyOf(ikms);
            this.salts = List.copyOf(salts);
        }

        /**
         * Returns an unmodifiable {@code List} of input key material
         * values.
         *
         * @return the unmodifiable {@code List} of input key material values
         */
        public List<SecretKey> ikms() {
            return ikms;
        }

        /**
         * Returns an unmodifiable {@code List} of salt values.
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
    @PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
    final class Expand implements HKDFParameterSpec {

        // HKDF-Expand(PRK, info, L) -> OKM
        private final SecretKey prk;
        private final byte[] info;
        private final int length;

        /**
         * Constructor that may be used to initialize an {@code Expand} object
         *
         * @param prk
         *     the pseudorandom key; may be {@code null}
         * @param info
         *     the optional context and application specific information (may be
         *     {@code null}); the byte[] is copied to prevent subsequent
         *     modification
         * @param length
         *     the length of the output key material (must be > 0 and < 255 *
         *     HMAC length)
         *
         * @throws IllegalArgumentException
         *     if {@code length} not > 0
         */
        private Expand(SecretKey prk, byte[] info, int length) {
            // a null prk could be indicative of ExtractThenExpand
            this.prk = prk;
            this.info = (info == null) ? null : info.clone();
            if (!(length > 0)) {
                throw new IllegalArgumentException("length must be > 0");
            }
            this.length = length;
        }

        /**
         * Returns the pseudorandom key.
         *
         * @return the pseudorandom key
         */
        public SecretKey prk() {
            return prk;
        }

        /**
         * Returns the optional context and application specific information.
         *
         * @return a copy of the optional context and application specific
         *     information, or {@code null} if not specified
         */
        public byte[] info() {
            return (info == null) ? null : info.clone();
        }

        /**
         * Returns the length of the output key material.
         *
         * @return the length of the output key material
         */
        public int length() {
            return length;
        }

    }

    /**
     * Defines the input parameters of an ExtractThenExpand operation as defined in
     * <a href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    @PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
    final class ExtractThenExpand implements HKDFParameterSpec {
        private final Extract ext;
        private final Expand exp;

        /**
         * Constructor that may be used to initialize an
         * {@code ExtractThenExpand} object
         * <p>
         * Note: {@code addIKMValue} and {@code addSaltValue} may be called
         * afterward to supply additional values, if desired
         *
         * @param ext
         *     a pre-generated {@code Extract}
         * @param info
         *     the optional context and application specific information (may be
         *     {@code null}); the byte[] is copied to prevent subsequent
         *     modification
         * @param length
         *     the length of the output key material (must be > 0 and < 255 *
         *     HMAC length)
         *
         * @throws IllegalArgumentException
         *     if {@code length} is not > 0
         */
        private ExtractThenExpand(Extract ext, byte[] info, int length) {
            // null-checked previously
            this.ext = ext;
            // - null prk is ok here (it's a signal)
            // - {@code Expand} constructor can deal with a null info
            // - length is checked in {@code Expand} constructor
            this.exp = new Expand(null, info, length);
        }

        /**
         * Returns an unmodifiable {@code List} of input key material
         * values.
         *
         * @return the input key material values
         */
        public List<SecretKey> ikms() {
            return ext.ikms();
        }

        /**
         * Returns an unmodifiable {@code List} of salt values.
         *
         * @return the salt values
         */
        public List<SecretKey> salts() {
            return ext.salts();
        }

        /**
         * Returns the optional context and application specific information.
         *
         * @return a copy of the optional context and application specific
         *     information, or {@code null} if not specified
         */
        public byte[] info() {
            return exp.info();
        }

        /**
         * Returns the length of the output key material.
         *
         * @return the length of the output key material
         */
        public int length() {
            return exp.length();
        }

    }

}
