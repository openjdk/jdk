/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static org.junit.jupiter.api.Assertions.*;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.AccessMode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/*
 * @test
 * @summary Test atomic access modes on var handles for flattened values
 * @enablePreview
 * @modules java.base/jdk.internal.value java.base/jdk.internal.vm.annotation
 * @run junit/othervm -XX:-UseArrayFlattening -XX:-UseNullableValueFlattening FlatVarHandleTest
 * @run junit/othervm -XX:+UseArrayFlattening -XX:+UseNullableValueFlattening FlatVarHandleTest
 */
public class FlatVarHandleTest {

    interface Pointable { }

    @FunctionalInterface
    interface TriFunction<A, B, C, R> {

        R apply(A a, B b, C c);

        default <V> TriFunction<A, B, C, V> andThen(
                Function<? super R, ? extends V> after) {
            Objects.requireNonNull(after);
            return (A a, B b, C c) -> after.apply(apply(a, b, c));
        }
    }

    @LooselyConsistentValue
    static value class WeakPoint implements Pointable {
        short x,y;
        WeakPoint(int i, int j) { x = (short)i; y = (short)j; }

        static WeakPoint[] makePoints(int len, BiFunction<Class<?>, Integer, Object[]> arrayFactory) {
            WeakPoint[] array = (WeakPoint[])arrayFactory.apply(WeakPoint.class, len);
            for (int i = 0; i < len; ++i) {
                array[i] = new WeakPoint(i, i);
            }
            return array;
        }

        static WeakPoint[] makePoints(int len, Object initval, TriFunction<Class<?>, Integer, Object, Object[]> arrayFactory) {
            WeakPoint[] array = (WeakPoint[])arrayFactory.apply(WeakPoint.class, len, initval);
            for (int i = 0; i < len; ++i) {
                array[i] = new WeakPoint(i, i);
            }
            return array;
        }
    }

    static class WeakPointHolder {
        WeakPoint p_i = new WeakPoint(0, 0);
        static WeakPoint p_s = new WeakPoint(0, 0);
        @NullRestricted
        WeakPoint p_i_nr;
        @NullRestricted
        static WeakPoint p_s_nr = new WeakPoint(0, 0);

        WeakPointHolder() {
            p_i_nr = new WeakPoint(0, 0);
            super();
        }
    }

    static value class StrongPoint implements Pointable {
        short x,y;
        StrongPoint(int i, int j) { x = (short)i; y = (short)j; }

        static StrongPoint[] makePoints(int len, BiFunction<Class<?>, Integer, Object[]> arrayFactory) {
            StrongPoint[] array = (StrongPoint[])arrayFactory.apply(StrongPoint.class, len);
            for (int i = 0; i < len; ++i) {
                array[i] = new StrongPoint(i, i);
            }
            return array;
        }

        static StrongPoint[] makePoints(int len, Object initval, TriFunction<Class<?>, Integer, Object, Object[]> arrayFactory) {
            StrongPoint[] array = (StrongPoint[])arrayFactory.apply(StrongPoint.class, len, initval);
            for (int i = 0; i < len; ++i) {
                array[i] = new StrongPoint(i, i);
            }
            return array;
        }
    }

    static class StrongPointHolder {
        StrongPoint p_i = new StrongPoint(0, 0);
        static StrongPoint p_s = new StrongPoint(0, 0);
    }

    private static List<Arguments> fieldAccessProvider() {
        try {
            List<Field> fields = List.of(
                    WeakPointHolder.class.getDeclaredField("p_s"),
                    WeakPointHolder.class.getDeclaredField("p_i"),
                    WeakPointHolder.class.getDeclaredField("p_s_nr"),
                    WeakPointHolder.class.getDeclaredField("p_i_nr"),
                    StrongPointHolder.class.getDeclaredField("p_s"),
                    StrongPointHolder.class.getDeclaredField("p_i"));
            List<Arguments> arguments = new ArrayList<>();
            for (AccessMode accessMode : AccessMode.values()) {
                for (Field field : fields) {
                    boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
                    boolean isWeak = field.getDeclaringClass().equals(WeakPointHolder.class);
                    Object holder = null;
                    if (!isStatic) {
                        holder = isWeak ? new WeakPointHolder() : new StrongPointHolder();
                    }
                    BiFunction<Integer, Integer, Object> factory = isWeak ?
                            (i1, i2) -> new WeakPoint(i1, i2) :
                            (i1, i2) -> new StrongPoint(i1, i2);
                    boolean allowsNonPlainAccess = (field.getModifiers() & Modifier.VOLATILE) != 0 ||
                            !ValueClass.isNullRestrictedField(field) ||
                            !isWeak;
                    arguments.add(Arguments.of(accessMode, holder, factory, field, allowsNonPlainAccess));
                }
            }
            return arguments;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /*
     * Verify that atomic access modes are not supported on flat fields.
     */
    @ParameterizedTest
    @MethodSource("fieldAccessProvider")
    public void testFieldAccess(AccessMode accessMode, Object holder, BiFunction<Integer, Integer, Object> factory,
                                Field field, boolean allowsNonPlainAccess) throws Throwable {
        VarHandle varHandle = MethodHandles.lookup().unreflectVarHandle(field);
        if (varHandle.isAccessModeSupported(accessMode)) {
            assertTrue(isPlain(accessMode) || (allowsNonPlainAccess && !isBitwise(accessMode) && !isNumeric(accessMode)));
            MethodHandle methodHandle = varHandle.toMethodHandle(accessMode);
            List<Object> arguments = new ArrayList<>();
            if (holder != null) {
                arguments.add(holder); // receiver
            }
            for (int i = arguments.size(); i < methodHandle.type().parameterCount(); i++) {
                arguments.add(factory.apply(i, i)); // add extra setter param
            }
            methodHandle.invokeWithArguments(arguments.toArray());
        } else {
            assertTrue(!allowsNonPlainAccess || isBitwise(accessMode) || isNumeric(accessMode));
        }
    }

    private static List<Arguments> arrayAccessProvider() {
        List<Object[]> arrayObjects = List.of(
                WeakPoint.makePoints(10, ValueClass::newNullableAtomicArray),
                WeakPoint.makePoints(10, new WeakPoint(0, 0), ValueClass::newNullRestrictedNonAtomicArray),
                WeakPoint.makePoints(10, new WeakPoint(0, 0), ValueClass::newNullRestrictedAtomicArray),
                new WeakPoint[10],
                StrongPoint.makePoints(10, ValueClass::newNullableAtomicArray),
                StrongPoint.makePoints(10, new StrongPoint(0, 0), ValueClass::newNullRestrictedNonAtomicArray),
                StrongPoint.makePoints(10, new StrongPoint(0, 0), ValueClass::newNullRestrictedAtomicArray),
                new StrongPoint[10]);

        List<Arguments> arguments = new ArrayList<>();
        for (AccessMode accessMode : AccessMode.values()) {
            if (accessMode.ordinal() != 2) continue;
            for (Object[] arrayObject : arrayObjects) {
                boolean isWeak = arrayObject.getClass().getComponentType().equals(WeakPoint.class);
                List<Class<?>> arrayTypes = List.of(
                        isWeak ? WeakPoint[].class : StrongPoint[].class, Pointable[].class, Object[].class);
                for (Class<?> arrayType : arrayTypes) {
                    BiFunction<Integer, Integer, Object> factory = isWeak ?
                            (i1, i2) -> new WeakPoint(i1, i2) :
                            (i1, i2) -> new StrongPoint((short)(int)i1, (short)(int)i2);
                    boolean allowsNonPlainAccess = !ValueClass.isNullRestrictedArray(arrayObject) ||
                            ValueClass.isAtomicArray(arrayObject) ||
                            !isWeak;
                    arguments.add(Arguments.of(accessMode, arrayObject, factory, arrayType, allowsNonPlainAccess));
                }
            }
        }
        return arguments;
    }

    /*
     * Verify that atomic access modes are not supported on flat array instances.
     */
    @ParameterizedTest
    @MethodSource("arrayAccessProvider")
    public void testArrayAccess(AccessMode accessMode, Object[] arrayObject, BiFunction<Integer, Integer, Object> factory,
                                Class<?> arrayType, boolean allowsNonPlainAccess) throws Throwable {
        VarHandle varHandle = MethodHandles.arrayElementVarHandle(arrayType);
        if (varHandle.isAccessModeSupported(accessMode)) {
            assertTrue(!isBitwise(accessMode) && !isNumeric(accessMode));
            MethodHandle methodHandle = varHandle.toMethodHandle(accessMode);
            List<Object> arguments = new ArrayList<>();
            arguments.add(arrayObject); // array receiver
            arguments.add(0); // index
            for (int i = 2; i < methodHandle.type().parameterCount(); i++) {
                arguments.add(factory.apply(i, i)); // add extra setter param
            }
            try {
                methodHandle.invokeWithArguments(arguments.toArray());
            } catch (IllegalArgumentException ex) {
                assertFalse(allowsNonPlainAccess);
            }
        } else {
            assertTrue(isBitwise(accessMode) || isNumeric(accessMode));
        }
    }

    boolean isBitwise(AccessMode accessMode) {
        return switch (accessMode) {
            case GET_AND_BITWISE_AND, GET_AND_BITWISE_AND_ACQUIRE,
                 GET_AND_BITWISE_AND_RELEASE, GET_AND_BITWISE_OR,
                 GET_AND_BITWISE_OR_ACQUIRE, GET_AND_BITWISE_OR_RELEASE,
                 GET_AND_BITWISE_XOR, GET_AND_BITWISE_XOR_ACQUIRE,
                 GET_AND_BITWISE_XOR_RELEASE -> true;
            default -> false;
        };
    }

    boolean isNumeric(AccessMode accessMode) {
        return switch (accessMode) {
            case GET_AND_ADD, GET_AND_ADD_ACQUIRE, GET_AND_ADD_RELEASE -> true;
            default -> false;
        };
    }

    boolean isPlain(AccessMode accessMode) {
        return switch (accessMode) {
            case GET, SET -> true;
            default -> false;
        };
    }
}
