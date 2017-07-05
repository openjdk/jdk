/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security;

import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import java.io.*;

import java.nio.ByteBuffer;

import sun.security.jca.JCAUtil;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the <code>Signature</code> class, which is used to provide the
 * functionality of a digital signature algorithm. Digital signatures are used
 * for authentication and integrity assurance of digital data.
 *.
 * <p> All the abstract methods in this class must be implemented by each
 * cryptographic service provider who wishes to supply the implementation
 * of a particular signature algorithm.
 *
 * @author Benjamin Renaud
 *
 *
 * @see Signature
 */

public abstract class SignatureSpi {

    /**
     * Application-specified source of randomness.
     */
    protected SecureRandom appRandom = null;

    /**
     * Initializes this signature object with the specified
     * public key for verification operations.
     *
     * @param publicKey the public key of the identity whose signature is
     * going to be verified.
     *
     * @exception InvalidKeyException if the key is improperly
     * encoded, parameters are missing, and so on.
     */
    protected abstract void engineInitVerify(PublicKey publicKey)
        throws InvalidKeyException;

    /**
     * Initializes this signature object with the specified
     * private key for signing operations.
     *
     * @param privateKey the private key of the identity whose signature
     * will be generated.
     *
     * @exception InvalidKeyException if the key is improperly
     * encoded, parameters are missing, and so on.
     */
    protected abstract void engineInitSign(PrivateKey privateKey)
        throws InvalidKeyException;

    /**
     * Initializes this signature object with the specified
     * private key and source of randomness for signing operations.
     *
     * <p>This concrete method has been added to this previously-defined
     * abstract class. (For backwards compatibility, it cannot be abstract.)
     *
     * @param privateKey the private key of the identity whose signature
     * will be generated.
     * @param random the source of randomness
     *
     * @exception InvalidKeyException if the key is improperly
     * encoded, parameters are missing, and so on.
     */
    protected void engineInitSign(PrivateKey privateKey,
                                  SecureRandom random)
        throws InvalidKeyException {
            this.appRandom = random;
            engineInitSign(privateKey);
    }

    /**
     * Updates the data to be signed or verified
     * using the specified byte.
     *
     * @param b the byte to use for the update.
     *
     * @exception SignatureException if the engine is not initialized
     * properly.
     */
    protected abstract void engineUpdate(byte b) throws SignatureException;

    /**
     * Updates the data to be signed or verified, using the
     * specified array of bytes, starting at the specified offset.
     *
     * @param b the array of bytes
     * @param off the offset to start from in the array of bytes
     * @param len the number of bytes to use, starting at offset
     *
     * @exception SignatureException if the engine is not initialized
     * properly
     */
    protected abstract void engineUpdate(byte[] b, int off, int len)
        throws SignatureException;

    /**
     * Updates the data to be signed or verified using the specified
     * ByteBuffer. Processes the <code>data.remaining()</code> bytes
     * starting at at <code>data.position()</code>.
     * Upon return, the buffer's position will be equal to its limit;
     * its limit will not have changed.
     *
     * @param input the ByteBuffer
     * @since 1.5
     */
    protected void engineUpdate(ByteBuffer input) {
        if (input.hasRemaining() == false) {
            return;
        }
        try {
            if (input.hasArray()) {
                byte[] b = input.array();
                int ofs = input.arrayOffset();
                int pos = input.position();
                int lim = input.limit();
                engineUpdate(b, ofs + pos, lim - pos);
                input.position(lim);
            } else {
                int len = input.remaining();
                byte[] b = new byte[JCAUtil.getTempArraySize(len)];
                while (len > 0) {
                    int chunk = Math.min(len, b.length);
                    input.get(b, 0, chunk);
                    engineUpdate(b, 0, chunk);
                    len -= chunk;
                }
            }
        } catch (SignatureException e) {
            // is specified to only occur when the engine is not initialized
            // this case should never occur as it is caught in Signature.java
            throw new ProviderException("update() failed", e);
        }
    }

    /**
     * Returns the signature bytes of all the data
     * updated so far.
     * The format of the signature depends on the underlying
     * signature scheme.
     *
     * @return the signature bytes of the signing operation's result.
     *
     * @exception SignatureException if the engine is not
     * initialized properly or if this signature algorithm is unable to
     * process the input data provided.
     */
    protected abstract byte[] engineSign() throws SignatureException;

    /**
     * Finishes this signature operation and stores the resulting signature
     * bytes in the provided buffer <code>outbuf</code>, starting at
     * <code>offset</code>.
     * The format of the signature depends on the underlying
     * signature scheme.
     *
     * <p>The signature implementation is reset to its initial state
     * (the state it was in after a call to one of the
     * <code>engineInitSign</code> methods)
     * and can be reused to generate further signatures with the same private
     * key.
     *
     * This method should be abstract, but we leave it concrete for
     * binary compatibility.  Knowledgeable providers should override this
     * method.
     *
     * @param outbuf buffer for the signature result.
     *
     * @param offset offset into <code>outbuf</code> where the signature is
     * stored.
     *
     * @param len number of bytes within <code>outbuf</code> allotted for the
     * signature.
     * Both this default implementation and the SUN provider do not
     * return partial digests. If the value of this parameter is less
     * than the actual signature length, this method will throw a
     * SignatureException.
     * This parameter is ignored if its value is greater than or equal to
     * the actual signature length.
     *
     * @return the number of bytes placed into <code>outbuf</code>
     *
     * @exception SignatureException if the engine is not
     * initialized properly, if this signature algorithm is unable to
     * process the input data provided, or if <code>len</code> is less
     * than the actual signature length.
     *
     * @since 1.2
     */
    protected int engineSign(byte[] outbuf, int offset, int len)
                        throws SignatureException {
        byte[] sig = engineSign();
        if (len < sig.length) {
                throw new SignatureException
                    ("partial signatures not returned");
        }
        if (outbuf.length - offset < sig.length) {
                throw new SignatureException
                    ("insufficient space in the output buffer to store the "
                     + "signature");
        }
        System.arraycopy(sig, 0, outbuf, offset, sig.length);
        return sig.length;
    }

    /**
     * Verifies the passed-in signature.
     *
     * @param sigBytes the signature bytes to be verified.
     *
     * @return true if the signature was verified, false if not.
     *
     * @exception SignatureException if the engine is not
     * initialized properly, the passed-in signature is improperly
     * encoded or of the wrong type, if this signature algorithm is unable to
     * process the input data provided, etc.
     */
    protected abstract boolean engineVerify(byte[] sigBytes)
        throws SignatureException;

    /**
     * Verifies the passed-in signature in the specified array
     * of bytes, starting at the specified offset.
     *
     * <p> Note: Subclasses should overwrite the default implementation.
     *
     *
     * @param sigBytes the signature bytes to be verified.
     * @param offset the offset to start from in the array of bytes.
     * @param length the number of bytes to use, starting at offset.
     *
     * @return true if the signature was verified, false if not.
     *
     * @exception SignatureException if the engine is not
     * initialized properly, the passed-in signature is improperly
     * encoded or of the wrong type, if this signature algorithm is unable to
     * process the input data provided, etc.
     * @since 1.4
     */
    protected boolean engineVerify(byte[] sigBytes, int offset, int length)
        throws SignatureException {
        byte[] sigBytesCopy = new byte[length];
        System.arraycopy(sigBytes, offset, sigBytesCopy, 0, length);
        return engineVerify(sigBytesCopy);
    }

    /**
     * Sets the specified algorithm parameter to the specified
     * value. This method supplies a general-purpose mechanism through
     * which it is possible to set the various parameters of this object.
     * A parameter may be any settable parameter for the algorithm, such as
     * a parameter size, or a source of random bits for signature generation
     * (if appropriate), or an indication of whether or not to perform
     * a specific but optional computation. A uniform algorithm-specific
     * naming scheme for each parameter is desirable but left unspecified
     * at this time.
     *
     * @param param the string identifier of the parameter.
     *
     * @param value the parameter value.
     *
     * @exception InvalidParameterException if <code>param</code> is an
     * invalid parameter for this signature algorithm engine,
     * the parameter is already set
     * and cannot be set again, a security exception occurs, and so on.
     *
     * @deprecated Replaced by {@link
     * #engineSetParameter(java.security.spec.AlgorithmParameterSpec)
     * engineSetParameter}.
     */
    @Deprecated
    protected abstract void engineSetParameter(String param, Object value)
        throws InvalidParameterException;

    /**
     * <p>This method is overridden by providers to initialize
     * this signature engine with the specified parameter set.
     *
     * @param params the parameters
     *
     * @exception UnsupportedOperationException if this method is not
     * overridden by a provider
     *
     * @exception InvalidAlgorithmParameterException if this method is
     * overridden by a provider and the the given parameters
     * are inappropriate for this signature engine
     */
    protected void engineSetParameter(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException {
            throw new UnsupportedOperationException();
    }

    /**
     * <p>This method is overridden by providers to return the
     * parameters used with this signature engine, or null
     * if this signature engine does not use any parameters.
     *
     * <p>The returned parameters may be the same that were used to initialize
     * this signature engine, or may contain a combination of default and
     * randomly generated parameter values used by the underlying signature
     * implementation if this signature engine requires algorithm parameters
     * but was not initialized with any.
     *
     * @return the parameters used with this signature engine, or null if this
     * signature engine does not use any parameters
     *
     * @exception UnsupportedOperationException if this method is
     * not overridden by a provider
     * @since 1.4
     */
    protected AlgorithmParameters engineGetParameters() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the value of the specified algorithm parameter.
     * This method supplies a general-purpose mechanism through which it
     * is possible to get the various parameters of this object. A parameter
     * may be any settable parameter for the algorithm, such as a parameter
     * size, or  a source of random bits for signature generation (if
     * appropriate), or an indication of whether or not to perform a
     * specific but optional computation. A uniform algorithm-specific
     * naming scheme for each parameter is desirable but left unspecified
     * at this time.
     *
     * @param param the string name of the parameter.
     *
     * @return the object that represents the parameter value, or null if
     * there is none.
     *
     * @exception InvalidParameterException if <code>param</code> is an
     * invalid parameter for this engine, or another exception occurs while
     * trying to get this parameter.
     *
     * @deprecated
     */
    @Deprecated
    protected abstract Object engineGetParameter(String param)
        throws InvalidParameterException;

    /**
     * Returns a clone if the implementation is cloneable.
     *
     * @return a clone if the implementation is cloneable.
     *
     * @exception CloneNotSupportedException if this is called
     * on an implementation that does not support <code>Cloneable</code>.
     */
    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) {
            return super.clone();
        } else {
            throw new CloneNotSupportedException();
        }
    }
}
