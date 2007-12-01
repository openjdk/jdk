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

import java.util.*;

import java.security.Provider.Service;
import java.security.spec.KeySpec;
import java.security.spec.InvalidKeySpecException;

import sun.security.util.Debug;
import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * Key factories are used to convert <I>keys</I> (opaque
 * cryptographic keys of type <code>Key</code>) into <I>key specifications</I>
 * (transparent representations of the underlying key material), and vice
 * versa.
 *
 * <P> Key factories are bi-directional. That is, they allow you to build an
 * opaque key object from a given key specification (key material), or to
 * retrieve the underlying key material of a key object in a suitable format.
 *
 * <P> Multiple compatible key specifications may exist for the same key.
 * For example, a DSA public key may be specified using
 * <code>DSAPublicKeySpec</code> or
 * <code>X509EncodedKeySpec</code>. A key factory can be used to translate
 * between compatible key specifications.
 *
 * <P> The following is an example of how to use a key factory in order to
 * instantiate a DSA public key from its encoding.
 * Assume Alice has received a digital signature from Bob.
 * Bob also sent her his public key (in encoded format) to verify
 * his signature. Alice then performs the following actions:
 *
 * <pre>
 * X509EncodedKeySpec bobPubKeySpec = new X509EncodedKeySpec(bobEncodedPubKey);
 * KeyFactory keyFactory = KeyFactory.getInstance("DSA");
 * PublicKey bobPubKey = keyFactory.generatePublic(bobPubKeySpec);
 * Signature sig = Signature.getInstance("DSA");
 * sig.initVerify(bobPubKey);
 * sig.update(data);
 * sig.verify(signature);
 * </pre>
 *
 * @author Jan Luehe
 *
 *
 * @see Key
 * @see PublicKey
 * @see PrivateKey
 * @see java.security.spec.KeySpec
 * @see java.security.spec.DSAPublicKeySpec
 * @see java.security.spec.X509EncodedKeySpec
 *
 * @since 1.2
 */

public class KeyFactory {

    private static final Debug debug =
                        Debug.getInstance("jca", "KeyFactory");

    // The algorithm associated with this key factory
    private final String algorithm;

    // The provider
    private Provider provider;

    // The provider implementation (delegate)
    private volatile KeyFactorySpi spi;

    // lock for mutex during provider selection
    private final Object lock = new Object();

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    /**
     * Creates a KeyFactory object.
     *
     * @param keyFacSpi the delegate
     * @param provider the provider
     * @param algorithm the name of the algorithm
     * to associate with this <tt>KeyFactory</tt>
     */
    protected KeyFactory(KeyFactorySpi keyFacSpi, Provider provider,
                         String algorithm) {
        this.spi = keyFacSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    private KeyFactory(String algorithm) throws NoSuchAlgorithmException {
        this.algorithm = algorithm;
        List<Service> list = GetInstance.getServices("KeyFactory", algorithm);
        serviceIterator = list.iterator();
        // fetch and instantiate initial spi
        if (nextSpi(null) == null) {
            throw new NoSuchAlgorithmException
                (algorithm + " KeyFactory not available");
        }
    }

    /**
     * Returns a KeyFactory object that converts
     * public/private keys of the specified algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new KeyFactory object encapsulating the
     * KeyFactorySpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested key algorithm.
     * See Appendix A in the <a href=
     * "../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @return the new KeyFactory object.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          KeyFactorySpi implementation for the
     *          specified algorithm.
     *
     * @see Provider
     */
    public static KeyFactory getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        return new KeyFactory(algorithm);
    }

    /**
     * Returns a KeyFactory object that converts
     * public/private keys of the specified algorithm.
     *
     * <p> A new KeyFactory object encapsulating the
     * KeyFactorySpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested key algorithm.
     * See Appendix A in the <a href=
     * "../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return the new KeyFactory object.
     *
     * @exception NoSuchAlgorithmException if a KeyFactorySpi
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
    public static KeyFactory getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Instance instance = GetInstance.getInstance("KeyFactory",
            KeyFactorySpi.class, algorithm, provider);
        return new KeyFactory((KeyFactorySpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a KeyFactory object that converts
     * public/private keys of the specified algorithm.
     *
     * <p> A new KeyFactory object encapsulating the
     * KeyFactorySpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param algorithm the name of the requested key algorithm.
     * See Appendix A in the <a href=
     * "../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference </a>
     * for information about standard algorithm names.
     *
     * @param provider the provider.
     *
     * @return the new KeyFactory object.
     *
     * @exception NoSuchAlgorithmException if a KeyFactorySpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the specified provider is null.
     *
     * @see Provider
     *
     * @since 1.4
     */
    public static KeyFactory getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("KeyFactory",
            KeyFactorySpi.class, algorithm, provider);
        return new KeyFactory((KeyFactorySpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns the provider of this key factory object.
     *
     * @return the provider of this key factory object
     */
    public final Provider getProvider() {
        synchronized (lock) {
            // disable further failover after this call
            serviceIterator = null;
            return provider;
        }
    }

    /**
     * Gets the name of the algorithm
     * associated with this <tt>KeyFactory</tt>.
     *
     * @return the name of the algorithm associated with this
     * <tt>KeyFactory</tt>
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Update the active KeyFactorySpi of this class and return the next
     * implementation for failover. If no more implemenations are
     * available, this method returns null. However, the active spi of
     * this class is never set to null.
     */
    private KeyFactorySpi nextSpi(KeyFactorySpi oldSpi) {
        synchronized (lock) {
            // somebody else did a failover concurrently
            // try that spi now
            if ((oldSpi != null) && (oldSpi != spi)) {
                return spi;
            }
            if (serviceIterator == null) {
                return null;
            }
            while (serviceIterator.hasNext()) {
                Service s = serviceIterator.next();
                try {
                    Object obj = s.newInstance(null);
                    if (obj instanceof KeyFactorySpi == false) {
                        continue;
                    }
                    KeyFactorySpi spi = (KeyFactorySpi)obj;
                    provider = s.getProvider();
                    this.spi = spi;
                    return spi;
                } catch (NoSuchAlgorithmException e) {
                    // ignore
                }
            }
            serviceIterator = null;
            return null;
        }
    }

    /**
     * Generates a public key object from the provided key specification
     * (key material).
     *
     * @param keySpec the specification (key material) of the public key.
     *
     * @return the public key.
     *
     * @exception InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a public key.
     */
    public final PublicKey generatePublic(KeySpec keySpec)
            throws InvalidKeySpecException {
        if (serviceIterator == null) {
            return spi.engineGeneratePublic(keySpec);
        }
        Exception failure = null;
        KeyFactorySpi mySpi = spi;
        do {
            try {
                return mySpi.engineGeneratePublic(keySpec);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
                mySpi = nextSpi(mySpi);
            }
        } while (mySpi != null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
        }
        if (failure instanceof InvalidKeySpecException) {
            throw (InvalidKeySpecException)failure;
        }
        throw new InvalidKeySpecException
                ("Could not generate public key", failure);
    }

    /**
     * Generates a private key object from the provided key specification
     * (key material).
     *
     * @param keySpec the specification (key material) of the private key.
     *
     * @return the private key.
     *
     * @exception InvalidKeySpecException if the given key specification
     * is inappropriate for this key factory to produce a private key.
     */
    public final PrivateKey generatePrivate(KeySpec keySpec)
            throws InvalidKeySpecException {
        if (serviceIterator == null) {
            return spi.engineGeneratePrivate(keySpec);
        }
        Exception failure = null;
        KeyFactorySpi mySpi = spi;
        do {
            try {
                return mySpi.engineGeneratePrivate(keySpec);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
                mySpi = nextSpi(mySpi);
            }
        } while (mySpi != null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
        }
        if (failure instanceof InvalidKeySpecException) {
            throw (InvalidKeySpecException)failure;
        }
        throw new InvalidKeySpecException
                ("Could not generate private key", failure);
    }

    /**
     * Returns a specification (key material) of the given key object.
     * <code>keySpec</code> identifies the specification class in which
     * the key material should be returned. It could, for example, be
     * <code>DSAPublicKeySpec.class</code>, to indicate that the
     * key material should be returned in an instance of the
     * <code>DSAPublicKeySpec</code> class.
     *
     * @param key the key.
     *
     * @param keySpec the specification class in which
     * the key material should be returned.
     *
     * @return the underlying key specification (key material) in an instance
     * of the requested specification class.
     *
     * @exception InvalidKeySpecException if the requested key specification is
     * inappropriate for the given key, or the given key cannot be processed
     * (e.g., the given key has an unrecognized algorithm or format).
     */
    public final <T extends KeySpec> T getKeySpec(Key key, Class<T> keySpec)
            throws InvalidKeySpecException {
        if (serviceIterator == null) {
            return spi.engineGetKeySpec(key, keySpec);
        }
        Exception failure = null;
        KeyFactorySpi mySpi = spi;
        do {
            try {
                return mySpi.engineGetKeySpec(key, keySpec);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
                mySpi = nextSpi(mySpi);
            }
        } while (mySpi != null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
        }
        if (failure instanceof InvalidKeySpecException) {
            throw (InvalidKeySpecException)failure;
        }
        throw new InvalidKeySpecException
                ("Could not get key spec", failure);
    }

    /**
     * Translates a key object, whose provider may be unknown or potentially
     * untrusted, into a corresponding key object of this key factory.
     *
     * @param key the key whose provider is unknown or untrusted.
     *
     * @return the translated key.
     *
     * @exception InvalidKeyException if the given key cannot be processed
     * by this key factory.
     */
    public final Key translateKey(Key key) throws InvalidKeyException {
        if (serviceIterator == null) {
            return spi.engineTranslateKey(key);
        }
        Exception failure = null;
        KeyFactorySpi mySpi = spi;
        do {
            try {
                return mySpi.engineTranslateKey(key);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
                mySpi = nextSpi(mySpi);
            }
        } while (mySpi != null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException)failure;
        }
        if (failure instanceof InvalidKeyException) {
            throw (InvalidKeyException)failure;
        }
        throw new InvalidKeyException
                ("Could not translate key", failure);
    }

}
