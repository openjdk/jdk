/*
 * Copyright 2008 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * This is a combination and adaptation of subsets of
 * <code>java.security.MessageDigest</code> and
 * <code>sun.security.provider.DigestBase</code> to provide a class offering
 * most of the same public methods of <code>MessageDigest</code> while not
 * depending on the Java Security Framework.
 * <p>
 * One algorithm is currently supported: "SHA-1".
 * <p>
 * NOTE If <code>java.security.MessageDigest</code>,
 * <code>sun.security.provider.DigestBase</code> or
 * <code>sun.security.provider.SHA</code> are modified, review of those
 * modifications should be done to determine any possible implications for this
 * class and <code>StandaloneSHA</code>.
 */

package sun.jkernel;

import java.security.DigestException;
import java.security.ProviderException;
import java.security.NoSuchAlgorithmException;

/**
 * (Adapted from the <code>sun.security.provider.DigestBase</code> doc).
 * This is a simple subset of the Common base message digest implementation
 * for the Sun provider.
 * It implements most of the JCA methods as suitable for a Java message
 * digest
 * implementation of an algorithm based on a compression function (as all
 * commonly used algorithms are). The individual digest subclasses only need to
 * implement the following methods:
 *
 *  . abstract void implCompress(byte[] b, int ofs);
 *  . abstract void implDigest(byte[] out, int ofs);
 *  . abstract void implReset();
 * <p>
 * No support for a clone() method is provided.
 * <p>
 * See the inline documentation for details.
 *
 * @since   1.5
 * @version 1.3, 08/08/07
 * @author  Andreas Sterbenz (MessageDigest)
 * @author  Pete Soper (this derived class)
 */
public abstract class StandaloneMessageDigest {

     public static final boolean debug = false;

    /*
     * (Copied/adapted from <code>java.security.MessageDigest</code>
     *
     * This is a subset/simplification <code>java.security.MessageDigest</code>
     * that supports a fixed set of hashcode mechanisms (currently just
     * SHA-1) while preserving the following MessageDigest methods:
     *
     * public MessageDigest getInstance(String algorithm)
     * public final int getDigestLength()
     * public void reset()
     * public byte[] digest()
     * public void update(byte[] input, int offset, int len)
     * public final String getAlgorithm()
     * <p>
     * NOTE that the clone() method is not provided.
     */

    /**
     * Prevent direct instantiation except via the factory method.
     */

    private StandaloneMessageDigest() {
        // Keep javac happy.
        digestLength = 0;
        blockSize = 0;
        algorithm = null;
        buffer = null;
    }

    private String algorithm;

    // The state of this digest
    private static final int INITIAL = 0;
    private static final int IN_PROGRESS = 1;
    private int state = INITIAL;

    /**
     * Returns a StandaloneMessageDigest object that implements the specified
     * digest algorithm.
     *
     * <p> This method returns a new StandaloneMessageDigest for a single
     * algorithm provider.
     *
     * @param algorithm the name of the algorithm requested.
     *
     * @return a standalone Message Digest object that implements the specified algorithm.
     *
     * @exception NoSuchAlgorithmException if algorithm not supported
     *
     */
    public static StandaloneMessageDigest getInstance(String algorithm)
        throws NoSuchAlgorithmException {
        if (! algorithm.equals("SHA-1")) {
            throw new NoSuchAlgorithmException(algorithm + " not found");
        } else {
            return new StandaloneSHA();
        }
    }

    /**
     * Updates the digest using the specified array of bytes, starting
     * at the specified offset.
     *
     * @param input the array of bytes.
     *
     * @param offset the offset to start from in the array of bytes.
     *
     * @param len the number of bytes to use, starting at
     * <code>offset</code>.
     */
    public void update(byte[] input, int offset, int len) {
        if (debug) {
            System.out.println("StandaloneMessageDigest.update");
            (new Exception()).printStackTrace();
        }
        if (input == null) {
            throw new IllegalArgumentException("No input buffer given");
        }
        if (input.length - offset < len) {
            throw new IllegalArgumentException("Input buffer too short");
        }
        // No need to check for negative offset: engineUpdate does this

        engineUpdate(input, offset, len);
        state = IN_PROGRESS;
    }

    /**
     * Completes the hash computation by performing final operations
     * such as padding. The digest is reset after this call is made.
     *
     * @return the array of bytes for the resulting hash value.
     */
    public byte[] digest() {
        if (debug) {
            System.out.println("StandaloneMessageDigest.digest");
        }
        /* Resetting is the responsibility of implementors. */
        byte[] result = engineDigest();
        state = INITIAL;
        return result;
    }

    /**
     * Compares two digests for equality. Does a simple byte compare.
     *
     * @param digesta one of the digests to compare.
     *
     * @param digestb the other digest to compare.
     *
     * @return true if the digests are equal, false otherwise.
     */
    public static boolean isEqual(byte digesta[], byte digestb[]) {
        if (digesta.length != digestb.length)
            return false;

        for (int i = 0; i < digesta.length; i++) {
            if (digesta[i] != digestb[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resets the digest for further use.
     */
    public void reset() {
        if (debug) {
            System.out.println("StandaloneMessageDigest.reset");
        }
        engineReset();
        state = INITIAL;
    }

    /**
     * Returns a string that identifies the algorithm, independent of
     * implementation details. The name should be a standard
     * Java Security name (such as "SHA", "MD5", and so on).
     * See Appendix A in the <a href=
     * "../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @return the name of the algorithm
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the length of the digest in bytes.
     *
     * @return the digest length in bytes.
     *
     * @since 1.2
     */
    public final int getDigestLength() {
        return engineGetDigestLength();
    }

    //* End of copied/adapted <code>java.security.MessageDigest</code>

    // Start of copied/adapted <code>sun.security.provider.DigestBase</code>

    // one element byte array, temporary storage for update(byte)
    private byte[] oneByte;

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
    StandaloneMessageDigest(String algorithm, int digestLength, int blockSize) {
        // super();
        this.algorithm = algorithm;
        this.digestLength = digestLength;
        this.blockSize = blockSize;
        buffer = new byte[blockSize];
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
    protected final byte[] engineDigest() throws ProviderException {
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
     * StandaloneMessageDigest calls implReset() when necessary.
     */
    abstract void implDigest(byte[] out, int ofs);

    /**
     * Reset subclass specific state to their initial values. StandaloneMessageDigest
     * calls this method when necessary.
     */
    abstract void implReset();

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
