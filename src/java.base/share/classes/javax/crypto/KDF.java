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

import jdk.internal.javac.PreviewFeature;
import sun.security.jca.GetInstance;
import sun.security.jca.GetInstance.Instance;
import sun.security.util.Debug;

import javax.crypto.spec.KDFParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Iterator;
import java.util.Objects;

/**
 * This class provides the functionality of a Key Derivation Function (KDF).
 * {@code KDF} objects are instantiated with the {@code getInstance} family of
 * methods. KDF algorithm names follow a naming convention of
 * <em>Algorithm</em>With<em>PRF</em>. For instance, a KDF implementation of
 * HKDF using HMAC-SHA256 has an algorithm name of "HKDFWithHmacSHA256". In some
 * cases the PRF portion of the algorithm field may be omitted if the KDF
 * algorithm has a fixed or default PRF.
 * <p>
 * Example:
 * {@snippet lang = java:
 *    KDF kdfHkdf = KDF.getInstance("HKDFWithHmacSHA256");
 *
 *    KDFParameterSpec kdfParameterSpec =
 *             HKDFParameterSpec.extract()
 *                              .addIKM(ikm)
 *                              .addSalt(salt).andExpand(info, 42);
 *
 *    kdfHkdf.deriveKey("AES", kdfParameterSpec);
 *}
 *
 * @see SecretKey
 * @since 23
 */
@PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
public final class KDF {
    private static final Debug debug = Debug.getInstance("jca",
                                                         "KDF");

    private static final Debug pdebug = Debug.getInstance("provider",
                                                          "Provider");
    private static final boolean skipDebug = Debug.isOn("engine=")
                                             && !Debug.isOn("kdf");

    // The provider
    private Provider provider;

    // The provider implementation (delegate)
    private KDFSpi spi;

    // The name of the KDF algorithm.
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
     * Returns a {@code KDF} object that implements the specified algorithm.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDF} implementation for the
     *     specified algorithm
     * @throws NullPointerException
     *     if {@code algorithm} is {@code null}
     */
    public static KDF getInstance(String algorithm)
        throws NoSuchAlgorithmException {
        try {
            return getInstance(algorithm, (AlgorithmParameterSpec) null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidAlgorithmParameterException. Does this "
                + "algorithm require an AlgorithmParameterSpec?", e);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm from
     * the specified security provider.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param provider
     *     the provider to use for this key derivation; if null, this method is
     *     equivalent to {@code getInstance(String)}
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if a provider is specified and it does not support the specified KDF
     *     algorithm, or if provider is {@code null} and there is no provider that
     *     supports a KDF implementation of the specified algorithm
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm, String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException {
        try {
            return getInstance(algorithm, null,
                               provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidAlgorithmParameterException. Does this "
                + "algorithm require an AlgorithmParameterSpec?", e);
        }
    }

    /**
     * Returns a {code KDF} object that implements the specified algorithm from
     * the specified security provider.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param provider
     *     the provider to use for this key derivation; if null, this method is
     *     equivalent to {@code getInstance(String)}
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if a provider is specified and it does not support the specified KDF
     *     algorithm, or if provider is {@code null} and there is no provider that
     *     supports a KDF implementation of the specified algorithm
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm, Provider provider)
        throws NoSuchAlgorithmException {
        try {
            return getInstance(algorithm, null,
                               provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "Received an InvalidAlgorithmParameterException. Does this "
                + "algorithm require an AlgorithmParameterSpec?", e);
        }
    }

    /**
     * Returns a {@code KDF} object that implements the specified algorithm and
     * is initialized with the specified parameters.
     *
     * @param algorithm
     *     the key derivation algorithm to use
     * @param algParameterSpec
     *     the {@code AlgorithmParameterSpec} used to configure this KDF's
     *     algorithm or {@code null} if no additional parameters are provided
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws InvalidAlgorithmParameterException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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
     *     algorithm or {@code null} if no additional parameters are provided
     * @param provider
     *     the provider to use for this key derivation; if null, this method is
     *     equivalent to {@code getInstance(String, AlgorithmParameterSpec)}
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if a provider is specified and it does not support the specified KDF
     *     algorithm, or if provider is {@code null} and there is no provider that
     *     supports a KDF implementation of the specified algorithm
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws InvalidAlgorithmParameterException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec,
                                  String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
               InvalidAlgorithmParameterException {
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
            return handleException(nsae);
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
     *     algorithm or {@code null} if no additional parameters are provided
     * @param provider
     *     the provider to use for this key derivation; if null, this method is
     *     equivalent to {@code getInstance(String, AlgorithmParameterSpec)}
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if a provider is specified and it does not support the specified KDF
     *     algorithm, or if provider is {@code null} and there is no provider that
     *     supports a KDF implementation of the specified algorithm
     * @throws InvalidAlgorithmParameterException
     *     if the {@code AlgorithmParameterSpec} is an invalid value
     * @throws NullPointerException
     *     if the algorithm is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  AlgorithmParameterSpec algParameterSpec,
                                  Provider provider)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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
            return handleException(nsae);
        }
    }

    private static KDF handleException(NoSuchAlgorithmException e)
        throws NoSuchAlgorithmException,
               InvalidAlgorithmParameterException {
        Throwable cause = e.getCause();
        if (cause instanceof InvalidAlgorithmParameterException) {
            throw (InvalidAlgorithmParameterException) cause;
        }
        throw e;
    }

    /**
     * Derives a key, returned as a {@code SecretKey}.
     * <p>
     * The {@code deriveKey} method may be called multiple times on a particular
     * {@code KDF} instance.
     * <p>
     * Delayed provider selection is also supported such that the provider
     * performing the derive is not selected until the method is called.
     *
     * @param alg
     *     the algorithm of the resultant {@code SecretKey} object
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a {@code SecretKey} object corresponding to a key built from the
     *     KDF output and according to the derivation parameters
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived
     * @throws NullPointerException
     *     if {@code alg} is null
     */
    public SecretKey deriveKey(String alg, KDFParameterSpec kdfParameterSpec)
        throws InvalidParameterSpecException {
        synchronized (lock) {
            if (alg == null || alg.isEmpty()) {
                throw new NullPointerException(
                    "the algorithm for the SecretKey return value may not be "
                    + "null or empty");
            }
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
                    KDFSpi spi = (KDFSpi) s.newInstance(algorithmParameterSpec);
                    SecretKey result = spi.engineDeriveKey(alg,
                                                           kdfParameterSpec);
                    provider = s.getProvider();
                    this.spi = spi;
                    firstService = null;
                    serviceIterator = null;
                    return result;
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if (lastException instanceof InvalidParameterSpecException) {
                throw (InvalidParameterSpecException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
            }
        }
        // should never reach here
        return null;
    }

    /**
     * Obtains raw data from a key derivation function.
     * <p>
     * The {@code deriveData} method may be called multiple times on a
     * particular {@code KDF} instance.
     * <p>
     * Delayed provider selection is also supported such that the provider
     * performing the derive is not selected until the method is called.
     *
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a byte array whose length matches the specified length in the
     *     processed {@code KDFParameterSpec} and containing the output from the
     *     key derivation function
     *
     * @throws InvalidParameterSpecException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived
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
                    KDFSpi spi = (KDFSpi) s.newInstance(algorithmParameterSpec);
                    byte[] result = spi.engineDeriveData(kdfParameterSpec);
                    provider = s.getProvider();
                    this.spi = spi;
                    firstService = null;
                    serviceIterator = null;
                    return result;
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if (lastException instanceof InvalidParameterSpecException) {
                throw (InvalidParameterSpecException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
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