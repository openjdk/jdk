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

/* @test
 * @summary Basic tests for StableFieldUpdater implementations
 * @modules java.base/jdk.internal.lang.stable
 * @modules java.base/jdk.internal.invoke
 * @run junit StableFieldUpdaterTest
 */

import jdk.internal.invoke.MhUtil;
import jdk.internal.lang.stable.StableFieldUpdater;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.*;

final class StableFieldUpdaterTest {

    private static final String STRING = "Abc";
    private static final int SIZE = 8;


    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final VarHandle ACCESSOR;
    private static final MethodHandle UNDERLYING;
    private static final MethodHandle DOUBLE_UNDERLYING;

    static {
        try {
            ACCESSOR = LOOKUP.findVarHandle(Foo.class, "hash", int.class);
            UNDERLYING = LOOKUP.findStatic(Foo.class, "hashCodeFor", MethodType.methodType(int.class, Foo.class));
            DOUBLE_UNDERLYING = LOOKUP.findStatic(StableFieldUpdaterTest.class, "doubleFrom", MethodType.methodType(double.class, Foo.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    @Test
    void invariants() {
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.atMostOnce(null, UNDERLYING));
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.atMostOnce(ACCESSOR, null));
        var xi = assertThrows(IllegalArgumentException.class, () -> StableFieldUpdater.atMostOnce(ACCESSOR, DOUBLE_UNDERLYING));
        assertEquals("Return type mismatch: accessor: VarHandle[varType=int, coord=[class StableFieldUpdaterTest$Foo]], underlying: MethodHandle(Foo)double", xi.getMessage());

        assertThrows(NullPointerException.class, () -> StableFieldUpdater.replaceIntZero((MethodHandle) null, 1));
        assertThrows(NullPointerException.class, () -> StableFieldUpdater.replaceLongZero((MethodHandle) null, 1L));
    }

    @Test
    void multiCoordinateIntArray() throws Throwable {
        var accessor = MethodHandles.arrayElementVarHandle(int[].class);
        var underlying = LOOKUP.findStatic(StableFieldUpdaterTest.class, "multiCoordinateMethod",
                MethodType.methodType(int.class, int[].class, int.class));
        var atMostOnce = StableFieldUpdater.atMostOnce(accessor, underlying);
        int index = 1;
        int[] array = new int[SIZE];
        int val = (int) atMostOnce.invokeExact(array, index);
        assertEquals(index, val);
    }

    @Test
    void multiCoordinateLongArray() throws Throwable {
        var accessor = MethodHandles.arrayElementVarHandle(long[].class);
        var underlying = LOOKUP.findStatic(StableFieldUpdaterTest.class, "multiCoordinateMethod",
                MethodType.methodType(long.class, long[].class, int.class));
        var atMostOnce = StableFieldUpdater.atMostOnce(accessor, underlying);
        int index = 1;
        long[] array = new long[SIZE];
        long val = (long) atMostOnce.invokeExact(array, index);
        assertEquals(index, val);
    }

    @Test
    void multiCoordinateSegment() throws Throwable {
        var layout = MemoryLayout.sequenceLayout(SIZE, JAVA_INT);
        var accessor = layout.varHandle(MemoryLayout.PathElement.sequenceElement());
        accessor = MethodHandles.insertCoordinates(accessor, 1, 0L); // zero offset
        var underlying = LOOKUP.findStatic(StableFieldUpdaterTest.class, "multiCoordinateMethod",
                MethodType.methodType(int.class, MemorySegment.class, long.class));
        var atMostOnce = StableFieldUpdater.atMostOnce(accessor, underlying);
        long index = 1L;
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(layout);
            int val = (int) atMostOnce.invokeExact(segment, index);
            assertEquals(index, val);
        }
    }

    // Used reflectively: int[], int
    static int multiCoordinateMethod(int[] array, int index) {
        return index;
    }

    // Used reflectively:
    static long multiCoordinateMethod(long[] array, int index) {
        return index;
    }

    // Used reflectively
    static int multiCoordinateMethod(MemorySegment segment, long index) {
        return (int) index;
    }

    @Test
    void returnedHandleTypes() {
        MethodHandle handle = StableFieldUpdater.atMostOnce(ACCESSOR, UNDERLYING);
        assertEquals(int.class, handle.type().returnType());
        assertEquals(Foo.class, handle.type().parameterType(0));
        assertEquals(1, handle.type().parameterCount());
    }

    @Test
    void foo() throws Throwable {
        MethodHandle handle = StableFieldUpdater.atMostOnce(ACCESSOR, UNDERLYING);
        Foo foo = new Foo(STRING);
        int hash = (int) handle.invokeExact(foo);
        assertEquals(STRING.hashCode(), hash);
    }

    @ParameterizedTest
    @MethodSource("fooConstructors")
    void basic(Function<String, HasHashField> ctor) {
        final HasHashField foo = ctor.apply(STRING);
        assertEquals(0L, foo.hash());
        int actual = foo.hashCode();
        assertEquals(STRING.hashCode(), actual);
        assertEquals(actual, foo.hash());
    }

    @Test
    void recordFoo() throws ReflectiveOperationException {
        record RecordFoo(String string, int hash) {

            private int hashCodeFor() {
                return string.hashCode();
            }

        }
        var accessor = LOOKUP.findVarHandle(RecordFoo.class, "hash", int.class);
        var underlying = LOOKUP.findVirtual(RecordFoo.class, "hashCodeFor", MethodType.methodType(int.class));

        accessor.isAccessModeSupported(VarHandle.AccessMode.SET);

        // The field is `final`
        var x = assertThrows(IllegalArgumentException.class, () -> StableFieldUpdater.atMostOnce(accessor, underlying));
        assertEquals("The accessor is read only: VarHandle[varType=int, coord=[class StableFieldUpdaterTest$1RecordFoo]]", x.getMessage());
    }

    @Test
    void wrongReceiver() {
        var handle = StableFieldUpdater.atMostOnce(ACCESSOR, UNDERLYING);
        var wrongType = new InheritingFoo(STRING);

        assertThrows(WrongMethodTypeException.class, () -> {
           int hash =  (int) handle.invokeExact(wrongType);
        });

        assertThrows(ClassCastException.class, () -> {
           int hash =  (int) handle.invoke(wrongType);
        });
    }

    @Test
    void lazyAtMostOnce() throws Throwable {
        var lookup = MethodHandles.lookup();
        CallSite callSite = StableFieldUpdater.lazyAtMostOnce(lookup, "", ACCESSOR, UNDERLYING);

        MethodHandle hasher = (MethodHandle) callSite.getTarget().invoke();

        var foo = new Foo(STRING);
        int hash = (int) hasher.invoke(foo);
        assertEquals(STRING.hashCode(), hash);
    }

    @Test
    void replaceIntZeroHandle() throws Throwable {
        int zeroReplacement = -1;
        MethodHandle underlying = MethodHandles.identity(int.class);
        var mod = StableFieldUpdater.replaceIntZero(underlying, zeroReplacement);
        assertEquals(1, (int) mod.invoke(1));
        assertEquals(zeroReplacement, (int) mod.invoke(0));
    }

    @Test
    void replaceLongZeroHandle() throws Throwable {
        long zeroReplacement = -1;
        MethodHandle underlying = MethodHandles.identity(long.class);
        var mod = StableFieldUpdater.replaceLongZero(underlying, zeroReplacement);
        assertEquals(1L, (long) mod.invoke(1L));
        assertEquals(zeroReplacement, (long) mod.invoke(0L));
    }

    static final class Foo implements HasHashField {

        private static final MethodHandle HASH_UPDATER;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                VarHandle accessor = lookup.findVarHandle(Foo.class, "hash", int.class);
                MethodHandle underlying = lookup.findStatic(Foo.class, "hashCodeFor", MethodType.methodType(int.class, Foo.class));
                HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        private final String string;

        int hash;
        long dummy;

        public Foo(String string) {
            this.string = string;
        }

        @Override
        public int hashCode() {
            try {
                return (int) HASH_UPDATER.invokeExact(this);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long hash() {
            return hash;
        }

        private static int hashCodeFor(Foo foo) {
            return foo.string.hashCode();
        }
    }

    static final class LongFoo implements HasHashField {

        private static final MethodHandle HASH_UPDATER;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                VarHandle accessor = lookup.findVarHandle(LongFoo.class, "hash", long.class);
                MethodHandle underlying = lookup.findStatic(LongFoo.class, "hashCodeFor", MethodType.methodType(long.class, LongFoo.class));
                HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        private final String string;

        long hash;
        long dummy;

        public LongFoo(String string) {
            this.string = string;
        }

        @Override
        public int hashCode() {
            try {
                return (int) (long) HASH_UPDATER.invokeExact(this);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long hash() {
            return hash;
        }

        private static long hashCodeFor(LongFoo foo) {
            return foo.string.hashCode();
        }

    }



    static final class InheritingFoo extends AbstractFoo implements HasHashField {

        private static final MethodHandle HASH_UPDATER;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                VarHandle accessor = lookup.findVarHandle(InheritingFoo.class, "hash", int.class);
                MethodHandle underlying = lookup.findStatic(InheritingFoo.class, "hashCodeFor", MethodType.methodType(int.class, InheritingFoo.class));
                HASH_UPDATER = StableFieldUpdater.atMostOnce(accessor, underlying);
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }

        public InheritingFoo(String string) {
            super(string);
        }

        @Override
        public int hashCode() {
            try {
                return (int) HASH_UPDATER.invokeExact(this);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        private static int hashCodeFor(InheritingFoo foo) {
            return foo.string.hashCode();
        }
    }

    static abstract class AbstractFoo implements HasHashField {
        final String string;
        int hash;

        public AbstractFoo(String string) {
            this.string = string;
        }

        @Override
        public long hash() {
            return hash;
        }
    }

    interface HasHashField {
        long hash();
    }

    // Illegal underlying function for int and long
    private static double doubleFrom(Foo foo) {
        return 1;
    }

    // Apparently, `hashCode()` is invoked if we create a stream of just `HasHashField`
    // instances so we provide the associated constructors instead.
    static Stream<Function<String, HasHashField>> fooConstructors() {
        return Stream.of(
                Foo::new,
                LongFoo::new,
                InheritingFoo::new
        );
    }

}
