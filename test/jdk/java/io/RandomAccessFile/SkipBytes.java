/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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


/* @test
   @bug 4027717 8396040
   @summary Check for correct implementation of RandomAccessFile.skipBytes
   @library /test/lib
   */


import java.io.*;
import java.nio.file.Files;
import java.util.List;

import static jdk.test.lib.Utils.*;

public class SkipBytes{


    /*
     * doTest attempts to skip num_to_skip bytes in raf starting from position 0.
     * It also does a read after the skip to check if EOF has been reached
     * correctly or incorrectly.
     */


    private static void doTest(RandomAccessFile raf, long start, int num_to_skip)
        throws Exception
    {

        int length = (int) raf.length();
        System.err.printf(
                "size: %s, position: %s, requestedPosition: %s, requestedSkip: %s%n",
                length, raf.getFilePointer(), start, num_to_skip);

        raf.seek(start);
        long cur_ptr0 = raf.getFilePointer();
        if (start != cur_ptr0) {
            throw new RuntimeException(
                    "cur_ptr0: expected: %s, actual: %s"
                            .formatted(start, cur_ptr0));
        }

        //Do the Skip test
        int num_skipped = raf.skipBytes(num_to_skip);
        System.err.println("After skipBytes -- no. skipped = " + num_skipped);

        final int expected_num_skipped;
        if (num_to_skip <= 0 || cur_ptr0 >= length) {
            expected_num_skipped = 0;
        } else if (length - cur_ptr0 > num_to_skip) {
            expected_num_skipped = num_to_skip;
        } else {
            expected_num_skipped = Math.toIntExact(length - cur_ptr0);
        }
        if (expected_num_skipped != num_skipped) {
            throw new RuntimeException(
                    "num_skipped: expected: %s, actual: %s"
                            .formatted(expected_num_skipped, num_skipped));
        }

        long cur_ptr = raf.getFilePointer();
        System.err.println("Current pointer = " + cur_ptr);

        final long expected_cur_ptr;
        if (cur_ptr0 >= length || num_to_skip <= 0) {
            expected_cur_ptr = cur_ptr0;
        } else if (length - cur_ptr0 > num_to_skip) {
            expected_cur_ptr = cur_ptr0 + num_to_skip;
        } else {
            expected_cur_ptr = length;
        }
        if (expected_cur_ptr != cur_ptr) {
            throw new RuntimeException(
                    "cur_ptr: expected: %s, actual: %s"
                            .formatted(expected_cur_ptr, cur_ptr));
        }

        // do read test
        int byte_read = raf.read();
        if ( (cur_ptr == length) &&
             (byte_read != -1) ) {
            System.err.println("byte_read = " + byte_read +
                               " Read Test Failed ......");
            throw new RuntimeException("Read Test Failed");
        }
        else {
            System.err.println("byte_read = " + byte_read +
                               " Read Test Succeeded");
        }

    }

    public static void main(String[] args) throws Exception {
        for (long size : List.of(0, 1, 100)) {
            var fileNamePrefix = "RAF-%s-%s-".formatted(SkipBytes.class.getSimpleName(), size);
            var filePath = createTempFileOfSize(fileNamePrefix, ".dat", size);
            try {
                long[] positions = {
                        0,
                        1,
                        2,
                        size - 1,
                        size,
                        size + 1,
                        Integer.MAX_VALUE - 1L,
                        Integer.MAX_VALUE
                };
                long[] skips = {
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE + 1L,
                        -size - 1,
                        -size,
                        -size + 1,
                        -2,
                        -1,
                        0,
                        1,
                        2,
                        size - 1,
                        size,
                        size + 1,
                        Integer.MAX_VALUE - 1L,
                        Integer.MAX_VALUE
                };
                for (long position : positions) {
                    if (position >= 0) {
                        for (long skip : skips) {
                            if (skip <= Integer.MAX_VALUE) {
                                try (var raf = new RandomAccessFile(filePath.toFile(), "rw")) {
                                    doTest(raf, position, (int) skip);
                                }
                            }
                        }
                    }
                }
            } finally {
                Files.delete(filePath);
            }
        }
    }

}
