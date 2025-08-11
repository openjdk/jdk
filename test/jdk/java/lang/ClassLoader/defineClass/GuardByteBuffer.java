/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8365203
 * @summary Tests guarding of ByteBuffers in ClassLoader::defineClass
 * @run junit GuardByteBuffer
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

final class GuardByteBuffer {

    @Test
    void guardCrash() throws InterruptedException {
        final var cl = new ClassLoader() {
            void tryCrash() {
                var arena = Arena.ofConfined();
                int size = 65536;
                var byteBuffer = arena.allocate(size).asByteBuffer();
                for (int i = 0; i < size; i += Long.BYTES) {
                    byteBuffer.putLong(i, ThreadLocalRandom.current().nextLong());
                }
                // Close the arena underneath
                arena.close();
                defineClass(null, byteBuffer, null);
            }
        };
        final List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            threads.add(Thread.ofPlatform().start(() -> forAWhile(cl::tryCrash)));
        }
        for (var thread : threads) {
            thread.join();
        }
    }

    static void forAWhile(Runnable runnable) {
        final long deadLine = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadLine) {
            try {
                runnable.run();
            } catch (Throwable _) { }
        }
    }

}
