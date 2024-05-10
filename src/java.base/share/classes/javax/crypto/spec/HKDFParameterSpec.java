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

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parameters for the combined Extract-Only, Expand-Only, or Extract-then-Expand
 * operations of the HMAC-based Key Derivation Function (HKDF). The HKDF
 * function is defined in <a href="http://tools.ietf.org/html/rfc5869">RFC
 * 5869</a>.
 *
 * @since 23
 */
public interface HKDFParameterSpec extends KDFParameterSpec {

    /**
     * This builder helps with the mutation required by the {@code Extract}
     * scenario.
     */
    final class Builder {

        Extract extract = null;
        List<SecretKey> ikms = new ArrayList<>();
        List<SecretKey> salts = new ArrayList<>();

        Builder() {}

        /**
         * Creates a {@code Builder} for an {@code Extract}
         *
         * @return a {@code Builder} to mutate
         */
        private Builder createExtract() {
            extract = new Extract();
            return this;
        }

        /**
         * Akin to a {@code Builder.build()} method for an {@code Extract}
         *
         * @return an immutable {@code Extract}
         */
        public Extract extractOnly() {
            if (this.ikms.isEmpty() && this.salts.isEmpty()) {
                throw new IllegalStateException(
                    "this `Builder` must have either at least one IKM value, "
                    + "at least one salt "
                    + "value, or values for both before calling `extractOnly`");
            } else {
                this.extract = new Extract(List.copyOf(ikms),
                                           List.copyOf(salts));
                return this.extract;
            }
        }

        /**
         * Akin to a {@code Builder.build()} method for an
         * {@code ExtractExpand}
         *
         * @param info
         *     the info
         * @param length
         *     the length
         *
         * @return an {@code ExtractExpand}
         */
        public ExtractExpand andExpand(byte[] info, int length) {
            if (extract == null) {
                throw new IllegalStateException(
                    "`thenExpand` can only be called on a `Builder` when "
                    + "`ofTypeExtract` has "
                    + "been called");
            }
            return extractExpand(
                new Extract(List.copyOf(ikms), List.copyOf(salts)), info,
                length);
        }

        /**
         * {@code addIKM} may be called when the ikm value is to be assembled
         * piece-meal or if part of the IKM is to be supplied by a hardware
         * crypto device. This method appends to the existing list of values or
         * creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the IKM
         * but the actual value of the IKM is not yet available.
         *
         * @param ikm
         *     the ikm value (null values will not be added)
         *
         * @return a new {@code Extract} object
         */
        public Builder addIKM(SecretKey ikm) {
            if (ikm != null) {
                ikms.add(ikm);
            }
            return this;
        }

        /**
         * {@code addIKM} may be called when the ikm value is to be assembled
         * piece-meal or if part of the IKM is to be supplied by a hardware
         * crypto device. This method appends to the existing list of values or
         * creates a new list if there are none yet.
         * <p>
         * This supports the use-case where a label can be applied to the IKM
         * but the actual value of the IKM is not yet available.
         *
         * @param ikm
         *     the ikm value (null or empty values will not be added)
         *
         * @return a new {@code Extract} object
         */
        public Builder addIKM(byte[] ikm) {
            if (ikm != null && ikm.length != 0) {
                return addIKM(new SecretKeySpec(ikm, "RAW"));
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
         *     the salt value (null values will not be added)
         *
         * @return a new {@code Extract} object
         */
        public Builder addSalt(SecretKey salt) {
            if (salt != null) {
                salts.add(salt);
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
         *     the salt value (null or empty values will not be added)
         *
         * @return a new {@code Extract} object
         */
        public Builder addSalt(byte[] salt) {
            if (salt != null && salt.length != 0) {
                return addSalt(new SecretKeySpec(salt, "RAW"));
            } else {
                return this;
            }
        }
    }

    /**
     * Static helper-method that may be used to initialize a {@code Builder}
     * with an empty {@code Extract}
     * <p>
     * Note: one or more of the methods {@code addIKM} or {@code addSalt} should
     * be called next, before calling build methods, such as
     * {@code Builder.extractOnly()}
     *
     * @return a {@code Builder} to mutate
     */
    static Builder extract() {
        return new Builder().createExtract();
    }

    /**
     * Static helper-method that may be used to initialize an {@code Expand}
     * object
     *
     * @param prk
     *     the PRK (may be null)
     * @param info
     *     the info (may be null)
     * @param length
     *     the length
     *
     * @return a new {@code Expand} object
     */
    static Expand expand(SecretKey prk, byte[] info, int length) {
        return new Expand(prk, info, length);
    }

    /**
     * Static helper-method that may be used to initialize an
     * {@code ExtractExpand} object
     * <p>
     * Note: one or more of the methods {@code addIKM} or {@code addSalt} should
     * be called on the {@code Extract} parameter, before calling this method,
     * since {@code ExtractExpand} is immutable
     *
     * @param ext
     *     a pre-generated {@code Extract}
     * @param info
     *     the info (may be {@code null})
     * @param length
     *     the length
     *
     * @return a new {@code ExtractExpand} object
     */
    static ExtractExpand extractExpand(Extract ext, byte[] info, int length) {
        return new ExtractExpand(ext, info, length);
    }

    /**
     * Defines the input parameters of an Extract operation as defined in <a
     * href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    final class Extract implements HKDFParameterSpec {

        // HKDF-Extract(salt, IKM) -> PRK
        private final List<SecretKey> ikms;
        private final List<SecretKey> salts;

        private Extract() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        private Extract(List<SecretKey> ikms, List<SecretKey> salts) {
            this.ikms = ikms;
            this.salts = salts;
        }

        /**
         * Gets the unmodifiable {@code List} of IKM values
         *
         * @return the unmodifiable {@code List} of IKM values
         */
        public List<SecretKey> ikms() {
            return Collections.unmodifiableList(ikms);
        }

        /**
         * Gets the unmodifiable {@code List} of salt values
         *
         * @return the unmodifiable {@code List} of salt values
         */
        public List<SecretKey> salts() {
            return Collections.unmodifiableList(salts);
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
         *     the PRK; may be {@code null}
         * @param info
         *     the info
         * @param length
         *     the length
         */
        private Expand(SecretKey prk, byte[] info, int length) {
            // a null prk could be indicative of ExtractExpand
            this.prk = prk;
            this.info = (info == null) ? null : info.clone();
            if (length < 1) {
                throw new IllegalArgumentException("length must be >= 1");
            }
            this.length = length;
        }

        /**
         * gets the PRK
         *
         * @return the PRK value
         */
        public SecretKey prk() {
            return prk;
        }

        /**
         * gets the info
         *
         * @return the info value
         */
        public byte[] info() {
            return (info == null)? null : info.clone();
        }

        /**
         * gets the length
         *
         * @return the length value
         */
        public int length() {
            return length;
        }

    }

    /**
     * Defines the input parameters of an ExtractExpand operation as defined in
     * <a href="http://tools.ietf.org/html/rfc5869">RFC 5869</a>.
     */
    final class ExtractExpand implements HKDFParameterSpec {
        private final Extract ext;
        private final Expand exp;

        /**
         * Constructor that may be used to initialize an {@code ExtractExpand}
         * object
         * <p>
         * Note: {@code addIKMValue} and {@code addSaltValue} may be called
         * afterward to supply additional values, if desired
         *
         * @param ext
         *     a pre-generated {@code Extract}
         * @param info
         *     the info (may be {@code null})
         * @param length
         *     the length
         */
        private ExtractExpand(Extract ext, byte[] info, int length) {
            if (ext == null) {
                throw new IllegalArgumentException(
                    "ext (the Extract parameter) must not be null");
            } else {
                this.ext = ext;
            }
            if (length < 1) {
                throw new IllegalArgumentException("length must be >= 1");
            }
            this.exp = expand(null, info, length);
        }

        /**
         * Gets the {@code List} of IKM values
         *
         * @return the IKM values
         */
        public List<SecretKey> ikms() {
            return ext.ikms();
        }

        /**
         * Gets the {@code List} of salt values
         *
         * @return the salt values
         */
        public List<SecretKey> salts() {
            return ext.salts();
        }

        /**
         * Gets the info
         *
         * @return the info value
         */
        public byte[] info() {
            return exp.info();
        }

        /**
         * Gets the length
         *
         * @return the length value
         */
        public int length() {
            return exp.length();
        }

    }

}
