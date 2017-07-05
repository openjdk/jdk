/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.nio.zipfs;

import java.nio.ByteBuffer;

/**
 *
 * @author Xueming Shen
 */

class ZipConstants {
    /*
     * Compression methods
     */
    static final int METHOD_STORED     = 0;
    static final int METHOD_DEFLATED   = 8;
    static final int METHOD_DEFLATED64 = 9;
    static final int METHOD_BZIP2      = 12;
    static final int METHOD_LZMA       = 14;
    static final int METHOD_LZ77       = 19;

    /*
     * General purpose big flag
     */
    static final int FLAG_ENCRYPTED  = 0x01;
    static final int FLAG_DATADESCR  = 0x08;    // crc, size and csize in dd
    static final int FLAG_EFS        = 0x800;   // If this bit is set the filename and
                                                // comment fields for this file must be
                                                // encoded using UTF-8.
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

    /*
     * ZIP64 constants
     */
    static final long ZIP64_ENDSIG = 0x06064b50L;  // "PK\006\006"
    static final long ZIP64_LOCSIG = 0x07064b50L;  // "PK\006\007"
    static final int  ZIP64_ENDHDR = 56;           // ZIP64 end header size
    static final int  ZIP64_LOCHDR = 20;           // ZIP64 end loc header size
    static final int  ZIP64_EXTHDR = 24;           // EXT header size
    static final int  ZIP64_EXTID  = 0x0001;       // Extra field Zip64 header ID

    static final int  ZIP64_MINVAL32 = 0xFFFF;
    static final long ZIP64_MINVAL = 0xFFFFFFFFL;

    /*
     * Zip64 End of central directory (END) header field offsets
     */
    static final int  ZIP64_ENDLEN = 4;       // size of zip64 end of central dir
    static final int  ZIP64_ENDVEM = 12;      // version made by
    static final int  ZIP64_ENDVER = 14;      // version needed to extract
    static final int  ZIP64_ENDNMD = 16;      // number of this disk
    static final int  ZIP64_ENDDSK = 20;      // disk number of start
    static final int  ZIP64_ENDTOD = 24;      // total number of entries on this disk
    static final int  ZIP64_ENDTOT = 32;      // total number of entries
    static final int  ZIP64_ENDSIZ = 40;      // central directory size in bytes
    static final int  ZIP64_ENDOFF = 48;      // offset of first CEN header
    static final int  ZIP64_ENDEXT = 56;      // zip64 extensible data sector

    /*
     * Zip64 End of central directory locator field offsets
     */
    static final int  ZIP64_LOCDSK = 4;       // disk number start
    static final int  ZIP64_LOCOFF = 8;       // offset of zip64 end
    static final int  ZIP64_LOCTOT = 16;      // total number of disks

    /*
     * Zip64 Extra local (EXT) header field offsets
     */
    static final int  ZIP64_EXTCRC = 4;       // uncompressed file crc-32 value
    static final int  ZIP64_EXTSIZ = 8;       // compressed size, 8-byte
    static final int  ZIP64_EXTLEN = 16;      // uncompressed size, 8-byte

    /*
     * Extra field header ID
     */
    static final int  EXTID_ZIP64 = 0x0001;      // ZIP64
    static final int  EXTID_NTFS  = 0x000a;      // NTFS
    static final int  EXTID_UNIX  = 0x000d;      // UNIX


    /*
     * fields access methods
     */
    ///////////////////////////////////////////////////////
    static final int CH(byte[] b, int n) {
       return b[n] & 0xff;
    }

    static final int SH(byte[] b, int n) {
        return (b[n] & 0xff) | ((b[n + 1] & 0xff) << 8);
    }

    static final long LG(byte[] b, int n) {
        return ((SH(b, n)) | (SH(b, n + 2) << 16)) & 0xffffffffL;
    }

    static final long LL(byte[] b, int n) {
        return (LG(b, n)) | (LG(b, n + 4) << 32);
    }

    static final long GETSIG(byte[] b) {
        return LG(b, 0);
    }

    // local file (LOC) header fields
    static final long LOCSIG(byte[] b) { return LG(b, 0); } // signature
    static final int  LOCVER(byte[] b) { return SH(b, 4); } // version needed to extract
    static final int  LOCFLG(byte[] b) { return SH(b, 6); } // general purpose bit flags
    static final int  LOCHOW(byte[] b) { return SH(b, 8); } // compression method
    static final long LOCTIM(byte[] b) { return LG(b, 10);} // modification time
    static final long LOCCRC(byte[] b) { return LG(b, 14);} // crc of uncompressed data
    static final long LOCSIZ(byte[] b) { return LG(b, 18);} // compressed data size
    static final long LOCLEN(byte[] b) { return LG(b, 22);} // uncompressed data size
    static final int  LOCNAM(byte[] b) { return SH(b, 26);} // filename length
    static final int  LOCEXT(byte[] b) { return SH(b, 28);} // extra field length

    // extra local (EXT) header fields
    static final long EXTCRC(byte[] b) { return LG(b, 4);}  // crc of uncompressed data
    static final long EXTSIZ(byte[] b) { return LG(b, 8);}  // compressed size
    static final long EXTLEN(byte[] b) { return LG(b, 12);} // uncompressed size

    // end of central directory header (END) fields
    static final int  ENDSUB(byte[] b) { return SH(b, 8); }  // number of entries on this disk
    static final int  ENDTOT(byte[] b) { return SH(b, 10);}  // total number of entries
    static final long ENDSIZ(byte[] b) { return LG(b, 12);}  // central directory size
    static final long ENDOFF(byte[] b) { return LG(b, 16);}  // central directory offset
    static final int  ENDCOM(byte[] b) { return SH(b, 20);}  // size of zip file comment
    static final int  ENDCOM(byte[] b, int off) { return SH(b, off + 20);}

    // zip64 end of central directory recoder fields
    static final long ZIP64_ENDTOD(byte[] b) { return LL(b, 24);}  // total number of entries on disk
    static final long ZIP64_ENDTOT(byte[] b) { return LL(b, 32);}  // total number of entries
    static final long ZIP64_ENDSIZ(byte[] b) { return LL(b, 40);}  // central directory size
    static final long ZIP64_ENDOFF(byte[] b) { return LL(b, 48);}  // central directory offset
    static final long ZIP64_LOCOFF(byte[] b) { return LL(b, 8);}   // zip64 end offset

    //////////////////////////////////////////
    static final int CH(ByteBuffer b, int pos) {
       return b.get(pos) & 0xff;
    }
    static final int SH(ByteBuffer b, int pos) {
        return b.getShort(pos) & 0xffff;
    }
    static final long LG(ByteBuffer b, int pos) {
        return b.getInt(pos) & 0xffffffffL;
    }

    // central directory header (END) fields
    static final long CENSIG(ByteBuffer b, int pos) { return LG(b, pos + 0); }
    static final int  CENVEM(ByteBuffer b, int pos) { return SH(b, pos + 4); }
    static final int  CENVER(ByteBuffer b, int pos) { return SH(b, pos + 6); }
    static final int  CENFLG(ByteBuffer b, int pos) { return SH(b, pos + 8); }
    static final int  CENHOW(ByteBuffer b, int pos) { return SH(b, pos + 10);}
    static final long CENTIM(ByteBuffer b, int pos) { return LG(b, pos + 12);}
    static final long CENCRC(ByteBuffer b, int pos) { return LG(b, pos + 16);}
    static final long CENSIZ(ByteBuffer b, int pos) { return LG(b, pos + 20);}
    static final long CENLEN(ByteBuffer b, int pos) { return LG(b, pos + 24);}
    static final int  CENNAM(ByteBuffer b, int pos) { return SH(b, pos + 28);}
    static final int  CENEXT(ByteBuffer b, int pos) { return SH(b, pos + 30);}
    static final int  CENCOM(ByteBuffer b, int pos) { return SH(b, pos + 32);}
    static final int  CENDSK(ByteBuffer b, int pos) { return SH(b, pos + 34);}
    static final int  CENATT(ByteBuffer b, int pos) { return SH(b, pos + 36);}
    static final long CENATX(ByteBuffer b, int pos) { return LG(b, pos + 38);}
    static final long CENOFF(ByteBuffer b, int pos) { return LG(b, pos + 42);}

    /* The END header is followed by a variable length comment of size < 64k. */
    static final long END_MAXLEN = 0xFFFF + ENDHDR;
    static final int READBLOCKSZ = 128;
}
