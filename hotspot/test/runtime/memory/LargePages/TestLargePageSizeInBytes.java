/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestLargePageSizeInBytes
 * @summary Tests that the flag -XX:LargePageSizeInBytes does not cause warnings on Solaris
 * @bug 8049536
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestLargePageSizeInBytes
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;

public class TestLargePageSizeInBytes {
    private static long M = 1024L * 1024L;
    private static long G = 1024L * M;

    public static void main(String[] args) throws Exception {
        if (!Platform.isSolaris()) {
            // We only use the syscall mencntl on Solaris
            return;
        }

        testLargePageSizeInBytes(4 * M);
        testLargePageSizeInBytes(256 * M);
        testLargePageSizeInBytes(512 * M);
        testLargePageSizeInBytes(2 * G);
    }

    private static void testLargePageSizeInBytes(long size) throws Exception {
        ProcessBuilder pb =
            ProcessTools.createJavaProcessBuilder("-XX:+UseLargePages",
                                                  "-XX:LargePageSizeInBytes=" + size,
                                                  "-version");

        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldNotContain("Attempt to use MPSS failed.");
        out.shouldHaveExitValue(0);
    }
}
