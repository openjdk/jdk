/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.security.*;
import java.util.Arrays;
import javax.crypto.DecapsulateException;
import jdk.internal.vm.annotation.IntrinsicCandidate;

import sun.security.provider.SHA3.SHAKE256;
import sun.security.provider.SHA3Parallel.Shake128Parallel;

public final class ML_KEM {

    public static final int SECRET_SIZE = 32;
    private static final String HASH_H_NAME = "SHA3-256";
    private static final String HASH_G_NAME = "SHA3-512";

    private static final int ML_KEM_Q = 3329;
    private static final int ML_KEM_N = 256;

    private static final int XOF_BLOCK_LEN = 168; // the block length for SHAKE128
    private static final int XOF_PAD = 24;
    private static final int MONT_R_BITS = 20;
    private static final int MONT_Q = 3329;
    private static final int MONT_R_SQUARE_MOD_Q = 152;
    private static final int MONT_Q_INV_MOD_R = 586497;

    // toMont((ML_KEM_N / 2)^-1 mod ML_KEM_Q) using R = 2^MONT_R_BITS
    private static final int MONT_DIM_HALF_INVERSE = 1534;
    private static final int BARRETT_MULTIPLIER = 20159;
    private static final int BARRETT_SHIFT = 26;
    private static final int[] MONT_ZETAS_FOR_NTT = new int[]{
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
    private static final int[] MONT_ZETAS_FOR_INVERSE_NTT = new int[]{
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

    private static final int[] MONT_ZETAS_FOR_NTT_MULT = new int[]{
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

    private final int mlKem_k;
    private final int mlKem_eta1;
    private final int mlKem_eta2;

    private final int mlKem_du;
    private final int mlKem_dv;
    private final int encapsulationSize;

    public ML_KEM(String name) {
        switch (name) {
            case "ML-KEM-512" -> {
                mlKem_k = 2;
                mlKem_eta1 = 3;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case "ML-KEM-768" -> {
                mlKem_k = 3;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 10;
                mlKem_dv = 4;
            }
            case "ML-KEM-1024" -> {
                mlKem_k = 4;
                mlKem_eta1 = 2;
                mlKem_eta2 = 2;
                mlKem_du = 11;
                mlKem_dv = 5;
            }
            default -> throw new IllegalArgumentException(
                    // This should never happen.
                    "Invalid algorithm name (" + name + ").");
        }
        encapsulationSize = (mlKem_k * mlKem_du + mlKem_dv) * 32;
    }

    /*
    Classes for the internal K_PKE scheme
     */
    private record K_PKE_EncryptionKey(byte[] keyBytes) {}

    private record K_PKE_DecryptionKey(byte[] keyBytes) {}

    private record K_PKE_KeyPair(
            K_PKE_EncryptionKey publicKey, K_PKE_DecryptionKey privateKey) {
    }

    protected record K_PKE_CipherText(byte[] encryptedBytes) {
    }

    private boolean isValidCipherText(K_PKE_CipherText cipherText) {
        return (cipherText.encryptedBytes.length == encapsulationSize);
    }

    /*
    Classes for internal KEM scheme
     */
    protected record ML_KEM_EncapsulationKey(byte[] keyBytes) {
    }

    protected record ML_KEM_DecapsulationKey(byte[] keyBytes) {
    }

    protected record ML_KEM_KeyPair(
            ML_KEM_EncapsulationKey encapsulationKey,
            ML_KEM_DecapsulationKey decapsulationKey) {
    }

    protected record ML_KEM_EncapsulateResult(
            K_PKE_CipherText cipherText, byte[] sharedSecret) {
    }

    protected int getEncapsulationSize() {
        return encapsulationSize;
    }

    // Encapsulation key checks from section 7.2 of spec
    protected Object checkPublicKey(byte[] pk) throws InvalidKeyException {
        //Encapsulation key type check
        if (pk.length != mlKem_k * 384 + 32) {
            throw new InvalidKeyException("Public key is not the correct size");
        }

        //Encapsulation key modulus check
        int x, y, z, a, b;
        for (int i = 0; i < mlKem_k * 384; i += 3) {
            x = pk[i] & 0xFF;
            y = pk[i + 1] & 0xFF;
            z = pk[i + 2] & 0xFF;
            a = x + ((y & 0xF) << 8);
            b = (y >> 4) + (z << 4);
            if ((a >= ML_KEM_Q) || (b >= ML_KEM_Q)) {
                throw new InvalidKeyException(
                    "Coefficients in public key not in specified range");
            }
        }
        return null;
    }

    // Decapsulation key checks from Section 7.3 of spec
    protected Object checkPrivateKey(byte[] sk) throws InvalidKeyException {
        MessageDigest mlKemH;
        try {
            mlKemH = MessageDigest.getInstance(HASH_H_NAME);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        //Decapsulation key type check
        if (sk.length != mlKem_k * 768 + 96) {
            throw new InvalidKeyException("Private key is not the correct size");
        }

        //Decapsulation hash check
        mlKemH.update(sk, mlKem_k * 384, mlKem_k * 384 + 32);
        byte[] check = Arrays.copyOfRange(sk, mlKem_k * 768 + 32, mlKem_k * 768 + 64);
        if (!MessageDigest.isEqual(mlKemH.digest(), check)) {
            throw new InvalidKeyException("Private key hash check failed");
        }
        return null;
    }

    /*
    Main internal algorithms from Section 6 of specification
     */
    protected ML_KEM_KeyPair generateKemKeyPair(byte[] kem_d, byte[] kem_z) {
        MessageDigest mlKemH;
        try {
            mlKemH = MessageDigest.getInstance(HASH_H_NAME);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen.
            throw new RuntimeException(e);
        }

        //Generate K-PKE keys
        var kPkeKeyPair = generateK_PkeKeyPair(kem_d);
        //encaps key = kPke encryption key
        byte[] encapsKey = kPkeKeyPair.publicKey.keyBytes;

        //Derive decapsulation key = kPkePrivatKey || encapsKey || H(encapsKey) || kem_Z
        byte[] kPkePrivateKey = kPkeKeyPair.privateKey.keyBytes;
        byte[] decapsKey = new byte[encapsKey.length + kPkePrivateKey.length + 64];
        System.arraycopy(kPkePrivateKey, 0, decapsKey, 0, kPkePrivateKey.length);
        Arrays.fill(kPkePrivateKey, (byte)0);
        System.arraycopy(encapsKey, 0, decapsKey,
                kPkePrivateKey.length, encapsKey.length);

        mlKemH.update(encapsKey);
        try {
            mlKemH.digest(decapsKey, kPkePrivateKey.length + encapsKey.length, 32);
        } catch (DigestException e) {
            // This should never happen.
            throw new RuntimeException(e);
        }
        System.arraycopy(kem_z, 0, decapsKey,
            kPkePrivateKey.length + encapsKey.length + 32, 32);

        return new ML_KEM_KeyPair(
            new ML_KEM_EncapsulationKey(encapsKey),
            new ML_KEM_DecapsulationKey(decapsKey));
    }

    protected ML_KEM_EncapsulateResult encapsulate(
            ML_KEM_EncapsulationKey encapsulationKey, byte[] randomMessage) {
        MessageDigest mlKemH;
        MessageDigest mlKemG;
        try {
            mlKemH = MessageDigest.getInstance(HASH_H_NAME);
            mlKemG = MessageDigest.getInstance(HASH_G_NAME);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen.
            throw new RuntimeException(e);
        }

        mlKemH.update(encapsulationKey.keyBytes);
        mlKemG.update(randomMessage);
        mlKemG.update(mlKemH.digest());
        var kHatAndRandomCoins = mlKemG.digest();
        var randomCoins = Arrays.copyOfRange(kHatAndRandomCoins, 32, 64);
        var cipherText = kPkeEncrypt(new K_PKE_EncryptionKey(encapsulationKey.keyBytes),
                randomMessage, randomCoins);
        Arrays.fill(randomCoins, (byte) 0);
        byte[] sharedSecret = Arrays.copyOfRange(kHatAndRandomCoins, 0, 32);
        Arrays.fill(kHatAndRandomCoins, (byte) 0);

        return new ML_KEM_EncapsulateResult(cipherText, sharedSecret);
    }

    protected byte[] decapsulate(ML_KEM_DecapsulationKey decapsulationKey,
                                 K_PKE_CipherText cipherText)
        throws DecapsulateException {

        //Check ciphertext validity
        if (!isValidCipherText(cipherText)) {
            throw new DecapsulateException("Invalid ciphertext");
        }

        int encode12PolyLen = 12 * ML_KEM_N / 8;
        var decapsKeyBytes = decapsulationKey.keyBytes;
        MessageDigest mlKemG;
        SHAKE256 mlKemJ;
        try {
            mlKemG = MessageDigest.getInstance(HASH_G_NAME);
            mlKemJ = new SHAKE256(32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        byte[] kPkePrivateKeyBytes = new byte[mlKem_k * encode12PolyLen];
        System.arraycopy(decapsKeyBytes, 0, kPkePrivateKeyBytes, 0,
                kPkePrivateKeyBytes.length);

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

        // Zero out unused byte arrays containing sensitive data
        Arrays.fill(kPkePrivateKeyBytes, (byte) 0);
        Arrays.fill(kAndCoins, (byte) 0);

        mlKemJ.update(decapsKeyBytes, decapsKeyBytes.length - 32, 32);
        mlKemJ.update(cipherText.encryptedBytes);
        var fakeResult = mlKemJ.digest();
        var computedCipherText = kPkeEncrypt(
                new K_PKE_EncryptionKey(encapsKeyBytes), mCandidate, coins);

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

    /*
    K-PKE subroutines defined in Section 5 of spec
     */
    private K_PKE_KeyPair generateK_PkeKeyPair(byte[] seed) {

        MessageDigest mlKemG;
        SHAKE256 mlKemJ;
        try {
            mlKemG = MessageDigest.getInstance(HASH_G_NAME);
            mlKemJ = new SHAKE256(64 * mlKem_eta1);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen.
            throw new RuntimeException(e);
        }

        mlKemG.update(seed);
        mlKemG.update((byte)mlKem_k);

        var rhoSigma = mlKemG.digest();
        var rho = Arrays.copyOfRange(rhoSigma, 0, 32);
        var sigma = Arrays.copyOfRange(rhoSigma, 32, 64);
        Arrays.fill(rhoSigma, (byte)0);

        var keyGenA = generateA(rho, false);

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
        Arrays.fill(sigma, (byte)0);

        short[][] keyGenSHat = mlKemVectorNTT(keyGenS);
        mlKemVectorReduce(keyGenSHat);
        short[][] keyGenEHat = mlKemVectorNTT(keyGenE);

        short[][] keyGenTHat =
                mlKemMatrixVectorMuladd(keyGenA, keyGenSHat, keyGenEHat);

        byte[] pkEncoded = new byte[(mlKem_k * ML_KEM_N * 12) / 8 + rho.length];
        byte[] skEncoded = new byte[(mlKem_k * ML_KEM_N * 12) / 8];
        for (int i = 0; i < mlKem_k; i++) {
            encodePoly12(keyGenTHat[i], pkEncoded, i * ((ML_KEM_N * 12) / 8));
            encodePoly12(keyGenSHat[i], skEncoded, i * ((ML_KEM_N * 12) / 8));
            Arrays.fill(keyGenEHat[i], (short) 0);
            Arrays.fill(keyGenSHat[i], (short) 0);
        }
        System.arraycopy(rho, 0,
                pkEncoded, (mlKem_k * ML_KEM_N * 12) / 8, rho.length);

        return new K_PKE_KeyPair(
                new K_PKE_EncryptionKey(pkEncoded),
                new K_PKE_DecryptionKey(skEncoded));
    }

    private K_PKE_CipherText kPkeEncrypt(
            K_PKE_EncryptionKey publicKey, byte[] message, byte[] sigma) {
        short[][] zeroes = shortMatrixAlloc(mlKem_k, ML_KEM_N);
        byte[] pkBytes = publicKey.keyBytes;
        byte[] rho = Arrays.copyOfRange(pkBytes,
                pkBytes.length - 32, pkBytes.length);
        byte[] tHatBytes = Arrays.copyOfRange(pkBytes,
                0, pkBytes.length - 32);
        var encryptTHat = decodeVector(12, tHatBytes);
        var encryptA = generateA(rho, true);
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
        encryptV = mlKemAddPoly(encryptV, encryptE2, decompressDecode(message));
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
        int uBytesLen = mlKem_k * mlKem_du * ML_KEM_N / 8;
        byte[] uBytes = Arrays.copyOfRange(cipherText.encryptedBytes,
                0, uBytesLen);
        byte[] vBytes = Arrays.copyOfRange(cipherText.encryptedBytes,
                uBytesLen, cipherText.encryptedBytes.length);
        var decryptU = decompressVector(decodeVector(mlKem_du, uBytes), mlKem_du);
        var decryptV = decompressPoly(
                decodePoly(mlKem_dv, vBytes, 0), mlKem_dv);
        var decryptSHat = decodeVector(12, privateKey.keyBytes);
        var decryptSU = mlKemInverseNTT(
                mlKemVectorScalarMult(decryptSHat, mlKemVectorNTT(decryptU)));
        for (int i = 0; i < mlKem_k; i++) {
            Arrays.fill(decryptSHat[i], (short) 0);
        }
        decryptV = mlKemSubtractPoly(decryptV, decryptSU);
        Arrays.fill(decryptSU, (short) 0);

        return encodeCompress(decryptV);
    }

    /*
    Sampling algorithms from Section 4.2.2 of the spec
     */

    //Combination of SampleNTT and KeyGen/Encrypt generation of A
    private short[][][] generateA(byte[] rho, Boolean transposed) {
        short[][][] a = new short[mlKem_k][mlKem_k][];

        int nrPar = 2;
        int rhoLen = rho.length;
        byte[] seedBuf = new byte[XOF_BLOCK_LEN];
        System.arraycopy(rho, 0, seedBuf, 0, rho.length);
        seedBuf[rhoLen + 2] = 0x1F;
        seedBuf[XOF_BLOCK_LEN - 1] = (byte)0x80;
        byte[][] xofBufArr = byteMatrixAlloc(nrPar, XOF_BLOCK_LEN + XOF_PAD);
        int[] iIndex = new int[nrPar];
        int[] jIndex = new int[nrPar];

        short[] parsedBuf = new short[(xofBufArr[0].length / 3) * 2];

        int parInd = 0;
        boolean allDone;
        int[] ofs = new int[nrPar];
        Arrays.fill(ofs, 0);
        short[][] aij = new short[nrPar][];
        try {
            Shake128Parallel parXof = new Shake128Parallel(xofBufArr);

            for (int i = 0; i < mlKem_k; i++) {
                for (int j = 0; j < mlKem_k; j++) {
                    System.arraycopy(seedBuf, 0, xofBufArr[parInd], 0, seedBuf.length);
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
                    aij[parInd] = new short[ML_KEM_N];
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
                                if (ofs[k] < ML_KEM_N) {
                                    twelve2Sixteen(xofBufArr[k], 0,
                                            parsedBuf, (XOF_BLOCK_LEN / 3) * 2);
                                }
                                while ((ofs[k] < ML_KEM_N) &&
                                        (parsedOfs < (XOF_BLOCK_LEN / 3) * 2)) {
                                    tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                                    if (tmp < ML_KEM_Q) {
                                        aij[k][ofs[k]] = (short) tmp;
                                        ofs[k]++;
                                    }
                                    tmp = parsedBuf[parsedOfs++] & 0xFFFF;
                                    if ((ofs[k] < ML_KEM_N) && (tmp < ML_KEM_Q)) {
                                        aij[k][ofs[k]] = (short) tmp;
                                        ofs[k]++;
                                    }
                                }
                                if (ofs[k] < ML_KEM_N) {
                                    allDone = false;
                                }
                            }
                        }

                        for (int k = 0; k < parInd; k++) {
                            a[iIndex[k]][jIndex[k]] = aij[k];
                        }
                        parInd = 0;
                    }
                }
            }
        } catch (InvalidAlgorithmParameterException e) {
            // This should never happen since xofBufArr is of the correct size
            throw new RuntimeException("Internal error.");
        }

        return a;
    }

    private short[] centeredBinomialDistribution(int eta, byte[] input) {
        if (eta == 2) return centeredBinomialDistribution2(input);
        if (eta == 3) return centeredBinomialDistribution3(input);
        // Below for arbitrary eta, not used in ML-KEM
        short[] result = new short[ML_KEM_N];
        int index = 0;
        int shift = 8;
        int currentByte = input[0];
        for (int m = 0; m < ML_KEM_N; m++) {
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
        short[] result = new short[ML_KEM_N];
        // A 64-bit number divided into 16 4-bits, representing all 4-bit
        // patterns of input with values are CBD samples in [-2, 2].
        long bits = 0x0112f001f001eff0L;
        int j = 0;

        for (int i = 0; i < input.length; i++) {
            // One byte has 2 4-bit sequences, each producing a sample
            int a = input[i];
            int shift1 = (a << 2) & 0x3c;
            int shift2 = (a >> 2) & 0x3c;
            result[j++] = (short) ((bits << shift1) >> 60);
            result[j++] = (short) ((bits << shift2) >> 60);
        }

        return result;
    }

    private short[] centeredBinomialDistribution3(byte[] input) {
        short[] result = new short[ML_KEM_N];
        // A 32-bit number divided into 8 4-bits, representing all 3-bits
        // patterns (one half of a 6-bit input) with values in [0, 3].
        int bits = 0x01121223;
        int j = 0;

        for (int i = 0; i < input.length; i += 3) {
            // Every 3 bytes has 24 bits that produce 4 6-bit sequences.
            // We calculate values for both halves of each sequence and
            // do the subtraction to get the sample
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

        return result;
    }

    /*
    NTT algorithms from Section 4.3 of the specification
     */

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
    private short[][] mlKemVectorReduce(short[][] vector) {
        for (int i = 0; i < mlKem_k; i++) {
            mlKemBarrettReduce(vector[i]);
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
        int[] coeffs = new int[ML_KEM_N];
        for (int m = 0; m < ML_KEM_N; m++) {
            coeffs[m] = poly[m];
        }
        seilerNTT(coeffs);
        for (int m = 0; m < ML_KEM_N; m++) {
            poly[m] = (short) coeffs[m];
        }
    }

    // The elements of poly should be in the range [-mlKem_q, mlKem_q]
    // The elements of poly at return will be in the range of [0, mlKem_q]
    private void mlKemNTT(short[] poly) {
        assert poly.length == ML_KEM_N;
        implKyberNtt(poly, montZetasForVectorNttArr);
        mlKemBarrettReduce(poly);
    }

    @IntrinsicCandidate
    static int implKyberInverseNtt(short[] poly, short[] zetas) {
        implKyberInverseNttJava(poly);
        return 1;
    }

    static void implKyberInverseNttJava(short[] poly) {
        int[] coeffs = new int[ML_KEM_N];
        for (int m = 0; m < ML_KEM_N; m++) {
            coeffs[m] = poly[m];
        }
        seilerInverseNTT(coeffs);
        for (int m = 0; m < ML_KEM_N; m++) {
            poly[m] = (short) coeffs[m];
        }
    }

    // Works in place, but also returns its (modified) input so that it can
    // be used in expressions
    private short[] mlKemInverseNTT(short[] poly) {
        assert poly.length == ML_KEM_N;
        implKyberInverseNtt(poly, montZetasForVectorInverseNttArr);
        return poly;
    }

    // Implements the ML_KEM NTT algorithm similarly to that described
    // in https://eprint.iacr.org/2018/039.pdf .
    // It works in place, replaces the elements of the input coeffs array
    // by the transformed representation.
    // The input elements should be in the range [-MONT_Q, MONT_Q].
    // The result elements will fit into the range of short
    // (i.e. [-32768, 32767]).
    private static void seilerNTT(int[] coeffs) {
        int dimension = ML_KEM_N;
        int zetaIndex = 0;
        for (int l = dimension / 2; l > 1; l /= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = montMul(MONT_ZETAS_FOR_NTT[zetaIndex], coeffs[j + l]);
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
    // The input elements should be in the range [-MONT_Q, MONT_Q).
    // The output elements will be in the range (-MONT_Q, MONT_Q).
    private static void seilerInverseNTT(int[] coeffs) {
        int dimension = ML_KEM_N;
        int zetaIndex = MONT_ZETAS_FOR_NTT.length - 1;
        for (int l = 2; l < dimension; l *= 2) {
            for (int s = 0; s < dimension; s += 2 * l) {
                for (int j = s; j < s + l; j++) {
                    int tmp = coeffs[j];
                    coeffs[j] = (tmp + coeffs[j + l]);
                    coeffs[j + l] = montMul(
                            tmp - coeffs[j + l],
                            -MONT_ZETAS_FOR_NTT[zetaIndex]);
                }
                zetaIndex--;
            }
        }

        for (int i = 0; i < dimension; i++) {
            int r = montMul(coeffs[i], MONT_DIM_HALF_INVERSE);
            coeffs[i] = r;
        }
    }

    // Performs A o b + c where
    // A is a mlKem_k by mlKem_k matrix,
    // b and c are mlKem_k long vectors of degree ML_KEM_N - 1
    // polynomials in the NTT domain representation.
    // The coefficients in the result are in the range [0, ML_KEM_Q).
    private short[][] mlKemMatrixVectorMuladd(
            short[][][] a, short[][] b, short[][] c) {
        short[] product = new short[ML_KEM_N];

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
    // of degree ML_KEM_N - 1 polynomials in the NTT representation,
    // with coefficients in the range [-ML_KEM_Q, ML_KEM_Q].
    // The coefficients in the result are in the range [0, ML_KEM_Q).
    private short[] mlKemVectorScalarMult(short[][] a, short[][] b) {
        short[] result = new short[ML_KEM_N];
        short[] product = new short[ML_KEM_N];

        int j;
        for (j = 0; j < mlKem_k; j++) {
            nttMult(product, a[j], b[j]);
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
        for (int m = 0; m < ML_KEM_N / 2; m++) {

            int a0 = ntta[2 * m];
            int a1 = ntta[2 * m + 1];
            int b0 = nttb[2 * m];
            int b1 = nttb[2 * m + 1];
            int r = montMul(a0, b0) +
                    montMul(montMul(a1, b1), MONT_ZETAS_FOR_NTT_MULT[m]);
            result[2 * m] = (short) montMul(r, MONT_R_SQUARE_MOD_Q);
            result[2 * m + 1] = (short) montMul(
                    (montMul(a0, b1) + montMul(a1, b0)), MONT_R_SQUARE_MOD_Q);
        }
    }

    // Multiplies two polynomials represented in the NTT domain.
    // The result is a representation of the product still in the NTT domain.
    // The coefficients in the result are in the range (-mlKem_q, mlKem_q).
    private void nttMult(short[] result, short[] ntta, short[] nttb) {
        assert (result.length == ML_KEM_N) && (ntta.length == ML_KEM_N) &&
                (nttb.length == ML_KEM_N);
        implKyberNttMult(result, ntta, nttb, montZetasForVectorNttMultArr);
    }

    // Adds the vector of polynomials b to a in place, i.e. a will hold
    // the result. It also returns (the modified) a so that it can be used
    // in an expression.
    // The coefficients in all polynomials of both vectors are supposed to be
    // greater than -ML_KEM_Q and less than ML_KEM_Q.
    // The coefficients in the result are nonnegative and less than ML_KEM_Q.
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
        for (int m = 0; m < ML_KEM_N; m++) {
            int r = a[m] + b[m] + ML_KEM_Q; // This makes r > - ML_KEM_Q
            a[m] = (short) r;
        }
    }

    // Adds the polynomial b to a in place, i.e. (the modified) a will hold
    // the result.
    // The coefficients are supposed be greater than -ML_KEM_Q in a and
    // greater than -ML_KEM_Q and less than ML_KEM_Q in b.
    // The coefficients in the result are greater than -ML_KEM_Q.
    private short[] mlKemAddPoly(short[] a, short[] b) {
        assert (a.length == ML_KEM_N) && (b.length == ML_KEM_N);
        implKyberAddPoly(a, a, b);
        return a;
    }

    @IntrinsicCandidate
    static int implKyberAddPoly(short[] result, short[] a, short[] b, short[] c) {
        implKyberAddPolyJava(result, a, b, c);
        return 1;
    }

    static void implKyberAddPolyJava(short[] result, short[] a, short[] b, short[] c) {
        for (int m = 0; m < ML_KEM_N; m++) {
            int r = a[m] + b[m] + c[m] + 2 * ML_KEM_Q; // This makes r > - ML_KEM_Q
            result[m] = (short) r;
        }
    }

    // Adds the polynomials b and c to a in place, i.e. 'a' will hold the sum.
    // 'a' is also returned so that this function can be used in an expression.
    // The coefficients in all three polynomials are supposed to be
    // greater than -ML_KEM_Q and less than ML_KEM_Q.
    // The coefficients in the result are nonnegative and less than ML_KEM_Q.
    private short[] mlKemAddPoly(short[] a, short[] b, short[] c) {
        assert (a.length == ML_KEM_N) && (b.length == ML_KEM_N) &&
                (c.length == ML_KEM_N);
        implKyberAddPoly(a, a, b, c);
        mlKemBarrettReduce(a);
        return a;
    }

    // Subtracts the polynomial b from a in place, i.e. the result is
    // stored in a. It also returns (the modified) a, so that it can be used
    // in an expression.
    // The coefficiens in both are assumed to be greater than -ML_KEM_Q
    // and less than ML_KEM_Q.
    // The coefficients in the result are nonnegative and less than ML_KEM_Q.
    private short[] mlKemSubtractPoly(short[] a, short[] b) {
        for (int m = 0; m < ML_KEM_N; m++) {
            int r = a[m] - b[m] + ML_KEM_Q; // This makes r > -ML_KEM_Q
            a[m] = (short) r;
        }
        mlKemBarrettReduce(a);
        return a;
    }

    private byte[] encodeVector(int l, short[][] vector) {
        return encodeVector(l, vector, mlKem_k);
    }

    private static byte[] encodeVector(int l, short[][] vector, int k) {
        int encodedPolyLength = ML_KEM_N * l / 8;
        byte[] result = new byte[k * encodedPolyLength];

        for (int i = 0; i < k; i++) {
            byte[] resultBytes = encodePoly(l, vector[i]);
            System.arraycopy(resultBytes, 0,
                    result, i * encodedPolyLength, encodedPolyLength);
        }
        return result;
    }

    private static void encodePoly12(short[] poly, byte[] result, int resultOffs) {
        int low;
        int high;
        for (int m = 0; m < ML_KEM_N / 2; m++) {
            low = poly[2 * m];
            low += ((low >> 31) & ML_KEM_Q);
            low = low & 0xfff;
            high = poly[2 * m + 1];
            high += ((high >> 31) & ML_KEM_Q);
            high = high & 0xfff;

            result[resultOffs++] = (byte) low;
            result[resultOffs++] = (byte) ((high << 4) + (low >> 8));
            result[resultOffs++] = (byte) (high >> 4);
        }
    }

    private static void encodePoly4(short[] poly, byte[] result) {
        for (int m = 0; m < ML_KEM_N / 2; m++) {
            result[m] = (byte) ((poly[2 * m] & 0xf) + (poly[2 * m + 1] << 4));
        }
    }

    // Computes the byte array containing the packed l-bit representation
    // of a polynomial. The coefficients in poly should be either nonnegative
    // or elements of Z_(ML_KEM_Q) represented by a 16-bit value
    // between -ML_KEM_Q and ML_KEM_Q.
    private static byte[] encodePoly(int l, short[] poly) {
        byte[] result = new byte[ML_KEM_N / 8 * l];
        if (l == 4) {
            encodePoly4(poly, result);
        } else {
            int mask = (1 << l) - 1;
            int shift = 0;
            int index = 0;
            int current = 0;
            for (int m = 0; m < ML_KEM_N; m++) {
                int currentShort = poly[m];
                currentShort += (currentShort >> 31) & ML_KEM_Q;
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

    private static byte[] encodeCompress(short[] poly) {
        byte[] result = new byte[ML_KEM_N / 8];
        int xx;
        int currentByte;
        for (int i = 0; i < ML_KEM_N / 8; i++) {
            currentByte = 0;
            xx = poly[i * 8];
            currentByte |= (((832 - xx) & (xx - 2497)) >>> 31);
            xx = poly[i * 8 + 1];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 30) & 2);
            xx = poly[i * 8 + 2];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 29) & 4);
            xx = poly[i * 8 + 3];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 28) & 8);
            xx = poly[i * 8 + 4];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 27) & 16);
            xx = poly[i * 8 + 5];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 26) & 32);
            xx = poly[i * 8 + 6];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 25) & 64);
            xx = poly[i * 8 + 7];
            currentByte |= ((((832 - xx) & (xx - 2497)) >>> 24) & 128);
            result[i] = (byte) currentByte;
        }
        return result;
    }

    private short[][] decodeVector(int l, byte[] encodedVector) {
        short[][] result = new short[mlKem_k][];
        for (int i = 0; i < mlKem_k; i++) {
            result[i] = decodePoly(l, encodedVector, (i * ML_KEM_N * l) / 8);
        }
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
    // are such that condensed can be read in 96-byte chunks and
    // parsed can be written in 64 shorts chunks except for the last chunk
    // that can be either 48 or 64 shorts. In other words,
    // if (i - 1) * 64 < parsedLengths <= i * 64 then
    // parsed.length should be either i * 64 or (i-1) * 64 + 48 and
    // condensed.length should be at least index + i * 96.
    private void twelve2Sixteen(byte[] condensed, int index,
                                short[] parsed, int parsedLength) {
        int i = parsedLength / 64;
        int remainder = parsedLength - i * 64;
        if (remainder != 0) {
            i++;
        }
        assert ((remainder == 0) || (remainder == 48)) &&
                (index + i * 96 <= condensed.length);
        implKyber12To16(condensed, index, parsed, parsedLength);
    }

    private static void decodePoly5(byte[] condensed, int index, short[] parsed) {
        int j = index;
        for (int i = 0; i < ML_KEM_N; i += 8) {
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
        for (int i = 0; i < ML_KEM_N / 2; i++) {
            parsed[i * 2] = (short) (condensed[i + index] & 0xf);
            parsed[i * 2 + 1] = (short) ((condensed[i + index] >>> 4) & 0xf);
        }
    }

    // Recovers the 16-bit coefficients of a polynomial from a byte array
    // containing a packed l-bit representation.
    // The recovered coefficients will be in the range 0 <= coeff < 2^l .
    private short[] decodePoly(int l, byte[] input, int index) {
        short[] poly = new short[ML_KEM_N];
        if (l == 12) {
            twelve2Sixteen(input, index, poly, ML_KEM_N);
        } else if (l == 4) {
            decodePoly4(input, index, poly);
        } else if (l == 5) {
            decodePoly5(input, index, poly);
        } else {
            int mask = (1 << l) - 1;
            int top = 0;
            int shift = 0;
            int acc = 0;
            for (int m = 0; m < ML_KEM_N; m++) {
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

    // Prerequisite: for all m, 0 <= poly[m] < ML_KEM_Q, d == 4 or d == 5
    // Replaces poly[m] with round(2^d * poly[m] / ML_KEM_Q) mod 2^d for all m,
    // where round(z) is the integer closest to z, i.e.
    // compresses a polynomial in place.
    private static short[] compressPoly4_5(short[] poly, int d) {
        int xx;
        for (int m = 0; m < ML_KEM_N; m++) {
            xx = (poly[m] << d) + ML_KEM_Q / 2;
            poly[m] = (short)((xx * 315) >> 20);
        }
        return poly;
    }

    // Prerequisite: for all m, 0 <= poly[m] < ML_KEM_Q, d == 10 or d == 11
    // Replaces poly[m] with round(2^d * poly[m] / ML_KEM_Q) mod 2^d for all m,
    // where round(z) is the integer closest to z, i.e.
    // compresses a polynomial in place.
    private static short[] compressPoly10_11(short[] poly, int d) {
        long xx;
        for (int m = 0; m < ML_KEM_N; m++) {
            xx = (poly[m] << d) + ML_KEM_Q / 2;
            poly[m] = (short)((xx * 161271L) >> 29);
        }
        return poly;
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

    // Decompresses a polynomial in place, i.e. it modifies the coefficients
    // in its input. It returns its (modified) input so that the function can
    // be used in an expression.
    // Prerequisite: 0 <= x[i] < 2^d < ML_KEM_Q .
    // Computes Round(ML_KEM_Q * x[i] / 2^d),
    // where Round(z) is the integer closest to z,
    // for each coefficient of a polynomial
    private static short[] decompressPoly(short[] poly, int d) {
        for (int m = 0; m < ML_KEM_N; m++) {
            int qx = ML_KEM_Q * poly[m];
            poly[m] = (short) ((qx >> d) + ((qx >> (d - 1)) & 1));
        }
        return poly;
    }

    private static short[] decompressDecode(byte[] input) {
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

    @IntrinsicCandidate
    static int implKyberBarrettReduce(short[] coeffs) {
        implKyberBarrettReduceJava(coeffs);
        return 1;
    }

    static void implKyberBarrettReduceJava(short[] poly) {
        for (int m = 0; m < ML_KEM_N; m++) {
            int tmp = ((int) poly[m] * BARRETT_MULTIPLIER) >> BARRETT_SHIFT;
            poly[m] = (short) (poly[m] - tmp * ML_KEM_Q);
        }
    }

    // The input elements can have any short value.
    // Modifies poly such that upon return poly[i] will be
    // in the range [0, ML_KEM_Q] and will be congruent with the original
    // poly[i] modulo ML_KEM_Q, for all i in [0, ML_KEM_N).
    // At return, poly[i] == ML_KEM_Q if and only if the original poly[i] was
    // a negative integer multiple of ML_KEM_Q.
    // That means that if the original poly[i] > -ML_KEM_Q then at return it
    // will be in the range [0, ML_KEM_Q), i.e. it will be the canonical
    // representative of its residue class.
    private static void mlKemBarrettReduce(short[] poly) {
        assert poly.length == ML_KEM_N;
        implKyberBarrettReduce(poly);
    }

    // Precondition: -(2^MONT_R_BITS -1) * MONT_Q <= b * c < (2^MONT_R_BITS - 1) * MONT_Q
    // Computes b * c * 2^-MONT_R_BITS mod MONT_Q
    // The result is between -MONT_Q and MONT_Q
    private static int montMul(int b, int c) {
        int a = b * c;
        int aHigh = a >> MONT_R_BITS;
        int aLow = a & ((1 << MONT_R_BITS) - 1);
        // signed low product
        int m = ((MONT_Q_INV_MOD_R * aLow) << (32 - MONT_R_BITS)) >> (32 - MONT_R_BITS);

        return (aHigh - ((m * MONT_Q) >> MONT_R_BITS)); // subtract signed high product
    }

    // For multidimensional array initialization, manually allocating each entry is
    // faster than doing the entire initialization in one go
    static short[][] shortMatrixAlloc(int first, int second) {
        short[][] res = new short[first][];
        for (int i = 0; i < first; i++) {
            res[i] = new short[second];
        }
        return res;
    }

    static byte[][] byteMatrixAlloc(int first, int second) {
        byte[][] res = new byte[first][];
        for (int i = 0; i < first; i++) {
            res[i] = new byte[second];
        }
        return res;
    }
}
