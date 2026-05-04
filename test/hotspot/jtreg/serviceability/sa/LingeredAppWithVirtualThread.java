
/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, NTT DATA
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

import java.lang.invoke.MethodHandle;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CountDownLatch;

import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithVirtualThread extends LingeredApp implements Runnable {

    private static final String THREAD_NAME = "target thread";

    private static final MethodHandle hndSleep;

    private static final int sleepArg;

    private static final CountDownLatch signal = new CountDownLatch(1);

    static {
        MemorySegment func;
        if (System.getProperty("os.name").startsWith("Windows")) {
            func = SymbolLookup.libraryLookup("Kernel32", Arena.global())
                               .findOrThrow("Sleep");
            sleepArg = 3600_000; // 1h in milliseconds
        } else {
            func = Linker.nativeLinker()
                         .defaultLookup()
                         .findOrThrow("sleep");
            sleepArg = 3600; // 1h in seconds
        }

        var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
        hndSleep = Linker.nativeLinker().downcallHandle(func, desc);
    }

    @Override
    public void run() {
        Thread.yield();
        signal.countDown();
        try {
            hndSleep.invoke(sleepArg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void main(String[] args) {
        try {
            Thread.ofVirtual()
                  .name(THREAD_NAME)
                  .start(new LingeredAppWithVirtualThread());

            signal.await();
            LingeredApp.main(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
