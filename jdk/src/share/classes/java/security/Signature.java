/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.nio.ByteBuffer;

import java.security.Provider.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import javax.crypto.NoSuchPaddingException;

import sun.security.util.Debug;
import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * The Signature class is used to provide applications the functionality
 * of a digital signature algorithm. Digital signatures are used for
 * authentication and integrity assurance of digital data.
 *
 * <p> The signature algorithm can be, among others, the NIST standard
 * DSA, using DSA and SHA-1. The DSA algorithm using the
 * SHA-1 message digest algorithm can be specified as {@code SHA1withDSA}.
 * In the case of RSA, there are multiple choices for the message digest
 * algorithm, so the signing algorithm could be specified as, for example,
 * {@code MD2withRSA}, {@code MD5withRSA}, or {@code SHA1withRSA}.
 * The algorithm name must be specified, as there is no default.
 *
 * <p> A Signature object can be used to generate and verify digital
 * signatures.
 *
 * <p> There are three phases to the use of a Signature object for
 * either signing data or verifying a signature:<ol>
 *
 * <li>Initialization, with either
 *
 *     <ul>
 *
 *     <li>a public key, which initializes the signature for
 *     verification (see {@link #initVerify(PublicKey) initVerify}), or
 *
 *     <li>a private key (and optionally a Secure Random Number Generator),
 *     which initializes the signature for signing
 *     (see {@link #initSign(PrivateKey)}
 *     and {@link #initSign(PrivateKey, SecureRandom)}).
 *
 *     </ul>
 *
 * <li>Updating
 *
 * <p>Depending on the type of initialization, this will update the
 * bytes to be signed or verified. See the
 * {@link #update(byte) update} methods.
 *
 * <li>Signing or Verifying a signature on all updated bytes. See the
 * {@link #sign() sign} methods and the {@link #verify(byte[]) verify}
 * method.
 *
 * </ol>
 *
 * <p>Note that this class is abstract and extends from
 * {@code SignatureSpi} for historical reasons.
 * Application developers should only take notice of the methods defined in
 * this {@code Signature} class; all the methods in
 * the superclass are intended for cryptographic service providers who wish to
 * supply their own implementations of digital signature algorithms.
 *
 * <p> Every implementation of the Java platform is required to support the
 * following standard {@code Signature} algorithms:
 * <ul>
 * <li>{@code SHA1withDSA}</li>
 * <li>{@code SHA1withRSA}</li>
 * <li>{@code SHA256withRSA}</li>
 * </ul>
 * These algorithms are described in the <a href=
 * "{@docRoot}/../technotes/guides/security/StandardNames.html#Signature">
 * Signature section</a> of the
 * Java Cryptography Architecture Standard Algorithm Name Documentation.
 * Consult the release documentation for your implementation to see if any
 * other algorithms are supported.
 *
 * @author Benjamin Renaud
 *
 */

public abstract class Signature extends SignatureSpi {

    private static final Debug debug =
                        Debug.getInstance("jca", "Signature");

    /*
     * The algorithm for this signature object.
     * This value is used to map an OID to the particular algorithm.
     * The mapping is done in AlgorithmObject.algOID(String algorithm)
     */
    private String algorithm;

    // The provider
    Provider provider;

    /**
     * Possible {@link #state} value, signifying that
     * this signature object has not yet been initialized.
     */
    protected final static int UNINITIALIZED = 0;

    /**
     * Possible {@link #state} value, signifying that
     * this signature object has been initialized for signing.
     */
    protected final static int SIGN = 2;

    /**
     * Possible {@link #state} value, signifying that
     * this signature object has been initialized for verification.
     */
    protected final static int VERIFY = 3;

    /**
     * Current state of this signature object.
     */
    protected int state = UNINITIALIZED;

    /**
     * Creates a Signature object for the specified algorithm.
     *
     * @param algorithm the standard string name of the algorithm.
     * See the Signature section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#Signature">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     */
    protected Signature(String algorithm) {
        this.algorithm = algorithm;
    }

    // name of the special signature alg
    private final static String RSA_SIGNATURE = "NONEwithRSA";

    // name of the equivalent cipher alg
    private final static String RSA_CIPHER = "RSA/ECB/PKCS1Padding";

    // all the services we need to lookup for compatibility with Cipher
    private final static List<ServiceId> rsaIds = Arrays.asList(
        new ServiceId[] {
            new ServiceId("Signature", "NONEwithRSA"),
            new ServiceId("Cipher", "RSA/ECB/PKCS1Padding"),
            new ServiceId("Cipher", "RSA/ECB"),
            new ServiceId("Cipher", "RSA//PKCS1Padding"),
            new ServiceId("Cipher", "RSA"),
        }
    );

    /**
     * Returns a Signature object that implements the specified signature
     * algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new Signature object encapsulating the
     * SignatureSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the standard name of the algorithm requested.
     * See the Signature section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#Signature">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @return the new Signature object.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          Signature implementation for the
     *          specified algorithm.
     *
     * @see Provider
     */
    public static Signature getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        List<Service> list;
        if (algorithm.equalsIgnoreCase(RSA_SIGNATURE)) {
            list = GetInstance.getServices(rsaIds);
        } else {
            list = GetInstance.getServices("Signature", algorithm);
        }
        Iterator<Service> t = list.iterator();
        if (t.hasNext() == false) {
            throw new NoSuchAlgorithmException
                (algorithm + " Signature not available");
        }
        // try services until we find an Spi or a working Signature subclass
        NoSuchAlgorithmException failure;
        do {
            Service s = t.next();
            if (isSpi(s)) {
                return new Delegate(s, t, algorithm);
            } else {
                // must be a subclass of Signature, disable dynamic selection
                try {
                    Instance instance =
                        GetInstance.getInstance(s, SignatureSpi.class);
                    return getInstance(instance, algorithm);
                } catch (NoSuchAlgorithmException e) {
                    failure = e;
                }
            }
        } while (t.hasNext());
        throw failure;
    }

    private static Signature getInstance(Instance instance, String algorithm) {
        Signature sig;
        if (instance.impl instanceof Signature) {
            sig = (Signature)instance.impl;
            sig.algorithm = algorithm;
        } else {
            SignatureSpi spi = (SignatureSpi)instance.impl;
            sig = new Delegate(spi, algorithm);
        }
        sig.provider = instance.provider;
        return sig;
    }

    private final static Map<String,Boolean> signatureInfo;

    static {
        signatureInfo = new ConcurrentHashMap<String,Boolean>();
        Boolean TRUE = Boolean.TRUE;
        // pre-initialize with values for our SignatureSpi implementations
        signatureInfo.put("sun.security.provider.DSA$RawDSA", TRUE);
        signatureInfo.put("sun.security.provider.DSA$SHA1withDSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$MD2withRSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$MD5withRSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$SHA1withRSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$SHA256withRSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$SHA384withRSA", TRUE);
        signatureInfo.put("sun.security.rsa.RSASignature$SHA512withRSA", TRUE);
        signatureInfo.put("com.sun.net.ssl.internal.ssl.RSASignature", TRUE);
        signatureInfo.put("sun.security.pkcs11.P11Signature", TRUE);
    }

    private static boolean isSpi(Service s) {
        if (s.getType().equals("Cipher")) {
            // must be a CipherSpi, which we can wrap with the CipherAdapter
            return true;
        }
        String className = s.getClassName();
        Boolean result = signatureInfo.get(className);
        if (result == null) {
            try {
                Object instance = s.newInstance(null);
                // Signature extends SignatureSpi
                // so it is a "real" Spi if it is an
                // instance of SignatureSpi but not Signature
                boolean r = (instance instanceof SignatureSpi)
                                && (instance instanceof Signature == false);
                if ((debug != null) && (r == false)) {
                    debug.println("Not a SignatureSpi " + className);
                    debug.println("Delayed provider selection may not be "
                        + "available for algorithm " + s.getAlgorithm());
                }
                result = Boolean.valueOf(r);
                signatureInfo.put(className, result);
            } catch (Exception e) {
                // something is wrong, assume not an SPI
                return false;
            }
        }
        return result.booleanValue();
    }

    /**
     * Returns a Signature object that implements the specified signature
     * algorithm.
     *
     * <p> A new Signature object encapsulating the
     * SignatureSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the algorithm requested.
     * See the Signature section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#Signature">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return the new Signature object.
     *
     * @exception NoSuchAlgorithmException if a SignatureSpi
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
    public static Signature getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (algorithm.equalsIgnoreCase(RSA_SIGNATURE)) {
            // exception compatibility with existing code
            if ((provider == null) || (provider.length() == 0)) {
                throw new IllegalArgumentException("missing provider");
            }
            Provider p = Security.getProvider(provider);
            if (p == null) {
                throw new NoSuchProviderException
                    ("no such provider: " + provider);
            }
            return getInstanceRSA(p);
        }
        Instance instance = GetInstance.getInstance
                ("Signature", SignatureSpi.class, algorithm, provider);
        return getInstance(instance, algorithm);
    }

    /**
     * Returns a Signature object that implements the specified
     * signature algorithm.
     *
     * <p> A new Signature object encapsulating the
     * SignatureSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param algorithm the name of the algorithm requested.
     * See the Signature section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#Signature">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the provider.
     *
     * @return the new Signature object.
     *
     * @exception NoSuchAlgorithmException if a SignatureSpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the provider is null.
     *
     * @see Provider
     *
     * @since 1.4
     */
    public static Signature getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        if (algorithm.equalsIgnoreCase(RSA_SIGNATURE)) {
            // exception compatibility with existing code
            if (provider == null) {
                throw new IllegalArgumentException("missing provider");
            }
            return getInstanceRSA(provider);
        }
        Instance instance = GetInstance.getInstance
                ("Signature", SignatureSpi.class, algorithm, provider);
        return getInstance(instance, algorithm);
    }

    // return an implementation for NONEwithRSA, which is a special case
    // because of the Cipher.RSA/ECB/PKCS1Padding compatibility wrapper
    private static Signature getInstanceRSA(Provider p)
            throws NoSuchAlgorithmException {
        // try Signature first
        Service s = p.getService("Signature", RSA_SIGNATURE);
        if (s != null) {
            Instance instance = GetInstance.getInstance(s, SignatureSpi.class);
            return getInstance(instance, RSA_SIGNATURE);
        }
        // check Cipher
        try {
            Cipher c = Cipher.getInstance(RSA_CIPHER, p);
            return new Delegate(new CipherAdapter(c), RSA_SIGNATURE);
        } catch (GeneralSecurityException e) {
            // throw Signature style exception message to avoid confusion,
            // but append Cipher exception as cause
            throw new NoSuchAlgorithmException("no such algorithm: "
                + RSA_SIGNATURE + " for provider " + p.getName(), e);
        }
    }

    /**
     * Returns the provider of this signature object.
     *
     * @return the provider of this signature object
     */
    public final Provider getProvider() {
        chooseFirstProvider();
        return this.provider;
    }

    void chooseFirstProvider() {
        // empty, overridden in Delegate
    }

    /**
     * Initializes this object for verification. If this method is called
     * again with a different argument, it negates the effect
     * of this call.
     *
     * @param publicKey the public key of the identity whose signature is
     * going to be verified.
     *
     * @exception InvalidKeyException if the key is invalid.
     */
    public final void initVerify(PublicKey publicKey)
            throws InvalidKeyException {
        engineInitVerify(publicKey);
        state = VERIFY;
    }

    /**
     * Initializes this object for verification, using the public key from
     * the given certificate.
     * <p>If the certificate is of type X.509 and has a <i>key usage</i>
     * extension field marked as critical, and the value of the <i>key usage</i>
     * extension field implies that the public key in
     * the certificate and its corresponding private key are not
     * supposed to be used for digital signatures, an
     * {@code InvalidKeyException} is thrown.
     *
     * @param certificate the certificate of the identity whose signature is
     * going to be verified.
     *
     * @exception InvalidKeyException  if the public key in the certificate
     * is not encoded properly or does not include required  parameter
     * information or cannot be used for digital signature purposes.
     * @since 1.3
     */
    public final void initVerify(Certificate certificate)
            throws InvalidKeyException {
        // If the certificate is of type X509Certificate,
        // we should check whether it has a Key Usage
        // extension marked as critical.
        if (certificate instanceof java.security.cert.X509Certificate) {
            // Check whether the cert has a key usage extension
            // marked as a critical extension.
            // The OID for KeyUsage extension is 2.5.29.15.
            X509Certificate cert = (X509Certificate)certificate;
            Set<String> critSet = cert.getCriticalExtensionOIDs();

            if (critSet != null && !critSet.isEmpty()
                && critSet.contains("2.5.29.15")) {
                boolean[] keyUsageInfo = cert.getKeyUsage();
                // keyUsageInfo[0] is for digitalSignature.
                if ((keyUsageInfo != null) && (keyUsageInfo[0] == false))
                    throw new InvalidKeyException("Wrong key usage");
            }
        }

        PublicKey publicKey = certificate.getPublicKey();
        engineInitVerify(publicKey);
        state = VERIFY;
    }

    /**
     * Initialize this object for signing. If this method is called
     * again with a different argument, it negates the effect
     * of this call.
     *
     * @param privateKey the private key of the identity whose signature
     * is going to be generated.
     *
     * @exception InvalidKeyException if the key is invalid.
     */
    public final void initSign(PrivateKey privateKey)
            throws InvalidKeyException {
        engineInitSign(privateKey);
        state = SIGN;
    }

    /**
     * Initialize this object for signing. If this method is called
     * again with a different argument, it negates the effect
     * of this call.
     *
     * @param privateKey the private key of the identity whose signature
     * is going to be generated.
     *
     * @param random the source of randomness for this signature.
     *
     * @exception InvalidKeyException if the key is invalid.
     */
    public final void initSign(PrivateKey privateKey, SecureRandom random)
            throws InvalidKeyException {
        engineInitSign(privateKey, random);
        state = SIGN;
    }

    /**
     * Returns the signature bytes of all the data updated.
     * The format of the signature depends on the underlying
     * signature scheme.
     *
     * <p>A call to this method resets this signature object to the state
     * it was in when previously initialized for signing via a
     * call to {@code initSign(PrivateKey)}. That is, the object is
     * reset and available to generate another signature from the same
     * signer, if desired, via new calls to {@code update} and
     * {@code sign}.
     *
     * @return the signature bytes of the signing operation's result.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly or if this signature algorithm is unable to
     * process the input data provided.
     */
    public final byte[] sign() throws SignatureException {
        if (state == SIGN) {
            return engineSign();
        }
        throw new SignatureException("object not initialized for " +
                                     "signing");
    }

    /**
     * Finishes the signature operation and stores the resulting signature
     * bytes in the provided buffer {@code outbuf}, starting at
     * {@code offset}.
     * The format of the signature depends on the underlying
     * signature scheme.
     *
     * <p>This signature object is reset to its initial state (the state it
     * was in after a call to one of the {@code initSign} methods) and
     * can be reused to generate further signatures with the same private key.
     *
     * @param outbuf buffer for the signature result.
     *
     * @param offset offset into {@code outbuf} where the signature is
     * stored.
     *
     * @param len number of bytes within {@code outbuf} allotted for the
     * signature.
     *
     * @return the number of bytes placed into {@code outbuf}.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly, if this signature algorithm is unable to
     * process the input data provided, or if {@code len} is less
     * than the actual signature length.
     *
     * @since 1.2
     */
    public final int sign(byte[] outbuf, int offset, int len)
        throws SignatureException {
        if (outbuf == null) {
            throw new IllegalArgumentException("No output buffer given");
        }
        if (outbuf.length - offset < len) {
            throw new IllegalArgumentException
                ("Output buffer too small for specified offset and length");
        }
        if (state != SIGN) {
            throw new SignatureException("object not initialized for " +
                                         "signing");
        }
        return engineSign(outbuf, offset, len);
    }

    /**
     * Verifies the passed-in signature.
     *
     * <p>A call to this method resets this signature object to the state
     * it was in when previously initialized for verification via a
     * call to {@code initVerify(PublicKey)}. That is, the object is
     * reset and available to verify another signature from the identity
     * whose public key was specified in the call to {@code initVerify}.
     *
     * @param signature the signature bytes to be verified.
     *
     * @return true if the signature was verified, false if not.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly, the passed-in signature is improperly
     * encoded or of the wrong type, if this signature algorithm is unable to
     * process the input data provided, etc.
     */
    public final boolean verify(byte[] signature) throws SignatureException {
        if (state == VERIFY) {
            return engineVerify(signature);
        }
        throw new SignatureException("object not initialized for " +
                                     "verification");
    }

    /**
     * Verifies the passed-in signature in the specified array
     * of bytes, starting at the specified offset.
     *
     * <p>A call to this method resets this signature object to the state
     * it was in when previously initialized for verification via a
     * call to {@code initVerify(PublicKey)}. That is, the object is
     * reset and available to verify another signature from the identity
     * whose public key was specified in the call to {@code initVerify}.
     *
     *
     * @param signature the signature bytes to be verified.
     * @param offset the offset to start from in the array of bytes.
     * @param length the number of bytes to use, starting at offset.
     *
     * @return true if the signature was verified, false if not.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly, the passed-in signature is improperly
     * encoded or of the wrong type, if this signature algorithm is unable to
     * process the input data provided, etc.
     * @exception IllegalArgumentException if the {@code signature}
     * byte array is null, or the {@code offset} or {@code length}
     * is less than 0, or the sum of the {@code offset} and
     * {@code length} is greater than the length of the
     * {@code signature} byte array.
     * @since 1.4
     */
    public final boolean verify(byte[] signature, int offset, int length)
        throws SignatureException {
        if (state == VERIFY) {
            if ((signature == null) || (offset < 0) || (length < 0) ||
                (length > signature.length - offset)) {
                throw new IllegalArgumentException("Bad arguments");
            }

            return engineVerify(signature, offset, length);
        }
        throw new SignatureException("object not initialized for " +
                                     "verification");
    }

    /**
     * Updates the data to be signed or verified by a byte.
     *
     * @param b the byte to use for the update.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly.
     */
    public final void update(byte b) throws SignatureException {
        if (state == VERIFY || state == SIGN) {
            engineUpdate(b);
        } else {
            throw new SignatureException("object not initialized for "
                                         + "signature or verification");
        }
    }

    /**
     * Updates the data to be signed or verified, using the specified
     * array of bytes.
     *
     * @param data the byte array to use for the update.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly.
     */
    public final void update(byte[] data) throws SignatureException {
        update(data, 0, data.length);
    }

    /**
     * Updates the data to be signed or verified, using the specified
     * array of bytes, starting at the specified offset.
     *
     * @param data the array of bytes.
     * @param off the offset to start from in the array of bytes.
     * @param len the number of bytes to use, starting at offset.
     *
     * @exception SignatureException if this signature object is not
     * initialized properly.
     */
    public final void update(byte[] data, int off, int len)
            throws SignatureException {
        if (state == SIGN || state == VERIFY) {
            engineUpdate(data, off, len);
        } else {
            throw new SignatureException("object not initialized for "
                                         + "signature or verification");
        }
    }

    /**
     * Updates the data to be signed or verified using the specified
     * ByteBuffer. Processes the {@code data.remaining()} bytes
     * starting at at {@code data.position()}.
     * Upon return, the buffer's position will be equal to its limit;
     * its limit will not have changed.
     *
     * @param data the ByteBuffer
     *
     * @exception SignatureException if this signature object is not
     * initialized properly.
     * @since 1.5
     */
    public final void update(ByteBuffer data) throws SignatureException {
        if ((state != SIGN) && (state != VERIFY)) {
            throw new SignatureException("object not initialized for "
                                         + "signature or verification");
        }
        if (data == null) {
            throw new NullPointerException();
        }
        engineUpdate(data);
    }

    /**
     * Returns the name of the algorithm for this signature object.
     *
     * @return the name of the algorithm for this signature object.
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns a string representation of this signature object,
     * providing information that includes the state of the object
     * and the name of the algorithm used.
     *
     * @return a string representation of this signature object.
     */
    public String toString() {
        String initState = "";
        switch (state) {
        case UNINITIALIZED:
            initState = "<not initialized>";
            break;
        case VERIFY:
            initState = "<initialized for verifying>";
            break;
        case SIGN:
            initState = "<initialized for signing>";
            break;
        }
        return "Signature object: " + getAlgorithm() + initState;
    }

    /**
     * Sets the specified algorithm parameter to the specified value.
     * This method supplies a general-purpose mechanism through
     * which it is possible to set the various parameters of this object.
     * A parameter may be any settable parameter for the algorithm, such as
     * a parameter size, or a source of random bits for signature generation
     * (if appropriate), or an indication of whether or not to perform
     * a specific but optional computation. A uniform algorithm-specific
     * naming scheme for each parameter is desirable but left unspecified
     * at this time.
     *
     * @param param the string identifier of the parameter.
     * @param value the parameter value.
     *
     * @exception InvalidParameterException if {@code param} is an
     * invalid parameter for this signature algorithm engine,
     * the parameter is already set
     * and cannot be set again, a security exception occurs, and so on.
     *
     * @see #getParameter
     *
     * @deprecated Use
     * {@link #setParameter(java.security.spec.AlgorithmParameterSpec)
     * setParameter}.
     */
    @Deprecated
    public final void setParameter(String param, Object value)
            throws InvalidParameterException {
        engineSetParameter(param, value);
    }

    /**
     * Initializes this signature engine with the specified parameter set.
     *
     * @param params the parameters
     *
     * @exception InvalidAlgorithmParameterException if the given parameters
     * are inappropriate for this signature engine
     *
     * @see #getParameters
     */
    public final void setParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        engineSetParameter(params);
    }

    /**
     * Returns the parameters used with this signature object.
     *
     * <p>The returned parameters may be the same that were used to initialize
     * this signature, or may contain a combination of default and randomly
     * generated parameter values used by the underlying signature
     * implementation if this signature requires algorithm parameters but
     * was not initialized with any.
     *
     * @return the parameters used with this signature, or null if this
     * signature does not use any parameters.
     *
     * @see #setParameter(AlgorithmParameterSpec)
     * @since 1.4
     */
    public final AlgorithmParameters getParameters() {
        return engineGetParameters();
    }

    /**
     * Gets the value of the specified algorithm parameter. This method
     * supplies a general-purpose mechanism through which it is possible to
     * get the various parameters of this object. A parameter may be any
     * settable parameter for the algorithm, such as a parameter size, or
     * a source of random bits for signature generation (if appropriate),
     * or an indication of whether or not to perform a specific but optional
     * computation. A uniform algorithm-specific naming scheme for each
     * parameter is desirable but left unspecified at this time.
     *
     * @param param the string name of the parameter.
     *
     * @return the object that represents the parameter value, or null if
     * there is none.
     *
     * @exception InvalidParameterException if {@code param} is an invalid
     * parameter for this engine, or another exception occurs while
     * trying to get this parameter.
     *
     * @see #setParameter(String, Object)
     *
     * @deprecated
     */
    @Deprecated
    public final Object getParameter(String param)
            throws InvalidParameterException {
        return engineGetParameter(param);
    }

    /**
     * Returns a clone if the implementation is cloneable.
     *
     * @return a clone if the implementation is cloneable.
     *
     * @exception CloneNotSupportedException if this is called
     * on an implementation that does not support {@code Cloneable}.
     */
    public Object clone() throws CloneNotSupportedException {
        if (this instanceof Cloneable) {
            return super.clone();
        } else {
            throw new CloneNotSupportedException();
        }
    }

    /*
     * The following class allows providers to extend from SignatureSpi
     * rather than from Signature. It represents a Signature with an
     * encapsulated, provider-supplied SPI object (of type SignatureSpi).
     * If the provider implementation is an instance of SignatureSpi, the
     * getInstance() methods above return an instance of this class, with
     * the SPI object encapsulated.
     *
     * Note: All SPI methods from the original Signature class have been
     * moved up the hierarchy into a new class (SignatureSpi), which has
     * been interposed in the hierarchy between the API (Signature)
     * and its original parent (Object).
     */

    @SuppressWarnings("deprecation")
    private static class Delegate extends Signature {

        // The provider implementation (delegate)
        // filled in once the provider is selected
        private SignatureSpi sigSpi;

        // lock for mutex during provider selection
        private final Object lock;

        // next service to try in provider selection
        // null once provider is selected
        private Service firstService;

        // remaining services to try in provider selection
        // null once provider is selected
        private Iterator<Service> serviceIterator;

        // constructor
        Delegate(SignatureSpi sigSpi, String algorithm) {
            super(algorithm);
            this.sigSpi = sigSpi;
            this.lock = null; // no lock needed
        }

        // used with delayed provider selection
        Delegate(Service service,
                        Iterator<Service> iterator, String algorithm) {
            super(algorithm);
            this.firstService = service;
            this.serviceIterator = iterator;
            this.lock = new Object();
        }

        /**
         * Returns a clone if the delegate is cloneable.
         *
         * @return a clone if the delegate is cloneable.
         *
         * @exception CloneNotSupportedException if this is called on a
         * delegate that does not support {@code Cloneable}.
         */
        public Object clone() throws CloneNotSupportedException {
            chooseFirstProvider();
            if (sigSpi instanceof Cloneable) {
                SignatureSpi sigSpiClone = (SignatureSpi)sigSpi.clone();
                // Because 'algorithm' and 'provider' are private
                // members of our supertype, we must perform a cast to
                // access them.
                Signature that =
                    new Delegate(sigSpiClone, ((Signature)this).algorithm);
                that.provider = ((Signature)this).provider;
                return that;
            } else {
                throw new CloneNotSupportedException();
            }
        }

        private static SignatureSpi newInstance(Service s)
                throws NoSuchAlgorithmException {
            if (s.getType().equals("Cipher")) {
                // must be NONEwithRSA
                try {
                    Cipher c = Cipher.getInstance(RSA_CIPHER, s.getProvider());
                    return new CipherAdapter(c);
                } catch (NoSuchPaddingException e) {
                    throw new NoSuchAlgorithmException(e);
                }
            } else {
                Object o = s.newInstance(null);
                if (o instanceof SignatureSpi == false) {
                    throw new NoSuchAlgorithmException
                        ("Not a SignatureSpi: " + o.getClass().getName());
                }
                return (SignatureSpi)o;
            }
        }

        // max number of debug warnings to print from chooseFirstProvider()
        private static int warnCount = 10;

        /**
         * Choose the Spi from the first provider available. Used if
         * delayed provider selection is not possible because initSign()/
         * initVerify() is not the first method called.
         */
        void chooseFirstProvider() {
            if (sigSpi != null) {
                return;
            }
            synchronized (lock) {
                if (sigSpi != null) {
                    return;
                }
                if (debug != null) {
                    int w = --warnCount;
                    if (w >= 0) {
                        debug.println("Signature.init() not first method "
                            + "called, disabling delayed provider selection");
                        if (w == 0) {
                            debug.println("Further warnings of this type will "
                                + "be suppressed");
                        }
                        new Exception("Call trace").printStackTrace();
                    }
                }
                Exception lastException = null;
                while ((firstService != null) || serviceIterator.hasNext()) {
                    Service s;
                    if (firstService != null) {
                        s = firstService;
                        firstService = null;
                    } else {
                        s = serviceIterator.next();
                    }
                    if (isSpi(s) == false) {
                        continue;
                    }
                    try {
                        sigSpi = newInstance(s);
                        provider = s.getProvider();
                        // not needed any more
                        firstService = null;
                        serviceIterator = null;
                        return;
                    } catch (NoSuchAlgorithmException e) {
                        lastException = e;
                    }
                }
                ProviderException e = new ProviderException
                        ("Could not construct SignatureSpi instance");
                if (lastException != null) {
                    e.initCause(lastException);
                }
                throw e;
            }
        }

        private void chooseProvider(int type, Key key, SecureRandom random)
                throws InvalidKeyException {
            synchronized (lock) {
                if (sigSpi != null) {
                    init(sigSpi, type, key, random);
                    return;
                }
                Exception lastException = null;
                while ((firstService != null) || serviceIterator.hasNext()) {
                    Service s;
                    if (firstService != null) {
                        s = firstService;
                        firstService = null;
                    } else {
                        s = serviceIterator.next();
                    }
                    // if provider says it does not support this key, ignore it
                    if (s.supportsParameter(key) == false) {
                        continue;
                    }
                    // if instance is not a SignatureSpi, ignore it
                    if (isSpi(s) == false) {
                        continue;
                    }
                    try {
                        SignatureSpi spi = newInstance(s);
                        init(spi, type, key, random);
                        provider = s.getProvider();
                        sigSpi = spi;
                        firstService = null;
                        serviceIterator = null;
                        return;
                    } catch (Exception e) {
                        // NoSuchAlgorithmException from newInstance()
                        // InvalidKeyException from init()
                        // RuntimeException (ProviderException) from init()
                        if (lastException == null) {
                            lastException = e;
                        }
                    }
                }
                // no working provider found, fail
                if (lastException instanceof InvalidKeyException) {
                    throw (InvalidKeyException)lastException;
                }
                if (lastException instanceof RuntimeException) {
                    throw (RuntimeException)lastException;
                }
                String k = (key != null) ? key.getClass().getName() : "(null)";
                throw new InvalidKeyException
                    ("No installed provider supports this key: "
                    + k, lastException);
            }
        }

        private final static int I_PUB     = 1;
        private final static int I_PRIV    = 2;
        private final static int I_PRIV_SR = 3;

        private void init(SignatureSpi spi, int type, Key  key,
                SecureRandom random) throws InvalidKeyException {
            switch (type) {
            case I_PUB:
                spi.engineInitVerify((PublicKey)key);
                break;
            case I_PRIV:
                spi.engineInitSign((PrivateKey)key);
                break;
            case I_PRIV_SR:
                spi.engineInitSign((PrivateKey)key, random);
                break;
            default:
                throw new AssertionError("Internal error: " + type);
            }
        }

        protected void engineInitVerify(PublicKey publicKey)
                throws InvalidKeyException {
            if (sigSpi != null) {
                sigSpi.engineInitVerify(publicKey);
            } else {
                chooseProvider(I_PUB, publicKey, null);
            }
        }

        protected void engineInitSign(PrivateKey privateKey)
                throws InvalidKeyException {
            if (sigSpi != null) {
                sigSpi.engineInitSign(privateKey);
            } else {
                chooseProvider(I_PRIV, privateKey, null);
            }
        }

        protected void engineInitSign(PrivateKey privateKey, SecureRandom sr)
                throws InvalidKeyException {
            if (sigSpi != null) {
                sigSpi.engineInitSign(privateKey, sr);
            } else {
                chooseProvider(I_PRIV_SR, privateKey, sr);
            }
        }

        protected void engineUpdate(byte b) throws SignatureException {
            chooseFirstProvider();
            sigSpi.engineUpdate(b);
        }

        protected void engineUpdate(byte[] b, int off, int len)
                throws SignatureException {
            chooseFirstProvider();
            sigSpi.engineUpdate(b, off, len);
        }

        protected void engineUpdate(ByteBuffer data) {
            chooseFirstProvider();
            sigSpi.engineUpdate(data);
        }

        protected byte[] engineSign() throws SignatureException {
            chooseFirstProvider();
            return sigSpi.engineSign();
        }

        protected int engineSign(byte[] outbuf, int offset, int len)
                throws SignatureException {
            chooseFirstProvider();
            return sigSpi.engineSign(outbuf, offset, len);
        }

        protected boolean engineVerify(byte[] sigBytes)
                throws SignatureException {
            chooseFirstProvider();
            return sigSpi.engineVerify(sigBytes);
        }

        protected boolean engineVerify(byte[] sigBytes, int offset, int length)
                throws SignatureException {
            chooseFirstProvider();
            return sigSpi.engineVerify(sigBytes, offset, length);
        }

        protected void engineSetParameter(String param, Object value)
                throws InvalidParameterException {
            chooseFirstProvider();
            sigSpi.engineSetParameter(param, value);
        }

        protected void engineSetParameter(AlgorithmParameterSpec params)
                throws InvalidAlgorithmParameterException {
            chooseFirstProvider();
            sigSpi.engineSetParameter(params);
        }

        protected Object engineGetParameter(String param)
                throws InvalidParameterException {
            chooseFirstProvider();
            return sigSpi.engineGetParameter(param);
        }

        protected AlgorithmParameters engineGetParameters() {
            chooseFirstProvider();
            return sigSpi.engineGetParameters();
        }
    }

    // adapter for RSA/ECB/PKCS1Padding ciphers
    @SuppressWarnings("deprecation")
    private static class CipherAdapter extends SignatureSpi {

        private final Cipher cipher;

        private ByteArrayOutputStream data;

        CipherAdapter(Cipher cipher) {
            this.cipher = cipher;
        }

        protected void engineInitVerify(PublicKey publicKey)
                throws InvalidKeyException {
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            if (data == null) {
                data = new ByteArrayOutputStream(128);
            } else {
                data.reset();
            }
        }

        protected void engineInitSign(PrivateKey privateKey)
                throws InvalidKeyException {
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            data = null;
        }

        protected void engineInitSign(PrivateKey privateKey,
                SecureRandom random) throws InvalidKeyException {
            cipher.init(Cipher.ENCRYPT_MODE, privateKey, random);
            data = null;
        }

        protected void engineUpdate(byte b) throws SignatureException {
            engineUpdate(new byte[] {b}, 0, 1);
        }

        protected void engineUpdate(byte[] b, int off, int len)
                throws SignatureException {
            if (data != null) {
                data.write(b, off, len);
                return;
            }
            byte[] out = cipher.update(b, off, len);
            if ((out != null) && (out.length != 0)) {
                throw new SignatureException
                    ("Cipher unexpectedly returned data");
            }
        }

        protected byte[] engineSign() throws SignatureException {
            try {
                return cipher.doFinal();
            } catch (IllegalBlockSizeException e) {
                throw new SignatureException("doFinal() failed", e);
            } catch (BadPaddingException e) {
                throw new SignatureException("doFinal() failed", e);
            }
        }

        protected boolean engineVerify(byte[] sigBytes)
                throws SignatureException {
            try {
                byte[] out = cipher.doFinal(sigBytes);
                byte[] dataBytes = data.toByteArray();
                data.reset();
                return Arrays.equals(out, dataBytes);
            } catch (BadPaddingException e) {
                // e.g. wrong public key used
                // return false rather than throwing exception
                return false;
            } catch (IllegalBlockSizeException e) {
                throw new SignatureException("doFinal() failed", e);
            }
        }

        protected void engineSetParameter(String param, Object value)
                throws InvalidParameterException {
            throw new InvalidParameterException("Parameters not supported");
        }

        protected Object engineGetParameter(String param)
                throws InvalidParameterException {
            throw new InvalidParameterException("Parameters not supported");
        }

    }

}
