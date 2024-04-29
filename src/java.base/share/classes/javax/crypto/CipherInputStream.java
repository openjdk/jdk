/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@code CipherInputStream} is composed of an {@code InputStream}
 * and a {@code Cipher} object so that read() methods return data that are
 * read in from the underlying {@code InputStream} but have been
 * additionally processed by the {@code Cipher} object.  The {@code Cipher}
 * object must be fully initialized before being used by a
 * {@code CipherInputStream}.
 *
 * <p> For example, if the {@code Cipher} object is initialized for decryption,
 * the {@code CipherInputStream} will attempt to read in data and decrypt
 * them, before returning the decrypted data.
 *
 * <p> This class adheres strictly to the semantics, especially the
 * failure semantics, of its ancestor classes
 * {@code java.io.FilterInputStream} and {@code java.io.InputStream}.
 * This class has exactly those methods specified in its ancestor classes, and
 * overrides them all.  Moreover, this class catches all exceptions
 * that are not thrown by its ancestor classes.  In particular, the
 * {@code skip} method skips, and the {@code available}
 * method counts only data that have been processed by the encapsulated
 * {@code Cipher} object.
 * This class may catch {@code BadPaddingException} and other exceptions
 * thrown by failed integrity checks during decryption. These exceptions are not
 * re-thrown, so the client may not be informed that integrity checks
 * failed. Because of this behavior, this class may not be suitable
 * for use with decryption in an authenticated mode of operation (e.g. GCM).
 * Applications that require authenticated encryption can use the
 * {@code Cipher} API directly as an alternative to using this class.
 *
 * <p> It is crucial for a programmer using this class not to use
 * methods that are not defined or overridden in this class (such as a
 * new method or constructor that is later added to one of the super
 * classes), because the design and implementation of those methods
 * are unlikely to have considered security impact with regard to
 * {@code CipherInputStream}.
 *
 * @author  Li Gong
 * @see     java.io.InputStream
 * @see     java.io.FilterInputStream
 * @see     javax.crypto.Cipher
 * @see     javax.crypto.CipherOutputStream
 *
 * @since 1.4
 */

public class CipherInputStream extends FilterInputStream {

    // the cipher engine to use to process stream data
    private final Cipher cipher;

    // the underlying input stream
    private final InputStream input;

    /* the buffer holding data that have been read in from the
       underlying stream, but have not been processed by the cipher
       engine. */
    private final byte[] ibuffer = new byte[8192];

    // having reached the end of the underlying input stream
    private boolean done = false;

    /* the buffer holding data that have been processed by the cipher
       engine, but have not been read out */
    private byte[] obuffer = null;
    // the offset pointing to the next "new" byte
    private int ostart = 0;
    // the offset pointing to the last "new" byte
    private int ofinish = 0;
    // stream status
    private boolean closed = false;

    /**
     * Ensure obuffer is big enough for the next update or doFinal
     * operation, given the input length {@code inLen} (in bytes)
     * The ostart and ofinish indices are reset to 0.
     *
     * If obuffer is null/zero-sized, do not allocate a new buffer.
     * This reduces allocation for authenticated decryption
     * that never returns data from update
     *
     * @param inLen the input length (in bytes)
     */
    private void ensureCapacity(int inLen) {
        if (obuffer == null || obuffer.length == 0) {
            return;
        }
        int minLen = cipher.getOutputSize(inLen);
        if (obuffer.length < minLen) {
            obuffer = new byte[minLen];
        }
        ostart = 0;
        ofinish = 0;
    }

    /**
     * Private convenience function, read in data from the underlying
     * input stream and process them with cipher. This method is called
     * when the processed bytes inside obuffer has been exhausted.
     *
     * Entry condition: ostart = ofinish
     *
     * Exit condition: ostart = 0 AND ostart <= ofinish
     *
     * return (ofinish-ostart) (we have this many bytes for you)
     * return 0 (no data now, but could have more later)
     * return -1 (absolutely no more data)
     *
     * Note: Exceptions are only thrown after the stream is completely read.
     * For AEAD ciphers a read() of any length will internally cause the
     * whole stream to be read fully and verify the authentication tag before
     * returning decrypted data or exceptions.
     */
    private int getMoreData() throws IOException {
        if (done) return -1;
        int readin = input.read(ibuffer);

        if (readin == -1) {
            done = true;
            ensureCapacity(0);
            try {
                if (obuffer != null && obuffer.length > 0) {
                    ofinish = cipher.doFinal(obuffer, 0);
                } else {
                    obuffer = cipher.doFinal();
                    ofinish = (obuffer != null) ? obuffer.length : 0;
                }
            } catch (IllegalBlockSizeException | BadPaddingException
                    | ShortBufferException e) {
                throw new IOException(e);
            }
            if (ofinish == 0) {
                return -1;
            } else {
                return ofinish;
            }
        }
        ensureCapacity(readin);
        try {
            // initial obuffer is assigned by update/doFinal;
            // for AEAD decryption, obuffer is always null or zero-length here
            if (obuffer != null && obuffer.length > 0) {
                ofinish = cipher.update(ibuffer, 0, readin, obuffer, ostart);
            } else {
                obuffer = cipher.update(ibuffer, 0, readin);
                ofinish = (obuffer != null) ? obuffer.length : 0;
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (ShortBufferException e) {
            throw new IOException(e);
        }
        return ofinish;
    }

    /**
     * Constructs a {@code CipherInputStream} from an
     * {@code InputStream} and a {@code Cipher} object.
     * <br>Note: if the specified input stream or cipher is
     * {@code null}, a {@code NullPointerException} may be thrown later when
     * they are used.
     * @param is the to-be-processed input stream
     * @param c an initialized {@code Cipher} object
     */
    public CipherInputStream(InputStream is, Cipher c) {
        super(is);
        input = is;
        cipher = c;
    }

    /**
     * Constructs a {@code CipherInputStream} from an
     * {@code InputStream} without specifying a {@code Cipher} object.
     * This has the effect of constructing a {@code CipherInputStream}
     * using a {@code NullCipher}.
     * <br>Note: if the specified input stream is {@code null}, a
     * {@code NullPointerException} may be thrown later when it is used.
     * @param is the to-be-processed input stream
     */
    protected CipherInputStream(InputStream is) {
        super(is);
        input = is;
        cipher = new NullCipher();
    }

    /**
     * Reads the next byte of data from this input stream. The value
     * byte is returned as an {@code int} in the range
     * {@code 0} to {@code 255}. If no byte is available
     * because the end of the stream has been reached, the value
     * {@code -1} is returned. This method blocks until input data
     * is available, the end of the stream is detected, or an exception
     * is thrown.
     *
     * @return  the next byte of data, or {@code -1} if the end of the
     *          stream is reached.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        if (ostart >= ofinish) {
            // we loop for new data as the spec says we are blocking
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        return ((int) obuffer[ostart++] & 0xff);
    }

    /**
     * Reads up to {@code b.length} bytes of data from this input
     * stream into an array of bytes.
     * <p>
     * The {@code read} method of {@code InputStream} calls
     * the {@code read} method of three arguments with the arguments
     * {@code b}, {@code 0}, and {@code b.length}.
     *
     * @param      b   the buffer into which the data is read.
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} is there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.InputStream#read(byte[], int, int)
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads up to {@code len} bytes of data from this input stream
     * into an array of bytes. This method blocks until some input is
     * available. If the first argument is {@code null}, up to
     * {@code len} bytes are read and discarded.
     *
     * @param      b     the buffer into which the data is read.
     * @param      off   the start offset in the destination array
     *                   {@code buf}
     * @param      len   the maximum number of bytes read.
     * @return     the total number of bytes read into the buffer, or
     *             {@code -1} if there is no more data because the end of
     *             the stream has been reached.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.InputStream#read()
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (ostart >= ofinish) {
            // we loop for new data as the spec says we are blocking
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        if (len <= 0) {
            return 0;
        }
        int available = ofinish - ostart;
        if (len < available) available = len;
        if (b != null) {
            System.arraycopy(obuffer, ostart, b, off, available);
        }
        ostart = ostart + available;
        return available;
    }

    /**
     * Skips {@code n} bytes of input from the bytes that can be read
     * from this input stream without blocking.
     *
     * <p>Fewer bytes than requested might be skipped.
     * The actual number of bytes skipped is equal to {@code n} or
     * the result of a call to
     * {@link #available() available},
     * whichever is smaller.
     * If {@code n} is less than zero, no bytes are skipped.
     *
     * <p>The actual number of bytes skipped is returned.
     *
     * @param      n the number of bytes to be skipped.
     * @return     the actual number of bytes skipped.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public long skip(long n) throws IOException {
        int available = ofinish - ostart;
        if (n > available) {
            n = available;
        }
        if (n < 0) {
            return 0;
        }
        ostart += (int) n;
        return n;
    }

    /**
     * Returns the number of bytes that can be read from this input
     * stream without blocking. The {@code available} method of
     * {@code InputStream} returns {@code 0}. This method
     * <B>should</B> be overridden by subclasses.
     *
     * @return     the number of bytes that can be read from this input stream
     *             without blocking.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public int available() throws IOException {
        return (ofinish - ostart);
    }

    /**
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * <p>
     * The {@code close} method of {@code CipherInputStream}
     * calls the {@code close} method of its underlying input
     * stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        input.close();

        // Throw away the unprocessed data and throw no crypto exceptions.
        // AEAD ciphers are fully read before closing.  Any authentication
        // exceptions would occur while reading.
        if (!done) {
            ensureCapacity(0);
            try {
                if (obuffer != null && obuffer.length > 0) {
                    cipher.doFinal(obuffer, 0);
                } else {
                    cipher.doFinal();
                }
            } catch (BadPaddingException | IllegalBlockSizeException
                    | ShortBufferException ex) {
                // Catch exceptions as the rest of the stream is unused.
            }
        }
        obuffer = null;
    }

    /**
     * Tests if this input stream supports the {@code mark}
     * and {@code reset} methods, which it does not.
     *
     * @return  {@code false}, since this class does not support the
     *          {@code mark} and {@code reset} methods.
     * @see     java.io.InputStream#mark(int)
     * @see     java.io.InputStream#reset()
     */
    @Override
    public boolean markSupported() {
        return false;
    }
}
