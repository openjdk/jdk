/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

import jdk.test.lib.*;
import jdk.test.lib.process.*;

/*
 * @test TestAbortOnVMOperationTimeout
 * @bug 8181143
 * @summary Check abort on VM timeout is working
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

public class TestAbortOnVMOperationTimeout {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Object[] arr = new Object[10_000_000];
            for (int i = 0; i < arr.length; i++) {
               arr[i] = new Object();
            }
            return;
        }

        // These should definitely pass: more than a minute is enough for Serial to act.
        // The values are deliberately non-round to trip off periodic task granularity.
        for (int delay : new int[]{63423, 12388131}) {
            testWith(delay, true);
        }

        // These should fail: Serial is not very fast. Traversing 10M objects in 5 ms
        // means less than 0.5 ns per object, which is not doable.
        for (int delay : new int[]{0, 1, 5}) {
            testWith(delay, false);
        }
    }

    public static void testWith(int delay, boolean shouldPass) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+AbortVMOnVMOperationTimeout",
                "-XX:AbortVMOnVMOperationTimeoutDelay=" + delay,
                "-Xmx256m",
                "-XX:+UseSerialGC",
                "-XX:-CreateCoredumpOnCrash",
                "TestAbortOnVMOperationTimeout",
                "foo"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (shouldPass) {
            output.shouldHaveExitValue(0);
        } else {
            output.shouldMatch("VM operation took too long");
            output.shouldNotHaveExitValue(0);
        }
    }
}

