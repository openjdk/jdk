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
 * @requires vm.flagless
 * @run driver TestAbortVmOnException
 * @bug 8264899
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;


public class TestAbortVmOnException {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if (args[0].equals("throwExceptionWithMessage")) {
                throw new MyAbortException("MyExceptionMessage");
            } else {
                throw new MyAbortException();
            }
        }
        // Run process throwing MyException
        Process myExceptionThrowingProcess = runProcess( "MyAbortException", false, null);
        parseOutput(myExceptionThrowingProcess, "fatal error: Saw MyAbortException, aborting");
        // Run process throwing MyException with message
        Process myExceptionThrowingWithMessageProcess = runProcess( "MyAbortException", true, null);
        parseOutput(myExceptionThrowingWithMessageProcess, "fatal error: Saw MyAbortException: MyExceptionMessage, aborting");
        // Run process throwing MyException with message and check message
        Process myExceptionThrowingWithMessageCheckProcess = runProcess( "MyAbortException", true, "MyExceptionMessage");
        parseOutput(myExceptionThrowingWithMessageCheckProcess, "fatal error: Saw MyAbortException: MyExceptionMessage, aborting");
        System.out.println("PASSED");
    }

    private static Process runProcess(String exceptionName, boolean withMessage, String exceptionMessage) throws IOException {
        if (exceptionMessage == null) {
            return ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                "-XX:AbortVMOnException=" + exceptionName, "-Xcomp", "-XX:TieredStopAtLevel=3", "-XX:-CreateCoredumpOnCrash",
                "-XX:CompileCommand=compileonly,TestAbortVmOnException::*", TestAbortVmOnException.class.getName(),
                withMessage ? "throwExceptionWithMessage" : "throwException").start();
        } else {
            return ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+UnlockDiagnosticVMOptions",
                "-XX:AbortVMOnException=" + exceptionName, "-XX:AbortVMOnExceptionMessage=" + exceptionMessage,
                "-Xcomp", "-XX:TieredStopAtLevel=3", "-XX:-CreateCoredumpOnCrash", "-XX:CompileCommand=compileonly,TestAbortVmOnException::*",
                TestAbortVmOnException.class.getName(),withMessage ? "throwExceptionWithMessage" : "throwException").start();
        }
    }

    private static void parseOutput(Process process, String expectedString) throws IOException {
        OutputAnalyzer output = new OutputAnalyzer(process);
        output.stdoutShouldNotBeEmpty();
        output.shouldContain(expectedString);
    }

}

class MyAbortException extends RuntimeException {
    public MyAbortException() {
        super();
    }

    public MyAbortException(String message) {
        super(message);
    }
}
