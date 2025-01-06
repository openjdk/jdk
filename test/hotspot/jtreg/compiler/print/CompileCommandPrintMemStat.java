/*
 * Copyright (c) 2023, 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=c2
 * @summary Checks that -XX:CompileCommand=PrintMemStat,... works
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib
 * @run driver compiler.print.CompileCommandPrintMemStat c2
 */

/*
 * @test id=c1
 * @summary Checks that -XX:CompileCommand=PrintMemStat,... works
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib
 * @run driver compiler.print.CompileCommandPrintMemStat c1
 */

package compiler.print;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.List;

public class CompileCommandPrintMemStat {

    final static String METHOD1 = "method1";
    final static String METHOD2 = "method2";

    enum CompType {
        c1, c2
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Missing Args");
        }
        CompType ctyp = CompType.valueOf(args[0]);
        test(METHOD1, METHOD2, ctyp);
        test(METHOD2, METHOD1, ctyp);
        test(METHOD1, METHOD2, ctyp);
        test(METHOD2, METHOD1, ctyp);
    }

    // By activating "memstattest" compile command, we force the compiler to do some defined (large) test allocations
    // that should show up in the reports. For details, see CompilationMemoryStatistic::do_test_allocations().

    static final long largeAlloc = 2 * 1024 * 1024;

    // For C1 and C2 both, we allocate (and leak to the end of the compilation) a large amount of resourcearea.
    static final long resourceAreaLeaked = 10 * largeAlloc;

    // For C2 only, we do some temporary test allocations in two (nested) test phases
    static final long resourceAreaLeaked = 10 * largeAlloc;

    // Example output for a C2 compilation:
    //
    //    CompileCommand: MemStat compiler/print/CompileCommandPrintMemStat$TestMain.method1 uintx MemStat = 3
    //    c1 (5) Arena usagecompiler/print/CompileCommandPrintMemStat$TestMain::method1(()V): 65456 [ra 65456]
    //    c2 (6) Arena usagecompiler/print/CompileCommandPrintMemStat$TestMain::method1(()V): 405888 [ra 272024, node 66440, comp 33712, other 33712]
    //    ----- By phase and arena type, at arena usage peak (405888) -----
    //                  phase name      total        ra      node      comp      type     index   reglive  regsplit     cienv     other
    //         (outside any phase)     111336     42928       984     33712         0         0         0         0         0     33712
    //                       parse      32728         0     32728         0         0         0         0         0         0         0
    //                     matcher      32728         0     32728         0         0         0         0         0         0         0
    //                  bldOopMaps      32728     32728         0         0         0         0         0         0         0         0
    //                   testTimer     196368    196368         0         0         0         0         0         0         0         0
    //    ------------- Arena allocation timelime by phase -----------------
    //                                         start         end   end delta
    //     0      (outside any phase):             0      102120     +102120
    //     1                    parse:        102120      134848      +32728
    //     2      (outside any phase):        134848      145048      +10200
    //     3                  matcher:        145048      176792      +31744
    //     4                 regalloc:        176792      178760       +1968
    //     5              computeLive:        178760      211488      +32728
    //     6                 regalloc:        211488      176792      -34696
    //     7               bldOopMaps:        176792      209520      +32728
    //     8                testTimer:        209520      307704      +98184
    //------------------------------------------------------------------

    private static void test(String include, String exclude, CompType ctyp) throws Exception {
        List<String> options = new ArrayList<String>();
        options.add("-Xcomp");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::*");
        options.add("-XX:-Inline");
        if (ctyp.equals(CompType.c2)) {
            options.add("-XX:-TieredCompilation");
        } else {
            options.add("-XX:TieredStopAtLevel=1");
        }
        options.add("-XX:CompileCommand=MemStat," + getTestMethod(include) + ",print");
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.reportDiagnosticSummary();

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
        oa.shouldHaveExitValue(0).
                shouldMatch("Arena usage.*" + expectedNameIncl + ".*").
                shouldNotMatch("Arena usage.*" + expectedNameExcl + ".*");


        // Should see final report
        // Looks like this:
        // total     Others    RA        HA        NA        result  #nodes  limit   time    type  #rc thread             method
        // 523648    32728     490920    0         0         ok      -       -       0.250   c1    1   0x00007f4ec00d4ac0 java/lang/Class::descriptorString(()Ljava/lang/String;)
        // or
        // 1898600   853176    750872    0         294552    ok      934     -       1.501   c2    1   0x00007f4ec00d3330 java/lang/String::replace((CC)Ljava/lang/String;)
        oa.shouldMatch("total.*method");
        oa.shouldMatch("\\d+ +(\\d+ +){4}ok +(\\d+|-) +.*" + expectedNameIncl + ".*");

        // In debug builds, we have a default memory limit enabled. That implies MemStat. Therefore we
        // expect to see all methods, not just the one we specified on the command line.
        if (Platform.isDebugBuild()) {
            oa.shouldMatch("\\d+ +(\\d+ +){4}ok +(\\d+|-) +.*" + expectedNameExcl + ".*");
        } else {
            oa.shouldNotMatch(".*" + expectedNameExcl + ".*");
        }
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

