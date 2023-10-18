/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.lang.foreign.*;
import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/*
 * @test id=specialized
 * @run testng/othervm
 *  -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *  --enable-native-access=ALL-UNNAMED
 *  LibraryLookupTest
 */

/*
 * @test id=interpreted
 * @run testng/othervm
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   --enable-native-access=ALL-UNNAMED
 *   LibraryLookupTest
 */
public class LibraryLookupTest {

    static final Path JAVA_LIBRARY_PATH = Path.of(System.getProperty("java.library.path"));
    static final MethodHandle INC = Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid());
    static final Path LIB_PATH = JAVA_LIBRARY_PATH.resolve(System.mapLibraryName("LibraryLookup"));

    @Test
    void testLoadLibraryConfined() {
        try (Arena arena0 = Arena.ofConfined()) {
            callFunc(loadLibrary(arena0));
            try (Arena arena1 = Arena.ofConfined()) {
                callFunc(loadLibrary(arena1));
                try (Arena arena2 = Arena.ofConfined()) {
                    callFunc(loadLibrary(arena2));
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void testLoadLibraryConfinedClosed() {
        MemorySegment addr;
        try (Arena arena = Arena.ofConfined()) {
            addr = loadLibrary(arena);
        }
        callFunc(addr);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testLoadLibraryBadName() {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup.libraryLookup(LIB_PATH.toString() + "\u0000", arena);
        }
    }

    @Test
    void testLoadLibraryBadLookupName() {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup lookup = SymbolLookup.libraryLookup(LIB_PATH, arena);
            assertTrue(lookup.find("inc\u0000foobar").isEmpty());
        }
    }

    private static MemorySegment loadLibrary(Arena session) {
        SymbolLookup lib = SymbolLookup.libraryLookup(LIB_PATH, session);
        MemorySegment addr = lib.find("inc").get();
        assertEquals(addr.scope(), session.scope());
        return addr;
    }

    private static void callFunc(MemorySegment addr) {
        try {
            INC.invokeExact(addr);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final int ITERATIONS = 100;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 20;
    static final int NUM_ACCESSORS = Math.min(10, Runtime.getRuntime().availableProcessors());

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadLibraryLookupName() {
        SymbolLookup.libraryLookup("nonExistent", Arena.global());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadLibraryLookupPath() {
        SymbolLookup.libraryLookup(Path.of("nonExistent"), Arena.global());
    }

    @Test
    void testLoadLibraryShared() throws Throwable {
        ExecutorService accessExecutor = Executors.newCachedThreadPool();
        for (int i = 0; i < NUM_ACCESSORS ; i++) {
            accessExecutor.execute(new LibraryLoadAndAccess());
        }
        accessExecutor.shutdown();
        assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
    }

    static class LibraryLoadAndAccess implements Runnable {
        @Override
        public void run() {
            for (int i = 0 ; i < ITERATIONS ; i++) {
                try (Arena arena = Arena.ofConfined()) {
                    callFunc(loadLibrary(arena));
                }
            }
        }
    }

    @Test
    void testLoadLibrarySharedClosed() throws Throwable {
        Arena arena = Arena.ofShared();
        MemorySegment addr = loadLibrary(arena);
        ExecutorService accessExecutor = Executors.newCachedThreadPool();
        for (int i = 0; i < NUM_ACCESSORS ; i++) {
            accessExecutor.execute(new LibraryAccess(addr));
        }
        while (true) {
            try {
                arena.close();
                break;
            } catch (IllegalStateException ex) {
                // wait for addressable parameter to be released
                Thread.onSpinWait();
            }
        }
        accessExecutor.shutdown();
        assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
    }

    static class LibraryAccess implements Runnable {

        final MemorySegment addr;

        LibraryAccess(MemorySegment addr) {
            this.addr = addr;
        }

        @Override
        public void run() {
            for (int i = 0 ; i < ITERATIONS ; i++) {
                try {
                    callFunc(addr);
                } catch (IllegalStateException ex) {
                    // library closed
                    break;
                }
            }
        }
    }
}
