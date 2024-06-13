/*
 * Copyright (c) 2022, 2023, Oracle, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @summary Basic array hashCode functionality
 * @run main/othervm --add-exports java.base/jdk.internal.util=ALL-UNNAMED
 *     --add-opens java.base/jdk.internal.util=ALL-UNNAMED -Xcomp -Xbatch HashCode
 */

import java.lang.reflect.Method;
import java.util.Arrays;

public class HashCode {
    private static String[] tests = { "", " ", "a", "abcdefg",
            "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season of Light, it was the season of Darkness, it was the spring of hope, it was the winter of despair, we had everything before us, we had nothing before us, we were all going direct to Heaven, we were all going direct the other way- in short, the period was so far like the present period, that some of its noisiest authorities insisted on its being received, for good or for evil, in the superlative degree of comparison only.  -- Charles Dickens, Tale of Two Cities",
            "C'était le meilleur des temps, c'était le pire des temps, c'était l'âge de la sagesse, c'était l'âge de la folie, c'était l'époque de la croyance, c'était l'époque de l'incrédulité, c'était la saison de la Lumière, c'était C'était la saison des Ténèbres, c'était le printemps de l'espoir, c'était l'hiver du désespoir, nous avions tout devant nous, nous n'avions rien devant nous, nous allions tous directement au Ciel, nous allions tous directement dans l'autre sens bref, la période ressemblait tellement à la période actuelle, que certaines de ses autorités les plus bruyantes ont insisté pour qu'elle soit reçue, pour le bien ou pour le mal, au degré superlatif de la comparaison seulement. -- Charles Dickens, Tale of Two Cities (in French)",
            "禅道修行を志した雲水は、一般に参禅のしきたりを踏んだうえで一人の師につき、各地にある専門道場と呼ばれる養成寺院に入門し、与えられた公案に取り組むことになる。公案は、師家（老師）から雲水が悟りの境地へと進んで行くために手助けとして課す問題であり、悟りの境地に達していない人には容易に理解し難い難問だが、屁理屈や詭弁が述べられているわけではなく、頓知や謎かけとも異なる。"
    };

    byte[][] zeroes = new byte[64][];
    private static byte[][] testBytes = new byte[tests.length][];
    private static short[][] testShorts = new short[tests.length][];
    private static char[][] testChars = new char[tests.length][];
    private static int[][] testInts = new int[tests.length][];

    private static int[] expected = { 1, 63, 128, 536518979, -1174896354, -1357593156, 428276276};
    private static int[] expectedUnsigned = { 1, 63, 128, 536518979, -1174896354, 584369596, -2025326028};

    public static void main(String[] args) throws Exception {

        // Deep introspection into range-based hash functions
        Class<?> arraysSupport = Class.forName("jdk.internal.util.ArraysSupport");
        Method vectorizedHashCode = arraysSupport.getDeclaredMethod("vectorizedHashCode", Object.class, int.class, int.class, int.class, int.class);
        vectorizedHashCode.setAccessible(true);

        for (int i = 0; i < tests.length; i++) {
            testBytes[i] = tests[i].getBytes("UTF-8");
            int len = testBytes[i].length;
            testChars[i] = new char[len];
            testShorts[i] = new short[len];
            testInts[i] = new int[len];
            for (int j = 0; j < len; j++) {
                testChars[i][j] = (char) testBytes[i][j];
                testShorts[i][j] = testBytes[i][j];
                testInts[i][j] = testBytes[i][j];
            }
        }

        boolean failed = false;
        try {
            int zeroResult = 1;
            for (int i = 0; i < 64; i++) {
                byte[] zeroes = new byte[i];
                byte[] extraZeroes = new byte[i + 47];
                for (int j = 0; j < 10_000; j++) {
                    int hashCode = Arrays.hashCode(zeroes);
                    if (hashCode != zeroResult) {
                        throw new RuntimeException("byte[] \"" + Arrays.toString(zeroes) + "\": "
                                + " e = " + zeroResult
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                    hashCode = (int) vectorizedHashCode.invoke(null, extraZeroes, 17, i, 1, /* ArraysSupport.T_BYTE */ 8);
                    if (hashCode != zeroResult) {
                        throw new RuntimeException("byte[] subrange \"" + Arrays.toString(extraZeroes)
                                + "\" at offset 17, limit " + i + ": "
                                + " e = " + zeroResult
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                }
                zeroResult *= 31;
            }
            for (int i = 0; i < tests.length; i++) {
                for (int j = 0; j < 64; j++) {
                    int e = expected[i];
                    int hashCode = Arrays.hashCode(testBytes[i]);
                    if (hashCode != e) {
                        throw new RuntimeException("byte[] \"" + Arrays.toString(testBytes[i]) + "\": "
                                + " e = " + e
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                }
            }
            System.out.println("byte[] tests passed");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            failed = true;
        }

        try {
            for (int i = 0; i < tests.length; i++) {
                for (int j = 0; j < 64; j++) {
                    int e = expected[i];
                    int hashCode = Arrays.hashCode(testShorts[i]);
                    if (hashCode != e) {
                        throw new RuntimeException("short[] \"" + Arrays.toString(testShorts[i]) + "\": "
                                + " e = " + e
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                }
            }
            System.out.println("short[] tests passed");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            failed = true;
        }

        try {
            for (int i = 0; i < tests.length; i++) {
                for (int j = 0; j < 64; j++) {
                    int e = expected[i];
                    int hashCode = Arrays.hashCode(testInts[i]);
                    if (hashCode != e) {
                        throw new RuntimeException("int[] \"" + Arrays.toString(testInts[i]) + "\": "
                                + " e = " + e
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                }
            }
            System.out.println("int[] tests passed");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            failed = true;
        }

        try {
            for (int i = 0; i < tests.length; i++) {
                for (int j = 0; j < 64; j++) {
                    int e = expectedUnsigned[i];
                    int hashCode = Arrays.hashCode(testChars[i]);
                    if (hashCode != e) {
                        throw new RuntimeException("char[] \"" + Arrays.toString(testChars[i]) + "\": "
                                + " e = " + e
                                + ", hashCode = " + hashCode
                                + ", repetition = " + j);
                    }
                }
            }
            System.out.println("char[] tests passed");
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            failed = true;
        }

        if (failed) {
            throw new RuntimeException("Some tests failed");
        }
    }
}
