/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A collection of utilities for String manipulation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class StringUtils {

    /**Converts the given String to lower case using the {@link Locale#US US Locale}. The result
     * is independent of the default Locale in the current JVM instance.
     */
    public static String toLowerCase(String source) {
        return source.toLowerCase(Locale.US);
    }

    /**Converts the given String to upper case using the {@link Locale#US US Locale}. The result
     * is independent of the default Locale in the current JVM instance.
     */
    public static String toUpperCase(String source) {
        return source.toUpperCase(Locale.US);
    }

    /**Case insensitive version of {@link String#indexOf(java.lang.String)}. Equivalent to
     * {@code text.indexOf(str)}, except the matching is case insensitive.
     */
    public static int indexOfIgnoreCase(String text, String str) {
        return indexOfIgnoreCase(text, str, 0);
    }

    /**Case insensitive version of {@link String#indexOf(java.lang.String, int)}. Equivalent to
     * {@code text.indexOf(str, startIndex)}, except the matching is case insensitive.
     */
    public static int indexOfIgnoreCase(String text, String str, int startIndex) {
        Matcher m = Pattern.compile(Pattern.quote(str), Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find(startIndex) ? m.start() : -1;
    }

    /**Call {@link #of(String, String)} to calculate the distance.
     *
     * <h2>Usage Examples</h2>
     *
     * Pick top three vocabulary words whose normalized distance from
     * the misspelled word is no greater than one-third.
     *
     * {@snippet :
     *     record Pair(String word, int distance) { }
     *
     *     var suggestions = vocabulary.stream()
     *             .map(v -> new Pair(v, DamerauLevenshteinDistance.of(v, misspelledWord)))
     *             .filter(p -> Double.compare(1.0 / 3, ((double) p.distance()) / p.word().length()) >= 0)
     *             .sorted(Comparator.comparingDouble(Pair::distance))
     *             .limit(3)
     *             .toList();
     * }
     */
    public static final class DamerauLevenshteinDistance {

        /*
         * This is a Java implementation of the algorithm from "An Extension of
         * the String-to-String Correction Problem" by R. Lowrance and
         * R. A. Wagner (https://dl.acm.org/doi/10.1145/321879.321880).
         * That algorithm is O(|a|*|b|) in both space and time.
         *
         * This implementation encapsulates arrays and (most of) strings behind
         * methods to accommodate for algorithm indexing schemes which are -1,
         * 0, and 1 based and to offset memory and performance overhead if any
         * strings in the pair contain non-ASCII symbols.
         */

        private final int INF;
        private final int[][] h;
        private final String a;
        private final String b;

        private static final int Wi = 1; // insert
        private static final int Wd = 1; // delete
        private static final int Wc = 1; // change
        private static final int Ws = 1; // interchange

        static {
            assert 2L * Ws >= Wi + Wd; // algorithm requirement
        }

        private int[] smallDA;
        private Map<Character, Integer> bigDA;

        /** {@return the edit distance between two strings}
         * The distance returned from this method has the following properties:
         * <ol>
         *     <li> {@code a.equals(b) && of(a, b) == 0) || (!a.equals(b) && of(a, b) > 0)}
         *     <li> {@code of(a, b) == of(b, a)}
         *     <li> {@code of(a, b) + of(b, c) >= of(a, c)}
         * </ol>
         *
         * @implSpec
         * This method is safe to be called by multiple threads.
         * @throws NullPointerException if any of the two strings are null
         * @throws ArithmeticException if any step of the calculation
         *                             overflows an int
         */
        public static int of(String a, String b) {
            return new DamerauLevenshteinDistance(a, b).calculate();
        }

        private int calculate() {
            for (int i = 0; i <= a.length(); i++) {
                h(i, 0, i * Wd);
                h(i, -1, INF);
            }
            for (int j = 0; j <= b.length(); j++) {
                h(0, j, j * Wi);
                h(-1, j, INF);
            }
            // algorithm's line #8 that initializes DA is not needed here
            // because this class encapsulates DA and initializes it
            // separately
            for (int i = 1; i <= a.length(); i++) {
                int db = 0;
                for (int j = 1; j <= b.length(); j++) {
                    int i1 = da(characterAt(b, j));
                    int j1 = db;
                    boolean eq = characterAt(a, i) == characterAt(b, j);
                    int d = eq ? 0 : Wc;
                    if (eq) {
                        db = j;
                    }
                    int m = min(h(i - 1, j - 1) + d,
                            h(i, j - 1) + Wi,
                            h(i - 1, j) + Wd,
                            h(i1 - 1, j1 - 1) + (i - i1 - 1) * Wd + Ws + (j - j1 - 1) * Wi);
                    h(i, j, m);
                }
                da(characterAt(a, i), i);
            }
            return h(a.length(), b.length());
        }

        private int characterAt(String s, int i) {
            return s.charAt(i - 1);
        }

        private void h(int i, int j, int value) {
            h[i + 1][j + 1] = value;
        }

        private int h(int i, int j) {
            return h[i + 1][j + 1];
        }

        /*
         * This implementation works with UTF-16 strings, but favours strings
         * that comprise ASCII characters. Measuring distance between a pair
         * of ASCII strings is likely to be a typical use case for this
         * implementation.
         *
         * If a character for which the value is to be stored does not fit into
         * the ASCII range, this implementation switches to a different storage
         * dynamically. Since neither string lengths nor character values
         * change, any state accumulated so far, including any loops and local
         * variables, remains valid.
         *
         * Note, that if the provided character were a surrogate and this
         * implementation dealt with code points, which it does not, dynamic
         * switching of the storage would not be enough. The complete
         * representation would need to be changed. That would entail
         * discarding any accumulated state and repeating the computation.
         */

        private int da(int i) {
            if (smallDA != null && i < '\u0080') {
                return smallDA[i];
            }
            // if a character cannot be found, it means that the character
            // hasn't been updated, which means that the associated value
            // is the default value, which is 0
            if (bigDA != null) {
                Integer v = bigDA.get((char) i);
                return v == null ? 0 : v;
            } else {
                return 0;
            }
        }

        private void da(int i, int value) {
            if (bigDA == null && i < '\u0080') {
                if (smallDA == null) {
                    smallDA = new int[127];
                }
                smallDA[i] = value;
            } else {
                if (bigDA == null) {
                    bigDA = new HashMap<>();
                    if (smallDA != null) { // rebuild DA accumulated so far
                        for (int j = 0; j < smallDA.length; j++) {
                            int v = smallDA[j];
                            if (v != 0)
                                bigDA.put((char) j, v);
                        }
                        smallDA = null; // no longer needed
                    }
                }
                bigDA.put((char) i, value);
            }
            assert smallDA == null ^ bigDA == null; // at most one in use
        }

        private static int min(int a, int b, int c, int d) {
            return Math.min(a, Math.min(b, Math.min(c, d)));
        }

        private DamerauLevenshteinDistance(String a, String b) {
            this.a = a;
            this.b = b;
            this.h = new int[this.a.length() + 2][this.b.length() + 2];
            INF = this.a.length() * Wd + this.b.length() * Wi + 1;
            if (INF < 0)
                throw new ArithmeticException("Overflow");
        }
    }
}
