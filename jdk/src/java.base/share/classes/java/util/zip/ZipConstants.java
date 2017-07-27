/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

/*
 * This interface defines the constants that are used by the classes
 * which manipulate ZIP files.
 *
 * @author      David Connelly
 * @since 1.1
 */
interface ZipConstants {
    /*
     * Header signatures
     */
    static long LOCSIG = 0x04034b50L;   // "PK\003\004"
    static long EXTSIG = 0x08074b50L;   // "PK\007\008"
    static long CENSIG = 0x02014b50L;   // "PK\001\002"
    static long ENDSIG = 0x06054b50L;   // "PK\005\006"

    /*
     * Header sizes in bytes (including signatures)
     */
    static final int LOCHDR = 30;       // LOC header size
    static final int EXTHDR = 16;       // EXT header size
    static final int CENHDR = 46;       // CEN header size
    static final int ENDHDR = 22;       // END header size

    /*
     * Local file (LOC) header field offsets
     */
    static final int LOCVER = 4;        // version needed to extract
    static final int LOCFLG = 6;        // general purpose bit flag
    static final int LOCHOW = 8;        // compression method
    static final int LOCTIM = 10;       // modification time
    static final int LOCCRC = 14;       // uncompressed file crc-32 value
    static final int LOCSIZ = 18;       // compressed size
    static final int LOCLEN = 22;       // uncompressed size
    static final int LOCNAM = 26;       // filename length
    static final int LOCEXT = 28;       // extra field length

    /*
     * Extra local (EXT) header field offsets
     */
    static final int EXTCRC = 4;        // uncompressed file crc-32 value
    static final int EXTSIZ = 8;        // compressed size
    static final int EXTLEN = 12;       // uncompressed size

    /*
     * Central directory (CEN) header field offsets
     */
    static final int CENVEM = 4;        // version made by
    static final int CENVER = 6;        // version needed to extract
    static final int CENFLG = 8;        // encrypt, decrypt flags
    static final int CENHOW = 10;       // compression method
    static final int CENTIM = 12;       // modification time
    static final int CENCRC = 16;       // uncompressed file crc-32 value
    static final int CENSIZ = 20;       // compressed size
    static final int CENLEN = 24;       // uncompressed size
    static final int CENNAM = 28;       // filename length
    static final int CENEXT = 30;       // extra field length
    static final int CENCOM = 32;       // comment length
    static final int CENDSK = 34;       // disk number start
    static final int CENATT = 36;       // internal file attributes
    static final int CENATX = 38;       // external file attributes
    static final int CENOFF = 42;       // LOC header offset

    /*
     * End of central directory (END) header field offsets
     */
    static final int ENDSUB = 8;        // number of entries on this disk
    static final int ENDTOT = 10;       // total number of entries
    static final int ENDSIZ = 12;       // central directory size in bytes
    static final int ENDOFF = 16;       // offset of first CEN header
    static final int ENDCOM = 20;       // zip file comment length
}
