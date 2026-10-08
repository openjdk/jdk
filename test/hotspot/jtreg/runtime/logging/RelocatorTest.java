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
 * @bug 8197901 8209758
 * @summary Log relocation in class redefinition.
 * @library /test/lib
 * @modules java.compiler
 *          java.instrument
 * @requires vm.jvmti
 * @run main RedefineClassHelper
 * @run driver RelocatorTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

// package access top-level class to avoid problem with RedefineClassHelper
// and nested types.
class RelocatorTest_B {
    public static void test() {
        System.out.println("Old class");
    }
}

public class RelocatorTest {
    public static class InternalClass {
        public static String newB =
            "class RelocatorTest_B {" +
            "    public static void test() { " +
            "       System.out.println(\"New class\");" +
            "       System.out.println(\"Need more ldc's in this class\");" +
            "       System.out.println(\"Another ldc\");" +
            "    }" +
            "}";

        public static void main(String[] args) throws Exception {
            RelocatorTest_B.test();
            RedefineClassHelper.redefineClass(RelocatorTest_B.class, newB);
            RelocatorTest_B.test();
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-javaagent:redefineagent.jar",
                                                                             "-XX:+UnlockDiagnosticVMOptions",
                                                                             "-XX:+StressLdcRewrite",
                                                                             "-Xlog:relocator=trace,redefine+class+constantpool=trace",
                                                                             InternalClass.class.getName());
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldContain("Old class");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] Space at: 3 Size: 3");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] \\{method\\} .* 'test' '\\(\\)V' in 'RelocatorTest_B'");
        output.shouldMatch("\\[trace\\]\\[relocator *\\] ChangeWiden. bci: 3   New_ilen: 3");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] Space at: 12 Size: 3");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] \\{method\\} .* 'test' '\\(\\)V' in 'RelocatorTest_B'");
        output.shouldMatch("\\[trace\\]\\[relocator *\\] ChangeWiden. bci: 12   New_ilen: 3");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] Space at: 21 Size: 3");
        output.shouldMatch("\\[debug\\]\\[relocator *\\] \\{method\\} .* 'test' '\\(\\)V' in 'RelocatorTest_B'");
        output.shouldContain("New class");
        output.shouldHaveExitValue(0);
    }
}
