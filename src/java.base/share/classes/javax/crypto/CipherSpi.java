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

import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@code Cipher} class.
 * All the abstract methods in this class must be implemented by each
 * cryptographic service provider who wishes to supply the implementation
 * of a particular cipher algorithm.
 *
 * <p>In order to create an instance of {@code Cipher}, which
 * encapsulates an instance of this {@code CipherSpi} class, an
 * application calls one of the
 * {@link Cipher#getInstance(java.lang.String) getInstance}
 * factory methods of the
 * {@link Cipher Cipher} engine class and specifies the requested
 * <i>transformation</i>.
 * Optionally, the application may also specify the name of a provider.
 *
 * <p>A <i>transformation</i> is a string that describes the operation (or
 * set of operations) to be performed on the given input, to produce some
 * output. A transformation always includes the name of a cryptographic
 * algorithm (e.g., <i>AES</i>), and may be followed by a feedback mode and
 * padding scheme.
 *
 * <p> A transformation is of the form:
 *
 * <ul>
 * <li>"<i>algorithm/mode/padding</i>" or
 *
 * <li>"<i>algorithm</i>"
 * </ul>
 *
 * <P> (in the latter case,
 * provider-specific default values for the mode and padding scheme are used).
 * For example, the following is a valid transformation:
 *
 * <pre>
 *     Cipher c = Cipher.getInstance("<i>AES/CBC/PKCS5Padding</i>");
 * </pre>
 *
 * <p>A provider may supply a separate class for each combination
 * of <i>algorithm/mode/padding</i>, or may decide to provide more generic
 * classes representing sub-transformations corresponding to
 * <i>algorithm</i> or <i>algorithm/mode</i> or <i>algorithm//padding</i>
 * (note the double slashes),
 * in which case the requested mode and/or padding are set automatically by
 * the {@code getInstance} methods of {@code Cipher}, which invoke
 * the {@link #engineSetMode(java.lang.String) engineSetMode} and
 * {@link #engineSetPadding(java.lang.String) engineSetPadding}
 * methods of the provider's subclass of {@code CipherSpi}.
 *
 * <p>A {@code Cipher} property in a provider master class may have one of
 * the following formats:
 *
 * <ul>
 *
 * <li>
 * <pre>
 *     // provider's subclass of "CipherSpi" implements "algName" with
 *     // pluggable mode and padding
 *     {@code Cipher.}<i>algName</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     // provider's subclass of "CipherSpi" implements "algName" in the
 *     // specified "mode", with pluggable padding
 *     {@code Cipher.}<i>algName/mode</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     // provider's subclass of "CipherSpi" implements "algName" with the
 *     // specified "padding", with pluggable mode
 *     {@code Cipher.}<i>algName//padding</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     // provider's subclass of "CipherSpi" implements "algName" with the
 *     // specified "mode" and "padding"
 *     {@code Cipher.}<i>algName/mode/padding</i>
 * </pre>
 *
 * </ul>
 *
 * <p>For example, a provider may supply a subclass of {@code CipherSpi}
 * that implements <i>AES/ECB/PKCS5Padding</i>, one that implements
 * <i>AES/CBC/PKCS5Padding</i>, one that implements
 * <i>AES/CFB/PKCS5Padding</i>, and yet another one that implements
 * <i>AES/OFB/PKCS5Padding</i>. That provider would have the following
 * {@code Cipher} properties in its master class:
 *
 * <ul>
 *
 * <li>
 * <pre>
 *     {@code Cipher.}<i>AES/ECB/PKCS5Padding</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     {@code Cipher.}<i>AES/CBC/PKCS5Padding</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     {@code Cipher.}<i>AES/CFB/PKCS5Padding</i>
 * </pre>
 *
 * <li>
 * <pre>
 *     {@code Cipher.}<i>AES/OFB/PKCS5Padding</i>
 * </pre>
 *
 * </ul>
 *
 * <p>Another provider may implement a class for each of the above modes
 * (i.e., one class for <i>ECB</i>, one for <i>CBC</i>, one for <i>CFB</i>,
 * and one for <i>OFB</i>), one class for <i>PKCS5Padding</i>,
 * and a generic <i>AES</i> class that subclasses from {@code CipherSpi}.
 * That provider would have the following
 * {@code Cipher} properties in its master class:
 *
 * <ul>
 *
 * <li>
 * <pre>
 *     {@code Cipher.}<i>AES</i>
 * </pre>
 *
 * </ul>
 *
 * <p>The {@code getInstance} factory method of the {@code Cipher}
 * engine class follows these rules in order to instantiate a provider's
 * implementation of {@code CipherSpi} for a
 * transformation of the form "<i>algorithm</i>":
 *
 * <ol>
 * <li>
 * Check if the provider has registered a subclass of {@code CipherSpi}
 * for the specified "<i>algorithm</i>".
 * <p>If the answer is YES, instantiate this
 * class, for whose mode and padding scheme default values (as supplied by
 * the provider) are used.
 * <p>If the answer is NO, throw a {@code NoSuchAlgorithmException}
 * exception.
 * </ol>
 *
 * <p>The {@code getInstance} factory method of the {@code Cipher}
 * engine class follows these rules in order to instantiate a provider's
 * implementation of {@code CipherSpi} for a
 * transformation of the form "<i>algorithm/mode/padding</i>":
 *
 * <ol>
 * <li>
 * Check if the provider has registered a subclass of {@code CipherSpi}
 * for the specified "<i>algorithm/mode/padding</i>" transformation.
 * <p>If the answer is YES, instantiate it.
 * <p>If the answer is NO, go to the next step.
 * <li>
 * Check if the provider has registered a subclass of {@code CipherSpi}
 * for the sub-transformation "<i>algorithm/mode</i>".
 * <p>If the answer is YES, instantiate it, and call
 * {@code engineSetPadding(<i>padding</i>)} on the new instance.
 * <p>If the answer is NO, go to the next step.
 * <li>
 * Check if the provider has registered a subclass of {@code CipherSpi}
 * for the sub-transformation "<i>algorithm//padding</i>" (note the double
 * slashes).
 * <p>If the answer is YES, instantiate it, and call
 * {@code engineSetMode(<i>mode</i>)} on the new instance.
 * <p>If the answer is NO, go to the next step.
 * <li>
 * Check if the provider has registered a subclass of {@code CipherSpi}
 * for the sub-transformation "<i>algorithm</i>".
 * <p>If the answer is YES, instantiate it, and call
 * {@code engineSetMode(<i>mode</i>)} and
 * {@code engineSetPadding(<i>padding</i>)} on the new instance.
 * <p>If the answer is NO, throw a {@code NoSuchAlgorithmException}
 * exception.
 * </ol>
 *
 * @author Jan Luehe
 * @see KeyGenerator
 * @see SecretKey
 * @since 1.4
 */

public abstract class CipherSpi {

    /**
     * Constructor for subclasses to call.
     */
    public CipherSpi() {}

    /**
     * Sets the mode of this cipher.
     *
     * @param mode the cipher mode
     *
     * @throws NoSuchAlgorithmException if the requested cipher mode does
     * not exist
     */
    protected abstract void engineSetMode(String mode)
        throws NoSuchAlgorithmException;

    /**
     * Sets the padding mechanism of this cipher.
     *
     * @param padding the padding mechanism
     *
     * @throws NoSuchPaddingException if the requested padding mechanism
     * does not exist
     */
    protected abstract void engineSetPadding(String padding)
        throws NoSuchPaddingException;

    /**
     * Returns the block size (in bytes).
     *
     * @return the block size (in bytes), or 0 if the algorithm is
     * not a block cipher
     */
    protected abstract int engineGetBlockSize();

    /**
     * Returns the length in bytes that an output buffer would
     * need to be in order to hold the result of the next {@code update}
     * or {@code doFinal} operation, given the input length
     * {@code inputLen} (in bytes).
     *
     * <p>This call takes into account any unprocessed (buffered) data from a
     * previous {@code update} call, padding, and AEAD tagging.
     *
     * <p>The actual output length of the next {@code update} or
     * {@code doFinal} call may be smaller than the length returned by
     * this method.
     *
     * @param inputLen the input length (in bytes)
     *
     * @return the required output buffer size (in bytes)
     */
    protected abstract int engineGetOutputSize(int inputLen);

    /**
     * Returns the initialization vector (IV) in a new buffer.
     *
     * <p> This is useful in the context of password-based encryption or
     * decryption, where the IV is derived from a user-provided passphrase.
     *
     * @return the initialization vector in a new buffer, or {@code null} if the
     * algorithm does not use an IV, or if the IV has not yet
     * been set
     */
    protected abstract byte[] engineGetIV();

    /**
     * Returns the parameters used with this cipher.
     *
     * <p>The returned parameters may be the same that were used to initialize
     * this cipher, or may contain additional default or random parameter
     * values used by the underlying cipher implementation. If the required
     * parameters were not supplied and can be generated by the cipher, the
     * generated parameters are returned. Otherwise, {@code null} is returned.
     *
     * @return the parameters used with this cipher, or {@code null}
     */
    protected abstract AlgorithmParameters engineGetParameters();

    /**
     * Initializes this cipher with a key and a source
     * of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on
     * the value of {@code opmode}.
     *
     * <p>If this cipher requires any algorithm parameters that cannot be
     * derived from the given {@code key}, the underlying cipher
     * implementation is supposed to generate the required parameters itself
     * (using provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * {@code InvalidKeyException} if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #engineGetParameters() engineGetParameters} or
     * {@link #engineGetIV() engineGetIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from {@code random}.
     *
     * <p>Note that when a {@code Cipher} object is initialized, it loses all
     * previously-acquired state. In other words, initializing a cipher is
     * equivalent to creating a new instance of that cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * {@code ENCRYPT_MODE}, {@code DECRYPT_MODE},
     * {@code WRAP_MODE} or {@code UNWRAP_MODE})
     * @param key the encryption key
     * @param random the source of randomness
     *
     * @throws InvalidKeyException if the given key is inappropriate for
     * initializing this cipher, or requires
     * algorithm parameters that cannot be
     * determined from the given key
     * @throws UnsupportedOperationException if {@code opmode} is
     * {@code WRAP_MODE} or {@code UNWRAP_MODE} is not implemented
     * by the cipher
     */
    protected abstract void engineInit(int opmode, Key key,
                                       SecureRandom random)
        throws InvalidKeyException;

    /**
     * Initializes this cipher with a key, a set of
     * algorithm parameters, and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on
     * the value of {@code opmode}.
     *
     * <p>If this cipher requires any algorithm parameters and
     * {@code params} is {@code null}, the underlying cipher implementation
     * is supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * {@code InvalidAlgorithmParameterException} if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #engineGetParameters() engineGetParameters} or
     * {@link #engineGetIV() engineGetIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from {@code random}.
     *
     * <p>Note that when a {@code Cipher} object is initialized, it loses all
     * previously-acquired state. In other words, initializing a cipher is
     * equivalent to creating a new instance of that Cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * {@code ENCRYPT_MODE}, {@code DECRYPT_MODE},
     * {@code WRAP_MODE}> or {@code UNWRAP_MODE})
     * @param key the encryption key
     * @param params the algorithm parameters
     * @param random the source of randomness
     *
     * @throws InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     * @throws InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or if this cipher requires
     * algorithm parameters and {@code params} is {@code null}
     * @throws UnsupportedOperationException if {@code opmode} is
     * {@code WRAP_MODE} or {@code UNWRAP_MODE} is not implemented
     * by the cipher
     */
    protected abstract void engineInit(int opmode, Key key,
                                       AlgorithmParameterSpec params,
                                       SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException;

    /**
     * Initializes this cipher with a key, a set of
     * algorithm parameters, and a source of randomness.
     *
     * <p>The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on
     * the value of {@code opmode}.
     *
     * <p>If this cipher requires any algorithm parameters and
     * {@code params} is {@code null}, the underlying cipher implementation is
     * supposed to generate the required parameters itself (using
     * provider-specific default or random values) if it is being
     * initialized for encryption or key wrapping, and raise an
     * {@code InvalidAlgorithmParameterException} if it is being
     * initialized for decryption or key unwrapping.
     * The generated parameters can be retrieved using
     * {@link #engineGetParameters() engineGetParameters} or
     * {@link #engineGetIV() engineGetIV} (if the parameter is an IV).
     *
     * <p>If this cipher requires algorithm parameters that cannot be
     * derived from the input parameters, and there are no reasonable
     * provider-specific default values, initialization will
     * necessarily fail.
     *
     * <p>If this cipher (including its feedback or padding scheme)
     * requires any random bytes (e.g., for parameter generation), it will get
     * them from {@code random}.
     *
     * <p>Note that when a {@code Cipher} object is initialized, it loses all
     * previously-acquired state. In other words, initializing a cipher is
     * equivalent to creating a new instance of that cipher and initializing
     * it.
     *
     * @param opmode the operation mode of this cipher (this is one of
     * the following:
     * {@code ENCRYPT_MODE}, {@code DECRYPT_MODE},
     * {@code WRAP_MODE} or {@code UNWRAP_MODE})
     * @param key the encryption key
     * @param params the algorithm parameters
     * @param random the source of randomness
     *
     * @throws InvalidKeyException if the given key is inappropriate for
     * initializing this cipher
     * @throws InvalidAlgorithmParameterException if the given algorithm
     * parameters are inappropriate for this cipher,
     * or if this cipher requires
     * algorithm parameters and {@code params} is null
     * @throws UnsupportedOperationException if {@code opmode} is
     * {@code WRAP_MODE} or {@code UNWRAP_MODE} is not implemented
     * by the cipher
     */
    protected abstract void engineInit(int opmode, Key key,
                                       AlgorithmParameters params,
                                       SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException;

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first {@code inputLen} bytes in the {@code input}
     * buffer, starting at {@code inputOffset} inclusive, are processed,
     * and the result is stored in a new buffer.
     *
     * @param input the input buffer
     * @param inputOffset the offset in {@code input} where the input starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result, or {@code null} if the
     * cipher is a block cipher and the input data is too short to result in a
     * new block
     */
    protected abstract byte[] engineUpdate(byte[] input, int inputOffset,
                                           int inputLen);

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>The first {@code inputLen} bytes in the {@code input}
     * buffer, starting at {@code inputOffset} inclusive, are processed,
     * and the result is stored in the {@code output} buffer, starting at
     * {@code outputOffset} inclusive.
     *
     * <p>If the {@code output} buffer is too small to hold the result,
     * a {@code ShortBufferException} is thrown.
     *
     * @param input the input buffer
     * @param inputOffset the offset in {@code input} where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in {@code output} where the result
     * is stored
     *
     * @return the number of bytes stored in {@code output}
     *
     * @throws ShortBufferException if the given output buffer is too small
     * to hold the result
     */
    protected abstract int engineUpdate(byte[] input, int inputOffset,
                                        int inputLen, byte[] output,
                                        int outputOffset)
        throws ShortBufferException;

    /**
     * Continues a multiple-part encryption or decryption operation
     * (depending on how this cipher was initialized), processing another data
     * part.
     *
     * <p>All {@code input.remaining()} bytes starting at
     * {@code input.position()} are processed. The result is stored
     * in the output buffer.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed. The output buffer's
     * position will have advanced by n, where n is the value returned
     * by this method; the output buffer's limit will not have changed.
     *
     * <p>If {@code output.remaining()} bytes are insufficient to
     * hold the result, a {@code ShortBufferException} is thrown.
     *
     * <p>Subclasses should consider overriding this method if they can
     * process ByteBuffers more efficiently than byte arrays.
     *
     * @param input the input ByteBuffer
     * @param output the output ByteBuffer
     *
     * @return the number of bytes stored in {@code output}
     *
     * @throws ShortBufferException if there is insufficient space in the
     * output buffer
     *
     * @throws NullPointerException if either parameter is {@code null}
     * @since 1.5
     */
    protected int engineUpdate(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException {
        try {
            return bufferCrypt(input, output, true);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // never thrown for engineUpdate()
            throw new ProviderException("Internal error in update()");
        }
    }

    /**
     * Encrypts or decrypts data in a single-part operation,
     * or finishes a multiple-part operation.
     * The data is encrypted or decrypted, depending on how this cipher was
     * initialized.
     *
     * <p>The first {@code inputLen} bytes in the {@code input}
     * buffer, starting at {@code inputOffset} inclusive, and any input
     * bytes that may have been buffered during a previous {@code update}
     * operation, are processed, with padding (if requested) being applied.
     * If an AEAD mode (such as GCM or CCM) is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in a new buffer.
     *
     * <p>Upon finishing, this method resets this cipher to the state
     * it was in when previously initialized via a call to
     * {@code engineInit}.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * {@code engineInit}) more data.
     *
     * <p>Note: if any exception is thrown, this cipher may need to
     * be reset before it can be used again.
     *
     * @param input the input buffer
     * @param inputOffset the offset in {@code input} where the input starts
     * @param inputLen the input length
     *
     * @return the new buffer with the result
     *
     * @throws IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided
     * @throws BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @throws AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM or CCM), and the received authentication tag
     * does not match the calculated value
     */
    protected abstract byte[] engineDoFinal(byte[] input, int inputOffset,
                                            int inputLen)
        throws IllegalBlockSizeException, BadPaddingException;

    /**
     * Encrypts or decrypts data in a single-part operation,
     * or finishes a multiple-part operation.
     * The data is encrypted or decrypted, depending on how this cipher was
     * initialized.
     *
     * <p>The first {@code inputLen} bytes in the {@code input}
     * buffer, starting at {@code inputOffset} inclusive, and any input
     * bytes that may have been buffered during a previous {@code update}
     * operation, are processed, with padding (if requested) being applied.
     * If an AEAD mode such as GCM or CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the {@code output} buffer, starting at
     * {@code outputOffset} inclusive.
     *
     * <p>If the {@code output} buffer is too small to hold the result,
     * a {@code ShortBufferException} is thrown.
     *
     * <p>Upon finishing, this method resets this cipher to the state
     * it was in when previously initialized via a call to
     * {@code engineInit}.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * {@code engineInit}) more data.
     *
     * <p>Note: if any exception is thrown, this cipher may need to
     * be reset before it can be used again.
     *
     * @param input the input buffer
     * @param inputOffset the offset in {@code input} where the input
     * starts
     * @param inputLen the input length
     * @param output the buffer for the result
     * @param outputOffset the offset in {@code output} where the result
     * is stored
     *
     * @return the number of bytes stored in {@code output}
     *
     * @throws IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided
     * @throws ShortBufferException if the given output buffer is too small
     * to hold the result
     * @throws BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @throws AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM or CCM), and the received authentication tag
     * does not match the calculated value
     */
    protected abstract int engineDoFinal(byte[] input, int inputOffset,
                                         int inputLen, byte[] output,
                                         int outputOffset)
        throws ShortBufferException, IllegalBlockSizeException,
               BadPaddingException;

    /**
     * Encrypts or decrypts data in a single-part operation,
     * or finishes a multiple-part operation.
     * The data is encrypted or decrypted, depending on how this cipher was
     * initialized.
     *
     * <p>All {@code input.remaining()} bytes starting at
     * {@code input.position()} are processed.
     * If an AEAD mode such as GCM or CCM is being used, the authentication
     * tag is appended in the case of encryption, or verified in the
     * case of decryption.
     * The result is stored in the output buffer.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed. The output buffer's
     * position will have advanced by n, where n is the value returned
     * by this method; the output buffer's limit will not have changed.
     *
     * <p>If {@code output.remaining()} bytes are insufficient to
     * hold the result, a {@code ShortBufferException} is thrown.
     *
     * <p>Upon finishing, this method resets this cipher to the state
     * it was in when previously initialized via a call to
     * {@code engineInit}.
     * That is, the object is reset and available to encrypt or decrypt
     * (depending on the operation mode that was specified in the call to
     * {@code engineInit} more data.
     *
     * <p>Note: if any exception is thrown, this cipher may need to
     * be reset before it can be used again.
     *
     * <p>Subclasses should consider overriding this method if they can
     * process ByteBuffers more efficiently than byte arrays.
     *
     * @param input the input ByteBuffer
     * @param output the output ByteBuffer
     *
     * @return the number of bytes stored in {@code output}
     *
     * @throws IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested (only in encryption mode), and the total
     * input length of the data processed by this cipher is not a multiple of
     * block size; or if this encryption algorithm is unable to
     * process the input data provided
     * @throws ShortBufferException if there is insufficient space in the
     * output buffer
     * @throws BadPaddingException if this cipher is in decryption mode,
     * and (un)padding has been requested, but the decrypted data is not
     * bounded by the appropriate padding bytes
     * @throws AEADBadTagException if this cipher is decrypting in an
     * AEAD mode (such as GCM or CCM), and the received authentication tag
     * does not match the calculated value
     *
     * @throws NullPointerException if either parameter is {@code null}
     * @since 1.5
     */
    protected int engineDoFinal(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        return bufferCrypt(input, output, false);
    }

    // copied from sun.security.jca.JCAUtil
    // will be changed to reference that method once that code has been
    // integrated and promoted
    static int getTempArraySize(int totalSize) {
        return Math.min(4096, totalSize);
    }

    /**
     * Implementation for encryption using ByteBuffers. Used for both
     * engineUpdate() and engineDoFinal().
     */
    private int bufferCrypt(ByteBuffer input, ByteBuffer output,
            boolean isUpdate) throws ShortBufferException,
            IllegalBlockSizeException, BadPaddingException {
        if ((input == null) || (output == null)) {
            throw new NullPointerException
                ("Input and output buffers must not be null");
        }
        int inPos = input.position();
        int inLimit = input.limit();
        int inLen = inLimit - inPos;
        if (isUpdate && (inLen == 0)) {
            return 0;
        }
        int outLenNeeded = engineGetOutputSize(inLen);

        if (output.remaining() < outLenNeeded) {
            throw new ShortBufferException("Need at least " + outLenNeeded
                + " bytes of space in output buffer");
        }

        // detecting input and output buffer overlap may be tricky
        // we can only write directly into output buffer when we
        // are 100% sure it's safe to do so

        boolean a1 = input.hasArray();
        boolean a2 = output.hasArray();
        int total = 0;

        if (a1) { // input has an accessible byte[]
            byte[] inArray = input.array();
            int inOfs = input.arrayOffset() + inPos;

            byte[] outArray;
            if (a2) { // output has an accessible byte[]
                outArray = output.array();
                int outPos = output.position();
                int outOfs = output.arrayOffset() + outPos;

                // check array address and offsets and use temp output buffer
                // if output offset is larger than input offset and
                // falls within the range of input data
                boolean useTempOut = false;
                if (inArray == outArray &&
                    ((inOfs < outOfs) && (outOfs < inOfs + inLen))) {
                    useTempOut = true;
                    outArray = new byte[outLenNeeded];
                    outOfs = 0;
                }
                if (isUpdate) {
                    total = engineUpdate(inArray, inOfs, inLen, outArray, outOfs);
                } else {
                    total = engineDoFinal(inArray, inOfs, inLen, outArray, outOfs);
                }
                if (useTempOut) {
                    output.put(outArray, outOfs, total);
                } else {
                    // adjust output position manually
                    output.position(outPos + total);
                }
            } else { // output does not have an accessible byte[]
                if (isUpdate) {
                    outArray = engineUpdate(inArray, inOfs, inLen);
                } else {
                    outArray = engineDoFinal(inArray, inOfs, inLen);
                }
                if (outArray != null && outArray.length != 0) {
                    output.put(outArray);
                    total = outArray.length;
                }
            }
            // adjust input position manually
            input.position(inLimit);
        } else { // input does not have an accessible byte[]
            // have to assume the worst, since we have no way of determine
            // if input and output overlaps or not
            byte[] tempOut = new byte[outLenNeeded];
            int outOfs = 0;

            byte[] tempIn = new byte[getTempArraySize(inLen)];
            do {
                int chunk = Math.min(inLen, tempIn.length);
                if (chunk > 0) {
                    input.get(tempIn, 0, chunk);
                }
                int n;
                if (isUpdate || (inLen > chunk)) {
                    n = engineUpdate(tempIn, 0, chunk, tempOut, outOfs);
                } else {
                    n = engineDoFinal(tempIn, 0, chunk, tempOut, outOfs);
                }
                outOfs += n;
                total += n;
                inLen -= chunk;
            } while (inLen > 0);
            if (total > 0) {
                output.put(tempOut, 0, total);
            }
        }

        return total;
    }

    /**
     * Wrap a key.
     *
     * <p>This concrete method has been added to this previously-defined
     * abstract class. (For backwards compatibility, it cannot be abstract.)
     * It may be overridden by a provider to wrap a key.
     * Such an override is expected to throw an
     * {@code IllegalBlockSizeException} or {@code InvalidKeyException}
     * (under the specified circumstances), if the given key cannot be wrapped.
     * If this method is not overridden, it always throws an
     * {@code UnsupportedOperationException}.
     *
     * @param key the key to be wrapped
     *
     * @return the wrapped key
     *
     * @throws IllegalBlockSizeException if this cipher is a block cipher,
     * no padding has been requested, and the length of the encoding of the
     * key to be wrapped is not a multiple of the block size
     *
     * @throws InvalidKeyException if it is impossible or unsafe to
     * wrap the key with this cipher (e.g., a hardware protected key is
     * being passed to a software-only cipher)
     *
     * @throws UnsupportedOperationException if this method is not supported
     */
    protected byte[] engineWrap(Key key)
        throws IllegalBlockSizeException, InvalidKeyException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Unwrap a previously wrapped key.
     *
     * <p>This concrete method has been added to this previously-defined
     * abstract class. (For backwards compatibility, it cannot be abstract.)
     * It may be overridden by a provider to unwrap a previously wrapped key.
     * Such an override is expected to throw an {@code InvalidKeyException} if
     * the given wrapped key cannot be unwrapped.
     * If this method is not overridden, it always throws an
     * {@code UnsupportedOperationException}.
     *
     * @param wrappedKey the key to be unwrapped
     *
     * @param wrappedKeyAlgorithm the algorithm associated with the wrapped
     * key
     *
     * @param wrappedKeyType the type of the wrapped key. This is one of
     * {@code SECRET_KEY}, {@code PRIVATE_KEY}, or {@code PUBLIC_KEY}.
     *
     * @return the unwrapped key
     *
     * @throws NoSuchAlgorithmException if no installed providers
     * can create keys of type {@code wrappedKeyType} for the
     * {@code wrappedKeyAlgorithm}
     *
     * @throws InvalidKeyException if {@code wrappedKey} does not
     * represent a wrapped key of type {@code wrappedKeyType} for
     * the {@code wrappedKeyAlgorithm}
     *
     * @throws UnsupportedOperationException if this method is not supported
     */
    protected Key engineUnwrap(byte[] wrappedKey,
                               String wrappedKeyAlgorithm,
                               int wrappedKeyType)
        throws InvalidKeyException, NoSuchAlgorithmException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the key size of the given key object in bits.
     * <p>This concrete method has been added to this previously-defined
     * abstract class. It throws an {@code UnsupportedOperationException}
     * if it is not overridden by the provider.
     *
     * @param key the key object
     *
     * @return the key size of the given key object
     *
     * @throws InvalidKeyException if {@code key} is invalid
     */
    protected int engineGetKeySize(Key key)
        throws InvalidKeyException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Continues a multipart update of the Additional Authentication
     * Data (AAD), using a subset of the provided buffer.
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM or CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and
     * {@code doFinal} methods).
     *
     * @param src the buffer containing the AAD
     * @param offset the offset in {@code src} where the AAD input starts
     * @param len the number of AAD bytes
     *
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if this method
     * has not been overridden by an implementation
     *
     * @since 1.7
     */
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        throw new UnsupportedOperationException(
            "The underlying Cipher implementation "
            +  "does not support this method");
    }

    /**
     * Continues a multipart update of the Additional Authentication
     * Data (AAD).
     * <p>
     * Calls to this method provide AAD to the cipher when operating in
     * modes such as AEAD (GCM or CCM).  If this cipher is operating in
     * either GCM or CCM mode, all AAD must be supplied before beginning
     * operations on the ciphertext (via the {@code update} and
     * {@code doFinal} methods).
     * <p>
     * All {@code src.remaining()} bytes starting at
     * {@code src.position()} are processed.
     * Upon return, the input buffer's position will be equal
     * to its limit; its limit will not have changed.
     *
     * @param src the buffer containing the AAD
     *
     * @throws IllegalStateException if this cipher is in a wrong state
     * (e.g., has not been initialized), does not accept AAD, or if
     * operating in either GCM or CCM mode and one of the {@code update}
     * methods has already been called for the active
     * encryption/decryption operation
     * @throws UnsupportedOperationException if this method
     * has not been overridden by an implementation
     *
     * @since 1.7
     */
    protected void engineUpdateAAD(ByteBuffer src) {
        throw new UnsupportedOperationException(
            "The underlying Cipher implementation "
            +  "does not support this method");
    }
}
