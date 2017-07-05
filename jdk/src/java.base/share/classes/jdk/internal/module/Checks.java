/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;


public final class Checks {

    private Checks() { }

    private static void fail(String what, String id, int i) {
        throw new IllegalArgumentException(id
                                           + ": Invalid " + what + ": "
                                           + " Illegal character"
                                           + " at index " + i);
    }

    /**
     * Returns {@code true} if the given identifier is a legal Java identifier.
     */
    public static boolean isJavaIdentifier(String id) {
        int n = id.length();
        if (n == 0)
            return false;
        if (!Character.isJavaIdentifierStart(id.codePointAt(0)))
            return false;
        int cp = id.codePointAt(0);
        int i = Character.charCount(cp);
        for (; i < n; i += Character.charCount(cp)) {
            cp = id.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp) && id.charAt(i) != '.')
                return false;
        }
        if (cp == '.')
            return false;

        return true;
    }

    /**
     * Checks if a given identifier is a legal Java identifier.
     */
    public static String requireJavaIdentifier(String what, String id) {
        if (id == null)
            throw new IllegalArgumentException("Null " + what);
        int n = id.length();
        if (n == 0)
            throw new IllegalArgumentException("Empty " + what);
        if (!Character.isJavaIdentifierStart(id.codePointAt(0)))
            fail(what, id, 0);
        int cp = id.codePointAt(0);
        int i = Character.charCount(cp);
        int last = 0;
        for (; i < n; i += Character.charCount(cp)) {
            cp = id.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp) && id.charAt(i) != '.')
                fail(what, id, i);
            last = i;
        }
        if (cp == '.')
            fail(what, id, last);

        return id;
    }

    public static String requireModuleName(String id) {
        return requireJavaIdentifier("module name", id);
    }

    public static String requirePackageName(String id) {
        return requireJavaIdentifier("package name", id);
    }

    public static String requireServiceTypeName(String id) {
        return requireJavaIdentifier("service type name", id);
    }

    public static String requireServiceProviderName(String id) {
        return requireJavaIdentifier("service provider name", id);
    }

}
