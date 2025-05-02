/*
 * Copyright 2023 Alphabet LLC.  All Rights Reserved.
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
 * @bug 8315149
 * @summary Unit test to ensure CPU hsperf counters are published.
 * @requires vm.gc.G1
 *
 * @library /test/lib
 *
 * @run main/othervm -XX:+UsePerfData -XX:+UseStringDeduplication TestGcCounters
 */

import static jdk.test.lib.Asserts.*;

import jdk.test.lib.process.OutputAnalyzer;

public class TestGcCounters {

    private static final String SUN_THREADS = "sun.threads";
    private static final String SUN_THREADS_CPUTIME = "sun.threads.cpu_time";

    public static void main(String[] args) throws Exception {
        testGcCpuCountersExist();
    }


    /**
     * jcmd -J-XX:+UsePerfData pid PerfCounter.print
     */
     private static void testGcCpuCountersExist() throws Exception {
        OutputAnalyzer output = JcmdBase.jcmd(new String[] {"PerfCounter.print"});

        output.shouldHaveExitValue(0);
        output.shouldContain(SUN_THREADS + ".total_gc_cpu_time");
        output.shouldContain(SUN_THREADS_CPUTIME + ".gc_conc_mark");
        output.shouldContain(SUN_THREADS_CPUTIME + ".gc_conc_refine");
        output.shouldContain(SUN_THREADS_CPUTIME + ".gc_service");
        output.shouldContain(SUN_THREADS_CPUTIME + ".gc_parallel_workers");
        output.shouldContain(SUN_THREADS_CPUTIME + ".vm");
        output.shouldContain(SUN_THREADS_CPUTIME + ".conc_dedup");
    }
}

