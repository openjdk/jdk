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

import java.security.InvalidAlgorithmParameterException;
import java.security.KDFParameters;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Iterator;
import java.util.Objects;

/**
 * This class provides the functionality of a Key Derivation Function (KDF),
 * which is a cryptographic algorithm for deriving additional keys from input
 * keying material and (optionally) other data.
 * <p>
 * {@code KDF} objects are instantiated with the {@code getInstance} family
 * of methods. For HKDF, KDF algorithm names follow a naming convention of
 * <em>Algorithm</em>With<em>PRF</em>. For instance, a KDF implementation of
 * HKDF using HMAC-SHA256 has an algorithm name of "HKDFWithHmacSHA256". For
 * future algorithms, the "WithPRF" portion of the algorithm field may be
 * omitted if the KDF algorithm has a fixed or default PRF or if the PRF is
 * specified as part of the {@code KDFParameters}.
 * <p>
 * The class has two derive methods, {@code deriveKey} and {@code deriveData}.
 * The {@code deriveKey} method accepts an algorithm {@code String} and
 * will return a {@code SecretKey} object with the specified algorithm. The
 * {@code deriveData} method returns a byte array of raw data.
 * <p>
 * If a provider is not specified in the {@code getInstance} method when
 * instantiating a {@code KDF} object, the provider is selected the first time
 * the {@code deriveKey} or {@code deriveData} method is called, and a provider
 * is chosen that supports the parameters passed to the {@code deriveKey} or
 * {@code deriveData} method. However, if {@code getProviderName} or
 * {@code getParameters} is called before calling the {@code deriveKey} or
 * {@code deriveData} methods, the first provider supporting the KDF algorithm
 * and {@code KDFParameters} is chosen which may not be the provider that is
 * eventually selected once the {@code AlgorithmParameterSpec} is supplied in
 * the derive methods; therefore it is recommended not to call
 * {@code getProviderName} or {@code getKDFParameters} until after a key
 * derivation operation. Once a provider is selected, it cannot be changed.
 * <p>
 * API Usage Example:
 * {@snippet lang = java:
 *    KDF kdfHkdf = KDF.getInstance("HKDFWithHmacSHA256");
 *
 *    AlgorithmParameterSpec derivationParameterSpec =
 *             HKDFParameterSpec.ofExtract()
 *                              .addIKM(ikm)
 *                              .addSalt(salt).thenExpand(info, 32);
 *
 *    kdfHkdf.deriveKey("AES", derivationParameterSpec);
 *}
 *
 * @see KDFParameters
 * @see SecretKey
 * @since 24
 */
@PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
public final class KDF {
    private static final Debug debug = Debug.getInstance("jca", "KDF");

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
    private final KDFParameters kdfParameters;

    // the first KDFSpi and provider supporting the specified configuration parameters
    private KDFSpi firstSpi;
    private Provider firstProv;

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    private final Object lock;

    /**
     * Instantiates a {@code KDF} object. This constructor is called when a
     * provider is supplied to {@code getInstance}.
     *
     * @param keyDerivSpi
     *     the delegate
     * @param provider
     *     the provider
     * @param algorithm
     *     the algorithm
     */
    private KDF(KDFSpi keyDerivSpi, Provider provider, String algorithm) {
        this.spi = keyDerivSpi;
        this.provider = provider;
        this.algorithm = algorithm;
        // note that the parameters are being passed to the impl in getInstance
        this.kdfParameters = null;
        serviceIterator = null;
        lock = new Object();
    }

    /**
     * Instantiates a {@code KDF} object. This constructor is called when a
     * provider is not supplied to {@code getInstance}.
     *
     * @param firstSpi the first matching Spi
     * @param firstProv the first matching provider
     * @param t the service iterator
     * @param algorithm the algorithm
     * @param kdfParameters the algorithm parameters
     */
    private KDF(KDFSpi firstSpi, Provider firstProv, Iterator<Service> t, String algorithm,
                KDFParameters kdfParameters) {
        this.firstSpi = firstSpi;
        this.firstProv = firstProv;
        serviceIterator = t;
        this.algorithm = algorithm;
        this.kdfParameters = kdfParameters;
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
        useFirstSpi();
        return provider.getName();
    }

    /**
     * Returns the {@code KDFParameters} used with this {@code KDF} object.
     * <p>
     * The returned parameters may be the same that were used to initialize
     * this {@code KDF} object, or may contain additional default or
     * random parameter values used by the underlying KDF algorithm.
     * If the required parameters were not supplied and can be generated by
     * the {@code KDF} object, the generated parameters are returned;
     * otherwise {@code null} is returned.
     *
     * @return the parameters used with this {@code KDF} object, or
     * {@code null}
     */
    public KDFParameters getParameters() {
        useFirstSpi();
        return spi.engineGetParameters();
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
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
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        try {
            return getInstance(algorithm, (KDFParameters) null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm from
     * the specified security provider.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if the specified provider does not support the specified KDF
     *     algorithm
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws NullPointerException
     *     if the {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm, String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException {
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        Objects.requireNonNull(provider, "provider should not be null");
        try {
            return getInstance(algorithm, null, provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm from
     * the specified security provider.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if the specified provider does not support the specified KDF
     *     algorithm
     * @throws NullPointerException
     *     if the {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm, Provider provider)
        throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        Objects.requireNonNull(provider, "provider should not be null");
        try {
            return getInstance(algorithm, null, provider);
        } catch (InvalidAlgorithmParameterException e) {
            throw new NoSuchAlgorithmException(
                "No implementation found using null KDFParameters", e);
        }
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm and
     * is initialized with the specified parameters.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
     * @param kdfParameters
     *     the {@code KDFParameters} used to configure the derivation
     *     algorithm or {@code null} if no parameters are provided
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if no {@code Provider} supports a {@code KDFSpi} implementation for
     *     the specified algorithm
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this
     *     {@code KDF} or if no provider can be found which supports the
     *     parameters
     * @throws NullPointerException
     *     if the {@code algorithm} is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        // make sure there is at least one service from a signed provider
        Iterator<Service> t = GetInstance.getServices("KDF", algorithm);
        while (t.hasNext()) {
            Service s = t.next();
            if (!JceSecurity.canUseProvider(s.getProvider())) {
                continue;
            }
            try {
                Object obj = s.newInstance(kdfParameters);
                if (!(obj instanceof KDFSpi spiObj)) {
                    continue;
                }
                return new KDF(spiObj, s.getProvider(), t, algorithm, kdfParameters);
            } catch (NoSuchAlgorithmException e) {
                // ignore
                continue;
            }
        }
        throw new NoSuchAlgorithmException(
            "Algorithm " + algorithm + " not available");
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm from
     * the specified provider and is initialized with the specified parameters.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
     * @param kdfParameters
     *     the {@code KDFParameters} used to configure the derivation
     *     algorithm or {@code null} if no parameters are provided
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if the specified provider does not support the specified KDF
     *     algorithm
     * @throws NoSuchProviderException
     *     if the specified provider is not registered in the security provider
     *     list
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this
     *     {@code KDF} or if the specified provider does not support the
     *     parameters
     * @throws NullPointerException
     *     if the {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters,
                                  String provider)
        throws NoSuchAlgorithmException, NoSuchProviderException,
               InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        Objects.requireNonNull(provider, "provider should not be null");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        kdfParameters,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new NoSuchProviderException(msg);
            }
            return new KDF((KDFSpi) instance.impl, instance.provider, algorithm);

        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    /**
     * Returns a {@code KDF} instance initialized with the specified algorithm from
     * the specified provider and is initialized with the specified parameters.
     *
     * @param algorithm
     *     the key derivation algorithm to use.
     *     See the {@code KDF} section in the <a href=
     *     "{@docRoot}/../specs/security/standard-names.html#kdf-algorithms">
     *     Java Security Standard Algorithm Names Specification</a>
     *     for information about standard KDF algorithm names.
     * @param kdfParameters
     *     the {@code KDFParameters} used to configure the derivation
     *     algorithm or {@code null} if no parameters are provided
     * @param provider
     *     the provider to use for this key derivation
     *
     * @return a {@code KDF} object
     *
     * @throws NoSuchAlgorithmException
     *     if the specified provider does not support the specified KDF
     *     algorithm
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this
     *     {@code KDF} or if the specified provider does not support the
     *     parameters
     * @throws NullPointerException
     *     if the {@code algorithm} or {@code provider} is {@code null}
     */
    public static KDF getInstance(String algorithm,
                                  KDFParameters kdfParameters,
                                  Provider provider)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Objects.requireNonNull(algorithm, "algorithm should not be null");
        Objects.requireNonNull(provider, "provider should not be null");
        try {
            Instance instance = GetInstance.getInstance("KDF", KDFSpi.class,
                                                        algorithm,
                                                        kdfParameters,
                                                        provider);
            if (!JceSecurity.canUseProvider(instance.provider)) {
                String msg = "JCE cannot authenticate the provider "
                             + instance.provider.getName();
                throw new SecurityException(msg);
            }
            return new KDF((KDFSpi) instance.impl, instance.provider, algorithm);

        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    private static KDF handleException(NoSuchAlgorithmException e)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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
     * {@code KDF} instance, but it is not considered thread-safe.
     *
     * @param alg
     *     the algorithm of the resultant {@code SecretKey} object
     * @param derivationParameterSpec
     *     the object describing the inputs to the derivation function
     *
     * @return a {@code SecretKey} object corresponding to a key built from the
     *     KDF output and according to the derivation parameters.
     *
     * @throws InvalidAlgorithmParameterException
     *     if the information contained within the {@code derivationParameterSpec} is
     *     invalid or if the combination of {@code alg} and the {@code derivationParameterSpec}
     *     results in something invalid, ie - a key of inappropriate length
     *     for the specified algorithm
     * @throws NoSuchAlgorithmException
     *     if {@code alg} is empty or invalid
     * @throws NullPointerException
     *     if {@code alg} or {@code derivationParameterSpec} is null
     */
    public SecretKey deriveKey(String alg,
                               AlgorithmParameterSpec derivationParameterSpec)
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        if (alg == null) {
            throw new NullPointerException(
                "the algorithm for the SecretKey return value should not be null");
        }
        if (alg.isEmpty()) {
            throw new NoSuchAlgorithmException(
                "the algorithm for the SecretKey return value should not be "
                + "empty");
        }
        Objects.requireNonNull(derivationParameterSpec);
        if (spi != null) {
            return spi.engineDeriveKey(alg, derivationParameterSpec);
        } else {
            return (SecretKey) chooseProvider(alg, derivationParameterSpec);
        }
    }

    /**
     * Obtains raw data from a key derivation function.
     * <p>
     * The {@code deriveData} method may be called multiple times on a
     * particular {@code KDF} instance, but it is not considered thread-safe.
     *
     * @param derivationParameterSpec
     *     the object describing the inputs to the derivation function
     *
     * @return a byte array corresponding to the KDF output and according to
     * the derivation parameters.
     *
     * @throws InvalidAlgorithmParameterException
     *     if the information contained within the {@code derivationParameterSpec} is
     *     invalid
     * @throws UnsupportedOperationException
     *     if the derived key material is not extractable
     * @throws NullPointerException
     *     if {@code derivationParameterSpec} is null
     */
    public byte[] deriveData(AlgorithmParameterSpec derivationParameterSpec)
        throws InvalidAlgorithmParameterException {

        Objects.requireNonNull(derivationParameterSpec);
        if (spi != null) {
            return spi.engineDeriveData(derivationParameterSpec);
        } else {
            try {
                return (byte[]) chooseProvider(null, derivationParameterSpec);
            } catch (NoSuchAlgorithmException e) {
                // this will never be thrown in the deriveData case
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Use the firstSpi as the chosen KDFSpi and set the fields accordingly
     */
    private void useFirstSpi() {
        if ((spi != null) || (serviceIterator == null)) return;

        synchronized (lock) {
            if ((spi == null) && (serviceIterator != null)) {
                spi = firstSpi;
                provider = firstProv;
                // not needed any more
                firstSpi = null;
                firstProv = null;
                serviceIterator = null;
            }
        }
    }

    /**
     * Selects the provider which supports the passed {@code algorithm} and
     * {@code derivationParameterSpec} values, and assigns the global spi and
     * provider variables if they have not been assigned yet.
     * <p>
     * If the spi has already been set, it will just return the result.
     */
    private Object chooseProvider(String algorithm,
                                  AlgorithmParameterSpec derivationParameterSpec)
        throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {

        boolean isDeriveData = (algorithm == null);

        synchronized (lock) {
            if (spi != null) {
                return (isDeriveData) ? spi.engineDeriveData(
                    derivationParameterSpec) : spi.engineDeriveKey(algorithm,
                                                                   derivationParameterSpec);
            }

            Exception lastException = null;
            while ((firstSpi != null) || serviceIterator.hasNext()) {
                KDFSpi currSpi;
                Provider currProv;
                if (firstSpi != null) {
                    currSpi = firstSpi;
                    currProv = firstProv;
                    firstSpi = null;
                    firstProv = null;
                } else {
                    Service s = serviceIterator.next();
                    currProv = s.getProvider();
                    if (!JceSecurity.canUseProvider(currProv)) {
                        continue;
                    }
                    try {
                        Object obj = s.newInstance(kdfParameters);
                        if (!(obj instanceof KDFSpi)) {
                            continue;
                        }
                        currSpi = (KDFSpi) obj;
                    } catch (Exception e) {
                        // continue to the next provider
                        continue;
                    }
                }

                try {
                    Object result = (isDeriveData) ? currSpi.engineDeriveData(
                        derivationParameterSpec) : currSpi.engineDeriveKey(
                        algorithm, derivationParameterSpec);
                    // found a working KDFSpi
                    this.provider = currProv;
                    this.spi = currSpi;
                    // not looking further
                    serviceIterator = null;
                    return result;
                } catch (Exception e) {
                    if (lastException == null) {
                        lastException = e;
                    }
                }
            }
            // no working provider found, fail
            if (lastException instanceof InvalidAlgorithmParameterException) {
                throw (InvalidAlgorithmParameterException) lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
            }
        }
        throw new InvalidAlgorithmParameterException(
            "No installed provider supports the " +
            ((isDeriveData) ? "deriveData" : "deriveKey")
            + " method with these parameters");
    }
}