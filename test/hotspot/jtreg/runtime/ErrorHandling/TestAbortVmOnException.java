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

/*
 * @test TestAbortVmOnException
 * @summary Test -XX:AbortVMOnException=MyAbortException with C1 compilation
 * @library /test/lib
 * @run driver TestAbortVmOnException
 * @bug 8264899
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAbortVmOnException {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            throw new MyAbortException();
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                "-XX:AbortVMOnException=MyAbortException", "-Xcomp", "-Xbatch",  "-XX:TieredStopAtLevel=3",
                TestAbortVmOnException.class.getName(), "throwException");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("fatal error: Saw MyAbortException, aborting");
        System.out.println("PASSED");
    }
}

class MyAbortException extends RuntimeException {
}
