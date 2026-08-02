/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Field access to strictly-initialized final fields
 * @enablePreview
 * @library /test/lib
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.StrictProcessor StrictInitFinalFields$TestClass
 * @run junit/othervm --enable-final-field-mutation=ALL-UNNAMED ${test.main.class}
 * @run junit/othervm --illegal-final-field-mutation=allow ${test.main.class}
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import jdk.test.lib.helpers.StrictInit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrictInitFinalFields {

    /**
     * Test class with strictly-initialized final fields.
     */
    static class TestClass {
        @StrictInit
        private static final int DEFAULT_VALUE;
        static {
            DEFAULT_VALUE = 100;
        }

        @StrictInit
        private final int value;

        TestClass() {
            this.value = DEFAULT_VALUE;
            super();
        }

        TestClass(int i) {
            this.value = i;
            super();
        }
    }

    /**
     * Get/set strictly initialized static field.
     */
    @Test
    void testStaticField() throws Throwable {
        Field field = TestClass.class.getDeclaredField("DEFAULT_VALUE");
        assertTrue(field.isStrictInit());

        int initialValue = TestClass.DEFAULT_VALUE;
        int newValue = initialValue + 100;

        // Field.get/set before setAccessible
        assertEquals(initialValue, field.get(null));
        assertThrows(IllegalAccessException.class, () -> field.set(null, newValue));

        field.setAccessible(true);

        // Field.get/set after setAccessible
        assertEquals(initialValue, field.get(null));
        assertThrows(IllegalAccessException.class, () -> field.set(null, newValue));

        // VarHandle produced from reflected field
        var lookup = MethodHandles.lookup();
        VarHandle vh = lookup.unreflectVarHandle(field);
        assertEquals(initialValue, vh.get());
        assertThrows(UnsupportedOperationException.class, () -> vh.set(newValue));

        // Method handles produced from reflected field
        assertEquals(initialValue, (int) lookup.unreflectGetter(field).invokeExact());
        assertThrows(IllegalAccessException.class, () -> lookup.unreflectSetter(field));

        MethodHandle mh = lookup.findStaticGetter(TestClass.class, "DEFAULT_VALUE", int.class);
        assertEquals(initialValue, (int) mh.invokeExact());
        assertThrows(IllegalAccessException.class,
                () -> lookup.findStaticSetter(TestClass.class, "DEFAULT_VALUE", int.class));

        assertEquals(initialValue, TestClass.DEFAULT_VALUE);  // check unchanged
    }

    /**
     * Get/set strictly initialized instance field.
     */
    @Test
    void testInstanceField() throws Throwable {
        Field field = TestClass.class.getDeclaredField("value");
        assertTrue(field.isStrictInit());

        var obj = new TestClass(150);
        int initialValue = obj.value;
        int newValue = initialValue + 100;

        // Field.get/set before setAccessible
        assertEquals(initialValue, field.get(obj));
        assertThrows(IllegalAccessException.class, () -> field.set(obj, newValue));

        // Field.get/set after setAccessible
        field.setAccessible(true);
        assertEquals(initialValue, field.get(obj));
        assertThrows(IllegalAccessException.class, () -> field.set(obj, newValue));

        // VarHandle produced from reflected field
        var lookup = MethodHandles.lookup();
        VarHandle vh = lookup.unreflectVarHandle(field);
        assertEquals(initialValue, vh.get(obj));
        assertThrows(UnsupportedOperationException.class, () -> vh.set(obj, newValue));

        // Method handles produced from reflected field
        assertEquals(initialValue, (int) lookup.unreflectGetter(field).invokeExact(obj));
        assertThrows(IllegalAccessException.class, () -> lookup.unreflectSetter(field));

        MethodHandle mh = lookup.findGetter(TestClass.class, "value", int.class);
        assertEquals(initialValue, (int) mh.invokeExact(obj));
        assertThrows(IllegalAccessException.class,
                () -> lookup.findSetter(TestClass.class, "value", int.class));

        assertEquals(initialValue, obj.value);  // check unchanged
    }
}
