/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.reflect.*;
import jdk.internal.misc.Unsafe;


/*
 * @test
 * @bug 8290892
 * @summary Tests to ensure that reachabilityFence() correctly keeps objects from being collected prematurely.
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:CompileCommand=compileonly,*TestReachabilityFenceWithBuffer::* -Xbatch compiler.c2.TestReachabilityFenceWithBuffer
 */
public class TestReachabilityFenceWithBuffer {
    /*
    /build/macosx-aarch64-debug/jdk/bin/java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -XX:CompileCommand=compileonly,*TestReachabilityFenceWithBuffer::test* -Xbatch -XX:+UseNewCode TestReachabilityFenceWithBuffer.java
    */

    static class MyBuffer {

        private static Unsafe UNSAFE = Unsafe.getUnsafe();

        static {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private static int current = 0;
        private static long payload[] = new long[10];

        private final int id;

        public MyBuffer(long size) {
            // Get a unique id, allocate memory and safe the address in the payload array
            id = current++;
            payload[id] = UNSAFE.allocateMemory(size);

            // Register a cleaner to free the memory when the buffer is garbage collected
            int lid = id; // Capture current value
            Cleaner.create()
                .register(this, () -> {
                    free(lid);
                });

            System.out.println(
                "Created new buffer of size = " + size + " with id = " + id
            );
        }

        private static void free(int id) {
            System.out.println("Freeing buffer with id = " + id);
            UNSAFE.freeMemory(payload[id]);
            payload[id] = 0;
        }

        public void put(int offset, byte b) {
            UNSAFE.putByte(payload[id] + offset, b);
        }

        public byte get(int offset) {
            return UNSAFE.getByte(payload[id] + offset);
        }
    }

    static MyBuffer buffer = new MyBuffer(100);

    static {
        // Initialize buffer
        for (int i = 0; i < 100; ++i) {
            buffer.put(i, (byte) 42);
        }
    }

    static void test(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBuffer myBuffer = buffer;
            if (myBuffer == null) return;
            for (int i = 0; i < 100; i++) {
                byte b = myBuffer.get(i);
                if (b != 42) {
                    throw new RuntimeException(
                        "Unexpected value = " +
                        b +
                        ". Buffer was garbage collected before reachabilityFence was reached!"
                    );
                }
            }
            // Keep the buffer live while we read from it
            Reference.reachabilityFence(myBuffer);
        }
    }

    public static void main(String[] args) throws Exception {
        // Warmup to trigger compilation
        for (int i = 0; i < 20; i++) {
            test(100);
        }

        // Clear reference to 'buffer' and make sure it's garbage collected
        Thread gcThread = new Thread() {
            public void run() {
                buffer = null;
                System.out.println(
                    "Buffer set to null. Waiting for garbage collection."
                );
                System.gc();
            }
        };
        gcThread.start();

        test(100_000);
    }
}
