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

/*
 * @test
 * @library ../ /test/lib
 * @requires vm.debug
 * @run main/othervm
 *   -Xms1g -Xmx1g
 *   -XX:+CheckUnhandledOops
 *   -Xlog:gc -Xlog:gc+jni=debug
 *   --enable-native-access=ALL-UNNAMED
 *   TestStressAllowHeap
 */

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static jdk.test.lib.Asserts.*;

/**
 * Test verifies the GCLocker::lock_critical slow path with FFM.
 * This is the case where we enter a critical section with _needs_gc == true,
 * and need to take a safepoint.
 *
 * Based on gc/TestJNICriticalStressTest
 */
public class TestStressAllowHeap {
    private static final long DURATION_SECONDS = 30;

    public static void main(String... args) throws Exception {
        System.out.println("Running for " + DURATION_SECONDS + " secs");

        int numCriticalThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("Starting " + numCriticalThreads + " critical threads");
        for (int i = 0; i < numCriticalThreads; i += 1) {
            Thread.ofPlatform().start(new CriticalWorker());
        }

        long durationMS = 1000L * DURATION_SECONDS;
        try {
            Thread.sleep(durationMS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // hitting the problematic code path doesn't seem to be guaranteed
        // and we can not guarantee it by, e.g. stalling in a critical method
        // since that can lock up the VM (and our test code)
    }

    private static class CriticalWorker extends NativeTestHelper implements Runnable {
        static {
            System.loadLibrary("Critical");
        }

        private void doStep(MethodHandle handle, SequenceLayout sequence) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment heapSegment = MemorySegment.ofArray(new int[(int) sequence.elementCount()]);
                TestValue sourceSegment = genTestValue(sequence, arena);

                handle.invokeExact(heapSegment, (MemorySegment) sourceSegment.value(), (int) sequence.byteSize());

                // check that writes went through to array
                sourceSegment.check(heapSegment);
            }
        }

        @Override
        public void run() {
            FunctionDescriptor fDesc = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
            MethodHandle handle = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("test_allow_heap_void").get(),
                fDesc,
                Linker.Option.critical(true));
            int elementCount = 10;
            SequenceLayout sequence = MemoryLayout.sequenceLayout(elementCount, C_INT);
            while (true) {
                try {
                    doStep(handle, sequence);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        }
    }
}
