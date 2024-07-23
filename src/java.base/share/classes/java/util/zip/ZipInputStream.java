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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.util.Objects;

import sun.nio.cs.UTF_8;

import static java.util.zip.ZipConstants64.*;
import static java.util.zip.ZipUtils.*;

/**
 * An input stream for reading compressed and uncompressed
 * {@linkplain ZipEntry ZIP file entries} from a stream of bytes in the ZIP file
 * format.
 * <p> Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 * <H2>Reading Zip File Entries</H2>
 *
 * The {@link #getNextEntry()} method is used to read the next ZIP file entry
 * (Local file (LOC) header record in the ZIP format) and position the stream at
 * the entry's file data. The file data may read using one of the
 * {@code ZipInputStream} read methods such
 * as {@link #read(byte[], int, int) read} or {@link #readAllBytes() readAllBytes()}.
 * For example:
 *    {@snippet lang="java" :
 *      Path jar = Path.of("foo.jar");
 *      try (InputStream is = Files.newInputStream(jar);
 *           ZipInputStream zis = new ZipInputStream(is)) {
 *          ZipEntry ze;
 *          while ((ze = zis.getNextEntry()) != null) {
 *             var bytes = zis.readAllBytes();
 *             System.out.printf("Entry: %s, bytes read: %s%n", ze.getName(),
 *                     bytes.length);
 *          }
 *      }
 *    }
 * @apiNote
 * The LOC header contains metadata about the ZIP file entry. {@code ZipInputStream}
 * does not read the Central directory (CEN) header for the entry and therefore
 * will not have access to its metadata such as the external file attributes.
 * {@linkplain ZipFile} may be used when the information stored within
 * the CEN header is required.
 *
 * @author      David Connelly
 * @since 1.1
 */
public class ZipInputStream extends InflaterInputStream implements ZipConstants {
    private ZipEntry entry;
    private int flag;
    private CRC32 crc = new CRC32();
    private long remaining;
    private byte[] tmpbuf = new byte[512];

    private static final int STORED = ZipEntry.STORED;
    private static final int DEFLATED = ZipEntry.DEFLATED;

    private boolean closed = false;
    // this flag is set to true after EOF has reached for
    // one entry
    private boolean entryEOF = false;

    private ZipCoder zc;

    // Flag to indicate readEnd should expect 64 bit Data Descriptor size fields
    private boolean expect64BitDataDescriptor;

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    /**
     * Creates a new ZIP input stream.
     *
     * <p>The UTF-8 {@link java.nio.charset.Charset charset} is used to
     * decode the entry names.
     *
     * @param in the actual input stream
     */
    public ZipInputStream(InputStream in) {
        this(in, UTF_8.INSTANCE);
    }

    /**
     * Creates a new ZIP input stream.
     *
     * @param in the actual input stream
     *
     * @param charset
     *        The {@linkplain java.nio.charset.Charset charset} to be
     *        used to decode the ZIP entry name (ignored if the
     *        <a href="package-summary.html#lang_encoding"> language
     *        encoding bit</a> of the ZIP entry's general purpose bit
     *        flag is set).
     *
     * @since 1.7
     */
    public ZipInputStream(InputStream in, Charset charset) {
        super(new PushbackInputStream(in, 512), new Inflater(true), 512);
        usesDefaultInflater = true;
        if (in == null) {
            throw new NullPointerException("in is null");
        }
        if (charset == null)
            throw new NullPointerException("charset is null");
        this.zc = ZipCoder.get(charset);
    }

    /**
     * Reads the next ZIP file entry and positions the stream at the
     * beginning of the entry data.
     * @return the next ZIP file entry, or null if there are no more entries
     * @throws    ZipException if a ZIP file error has occurred
     * @throws    IOException if an I/O error has occurred
     */
    public ZipEntry getNextEntry() throws IOException {
        ensureOpen();
        if (entry != null) {
            closeEntry();
        }
        crc.reset();
        inf.reset();
        if ((entry = readLOC()) == null) {
            return null;
        }
        if (entry.method == STORED) {
            remaining = entry.size;
        }
        entryEOF = false;
        return entry;
    }

    /**
     * Closes the current ZIP entry and positions the stream for reading the
     * next entry.
     * @throws    ZipException if a ZIP file error has occurred
     * @throws    IOException if an I/O error has occurred
     */
    public void closeEntry() throws IOException {
        ensureOpen();
        while (read(tmpbuf, 0, tmpbuf.length) != -1) ;
        entryEOF = true;
    }

    /**
     * Returns 0 when end of stream is detected for the current ZIP entry or
     * {@link #closeEntry()} has been called on the current ZIP entry, otherwise
     * returns 1.
     * <p>
     * Programs should not count on this method to return the actual number
     * of bytes that could be read without blocking.
     *
     * @return 0 when end of stream is detected for the current ZIP entry or
     * {@link #closeEntry()} has been called on the current ZIP entry, otherwise 1.
     * @throws     IOException  if an I/O error occurs.
     *
     */
    public int available() throws IOException {
        ensureOpen();
        if (entryEOF) {
            return 0;
        } else {
            return 1;
        }
    }

    /**
     * Reads the next byte of data from the input stream for the current
     * ZIP entry. This method will block until enough input is available for
     * decompression.
     * @return the byte read, or -1 if the end of the stream is reached
     * @throws    IOException if an I/O error has occurred
     */
    @Override
    public int read() throws IOException {
        return super.read();
    }

    /**
     * Reads all remaining bytes from the input stream for the current ZIP entry.
     * This method blocks until all remaining bytes have been read and end of
     * stream is detected, or an exception is thrown. This method does not close
     * the input stream.
     *
     * <p> When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     * <p> Note that this method is intended for simple cases where it is
     * convenient to read all bytes into a byte array. It is not intended for
     * reading input streams with large amounts of data.
     *
     * <p> If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently, the input
     * stream may not be at end of stream and may be in an inconsistent state.
     * It is strongly recommended that the stream be promptly closed if an I/O
     * error occurs.
     *
     * @throws OutOfMemoryError {@inheritDoc}
     *
     * @since 9
     */
    @Override
    public byte[] readAllBytes() throws IOException {
        return super.readAllBytes();
    }

    /**
     * Reads up to a specified number of bytes from the input stream
     * for the current ZIP entry. This method blocks until the requested number
     * of bytes has been read, end of stream is detected, or an exception
     * is thrown. This method does not close the input stream.
     *
     * <p> The length of the returned array equals the number of bytes read
     * from the stream. If {@code len} is zero, then no bytes are read and
     * an empty byte array is returned. Otherwise, up to {@code len} bytes
     * are read from the stream. Fewer than {@code len} bytes may be read if
     * end of stream is encountered.
     *
     * <p> When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     * <p> Note that this method is intended for simple cases where it is
     * convenient to read the specified number of bytes into a byte array. The
     * total amount of memory allocated by this method is proportional to the
     * number of bytes read from the stream which is bounded by {@code len}.
     * Therefore, the method may be safely called with very large values of
     * {@code len} provided sufficient memory is available.
     *
     * <p> If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently, the input
     * stream may not be at end of stream and may be in an inconsistent state.
     * It is strongly recommended that the stream be promptly closed if an I/O
     * error occurs.
     *
     * @implNote
     * This method calls {@code super.readNBytes(int len)}.
     *
     *  @throws OutOfMemoryError {@inheritDoc}
     *
     * @since 11
     */
    @Override
    public byte[] readNBytes(int len) throws IOException {
        return super.readNBytes(len);
    }

    /**
     * Reads the requested number of bytes from the input stream into the given
     * byte array for the current ZIP entry returning the number of
     * inflated bytes.
     * This method blocks until {@code len} bytes of input data have
     * been read, end of stream is detected, or an exception is thrown. The
     * number of bytes actually read, possibly zero, is returned. This method
     * does not close the input stream.
     *
     * <p> In the case where end of stream is reached before {@code len} bytes
     * have been read, then the actual number of bytes read will be returned.
     * When this stream reaches end of stream, further invocations of this
     * method will return zero.
     *
     * <p> If {@code len} is zero, then no bytes are read and {@code 0} is
     * returned; otherwise, there is an attempt to read up to {@code len} bytes.
     *
     * <p> The first byte read is stored into element {@code b[off]}, the next
     * one in to {@code b[off+1]}, and so on. The number of bytes read is, at
     * most, equal to {@code len}. Let <i>k</i> be the number of bytes actually
     * read; these bytes will be stored in elements {@code b[off]} through
     * {@code b[off+}<i>k</i>{@code -1]}, leaving elements {@code b[off+}<i>k</i>
     * {@code ]} through {@code b[off+len-1]} unaffected.
     *
     * <p> If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes of {@code b} have been updated with
     * data from the input stream. Consequently, the input stream and {@code b}
     * may be in an inconsistent state. It is strongly recommended that the
     * stream be promptly closed if an I/O error occurs.
     *
     * @throws IndexOutOfBoundsException {@inheritDoc}
     *
     * @since 9
     */
    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return super.readNBytes(b, off, len);
    }

    /**
     * Skips over and discards exactly {@code n} bytes of data from this input
     * stream for the current ZIP entry.
     * If {@code n} is zero, then no bytes are skipped.
     * If {@code n} is negative, then no bytes are skipped.
     * Subclasses may handle the negative value differently.
     *
     * <p> This method blocks until the requested number of bytes has been
     * skipped, end of file is reached, or an exception is thrown.
     *
     * <p> If end of stream is reached before the stream is at the desired
     * position, then an {@code EOFException} is thrown.
     *
     * <p> If an I/O error occurs, then the input stream may be
     * in an inconsistent state. It is strongly recommended that the
     * stream be promptly closed if an I/O error occurs.
     *
     * @since 12
     */
    @Override
    public void skipNBytes(long n) throws IOException {
        super.skipNBytes(n);
    }

    /**
     * Reads all bytes from this input stream for the current ZIP entry
     * and writes the bytes to the given output stream in the order that they
     * are read. On return, this input stream will be at end of stream.
     * This method does not close either stream.
     * <p>
     * This method may block indefinitely reading from the input stream, or
     * writing to the output stream. The behavior for the case where the input
     * and/or output stream is <i>asynchronously closed</i>, or the thread
     * interrupted during the transfer, is highly input and output stream
     * specific, and therefore not specified.
     * <p>
     * If the total number of bytes transferred is greater than {@linkplain
     * Long#MAX_VALUE}, then {@code Long.MAX_VALUE} will be returned.
     * <p>
     * If an I/O error occurs reading from the input stream or writing to the
     * output stream, then it may do so after some bytes have been read or
     * written. Consequently, the input stream may not be at end of stream and
     * one, or both, streams may be in an inconsistent state. It is strongly
     * recommended that both streams be promptly closed if an I/O error occurs.
     *
     * @since 9
     */
    @Override
    public long transferTo(OutputStream out) throws IOException {
        return super.transferTo(out);
    }

    /**
     * Reads the requested number of bytes from the input stream into the given
     * byte array for the current ZIP entry returning the number of
     * inflated bytes. If {@code len} is not zero, the method blocks until some input is
     * available; otherwise, no bytes are read and {@code 0} is returned.
     * <p>
     * If the current entry is compressed and this method returns a nonzero
     * integer <i>n</i> then {@code buf[off]}
     * through {@code buf[off+}<i>n</i>{@code -1]} contain the uncompressed
     * data.  The content of elements {@code buf[off+}<i>n</i>{@code ]} through
     * {@code buf[off+}<i>len</i>{@code -1]} is undefined, contrary to the
     * specification of the {@link java.io.InputStream InputStream} superclass,
     * so an implementation is free to modify these elements during the inflate
     * operation. If this method returns {@code -1} or throws an exception then
     * the content of {@code buf[off]} through {@code buf[off+}<i>len</i>{@code
     * -1]} is undefined.
     *
     * @param b the buffer into which the data is read
     * @param off the start offset in the destination array {@code b}
     * @param len the maximum number of bytes read
     * @return the actual number of bytes read, or -1 if the end of the
     *         entry is reached
     * @throws     IndexOutOfBoundsException if {@code off} is negative,
     * {@code len} is negative, or {@code len} is greater than
     * {@code b.length - off}
     * @throws    ZipException if a ZIP file error has occurred
     * @throws    IOException if an I/O error has occurred
     */
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }

        if (entry == null) {
            return -1;
        }
        switch (entry.method) {
        case DEFLATED:
            len = super.read(b, off, len);
            if (len == -1) {
                readEnd(entry);
                entryEOF = true;
                entry = null;
            } else {
                crc.update(b, off, len);
            }
            return len;
        case STORED:
            if (remaining <= 0) {
                entryEOF = true;
                entry = null;
                return -1;
            }
            if (len > remaining) {
                len = (int)remaining;
            }
            len = in.read(b, off, len);
            if (len == -1) {
                throw new ZipException("unexpected EOF");
            }
            crc.update(b, off, len);
            remaining -= len;
            if (remaining == 0 && entry.crc != crc.getValue()) {
                throw new ZipException(
                    "invalid entry CRC (expected 0x" + Long.toHexString(entry.crc) +
                    " but got 0x" + Long.toHexString(crc.getValue()) + ")");
            }
            return len;
        default:
            throw new ZipException("invalid compression method");
        }
    }

    /**
     * Skips over and discards {@code n} bytes of data from this input stream
     * for the current ZIP entry.
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped
     * @throws    ZipException if a ZIP file error has occurred
     * @throws    IOException if an I/O error has occurred
     * @throws    IllegalArgumentException if {@code n < 0}
     */
    public long skip(long n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("negative skip length");
        }
        ensureOpen();
        int max = (int)Math.min(n, Integer.MAX_VALUE);
        int total = 0;
        while (total < max) {
            int len = max - total;
            if (len > tmpbuf.length) {
                len = tmpbuf.length;
            }
            len = read(tmpbuf, 0, len);
            if (len == -1) {
                entryEOF = true;
                break;
            }
            total += len;
        }
        return total;
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     * @throws    IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (!closed) {
            super.close();
            closed = true;
        }
    }

    private byte[] b = new byte[256];

    /*
     * Reads local file (LOC) header for next entry.
     */
    private ZipEntry readLOC() throws IOException {
        try {
            readFully(tmpbuf, 0, LOCHDR);
        } catch (EOFException e) {
            return null;
        }
        if (get32(tmpbuf, 0) != LOCSIG) {
            return null;
        }
        // get flag first, we need check USE_UTF8.
        flag = get16(tmpbuf, LOCFLG);
        // get the entry name and create the ZipEntry first
        int len = get16(tmpbuf, LOCNAM);
        int blen = b.length;
        if (len > blen) {
            do {
                blen = blen * 2;
            } while (len > blen);
            b = new byte[blen];
        }
        readFully(b, 0, len);
        // Force to use UTF-8 if the USE_UTF8 bit is ON
        String entryName;
        try {
            entryName = ((flag & USE_UTF8) != 0) ?
                    ZipCoder.toStringUTF8(b, len)
                    : zc.toString(b, len);
        } catch (Exception ex) {
            throw (ZipException) new ZipException(
                    "invalid LOC header (bad entry name)").initCause(ex);
        }
        ZipEntry e = createZipEntry(entryName);
        // now get the remaining fields for the entry
        if ((flag & 1) == 1) {
            throw new ZipException("encrypted ZIP entry not supported");
        }
        e.method = get16(tmpbuf, LOCHOW);
        e.xdostime = get32(tmpbuf, LOCTIM);

        // Expect 32-bit Data Descriptor size fields by default
        expect64BitDataDescriptor = false;

        long csize = get32(tmpbuf, LOCSIZ);
        long size = get32(tmpbuf, LOCLEN);

        if ((flag & 8) == 8) {
            /* "Data Descriptor" present */
            if (e.method != DEFLATED) {
                throw new ZipException(
                        "only DEFLATED entries can have EXT descriptor");
            }
        } else {
            e.crc = get32(tmpbuf, LOCCRC);
            e.csize = csize;
            e.size = size;
        }
        len = get16(tmpbuf, LOCEXT);
        if (len > 0) {
            byte[] extra = new byte[len];
            readFully(extra, 0, len);
            e.setExtra0(extra,
                        e.csize == ZIP64_MAGICVAL || e.size == ZIP64_MAGICVAL, true);
            // Determine if readEnd should expect 64-bit size fields in the Data Descriptor
            expect64BitDataDescriptor = expect64BitDataDescriptor(extra, flag, csize, size);
        }
        return e;
    }

    /**
     * Creates a new {@code ZipEntry} object for the specified
     * entry name.
     *
     * @param name the ZIP file entry name
     * @return the ZipEntry just created
     */
    protected ZipEntry createZipEntry(String name) {
        return new ZipEntry(name);
    }

    /**
     * Reads end of deflated entry as well as EXT descriptor if present.
     *
     * Local headers for DEFLATED entries may optionally be followed by a
     * data descriptor, and that data descriptor may optionally contain a
     * leading signature (EXTSIG).
     *
     * From the ZIP spec http://www.pkware.com/documents/casestudies/APPNOTE.TXT
     *
     * """Although not originally assigned a signature, the value 0x08074b50
     * has commonly been adopted as a signature value for the data descriptor
     * record.  Implementers should be aware that ZIP files may be
     * encountered with or without this signature marking data descriptors
     * and should account for either case when reading ZIP files to ensure
     * compatibility."""
     */
    private void readEnd(ZipEntry e) throws IOException {
        int n = inf.getRemaining();
        if (n > 0) {
            ((PushbackInputStream)in).unread(buf, len - n, n);
        }
        if ((flag & 8) == 8) {
            /* "Data Descriptor" present */
            if (inf.getBytesWritten() > ZIP64_MAGICVAL ||
                inf.getBytesRead() > ZIP64_MAGICVAL ||
                    expect64BitDataDescriptor) {
                // ZIP64 format
                readFully(tmpbuf, 0, ZIP64_EXTHDR);
                long sig = get32(tmpbuf, 0);
                if (sig != EXTSIG) { // no EXTSIG present
                    e.crc = sig;
                    e.csize = get64(tmpbuf, ZIP64_EXTSIZ - ZIP64_EXTCRC);
                    e.size = get64(tmpbuf, ZIP64_EXTLEN - ZIP64_EXTCRC);
                    ((PushbackInputStream)in).unread(
                        tmpbuf, ZIP64_EXTHDR - ZIP64_EXTCRC, ZIP64_EXTCRC);
                } else {
                    e.crc = get32(tmpbuf, ZIP64_EXTCRC);
                    e.csize = get64(tmpbuf, ZIP64_EXTSIZ);
                    e.size = get64(tmpbuf, ZIP64_EXTLEN);
                }
            } else {
                readFully(tmpbuf, 0, EXTHDR);
                long sig = get32(tmpbuf, 0);
                if (sig != EXTSIG) { // no EXTSIG present
                    e.crc = sig;
                    e.csize = get32(tmpbuf, EXTSIZ - EXTCRC);
                    e.size = get32(tmpbuf, EXTLEN - EXTCRC);
                    ((PushbackInputStream)in).unread(
                                               tmpbuf, EXTHDR - EXTCRC, EXTCRC);
                } else {
                    e.crc = get32(tmpbuf, EXTCRC);
                    e.csize = get32(tmpbuf, EXTSIZ);
                    e.size = get32(tmpbuf, EXTLEN);
                }
            }
        }
        if (e.size != inf.getBytesWritten()) {
            throw new ZipException(
                "invalid entry size (expected " + e.size +
                " but got " + inf.getBytesWritten() + " bytes)");
        }
        if (e.csize != inf.getBytesRead()) {
            throw new ZipException(
                "invalid entry compressed size (expected " + e.csize +
                " but got " + inf.getBytesRead() + " bytes)");
        }
        if (e.crc != crc.getValue()) {
            throw new ZipException(
                "invalid entry CRC (expected 0x" + Long.toHexString(e.crc) +
                " but got 0x" + Long.toHexString(crc.getValue()) + ")");
        }
    }

    /**
     * Determine whether the {@link #readEnd(ZipEntry)} method should interpret the
     * 'compressed size' and 'uncompressed size' fields of the Data Descriptor record
     * as 64-bit numbers instead of the regular 32-bit numbers.
     *
     * Returns true if the LOC has the 'streaming mode' flag set, at least one of the
     * 'compressed size' and 'uncompressed size' are set to the Zip64 magic value
     * 0xFFFFFFFF, and the LOC's extra field contains a Zip64 Extended Information Field.
     *
     * @param extra the LOC extra field to look for a Zip64 field in
     * @param flag the value of the 'general purpose bit flag' field in the LOC
     * @param csize the value of the 'compressed size' field in the LOC
     * @param size  the value of the 'uncompressed size' field in the LOC
     */
    private boolean expect64BitDataDescriptor(byte[] extra, int flag, long csize, long size) {
        // The LOC's 'general purpose bit flag' 3 must indicate use of a Data Descriptor
        if ((flag & 8) == 0) {
            return false;
        }

        // At least one LOC size field must be marked for Zip64
        if (csize != ZIP64_MAGICVAL && size != ZIP64_MAGICVAL) {
            return false;
        }

        // Look for a Zip64 field
        int headerSize = 2 * Short.BYTES; // id + size
        if (extra != null) {
            for (int i = 0; i + headerSize < extra.length;) {
                int id = get16(extra, i);
                int dsize = get16(extra, i + Short.BYTES);
                if (i + headerSize + dsize > extra.length) {
                    return false; // Invalid size
                }
                if (id == EXTID_ZIP64) {
                    return true;
                }
                i += headerSize + dsize;
            }
        }
        return false;
    }

    /*
     * Reads bytes, blocking until all bytes are read.
     */
    private void readFully(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(b, off, len);
            if (n == -1) {
                throw new EOFException();
            }
            off += n;
            len -= n;
        }
    }

}
