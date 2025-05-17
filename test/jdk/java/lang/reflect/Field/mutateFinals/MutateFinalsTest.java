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

/*
 * @test
 * @bug 8353835
 * @summary Test Field.set and Lookup.unreflectSetter on final instance fields
 * @run junit/othervm --enable-final-field-mutation=ALL-UNNAMED -DwriteAccess=true MutateFinalsTest
 * @run junit/othervm --illegal-final-field-mutation=allow -DwriteAccess=true MutateFinalsTest
 * @run junit/othervm --illegal-final-field-mutation=deny -DwriteAccess=false MutateFinalsTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MutateFinalsTest {
    static boolean writeAccess;

    @BeforeAll
    static void setup() throws Exception {
        String s = System.getProperty("writeAccess");
        assertNotNull(s);
        writeAccess = Boolean.valueOf(s);
    }

    @Test
    void testFieldSet() throws Exception {
        class C {
            final Object value;
            C(Object value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        Object oldValue = new Object();
        Object newValue = new Object();
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.set(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.set(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.set(null, newValue));
        if (writeAccess) {
            f.set(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.set(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetBoolean() throws Throwable {
        class C {
            final boolean value;
            C(boolean value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        boolean oldValue = false;
        boolean newValue = true;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setBoolean(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setBoolean(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.set(null, newValue));
        if (writeAccess) {
            f.setBoolean(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setBoolean(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetByte() throws Exception {
        class C {
            final byte value;
            C(byte value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        byte oldValue = (byte) 1;
        byte newValue = (byte) 2;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setByte(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setByte(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setByte(null, newValue));
        if (writeAccess) {
            f.setByte(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setByte(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetChar() throws Exception {
        class C {
            final char value;
            C(char value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        char oldValue = 'A';
        char newValue = 'B';
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setChar(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setChar(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setChar(null, newValue));
        if (writeAccess) {
            f.setChar(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setChar(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetShort() throws Exception {
        class C {
            final short value;
            C(short value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        short oldValue = (short) 1;
        short newValue = (short) 2;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setShort(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setShort(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setShort(null, newValue));
        if (writeAccess) {
            f.setShort(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setShort(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetInt() throws Exception {
        class C {
            final int value;
            C(int value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        int oldValue = 1;
        int newValue = 2;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setInt(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setInt(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setInt(null, newValue));
        if (writeAccess) {
            f.setInt(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setInt(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetLong() throws Exception {
        class C {
            final long value;
            C(long value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        long oldValue = 1L;
        long newValue = 2L;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setLong(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setLong(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setLong(null, newValue));
        if (writeAccess) {
            f.setLong(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setLong(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetFloat() throws Exception {
        class C {
            final float value;
            C(float value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        float oldValue = 1.0f;
        float newValue = 2.0f;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setFloat(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setFloat(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setFloat(null, newValue));
        if (writeAccess) {
            f.setFloat(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setFloat(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testFieldSetDouble() throws Exception {
        class C {
            final double value;
            C(double value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        double oldValue = 1.0d;
        double newValue = 2.0d;
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(NullPointerException.class, () -> f.setDouble(null, newValue));
        assertThrows(IllegalAccessException.class, () -> f.setDouble(obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        assertThrows(NullPointerException.class, () -> f.setDouble(null, newValue));
        if (writeAccess) {
            f.setDouble(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> f.setDouble(obj, newValue));
            assertTrue(obj.value == oldValue);
        }
    }

    @Test
    void testUnreflectSetter() throws Throwable {
        class C {
            final Object value;
            C(Object value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");

        Object oldValue = new Object();
        var obj = new C(oldValue);

        f.setAccessible(false);
        assertThrows(IllegalAccessException.class, () -> MethodHandles.lookup().unreflectSetter(f));

        f.setAccessible(true);
        if (writeAccess) {
            Object newValue = new Object();
            MethodHandles.lookup().unreflectSetter(f).invoke(obj, newValue);
            assertTrue(obj.value == newValue);
        } else {
            assertThrows(IllegalAccessException.class, () -> MethodHandles.lookup().unreflectSetter(f));
        }
    }
}