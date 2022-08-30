/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests.phase;

import compiler.lib.ir_framework.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test IR matcher with different default IR node regexes. Use -DPrintIREncoding.
 *          Normally, the framework should be called with driver.
 * @library /test/lib /testlibrary_tests /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=240 -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                               -XX:+WhiteBoxAPI -DPrintIREncoding=true  ir_framework.tests.TestIRMatching
 */

public class TestPhaseIRMatching {

    private static final Map<Exception, String> exceptions = new LinkedHashMap<>();
    private static final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
    private static final PrintStream ps = new PrintStream(baos);
    private static final PrintStream psErr = new PrintStream(baosErr);
    private static final PrintStream oldOut = System.out;
    private static final PrintStream oldErr = System.err;

    private static void addException(Exception e) {
        System.out.flush();
        System.err.flush();
        exceptions.put(e, baos + System.lineSeparator() + baosErr);
    }

    public static void main(String[] args) {
        // Redirect System.out and System.err to reduce noise.
        testNoCompilationOutputForPhase();
    }

    private static void testNoCompilationOutputForPhase() {
//        String irEncoding = """
//                            ##### IRMatchRulesEncoding - used by TestFramework #####
//                            <method>,{comma separated applied @IR rule ids}
//                            bad1,1
//                            bad2,1
//                            ----- END -----
//                            """;
//        IRMatcher irMatcher = new IRMatcher("hotspot_pid_no_compilation_output.log", irEncoding,
//                                            NoCompilationOutputForPhase.class);
//        var e = irMatcher.applyIRRules().stream().filter(MatchResult::fail);
        TestFramework.run(Asdf.class);
    }

}

class Asdf {
    int i;
    long l;
    @Test
    @IR(failOn = IRNode.STORE, phase = CompilePhase.DEFAULT)
    @IR(counts = {IRNode.STORE, "3", IRNode.ALLOC, "1", IRNode.STORE_I, "2"}, phase = CompilePhase.DEFAULT)
    public void bad1() {
        i = 34;
        l = 34;
    }

    @Test
    @IR(failOn = IRNode.STORE, phase = CompilePhase.DEFAULT)
    public void bad2() {
        i = 34;
        l = 34;
    }

    @Run(test = "bad2", mode = RunMode.STANDALONE)
    public void run() {

    }

    @Test
    @IR(failOn = IRNode.STORE, phase = {CompilePhase.DEFAULT, CompilePhase.AFTER_PARSING})
    @IR(failOn = IRNode.STORE, phase = {CompilePhase.DEFAULT, CompilePhase.AFTER_PARSING})
    public void asdf() {
        i = 34;
        l = 34;
    }

    @Run(test = "asdf", mode = RunMode.STANDALONE)
    public void ff() {

    }

    @Test
    public void foo() {}

    @Test
    @IR(failOn = IRNode.STORE, phase = {CompilePhase.DEFAULT, CompilePhase.AFTER_PARSING, CompilePhase.BEFORE_MATCHING})
    public void foo2() {
        i = 34;
        l = 34;
    }
}
class NoCompilationOutputForPhase {

    @Test
    @IR(failOn = IRNode.STORE, phase = CompilePhase.AFTER_CLOOPS)
    public void bad1() {}

    @Test
    @IR(failOn = IRNode.STORE,
        phase = {
            CompilePhase.BEFORE_STRINGOPTS,
            CompilePhase.AFTER_STRINGOPTS,
            CompilePhase.INCREMENTAL_INLINE_STEP,
            CompilePhase.INCREMENTAL_INLINE_CLEANUP,
            CompilePhase.EXPAND_VUNBOX,
            CompilePhase.SCALARIZE_VBOX,
            CompilePhase.INLINE_VECTOR_REBOX,
            CompilePhase.EXPAND_VBOX,
            CompilePhase.ELIMINATE_VBOX_ALLOC,
            CompilePhase.ITER_GVN_BEFORE_EA,
            CompilePhase.ITER_GVN_AFTER_VECTOR,
            CompilePhase.BEFORE_BEAUTIFY_LOOPS,
            CompilePhase.AFTER_BEAUTIFY_LOOPS,
            CompilePhase.BEFORE_CLOOPS,
            CompilePhase.AFTER_CLOOPS,
            CompilePhase.PHASEIDEAL_BEFORE_EA,
            CompilePhase.AFTER_EA,
            CompilePhase.ITER_GVN_AFTER_EA,
            CompilePhase.ITER_GVN_AFTER_ELIMINATION,
            CompilePhase.PHASEIDEALLOOP1,
            CompilePhase.PHASEIDEALLOOP2,
            CompilePhase.PHASEIDEALLOOP3,
            CompilePhase.PHASEIDEALLOOP_ITERATIONS}
        )
    public void bad2() {}

}
