/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.provider;

import java.security.MessageDigestSpi;
import java.security.DigestException;
import java.security.ProviderException;

/**
 * Common base message digest implementation for the Sun provider.
 * It implements all the JCA methods as suitable for a Java message digest
 * implementation of an algorithm based on a compression function (as all
 * commonly used algorithms are). The individual digest subclasses only need to
 * implement the following methods:
 *
 *  . abstract void implCompress(byte[] b, int ofs);
 *  . abstract void implDigest(byte[] out, int ofs);
 *  . abstract void implReset();
 *  . public abstract Object clone();
 *
 * See the inline documentation for details.
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
abstract class DigestBase extends MessageDigestSpi implements Cloneable {

    // one element byte array, temporary storage for update(byte)
    private byte[] oneByte;

    // algorithm name to use in the exception message
    private final String algorithm;
    // length of the message digest in bytes
    private final int digestLength;

    // size of the input to the compression function in bytes
    private final int blockSize;
    // buffer to store partial blocks, blockSize bytes large
    // Subclasses should not access this array directly except possibly in their
    // implDigest() method. See MD5.java as an example.
    final byte[] buffer;
    // offset into buffer
    private int bufOfs;

    // number of bytes processed so far. subclasses should not modify
    // this value.
    // also used as a flag to indicate reset status
    // -1: need to call engineReset() before next call to update()
    //  0: is already reset
    long bytesProcessed;

    /**
     * Main constructor.
     */
    DigestBase(String algorithm, int digestLength, int blockSize) {
        super();
        this.algorithm = algorithm;
        this.digestLength = digestLength;
        this.blockSize = blockSize;
        buffer = new byte[blockSize];
    }

    /**
     * Constructor for cloning. Replicates common data.
     */
    DigestBase(DigestBase base) {
        this.algorithm = base.algorithm;
        this.digestLength = base.digestLength;
        this.blockSize = base.blockSize;
        this.buffer = base.buffer.clone();
        this.bufOfs = base.bufOfs;
        this.bytesProcessed = base.bytesProcessed;
    }

    // return digest length. See JCA doc.
    protected final int engineGetDigestLength() {
        return digestLength;
    }

    // single byte update. See JCA doc.
    protected final void engineUpdate(byte b) {
        if (oneByte == null) {
            oneByte = new byte[1];
        }
        oneByte[0] = b;
        engineUpdate(oneByte, 0, 1);
    }

    // array update. See JCA doc.
    protected final void engineUpdate(byte[] b, int ofs, int len) {
        if (len == 0) {
            return;
        }
        if ((ofs < 0) || (len < 0) || (ofs > b.length - len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (bytesProcessed < 0) {
            engineReset();
        }
        bytesProcessed += len;
        // if buffer is not empty, we need to fill it before proceeding
        if (bufOfs != 0) {
            int n = Math.min(len, blockSize - bufOfs);
            System.arraycopy(b, ofs, buffer, bufOfs, n);
            bufOfs += n;
            ofs += n;
            len -= n;
            if (bufOfs >= blockSize) {
                // compress completed block now
                implCompress(buffer, 0);
                bufOfs = 0;
            }
        }
        // compress complete blocks
        while (len >= blockSize) {
            implCompress(b, ofs);
            len -= blockSize;
            ofs += blockSize;
        }
        // copy remainder to buffer
        if (len > 0) {
            System.arraycopy(b, ofs, buffer, 0, len);
            bufOfs = len;
        }
    }

    // reset this object. See JCA doc.
    protected final void engineReset() {
        if (bytesProcessed == 0) {
            // already reset, ignore
            return;
        }
        implReset();
        bufOfs = 0;
        bytesProcessed = 0;
    }

    // return the digest. See JCA doc.
    protected final byte[] engineDigest() {
        byte[] b = new byte[digestLength];
        try {
            engineDigest(b, 0, b.length);
        } catch (DigestException e) {
            throw (ProviderException)
                new ProviderException("Internal error").initCause(e);
        }
        return b;
    }

    // return the digest in the specified array. See JCA doc.
    protected final int engineDigest(byte[] out, int ofs, int len)
            throws DigestException {
        if (len < digestLength) {
            throw new DigestException("Length must be at least "
                + digestLength + " for " + algorithm + "digests");
        }
        if ((ofs < 0) || (len < 0) || (ofs > out.length - len)) {
            throw new DigestException("Buffer too short to store digest");
        }
        if (bytesProcessed < 0) {
            engineReset();
        }
        implDigest(out, ofs);
        bytesProcessed = -1;
        return digestLength;
    }

    /**
     * Core compression function. Processes blockSize bytes at a time
     * and updates the state of this object.
     */
    abstract void implCompress(byte[] b, int ofs);

    /**
     * Return the digest. Subclasses do not need to reset() themselves,
     * DigestBase calls implReset() when necessary.
     */
    abstract void implDigest(byte[] out, int ofs);

    /**
     * Reset subclass specific state to their initial values. DigestBase
     * calls this method when necessary.
     */
    abstract void implReset();

    /**
     * Clone this digest. Should be implemented as "return new MyDigest(this)".
     * That constructor should first call "super(baseDigest)" and then copy
     * subclass specific data.
     */
    public abstract Object clone();

    // padding used for the MD5, and SHA-* message digests
    static final byte[] padding;

    static {
        // we need 128 byte padding for SHA-384/512
        // and an additional 8 bytes for the high 8 bytes of the 16
        // byte bit counter in SHA-384/512
        padding = new byte[136];
        padding[0] = (byte)0x80;
    }

}
