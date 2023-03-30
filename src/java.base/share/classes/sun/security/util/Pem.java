/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import sun.security.x509.AlgorithmId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A utility class for PEM format encoding.
 */
public class Pem {

    /**
     * Public Key PEM header & footer
     */
    public static final String PUBHEADER = "-----BEGIN PUBLIC KEY-----";
    public static final String PUBFOOTER = "-----END PUBLIC KEY-----";

    /**
     * Private Key PEM header & footer
     */
    public static final String PKCS8HEADER = "-----BEGIN PRIVATE KEY-----";
    public static final String PKCS8FOOTER = "-----END PRIVATE KEY-----";

    /**
     * Encrypted Private Key PEM header & footer
     */
    public static final String PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----";
    public static final String PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----";

    /**
     * Certificate PEM header & footer
     */
    public static final String CERTHEADER = "-----BEGIN CERTIFICATE-----";
    public static final String CERTFOOTER = "-----END CERTIFICATE-----";

    /**
     * CRL PEM header & footer
     */
    public static final String CRLHEADER = "-----BEGIN CRL-----";
    public static final String CRLFOOTER = "-----END CRL-----";

    /**
     * Decodes a PEM-encoded block.
     *
     * @param input the input string, according to RFC 1421, can only contain
     *              characters in the base-64 alphabet and whitespaces.
     * @return the decoded bytes
     * @throws java.io.IOException if input is invalid
     */
    public static byte[] decode(String input) throws IOException {
        byte[] src = input.replaceAll("\\s+", "")
                .getBytes(StandardCharsets.ISO_8859_1);
        try {
            return Base64.getDecoder().decode(src);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }


    // Sorta hack to get the right OID for PBBS2
    public static ObjectIdentifier getPBEID(String algorithm) throws IOException {
        try {
            if (algorithm.contains("AES")) {
                return AlgorithmId.get("PBES2").getOID();
            } else {
                return AlgorithmId.get(algorithm).getOID();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
