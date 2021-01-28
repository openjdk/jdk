/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @bug 8260349
 * @summary test that setting via the env-var and options file shows up as expected
 * @library /test/lib
 * @run driver MaxMetaspaceSizeEnvVarTest
 */

public class MaxMetaspaceSizeEnvVarTest {

    // This is the test class we exec, passing the MaxMetaspaceSize flag
    // by different mechanisms.
    static class Main {
        public static void main(String[] args) throws Throwable {
            long expected = Long.parseLong(args[0]);
            MemoryPoolMXBean metaspaceMemoryPool =
                ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)
                .stream()
                .filter(pool -> "Metaspace".equals(pool.getName()))
                .findFirst()
                .orElseThrow();
            long max = metaspaceMemoryPool.getUsage().getMax();
            System.out.println("Metaspace max usage is " + max);
            if (max != expected) {
                throw new RuntimeException("Metaspace max " + max +
                                           " != " + expected);
            }
        }
    }

    static void report(String msg) {
        System.out.println(msg);
        System.err.println(msg);
    }

    public static void main(String... args) throws Exception {
        final String max = String.valueOf(1024 * 1024); // 1024KB
        final String flagRaw = "MaxMetaspaceSize=" + max;
        final String flag = "-XX:" + flagRaw;
        final String main = "MaxMetaspaceSizeEnvVarTest$Main";

        ProcessBuilder pb = null;
        OutputAnalyzer output = null;

        int test = 1;
        String msg = "Test " + test +": normal command-line flag";
        report(msg);
        pb = ProcessTools.createJavaProcessBuilder(flag, main, max);
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        msg = "------ end Test " + test;
        report(msg);
        test++;

        msg = "Test " + test + ": JDK_JAVA_OPTIONS env-var";
        report(msg);
        pb = ProcessTools.createJavaProcessBuilder(main, max);
        pb.environment().put("JDK_JAVA_OPTIONS", flag);
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        msg = "------ end Test " + test;
        report(msg);
        test++;

        msg = "Test " + test + ": _JAVA_OPTIONS env-var";
        report(msg);
        pb = ProcessTools.createJavaProcessBuilder(main, max);
        pb.environment().put("_JAVA_OPTIONS", flag);
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        msg = "------ end Test " + test;
        report(msg);
        test++;

        msg = "Test " + test + ": JAVA_TOOL_OPTIONS env-var";
        report(msg);
        pb = ProcessTools.createJavaProcessBuilder(main, max);
        pb.environment().put("JAVA_TOOL_OPTIONS", flag);
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        msg = "------ end Test " + test;
        report(msg);
        test++;

        msg = "Test " + test + ": .hotspotrc file";
        report(msg);
        final String rcFile = ".hotspotrc";
        final String rcFileFlag = "-XX:Flags=" + rcFile;

        PrintWriter pw = new PrintWriter(rcFile);
        pw.println(flagRaw);
        pw.close();
        pb = ProcessTools.createJavaProcessBuilder(rcFileFlag, main, max);
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.reportDiagnosticSummary();
        msg = "------ end Test " + test;
        report(msg);
    }
}
