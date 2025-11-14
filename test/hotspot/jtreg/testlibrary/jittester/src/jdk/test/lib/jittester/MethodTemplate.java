/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;

import static java.util.function.Predicate.not;


/**
 * A wrapper for string method templates, similar to the CompileCommand patterns.
 */
public final class MethodTemplate {

    /**
     * String that can have wildcard symbols on its ends, allowing it to match a family of strings.
     * For example, "abc*" matches "abc123", and so on.
     */
    public static class WildcardString {
        private final String pattern;
        private final boolean frontWildcarded;
        private final boolean tailWildcarded;

        /**
         * Creates a WildcardString from given string.
         * @param pattern   string pattern, like "some*"
         */
        public WildcardString(String pattern) {
            // check for the leading '*'
            frontWildcarded = pattern.charAt(0) == '*';
            pattern = frontWildcarded ? pattern.substring(1) : pattern;

            // check for the trailing '*'
            tailWildcarded = pattern.length() > 0 && pattern.charAt(pattern.length() - 1) == '*';
            pattern = tailWildcarded ? pattern.substring(0, pattern.length() - 1) : pattern;

            this.pattern = pattern;
        }

        /**
         * Returns true it this WildcardString matches given other string.
         * @param other the string that this WildcardString should be matched against
         * @return      true in case of a match.
         */
        public boolean matches(String other) {
            boolean result = pattern.equals(other);
            result |= frontWildcarded ? other.endsWith(pattern) : result;
            result |= tailWildcarded ? other.startsWith(pattern) : result;
            result |= tailWildcarded && frontWildcarded ? other.contains(pattern) : result;
            return result;
        }
    }

    private static final Pattern METHOD_PATTERN = Pattern.compile(generateMethodPattern());

    private final WildcardString klassName;
    private final WildcardString methodName;
    private final Optional<List<Class<?>>> signature;

    private MethodTemplate(String klassName, String methodName, Optional<List<Class<?>>> signature) {
        this.klassName = new WildcardString(klassName);
        this.methodName = new WildcardString(methodName);
        this.signature = signature;
    }

    private static String generateMethodPattern() {
        // Sample valid template(s):    java/lang/String::indexOf(Ljava/lang/String;I)
        //                              java/lang/::*(Ljava/lang/String;I)
        //                              *String::indexOf(*)
        //                              java/lang/*::indexOf

        String primitiveType = "[ZBSCIJFD]";        // Simply a letter, like 'I'
        String referenceType = "L[\\w/$]+;";        // Like 'Ljava/lang/String;'
        String primOrRefType =
            "\\[?" + primitiveType +                // Bracket is optional: '[Z', or 'Z'
            "|" +
            "\\[?" + referenceType;                 // Bracket is optional: '[LSomeObject;' or 'LSomeObject;'
        String argTypesOrWildcard = "(" +           // Method argument(s) Ljava/lang/String;Z...
                "(" + primOrRefType + ")*" +
            ")|\\*";                                // .. or a wildcard:

        return
            "(?<klassName>[\\w/$]*\\*?)" +          // Class name, like 'java/lang/String'
            "::" +                                  // Simply '::'
            "(?<methodName>\\*?[\\w$]+\\*?)" +      // method name, 'indexOf''
            "(\\((?<argTypes>" +                    // Method argument(s) in brackets:
                argTypesOrWildcard +                //     (Ljava/lang/String;Z) or '*' or nothing
            ")\\))?";
    }

    /**
     * Returns true iff none of the given MethodTemplates matches the given Executable.
     *
     * @param templates     the collection of templates to check
     * @param method        the executable to match the colletions templates
     * @return              true if none of the given templates matches the method, false otherwise
     */
    public static boolean noneMatches(Collection<MethodTemplate> templates, Executable method) {
        for (MethodTemplate template : templates) {
            if (template.matches(method)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if this MethodTemplate matches the given Executable.
     *
     * @param other     the Executable to try to match to
     * @return          whether the other matches this MethodTemplate
     */
    public boolean matches(Executable other) {
        boolean result = klassName.matches(other.getDeclaringClass().getName());

        result &= (other instanceof Constructor)
            ? result
            : methodName.matches(other.getName());

        return result &&
            signature.map(Arrays.asList(other.getParameterTypes())::equals)
                     .orElse(true);
    }

    /**
     * Parses the given string and returs a MethodTemplate.
     *
     * @param methodStr     the string to parse
     * @return              created MethodTemplate
     */
    public static MethodTemplate parse(String methodStr) {
        Matcher matcher = METHOD_PATTERN.matcher(methodStr);
        String msg = String.format("Format of the methods exclude input file is incorrect,"
                + " methodStr \"%s\" has wrong format", methodStr);
        Asserts.assertTrue(matcher.matches(), msg);

        String klassName = matcher.group("klassName").replaceAll("/", "\\.");
        String methodName = matcher.group("methodName");
        Optional<List<Class<?>>> signature = Optional.ofNullable(matcher.group("argTypes"))
                                                     .filter(not("*"::equals))
                                                     .map(MethodTemplate::parseSignature);
        return new MethodTemplate(klassName, methodName, signature);
    }

    private static List<Class<?>> parseSignature(String signature) {
        List<Class<?>> sigClasses = new ArrayList<>();
        char typeChar;
        boolean isArray;
        String klassName;
        StringBuilder sb;
        StringBuilder arrayDim;
        try (StringReader str = new StringReader(signature)) {
            int symbol = str.read();
            while (symbol != -1) {
                typeChar = (char) symbol;
                arrayDim = new StringBuilder();
                Class<?> primArrayClass = null;
                if (typeChar == '[') {
                    isArray = true;
                    arrayDim.append('[');
                    symbol = str.read();
                    while (symbol == '[') {
                        arrayDim.append('[');
                        symbol = str.read();
                    }
                    typeChar = (char) symbol;
                    if (typeChar != 'L') {
                        primArrayClass = Class.forName(arrayDim.toString() + typeChar);
                    }
                } else {
                    isArray = false;
                }
                switch (typeChar) {
                    case 'Z':
                        sigClasses.add(isArray ? primArrayClass : boolean.class);
                        break;
                    case 'I':
                        sigClasses.add(isArray ? primArrayClass : int.class);
                        break;
                    case 'J':
                        sigClasses.add(isArray ? primArrayClass : long.class);
                        break;
                    case 'F':
                        sigClasses.add(isArray ? primArrayClass : float.class);
                        break;
                    case 'D':
                        sigClasses.add(isArray ? primArrayClass : double.class);
                        break;
                    case 'B':
                        sigClasses.add(isArray ? primArrayClass : byte.class);
                        break;
                    case 'S':
                        sigClasses.add(isArray ? primArrayClass : short.class);
                        break;
                    case 'C':
                        sigClasses.add(isArray ? primArrayClass : char.class);
                        break;
                    case 'L':
                        sb = new StringBuilder();
                        symbol = str.read();
                        while (symbol != ';') {
                            sb.append((char) symbol);
                            symbol = str.read();
                        }
                        klassName = sb.toString().replaceAll("/", "\\.");
                        if (isArray) {
                            klassName = arrayDim.toString() + "L" + klassName + ";";
                        }
                        Class<?> klass = Class.forName(klassName);
                        sigClasses.add(klass);
                        break;
                    default:
                        throw new Error("Unknown type " + typeChar);
                }
                symbol = str.read();
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new Error("Unexpected exception while parsing exclude methods file", ex);
        }
        return sigClasses;
    }

}
