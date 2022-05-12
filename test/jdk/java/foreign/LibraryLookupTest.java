/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED LibraryLookupTest
 */
public class LibraryLookupTest {

    static final Path JAVA_LIBRARY_PATH = Path.of(System.getProperty("java.library.path"));
    static final MethodHandle INC = Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid());
    static final Path LIB_PATH = JAVA_LIBRARY_PATH.resolve(System.mapLibraryName("LibraryLookup"));

    @Test
    void testLoadLibraryConfined() {
        try (MemorySession session0 = MemorySession.openConfined()) {
            callFunc(loadLibrary(session0));
            try (MemorySession session1 = MemorySession.openConfined()) {
                callFunc(loadLibrary(session1));
                try (MemorySession session2 = MemorySession.openConfined()) {
                    callFunc(loadLibrary(session2));
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    void testLoadLibraryConfinedClosed() {
        Addressable addr;
        try (MemorySession session = MemorySession.openConfined()) {
            addr = loadLibrary(session);
        }
        callFunc(addr);
    }

    private static Addressable loadLibrary(MemorySession session) {
        SymbolLookup lib = SymbolLookup.libraryLookup(LIB_PATH, session);
        MemorySegment addr = lib.lookup("inc").get();
        assertEquals(addr.session(), session);
        return addr;
    }

    private static void callFunc(Addressable addr) {
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
        SymbolLookup.libraryLookup("nonExistent", MemorySession.global());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    void testBadLibraryLookupPath() {
        SymbolLookup.libraryLookup(Path.of("nonExistent"), MemorySession.global());
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
                try (MemorySession session = MemorySession.openConfined()) {
                    callFunc(loadLibrary(session));
                }
            }
        }
    }

    @Test
    void testLoadLibrarySharedClosed() throws Throwable {
        MemorySession session = MemorySession.openShared();
        Addressable addr = loadLibrary(session);
        ExecutorService accessExecutor = Executors.newCachedThreadPool();
        for (int i = 0; i < NUM_ACCESSORS ; i++) {
            accessExecutor.execute(new LibraryAccess(addr));
        }
        while (true) {
            try {
                session.close();
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

        final Addressable addr;

        LibraryAccess(Addressable addr) {
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
