package com.sun.crypto.provider;

import java.io.Serial;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.HexFormat;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import sun.security.provider.SHA3Parallel;
import sun.security.provider.SHAKE128;
import sun.security.provider.SHA3Parallel.Shake128Parallel;
import sun.security.provider.SHAKE256;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


public final class ML_KEM
        implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {
    public static class ML_KEM_EncapsulationKey implements PublicKey {
        @Serial
        private static final long serialVersionUID = 22;

        int size;
        byte[] keyBytes;

        public ML_KEM_EncapsulationKey(int size, byte[] keyBytes) {
            if ((size != 512) && (size != 768) && (size != 1024)) {
                throw new InvalidParameterException("wrong ML-KEM key size");
            }
            this.size = size;
            this.keyBytes = keyBytes.clone();
        }

        @Override
        public String getAlgorithm() {
            return "ML-KEM-" + size;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return keyBytes.clone();
        }
    }

    public static class ML_KEM_DecapsulationKey implements PrivateKey {
        @Serial
        private static final long serialVersionUID = 22;

        int size;
        byte[] keyBytes;

        public ML_KEM_DecapsulationKey(int size, byte[] keyBytes) {
            if ((size != 512) && (size != 768) && (size != 1024)) {
                throw new InvalidParameterException("wrong ML-KEM key size");
            }
            this.size = size;
            this.keyBytes = keyBytes.clone();
        }

        @Override
        public String getAlgorithm() {
            return "ML-KEM-" + size;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return keyBytes.clone();
        }
    }

    public static record MlKemAlgorithmParameterSpec
            (int size, boolean useIntrinsics, long flags)
            implements AlgorithmParameterSpec {}

    public static class KeyPairGenerator extends KeyPairGeneratorSpi {

        String name = "ML-KEM KeyPairGenerator";
        private int size = 0;
        private boolean useIntrinsics = false;
        private long flags = 0L;
        private SecureRandom random = null;

        public KeyPairGenerator() {
        }

        public KeyPairGenerator(int size, SecureRandom random) {
            if (random == null) {
                random = new SecureRandom();
            }
            this.size = size;
            this.random = random;
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            if (size != 0) {
                throw new InvalidParameterException(
                        "This generator has already been initialized.");
            }
            if (random == null) {
                random = new SecureRandom();
            }
            this.size = keysize;
            this.random = random;
        }
        public void initialize(AlgorithmParameterSpec params,
                                SecureRandom random) {
            if (size != 0) {
                throw new InvalidParameterException(
                        "This generator has already been initialized.");
            }
            if (!(params instanceof ML_KEM.MlKemAlgorithmParameterSpec mlKemParams)) {
                throw new InvalidParameterException(
                        "Bad AlgorithmParameterSpec.");
            }

            if (random == null) {
                random = new SecureRandom();
            }
            this.size = mlKemParams.size;
            this.useIntrinsics = mlKemParams.useIntrinsics;
            this.flags = mlKemParams.flags;
            this.random = random;
        }

        @Override
        public KeyPair generateKeyPair() {
            byte[] seed = new byte[32];
            random.nextBytes(seed);
            byte[] z = new byte[32];
            random.nextBytes(z);
            KeyPair kp;
            ML_KEM mlKem = new ML_KEM(size, useIntrinsics, flags);
            try {
                kp = mlKem.generateKemKeyPair(seed, z);
            } catch (NoSuchAlgorithmException | DigestException e) {
                throw new RuntimeException("internal error", e);
            }
            return new KeyPair(
                    new ML_KEM_EncapsulationKey(size, kp.getPublic().getEncoded()),
                    new ML_KEM_DecapsulationKey(size, kp.getPrivate().getEncoded())
            );
        }

        public static class Gen512 extends KeyPairGenerator {
            public Gen512() {
                super(512, new SecureRandom());
            }
        }

        public static class Gen768 extends KeyPairGenerator {
            public Gen768() {
                super(768, new SecureRandom());
            }
        }

        public static class Gen1024 extends KeyPairGenerator {
            public Gen1024() {
                super(1024, new SecureRandom());
            }
        }
    }

    public static class Spi implements KEMSpi {

        public KEMSpi.EncapsulatorSpi engineNewEncapsulator(
                PublicKey encapsulationKey,
                AlgorithmParameterSpec spec,
                SecureRandom secureRandom)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (encapsulationKey == null) {
                throw new InvalidKeyException("input key is null");
            }
            if (!(encapsulationKey instanceof ML_KEM_EncapsulationKey)) {
                throw new InvalidKeyException("input key type is bad");
            }
            if (spec == null) {
                return from((ML_KEM_EncapsulationKey) encapsulationKey, null, secureRandom);
            } else {
                if (!(spec instanceof MlKemAlgorithmParameterSpec mlKemSpec)) {
                    throw new InvalidAlgorithmParameterException("Bad AlgorithmParameterSpec");
                }
                return from((ML_KEM_EncapsulationKey) encapsulationKey, mlKemSpec, secureRandom);
            }
        }

        public KEMSpi.DecapsulatorSpi engineNewDecapsulator(
                PrivateKey decapsulationKey,
                AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (decapsulationKey == null) {
                throw new InvalidKeyException("input key is null");
            }
            if (!(decapsulationKey instanceof ML_KEM_DecapsulationKey)) {
                throw new InvalidKeyException("input key type is bad");
            }
            if (spec == null) {
                return from((ML_KEM_DecapsulationKey) decapsulationKey, null);
            } else {
                if (!(spec instanceof MlKemAlgorithmParameterSpec mlKemSpec)) {
                    throw new InvalidAlgorithmParameterException("Bad AlgorithmParameterSpec");
                }
                return from((ML_KEM_DecapsulationKey) decapsulationKey, mlKemSpec);
            }
        }

    }

    private final int mlKem_size;
    private final int mlKem_k;
    private final int mlKem_eta1;
    private final int mlKem_eta2;

    private final int mlKem_du;
    private final int mlKem_dv;
    private final int encapsulationSize;
    private ML_KEM_EncapsulationKey encapsulationKey = null;
    private ML_KEM_DecapsulationKey decapsulationKey = null;
    private SecureRandom secureRandom = null;

    private static final int secretSize = 32;
    private boolean useIntrinsics = true;
    private long flags;
    public Statistics statistics = new Statistics();

    private static final int mlKem_q = 3329;
    private static final int mlKem_n = 256;

    // mlKemXofBlockLen + mlKemXofPad should be divisible by 192 as that is
    // the granularity of what the intrinsics for twelve2Sixteen() can deal with
    private static final int mlKemXofBlockLen = 168; // the block length for SHAKE128
    private static final int mlKemXofPad = 24;
    private static final int montRBits = 20;
    private static final int montQ = 3329;
    private static final int montRSquareModQ = 152;
    private static final int montQInvModR = 586497;

    // toMont((mlKem_n / 2)^-1 mod mlKem_q) using R = 2^montRbits
    private static final int montDimHalfInverse = 1534;
    private static final int mlKemBarrettMultiplier = 20159;
    private static final int mlKemBarrettShift = 26;
    private static final int[] montZetasForNtt = new int[]{
            1188, 914, -969, 585, -551, 1263, -97, 593,
            -35, -1400, -417, -1253, 742, -281, 185, -819,
            -1226, 895, -530, 52, 25, 1000, 1249, -909,
            -373, -1604, -259, -1369, -82, 49, 1496, -406,
            445, 1155, -405, -714, 553, -1183, -1401, 1598,
            -128, 1538, -669, 744, 1382, -1313, 201, -332,
            -1440, -1007, -36, -1617, 567, -623, 1429, 290,
            -1269, -825, -1613, 510, -395, 845, -426, -1003,
            222, -1107, 172, -42, 620, 1497, -1649, 94,
            -595, -497, -431, -1327, -702, -1448, -184, -607,
            -868, -1430, 977, 884, 425, 355, 1259, 1192,
            317, -636, -1074, 30, -1394, 833, -1200, -244,
            907, -339, -227, 1178, -586, -137, -514, 534,
            1153, -486, -1386, -668, 191, 982, 88, 1014,
            -1177, -474, -612, -857, -348, -604, 990, 1601,
            -1599, -709, -789, -1317, -57, 1049, -584
    };

    private static final short[] montZetasForVectorNttArr = new short[]{
            // level 0
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            -758, -758, -758, -758, -758, -758, -758, -758,
            // level 1
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -359, -359, -359, -359, -359, -359, -359, -359,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            -1517, -1517, -1517, -1517, -1517, -1517, -1517, -1517,
            // level 2
            1493, 1493, 1493, 1493, 1493, 1493, 1493, 1493,
            1493, 1493, 1493, 1493, 1493, 1493, 1493, 1493,
            1493, 1493, 1493, 1493, 1493, 1493, 1493, 1493,
            1493, 1493, 1493, 1493, 1493, 1493, 1493, 1493,
            1422, 1422, 1422, 1422, 1422, 1422, 1422, 1422,
            1422, 1422, 1422, 1422, 1422, 1422, 1422, 1422,
            1422, 1422, 1422, 1422, 1422, 1422, 1422, 1422,
            1422, 1422, 1422, 1422, 1422, 1422, 1422, 1422,
            287, 287, 287, 287, 287, 287, 287, 287,
            287, 287, 287, 287, 287, 287, 287, 287,
            287, 287, 287, 287, 287, 287, 287, 287,
            287, 287, 287, 287, 287, 287, 287, 287,
            202, 202, 202, 202, 202, 202, 202, 202,
            202, 202, 202, 202, 202, 202, 202, 202,
            202, 202, 202, 202, 202, 202, 202, 202,
            202, 202, 202, 202, 202, 202, 202, 202,
            // level 3
            -171, -171, -171, -171, -171, -171, -171, -171,
            -171, -171, -171, -171, -171, -171, -171, -171,
            622, 622, 622, 622, 622, 622, 622, 622,
            622, 622, 622, 622, 622, 622, 622, 622,
            1577, 1577, 1577, 1577, 1577, 1577, 1577, 1577,
            1577, 1577, 1577, 1577, 1577, 1577, 1577, 1577,
            182, 182, 182, 182, 182, 182, 182, 182,
            182, 182, 182, 182, 182, 182, 182, 182,
            962, 962, 962, 962, 962, 962, 962, 962,
            962, 962, 962, 962, 962, 962, 962, 962,
            -1202, -1202, -1202, -1202, -1202, -1202, -1202, -1202,
            -1202, -1202, -1202, -1202, -1202, -1202, -1202, -1202,
            -1474, -1474, -1474, -1474, -1474, -1474, -1474, -1474,
            -1474, -1474, -1474, -1474, -1474, -1474, -1474, -1474,
            1468, 1468, 1468, 1468, 1468, 1468, 1468, 1468,
            1468, 1468, 1468, 1468, 1468, 1468, 1468, 1468,
            // level 4
            573, 573, 573, 573, 573, 573, 573, 573,
            -1325, -1325, -1325, -1325, -1325, -1325, -1325, -1325,
            264, 264, 264, 264, 264, 264, 264, 264,
            383, 383, 383, 383, 383, 383, 383, 383,
            -829, -829, -829, -829, -829, -829, -829, -829,
            1458, 1458, 1458, 1458, 1458, 1458, 1458, 1458,
            -1602, -1602, -1602, -1602, -1602, -1602, -1602, -1602,
            -130, -130, -130, -130, -130, -130, -130, -130,
            -681, -681, -681, -681, -681, -681, -681, -681,
            1017, 1017, 1017, 1017, 1017, 1017, 1017, 1017,
            732, 732, 732, 732, 732, 732, 732, 732,
            608, 608, 608, 608, 608, 608, 608, 608,
            -1542, -1542, -1542, -1542, -1542, -1542, -1542, -1542,
            411, 411, 411, 411, 411, 411, 411, 411,
            -205, -205, -205, -205, -205, -205, -205, -205,
            -1571, -1571, -1571, -1571, -1571, -1571, -1571, -1571,
            // level 5
            1223, 1223, 1223, 1223, 652, 652, 652, 652,
            -552, -552, -552, -552, 1015, 1015, 1015, 1015,
            -1293, -1293, -1293, -1293, 1491, 1491, 1491, 1491,
            -282, -282, -282, -282, -1544, -1544, -1544, -1544,
            516, 516, 516, 516, -8, -8, -8, -8,
            -320, -320, -320, -320, -666, -666, -666, -666,
            1711, 1711, 1711, 1711, -1162, -1162, -1162, -1162,
            126, 126, 126, 126, 1469, 1469, 1469, 1469,
            -853, -853, -853, -853, -90, -90, -90, -90,
            -271, -271, -271, -271, 830, 830, 830, 830,
            107, 107, 107, 107, -1421, -1421, -1421, -1421,
            -247, -247, -247, -247, -951, -951, -951, -951,
            -398, -398, -398, -398, 961, 961, 961, 961,
            -1508, -1508, -1508, -1508, -725, -725, -725, -725,
            448, 448, 448, 448, -1065, -1065, -1065, -1065,
            677, 677, 677, 677, -1275, -1275, -1275, -1275,
            // level 6
            -1103, -1103, 430, 430, 555, 555, 843, 843,
            -1251, -1251, 871, 871, 1550, 1550, 105, 105,
            422, 422, 587, 587, 177, 177, -235, -235,
            -291, -291, -460, -460, 1574, 1574, 1653, 1653,
            -246, -246, 778, 778, 1159, 1159, -147, -147,
            -777, -777, 1483, 1483, -602, -602, 1119, 1119,
            -1590, -1590, 644, 644, -872, -872, 349, 349,
            418, 418, 329, 329, -156, -156, -75, -75,
            817, 817, 1097, 1097, 603, 603, 610, 610,
            1322, 1322, -1285, -1285, -1465, -1465, 384, 384,
            -1215, -1215, -136, -136, 1218, 1218, -1335, -1335,
            -874, -874, 220, 220, -1187, -1187, 1670, 1670,
            -1185, -1185, -1530, -1530, -1278, -1278, 794, 794,
            -1510, -1510, -854, -854, -870, -870, 478, 478,
            -108, -108, -308, -308, 996, 996, 991, 991,
            958, 958, -1460, -1460, 1522, 1522, 1628, 1628
    };
    private static final int[] montZetasForInverseNtt = new int[]{
            584, -1049, 57, 1317, 789, 709, 1599, -1601,
            -990, 604, 348, 857, 612, 474, 1177, -1014,
            -88, -982, -191, 668, 1386, 486, -1153, -534,
            514, 137, 586, -1178, 227, 339, -907, 244,
            1200, -833, 1394, -30, 1074, 636, -317, -1192,
            -1259, -355, -425, -884, -977, 1430, 868, 607,
            184, 1448, 702, 1327, 431, 497, 595, -94,
            1649, -1497, -620, 42, -172, 1107, -222, 1003,
            426, -845, 395, -510, 1613, 825, 1269, -290,
            -1429, 623, -567, 1617, 36, 1007, 1440, 332,
            -201, 1313, -1382, -744, 669, -1538, 128, -1598,
            1401, 1183, -553, 714, 405, -1155, -445, 406,
            -1496, -49, 82, 1369, 259, 1604, 373, 909,
            -1249, -1000, -25, -52, 530, -895, 1226, 819,
            -185, 281, -742, 1253, 417, 1400, 35, -593,
            97, -1263, 551, -585, 969, -914, -1188
    };

    private static final short[] montZetasForVectorInverseNttArr = new short[]{
            // level 0
            -1628, -1628, -1522, -1522, 1460, 1460, -958, -958,
            -991, -991, -996, -996, 308, 308, 108, 108,
            -478, -478, 870, 870, 854, 854, 1510, 1510,
            -794, -794, 1278, 1278, 1530, 1530, 1185, 1185,
            1659, 1659, 1187, 1187, -220, -220, 874, 874,
            1335, 1335, -1218, -1218, 136, 136, 1215, 1215,
            -384, -384, 1465, 1465, 1285, 1285, -1322, -1322,
            -610, -610, -603, -603, -1097, -1097, -817, -817,
            75, 75, 156, 156, -329, -329, -418, -418,
            -349, -349, 872, 872, -644, -644, 1590, 1590,
            -1119, -1119, 602, 602, -1483, -1483, 777, 777,
            147, 147, -1159, -1159, -778, -778, 246, 246,
            -1653, -1653, -1574, -1574, 460, 460, 291, 291,
            235, 235, -177, -177, -587, -587, -422, -422,
            -105, -105, -1550, -1550, -871, -871, 1251, 1251,
            -843, -843, -555, -555, -430, -430, 1103, 1103,
            // level 1
            1275, 1275, 1275, 1275, -677, -677, -677, -677,
            1065, 1065, 1065, 1065, -448, -448, -448, -448,
            725, 725, 725, 725, 1508, 1508, 1508, 1508,
            -961, -961, -961, -961, 398, 398, 398, 398,
            951, 951, 951, 951, 247, 247, 247, 247,
            1421, 1421, 1421, 1421, -107, -107, -107, -107,
            -830, -830, -830, -830, 271, 271, 271, 271,
            90, 90, 90, 90, 853, 853, 853, 853,
            -1469, -1469, -1469, -1469, -126, -126, -126, -126,
            1162, 1162, 1162, 1162, 1618, 1618, 1618, 1618,
            666, 666, 666, 666, 320, 320, 320, 320,
            8, 8, 8, 8, -516, -516, -516, -516,
            1544, 1544, 1544, 1544, 282, 282, 282, 282,
            -1491, -1491, -1491, -1491, 1293, 1293, 1293, 1293,
            -1015, -1015, -1015, -1015, 552, 552, 552, 552,
            -652, -652, -652, -652, -1223, -1223, -1223, -1223,
            // level 2
            1571, 1571, 1571, 1571, 1571, 1571, 1571, 1571,
            205, 205, 205, 205, 205, 205, 205, 205,
            -411, -411, -411, -411, -411, -411, -411, -411,
            1542, 1542, 1542, 1542, 1542, 1542, 1542, 1542,
            -608, -608, -608, -608, -608, -608, -608, -608,
            -732, -732, -732, -732, -732, -732, -732, -732,
            -1017, -1017, -1017, -1017, -1017, -1017, -1017, -1017,
            681, 681, 681, 681, 681, 681, 681, 681,
            130, 130, 130, 130, 130, 130, 130, 130,
            1602, 1602, 1602, 1602, 1602, 1602, 1602, 1602,
            -1458, -1458, -1458, -1458, -1458, -1458, -1458, -1458,
            829, 829, 829, 829, 829, 829, 829, 829,
            -383, -383, -383, -383, -383, -383, -383, -383,
            -264, -264, -264, -264, -264, -264, -264, -264,
            1325, 1325, 1325, 1325, 1325, 1325, 1325, 1325,
            -573, -573, -573, -573, -573, -573, -573, -573,
            // level 3
            -1468, -1468, -1468, -1468, -1468, -1468, -1468, -1468,
            -1468, -1468, -1468, -1468, -1468, -1468, -1468, -1468,
            1474, 1474, 1474, 1474, 1474, 1474, 1474, 1474,
            1474, 1474, 1474, 1474, 1474, 1474, 1474, 1474,
            1202, 1202, 1202, 1202, 1202, 1202, 1202, 1202,
            1202, 1202, 1202, 1202, 1202, 1202, 1202, 1202,
            -962, -962, -962, -962, -962, -962, -962, -962,
            -962, -962, -962, -962, -962, -962, -962, -962,
            -182, -182, -182, -182, -182, -182, -182, -182,
            -182, -182, -182, -182, -182, -182, -182, -182,
            -1577, -1577, -1577, -1577, -1577, -1577, -1577, -1577,
            -1577, -1577, -1577, -1577, -1577, -1577, -1577, -1577,
            -622, -622, -622, -622, -622, -622, -622, -622,
            -622, -622, -622, -622, -622, -622, -622, -622,
            171, 171, 171, 171, 171, 171, 171, 171,
            171, 171, 171, 171, 171, 171, 171, 171,
            // level 4
            -202, -202, -202, -202, -202, -202, -202, -202,
            -202, -202, -202, -202, -202, -202, -202, -202,
            -202, -202, -202, -202, -202, -202, -202, -202,
            -202, -202, -202, -202, -202, -202, -202, -202,
            -287, -287, -287, -287, -287, -287, -287, -287,
            -287, -287, -287, -287, -287, -287, -287, -287,
            -287, -287, -287, -287, -287, -287, -287, -287,
            -287, -287, -287, -287, -287, -287, -287, -287,
            -1422, -1422, -1422, -1422, -1422, -1422, -1422, -1422,
            -1422, -1422, -1422, -1422, -1422, -1422, -1422, -1422,
            -1422, -1422, -1422, -1422, -1422, -1422, -1422, -1422,
            -1422, -1422, -1422, -1422, -1422, -1422, -1422, -1422,
            -1493, -1493, -1493, -1493, -1493, -1493, -1493, -1493,
            -1493, -1493, -1493, -1493, -1493, -1493, -1493, -1493,
            -1493, -1493, -1493, -1493, -1493, -1493, -1493, -1493,
            -1493, -1493, -1493, -1493, -1493, -1493, -1493, -1493,
            // level 5
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            1517, 1517, 1517, 1517, 1517, 1517, 1517, 1517,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            359, 359, 359, 359, 359, 359, 359, 359,
            // level 6
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758,
            758, 758, 758, 758, 758, 758, 758, 758
    };

    private static final int[] montZetasForNttMult = new int[]{
            -1003, 1003, 222, -222, -1107, 1107, 172, -172,
            -42, 42, 620, -620, 1497, -1497, -1649, 1649,
            94, -94, -595, 595, -497, 497, -431, 431,
            -1327, 1327, -702, 702, -1448, 1448, -184, 184,
            -607, 607, -868, 868, -1430, 1430, 977, -977,
            884, -884, 425, -425, 355, -355, 1259, -1259,
            1192, -1192, 317, -317, -636, 636, -1074, 1074,
            30, -30, -1394, 1394, 833, -833, -1200, 1200,
            -244, 244, 907, -907, -339, 339, -227, 227,
            1178, -1178, -586, 586, -137, 137, -514, 514,
            534, -534, 1153, -1153, -486, 486, -1386, 1386,
            -668, 668, 191, -191, 982, -982, 88, -88,
            1014, -1014, -1177, 1177, -474, 474, -612, 612,
            -857, 857, -348, 348, -604, 604, 990, -990,
            1601, -1601, -1599, 1599, -709, 709, -789, 789,
            -1317, 1317, -57, 57, 1049, -1049, -584, 584
    };

    private static final short[] montZetasForVectorNttMultArr = new short[]{
            -1103, 1103, 430, -430, 555, -555, 843, -843,
            -1251, 1251, 871, -871, 1550, -1550, 105, -105,
            422, -422, 587, -587, 177, -177, -235, 235,
            -291, 291, -460, 460, 1574, -1574, 1653, -1653,
            -246, 246, 778, -778, 1159, -1159, -147, 147,
            -777, 777, 1483, -1483, -602, 602, 1119, -1119,
            -1590, 1590, 644, -644, -872, 872, 349, -349,
            418, -418, 329, -329, -156, 156, -75, 75,
            817, -817, 1097, -1097, 603, -603, 610, -610,
            1322, -1322, -1285, 1285, -1465, 1465, 384, -384,
            -1215, 1215, -136, 136, 1218, -1218, -1335, 1335,
            -874, 874, 220, -220, -1187, 1187, 1670, 1659,
            -1185, 1185, -1530, 1530, -1278, 1278, 794, -794,
            -1510, 1510, -854, 854, -870, 870, 478, -478,
            -108, 108, -308, 308, 996, -996, 991, -991,
            958, -958, -1460, 1460, 1522, -1522, 1628, -1628
    };

    public ML_KEM(int size) {
        switch (size) {
            case 512 -> {
                mlKem_k = 2;
                mlKem_eta1 = 3;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case 768 -> {
                mlKem_k = 3;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case 1024 -> {
                mlKem_k = 4;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 11;
                mlKem_dv = 5;
            }
            default -> throw new IllegalArgumentException(
                    "Bad size for ML_KEM-" + size);
        }
        mlKem_size = size;
        encapsulationSize = (mlKem_k * mlKem_du + mlKem_dv) * 32;
        useIntrinsics = false;
        flags = 127;
        statistics.setUseThis(flags);
    }

    public ML_KEM(int size, boolean useIntr, long flags) {
        switch (size) {
            case 512 -> {
                mlKem_k = 2;
                mlKem_eta1 = 3;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case 768 -> {
                mlKem_k = 3;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case 1024 -> {
                mlKem_k = 4;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 11;
                mlKem_dv = 5;
            }
            default -> throw new IllegalArgumentException(
                    "Bad size for ML_KEM-" + size);
        }
        mlKem_size = size;
        encapsulationSize = (mlKem_k * mlKem_du + mlKem_dv) * 32;
        useIntrinsics = useIntr;
        this.flags = flags;
        statistics.setUseThis(flags);
    }

    public void setIntrinsicFlags(boolean useIntr, long flags) {
        useIntrinsics = useIntr;
        this.flags = flags;
        statistics.setUseThis(flags);
    }

    static private KEMSpi.EncapsulatorSpi from(
            ML_KEM_EncapsulationKey pubKey, MlKemAlgorithmParameterSpec spec,
            SecureRandom secureRandom) {
        ML_KEM mlKem;
        if (spec != null) {
            if (pubKey.size != spec.size) {
                throw new InvalidParameterException("public key size does not match size in AlgorithmParameterSpec.");
            }
            mlKem = new ML_KEM(spec.size, spec.useIntrinsics, spec.flags);
        } else {
            mlKem = new ML_KEM(pubKey.size);
        }
        mlKem.encapsulationKey = pubKey;
        mlKem.secureRandom = secureRandom;
        return mlKem;
    }

    static private KEMSpi.DecapsulatorSpi from(
            ML_KEM_DecapsulationKey privKey,
            MlKemAlgorithmParameterSpec spec) {
        ML_KEM mlKem;
        if (spec != null) {
            if (privKey.size != spec.size) {
                throw new InvalidParameterException("private key size does not match size in AlgorithmParameterSpec.");
            }
            mlKem = new ML_KEM(spec.size, spec.useIntrinsics, spec.flags);
        } else {
            mlKem = new ML_KEM(privKey.size);
        }
        mlKem.decapsulationKey = privKey;
        return mlKem;
    }

    public record K_PKE_DecryptionKey(byte[] keyBytes) {
        static K_PKE_DecryptionKey from(ML_KEM_DecapsulationKey key) {
            return new K_PKE_DecryptionKey(key.keyBytes);
        }
    }

    public record K_PKE_KeyPair(
            K_PKE_DecryptionKey privateKey, ML_KEM_EncapsulationKey publicKey) {
    }

    public record K_PKE_CipherText(byte[] encryptedBytes) {
    }

    public int engineSecretSize() {
        return secretSize;
    }

    @Override
    public int engineEncapsulationSize() {
        return encapsulationSize;
    }

    public record ML_KEM_EncapsulateResult(
            K_PKE_CipherText cipherText, byte[] sharedSecret) {
    }

    @Override
    public KEM.Encapsulated engineEncapsulate(
            int from, int to, String algorithm) {
        if ((encapsulationKey == null) || (from != 0) || (to != 32)) {
            throw new UnsupportedOperationException();
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        ML_KEM_EncapsulateResult mlKemEncapsulateResult = null;
        try {
            mlKemEncapsulateResult = encapsulate(encapsulationKey, randomBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e); // should not happen
        }

        return new KEM.Encapsulated(
                new SecretKeySpec(
                        mlKemEncapsulateResult.sharedSecret,
                        from, to - from, algorithm),
                mlKemEncapsulateResult.cipherText.encryptedBytes, null);
    }

    @Override
    public SecretKey engineDecapsulate(
            byte[] encapsulation, int from, int to, String algorithm)
            throws DecapsulateException {
        if (decapsulationKey == null) {
            throw new UnsupportedOperationException();
        }
        var kpkeCiphertext = new K_PKE_CipherText(encapsulation);
        byte[] decapsulateResult = null;
        try {
            decapsulateResult =
                    decapsulate(decapsulationKey, kpkeCiphertext);
            if ((flags & 128) != 0) {
                statistics.printStat(0);
                statistics.printStat(1);
                statistics.printStat(2);
                statistics.printStat(3);
                statistics.printStat(4);
                statistics.printStat(5);
                statistics.printStat(6);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e); // should not happen
        }

        return new SecretKeySpec(
                decapsulateResult, from, to - from, algorithm);
    }


    public KeyPair generateKemKeyPair(
            byte[] seedForIndCpaGenerator, byte[] kem_z)
            throws NoSuchAlgorithmException, DigestException {
        long startTime = System.nanoTime();
        var mlKemH = MessageDigest.getInstance("SHA3-256");

        var indCpaKeyPair = generateK_PkeKeyPair(seedForIndCpaGenerator);
        long endTime = System.nanoTime();
        statistics.stats[Statistics.generateKemKeyPairStat_1].time += (endTime - startTime);
        statistics.stats[Statistics.generateKemKeyPairStat_1].nrCalls++;
        startTime = endTime;
        byte[] encapsKeyBytes = indCpaKeyPair.publicKey.keyBytes;
        byte[] indCpaPrivateKeyBytes = indCpaKeyPair.privateKey.keyBytes;
        byte[] decapsKeyBytes =
                new byte[encapsKeyBytes.length + indCpaPrivateKeyBytes.length + 64];
        System.arraycopy(indCpaPrivateKeyBytes, 0,
                decapsKeyBytes, 0, indCpaPrivateKeyBytes.length);
        System.arraycopy(encapsKeyBytes, 0,
                decapsKeyBytes, indCpaPrivateKeyBytes.length, encapsKeyBytes.length);
        mlKemH.update(encapsKeyBytes);
        mlKemH.digest(decapsKeyBytes,
                indCpaPrivateKeyBytes.length + encapsKeyBytes.length, 32);
        System.arraycopy(kem_z, 0,
                decapsKeyBytes,
                indCpaPrivateKeyBytes.length + encapsKeyBytes.length + 32, 32);

        var ccakp = new KeyPair(
                new ML_KEM_EncapsulationKey(mlKem_size, encapsKeyBytes),
                new ML_KEM_DecapsulationKey(mlKem_size, decapsKeyBytes));
        endTime = System.nanoTime();
        statistics.stats[Statistics.generateKemKeyPairStat_2].time += (endTime - startTime);
        statistics.stats[Statistics.generateKemKeyPairStat_2].nrCalls++;

        return ccakp;
    }

    private boolean isValidEncapsulationKey(ML_KEM_EncapsulationKey key) {
        byte[] keyBytes = key.keyBytes;
        if (keyBytes.length != mlKem_k * 384 + 32) {
            return false;
        }
        int x, y, z, a, b;
        for (int i = 0; i < mlKem_k * 384; i += 3) {
            x = keyBytes[i] & 0xFF;
            y = keyBytes[i + 1] & 0xFF;
            z = keyBytes[i + 2] & 0xFF;
            a = x + ((y & 0xF) << 8);
            b = (y >> 4) + (z << 4);
            if ((a >= mlKem_q) || (b >= mlKem_q)) {
                return false;
            }
        }
        return true;
    }

    public ML_KEM_EncapsulateResult encapsulate(
            ML_KEM_EncapsulationKey encapsulationKey, byte[] randomMessage)
            throws NoSuchAlgorithmException, InvalidKeyException {
        var mlKemH = MessageDigest.getInstance("SHA3-256");
        var mlKemG = MessageDigest.getInstance("SHA3-512");

        if (!isValidEncapsulationKey(encapsulationKey)) {
            throw new InvalidKeyException("Invalid encapsulation key");
        }
        mlKemH.update(encapsulationKey.keyBytes);
        mlKemG.update(randomMessage);
        mlKemG.update(mlKemH.digest());
        var kHatAndRandomCoins = mlKemG.digest();
        var randomCoins = Arrays.copyOfRange(kHatAndRandomCoins, 32, 64);
        var cipherText = kPkeEncrypt(encapsulationKey, randomMessage, randomCoins);
        Arrays.fill(randomMessage, (byte) 0);
        Arrays.fill(randomCoins, (byte) 0);
        byte[] sharedSecret = Arrays.copyOfRange(kHatAndRandomCoins, 0, 32);
        Arrays.fill(kHatAndRandomCoins, (byte) 0);

        return new ML_KEM_EncapsulateResult(cipherText, sharedSecret);
    }

    private boolean isValidDecapsulationKey(ML_KEM_DecapsulationKey key) {
        return (key.keyBytes.length == mlKem_k * 768 + 96);
    }

    private boolean isValidCipherText(K_PKE_CipherText cipherText) {
        return (cipherText.encryptedBytes.length == encapsulationSize);
    }

    public byte[] decapsulate(ML_KEM_DecapsulationKey decapsulationKey,
                              K_PKE_CipherText cipherText)
            throws NoSuchAlgorithmException,
            InvalidKeyException, DecapsulateException {
        int encode12PolyLen = 12 * mlKem_n / 8;
        var decapsKeyBytes = decapsulationKey.keyBytes;
        var mlKemG = MessageDigest.getInstance("SHA3-512");
        var mlKemJ = new SHAKE256(32);

        if (!isValidDecapsulationKey(decapsulationKey)) {
            throw new InvalidKeyException("Invalid decapsulation key");
        }
        if (!isValidCipherText(cipherText)) {
            throw new DecapsulateException("Invalid ciphertext");
        }
        byte[] kPkePrivateKeyBytes = new byte[mlKem_k * encode12PolyLen];
        System.arraycopy(decapsKeyBytes, 0,
                kPkePrivateKeyBytes, 0, kPkePrivateKeyBytes.length);
        byte[] encapsKeyBytes = new byte[mlKem_k * encode12PolyLen + 32];
        System.arraycopy(decapsKeyBytes, mlKem_k * encode12PolyLen,
                encapsKeyBytes, 0, encapsKeyBytes.length);
        var mCandidate = kPkeDecrypt(
                new K_PKE_DecryptionKey(kPkePrivateKeyBytes), cipherText);
        mlKemG.update(mCandidate);
        mlKemG.update(decapsKeyBytes, decapsKeyBytes.length - 64, 32);
        var kAndCoins = mlKemG.digest();
        var realResult = Arrays.copyOfRange(kAndCoins, 0, 32);
        var coins = Arrays.copyOfRange(kAndCoins, 32, 64);
        mlKemJ.update(decapsKeyBytes, decapsKeyBytes.length - 32, 32);
        mlKemJ.update(cipherText.encryptedBytes);
        var fakeResult = mlKemJ.digest();
        var computedCipherText = kPkeEncrypt(
                new ML_KEM_EncapsulationKey(mlKem_size, encapsKeyBytes), mCandidate, coins);

        // The rest of this method implements the following in constant time
        //
        //        if (Arrays.equals(cipherText.encryptedBytes,
        //                computedCipherText.encryptedBytes)) {
        //            return realResult;
        //        } else {
        //            return fakeResult;
        //        }

        int mask = 0;
        byte[] origCiphertestBytes = cipherText.encryptedBytes;
        byte[] compCipherTextBytes = computedCipherText.encryptedBytes;
        for (int i = 0; i < cipherText.encryptedBytes.length; i++) {
            mask |= (origCiphertestBytes[i] ^ compCipherTextBytes[i]);
        }
        mask = - (mask & 0xff); // sets mask to negative or 0
        mask >>= 31; // sets mask to all 1-bits or all 0-bits
        int notMask = ~mask;

        byte[] result = realResult;
        for (int i = 0; i < realResult.length; i ++) {
            result[i] = (byte)((notMask & realResult[i]) | (mask & fakeResult[i]));
        }

        return result;
    }

    private K_PKE_KeyPair generateK_PkeKeyPair(byte[] seed)
            throws NoSuchAlgorithmException {
        long startTime = System.nanoTime();
        var mlKemG = MessageDigest.getInstance("SHA3-512");
        var mlKemJ = new SHAKE256(64 * mlKem_eta1);

        mlKemG.update(seed);
        var rhoSigma = mlKemG.digest();
        var rho = Arrays.copyOfRange(rhoSigma, 0, 32);
        var sigma = Arrays.copyOfRange(rhoSigma, 32, 64);

        long endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_1].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_1].nrCalls++;
        startTime = endTime;

        var keyGenA = generateAparallel(rho, false);

        endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_2].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_2].nrCalls++;
        startTime = endTime;

        int keyGenN = 0;
        byte[] prfSeed = new byte[sigma.length + 1];
        System.arraycopy(sigma, 0, prfSeed, 0, sigma.length);
        byte[] cbdInput;
        short[][] keyGenS = new short[mlKem_k][];
        short[][] keyGenE = new short[mlKem_k][];
        for (int i = 0; i < mlKem_k; i++) {
            prfSeed[sigma.length] = (byte) (keyGenN++);
            mlKemJ.update(prfSeed);
            cbdInput = mlKemJ.digest();
            keyGenS[i] = centeredBinomialDistribution(mlKem_eta1, cbdInput);
        }
        for (int i = 0; i < mlKem_k; i++) {
            prfSeed[sigma.length] = (byte) (keyGenN++);
            mlKemJ.update(prfSeed);
            cbdInput = mlKemJ.digest();
            keyGenE[i] = centeredBinomialDistribution(mlKem_eta1, cbdInput);
        }

        endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_3].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_3].nrCalls++;
        startTime = endTime;

        short[][] keyGenSHat = mlKemVectorNTT(keyGenS);
        short[][] keyGenEHat = mlKemVectorNTT(keyGenE);

        endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_4].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_4].nrCalls++;
        startTime = endTime;

        short[][] keyGenTHat =
                mlKemMatrixVectorMuladd(keyGenA, keyGenSHat, keyGenEHat);

        endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_5].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_5].nrCalls++;
        startTime = endTime;

        byte[] pkEncoded = new byte[(mlKem_k * mlKem_n * 12) / 8 + rho.length];
        byte[] skEncoded = new byte[(mlKem_k * mlKem_n * 12) / 8];
        byte[] encodedPoly;
        for (int i = 0; i < mlKem_k; i++) {
            encodedPoly = encodePoly(12, keyGenTHat[i]);
            System.arraycopy(encodedPoly, 0,
                    pkEncoded, i * ((mlKem_n * 12) / 8), (mlKem_n * 12) / 8);
            encodedPoly = encodePoly(12, keyGenSHat[i]);
            System.arraycopy(encodedPoly, 0,
                    skEncoded, i * ((mlKem_n * 12) / 8), (mlKem_n * 12) / 8);
        }
        System.arraycopy(rho, 0,
                pkEncoded, (mlKem_k * mlKem_n * 12) / 8, rho.length);

        var kPkekp = new K_PKE_KeyPair(
                new K_PKE_DecryptionKey(skEncoded),
                new ML_KEM_EncapsulationKey(mlKem_size, pkEncoded));
        endTime = System.nanoTime();
        statistics.stats[Statistics.generateK_PkeKeyPairStat_6].time += (endTime - startTime);
        statistics.stats[Statistics.generateK_PkeKeyPairStat_6].nrCalls++;

        return kPkekp;
    }

    private K_PKE_CipherText kPkeEncrypt(
            ML_KEM_EncapsulationKey publicKey, byte[] message, byte[] sigma) {
        short[][] zeroes = new short[mlKem_k][mlKem_n];
        byte[] pkBytes = publicKey.keyBytes;
        byte[] rho = Arrays.copyOfRange(pkBytes,
                pkBytes.length - 32, pkBytes.length);
        byte[] tHatBytes = Arrays.copyOfRange(pkBytes,
                0, pkBytes.length - 32);
        var encryptTHat = decodeVector(12, tHatBytes);
        var encryptA = generateAparallel(rho, true);
        short[][] encryptR = new short[mlKem_k][];
        short[][] encryptE1 = new short[mlKem_k][];
        int encryptN = 0;
        byte[] prfSeed = new byte[sigma.length + 1];
        System.arraycopy(sigma, 0, prfSeed, 0, sigma.length);

        var kPkePRFeta1 = new SHAKE256(64 * mlKem_eta1);
        var kPkePRFeta2 = new SHAKE256(64 * mlKem_eta2);
        for (int i = 0; i < mlKem_k; i++) {
            prfSeed[sigma.length] = (byte) (encryptN++);
            kPkePRFeta1.update(prfSeed);
            byte[] cbdInput = kPkePRFeta1.digest();
            encryptR[i] = centeredBinomialDistribution(mlKem_eta1, cbdInput);
        }
        for (int i = 0; i < mlKem_k; i++) {
            prfSeed[sigma.length] = (byte) (encryptN++);
            kPkePRFeta2.update(prfSeed);
            byte[] cbdInput = kPkePRFeta2.digest();
            encryptE1[i] = centeredBinomialDistribution(mlKem_eta2, cbdInput);
        }
        prfSeed[sigma.length] = (byte) encryptN;
        kPkePRFeta2.reset();
        kPkePRFeta2.update(prfSeed);
        byte[] cbdInput = kPkePRFeta2.digest();
        var encryptE2 = centeredBinomialDistribution(mlKem_eta2, cbdInput);

        var encryptRHat = mlKemVectorNTT(encryptR);
        var encryptUHat = mlKemMatrixVectorMuladd(encryptA, encryptRHat, zeroes);
        var encryptU = mlKemVectorInverseNTT(encryptUHat);
        encryptU = mlKemAddVec(encryptU, encryptE1);
        var encryptVHat = mlKemVectorScalarMult(encryptTHat, encryptRHat);
        var encryptV = mlKemInverseNTT(encryptVHat);
        encryptV = mlKemAddPoly(encryptV, encryptE2,
//                decompressPoly(decodePoly(this, 1, message, 0), 1));
                  decompressDecode1(message));
        var encryptC1 = encodeVector(mlKem_du, compressVector10_11(encryptU, mlKem_du));
        var encryptC2 = encodePoly(mlKem_dv, compressPoly4_5(encryptV, mlKem_dv));

        byte[] result = new byte[encryptC1.length + encryptC2.length];
        System.arraycopy(encryptC1, 0,
                result, 0, encryptC1.length);
        System.arraycopy(encryptC2, 0,
                result, encryptC1.length, encryptC2.length);

        return new K_PKE_CipherText(result);
    }

    private byte[] kPkeDecrypt(K_PKE_DecryptionKey privateKey,
                               K_PKE_CipherText cipherText) {
        int uBytesLen = mlKem_k * mlKem_du * mlKem_n / 8;
        byte[] uBytes = Arrays.copyOfRange(cipherText.encryptedBytes,
                0, uBytesLen);
        byte[] vBytes = Arrays.copyOfRange(cipherText.encryptedBytes,
                uBytesLen, cipherText.encryptedBytes.length);
        var decryptU = decompressVector(decodeVector(mlKem_du, uBytes), mlKem_du);
        var decryptV = decompressPoly(
                decodePoly(this, mlKem_dv, vBytes, 0), mlKem_dv);
        var decryptSHat = decodeVector(12, privateKey.keyBytes);
        var decryptSU = mlKemInverseNTT(
                mlKemVectorScalarMult(decryptSHat, mlKemVectorNTT(decryptU)));
        decryptV = mlKemSubtractPoly(decryptV, decryptSU);

//        return encodePoly(1, compressPoly1(decryptV));
        return encodeCompress1(decryptV);
    }

    private short[][][] generateA(byte[] rho, Boolean transposed) {
        short[][][] a = new short[mlKem_k][][];
        var mlKemXOF = new SHAKE128(0);
        byte[] xofSeed = new byte[rho.length + 2];
        System.arraycopy(rho, 0, xofSeed, 0, rho.length);
        byte[] rawBuf = new byte[mlKemXofBlockLen + mlKemXofPad];
        short[] parsedBuf = new short[(rawBuf.length / 3) * 2];
        long startTime;
        long endTime;

        for (int i = 0; i < mlKem_k; i++) {
            a[i] = new short[mlKem_k][];
            for (int j = 0; j < mlKem_k; j++) {
                if (transposed) {
                    xofSeed[rho.length] = (byte) i;
                    xofSeed[rho.length + 1] = (byte) j;
                } else {
                    xofSeed[rho.length] = (byte) j;
                    xofSeed[rho.length + 1] = (byte) i;
                }
                mlKemXOF.reset();
                mlKemXOF.update(xofSeed);
                short[] aij = new short[mlKem_n];
                int ofs = 0;
                int parsedOfs = (mlKemXofBlockLen / 3) * 2;
                int tmp;
                while (ofs < mlKem_n) {
                    if (parsedOfs == (mlKemXofBlockLen / 3) * 2) {
                        mlKemXOF.squeeze(rawBuf, 0, mlKemXofBlockLen);
                        twelve2Sixteen(this, rawBuf, 0,
                                parsedBuf, mlKemXofBlockLen * 2 / 3);
                        parsedOfs = 0;
                    }

                    tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                    if (tmp < mlKem_q) {
                        aij[ofs] = (short) tmp;
                        ofs++;
                    }
                    tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                    if ((ofs < mlKem_n) && (tmp < mlKem_q)) {
                        aij[ofs] = (short) tmp;
                        ofs++;
                    }
                }
                a[i][j] = aij;
            }
        }
        return a;
    }

    private short[][][] generateAparallel(byte[] rho, Boolean transposed) {
        short[][][] a = new short[mlKem_k][mlKem_k][];

        int nrPar = 2;
        int rhoLen = rho.length;
        byte[] seedBuf = new byte[mlKemXofBlockLen];
        System.arraycopy(rho, 0, seedBuf, 0, rho.length);
        seedBuf[rhoLen + 2] = 0x1F;
        seedBuf[mlKemXofBlockLen - 1] = (byte)0x80;
        byte[][] xofBufArr = new byte[nrPar][mlKemXofBlockLen + mlKemXofPad];
        int[] iIndex = new int[nrPar];
        int[] jIndex = new int[nrPar];

        short[] parsedBuf = new short[(xofBufArr[0].length / 3) * 2];

        int parInd = 0;
        boolean allDone;
        int[] ofs = new int[nrPar];
        Arrays.fill(ofs, 0);
        short[][] aij = new short[nrPar][];
        SHA3Parallel.Shake128Parallel parXof = new SHA3Parallel.Shake128Parallel(xofBufArr);;

        for (int i = 0; i < mlKem_k; i++) {
            for (int j = 0; j < mlKem_k; j++) {
                xofBufArr[parInd] = seedBuf.clone();
                if (transposed) {
                    xofBufArr[parInd][rhoLen] = (byte) i;
                    xofBufArr[parInd][rhoLen + 1] = (byte) j;
                } else {
                    xofBufArr[parInd][rhoLen] = (byte) j;
                    xofBufArr[parInd][rhoLen + 1] = (byte) i;
                }
                iIndex[parInd] = i;
                jIndex[parInd] = j;
                ofs[parInd] = 0;
                aij[parInd] = new short[mlKem_n];
                parInd++;

                if ((parInd == nrPar) ||
                        ((i == mlKem_k - 1) && (j == mlKem_k - 1))) {
                    parXof.reset(xofBufArr);

                    allDone = false;
                    while (!allDone) {
                        allDone = true;
                        parXof.squeezeBlock();
                        for (int k = 0; k < parInd; k++) {
                            int parsedOfs = 0;
                            int tmp;
                            if (ofs[k] < mlKem_n) {
                                twelve2Sixteen(this, xofBufArr[k], 0,
                                        parsedBuf, (mlKemXofBlockLen / 3) * 2);
                            }
                            while ((ofs[k] < mlKem_n) &&
                                    (parsedOfs < (mlKemXofBlockLen / 3) * 2)) {
                                tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                                if (tmp < mlKem_q) {
                                    aij[k][ofs[k]] = (short) tmp;
                                    ofs[k]++;
                                }
                                tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                                if ((ofs[k] < mlKem_n) && (tmp < mlKem_q)) {
                                    aij[k][ofs[k]] = (short) tmp;
                                    ofs[k]++;
                                }
                            }
                            if (ofs[k] < mlKem_n) {
                                allDone = false;
                            }
                        }
                    }

                    for (int k = 0; k < parInd; k ++) {
                        a[iIndex[k]][jIndex[k]] = aij[k];
                    }
                    parInd = 0;
                }
            }
        }

        return a;
    }

    private short[] centeredBinomialDistribution(int eta, byte[] input) {
        if (eta == 2) return centeredBinomialDistribution2(input);
        if (eta == 3) return centeredBinomialDistribution3(input);
        short[] result = new short[mlKem_n];
        int index = 0;
        int shift = 8;
        int currentByte = input[0];
        for (int m = 0; m < mlKem_n; m++) {
            int a = 0;
            int b = 0;
            for (int j = 0; j < eta; j++) {
                if (shift == 8) {
                    currentByte = input[index++];
                    shift = 0;
                }
                a += (currentByte >> shift) & 1;
                shift++;
            }
            for (int j = 0; j < eta; j++) {
                if (shift == 8) {
                    currentByte = input[index++];
                    shift = 0;
                }
                b += (currentByte >> shift) & 1;
                shift++;
            }
            result[m] = (short) (a - b);
        }
        return result;
    }

    private short[] centeredBinomialDistribution2(byte[] input) {
        short[] result = new short[mlKem_n];
        long bits = 0x0112f001f001eff0L;
        int j = 0;

        long startTime = System.nanoTime();
        for (int i = 0; i < input.length; i++) {
            int a = input[i];
            int shift1 = (a << 2) & 0x3c;
            int shift2 = (a >> 2) & 0x3c;
            result[j++] = (short) ((bits << shift1) >> 60);
            result[j++] = (short) ((bits << shift2) >> 60);
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.centeredBinomialDistribution2].time += (endTime - startTime);
        statistics.stats[Statistics.centeredBinomialDistribution2].nrCalls++;

        return result;
    }

    private short[] centeredBinomialDistribution3(byte[] input) {
        short[] result = new short[mlKem_n];
        int bits = 0x01121223;
        int j = 0;

        long startTime = System.nanoTime();
        for (int i = 0; i < input.length; i += 3) {
            int a1 = input[i];
            int a2 = input[i + 1];
            int a3 = input[i + 2];
            int shift1 = (a1 << 2) & 0x1c;
            int shift2 = (a1 >> 1) & 0x1c;
            int shift3 = ((a1 >> 4) & 0x0c) | ((a2 << 4) & 0x10);
            int shift4 = (a2 << 1) & 0x1c;
            int shift5 = (a2 >> 2) & 0x1c;
            int shift6 = ((a2 >> 5) & 0x04) | ((a3 << 3) & 0x18);
            int shift7 = a3 & 0x1c;
            int shift8 = (a3 >> 3) & 0x1c;
            result[j++] = (short)
                    (((bits << shift1) >> 28) - ((bits << shift2) >> 28));
            result[j++] = (short)
                    (((bits << shift3) >> 28) - ((bits << shift4) >> 28));
            result[j++] = (short)
                    (((bits << shift5) >> 28) - ((bits << shift6) >> 28));
            result[j++] = (short)
                    (((bits << shift7) >> 28) - ((bits << shift8) >> 28));
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.centeredBinomialDistribution3].time += (endTime - startTime);
        statistics.stats[Statistics.centeredBinomialDistribution3].nrCalls++;

        return result;
    }

    // Works in place, it returns its (modified) input so that it can be used in
    // expressions
    private short[][] mlKemVectorNTT(short[][] vector) {
        for (int i = 0; i < mlKem_k; i++) {
            mlKemNTT(vector[i]);
        }
        return vector;
    }

    // Works in place, it returns its (modified) input so that it can be used in
    // expressions
    private short[][] mlKemVectorInverseNTT(short[][] vector) {
        for (int i = 0; i < mlKem_k; i++) {
            vector[i] = mlKemInverseNTT(vector[i]);
        }
        return vector;
    }

    @IntrinsicCandidate
    static int implKyberNtt(short[] poly, short[] ntt_zetas) {
        implKyberNttJava(poly);
        return 1;
    }

    static void implKyberNttJava(short[] poly) {
        int[] coeffs = new int[mlKem_n];
        for (int m = 0; m < mlKem_n; m++) {
            coeffs[m] = poly[m];
        }
        seilerNTT(coeffs);
        for (int m = 0; m < mlKem_n; m++) {
            poly[m] = (short) coeffs[m];
        }
    }

    // The elements of poly should be in the range [-mlKem_q, mlKem_q]
    // The elements of poly at return will be in the range of [0, mlKem_q]
    private void mlKemNTT(short[] poly) {
        boolean useThis = statistics.stats[Statistics.NttStat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            statistics.stats[Statistics.NttStat].nrNoIntrCalls +=
                    implKyberNtt(poly, montZetasForVectorNttArr);
        } else {
            implKyberNttJava(poly);
            statistics.stats[Statistics.NttStat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.NttStat].time += (endTime - startTime);
        statistics.stats[Statistics.NttStat].nrCalls++;
        mlKemBarrettReduce(poly);
    }

    @IntrinsicCandidate
    static int implKyberInverseNtt(short[] poly, short[] zetas) {
        implKyberInverseNttJava(poly);
        return 1;
    }

    static void implKyberInverseNttJava(short[] poly) {
        int[] coeffs = new int[mlKem_n];
        for (int m = 0; m < mlKem_n; m++) {
            coeffs[m] = poly[m];
        }
        seilerInverseNTT(coeffs);
        for (int m = 0; m < mlKem_n; m++) {
            poly[m] = (short) coeffs[m];
        }
    }

    // Works in place, but also returns its (modified) input so that it can
    // be used in expressions
    private short[] mlKemInverseNTT(short[] poly) {
        boolean useThis = statistics.stats[Statistics.InvesreNttStat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            statistics.stats[Statistics.InvesreNttStat].nrNoIntrCalls +=
                    implKyberInverseNtt(poly, montZetasForVectorInverseNttArr);
        } else {
            implKyberInverseNttJava(poly);
            statistics.stats[Statistics.InvesreNttStat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.InvesreNttStat].time +=
                (endTime - startTime);
        statistics.stats[Statistics.InvesreNttStat].nrCalls++;
        return poly;
    }

    // Implements the ML_KEM NTT algorithm similarly to that described
    // in https://eprint.iacr.org/2018/039.pdf .
    // It works in place, replaces the elements of the input coeffs array
    // by the transformed representation.
    // The input elements should be in the range [-montQ, montQ].
    // The result elements will fit into the range of short
    // (i.e. [-32768, 32767]).
    private static void seilerNTT(int[] coeffs) {
        int dimension = mlKem_n;
        int zetaIndex = 0;
        for (int l = dimension / 2; l > 1; l /= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = montMul(montZetasForNtt[zetaIndex], coeffs[j + l]);
                    coeffs[j + l] = coeffs[j] - tmp;
                    coeffs[j] = coeffs[j] + tmp;
                }
                zetaIndex++;
            }
        }
    }

    // Implements the ML_KEM inverse NTT algorithm similarly to that described
    // in https://eprint.iacr.org/2018/039.pdf .
    // It works in place, replaces the elements of the input coeffs array
    // by the transformed representation.
    // The input elements should be in the range [-montQ, montQ).
    // The output elements will be in the range (-montQ, montQ).
    private static void seilerInverseNTT(int[] coeffs) {
        int dimension = mlKem_n;
        int zetaIndex = 0;
        for (int l = 2; l < dimension; l *= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = coeffs[j];
                    coeffs[j] = (tmp + coeffs[j + l]);
                    coeffs[j + l] = montMul(
                            tmp - coeffs[j + l],
                            montZetasForInverseNtt[zetaIndex]);
                }
                zetaIndex++;
            }
        }

        for (int i = 0; i < dimension; i++) {
            int r = montMul(coeffs[i], montDimHalfInverse);
            coeffs[i] = r;
        }
    }

    // Performs A o b + c where
    // A is a mlKem_k by mlKem_k matrix,
    // b and c are mlKem_k long vectors of degree mlKem_n - 1
    // polynomials in the NTT domain representation.
    // The coefficients in the result are in the range [0, mlKem_q).
    private short[][] mlKemMatrixVectorMuladd(
            short[][][] a, short[][] b, short[][] c) {
        short[] product = new short[mlKem_n];

        for (int i = 0; i < mlKem_k; i++) {
            for (int j = 0; j < mlKem_k; j++) {
                nttMult(product, a[i][j], b[j]);
                mlKemAddPoly(c[i], product);
            }
            mlKemBarrettReduce(c[i]);
        }
        return c;
    }

    // Performs a^T o b where a and b are mlKem_k long vectors
    // of degree mlKem_n - 1 polynomials in the NTT representation,
    // with coefficients in the range [-mlKem_q, mlKem_q].
    // The coefficients in the result are in the range [0, mlKem_q).
    private short[] mlKemVectorScalarMult(short[][] a, short[][] b) {
        short[] result = new short[mlKem_n];
        short[] product = new short[mlKem_n];
        short[] ntta;
        short[] nttb;

        int j;
        for (j = 0; j < mlKem_k; j++) {
            ntta = a[j];
            nttb = b[j];
            nttMult(product, ntta, nttb);
            mlKemAddPoly(result, product);
        }
        mlKemBarrettReduce(result);

        return result;
    }

    @IntrinsicCandidate
    static int implKyberNttMult(short[] result, short[] ntta, short[] nttb,
                                short[] zetas) {
        implKyberNttMultJava(result, ntta, nttb);
        return 1;
    }

    static void implKyberNttMultJava(short[] result, short[] ntta, short[] nttb) {
        for (int m = 0; m < mlKem_n / 2; m++) {
            int a0 = ntta[2 * m];
            int a1 = ntta[2 * m + 1];
            int b0 = nttb[2 * m];
            int b1 = nttb[2 * m + 1];
            int r = montMul(a0, b0) +
                    montMul(montMul(a1, b1), montZetasForNttMult[m]);
            result[2 * m] = (short) montMul(r, montRSquareModQ);
            result[2 * m + 1] = (short) montMul(
                    (montMul(a0, b1) + montMul(a1, b0)), montRSquareModQ);
        }
    }

    // Multiplies two polynomials represented in the NTT domain.
    // The result is a representation of the product still in the NTT domain.
    // The coefficients in the result are in the range (-mlKem_q, mlKem_q).
    private void nttMult(short[] result, short[] ntta, short[] nttb) {
        boolean useThis = statistics.stats[Statistics.NttMultStat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            short[] product = new short[mlKem_n];
            statistics.stats[Statistics.NttMultStat].nrNoIntrCalls +=
                    implKyberNttMult(result, ntta, nttb,
                            montZetasForVectorNttMultArr);
        } else {
            implKyberNttMultJava(result, ntta, nttb);
            statistics.stats[Statistics.NttMultStat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.NttMultStat].time += (endTime - startTime);
        statistics.stats[Statistics.NttMultStat].nrCalls++;
    }

    // Adds the vector of polynomials b to a in place, i.e. a will hold
    // the result. It also returns (the modified) a so that it can be used
    // in an expression.
    // The coefficiens in all polynomials of both vectors are supposed to be
    // greater than -mlKem_q and less than mlKem_q.
    // The coefficients in the result are nonnegative and less than mlKem_q.
    private short[][] mlKemAddVec(short[][] a, short[][] b) {
        for (int i = 0; i < mlKem_k; i++) {
            mlKemAddPoly(a[i], b[i]);
            mlKemBarrettReduce(a[i]);
        }
        return a;
    }

    @IntrinsicCandidate
    static int implKyberAddPoly(short[] result, short[] a, short[] b) {
        implKyberAddPolyJava(result, a, b);
        return 1;
    }

    static void implKyberAddPolyJava(short[] result, short[] a, short[] b) {
        for (int m = 0; m < mlKem_n; m++) {
            int r = a[m] + b[m] + mlKem_q; // This makes r > -mlKem_q
            result[m] = (short) r;
        }
    }

    // Adds the polynomial b to a in place, i.e. (the modified) a will hold
    // the result.
    // The coefficients are supposed be greater than -mlKem_q in a and
    // greater than -mlKem_q and less than mlKem_q in b.
    // The coefficients in the result are greater than -mlKem_q.
    private void mlKemAddPoly(short[] a, short[] b) {
        boolean useThis = statistics.stats[Statistics.AddPoly2Stat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            statistics.stats[Statistics.AddPoly2Stat].nrNoIntrCalls +=
                    implKyberAddPoly(a, a, b);
        } else {
            for (int m = 0; m < mlKem_n; m++) {
                a[m] = (short) (a[m] + b[m] + mlKem_q); // This makes r > -mlKem_q
            }
            statistics.stats[Statistics.AddPoly2Stat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.AddPoly2Stat].time += (endTime - startTime);
        statistics.stats[Statistics.AddPoly2Stat].nrCalls++;
    }

    @IntrinsicCandidate
    static int implKyberAddPoly(short[] result, short[] a, short[] b, short[] c) {
        implKyberAddPolyJava(result, a, b, c);
        return 1;
    }

    static void implKyberAddPolyJava(short[] result, short[] a, short[] b, short[] c) {
        for (int m = 0; m < mlKem_n; m++) {
            int r = a[m] + b[m] + c[m] + 2 * mlKem_q; // This makes r > - mlKem_q
            result[m] = (short) r;
        }
    }

    // Adds the polynomials b and c to a in place, i.e. a will hold the sum.
    // a is also returned so that this function can be used in an expression.
    // The coefficients in all three polynomials are supposed to be
    // greater than -mlKem_q and less than mlKem_q.
    // The coefficients in the result are nonnegative and less than mlKem_q.
    private short[] mlKemAddPoly(short[] a, short[] b, short[] c) {
        boolean useThis = statistics.stats[Statistics.AddPoly3Stat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            statistics.stats[Statistics.AddPoly3Stat].nrNoIntrCalls +=
                    implKyberAddPoly(a, a, b, c);
            mlKemBarrettReduce(a);
        } else {
            for (int m = 0; m < mlKem_n; m++) {
                int r = a[m] + b[m] + c[m]; // -3*mlKem_q < r < 3 * mlKem_q
                r += (((r >> 31) & (3 * mlKem_q)) - 2 * mlKem_q); // -2*mlKem_q < r < mlKem_q
                r += ((r >> 31) & mlKem_q); // -mlKem_q < r < mlKem_q
                r += ((r >> 31) & mlKem_q); // 0 <= r < mlKem_q
                a[m] = (short) r;
            }
            statistics.stats[Statistics.AddPoly3Stat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.AddPoly3Stat].time += (endTime - startTime);
        statistics.stats[Statistics.AddPoly3Stat].nrCalls++;
        return a;
    }

    // Subtracts the polynomial b from a in place, i.e. the result is
    // stored in a. It also returns (the modified) a, so that it can be used
    // in an expression.
    // The coefficiens in both are assumed to be greater than -mlKem_q
    // and less than mlKem_q.
    // The coefficients in the result are nonnegative and less than mlKem_q.
    private short[] mlKemSubtractPoly(short[] a, short[] b) {
        for (int m = 0; m < mlKem_n; m++) {
            int r = a[m] - b[m] + mlKem_q; // This makes r > -mlKem_q
            a[m] = (short) r;
        }
        mlKemBarrettReduce(a);
        return a;
    }

    private byte[] encodeVector(int l, short[][] vector) {
        return encodeVector(l, vector, mlKem_k);
    }

    private static byte[] encodeVector(int l, short[][] vector, int k) {
        int encodedPolyLength = mlKem_n * l / 8;
        byte[] result = new byte[k * encodedPolyLength];

        for (int i = 0; i < k; i++) {
            byte[] resultBytes = encodePoly(l, vector[i]);
            System.arraycopy(resultBytes, 0,
                    result, i * encodedPolyLength, encodedPolyLength);
        }
        return result;
    }

    private static void encodePoly12(short[] poly, byte[] result) {
        int low;
        int high;
        for (int m = 0; m < mlKem_n / 2; m++) {
            low = poly[2 * m];
            low += ((low >> 31) & mlKem_q);
            low = low & 0xfff;
            high = poly[2 * m + 1];
            high += ((high >> 31) & mlKem_q);
            high = high & 0xfff;

            result[m * 3] = (byte) low;
            result[m * 3 + 1] = (byte) ((high << 4) + (low >> 8));
            result[m * 3 + 2] = (byte) (high >> 4);
        }
    }

    private static void encodePoly4(short[] poly, byte[] result) {
        for (int m = 0; m < mlKem_n / 2; m++) {
            result[m] = (byte) ((poly[2 * m] & 0xf) + (poly[2 * m + 1] << 4));
        }
    }

    // Computes the byte array containing the packed l-bit representation
    // of a polynomial. The coefficients in poly should be either nonnegative
    // or elements of Z_(mlKem_q) represented by a 16-bit value
    // between -mlKem_q and mlKem_q.
    private static byte[] encodePoly(int l, short[] poly) {
        byte[] result = new byte[mlKem_n / 8 * l];
        if (l == 12) {
            encodePoly12(poly, result);
        } else if (l == 4) {
            encodePoly4(poly, result);
        } else {
            int mask = (1 << l) - 1;
            int shift = 0;
            int index = 0;
            int current = 0;
            for (int m = 0; m < mlKem_n; m++) {
                int currentShort = poly[m];
                currentShort += (currentShort >> 31) & mlKem_q;
                current += ((currentShort & mask) << shift);
                shift += l;
                while (shift >= 8) {
                    result[index++] = (byte) current;
                    current >>>= 8;
                    shift -= 8;
                }
            }
        }

        return result;
    }

    static byte[] encodeCompress1(short[] poly) {
        byte[] result = new byte[mlKem_n / 8];
        int xx;
        int currentByte;
        for (int i = 0; i < mlKem_n / 8; i++) {
            currentByte = 0;
            xx = poly[i * 8];
            currentByte |= (((832 - xx) & (xx - 2497)) >>> 31);
            xx = poly[i * 8 + 1];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 1);
            xx = poly[i * 8 + 2];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 2);
            xx = poly[i * 8 + 3];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 3);
            xx = poly[i * 8 + 4];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 4);
            xx = poly[i * 8 + 5];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 5);
            xx = poly[i * 8 + 6];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 6);
            xx = poly[i * 8 + 7];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 31) << 7);
            result[i] = (byte) currentByte;
        }
        return result;
    }


    private short[][] decodeVector(int l, byte[] encodedVector) {
        short[][] result = new short[mlKem_k][];
        for (int i = 0; i < mlKem_k; i++) {
            result[i] = decodePoly(this, l, encodedVector, (i * mlKem_n * l) / 8);
        }
        return result;
    }

    public static byte[] normalizeDecapsKeyBytes(byte[] decapsKeyBytes) {
        byte[] result = decapsKeyBytes.clone();
        int k = (decapsKeyBytes.length - 64) / ((mlKem_n * 3) / 2);
        short[][] vector = new short[k][];
        for (int i = 0; i < k; i++) {
            vector[i] = new short[mlKem_n];
            implKyber12To16(decapsKeyBytes, i * ((mlKem_n * 3)/ 2), vector[i], mlKem_n);
            implKyberBarrettReduce(vector[i]);
        }
        var normalized = encodeVector(12, vector, k);
        System.arraycopy(normalized, 0, result, 0, normalized.length);
        return result;
    }

    @IntrinsicCandidate
    private static int implKyber12To16(byte[] condensed, int index, short[] parsed, int parsedLength) {
        implKyber12To16Java(condensed, index, parsed, parsedLength);
        return 1;
    }

    private static void implKyber12To16Java(byte[] condensed, int index, short[] parsed, int parsedLength) {
        for (int i = 0; i < parsedLength * 3 / 2; i += 3) {
            parsed[(i / 3) * 2] = (short) ((condensed[i + index] & 0xff) +
                    256 * (condensed[i + index + 1] & 0xf));
            parsed[(i / 3) * 2 + 1] = (short) (((condensed[i + index + 1] >>> 4) & 0xf) +
                    16 * (condensed[i + index + 2] & 0xff));
        }
    }

    // The intrinsic implementations assume that the input and output buffers
    // are such that condensed can be read in 192-byte chunks and
    // parsed can be written in 128 shorts chunks. In other words,
    // if (i - 1) * 128 < parsedLengths <= i * 128 then
    // parsed.size should be at least i * 128 and
    // condensed.size should be at least index + i * 192
    private void twelve2Sixteen(ML_KEM mlKem, byte[] condensed, int index, short[] parsed, int parsedLength) {
        boolean useThis = mlKem.statistics.stats[Statistics.Twelve2SixteenStat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            mlKem.statistics.stats[Statistics.Twelve2SixteenStat].nrNoIntrCalls +=
                    implKyber12To16(condensed, index, parsed, parsedLength);
        } else {
            implKyber12To16Java(condensed, index, parsed, parsedLength);
            mlKem.statistics.stats[Statistics.Twelve2SixteenStat].nrNoIntrCalls++;
        }

        long endTime = System.nanoTime();
        mlKem.statistics.stats[Statistics.Twelve2SixteenStat].time += (endTime - startTime);
        mlKem.statistics.stats[Statistics.Twelve2SixteenStat].nrCalls++;
    }

    private static void decodePoly5(byte[] condensed, int index, short[] parsed) {
        int j = index;
        for (int i = 0; i < mlKem_n; i += 8) {
            parsed[i] = (short) (condensed[j] & 0x1f);
            parsed[i + 1] = (short) ((((condensed[j] & 0xff) >>> 5) +
                    (condensed[j + 1] << 3) & 0x1f));
            parsed[i + 2] = (short) ((condensed[j + 1] & 0x7f) >>> 2);
            parsed[i + 3] = (short) ((((condensed[j + 1] & 0xff) >>> 7) +
                    (condensed[j + 2] << 1)) & 0x1f);
            parsed[i + 4] = (short) ((((condensed[j + 2] & 0xff) >>> 4) +
                    (condensed[j + 3] << 4)) & 0x1f);
            parsed[i + 5] = (short) ((condensed[j + 3] & 0x3f) >>> 1);
            parsed[i + 6] = (short) ((((condensed[j + 3] & 0xff) >>> 6) +
                    (condensed[j + 4] << 2)) & 0x1f);
            parsed[i + 7] = (short) ((condensed[j + 4] & 0xff) >>> 3);
            j += 5;
        }
    }

    private static void decodePoly4(byte[] condensed, int index, short[] parsed) {
        for (int i = 0; i < mlKem_n / 2; i++) {
            parsed[i * 2] = (short) (condensed[i + index] & 0xf);
            parsed[i * 2 + 1] = (short) ((condensed[i + index] >>> 4) & 0xf);
        }
    }

    // Recovers the 16-bit coefficients of a polynomial from a byte array
    // containing a packed l-bit representation.
    // The recovered coefficients will be in the range 0 <= coeff < 2^l .
    private short[] decodePoly(
            ML_KEM mlKem, int l, byte[] input, int index) {
        short[] poly = new short[mlKem_n];
        short[] poly1 = new short[mlKem_n];
        if (l == 12) {
            twelve2Sixteen(mlKem, input, index, poly, mlKem_n);
        } else if (l == 4) {
            decodePoly4(input, index, poly);
        } else if (l == 5) {
            decodePoly5(input, index, poly);
        } else {
            int mask = (1 << l) - 1;
            int top = 0;
            int shift = 0;
            int acc = 0;
            for (int m = 0; m < mlKem_n; m++) {
                while (top - shift < l) {
                    acc += ((input[index++] & 0xff) << top);
                    top += 8;
                }
                poly[m] = (short) ((acc >> shift) & mask);
                shift += l;
                while (shift >= 8) {
                    top -= 8;
                    shift -= 8;
                    acc >>>= 8;
                }
            }
        }

        return poly;
    }

    // Compresses a vector in place, i.e. it modifies the coefficients of the
    // polynomials of its input vector. It returns its (modified) input so that
    // the function can be used in an expression.
    private short[][] compressVector(short[][] vector, int d) {
        for (int i = 0; i < mlKem_k; i++) {
            vector[i] = compressPoly(vector[i], d);
        }
        return vector;
    }

    // Prerequisite: d == 10 or d == 11
    // Compresses a vector in place, i.e. it modifies the coefficients of the
    // polynomials of its input vector. It returns its (modified) input so that
    // the function can be used in an expression.
    private short[][] compressVector10_11(short[][] vector, int d) {
        for (int i = 0; i < mlKem_k; i++) {
            vector[i] = compressPoly10_11(vector[i], d);
        }
        return vector;
    }

    // Compresses a polynomial in place, i.e. it modifies the coefficients
    // in its input. It returns its (modified) input so that the function can
    // be used in an expression.
    private static short[] compressPoly1(short[] poly) {
        int xx;
        for (int m = 0; m < mlKem_n; m++) {
//            xx = (poly[m] << 1) + mlKem_q / 2;
//
//            // 315 is (2^20 + mlKem_q / 2) / mlKem_q
//            // For 0 <= poly[m] < mlKem_q the below assignment is the same as
//            //  poly[m] = (short) (xx / mlKem_q);
//            // and it executes in constant time
//            poly[m] = (short) ((xx * 315) >> 20);

            xx = poly[m];
            poly[m] = (short)(((832 - xx) & (xx - 2497)) >>> 31);
        }

        return poly;
    }

    // Compresses a polynomial in place, i.e. it modifies the coefficients
    // in its input. It returns its (modified) input so that the function can
    // be used in an expression.
    private static short[] compressPoly(short[] poly, int d) {
        for (int m = 0; m < mlKem_n; m++) {
            poly[m] = compress(poly[m], d);
        }
        return poly;
    }

    // Prerequisite: for all m, 0 <= poly[m] < mlKem_q, d == 4 or d == 5
    // Replaces poly[m] with round(2^d * poly[m] / mlKem_q) mod 2^d for all m,
    // where round(z) is the integer closest to z, i.e.
    // compresses a polynomial in place.
    private static short[] compressPoly4_5(short[] poly, int d) {
        int xx;
        for (int m = 0; m < mlKem_n; m++) {
            xx = (poly[m] << d) + mlKem_q / 2;
            poly[m] = (short)((xx * 315) >> 20);
        }
        return poly;
    }

    // Prerequisite: for all m, 0 <= poly[m] < mlKem_q, d == 10 or d == 11
    // Replaces poly[m] with round(2^d * poly[m] / mlKem_q) mod 2^d for all m,
    // where round(z) is the integer closest to z, i.e.
    // compresses a polynomial in place.
    private static short[] compressPoly10_11(short[] poly, int d) {
        long xx;
        for (int m = 0; m < mlKem_n; m++) {
            xx = (poly[m] << d) + mlKem_q / 2;
            poly[m] = (short)((xx * 161271L) >> 29);
        }
        return poly;
    }

    // Prerequisite: 0 <= x < mlKem_q, 2^d < mlKem_q .
    // Computes round(2^d * x / mlKem_q) mod 2^d for x > 0 ,
    // where Round(z) is the integer closest to z .
    private static short compress(short x, int d) {
        int xx = x << d;
        int ratio = (xx + mlKem_q / 2) / mlKem_q;

        return (short) ratio;
    }

    // Decompresses a vector in place, i.e. it modifies the coefficients of the
    // polynomials of its input vector. It returns its (modified) input so that
    // the function can be used in an expression.
    private short[][] decompressVector(short[][] vector, int d) {
        for (int i = 0; i < mlKem_k; i++) {
            vector[i] = decompressPoly(vector[i], d);
        }
        return vector;
    }

    // Compresses a polynomial in place, i.e. it modifies the coefficients
    // in its input. It returns its (modified) input so that the function can
    // be used in an expression.
    // Prerequisite: 0 <= x[i] < 2^d < mlKem_q .
    // Computes Round(mlKem_q * x[i] / 2^d),
    // where Round(z) is the integer closest to z,
    // for each coefficient of a polynomial
    private static short[] decompressPoly(short[] poly, int d) {
        for (int m = 0; m < mlKem_n; m++) {
            int qx = mlKem_q * poly[m];
            poly[m] = (short) ((qx >> d) + ((qx >> (d - 1)) & 1));
        }
        return poly;
    }

    private static short[] decompressDecode1 (byte[] input) {
        short[] result = new short[256];
        for (int i = 0; i < 32; i++) {
            int currentByte = input[i] & 0xFF;
            result [i * 8] = (short)(((currentByte << 31 ) >> 31) & 1665);
            result [i * 8 + 1] = (short)(((currentByte << 30 ) >> 31) & 1665);
            result [i * 8 + 2] = (short)(((currentByte << 29 ) >> 31) & 1665);
            result [i * 8 + 3] = (short)(((currentByte << 28 ) >> 31) & 1665);
            result [i * 8 + 4] = (short)(((currentByte << 27 ) >> 31) & 1665);
            result [i * 8 + 5] = (short)(((currentByte << 26 ) >> 31) & 1665);
            result [i * 8 + 6] = (short)(((currentByte << 25 ) >> 31) & 1665);
            result [i * 8 + 7] = (short)(((currentByte << 24 ) >> 31) & 1665);
        }
        return result;
    }

    private static void mlKemBarrettReduce(int[] poly) {
        for (int m = 0; m < mlKem_n; m++) {
            int tmp = (poly[m] * mlKemBarrettMultiplier) >> mlKemBarrettShift;
            poly[m] = poly[m] - tmp * mlKem_q;
        }
    }

    @IntrinsicCandidate
    static int implKyberBarrettReduce(short[] coeffs) {
        implKyberBarrettReduceJava(coeffs);
        return 1;
    }

    static void implKyberBarrettReduceJava(short[] coeffs) {
        for (int m = 0; m < mlKem_n; m++) {
            int tmp = ((int) coeffs[m] * mlKemBarrettMultiplier) >>
                    mlKemBarrettShift;
            coeffs[m] = (short) (coeffs[m] - tmp * mlKem_q);
        }
    }

    // The input elements can have any short value.
    // Modifies poly such that upon return poly[i] will be
    // in the range [0, mlKem_q] and will be congruent with the original
    // poly[i] modulo mlKem_q, for all i in [0, mlKem_n).
    // At return, poly[i] == mlKem_q if and only if the original poly[i] was
    // a negative integer multiple of mlKem_q.
    // That means that if the original poly[i] > -mlKem_q then at return it
    // will be in the range [0, mlKem_q), i.e. it will be the canonical
    // representative of its residue class.
    private void mlKemBarrettReduce(short[] poly) {
        boolean useThis = statistics.stats[Statistics.BarrettReduceStat].useThis;
        long startTime = System.nanoTime();
        if (useIntrinsics && useThis) {
            statistics.stats[Statistics.BarrettReduceStat].nrNoIntrCalls +=
                    implKyberBarrettReduce(poly);
        } else {
            implKyberBarrettReduceJava(poly);
            statistics.stats[Statistics.BarrettReduceStat].nrNoIntrCalls++;
        }
        long endTime = System.nanoTime();
        statistics.stats[Statistics.BarrettReduceStat].time +=
                (endTime - startTime);
        statistics.stats[Statistics.BarrettReduceStat].nrCalls++;
    }

    // Precondition: -(2^montRBits -1) * montQ <= b * c < (2^montRBits - 1) * montQ .
    // Computes b * c * 2^-montRBits mod montQ .
    // The result is between  -montQ and montQ.
    private static int montMul(int b, int c) {
        int a = b * c;
        int aHigh = a >> montRBits;
        int aLow = a & ((1 << montRBits) - 1);
        int m = ((montQInvModR * aLow) << (32 - montRBits)) >>
                (32 - montRBits); // signed low product

        return (aHigh - ((m * montQ) >> montRBits)); // subtract signed high product
    }

    public static class Statistics {
        static final int NttStat = 0;
        static final int InvesreNttStat = 1;
        static final int NttMultStat = 2;
        static final int BarrettReduceStat = 3;
        static final int AddPoly2Stat = 4;
        static final int AddPoly3Stat = 5;
        static final int Twelve2SixteenStat = 6;
        static final int generateKemKeyPairStat_1 = 7;
        static final int generateKemKeyPairStat_2 = 8;
        static final int generateK_PkeKeyPairStat_1 = 9;
        static final int generateK_PkeKeyPairStat_2 = 10;
        static final int generateK_PkeKeyPairStat_3 = 11;
        static final int generateK_PkeKeyPairStat_4 = 12;
        static final int generateK_PkeKeyPairStat_5 = 13;
        static final int generateK_PkeKeyPairStat_6 = 14;
        static final int centeredBinomialDistribution2 = 15;
        static final int centeredBinomialDistribution3 = 16;
        static final int generateAparse = 17;

        static class StatRecord {
            String name;
            boolean useThis;
            int nrCalls;
            int nrNoIntrCalls;
            long time;

            StatRecord(String name) {
                this.name = name;
                this.useThis = false;
                this.nrCalls = 0;
                this.nrNoIntrCalls = 0;
                this.time = 0;
            }
        }

        StatRecord[] stats = {
                new StatRecord("                              Ntt"),
                new StatRecord("                       InverseNtt"),
                new StatRecord("                          NttMult"),
                new StatRecord("                    BarrettReduce"),
                new StatRecord("                         AddPoly2"),
                new StatRecord("                         AddPoly3"),
                new StatRecord("                   twelve2Sixteen"),
                new StatRecord("   KemKeyPair(generateCcaKeyPair)"),
                new StatRecord("                 KemKeyPair(rest)"),
                new StatRecord("             K_PkeKeyPair(setup)"),
                new StatRecord("           K_PkeKeyPair(keyGenA)"),
                new StatRecord("               K_PkeKeyPair(cbd)"),
                new StatRecord("               K_PkeKeyPair(ntt)"),
                new StatRecord("K_PkeKeyPair(MatrixVectorMuladd)"),
                new StatRecord("     K_PkeKeyPair(encode + rest)"),
                new StatRecord("                             cBD2"),
                new StatRecord("                             cBD3"),
                new StatRecord("                   generateAparse")
        };

        void setUseThis(long flags) {
            if (flags >= (1L << (stats.length + 1))) {
                throw new RuntimeException("Invalid flags");
            }
            for (int i = 0; i < stats.length; i++) {
                stats[i].useThis = ((flags & (1L << i)) != 0);
            }
        }

        public void printStat(int stat) {
            if (stat < 0 || (stat > stats.length)) {
                throw new RuntimeException("No stat record exist");
            }
            System.out.println(stats[stat].name + "(" + stats[stat].useThis +
                    ")* nrCalls = " + stats[stat].nrCalls +
                    " nrNoIntrCalls = " + stats[stat].nrNoIntrCalls +
                    " time = " + stats[stat].time + "ns");
        }
    }

    public static class TestUtils {
        static final String[] nibbles = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

        static String hexChar(int nibble) {
            return nibbles[nibble & 0xf];
        }

        static String hexStr(byte b) {
            return hexChar((b >> 4)) + hexChar((int) b);
        }

        public static void printHex(String prefix, byte[] arr) {
            printHex(prefix, arr, 0, arr.length);
        }

        public static void printHex(String prefix, byte[] arr, int start, int len) {
            String str = prefix;
            for (int i = 0; i < len; i++) {
                str += hexStr(arr[start + i]);
            }
            System.out.println(str);
        }

        public static void printHex(String prefix, short[] arr) {
            printHex(prefix, arr, 0, arr.length);
        }

        public static void printHex(String prefix, short[] arr, int start, int len) {
            String str = prefix;
            for (int i = 0; i < len; i++) {
                str += hexStr((byte) (arr[start + i] & 0xff));
                str += hexStr((byte) (arr[start + i] >>> 8));
                str += " ";

            }
            System.out.println(str);
        }

        public static void printHex(String prefix, int[] arr) {
            printHex(prefix, arr, 0, arr.length);
        }

        public static void printHex(String prefix, int[] arr, int start, int len) {
            String str = prefix;
            for (int i = 0; i < len; i++) {
                str += hexStr((byte) arr[start + i]);
                str += hexStr((byte) (arr[start + i] >>> 8));
                str += hexStr((byte) (arr[start + i] >>> 16));
                str += hexStr((byte) (arr[start + i] >>> 24));
                str += " ";

            }
            System.out.println(str);
        }

        public static void printHex(String prefix, long[] arr) {
            printHex(prefix, arr, 0, arr.length);
        }

        static void printHex(String prefix, long[] arr, int start, int len) {
            String str = prefix;
            for (int i = 0; i < len; i++) {
                str += hexStr((byte) (arr[start + i] & 0xff));
                str += hexStr((byte) (arr[start + i] >>> 8));
                str += hexStr((byte) (arr[start + i] >>> 16));
                str += hexStr((byte) (arr[start + i] >>> 24));
                str += hexStr((byte) (arr[start + i] >>> 32));
                str += hexStr((byte) (arr[start + i] >>> 40));
                str += hexStr((byte) (arr[start + i] >>> 48));
                str += hexStr((byte) (arr[start + i] >>> 56));
                str += " ";

            }
            System.out.println(str);
        }

        static byte[] hexDecode(String s) {
            var cleaned = s
                    .replaceAll("//.*", "")
                    .replaceAll("\\s", "");
            return HexFormat.of().parseHex(cleaned);
        }
    }
}
