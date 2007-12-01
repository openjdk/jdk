/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider;

import java.util.*;
import java.math.BigInteger;

import java.security.*;
import java.security.SecureRandom;
import java.security.spec.*;

import javax.crypto.spec.DHParameterSpec;

/**
 * Cache for DSA and DH parameter specs. Used by the KeyPairGenerators
 * in the Sun, SunJCE, and SunPKCS11 provider if no parameters have been
 * explicitly specified by the application.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
public final class ParameterCache {

    private ParameterCache() {
        // empty
    }

    // cache of DSA parameters
    private final static Map<Integer,DSAParameterSpec> dsaCache;

    // cache of DH parameters
    private final static Map<Integer,DHParameterSpec> dhCache;

    /**
     * Return cached DSA parameters for the given keylength, or null if none
     * are available in the cache.
     */
    public static DSAParameterSpec getCachedDSAParameterSpec(int keyLength) {
        return dsaCache.get(Integer.valueOf(keyLength));
    }

    /**
     * Return cached DH parameters for the given keylength, or null if none
     * are available in the cache.
     */
    public static DHParameterSpec getCachedDHParameterSpec(int keyLength) {
        return dhCache.get(Integer.valueOf(keyLength));
    }

    /**
     * Return DSA parameters for the given keylength. Uses cache if possible,
     * generates new parameters and adds them to the cache otherwise.
     */
    public static DSAParameterSpec getDSAParameterSpec(int keyLength,
            SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException {
        DSAParameterSpec spec = getCachedDSAParameterSpec(keyLength);
        if (spec != null) {
            return spec;
        }
        spec = getNewDSAParameterSpec(keyLength, random);
        dsaCache.put(Integer.valueOf(keyLength), spec);
        return spec;
    }

    /**
     * Return DH parameters for the given keylength. Uses cache if possible,
     * generates new parameters and adds them to the cache otherwise.
     */
    public static DHParameterSpec getDHParameterSpec(int keyLength,
            SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException {
        DHParameterSpec spec = getCachedDHParameterSpec(keyLength);
        if (spec != null) {
            return spec;
        }
        AlgorithmParameterGenerator gen =
                AlgorithmParameterGenerator.getInstance("DH");
        gen.init(keyLength, random);
        AlgorithmParameters params = gen.generateParameters();
        spec = params.getParameterSpec(DHParameterSpec.class);
        dhCache.put(Integer.valueOf(keyLength), spec);
        return spec;
    }

    /**
     * Return new DSA parameters for the given keylength. Do not lookup in
     * cache and do not cache the newly generated parameters. This method
     * really only exists for the legacy method
     * DSAKeyPairGenerator.initialize(int, boolean, SecureRandom).
     */
    public static DSAParameterSpec getNewDSAParameterSpec(int keyLength,
            SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException {
        AlgorithmParameterGenerator gen =
                AlgorithmParameterGenerator.getInstance("DSA");
        gen.init(keyLength, random);
        AlgorithmParameters params = gen.generateParameters();
        DSAParameterSpec spec = params.getParameterSpec(DSAParameterSpec.class);
        return spec;
    }

    static {
        // XXX change to ConcurrentHashMap once available
        dhCache = Collections.synchronizedMap
                        (new HashMap<Integer,DHParameterSpec>());
        dsaCache = Collections.synchronizedMap
                        (new HashMap<Integer,DSAParameterSpec>());

        /*
         * We support precomputed parameter for 512, 768 and 1024 bit
         * moduli. In this file we provide both the seed and counter
         * value of the generation process for each of these seeds,
         * for validation purposes. We also include the test vectors
         * from the DSA specification, FIPS 186, and the FIPS 186
         * Change No 1, which updates the test vector using SHA-1
         * instead of SHA (for both the G function and the message
         * hash.
         */

        /*
         * L = 512
         * SEED = b869c82b35d70e1b1ff91b28e37a62ecdc34409b
         * counter = 123
         */
        BigInteger p512 =
            new BigInteger("fca682ce8e12caba26efccf7110e526db078b05edecb" +
                           "cd1eb4a208f3ae1617ae01f35b91a47e6df63413c5e1" +
                           "2ed0899bcd132acd50d99151bdc43ee737592e17", 16);

        BigInteger q512 =
            new BigInteger("962eddcc369cba8ebb260ee6b6a126d9346e38c5", 16);

        BigInteger g512 =
            new BigInteger("678471b27a9cf44ee91a49c5147db1a9aaf244f05a43" +
                           "4d6486931d2d14271b9e35030b71fd73da179069b32e" +
                           "2935630e1c2062354d0da20a6c416e50be794ca4", 16);

        /*
         * L = 768
         * SEED = 77d0f8c4dad15eb8c4f2f8d6726cefd96d5bb399
         * counter = 263
         */
        BigInteger p768 =
            new BigInteger("e9e642599d355f37c97ffd3567120b8e25c9cd43e" +
                           "927b3a9670fbec5d890141922d2c3b3ad24800937" +
                           "99869d1e846aab49fab0ad26d2ce6a22219d470bc" +
                           "e7d777d4a21fbe9c270b57f607002f3cef8393694" +
                           "cf45ee3688c11a8c56ab127a3daf", 16);

        BigInteger q768 =
            new BigInteger("9cdbd84c9f1ac2f38d0f80f42ab952e7338bf511",
                           16);

        BigInteger g768 =
            new BigInteger("30470ad5a005fb14ce2d9dcd87e38bc7d1b1c5fac" +
                           "baecbe95f190aa7a31d23c4dbbcbe06174544401a" +
                           "5b2c020965d8c2bd2171d3668445771f74ba084d2" +
                           "029d83c1c158547f3a9f1a2715be23d51ae4d3e5a" +
                           "1f6a7064f316933a346d3f529252", 16);


        /*
         * L = 1024
         * SEED = 8d5155894229d5e689ee01e6018a237e2cae64cd
         * counter = 92
         */
        BigInteger p1024 =
            new BigInteger("fd7f53811d75122952df4a9c2eece4e7f611b7523c" +
                           "ef4400c31e3f80b6512669455d402251fb593d8d58" +
                           "fabfc5f5ba30f6cb9b556cd7813b801d346ff26660" +
                           "b76b9950a5a49f9fe8047b1022c24fbba9d7feb7c6" +
                           "1bf83b57e7c6a8a6150f04fb83f6d3c51ec3023554" +
                           "135a169132f675f3ae2b61d72aeff22203199dd148" +
                           "01c7", 16);

        BigInteger q1024 =
            new BigInteger("9760508f15230bccb292b982a2eb840bf0581cf5",
                           16);

        BigInteger g1024 =
            new BigInteger("f7e1a085d69b3ddecbbcab5c36b857b97994afbbfa" +
                           "3aea82f9574c0b3d0782675159578ebad4594fe671" +
                           "07108180b449167123e84c281613b7cf09328cc8a6" +
                           "e13c167a8b547c8d28e0a3ae1e2bb3a675916ea37f" +
                           "0bfa213562f1fb627a01243bcca4f1bea8519089a8" +
                           "83dfe15ae59f06928b665e807b552564014c3bfecf" +
                           "492a", 16);

        dsaCache.put(Integer.valueOf(512),
                                new DSAParameterSpec(p512, q512, g512));
        dsaCache.put(Integer.valueOf(768),
                                new DSAParameterSpec(p768, q768, g768));
        dsaCache.put(Integer.valueOf(1024),
                                new DSAParameterSpec(p1024, q1024, g1024));

        // use DSA parameters for DH as well
        dhCache.put(Integer.valueOf(512), new DHParameterSpec(p512, g512));
        dhCache.put(Integer.valueOf(768), new DHParameterSpec(p768, g768));
        dhCache.put(Integer.valueOf(1024), new DHParameterSpec(p1024, g1024));
    }

}
