/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=Arena_allocateFrom
 * @run main/othervm/timeout=10 --enable-native-access=ALL-UNNAMED -Xlog:class+init TestDeadlock Arena
 */

/*
 * @test id=FileChannel_map
 * @run main/othervm/timeout=60 --enable-native-access=ALL-UNNAMED -Xlog:class+init TestDeadlock FileChannel
 */

import java.lang.foreign.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

public class TestDeadlock {
    public static void main(String[] args) throws Throwable {
        CountDownLatch latch = new CountDownLatch(2);

        Runnable tester = switch (args[0]) {
            case "Arena" -> () -> {
                Arena arena = Arena.global();
                arena.scope(); // init ArenaImpl
                ValueLayout.JAVA_INT.byteSize(); // init ValueLayout (and impls)
                latch.countDown();
                try {
                    latch.await();
                } catch(InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // Access ArenaImpl -> NativeMemorySegmentImpl -> MemorySegment
                arena.allocateFrom(ValueLayout.JAVA_INT, 42);
            };
            case "FileChannel" -> () -> {
                try {
                    Arena arena = Arena.global();
                    Path p = Files.createFile(Path.of("test.out"));

                    try (FileChannel channel = FileChannel.open(p, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                        channel.map(FileChannel.MapMode.READ_WRITE, 0, 4); // create MappedByteBuffer to initialize other things
                        latch.countDown();
                        latch.await();

                        // Access MappedMemorySegmentImpl -> MemorySegment
                        channel.map(FileChannel.MapMode.READ_WRITE, 0, 4, arena);
                    }
                } catch(InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            };
            default -> throw new IllegalArgumentException("Unknown test selection: " + args[0]);
        };

        Thread t1 = Thread.ofPlatform().start(tester);
        Thread t2 = Thread.ofPlatform().start(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch(InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Access MemorySegment -> NativeMemorySegmentImpl
            MemorySegment.ofAddress(42);
        });

        // wait for potential deadlock

        t1.join();
        t2.join();

        // all good
    }
}
