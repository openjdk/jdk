/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package java.io;

import java.nio.charset.Charset;

/**
 * This class extends {@code ByteArrayOutputStream} but overrides the
 * synchronized methods with non-synchronized equivalents.
 *
 * @see java.io.ByteArrayOutputStream
 * @author John Engebretson
 * @since 25
 */

class UnsynchronizedByteArrayOutputStream extends ByteArrayOutputStream {

    /**
     * Creates a new {@code UnsynchronizedByteArrayOutputStream}.
     * 
     * @see java.io.ByteArrayOutputStream#unsynchronizedInstance
     * @since 25
     */
    UnsynchronizedByteArrayOutputStream() {
        super();
    }

    /**
     * Creates a new {@code UnsynchronizedByteArrayOutputStream},
     *
     * @param size the initial size.
     * @throws IllegalArgumentException if size is negative.
     * @see java.io.ByteArrayOutputStream#unsynchronizedInstance
     * @since 25
     */
    UnsynchronizedByteArrayOutputStream(int size) {
        super(size);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public void reset() {
        internalReset();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public int size() {
        return internalSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long sizeAsLong() {
        return internalSize();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public byte[] toByteArray() {
        return internalToByteArray();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public String toString() {
        return internalToString();
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public String toString(Charset charset) {
        return internalToString(charset);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public String toString(String charsetName) throws UnsupportedEncodingException {
        return internalToString(charsetName);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public void write(byte[] b, int off, int len) {
        internalWrite(b, off, len);
    }


    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public void write(int b) {
        internalWrite(b);
    }

    /**
     * {@inheritDoc}
     * 
     * @since 25
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
        internalWriteTo(out);
    }

}
