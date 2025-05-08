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

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Invoked by MutateFinalsTest, either directly or in a child VM, with the name of the
 * test method in this class to execute.
 */

public class MutateFinals {

    /**
     *  Usage: java MutateFinals <method-name>
     */
    public static void main(String[] args) throws Exception {
        invoke(args[0]);
    }

    /**
     * Invokes the given method.
     */
    static void invoke(String methodName) throws Exception {
        Method m = MutateFinals.class.getDeclaredMethod(methodName);
        m.invoke(null);
    }

    /**
     * JNI SetObjectField.
     */
    private static void testJniSetObjectField() throws Exception {
        class C {
            final Object value;
            C(Object value) {
                this.value = value;
            }
        }
        Object oldValue = new Object();
        Object newValue = new Object();
        var obj = new C(oldValue);
        jniSetObjectField(obj, newValue);
        assertTrue(obj.value == newValue);
    }

    /**
     * JNI SetBooleanField.
     */
    private static void testJniSetBooleanField() throws Exception {
        class C {
            final boolean value;
            C(boolean value) {
                this.value = value;
            }
        }
        var obj = new C(false);
        jniSetBooleanField(obj, true);
        assertTrue(obj.value);
    }

    /**
     * JNI SetByteField.
     */
    private static void testJniSetByteField() throws Exception {
        class C {
            final byte value;
            C(byte value) {
                this.value = value;
            }
        }
        byte oldValue = (byte) 1;
        byte newValue = (byte) 2;
        var obj = new C(oldValue);
        jniSetByteField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetCharField.
     */
    private static void testJniSetCharField() throws Exception {
        class C {
            final char value;
            C(char value) {
                this.value = value;
            }
        }
        char oldValue = 'A';
        char newValue = 'B';
        var obj = new C(oldValue);
        jniSetCharField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetShortField.
     */
    private static void testJniSetShortField() throws Exception {
        class C {
            final short value;
            C(short value) {
                this.value = value;
            }
        }
        short oldValue = (short) 1;
        short newValue = (short) 2;
        var obj = new C(oldValue);
        jniSetShortField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetIntField.
     */
    private static void testJniSetIntField() throws Exception {
        class C {
            final int value;
            C(int value) {
                this.value = value;
            }
        }
        int oldValue = 1;
        int newValue = 2;
        var obj = new C(oldValue);
        jniSetIntField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetLongField.
     */
    private static void testJniSetLongField() throws Exception {
        class C {
            final long value;
            C(long value) {
                this.value = value;
            }
        }
        long oldValue = 1L;
        long newValue = 2L;
        var obj = new C(oldValue);
        jniSetLongField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetFloatField.
     */
    private static void testJniSetFloatField() throws Exception {
        class C {
            final float value;
            C(float value) {
                this.value = value;
            }
        }
        float oldValue = 1.0f;
        float newValue = 2.0f;
        var obj = new C(oldValue);
        jniSetFloatField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetDoubleField.
     */
    private static void testJniSetDoubleField() throws Exception {
        class C {
            final double value;
            C(double value) {
                this.value = value;
            }
        }
        double oldValue = 1.0d;
        double newValue = 2.0d;
        var obj = new C(oldValue);
        jniSetDoubleField(obj, newValue);
        assertEquals(newValue, obj.value);
    }

    /**
     * JNI SetStaticObjectField.
     */
    private static void testJniSetStaticObjectField() throws Exception {
        class C {
            static final Object value = new Object();
        }
        Object newValue = new Object();
        jniSetStaticObjectField(C.class, newValue);
        assertTrue(C.value == newValue);
    }

    /**
     * JNI SetStaticBooleanField.
     */
    private static void testJniSetStaticBooleanField() throws Exception {
        class C {
            static final boolean value = false;
        }
        jniSetStaticBooleanField(C.class, true);
        // use reflection as field treated as constant by compiler
        boolean value = (boolean) C.class.getDeclaredField("value").get(null);
        assertTrue(value);
    }

    /**
     * JNI SetStaticByteField.
     */
    private static void testJniSetStaticByteField() throws Exception {
        class C {
            static final byte value = (byte) 1;
        }
        byte newValue = (byte) 2;
        jniSetStaticByteField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        byte value = (byte) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticCharField.
     */
    private static void testJniSetStaticCharField() throws Exception {
        class C {
            static final char value = 'A';
        }
        char newValue = 'B';
        jniSetStaticCharField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        char value = (char) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticShortField.
     */
    private static void testJniSetStaticShortField() throws Exception {
        class C {
            static final short value = (short) 1;
        }
        short newValue = (short) 2;
        jniSetStaticShortField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        short value = (short) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticIntField.
     */
    private static void testJniSetStaticIntField() throws Exception {
        class C {
            static final int value = 1;
        }
        int newValue = 2;
        jniSetStaticIntField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        int value = (int) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticLongField.
     */
    private static void testJniSetStaticLongField() throws Exception {
        class C {
            static final long value = 1L;
        }
        long newValue = 2L;
        jniSetStaticLongField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        long value = (long) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticFloatField.
     */
    private static void testJniSetStaticFloatField() throws Exception {
        class C {
            static final float value = 1.0f;
        }
        float newValue = 2.0f;
        jniSetStaticFloatField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        float value = (float) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    /**
     * JNI SetStaticDoubleField.
     */
    private static void testJniSetStaticDoubleField() throws Exception {
        class C {
            static final double value = 1.0d;
        }
        double newValue = 2.0f;
        jniSetStaticDoubleField(C.class, newValue);
        // use reflection as field treated as constant by compiler
        double value = (double) C.class.getDeclaredField("value").get(null);
        assertEquals(newValue, value);
    }

    private static native void jniSetObjectField(Object obj, Object value);
    private static native void jniSetBooleanField(Object obj, boolean value);
    private static native void jniSetByteField(Object obj, byte value);
    private static native void jniSetCharField(Object obj, char value);
    private static native void jniSetShortField(Object obj, short value);
    private static native void jniSetIntField(Object obj, int value);
    private static native void jniSetLongField(Object obj, long value);
    private static native void jniSetFloatField(Object obj, float value);
    private static native void jniSetDoubleField(Object obj, double value);

    private static native void jniSetStaticObjectField(Class<?> clazz, Object value);
    private static native void jniSetStaticBooleanField(Class<?> clazz, boolean value);
    private static native void jniSetStaticByteField(Class<?> clazz, byte value);
    private static native void jniSetStaticCharField(Class<?> clazz, char value);
    private static native void jniSetStaticShortField(Class<?> clazz, short value);
    private static native void jniSetStaticIntField(Class<?> clazz, int value);
    private static native void jniSetStaticLongField(Class<?> clazz, long value);
    private static native void jniSetStaticFloatField(Class<?> clazz, float value);
    private static native void jniSetStaticDoubleField(Class<?> clazz, double value);

    static {
        System.loadLibrary("MutateFinals");
    }

    private static void assertTrue(boolean e) {
        if (!e) throw new RuntimeException("Not true as expected");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new RuntimeException("Actual: " + actual + ", expected: " + expected);
        }
    }
}