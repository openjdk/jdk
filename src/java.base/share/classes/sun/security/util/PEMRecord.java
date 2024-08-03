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

package sun.security.util;

import java.nio.charset.StandardCharsets;

/**
 * A Record for PEM
 *
 * @param id      The PEM header and footer value that identifies the data.
 * @param pem     The Base64 encoded data only in byte[] format
 */
public record PEMRecord(String id, String pem) {

    public static final String PUBLIC_KEY = "PUBLIC KEY";
    public static final String ENCRYPTED_PRIVATE_KEY = "ENCRYPTED PRIVATE KEY";
    public static final String PRIVATE_KEY = "PRIVATE KEY";
    public static final String RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String X509_CERTIFICATE = "X509 CERTIFICATE";
    public static final String X509_CRL = "X509 CRL";

    /**
     * Get an instance of a PEMRecord.
     *
     * @param id  The PEM header and footer value that identifies the data.
     * @param pem The Base64 encoded data only in byte[] format
     */
    public PEMRecord(String id, byte[] pem) {
        this(id, new String(pem, StandardCharsets.ISO_8859_1));
    }

    /**
     * Instantiates a new Pem record.
     *
     * @param id      The PEM header and footer value that identifies the data.
     * @param pem     The Base64 encoded data only in byte[] format
     */
    public PEMRecord(String id, String pem) {
        if (id.startsWith("-----")) {
            // decode id in the
            this.id = id.substring(11, id.lastIndexOf('-') - 4);
        } else {
            this.id = id;
        }

        this.pem = pem;
    }
}
