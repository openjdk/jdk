/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test TestMethodHandleMapper
 * @modules java.base/jdk.internal.foreign
 * @run junit TestMethodHandleMapper
 */

import jdk.internal.foreign.MethodHandleMapper;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static org.junit.jupiter.api.Assertions.*;

final class TestMethodHandleMapper {

    private static final String ERRNO_NAME = "errno";

    private static final VarHandle ERRNO_HANDLE = Linker.Option.captureStateLayout()
                    .varHandle(MemoryLayout.PathElement.groupElement(ERRNO_NAME));

    private static final MethodHandle INT_DUMMY_HANDLE;
    private static final MethodHandle THROWING_INT_DUMMY_HANDLE;
    private static final MethodHandle LONG_DUMMY_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            INT_DUMMY_HANDLE = lookup
                    .findStatic(TestMethodHandleMapper.class, "dummy",
                            MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
            THROWING_INT_DUMMY_HANDLE = lookup
                    .findStatic(TestMethodHandleMapper.class, "throwingDummy",
                            MethodType.methodType(int.class, MemorySegment.class, int.class, int.class));
            LONG_DUMMY_HANDLE = lookup
                    .findStatic(TestMethodHandleMapper.class, "dummy",
                            MethodType.methodType(long.class, MemorySegment.class, long.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    @FunctionalInterface
    public interface RawInt {
        int invoke(MemorySegment segment, int result, int errno);
    }

    @FunctionalInterface
    public interface ThrowingRawInt {
        int invoke(MemorySegment segment, int result, int errno) throws IllegalArgumentException;
    }

    @FunctionalInterface
    public interface RawLong {
        long invoke(MemorySegment segment, long result, int errno);
    }

    @Test
    void rawInt() {
        RawInt rawInt = MethodHandleMapper.map(MethodHandles.lookup(), RawInt.class, INT_DUMMY_HANDLE);
        try (var arena = Arena.ofConfined()) {
            int r = rawInt.invoke(arena.allocate(Linker.Option.captureStateLayout()), 1, 0);
            assertEquals(1, r);
        }
    }

    @Test
    void throwingRawInt() {
        ThrowingRawInt throwingRawInt = MethodHandleMapper.map(MethodHandles.lookup(), ThrowingRawInt.class, THROWING_INT_DUMMY_HANDLE);
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(Linker.Option.captureStateLayout());
            int r = throwingRawInt.invoke(segment, 1, 0);
            assertEquals(1, r);
            try {
                throwingRawInt.invoke(segment, -1, 1);
            } catch (IllegalArgumentException illegalArgumentException) {
                // All good
            } catch (Throwable throwable) {
                fail(throwable);
            }
            try {
                throwingRawInt.invoke(segment, -2, 1);
            } catch (IllegalArgumentException illegalArgumentException) {
                fail(illegalArgumentException);
            } catch (Throwable throwable) {
                assertInstanceOf(ArithmeticException.class, throwable);
                // All good
            }
        }

    }

    @Test
    void rawLong() {
        RawLong rawLong = MethodHandleMapper.map(MethodHandles.lookup(), RawLong.class, LONG_DUMMY_HANDLE);
        try (var arena = Arena.ofConfined()) {
            long r = rawLong.invoke(arena.allocate(Linker.Option.captureStateLayout()), 1, 0);
            assertEquals(1, r);
        }
    }

    @Test
    void initialInvariants() {
        assertThrows(NullPointerException.class, () -> MethodHandleMapper.map(null, RawInt.class, INT_DUMMY_HANDLE));
        assertThrows(NullPointerException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), null, INT_DUMMY_HANDLE));
        assertThrows(NullPointerException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), RawInt.class, null));
    }

    @Test
    void notFunctionalInterface() {
        interface NotFunctional {
            int a();
        }
        var e = assertThrows(IllegalArgumentException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), NotFunctional.class, INT_DUMMY_HANDLE));
        var message = e.getMessage();
        assertTrue(message.contains("is not a @FunctionalInterface"), message);
    }

    @Test
    void notInterface() {
        final class NotInterface {
            int a() {
                return 0;
            }
        }
        var e = assertThrows(IllegalArgumentException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), NotInterface.class, INT_DUMMY_HANDLE));
        var message = e.getMessage();
        assertTrue(message.contains("is not an interface"), message);
    }

    @Test
    void wrongParameter() {
        @FunctionalInterface
        interface WrongParameter {
            int a(int a, byte b);
        }
        var e = assertThrows(IllegalArgumentException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), WrongParameter.class, INT_DUMMY_HANDLE));
        var message = e.getMessage();
        assertTrue(message.contains("parameter list"), message);
    }

    @Test
    void wrongReturnType() {
        @FunctionalInterface
        interface WrongParameter {
            byte a(int a, int b);
        }
        var e = assertThrows(IllegalArgumentException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), WrongParameter.class, INT_DUMMY_HANDLE));
        var message = e.getMessage();
        assertTrue(message.contains("return type"), message);
    }

    @Test
    void hasTypeParameter() {
        @FunctionalInterface
        interface HasTypeParameter<T> {
            int a(int a, byte b);
        }
        var e = assertThrows(IllegalArgumentException.class, () -> MethodHandleMapper.map(MethodHandles.lookup(), HasTypeParameter.class, INT_DUMMY_HANDLE));
        var message = e.getMessage();
        assertTrue(message.contains("has type parameters"), message);
    }

    // Dummy method that is just returning the provided parameters
    private static int dummy(MemorySegment segment, int result, int errno) {
        ERRNO_HANDLE.set(segment, 0, errno);
        return result;
    }

    // Dummy method that is just returning the provided parameters
    private static int throwingDummy(MemorySegment segment, int result, int errno) throws IllegalArgumentException {
        ERRNO_HANDLE.set(segment, 0, errno);
        if (result == -1) {
            throw new IllegalArgumentException();
        }
        if (result == -2) {
            throw new ArithmeticException();
        }
        return result;
    }

    // Dummy method that is just returning the provided parameters
    private static long dummy(MemorySegment segment, long result, int errno) {
        ERRNO_HANDLE.set(segment, 0, errno);
        return result;
    }

    private static long wrongType(long result, int errno) {
        return 0;
    }

    private static short wrongType(MemorySegment segment, long result, int errno) {
        return 0;
    }

}
