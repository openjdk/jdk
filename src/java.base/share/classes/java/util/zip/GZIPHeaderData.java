/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * This class implements the header of GZIP file which contains members defined
 * in the RFC 1952 specification
 * @author      Lin Zang
 * @since 17
 *
 */
public class GZIPHeaderData {

    /**
     * CRC-32 of header data.
     */
    private CRC32 crc = new CRC32();

    /**
     * GZIP header magic number.
     */
    private static final int GZIP_MAGIC = 0x8b1f;


    // Represents the default "unknown" value for OS header, per RFC-1952
    private static final byte OS_UNKNOWN = (byte) 255;

    /**
     * File header flags.
     *
     * Per RFC 1952, the header fields contains serveral members based on flag byte:
     * This flag byte is divided into individual bits as follows:
     *
     *                bit 0   FTEXT        file probably ascii text
     *                bit 1   FHCRC        CRC16 for the gzip header
     *                bit 2   FEXTRA       extra field present
     *                bit 3   FNAME        original file name present
     *                bit 4   FCOMMENT     file comment present
     *                bit 5   reserved
     *                bit 6   reserved
     *                bit 7   reserved
     */
    private byte flags;

    private static final int FTEXT      = 1;    // Extra text
    private static final int FHCRC      = 2;    // Header CRC
    private static final int FEXTRA     = 4;    // Extra field
    private static final int FNAME      = 8;    // File name
    private static final int FCOMMENT   = 16;   // File comment

    /*
     * GZIP Header bytes.
     */
    private ByteArrayOutputStream baos;

    /**
     * Creates GZIP member header with optional header members and compress method.
     * Per RFC-1952:
     *    The filename and fileComment member should be String in
     *          LATIN-1 (ISO-8859-1) character set.
     *
     *    A compliant compressor must produce files with correct ID1,
     *          ID2, CM, CRC32, and ISIZE, but may set all the other fields in
     *          the fixed-length part of the header to default values (255 for
     *          OS, 0 for all others).  The compressor must set all reserved
     *          bits to zero.
     *
     *   The XFL (extra Flags) is set to zero and OS is set to {@code OS_UNKNOWN (=255)}.
     *   The FTEXT flag is set to zero and MTIME is filled with 0.
     *
     * @param cm                    compress method,
     *                              per RFC-1952, 0-7 are reserved, 8 denotes "deflate".
     *                              usually passed by {@code Deflater.DEFLATED}
     * @param generateHeaderCRC
     *        if {@code true} the header will include the CRC16 of the header.
     * @param extraFieldBytes
     *        The byte array of extra filed, the generated header would calculate the
     *        byte[] size and fill it before the byte[] in header.
     * @param filename              the original file name in ISO-8859-1 character set
     * @param fileComment           the file comment in ISO_8859-1 character set.
     * @throws ZipException         If extra field size is out of range.
     *                              Or if extra filed data layout is incorrect.
     * @throws IllegalArgumentException     If compress method is not 0-8.
     */
     public GZIPHeaderData(byte cm,
                           boolean generateHeaderCRC,
                           byte[] extraFieldBytes,
                           String filename,
                           String fileComment) throws IOException {
        baos = new ByteArrayOutputStream();
        byte flags = 0;
        if (cm > 8 || cm < 0) {
            throw new IllegalArgumentException("Illegal compress method");
        }

        // set flags.
        if (generateHeaderCRC == true) {
            flags |= FHCRC;
        }
        if (extraFieldBytes != null && extraFieldBytes.length != 0) {
            flags |= FEXTRA;
        }
        if (filename != null && filename.length() != 0) {
            flags |= FNAME;
        }
        if (fileComment != null && fileComment.length() != 0) {
            flags |= FCOMMENT;
        }

        // the head of header.
        byte [] head = new byte[] {
                (byte) GZIP_MAGIC,        // Magic number (short)
                (byte)(GZIP_MAGIC >> 8),  // Magic number (short)
                cm,                       // Compression method (CM)
                flags,                    // Flags (FLG)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Extra flags (XFLG)
                OS_UNKNOWN                // Operating system (OS)
        };
        // write head.
        baos.write(head);
        if (generateHeaderCRC) {
            crc.update(head, 0, head.length);
        }

        // write extra field.
        if ((flags & FEXTRA) == FEXTRA) {
            /* extra field, per RFC-1952:
             *     +---+---+=================================+
             *     | XLEN  |...XLEN bytes of "extra field"...|
             *     +---+---+=================================+
             */
            int xlen = extraFieldBytes.length;
            if (xlen > 0xffff) {
                throw new ZipException("extra field size out of range");
            }
            // write XLEN.
            baos.write((byte)(xlen & 0xff));
            baos.write((byte)((xlen >> 8) & 0xff));
            /*
             * Here do verification before write data.
             */
            if (!verifyExtraFieldLayout(extraFieldBytes)) {
                throw new ZipException("incorrect extra field format");
            };
            baos.write(extraFieldBytes);
            if (generateHeaderCRC) {
                crc.update((byte)(xlen & 0xff));
                crc.update(((byte)(xlen >> 8) & 0xff));
                crc.update(extraFieldBytes, 0, extraFieldBytes.length);
            }
        }
        // write file name.
        if ((flags & FNAME) == FNAME) {
            /*
             *    +=========================================+
             *    |...original file name, zero-terminated...|
             *    +=========================================+
             */
            byte[] filenameBytes = filename.getBytes("ISO-8859-1");
            baos.write(filenameBytes);
            baos.write(0);
            if (generateHeaderCRC) {
                crc.update(filenameBytes, 0, filenameBytes.length);
                crc.update(0);
            }
        }
        // write file comment.
        if ((flags & FCOMMENT) == FCOMMENT) {
            /*
             *    +===================================+
             *    |...file comment, zero-terminated...|
             *    +===================================+
             */
            byte[] fcommBytes = fileComment.getBytes("ISO-8859-1");
            baos.write(fcommBytes);
            baos.write(0);
            if (generateHeaderCRC) {
                crc.update(fcommBytes, 0, fcommBytes.length);
                crc.update(0);
            }
        }
        // write header crc16.
        if ((flags & FHCRC) == FHCRC) {
            int crc16 = (int)crc.getValue() & 0xffff;
            baos.write((byte)(crc16 & 0xff));
            baos.write((byte)(crc16 >> 8) & 0xff);
            crc.reset();
        }
    }

    /**
     * Creates GZIP member header with optional header members.
     * Per RFC-1952:
     *    The filename and fileComment member should be String in
     *          LATIN-1 (ISO-8859-1) character set.
     *
     *    A compliant compressor must produce files with correct ID1,
     *          ID2, CM, CRC32, and ISIZE, but may set all the other fields in
     *          the fixed-length part of the header to default values (255 for
     *          OS, 0 for all others).  The compressor must set all reserved
     *          bits to zero.
     *
     *   The XFL (extra Flags) is set to zero and OS is set to {@code OS_UNKNOWN (=255)}.
     *   The FTEXT flag is set to zero and MTIME is filled with 0.
     *
     *   The compress method is set to {@code Defalter.DEFLATED}
     *
     * @param generateHeaderCRC
     *        if {@code true} the header will include the CRC16 of the header.
     * @param extraFieldBytes
     *        The byte array of extra filed, the generated header would calculate the
     *        byte[] size and fill it before the byte[] in header.
     * @param filename              the original file name in ISO-8859-1 character set
     * @param fileComment           the file comment in ISO_8859-1 character set.
     * @throws ZipException         If extra field size is out of range.
     *                              Or if extra filed data layout is incorrect.
     */
    public GZIPHeaderData(boolean generateHeaderCRC,
                          byte[] extraFieldBytes,
                          String filename,
                          String fileComment) throws IOException {
        this((byte)Deflater.DEFLATED, generateHeaderCRC, extraFieldBytes, filename, fileComment);
    }

    /**
     * Creates GZIP File header with default members.
     *
     * <p>All optional member of head are set to zero and header crc is not requried by default.
     *
     * @param cm    compress method
     *
     * @throws ZipException                 If extra field size is out of range.
     *                                      Or if extra filed data layout is incorrect.
     * @throws IllegalArgumentException     If compress method is not 0-8.
     */
    public GZIPHeaderData(byte cm) throws IOException {
        this(cm, false, null, null, null);
    }

    /**
     * Creates GZIP File header with default members and compress method.
     *
     * <p>All optional member of head are set to zero and header crc is not requried by default.
     *
     * @param cm    compress method
     *
     * @throws ZipException         If extra field size is out of range.
     *                              Or if extra filed data layout is incorrect.
     */
    public GZIPHeaderData() throws IOException {
        this((byte)Deflater.DEFLATED);
    }

    /** verify extra field data layout.
     * Per RFC 1952:
     * If the FLG.FEXTRA bit is set, an "extra field" is present in
     * the header, with total length XLEN bytes.  It consists of a
     * series of subfields, each of the form:
     *
     * +---+---+---+---+==================================+
     * |SI1|SI2|  LEN  |... LEN bytes of subfield data ...|
     * +---+---+---+---+==================================+
            *
     * SI1 and SI2 provide a subfield ID, typically two ASCII letters
     * with some mnemonic value
     *
     * @param fieldBytes    the data of extra fileds.
     */
    private boolean verifyExtraFieldLayout(byte[] fieldBytes) {
        int index = 0;
        int total = fieldBytes.length;
        while(index < total) {
            byte si1 = fieldBytes[index++];
            byte si2 = fieldBytes[index++];
            // si1 and si2 should be ASCII
            if (si1 > 127 || si2 > 127) return false;
            byte loLen = fieldBytes[index++];
            byte hiLen = fieldBytes[index++];
            int len = ((int)hiLen) << 8 | (int)loLen;
            index += len;
        }
        return index == total;
    }

    /**
     * Get bytes of header data
     * @return the bytes of header
     */
    public byte[] getBytes() {
        if (baos == null) {
            return null;
        }
        return baos.toByteArray();
    }
}
