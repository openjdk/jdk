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

/**
 * @test
 * @bug 8246774
 * @summary test several assertions on record classes members
 * @run junit RecordMemberTests
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class RecordMemberTests {
    public record R1(int i, int j) {}

    public record R2(int i, int j) {
        public R2 {}
    }

    public record R3(int i, int j) {
        public R3(int i, int j) {
            this.i = i;
            this.j = j;
        }
    }

    public record R4(int i, int j) {
        public R4(int i, int j) { this.i = this.j = 0; }
    }

    R1 r1 = new R1(1, 2);
    R2 r2 = new R2(1, 2);
    R3 r3 = new R3(1, 2);
    R4 r4 = new R4(1, 2);

    @Test
    public void testConstruction() {
        for (int i : new int[] { r1.i, r2.i, r3.i,
                                 r1.i(), r2.i(), r3.i() })
            assertEquals(1, i);

        for (int j : new int[] { r1.j, r2.j, r3.j,
                                 r1.j(), r2.j(), r3.j() })
            assertEquals(2, j);

        assertEquals(0, r4.i);
        assertEquals(0, r4.j);
    }

    @Test
    public void testConstructorParameterNames() throws ReflectiveOperationException {
        for (Class cl : List.of(R1.class, R2.class, R3.class, R4.class)) {
            Constructor c = cl.getConstructor(int.class, int.class);
            assertNotNull(c);
            Parameter[] parameters = c.getParameters();
            assertEquals(2, parameters.length);
            assertEquals("i", parameters[0].getName());
            assertEquals("j", parameters[1].getName());
        }
    }

    @Test
    public void testSuperclass() {
        assertEquals(Record.class, R1.class.getSuperclass());
        // class is final
        assertTrue((R1.class.getModifiers() & Modifier.FINAL) != 0);
    }

    @Test
    public void testMandatedMembersPresent() throws ReflectiveOperationException {
        // fields are present, of the right type, final and private
        assertEquals(2, R1.class.getDeclaredFields().length);
        for (String s : List.of("i", "j")) {
            Field iField = R1.class.getDeclaredField(s);
            assertEquals(int.class, iField.getType());
            assertEquals(0, (iField.getModifiers() & Modifier.STATIC));
            assertTrue((iField.getModifiers() & Modifier.PRIVATE) != 0);
            assertTrue((iField.getModifiers() & Modifier.FINAL) != 0);
        }

        // methods are present, of the right descriptor, and public/instance/concrete
        for (String s : List.of("i", "j")) {
            Method iMethod = R1.class.getDeclaredMethod(s);
            assertEquals(int.class, iMethod.getReturnType());
            assertEquals(0, iMethod.getParameterCount());
            assertEquals(0, (iMethod.getModifiers() & (Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC | Modifier.ABSTRACT)));
        }

        Constructor c = R1.class.getConstructor(int.class, int.class);
        R1 r1 = (R1) c.newInstance(1, 2);
        assertEquals(1, r1.i());
        assertEquals(2, r1.j());
    }

    record OrdinaryMembers(int x) {
        public static String ss;
        public static String ssf () { return ss; }
        public String sf () { return "instance"; }
    }

    @Test
    public void testOrdinaryMembers() {
        OrdinaryMembers.ss = "foo";
        assertEquals("foo", OrdinaryMembers.ssf());
        OrdinaryMembers o = new OrdinaryMembers(3);
        assertEquals("instance", o.sf());
    }

    class LocalRecordHelper {
        Class<?> m(int x) {
            record R (int x) { }
            assertEquals(x, new R(x).x());
            return R.class;
        }
    }

    @Test
    public void testLocalRecordsStatic() {
        Class<?> c = new LocalRecordHelper().m(3);
        String message = c.toGenericString();
        assertTrue(c.isRecord(), message);
        assertTrue((c.getModifiers() & Modifier.STATIC) != 0, message);
        assertTrue((c.getModifiers() & Modifier.FINAL)  != 0, message);
    }

    static class NestedRecordHelper {
        record R1(int x) { }

        static class Nested {
            record R2(int x) { }
        }

        Class<?> m() {
            record R4(int x) { }
            return R4.class;
        }

        Class<?> m2() {
            Supplier<Class<?>> s = () -> {
                record R5(int x) { }
                return R5.class;
            };
            return s.get();
        }

        static Class<?> m3() {
            record R6(int x) { }
            return R6.class;
        }

        static Class<?> m4() {
            Supplier<Class<?>> s = () -> {
                record R7(int x) { }
                return R7.class;
            };
            return s.get();
        }
    }

    @Test
    public void testNestedRecordsStatic() {
        NestedRecordHelper n = new NestedRecordHelper();
        for (Class<?> c : List.of(NestedRecordHelper.R1.class,
                                  NestedRecordHelper.Nested.R2.class,
                                  n.m(),
                                  n.m2(),
                                  NestedRecordHelper.m3(),
                                  NestedRecordHelper.m4())) {
            String message = c.toGenericString();
            assertTrue(c.isRecord(), message);
            assertTrue((c.getModifiers() & Modifier.STATIC) != 0, message);
            assertTrue((c.getModifiers() & Modifier.FINAL) != 0, message);
        }
    }
}
