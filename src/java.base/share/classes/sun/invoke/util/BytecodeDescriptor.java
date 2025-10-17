/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.invoke.util;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility routines for dealing with bytecode-level signatures.
 * @author jrose
 */
public class BytecodeDescriptor {

    private BytecodeDescriptor() { }  // cannot instantiate

    /// Parses and validates a field descriptor string in the {@code loader} context.
    ///
    /// @param descriptor a field descriptor string
    /// @param loader the class loader in which to look up the types (null means
    ///               bootstrap class loader)
    /// @throws IllegalArgumentException if the descriptor is invalid
    /// @throws TypeNotPresentException if the descriptor is valid, but
    ///         the class cannot be found by the loader
    public static Class<?> parseClass(String descriptor, ClassLoader loader) {
        int[] i = {0};
        var ret = parseSig(descriptor, i, descriptor.length(), loader);
        if (i[0] != descriptor.length() || ret == null) {
            parseError("not a class descriptor", descriptor);
        }
        return ret;
    }

    /// Parses and validates a method descriptor string in the {@code loader} context.
    ///
    /// @param descriptor a method descriptor string
    /// @param loader the class loader in which to look up the types (null means
    ///               bootstrap class loader)
    /// @throws IllegalArgumentException if the descriptor is invalid
    /// @throws TypeNotPresentException if a reference type cannot be found by
    ///         the loader (before the descriptor is found invalid)
    public static List<Class<?>> parseMethod(String descriptor, ClassLoader loader) {
        return parseMethod(descriptor, 0, descriptor.length(), loader);
    }

    /**
     * @param loader the class loader in which to look up the types (null means
     *               bootstrap class loader)
     */
    static List<Class<?>> parseMethod(String bytecodeSignature,
            int start, int end, ClassLoader loader) {
        String str = bytecodeSignature;
        int[] i = {start};
        var ptypes = new ArrayList<Class<?>>();
        if (i[0] < end && str.charAt(i[0]) == '(') {
            ++i[0];  // skip '('
            while (i[0] < end && str.charAt(i[0]) != ')') {
                Class<?> pt = parseSig(str, i, end, loader);
                if (pt == null || pt == void.class)
                    parseError(str, "bad argument type");
                ptypes.add(pt);
            }
            ++i[0];  // skip ')'
        } else {
            parseError(str, "not a method type");
        }
        Class<?> rtype = parseSig(str, i, end, loader);
        if (rtype == null || i[0] != end)
            parseError(str, "bad return type");
        ptypes.add(rtype);
        return ptypes;
    }

    private static void parseError(String str, String msg) {
        throw new IllegalArgumentException("bad signature: "+str+": "+msg);
    }

    /// Parse a single type in a descriptor. Results can be:
    ///
    /// - A `Class` for successful parsing
    /// - `null` for malformed descriptor format
    /// - Throwing a [TypeNotPresentException] for valid class name,
    ///   but class cannot be found
    ///
    /// @param str contains the string to parse
    /// @param i cursor for the next token in the string, modified in-place
    /// @param end the limit for parsing
    /// @param loader the class loader in which to look up the types (null means
    ///               bootstrap class loader)
    ///
    private static Class<?> parseSig(String str, int[] i, int end, ClassLoader loader) {
        if (i[0] == end)  return null;
        char c = str.charAt(i[0]++);
        if (c == 'L') {
            int begc = i[0], endc = str.indexOf(';', begc);
            if (endc < 0)  return null;
            i[0] = endc+1;
            String name = str.substring(begc, endc).replace('/', '.');
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ex) {
                throw new TypeNotPresentException(name, ex);
            }
        } else if (c == '[') {
            Class<?> t = parseSig(str, i, end, loader);
            if (t != null) {
                try {
                    t = t.arrayType();
                } catch (UnsupportedOperationException ex) {
                    // Bad arrays, such as [V or more than 255 dims
                    // We have a more informative IAE
                    return null;
                }
            }
            return t;
        } else {
            Wrapper w;
            try {
                w = Wrapper.forBasicType(c);
            } catch (IllegalArgumentException ex) {
                // Our reporting has better error message
                return null;
            }
            return w.primitiveType();
        }
    }

    public static String unparse(Class<?> type) {
        if (type == Object.class) {
            return "Ljava/lang/Object;";
        } else if (type == int.class) {
            return "I";
        }
        return type.descriptorString();
    }

    public static String unparse(Object type) {
        if (type instanceof Class<?> cl)
            return unparse(cl);
        if (type instanceof MethodType mt)
            return mt.toMethodDescriptorString();
        return (String) type;
    }

    public static String unparseMethod(Class<?> rtype, List<Class<?>> ptypes) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class<?> pt : ptypes)
            unparseSig(pt, sb);
        sb.append(')');
        unparseSig(rtype, sb);
        return sb.toString();
    }

    public static String unparseMethod(Class<?> rtype, Class<?>[] ptypes) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Class<?> pt : ptypes)
            unparseSig(pt, sb);
        sb.append(')');
        unparseSig(rtype, sb);
        return sb.toString();
    }

    private static void unparseSig(Class<?> t, StringBuilder sb) {
        char c = Wrapper.forBasicType(t).basicTypeChar();
        if (c != 'L') {
            sb.append(c);
        } else if (t == Object.class) {
            sb.append("Ljava/lang/Object;");
        } else {
            sb.append(t.descriptorString());
        }
    }
}
