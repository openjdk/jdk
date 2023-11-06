/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=specialized
 * @run testng/othervm
 *  -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *  --enable-native-access=ALL-UNNAMED
 *  SafeFunctionAccessTest
 */

/*
 * @test id=interpreted
 * @run testng/othervm
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   --enable-native-access=ALL-UNNAMED
 *   SafeFunctionAccessTest
 */

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemoryLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.stream.Stream;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class SafeFunctionAccessTest extends NativeTestHelper {
    static {
        System.loadLibrary("SafeAccess");
    }

    static MemoryLayout POINT = MemoryLayout.structLayout(
            C_INT, C_INT
    );

    @Test(expectedExceptions = IllegalStateException.class)
    public void testClosedStruct() throws Throwable {
        MemorySegment segment;
        try (Arena arena = Arena.ofConfined()) {
            segment = arena.allocate(POINT);
        }
        assertFalse(segment.scope().isAlive());
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("struct_func"),
                FunctionDescriptor.ofVoid(POINT));

        handle.invokeExact(segment);
    }

    @Test
    public void testClosedStructAddr_6() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_6"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        record Allocation(Arena drop, MemorySegment segment) {
            static Allocation of(MemoryLayout layout) {
                Arena arena = Arena.ofShared();
                return new Allocation(arena, arena.allocate(layout));
            }
        }
        for (int i = 0 ; i < 6 ; i++) {
            Allocation[] allocations = new Allocation[]{
                    Allocation.of(POINT),
                    Allocation.of(POINT),
                    Allocation.of(POINT),
                    Allocation.of(POINT),
                    Allocation.of(POINT),
                    Allocation.of(POINT)
            };
            // check liveness
            allocations[i].drop().close();
            for (int j = 0 ; j < 6 ; j++) {
                if (i == j) {
                    assertFalse(allocations[j].drop().scope().isAlive());
                } else {
                    assertTrue(allocations[j].drop().scope().isAlive());
                }
            }
            try {
                handle.invokeWithArguments(Stream.of(allocations).map(Allocation::segment).toArray());
                fail();
            } catch (IllegalStateException ex) {
                assertTrue(ex.getMessage().contains("Already closed"));
            }
            for (int j = 0 ; j < 6 ; j++) {
                if (i != j) {
                    allocations[j].drop().close(); // should succeed!
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testClosedUpcall() throws Throwable {
        MemorySegment upcall;
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle dummy = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "dummy", MethodType.methodType(void.class));
            upcall = Linker.nativeLinker().upcallStub(dummy, FunctionDescriptor.ofVoid(), arena);
        }
        assertFalse(upcall.scope().isAlive());
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func"),
                FunctionDescriptor.ofVoid(C_POINTER));

        handle.invokeExact(upcall);
    }

    static void dummy() { }

    @Test
    public void testClosedStructCallback() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_cb"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(POINT);
            handle.invokeExact(segment, sessionChecker(arena));
        }
    }

    @Test
    public void testClosedUpcallCallback() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_cb"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (Arena arena = Arena.ofConfined()) {
            MethodHandle dummy = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "dummy", MethodType.methodType(void.class));
            MemorySegment upcall = Linker.nativeLinker().upcallStub(dummy, FunctionDescriptor.ofVoid(), arena);
            handle.invokeExact(upcall, sessionChecker(arena));
        }
    }

    MemorySegment sessionChecker(Arena arena) {
        try {
            MethodHandle handle = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "checkSession",
                    MethodType.methodType(void.class, Arena.class));
            handle = handle.bindTo(arena);
            return Linker.nativeLinker().upcallStub(handle, FunctionDescriptor.ofVoid(), Arena.ofAuto());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static void checkSession(Arena arena) {
        try {
            arena.close();
            fail("Session closed unexpectedly!");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("acquired")); //if acquired, fine
        }
    }
}
