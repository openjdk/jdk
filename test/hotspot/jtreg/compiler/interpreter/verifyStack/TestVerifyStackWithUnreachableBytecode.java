/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=return
 * @bug 8336906
 * @summary Ensure VerifyStack does not crash on bytecodes in unreachable basic blocks after return.
 * @compile B.java A.java TestImpl.jasm TestVerifyStackWithUnreachableBytecode.java
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack -Xcomp
 *      -XX:CompileCommand=compileonly,compiler/interpreter/verifyStack/TestImpl.test*
 *      compiler.interpreter.verifyStack.TestVerifyStackWithUnreachableBytecode return
 */

/*
 * @test id=areturn
 * @bug 8336906
 * @summary Ensure VerifyStack does not crash on bytecodes in unreachable basic blocks after areturn.
 * @compile B.java A.java TestImpl.jasm TestVerifyStackWithUnreachableBytecode.java
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack -Xcomp
 *      -XX:CompileCommand=compileonly,compiler/interpreter/verifyStack/TestImpl.test*
 *      compiler.interpreter.verifyStack.TestVerifyStackWithUnreachableBytecode areturn
 */

/*
 * @test id=goto
 * @bug 8336906 8271055
 * @summary Ensure VerifyStack does not crash on bytecodes in unreachable basic blocks after goto.
 * @compile B.java A.java TestImpl.jasm TestVerifyStackWithUnreachableBytecode.java
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack -Xcomp
 *      -XX:CompileCommand=compileonly,compiler/interpreter/verifyStack/TestImpl.test*
 *      compiler.interpreter.verifyStack.TestVerifyStackWithUnreachableBytecode goto
 */

/*
 * @test id=gotow
 * @bug 8336906 8271055
 * @summary Ensure VerifyStack does not crash on bytecodes in unreachable basic blocks after gotow.
 * @compile B.java A.java TestImpl.jasm TestVerifyStackWithUnreachableBytecode.java
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack -Xcomp
 *      -XX:CompileCommand=compileonly,compiler/interpreter/verifyStack/TestImpl.test*
 *      compiler.interpreter.verifyStack.TestVerifyStackWithUnreachableBytecode gotow
 */

package compiler.interpreter.verifyStack;

import compiler.interpreter.verifyStack.*;

public class TestVerifyStackWithUnreachableBytecode {
    public static void main(String[] args) {
        TestCase t = testCaseFromString(args[0]);

        // The following is designed to cause a deopt with the reason `null_assert_or_unreached0`
        // when accessing A.val using getstatic due to the class B not being loaded and the consequent
        // assumption of A.val == null.
        A.val = null;
        dispatchTest(t);
        A.val = new B(42);
        dispatchTest(t);
    }

    private enum TestCase {
        ARETURN,
        GOTO,
        GOTOW,
        RETURN;
    }

    private static TestCase testCaseFromString(String s) {
        return switch (s) {
            case "areturn" -> TestCase.ARETURN;
            case "return" -> TestCase.RETURN;
            case "goto" -> TestCase.GOTO;
            case "gotow" -> TestCase.GOTOW;
            default -> throw new RuntimeException("Test argument not recognized: " + s);
        };
    }

    private static void dispatchTest(TestCase testCase) {
        switch (testCase) {
            case ARETURN -> TestImpl.testAreturn();
            case RETURN -> TestImpl.testReturn();
            case GOTO -> TestImpl.testGoto();
            case GOTOW -> TestImpl.testGotow();
        }
    }
}
