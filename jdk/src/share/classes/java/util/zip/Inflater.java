/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class provides support for general purpose decompression using the
 * popular ZLIB compression library. The ZLIB compression library was
 * initially developed as part of the PNG graphics standard and is not
 * protected by patents. It is fully described in the specifications at
 * the <a href="package-summary.html#package_description">java.util.zip
 * package description</a>.
 *
 * <p>The following code fragment demonstrates a trivial compression
 * and decompression of a string using <tt>Deflater</tt> and
 * <tt>Inflater</tt>.
 *
 * <blockquote><pre>
 * try {
 *     // Encode a String into bytes
 *     String inputString = "blahblahblah\u20AC\u20AC";
 *     byte[] input = inputString.getBytes("UTF-8");
 *
 *     // Compress the bytes
 *     byte[] output = new byte[100];
 *     Deflater compresser = new Deflater();
 *     compresser.setInput(input);
 *     compresser.finish();
 *     int compressedDataLength = compresser.deflate(output);
 *
 *     // Decompress the bytes
 *     Inflater decompresser = new Inflater();
 *     decompresser.setInput(output, 0, compressedDataLength);
 *     byte[] result = new byte[100];
 *     int resultLength = decompresser.inflate(result);
 *     decompresser.end();
 *
 *     // Decode the bytes into a String
 *     String outputString = new String(result, 0, resultLength, "UTF-8");
 * } catch(java.io.UnsupportedEncodingException ex) {
 *     // handle
 * } catch (java.util.zip.DataFormatException ex) {
 *     // handle
 * }
 * </pre></blockquote>
 *
 * @see         Deflater
 * @author      David Connelly
 *
 */
public
class Inflater {

    private final ZStreamRef zsRef;
    private byte[] buf = defaultBuf;
    private int off, len;
    private boolean finished;
    private boolean needDict;

    private static final byte[] defaultBuf = new byte[0];

    static {
        /* Zip library is loaded from System.initializeSystemClass */
        initIDs();
    }

    /**
     * Creates a new decompressor. If the parameter 'nowrap' is true then
     * the ZLIB header and checksum fields will not be used. This provides
     * compatibility with the compression format used by both GZIP and PKZIP.
     * <p>
     * Note: When using the 'nowrap' option it is also necessary to provide
     * an extra "dummy" byte as input. This is required by the ZLIB native
     * library in order to support certain optimizations.
     *
     * @param nowrap if true then support GZIP compatible compression
     */
    public Inflater(boolean nowrap) {
        zsRef = new ZStreamRef(init(nowrap));
    }

    /**
     * Creates a new decompressor.
     */
    public Inflater() {
        this(false);
    }

    /**
     * Sets input data for decompression. Should be called whenever
     * needsInput() returns true indicating that more input data is
     * required.
     * @param b the input data bytes
     * @param off the start offset of the input data
     * @param len the length of the input data
     * @see Inflater#needsInput
     */
    public void setInput(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            this.buf = b;
            this.off = off;
            this.len = len;
        }
    }

    /**
     * Sets input data for decompression. Should be called whenever
     * needsInput() returns true indicating that more input data is
     * required.
     * @param b the input data bytes
     * @see Inflater#needsInput
     */
    public void setInput(byte[] b) {
        setInput(b, 0, b.length);
    }

    /**
     * Sets the preset dictionary to the given array of bytes. Should be
     * called when inflate() returns 0 and needsDictionary() returns true
     * indicating that a preset dictionary is required. The method getAdler()
     * can be used to get the Adler-32 value of the dictionary needed.
     * @param b the dictionary data bytes
     * @param off the start offset of the data
     * @param len the length of the data
     * @see Inflater#needsDictionary
     * @see Inflater#getAdler
     */
    public void setDictionary(byte[] b, int off, int len) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            ensureOpen();
            setDictionary(zsRef.address(), b, off, len);
            needDict = false;
        }
    }

    /**
     * Sets the preset dictionary to the given array of bytes. Should be
     * called when inflate() returns 0 and needsDictionary() returns true
     * indicating that a preset dictionary is required. The method getAdler()
     * can be used to get the Adler-32 value of the dictionary needed.
     * @param b the dictionary data bytes
     * @see Inflater#needsDictionary
     * @see Inflater#getAdler
     */
    public void setDictionary(byte[] b) {
        setDictionary(b, 0, b.length);
    }

    /**
     * Returns the total number of bytes remaining in the input buffer.
     * This can be used to find out what bytes still remain in the input
     * buffer after decompression has finished.
     * @return the total number of bytes remaining in the input buffer
     */
    public int getRemaining() {
        synchronized (zsRef) {
            return len;
        }
    }

    /**
     * Returns true if no data remains in the input buffer. This can
     * be used to determine if #setInput should be called in order
     * to provide more input.
     * @return true if no data remains in the input buffer
     */
    public boolean needsInput() {
        synchronized (zsRef) {
            return len <= 0;
        }
    }

    /**
     * Returns true if a preset dictionary is needed for decompression.
     * @return true if a preset dictionary is needed for decompression
     * @see Inflater#setDictionary
     */
    public boolean needsDictionary() {
        synchronized (zsRef) {
            return needDict;
        }
    }

    /**
     * Returns true if the end of the compressed data stream has been
     * reached.
     * @return true if the end of the compressed data stream has been
     * reached
     */
    public boolean finished() {
        synchronized (zsRef) {
            return finished;
        }
    }

    /**
     * Uncompresses bytes into specified buffer. Returns actual number
     * of bytes uncompressed. A return value of 0 indicates that
     * needsInput() or needsDictionary() should be called in order to
     * determine if more input data or a preset dictionary is required.
     * In the latter case, getAdler() can be used to get the Adler-32
     * value of the dictionary required.
     * @param b the buffer for the uncompressed data
     * @param off the start offset of the data
     * @param len the maximum number of uncompressed bytes
     * @return the actual number of uncompressed bytes
     * @exception DataFormatException if the compressed data format is invalid
     * @see Inflater#needsInput
     * @see Inflater#needsDictionary
     */
    public int inflate(byte[] b, int off, int len)
        throws DataFormatException
    {
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (zsRef) {
            ensureOpen();
            return inflateBytes(zsRef.address(), b, off, len);
        }
    }

    /**
     * Uncompresses bytes into specified buffer. Returns actual number
     * of bytes uncompressed. A return value of 0 indicates that
     * needsInput() or needsDictionary() should be called in order to
     * determine if more input data or a preset dictionary is required.
     * In the latter case, getAdler() can be used to get the Adler-32
     * value of the dictionary required.
     * @param b the buffer for the uncompressed data
     * @return the actual number of uncompressed bytes
     * @exception DataFormatException if the compressed data format is invalid
     * @see Inflater#needsInput
     * @see Inflater#needsDictionary
     */
    public int inflate(byte[] b) throws DataFormatException {
        return inflate(b, 0, b.length);
    }

    /**
     * Returns the ADLER-32 value of the uncompressed data.
     * @return the ADLER-32 value of the uncompressed data
     */
    public int getAdler() {
        synchronized (zsRef) {
            ensureOpen();
            return getAdler(zsRef.address());
        }
    }

    /**
     * Returns the total number of compressed bytes input so far.
     *
     * <p>Since the number of bytes may be greater than
     * Integer.MAX_VALUE, the {@link #getBytesRead()} method is now
     * the preferred means of obtaining this information.</p>
     *
     * @return the total number of compressed bytes input so far
     */
    public int getTotalIn() {
        return (int) getBytesRead();
    }

    /**
     * Returns the total number of compressed bytes input so far.</p>
     *
     * @return the total (non-negative) number of compressed bytes input so far
     * @since 1.5
     */
    public long getBytesRead() {
        synchronized (zsRef) {
            ensureOpen();
            return getBytesRead(zsRef.address());
        }
    }

    /**
     * Returns the total number of uncompressed bytes output so far.
     *
     * <p>Since the number of bytes may be greater than
     * Integer.MAX_VALUE, the {@link #getBytesWritten()} method is now
     * the preferred means of obtaining this information.</p>
     *
     * @return the total number of uncompressed bytes output so far
     */
    public int getTotalOut() {
        return (int) getBytesWritten();
    }

    /**
     * Returns the total number of uncompressed bytes output so far.</p>
     *
     * @return the total (non-negative) number of uncompressed bytes output so far
     * @since 1.5
     */
    public long getBytesWritten() {
        synchronized (zsRef) {
            ensureOpen();
            return getBytesWritten(zsRef.address());
        }
    }

    /**
     * Resets inflater so that a new set of input data can be processed.
     */
    public void reset() {
        synchronized (zsRef) {
            ensureOpen();
            reset(zsRef.address());
            buf = defaultBuf;
            finished = false;
            needDict = false;
            off = len = 0;
        }
    }

    /**
     * Closes the decompressor and discards any unprocessed input.
     * This method should be called when the decompressor is no longer
     * being used, but will also be called automatically by the finalize()
     * method. Once this method is called, the behavior of the Inflater
     * object is undefined.
     */
    public void end() {
        synchronized (zsRef) {
            long addr = zsRef.address();
            zsRef.clear();
            if (addr != 0) {
                end(addr);
                buf = null;
            }
        }
    }

    /**
     * Closes the decompressor when garbage is collected.
     */
    protected void finalize() {
        end();
    }

    private void ensureOpen () {
        assert Thread.holdsLock(zsRef);
        if (zsRef.address() == 0)
            throw new NullPointerException("Inflater has been closed");
    }

    boolean ended() {
        synchronized (zsRef) {
            return zsRef.address() == 0;
        }
    }

    private native static void initIDs();
    private native static long init(boolean nowrap);
    private native static void setDictionary(long addr, byte[] b, int off,
                                             int len);
    private native int inflateBytes(long addr, byte[] b, int off, int len)
            throws DataFormatException;
    private native static int getAdler(long addr);
    private native static long getBytesRead(long addr);
    private native static long getBytesWritten(long addr);
    private native static void reset(long addr);
    private native static void end(long addr);
}
