/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import java.lang.invoke.MethodType;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import e1.CrackM5Access;

import static java.lang.invoke.MethodHandles.Lookup.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ModuleAccessTest {
    static ModuleLookup m3;
    static ModuleLookup m4;
    static ModuleLookup m5;
    static Map<String, ModuleLookup> moduleLookupMap = new HashMap<>();
    static Lookup privLookupIn;
    static Lookup privLookupIn2;
    static Lookup unnamedLookup;
    static Class<?> unnamed;
    static Class<?> unnamed1;

    @BeforeAll
    public static void setup() throws Exception {
        m3 = new ModuleLookup("m3", 'C');
        m4 = new ModuleLookup("m4", 'D');
        m5 = new ModuleLookup("m5", 'E');
        moduleLookupMap.put(m3.name(), m3);
        moduleLookupMap.put(m4.name(), m4);
        moduleLookupMap.put(m5.name(), m5);

        privLookupIn = MethodHandles.privateLookupIn(m3.type2, m3.lookup);
        privLookupIn2 = MethodHandles.privateLookupIn(m4.type1, m3.lookup);

        unnamed = Class.forName("Unnamed");
        unnamed1 = Class.forName("Unnamed1");
        unnamedLookup = (Lookup)unnamed.getMethod("lookup").invoke(null);

        // m5 reads m3
        CrackM5Access.addReads(m3.module);
        CrackM5Access.addReads(unnamed.getModule());
    }

    public static Object[][] samePackage() throws Exception {
        return new Object[][] {
            { m3.lookup,     m3.type2 },
            { privLookupIn,  m3.type1 },
            { privLookupIn2, m4.type2 },
            { unnamedLookup, unnamed1 }
        };
    }

    /**
     * Test lookup.in(T) where T is in the same package of the lookup class.
     *
     * [A0] targetClass becomes the lookup class
     * [A1] no change in previous lookup class
     * [A2] PROTECTED, PRIVATE and ORIGINAL are dropped
     */
    @ParameterizedTest
    @MethodSource("samePackage")
    public void testLookupInSamePackage(Lookup lookup, Class<?> targetClass) throws Exception {
        Class<?> lookupClass = lookup.lookupClass();
        Lookup lookup2 = lookup.in(targetClass);

        assertTrue(lookupClass.getPackage() == targetClass.getPackage());
        assertTrue(lookupClass.getModule() == targetClass.getModule());
        assertTrue(lookup2.lookupClass() == targetClass);   // [A0]
        assertTrue(lookup2.previousLookupClass() == lookup.previousLookupClass());  // [A1]
        assertTrue(lookup2.lookupModes() == (lookup.lookupModes() & ~(PROTECTED|PRIVATE|ORIGINAL)));  // [A2]
    }

    public static Object[][] sameModule() throws Exception {
        return new Object[][] {
            { m3.lookup,     m3.type3},
            { privLookupIn,  m3.type3},
            { privLookupIn2, m4.type3}
        };
    }

    /**
     * Test lookup.in(T) where T is in the same module but different package from the lookup class.
     *
     * [A0] targetClass becomes the lookup class
     * [A1] no change in previous lookup class
     * [A2] PROTECTED, PRIVATE, PACKAGE and ORIGINAL are dropped
     */
    @ParameterizedTest
    @MethodSource("sameModule")
    public void testLookupInSameModule(Lookup lookup, Class<?> targetClass) throws Exception {
        Class<?> lookupClass = lookup.lookupClass();
        Lookup lookup2 = lookup.in(targetClass);

        assertNotSame(targetClass.getPackage(), lookupClass.getPackage());
        assertSame(targetClass.getModule(), lookupClass.getModule());
        assertSame(targetClass, lookup2.lookupClass());   // [A0]
        assertSame(lookup.previousLookupClass(), lookup2.previousLookupClass());  // [A1]
        assertEquals(lookup.lookupModes() & ~(PROTECTED | PRIVATE | PACKAGE | ORIGINAL), lookup2.lookupModes()); // [A2]
    }

    public static Object[][] anotherModule() throws Exception {
        return new Object[][] {
            { m3.lookup, m4.type1, m5, m5.accessibleTypesTo(m3.module, m4.module) },
            { m4.lookup, m5.type2, m3, m3.accessibleTypesTo(m4.module, m5.module) },
            { m3.lookup, m5.type1, m4, m4.accessibleTypesTo(m3.module, m5.module) },
            { m5.lookup, unnamed,  m3, m3.accessibleTypesTo(m5.module, unnamed.getModule()) },
        };
    }

    /**
     * Test lookup.in(T) where T is in a different module from the lookup class.
     *
     * [A0] targetClass becomes the lookup class
     * [A1] lookup class becomes previous lookup class
     * [A2] PROTECTED, PRIVATE, PACKAGE, MODULE and ORIGINAL are dropped
     * [A3] no access to module internal types in m0 and m1
     * [A4] if m1 reads m0, can access public types in m0; otherwise no access.
     * [A5] can access public types in m1 exported to m0
     * [A6] can access public types in m2 exported to m0 and m1
     */
    @ParameterizedTest
    @MethodSource("anotherModule")
    public void testLookupInAnotherModule(Lookup lookup, Class<?> targetClass,
                                          ModuleLookup m2, Set<Class<?>> otherTypes) throws Exception {
        Class<?> lookupClass = lookup.lookupClass();
        Module m0 = lookupClass.getModule();
        Module m1 = targetClass.getModule();

        assertNotSame(m1, m0);
        assertTrue(m0.canRead(m1));
        assertTrue(m1.isExported(targetClass.getPackageName(), m0));

        Lookup lookup2 = lookup.in(targetClass);
        assertSame(targetClass, lookup2.lookupClass());   // [A0]
        assertSame(lookup.lookupClass(), lookup2.previousLookupClass());  // [A1]
        assertEquals(lookup.lookupModes() & ~(PROTECTED | PRIVATE | PACKAGE | MODULE | ORIGINAL), lookup2.lookupModes());  // [A2]

        // [A3] no access to module internal type in m0
        // [A4] if m1 reads m0,
        // [A4]   no access to public types exported from m0 unconditionally
        // [A4]   no access to public types exported from m0
        ModuleLookup ml0 = moduleLookupMap.get(m0.getName());
        if (m1.canRead(m0)) {
            for (Class<?> type : ml0.unconditionalExports()) {
                testAccess(lookup2, type);
            }
            for (Class<?> type : ml0.qualifiedExportsTo(m1)) {
                testAccess(lookup2, type);
            }
        } else {
            findConstructorExpectingIAE(lookup2, ml0.type1, void.class);
            findConstructorExpectingIAE(lookup2, ml0.type2, void.class);
            findConstructorExpectingIAE(lookup2, ml0.type3, void.class);
        }

        // [A5] can access public types exported from m1 unconditionally
        // [A5] can access public types exported from m1 to m0
        if (m1.isNamed()) {
            ModuleLookup ml1 = moduleLookupMap.get(m1.getName());
            assertTrue(ml1.unconditionalExports().size() + ml1.qualifiedExportsTo(m0).size() > 0);
            for (Class<?> type : ml1.unconditionalExports()) {
                testAccess(lookup2, type);
            }
            for (Class<?> type : ml1.qualifiedExportsTo(m0)) {
                testAccess(lookup2, type);
            }
        } else {
            // unnamed module
            testAccess(lookup2, unnamed1);
        }

        // [A5] can access public types exported from m2 unconditionally
        // [A5] can access public types exported from m2 to m0 and m1
        for (Class<?> type : otherTypes) {
            assertSame(m2.module, type.getModule());
            testAccess(lookup2, type);
        }

        // test inaccessible types
        for (Class<?> type : Set.of(m2.type1, m2.type2, m2.type3)) {
            if (!otherTypes.contains(type)) {
                // type is accessible to this lookup
                assertThrows(IllegalAccessException.class, () -> lookup2.accessClass(type));

                findConstructorExpectingIAE(lookup2, type, void.class);
            }
        }
    }

    public void testAccess(Lookup lookup, Class<?> type) throws Exception {
        // type is accessible to this lookup
        assertSame(type, lookup.accessClass(type));

        // can find constructor
        findConstructor(lookup, type, void.class);

        Module m0 = lookup.previousLookupClass().getModule();
        Module m1 = lookup.lookupClass().getModule();
        Module m2 = type.getModule();

        assertTrue(m0 != m1 && m0 != null);
        assertEquals(0, lookup.lookupModes() & MODULE);
        assertTrue(m0 != m2 || m1 != m2);

        MethodHandles.Lookup lookup2 = lookup.in(type);
        if (m2 == m1) {
            // the same module of the lookup class
            assertSame(type, lookup2.lookupClass());
            assertSame(lookup.previousLookupClass(), lookup2.previousLookupClass());
        } else if (m2 == m0) {
            // hop back to the module of the previous lookup class
            assertSame(type, lookup2.lookupClass());
            assertSame(lookup.lookupClass(), lookup2.previousLookupClass());
        } else {
            // hop to a third module
            assertSame(type, lookup2.lookupClass());
            assertSame(lookup.lookupClass(), lookup2.previousLookupClass());
            assertEquals(0, lookup2.lookupModes());
        }
    }

    public static Object[][] thirdModule() throws Exception {
        return new Object[][] {
            { m3.lookup, m4.type1, m5.type1},
            { m3.lookup, m4.type2, m5.type1},
            { unnamedLookup, m3.type1, m4.type1 },
        };
    }

    /**
     * Test lookup.in(c1).in(c2) where c1 is in second module and c2 is in a third module.
     *
     * [A0] c2 becomes the lookup class
     * [A1] c1 becomes previous lookup class
     * [A2] all access bits are dropped
     */
    @ParameterizedTest
    @MethodSource("thirdModule")
    public void testLookupInThirdModule(Lookup lookup, Class<?> c1, Class<?> c2) throws Exception {
        Class<?> c0 = lookup.lookupClass();
        Module m0 = c0.getModule();
        Module m1 = c1.getModule();
        Module m2 = c2.getModule();

        assertTrue(m0 != m1 && m0 != m2 && m1 != m2);
        assertTrue(m0.canRead(m1) && m0.canRead(m2));
        assertTrue(m1.canRead(m2));
        assertTrue(m1.isExported(c1.getPackageName(), m0));
        assertTrue(m2.isExported(c2.getPackageName(), m0) && m2.isExported(c2.getPackageName(), m1));

        Lookup lookup1 = lookup.in(c1);
        assertSame(c1, lookup1.lookupClass());
        assertSame(c0, lookup1.previousLookupClass());
        assertEquals(lookup.lookupModes() & ~(PROTECTED | PRIVATE | PACKAGE | MODULE | ORIGINAL), lookup1.lookupModes());

        Lookup lookup2 = lookup1.in(c2);
        assertSame(c2, lookup2.lookupClass());                    // [A0]
        assertSame(c1, lookup2.previousLookupClass());            // [A1]
        assertEquals(0, lookup2.lookupModes(), lookup2.toString()); // [A2]
    }

    public static Object[][] privLookupIn() throws Exception {
        return new Object[][] {
            { m3.lookup,  m4.type1 },
            { m3.lookup,  m5.type1 },
            { m4.lookup,  m5.type2 },
            { m5.lookup,  m3.type3 },
            { m5.lookup,  unnamed  }
        };
    }

    /**
     * Test privateLookupIn(T, lookup) where T is in another module
     *
     * [A0] full capabilities except MODULE bit
     * [A1] target class becomes the lookup class
     * [A2] the lookup class becomes previous lookup class
     * [A3] IAE thrown if lookup has no MODULE access
     */
    @ParameterizedTest
    @MethodSource("privLookupIn")
    public void testPrivateLookupIn(Lookup lookup, Class<?> targetClass) throws Exception {
        Module m0 = lookup.lookupClass().getModule();
        Module m1 = targetClass.getModule();

        // privateLookupIn from m0 to m1
        assertNotSame(m1, m0);
        assertTrue(m1.isOpen(targetClass.getPackageName(), m0));
        Lookup privLookup1 = MethodHandles.privateLookupIn(targetClass, lookup);
        assertEquals(PROTECTED | PRIVATE | PACKAGE | PUBLIC, privLookup1.lookupModes());  // [A0]
        assertSame(targetClass, privLookup1.lookupClass());                    // [A1]
        assertSame(lookup.lookupClass(), privLookup1.previousLookupClass());   // [A2]

        // privLookup1 has no MODULE access; can't do privateLookupIn
        assertThrows(IllegalAccessException.class, () -> MethodHandles.privateLookupIn(targetClass, privLookup1)); // [A3]
    }

    /**
     * Test member access from the Lookup returned from privateLookupIn
     */
    @Test
    public void testPrivateLookupAccess() throws Exception {
        Class<?> staticsClass = e1.Statics.class;
        Lookup privLookup1 = MethodHandles.privateLookupIn(staticsClass, m4.lookup);
        assertEquals(0, (privLookup1.lookupModes() & MODULE));
        assertSame(staticsClass, privLookup1.lookupClass());
        assertSame(m4.lookup.lookupClass(), privLookup1.previousLookupClass());

        // access private member and default package member in m5
        MethodType mtype = MethodType.methodType(void.class);
        MethodHandle mh1 = privLookup1.findStatic(staticsClass, "privateMethod", mtype);
        MethodHandle mh2 = privLookup1.findStatic(staticsClass, "packageMethod", mtype);

        // access public member in exported types from m5 to m4
        findConstructor(privLookup1, m5.type1, void.class);
        // no access to public member in non-exported types to m5
        findConstructorExpectingIAE(privLookup1, m5.type3, void.class);

        // no access to public types in m4 since m5 does not read m4
        assertFalse(m5.module.canRead(m4.module));
        findConstructorExpectingIAE(privLookup1, m4.type1, void.class);

        // teleport from a privateLookup to another class in the same package
        // lose private access
        Lookup privLookup2 = MethodHandles.privateLookupIn(m5.type1, m4.lookup);
        Lookup lookup = privLookup2.in(staticsClass);
        assertEquals(0, lookup.lookupModes() & PRIVATE);
        MethodHandle mh3 = lookup.findStatic(staticsClass, "packageMethod", mtype);
        assertThrows(IllegalAccessException.class, () -> lookup.findStatic(staticsClass, "privateMethod", mtype));
    }

    /**
     * Test member access from the Lookup returned from privateLookupIn and
     * the lookup mode after dropLookupMode
     */
    @Test
    public void testDropLookupMode() throws Exception {
        Lookup lookup = MethodHandles.privateLookupIn(m5.type1, m4.lookup);
        assertEquals(0, lookup.lookupModes() & MODULE);

        Lookup lookup1 = lookup.dropLookupMode(PRIVATE);
        assertEquals(lookup1.lookupModes(), lookup.lookupModes() & ~(PROTECTED | PRIVATE));
        Lookup lookup2 = lookup.dropLookupMode(PACKAGE);
        assertEquals(lookup2.lookupModes(), lookup.lookupModes() & ~(PROTECTED | PRIVATE | PACKAGE));
        Lookup lookup3 = lookup.dropLookupMode(MODULE);
        assertEquals(lookup3.lookupModes(), lookup.lookupModes() & ~(PROTECTED | PRIVATE | PACKAGE));
        Lookup lookup4 = lookup.dropLookupMode(PUBLIC);
        assertEquals(0, lookup4.lookupModes());

    }

    /**
     * Test no access to a public member on a non-public class
     */
    @Test
    public void testPrivateLookupOnNonPublicType() throws Exception {
        // privateLookup in a non-public type
        Class<?> nonPUblicType = Class.forName("e1.NonPublic");
        Lookup privLookup = MethodHandles.privateLookupIn(nonPUblicType, m4.lookup);
        MethodType mtype = MethodType.methodType(void.class);
        MethodHandle mh1 = privLookup.findStatic(nonPUblicType, "publicStatic", mtype);

        // drop MODULE access i.e. only PUBLIC access
        Lookup lookup = privLookup.dropLookupMode(MODULE);
        assertEquals(PUBLIC, lookup.lookupModes());
        assertThrows(IllegalAccessException.class, () -> lookup.findStatic(nonPUblicType, "publicStatic", mtype));
    }

    @Test
    public void testPublicLookup() {
        Lookup publicLookup = MethodHandles.publicLookup();
        Lookup pub1 = publicLookup.in(m3.type1);
        Lookup pub2 = pub1.in(java.lang.String.class);
        Lookup pub3 = pub2.in(java.lang.management.ThreadMXBean.class);
        Lookup pub4 = pub3.dropLookupMode(UNCONDITIONAL);

        assertSame(Object.class, publicLookup.lookupClass());
        assertEquals(UNCONDITIONAL, publicLookup.lookupModes());
        assertSame(m3.type1, pub1.lookupClass());
        assertEquals(UNCONDITIONAL, pub1.lookupModes());
        assertSame(String.class, pub2.lookupClass());
        assertEquals(UNCONDITIONAL, pub2.lookupModes());
        assertSame(ThreadMXBean.class, pub3.lookupClass());
        assertEquals(UNCONDITIONAL, pub3.lookupModes());
        assertEquals(0, pub4.lookupModes());

        // publicLookup has no MODULE access; can't do privateLookupIn
        assertThrows(IllegalAccessException.class, () -> MethodHandles.privateLookupIn(m4.type1, pub1));
    }

    static class ModuleLookup {
        private final Module module;
        private final Set<String> packages;
        private final Lookup lookup;
        private final Class<?> type1;
        private final Class<?> type2;
        private final Class<?> type3;

        ModuleLookup(String mn, char c) throws Exception {
            this.module = ModuleLayer.boot().findModule(mn).orElse(null);
            assertNotNull(this.module);
            this.packages = module.getDescriptor().packages();
            assertTrue(packages.size() <= 3);
            Lookup lookup = null;
            Class<?> type1 = null;
            Class<?> type2 = null;
            Class<?> type3 = null;
            for (String pn : packages) {
                char n = pn.charAt(pn.length() - 1);
                switch (n) {
                    case '1':
                        type1 = Class.forName(pn + "." + c + "1");
                        type2 = Class.forName(pn + "." + c + "2");
                        Method m = type1.getMethod("lookup");
                        lookup = (Lookup) m.invoke(null);
                        break;
                    case '2':
                        type3 = Class.forName(pn + "." + c + "3");
                        break;

                    default:
                }
            }
            this.lookup = lookup;
            this.type1 = type1;
            this.type2 = type2;
            this.type3 = type3;
        }

        String name() {
            return module.getName();
        }

        /*
         * Returns the set of types that are unconditionally exported.
         */
        Set<Class<?>> unconditionalExports() {
            return Stream.of(type1, type2, type3)
                         .filter(c -> module.isExported(c.getPackageName()))
                         .collect(Collectors.toSet());
        }

        /*
         * Returns the set of types that are qualifiedly exported to the specified
         * caller module
         */
        Set<Class<?>> qualifiedExportsTo(Module caller) {
            if (caller.canRead(this.module)) {
                return Stream.of(type1, type2, type3)
                             .filter(c -> !module.isExported(c.getPackageName())
                                          && module.isExported(c.getPackageName(), caller))
                             .collect(Collectors.toSet());
            } else {
                return Set.of();
            }
        }

        /*
         * Returns the set of types that are qualifiedly exported to the specified
         * caller module
         */
        Set<Class<?>> accessibleTypesTo(Module m0, Module m1) {
            if (m0.canRead(this.module) && m1.canRead(this.module)) {
                return Stream.of(type1, type2, type3)
                             .filter(c -> module.isExported(c.getPackageName(), m0)
                                          && module.isExported(c.getPackageName(), m1))
                             .collect(Collectors.toSet());
            } else {
                return Set.of();
            }
        }

        /*
         * Returns the set of types that are open to the specified caller
         * unconditionally or qualifiedly.
         */
        Set<Class<?>> opensTo(Module caller) {
            if (caller.canRead(this.module)) {
                return Stream.of(type1, type2, type3)
                             .filter(c -> module.isOpen(c.getPackageName(), caller))
                             .collect(Collectors.toSet());
            } else {
                return Set.of();
            }
        }

        public String toString() {
            return module.toString();
        }
    }

    /**
     * Invokes Lookup findConstructor with a method type constructed from the
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
