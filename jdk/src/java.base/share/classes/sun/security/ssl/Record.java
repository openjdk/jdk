/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

/**
 * SSL/TLS/DTLS records, as pulled off (and put onto) a TCP stream.  This is
 * the base interface, which defines common information and interfaces
 * used by both Input and Output records.
 *
 * @author David Brownell
 */
interface Record {

    /*
     * There are four record types, which are part of the interface
     * to this level (along with the maximum record size).
     *
     * enum { change_cipher_spec(20), alert(21), handshake(22),
     *      application_data(23), (255) } ContentType;
     */
    static final byte   ct_change_cipher_spec = 20;
    static final byte   ct_alert = 21;
    static final byte   ct_handshake = 22;
    static final byte   ct_application_data = 23;

    static final int    maxMacSize = 48;        // the max supported MAC or
                                                // AEAD tag size
    static final int    maxDataSize = 16384;    // 2^14 bytes of data
    static final int    maxPadding = 256;       // block cipher padding
    static final int    maxIVLength = 16;       // the max supported IV length

    static final int    maxFragmentSize = 18432;    // the max fragment size
                                                    // 2^14 + 2048

    /*
     * System property to enable/disable CBC protection in SSL3/TLS1.
     */
    static final boolean enableCBCProtection =
            Debug.getBooleanProperty("jsse.enableCBCProtection", true);

    /*
     * The overflow values of integers of 8, 16 and 24 bits.
     */
    static final int OVERFLOW_OF_INT08 = (1 << 8);
    static final int OVERFLOW_OF_INT16 = (1 << 16);
    static final int OVERFLOW_OF_INT24 = (1 << 24);

    /**
     * Return a description for the given content type.
     */
    static String contentName(byte contentType) {
        switch (contentType) {
        case ct_change_cipher_spec:
            return "Change Cipher Spec";
        case ct_alert:
            return "Alert";
        case ct_handshake:
            return "Handshake";
        case ct_application_data:
            return "Application Data";
        default:
            return "contentType = " + contentType;
        }
    }

    static boolean isValidContentType(byte contentType) {
        return (contentType == 20) || (contentType == 21) ||
               (contentType == 22) || (contentType == 23);
    }
}
