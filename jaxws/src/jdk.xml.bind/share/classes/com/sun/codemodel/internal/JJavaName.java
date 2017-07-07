/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods that convert arbitrary strings into Java identifiers.
 */
public class JJavaName {


    /**
     * Checks if a given string is usable as a Java identifier.
     */
    public static boolean isJavaIdentifier(String s) {
        if(s.length()==0)   return false;
        if( reservedKeywords.contains(s) )  return false;

        if(!Character.isJavaIdentifierStart(s.charAt(0)))   return false;

        for (int i = 1; i < s.length(); i++)
            if (!Character.isJavaIdentifierPart(s.charAt(i)))
                return false;

        return true;
    }

    /**
     * Checks if the given string is a valid fully qualified name.
     */
    public static boolean isFullyQualifiedClassName(String s) {
        return isJavaPackageName(s);
    }

    /**
     * Checks if the given string is a valid Java package name.
     */
    public static boolean isJavaPackageName(String s) {
        while(s.length()!=0) {
            int idx = s.indexOf('.');
            if(idx==-1) idx=s.length();
            if( !isJavaIdentifier(s.substring(0,idx)) )
                return false;

            s = s.substring(idx);
            if(s.length()!=0)    s = s.substring(1);    // remove '.'
        }
        return true;
    }

    /**
     * <b>Experimental API:</b> converts an English word into a plural form.
     *
     * @param word
     *      a word, such as "child", "apple". Must not be null.
     *      It accepts word concatanation forms
     *      that are common in programming languages, such as "my_child", "MyChild",
     *      "myChild", "MY-CHILD", "CODE003-child", etc, and mostly tries to do the right thing.
     *      ("my_children","MyChildren","myChildren", and "MY-CHILDREN", "CODE003-children" respectively)
     *      <p>
     *      Although this method only works for English words, it handles non-English
     *      words gracefully (by just returning it as-is.) For example, "{@literal &#x65E5;&#x672C;&#x8A9E;}"
     *      will be returned as-is without modified, not "{@literal &#x65E5;&#x672C;&#x8A9E;s}"
     *      <p>
     *      This method doesn't handle suffixes very well. For example, passing
     *      "person56" will return "person56s", not "people56".
     *
     * @return
     *      always non-null.
     */
    public static String getPluralForm(String word) {
        // remember the casing of the word
        boolean allUpper = true;

        // check if the word looks like an English word.
        // if we see non-ASCII characters, abort
        for(int i=0; i<word.length(); i++ ) {
            char ch = word.charAt(i);
            if(ch >=0x80)
                return word;

            // note that this isn't the same as allUpper &= Character.isUpperCase(ch);
            allUpper &= !Character.isLowerCase(ch);
        }

        for (Entry e : TABLE) {
            String r = e.apply(word);
            if(r!=null) {
                if(allUpper)    r=r.toUpperCase();
                return r;
            }
        }

        // failed
        return word;
    }


    /** All reserved keywords of Java. */
    private static HashSet<String> reservedKeywords = new HashSet<String>();

    static {
        // see http://java.sun.com/docs/books/tutorial/java/nutsandbolts/_keywords.html
        String[] words = new String[]{
            "abstract",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",

            // technically these are not reserved words but they cannot be used as identifiers.
            "true",
            "false",
            "null",

            // and I believe assert is also a new keyword
            "assert",

            // and 5.0 keywords
            "enum"
            };
        for (String w : words)
            reservedKeywords.add(w);
    }


    private static class Entry {
        private final Pattern pattern;
        private final String replacement;

        public Entry(String pattern, String replacement) {
            this.pattern = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE);
            this.replacement = replacement;
        }

        String apply(String word) {
            Matcher m = pattern.matcher(word);
            if(m.matches()) {
                StringBuffer buf = new StringBuffer();
                m.appendReplacement(buf,replacement);
                return buf.toString();
            } else {
                return null;
            }
        }
    }

    private static final Entry[] TABLE;

    static {
        String[] source = {
              "(.*)child","$1children",
                 "(.+)fe","$1ves",
              "(.*)mouse","$1mise",
                  "(.+)f","$1ves",
                 "(.+)ch","$1ches",
                 "(.+)sh","$1shes",
              "(.*)tooth","$1teeth",
                 "(.+)um","$1a",
                 "(.+)an","$1en",
                "(.+)ato","$1atoes",
              "(.*)basis","$1bases",
               "(.*)axis","$1axes",
                 "(.+)is","$1ises",
                 "(.+)ss","$1sses",
                 "(.+)us","$1uses",
                  "(.+)s","$1s",
               "(.*)foot","$1feet",
                 "(.+)ix","$1ixes",
                 "(.+)ex","$1ices",
                 "(.+)nx","$1nxes",
                  "(.+)x","$1xes",
                  "(.+)y","$1ies",
                   "(.+)","$1s",
        };

        TABLE = new Entry[source.length/2];

        for( int i=0; i<source.length; i+=2 ) {
            TABLE[i/2] = new Entry(source[i],source[i+1]);
        }
    }
}
