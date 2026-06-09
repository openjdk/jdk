/*
 * Copyright (c) 1996, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

/**
 * This class implements a stream filter for decompressing GZIP file format data.
 *
 * <h2><a id="gzip_file_format">GZIP file format</a></h2>
 * The GZIP file format is specified by RFC 1952. The format, as specified in section 2.2 of
 * the RFC, consists of a series of "members" that appear one after another in the stream with
 * no additional information before, between, or after them. Each member consists of a header,
 * followed by data that is compressed using the {@code deflate} algorithm, and then a trailer.
 * <p>
 * This class is capable of reading a stream consisting of a series of members.
 * <p>
 * Reading from the stream may read and buffer bytes from the underlying stream.
 * This includes bytes that follow a member's trailer. Whether or not any additional bytes
 * have been read past a member's trailer, the read methods on this class yield decompressed
 * data from at most one member; data from multiple members is not combined in
 * a single read operation.
 *
 * <h2><a id="thread_safety">Thread safety</a></h2>
 * {@code GZIPInputStream} is not safe for use by multiple concurrent threads. Any multithreaded
 * concurrent use must be guarded by appropriate synchronization.
 *
 * @apiNote
 * The {@link #close} method should be called to release resources used by this
 * stream, either directly, or with the {@code try}-with-resources statement.
 *
 * @implNote
 * After reading a member trailer, the {@linkplain #read(byte[], int, int) read} method calls
 * {@link InputStream#available()} on the underlying stream to determine whether additional
 * bytes are available that may represent a subsequent member. If the
 * {@systemProperty jdk.util.gzip.tryReadAheadAfterTrailer} system property is set
 * to {@code true}, then the call to {@code InputStream.available()} is skipped and the
 * implementation instead attempts to read a subsequent member in the stream.
 * {@code GZIPInputStream} depends on the return value of {@code InputStream.available()}
 * to reliably process a stream with a series of members. Consequently, it may be necessary
 * to set this property in environments that process streams with a series of members. By default,
 * the {@code jdk.util.gzip.tryReadAheadAfterTrailer} system property is not set, and
 * {@code InputStream.available()} gets called.
 *
 * @spec https://www.rfc-editor.org/info/rfc1952
 *       RFC 1952: GZIP file format specification version 4.3
 *
 * @see InflaterInputStream
 *
 * @since 1.1
 */
public class GZIPInputStream extends InflaterInputStream {

    // system property which configures whether we skip the call to InputStream.available()
    // when checking for additional GZIP members in a stream
    private static final boolean alwaysReadNextMember =
            Boolean.getBoolean("jdk.util.gzip.tryReadAheadAfterTrailer");

    /**
     * GZIP header magic number.
     */
    public static final int GZIP_MAGIC = 0x8b1f;

    /*
     * File header flags.
     */
    private static final int FHCRC      = 2;    // Header CRC
    private static final int FEXTRA     = 4;    // Extra field
    private static final int FNAME      = 8;    // File name
    private static final int FCOMMENT   = 16;   // File comment

    private final byte[] tmpbuf = new byte[128];

    /**
     * CRC-32 for decompressed data.
     */
    protected CRC32 crc = new CRC32();

    /**
     * Indicates end of input stream.
     */
    protected boolean eos;

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
     * Creates a new input stream with the specified buffer size.
     *
     * @param in the input stream
     * @param size the input buffer size
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error occurs when reading the member header
     *                        from the underlying stream
     * @throws    IllegalArgumentException if {@code size <= 0}
     */
    public GZIPInputStream(InputStream in, int size) throws IOException {
        super(in, createInflater(in, size), size);
        usesDefaultInflater = true;
        try {
            // we don't expect the stream to be at EOF
            // and if it is, then we want readHeader to
            // raise an exception, so we pass "true" for
            // the "failOnEOF" param.
            readHeader(in, true);
        } catch (IOException ioe) {
            this.inf.end();
            throw ioe;
        }
    }

    /*
     * Creates and returns an Inflater only if the input stream is not null and the
     * buffer size is > 0.
     * If the input stream is null, then this method throws a
     * NullPointerException. If the size is <= 0, then this method throws
     * an IllegalArgumentException
     */
    private static Inflater createInflater(InputStream in, int size) {
        Objects.requireNonNull(in);
        if (size <= 0) {
            throw new IllegalArgumentException("buffer size <= 0");
        }
        return new Inflater(true);
    }

    /**
     * Creates a new input stream with a default buffer size.
     *
     * @param in the input stream
     *
     * @throws    ZipException if a GZIP format error has occurred or the
     *                         compression method used is unsupported
     * @throws    NullPointerException if {@code in} is null
     * @throws    IOException if an I/O error occurs when reading the member header
     *                        from the underlying stream
     */
    public GZIPInputStream(InputStream in) throws IOException {
        this(in, 512);
    }

    /**
     * Reads decompressed data into an array of bytes, returning the number of decompressed
     * bytes. If {@code len} is not zero, the method will block until some input can be
     * decompressed; otherwise, no bytes are read and {@code 0} is returned.
     * <p>
     * If this method returns a nonzero integer <i>n</i> then {@code buf[off]}
     * through {@code buf[off+}<i>n</i>{@code -1]} contain the decompressed
     * data. The content of elements {@code buf[off+}<i>n</i>{@code ]} through
     * {@code buf[off+}<i>len</i>{@code -1]} is undefined, contrary to the
     * specification of the {@link java.io.InputStream InputStream} superclass,
     * so an implementation is free to modify these elements during the inflate
     * operation. If this method returns {@code -1} or throws an exception then
     * the content of {@code buf[off]} through {@code buf[off+}<i>len</i>{@code
     * -1]} is undefined.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset in the destination array {@code buf}
     * @param len the maximum number of bytes to read into {@code buf}
     * @return  the actual number of bytes decompressed from a GZIP member, or -1 if the
     *          end-of-stream is reached
     *
     * @throws     NullPointerException If {@code buf} is {@code null}.
     * @throws     IndexOutOfBoundsException If {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code buf.length - off}
     * @throws    ZipException if the compressed input data is corrupt.
     * @throws    IOException if the stream is closed or an I/O error has occurred.
     *
     * @see ##gzip_file_format GZIP file format
     */
    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        ensureOpen();
        if (eos) {
            return -1;
        }
        int n = super.read(buf, off, len);
        if (n == -1) {
            if (hasNoMoreMembers()) {
                eos = true;
            } else {
                // When a next member is available, hasNoMoreMembers() will read
                // its header and will position the stream at the next member's
                // deflated data. We now decompress and return that member's
                // decompressed data.
                return this.read(buf, off, len);
            }
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
    @Override
    public void close() throws IOException {
        if (!closed) {
            super.close();
            eos = true;
            closed = true;
        }
    }

    /*
     * Reads GZIP member header and returns the total byte number
     * of this member header.
     * If failOnEOF is false and if the given InputStream has already
     * reached EOF when this method was invoked, then this method returns
     * -1 (indicating that there's no GZIP member header).
     * In all other cases of malformed header or EOF being detected
     * when reading the header, this method will throw an IOException.
     */
    private int readHeader(InputStream stream, boolean failOnEOF) throws IOException {
        CheckedInputStream in = new CheckedInputStream(stream, crc);
        crc.reset();

        int magic;
        if (!failOnEOF) {
            // read an unsigned short value representing the GZIP magic header.
            // this is the same as calling readUShort(in), except that here,
            // when reading the first byte, we don't raise an EOFException
            // if the stream has already reached EOF.

            // read unsigned byte
            int b = in.read();
            if (b == -1) { // EOF
                crc.reset();
                return -1; // represents no header bytes available
            }
            checkUnexpectedByte(b);
            // read the next unsigned byte to form the unsigned
            // short. we throw the usual EOFException/ZipException
            // from this point on if there is no more data or
            // the data doesn't represent a header.
            magic = (readUByte(in) << 8) | b;
        } else {
            magic = readUShort(in);
        }
        // Check header magic
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

    /**
     * Reads the current GZIP member's trailer and returns true if the end-of-stream is
     * reached. After reading the current member's trailer, if the stream has a subsequent
     * GZIP member, then this method reads that member's header and returns false indicating
     * that there is another member in the stream.
     */
    private boolean hasNoMoreMembers() throws IOException {
        final int numRemainingInInflater = inf.getRemaining();
        InputStream stream = this.in;
        if (numRemainingInInflater > 0) {
            stream = new SequenceInputStream(
                    new ByteArrayInputStream(buf, len - numRemainingInInflater, numRemainingInInflater),
                    new FilterInputStream(stream) {
                        public void close() {}
                    });
        }
        // first read the current member's trailer
        readTrailer(stream);
        // decide whether to read next member's header
        final boolean readNextMember = alwaysReadNextMember
                || this.in.available() > 0
                || numRemainingInInflater > 26; // current member's trailer == 8 bytes
                                                // + minimum of 10 bytes header for next member
                                                // + mandatory 8 bytes from next member's trailer
                                                // == at least 26 bytes needed for next member to
                                                // be present
        if (!readNextMember) {
            return true; // no need to read next member
        }
        // read next member's header
        int m = 8;  // this.trailer
        try {
            int numNextHeaderBytes = readHeader(stream, false); // next.header (if available)
            if (numNextHeaderBytes == -1) {
                return true; // end of stream reached, no more members
            }
            m += numNextHeaderBytes;
        } catch (IOException ze) {
            return true;  // ignore any malformed, consider it as no more members in the stream
        }
        inf.reset(); // reset the inflater for fresh input data from the next member
        if (numRemainingInInflater > m) {
            // position the inflater's input buffer to the start of next member's deflated data
            inf.setInput(buf, len - numRemainingInInflater + m, numRemainingInInflater - m);
        }
        return false; // next member exists
    }

    /**
     * Reads the current member's trailer
     *
     * @param stream the InputStream containing the trailer
     */
    private void readTrailer(final InputStream stream) throws IOException {
        // Uses left-to-right evaluation order
        if ((readUInt(stream) != crc.getValue()) ||
                // rfc1952; ISIZE is the input size modulo 2^32
                (readUInt(stream) != (inf.getBytesWritten() & 0xffffffffL))) {
            throw new ZipException("Corrupt GZIP trailer");
        }
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
        checkUnexpectedByte(b);
        return b;
    }

    private void checkUnexpectedByte(final int b) throws IOException {
        if (b < -1 || b > 255) {
            // report the InputStream type which returned this unexpected byte
            throw new IOException(this.in.getClass().getName()
                    + ".read() returned value out of range -1..255: " + b);
        }
    }

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
