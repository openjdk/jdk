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

package java.security;

import jdk.internal.javac.PreviewFeature;

import sun.security.util.Pem;

import java.util.Objects;

/**
 * {@code PEMRecord} is a {@link DEREncodable} that represents Privacy-Enhanced
 * Mail (PEM) data by its type and Base64 form.  {@link PEMDecoder} and
 * {@link PEMEncoder} use {@code PEMRecord} when representing the data as a
 * cryptographic object is not desired or the type has no
 * {@code DEREncodable}.
 *
 * <p> {@code type} and {@code content} may not be {@code null}.
 * {@code leadingData} may be null if no non-PEM data preceded PEM header
 * during decoding.  {@code leadingData} may be useful for reading metadata
 * that accompanies PEM data.
 *
 * <p> No validation is performed during instantiation to ensure that
 * {@code type} conforms to {@code RFC 7468}, that {@code content} is valid
 * Base64, or that {@code content} matches the {@code type}.
 * {@code leadingData} is not defensively copied and does not return a
 * clone when {@linkplain #leadingData()} is called.
 *
 * @param type the type identifier in the PEM header without PEM syntax labels.
 *           For a public key, {@code type} would be "PUBLIC KEY".
 * @param content the Base64-encoded data, excluding the PEM header and footer
 * @param leadingData any non-PEM data preceding the PEM header when decoding.
 *
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @see PEMDecoder
 * @see PEMEncoder
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public record PEMRecord(String type, String content, byte[] leadingData)
    implements DEREncodable {

    /**
     * Creates a {@code PEMRecord} instance with the given parameters.
     *
     * @param type the type identifier
     * @param content the Base64-encoded data, excluding the PEM header and
     *               footer
     * @param leadingData any non-PEM data read during the decoding process
     *                    before the PEM header.  This value maybe {@code null}.
     * @throws IllegalArgumentException if {@code type} is incorrectly
     * formatted.
     * @throws NullPointerException if {@code type} and/or {@code content} are
     * {@code null}.
     */
    public PEMRecord {
        Objects.requireNonNull(type, "\"type\" cannot be null.");
        Objects.requireNonNull(content, "\"content\" cannot be null.");

        // With no validity checking on `type`, the constructor accept anything
        // including lowercase.  The onus is on the caller.
        if (type.startsWith("-") || type.startsWith("BEGIN ") ||
            type.startsWith("END ")) {
            throw new IllegalArgumentException("PEM syntax labels found.  " +
                "Only the PEM type identifier is allowed");
        }

    }

    /**
     * Creates a {@code PEMRecord} instance with a given {@code type} and
     * {@code content} data in String form.  {@code leadingData} is set to null.
     *
     * @param type the PEM type identifier
     * @param content the Base64-encoded data, excluding the PEM header and
     *               footer
     * @throws IllegalArgumentException if {@code type} is incorrectly
     * formatted.
     * @throws NullPointerException if {@code type} and/or {@code content} are
     * {@code null}.
     */
    public PEMRecord(String type, String content) {
        this(type, content, null);
    }

    /**
     * Returns the type and Base64 encoding in PEM format.  {@code leadingData}
     * is not returned by this method.
     */
    @Override
    public String toString() {
        return Pem.pemEncoded(this);
    }
}
