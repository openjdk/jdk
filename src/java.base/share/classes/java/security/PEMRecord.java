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

package java.security;

import java.nio.charset.StandardCharsets;

/**
 * PEMRecord stores input read by a {@link PEMDecoder} or can be used to
 * construct PEM encodings that does not come from a {@link DEREncodable}
 * object.
 *
 * @param id      The PEM header and footer value that identifies the data.
 * @param pem     The Base64 encoded data only in byte[] format
 * @param leadingData Data that came before the PEM header.  This is only useful
 *                for reading from a File or IO stream
 */
public sealed record PEMRecord(String id, String pem, byte[] leadingData)
    implements DEREncodable {

    public static final String CERTIFICATE_REQUEST = "CERTIFICATE REQUEST";
    public static final String NEW_CERTIFICATE_REQUEST = "NEW CERTIFICATE REQUEST";
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String TRUSTED_CERTIFICATE = "TRUSTED CERTIFICATE";
    public static final String X509_CERTIFICATE = "X509 CERTIFICATE";
    public static final String X509_CRL = "X509 CRL";
    public static final String PKCS7 = "PKCS7";
    public static final String CMS = "CMS";
    public static final String ATTRIBUTE_CERTIFICATE = "ATTRIBUTE CERTIFICATE";
    public static final String EC_PARAMETERS = "EC PARAMETERS";
    public static final String PUBLIC_KEY = "PUBLIC KEY";
    public static final String RSA_PUBLIC_KEY = "RSA PUBLIC KEY";
    public static final String RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String DSA_PRIVATE_KEY = "DSA PRIVATE KEY";
    public static final String EC_PRIVATE_KEY = "EC PRIVATE KEY";
    public static final String ENCRYPTED_PRIVATE_KEY = "ENCRYPTED PRIVATE KEY";
    public static final String PRIVATE_KEY = "PRIVATE KEY";

    /**
     * Instantiates a new Pem record.
     *
     * @param id      The PEM header and footer value that identifies the data.
     * @param pem     The Base64 encoded data only in byte[] format
     * @param leadingData Data that came before the PEM header.  This is only useful
     *                for reading from a File or IO stream.
     */
    public PEMRecord(String id, String pem, byte[] leadingData) {
            Objects.requireNonNull(pem);
        if (id.startsWith("-----")) {
            // decode id in the
            this.id = id.substring(11, id.lastIndexOf('-') - 4);
        } else {
            this.id = id;
        }

        this.pem = pem;
        this.leadingData = leadingData;
    }

    /**
     * Instance with no leadingData.
     *
     * @param id  The PEM header and footer value that identifies the data.
     * @param pem The Base64 encoded data only.
     */
    public PEMRecord(String id, String pem) {
        this(id, pem, null);
    }

    /**
     * Get an instance of a PEMRecord.
     *
     * @param id  The PEM header and footer value that identifies the data.
     * @param pem The Base64 encoded data only in byte[] format
     */
    public PEMRecord(String id, byte[] pem) {
        this(id, new String(pem, StandardCharsets.ISO_8859_1), null);
    }
}
