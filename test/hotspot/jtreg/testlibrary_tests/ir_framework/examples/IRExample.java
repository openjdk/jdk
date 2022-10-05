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
 * Multiple {@link IR @IR} rules can be specified at {@link Test @Test} methods. The IR framework performs a regex-based
 * IR matching on the ideal graph compile phase (printed by compile command PrintIdealPhase) or PrintOptoAssembly (also
 * treated as a compile phase and specified in {@link CompilePhase}) output of a method.
 *
 * <p>
 * To perform a matching on a C2 IR node, the user can directly use the public static final strings defined in
 * {@link IRNode} which mostly represent either a real IR node or group of IR nodes as found in the C2 compiler as node
 * classes (there are rare exceptions). These strings represent special placeholder strings (referred to as
 * "IR placeholder string" or just "IR node") which are replaced by the framework by regexes depending on which compile
 * phases (defined with {@link IR#phase()) the IR rule should be applied on. If an IR node placeholder string cannot be
 * used for a specific compile phase (e.g. the IR node does not exist in this phase), a format violation will be reported.
 *
 * <p>
 * The exact mapping from an IR node placeholder string to regexes for different compile phases together with a default
 * phase (see next section) is defined in a static block directly below the corresponding IR node placeholder string in
 * {@link IRNode}.
 *
 * <p>
 * The user can also directly specify user-defined regexes in combination with a required compile phase (there is no
 * default compile phase known by the framework for custom regexes). If such a user-defined regex represents a not yet
 * supported C2 IR node, it is highly encouraged to directly add a new IR node placeholder string definition to
 * {@link IRNode} for it instead together with a static regex mapping block.
 *
 * <p>
 * When not specifying any compile phase with {@link IR#phase()} (or explicitly setting {@link CompilePhase#DEFAULT}),
 * the framework will perform IR matching on a default compile phase which for most IR nodes is
 * {@link CompilePhase#PRINT_IDEAL} (output of flag -XX:+PrintIdeal, the state of the machine independent ideal graph
 * after applying optimizations). The default phase for each IR node is defined in the static regex mapping block below
 * each IR node placeholder string in {@link IRNode}.
 *
 * <p>
 * The {@link IR @IR} annotations provides two kinds of checks:
 * <ul>
 *     <li><p>{@link IR#failOn}: A list of one or more IR nodes/user-defined regexes which are not allowed to occur in
 *                               any compilation output of any compile phase.</li>
 *     <li><p>{@link IR#counts}: A list of one or more "IR node/user-defined regex - counter" pairs which specify how
 *                               often each IR node/user-defined regex should be matched on the compilation output of
 *                               each compile phase.</li>
 * </ul>
 * <p>
 *
 * One might also want to restrict the application of certain @IR rules depending on the used flags in the test VM.
 * These could be flags defined by the user or by JTreg. In the latter case, the flags must be whitelisted in
 * {@link TestFramework#JTREG_WHITELIST_FLAGS} (i.e. have no unexpected impact on the IR except if the flag simulates a
 * specific machine setup like UseAVX={1,2,3} etc.) to enable an IR verification by the framework.
 * The @IR rules thus have an option to restrict their application:
 * <ul>
 *     <li><p>{@link IR#applyIf}:    Only apply a rule if a flag has the specified value/range of values.</li>
 *     <li><p>{@link IR#applyIfNot}: Only apply a rule if a flag has NOT a specified value/range of values
 *                                   (inverse of applyIf).</li>
 *     <li><p>{@link IR#applyIfAnd}: Only apply a rule if ALL flags have the specified value/range of values.</li>
 *     <li><p>{@link IR#applyIfOr}:  Only apply a rule if AT LEAST ONE flag has the specified value/range of values.</li>
 * </ul>
 * <p>
 *
 * An IR verification cannot always be performed. Certain VM flags explicitly disable IR verification, change the IR
 * shape in unexpected ways letting IR rules fail or even make IR verification impossible:
 * <ul>
 *     <li><p>-DVerifyIR=false is used</li>
 *     <li><p>The test is run with a non-debug build</li>
 *     <li><p>-Xcomp, -Xint, -XX:-UseCompile, -XX:CompileThreshold, -DFlipC1C2=true, or -DExcludeRandom=true are used.</li>
 *     <li><p>JTreg specifies non-whitelisted flags as VM and/or Javaoptions (could change the IR in unexpected ways).</li>
 * </ul>
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
    @IR(failOn = IRNode.LOAD) // 1 default regex
    @IR(failOn = {IRNode.LOAD, IRNode.LOOP}) // 2 default regexes
    @IR(failOn = {IRNode.LOAD, "some regex that does not occur"}, // 1 default regex and a user-defined regex
        phase = CompilePhase.PRINT_IDEAL)
    // Rule with special configurable default regexes. All regexes with a "_OF" postfix in IR node expect a
    // second string specifying an additional required information.
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
    @IR(counts = {IRNode.STORE, "2"}) // 1 default regex
    @IR(counts = {IRNode.LOAD, "0"}) // equivalent to failOn = IRNode.LOAD
    @IR(counts = {IRNode.STORE, "2",
                  IRNode.LOAD, "0"}) // 2 default regexes
    @IR(counts = {IRNode.STORE, "2",
                  "some regex that does not occur", "0"}, // 1 default regex and a user-defined regex
        phase = CompilePhase.PRINT_IDEAL)
    // Rule with special configurable default regexes. All regexes with a "_OF" postfix in IR node expect a
    // second string specifying an additional required information.
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
    // be specified as there is no default compiler phase for user defined regexes.
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
    // Rule with special configurable default regexes. All regexes with a "_OF" postfix in IR node expect a
    // second string specifying an additional required information.
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
}
