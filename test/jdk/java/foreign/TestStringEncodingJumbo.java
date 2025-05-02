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

import org.testng.annotations.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

/*
 * @test
 * @modules java.base/jdk.internal.foreign
 * @requires sun.arch.data.model == "64"
 * @requires vm.flavor != "zero"
 *
 * @run testng/othervm -Xmx6G TestStringEncodingJumbo
 */

public class TestStringEncodingJumbo {

    @Test()
    public void testJumboSegment() {
        testWithJumboSegment("testJumboSegment", segment -> {
            segment.fill((byte) 1);
            segment.set(JAVA_BYTE, Integer.MAX_VALUE + 10L, (byte) 0);
            String big = segment.getString(100);
            assertEquals(big.length(), Integer.MAX_VALUE - (100 - 10));
        });
    }

    @Test()
    public void testStringLargerThanMaxInt() {
        testWithJumboSegment("testStringLargerThanMaxInt", segment -> {
            segment.fill((byte) 1);
            segment.set(JAVA_BYTE, Integer.MAX_VALUE + 10L, (byte) 0);
            assertThrows(IllegalArgumentException.class, () -> {
                segment.getString(0);
            });
        });
    }

    private static void testWithJumboSegment(String testName, Consumer<MemorySegment> tester) {
        Path path = Paths.get("mapped_file");
        try {
            // Relly try to make sure the file is deleted after use
            path.toFile().deleteOnExit();
            deleteIfExistsOrThrow(path);
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
                FileChannel fc = raf.getChannel();
                try (Arena arena = Arena.ofConfined()) {
                    var segment = fc.map(FileChannel.MapMode.READ_WRITE, 0L, (long) Integer.MAX_VALUE + 100, arena);
                    tester.accept(segment);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        } catch (OutOfMemoryError oome) {
            // Unfortunately, we run out of memory and cannot run this test in this configuration
            System.out.println("Skipping test because of insufficient memory: " + testName);
        } finally {
            deleteIfExistsOrThrow(path);
        }
    }

    private static void deleteIfExistsOrThrow(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ioe) {
            throw new AssertionError("Unable to delete mapped file: " + file);
        }
    }

}
