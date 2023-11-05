/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

import java.util.List;

public final class SpellChecker {
    public static String check(String name, List<String> alternatives) {
        for (String expected : alternatives) {
            String s = name.toLowerCase();
            int lengthDifference = expected.length() - s.length();
            boolean spellingError = false;
            if (lengthDifference == 0) {
                if (expected.equals(s)) {
                    spellingError = true; // incorrect case, or we wouldn't be here
                } else {
                    if (s.length() < 6) {
                        spellingError = diff(expected, s) < 2; // one incorrect letter
                    } else {
                        spellingError = diff(expected, s) < 3; // two incorrect letter
                    }
                }
            }
            if (lengthDifference == 1) {
                spellingError = inSequence(expected, s); // missing letter
            }
            if (lengthDifference == -1) {
                spellingError = inSequence(s, expected); // additional letter
            }
            if (spellingError) {
                return expected;
            }
        }
        return null;
    }

    private static int diff(String a, String b) {
        int count = a.length();
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) == b.charAt(i)) {
                count--;
            }
        }
        return count;
    }

    private static boolean inSequence(String longer, String shorter) {
        int l = 0;
        int s = 0;
        while (l < longer.length() && s < shorter.length()) {
            if (longer.charAt(l) == shorter.charAt(s)) {
                s++;
            }
            l++;
        }
        return shorter.length() == s; // if 0, all letters in longer found in shorter
    }
}
