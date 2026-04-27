/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.invoke.MethodHandles.Lookup.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Basic test case for module access checks and Lookup.in.
 */
public class Main {

    private static Class<?> p1_Type1;        // m1, exported
    private static Class<?> p2_Type2;        // m1, not exported
    private static Class<?> q1_Type1;        // m2, exported
    private static Class<?> q2_Type2;        // m2, not exported
    private static Class<?> signalClass;     // java.base, not exported
    private static Class<?> unnamedClass;    // class in unnamed module

    @BeforeAll
    public static void setup() throws Exception {
        try {
            p1_Type1 = Class.forName("p1.Type1");
            p2_Type2 = Class.forName("p2.Type2");
            q1_Type1 = Class.forName("q1.Type1");
            q2_Type2 = Class.forName("q2.Type2");
            signalClass = Class.forName("jdk.internal.misc.Signal");
            unnamedClass = Class.forName("Unnamed");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }

        // check setup
        Module m1 = ModuleLayer.boot().findModule("m1").orElse(null);
        assertNotNull(m1);
        assertSame(m1, p1_Type1.getModule());
        assertSame(m1, p2_Type2.getModule());
        assertTrue(m1.isExported("p1"));
        assertFalse(m1.isExported("p2"));

        Module m2 = ModuleLayer.boot().findModule("m2").orElse(null);
        assertNotNull(m2);
        assertSame(m2, q1_Type1.getModule());
        assertSame(m2, q2_Type2.getModule());
        assertTrue(m2.isExported("q1"));
        assertFalse(m2.isExported("q2"));

        Module unnamedModule = unnamedClass.getModule();
        assertFalse(unnamedModule.isNamed());

        // m1 needs to read unnamed module
        Main.class.getModule().addReads(unnamedModule);
    }

    /**
     * MethodHandles.lookup()
     *
     * [A0] has module access
     * [A1] can access all public types in m1
     * [A2] can access public types in packages exported by modules that m1 reads
     * [A3] cannot access public types in non-exported modules of modules that m1 reads
     */
    @Test
    public void testLookup() throws Exception {
        Lookup lookup = MethodHandles.lookup();
        assertEquals(MODULE, lookup.lookupModes() & MODULE); // [A0]

        // m1
        findConstructor(lookup, p1_Type1, void.class); // [A1]
        findConstructor(lookup, p2_Type2, void.class); // [A1]

        // m2
        findConstructor(lookup, q1_Type1, void.class); // [A2]
        findConstructorExpectingIAE(lookup, q2_Type2, void.class); // [A3]

        // java.base
        findConstructor(lookup, Object.class, void.class); // [A2]
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class); // [A3]

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);  // [A3]
    }

    /**
     * Hop to lookup class in the same module
     *
     * [A0] module and public access is not lost
     */
    @Test
    public void testToSameModule() throws Exception {
        Lookup lookup = MethodHandles.lookup().in(p2_Type2);
        assertEquals(MODULE | PUBLIC, lookup.lookupModes()); // [A0]

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructor(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * Hop to lookup class in another named module
     *
     * [A0] has PUBLIC access if accessible; otherwise no access
     * [A1] old lookup class becomes previous lookup class
     */
    @Test
    public void testFromNamedToNamedModule() throws Exception {
        // m2/q1_Type1 is accessible to m1 whereas m2/q_Type2 is not accessible
        Lookup lookup = MethodHandles.lookup().in(q1_Type1);
        assertEquals(PUBLIC, lookup.lookupModes()); // [A0]
        assertSame(Main.class, lookup.previousLookupClass()); // [A1]

        Lookup lookup2 = MethodHandles.lookup().in(q2_Type2);
        assertEquals(0, lookup2.lookupModes());      // [A0]
        assertSame(Main.class, lookup2.previousLookupClass()); // [A1]

        // m1
        findConstructorExpectingIAE(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        findConstructorExpectingIAE(lookup2, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup2, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);  // m2/q1 is exported
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        findConstructorExpectingIAE(lookup2, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup2, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        findConstructorExpectingIAE(lookup2, Object.class, void.class);
        findConstructorExpectingIAE(lookup2, signalClass, void.class, String.class);

        // unnamed
        findConstructorExpectingIAE(lookup, unnamedClass, void.class);

        findConstructorExpectingIAE(lookup2, unnamedClass, void.class);

    }

    /**
     * Hop to lookup class in an unnamed module
     *
     * [A0] has PUBLIC access
     */
    @Test
    public void testFromNamedToUnnamedModule() throws Exception {
        Lookup lookup = MethodHandles.lookup().in(unnamedClass);
        assertEquals(PUBLIC, lookup.lookupModes()); // [A0]

        // m1
        findConstructor(lookup, p1_Type1, void.class);      // p1 is exported
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * Hop from unnamed to named module.
     *
     * [A0] retains PUBLIC access
     */
    @Test
    public void testFromUnnamedToNamedModule() throws Exception {
        Lookup lookup = MethodHandles.lookup();
        lookup = MethodHandles.privateLookupIn(unnamedClass, lookup).in(p1_Type1);
        assertEquals(PUBLIC, lookup.lookupModes()); // A0

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * MethodHandles.publicLookup()
     *
     * [A0] has UNCONDITIONAL access
     */
    @Test
    public void testPublicLookup() throws Exception {
        Lookup lookup = MethodHandles.publicLookup();
        assertEquals(UNCONDITIONAL, lookup.lookupModes()); // A0

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * Hop from publicLookup to accessible type in java.base
     *
     * [A0] has UNCONDITIONAL access
     */
    @Test
    public void testPublicLookupToBaseModule() throws Exception {
        Lookup lookup = MethodHandles.publicLookup().in(String.class);
        assertEquals(UNCONDITIONAL, lookup.lookupModes()); // A0

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }


    /**
     * Hop from publicLookup to accessible type in named module.
     *
     * [A0] has UNCONDITIONAL access
     */
    @Test
    public void testPublicLookupToAccessibleTypeInNamedModule() throws Exception {
        Lookup lookup = MethodHandles.publicLookup().in(p1_Type1);
        assertEquals(UNCONDITIONAL, lookup.lookupModes()); // A0

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * Teleport from publicLookup to inaccessible type in named module.
     *
     * [A0] has no access
     */
    @Test
    public void testPublicLookupToInaccessibleTypeInNamedModule() throws Exception {
        Lookup lookup = MethodHandles.publicLookup().in(p2_Type2);
        assertEquals(0, lookup.lookupModes()); // A0

        // m1
        findConstructorExpectingIAE(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructorExpectingIAE(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructorExpectingIAE(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructorExpectingIAE(lookup, unnamedClass, void.class);
    }

    /**
     * Teleport from publicLookup to public type in unnamed module
     *
     * [A0] has UNCONDITIONAL access
     */
    @Test
    public void testPublicLookupToUnnamedModule() throws Exception {
        Lookup lookup = MethodHandles.publicLookup().in(unnamedClass);
        assertEquals(UNCONDITIONAL, lookup.lookupModes()); // A0

        // m1
        findConstructor(lookup, p1_Type1, void.class);
        findConstructorExpectingIAE(lookup, p2_Type2, void.class);

        // m2
        findConstructor(lookup, q1_Type1, void.class);
        findConstructorExpectingIAE(lookup, q2_Type2, void.class);

        // java.base
        findConstructor(lookup, Object.class, void.class);
        findConstructorExpectingIAE(lookup, signalClass, void.class, String.class);

        // unnamed
        findConstructor(lookup, unnamedClass, void.class);
    }

    /**
     * Invokes Lookup findConstructor with a method type constructored from the
     * given return and parameter types, expecting IllegalAccessException to be
     * thrown.
     */
    static void findConstructorExpectingIAE(Lookup lookup,
                                            Class<?> clazz,
                                            Class<?> rtype,
                                            Class<?>... ptypes) throws Exception {
        assertThrows(IllegalAccessException.class, () -> findConstructor(lookup, clazz, rtype, ptypes));
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
}
