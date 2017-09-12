/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Methods that convert strings into various formats.
 *
 * <p>
 * What JAX-RPC name binding tells us is that even such basic method
 * like "isLetter" can be different depending on the situation.
 *
 * For this reason, a whole lot of methods are made non-static,
 * even though they look like they should be static.
 */
class NameUtil {
    protected boolean isPunct(char c) {
        return c == '-' || c == '.' || c == ':' || c == '_' || c == '\u00b7' || c == '\u0387' || c == '\u06dd' || c == '\u06de';
    }

    protected static boolean isDigit(char c) {
        return c >= '0' && c <= '9' || Character.isDigit(c);
    }

    protected static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z' || Character.isUpperCase(c);
    }

    protected static boolean isLower(char c) {
        return c >= 'a' && c <= 'z' || Character.isLowerCase(c);
    }

    protected boolean isLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || Character.isLetter(c);
    }

    private String toLowerCase(String s)
    {
        return s.toLowerCase(Locale.ENGLISH);
    }

    private String toUpperCase(char c)
    {
        return String.valueOf(c).toUpperCase(Locale.ENGLISH);
    }

    private String toUpperCase(String s)
    {
        return s.toUpperCase(Locale.ENGLISH);
    }

    /**
     * Capitalizes the first character of the specified string,
     * and de-capitalize the rest of characters.
     */
    public String capitalize(String s) {
        if (!isLower(s.charAt(0)))
            return s;
        StringBuilder sb = new StringBuilder(s.length());
        sb.append(toUpperCase(s.charAt(0)));
        sb.append(toLowerCase(s.substring(1)));
        return sb.toString();
    }

    // Precondition: s[start] is not punctuation
    private int nextBreak(String s, int start) {
        int n = s.length();

        char c1 = s.charAt(start);
        int t1 = classify(c1);

        for (int i=start+1; i<n; i++) {
            // shift (c1,t1) into (c0,t0)
            // char c0 = c1;  --- conceptually, but c0 won't be used
            int t0 = t1;

            c1 = s.charAt(i);
            t1 = classify(c1);

            switch(actionTable[t0*5+t1]) {
            case ACTION_CHECK_PUNCT:
                if(isPunct(c1)) return i;
                break;
            case ACTION_CHECK_C2:
                if (i < n-1) {
                    char c2 = s.charAt(i+1);
                    if (isLower(c2))
                        return i;
                }
                break;
            case ACTION_BREAK:
                return i;
            }
        }
        return -1;
    }

    // the 5-category classification that we use in this code
    // to find work breaks
    static protected final int UPPER_LETTER = 0;
    static protected final int LOWER_LETTER = 1;
    static protected final int OTHER_LETTER = 2;
    static protected final int DIGIT = 3;
    static protected final int OTHER = 4;

    /**
     * Look up table for actions.
     * type0*5+type1 would yield the action to be taken.
     */
    private static final byte[] actionTable = new byte[5*5];

    // action constants. see nextBreak for the meaning
    static private final byte ACTION_CHECK_PUNCT = 0;
    static private final byte ACTION_CHECK_C2 = 1;
    static private final byte ACTION_BREAK = 2;
    static private final byte ACTION_NOBREAK = 3;

    /**
     * Decide the action to be taken given
     * the classification of the preceding character 't0' and
     * the classification of the next character 't1'.
     */
    private static byte decideAction( int t0, int t1 ) {
        if(t0==OTHER && t1==OTHER)  return ACTION_CHECK_PUNCT;
        if(!xor(t0==DIGIT,t1==DIGIT))  return ACTION_BREAK;
        if(t0==LOWER_LETTER && t1!=LOWER_LETTER)    return ACTION_BREAK;
        if(!xor(t0<=OTHER_LETTER,t1<=OTHER_LETTER)) return ACTION_BREAK;
        if(!xor(t0==OTHER_LETTER,t1==OTHER_LETTER)) return ACTION_BREAK;

        if(t0==UPPER_LETTER && t1==UPPER_LETTER)    return ACTION_CHECK_C2;

        return ACTION_NOBREAK;
    }

    private static boolean xor(boolean x,boolean y) {
        return (x&&y) || (!x&&!y);
    }

    static {
        // initialize the action table
        for( int t0=0; t0<5; t0++ )
            for( int t1=0; t1<5; t1++ )
                actionTable[t0*5+t1] = decideAction(t0,t1);
    }

    /**
     * Classify a character into 5 categories that determine the word break.
     */
    protected int classify(char c0) {
        switch(Character.getType(c0)) {
        case Character.UPPERCASE_LETTER:        return UPPER_LETTER;
        case Character.LOWERCASE_LETTER:        return LOWER_LETTER;
        case Character.TITLECASE_LETTER:
        case Character.MODIFIER_LETTER:
        case Character.OTHER_LETTER:            return OTHER_LETTER;
        case Character.DECIMAL_DIGIT_NUMBER:    return DIGIT;
        default:                                return OTHER;
        }
    }


    /**
     * Tokenizes a string into words and capitalizes the first
     * character of each word.
     *
     * <p>
     * This method uses a change in character type as a splitter
     * of two words. For example, "abc100ghi" will be splitted into
     * {"Abc", "100","Ghi"}.
     */
    public List<String> toWordList(String s) {
        ArrayList<String> ss = new ArrayList<String>();
        int n = s.length();
        for (int i = 0; i < n;) {

            // Skip punctuation
            while (i < n) {
                if (!isPunct(s.charAt(i)))
                    break;
                i++;
            }
            if (i >= n) break;

            // Find next break and collect word
            int b = nextBreak(s, i);
            String w = (b == -1) ? s.substring(i) : s.substring(i, b);
            ss.add(escape(capitalize(w)));
            if (b == -1) break;
            i = b;
        }

//      we can't guarantee a valid Java identifier anyway,
//      so there's not much point in rejecting things in this way.
//        if (ss.size() == 0)
//            throw new IllegalArgumentException("Zero-length identifier");
        return ss;
    }

    protected String toMixedCaseName(List<String> ss, boolean startUpper) {
        StringBuilder sb = new StringBuilder();
        if(!ss.isEmpty()) {
            sb.append(startUpper ? ss.get(0) : toLowerCase(ss.get(0)));
            for (int i = 1; i < ss.size(); i++)
                sb.append(ss.get(i));
        }
        return sb.toString();
    }

    protected String toMixedCaseVariableName(String[] ss,
                                                  boolean startUpper,
                                                  boolean cdrUpper) {
        if (cdrUpper)
            for (int i = 1; i < ss.length; i++)
                ss[i] = capitalize(ss[i]);
        StringBuilder sb = new StringBuilder();
        if( ss.length>0 ) {
            sb.append(startUpper ? ss[0] : toLowerCase(ss[0]));
            for (int i = 1; i < ss.length; i++)
                sb.append(ss[i]);
        }
        return sb.toString();
    }


    /**
     * Formats a string into "THIS_KIND_OF_FORMAT_ABC_DEF".
     *
     * @return
     *      Always return a string but there's no guarantee that
     *      the generated code is a valid Java identifier.
     */
    public String toConstantName(String s) {
        return toConstantName(toWordList(s));
    }

    /**
     * Formats a string into "THIS_KIND_OF_FORMAT_ABC_DEF".
     *
     * @return
     *      Always return a string but there's no guarantee that
     *      the generated code is a valid Java identifier.
     */
    public String toConstantName(List<String> ss) {
        StringBuilder sb = new StringBuilder();
        if( !ss.isEmpty() ) {
            sb.append(toUpperCase(ss.get(0)));
            for (int i = 1; i < ss.size(); i++) {
                sb.append('_');
                sb.append(toUpperCase(ss.get(i)));
            }
        }
        return sb.toString();
    }



    /**
     * Escapes characters is the given string so that they can be
     * printed by only using US-ASCII characters.
     *
     * The escaped characters will be appended to the given
     * StringBuffer.
     *
     * @param sb
     *      StringBuffer that receives escaped string.
     * @param s
     *      String to be escaped. <code>s.substring(start)</code>
     *      will be escaped and copied to the string buffer.
     */
    public static void escape(StringBuilder sb, String s, int start) {
        int n = s.length();
        for (int i = start; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isJavaIdentifierPart(c))
                sb.append(c);
            else {
                sb.append('_');
                if (c <= '\u000f') sb.append("000");
                else if (c <= '\u00ff') sb.append("00");
                else if (c <= '\u0fff') sb.append('0');
                sb.append(Integer.toString(c, 16));
            }
        }
    }

    /**
     * Escapes characters that are unusable as Java identifiers
     * by replacing unsafe characters with safe characters.
     */
    private static String escape(String s) {
        int n = s.length();
        for (int i = 0; i < n; i++)
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                StringBuilder sb = new StringBuilder(s.substring(0, i));
                escape(sb, s, i);
                return sb.toString();
            }
        return s;
    }
}
