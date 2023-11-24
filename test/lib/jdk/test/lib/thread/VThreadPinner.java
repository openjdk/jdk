/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.thread;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class to allow tests run an action in a virtual thread while pinning its carrier.
 *
 * It defines the {@code runPinned} method to run an action with a native frame on the stack.
 */
public class VThreadPinner {
    private static final Path JAVA_LIBRARY_PATH = Path.of(System.getProperty("java.library.path"));
    private static final Path LIB_PATH = JAVA_LIBRARY_PATH.resolve(System.mapLibraryName("VThreadPinner"));

    // method handle to call the native function
    private static final MethodHandle INVOKER = invoker();

    // function pointer to call
    private static final MemorySegment UPCALL_STUB = upcallStub();

    /**
     * Thread local with the action to run.
     */
    private static final ThreadLocal<ActionRunner> ACTION_RUNNER = new ThreadLocal<>();

    /**
     * Runs an action, capturing any exception or error thrown.
     */
    private static class ActionRunner implements Runnable {
        private final ThrowingAction<?> action;
        private Throwable throwable;

        ActionRunner(ThrowingAction<?> action) {
            this.action = action;
        }

        @Override
        public void run() {
            try {
                action.run();
            } catch (Throwable ex) {
                throwable = ex;
            }
        }

        Throwable exception() {
            return throwable;
        }
    }

    /**
     * Called by the native function to run the action stashed in the thread local. The
     * action runs with the native frame on the stack.
     */
    private static void callback() {
        ACTION_RUNNER.get().run();
    }

    /**
     * A function to run from a virtual thread pinned to its carrier.
     */
    @FunctionalInterface
    public interface ThrowingAction<X extends Throwable> {
        void run() throws X;
    }

    /**
     * Runs the given action on virtual thread pinned to its carrier.
     */
    public static <X extends Throwable> void runPinned(ThrowingAction<X> action) throws X {
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalCallerException("Not a virtual thread");
        }
        var runner = new ActionRunner(action);
        ACTION_RUNNER.set(runner);
        try {
            INVOKER.invoke(UPCALL_STUB);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            ACTION_RUNNER.remove();
        }
        Throwable ex = runner.exception();
        if (ex != null) {
            if (ex instanceof RuntimeException e)
                throw e;
            if (ex instanceof Error e)
                throw e;
            throw (X) ex;
        }
    }

    /**
     * Returns a method handle to the native function void call(void *(*f)(void *)).
     */
    private static MethodHandle invoker() {
        Linker abi = Linker.nativeLinker();
        try {
            SymbolLookup lib = SymbolLookup.libraryLookup(LIB_PATH, Arena.global());
            MemorySegment symbol = lib.find("call").orElseThrow();
            FunctionDescriptor desc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
            return abi.downcallHandle(symbol, desc);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an upcall stub to use as a function pointer to invoke the callback method.
     */
    private static MemorySegment upcallStub() {
        Linker abi = Linker.nativeLinker();
        try {
            MethodHandle callback = MethodHandles.lookup()
                    .findStatic(VThreadPinner.class, "callback", MethodType.methodType(void.class));
            return abi.upcallStub(callback, FunctionDescriptor.ofVoid(), Arena.global());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
