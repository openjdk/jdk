/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.util.*;
import java.lang.*;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.nio.ByteBuffer;

/**
 * This MessageDigest class provides applications the functionality of a
 * message digest algorithm, such as SHA-1 or SHA-256.
 * Message digests are secure one-way hash functions that take arbitrary-sized
 * data and output a fixed-length hash value.
 *
 * <p>A MessageDigest object starts out initialized. The data is
 * processed through it using the {@link #update(byte) update}
 * methods. At any point {@link #reset() reset} can be called
 * to reset the digest. Once all the data to be updated has been
 * updated, one of the {@link #digest() digest} methods should
 * be called to complete the hash computation.
 *
 * <p>The <code>digest</code> method can be called once for a given number
 * of updates. After <code>digest</code> has been called, the MessageDigest
 * object is reset to its initialized state.
 *
 * <p>Implementations are free to implement the Cloneable interface.
 * Client applications can test cloneability by attempting cloning
 * and catching the CloneNotSupportedException: <p>
 *
* <pre>
* MessageDigest md = MessageDigest.getInstance("SHA");
*
* try {
*     md.update(toChapter1);
*     MessageDigest tc1 = md.clone();
*     byte[] toChapter1Digest = tc1.digest();
*     md.update(toChapter2);
*     ...etc.
* } catch (CloneNotSupportedException cnse) {
*     throw new DigestException("couldn't make digest of partial content");
* }
* </pre>
 *
 * <p>Note that if a given implementation is not cloneable, it is
 * still possible to compute intermediate digests by instantiating
 * several instances, if the number of digests is known in advance.
 *
 * <p>Note that this class is abstract and extends from
 * <code>MessageDigestSpi</code> for historical reasons.
 * Application developers should only take notice of the methods defined in
 * this <code>MessageDigest</code> class; all the methods in
 * the superclass are intended for cryptographic service providers who wish to
 * supply their own implementations of message digest algorithms.
 *
 * <p> Every implementation of the Java platform is required to support
 * the following standard <code>MessageDigest</code> algorithms:
 * <ul>
 * <li><tt>MD5</tt></li>
 * <li><tt>SHA-1</tt></li>
 * <li><tt>SHA-256</tt></li>
 * </ul>
 * These algorithms are described in the <a href=
 * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
 * MessageDigest section</a> of the
 * Java Cryptography Architecture Standard Algorithm Name Documentation.
 * Consult the release documentation for your implementation to see if any
 * other algorithms are supported.
 *
 * @author Benjamin Renaud
 *
 * @see DigestInputStream
 * @see DigestOutputStream
 */

public abstract class MessageDigest extends MessageDigestSpi {

    private String algorithm;

    // The state of this digest
    private static final int INITIAL = 0;
    private static final int IN_PROGRESS = 1;
    private int state = INITIAL;

    // The provider
    private Provider provider;

    /**
     * Creates a message digest with the specified algorithm name.
     *
     * @param algorithm the standard name of the digest algorithm.
     * See the MessageDigest section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     */
    protected MessageDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Returns a MessageDigest object that implements the specified digest
     * algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new MessageDigest object encapsulating the
     * MessageDigestSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the algorithm requested.
     * See the MessageDigest section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @return a Message Digest object that implements the specified algorithm.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          MessageDigestSpi implementation for the
     *          specified algorithm.
     *
     * @see Provider
     */
    public static MessageDigest getInstance(String algorithm)
    throws NoSuchAlgorithmException {
        try {
            Object[] objs = Security.getImpl(algorithm, "MessageDigest",
                                             (String)null);
            if (objs[0] instanceof MessageDigest) {
                MessageDigest md = (MessageDigest)objs[0];
                md.provider = (Provider)objs[1];
                return md;
            } else {
                MessageDigest delegate =
                    new Delegate((MessageDigestSpi)objs[0], algorithm);
                delegate.provider = (Provider)objs[1];
                return delegate;
            }
        } catch(NoSuchProviderException e) {
            throw new NoSuchAlgorithmException(algorithm + " not found");
        }
    }

    /**
     * Returns a MessageDigest object that implements the specified digest
     * algorithm.
     *
     * <p> A new MessageDigest object encapsulating the
     * MessageDigestSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the algorithm requested.
     * See the MessageDigest section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return a MessageDigest object that implements the specified algorithm.
     *
     * @exception NoSuchAlgorithmException if a MessageDigestSpi
     *          implementation for the specified algorithm is not
     *          available from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception IllegalArgumentException if the provider name is null
     *          or empty.
     *
     * @see Provider
     */
    public static MessageDigest getInstance(String algorithm, String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException
    {
        if (provider == null || provider.length() == 0)
            throw new IllegalArgumentException("missing provider");
        Object[] objs = Security.getImpl(algorithm, "MessageDigest", provider);
        if (objs[0] instanceof MessageDigest) {
            MessageDigest md = (MessageDigest)objs[0];
            md.provider = (Provider)objs[1];
            return md;
        } else {
            MessageDigest delegate =
                new Delegate((MessageDigestSpi)objs[0], algorithm);
            delegate.provider = (Provider)objs[1];
            return delegate;
        }
    }

    /**
     * Returns a MessageDigest object that implements the specified digest
     * algorithm.
     *
     * <p> A new MessageDigest object encapsulating the
     * MessageDigestSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param algorithm the name of the algorithm requested.
     * See the MessageDigest section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the provider.
     *
     * @return a MessageDigest object that implements the specified algorithm.
     *
     * @exception NoSuchAlgorithmException if a MessageDigestSpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the specified provider is null.
     *
     * @see Provider
     *
     * @since 1.4
     */
    public static MessageDigest getInstance(String algorithm,
                                            Provider provider)
        throws NoSuchAlgorithmException
    {
        if (provider == null)
            throw new IllegalArgumentException("missing provider");
        Object[] objs = Security.getImpl(algorithm, "MessageDigest", provider);
        if (objs[0] instanceof MessageDigest) {
            MessageDigest md = (MessageDigest)objs[0];
            md.provider = (Provider)objs[1];
            return md;
        } else {
            MessageDigest delegate =
                new Delegate((MessageDigestSpi)objs[0], algorithm);
            delegate.provider = (Provider)objs[1];
            return delegate;
        }
    }

    /**
     * Returns the provider of this message digest object.
     *
     * @return the provider of this message digest object
     */
    public final Provider getProvider() {
        return this.provider;
    }

    /**
     * Updates the digest using the specified byte.
     *
     * @param input the byte with which to update the digest.
     */
    public void update(byte input) {
        engineUpdate(input);
        state = IN_PROGRESS;
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
        if (input == null) {
            throw new IllegalArgumentException("No input buffer given");
        }
        if (input.length - offset < len) {
            throw new IllegalArgumentException("Input buffer too short");
        }
        engineUpdate(input, offset, len);
        state = IN_PROGRESS;
    }

    /**
     * Updates the digest using the specified array of bytes.
     *
     * @param input the array of bytes.
     */
    public void update(byte[] input) {
        engineUpdate(input, 0, input.length);
        state = IN_PROGRESS;
    }

    /**
     * Update the digest using the specified ByteBuffer. The digest is
     * updated using the <code>input.remaining()</code> bytes starting
     * at <code>input.position()</code>.
     * Upon return, the buffer's position will be equal to its limit;
     * its limit will not have changed.
     *
     * @param input the ByteBuffer
     * @since 1.5
     */
    public final void update(ByteBuffer input) {
        if (input == null) {
            throw new NullPointerException();
        }
        engineUpdate(input);
        state = IN_PROGRESS;
    }

    /**
     * Completes the hash computation by performing final operations
     * such as padding. The digest is reset after this call is made.
     *
     * @return the array of bytes for the resulting hash value.
     */
    public byte[] digest() {
        /* Resetting is the responsibility of implementors. */
        byte[] result = engineDigest();
        state = INITIAL;
        return result;
    }

    /**
     * Completes the hash computation by performing final operations
     * such as padding. The digest is reset after this call is made.
     *
     * @param buf output buffer for the computed digest
     *
     * @param offset offset into the output buffer to begin storing the digest
     *
     * @param len number of bytes within buf allotted for the digest
     *
     * @return the number of bytes placed into <code>buf</code>
     *
     * @exception DigestException if an error occurs.
     */
    public int digest(byte[] buf, int offset, int len) throws DigestException {
        if (buf == null) {
            throw new IllegalArgumentException("No output buffer given");
        }
        if (buf.length - offset < len) {
            throw new IllegalArgumentException
                ("Output buffer too small for specified offset and length");
        }
        int numBytes = engineDigest(buf, offset, len);
        state = INITIAL;
        return numBytes;
    }

    /**
     * Performs a final update on the digest using the specified array
     * of bytes, then completes the digest computation. That is, this
     * method first calls {@link #update(byte[]) update(input)},
     * passing the <i>input</i> array to the <code>update</code> method,
     * then calls {@link #digest() digest()}.
     *
     * @param input the input to be updated before the digest is
     * completed.
     *
     * @return the array of bytes for the resulting hash value.
     */
    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    /**
     * Returns a string representation of this message digest object.
     */
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(baos);
        p.print(algorithm+" Message Digest from "+provider.getName()+", ");
        switch (state) {
        case INITIAL:
            p.print("<initialized>");
            break;
        case IN_PROGRESS:
            p.print("<in progress>");
            break;
        }
        p.println();
        return (baos.toString());
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
    public static boolean isEqual(byte[] digesta, byte[] digestb) {
        if (digesta.length != digestb.length) {
            return false;
        }

        int result = 0;
        // time-constant comparison
        for (int i = 0; i < digesta.length; i++) {
            result |= digesta[i] ^ digestb[i];
        }
        return result == 0;
    }

    /**
     * Resets the digest for further use.
     */
    public void reset() {
        engineReset();
        state = INITIAL;
    }

    /**
     * Returns a string that identifies the algorithm, independent of
     * implementation details. The name should be a standard
     * Java Security name (such as "SHA", "MD5", and so on).
     * See the MessageDigest section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#MessageDigest">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @return the name of the algorithm
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the length of the digest in bytes, or 0 if this operation is
     * not supported by the provider and the implementation is not cloneable.
     *
     * @return the digest length in bytes, or 0 if this operation is not
     * supported by the provider and the implementation is not cloneable.
     *
     * @since 1.2
     */
    public final int getDigestLength() {
        int digestLen = engineGetDigestLength();
        if (digestLen == 0) {
            try {
                MessageDigest md = (MessageDigest)clone();
                byte[] digest = md.digest();
                return digest.length;
            } catch (CloneNotSupportedException e) {
                return digestLen;
            }
        }
        return digestLen;
    }

    /**
     * Returns a clone if the implementation is cloneable.
     *
     * @return a clone if the implementation is cloneable.
     *
     * @exception CloneNotSupportedException if this is called on an
     * implementation that does not support <code>Cloneable</code>.
     */
    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) {
            return super.clone();
        } else {
            throw new CloneNotSupportedException();
        }
    }




    /*
     * The following class allows providers to extend from MessageDigestSpi
     * rather than from MessageDigest. It represents a MessageDigest with an
     * encapsulated, provider-supplied SPI object (of type MessageDigestSpi).
     * If the provider implementation is an instance of MessageDigestSpi,
     * the getInstance() methods above return an instance of this class, with
     * the SPI object encapsulated.
     *
     * Note: All SPI methods from the original MessageDigest class have been
     * moved up the hierarchy into a new class (MessageDigestSpi), which has
     * been interposed in the hierarchy between the API (MessageDigest)
     * and its original parent (Object).
     */

    static class Delegate extends MessageDigest {

        // The provider implementation (delegate)
        private MessageDigestSpi digestSpi;

        // constructor
        public Delegate(MessageDigestSpi digestSpi, String algorithm) {
            super(algorithm);
            this.digestSpi = digestSpi;
        }

        /**
         * Returns a clone if the delegate is cloneable.
         *
         * @return a clone if the delegate is cloneable.
         *
         * @exception CloneNotSupportedException if this is called on a
         * delegate that does not support <code>Cloneable</code>.
         */
        public Object clone() throws CloneNotSupportedException {
            if (digestSpi instanceof Cloneable) {
                MessageDigestSpi digestSpiClone =
                    (MessageDigestSpi)digestSpi.clone();
                // Because 'algorithm', 'provider', and 'state' are private
                // members of our supertype, we must perform a cast to
                // access them.
                MessageDigest that =
                    new Delegate(digestSpiClone,
                                 ((MessageDigest)this).algorithm);
                that.provider = ((MessageDigest)this).provider;
                that.state = ((MessageDigest)this).state;
                return that;
            } else {
                throw new CloneNotSupportedException();
            }
        }

        protected int engineGetDigestLength() {
            return digestSpi.engineGetDigestLength();
        }

        protected void engineUpdate(byte input) {
            digestSpi.engineUpdate(input);
        }

        protected void engineUpdate(byte[] input, int offset, int len) {
            digestSpi.engineUpdate(input, offset, len);
        }

        protected void engineUpdate(ByteBuffer input) {
            digestSpi.engineUpdate(input);
        }

        protected byte[] engineDigest() {
            return digestSpi.engineDigest();
        }

        protected int engineDigest(byte[] buf, int offset, int len)
            throws DigestException {
                return digestSpi.engineDigest(buf, offset, len);
        }

        protected void engineReset() {
            digestSpi.engineReset();
        }
    }
}
