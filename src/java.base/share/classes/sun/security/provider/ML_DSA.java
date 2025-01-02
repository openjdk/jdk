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

package sun.security.provider;

import sun.security.provider.SHA3.SHAKE128;
import sun.security.provider.SHA3.SHAKE256;

import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Arrays;

public class ML_DSA {
    // Security level constants
    static final int ML_DSA_44 = 2;
    static final int ML_DSA_65 = 3;
    static final int ML_DSA_87 = 5;

    // Constants from FIPS 204 that do not depend on security level
    private static final int ML_DSA_D = 13;
    private static final int ML_DSA_Q = 8380417;
    private static final int ML_DSA_N = 256;
    private static final int SHAKE256_BLOCK_SIZE = 136; // the block length for SHAKE256

    private final int A_SEED_LEN = 32;
    private final int S1S2_SEED_LEN = 64;
    private final int K_LEN = 32;
    private final int TR_LEN = 64;
    private final int MU_LEN = 64;
    private final int MASK_SEED_LEN = 64;
    private static final int D_MASK = (1 << ML_DSA_D) - 1;
    private final int T0_COEFF_SIZE = 13;

    private static final int MONT_R_BITS = 32;
    private static final long MONT_R = 4294967296L; // 1 << MONT_R_BITS
    private static final int MONT_Q = 8380417;
    private static final int MONT_R_SQUARE_MOD_Q = 2365951;
    private static final int MONT_Q_INV_MOD_R = 58728449;
    private static final int MONT_R_MOD_Q = 4193792;
    // toMont((ML_DSA_N)^-1 (mod ML_DSA_Q))
    private static final int MONT_DIM_INVERSE = 16382;

    // Zeta values for NTT with montgomery factor precomputed
    private static final int[] MONT_ZETAS_FOR_NTT = new int[]{
        25847, -2608894, -518909, 237124, -777960, -876248, 466468, 1826347,
        2353451, -359251, -2091905, 3119733, -2884855, 3111497, 2680103, 2725464,
        1024112, -1079900, 3585928, -549488, -1119584, 2619752, -2108549, -2118186,
        -3859737, -1399561, -3277672, 1757237, -19422, 4010497, 280005, 2706023,
        95776, 3077325, 3530437, -1661693, -3592148, -2537516, 3915439, -3861115,
        -3043716, 3574422, -2867647, 3539968, -300467, 2348700, -539299, -1699267,
        -1643818, 3505694, -3821735, 3507263, -2140649, -1600420, 3699596, 811944,
        531354, 954230, 3881043, 3900724, -2556880, 2071892, -2797779, -3930395,
        -1528703, -3677745, -3041255, -1452451, 3475950, 2176455, -1585221, -1257611,
        1939314, -4083598, -1000202, -3190144, -3157330, -3632928, 126922, 3412210,
        -983419, 2147896, 2715295, -2967645, -3693493, -411027, -2477047, -671102,
        -1228525, -22981, -1308169, -381987, 1349076, 1852771, -1430430, -3343383,
        264944, 508951, 3097992, 44288, -1100098, 904516, 3958618, -3724342,
        -8578, 1653064, -3249728, 2389356, -210977, 759969, -1316856, 189548,
        -3553272, 3159746, -1851402, -2409325, -177440, 1315589, 1341330, 1285669,
        -1584928, -812732, -1439742, -3019102, -3881060, -3628969, 3839961, 2091667,
        3407706, 2316500, 3817976, -3342478, 2244091, -2446433, -3562462, 266997,
        2434439, -1235728, 3513181, -3520352, -3759364, -1197226, -3193378, 900702,
        1859098, 909542, 819034, 495491, -1613174, -43260, -522500, -655327,
        -3122442, 2031748, 3207046, -3556995, -525098, -768622, -3595838, 342297,
        286988, -2437823, 4108315, 3437287, -3342277, 1735879, 203044, 2842341,
        2691481, -2590150, 1265009, 4055324, 1247620, 2486353, 1595974, -3767016,
        1250494, 2635921, -3548272, -2994039, 1869119, 1903435, -1050970, -1333058,
        1237275, -3318210, -1430225, -451100, 1312455, 3306115, -1962642, -1279661,
        1917081, -2546312, -1374803, 1500165, 777191, 2235880, 3406031, -542412,
        -2831860, -1671176, -1846953, -2584293, -3724270, 594136, -3776993, -2013608,
        2432395, 2454455, -164721, 1957272, 3369112, 185531, -1207385, -3183426,
        162844, 1616392, 3014001, 810149, 1652634, -3694233, -1799107, -3038916,
        3523897, 3866901, 269760, 2213111, -975884, 1717735, 472078, -426683,
        1723600, -1803090, 1910376, -1667432, -1104333, -260646, -3833893, -2939036,
        -2235985, -420899, -2286327, 183443, -976891, 1612842, -3545687, -554416,
        3919660, -48306, -1362209, 3937738, 1400424, -846154, 1976782
    };
    private static final int[] MONT_ZETAS_FOR_INVERSE_NTT = new int[]{
        -1976782, 846154, -1400424, -3937738, 1362209, 48306, -3919660, 554416,
        3545687, -1612842, 976891, -183443, 2286327, 420899, 2235985, 2939036,
        3833893, 260646, 1104333, 1667432, -1910376, 1803090, -1723600, 426683,
        -472078, -1717735, 975884, -2213111, -269760, -3866901, -3523897, 3038916,
        1799107, 3694233, -1652634, -810149, -3014001, -1616392, -162844, 3183426,
        1207385, -185531, -3369112, -1957272, 164721, -2454455, -2432395, 2013608,
        3776993, -594136, 3724270, 2584293, 1846953, 1671176, 2831860, 542412,
        -3406031, -2235880, -777191, -1500165, 1374803, 2546312, -1917081, 1279661,
        1962642, -3306115, -1312455, 451100, 1430225, 3318210, -1237275, 1333058,
        1050970, -1903435, -1869119, 2994039, 3548272, -2635921, -1250494, 3767016,
        -1595974, -2486353, -1247620, -4055324, -1265009, 2590150, -2691481, -2842341,
        -203044, -1735879, 3342277, -3437287, -4108315, 2437823, -286988, -342297,
        3595838, 768622, 525098, 3556995, -3207046, -2031748, 3122442, 655327,
        522500, 43260, 1613174, -495491, -819034, -909542, -1859098, -900702,
        3193378, 1197226, 3759364, 3520352, -3513181, 1235728, -2434439, -266997,
        3562462, 2446433, -2244091, 3342478, -3817976, -2316500, -3407706, -2091667,
        -3839961, 3628969, 3881060, 3019102, 1439742, 812732, 1584928, -1285669,
        -1341330, -1315589, 177440, 2409325, 1851402, -3159746, 3553272, -189548,
        1316856, -759969, 210977, -2389356, 3249728, -1653064, 8578, 3724342,
        -3958618, -904516, 1100098, -44288, -3097992, -508951, -264944, 3343383,
        1430430, -1852771, -1349076, 381987, 1308169, 22981, 1228525, 671102,
        2477047, 411027, 3693493, 2967645, -2715295, -2147896, 983419, -3412210,
        -126922, 3632928, 3157330, 3190144, 1000202, 4083598, -1939314, 1257611,
        1585221, -2176455, -3475950, 1452451, 3041255, 3677745, 1528703, 3930395,
        2797779, -2071892, 2556880, -3900724, -3881043, -954230, -531354, -811944,
        -3699596, 1600420, 2140649, -3507263, 3821735, -3505694, 1643818, 1699267,
        539299, -2348700, 300467, -3539968, 2867647, -3574422, 3043716, 3861115,
        -3915439, 2537516, 3592148, 1661693, -3530437, -3077325, -95776, -2706023,
        -280005, -4010497, 19422, -1757237, 3277672, 1399561, 3859737, 2118186,
        2108549, -2619752, 1119584, 549488, -3585928, 1079900, -1024112, -2725464,
        -2680103, -3111497, 2884855, -3119733, 2091905, 359251, -2353451, -1826347,
        -466468, 876248, 777960, -237124, 518909, 2608894, -25847
    };

    // Constants defined for each security level
    private final int level;
    private final int tau;
    private final int lambda;
    private final int gamma1;
    private final int gamma2;
    private final int mlDsa_k;
    private final int mlDsa_l;
    private final int eta;
    private final int beta;
    private final int omega;

    // Second-class constants derived from above values
    // log_2(gamma1)
    private final int gamma1Bits;
    // mlDsa_l * (eta + 1) * 256 / 8
    private final int s1PackedLength;
    // mlDsa_k * (eta + 1) * 256 / 8
    private final int s2PackedLength;
    // mlDsa_k * ML_DSA_D * 256 / 8
    private final int t0PackedLength;
    // log_2(eta) + 1
    private final int s1s2CoeffSize;
    // rho_size + t1_size
    private final int publicKeyLength;
    // c_tilde_size + z_size + h_size
    private final int signatureLength;
    // mlDsa_k * log_2((q-1)/(2*gamma2) - 1) * 256 / 8
    private final int wCoeffSize;

    public ML_DSA(int security_level) {
        switch (security_level) {
            case ML_DSA_44:
                level = 2;
                tau = 39;
                lambda = 128;
                gamma1 = 1 << 17;
                gamma1Bits = 17;
                gamma2 = (ML_DSA_Q - 1) / 88;
                mlDsa_k = 4;
                mlDsa_l = 4;
                eta = 2;
                beta = 78;
                omega = 80;
                publicKeyLength = 1312;
                signatureLength = 2420;
                s1PackedLength = 384;
                s2PackedLength = 384;
                t0PackedLength = 1664;
                s1s2CoeffSize = 3;
                wCoeffSize = 6;
                break;
            case ML_DSA_65:
                level = 3;
                tau = 49;
                lambda = 192;
                gamma1 = 1 << 19;
                gamma2 = (ML_DSA_Q - 1) / 32;
                mlDsa_k = 6;
                mlDsa_l = 5;
                eta = 4;
                beta = 196;
                omega = 55;
                publicKeyLength = 1952;
                signatureLength = 3293;
                s1PackedLength = 640;
                s2PackedLength = 768;
                t0PackedLength = 2496;
                s1s2CoeffSize = 4;
                wCoeffSize = 4;
                gamma1Bits = 19;
                break;
            case ML_DSA_87:
                level = 4;
                tau = 60;
                lambda = 256;
                gamma1 = 1 << 19;
                gamma1Bits = 19;
                gamma2 = (ML_DSA_Q - 1) / 32;
                mlDsa_k = 8;
                mlDsa_l = 7;
                eta = 2;
                beta = 120;
                omega = 75;
                publicKeyLength = 2592;
                signatureLength = 4595;
                s1PackedLength = 672;
                s2PackedLength = 768;
                t0PackedLength = 3328;
                s1s2CoeffSize = 3;
                wCoeffSize = 4;
                break;
            default:
                throw new IllegalArgumentException("Wrong security level");
        }
    }

    public record ML_DSA_PrivateKey(byte[] rho, byte[] k, byte[] tr,
                                    int[][] s1, int[][] s2, int[][] t0) {
        void destroy() {
            Arrays.fill(k, (byte)0);
            for (var b : s1) {
                Arrays.fill(b, (byte) 0);
            }
            for (var b : s2) {
                Arrays.fill(b, (byte) 0);
            }
            for (var b : t0) {
                Arrays.fill(b, (byte) 0);
            }
        }
    }

    public record ML_DSA_PublicKey(byte[] rho, int[][] t1) {
    }

    public record ML_DSA_KeyPair(ML_DSA_PrivateKey privateKey,
                                 ML_DSA_PublicKey publicKey) {
    }

    public record ML_DSA_Signature(byte[] commitmentHash,
                                   int[][] response, boolean[][] hint) {
    }

    /*
    Key validity checks
     */
    public Object checkPublicKey(byte[] pk) throws InvalidKeyException {
        int pk_size = 32 + (mlDsa_k * 32 * (23 - ML_DSA_D));
        if (pk.length != pk_size) {
            throw new InvalidKeyException("Incorrect public key size");
        }
        return null;
    }

    public Object checkPrivateKey(byte[] sk) throws InvalidKeyException {
        int eta_bits = eta == 4 ? 4 : 3;

        //SK size is 128 + 32 * ((l + k) * bitlen(2*eta) + d*k)
        int sk_size = 128 + 32 * ((mlDsa_l + mlDsa_k) * eta_bits + ML_DSA_D * mlDsa_k);
        if (sk.length != sk_size) {
            throw new InvalidKeyException("Incorrect private key size");
        }
        return null;
    }

    //Internal functions in Section 6 of specification
    public ML_DSA_KeyPair generateKeyPairInternal(byte[] randomBytes) {
        //Initialize hash functions
        var hash = new SHAKE256(0);
        var crHash = new SHAKE256(TR_LEN);

        //Expand seed
        hash.update(randomBytes);
        hash.update((byte)mlDsa_k);
        hash.update((byte)mlDsa_l);
        byte[] rho = hash.squeeze(A_SEED_LEN);
        byte[] rhoPrime = hash.squeeze(S1S2_SEED_LEN);
        byte[] k = hash.squeeze(K_LEN);
        hash.reset();

        //Sample A
        int[][][] keygenA = generateA(rho); //A is in NTT domain

        //Sample S1 and S2
        int[][] s1 = new int[mlDsa_l][ML_DSA_N];
        int[][] s2 = new int[mlDsa_k][ML_DSA_N];
        //hash is reset before being used in sampleS1S2
        sampleS1S2(s1, s2, hash, rhoPrime);

        //Compute t and tr
        mlDsaVectorNtt(s1); //s1 now in NTT domain
        int[][] As1 = new int[mlDsa_k][ML_DSA_N];
        matrixVectorPointwiseMultiply(As1, keygenA, s1);
        mlDsaVectorInverseNtt(s1); //take s1 out of NTT domain

        mlDsaVectorInverseNtt(As1);
        int[][] t = vectorAddPos(As1, s2);
        int[][] t0 = new int[mlDsa_k][ML_DSA_N];
        int[][] t1 = new int[mlDsa_k][ML_DSA_N];
        power2Round(t, t0, t1);

        //Encode PK and SK
        ML_DSA_PublicKey pk = new ML_DSA_PublicKey(rho, t1);
        byte[] publicKeyBytes = pkEncode(pk);
        crHash.update(publicKeyBytes);
        byte[] tr = crHash.digest();
        ML_DSA_PrivateKey sk = new ML_DSA_PrivateKey(rho, k, tr, s1, s2, t0);

        return new ML_DSA_KeyPair(sk, pk);
    }

    public ML_DSA_Signature signInternal(byte[] message, byte[] rnd, byte[] skBytes) {
        //Decode private key and initialize hash function
        ML_DSA_PrivateKey sk = skDecode(skBytes);
        var hash = new SHAKE256(0);

        //Do some NTTs
        mlDsaVectorNtt(sk.s1());
        mlDsaVectorNtt(sk.s2());
        mlDsaVectorNtt(sk.t0());
        int[][][] aHat = generateA(sk.rho());

        //Compute mu
        hash.update(sk.tr());
        hash.update(message);
        byte[] mu = hash.squeeze(MU_LEN);
        hash.reset();

        //Compute rho'
        hash.update(sk.k());
        hash.update(rnd);
        hash.update(mu);
        byte[] rhoDoublePrime = hash.squeeze(MASK_SEED_LEN);
        hash.reset();

        //Initialize vectors used in loop
        int[][] z = new int[mlDsa_l][ML_DSA_N];
        boolean[][] h = new boolean[mlDsa_k][ML_DSA_N];
        byte[] commitmentHash = new byte[lambda/4];
        int[][] y = new int[mlDsa_l][ML_DSA_N];
        int[][] yy = new int[mlDsa_l][ML_DSA_N];
        int[][] w = new int[mlDsa_k][ML_DSA_N];
        int[][] w0 = new int[mlDsa_k][ML_DSA_N];
        int[][] w1 = new int[mlDsa_k][ML_DSA_N];
        int[][] w_ct0 = new int[mlDsa_k][ML_DSA_N];
        int[] c =  new int[ML_DSA_N];
        int[][] cs1 = new int[mlDsa_l][ML_DSA_N];
        int[][] cs2 = new int[mlDsa_k][ML_DSA_N];
        int[][] ct0 = new int[mlDsa_k][ML_DSA_N];

        int kappa = 0;
        while (true) {
            expandMask(y, rhoDoublePrime, kappa);

            //Save non-ntt version of y for later use
            for (int i = 0; i < y.length; i++) {
                System.arraycopy(y[i], 0, yy[i], 0, ML_DSA_N);
            }

            //Compute w and w1
            mlDsaVectorNtt(y); //y is now in NTT domain
            matrixVectorPointwiseMultiply(w, aHat, y);
            mlDsaVectorInverseNtt(w); //w is now in normal domain
            decompose(w, w0, w1);
            //mlDsaVectorInverseNtt(y);

            //Get commitment hash
            hash.update(mu);
            hash.update(simpleBitPack(wCoeffSize, w1));
            commitmentHash = hash.squeeze(lambda/4);
            hash.reset();

            //Get z and r0
            sampleInBall(c, commitmentHash);
            mlDsaNtt(c); //c is now in NTT domain
            nttConstMultiply(cs1, c, sk.s1());
            nttConstMultiply(cs2, c, sk.s2());
            mlDsaVectorInverseNtt(cs1);
            mlDsaVectorInverseNtt(cs2);
            z = vectorAdd(z, yy, cs1);

            //w0 = w0 - cs2 (this is r0 in the spec)
            vectorSub(w0, cs2, false);

            //Update z and h
            kappa += mlDsa_l;
            if (vectorNormBound(z, gamma1 - beta) ||
                vectorNormBound(w0, gamma2 - beta)) {
                continue;
            } else {
                nttConstMultiply(ct0, c, sk.t0());
                mlDsaVectorInverseNtt(ct0);
                w = vectorSub(w, cs2, false);
                int hint_weight = makeHint(h, w, vectorAdd(w_ct0, w, ct0));
                if (vectorNormBound(ct0, gamma2) || (hint_weight > omega)) {
                    continue;
                }
            }
            sk.destroy();
            return new ML_DSA_Signature(commitmentHash, z, h);
        }
    }

    public boolean verifyInternal(byte[] pkBytes, byte[] message, byte[] sigBytes)
            throws SignatureException {
        //Decode sig and initialize hash
        ML_DSA_Signature sig = sigDecode(sigBytes);
        var hash = new SHAKE256(0);

        //Decode pk
        ML_DSA_PublicKey pk = pkDecode(pkBytes);

        //Expand A
        int[][][] aHat = generateA(pk.rho());

        //Generate tr
        hash.update(pkBytes);
        byte[] tr = hash.squeeze(TR_LEN);
        hash.reset();

        //Generate mu
        hash.update(tr);
        hash.update(message);
        byte[] mu = hash.squeeze(MU_LEN);
        hash.reset();

        //Get verifiers challenge
        int[] cHat = new int[ML_DSA_N];
        sampleInBall(cHat, sig.commitmentHash());
        mlDsaNtt(cHat);

        //Compute response norm and put it in NTT domain
        boolean zNorm = vectorNormBound(sig.response(), gamma1 - beta);
        mlDsaVectorNtt(sig.response());

        //Reconstruct signer's commitment
        int[][] aHatZ = new int[mlDsa_k][ML_DSA_N];
        matrixVectorPointwiseMultiply(aHatZ, aHat, sig.response());

        int[][] t1Hat = vectorConstMul(1 << ML_DSA_D, pk.t1());
        mlDsaVectorNtt(t1Hat);

        int[][] ct1 = new int[mlDsa_k][ML_DSA_N];
        nttConstMultiply(ct1, cHat, t1Hat);

        int[][] wApprox = vectorSub(aHatZ, ct1, true);
        mlDsaVectorInverseNtt(wApprox);
        int[][] w1Prime = useHint(sig.hint(), wApprox);

        //Hash signer's commitment
        hash.update(mu);
        hash.update(simpleBitPack(wCoeffSize, w1Prime));
        byte[] cTildePrime = hash.squeeze(lambda/4);

        //Check verify conditions
        boolean hashEq = MessageDigest.isEqual(sig.commitmentHash(), cTildePrime);
        return !zNorm && hashEq;
    }

    /*
    Data conversion functions in Section 7.1 of specification
     */

    // Bit-pack the t1 and w1 vector into a byte array.
    // The coefficients of the polynomials in the vector should be
    // nonnegative and less than 2^bitsPerCoeff .
    public byte[] simpleBitPack(int bitsPerCoeff, int[][] vector) {
        byte[] result = new byte[(mlDsa_k * ML_DSA_N * bitsPerCoeff) / 8];
        int acc = 0;
        int shift = 0;
        int i = 0;
        for (int[] poly : vector) {
            for (int m = 0; m < ML_DSA_N; m++) {
                acc += (poly[m] << shift);
                shift += bitsPerCoeff;
                while (shift >= 8) {
                    result[i++] = (byte) acc;
                    acc >>= 8;
                    shift -= 8;
                }
            }
        } // Shift must now be 0 so we have all output bits
        return result;
    }

    public void bitPack(int[][] vector, int bitsPerCoeff, int maxValue,
            byte[] output, int offset) {
        int vecLen = vector.length;
        int acc = 0;
        int shift = 0;
        for (int[] poly : vector) {
            for (int m = 0; m < ML_DSA_N; m++) {
                acc += (maxValue - poly[m]) << shift;
                shift += bitsPerCoeff;
                while (shift >= 8) {
                    output[offset++] = (byte) acc;
                    acc >>= 8;
                    shift -= 8;
                }
            }
        }
    }

    //This is simpleBitUnpack from FIPS 204. Since it is only called on the
    //vector t1 we can optimize for that case
    public int[][] t1Unpack(byte[] v) {
        int[][] t1 = new int[mlDsa_k][ML_DSA_N];
        for (int i = 0; i < mlDsa_k; i++) {
            for (int j = 0; j < ML_DSA_N / 4; j++) {
                int tOffset = j*4;
                int vOffset = (i*320) + (j*5);
                t1[i][tOffset] = (v[vOffset] & 0xFF) +
                        ((v[vOffset+1] << 8) & 0x3FF);
                t1[i][tOffset+1] = ((v[vOffset+1] >> 2) & 0x3F) +
                        ((v[vOffset+2] << 6) & 0x3FF);
                t1[i][tOffset+2] = ((v[vOffset+2] >> 4) & 0xF) +
                        ((v[vOffset+3] << 4) & 0x3FF);
                t1[i][tOffset+3] = ((v[vOffset+3] >> 6) & 0x3) +
                        ((v[vOffset+4] << 2) & 0x3FF);
            }
        }
        return t1;
    }

    public int[][] bitUnpack(int[][] result, byte[] v, int offset, int dim,
                             int maxValue, int bitsPerCoeff) {

        switch (bitsPerCoeff) {
            case 3 -> { bitUnpackGeneral(result, v, offset, dim, maxValue, 3); }
            case 4 -> { bitUnpackGeneral(result, v, offset, dim, maxValue, 4); }
            case 13 -> { bitUnpackGeneral(result, v, offset, dim,  maxValue, 13); }
            case 18 -> { bitUnpack18(result, v, offset, dim, maxValue); }
            case 20 -> { bitUnpack20(result, v, offset, dim, maxValue); }
            default -> throw new RuntimeException(
                "Wrong bitsPerCoeff value in bitUnpack (" + bitsPerCoeff + ").");
        }
        return result;
    }
    public void bitUnpackGeneral(int[][] result,
            byte[] v, int offset, int dim, int maxValue, int bitsPerCoeff) {

        int mask = (1 << bitsPerCoeff) - 1;
        int top = 0;
        int shift = 0;
        int acc = 0;
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                while (top - shift < bitsPerCoeff) {
                    acc += ((v[offset++] & 0xff) << top);
                    top += 8;
                }
                result[i][j] = maxValue - ((acc >> shift) & mask);
                shift += bitsPerCoeff;
                while (shift >= 8) {
                    top -= 8;
                    shift -= 8;
                    acc >>>= 8;
                }
            }
        }
    }
    public void bitUnpack18(int [][] result, byte[] v, int offset,
                            int dim, int maxValue) {

        int vIndex = offset;
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < ML_DSA_N; j += 4) {
                result[i][j] = maxValue - ((v[vIndex] & 0xff) +
                        ((v[vIndex + 1] & 0xff) << 8) +
                        ((v[vIndex + 2] & 0x3) << 16));
                result[i][j + 1] = maxValue - (((v[vIndex + 2] >> 2) & 0x3f) +
                        ((v[vIndex + 3] & 0xff) << 6) +
                        ((v[vIndex + 4] & 0xf) << 14));
                result[i][j + 2] = maxValue - (((v[vIndex + 4] >> 4) & 0xf) +
                        ((v[vIndex + 5] & 0xff) << 4) +
                        ((v[vIndex + 6] & 0x3f) << 12));
                result[i][j + 3] = maxValue - (((v[vIndex + 6] >> 6) & 0x3) +
                        ((v[vIndex + 7] & 0xff) << 2) +
                        ((v[vIndex + 8] & 0xff) << 10));
                vIndex += 9;
            }
        }
    }

    public void bitUnpack20(int[][] result, byte[] v, int offset,
                            int dim, int maxValue) {
        int vIndex = offset;

        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < ML_DSA_N; j += 2) {
                result[i][j] = maxValue - ((v[vIndex] & 0xff) +
                        ((v[vIndex + 1] & 0xff) << 8) +
                        ((v[vIndex + 2] & 0xf) << 16));
                result[i][j + 1] = maxValue - (((v[vIndex + 2] >> 4) & 0xf) +
                        ((v[vIndex + 3] & 0xff) << 4) +
                        ((v[vIndex + 4] & 0xff) << 12));
                vIndex += 5;
            }
        }
    }

    private void hintBitPack(boolean[][] h, byte[] buffer, int offset) {
        int idx = 0;
        for (int i = 0; i < mlDsa_k; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                if (h[i][j]) {
                    buffer[offset + idx] = (byte)j;
                    idx++;
                }
            }
            buffer[offset + omega + i] = (byte)idx;
        }
    }

    private boolean[][] hintBitUnpack(byte[] y, int offset) {
        boolean[][] h = new boolean[mlDsa_k][ML_DSA_N];
        int idx = 0;
        for (int i = 0; i < mlDsa_k; i++) {
            int j = y[offset + omega + i];
            if (j < idx || j > omega) {
                return null;
            }
            int first = idx;
            while (idx < j) {
                if (idx > first) {
                    if ((y[offset + idx - 1] & 0xff) >= (y[offset + idx] & 0xff)) {
                        return null;
                    }
                }
                int hintIndex = y[offset + idx] & 0xff;
                h[i][hintIndex] = true;
                idx++;
            }
        }

        while (idx < omega) {
            if (y[offset + idx] != 0) {
                return null;
            }
            idx++;
        }
        return h;
    }

    /*
    Encoding functions as specified in Section 7.2 of the specification
     */

    public byte[] pkEncode(ML_DSA_PublicKey key) {
        byte[] t1Packed = simpleBitPack(10, key.t1);
        byte[] publicKeyBytes = new byte[A_SEED_LEN + t1Packed.length];
        System.arraycopy(key.rho, 0, publicKeyBytes, 0, A_SEED_LEN);
        System.arraycopy(t1Packed, 0, publicKeyBytes, A_SEED_LEN, t1Packed.length);

        return publicKeyBytes;
    }

    public ML_DSA_PublicKey pkDecode(byte[] pk) {
        byte[] rho = Arrays.copyOfRange(pk, 0, A_SEED_LEN);
        byte[] v = Arrays.copyOfRange(pk, A_SEED_LEN, pk.length);
        int[][] t1 = t1Unpack(v);
        return new ML_DSA_PublicKey(rho, t1);
    }

    public byte[] skEncode(ML_DSA_PrivateKey key) {

        byte[] skBytes = new byte[A_SEED_LEN + K_LEN + key.tr.length +
                s1PackedLength + s2PackedLength + t0PackedLength];

        int pos = 0;
        System.arraycopy(key.rho, 0, skBytes, pos, A_SEED_LEN);
        pos += A_SEED_LEN;
        System.arraycopy(key.k, 0, skBytes, pos, K_LEN);
        pos += K_LEN;
        System.arraycopy(key.tr, 0, skBytes, pos, TR_LEN);
        pos += TR_LEN;

        bitPack(key.s1, s1s2CoeffSize, eta, skBytes, pos);
        pos += s1PackedLength;
        bitPack(key.s2, s1s2CoeffSize, eta, skBytes, pos);
        pos += s2PackedLength;
        bitPack(key.t0, T0_COEFF_SIZE, 1 << 12, skBytes, pos);

        return skBytes;
    }

    public ML_DSA_PrivateKey skDecode(byte[] sk) {
        byte[] rho = new byte[A_SEED_LEN];
        System.arraycopy(sk, 0, rho, 0, A_SEED_LEN);

        byte[] k = new byte[K_LEN];
        System.arraycopy(sk, A_SEED_LEN, k, 0, K_LEN);

        byte[] tr = new byte[TR_LEN];
        System.arraycopy(sk, A_SEED_LEN + K_LEN, tr, 0, TR_LEN);

        //Parse s1
        int start = A_SEED_LEN + K_LEN + TR_LEN;
        int end = start + (32 * mlDsa_l * s1s2CoeffSize);
        int[][] s1 = new int[mlDsa_l][ML_DSA_N];
        bitUnpack(s1, sk, start, mlDsa_l, eta, s1s2CoeffSize);

        //Parse s2
        start = end;
        end += 32 * s1s2CoeffSize * mlDsa_k;
        int[][] s2 = new int[mlDsa_k][ML_DSA_N];
        bitUnpack(s2, sk, start, mlDsa_k, eta, s1s2CoeffSize);

        //Parse t0
        start = end;
        int[][] t0 = new int[mlDsa_k][ML_DSA_N];
        bitUnpack(t0, sk, start, mlDsa_k, 1 << 12, T0_COEFF_SIZE);

        return new ML_DSA_PrivateKey(rho, k, tr, s1, s2, t0);
    }

    public byte[] sigEncode(ML_DSA_Signature sig) {
        int cSize = lambda / 4;
        int zSize = mlDsa_l * 32 * (1 + gamma1Bits);

        byte[] sigBytes = new byte[cSize + zSize + omega + mlDsa_k];

        System.arraycopy(sig.commitmentHash, 0, sigBytes, 0, cSize);
        bitPack(sig.response, gamma1Bits + 1, gamma1, sigBytes, cSize);
        hintBitPack(sig.hint, sigBytes, cSize + zSize);

        return sigBytes;
    }

    public ML_DSA_Signature sigDecode(byte[] sig) throws SignatureException {

        int cSize = lambda / 4;
        int zSize = mlDsa_l * 32 * (1 + gamma1Bits);

        int sigLen = cSize + zSize + omega + mlDsa_k;
        if (sig.length != sigLen) {
            throw new SignatureException("Incorrect signature length");
        }

        //Decode cTilde
        byte[] cTilde = Arrays.copyOfRange(sig, 0, lambda/4);

        //Decode z
        int start = cSize;
        int end = start + zSize;
        int[][] z = new int[mlDsa_l][ML_DSA_N];
        bitUnpack(z, sig, start, mlDsa_l, gamma1, gamma1Bits + 1);

        //Decode h
        start = end;
        boolean[][] h = hintBitUnpack(sig, start);
        if (h == null) {
            throw new SignatureException("Invalid hints encoding");
        }

        return new ML_DSA_Signature(cTilde, z, h);
    }

    /*
    Auxiliary functions defined in Section 7.3 of specification
     */

    private class Shake256Slicer {
        SHAKE256 xof;
        byte[] block;
        int byteOffset;
        int current;
        int bitsInCurrent;
        int bitsPerCall;
        int bitMask;

        Shake256Slicer(SHAKE256 xof, int bitsPerCall) {
            this.xof = xof;
            //BitsPerCall can only be 4 (when called from sampleS1S2),
            //or 8 (when called from sampleInBall)
            this.bitsPerCall = bitsPerCall;
            bitMask = (1 << bitsPerCall) - 1;
            current = 0;
            byteOffset = SHAKE256_BLOCK_SIZE;
            bitsInCurrent = 0;
            block = new byte[SHAKE256_BLOCK_SIZE];
        }

        void reset() {
            xof.reset();
            current = 0;
            byteOffset = SHAKE256_BLOCK_SIZE;
            bitsInCurrent = 0;
        }

        int squeezeBits() {
            while (bitsInCurrent < bitsPerCall) {
                if (byteOffset == SHAKE256_BLOCK_SIZE) {
                    xof.squeeze(block, 0, SHAKE256_BLOCK_SIZE);
                    byteOffset = 0;
                }
                current += ((block[byteOffset++] & 0xff) << bitsInCurrent);
                bitsInCurrent += 8;
            }
            int result = current & bitMask;
            current >>= bitsPerCall;
            bitsInCurrent -= bitsPerCall;
            return result;
        }
    }

    private void sampleInBall(int[] c, byte[] rho) {
        var xof = new SHAKE256(0);
        Shake256Slicer slicer = new Shake256Slicer(xof, 8);
        xof.update(rho);

        long parity = 0;
        for (int i = 0; i < 8; i++) {
            long sample = slicer.squeezeBits();
            parity |= sample << 8 * i;
        }

        Arrays.fill(c, 0);

        int k = 8;
        for (int i = 256 - tau; i < 256; i++) {
            //Get random index < i
            int j = slicer.squeezeBits();
            while (j > i) {
                j = slicer.squeezeBits();
            }

            //Swap c[i] and c[j], set c[j] based on parity
            c[i] = c[j];
            c[j] = (int) (1 - 2 * (parity & 1));
            parity >>= 1;
        }
    }

    int[][][] generateA(byte[] seed) {
        int blockSize = 168;  // the size of one block of SHAKE128 output
        var xof = new SHAKE128(0);
        byte[] xofSeed = new byte[A_SEED_LEN + 2];
        System.arraycopy(seed, 0, xofSeed, 0, A_SEED_LEN);
        int[][][] a = new int[mlDsa_k][mlDsa_l][];

        for (int i = 0; i < mlDsa_k; i++) {
            for (int j = 0; j < mlDsa_l; j++) {
                xofSeed[A_SEED_LEN] = (byte) j;
                xofSeed[A_SEED_LEN + 1] = (byte) i;
                xof.reset();
                xof.update(xofSeed);

                byte[] rawAij = new byte[blockSize];
                int[] aij = new int[ML_DSA_N];
                int ofs = 0;
                int rawOfs = blockSize;
                int tmp;
                while (ofs < ML_DSA_N) {
                    if (rawOfs == blockSize) {
                        // works because 3 divides blockSize (=168)
                        xof.squeeze(rawAij, 0, blockSize);
                        rawOfs = 0;
                    }
                    tmp = (rawAij[rawOfs] & 0xFF) +
                            ((rawAij[rawOfs + 1] & 0xFF) << 8) +
                            ((rawAij[rawOfs + 2] & 0x7F) << 16);
                    rawOfs += 3;
                    if (tmp < ML_DSA_Q) {
                        aij[ofs] = tmp;
                        ofs++;
                    }
                }
                a[i][j] = aij;
            }
        }
        return a;
    }

    private void sampleS1S2(int[][] s1, int[][] s2, SHAKE256 xof, byte[] rhoPrime) {
        byte[] seed = new byte[S1S2_SEED_LEN + 2];
        System.arraycopy(rhoPrime, 0, seed, 0, S1S2_SEED_LEN);

        Shake256Slicer slicer = new Shake256Slicer(xof, 4);
        for (int i = 0; i < mlDsa_l; i++) {
            seed[S1S2_SEED_LEN] = (byte) i;
            seed[S1S2_SEED_LEN + 1] = 0;
            slicer.reset();
            xof.update(seed);
            if (eta == 2) {
                for (int j = 0; j < ML_DSA_N; j++) {
                    int sample;
                    do {
                        sample = slicer.squeezeBits();
                    } while (sample > 14);
                    // 2 - sample mod 5
                    s1[i][j] = eta - sample + (205 * sample >> 10) * 5;
                }
            } else { // eta == 4
                for (int j = 0; j < ML_DSA_N; j++) {
                    int sample;
                    do {
                        sample = slicer.squeezeBits();
                    } while (sample > 2 * eta);
                    s1[i][j] = eta - sample;
                }
            }
        }
        for (int i = 0; i < mlDsa_k; i++) {
            seed[S1S2_SEED_LEN] = (byte) (mlDsa_l + i);
            seed[S1S2_SEED_LEN + 1] = 0;
            slicer.reset();
            xof.update(seed);
            if (eta == 2) {
                for (int j = 0; j < ML_DSA_N; j++) {
                    int sample;
                    do {
                        sample = slicer.squeezeBits();
                    } while (sample > 14);
                    s2[i][j] = eta - sample + (205 * sample >> 10) * 5;
                }
            } else {
                for (int j = 0; j < ML_DSA_N; j++) {
                    int sample;
                    do {
                        sample = slicer.squeezeBits();
                    } while (sample > 2 * eta);
                    s2[i][j] = eta - sample;
                }
            }
        }
    }

    private void expandMask(int[][] result, byte[] rho, int mu) {
        var xof = new SHAKE256(0);

        int c = 1 + gamma1Bits;
        byte[] v = new byte[mlDsa_l * 32 * c];
        for (int r = 0; r < mlDsa_l; r++) {
            int a = mu + r;
            byte[] n = {(byte) a, (byte) (a >> 8)};

            xof.update(rho);
            xof.update(n);
            xof.squeeze(v, r * 32 * c, 32 * c);
            xof.reset();
        }
        bitUnpack(result, v, 0, mlDsa_l, gamma1, c);
    }

    /*
    Auxiliary functions defined in section 7.4 of specification
     */

    private void power2Round(int[][] input, int[][] lowPart, int[][] highPart) {
        for (int i = 0; i < mlDsa_k; i++) {
            for (int m = 0; m < ML_DSA_N; m++) {
                int rplus = input[i][m];
                int r0 = input[i][m] & D_MASK;
                int r00 = (1 << (ML_DSA_D - 1)) - r0 ; // 2^d/2 - r+
                r0 -= (r00 >> 31) & (1 << ML_DSA_D); //0 if r+ < 2^d/2
                lowPart[i][m] = r0;
                highPart[i][m] = (rplus - r0) >> ML_DSA_D;
            }
        }
    }

    private void decompose(int[][] input, int[][] lowPart, int[][] highPart) {
        int multiplier = (gamma2 == 95232 ? 22 : 8);
        for (int i = 0; i < mlDsa_k; i++) {
            ML_DSA.mlDsaDecomposePoly(input[i], lowPart[i],
                    highPart[i], gamma2 * 2, multiplier);
        }
    }

    private int[][] highBits(int[][] input) {
        int[][] lowPart = new int[mlDsa_k][ML_DSA_N];
        int[][] highPart = new int[mlDsa_k][ML_DSA_N];
        decompose(input, lowPart, highPart);
        return highPart;
    }

    //Creates the hint polynomial and returns its hamming weight
    private int makeHint(boolean[][] res, int[][] z, int[][] r) {
        int hammingWeight = 0;
        int[][] r1 = highBits(r);
        int[][] v1 = highBits(z);
        for (int i = 0; i < mlDsa_k; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                if (r1[i][j] != v1[i][j]) {
                    res[i][j] = true;
                    hammingWeight++;
                } else {
                    res[i][j] = false;
                }
            }
        }
        return hammingWeight;
    }

    private int[][] useHint(boolean[][] h, int[][] r) {
        int m = (ML_DSA_Q - 1) / (2*gamma2);
        int[][] lowPart = new int[mlDsa_k][ML_DSA_N];
        int[][] highPart = new int[mlDsa_k][ML_DSA_N];
        decompose(r, lowPart, highPart);

        for (int i = 0; i < mlDsa_k; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                if (h[i][j]) {
                    highPart[i][j] += lowPart[i][j] > 0 ? 1 : -1;
                }
                highPart[i][j] = ((highPart[i][j] % m) + m) % m;
            }
        }
        return highPart;
    }

    /*
    NTT functions as specified in Section 7.5 of specification
    */

    public static int[] mlDsaNtt(int[] coeffs) {
        int dimension = ML_DSA_N;
        int m = 0;
        for (int l = dimension / 2; l > 0; l /= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = montMul(MONT_ZETAS_FOR_NTT[m], coeffs[j + l]);
                    coeffs[j + l] = coeffs[j] - tmp;
                    coeffs[j] = coeffs[j] + tmp;
                }
                m++;
            }
        }
        montMulByConstant(coeffs,  MONT_R_MOD_Q);
        return coeffs;
    }

    public static int[] mlDsaInverseNtt(int[] coeffs) {
        int dimension = ML_DSA_N;
        int m = 0;
        for (int l = 1; l < dimension; l *= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = coeffs[j];
                    coeffs[j] = (tmp + coeffs[j + l]);
                    coeffs[j + l] = montMul(tmp - coeffs[j + l],
                            MONT_ZETAS_FOR_INVERSE_NTT[m]);
                }
                m++;
            }
        }
        montMulByConstant(coeffs, MONT_DIM_INVERSE);
        return coeffs;
    }

    void mlDsaVectorNtt(int[][] vector) {
        for (int[] ints : vector) {
            mlDsaNtt(ints);
        }
    }

    void mlDsaVectorInverseNtt(int[][] vector) {
        for (int[] ints : vector) {
            mlDsaInverseNtt(ints);
        }
    }

    public static void mlDsaNttMultiply(int[] product, int[] coeffs1, int[] coeffs2) {
        for (int i = 0; i < ML_DSA_N; i++) {
            product[i] = montMul(coeffs1[i], toMont(coeffs2[i]));
        }
    }

    public static void montMulByConstant(int[] coeffs, int constant) {
        for (int i = 0; i < ML_DSA_N; i++) {
            coeffs[i] = montMul((coeffs[i]), constant);
        }
    }

    public static void mlDsaDecomposePoly(int[] input, int[] lowPart, int[] highPart,
                                         int twoGamma2, int multiplier) {
        for (int m = 0; m < ML_DSA_N; m++) {
            int rplus = input[m];
            rplus = rplus - ((rplus + 5373807) >> 23) * ML_DSA_Q;
            rplus = rplus + ((rplus >> 31) & ML_DSA_Q);
            int r0 = rplus - ((rplus * multiplier) >> 22) * twoGamma2;
            r0 -= (((twoGamma2 - r0) >> 22) & twoGamma2);
            r0 -= (((twoGamma2 / 2 - r0) >> 31) & twoGamma2);
            int r1 = rplus - r0 - (ML_DSA_Q - 1);
            r1 = (r1 | (-r1)) >> 31;
            r0 += ~r1;
            r1 = r1 & ((rplus - r0) / twoGamma2);
            lowPart[m] = r0;
            highPart[m] = r1;
        }
    }

    private void matrixVectorPointwiseMultiply(int[][] res, int[][][] matrix,
                                               int[][] vector) {

        int resulti[] = new int[ML_DSA_N];
        int[] product = new int[ML_DSA_N];
        for (int i = 0; i < mlDsa_k; i++) {
            for (int m = 0; m < ML_DSA_N; m++) {
                resulti[m] = 0;
            }
            for (int j = 0; j < mlDsa_l; j++) {
                mlDsaNttMultiply(product, matrix[i][j], vector[j]);
                for (int m = 0; m < ML_DSA_N; m++) {
                    resulti[m] += product[m];
                }
            }
            for (int m = 0; m < ML_DSA_N; m++) {
                res[i][m] = montMul(resulti[m], MONT_R_MOD_Q);
            }
        }
    }

    private void nttConstMultiply(int[][] res, int[] a, int[][] b) {
        for (int i = 0; i < b.length; i++) {
            mlDsaNttMultiply(res[i], a, b[i]);
        }
    }

    private int[][] vectorConstMul(int c, int[][] vec) {
        int[][] res = new int[vec.length][vec[0].length];
        for (int i = 0; i < vec.length; i++) {
            for (int j = 0; j < vec[0].length; j++) {
                res[i][j] = montMul(c, toMont(vec[i][j]));
            }
        }
        return res; // -q < res[i][j] < q
    }

    // Adds two vectors of polynomials
    // The coefficients in the input should be between -MONT_Q and MONT_Q .
    // The coefficients in the output will be nonnegative and less than MONT_Q
    int[][] vectorAddPos(int[][] vec1, int[][] vec2) {
        int dim = vec1.length;
        int[][] result = new int[dim][ML_DSA_N];
        for (int i = 0; i < dim; i++) {
            for (int m = 0; m < ML_DSA_N; m++) {
                int r = vec1[i][m] + vec2[i][m]; // -2 * MONT_Q < r < 2 * MONT_Q
                r += (((r >> 31) & (2 * MONT_Q)) - MONT_Q); // -MONT_Q < r < MONT_Q
                r += ((r >> 31) & MONT_Q); // 0 <= r < MONT_Q
                result[i][m] = r;
            }
        }
        return result;
    }

    int[][] vectorAdd(int[][] result, int[][] vec1, int[][] vec2) {
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                int tmp = vec1[i][j] + vec2[i][j];
                result[i][j] = tmp;
            }
        }
        return result;
    }

    int[][] vectorSub(int[][] vec1, int[][] vec2, boolean needsAdjustment) {
        int dim = vec1.length;
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                int tmp = vec1[i][j] - vec2[i][j];
                if (needsAdjustment) {
                    if (tmp <= -ML_DSA_Q) {
                        tmp += ML_DSA_Q;
                    } else if (tmp >= ML_DSA_Q) {
                        tmp -= ML_DSA_Q;
                    }
                }
                vec1[i][j] = tmp;
            }
        }
        return vec1;
    }

    //Precondition: 2^-31 <= r1 <= 2^31 - 5 * 2^20, and bound < q - 5234431
    //Computes whether the infinity norm of a vector is >= bound
    boolean vectorNormBound(int[][] vec, int bound) {
        boolean res = false;
        for (int i = 0; i < vec.length; i++) {
            for (int j = 0; j < ML_DSA_N; j++) {
                int r1 = vec[i][j];
                r1 = r1 - ((r1 + (5  << 20)) >> 23) * ML_DSA_Q;
                r1 = r1 - ((r1 >> 31) & r1) * 2;
                res |= (r1 >= bound);
            }
        }
        return res;
    }

    // precondition: -2^31 * MONT_Q <= a, b < 2^31, -2^31 < a * b < 2^31 * MONT_Q
    // computes a * b * 2^-32 mod MONT_Q
    // the result is greater than -MONT_Q and less than MONT_Q
    private static int montMul(int b, int c) {
        long a = (long) b * (long) c;
        int aHigh = (int) (a >> MONT_R_BITS);
        int aLow = (int) a;
        int m = MONT_Q_INV_MOD_R * aLow; // signed low product

        // subtract signed high product
        return (aHigh - (int) (((long)m * MONT_Q) >> MONT_R_BITS));
    }

    static int toMont(int a) {
        return montMul(a, MONT_R_SQUARE_MOD_Q);
    }
}
