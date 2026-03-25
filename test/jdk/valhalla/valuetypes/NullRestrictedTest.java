/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test reflection and method handle on accessing a field of a null-restricted value class
 *          that may be flattened or non-flattened
 * @enablePreview
 * @run junit/othervm NullRestrictedTest
 * @run junit/othervm -XX:-UseFieldFlattening NullRestrictedTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.stream.Stream;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class NullRestrictedTest {
    static value class EmptyValue {
        public boolean isEmpty() {
            return true;
        }
    }

    static value class Value {
        Object o;
        @NullRestricted
        EmptyValue empty;
        Value() {
            this.o = null;
            this.empty = new EmptyValue();
        }
        Value(EmptyValue empty) {
            this.o = null;
            this.empty = empty;
        }
    }

    static class Mutable {
        EmptyValue o;
        @NullRestricted
        EmptyValue empty;
        @NullRestricted
        volatile EmptyValue vempty;

        Mutable() {
            empty = new EmptyValue();
            vempty = new EmptyValue();
            super();
        }
    }

    @Test
    public void emptyValueClass() {
        EmptyValue e = new EmptyValue();
        Field[] fields = e.getClass().getDeclaredFields();
        assertTrue(fields.length == 0);
    }

    @Test
    public void testNonNullFieldAssignment() {
        var npe = assertThrows(NullPointerException.class, () -> new Value(null));
        System.err.println(npe);    // log the exception message
    }

    static Stream<Arguments> getterCases() {
        Value v = new Value();
        EmptyValue emptyValue = new EmptyValue();
        Mutable m = new Mutable();

        return Stream.of(
                Arguments.of(Value.class, "o", Object.class, v, null),
                Arguments.of(Value.class, "empty", EmptyValue.class, v, emptyValue),
                Arguments.of(Mutable.class, "o", EmptyValue.class, m, null),
                Arguments.of(Mutable.class, "empty", EmptyValue.class, m, emptyValue),
                Arguments.of(Mutable.class, "vempty", EmptyValue.class, m, emptyValue)
        );
    };

    @ParameterizedTest
    @MethodSource("getterCases")
    public void testGetter(Class<?> type, String name, Class<?> ftype, Object obj, Object expected) throws Throwable {
        var f = type.getDeclaredField(name);
        assertTrue(f.getType() == ftype);
        var o1 = f.get(obj);
        assertTrue(expected == o1);

        var getter = MethodHandles.lookup().findGetter(type, name, ftype);
        var o2 = getter.invoke(obj);
        assertTrue(expected == o2);

        var vh = MethodHandles.lookup().findVarHandle(type, name, ftype);
        var o3 = vh.get(obj);
        assertTrue(expected == o3);
    }

    static Stream<Arguments> setterCases() {
        EmptyValue emptyValue = new EmptyValue();
        Mutable m = new Mutable();
        return Stream.of(
                Arguments.of(Mutable.class, "o", EmptyValue.class, m, null),
                Arguments.of(Mutable.class, "o", EmptyValue.class, m, emptyValue),
                Arguments.of(Mutable.class, "empty", EmptyValue.class, m, emptyValue),
                Arguments.of(Mutable.class, "vempty", EmptyValue.class, m, emptyValue)
        );
    };

    @ParameterizedTest
    @MethodSource("setterCases")
    public void testSetter(Class<?> type, String name, Class<?> ftype, Object obj, Object expected) throws Throwable {
        var f = type.getDeclaredField(name);
        assertTrue(f.getType() == ftype);
        f.set(obj, expected);
        assertTrue(f.get(obj) == expected);

        var setter = MethodHandles.lookup().findSetter(type, name, ftype);
        setter.invoke(obj, expected);
        assertTrue(f.get(obj) == expected);
    }

    @Test
    public void noWriteAccess() throws ReflectiveOperationException {
        Value v = new Value();
        Field f = v.getClass().getDeclaredField("o");
        assertThrows(IllegalAccessException.class, () -> f.set(v, null));
    }

    static Stream<Arguments> nullRestrictedFields() {
        Mutable m = new Mutable();
        return Stream.of(
                Arguments.of(Mutable.class, "o", EmptyValue.class, m, false),
                Arguments.of(Mutable.class, "empty", EmptyValue.class, m, true),
                Arguments.of(Mutable.class, "vempty", EmptyValue.class, m, true)
        );
    };

    @ParameterizedTest
    @MethodSource("nullRestrictedFields")
    public void testNullRestrictedField(Class<?> type, String name, Class<?> ftype, Object obj, boolean nullRestricted) throws Throwable {
        var f = type.getDeclaredField(name);
        assertTrue(f.getType() == ftype);
        if (nullRestricted) {
            assertThrows(NullPointerException.class, () -> f.set(obj, null));
        } else {
            f.set(obj, null);
            assertTrue(f.get(obj) == null);
        }

        var mh = MethodHandles.lookup().findSetter(type, name, ftype);
        if (nullRestricted) {
            assertThrows(NullPointerException.class, () -> mh.invoke(obj, null));
        } else {
            mh.invoke(obj, null);
            assertTrue(f.get(obj) == null);
        }

        var vh = MethodHandles.lookup().findVarHandle(type, name, ftype);
        if (nullRestricted) {
            assertThrows(NullPointerException.class, () -> vh.set(obj, null));
        } else {
            vh.set(obj, null);
            assertTrue(f.get(obj) == null);
        }
    }
}
