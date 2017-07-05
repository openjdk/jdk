/*
 * Copyright (c) 1996, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * SSL/TLS records, as pulled off (and put onto) a TCP stream.  This is
 * the base interface, which defines common information and interfaces
 * used by both Input and Output records.
 *
 * @author David Brownell
 */
interface Record {
    /*
     * There are four SSL record types, which are part of the interface
     * to this level (along with the maximum record size)
     *
     * enum { change_cipher_spec(20), alert(21), handshake(22),
     *      application_data(23), (255) } ContentType;
     */
    static final byte   ct_change_cipher_spec = 20;
    static final byte   ct_alert = 21;
    static final byte   ct_handshake = 22;
    static final byte   ct_application_data = 23;

    static final int            headerSize = 5;         // SSLv3 record header
    static final int            maxExpansion = 1024;    // for bad compression
    static final int            trailerSize = 20;       // SHA1 hash size
    static final int            maxDataSize = 16384;    // 2^14 bytes of data
    static final int            maxPadding = 256;       // block cipher padding

    /*
     * SSL has a maximum record size.  It's header, (compressed) data,
     * padding, and a trailer for the MAC.
     * Some compression algorithms have rare cases where they expand the data.
     * As we don't support compression at this time, leave that out.
     */
    static final int            maxRecordSize =
                                      headerSize        // header
                                    + maxDataSize       // data
                                    + maxPadding        // padding
                                    + trailerSize;      // MAC

    /*
     * The maximum large record size.
     *
     * Some SSL/TLS implementations support large fragment upto 2^15 bytes,
     * such as Microsoft. We support large incoming fragments.
     *
     * The maximum large record size is defined as maxRecordSize plus 2^14,
     * this is the amount OpenSSL is using.
     */
    static final int            maxLargeRecordSize =
                maxRecordSize   // Max size with a conforming implemenation
              + maxDataSize;    // extra 2^14 bytes for large data packets.


    /*
     * Maximum record size for alert and change cipher spec records.
     * They only contain 2 and 1 bytes of data, respectively.
     * Allocate a smaller array.
     */
    static final int maxAlertRecordSize =
                        headerSize + 2 + maxPadding + trailerSize;

}
