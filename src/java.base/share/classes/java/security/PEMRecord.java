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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {@code PEMRecord} is a {@link DEREncodable} that represents Privacy-Enhanced
 * Mail (PEM) data by its type and Base64 form.  {@link PEMDecoder} and
 * {@link PEMEncoder} use {@code PEMRecord} when representing the data as a
 * Java API cryptographic object is not desired or there is no other
 * {@code DEREncodable} for the type.
 *
 * <p>Types with Java API representation, such as a {@link PrivateKey},
 * can return a {@code PEMRecord} when used with
 * {@linkplain PEMDecoder#decode(String, Class)}. Using {@code PEMRecord} can
 * be helpful when generating a representation is not desired or when used
 * with {@code leadingData}.  {@code leadingData} can depend on which
 * decode() methods is used.
 *
 * <p>{@code PEMRecord} may have a null {@code type} and {@code pem} when
 * {@code PEMDecoder.decode()} methods encounter only non-PEM data and has
 * reached the end of the stream. If there is PEM data, {@code type} and
 * {@code pem} will both be non-null. {@code leadingData} may be null if the
 * input data only contains PEM data. All values can never be null.
 *
 * <p> During the instantiation of this record, there is no validation for the
 * {@code type} or {@code pem}.
 *
 * <p>This class is immutable and thread-safe.  {@code leadingData} is not
 * defensively copied.
 *
 * @param type The type identifier in the PEM header.  For a public key,
 * {@code type} would be "PUBLIC KEY".
 * @param pem Any data between the PEM header and footer.
 * @param leadingData Any non-PEM data read during the decoding process
 * before the PEM header. This can be useful when reading metadata that
 * accompanies PEM data.
 *
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @see PEMDecoder
 * @see PEMEncoder
 */
@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public record PEMRecord(String type, String pem, byte[] leadingData)
    implements DEREncodable {

    /**
     * Creates a {@code PEMRecord} instance with the given parameters.
     *
     * @param type the type identifier in the PEM header and footer, or
     *             {@code null} if there is no PEM data.
     * @param pem the data between the PEM header and footer.
     * @param leadingData Any non-PEM data read during the decoding process
     *                    before the PEM header.  This value maybe {@code null}.
     * @throws IllegalArgumentException on incorrect input values
     */
    public PEMRecord(String type, String pem, byte[] leadingData) {
        if (type == null && pem == null && leadingData == null) {
            throw new IllegalArgumentException("All values may not be null.");
        }

        if (type == null && pem != null || type != null && pem == null) {
            throw new IllegalArgumentException("\"type\" and \"pem\" must be" +
                " both null or non-null");
        }

        // With no validity checking on `type`, the constructor accept anything
        // including lowercase.  The onus is on the caller.
        if (type != null && (type.startsWith("-") || type.contains("BEGIN") ||
            type.contains("END") || type.endsWith("-"))) {
            throw new IllegalArgumentException("Only the PEM type identifier " +
                "is allowed");
        }

        this.type = type;
        this.pem = pem;
        this.leadingData = leadingData;
    }

    /**
     * Creates a {@code PEMRecord} instance with a given {@code type} and {@code pem}
     * data in String form.  {@code leadingData} is set to null.
     *
     * @param type the type identifier in the PEM header and footer, or {@code null} if there is no PEM data.
     * @param pem The data between the PEM header and footer.
     *
     * @see #PEMRecord(String, String, byte[])
     */
    public PEMRecord(String type, String pem) {
        this(type, pem, null);
    }

    /**
     * Creates a {@code PEMRecord} instance with a given String {@code type} and
     * byte array {@code pem}.  {@code leadingData} is set to null.
     *
     * @param type the type identifier in the PEM header and footer, or {@code null} if there is no PEM data.
     * @param pem the data between the PEM header and footer.
     *
     * @see #PEMRecord(String, String, byte[])
     */
    public PEMRecord(String type, byte[] pem) {
        this(type, new String(pem, StandardCharsets.ISO_8859_1), null);
    }

    /**
     * Returns the binary encoding from the Base64 data contained in
     * {@code pem}.
     *
     * @throws IllegalArgumentException if {@code pem} cannot be decoded.
     * @return Returns a new array of the binary encoding each time this
     * method is called, or null if {@code pem} is null.
     */
    public byte[] getEncoded() {
        return (pem == null ? null : Base64.getMimeDecoder().decode(pem));
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
