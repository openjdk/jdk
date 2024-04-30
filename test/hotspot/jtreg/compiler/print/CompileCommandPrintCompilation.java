/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255746
 * @summary Checks that -XX:CompileCommand=PrintCompilation,... works
 * @library /test/lib
 * @run driver compiler.print.CompileCommandPrintCompilation
 */

package compiler.print;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CompileCommandPrintCompilation {

    final static String METHOD1 = "method1";
    final static String METHOD2 = "method2";

    public static void main(String[] args) throws Exception {
        test(METHOD1, METHOD2);
        test(METHOD2, METHOD1);
    }

    private static void test(String include, String exclude) throws Exception {
        List<String> options = new ArrayList<String>();
        options.add("-Xcomp");
        options.add("-XX:-Inline");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::*");
        options.add("-XX:CompileCommand=PrintCompilation," + getTestMethod(include));
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);

        oa.shouldHaveExitValue(0)
        .shouldContain(getTestMethod(include))
        .shouldNotContain(getTestMethod(exclude));
    }

    // Test class that is invoked by the sub process
    public static String getTestClass() {
        return TestMain.class.getName();
    }

    public static String getTestMethod(String method) {
        return getTestClass() + "::" + method;
    }

    public static class TestMain {
        public static void main(String[] args) {
            method1();
            method2();
        }

        static void method1() {}
        static void method2() {}
    }
}

