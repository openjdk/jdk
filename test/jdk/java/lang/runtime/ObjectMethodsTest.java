/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8246774
 * @summary Basic tests for ObjectMethods
 * @run junit ObjectMethodsTest
 */

import java.util.List;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import static java.lang.invoke.MethodType.methodType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ObjectMethodsTest {

    public static class C {
        static final MethodType EQUALS_DESC = methodType(boolean.class, C.class, Object.class);
        static final MethodType HASHCODE_DESC = methodType(int.class, C.class);
        static final MethodType TO_STRING_DESC = methodType(String.class, C.class);

        static final MethodHandle[] ACCESSORS = accessors();
        static final String NAME_LIST = "x;y";
        private static MethodHandle[] accessors() {
            try {
                return  new MethodHandle[]{
                        MethodHandles.lookup().findGetter(C.class, "x", int.class),
                        MethodHandles.lookup().findGetter(C.class, "y", int.class),
                };
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private final int x;
        private final int y;
        C (int x, int y) { this.x = x; this.y = y; }
        public int x() { return x; }
        public int y() { return y; }
    }

    static class Empty {
        static final MethodType EQUALS_DESC = methodType(boolean.class, Empty.class, Object.class);
        static final MethodType HASHCODE_DESC = methodType(int.class, Empty.class);
        static final MethodType TO_STRING_DESC = methodType(String.class, Empty.class);
        static final MethodHandle[] ACCESSORS = new MethodHandle[] { };
        static final String NAME_LIST = "";
        Empty () {  }
    }

    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testEqualsC() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "equals", C.EQUALS_DESC, C.class, C.NAME_LIST, C.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        C c = new C(5, 5);
        assertTrue((boolean)handle.invokeExact(c, (Object)c));
        assertTrue((boolean)handle.invokeExact(c, (Object)new C(5, 5)));
        assertFalse((boolean)handle.invokeExact(c, (Object)new C(5, 4)));
        assertFalse((boolean)handle.invokeExact(c, (Object)new C(4, 5)));
        assertFalse((boolean)handle.invokeExact(c, (Object)null));
        assertFalse((boolean)handle.invokeExact(c, new Object()));
    }

    @Test
    public void testEqualsEmpty() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "equals", Empty.EQUALS_DESC, Empty.class, Empty.NAME_LIST, Empty.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        Empty e = new Empty();
        assertTrue((boolean)handle.invokeExact(e, (Object)e));
        assertTrue((boolean)handle.invokeExact(e, (Object)new Empty()));
        assertFalse((boolean)handle.invokeExact(e, (Object)null));
        assertFalse((boolean)handle.invokeExact(e, new Object()));
    }

    @Test
    public void testHashCodeC() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "hashCode", C.HASHCODE_DESC, C.class, "x;y", C.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        C c = new C(6, 7);
        int hc = (int)handle.invokeExact(c);
        assertEquals(hashCombiner(c.x(), c.y()), hc);

        assertEquals(hashCombiner(100, 1), (int)handle.invokeExact(new C(100, 1)));
        assertEquals(hashCombiner(0, 0), (int)handle.invokeExact(new C(0, 0)));
        assertEquals(hashCombiner(-1, 100), (int)handle.invokeExact(new C(-1, 100)));
        assertEquals(hashCombiner(100, 1), (int)handle.invokeExact(new C(100, 1)));
        assertEquals(hashCombiner(100, -1), (int)handle.invokeExact(new C(100, -1)));
    }

    @Test
    public void testHashCodeEmpty() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "hashCode", Empty.HASHCODE_DESC, Empty.class, "", Empty.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        Empty e = new Empty();
        assertEquals(0, (int)handle.invokeExact(e));
    }

    @Test
    public void testToStringC() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "toString", C.TO_STRING_DESC, C.class, C.NAME_LIST, C.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        assertEquals("C[x=8, y=9]", (String)handle.invokeExact(new C(8, 9))   );
        assertEquals("C[x=10, y=11]", (String)handle.invokeExact(new C(10, 11)) );
        assertEquals("C[x=100, y=-9]", (String)handle.invokeExact(new C(100, -9)));
        assertEquals("C[x=0, y=0]", (String)handle.invokeExact(new C(0, 0))   );
    }

    @Test
    public void testToStringEmpty() throws Throwable {
        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "toString", Empty.TO_STRING_DESC, Empty.class, Empty.NAME_LIST, Empty.ACCESSORS);
        MethodHandle handle = cs.dynamicInvoker();
        assertEquals("Empty[]", (String)handle.invokeExact(new Empty()));
    }

    Class<NullPointerException> NPE = NullPointerException.class;
    Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    @Test
    public void exceptions()  {
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "badName",  C.EQUALS_DESC,    C.class,         C.NAME_LIST, C.ACCESSORS));
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "toString", C.TO_STRING_DESC, C.class,         "x;y;z",     C.ACCESSORS));
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "toString", C.TO_STRING_DESC, C.class,         "x;y",       new MethodHandle[]{}));
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "toString", C.TO_STRING_DESC, this.getClass(), "x;y",       C.ACCESSORS));

        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "toString", C.EQUALS_DESC,    C.class, "x;y", C.ACCESSORS));
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "hashCode", C.TO_STRING_DESC, C.class, "x;y", C.ACCESSORS));
        assertThrows(IAE, () -> ObjectMethods.bootstrap(LOOKUP, "equals",   C.HASHCODE_DESC,  C.class, "x;y", C.ACCESSORS));

        record NamePlusType(String mn, MethodType mt) {}
        List<NamePlusType> namePlusTypeList = List.of(
                new NamePlusType("toString", C.TO_STRING_DESC),
                new NamePlusType("equals", C.EQUALS_DESC),
                new NamePlusType("hashCode", C.HASHCODE_DESC)
        );

        for (NamePlusType npt : namePlusTypeList) {
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, npt.mn(), npt.mt(), C.class, "x;y", null));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, npt.mn(), npt.mt(), C.class, "x;y", new MethodHandle[]{null}));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, npt.mn(), npt.mt(), C.class, null,  C.ACCESSORS));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, npt.mn(), npt.mt(), null,    "x;y", C.ACCESSORS));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, npt.mn(), null,     C.class, "x;y", C.ACCESSORS));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(LOOKUP, null,     npt.mt(), C.class, "x;y", C.ACCESSORS));
            assertThrows(NPE, () -> ObjectMethods.bootstrap(null, npt.mn(),     npt.mt(), C.class, "x;y", C.ACCESSORS));
        }
    }

    // Based on the ObjectMethods internal implementation
    private static int hashCombiner(int x, int y) {
        return x*31 + y;
    }
}
