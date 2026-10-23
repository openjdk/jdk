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
 * @summary Test default behavior of 1-arg and 2-arg newConstructorForSerialization methods
 * @modules jdk.unsupported
 * @run junit/othervm ${test.main.class}
 */

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.stream.Stream;
import sun.reflect.ReflectionFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class NewConstructorForSerialization {
    static ReflectionFactory factory;

    @BeforeAll
    static void init() {
        factory = ReflectionFactory.getReflectionFactory();
    }

    static class C1 {
        static final int INITIAL_VALUE = 100;
        int x;
        C1() {
            x = 100;
        }
    }

    static class C2 implements Serializable {
        static final int INITIAL_VALUE = 200;
        int x;
        C2() {
            x = 200;
        }
    }

    static class C3 implements Serializable {
        static final int INITIAL_VALUE = 300;
        int x;
        C3() {
            x = 300;
        }
    }

    static class C4 extends C1 implements Serializable {
        static final int INITIAL_VALUE = 400;
        int x;
        C4() {
            x = 400;
        }
    }

    static class C5 extends C3 {
        static final int INITIAL_VALUE = 500;
        int x;
        C5() {
            x = 500;
        }
    }

    static class C6 {
        static final int INITIAL_VALUE = 600;
        int x;
        C6(int x) {
            assertEquals(INITIAL_VALUE, x);
            this.x = x;
        }
    }

    static class C7 extends C6 implements Serializable {
        static final int INITIAL_VALUE = 700;
        int x;
        C7() {
            x = 700;
            super(600);
        }
    }

    static abstract class AC {
        static final int INITIAL_VALUE = -1;
        int x;
        AC() {
            x = -1;
        }
    }

    static class C8 extends AC implements Serializable {
        static final int INITIAL_VALUE = 800;
        int x;
        C8() {
            x = 800;
        }
    }

    static Stream<Class<?>> expectNull() {
        return Stream.of(
                C1.class,   // not serializable
                C6.class,   // not serializable
                C7.class    // non-serializable super does not have no-arg constructor
        );
    }

    static Stream<Class<?>> expectConstructor() {
        return Stream.of(
                C2.class,
                C3.class,
                C4.class,    // non-Serializable super is C1
                C5.class,
                C8.class     // non-Serializable super is abstract class
        );
    }

    static Stream<Class<?>> allTestClasses() {
        return Stream.concat(expectNull(), expectConstructor());
    }

    @ParameterizedTest
    @MethodSource("expectNull")
    void testOneArgExpectingNull(Class<?> cl) {
        assertNull(factory.newConstructorForSerialization(cl));
    }

    @ParameterizedTest
    @MethodSource("expectConstructor")
    void testOneArgExpectingConstructor(Class<?> cl) throws Exception {
        assertTrue(Serializable.class.isAssignableFrom(cl));

        // the constructor should be the no-arg constructor of its first non-serializable class
        var ctor = factory.newConstructorForSerialization(cl);
        Class<?> expectedNonSerSuper = cl;
        while (Serializable.class.isAssignableFrom(expectedNonSerSuper)) {
            expectedNonSerSuper = expectedNonSerSuper.getSuperclass();
        }
        assertSame(expectedNonSerSuper, ctor.getDeclaringClass());
        assertEquals(0, ctor.getParameterCount());

        // constructor should create an instance of cl
        var obj = ctor.newInstance();
        assertSame(cl, obj.getClass());

        // the fields in the non-serializable super classes should be initialized
        // the fields in the serializable sub classes should have their default value
        boolean shouldBeInit = false;
        Class<?> current = cl;
        while (current != null) {
            if (!shouldBeInit && !Serializable.class.isAssignableFrom(current)) {
                shouldBeInit = true;
            }
            for (Field f : current.getDeclaredFields()) {
                if (!f.accessFlags().contains(AccessFlag.STATIC)) {
                    // instance fields
                    if (f.getType() == int.class) {
                        int x = f.getInt(obj);
                        if (shouldBeInit) {
                            int expected = current.getDeclaredField("INITIAL_VALUE").getInt(null);
                            assertEquals(expected, x);
                        } else {
                            assertEquals(0, x);
                        }
                    } else {
                        fail("Not an int field");
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    @ParameterizedTest
    @MethodSource("allTestClasses")
    void testTwoArgMethod(Class<?> cl) throws Exception {
        Class<?> klass = cl;
        while (klass != null) {
            for (Constructor<?> ctor : klass.getDeclaredConstructors()) {
                assertThrows(UnsupportedOperationException.class,
                        () -> factory.newConstructorForSerialization(cl, ctor));
            }
            klass = klass.getSuperclass();
        }
    }
}

