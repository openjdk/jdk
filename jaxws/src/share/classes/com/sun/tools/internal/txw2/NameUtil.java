/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2;

import com.sun.codemodel.JJavaName;

import java.util.ArrayList;

/**
 * @author Kohsuke Kawaguchi
 */
public class NameUtil {

    protected static boolean isPunct(char c) {
        return (c == '-' || c == '.' || c == ':' || c == '_' || c == '\u00b7'
                || c == '\u0387' || c == '\u06dd' || c == '\u06de');
    }

    protected static boolean isDigit(char c) {
        return ((c >= '0' && c <= '9') || Character.isDigit(c));
    }

    protected static boolean isUpper(char c) {
        return ((c >= 'A' && c <= 'Z') || Character.isUpperCase(c));
    }

    protected static boolean isLower(char c) {
        return ((c >= 'a' && c <= 'z') || Character.isLowerCase(c));
    }

    protected static boolean isLetter(char c) {
        return ((c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || Character.isLetter(c));
    }

    /**
     * Capitalizes the first character of the specified string,
     * and de-capitalize the rest of characters.
     */
    public static String capitalize(String s) {
        if (!isLower(s.charAt(0)))
            return s;
        StringBuffer sb = new StringBuffer(s.length());
        sb.append(Character.toUpperCase(s.charAt(0)));
        sb.append(s.substring(1).toLowerCase());
        return sb.toString();
    }

    // Precondition: s[start] is not punctuation
    protected static int nextBreak(String s, int start) {
        int n = s.length();
        for (int i = start; i < n; i++) {
            char c0 = s.charAt(i);
            if (i < n - 1) {
                char c1 = s.charAt(i + 1);
                if (isPunct(c1)) return i + 1;
                if (isDigit(c0) && !isDigit(c1)) return i + 1;
                if (!isDigit(c0) && isDigit(c1)) return i + 1;
                if (isLower(c0) && !isLower(c1)) return i + 1;
                if (isLetter(c0) && !isLetter(c1)) return i + 1;
                if (!isLetter(c0) && isLetter(c1)) return i + 1;
                if (i < n - 2) {
                    char c2 = s.charAt(i + 2);
                    if (isUpper(c0) && isUpper(c1) && isLower(c2))
                        return i + 1;
                }
            }
        }
        return -1;
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
    public static String[] toWordList(String s) {
        ArrayList ss = new ArrayList();
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
        return (String[])(ss.toArray(new String[0]));
    }

    protected static String toMixedCaseName(String[] ss, boolean startUpper) {
        StringBuffer sb = new StringBuffer();
        if(ss.length>0) {
            sb.append(startUpper ? ss[0] : ss[0].toLowerCase());
            for (int i = 1; i < ss.length; i++)
                sb.append(ss[i]);
        }
        return sb.toString();
    }

    protected static  String toMixedCaseVariableName(String[] ss,
                                                  boolean startUpper,
                                                  boolean cdrUpper) {
        if (cdrUpper)
            for (int i = 1; i < ss.length; i++)
                ss[i] = capitalize(ss[i]);
        StringBuffer sb = new StringBuffer();
        if( ss.length>0 ) {
            sb.append(startUpper ? ss[0] : ss[0].toLowerCase());
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
    public static String toConstantName(String s) {
        return toConstantName(toWordList(s));
    }

    /**
     * Formats a string into "THIS_KIND_OF_FORMAT_ABC_DEF".
     *
     * @return
     *      Always return a string but there's no guarantee that
     *      the generated code is a valid Java identifier.
     */
    public static String toConstantName(String[] ss) {
        StringBuffer sb = new StringBuffer();
        if( ss.length>0 ) {
            sb.append(ss[0].toUpperCase());
            for (int i = 1; i < ss.length; i++) {
                sb.append('_');
                sb.append(ss[i].toUpperCase());
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
    public static void escape(StringBuffer sb, String s, int start) {
        int n = s.length();
        for (int i = start; i < n; i++) {
            char c = s.charAt(i);
            if (Character.isJavaIdentifierPart(c))
                sb.append(c);
            else {
                sb.append("_");
                if (c <= '\u000f') sb.append("000");
                else if (c <= '\u00ff') sb.append("00");
                else if (c <= '\u0fff') sb.append("0");
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
                StringBuffer sb = new StringBuffer(s.substring(0, i));
                escape(sb, s, i);
                return sb.toString();
            }
        return s;
    }

    /**
     * Escape any characters that would cause the single arg constructor
     * of java.net.URI to complain about illegal chars.
     *
     * @param s source string to be escaped
     */
    public static String escapeURI(String s) {
        StringBuffer sb = new StringBuffer();
        for( int i = 0; i < s.length(); i++ ) {
            char c = s.charAt(i);
            if(Character.isSpaceChar(c)) {
                sb.append("%20");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Calculate the parent URI path of the given URI path.
     *
     * @param uriPath the uriPath (as returned by java.net.URI#getPath()
     * @return the parent URI path of the given URI path
     */
    public static String getParentUriPath(String uriPath) {
        int idx = uriPath.lastIndexOf('/');

        if (uriPath.endsWith("/")) {
            uriPath = uriPath.substring(0,idx); // trim trailing slash
            idx = uriPath.lastIndexOf('/'); // move idx to parent context
        }

        return uriPath.substring(0, idx)+"/";
    }

    /**
     * Calculate the normalized form of the given uriPath.
     *
     * For example:
     *    /a/b/c/ -> /a/b/c/
     *    /a/b/c  -> /a/b/
     *    /a/     -> /a/
     *    /a      -> /
     *
     * @param uriPath path of a URI (as returned by java.net.URI#getPath()
     * @return the normalized uri path
     */
    public static String normalizeUriPath(String uriPath) {
        if (uriPath.endsWith("/"))
            return uriPath;

        // the uri path should always have at least a leading slash,
        // so no need to make sure that ( idx == -1 )
        int idx = uriPath.lastIndexOf('/');
        return uriPath.substring(0, idx+1);
    }

    /**
     * determine if two Strings are equal ignoring case allowing null values
     *
     * @param s string 1
     * @param t string 2
     * @return true iff the given strings are equal ignoring case, false if they aren't
     * equal or either of them are null.
     */
    public static boolean equalsIgnoreCase(String s, String t) {
        if (s == t) return true;
        if ((s != null) && (t != null)) {
            return s.equalsIgnoreCase(t);
        }
        return false;
    }

    /**
     * determine if two Strings are iqual allowing null values
     *
     * @param s string 1
     * @param t string 2
     * @return true iff the strings are equal, false if they aren't equal or either of
     * them are null.
     */
    public static boolean equal(String s, String t) {
        if (s == t) return true;
        if ((s != null) && (t != null)) {
            return s.equals(t);
        }
        return false;
    }

    public static String toClassName(String s) {
        return toMixedCaseName(toWordList(s), true);
    }
    public static String toVariableName(String s) {
        return toMixedCaseName(toWordList(s), false);
    }
    public static String toMethodName(String s) {
        String m = toMixedCaseName(toWordList(s), false);
        if(JJavaName.isJavaIdentifier(m))
            return m;
        else
            return '_'+m;
    }
    public static String toInterfaceName( String token ) {
        return toClassName(token);
    }
    public static String toPropertyName(String s) {
        return toClassName(s);
    }
    public static  String toPackageName( String s ) {
        return toMixedCaseName(toWordList(s), false );
    }
}
