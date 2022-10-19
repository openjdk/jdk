/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.security.spec.DerivedKeyParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.Provider.Service;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import sun.security.util.Debug;
import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * This class provides the functionality of a key derivation algorithm for the
 * Java Cryptographic Extension (JCE) framework.
 *
 * More comments/Usage TBD
 */

public class KeyDerivation {
    private static final Debug debug =
                        Debug.getInstance("jca", "KeyDerivation");

    private static final Debug pdebug =
                        Debug.getInstance("provider", "Provider");
    private static final boolean skipDebug =
        Debug.isOn("engine=") && !Debug.isOn("keyderive");

    // The provider
    private Provider provider;

    // The provider implementation (delegate)
    private KeyDerivationSpi spi;

    // The name of the MAC algorithm.
    private final String algorithm;

    // Has this object been initialized?
    private boolean initialized = false;

    // next service to try in provider selection
    // null once provider is selected
    private Service firstService;

    // remaining services to try in provider selection
    // null once provider is selected
    private Iterator<Service> serviceIterator;

    private final Object lock;

    /**
     * Instantiates a KeyDerivation object.
     *
     * @param keyDerivSpi the delegate
     * @param provider the provider
     * @param algorithm the algorithm
     */
    protected KeyDerivation(KeyDerivationSpi keyDerivSpi,
                     Provider provider,
                     String algorithm) {
        this.spi = keyDerivSpi;
        this.provider = provider;
        this.algorithm = algorithm;
        this.lock = null;
    }

    private KeyDerivation(Service s, Iterator<Service> t, String algorithm) {
        firstService = s;
        serviceIterator = t;
        this.algorithm = algorithm;
        lock = new Object();
    }

    /**
     * Returns the algorithm name of this {@code KeyDerivation} object.
     *
     * <p>This is the same name that was specified in one of the
     * {@code getInstance} calls that created this
     * {@code KeyDerivation} object.
     *
     * @return the algorithm name of this {@code KeyDerivation} object.
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Returns the provider of this {@code KeyDerivation} object.
     *
     * @return the provider of this {@code KeyDerivation} object.
     */
    public final Provider getProvider() {
        chooseFirstProvider();
        return this.provider;
    }

    private String getProviderName() {
        return (provider == null) ? "(no provider)" : provider.getName();
    }

    /**
     * Creates an instance of the {@code KeyDerivation} object.
     *
     * @param algorithm the key derivation algorithm to use
     *
     * @return a {@code KeyDerivation} object
     *
     * @throws NoSuchAlgorithmException if no {@code Provider} supports a
     *         {@code KeyDerivationSpi} implementation for the
     *         specified algorithm.
     */
    public static final KeyDerivation getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        List<Service> services =
                GetInstance.getServices("KeyDerivation", algorithm);
        Iterator<Service> t = services.iterator();
        while (t.hasNext()) {
            Service s = t.next();
            if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                continue;
            }
            return new KeyDerivation(s, t, algorithm);
        }
        throw new NoSuchAlgorithmException
                                ("Algorithm " + algorithm + " not available");
    }

    /**
     * Creates an instance of the {@code KeyDerivation} object on a specific
     * {@code Provider}.
     *
     * @param algorithm the key derivation algorithm to use
     * @param provider the provider to use for this key derivation
     *
     * @return a {@code KeyDerivation} object
     *
     * @throws NoSuchAlgorithmException if no {@code Provider} supports a
     *         {@code KeyDerivationSpi} implementation for the
     *         specified algorithm.
     * @throws NoSuchProviderException if the specified provider is not
     *         registered in the security provider list.
     */
    public static final KeyDerivation getInstance(String algorithm,
            String provider) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        Instance instance = JceSecurity.getInstance
                ("KeyDerivation", KeyDerivationSpi.class, algorithm, provider);
        return new KeyDerivation((KeyDerivationSpi)instance.impl,
                instance.provider, algorithm);
    }

    /**
     * Creates an instance of the {@code KeyDerivation} object using a
     * supplied {@code Provider} instance.
     *
     * @param algorithm the key derivation algorithm to use
     * @param provider the provider
     *
     * @return a {@code KeyDerivation} object
     *
     * @throws NoSuchAlgorithmException if no {@code Provider} supports a
     *         {@code KeyDerivationSpi} implementation for the
     *         specified algorithm.
     */
    public static final KeyDerivation getInstance(String algorithm,
            Provider provider) throws NoSuchAlgorithmException {
        Objects.requireNonNull(algorithm, "null algorithm name");
        Instance instance = JceSecurity.getInstance
                ("KeyDerivation", KeyDerivationSpi.class, algorithm, provider);
        return new KeyDerivation((KeyDerivationSpi)instance.impl,
                instance.provider, algorithm);
    }

    /**
     * Initializes the {@code KeyDerivation} object.
     *
     * @param secretKey the input keying material
     *
     * @throws InvalidKeyException if the given key is inappropriate for
     *      initializing this MAC
     */
    public void init(SecretKey secretKey) throws InvalidKeyException {
        try {
            if (spi != null) {
                spi.engineInit(secretKey, null);
            } else {
                chooseProvider(secretKey, null);
            }
        } catch (InvalidParameterSpecException e) {
            throw new InvalidKeyException("init() failed", e);
        }
        initialized = true;

        if (!skipDebug && pdebug != null) {
            pdebug.println("KeyDerivation." + algorithm + " algorithm from: " +
                getProviderName());
        }
    }

    /**
     * Initializes the {@code KeyDerivation} object.
     *
     * @param secretKey the input keying material
     * @param params key derivation algorithm parameters, if required
     *
     * @throws InvalidParameterSpecException if the wrong kind of
     *      {@code AlgorithmParameterSpec} object is provided.
     * @throws InvalidKeyException if the given key is inappropriate for
     *      initializing this MAC.
     */
    public void init(SecretKey secretKey, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidParameterSpecException {
        if (spi != null) {
            spi.engineInit(secretKey, params);
        } else {
            chooseProvider(secretKey, params);
        }
        initialized = true;

        if (!skipDebug && pdebug != null) {
            pdebug.println("KeyDerivation." + algorithm + " algorithm from: " +
                getProviderName());
        }
    }

    /**
     * Derive a key, returned as a {@code SecretKey}.
     *
     * @param params any algorithm parameters specific to the key being
     *      derived, if required
     *
     * @return a {@link SecretKey} containing the key
     *
     * @throws InvalidParameterSpecException if the information contained
     *      within the {@code DerivedKeyParameterSpec} is invalid or incorrect
     *      for the type of key to be derived.
     * @throws NullPointerException if either the {@code algorithm} or
     *      {@code params} parameters are {@code null}.
     */
    public SecretKey deriveKey(DerivedKeyParameterSpec params)
            throws InvalidParameterSpecException {
        List<SecretKey> keyList = deriveKeys(Collections.singletonList(params));
        return keyList.get(0);
    }

    /**
     * Derive one or more {@code SecretKey} objects.
     *
     * @param params a {@link List} of {@link DerivedKeyParameterSpec} objects,
     *      in the desired order the caller wishes the keys to be returned.
     *
     * @return an unmodifiable {@link List} consisting of one or more
     *      {@link SecretKey} objects, in the same order as the requested
     *      objects in the {@code params} parameter.
     *
     * @throws InvalidParameterSpecException if the information contained
     *      within any of the {@link DerivedKeyParameterSpec} objects is
     *      incorrect for the derived key type.
     * @throws NullPointerException if the {@code params} field is null
     *      or any elements within
     */
    public List<SecretKey> deriveKeys(List<DerivedKeyParameterSpec> params)
            throws InvalidParameterSpecException {

        // Make sure we have no null values across the parameter list
        Objects.requireNonNull(params,
                "received null derivation parameter list");
        if (params.contains(null)) {
            throw new NullPointerException(
                    "found null derived key parameter element in list");
        }
        return spi.engineDeriveKeys(params);
    }

    // max number of debug warnings to print from chooseFirstProvider()
    private static int warnCount = 10;

    /**
     * Choose the Spi from the first provider available. Used if
     * delayed provider selection is not possible because init()
     * is not the first method called.
     */
    void chooseFirstProvider() {
        if ((spi != null) || (serviceIterator == null)) {
            return;
        }
        synchronized (lock) {
            if (spi != null) {
                return;
            }
            if (debug != null) {
                int w = --warnCount;
                if (w >= 0) {
                    debug.println("KeyDerivation.init() not first method "
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
                if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                    continue;
                }
                try {
                    Object obj = s.newInstance(null);
                    if (obj instanceof KeyDerivationSpi == false) {
                        continue;
                    }
                    spi = (KeyDerivationSpi)obj;
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
                    ("Could not construct KeyDerivationSpi instance");
            if (lastException != null) {
                e.initCause(lastException);
            }
            throw e;
        }
    }

    private void chooseProvider(SecretKey key, AlgorithmParameterSpec params)
            throws InvalidKeyException, InvalidParameterSpecException {
        synchronized (lock) {
            if (spi != null) {
                spi.engineInit(key, params);
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
                if (JceSecurity.canUseProvider(s.getProvider()) == false) {
                    continue;
                }
                try {
                    KeyDerivationSpi kdSpi =
                            (KeyDerivationSpi)s.newInstance(null);
                    kdSpi.engineInit(key, params);
                    provider = s.getProvider();
                    this.spi = kdSpi;
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
            if (lastException instanceof InvalidParameterSpecException) {
                throw (InvalidParameterSpecException)lastException;
            }
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException)lastException;
            }
            String kName = (key != null) ? key.getClass().getName() : "(null)";
            throw new InvalidKeyException
                ("No installed provider supports this key: "
                + kName, lastException);
        }
    }
}
