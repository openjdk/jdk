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
package p1;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.ServiceLoader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test mutating final fields from different modules.
 */

class TestMain {
    // the names of modules that are can mutate finals in m1/p1
    static Set<String> allowedToMutate;
    static Module mainModule;
    static String mainPackageName;

    @BeforeAll
    static void setup() {
        String s = System.getProperty("allowedToMutate");
        if (s == null) {
            allowedToMutate = Set.of();
        } else {
            String[] names = s.split(",");
            allowedToMutate = Stream.of(names).collect(Collectors.toSet());
        }

        mainModule = TestMain.class.getModule();
        mainPackageName = TestMain.class.getPackageName();
    }

    /**
     * Returns a stream of Mutators that can mutate final fields in m1/p1.
     */
    static Stream<Mutator> mutators() {
        return ServiceLoader.load(Mutator.class)
                .stream()
                .filter(p -> allowedToMutate.contains(p.type().getModule().getName()))
                .map(ServiceLoader.Provider::get);
    }

    /**
     * Returns a stream of Mutators that can not mutate final fields in m1/p1.
     */
    static Stream<Mutator> deniedMutators() {
        List<Mutator> mutators = ServiceLoader.load(Mutator.class)
                .stream()
                .filter(p -> !allowedToMutate.contains(p.type().getModule().getName()))
                .map(ServiceLoader.Provider::get)
                .toList();
        if (mutators.isEmpty()) {
            // can't return an empty stream at this time
            return Stream.of(Mutator.throwing());
        } else {
            return mutators.stream();
        }
    }

    @ParameterizedTest()
    @MethodSource("mutators")
    void testFieldSet(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.set(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetBoolean(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setBoolean(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setBoolean(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetByte(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setByte(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setByte(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetChar(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setChar(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setChar(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetShort(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setShort(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setShort(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetInt(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setInt(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setInt(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetLong(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setLong(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setLong(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetFloat(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setFloat(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setFloat(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testFieldSetDouble(Mutator mutator) throws Exception {
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
        assertThrows(IllegalAccessException.class, () -> mutator.setDouble(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        f.setAccessible(true);
        mutator.setDouble(f, obj, newValue);
        assertTrue(obj.value == newValue);
    }

    @ParameterizedTest
    @MethodSource("mutators")
    void testUnreflectSetter(Mutator mutator) throws Throwable {
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
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        f.setAccessible(true);
        mutator.unreflectSetter(f).invokeExact(obj, newValue);
        assertTrue(obj.value == newValue);
    }


    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetThrows(Mutator mutator) throws Exception {
        class C {
            final Object value;
            C(Object value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        Object oldValue = new Object();
        Object newValue = new Object();
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.set(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetByteThrows(Mutator mutator) throws Exception {
        class C {
            final byte value;
            C(byte value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        byte oldValue = (byte) 1;
        byte newValue = (byte) 2;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setByte(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setByte(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetCharThrows(Mutator mutator) throws Exception {
        class C {
            final char value;
            C(char value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        char oldValue = 'A';
        char newValue = 'B';
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setChar(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setChar(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetShortThrows(Mutator mutator) throws Exception {
        class C {
            final short value;
            C(short value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        short oldValue = (short) 1;
        short newValue = (short) 2;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setShort(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setShort(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetIntThrows(Mutator mutator) throws Exception {
        class C {
            final int value;
            C(int value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        int oldValue = 1;
        int newValue = 2;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setInt(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setInt(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetLongThrows(Mutator mutator) throws Exception {
        class C {
            final long value;
            C(long value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        long oldValue = 1;
        long newValue = 2;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setLong(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setLong(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetFloatThrows(Mutator mutator) throws Exception {
        class C {
            final float value;
            C(float value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        float oldValue = 1.0f;
        float newValue = 2.0f;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setFloat(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setFloat(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testFieldSetDoubleThrows(Mutator mutator) throws Exception {
        class C {
            final double value;
            C(double value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        double oldValue = 1.0d;
        double newValue = 2.0d;
        var obj = new C(oldValue);
        assertThrows(IllegalAccessException.class, () -> mutator.setDouble(f, obj, newValue));
        assertTrue(obj.value == oldValue);

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.setDouble(f, obj, newValue));
        assertTrue(obj.value == oldValue);
    }

    @ParameterizedTest
    @MethodSource("deniedMutators")
    void testUnreflectSetterThrows(Mutator mutator) throws Exception {
        class C {
            final int value;
            C(int value) {
                this.value = value;
            }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);

        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));

        // open package to mutator module, should have no effect on set method
        mainModule.addOpens(mainPackageName, mutator.getClass().getModule());
        assertThrows(IllegalAccessException.class, () -> mutator.unreflectSetter(f));
    }
}
