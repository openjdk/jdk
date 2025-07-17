/*
 * Copyright (c) 2023, 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Checks that -XX:CompileCommand=MemStat,...,print works with C2
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib
 * @run driver compiler.print.CompileCommandPrintMemStat c2
 */

/*
 * @test id=c1
 * @summary Checks that -XX:CompileCommand=MemStat,...,print works with C1
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
        options.add("-XX:+UnlockDiagnosticVMOptions");
        options.add("-XX:+PrintCompilerMemoryStatisticsAtExit");

        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.reportDiagnosticSummary();

        // We expect two printouts for "MemStat". A line at compilation time, and a line in a summary report
        // that is printed when we exit. Both use the typical <class>::name format but use / as separator and also
        // print the signature.
        String expectedNameIncl = getTestMethod(include)
                .replace('.', '/')
                .replace("$", "\\$");
        String expectedNameExcl = getTestMethod(exclude)
                .replace('.', '/')
                .replace("$", "\\$");

        oa.shouldHaveExitValue(0);

        if (ctyp == CompType.c1) {
            // Example output for a C1 compilation output:
            // c1 (885) (ok) Arena usage java/util/zip/ZipFile$Source::get((Ljava/io/File;ZLjava/util/zip/ZipCoder;)Ljava/util/zip/ZipFile$Source;): 752744 [ra 687288, cienv 65456]
            oa.shouldMatch("c1.*Arena usage " + expectedNameIncl + ".*: \\d+.*");
        }

        // In C2, analyze phase timeline and per-phase accumulation
        if (ctyp == CompType.c2) {
            oa.shouldMatch("c2.*Arena usage " + expectedNameIncl + ".*: \\d+.*");
            oa.shouldMatch("--- Arena Usage by Arena Type and compilation phase, at arena usage peak of \\d+ ---");
            oa.shouldContain("--- Allocation timelime by phase ---");
            if (Platform.isDebugBuild()) {
                oa.shouldMatch(".*testPhase2 +33554432 +33554432 +0 +0 +0 +0 +0.*");
                oa.shouldMatch(" +>\\d+ +testPhase1.*significant temporary peak: \\d+ \\(\\+2098136\\)");
                oa.shouldMatch(" +>\\d+ +testPhase2 +\\d+ +\\(\\+33554432\\).*");
            }
        }

        // We also print a final report to tty if print is enabled. Looks like this:
        //
        // Compilation Memory usage:
        //    ctyp  total     ra        node      comp      type      index     reglive   regsplit  cienv     other     #nodes  codesz  result  limit   time    id    thread             method
        //    c1    14104176  13776896  0         0         0         0         0         0         327280    0         -       0       oom     10240K  0,547   412   0x00007fb14c1fb640 jdk/internal/classfile/impl/StackMapGenerator::processBlock((Ljdk/internal/classfile/impl/RawBytecodeHelper;)Z)
        //    c2    5058384   4499056   262808    197352    0         0         0         0         32728     66440     293     1464    ok      10240K  0,200   191   0x00007fb14c1f9bb0 java/lang/StringLatin1::lastIndexOf(([BII)I)
        oa.shouldMatch("\\s+ctyp\\s+total.*method.*");
        oa.shouldMatch("\\s+c(1|2)\\s+\\d+.*" + expectedNameIncl + ".*");

        // In debug builds, we have a default memory limit enabled. That implies MemStat. Therefore we
        // may see the other method also.
        if (!Platform.isDebugBuild()) {
            oa.shouldNotContain(expectedNameExcl);
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

