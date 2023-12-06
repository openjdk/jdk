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
 * @summary Checks that -XX:CompileCommand=PrintMemStat,... works
 * @requires vm.compiler1.enabled | vm.compiler2.enabled
 * @library /test/lib
 * @run driver compiler.print.CompileCommandPrintMemStat
 */

package compiler.print;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;

public class CompileCommandPrintMemStat {

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
        options.add("-XX:CompileCommand=MemStat," + getTestMethod(include) + ",print");
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJvm(options);

        // We expect two printouts for "PrintMemStat". A line at compilation time, and a line in a summary report
        // that is printed when we exit. Both use the typical <class>::name format but use / as separator and also
        // print the signature.
        String expectedNameIncl = getTestMethod(include)
                .replace('.', '/')
                .replace("$", "\\$");
        String expectedNameExcl = getTestMethod(exclude)
                .replace('.', '/')
                .replace("$", "\\$");

        // Should see trace output when methods are compiled
        oa.shouldHaveExitValue(0)
          .shouldMatch(".*" + expectedNameIncl + ".*")
          .shouldNotMatch(".*" + expectedNameExcl + ".*");

        // Should see final report
        // Looks like this:
        // total     NA        RA        result  #nodes  time    type  #rc thread              method
        // 211488    66440     77624     ok      13      0.057   c2    2   0x00007fb49428db70  compiler/print/CompileCommandPrintMemStat$TestMain::method1(()V)
        oa.shouldMatch("total.*method");
        oa.shouldMatch("\\d+ +\\d+ +\\d+ +\\S+ +\\d+.*" + expectedNameIncl + ".*");
        oa.shouldNotMatch("\\d+ +\\d+ +\\d+ +\\S+ +\\d+.*" + expectedNameExcl + ".*");
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

