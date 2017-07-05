/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc;

import java.util.Comparator;

/** Implements a locale and case insensitive comparator suitable for
    strings that are known to only contain ASCII characters. Some
    tables internal to the JDK contain only ASCII data and are using
    the "generalized" java.lang.String case-insensitive comparator
    which converts each character to both upper and lower case. */

public class ASCIICaseInsensitiveComparator implements Comparator<String> {
    public static final Comparator<String> CASE_INSENSITIVE_ORDER =
        new ASCIICaseInsensitiveComparator();

    public int compare(String s1, String s2) {
        int n1=s1.length(), n2=s2.length();
        int minLen = n1 < n2 ? n1 : n2;
        for (int i=0; i < minLen; i++) {
            char c1 = s1.charAt(i);
            char c2 = s2.charAt(i);
            assert c1 <= '\u007F' && c2 <= '\u007F';
            if (c1 != c2) {
                c1 = (char)toLower(c1);
                c2 = (char)toLower(c2);
                if (c1 != c2) {
                    return c1 - c2;
                }
            }
        }
        return n1 - n2;
    }

    /**
     * A case insensitive hash code method to go with the case insensitive
     * compare() method.
     *
     * Returns a hash code for this ASCII string as if it were lower case.
     *
     * returns same answer as:<p>
     * <code>s.toLowerCase(Locale.US).hashCode();</code><p>
     * but does not allocate memory (it does NOT have the special
     * case Turkish rules).
     *
     * @param s a String to compute the hashcode on.
     * @return  a hash code value for this object.
     */
    public static int lowerCaseHashCode(String s) {
        int h = 0;
        int len = s.length();

        for (int i = 0; i < len; i++) {
            h = 31*h + toLower(s.charAt(i));
        }

        return h;
    }

    /* If java.util.regex.ASCII ever becomes public or sun.*, use its code instead:*/
    static boolean isLower(int ch) {
        return ((ch-'a')|('z'-ch)) >= 0;
    }

    static boolean isUpper(int ch) {
        return ((ch-'A')|('Z'-ch)) >= 0;
    }

    static int toLower(int ch) {
        return isUpper(ch) ? (ch + 0x20) : ch;
    }

    static int toUpper(int ch) {
        return isLower(ch) ? (ch - 0x20) : ch;
    }
}
