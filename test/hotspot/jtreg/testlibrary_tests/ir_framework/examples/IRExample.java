/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.examples;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;

/*
 * @test
 * @summary Example test to use the new test framework.
 * @library /test/lib /
 * @run driver ir_framework.examples.IRExample
 */

/**
 * The file shows some examples how IR verification can be done by using the {@link IR @IR} annotation. Additional
 * comments are provided at the IR rules to explain their purpose. A more detailed and complete description about
 * IR verification and the possibilities to write IR tests with {@link IR @IR} annotations can be found in the
 * IR framework README.md file.
 *
 * @see IR
 * @see Test
 * @see TestFramework
 */
public class IRExample {
    int iFld, iFld2, iFld3;
    public static void main(String[] args) {
        TestFramework.run(); // First run tests from IRExample. No failure.
        try {
            TestFramework.run(FailingExamples.class); // Secondly, run tests from FailingExamples. Expected to fail.
        } catch (IRViolationException e) {
            // Expected. Check stderr/stdout to see how IR failures are reported (always printed, regardless if
            // exception is thrown or not). Uncomment the "throw" statement below to get a completely failing test.
            //throw e;
        }
    }

    // Rules with failOn constraint which all pass.
    @Test
    @IR(failOn = IRNode.LOAD) // 1 (pre-defined) IR node
    @IR(failOn = {IRNode.LOAD, IRNode.LOOP}) // 2 IR nodes
    @IR(failOn = {IRNode.LOAD, "some regex that does not occur"}, // 1 IR node with a user-defined regex
        phase = CompilePhase.PRINT_IDEAL)
    // Rule with special configurable IR nodes. All IR nodes with a "_OF" postfix expect a second string specifying an
    // additional required information.
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld2", IRNode.LOAD, IRNode.STORE_OF_CLASS, "Foo"})
    // Only apply this rule if the VM flag UseZGC is true
    @IR(applyIf = {"UseZGC", "true"}, failOn = IRNode.LOAD)
    // We can also use comparators (<, <=, >, >=, !=, =) to restrict the rules.
    // This rule is only applied if TypeProfileLevel is 100 or greater.
    @IR(applyIf = {"TypeProfileLevel", ">= 100"}, failOn = IRNode.LOAD)
    public void goodFailOn() {
        iFld = 42; // No load, no loop, no store to iFld2, no store to class Foo
    }

    // Rules with counts constraint which all pass.
    @Test
    @IR(counts = {IRNode.STORE, "2"}) // 1 (pre-defined) IR node
    @IR(counts = {IRNode.LOAD, "0"}) // equivalent to failOn = IRNode.LOAD
    @IR(counts = {IRNode.STORE, "2",
                  IRNode.LOAD, "0"}) // 2 IR nodes
    @IR(counts = {IRNode.STORE, "2",
                  "some regex that does not occur", "0"}, // 1 IR node and a user-defined regex
        phase = CompilePhase.PRINT_IDEAL)
    // Rule with special configurable IR nodes. All IR nodes with a "_OF" postfix expect a second string specifying an
    // additional required information.
    @IR(counts = {IRNode.STORE_OF_FIELD, "iFld", "1",
                  IRNode.STORE, "2",
                  IRNode.STORE_OF_CLASS, "IRExample", "2"})
    public void goodCounts() {
        iFld = 42; // No load, store to iFld in class IRExample
        iFld2 = 42; // No load, store to iFld2 in class IRExample
    }

    // @IR rules can also specify both type of checks in the same rule.
    @Test
    @IR(failOn = {IRNode.ALLOC,
                  IRNode.LOOP},
        counts = {IRNode.LOAD, "2",
                  IRNode.LOAD_OF_FIELD, "iFld2", "1",
                  IRNode.LOAD_OF_CLASS, "IRExample", "2"})
    public void mixFailOnAndCounts() {
        iFld = iFld2;
        iFld2 = iFld3;
    }

    // Rules on compile phases.
    @Test
    // Apply IR matching on default phase which is PrintOptoAssembly for ALLOC and PrintIdeal for LOAD
    @IR(failOn = {IRNode.ALLOC, IRNode.LOAD})
    // Apply IR matching on compile phase AFTER_PARSING.
    @IR(failOn = {IRNode.ALLOC, IRNode.LOAD}, phase = CompilePhase.AFTER_PARSING)
    // Apply IR matching on compile phase AFTER_PARSING and CCP1.
    @IR(counts = {IRNode.ALLOC, "0", IRNode.STORE_I, "1"}, phase = {CompilePhase.AFTER_PARSING, CompilePhase.CCP1})
    // Apply IR matching on compile phase BEFORE_MATCHING by using a custom regex. In this case, a compile phase must
    // be specified as there is no default compile phase for user defined regexes.
    @IR(failOn = "LoadI", phase = CompilePhase.BEFORE_MATCHING)
    public void compilePhases() {
        iFld = 42;
    }
}

class FailingExamples {
    int iFld2, iFld3;
    IRExample irExample = new IRExample();

    // Rules with failOn constraint which all fail.
    @Test
    @IR(failOn = IRNode.STORE)
    @IR(failOn = {IRNode.STORE, IRNode.LOOP}) // LOOP regex not found but STORE regex, letting the rule fail
    @IR(failOn = {IRNode.LOOP, IRNode.STORE}) // Order does not matter
    @IR(failOn = {IRNode.STORE, IRNode.LOAD}) // STORE and LOAD regex found, letting the rule fail
    @IR(failOn = {"LoadI"}, phase = CompilePhase.PRINT_IDEAL) // LoadI can be found in PrintIdeal letting the rule fail
    // Store to iFld, store, and store to class IRExample, all 3 regexes found letting the rule fail
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld", IRNode.STORE, IRNode.STORE_OF_CLASS, "IRExample"})
    public void badFailOn() {
        irExample.iFld = iFld2; // Store to iFld in class IRExample, load from iFld2
    }


    // Rules with counts constraint which all fail.
    @Test
    @IR(counts = {IRNode.STORE, "1"}) // There are 2 stores
    @IR(counts = {IRNode.LOAD, "0"}) // Equivalent to failOn = IRNode.LOAD, there is 1 load
    @IR(counts = {IRNode.STORE, "1",
                  IRNode.LOAD, "1"}) // First constraint holds (there is 1 load) but 2 stores, letting this rule fail
    @IR(counts = {IRNode.LOAD, "1",
                  IRNode.STORE, "1"}) // Order does not matter
    @IR(counts = {"some regex that does not occur", "1"},
        phase = CompilePhase.PRINT_IDEAL) // user-defined regex does not occur once in PrintIdeal output
    // Rule with special configurable IR nodes. All IR nodes with a "_OF" postfix expect a second string specifying an
    // additional required information.
    @IR(counts = {IRNode.STORE_OF_FIELD, "iFld", "2", // Only one store to iFld
                  IRNode.LOAD, "2", // Only 1 load
                  IRNode.STORE_OF_CLASS, "Foo", "1"}) // No store to class Foo
    public void badCounts() {
        irExample.iFld = iFld3; // No load, store to iFld in class IRExample
        iFld2 = 42; // No load, store to iFld2 in class IRExample
    }

    // Rules on compile phases which fail
    @Test
    // The compile phase BEFORE_STRINGOPTS will not be emitted for this method, resulting in an IR matching failure.
    @IR(failOn = IRNode.LOAD_I, phase = CompilePhase.BEFORE_STRINGOPTS)
    // The compile phase BEFORE_STRINGOPTS and AFTER_PARSING will not be emitted for this method. The other phases will
    // match on STORE_I. This results in a failure for each compile phase. The compile phase input will be sorted by
    // the order in which the compile phases are sorted in the enum class CompilePhase.
    @IR(failOn = IRNode.STORE_I, phase = {CompilePhase.BEFORE_MATCHING, CompilePhase.CCP1, CompilePhase.BEFORE_STRINGOPTS,
                                         CompilePhase.AFTER_CLOOPS, CompilePhase.AFTER_PARSING})
    // Apply IR matching on compile phase AFTER_PARSING and ITER_GVN1. After parsing, we have 2 stores and we fail
    // for compile phase AFTER_PARSING. However, once IGVN is over, we were able to optimize one store away, leaving
    // us with only 1 store and we do not fail with compile phase ITER_GVN1.
    @IR(counts = {IRNode.STORE_I, "1"},
        phase = {CompilePhase.AFTER_PARSING, // Fails
                 CompilePhase.ITER_GVN1}) // Works
    public void badCompilePhases() {
        iFld2 = 42;
        iFld2 = 42 + iFld2; // Removed in first IGVN iteration and replaced by iFld2 = 84
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"}) // Should work but since we do not invoke the method enough times, we fail.
    public void testNotCompiled() {
        iFld2 = 34;
    }

    // RunMode.STANDALONE gives the user full control over how the associated @Test method is compiled: The IR framework
    // only invokes this @Run method once, without any additional warm-up iterations, and does NOT initiate a compilation.
    // This is entirely left to the @Run method to do. Since we invoke the @Test method testNotCompiled() only once, this
    // is not enough to normally trigger a C2 compilation. IR matching fails since there is no C2 compilation output.
    // To fix that, we would need to invoke testNotCompiled() enough times to trigger a C2 compilation.
    @Run(test = "testNotCompiled", mode = RunMode.STANDALONE)
    public void badStandAloneNotCompiled() {
        testNotCompiled();
    }
}
