/*
 *  Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED SafeFunctionAccessTest
 */

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.foreign.VaList;
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
        try (MemorySession session = MemorySession.openConfined()) {
            segment = MemorySegment.allocateNative(POINT, session);
        }
        assertFalse(segment.session().isAlive());
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
        for (int i = 0 ; i < 6 ; i++) {
            MemorySegment[] segments = new MemorySegment[]{
                    MemorySegment.allocateNative(POINT, MemorySession.openShared()),
                    MemorySegment.allocateNative(POINT, MemorySession.openShared()),
                    MemorySegment.allocateNative(POINT, MemorySession.openShared()),
                    MemorySegment.allocateNative(POINT, MemorySession.openShared()),
                    MemorySegment.allocateNative(POINT, MemorySession.openShared()),
                    MemorySegment.allocateNative(POINT, MemorySession.openShared())
            };
            // check liveness
            segments[i].session().close();
            for (int j = 0 ; j < 6 ; j++) {
                if (i == j) {
                    assertFalse(segments[j].session().isAlive());
                } else {
                    assertTrue(segments[j].session().isAlive());
                }
            }
            try {
                handle.invokeWithArguments(segments);
                fail();
            } catch (IllegalStateException ex) {
                assertTrue(ex.getMessage().contains("Already closed"));
            }
            for (int j = 0 ; j < 6 ; j++) {
                if (i != j) {
                    segments[j].session().close(); // should succeed!
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testClosedVaList() throws Throwable {
        VaList list;
        try (MemorySession session = MemorySession.openConfined()) {
            list = VaList.make(b -> b.addVarg(C_INT, 42), session);
        }
        assertFalse(list.session().isAlive());
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func"),
                FunctionDescriptor.ofVoid(C_POINTER));

        handle.invokeExact((Addressable)list);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testClosedUpcall() throws Throwable {
        MemorySegment upcall;
        try (MemorySession session = MemorySession.openConfined()) {
            MethodHandle dummy = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "dummy", MethodType.methodType(void.class));
            upcall = Linker.nativeLinker().upcallStub(dummy, FunctionDescriptor.ofVoid(), session);
        }
        assertFalse(upcall.session().isAlive());
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func"),
                FunctionDescriptor.ofVoid(C_POINTER));

        handle.invokeExact((Addressable)upcall);
    }

    static void dummy() { }

    @Test
    public void testClosedVaListCallback() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_cb"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (MemorySession session = MemorySession.openConfined()) {
            VaList list = VaList.make(b -> b.addVarg(C_INT, 42), session);
            handle.invoke(list, sessionChecker(session));
        }
    }

    @Test
    public void testClosedStructCallback() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_cb"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (MemorySession session = MemorySession.openConfined()) {
            MemorySegment segment = MemorySegment.allocateNative(POINT, session);
            handle.invoke(segment, sessionChecker(session));
        }
    }

    @Test
    public void testClosedUpcallCallback() throws Throwable {
        MethodHandle handle = Linker.nativeLinker().downcallHandle(
                findNativeOrThrow("addr_func_cb"),
                FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));

        try (MemorySession session = MemorySession.openConfined()) {
            MethodHandle dummy = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "dummy", MethodType.methodType(void.class));
            MemorySegment upcall = Linker.nativeLinker().upcallStub(dummy, FunctionDescriptor.ofVoid(), session);
            handle.invoke(upcall, sessionChecker(session));
        }
    }

    MemorySegment sessionChecker(MemorySession session) {
        try {
            MethodHandle handle = MethodHandles.lookup().findStatic(SafeFunctionAccessTest.class, "checkSession",
                    MethodType.methodType(void.class, MemorySession.class));
            handle = handle.bindTo(session);
            return Linker.nativeLinker().upcallStub(handle, FunctionDescriptor.ofVoid(), MemorySession.openImplicit());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static void checkSession(MemorySession session) {
        try {
            session.close();
            fail("Session closed unexpectedly!");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("acquired")); //if acquired, fine
        }
    }
}
