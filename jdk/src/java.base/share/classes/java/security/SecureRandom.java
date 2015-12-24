/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.*;

import java.security.Provider.Service;

import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;
import sun.security.util.Debug;

/**
 * This class provides a cryptographically strong random number
 * generator (RNG).
 *
 * <p>A cryptographically strong random number
 * minimally complies with the statistical random number generator tests
 * specified in
 * <a href="http://csrc.nist.gov/publications/fips/fips140-2/fips1402.pdf">
 * <i>FIPS 140-2, Security Requirements for Cryptographic Modules</i></a>,
 * section 4.9.1.
 * Additionally, SecureRandom must produce non-deterministic output.
 * Therefore any seed material passed to a SecureRandom object must be
 * unpredictable, and all SecureRandom output sequences must be
 * cryptographically strong, as described in
 * <a href="http://tools.ietf.org/html/rfc4086">
 * <i>RFC 4086: Randomness Requirements for Security</i></a>.
 *
 * <p>A caller obtains a SecureRandom instance via the
 * no-argument constructor or one of the {@code getInstance} methods:
 *
 * <pre>
 *      SecureRandom random = new SecureRandom();
 * </pre>
 *
 * <p> Many SecureRandom implementations are in the form of a pseudo-random
 * number generator (PRNG), which means they use a deterministic algorithm
 * to produce a pseudo-random sequence from a true random seed.
 * Other implementations may produce true random numbers,
 * and yet others may use a combination of both techniques.
 *
 * <p> Typical callers of SecureRandom invoke the following methods
 * to retrieve random bytes:
 *
 * <pre>
 *      SecureRandom random = new SecureRandom();
 *      byte[] bytes = new byte[20];
 *      random.nextBytes(bytes);
 * </pre>
 *
 * <p> Callers may also invoke the {@code generateSeed} method
 * to generate a given number of seed bytes (to seed other random number
 * generators, for example):
 * <pre>
 *      byte[] seed = random.generateSeed(20);
 * </pre>
 *
 * Note: Depending on the implementation, the {@code generateSeed} and
 * {@code nextBytes} methods may block as entropy is being gathered,
 * for example, if they need to read from /dev/random on various Unix-like
 * operating systems.
 *
 * @see java.security.SecureRandomSpi
 * @see java.util.Random
 *
 * @author Benjamin Renaud
 * @author Josh Bloch
 */

public class SecureRandom extends java.util.Random {

    private static final Debug pdebug =
                        Debug.getInstance("provider", "Provider");
    private static final boolean skipDebug =
        Debug.isOn("engine=") && !Debug.isOn("securerandom");

    /**
     * The provider.
     *
     * @serial
     * @since 1.2
     */
    private Provider provider = null;

    /**
     * The provider implementation.
     *
     * @serial
     * @since 1.2
     */
    private SecureRandomSpi secureRandomSpi = null;

    /*
     * The algorithm name of null if unknown.
     *
     * @serial
     * @since 1.5
     */
    private String algorithm;

    // Seed Generator
    private static volatile SecureRandom seedGenerator;

    /**
     * Constructs a secure random number generator (RNG) implementing the
     * default random number algorithm.
     *
     * <p> This constructor traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new SecureRandom object encapsulating the
     * SecureRandomSpi implementation from the first
     * Provider that supports a SecureRandom (RNG) algorithm is returned.
     * If none of the Providers support a RNG algorithm,
     * then an implementation-specific default is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * <p> See the SecureRandom section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#SecureRandom">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard RNG algorithm names.
     *
     * <p> The returned SecureRandom object has not been seeded.  To seed the
     * returned object, call the {@code setSeed} method.
     * If {@code setSeed} is not called, the first call to
     * {@code nextBytes} will force the SecureRandom object to seed itself.
     * This self-seeding will not occur if {@code setSeed} was
     * previously called.
     */
    public SecureRandom() {
        /*
         * This call to our superclass constructor will result in a call
         * to our own {@code setSeed} method, which will return
         * immediately when it is passed zero.
         */
        super(0);
        getDefaultPRNG(false, null);
    }

    /**
     * Constructs a secure random number generator (RNG) implementing the
     * default random number algorithm.
     * The SecureRandom instance is seeded with the specified seed bytes.
     *
     * <p> This constructor traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new SecureRandom object encapsulating the
     * SecureRandomSpi implementation from the first
     * Provider that supports a SecureRandom (RNG) algorithm is returned.
     * If none of the Providers support a RNG algorithm,
     * then an implementation-specific default is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * <p> See the SecureRandom section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#SecureRandom">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard RNG algorithm names.
     *
     * @param seed the seed.
     */
    public SecureRandom(byte[] seed) {
        super(0);
        getDefaultPRNG(true, seed);
    }

    private void getDefaultPRNG(boolean setSeed, byte[] seed) {
        String prng = getPrngAlgorithm();
        if (prng == null) {
            // bummer, get the SUN implementation
            prng = "SHA1PRNG";
            this.secureRandomSpi = new sun.security.provider.SecureRandom();
            this.provider = Providers.getSunProvider();
            if (setSeed) {
                this.secureRandomSpi.engineSetSeed(seed);
            }
        } else {
            try {
                SecureRandom random = SecureRandom.getInstance(prng);
                this.secureRandomSpi = random.getSecureRandomSpi();
                this.provider = random.getProvider();
                if (setSeed) {
                    this.secureRandomSpi.engineSetSeed(seed);
                }
            } catch (NoSuchAlgorithmException nsae) {
                // never happens, because we made sure the algorithm exists
                throw new RuntimeException(nsae);
            }
        }
        // JDK 1.1 based implementations subclass SecureRandom instead of
        // SecureRandomSpi. They will also go through this code path because
        // they must call a SecureRandom constructor as it is their superclass.
        // If we are dealing with such an implementation, do not set the
        // algorithm value as it would be inaccurate.
        if (getClass() == SecureRandom.class) {
            this.algorithm = prng;
        }
    }

    /**
     * Creates a SecureRandom object.
     *
     * @param secureRandomSpi the SecureRandom implementation.
     * @param provider the provider.
     */
    protected SecureRandom(SecureRandomSpi secureRandomSpi,
                           Provider provider) {
        this(secureRandomSpi, provider, null);
    }

    private SecureRandom(SecureRandomSpi secureRandomSpi, Provider provider,
            String algorithm) {
        super(0);
        this.secureRandomSpi = secureRandomSpi;
        this.provider = provider;
        this.algorithm = algorithm;

        if (!skipDebug && pdebug != null) {
            pdebug.println("SecureRandom." + algorithm +
                " algorithm from: " + this.provider.getName());
        }
    }

    /**
     * Returns a SecureRandom object that implements the specified
     * Random Number Generator (RNG) algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new SecureRandom object encapsulating the
     * SecureRandomSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * <p> The returned SecureRandom object has not been seeded.  To seed the
     * returned object, call the {@code setSeed} method.
     * If {@code setSeed} is not called, the first call to
     * {@code nextBytes} will force the SecureRandom object to seed itself.
     * This self-seeding will not occur if {@code setSeed} was
     * previously called.
     *
     * @implNote
     * The JDK Reference Implementation additionally uses the
     * {@code jdk.security.provider.preferred} property to determine
     * the preferred provider order for the specified algorithm. This
     * may be different than the order of providers returned by
     * {@link Security#getProviders() Security.getProviders()}.
     *
     * @param algorithm the name of the RNG algorithm.
     * See the SecureRandom section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#SecureRandom">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard RNG algorithm names.
     *
     * @return the new SecureRandom object.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          SecureRandomSpi implementation for the
     *          specified algorithm.
     *
     * @see Provider
     *
     * @since 1.2
     */
    public static SecureRandom getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("SecureRandom",
            SecureRandomSpi.class, algorithm);
        return new SecureRandom((SecureRandomSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a SecureRandom object that implements the specified
     * Random Number Generator (RNG) algorithm.
     *
     * <p> A new SecureRandom object encapsulating the
     * SecureRandomSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * <p> The returned SecureRandom object has not been seeded.  To seed the
     * returned object, call the {@code setSeed} method.
     * If {@code setSeed} is not called, the first call to
     * {@code nextBytes} will force the SecureRandom object to seed itself.
     * This self-seeding will not occur if {@code setSeed} was
     * previously called.
     *
     * @param algorithm the name of the RNG algorithm.
     * See the SecureRandom section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#SecureRandom">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard RNG algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return the new SecureRandom object.
     *
     * @exception NoSuchAlgorithmException if a SecureRandomSpi
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
     *
     * @since 1.2
     */
    public static SecureRandom getInstance(String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        Instance instance = GetInstance.getInstance("SecureRandom",
            SecureRandomSpi.class, algorithm, provider);
        return new SecureRandom((SecureRandomSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a SecureRandom object that implements the specified
     * Random Number Generator (RNG) algorithm.
     *
     * <p> A new SecureRandom object encapsulating the
     * SecureRandomSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * <p> The returned SecureRandom object has not been seeded.  To seed the
     * returned object, call the {@code setSeed} method.
     * If {@code setSeed} is not called, the first call to
     * {@code nextBytes} will force the SecureRandom object to seed itself.
     * This self-seeding will not occur if {@code setSeed} was
     * previously called.
     *
     * @param algorithm the name of the RNG algorithm.
     * See the SecureRandom section in the <a href=
     * "{@docRoot}/../technotes/guides/security/StandardNames.html#SecureRandom">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard RNG algorithm names.
     *
     * @param provider the provider.
     *
     * @return the new SecureRandom object.
     *
     * @exception NoSuchAlgorithmException if a SecureRandomSpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the specified provider is null.
     *
     * @see Provider
     *
     * @since 1.4
     */
    public static SecureRandom getInstance(String algorithm,
            Provider provider) throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("SecureRandom",
            SecureRandomSpi.class, algorithm, provider);
        return new SecureRandom((SecureRandomSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns the SecureRandomSpi of this SecureRandom object.
     */
    SecureRandomSpi getSecureRandomSpi() {
        return secureRandomSpi;
    }

    /**
     * Returns the provider of this SecureRandom object.
     *
     * @return the provider of this SecureRandom object.
     */
    public final Provider getProvider() {
        return provider;
    }

    /**
     * Returns the name of the algorithm implemented by this SecureRandom
     * object.
     *
     * @return the name of the algorithm or {@code unknown}
     *          if the algorithm name cannot be determined.
     * @since 1.5
     */
    public String getAlgorithm() {
        return Objects.toString(algorithm, "unknown");
    }

    /**
     * Reseeds this random object. The given seed supplements, rather than
     * replaces, the existing seed. Thus, repeated calls are guaranteed
     * never to reduce randomness.
     *
     * @param seed the seed.
     *
     * @see #getSeed
     */
    public synchronized void setSeed(byte[] seed) {
        secureRandomSpi.engineSetSeed(seed);
    }

    /**
     * Reseeds this random object, using the eight bytes contained
     * in the given {@code long seed}. The given seed supplements,
     * rather than replaces, the existing seed. Thus, repeated calls
     * are guaranteed never to reduce randomness.
     *
     * <p>This method is defined for compatibility with
     * {@code java.util.Random}.
     *
     * @param seed the seed.
     *
     * @see #getSeed
     */
    @Override
    public void setSeed(long seed) {
        /*
         * Ignore call from super constructor (as well as any other calls
         * unfortunate enough to be passing 0).  It's critical that we
         * ignore call from superclass constructor, as digest has not
         * yet been initialized at that point.
         */
        if (seed != 0) {
            secureRandomSpi.engineSetSeed(longToByteArray(seed));
        }
    }

    /**
     * Generates a user-specified number of random bytes.
     *
     * <p> If a call to {@code setSeed} had not occurred previously,
     * the first call to this method forces this SecureRandom object
     * to seed itself.  This self-seeding will not occur if
     * {@code setSeed} was previously called.
     *
     * @param bytes the array to be filled in with random bytes.
     */
    @Override
    public synchronized void nextBytes(byte[] bytes) {
        secureRandomSpi.engineNextBytes(bytes);
    }

    /**
     * Generates an integer containing the user-specified number of
     * pseudo-random bits (right justified, with leading zeros).  This
     * method overrides a {@code java.util.Random} method, and serves
     * to provide a source of random bits to all of the methods inherited
     * from that class (for example, {@code nextInt},
     * {@code nextLong}, and {@code nextFloat}).
     *
     * @param numBits number of pseudo-random bits to be generated, where
     * {@code 0 <= numBits <= 32}.
     *
     * @return an {@code int} containing the user-specified number
     * of pseudo-random bits (right justified, with leading zeros).
     */
    @Override
    protected final int next(int numBits) {
        int numBytes = (numBits+7)/8;
        byte[] b = new byte[numBytes];
        int next = 0;

        nextBytes(b);
        for (int i = 0; i < numBytes; i++) {
            next = (next << 8) + (b[i] & 0xFF);
        }

        return next >>> (numBytes*8 - numBits);
    }

    /**
     * Returns the given number of seed bytes, computed using the seed
     * generation algorithm that this class uses to seed itself.  This
     * call may be used to seed other random number generators.
     *
     * <p>This method is only included for backwards compatibility.
     * The caller is encouraged to use one of the alternative
     * {@code getInstance} methods to obtain a SecureRandom object, and
     * then call the {@code generateSeed} method to obtain seed bytes
     * from that object.
     *
     * @param numBytes the number of seed bytes to generate.
     *
     * @return the seed bytes.
     *
     * @see #setSeed
     */
    public static byte[] getSeed(int numBytes) {
        SecureRandom seedGen = seedGenerator;
        if (seedGen == null) {
            seedGen = new SecureRandom();
            seedGenerator = seedGen;
        }
        return seedGen.generateSeed(numBytes);
    }

    /**
     * Returns the given number of seed bytes, computed using the seed
     * generation algorithm that this class uses to seed itself.  This
     * call may be used to seed other random number generators.
     *
     * @param numBytes the number of seed bytes to generate.
     *
     * @return the seed bytes.
     */
    public byte[] generateSeed(int numBytes) {
        return secureRandomSpi.engineGenerateSeed(numBytes);
    }

    /**
     * Helper function to convert a long into a byte array (least significant
     * byte first).
     */
    private static byte[] longToByteArray(long l) {
        byte[] retVal = new byte[8];

        for (int i = 0; i < 8; i++) {
            retVal[i] = (byte) l;
            l >>= 8;
        }

        return retVal;
    }

    /**
     * Gets a default PRNG algorithm by looking through all registered
     * providers. Returns the first PRNG algorithm of the first provider that
     * has registered a SecureRandom implementation, or null if none of the
     * registered providers supplies a SecureRandom implementation.
     */
    private static String getPrngAlgorithm() {
        for (Provider p : Providers.getProviderList().providers()) {
            for (Service s : p.getServices()) {
                if (s.getType().equals("SecureRandom")) {
                    return s.getAlgorithm();
                }
            }
        }
        return null;
    }

    /*
     * Lazily initialize since Pattern.compile() is heavy.
     * Effective Java (2nd Edition), Item 71.
     */
    private static final class StrongPatternHolder {
        /*
         * Entries are alg:prov separated by ,
         * Allow for prepended/appended whitespace between entries.
         *
         * Capture groups:
         *     1 - alg
         *     2 - :prov (optional)
         *     3 - prov (optional)
         *     4 - ,nextEntry (optional)
         *     5 - nextEntry (optional)
         */
        private static Pattern pattern =
            Pattern.compile(
                "\\s*([\\S&&[^:,]]*)(\\:([\\S&&[^,]]*))?\\s*(\\,(.*))?");
    }

    /**
     * Returns a {@code SecureRandom} object that was selected by using
     * the algorithms/providers specified in the {@code
     * securerandom.strongAlgorithms} {@link Security} property.
     * <p>
     * Some situations require strong random values, such as when
     * creating high-value/long-lived secrets like RSA public/private
     * keys.  To help guide applications in selecting a suitable strong
     * {@code SecureRandom} implementation, Java distributions
     * include a list of known strong {@code SecureRandom}
     * implementations in the {@code securerandom.strongAlgorithms}
     * Security property.
     * <p>
     * Every implementation of the Java platform is required to
     * support at least one strong {@code SecureRandom} implementation.
     *
     * @return a strong {@code SecureRandom} implementation as indicated
     * by the {@code securerandom.strongAlgorithms} Security property
     *
     * @throws NoSuchAlgorithmException if no algorithm is available
     *
     * @see Security#getProperty(String)
     *
     * @since 1.8
     */
    public static SecureRandom getInstanceStrong()
            throws NoSuchAlgorithmException {

        String property = AccessController.doPrivileged(
            new PrivilegedAction<>() {
                @Override
                public String run() {
                    return Security.getProperty(
                        "securerandom.strongAlgorithms");
                }
            });

        if ((property == null) || (property.length() == 0)) {
            throw new NoSuchAlgorithmException(
                "Null/empty securerandom.strongAlgorithms Security Property");
        }

        String remainder = property;
        while (remainder != null) {
            Matcher m;
            if ((m = StrongPatternHolder.pattern.matcher(
                    remainder)).matches()) {

                String alg = m.group(1);
                String prov = m.group(3);

                try {
                    if (prov == null) {
                        return SecureRandom.getInstance(alg);
                    } else {
                        return SecureRandom.getInstance(alg, prov);
                    }
                } catch (NoSuchAlgorithmException |
                        NoSuchProviderException e) {
                }
                remainder = m.group(5);
            } else {
                remainder = null;
            }
        }

        throw new NoSuchAlgorithmException(
            "No strong SecureRandom impls available: " + property);
    }

    // Declare serialVersionUID to be compatible with JDK1.1
    static final long serialVersionUID = 4940670005562187L;

    // Retain unused values serialized from JDK1.1
    /**
     * @serial
     */
    private byte[] state;
    /**
     * @serial
     */
    private MessageDigest digest = null;
    /**
     * @serial
     *
     * We know that the MessageDigest class does not implement
     * java.io.Serializable.  However, since this field is no longer
     * used, it will always be NULL and won't affect the serialization
     * of the SecureRandom class itself.
     */
    private byte[] randomBytes;
    /**
     * @serial
     */
    private int randomBytesUsed;
    /**
     * @serial
     */
    private long counter;
}
