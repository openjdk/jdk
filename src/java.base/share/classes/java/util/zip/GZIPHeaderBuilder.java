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
 * This class implements the GZIP file header builder
 *
 * @author      Lin Zang
 * @since 17
 *
 */
public class GZIPHeaderBuilder {
    /* compress method, only {@code Deflater.DEFLATED} supportted. */
    private byte cm;
    private byte[] extraFieldBytes;
    private String filename;
    private String fileComment;
    /* Header flags, refer to definition in {@code GZIPHeaderData} */
    private byte flags;
    private int headerCrc16;

    /**
     * Creates a GZIP file header builder.
     * Current only {@code Deflater.DEFLATED} compress method supportted.
     *
     * @since 17
     */
    public GZIPHeaderBuilder() {
        this.cm = (byte)Deflater.DEFLATED;
        this.extraFieldBytes = null;
        this.filename = null;
        this.fileComment = null;
        this.flags = 0;
        this.headerCrc16 = 0;
    }

    /**
     * Add extra field in GZIP file header.
     * This method verifies the extra fileds layout per RFC-1952.
     * See comments of {@code verifyExtraFieldLayout}
     *
     * @param extraFieldBytes The byte array of extra field.
     * @return {@code this}
     *
     * @throws ZipException if extra field layout is incorrect.
     *
     * @since 17
     */
    public GZIPHeaderBuilder withExtraFieldBytes(byte[] extraFieldBytes) throws ZipException {
        /*
         * Here do verification before write data.
         */
        if (!verifyExtraFieldLayout(extraFieldBytes)) {
            throw new ZipException("incorrect extra field format");
        }
        flags |= GZIPHeaderData.FEXTRA;
        this.extraFieldBytes = extraFieldBytes;
        return this;
    }

    /**
     * Add file name in GZIP file header.
     *
     * Per RFC-1952, the file name string should in ISO_8859-1 character set.
     *
     * @param filename The file name
     * @return {@code this}
     *
     * @since 17
     */
    public GZIPHeaderBuilder withFileName(String filename) {
        if (filename == null || filename.length() == 0) {
            this.filename = null;
        }
        this.filename = filename;
        flags |= GZIPHeaderData.FNAME;
        return this;
    }

    /**
     * Add file comment in GZIP file header.
     *
     * Per RFC-1952, the file comment string should in ISO_8859-1 character set.
     *
     * @param fileComment The file comment
     * @return {@code this}'
     *
     * @since 17
     */
    public GZIPHeaderBuilder withFileComment(String fileComment) {
        if (fileComment == null || fileComment.length() == 0) {
            this.fileComment = null;
        }
        this.fileComment = fileComment;
        flags |= GZIPHeaderData.FCOMMENT;
        return this;
    }

    /**
     * Enable/Disable the CRC calculation of GZIP file header.
     *
     * @param calculateHeaderCRC if {@code true} the header data contains the lower 16 bytes of header CRC
     * @return {@code this}
     *
     * @since 17
     */
    public GZIPHeaderBuilder calculateHeaderCRC(boolean calculateHeaderCRC) {
        if (calculateHeaderCRC) {
            flags |= GZIPHeaderData.FHCRC;
        }
        return this;
    }

    /**
     *  Generate the GZIP header data
     * @return the {@code record} of GZIP header data.
     *
     * @throws ZipException         If extra field size is out of range.
     *                              Or if extra filed data layout is incorrect.
     * @throws IllegalArgumentException     If compress method is not {@code Deflater.DEFLATED}
     * @since 17
     */
    public GZIPHeaderData build() throws IOException {
        byte[] headerBytes = generateBytes(cm, extraFieldBytes, filename, fileComment);
        return new GZIPHeaderData(cm, flags, extraFieldBytes, filename, fileComment, headerCrc16, headerBytes);
    }

    /**
     * Creates GZIP header bytes with optional header members and compress method.
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
     *                              at present only support {@code Deflater.DEFLATED}
     *
     * @param extraFieldBytes
     *        The byte array of extra filed, the generated header would calculate the
     *        byte[] size and fill it before the byte[] in header.
     * @param filename              the original file name in ISO-8859-1 character set
     * @param fileComment           the file comment in ISO_8859-1 character set.
     *                              
     * @return Bytes of header data generated.
     * 
     * @throws ZipException         If extra field size is out of range.
     *                              Or if extra filed data layout is incorrect.
     * @throws IllegalArgumentException     If compress method is not {@code Deflater.DEFLATED}.
     *
     * @since 17
     */
    public byte[] generateBytes(byte cm,
                                byte[] extraFieldBytes,
                                String filename,
                                String fileComment) throws IOException {
        CRC32 crc = new CRC32();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // the head of header.
        byte [] head = new byte[] {
                (byte) GZIPHeaderData.GZIP_MAGIC,        // Magic number (short)
                (byte)(GZIPHeaderData.GZIP_MAGIC >> 8),  // Magic number (short)
                cm,                       // Compression method (CM)
                flags,                    // Flags (FLG)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Extra flags (XFLG)
                GZIPHeaderData.OS_UNKNOWN                // Operating system (OS)
        };
        // write head.
        baos.write(head);
        
        if ((flags & GZIPHeaderData.FHCRC) == GZIPHeaderData.FHCRC) {
            crc.update(head, 0, head.length);
        }

        // write extra field.
        if ((flags & GZIPHeaderData.FEXTRA) == GZIPHeaderData.FEXTRA) {
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
            if ((flags & GZIPHeaderData.FHCRC) == GZIPHeaderData.FHCRC) {
                crc.update((byte)(xlen & 0xff));
                crc.update(((byte)(xlen >> 8) & 0xff));
                crc.update(extraFieldBytes, 0, extraFieldBytes.length);
            }
        }
        // write file name.
        if ((flags & GZIPHeaderData.FNAME) == GZIPHeaderData.FNAME) {
            /*
             *    +=========================================+
             *    |...original file name, zero-terminated...|
             *    +=========================================+
             */
            byte[] filenameBytes = filename.getBytes("ISO-8859-1");
            baos.write(filenameBytes);
            baos.write(0);
            if ((flags & GZIPHeaderData.FHCRC) == GZIPHeaderData.FHCRC) {
                crc.update(filenameBytes, 0, filenameBytes.length);
                crc.update(0);
            }
        }
        // write file comment.
        if ((flags & GZIPHeaderData.FCOMMENT) == GZIPHeaderData.FCOMMENT) {
            /*
             *    +===================================+
             *    |...file comment, zero-terminated...|
             *    +===================================+
             */
            byte[] fcommBytes = fileComment.getBytes("ISO-8859-1");
            baos.write(fcommBytes);
            baos.write(0);
            if ((flags & GZIPHeaderData.FHCRC) == GZIPHeaderData.FHCRC) {
                crc.update(fcommBytes, 0, fcommBytes.length);
                crc.update(0);
            }
        }
        // write header crc16.
        if ((flags & GZIPHeaderData.FHCRC) == GZIPHeaderData.FHCRC) {
            int crc16 = (int) crc.getValue() & 0xffff;
            this.headerCrc16 = crc16;
            baos.write((byte) (crc16 & 0xff));
            baos.write((byte) (crc16 >> 8) & 0xff);
            crc.reset();
        }
        return baos.toByteArray();
    }
    
    /** verify extra field data layout.
     * Per RFC 1952:
     * If the FEXTRA bit is set, an "extra field" is present in
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
     * @return {@code true} if field data layout is correct.
     *
     * @since 17
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
     * This class implements the header of GZIP file which contains members defined
     * in the RFC 1952 specification
     *
     * @since 17
     *
     */
    public record GZIPHeaderData (byte compressMethod, byte flags,
                                  byte[] extraFieldBytes,
                                  String filename,
                                  String fileComment,
                                  int headerCRC16,
                                  byte[] headerBytes) {
        /**
         * GZIP header magic number.
         */
        private static final int GZIP_MAGIC = 0x8b1f;

        /**
         * Represents the default "unknown" value for OS header, per RFC-1952
         */
        private static final byte OS_UNKNOWN = (byte) 255;

        /**
         * File header flags.
         *
         * Per RFC 1952, the header fields contains several members based on flag byte
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
         *
         */
        private static final int FTEXT      = 1;    // Extra text
        private static final int FHCRC      = 2;    // Header CRC
        private static final int FEXTRA     = 4;    // Extra field
        private static final int FNAME      = 8;    // File name
        private static final int FCOMMENT   = 16;   // File comment
    }
}