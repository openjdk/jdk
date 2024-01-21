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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class TestIRMatching {

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
        System.setOut(ps);
        System.setErr(psErr);
        runWithArguments(AndOr1.class, "-XX:TLABRefillWasteFraction=52", "-XX:+UsePerfData", "-XX:+UseTLAB");
        runWithArguments(CountComparisons.class, "-XX:TLABRefillWasteFraction=50");
        runWithArguments(GoodCount.class, "-XX:TLABRefillWasteFraction=50");
        runWithArguments(MultipleFailOnGood.class, "-XX:TLABRefillWasteFraction=50");

        runCheck(new String[] {"-XX:TLABRefillWasteFraction=50", "-XX:+UsePerfData", "-XX:+UseTLAB"}, BadFailOnConstraint.create(AndOr1.class, "test1(int)", 1, "CallStaticJava"));
        runCheck(new String[] {"-XX:TLABRefillWasteFraction=50", "-XX:-UsePerfData", "-XX:+UseTLAB"}, BadFailOnConstraint.create(AndOr1.class, "test2()", 1, "CallStaticJava"));

        String[] allocMatches = { "MyClass", "wrapper for: _new_instance_Java" };
        runCheck(BadFailOnConstraint.create(MultipleFailOnBad.class, "fail1()", 1, 1, "Store"),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail1()", 1,  3, "Store"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail1()", 1,  2, 4),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail2()", 1,  1),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail2()", 1,  2, "CallStaticJava"),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail3()", 1,  2, "Store"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail3()", 1,  1, 3),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail4()", 1,  1, "Store"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail4()", 1,  2, 3),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail5()", 1,  1, "Store"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail5()", 1,  2, 3),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail6()", 1,  1),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail6()", 1,  2, allocMatches),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail6()", 1,  3, "CallStaticJava"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail7()", 1,  1),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail7()", 1,  2, allocMatches),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail8()", 1,  1),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail8()", 1,  2, allocMatches),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail9()", 1,  1, "Store"),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail9()", 1,  2, "CallStaticJava"),
                 BadFailOnConstraint.create(MultipleFailOnBad.class, "fail10()", 1,  1, "Store", "iFld"),
                 GoodFailOnRegexConstraint.create(MultipleFailOnBad.class, "fail10()", 1,  2, 3)
        );

        runCheck(BadCountsConstraint.create(BadCount.class, "bad1()", 1, 2, "Load"),
                 GoodCountsConstraint.create(BadCount.class, "bad1()", 2),
                 GoodCountsConstraint.create(BadCount.class, "bad2()", 1),
                 BadCountsConstraint.create(BadCount.class, "bad2()", 2,  2, "Store"),
                 BadCountsConstraint.create(BadCount.class, "bad3()", 1,  2, "Load"),
                 BadCountsConstraint.create(BadCount.class, "bad3()", 2,  2, "Store")
        );

        runCheck(GoodRuleConstraint.create(Calls.class, "calls()", 1),
                 BadFailOnConstraint.create(Calls.class, "calls()", 2, 1, "CallStaticJava", "dontInline"),
                 BadFailOnConstraint.create(Calls.class, "calls()", 2, 2, "CallStaticJava", "dontInline"),
                 GoodRuleConstraint.create(Calls.class, "calls()", 3)
        );

        String[] allocArrayMatches = { "MyClass", "wrapper for: _new_array_Java"};
        runCheck(BadFailOnConstraint.create(AllocArray.class, "allocArray()", 1, allocArrayMatches),
                 BadFailOnConstraint.create(AllocArray.class, "allocArray()", 2,  allocArrayMatches),
                 GoodFailOnConstraint.create(AllocArray.class, "allocArray()", 3),
                 GoodFailOnConstraint.create(AllocArray.class, "allocArray()", 4),
                 BadFailOnConstraint.create(AllocArray.class, "allocArray()", 5,  allocArrayMatches)
        );

        runCheck(GoodRuleConstraint.create(RunTests.class, "good1()", 1),
                 GoodRuleConstraint.create(RunTests.class, "good1()", 2),
                 GoodRuleConstraint.create(RunTests.class, "good2()", 1),
                 GoodRuleConstraint.create(RunTests.class, "good2()", 2),
                 GoodRuleConstraint.create(RunTests.class, "good3(int)", 1),
                 BadCountsConstraint.create(RunTests.class, "bad1(int)", 1, 0),
                 BadFailOnConstraint.create(RunTests.class, "bad1(int)", 2, "Load")
        );

        runCheck(new String[] {"-XX:+IgnoreUnrecognizedVMOptions", "-XX:-UseCompressedClassPointers"},
                 BadFailOnConstraint.create(Loads.class, "load()", 1, 1, "Load"),
                 BadFailOnConstraint.create(Loads.class, "load()", 1, 3, "LoadI"),
                 BadCountsConstraint.create(Loads.class, "load()", 1, 1, 0),
                 BadCountsConstraint.create(Loads.class, "load()", 1, 2, 1,"Load"),
                 GoodRuleConstraint.create(Loads.class, "load()", 2),
                 GoodFailOnConstraint.create(Loads.class, "load()", 3),
                 BadCountsConstraint.create(Loads.class, "load()", 3, 2, 2,"Store"),
                 BadFailOnConstraint.create(Loads.class, "load()", 4, 2, "Store"),
                 BadFailOnConstraint.create(Loads.class, "load()", 5, "Load"),
                 BadFailOnConstraint.create(Loads.class, "load()", 6, "Load"),
                 BadFailOnConstraint.create(Loads.class, "load()", 7, "Load"),
                 GoodRuleConstraint.create(Loads.class, "load()", 8),
                 GoodRuleConstraint.create(Loads.class, "load()", 9),
                 GoodRuleConstraint.create(Loads.class, "load()", 10),
                 BadFailOnConstraint.create(Loads.class, "loadKlass()", 1),
                 BadCountsConstraint.create(Loads.class, "loadKlass()", 2, 2,"Field")
                 );

        // Loops
        runCheck(BadFailOnConstraint.create(Loops.class, "loop()", 1, "Loop"),
                 GoodRuleConstraint.create(Loops.class, "loop()", 2),
                 GoodRuleConstraint.create(Loops.class, "loop()", 3),
                 GoodRuleConstraint.create(Loops.class, "countedLoop()", 1),
                 BadFailOnConstraint.create(Loops.class, "countedLoop()", 2, "CountedLoop"),
                 GoodRuleConstraint.create(Loops.class, "countedLoop()", 3),
                 BadFailOnConstraint.create(Loops.class, "loopAndCountedLoop()", 1, "Loop"),
                 BadFailOnConstraint.create(Loops.class, "loopAndCountedLoop()", 2, "CountedLoop"),
                 GoodRuleConstraint.create(Loops.class, "loopAndCountedLoop()", 3),
                 GoodRuleConstraint.create(Loops.class, "countedLoopMain()", 1),
                 BadFailOnConstraint.create(Loops.class, "countedLoopMain()", 2, "CountedLoop"),
                 BadFailOnConstraint.create(Loops.class, "countedLoopMain()", 3, "CountedLoop", "main"),
                 GoodRuleConstraint.create(Loops.class, "countedLoopUnrolled()", 1),
                 GoodRuleConstraint.create(Loops.class, "countedLoopUnrolled()", 2),
                 GoodRuleConstraint.create(Loops.class, "countedLoopUnrolled()", 3)
        );

        // Traps
        runCheck(GoodRuleConstraint.create(Traps.class, "noTraps()", 1),
                 BadFailOnConstraint.create(Traps.class, "noTraps()", 2, "Store", "iFld"),
                 GoodRuleConstraint.create(Traps.class, "noTraps()", 3),
                 BadFailOnConstraint.create(Traps.class, "predicateTrap()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "predicateTrap()", 2, "CallStaticJava", "uncommon_trap", "predicate"),
                 GoodRuleConstraint.create(Traps.class, "predicateTrap()", 3),
                 GoodRuleConstraint.create(Traps.class, "predicateTrap()", 4),
                 BadFailOnConstraint.create(Traps.class, "nullCheck()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "nullCheck()", 2, "CallStaticJava", "uncommon_trap", "null_check"),
                 BadFailOnConstraint.create(Traps.class, "nullCheck()", 3, "uncommon_trap", "class_check"),
                 GoodRuleConstraint.create(Traps.class, "nullCheck()", 4),
                 BadFailOnConstraint.create(Traps.class, "nullAssert()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "nullAssert()", 2, "CallStaticJava", "uncommon_trap", "null_assert"),
                 BadFailOnConstraint.create(Traps.class, "nullAssert()", 3, "CallStaticJava", "uncommon_trap", "null_check"),
                 GoodRuleConstraint.create(Traps.class, "nullAssert()", 4),
                 BadFailOnConstraint.create(Traps.class, "unstableIf(boolean)", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "unstableIf(boolean)",  2, "CallStaticJava", "uncommon_trap", "unstable_if"),
                 GoodRuleConstraint.create(Traps.class, "unstableIf(boolean)", 3),
                 BadFailOnConstraint.create(Traps.class, "classCheck()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "classCheck()", 2, "CallStaticJava", "uncommon_trap", "class_check"),
                 BadFailOnConstraint.create(Traps.class, "classCheck()", 3, "CallStaticJava", "uncommon_trap", "null_check"),
                 GoodRuleConstraint.create(Traps.class, "classCheck()", 4),
                 BadFailOnConstraint.create(Traps.class, "rangeCheck()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(Traps.class, "rangeCheck()", 2, "CallStaticJava", "uncommon_trap", "range_check"),
                 BadFailOnConstraint.create(Traps.class, "rangeCheck()", 3, "CallStaticJava", "uncommon_trap", "null_check"),
                 GoodRuleConstraint.create(Traps.class, "rangeCheck()", 4),
                 BadFailOnConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 1, "CallStaticJava", "uncommon_trap"),
                 WhiteBox.getWhiteBox().isJVMCISupportedByGC() ?
                    BadFailOnConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 2, "CallStaticJava", "uncommon_trap", "intrinsic_or_type_checked_inlining")
                    : GoodRuleConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 2),
                 BadFailOnConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 3, "CallStaticJava", "uncommon_trap", "intrinsic"),
                 BadFailOnConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 4, "CallStaticJava", "uncommon_trap", "null_check"),
                 GoodRuleConstraint.create(Traps.class, "instrinsicOrTypeCheckedInlining()", 5)
        );


        runCheck(new String[] {"-XX:+BailoutToInterpreterForThrows"},
                 BadFailOnConstraint.create(UnhandledTrap.class, "unhandled()", 1, "CallStaticJava", "uncommon_trap"),
                 BadFailOnConstraint.create(UnhandledTrap.class, "unhandled()", 2, "CallStaticJava", "uncommon_trap", "unhandled"),
                 GoodRuleConstraint.create(UnhandledTrap.class, "unhandled()", 3)
        );

        runCheck(BadFailOnConstraint.create(ScopeObj.class, "scopeObject()", 1, "ScObj"));
        runCheck(BadFailOnConstraint.create(Membar.class, "membar()", 1, "MemBar"));

        String cmp;
        if (Platform.isPPC() || Platform.isX86()) {
            cmp = "CMP";
        } else if (Platform.isS390x()){
            cmp = "CLFI";
        } else {
            cmp = "cmp";
        }
        runCheck(BadFailOnConstraint.create(CheckCastArray.class, "array(java.lang.Object[])", 1, cmp, "precise"),
                 BadFailOnConstraint.create(CheckCastArray.class, "array(java.lang.Object[])", 2, 1,cmp, "precise", "MyClass"),
                 BadFailOnConstraint.create(CheckCastArray.class, "array(java.lang.Object[])", 2, 2,cmp, "precise", "ir_framework/tests/MyClass"),
                 GoodFailOnConstraint.create(CheckCastArray.class, "array(java.lang.Object[])", 3),
                 Platform.isS390x() ? // There is no checkcast_arraycopy stub for C2 on s390
                     GoodFailOnConstraint.create(CheckCastArray.class, "arrayCopy(java.lang.Object[],java.lang.Class)", 1)
                     : BadFailOnConstraint.create(CheckCastArray.class, "arrayCopy(java.lang.Object[],java.lang.Class)", 1, "checkcast_arraycopy")
        );

        try {
            runWithArgumentsFail(CompilationOutputOfFails.class);
            Asserts.fail("Should have thrown exception");
        } catch (IRViolationException e) {
            try {
                StringBuilder failures = new StringBuilder();
                System.out.flush();
                String output = baos.toString();
                baos.reset();
                Pattern pattern = Pattern.compile(compilationPrefix() + ".*both\\d.*\\R> Phase \""
                                                  + CompilePhase.PRINT_IDEAL.getName()
                                                  + "\":(?:(?!PrintOpto|" + compilationPrefix()
                                                  + ")[\\S\\s])+PrintOptoAssembly");
                Matcher matcher = pattern.matcher(output);
                long bothCount = matcher.results().count();
                if (bothCount != 7L) {
                    failures.append("- Could not find all both() methods, expected 7 but found ").append(bothCount)
                            .append(System.lineSeparator());
                }
                pattern = Pattern.compile(compilationPrefix() + ".*ideal\\d.*\\R> Phase \""
                                          + CompilePhase.PRINT_IDEAL.getName()
                                          + "\":(?:(?!" + compilationPrefix() + ")[\\S\\s])+");
                matcher = pattern.matcher(output);
                int count = 0;
                while (matcher.find()) {
                    String match = matcher.group();
                    if (match.contains("PrintOptoAssembly")) {
                        failures.append("Cannot contain opto assembly: ").append(System.lineSeparator()).append(match);
                    }
                    count++;
                }
                if (count != 7) {
                    failures.append("- Could not find all ideal() methods, expected 7 but found ").append(count)
                            .append(System.lineSeparator());
                }
                pattern = Pattern.compile(compilationPrefix() + ".*opto\\d.*\\R> Phase \"PrintOptoAssembly\":(?:(?!"
                                          + compilationPrefix()  + ")[\\S\\s])+");
                matcher = pattern.matcher(output);
                count = 0;
                while (matcher.find()) {
                    String match = matcher.group();
                    if (match.contains("PrintIdeal")) {
                        failures.append("Cannot contain print assembly: ").append(System.lineSeparator()).append(match);
                    }
                    count++;
                }
                if (count != 7) {
                    failures.append("- Could not find all opto() methods, expected 7 but found ").append(count).append(System.lineSeparator());
                }
                if (!failures.isEmpty()) {
                    addException(new RuntimeException(failures.toString()));
                }
            } catch (Exception e1) {
                addException(e1);
            }
        } catch (Exception e) {
            addException(e);
        }

        runWithArguments(FlagComparisons.class, "-XX:TLABRefillWasteFraction=50");
        System.out.flush();
        String output = baos.toString();
        findIrIds(output, "testMatchAllIf50", 1, 22);
        findIrIds(output, "testMatchNoneIf50", -1, -1);

        runWithArguments(FlagComparisons.class, "-XX:TLABRefillWasteFraction=49");
        System.out.flush();
        output = baos.toString();
        findIrIds(output, "testMatchAllIf50", 5, 7, 14, 19);
        findIrIds(output, "testMatchNoneIf50", 1, 4, 9, 11, 18, 23);

        runWithArguments(FlagComparisons.class, "-XX:TLABRefillWasteFraction=51");
        System.out.flush();
        output = baos.toString();
        findIrIds(output, "testMatchAllIf50", 8, 13, 20, 22);
        findIrIds(output, "testMatchNoneIf50", 5, 8, 12, 17, 21, 23);
        System.setOut(oldOut);
        System.setErr(oldErr);

        if (!exceptions.isEmpty()) {
            System.err.println("TestIRMatching failed with " + exceptions.size() + " exception(s):");
            int i = 1;
            System.err.println("************************");
            for (Map.Entry<Exception, String> entry : exceptions.entrySet()) {
                System.err.println("***** Exception " + String.format("%02d", i++) +" *****");
                System.err.println("************************");

                Exception e = entry.getKey();
                e.printStackTrace(System.err);
                System.err.println();
                System.err.println("===== OUTPUT ======");
                System.err.println(entry.getValue());
                System.err.println("MESSAGE: " + e.getMessage());
                System.err.println("************************");
            }
            i = 1;
            System.err.println("====================================");
            System.err.println("********************");
            System.err.println("***** OVERVIEW *****");
            System.err.println("********************");
            for (Map.Entry<Exception, String> entry : exceptions.entrySet()) {
                System.err.print((i++) + ") ");
                entry.getKey().printStackTrace(System.err);
                System.err.println("********************");
            }
            throw new RuntimeException("TestIRMatching failed with " + exceptions.size() + " exception(s) - check stderr and stdout");
        }
    }

    private static void runFramework(TestFramework framework) {
        baos.reset();
        baosErr.reset();
        framework.start();
    }

    private static void runWithArguments(Class<?> clazz, String... args) {
        try {
            runFramework(new TestFramework(clazz).addFlags(args));
        } catch (Exception e) {
            addException(e);
        }
    }

    private static void runWithArgumentsFail(Class<?> clazz, String... args) {
        runFramework(new TestFramework(clazz).addFlags(args));
    }

    private static void runCheck(String[] args , Constraint... constraints) {
        try {
            TestFramework framework = new TestFramework(constraints[0].getKlass()); // All constraints have the same class.
            if (args != null) {
                framework.addFlags(args);
            }
            runFramework(framework);
            Asserts.fail("Should have thrown exception");
        } catch (IRViolationException e) {
            checkConstraints(e, constraints);
        } catch (Exception e) {
            addException(e);
        }
    }

    private static void runCheck(Constraint... constraints) {
        runCheck(null, constraints);
    }

    private static void checkConstraints(IRViolationException e, Constraint[] constraints) {
        String message = e.getExceptionInfo();
        try {
            for (Constraint constraint : constraints) {
                constraint.checkConstraint(e);
            }
        } catch (Exception e1) {
            System.out.println(e.getCompilations());
            System.out.println(message);
            addException(e1);
        }
    }

    private static String compilationPrefix() {
        return "\\d\\) Compilation";
    }

    private static void findIrIds(String output, String method, int... numbers) {
        StringBuilder builder = new StringBuilder();
        builder.append(method);
        for (int i = 0; i < numbers.length; i+=2) {
            int start = numbers[i];
            int endIncluded = numbers[i + 1];
            for (int j = start; j <= endIncluded; j++) {
                builder.append(",");
                builder.append(j);
            }
        }
        if (!output.contains(builder.toString())) {
            addException(new RuntimeException("Could not find encoding: \"" + builder + System.lineSeparator()));
        }
    }
}

class AndOr1 {
    @Test
    @Arguments(Argument.DEFAULT)
    @IR(applyIfAnd = {"UsePerfData", "true", "TLABRefillWasteFraction", "50", "UseTLAB", "true"}, failOn = {IRNode.CALL})
    public void test1(int i) {
        dontInline();
    }

    @Test
    @IR(applyIfOr = {"UsePerfData", "false", "TLABRefillWasteFraction", "51", "UseTLAB", "false"}, failOn = {IRNode.CALL})
    public void test2() {
        dontInline();
    }

    @DontInline
    private void dontInline() {
    }
}

class MultipleFailOnGood {
    private int iFld;
    private MyClassSub myClassSub = new MyClassSub();

    @Test
    @IR(applyIf = {"TLABRefillWasteFraction", "50"}, failOn = {IRNode.STORE, IRNode.CALL})
    @IR(failOn = {IRNode.STORE, IRNode.CALL})
    @IR(applyIfOr = {"TLABRefillWasteFraction", "99", "TLABRefillWasteFraction", "100"}, failOn = {IRNode.LOOP, IRNode.CALL}) // Not applied
    public void good1() {
        forceInline();
    }

    @Test
    @IR(failOn = {IRNode.STORE, IRNode.CALL})
    @IR(applyIfNot = {"TLABRefillWasteFraction", "20"}, failOn = {IRNode.ALLOC})
    @IR(applyIfNot = {"TLABRefillWasteFraction", "< 100"}, failOn = {IRNode.ALLOC_OF, "Test"})
    public void good2() {
        forceInline();
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "Test", IRNode.CALL})
    @IR(applyIfNot = {"TLABRefillWasteFraction", "20"}, failOn = {IRNode.ALLOC})
    @IR(applyIfNot = {"TLABRefillWasteFraction", "< 100"}, failOn = {IRNode.ALLOC_OF, "Test"})
    public void good3() {
        forceInline();
    }

    @Test
    @IR(failOn = {IRNode.CALL, IRNode.STORE_OF_CLASS, "UnknownClass"})
    public void good4() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_FIELD, "xFld", IRNode.CALL})
    public void good5() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "MyClass"}) // Needs exact match to fail
    public void good6() {
        myClassSub.iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "MyClassSub"}) // Static write is with Class and not MySubClass
    public void good7() {
        MyClassSub.iFldStatic = 42;
    }

    @ForceInline
    private void forceInline() {}
}

class MultipleFailOnBad {
    private int iFld;
    private int myInt;
    private MyClassEmpty myClass;

    @Test
    @IR(failOn = {IRNode.STORE, IRNode.CALL, IRNode.STORE_I, IRNode.LOOP})
    public void fail1() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE, IRNode.CALL})
    public void fail2() {
        dontInline();
    }

    @Test
    @IR(failOn = {IRNode.CALL, IRNode.STORE_OF_CLASS, "MultipleFailOnBad", IRNode.ALLOC})
    public void fail3() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "ir_framework/tests/MultipleFailOnBad", IRNode.CALL, IRNode.ALLOC})
    public void fail4() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld", IRNode.CALL, IRNode.ALLOC})
    public void fail5() {
        iFld = 42;
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "MyClassEmpty", IRNode.ALLOC, IRNode.CALL})
    public void fail6() {
        myClass = new MyClassEmpty();
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "UnknownClass", IRNode.ALLOC_OF, "MyClassEmpty"})
    public void fail7() {
        myClass = new MyClassEmpty();
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_CLASS, "UnknownClass", IRNode.ALLOC_OF, "ir_framework/tests/MyClassEmptySub"})
    public void fail8() {
        myClass = new MyClassEmptySub();
    }

    @Test
    @IR(failOn = {IRNode.STORE, IRNode.CALL})
    public void fail9() {
        iFld = 42;
        dontInline();
    }

    @Test
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld", IRNode.CALL, IRNode.ALLOC})
    public void fail10() {
        myInt = 34;
        iFld = 42;
    }

    @DontInline
    private void dontInline() {}
}

// Called with -XX:TLABRefillWasteFraction=X.
class FlagComparisons {
    // Applies all IR rules if TLABRefillWasteFraction=50
    @Test
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "50"}) // Index 1
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "=50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "= 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " =   50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<=50"}) // Index 5
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<= 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " <=  50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">=50"}) // Index 8
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">= 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " >=  50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "> 49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " >  49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<51"}) // Index 14
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "< 51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " <  51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!=51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!= 51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " !=  51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!=49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!= 49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " !=  49"}) // Index 22
    public void testMatchAllIf50() {}

    // Applies no IR rules if TLABRefillWasteFraction=50
    @Test
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "49"}) // Index 1
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "=49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "= 49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " =  49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "51"}) // Index 5
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "=51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "= 51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " =  51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<=49"}) // Index 9
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<= 49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " <=  49"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">=51"}) // Index 12
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">= 51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " >=  51"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", ">50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "> 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " >  50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "<50"}) // Index 18
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "< 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " <  50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!=50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", "!= 50"})
    @IR(failOn = IRNode.CALL, applyIf = {"TLABRefillWasteFraction", " !=  50"}) // Index 23
    public void testMatchNoneIf50() {}
}

class CountComparisons {
    int iFld;

    @Test
    @IR(counts = {IRNode.STORE, "= 1",
                  IRNode.STORE, "=1",
                  IRNode.STORE, " = 1",
                  IRNode.STORE, "  =  1",
                  IRNode.STORE, ">= 1",
                  IRNode.STORE, ">=1",
                  IRNode.STORE, " >= 1",
                  IRNode.STORE, "  >=  1",
                  IRNode.STORE, "<= 1",
                  IRNode.STORE, "<=1",
                  IRNode.STORE, " <= 1",
                  IRNode.STORE, "  <=  1",
                  IRNode.STORE, "> 0",
                  IRNode.STORE, ">0",
                  IRNode.STORE, " > 0",
                  IRNode.STORE, "  >  0",
                  IRNode.STORE, "< 2",
                  IRNode.STORE, "<2",
                  IRNode.STORE, " < 2",
                  IRNode.STORE, "  <  2",
    })
    public void countComparison() {
        iFld = 3;
    }
}

class GoodCount {
    boolean flag;
    char cFld;
    byte bFld;
    short sFld;
    int iFld;
    long lFld;
    float fFld;
    double dFld;
    long x;

    long result;
    MyClass myClass = new MyClass();
    MyClassEmpty myClassEmpty = new MyClassEmpty();
    MyClass myClassSubPoly = new MyClassSub();
    MyClassSub myClassSub = new MyClassSub();

    @Test
    @IR(counts = {IRNode.STORE, "1", IRNode.STORE_I, "1"},
        failOn = {IRNode.STORE_B, IRNode.STORE_C, IRNode.STORE_D,
                  IRNode.STORE_F, IRNode.STORE_L})
    public void good1() {
        iFld = 3;
    }

    @Test
    @IR(counts = {IRNode.STORE, "8",
                  IRNode.STORE_B, "2", // bFld + flag
                  IRNode.STORE_C, "2", // cFld + sFld
                  IRNode.STORE_I, "1",
                  IRNode.STORE_L, "1",
                  IRNode.STORE_F, "1",
                  IRNode.STORE_D, "1"})
    public void good2() {
        flag = true;
        cFld = 'a';
        bFld = 1;
        sFld = 2;
        iFld = 3;
        lFld = 4L;
        fFld = 5.0f;
        dFld = 6.0;
    }

    @Test
    @IR(counts = {IRNode.STORE, "8", IRNode.STORE_OF_CLASS, "GoodCount", "8",
                  IRNode.STORE_B, "2", IRNode.STORE_B_OF_CLASS, "GoodCount", "2",
                  IRNode.STORE_C, "2", IRNode.STORE_C_OF_CLASS, "GoodCount", "2",
                  IRNode.STORE_I, "1", IRNode.STORE_I_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_L, "1", IRNode.STORE_L_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_F, "1", IRNode.STORE_F_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_D, "1", IRNode.STORE_D_OF_CLASS, "GoodCount", "1"})
    public void good3() {
        flag = true;
        cFld = 'a';
        bFld = 1;
        sFld = 2;
        iFld = 3;
        lFld = 4L;
        fFld = 5.0f;
        dFld = 6.0;
    }

    @Test
    @IR(counts = {IRNode.STORE, "8", IRNode.STORE_OF_CLASS, "GoodCount", "8",
                  IRNode.STORE_B, "2", IRNode.STORE_B_OF_CLASS, "GoodCount", "2",
                  IRNode.STORE_C, "2", IRNode.STORE_C_OF_CLASS, "GoodCount", "2",
                  IRNode.STORE_I, "1", IRNode.STORE_I_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_L, "1", IRNode.STORE_L_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_F, "1", IRNode.STORE_F_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_D, "1", IRNode.STORE_D_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_OF_FIELD, "lFld", "1"})
    public void good4() {
        flag = true;
        cFld = 'a';
        bFld = 1;
        sFld = 2;
        iFld = 3;
        lFld = 4L;
        fFld = 5.0f;
        dFld = 6.0;
    }

    @Test
    @IR(counts = {IRNode.STORE, "2", IRNode.STORE_I, "1", IRNode.STORE_L, "1",
                  IRNode.STORE_OF_CLASS, "GoodCount", "1", IRNode.STORE_L_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_OF_CLASS, "ir_framework/tests/MyClass", "1",
                  IRNode.STORE_I_OF_CLASS, "ir_framework/tests/MyClass", "1",
                  IRNode.STORE_OF_CLASS, "ir_framework/tests/GoodCount", "1",
                  IRNode.STORE_L_OF_CLASS, "ir_framework/tests/GoodCount", "1",
                  IRNode.STORE_OF_FIELD, "x", "2"})
    public void good5() {
        x = 3; // long
        myClass.x = 4; // int
    }

    @Test
    @IR(counts = {IRNode.STORE_OF_FIELD, "myClassEmpty", "1", IRNode.STORE_OF_CLASS, "GoodCount", "1",
                  IRNode.STORE_OF_CLASS, "/GoodCount", "1", IRNode.STORE_OF_CLASS, "MyClassEmpty", "0"},
        failOn = {IRNode.STORE_OF_CLASS, "MyClassEmpty"})
    public void good6() {
        myClassEmpty = new MyClassEmpty();
    }

    @Test
    @IR(counts = {IRNode.STORE_OF_FIELD, "iFld", "3", IRNode.STORE_OF_CLASS, "GoodCount", "0",
                  IRNode.STORE_OF_CLASS, "MyClass", "2", IRNode.STORE_OF_CLASS, "MyClassSub", "1",
                  IRNode.STORE, "3"},
        failOn = {IRNode.STORE_OF_CLASS, "GoodCount"})
    public void good7() {
        myClass.iFld = 1;
        myClassSubPoly.iFld = 2;
        myClassSub.iFld = 3;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1", IRNode.STORE, "1"})
    public void good8() {
        result = iFld;
    }


    @Test
    @IR(counts = {IRNode.LOAD, "4", IRNode.STORE, "1", IRNode.LOAD_OF_FIELD, "iFld", "2", IRNode.LOAD_OF_FIELD, "iFld2", "0",
                  IRNode.LOAD_OF_FIELD, "lFldStatic", "1", IRNode.LOAD_OF_CLASS, "GoodCount", "2", IRNode.LOAD_OF_CLASS, "MyClass", "1",
                  IRNode.STORE_OF_CLASS, "GoodCount", "1", IRNode.STORE_OF_FIELD, "result", "1",
                  IRNode.LOAD_OF_FIELD, "myClass", "1"})
    public void good9() {
        result = iFld + MyClass.lFldStatic + myClass.iFld; // 1 + 1 + 2 loads (myClass is LoadN of GoodCount and myClass.iFld a LoadI of MyClass)
    }

    @Test
    @IR(counts = {IRNode.LOAD, "8",
                  IRNode.LOAD_B, "1",
                  IRNode.LOAD_UB, "1",
                  IRNode.LOAD_S, "1",
                  IRNode.LOAD_US, "1",
                  IRNode.LOAD_I, "1",
                  IRNode.LOAD_L, "1",
                  IRNode.LOAD_F, "1",
                  IRNode.LOAD_D, "1"})
    public void good10() {
        bFld++;
        cFld++;
        sFld++;
        iFld++;
        lFld++;
        fFld++;
        dFld++;
        flag = !flag;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "8", IRNode.LOAD_OF_CLASS, "GoodCount", "8",
                  IRNode.LOAD_B, "1", IRNode.LOAD_B_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_UB, "1", IRNode.LOAD_UB_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_S, "1", IRNode.LOAD_S_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_US, "1", IRNode.LOAD_US_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_I, "1", IRNode.LOAD_I_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_L, "1", IRNode.LOAD_L_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_F, "1", IRNode.LOAD_F_OF_CLASS, "GoodCount", "1",
                  IRNode.LOAD_D, "1", IRNode.LOAD_D_OF_CLASS, "GoodCount", "1"})
    public void good11() {
        bFld++;
        cFld++;
        sFld++;
        iFld++;
        lFld++;
        fFld++;
        dFld++;
        flag = !flag;
    }
}

class BadCount {
    int iFld;
    int iFld2;
    int result;
    int result2;
    @Test
    @IR(counts = {IRNode.LOAD, "> 1000"}) // fail
    @IR(counts = {IRNode.STORE, "> 0"})
    public void bad1() {
        result = iFld;
        result2 = iFld2;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "2"}) // fail
    @IR(counts = {IRNode.STORE, "< 2"})
    public void bad2() {
        result = iFld;
        result2 = iFld2;
    }


    @Test
    @IR(counts = {IRNode.LOAD, "0"}) // fail
    @IR(counts = {IRNode.STORE, " <= 1"}) // fail
    public void bad3() {
        result = iFld;
        result2 = iFld2;
    }
}


class RunTests {
    public int iFld;

    @Test
    @IR(counts = {IRNode.STORE, "1"})
    @IR(failOn = IRNode.LOAD)
    public void good1() {
        iFld = 42;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    @IR(failOn = IRNode.STORE)
    public int good2() {
        return iFld;
    }

    @Run(test = {"good1", "good2"})
    public void runGood1() {
        good1();
        good2();
    }


    @Test
    @IR(counts = {IRNode.STORE, "1"})
    @IR(failOn = IRNode.LOAD)
    public void good3(int x) {
        iFld = x;
    }

    @Test
    @IR(counts = {IRNode.STORE, "1"})
    @IR(failOn = IRNode.LOAD)
    public int bad1(int x) {
        return iFld + x;
    }

    @Run(test = {"bad1", "good3"})
    public void run() {
        bad1(2);
        good3(4);
    }
}

class Calls {

    @Test
    @IR(counts = {IRNode.CALL, "1"})
    @IR(failOn = {IRNode.CALL_OF_METHOD, "dontInline",  // Fails
                  IRNode.STATIC_CALL_OF_METHOD, "dontInline"}) // Fails
    @IR(failOn = {IRNode.CALL_OF_METHOD, "forceInline",
                  IRNode.STATIC_CALL_OF_METHOD, "forceInline",
                  IRNode.CALL_OF_METHOD, "dontInlines",
                  IRNode.STATIC_CALL_OF_METHOD, "dontInlines",
                  IRNode.CALL_OF_METHOD, "dont",
                  IRNode.STATIC_CALL_OF_METHOD, "dont"})
    public void calls() {
        dontInline();
        forceInline();
    }

    @DontInline
    public void dontInline() {}

    @ForceInline
    public void forceInline() {}
}

class AllocArray {
    MyClass[] myClassArray;

    @Test
    @IR(failOn = {IRNode.ALLOC_ARRAY})
    @IR(failOn = {IRNode.ALLOC_ARRAY_OF, "MyClass"})
    @IR(failOn = {IRNode.ALLOC_ARRAY_OF, "MyClasss"}) // Does not fail
    @IR(failOn = {IRNode.ALLOC_ARRAY_OF, "ir_framework/tests/MySubClass"}) // Does not fail
    @IR(failOn = {IRNode.ALLOC_ARRAY_OF, "ir_framework/tests/MyClass"})
    public void allocArray() {
        myClassArray = new MyClass[2];
    }
}

class Loads {
    int iFld = 34;
    int result = 0;
    Object myClass = new MyClass();

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.LOOP, IRNode.LOAD_I}, counts = {IRNode.LOOP, "2", IRNode.LOAD, "2", IRNode.STORE, "2"})
    @IR(failOn = {IRNode.LOOP, IRNode.LOOP}, counts = {IRNode.LOOP, "0", IRNode.LOAD, "1"}) // Does not fail
    @IR(failOn = {IRNode.LOOP, IRNode.LOOP}, counts = {IRNode.LOOP, "0", IRNode.STORE, "1"})
    @IR(failOn = {IRNode.LOOP, IRNode.STORE}, counts = {IRNode.LOOP, "0", IRNode.LOAD, "1"})
    @IR(failOn = {IRNode.LOAD_OF_CLASS, "ir_framework/tests/Loads"})
    @IR(failOn = {IRNode.LOAD_OF_CLASS, "Loads"})
    @IR(failOn = {IRNode.LOAD_OF_FIELD, "iFld"})
    @IR(failOn = {IRNode.LOAD_OF_FIELD, "iFld2", IRNode.LOAD_OF_CLASS, "Load"}) // Does not fail
    @IR(failOn = {IRNode.LOAD_KLASS}) // Does not fail
    @IR(counts = {IRNode.FIELD_ACCESS, "3"}) // Does not fail
    public void load() {
        result = iFld;
        iFld = 3;
    }

    @Test
    @IR(failOn = {IRNode.LOAD_KLASS})
    @IR(counts = {IRNode.FIELD_ACCESS, "3"})
    public void loadKlass() {
        if (myClass instanceof MyClass) {
            result = 3;
        }
    }
}

class Loops {
    int limit = 1024;
    int[] iArr = new int[100];

    @DontInline
    public void dontInline() {}

    @Test
    @IR(failOn = IRNode.LOOP) // fails
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(failOn = IRNode.COUNTED_LOOP_MAIN)
    public void loop() {
        for (int i = 0; i < limit; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    @IR(failOn = IRNode.COUNTED_LOOP) // fails
    @IR(failOn = IRNode.COUNTED_LOOP_MAIN)
    public void countedLoop() {
        for (int i = 0; i < 2000; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.LOOP) // fails
    @IR(failOn = IRNode.COUNTED_LOOP) // fails
    @IR(failOn = IRNode.COUNTED_LOOP_MAIN)
    public void loopAndCountedLoop() {
        for (int i = 0; i < 2000; i++) {
            for (int j = 0; j < limit; j++) {
                dontInline();
            }
        }
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    @IR(failOn = IRNode.COUNTED_LOOP) // fails
    @IR(failOn = IRNode.COUNTED_LOOP_MAIN) // fails
    public void countedLoopMain() {
        // Cannot unroll completely -> create pre/main/post
        for (int i = 0; i < 100; i++) {
            iArr[i] = i;
        }
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(failOn = IRNode.COUNTED_LOOP_MAIN)
    public void countedLoopUnrolled() {
        // Completely unrolled -> no pre/main/post
        for (int i = 0; i < 8; i++) {
            iArr[i] = i;
        }
    }
}

class Traps {
    int number42 = 42;
    int iFld = 10;
    int[] iArr = new int[2];
    MyClass myClass = new MyClass();
    MyClassSub myClassSub = new MyClassSub();
    NotLoaded notLoaded = new NotLoaded();
    Object[] oArr = new Object[10];
    MyClass[] mArr = new MyClass[10];

    @Test
    @IR(failOn = IRNode.TRAP)
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld"}) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_CHECK_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void noTraps() {
        for (int i = 0; i < 100; i++) {
            if (i < 42) {
                // Reached, no uncommon trap
                iFld = i;
            }
        }
    }

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.PREDICATE_TRAP) // fails
    @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld"})
    @IR(failOn = {IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_CHECK_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void predicateTrap() {
        for (int i = 0; i < 100; i++) {
            if (number42 != 42) {
                // Never reached
                iFld = i;
            }
        }
    }

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.NULL_CHECK_TRAP) // fails
    @IR(failOn = IRNode.CLASS_CHECK_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void nullCheck() {
        if (myClass instanceof MyClassSub) {
            iFld = 4;
        }
    }

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.NULL_ASSERT_TRAP) // fails
    @IR(failOn = IRNode.NULL_CHECK_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public Object nullAssert() {
        return notLoaded.notLoadedFld;
    }

    @Test
    @Arguments(Argument.TRUE)
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.NULL_CHECK_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void unstableIf(boolean flag) {
        if (flag) {
            iFld++;
        } else {
            iFld--;
        }
    }

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.CLASS_CHECK_TRAP) // fails
    @IR(failOn = IRNode.NULL_CHECK_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void classCheck() {
        try {
            myClassSub = (MyClassSub) myClass;
        } catch (ClassCastException e) {
            // Expected
        }
    }

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.RANGE_CHECK_TRAP) // fails
    @IR(failOn = IRNode.NULL_CHECK_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.INTRINSIC_TRAP,
                  IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void rangeCheck() {
        iArr[1] = 3;
    }


    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP) // fails
    @IR(failOn = IRNode.INTRINSIC_TRAP) // fails
    @IR(failOn = IRNode.NULL_CHECK_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.CLASS_CHECK_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.UNHANDLED_TRAP})
    public void instrinsicOrTypeCheckedInlining() {
        System.arraycopy(oArr, 0, mArr, 0, 8);
    }
}

class UnhandledTrap {
    int iFld = 34;

    @Test
    @IR(failOn = IRNode.TRAP) // fails
    @IR(failOn = IRNode.UNHANDLED_TRAP) // fails
    @IR(failOn = {IRNode.PREDICATE_TRAP,
                  IRNode.UNSTABLE_IF_TRAP,
                  IRNode.NULL_CHECK_TRAP,
                  IRNode.NULL_ASSERT_TRAP,
                  IRNode.RANGE_CHECK_TRAP,
                  IRNode.CLASS_CHECK_TRAP})
    public void unhandled() {
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            // Expected
        }
    }
}

class ScopeObj {

    @DontInline
    public void dontInline(int i) {}

    @Test
    @IR(failOn = IRNode.SCOPE_OBJECT) // fails
    public int scopeObject() {
        MyClass myClass = new MyClass();
        for (int i = 0; i < 100; i++) {
            dontInline(myClass.iFld);
        }
        return 3;
    }
}

class Membar {
    volatile MyClass myClass;

    @Test
    @IR(failOn = IRNode.MEMBAR) // fails
    public int membar() {
        myClass = new MyClass();
        return myClass.x;
    }
}

class CheckCastArray {
    Object[] oArr = new Object[10];
    MyClass[] mArr = new MyClass[10];

    @Test
    @IR(failOn = IRNode.CHECKCAST_ARRAY) // fails
    @IR(failOn = {IRNode.CHECKCAST_ARRAY_OF, "MyClass", // fails
                  IRNode.CHECKCAST_ARRAY_OF, "ir_framework/tests/MyClass"}) // fails
    @IR(failOn = {IRNode.CHECKCAST_ARRAY_OF, "MyClasss", IRNode.CHECKCAST_ARRAY_OF, "Object"})
    public boolean array(Object[] arr) {
        return arr instanceof MyClass[];
    }

    @Run(test = "array")
    public void testArray() {
        array(oArr);
        array(mArr);
    }

    @Test
    @IR(failOn = IRNode.CHECKCAST_ARRAYCOPY) // fails
    public Object[] arrayCopy(Object[] src, Class klass) {
        return Arrays.copyOf(src, 8, klass);
    }

    @Run(test = "arrayCopy")
    public void testArrayCopy() {
        arrayCopy(mArr, MyClass[].class);
        arrayCopy(mArr, Object[].class);
        arrayCopy(mArr, MyClassEmpty[].class);
    }
}

class CompilationOutputOfFails {
    private Object obj;

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(failOn = {"call"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public void both1() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = "CountedLoop|call",
        phase = {CompilePhase.PRINT_IDEAL, CompilePhase.PRINT_OPTO_ASSEMBLY})
    public void both2() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(failOn = "call", phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public void both3() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "0"})
    @IR(counts = {"call", "0"},
        phase = {CompilePhase.PRINT_OPTO_ASSEMBLY})
    public void both4() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {"CountedLoop|call", "10"},
        phase = {CompilePhase.PRINT_IDEAL, CompilePhase.PRINT_OPTO_ASSEMBLY})
    public void both5() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "0"})
    @IR(counts = {"call", "0"}, phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public void both6() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(counts = {"call", "0"}, phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public void both7() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    public void ideal1() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(failOn = IRNode.ALLOC) // not fail
    public void ideal2() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.COUNTED_LOOP)
    @IR(counts = {IRNode.ALLOC, "0"}) // not fail
    public void ideal3() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "2"})
    public void ideal4() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.ALLOC) // not fail
    @IR(counts = {IRNode.COUNTED_LOOP, "2"})
    public void ideal5() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "0"}) // not fail
    @IR(counts = {IRNode.COUNTED_LOOP, "2"})
    public void ideal6() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "5"})
    @IR(counts = {IRNode.COUNTED_LOOP, "2"})
    public void ideal7() {
        for (int i = 0; i < 100; i++) {
            dontInline();
        }
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    public void opto1() {
        obj = new Object();
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    @IR(failOn = IRNode.STORE_F) // not fail
    public void opto2() {
        obj = new Object();
    }

    @Test
    @IR(failOn = IRNode.ALLOC)
    @IR(counts = {IRNode.COUNTED_LOOP, "1"}) // not fail
    public void opto3() {
        for (int i = 0; i < 100; i++) {
            obj = new Object();
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "0"})
    public void opto4() {
        obj = new Object();
    }

    @Test
    @IR(failOn = IRNode.STORE_F) // not fail
    @IR(counts = {IRNode.ALLOC, "0"})
    public void opto5() {
        obj = new Object();
    }

    @Test
    @IR(counts = {IRNode.STORE_F, "0"}) // not fail
    @IR(counts = {IRNode.ALLOC, "0"})
    public void opto6() {
        obj = new Object();
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "10"})
    @IR(counts = {IRNode.ALLOC, "0"})
    public void opto7() {
        obj = new Object();
    }

    @DontInline
    private void dontInline() {}
}


// Used only by class Traps
class NotLoaded {
    NotLoadedHelper notLoadedFld;
}

// Used only by class Traps
class NotLoadedHelper {}

class MyClass {
    int iFld = 3;
    int x = 5;
    static long lFldStatic;
}

class MyClassEmpty {}

class MyClassEmptySub extends MyClassEmpty {}

class MyClassSub extends MyClass {
    int iFld;
    static int iFldStatic;
}


// Base class for any kind of constraint that is used to verify if the framework reports the correct IR failures.
abstract class Constraint {
    private final Class<?> klass;
    protected final int ruleIdx;
    private final Pattern methodPattern;
    private final String classAndMethod;
    protected final Pattern irPattern;
    private final String methodName;
    protected boolean matched;

    Constraint(Class<?> klass, String methodName, int ruleIdx, Pattern irPattern) {
        this.klass = klass;
        classAndMethod = klass.getSimpleName() + "." + methodName;
        this.ruleIdx = ruleIdx;
        this.methodPattern = Pattern.compile(Pattern.quote(classAndMethod));
        this.irPattern = irPattern;
        this.methodName = methodName;
        this.matched = false;
    }

    // For good constraints only
    Constraint(Class<?> klass, String methodName, int ruleIdx) {
        this.klass = klass;
        classAndMethod = klass.getSimpleName() + "." + methodName;
        this.ruleIdx = ruleIdx;
        this.methodPattern = Pattern.compile(Pattern.quote(classAndMethod));
        this.irPattern = null;
        this.methodName = methodName;
        this.matched = false;
    }

    @Override
    public String toString() {
        return "Constraint " + getClass().getSimpleName() + ", " + errorPrefix();
    }

    public Class<?> getKlass() {
        return klass;
    }

    protected String errorPrefix() {
        return "Class " + klass.getSimpleName() + ", Method " + methodName + ", Rule " + ruleIdx;
    }

    public void checkConstraint(IRViolationException e) {
        String message = e.getExceptionInfo();
        String[] splitMethods = message.split("Method");
        for (int i = 1; i < splitMethods.length; i++) {
            String method = splitMethods[i];
            if (methodPattern.matcher(method).find()) {
                String[] splitIrRules = method.split("@IR ");
                for (int j = 1; j < splitIrRules.length; j++) {
                    String irRule = splitIrRules[j];
                    if (irRule.startsWith("rule " + ruleIdx)) {
                        checkIRRule(irRule);
                    }
                }
            }
        }
        Asserts.assertTrue(matched, this + " should have been matched");
    }

    abstract protected void checkIRRule(String irRule);
}

// Constraint for rule that does not fail.
class GoodRuleConstraint extends Constraint {

    GoodRuleConstraint(Class<?> klass, String methodName, int ruleIdx) {
        super(klass, methodName, ruleIdx);
        matched = true;
    }

    public static GoodRuleConstraint create(Class<?> klass, String methodName, int ruleIdx) {
        return new GoodRuleConstraint(klass, methodName, ruleIdx);
    }

    @Override
    protected void checkIRRule(String irRule) {
        Asserts.fail(errorPrefix() + " should not fail:" + System.lineSeparator() + irRule);
    }
}

// Constraint for rule that might fail but not with "failOn".
class GoodFailOnConstraint extends GoodRuleConstraint {

    private GoodFailOnConstraint(Class<?> klass, String methodName, int ruleIdx) {
        super(klass, methodName, ruleIdx);
    }

    public static GoodFailOnConstraint create(Class<?> klass, String methodName, int ruleIdx) {
        return new GoodFailOnConstraint(klass, methodName, ruleIdx);
    }

    @Override
    protected void checkIRRule(String irRule) {
        Asserts.assertFalse(irRule.contains("- failOn"), errorPrefix() + " should not have failed:" + System.lineSeparator() + irRule);
    }
}

// Constraint for rule that might fail but not with "counts".
class GoodCountsConstraint extends GoodRuleConstraint {

    private GoodCountsConstraint(Class<?> klass, String methodName, int ruleIdx) {
        super(klass, methodName, ruleIdx);
    }

    public static GoodCountsConstraint create(Class<?> klass, String methodName, int ruleIdx) {
        return new GoodCountsConstraint(klass, methodName, ruleIdx);
    }

    @Override
    protected void checkIRRule(String irRule) {
        Asserts.assertFalse(irRule.contains("- counts"), errorPrefix() + " should not have failed with counts:"
                                                         + System.lineSeparator() + irRule);
    }
}

// Base class for all Regex based constraint.
abstract class RegexConstraint extends Constraint {
    final String category;
    final String otherCategory;
    final int[] regexIndexes;
    final boolean isGood;
    final List<String> matches;

    RegexConstraint(Class<?> klass, String methodName, String category, boolean isGood, List<String> matches, int ruleIdx, int... regexIndexes) {
        super(klass, methodName, ruleIdx, initIRPattern(category, ruleIdx));
        this.category = category;
        this.regexIndexes = regexIndexes;
        if (category.equals("failOn")) {
            this.otherCategory = "counts";
        } else {
            Asserts.assertTrue(category.equals("counts"));
            this.otherCategory = "failOn";
        }
        this.isGood = isGood;
        this.matches = matches;
    }

    @Override
    public String toString() {
        String msg = super.toString() + ", ";
        if (regexIndexes.length > 1) {
            msg += "regexes: [" + String.join(", ", Arrays.stream(regexIndexes).mapToObj(String::valueOf).toArray(String[]::new)) + "]";
        } else {
            msg += "regex: " + regexIndexes[0];
        }
        return msg;
    }

    @Override
    protected String errorPrefix() {
        return super.errorPrefix() + " with \"" + category + "\"";
    }

    private static Pattern initIRPattern(String category, int ruleIdx) {
        if (category.equals("failOn")) {
            return Pattern.compile("rule " + ruleIdx + ":.*\\R.*- failOn: Graph contains forbidden nodes.*\\R" +
                                   ".*Constraint \\d+:.*\\R.*Matched forbidden node.*");
        } else {
            return Pattern.compile("rule " + ruleIdx + ":.*\\R.*- counts: Graph contains wrong number of nodes:\\R" +
                                   ".*Constraint \\d+:.*\\R.*Expected.*");
        }
    }

    @Override
    protected void checkIRRule(String irRule) {
        int categoryIndex = irRule.indexOf("- " + category);
        Asserts.assertTrue(categoryIndex != -1, errorPrefix() + " should have failed");

        int endIndex;
        int otherCategoryIndex = irRule.indexOf("- " + otherCategory);
        if (otherCategoryIndex == -1 || categoryIndex > otherCategoryIndex) {
            endIndex = irRule.length();
        } else {
            endIndex = otherCategoryIndex;
        }
        String categoryString = irRule.substring(irRule.indexOf("- " + category), endIndex);
        Pattern pattern;
        Matcher matcher;
        for (int regexIndex : this.regexIndexes) {
            pattern = Pattern.compile("Constraint " + regexIndex + ":.*");
            matcher = pattern.matcher(categoryString);
            if (isGood) {
                Asserts.assertFalse(matcher.find(), errorPrefix() + " failed with Constraint " + regexIndex);
                matched = true;
            } else {
                Asserts.assertTrue(matcher.find(), errorPrefix() + " should have failed at Constraint " + regexIndex);
                String[] splitRegex = categoryString.split("Constraint ");
                if (matches != null) {
                    for (int i = 1; i < splitRegex.length; i++) {
                        String regexString = splitRegex[i];
                        if (regexString.startsWith(String.valueOf(regexIndex))) {
                            // Do matching on actual match and not on regex string
                            String actualMatch = regexString.split("\\R", 2)[1];
                            Asserts.assertTrue(matches.stream().allMatch(actualMatch::contains),
                                               errorPrefix() + " could not find all matches at Constraint " + regexIndex);
                            matched = true;
                        }
                    }
                }
            }
        }
    }
}

// Base class for all good regex based constraints.
abstract class GoodRegexConstraint extends RegexConstraint {

    GoodRegexConstraint(Class<?> klass, String methodName, String category, int ruleIdx, int... regexIndexes) {
        super(klass, methodName, category, true, null, ruleIdx, regexIndexes);
    }
}

// Constraint for rule that might fail with "counts" or "failOn", but the specified regex in "failOn" does not fail.
class GoodFailOnRegexConstraint extends GoodRegexConstraint {

    private GoodFailOnRegexConstraint(Class<?> klass, String methodName, int ruleIdx, int... regexIndexes) {
        super(klass, methodName, "failOn", ruleIdx, regexIndexes);
    }


    public static GoodFailOnRegexConstraint create(Class<?> klass, String methodName, int ruleIdx, int... regexIndexes) {
        return new GoodFailOnRegexConstraint(klass, methodName, ruleIdx, regexIndexes);
    }
}


// Constraint for rule that might fail with "counts" or "failOn", but the specified regex in "counts" does not fail.
class GoodCountsRegexConstraint extends GoodRegexConstraint {

    private GoodCountsRegexConstraint(Class<?> klass, String methodName, int ruleIdx, int... regexIndexes) {
        super(klass, methodName, "counts", ruleIdx, regexIndexes);
    }


    public static GoodCountsRegexConstraint create(Class<?> klass, String methodName, int ruleIdx, int... regexIndexes) {
        return new GoodCountsRegexConstraint(klass, methodName, ruleIdx, regexIndexes);
    }
}

// Constraint for rule that fails with "failOn" and the specified regex must also fail.
class BadFailOnConstraint extends RegexConstraint {

    BadFailOnConstraint(Class<?> klass, String methodName, int ruleIdx, List<String> matches, int... regexIndexes) {
        super(klass, methodName, "failOn", false, matches, ruleIdx, regexIndexes);
    }

    public static BadFailOnConstraint create(Class<?> klass, String methodName, int ruleIdx, int regexId, String... matches) {
        return new BadFailOnConstraint(klass, methodName, ruleIdx, new ArrayList<>(Arrays.asList(matches)), regexId);
    }

    public static BadFailOnConstraint create(Class<?> klass, String methodName, int ruleIdx, String... matches) {
        return new BadFailOnConstraint(klass, methodName, ruleIdx, new ArrayList<>(Arrays.asList(matches)), 1);
    }
}

// Constraint for rule that fails with "counts" and the specified regex must also fail.
class BadCountsConstraint extends RegexConstraint {

    BadCountsConstraint(Class<?> klass, String methodName, int ruleIdx, List<String> matches, int... regexIndexes) {
        super(klass, methodName, "counts", false, matches, ruleIdx, regexIndexes);
    }

    public static BadCountsConstraint create(Class<?> klass, String methodName, int ruleIdx, int regexId, int foundCount, String... matches) {
        List<String> matchesList = getMatchesList(foundCount, matches, Arrays.asList(matches));
        return new BadCountsConstraint(klass, methodName, ruleIdx, matchesList, regexId);
    }

    public static BadCountsConstraint create(Class<?> klass, String methodName, int ruleIdx, int foundCount, String... matches) {
        List<String> matchesList = getMatchesList(foundCount, matches, Arrays.asList(matches));
        return new BadCountsConstraint(klass, methodName, ruleIdx, matchesList, 1);
    }

    private static List<String> getMatchesList(int foundCount, String[] matches, List<String> strings) {
        List<String> matchesList = new ArrayList<>();
        matchesList.add("Failed comparison: [found] " + foundCount);
        if (matches != null) {
            matchesList.addAll(strings);
        }
        return matchesList;
    }
}
