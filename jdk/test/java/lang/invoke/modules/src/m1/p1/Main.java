/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package p1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

/**
 * Basic test case for module access check, supplements AccessControlTest.
 *
 * The tests consists of two modules:
 *
 * module m1 { requires m2; exports p1; }
 * module m2 { exports q1; }
 *
 * Both modules read java.base (as every module reads java.base)
 *
 * module m1 has public types in packages p1 and p2, p2 is not exported.
 * module m2 has public types in packages q1 and q2, q2 is not exported.
 */

public class Main {

    static final int MODULE = Lookup.MODULE;

    // Use Class.forName to get classes for test because some
    // are not accessible at compile-time

    static final Class<?> p1_Type1;        // m1, exported
    static final Class<?> p2_Type2;        // m1, not exported
    static final Class<?> q1_Type1;        // m2, exported, m1 reads m2
    static final Class<?> q2_Type2;        // m2, not exported, m1 reads m2
    static final Class<?> x500NameClass;   // java.base, not exported

    static {
        try {
            p1_Type1 = Class.forName("p1.Type1");
            p2_Type2 = Class.forName("p2.Type2");
            q1_Type1 = Class.forName("q1.Type1");
            q2_Type2 = Class.forName("q2.Type2");
            x500NameClass = Class.forName("sun.security.x509.X500Name");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Lookup lookup, lookup2;

        /**
         * MethodHandles.lookup()
         * has module access [A0]
         * can access all public types in m1 [A1]
         * can access public types in packages exported by modules that m1 reads [A2]
         * cannot access public types in non-exported modules of modules that m1 reads [A3]
         */
        lookup = MethodHandles.lookup();
        assertTrue((lookup.lookupModes() & MODULE) == MODULE); // [A0]
        findConstructor(lookup, p1_Type1, void.class); // [A1]
        findConstructor(lookup, p2_Type2, void.class); // [A1]
        findConstructor(lookup, q1_Type1, void.class); // [A2]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A3]
        findConstructor(lookup, Object.class, void.class); // [A2]
        findConstructorExpectingIAE(lookup, x500NameClass, void.class, String.class); // [A3]

        /**
         * Teleport from MethodHandles.lookup() to lookup class in the same module
         * module access is retained [A0]
         * can access all public types in m1 [A1]
         * can access public types in packages exported by modules that m1 reads [A2]
         * cannot access public types in non-exported modules of modules that m1 reads [A3]
         */
        lookup2 = lookup.in(p2_Type2);
        assertTrue((lookup2.lookupModes() & MODULE) == MODULE); // [A0]
        findConstructor(lookup2, p1_Type1, void.class); // [A1]
        findConstructor(lookup2, p2_Type2, void.class); // [A1]
        findConstructor(lookup2, q1_Type1, void.class); // [A2]
        findConstructorExpectingIAE(lookup2, q2_Type2, void.class); // [A3]
        findConstructor(lookup2, Object.class, void.class); // [A2]
        findConstructorExpectingIAE(lookup2, x500NameClass, void.class, String.class); // [A3]

        /**
         * Teleport from MethodHandles.lookup() to lookup class in another named module
         * has no access [A0]
         */
        lookup2 = lookup.in(Object.class);
        assertTrue(lookup2.lookupModes() == 0); // [A0]
        findConstructorExpectingIAE(lookup2, Object.class, void.class);  // [A0]

        /**
         * Teleport from MethodHandles.lookup() to lookup class in an unnamed module
         * has no access [A0]
         */
        Class<?> c = MethodHandles.publicLookup().lookupClass();
        assertTrue(!c.getModule().isNamed());
        lookup2 = lookup.in(c);
        assertTrue(lookup2.lookupModes() == 0); // [A0]
        findConstructorExpectingIAE(lookup2, Object.class, void.class);

        /**
         * MethodHandles.publicLookup()
         * has no module access [A0]
         * can access public types in exported packages [A1]
         * cannot access public types in non-exported packages [A2]
         */
        lookup = MethodHandles.publicLookup();
        assertTrue((lookup.lookupModes() & MODULE) == 0); // [A0]
        findConstructor(lookup, p1_Type1, void.class); // [A1]
        findConstructorExpectingIAE(lookup, p2_Type2, void.class); // [A1]
        findConstructor(lookup, q1_Type1, void.class); // [A1]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A2]
        findConstructor(lookup, Object.class, void.class); // [A1]
        findConstructorExpectingIAE(lookup, x500NameClass, void.class); // [A2]

        /**
         * Teleport from MethodHandles.publicLookup() to lookup class in java.base
         * has no module access [A0]
         * can access public types in packages exported by java.base [A1]
         * cannot access public types in non-exported packages [A2]
         * no access to types in other named modules [A3]
         */
        lookup2 = lookup.in(Object.class);
        assertTrue((lookup2.lookupModes() & MODULE) == 0); // [A0]
        findConstructor(lookup2, String.class, void.class); // [A1]
        findConstructorExpectingIAE(lookup2, x500NameClass, void.class, String.class); // [A2]
        findConstructorExpectingIAE(lookup2, p1_Type1, void.class); // [A3]
        findConstructorExpectingIAE(lookup2, q1_Type1, void.class); // [A3]

        /**
         * Teleport from MethodHandles.publicLookup() to lookup class in m1
         * has no module access [A0]
         * can access public types in packages exported by m1, m2 and java.base [A1]
         * cannot access public types is non-exported packages [A2]
         */
        lookup2 = lookup.in(p1_Type1);
        assertTrue((lookup2.lookupModes() & MODULE) == 0); // [A0]
        findConstructor(lookup2, p1_Type1, void.class);  // [A1]
        findConstructor(lookup2, q1_Type1, void.class);  // [A1]
        findConstructor(lookup2, Object.class, void.class);  // [A1]
        findConstructorExpectingIAE(lookup, p2_Type2, void.class); // [A2]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A2]
        findConstructorExpectingIAE(lookup2, x500NameClass, void.class, String.class); // [A2]

        /**
         * Teleport from MethodHandles.publicLookup() to lookup class in m2
         * has no module access [A0]
         * can access public types in packages exported by m2 and java.base [A1]
         * cannot access public types is non-exported packages or modules that m2 does
         *   not read [A2]
         */
        lookup2 = lookup.in(q1_Type1);
        assertTrue((lookup2.lookupModes() & MODULE) == 0); // [A0]
        findConstructor(lookup2, q1_Type1, void.class); // [A1]
        findConstructor(lookup2, Object.class, void.class); // [A1]
        findConstructorExpectingIAE(lookup2, p1_Type1, void.class); // [A2]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A2]
        findConstructorExpectingIAE(lookup2, x500NameClass, void.class, String.class);  // [A2]

        /**
         * Teleport from MethodHandles.publicLookup() to lookup class that is not
         * in an exported package, should get no access [A0]
         */
        lookup2 = lookup.in(p2_Type2);
        assertTrue(lookup2.lookupModes()  == 0); // [A0]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A0]
    }

    /**
     * Invokes Lookup findConstructor with a method type constructored from the
     * given return and parameter types, expecting IllegalAccessException to be
     * thrown.
     */
    static MethodHandle findConstructorExpectingIAE(Lookup lookup,
                                                    Class<?> clazz,
                                                    Class<?> rtype,
                                                    Class<?>... ptypes) throws Exception {
        try {
            findConstructor(lookup, clazz, rtype, ptypes);
            throw new RuntimeException("IllegalAccessError expected");
        } catch (IllegalAccessException expected) {
            return null;
        }
    }

    /**
     * Invokes Lookup findConstructor with a method type constructored from the
     * given return and parameter types.
     */
    static MethodHandle findConstructor(Lookup lookup,
                                        Class<?> clazz,
                                        Class<?> rtype,
                                        Class<?>... ptypes) throws Exception {
        MethodType mt = MethodType.methodType(rtype, ptypes);
        return lookup.findConstructor(clazz, mt);
    }

    static void assertTrue(boolean condition) {
        if (!condition)
            throw new RuntimeException();
    }

}
