/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
     * Return cached DSA parameters for the given length combination of
     * prime and subprime, or null if none are available in the cache.
     */
    public static DSAParameterSpec getCachedDSAParameterSpec(int primeLen,
            int subprimeLen) {
        // ensure the sum is unique in all cases, i.e.
        // case#1: (512 <= p <= 1024) AND q=160
        // case#2: p=2048 AND q=224
        // case#3: p=2048 AND q=256
        // (NOT-YET-SUPPORTED)case#4: p=3072 AND q=256
        return dsaCache.get(Integer.valueOf(primeLen+subprimeLen));
    }

    /**
     * Return cached DH parameters for the given keylength, or null if none
     * are available in the cache.
     */
    public static DHParameterSpec getCachedDHParameterSpec(int keyLength) {
        return dhCache.get(Integer.valueOf(keyLength));
    }

    /**
     * Return DSA parameters for the given primeLen. Uses cache if
     * possible, generates new parameters and adds them to the cache
     * otherwise.
     */
    public static DSAParameterSpec getDSAParameterSpec(int primeLen,
            SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException,
                   InvalidAlgorithmParameterException {
        if (primeLen <= 1024) {
            return getDSAParameterSpec(primeLen, 160, random);
        } else if (primeLen == 2048) {
            return getDSAParameterSpec(primeLen, 224, random);
        } else {
            return null;
        }
    }

    /**
     * Return DSA parameters for the given primeLen and subprimeLen.
     * Uses cache if possible, generates new parameters and adds them to the
     * cache otherwise.
     */
    public static DSAParameterSpec getDSAParameterSpec(int primeLen,
            int subprimeLen, SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException,
                   InvalidAlgorithmParameterException {
        DSAParameterSpec spec =
            getCachedDSAParameterSpec(primeLen, subprimeLen);
        if (spec != null) {
            return spec;
        }
        spec = getNewDSAParameterSpec(primeLen, subprimeLen, random);
        dsaCache.put(Integer.valueOf(primeLen + subprimeLen), spec);
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
     * Return new DSA parameters for the given length combination of prime and
     * sub prime. Do not lookup in cache and do not cache the newly generated
     * parameters. This method really only exists for the legacy method
     * DSAKeyPairGenerator.initialize(int, boolean, SecureRandom).
     */
    public static DSAParameterSpec getNewDSAParameterSpec(int primeLen,
            int subprimeLen, SecureRandom random)
            throws NoSuchAlgorithmException, InvalidParameterSpecException,
                   InvalidAlgorithmParameterException {
        AlgorithmParameterGenerator gen =
                AlgorithmParameterGenerator.getInstance("DSA");
        // Use init(int size, SecureRandom random) for legacy DSA key sizes
        if (primeLen < 1024) {
            gen.init(primeLen, random);
        } else {
            DSAGenParameterSpec genParams =
                new DSAGenParameterSpec(primeLen, subprimeLen);
            gen.init(genParams, random);
        }
        AlgorithmParameters params = gen.generateParameters();
        DSAParameterSpec spec = params.getParameterSpec(DSAParameterSpec.class);
        return spec;
    }

    static {
        dhCache = new ConcurrentHashMap<Integer,DHParameterSpec>();
        dsaCache = new ConcurrentHashMap<Integer,DSAParameterSpec>();

        /*
         * We support precomputed parameter for legacy 512, 768 bit moduli,
         * and (L, N) combinations of (1024, 160), (2048, 224), (2048, 256).
         * In this file we provide both the seed and counter
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

        dsaCache.put(Integer.valueOf(512+160),
                                new DSAParameterSpec(p512, q512, g512));
        dsaCache.put(Integer.valueOf(768+160),
                                new DSAParameterSpec(p768, q768, g768));
        dsaCache.put(Integer.valueOf(1024+160),
                                new DSAParameterSpec(p1024, q1024, g1024));
        /*
         * L = 2048, N = 224
         * SEED = 584236080cfa43c09b02354135f4cc5198a19efada08bd866d601ba4
         * counter = 2666
         */
        BigInteger p2048_224 =
            new BigInteger("8f7935d9b9aae9bfabed887acf4951b6f32ec59e3b" +
                           "af3718e8eac4961f3efd3606e74351a9c4183339b8" +
                           "09e7c2ae1c539ba7475b85d011adb8b47987754984" +
                           "695cac0e8f14b3360828a22ffa27110a3d62a99345" +
                           "3409a0fe696c4658f84bdd20819c3709a01057b195" +
                           "adcd00233dba5484b6291f9d648ef883448677979c" +
                           "ec04b434a6ac2e75e9985de23db0292fc1118c9ffa" +
                           "9d8181e7338db792b730d7b9e349592f6809987215" +
                           "3915ea3d6b8b4653c633458f803b32a4c2e0f27290" +
                           "256e4e3f8a3b0838a1c450e4e18c1a29a37ddf5ea1" +
                           "43de4b66ff04903ed5cf1623e158d487c608e97f21" +
                           "1cd81dca23cb6e380765f822e342be484c05763939" +
                           "601cd667", 16);

        BigInteger q2048_224 =
            new BigInteger("baf696a68578f7dfdee7fa67c977c785ef32b233ba" +
                           "e580c0bcd5695d", 16);

        BigInteger g2048_224 =
            new BigInteger("16a65c58204850704e7502a39757040d34da3a3478" +
                           "c154d4e4a5c02d242ee04f96e61e4bd0904abdac8f" +
                           "37eeb1e09f3182d23c9043cb642f88004160edf9ca" +
                           "09b32076a79c32a627f2473e91879ba2c4e744bd20" +
                           "81544cb55b802c368d1fa83ed489e94e0fa0688e32" +
                           "428a5c78c478c68d0527b71c9a3abb0b0be12c4468" +
                           "9639e7d3ce74db101a65aa2b87f64c6826db3ec72f" +
                           "4b5599834bb4edb02f7c90e9a496d3a55d535bebfc" +
                           "45d4f619f63f3dedbb873925c2f224e07731296da8" +
                           "87ec1e4748f87efb5fdeb75484316b2232dee553dd" +
                           "af02112b0d1f02da30973224fe27aeda8b9d4b2922" +
                           "d9ba8be39ed9e103a63c52810bc688b7e2ed4316e1" +
                           "ef17dbde", 16);
        dsaCache.put(Integer.valueOf(2048+224),
                     new DSAParameterSpec(p2048_224, q2048_224, g2048_224));

        /*
         * L = 2048, N = 256
         * SEED = b0b4417601b59cbc9d8ac8f935cadaec4f5fbb2f23785609ae466748d9b5a536
         * counter = 497
         */
        BigInteger p2048_256 =
            new BigInteger("95475cf5d93e596c3fcd1d902add02f427f5f3c721" +
                           "0313bb45fb4d5bb2e5fe1cbd678cd4bbdd84c9836b" +
                           "e1f31c0777725aeb6c2fc38b85f48076fa76bcd814" +
                           "6cc89a6fb2f706dd719898c2083dc8d896f84062e2" +
                           "c9c94d137b054a8d8096adb8d51952398eeca852a0" +
                           "af12df83e475aa65d4ec0c38a9560d5661186ff98b" +
                           "9fc9eb60eee8b030376b236bc73be3acdbd74fd61c" +
                           "1d2475fa3077b8f080467881ff7e1ca56fee066d79" +
                           "506ade51edbb5443a563927dbc4ba520086746175c" +
                           "8885925ebc64c6147906773496990cb714ec667304" +
                           "e261faee33b3cbdf008e0c3fa90650d97d3909c927" +
                           "5bf4ac86ffcb3d03e6dfc8ada5934242dd6d3bcca2" +
                           "a406cb0b", 16);

        BigInteger q2048_256 =
            new BigInteger("f8183668ba5fc5bb06b5981e6d8b795d30b8978d43" +
                           "ca0ec572e37e09939a9773", 16);

        BigInteger g2048_256 =
            new BigInteger("42debb9da5b3d88cc956e08787ec3f3a09bba5f48b" +
                           "889a74aaf53174aa0fbe7e3c5b8fcd7a53bef563b0" +
                           "e98560328960a9517f4014d3325fc7962bf1e04937" +
                           "0d76d1314a76137e792f3f0db859d095e4a5b93202" +
                           "4f079ecf2ef09c797452b0770e1350782ed57ddf79" +
                           "4979dcef23cb96f183061965c4ebc93c9c71c56b92" +
                           "5955a75f94cccf1449ac43d586d0beee43251b0b22" +
                           "87349d68de0d144403f13e802f4146d882e057af19" +
                           "b6f6275c6676c8fa0e3ca2713a3257fd1b27d0639f" +
                           "695e347d8d1cf9ac819a26ca9b04cb0eb9b7b03598" +
                           "8d15bbac65212a55239cfc7e58fae38d7250ab9991" +
                           "ffbc97134025fe8ce04c4399ad96569be91a546f49" +
                           "78693c7a", 16);
        dsaCache.put(Integer.valueOf(2048+256),
                                new DSAParameterSpec(p2048_256, q2048_256, g2048_256));

        // use DSA parameters for DH as well
        dhCache.put(Integer.valueOf(512), new DHParameterSpec(p512, g512));
        dhCache.put(Integer.valueOf(768), new DHParameterSpec(p768, g768));
        dhCache.put(Integer.valueOf(1024), new DHParameterSpec(p1024, g1024));
        dhCache.put(Integer.valueOf(2048), new DHParameterSpec(p2048_224, g2048_224));
    }

}
