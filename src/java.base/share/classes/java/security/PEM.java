/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.KeyUtil;
import sun.security.util.Pem;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * A {@link BinaryEncodable} representing a Privacy-Enhanced Mail (PEM) structure
 * composed of a type identifier, base64-decoded binary content, and any
 * leading data that precedes the PEM header during decoding.
 *
 * <p>The {@code type} is the label in the PEM header, following the
 * {@code BEGIN} keyword and excluding the encapsulation boundaries.
 * Common {@code type} values include, but are not limited to:
 * CERTIFICATE, CERTIFICATE REQUEST, ATTRIBUTE CERTIFICATE, X509 CRL, PKCS7,
 * CMS, PRIVATE KEY, ENCRYPTED PRIVATE KEY, and PUBLIC KEY.
 *
 * <p>Instances of this class are returned by {@link PEMDecoder#decode(String)}
 * and {@link PEMDecoder#decode(InputStream)} when the content cannot be represented
 * as a cryptographic object. To explicitly retrieve a {@code PEM} instance
 * with access to the leading data, use {@link PEMDecoder#decode(String, Class)}
 * or {@link PEMDecoder#decode(InputStream, Class)} with {@code PEM.class} as the
 * type.
 *
 * <p>A {@code PEM} object can be encoded to its textual representation by
 * invoking {@link #toString()} or by using {@link PEMEncoder}.
 *
 * <p>To construct a {@code PEM} instance, both the {@code type} and content parameters
 * ({@code base64Content} or {@code binaryContent}) must be non-{@code null}.
 * If {@code base64Content} is provided, it is decoded to binary and stored.
 * The {@code binaryContent} and, if present, the {@code leadingData} are copied
 * defensively.
 *
 * <p>No validation is performed to ensure that the {@code type} conforms to
 * RFC 7468 or legacy formats, or that the content corresponds to the declared
 * {@code type}.
 *
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @see PEMDecoder
 * @see PEMEncoder
 *
 * @since 27
 */

public final class PEM implements BinaryEncodable {

    private final String type;
    private final byte[] content;
    private byte[] leadingData;

    /**
     * Creates a {@code PEM} instance with the specified type, Base64-encoded
     * content, and leading data. The {@code base64Content} is decoded and
     * stored internally.
     *
     * @param type the PEM type identifier; must not contain PEM syntax labels
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer
     * @param leadingData data that preceded the PEM header during decoding
     *
     * @throws IllegalArgumentException if {@code type} is incorrectly formatted
     *         or if decoding {@code base64Content} fails
     * @throws NullPointerException if any parameter is {@code null}
     * @see Base64#getMimeDecoder()
     */
    public PEM(String type, String base64Content, byte[] leadingData) {
        Objects.requireNonNull(leadingData, "leadingData cannot be null");
        this(type, base64Content);
        this.leadingData = leadingData.clone();
    }

    /**
     * Creates a {@code PEM} instance with the specified type and Base64-encoded
     * content. The {@code base64Content} is decoded and stored internally as binary.
     * {@code leadingData} is set to {@code null}.
     *
     * @param type the PEM type identifier; must not contain PEM syntax labels
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer
     *
     * @throws IllegalArgumentException if {@code type} is incorrectly formatted
     *         or if decoding {@code base64Content} fails
     * @throws NullPointerException if any parameter is {@code null}
     * @see Base64#getMimeDecoder()
     */
    public PEM(String type, String base64Content) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(base64Content, "base64Content cannot be null");

        // The `type` is not checked against any specification. The onus is on
        // the caller.  Only minor formatting checks are done
        if (type.startsWith("-") || type.startsWith("BEGIN ") ||
            type.startsWith("END ")) {
            throw new IllegalArgumentException("PEM syntax labels found. " +
                "Only the PEM type identifier is allowed.");
        }
        // Clone to byte array
        byte[] data = base64Content.getBytes(StandardCharsets.ISO_8859_1);
        try {
            content = Base64.getMimeDecoder().decode(data);
        } finally {
            KeyUtil.clear(data);
        }
        this.type = type;
    }

    /**
     * Creates a {@code PEM} instance with the specified type, binary content,
     * and leading data.
     *
     * @param type the PEM type identifier; must not contain PEM syntax labels
     * @param binaryContent binary-encoded content, such as DER
     * @param leadingData data that preceded the PEM header during decoding
     *
     * @throws IllegalArgumentException if {@code type} is incorrectly formatted
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, byte[] binaryContent, byte[] leadingData) {
        Objects.requireNonNull(leadingData, "leadingData cannot be null");
        this(type, binaryContent);
        this.leadingData = leadingData.clone();
    }

    /**
     * Creates a {@code PEM} instance with the specified type and binary content.
     * {@code leadingData} is set to {@code null}.
     *
     * @param type the PEM type identifier; must not contain PEM syntax labels
     * @param binaryContent binary-encoded content, such as DER
     *
     * @throws IllegalArgumentException if {@code type} is incorrectly formatted
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, byte[] binaryContent) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(binaryContent, "binaryContent cannot be null");

        // The `type` is not checked against any specification. The onus is on
        // the caller.  Only minor formatting checks are done
        if (type.startsWith("-") || type.startsWith("BEGIN ") ||
            type.startsWith("END ")) {
            throw new IllegalArgumentException("PEM syntax labels found. " +
                "Only the PEM type identifier is allowed.");
        }

        content = binaryContent.clone();
        this.type = type;
    }

    /**
     * Returns the PEM type identifier.
     *
     * @return the type of this {@code PEM} object
     */
    public String type() {
        return type;
    }

    /**
     * Returns the leading data that preceded the PEM header during decoding.
     *
     * @return a copy of the leading data, or {@code null} if no leading data
     *         is present
     */
    public byte[] leadingData() {
        return (leadingData != null) ? leadingData.clone() : null;
    }

    /**
     * Returns the binary content.
     *
     * @return a copy of the binary content
     */
    public byte[] content() {
        return content.clone();
    }

    /**
     * Returns the PEM-encoded string representation of this object. The
     * {@code type} is used to generate the header and footer,
     * and the content is Base64-encoded. {@code leadingData} is
     * not included.
     *
     * @return the PEM-formatted string
     */
    @Override
    public String toString() {
        return Pem.pemEncoded(this);
    }
}
