/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7121600 8016838
 * @summary Redefine a big class.
 * @author Daniel D. Daugherty
 * @key intermittent
 * @modules java.instrument
 *          java.management
 * @library /test/lib
 * @build RedefineBigClassAgent BigClass RedefineBigClassApp NMTHelper
 * @run driver jdk.test.lib.util.JavaAgentBuilder RedefineBigClassAgent RedefineBigClassAgent.jar Can-Redefine-Classes:true
 * @run driver/timeout=600 RedefineBigClassTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RedefineBigClassTest {
    public static void main(String[] args) throws Exception {
        String nmt;
        try {
            ProcessBuilder check = ProcessTools.createTestJavaProcessBuilder(
                    "-XX:NativeMemoryTracking=detail", "-version");
            OutputAnalyzer checkOutput = ProcessTools.executeProcess(check);
            nmt = (checkOutput.getExitValue() == 0)
                    ? "-XX:NativeMemoryTracking=detail"
                    : "-XX:NativeMemoryTracking=summary";
        } catch (Exception e) {
            nmt = "-XX:NativeMemoryTracking=summary";
        }

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-Xlog:redefine+class+load=debug,redefine+class+load+exceptions=info",
                nmt,
                "-javaagent:RedefineBigClassAgent.jar=BigClass.class",
                "RedefineBigClassApp");
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldNotContain("Exception");
        output.shouldHaveExitValue(0);
    }
}
