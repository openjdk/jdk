/*
 * Copyright (c) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @test TestExitOnDirectOutOfMemoryError
 * @summary Test using -XX:ExitOnOutOfMemoryError
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @run driver TestExitOnDirectOutOfMemoryError
 * @bug 8138745
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.nio.ByteBuffer;
public class TestExitOnDirectOutOfMemoryError {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            // This should guarantee to throw:
            // java.lang.OutOfMemoryError: Cannot reserve 2147483647 bytes of direct buffer memory (allocated: 0, limit: 20971520)
            try {
               ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.MAX_VALUE);
            } catch (OutOfMemoryError err) {
                throw new Error("OOME didn't terminate JVM!");
            }
        }

        // else this is the main test
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+ExitOnOutOfMemoryError",
                "-Xmx20m","-Djdk.nio.reportOomOnDirectMemoryOom=true", TestExitOnDirectOutOfMemoryError.class.getName(), "throwOOME");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        /*
         * Actual output should look like this:
         * Terminating due to java.lang.OutOfMemoryError: Cannot reserve 2147483647 bytes of direct buffer memory (allocated: 0, limit: 20971520)
         */
        output.shouldHaveExitValue(3);
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("Terminating due to java.lang.OutOfMemoryError: Cannot reserve 2147483647 bytes of direct" +
                " buffer memory (allocated: 0, limit: 20971520)");
        System.out.println("PASSED");
    }
}
