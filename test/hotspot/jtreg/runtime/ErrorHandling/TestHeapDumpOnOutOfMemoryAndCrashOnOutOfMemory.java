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
 * @test TestHeapDumpOnOutOfMemoryAndCrashOnOutOfMemory
 * @summary Test verifies call to -XX:HeapDumpOnOutOfMemoryError and
 *          CrashOnOutOfMemoryError handled in a single safepoint operation
 * @library /test/lib
 * @requires vm.flagless
 * @run driver TestHeapDumpOnOutOfMemoryAndCrashOnOutOfMemory
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestHeapDumpOnOutOfMemoryAndCrashOnOutOfMemory {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            try {
                Object[] oa = new Object[Integer.MAX_VALUE];
                for(int i = 0; i < oa.length; i++) {
                    oa[i] = new Object[Integer.MAX_VALUE];
                }
                throw new Error("OOME not triggered");
            } catch (OutOfMemoryError err) {
                return;
            }
        }
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+HeapDumpOnOutOfMemoryError",
                  "-XX:+CrashOnOutOfMemoryError",
                  "-Xlog:gc",
                  TestHeapDumpOnOutOfMemoryError.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        int exitValue = output.getExitValue();
        if(0 != exitValue) {
          //expecting a non zero value, as it could be due to HeapDumpOnOutOfMemory or CrashOnOutOfMemory
          output.stdoutShouldNotBeEmpty();
          output.shouldNotContain("[info][gc] GC");
          System.out.println("PASSED");
        } else {
          throw new Error("Expected to get non zero exit value");
        }
    }
}
