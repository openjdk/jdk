/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CountDownLatch;

/**
 * Test native threads attaching implicitly to the VM by means of an upcall.
 */
public class ImplicitAttach {
    private static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withBitAlignment(32);
    private static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS.withBitAlignment(64);

    private static volatile CountDownLatch latch;

    public static void main(String[] args) throws Throwable {
        int threadCount;
        if (args.length > 0) {
            threadCount = Integer.parseInt(args[0]);
        } else {
            threadCount = 2;
        }
        latch = new CountDownLatch(threadCount);

        Linker abi = Linker.nativeLinker();

        // stub to invoke callback
        MethodHandle callback = MethodHandles.lookup()
                .findStatic(ImplicitAttach.class, "callback", MethodType.methodType(void.class));
        MemorySegment upcallStub = abi.upcallStub(callback,
                FunctionDescriptor.ofVoid(),
                MemorySession.global());

        // void start_threads(int count, void *(*f)(void *))
        SymbolLookup symbolLookup = SymbolLookup.loaderLookup();
        MemorySegment symbol = symbolLookup.lookup("start_threads").orElseThrow();
        FunctionDescriptor desc = FunctionDescriptor.ofVoid(C_INT, C_POINTER);
        MethodHandle start_threads = abi.downcallHandle(symbol, desc);

        // start the threads and wait for the threads to call home
        start_threads.invoke(threadCount, upcallStub);
        latch.await();
    }

    /**
     * Invoked from native thread.
     */
    private static void callback() {
        System.out.println(Thread.currentThread());
        latch.countDown();
    }

    static {
        System.loadLibrary("ImplicitAttach");
    }
}
