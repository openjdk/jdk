/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392739
 * @summary A rejected prefixes array must not leave the prefixes that were
 *          already copied behind.
 * @requires vm.jvmti
 * @library /test/lib
 * @run main/othervm/native -agentlib:RejectedPrefixesNoLeak
 *      -XX:NativeMemoryTracking=summary RejectedPrefixesNoLeak
 */

/*
 * Regression test for the off-by-one cleanup loops in
 * JvmtiEnvBase::set_native_method_prefixes() (jvmtiEnvBase.cpp).
 *
 * The function copies the caller's prefixes one by one with os::strdup(). When
 * it finds a null entry it frees what it has copied so far and returns
 * JVMTI_ERROR_NULL_POINTER, but the cleanup loop ran to i - 1 while the array
 * was already populated up to i, so the most recently copied prefix stayed
 * allocated. Every rejected call leaked one string.
 *
 * The agent here calls SetNativeMethodPrefixes() with { <prefix>, nullptr },
 * so the first prefix is copied and the second one is rejected. Repeating that
 * with a large prefix makes the leak big enough to read off NMT's Serviceability
 * accounting, which is where os::strdup() charges the copies: an unfixed VM
 * grows by iterations * prefix length, a fixed one does not grow at all.
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.dcmd.PidJcmdExecutor;

public class RejectedPrefixesNoLeak {

    private static final int JVMTI_ERROR_NULL_POINTER = 100;

    // One megabyte per copied prefix, so the leak stands out against the noise
    // of whatever else is charged to Serviceability while the test runs.
    private static final int PREFIX_LENGTH = 1024 * 1024;
    private static final int ITERATIONS = 64;

    // An unfixed VM leaks 64MB here, a fixed one stays flat, so anything above
    // a few megabytes of growth is the bug and not measurement noise.
    private static final long GROWTH_LIMIT_KB = 16 * 1024;

    private static final Pattern SERVICEABILITY_COMMITTED =
        Pattern.compile("Serviceability \\(reserved=\\d+KB, committed=(\\d+)KB\\)");

    // Calls SetNativeMethodPrefixes() with a rejected array the given number of
    // times and returns the error code of the last call.
    private static native int rejectPrefixes(int iterations, int prefixLength);

    private static long serviceabilityCommittedKB() {
        String summary = new PidJcmdExecutor().execute("VM.native_memory summary scale=KB").getStdout();
        Matcher matcher = SERVICEABILITY_COMMITTED.matcher(summary);
        if (!matcher.find()) {
            throw new RuntimeException("No Serviceability entry in the NMT summary:\n" + summary);
        }
        return Long.parseLong(matcher.group(1));
    }

    public static void main(String[] args) {
        long before = serviceabilityCommittedKB();

        int error = rejectPrefixes(ITERATIONS, PREFIX_LENGTH);
        if (error != JVMTI_ERROR_NULL_POINTER) {
            throw new RuntimeException("SetNativeMethodPrefixes returned " + error
                                       + ", expected JVMTI_ERROR_NULL_POINTER");
        }

        long growth = serviceabilityCommittedKB() - before;
        System.out.println("Serviceability committed grew by " + growth + "KB");

        if (growth > GROWTH_LIMIT_KB) {
            throw new RuntimeException(ITERATIONS + " rejected SetNativeMethodPrefixes calls with a "
                                       + (PREFIX_LENGTH / 1024) + "KB prefix grew Serviceability by " + growth
                                       + "KB, expected at most " + GROWTH_LIMIT_KB + "KB");
        }
        System.out.println("Test PASSED");
    }
}
