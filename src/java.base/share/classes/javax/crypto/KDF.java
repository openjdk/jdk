/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.Provider.Service;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Objects;

import sun.security.util.Debug;
import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

import javax.crypto.spec.KDFParameterSpec;

/**
 * This class provides the functionality of a key derivation algorithm for JCE.
 * <p>
 * {@code KDF} objects are instantiated through the {@code getInstance} family
 * of methods.  Key derivation algorithm names follow a naming convention of
 * <I>algorithm</I>/<I>PRF</I>.  The algorithm field is the KDF algorithm (e.g.
 * HKDF, TLS-PRF, PBKDF2, etc.), while the PRF specifier identifies the
 * underlying pseudorandom function (e.g. HmacSHA256).  For instance, a KDF
 * implementation of HKDF using HMAC-SHA256 will have an algorithm string of
 * "HKDF/HmacSHA256".  In some cases the PRF portion of the algorithm specifier
 * may be omitted if the KDF algorithm has a fixed or default PRF.
 * <p>
 * TODO: finish this javadoc
 *
 * @since 23
 */

public final class KDF {
    private static final Debug debug = Debug.getInstance("jca",
                                                         "KeyDerivation");

    private static final Debug pdebug = Debug.getInstance("provider",
                                                          "Provider");
    private static final boolean skipDebug = Debug.isOn("engine=")
                                             && !Debug.isOn("keyderive");

    // The provider
    private Provider provider;

    // The provider implementation (delegate)
    private KDFSpi spi;

    // The name of the MAC algorithm.
    private final String algorithm;

    // Additional KDF configuration parameters
    private final AlgorithmParameterSpec algorithmParameterSpec;

    // next service to try in provider selection
    // null once provider is selected
    private Service firstService;

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    private final Object lock;

    /**
     * Instantiates a KDF object.
     *
     * @param keyDerivSpi
     *     the delegate
     * @param provider
     *     the provider
     * @param algorithm
     *     the algorithm
     * @param algParameterSpec
     *     the algorithm parameters
     */
    private KDF(KDFSpi keyDerivSpi, Provider provider, String algorithm,
                AlgorithmParameterSpec algParameterSpec) {
        this.spi = keyDerivSpi;
        this.provider = provider;
        this.algorithm = algorithm;
        this.algorithmParameterSpec = algParameterSpec;
        // the lock is not needed, because the Spi will already be set in
        // chooseProvider
        lock = null;
    }

    private KDF(Service s, Iterator<Service> t, String algorithm,
                AlgorithmParameterSpec algParameterSpec) {
        firstService = s;
        serviceIterator = t;
        this.algorithm = algorithm;
        this.algorithmParameterSpec = algParameterSpec;
        lock = new Object();
    }

    /**
     * Returns the algorithm name of this {@code KDF} object.
     *
     * <p>This is the same name that was specified in one of the
     * {@code getInstance} calls that created this {@code KDF} object.
     *
     * @return the algorithm name of this {@code KDF} object
     */
    public String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the name of the provider.
     *
     * @return the name of the provider
     */
    public String getProviderName() {
        chooseFirstProvider();
        return provider.getName();
    }

    /**
     * Creates an instance of the {@code KDF} object.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm)
        throws NoSuchAlgorithmException {
        KDF instance;
        try {
            instance = getInstance(algorithm, (AlgorithmParameterSpec) null);
        } catch (InvalidParameterSpecException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidParameterSpecException. Does this "
                + "algorithm require an "
                + "AlgorithmParameterSpec?");
        }
        return instance;
    }

    /**
     * Creates an instance of the {@code KDF} object with a specific
     * {@code Provider}.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm, String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException {
        KDF instance;
        try {
            instance = getInstance(algorithm, null,
                                   provider);
        } catch (InvalidParameterSpecException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidParameterSpecException. Does this "
                + "algorithm require an "
                + "AlgorithmParameterSpec?");
        }
        return instance;
    }

    /**
     * Creates an instance of the {@code KDF} object using a supplied
     * {@code Provider} instance.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param provider
     *     the provider
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm, Provider provider)
        throws NoSuchAlgorithmException {
        KDF instance;
        try {
            instance = getInstance(algorithm, null,
                                   provider);
        } catch (InvalidParameterSpecException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidParameterSpecException. Does this "
                + "algorithm require an "
                + "AlgorithmParameterSpec?");
        }
        return instance;
    }

    /**
     * Creates an instance of the {@code KDF} object.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param algParameterSpec
     *     the {@code AlgorithmParameterSpec} used to configure this KDF's
     *     algorithm or {@code null} if no additional parameters were provided.
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws InvalidParameterSpecException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec)
        throws NoSuchAlgorithmException, InvalidParameterSpecException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        // make sure there is at least one service from a signed provider
        Iterator<Service> t = GetInstance.getServices("KDF", algorithm);
        while (t.hasNext()) {
            Service s = t.next();
            if (!JceSecurity.canUseProvider(s.getProvider())) {
                continue;
            }
            return new KDF(s, t, algorithm, algParameterSpec);
        }
        throw new NoSuchAlgorithmException
                  ("Algorithm " + algorithm + " not available");
    }

    /**
     * Creates an instance of the {@code KDF} object with a specific
     * {@code Provider}.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param algParameterSpec
     *     the {@code AlgorithmParameterSpec} used to configure this KDF's
     *     algorithm or {@code null} if no additional parameters were provided.
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm.
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws InvalidParameterSpecException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec,
                                  String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
               InvalidParameterSpecException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        algParameterSpec,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new NoSuchProviderException(msg);
            }
            return new KDF((KDFSpi) instance.impl, instance.provider, algorithm,
                           algParameterSpec);

        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException(
                "Algorithm " + algorithm + " not available", nsae);
        }
    }

    /**
     * Creates an instance of the {@code KDF} object using a supplied
     * {@code Provider} instance.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param algParameterSpec
     *     the {@code AlgorithmParameterSpec} used to configure this KDF's
     *     algorithm or {@code null} if no additional parameters were provided.
     * @param provider
     *     the provider
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws InvalidParameterSpecException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec,
                                  Provider provider)
        throws NoSuchAlgorithmException, InvalidParameterSpecException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        algParameterSpec,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new SecurityException(msg);
            }
            return new KDF((KDFSpi) instance.impl, instance.provider, algorithm,
                           algParameterSpec);

        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException(
                "Algorithm " + algorithm + " not available", nsae);
        }
    }

    /**
     * Derive a key, returned as a {@code Key}.
     * <p>
     * TODO: additional description
     *
     * @param alg
     *     the algorithm of the resultant {@code Key} object
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a {@code SecretKey} object corresponding to a key built from the
     * KDF output and according to the derivation parameters
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived, or specifies
     *     a type of output that is not a key (e.g. raw data)
     * @throws IllegalStateException
     *     if the key derivation implementation cannot support additional calls
     *     to {@code deriveKey} or if all {@code KDFParameterSpec} objects
     *     provided at initialization have been processed
     * @throws InvalidAlgorithmParameterException
     *     TODO: fill this in
     */
    public SecretKey deriveKey(String alg, KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException,
               InvalidAlgorithmParameterException {
        synchronized (lock) {
            if (spi != null) {
                return spi.engineDeriveKey(alg, kdfParameterSpec);
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
                if (!JceSecurity.canUseProvider(s.getProvider())) {
                    continue;
                }
                try {
                    KDFSpi spi = (KDFSpi)s.newInstance(algorithmParameterSpec);
                    provider = s.getProvider();
                    this.spi = spi;
                    firstService = null;
                    serviceIterator = null;
                    return spi.engineDeriveKey(alg, kdfParameterSpec);
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if(lastException instanceof InvalidParameterSpecException) {
                throw (InvalidParameterSpecException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException)lastException;
            }
        }
        // should never reach here
        return null;
    }

    /**
     * Obtain raw data from a key derivation function.
     * <p>
     * TODO: additional description
     *
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a byte array whose length matches the length field in the
     * processed {@code DerivationParameterSpec} and containing the next bytes
     * of output from the key derivation function
     *
     * @throws InvalidParameterSpecException
     *     if the {@code DerivationParameterSpec} being applied to this method
     *     is of a type other than "data"
     * @throws IllegalStateException
     *     if the key derivation implementation cannot support additional calls
     *     to {@code deriveData } or if all {@code DerivationParameterSpec}
     *     objects have been processed
     * @throws UnsupportedOperationException
     *     if the derived key material is not extractable
     */
    public byte[] deriveData(KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {
        synchronized (lock) {
            if (spi != null) {
                return spi.engineDeriveData(kdfParameterSpec);
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
                if (!JceSecurity.canUseProvider(s.getProvider())) {
                    continue;
                }
                try {
                    KDFSpi spi = (KDFSpi)s.newInstance(algorithmParameterSpec);
                    provider = s.getProvider();
                    this.spi = spi;
                    firstService = null;
                    serviceIterator = null;
                    return spi.engineDeriveData(kdfParameterSpec);
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if(lastException instanceof InvalidParameterSpecException) {
                throw (InvalidParameterSpecException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException)lastException;
            }
        }
        // should never reach here
        return null;
    }

    // max number of debug warnings to print from chooseFirstProvider()
    private static int warnCount = 10;

    /**
     * Choose the Spi from the first provider available. Used if delayed
     * provider selection is not possible because init() is not the first method
     * called.
     */
    void chooseFirstProvider() {
        if ((spi != null) || (serviceIterator == null)) {
            return;
        }
        synchronized (lock) {
            if (spi != null) {
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
                if (!JceSecurity.canUseProvider(s.getProvider())) {
                    continue;
                }
                try {
                    Object obj = s.newInstance(algorithmParameterSpec);
                    if (!(obj instanceof KDFSpi)) {
                        continue;
                    }
                    spi = (KDFSpi) obj;
                    provider = s.getProvider();
                    // not needed any more
                    firstService = null;
                    serviceIterator = null;
                    return;
                } catch (NoSuchAlgorithmException e) {
                    lastException = e;
                }
            }
            ProviderException e = new ProviderException(
                "Could not construct KDFSpi instance");
            if (lastException != null) {
                e.initCause(lastException);
            }
            throw e;
        }
    }
}