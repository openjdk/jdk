/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

/**
 * A {@code CipherOutputStream} is composed of an {@code OutputStream}
 * and a {@code Cipher} object so that write() methods first process the data
 * before writing them out to the underlying {@code OutputStream}.
 * The {@code Cipher} object must be fully initialized before being used by a
 * {@code CipherOutputStream}.
 *
 * <p> For example, if the {@code Cipher} object is initialized for encryption,
 * the {@code CipherOutputStream} will attempt to encrypt data before
 * writing out the encrypted data.
 *
 * <p> This class adheres strictly to the semantics, especially the
 * failure semantics, of its ancestor classes
 * {@code java.io.OutputStream} and
 * {@code java.io.FilterOutputStream}.
 * This class has exactly those methods specified in its ancestor classes, and
 * overrides them all.  Moreover, this class catches all exceptions
 * that are not thrown by its ancestor classes. In particular, this
 * class catches {@code BadPaddingException} and other exceptions thrown by
 * failed integrity checks during decryption. These exceptions are not
 * re-thrown, so the client will not be informed that integrity checks
 * failed. Because of this behavior, this class may not be suitable
 * for use with decryption in an authenticated mode of operation (e.g. GCM)
 * if the application requires explicit notification when authentication
 * fails. Such an application can use the {@code Cipher} API directly as
 * an alternative to using this class.
 *
 * <p> It is crucial for a programmer using this class not to use
 * methods that are not defined or overridden in this class (such as a
 * new method or constructor that is later added to one of the super
 * classes), because the design and implementation of those methods
 * are unlikely to have considered security impact with regard to
 * {@code CipherOutputStream}.
 *
 * @author  Li Gong
 * @see     java.io.OutputStream
 * @see     java.io.FilterOutputStream
 * @see     javax.crypto.Cipher
 * @see     javax.crypto.CipherInputStream
 *
 * @since 1.4
 */

public class CipherOutputStream extends FilterOutputStream {

    // the cipher engine to use to process stream data
    private final Cipher cipher;

    // the underlying output stream
    private final OutputStream output;

    /* the buffer holding one byte of incoming data */
    private final byte[] ibuffer = new byte[1];

    // the buffer holding data ready to be written out
    private byte[] obuffer = null;

    // stream status
    private boolean closed = false;

    /**
     * Ensure obuffer is big enough for the next update or doFinal
     * operation, given the input length {@code inLen} (in bytes)
     *
     * @param inLen the input length (in bytes)
     */
    private void ensureCapacity(int inLen) {
        int minLen = cipher.getOutputSize(inLen);
        if (obuffer == null || obuffer.length < minLen) {
            obuffer = new byte[minLen];
        }
    }

    /**
     *
     * Constructs a {@code CipherOutputStream} from an
     * {@code OutputStream} and a {@code Cipher} object.
     * <br>Note: if the specified output stream or cipher is
     * {@code null}, {@code a NullPointerException} may be thrown later when
     * they are used.
     *
     * @param os  the {@code OutputStream} object
     * @param c   an initialized {@code Cipher} object
     */
    public CipherOutputStream(OutputStream os, Cipher c) {
        super(os);
        output = os;
        cipher = c;
    }

    /**
     * Constructs a {@code CipherOutputStream} from an
     * {@code OutputStream} without specifying a {@code Cipher} object.
     * This has the effect of constructing a {@code CipherOutputStream}
     * using a {@code NullCipher}.
     * <br>Note: if the specified output stream is {@code null}, a
     * {@code NullPointerException} may be thrown later when it is used.
     *
     * @param os  the {@code OutputStream} object
     */
    protected CipherOutputStream(OutputStream os) {
        super(os);
        output = os;
        cipher = new NullCipher();
    }

    /**
     * Writes the specified byte to this output stream.
     *
     * @param      b   the {@code byte}.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
        ibuffer[0] = (byte) b;
        ensureCapacity(1);
        try {
            int ostored = cipher.update(ibuffer, 0, 1, obuffer);
            if (ostored > 0) {
                output.write(obuffer, 0, ostored);
            }
        } catch (ShortBufferException sbe) {
            // should never happen; re-throw just in case
            throw new IOException(sbe);
        }
    }

    /**
     * Writes {@code b.length} bytes from the specified byte array
     * to this output stream.
     * <p>
     * The {@code write} method of
     * {@code CipherOutputStream} calls the {@code write}
     * method of three arguments with the three arguments
     * {@code b}, {@code 0}, and {@code b.length}.
     *
     * @param      b   the data.
     * @exception  NullPointerException if {@code b} is {@code null}.
     * @exception  IOException  if an I/O error occurs.
     * @see        javax.crypto.CipherOutputStream#write(byte[], int, int)
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this output stream.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureCapacity(len);
        try {
            int ostored = cipher.update(b, off, len, obuffer);
            if (ostored > 0) {
                output.write(obuffer, 0, ostored);
            }
        } catch (ShortBufferException e) {
            // should never happen; re-throw just in case
            throw new IOException(e);
        }
    }

    /**
     * Flushes this output stream by forcing any buffered output bytes
     * that have already been processed by the encapsulated {@code Cipher}
     * object to be written out.
     *
     * <p>Any bytes buffered by the encapsulated {@code Cipher} object
     * and waiting to be processed by it will not be written out. For example,
     * if the encapsulated {@code Cipher} object is a block cipher, and the
     * total number of bytes written using one of the {@code write}
     * methods is less than the cipher's block size, no bytes will be written
     * out.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        // simply call output.flush() since 'obuffer' content is always
        // written out immediately
        output.flush();
    }

    /**
     * Closes this output stream and releases any system resources
     * associated with this stream.
     * <p>
     * This method invokes the {@code doFinal} method of the encapsulated
     * {@code Cipher} object, which causes any bytes buffered by the
     * encapsulated {@code Cipher} object to be processed. The result is written
     * out by calling the {@code flush} method of this output stream.
     * <p>
     * This method resets the encapsulated {@code Cipher} object to its
     * initial state and calls the {@code close} method of the underlying
     * output stream.
     *
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        ensureCapacity(0);
        try {
            int ostored = cipher.doFinal(obuffer, 0);
            if (ostored > 0) {
                output.write(obuffer, 0, ostored);
            }
        } catch (IllegalBlockSizeException | BadPaddingException
                | ShortBufferException e) {
        }
        obuffer = null;
        try {
            flush();
        } catch (IOException ignored) {}
        output.close();
    }
}
