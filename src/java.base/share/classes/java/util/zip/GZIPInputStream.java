/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.SequenceInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;

/**
 * This class implements a stream filter for reading compressed data in the GZIP file format.
 *
 * <p>
 * The GZIP compressed data format is self-delimiting, i.e., it includes an explicit trailer
 * frame that marks the end of the compressed data. Therefore it's possible for the underlying
 * input to contain additional data beyond the end of the compressed GZIP data. In particular,
 * some GZIP compression tools will concatenate multiple compressed data streams together.
 * This class includes configurable support for decompressing multiple concatenated compressed
 * data streams as a single uncompressed data stream.
 *
 * @see         InflaterInputStream
 * @author      David Connelly
 * @since 1.1
 *
 */
public class GZIPInputStream extends InflaterInputStream {
    /**
     * CRC-32 for uncompressed data.
     */
    protected CRC32 crc = new CRC32();

    /**
     * Indicates end of input stream.
     */
    protected boolean eos;

    private final boolean allowConcatenation;
    private final boolean ignoreExtraBytes;

    private boolean closed = false;

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Creates a new input stream with the specified buffer size that
     * allows concatenated compressed streams and ignores extra bytes.
     *
     * @param in the input stream
     * @param size the input buffer size
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error has occurred
     * @throws    IllegalArgumentException if {@code size <= 0}
     */
    public GZIPInputStream(InputStream in, int size) throws IOException {
        this(in, size, true, true);
    }

    /**
     * Creates a new input stream with the default buffer size that
     * allows concatenated compressed streams and ignores extra bytes.
     *
     * @param in the input stream
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error has occurred
     */
    public GZIPInputStream(InputStream in) throws IOException {
        this(in, 512, true, true);
    }

    /**
     * Creates a new input stream with the specified buffer size,
     * support for concatenated streams, and tolerance for extra bytes.
     *
     * <p>
     * When {@code allowConcatenation} is true, this class will attempt to decode any data that
     * follows a GZIP trailer frame as the GZIP header frame of a new compressed data stream
     * and proceed to decompress it. As a result, arbitrarily many consecutive compressed data
     * streams in the underlying input will be read back as a single uncompressed stream. When
     * {@code allowConcatenation} is false, decompression stops after the first GZIP trailer
     * frame encountered, and any additional bytes are considered extraneous.
     *
     * <p>
     * The {@code ignoreExtraBytes} flag controls the behavior when extraneous data appears,
     * i.e., when any data follows the GZIP trailer frame (if {@code allowConcatenation} is
     * false), or when any data other than a valid GZIP header frame follows a GZIP trailer
     * frame (if {@code allowConcatenation} is true). When {@code ignoreExtraBytes} is true,
     * the unexpected data, and any {@link IOException} thrown while trying to read that
     * data, are simply discarded, and EOF is returned; when {@code ignoreExtraBytes} is
     * false, any unexpected data triggers an {@link IOException}, and any {@link IOException}
     * thrown while trying to read a concatenated GZIP header frame is propagated to the caller.
     * In other words, every byte of the underlying data stream is part of a complete and valid
     * compressed data stream, or else an {@link IOException} is guaranteed to be thrown.
     *
     * @apiNote The original behavior of this class is replicated by setting both
     * {@code allowConcatenation} and {@code ignoreExtraBytes} to true. However,
     * enabling {@code ignoreExtraBytes} is discouraged because of its imprecision in
     * how many additional bytes are read and the possibility that {@link IOException}s
     * and data corruption in the underlying stream can go unnoticed.
     *
     * @param in the input stream
     * @param size the input buffer size
     * @param allowConcatenation true to allow multiple concatenated compressed data streams,
     *                           or false to expect exactly one compressed data stream
     * @param ignoreExtraBytes true to tolerate and ignore extra bytes, false to throw
     *                             {@link IOException} if any extra bytes are encountered
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error has occurred
     * @since     23
     */
    public GZIPInputStream(InputStream in, int size,
            boolean allowConcatenation, boolean ignoreExtraBytes) throws IOException {
        super(in, in != null ? new Inflater(true) : null, size);
        usesDefaultInflater = true;
        this.allowConcatenation = allowConcatenation;
        this.ignoreExtraBytes = ignoreExtraBytes;
        readHeader(in, -1);
    }

    /**
     * Reads uncompressed data into an array of bytes, returning the number of inflated
     * bytes. If {@code len} is not zero, the method will block until some input can be
     * decompressed; otherwise, no bytes are read and {@code 0} is returned.
     * <p>
     * If this method returns a nonzero integer <i>n</i> then {@code buf[off]}
     * through {@code buf[off+}<i>n</i>{@code -1]} contain the uncompressed
     * data.  The content of elements {@code buf[off+}<i>n</i>{@code ]} through
     * {@code buf[off+}<i>len</i>{@code -1]} is undefined, contrary to the
     * specification of the {@link java.io.InputStream InputStream} superclass,
     * so an implementation is free to modify these elements during the inflate
     * operation. If this method returns {@code -1} or throws an exception then
     * the content of {@code buf[off]} through {@code buf[off+}<i>len</i>{@code
     * -1]} is undefined.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array {@code buf}
     * @param len the maximum number of bytes read
     * @return  the actual number of bytes inflated, or -1 if the end of the
     *          compressed input stream is reached
     *
     * @throws     NullPointerException If {@code buf} is {@code null}.
     * @throws     IndexOutOfBoundsException If {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code buf.length - off}
     * @throws    ZipException if the compressed input data is corrupt.
     * @throws    IOException if an I/O error has occurred.
     *
     */
    public int read(byte[] buf, int off, int len) throws IOException {
        ensureOpen();
        if (eos) {
            return -1;
        }
        int n = super.read(buf, off, len);
        if (n == -1) {
            if (readTrailer())
                eos = true;
            else
                return this.read(buf, off, len);
        } else {
            crc.update(buf, off, n);
        }
        return n;
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     * @throws    IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (!closed) {
            super.close();
            eos = true;
            closed = true;
        }
    }

    /**
     * GZIP header magic number.
     */
    public static final int GZIP_MAGIC = 0x8b1f;

    /*
     * File header flags.
     */
    private static final int FTEXT      = 1;    // Extra text
    private static final int FHCRC      = 2;    // Header CRC
    private static final int FEXTRA     = 4;    // Extra field
    private static final int FNAME      = 8;    // File name
    private static final int FCOMMENT   = 16;   // File comment

    /*
     * Reads GZIP member header and returns the total byte number
     * of this member header. Use the given value as the first byte
     * if not equal to -1 (and include it in the returned byte count).
     * Throws EOFException if there's not enough input data.
     */
    private int readHeader(InputStream this_in, int firstByte) throws IOException {
        CheckedInputStream in = new CheckedInputStream(this_in, crc);
        crc.reset();
        // Check header magic
        int byte1 = firstByte != -1 ? firstByte : readUByte(in);
        int byte2 = readUByte(in);
        int magic = (byte2 << 8) | byte1;
        if (magic != GZIP_MAGIC) {
            throw new ZipException("Not in GZIP format");
        }
        // Check compression method
        if (readUByte(in) != 8) {
            throw new ZipException("Unsupported compression method");
        }
        // Read flags
        int flg = readUByte(in);
        // Skip MTIME, XFL, and OS fields
        skipBytes(in, 6);
        int n = 2 + 2 + 6;
        // Skip optional extra field
        if ((flg & FEXTRA) == FEXTRA) {
            int m = readUShort(in);
            skipBytes(in, m);
            n += m + 2;
        }
        // Skip optional file name
        if ((flg & FNAME) == FNAME) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        // Skip optional file comment
        if ((flg & FCOMMENT) == FCOMMENT) {
            do {
                n++;
            } while (readUByte(in) != 0);
        }
        // Check optional header CRC
        if ((flg & FHCRC) == FHCRC) {
            int v = (int)crc.getValue() & 0xffff;
            if (readUShort(in) != v) {
                throw new ZipException("Corrupt GZIP header");
            }
            n += 2;
        }
        crc.reset();
        return n;
    }

    /*
     * Reads GZIP member trailer and returns true if the eos
     * reached, false if there are more (concatenated gzip
     * data set)
     */
    private boolean readTrailer() throws IOException {
        InputStream in = this.in;
        int n = inf.getRemaining();
        if (n > 0) {
            in = new SequenceInputStream(
                        new ByteArrayInputStream(buf, len - n, n),
                        new FilterInputStream(in) {
                            public void close() throws IOException {}
                        });
        }
        // Uses left-to-right evaluation order
        if ((readUInt(in) != crc.getValue()) ||
            // rfc1952; ISIZE is the input size modulo 2^32
            (readUInt(in) != (inf.getBytesWritten() & 0xffffffffL)))
            throw new ZipException("Corrupt GZIP trailer");

        // Keep track of how many bytes of buffered data we may have read
        int m = 8;                                          // this.trailer

        // Handle concatenation and/or extra bytes
        if (allowConcatenation && ignoreExtraBytes) {       // i.e., the legacy behavior
            try {
                m += readHeader(in, -1);                    // next.header
            } catch (IOException ze) {
                return true;  // ignore any malformed, do nothing
            }
        } else {

            // If there is no more data, the input has terminated at a proper GZIP boundary
            int nextByte = in.read();
            if (nextByte == -1)
                return true;

            // There is more data; if we are not allowing concatenation, then there are extra bytes
            if (!allowConcatenation) {
                if (!ignoreExtraBytes)
                    throw new ZipException("Extra bytes after GZIP trailer");
                return true;
            }

            // We are allowing concatenation and not ignoring extra bytes; read the next header
            m += readHeader(in, nextByte);                  // next.header
        }

        // Pass along any remaining buffered data to the new inflater
        inf.reset();
        if (n > m)
            inf.setInput(buf, len - n + m, n - m);
        return false;
    }

    /*
     * Reads unsigned integer in Intel byte order.
     */
    private long readUInt(InputStream in) throws IOException {
        long s = readUShort(in);
        return ((long)readUShort(in) << 16) | s;
    }

    /*
     * Reads unsigned short in Intel byte order.
     */
    private int readUShort(InputStream in) throws IOException {
        int b = readUByte(in);
        return (readUByte(in) << 8) | b;
    }

    /*
     * Reads unsigned byte.
     */
    private int readUByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        if (b < -1 || b > 255) {
            // Report on this.in, not argument in; see read{Header, Trailer}.
            throw new IOException(this.in.getClass().getName()
                + ".read() returned value out of range -1..255: " + b);
        }
        return b;
    }

    private byte[] tmpbuf = new byte[128];

    /*
     * Skips bytes of input data blocking until all bytes are skipped.
     * Does not assume that the input stream is capable of seeking.
     */
    private void skipBytes(InputStream in, int n) throws IOException {
        while (n > 0) {
            int len = in.read(tmpbuf, 0, n < tmpbuf.length ? n : tmpbuf.length);
            if (len == -1) {
                throw new EOFException();
            }
            n -= len;
        }
    }
}
