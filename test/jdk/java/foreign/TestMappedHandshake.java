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

/*
 * @test
 * @requires vm.flavor != "zero"
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.misc
 * @key randomness
 * @run testng/othervm TestMappedHandshake
 * @run testng/othervm -Xint TestMappedHandshake
 * @run testng/othervm -XX:TieredStopAtLevel=1 TestMappedHandshake
 * @run testng/othervm -XX:-TieredCompilation TestMappedHandshake
 */

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TestMappedHandshake {

    static final int SEGMENT_SIZE = 1_000_000;
    static final int ACCESS_START_DELAY_MILLIS = 100;
    static final int POST_ACCESS_DELAY_MILLIS = 1;
    static final int TIMED_RUN_TIME_SECONDS = 10;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 20;

    static final int NUM_ACCESSORS = 5;

    static final Path tempPath;

    static {
        try {
            File file = File.createTempFile("buffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
            Files.write(file.toPath(), new byte[SEGMENT_SIZE], StandardOpenOption.WRITE);

        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test
    public void testHandshake() throws InterruptedException, IOException {
        try (FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE) ;
             Arena arena = Arena.ofShared()) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, SEGMENT_SIZE, arena);
            ExecutorService accessExecutor = Executors.newFixedThreadPool(NUM_ACCESSORS + 1);
            // start handshaker
            accessExecutor.execute(new Handshaker());
            Thread.sleep(ACCESS_START_DELAY_MILLIS);
            // start accessors
            for (int i = 0 ; i < NUM_ACCESSORS ; i++) {
                accessExecutor.execute(new MappedSegmentAccessor(segment));
            }

            accessExecutor.shutdown();
            assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
        }
    }

    static abstract class TimedAction implements Runnable {
        @Override
        public void run() {
            long start = System.currentTimeMillis();
            while (true) {
                try {
                    doAction();
                } catch (Throwable ex) {
                    // ignore
                } finally {
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed > TIMED_RUN_TIME_SECONDS * 1000) {
                        break;
                    }
                }
            }
        }

        abstract void doAction() throws Throwable;
    }

    static class MappedSegmentAccessor extends TimedAction {

        final MemorySegment segment;

        MappedSegmentAccessor(MemorySegment segment) {
            this.segment = segment;
        }

        @Override
        void doAction() throws Throwable {
            segment.load();
            Thread.sleep(POST_ACCESS_DELAY_MILLIS);
            segment.isLoaded();
            Thread.sleep(POST_ACCESS_DELAY_MILLIS);
            segment.unload();
            Thread.sleep(POST_ACCESS_DELAY_MILLIS);
            segment.force();
        }
    }

    static class Handshaker extends TimedAction {

        @Override
        public void doAction() {
            Arena.ofShared().close();
        }
    }
}
