/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.jca.GetInstance;

import java.security.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class provides the functionality of a Key Encapsulation Mechanism (KEM).
 * A KEM can be used to secure symmetric keys using asymmetric or public key
 * cryptography between two parties. The sender calls the encapsulate method
 * to generate a secret key and a key encapsulation message, and the receiver
 * calls the decapsulate method to recover the same secret key from
 * the key encapsulation message.
 * <p>
 * The {@code getInstance} method creates a new {@code KEM} object that
 * implements the specified algorithm.
 * <p>
 * A {@code KEM} object is immutable. It is safe to call multiple
 * {@code newEncapsulator} and {@code newDecapsulator} methods on the
 * same {@code KEM} object at the same time.
 * <p>
 * If a provider is not specified in the {@code getInstance} method when
 * instantiating a {@code KEM} object, the {@code newEncapsulator} and
 * {@code newDecapsulator} methods may return encapsulators or decapsulators
 * from different providers. The provider selected is based on the parameters
 * passed to the {@code newEncapsulator} or {@code newDecapsulator} methods:
 * the private or public key and the optional {@code AlgorithmParameterSpec}.
 * The {@link Encapsulator#providerName} and {@link Decapsulator#providerName}
 * methods return the name of the selected provider.
 * <p>
 * {@code Encapsulator} and {@code Decapsulator} objects are also immutable.
 * It is safe to invoke multiple {@code encapsulate} and {@code decapsulate}
 * methods on the same {@code Encapsulator} or {@code Decapsulator} object
 * at the same time. Each invocation of {@code encapsulate} will generate a
 * new shared secret and key encapsulation message.
 * <p>
 *
 * Example:
 * {@snippet lang = java:
 *    // Receiver side
 *    var kpg = KeyPairGenerator.getInstance("X25519");
 *    var kp = kpg.generateKeyPair();
 *
 *    // Sender side
 *    var kem1 = KEM.getInstance("DHKEM");
 *    var sender = kem1.newEncapsulator(kp.getPublic());
 *    var encapsulated = sender.encapsulate();
 *    var k1 = encapsulated.key();
 *
 *    // Receiver side
 *    var kem2 = KEM.getInstance("DHKEM");
 *    var receiver = kem2.newDecapsulator(kp.getPrivate());
 *    var k2 = receiver.decapsulate(encapsulated.encapsulation());
 *
 *    assert Arrays.equals(k1.getEncoded(), k2.getEncoded());
 * }
 *
 * @since 21
 */
public final class KEM {

    /**
     * This class specifies the return value of the encapsulate method of
     * a Key Encapsulation Mechanism (KEM), which includes the shared secret
     * (as a {@code SecretKey}), the key encapsulation message,
     * and optional parameters.
     * <p>
     * Note: the key encapsulation message can be also referred to as ciphertext.
     *
     * @see #newEncapsulator(PublicKey, AlgorithmParameterSpec, SecureRandom)
     * @see Encapsulator#encapsulate(int, int, String)
     *
     * @since 21
     */
    public static final class Encapsulated {
        private final SecretKey key;
        private final byte[] encapsulation;
        private final byte[] params;

        /**
         * Constructs an {@code Encapsulated} object.
         *
         * @param key the shared secret as a key, must not be {@code null}.
         * @param encapsulation the key encapsulation message, must not
         *          be {@code null}. The contents of the array are copied
         *          to protect against subsequent modification.
         * @param params optional parameters, can be {@code null}.
         *          The contents of the array are copied to protect
         *          against subsequent modification.
         * @throws NullPointerException if {@code key} or {@code encapsulation}
         *          is {@code null}
         */
        public Encapsulated(SecretKey key, byte[] encapsulation, byte[] params) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(encapsulation);
            this.key = key;
            this.encapsulation = encapsulation.clone();
            this.params = params == null ? null : params.clone();
        }

        /**
         * Returns the {@code SecretKey}.
         *
         * @return the secret key
         */
        public SecretKey key() {
            return key;
        }

        /**
         * Returns the key encapsulation message.
         *
         * @return the key encapsulation message. A new copy of the byte array
         *      is returned.
         */
        public byte[] encapsulation() {
            return encapsulation.clone();
        }

        /**
         * Returns the optional parameters in a byte array.
         *
         * @return the optional parameters in a byte array or {@code null}
         *      if not specified. A new copy of the byte array is returned.
         */
        public byte[] params() {
            return params == null ? null : params.clone();
        }
    }

    /**
     * An encapsulator, generated by {@link #newEncapsulator} on the KEM
     * sender side.
     * <p>
     * This class represents the key encapsulation function of a KEM.
     * Each invocation of the {@code encapsulate} method generates a
     * new secret key and key encapsulation message that is returned
     * in an {@link Encapsulated} object.
     *
     * @since 21
     */
    public static final class Encapsulator {

        private final KEMSpi.EncapsulatorSpi e;
        private final Provider p;

        private Encapsulator(KEMSpi.EncapsulatorSpi e, Provider p) {
            assert e != null;
            assert p != null;
            this.e = e;
            this.p = p;
        }

        /**
         * Returns the name of the provider.
         *
         * @return the name of the provider
         */
        public String providerName() {
            return p.getName();
        }

        /**
         * The key encapsulation function.
         * <p>
         * This method is equivalent to
         * {@code encapsulate(0, secretSize(), "Generic")}. This combination
         * of arguments must be supported by every implementation.
         * <p>
         * The generated secret key is usually passed to a key derivation
         * function (KDF) as the input keying material.
         *
         * @return a {@link Encapsulated} object containing the shared
         *          secret, key encapsulation message, and optional parameters.
         *          The shared secret is a {@code SecretKey} containing all of
         *          the bytes of the secret, and an algorithm name of "Generic".
         */
        public Encapsulated encapsulate() {
            return encapsulate(0, secretSize(), "Generic");
        }

        /**
         * The key encapsulation function.
         * <p>
         * Each invocation of this method generates a new secret key and key
         * encapsulation message that is returned in an {@link Encapsulated} object.
         * <p>
         * An implementation may choose to not support arbitrary combinations
         * of {@code from}, {@code to}, and {@code algorithm}.
         *
         * @param from the initial index of the shared secret byte array
         *          to be returned, inclusive
         * @param to the final index of the shared secret byte array
         *          to be returned, exclusive
         * @param algorithm the algorithm name for the secret key that is returned
         * @return a {@link Encapsulated} object containing a portion of
         *          the shared secret, key encapsulation message, and optional
         *          parameters. The portion of the shared secret is a
         *          {@code SecretKey} containing the bytes of the secret
         *          ranging from {@code from} to {@code to}, exclusive,
         *          and an algorithm name as specified. For example,
         *          {@code encapsulate(0, 16, "AES")} uses the first 16 bytes
         *          of the shared secret as a 128-bit AES key.
         * @throws IndexOutOfBoundsException if {@code from < 0},
         *     {@code from > to}, or {@code to > secretSize()}
         * @throws NullPointerException if {@code algorithm} is {@code null}
         * @throws UnsupportedOperationException if the combination of
         *          {@code from}, {@code to}, and {@code algorithm}
         *          is not supported by the encapsulator
         */
        public Encapsulated encapsulate(int from, int to, String algorithm) {
            return e.engineEncapsulate(from, to, algorithm);
        }

        /**
         * Returns the size of the shared secret.
         * <p>
         * This method can be called to find out the length of the shared secret
         * before {@code encapsulate} is called or if the obtained
         * {@code SecretKey} is not extractable.
         *
         * @return the size of the shared secret
         */
        public int secretSize() {
            int result = e.engineSecretSize();
            assert result >= 0 && result != Integer.MAX_VALUE
                    : "invalid engineSecretSize result";
            return result;
        }

        /**
         * Returns the size of the key encapsulation message.
         * <p>
         * This method can be called to find out the length of the encapsulation
         * message before {@code encapsulate} is called.
         *
         * @return the size of the key encapsulation message
         */
        public int encapsulationSize() {
            int result = e.engineEncapsulationSize();
            assert result >= 0 && result != Integer.MAX_VALUE
                    : "invalid engineEncapsulationSize result";
            return result;
        }
    }

    /**
     * A decapsulator, generated by {@link #newDecapsulator} on the KEM
     * receiver side.
     * <p>
     * This class represents the key decapsulation function of a KEM.
     * An invocation of the {@code decapsulate} method recovers the
     * secret key from the key encapsulation message.
     *
     * @since 21
     */
    public static final class Decapsulator {
        private final KEMSpi.DecapsulatorSpi d;
        private final Provider p;

        private Decapsulator(KEMSpi.DecapsulatorSpi d, Provider p) {
            assert d != null;
            assert p != null;
            this.d = d;
            this.p = p;
        }

        /**
         * Returns the name of the provider.
         *
         * @return the name of the provider
         */
        public String providerName() {
            return p.getName();
        }

        /**
         * The key decapsulation function.
         * <p>
         * This method is equivalent to
         * {@code decapsulate(encapsulation, 0, secretSize(), "Generic")}. This
         * combination of arguments must be supported by every implementation.
         * <p>
         * The generated secret key is usually passed to a key derivation
         * function (KDF) as the input keying material.
         *
         * @param encapsulation the key encapsulation message from the sender.
         *          The size must be equal to the value returned by
         *          {@link #encapsulationSize()}, or a {@code DecapsulateException}
         *          will be thrown.
         * @return the shared secret as a {@code SecretKey} with
         *          an algorithm name of "Generic"
         * @throws DecapsulateException if an error occurs during the
         *          decapsulation process
         * @throws NullPointerException if {@code encapsulation} is {@code null}
         */
        public SecretKey decapsulate(byte[] encapsulation) throws DecapsulateException {
            return decapsulate(encapsulation, 0, secretSize(), "Generic");
        }

        /**
         * The key decapsulation function.
         * <p>
         * An invocation of this method recovers the secret key from the key
         * encapsulation message.
         * <p>
         * An implementation may choose to not support arbitrary combinations
         * of {@code from}, {@code to}, and {@code algorithm}.
         *
         * @param encapsulation the key encapsulation message from the sender.
         *          The size must be equal to the value returned by
         *          {@link #encapsulationSize()}, or a {@code DecapsulateException}
         *          will be thrown.
         * @param from the initial index of the shared secret byte array
         *          to be returned, inclusive
         * @param to the final index of the shared secret byte array
         *          to be returned, exclusive
         * @param algorithm the algorithm name for the secret key that is returned
         * @return a portion of the shared secret as a {@code SecretKey}
         *          containing the bytes of the secret ranging from {@code from}
         *          to {@code to}, exclusive, and an algorithm name as specified.
         *          For example, {@code decapsulate(encapsulation, secretSize()
         *          - 16, secretSize(), "AES")} uses the last 16 bytes
         *          of the shared secret as a 128-bit AES key.
         * @throws DecapsulateException if an error occurs during the
         *          decapsulation process
         * @throws IndexOutOfBoundsException if {@code from < 0},
         *          {@code from > to}, or {@code to > secretSize()}
         * @throws NullPointerException if {@code encapsulation} or
         *          {@code algorithm} is {@code null}
         * @throws UnsupportedOperationException if the combination of
         *          {@code from}, {@code to}, and {@code algorithm}
         *          is not supported by the decapsulator
         */
        public SecretKey decapsulate(byte[] encapsulation,
                int from, int to, String algorithm)
                throws DecapsulateException {
            return d.engineDecapsulate(
                    encapsulation,
                    from, to,
                    algorithm);
        }

        /**
         * Returns the size of the shared secret.
         * <p>
         * This method can be called to find out the length of the shared secret
         * before {@code decapsulate} is called or if the obtained
         * {@code SecretKey} is not extractable.
         *
         * @return the size of the shared secret
         */
        public int secretSize() {
            int result = d.engineSecretSize();
            assert result >= 0 && result != Integer.MAX_VALUE
                    : "invalid engineSecretSize result";
            return result;
        }

        /**
         * Returns the size of the key encapsulation message.
         * <p>
         * This method can be used to extract the encapsulation message
         * from a longer byte array if no length information is provided
         * by a higher level protocol.
         *
         * @return the size of the key encapsulation message
         */
        public int encapsulationSize() {
            int result = d.engineEncapsulationSize();
            assert result >= 0 && result != Integer.MAX_VALUE
                    : "invalid engineEncapsulationSize result";
            return result;
        }
    }

    private static final class DelayedKEM {

        private final Provider.Service[] list; // non empty array

        private DelayedKEM(Provider.Service[] list) {
            this.list = list;
        }

        private Encapsulator newEncapsulator(PublicKey publicKey,
                AlgorithmParameterSpec spec, SecureRandom secureRandom)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (publicKey == null) {
                throw new InvalidKeyException("input key is null");
            }
            RuntimeException re = null;
            InvalidAlgorithmParameterException iape = null;
            InvalidKeyException ike = null;
            NoSuchAlgorithmException nsae = null;
            for (Provider.Service service : list) {
                if (!service.supportsParameter(publicKey)) {
                    continue;
                }
                try {
                    KEMSpi spi = (KEMSpi) service.newInstance(null);
                    return new Encapsulator(
                            spi.engineNewEncapsulator(publicKey, spec, secureRandom),
                            service.getProvider());
                } catch (NoSuchAlgorithmException e) {
                    nsae = merge(nsae, e);
                } catch (InvalidAlgorithmParameterException e) {
                    iape = merge(iape, e);
                } catch (InvalidKeyException e) {
                    ike = merge(ike, e);
                } catch (RuntimeException e) {
                    re = merge(re, e);
                }
            }
            if (iape != null) throw iape;
            if (ike != null) throw ike;
            if (nsae != null) {
                throw new InvalidKeyException("No installed provider found", nsae);
            }
            throw new InvalidKeyException("No installed provider supports this key: "
                            + publicKey.getClass().getName(), re);
        }

        private static <T extends Exception> T merge(T e1, T e2) {
            if (e1 == null) {
                return e2;
            } else {
                e1.addSuppressed(e2);
                return e1;
            }
        }

        private Decapsulator newDecapsulator(PrivateKey privateKey, AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (privateKey == null) {
                throw new InvalidKeyException("input key is null");
            }
            RuntimeException re = null;
            InvalidAlgorithmParameterException iape = null;
            InvalidKeyException ike = null;
            NoSuchAlgorithmException nsae = null;
            for (Provider.Service service : list) {
                if (!service.supportsParameter(privateKey)) {
                    continue;
                }
                try {
                    KEMSpi spi = (KEMSpi) service.newInstance(null);
                    return new Decapsulator(
                            spi.engineNewDecapsulator(privateKey, spec),
                            service.getProvider());
                } catch (NoSuchAlgorithmException e) {
                    nsae = merge(nsae, e);
                } catch (InvalidAlgorithmParameterException e) {
                    iape = merge(iape, e);
                } catch (InvalidKeyException e) {
                    ike = merge(ike, e);
                } catch (RuntimeException e) {
                    re = merge(re, e);
                }
            }
            if (iape != null) throw iape;
            if (ike != null) throw ike;
            if (nsae != null) {
                throw new InvalidKeyException("No installed provider found", nsae);
            }
            throw new InvalidKeyException("No installed provider supports this key: "
                    + privateKey.getClass().getName(), re);
        }
    }

    // If delayed provider selection is needed
    private final DelayedKEM delayed;

    // otherwise
    private final KEMSpi spi;
    private final Provider provider;

    private final String algorithm;

    private KEM(String algorithm, KEMSpi spi, Provider provider) {
        assert spi != null;
        assert provider != null;
        this.delayed = null;
        this.spi = spi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    private KEM(String algorithm, DelayedKEM delayed) {
        assert delayed != null;
        this.delayed = delayed;
        this.spi = null;
        this.provider = null;
        this.algorithm = algorithm;
    }

    /**
     * Returns a {@code KEM} object that implements the specified algorithm.
     *
     * @param algorithm the name of the KEM algorithm.
     *          See the {@code KEM} section in the <a href=
     *          "{@docRoot}/../specs/security/standard-names.html#kem-algorithms">
     *          Java Security Standard Algorithm Names Specification</a>
     *          for information about standard KEM algorithm names.
     * @return the new {@code KEM} object
     * @throws NoSuchAlgorithmException if no {@code Provider} supports a
     *         {@code KEM} implementation for the specified algorithm
     * @throws NullPointerException if {@code algorithm} is {@code null}
     */
    public static KEM getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        List<Provider.Service> list = GetInstance.getServices(
                "KEM",
                Objects.requireNonNull(algorithm, "null algorithm name"));
        List<Provider.Service> allowed = new ArrayList<>();
        for (Provider.Service s : list) {
            if (!JceSecurity.canUseProvider(s.getProvider())) {
                continue;
            }
            allowed.add(s);
        }
        if (allowed.isEmpty()) {
            throw new NoSuchAlgorithmException
                    (algorithm + " KEM not available");
        }

        return new KEM(algorithm, new DelayedKEM(allowed.toArray(new Provider.Service[0])));
    }

    /**
     * Returns a {@code KEM} object that implements the specified algorithm
     * from the specified security provider.
     *
     * @param algorithm the name of the KEM algorithm.
     *          See the {@code KEM} section in the <a href=
     *          "{@docRoot}/../specs/security/standard-names.html#kem-algorithms">
     *          Java Security Standard Algorithm Names Specification</a>
     *          for information about standard KEM algorithm names.
     * @param provider the provider. If {@code null}, this method is equivalent
     *                 to {@link #getInstance(String)}.
     * @return the new {@code KEM} object
     * @throws NoSuchAlgorithmException if a {@code provider} is specified and
     *          it does not support the specified KEM algorithm,
     *          or if {@code provider} is {@code null} and there is no provider
     *          that supports a KEM implementation of the specified algorithm
     * @throws NullPointerException if {@code algorithm} is {@code null}
     */
    public static KEM getInstance(String algorithm, Provider provider)
            throws NoSuchAlgorithmException {
        if (provider == null) {
            return getInstance(algorithm);
        }
        GetInstance.Instance instance = JceSecurity.getInstance(
                "KEM",
                KEMSpi.class,
                Objects.requireNonNull(algorithm, "null algorithm name"),
                provider);
        return new KEM(algorithm, (KEMSpi) instance.impl, instance.provider);
    }

    /**
     * Returns a {@code KEM} object that implements the specified algorithm
     * from the specified security provider.
     *
     * @param algorithm the name of the KEM algorithm.
     *          See the {@code KEM} section in the <a href=
     *          "{@docRoot}/../specs/security/standard-names.html#kem-algorithms">
     *          Java Security Standard Algorithm Names Specification</a>
     *          for information about standard KEM algorithm names.
     * @param provider the provider. If {@code null}, this method is equivalent
     *                 to {@link #getInstance(String)}.
     * @return the new {@code KEM} object
     * @throws NoSuchAlgorithmException if a {@code provider} is specified and
     *          it does not support the specified KEM algorithm,
     *          or if {@code provider} is {@code null} and there is no provider
     *          that supports a KEM implementation of the specified algorithm
     * @throws NoSuchProviderException if the specified provider is not
     *         registered in the security provider list
     * @throws NullPointerException if {@code algorithm} is {@code null}
     */
    public static KEM getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (provider == null) {
            return getInstance(algorithm);
        }
        GetInstance.Instance instance = JceSecurity.getInstance(
                "KEM",
                KEMSpi.class,
                Objects.requireNonNull(algorithm, "null algorithm name"),
                provider);
        return new KEM(algorithm, (KEMSpi) instance.impl, instance.provider);
    }

    /**
     * Creates a KEM encapsulator on the KEM sender side.
     * <p>
     * This method is equivalent to {@code newEncapsulator(publicKey, null, null)}.
     *
     * @param publicKey the receiver's public key, must not be {@code null}
     * @return the encapsulator for this key
     * @throws InvalidKeyException if {@code publicKey} is {@code null} or invalid
     * @throws UnsupportedOperationException if this method is not supported
     *          because an {@code AlgorithmParameterSpec} must be provided
     */
    public Encapsulator newEncapsulator(PublicKey publicKey)
            throws InvalidKeyException {
        try {
            return newEncapsulator(publicKey, null, null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new UnsupportedOperationException(
                    "AlgorithmParameterSpec must be provided", e);
        }
    }

    /**
     * Creates a KEM encapsulator on the KEM sender side.
     * <p>
     * This method is equivalent to {@code newEncapsulator(publicKey, null, secureRandom)}.
     *
     * @param publicKey the receiver's public key, must not be {@code null}
     * @param secureRandom the source of randomness for encapsulation.
     *                     If {@code} null, a default one from the
     *                     implementation will be used.
     * @return the encapsulator for this key
     * @throws InvalidKeyException if {@code publicKey} is {@code null} or invalid
     * @throws UnsupportedOperationException if this method is not supported
     *          because an {@code AlgorithmParameterSpec} must be provided
     */
    public Encapsulator newEncapsulator(PublicKey publicKey, SecureRandom secureRandom)
            throws InvalidKeyException {
        try {
            return newEncapsulator(publicKey, null, secureRandom);
        } catch (InvalidAlgorithmParameterException e) {
            throw new UnsupportedOperationException(
                    "AlgorithmParameterSpec must be provided", e);
        }
    }

    /**
     * Creates a KEM encapsulator on the KEM sender side.
     * <p>
     * An algorithm can define an {@code AlgorithmParameterSpec} child class to
     * provide extra information in this method. This is especially useful if
     * the same key can be used to derive shared secrets in different ways.
     * If any extra information inside this object needs to be transmitted along
     * with the key encapsulation message so that the receiver is able to create
     * a matching decapsulator, it will be included as a byte array in the
     * {@link Encapsulated#params} field inside the encapsulation output.
     * In this case, the security provider should provide an
     * {@code AlgorithmParameters} implementation using the same algorithm name
     * as the KEM. The receiver can initiate such an {@code AlgorithmParameters}
     * instance with the {@code params} byte array received and recover
     * an {@code AlgorithmParameterSpec} object to be used in its
     * {@link #newDecapsulator(PrivateKey, AlgorithmParameterSpec)} call.
     *
     * @param publicKey the receiver's public key, must not be {@code null}
     * @param spec the optional parameter, can be {@code null}
     * @param secureRandom the source of randomness for encapsulation.
     *                     If {@code} null, a default one from the
     *                     implementation will be used.
     * @return the encapsulator for this key
     * @throws InvalidAlgorithmParameterException if {@code spec} is invalid
     *          or one is required but {@code spec} is {@code null}
     * @throws InvalidKeyException if {@code publicKey} is {@code null} or invalid
     */
    public Encapsulator newEncapsulator(PublicKey publicKey,
            AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        return delayed != null
                ? delayed.newEncapsulator(publicKey, spec, secureRandom)
                : new Encapsulator(spi.engineNewEncapsulator(publicKey, spec, secureRandom), provider);
    }

    /**
     * Creates a KEM decapsulator on the KEM receiver side.
     * <p>
     * This method is equivalent to {@code newDecapsulator(privateKey, null)}.
     *
     * @param privateKey the receiver's private key, must not be {@code null}
     * @return the decapsulator for this key
     * @throws InvalidKeyException if {@code privateKey} is {@code null} or invalid
     * @throws UnsupportedOperationException if this method is not supported
     *          because an {@code AlgorithmParameterSpec} must be provided
     */
    public Decapsulator newDecapsulator(PrivateKey privateKey)
            throws InvalidKeyException {
        try {
            return newDecapsulator(privateKey, null);
        } catch (InvalidAlgorithmParameterException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Creates a KEM decapsulator on the KEM receiver side.
     *
     * @param privateKey the receiver's private key, must not be {@code null}
     * @param spec the parameter, can be {@code null}
     * @return the decapsulator for this key
     * @throws InvalidAlgorithmParameterException if {@code spec} is invalid
     *          or one is required but {@code spec} is {@code null}
     * @throws InvalidKeyException if {@code privateKey} is {@code null} or invalid
     */
    public Decapsulator newDecapsulator(PrivateKey privateKey, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        return delayed != null
                ? delayed.newDecapsulator(privateKey, spec)
                : new Decapsulator(spi.engineNewDecapsulator(privateKey, spec), provider);
    }

    /**
     * Returns the name of the algorithm for this {@code KEM} object.
     *
     * @return the name of the algorithm for this {@code KEM} object.
     */
    public String getAlgorithm() {
        return this.algorithm;
    }
}
