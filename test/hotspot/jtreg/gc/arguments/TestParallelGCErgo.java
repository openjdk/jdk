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

package gc.arguments;

/*
 * @test TestParallelGCErgo
 * @bug 8272364
 * @requires vm.gc.Parallel
 * @summary Verify ParallelGC minimum young and old ergonomics are setup correctly
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library /
 * @run driver gc.arguments.TestParallelGCErgo
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

public class TestParallelGCErgo {
    private static final long HEAPWORD_SIZE = Platform.is64bit() ? 8 : 4;
    // Must be a power of 2
    private static final long GEN_ALIGNMENT = 64 * 1024 * HEAPWORD_SIZE;

    private static final long MINIMUM_HEAP_SIZE = 256 * 1024 * 1024; // 256M
    private static final long EXPECTED_MIN_YOUNG = alignDown(MINIMUM_HEAP_SIZE / 3, GEN_ALIGNMENT);
    private static final long EXPECTED_MIN_OLD = MINIMUM_HEAP_SIZE - EXPECTED_MIN_YOUNG;  // heap size = young size + old size

    // s has to be a power of 2
    private static long alignDown(long s, long align) {
        return s & (~(align-1));
    }

    public static void main(String[] args) throws Exception {
        ArrayList<String> flagList = new ArrayList<String>();
        flagList.add("-XX:+UseParallelGC");
        flagList.add("-Xms256m");
        flagList.add("-Xmx1g");
        flagList.add("-Xlog:gc+heap=trace");
        flagList.add("-version");

        OutputAnalyzer output = GCArguments.executeTestJava(flagList);
        output.shouldHaveExitValue(0);

        String stdout = output.getStdout();
        long minimumHeap = getFlagValue("Minimum heap", stdout);
        if (minimumHeap != MINIMUM_HEAP_SIZE) {
            throw new RuntimeException("Wrong value for minimum heap. Expected " + MINIMUM_HEAP_SIZE + " but got " + minimumHeap);
        }

        long minimumYoung = getFlagValue("Minimum young", stdout);
        if (minimumYoung != EXPECTED_MIN_YOUNG) {
            throw new RuntimeException("Wrong value for minimum young. Expected " + EXPECTED_MIN_YOUNG + " but got " + minimumYoung);
        }

        long minimumOld = getFlagValue("Minimum old", stdout);
        if (minimumOld != EXPECTED_MIN_OLD) {
            throw new RuntimeException("Wrong value for minimum old. Expected " + EXPECTED_MIN_OLD + " but got " + minimumOld);
        }
    }

    private static long getFlagValue(String flag, String where) {
        Matcher m = Pattern.compile(flag + " \\d+").matcher(where);
        if (!m.find()) {
            throw new RuntimeException("Could not find value for flag " + flag + " in output string");
        }
        String match = m.group();
        return Long.parseLong(match.substring(match.lastIndexOf(" ") + 1, match.length()));
    }

}
