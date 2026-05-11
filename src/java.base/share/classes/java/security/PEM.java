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

import jdk.internal.ref.CleanerFactory;
import sun.security.util.KeyUtil;
import sun.security.util.Pem;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * A {@link BinaryEncodable} representing a Privacy-Enhanced Mail (PEM) structure
 * composed of a type identifier, Base64-encoded content, and optional
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
 * <p>To construct a {@code PEM} instance, {@code type} and
 * {@code base64Content} must be non-{@code null}. For constructors that accept
 * {@code leadingData}, it must also be non-{@code null}.
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
     * string content, and leading data.
     *
     * @param type the PEM type identifier; must not contain PEM encapsulation
     *        syntax
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer
     * @param leadingData data that preceded the PEM header during decoding.
     *        This array is defensively copied.
     *
     * @throws IllegalArgumentException if {@code type} contains PEM
     *         encapsulation syntax
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, String base64Content, byte[] leadingData) {
        Objects.requireNonNull(base64Content, "base64Content cannot be null");
        this(type, base64Content.getBytes(StandardCharsets.ISO_8859_1),
            leadingData);
    }

    /**
     * Creates a {@code PEM} instance with the specified type and Base64-encoded
     * string content.
     *
     * @param type the PEM type identifier; must not contain PEM encapsulation
     *        syntax
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer
     * @throws IllegalArgumentException if {@code type} contains PEM
     *         encapsulation syntax
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, String base64Content) {
        Objects.requireNonNull(base64Content, "base64Content cannot be null");
        this(type, base64Content.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Creates a {@code PEM} instance with the specified type and Base64-encoded
     * byte array content.
     *
     * @param type the PEM type identifier; must not contain PEM encapsulation
     *        syntax
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer. This array is defensively copied.
     * @param leadingData data that preceded the PEM header during decoding.
     *        This array is defensively copied.
     *
     * @throws IllegalArgumentException if {@code type} contains PEM
     *         encapsulation syntax
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, byte[] base64Content, byte[] leadingData) {
        this(type, base64Content);
        this.leadingData = Objects.requireNonNull(
            leadingData, "leadingData cannot be null").clone();
        final var l = this.leadingData;
        CleanerFactory.cleaner().register(this, () -> KeyUtil.clear(l));
    }

    /**
     * Creates a {@code PEM} instance with the specified type and Base64-encoded
     * byte array content.
     *
     * @param type the PEM type identifier; must not contain PEM encapsulation
     *        syntax
     * @param base64Content the Base64-encoded content, excluding the PEM header
     *        and footer. This array is defensively copied.
     * @throws IllegalArgumentException if {@code type} contains PEM
     *         encapsulation syntax
     * @throws NullPointerException if any parameter is {@code null}
     */
    public PEM(String type, byte[] base64Content) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(base64Content, "base64Content cannot be null");

        // The `type` is not checked against any specification. The onus is on
        // the caller.  Only minor formatting checks are done
        if (type.startsWith("-") || type.startsWith("BEGIN ") ||
            type.startsWith("END ")) {
            throw new IllegalArgumentException("PEM syntax labels found. " +
                "Only the PEM type identifier is allowed.");
        }

        content = base64Content.clone();
        this.type = type;
        final var c = content;
        CleanerFactory.cleaner().register(this, () -> KeyUtil.clear(c));
    }

    /**
     * Returns the PEM type identifier.
     *
     * @return the PEM type identifier
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
     * Returns the Base64-encoded content.
     *
     * @return a copy of the Base64-encoded content byte array
     */
    public byte[] content() {
        return content.clone();
    }

    /**
     * Returns the Base64-decoded content as a byte array, using
     * {@link Base64#getMimeDecoder()}.
     *
     * @return a Base64-decoded byte array
     * @throws IllegalArgumentException if decoding fails
     */
    public byte[] decode() {
        return Base64.getMimeDecoder().decode(content);
    }

    /**
     * Returns a PEM string representation of this object, using {@code type}
     * for the header and footer lines and {@code content} for the Base64 body.
     *
     * @return the PEM-formatted string
     */
    @Override
    public String toString() {
        return Pem.pemEncodedToString(this);
    }
}
