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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import jdk.test.lib.thread.VThreadRunner.ThrowingRunnable;

/**
 * Helper class to allow tests run a task in a virtual thread while pinning its carrier.
 *
 * It defines the {@code runPinned} method to run a task with a native frame on the stack.
 */
public class VThreadPinner {
    private static final Path JAVA_LIBRARY_PATH = Path.of(System.getProperty("java.library.path"));
    private static final Path LIB_PATH = JAVA_LIBRARY_PATH.resolve(System.mapLibraryName("VThreadPinner"));

    // method handle to call the native function
    private static final MethodHandle INVOKER = invoker();

    // function pointer to call
    private static final MemorySegment UPCALL_STUB = upcallStub();

    /**
     * Thread local with the task to run.
     */
    private static final ThreadLocal<TaskRunner> TASK_RUNNER = new ThreadLocal<>();

    /**
     * Runs a task, capturing any exception or error thrown.
     */
    private static class TaskRunner implements Runnable {
        private final ThrowingRunnable<?> task;
        private Throwable throwable;

        TaskRunner(ThrowingRunnable<?> task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                task.run();
            } catch (Throwable ex) {
                throwable = ex;
            }
        }

        Throwable exception() {
            return throwable;
        }
    }

    /**
     * Called by the native function to run the task stashed in the thread local. The
     * task runs with the native frame on the stack.
     */
    private static void callback() {
        TASK_RUNNER.get().run();
    }

    /**
     * Runs the given task on a virtual thread pinned to its carrier. If called from a
     * virtual thread then it invokes the task directly.
     */
    public static <X extends Throwable> void runPinned(ThrowingRunnable<X> task) throws X {
        if (!Thread.currentThread().isVirtual()) {
            VThreadRunner.run(() -> runPinned(task));
            return;
        }
        var runner = new TaskRunner(task);
        TASK_RUNNER.set(runner);
        try {
            INVOKER.invoke(UPCALL_STUB);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            TASK_RUNNER.remove();
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
    @SuppressWarnings("restricted")
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
    @SuppressWarnings("restricted")
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
