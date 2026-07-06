/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.driver.FlagVMProcess;
import compiler.lib.ir_framework.driver.TestVMProcess;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeType;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.parser.TestClassParser;
import compiler.lib.ir_framework.driver.irmatching.visitor.AcceptChildren;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;
import jdk.test.lib.Asserts;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/*
 * @test
 * @bug 8280378
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test IR matcher with different default IR nodes and compile phases.
 *          Normally, the framework should be called with driver.
 * @library /test/lib /testlibrary_tests /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=240 -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                               -XX:+WhiteBoxAPI ir_framework.tests.TestPhaseIRMatching
 */
public class TestPhaseIRMatching {

    public static void main(String[] args) {
        run(Basics.class);
        run(NoCompilationOutput.class);
        run(LoadStore.class);
    }

    private static void run(Class<?> testClass) {
        List<String> noAdditionalFlags = new ArrayList<>();
        FlagVMProcess flagVMProcess = new FlagVMProcess(testClass, noAdditionalFlags);
        List<String> testVMFlags = flagVMProcess.getTestVMFlags();
        TestVMProcess testVMProcess = new TestVMProcess(testVMFlags, testClass, null, -1, false, false);
        TestClassParser testClassParser = new TestClassParser(testClass, false);
        Matchable testClassMatchable = testClassParser.parse(testVMProcess.testVmData());
        MatchResult result = testClassMatchable.match();
        List<Failure> expectedFails = new ExpectedFailsBuilder().build(testClass);
        List<Failure> foundFailures = new FailureBuilder().build(result);
        if (!expectedFails.equals(foundFailures)) {
            reportFailure(expectedFails, foundFailures);
        }
    }

    private static void reportFailure(List<Failure> expectedFails, List<Failure> foundFailures) {
        List<Failure> originalExpected = new ArrayList<>(expectedFails);
        expectedFails.removeAll(foundFailures);
        foundFailures.removeAll(originalExpected);
        System.out.println("\"Expected Failures\" WITHOUT \"Found Failures\":");
        if (expectedFails.isEmpty()) {
            System.out.println("[]");
        } else {
            expectedFails.forEach(System.out::println);
        }
        System.out.println("\"Found Failures\" WITHOUT \"Expected Failures\":");
        if (foundFailures.isEmpty()) {
            System.out.println("[]");
        } else {
            foundFailures.forEach(System.out::println);
        }
        Asserts.fail("did not find the same failures");
    }

}

class Basics {
    int i;
    long l;
    Object obj;
    Object obj2;
    Object obj3;
    Object obj4;

    public class Helper {
        private String s;

        public Helper(String s, int i) {
            this.s = s;
        }

        public String getString() { return s; }
    }

    @Test
    @IR(failOn = IRNode.STORE, phase = {CompilePhase.DEFAULT, CompilePhase.PRINT_IDEAL})
    @ExpectedFailure(ruleId = 1, failOn = 1) // Only one failure - remove duplicated phases after mapping DEFAULT
    public void removeDuplicates() {
        i = 34;
    }

    // Test failures on ideal phases only.
    @Test
    @IR(failOn = {IRNode.STORE, IRNode.OOPMAP_WITH, "asdf", IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE, "2", IRNode.FIELD_ACCESS, "2", IRNode.STORE_I, "1"})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.PRINT_IDEAL, failOn = 1)

    @IR(failOn = {IRNode.STORE_F, IRNode.OOPMAP_WITH, "asdf", IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE, "2", IRNode.FIELD_ACCESS, "2", IRNode.STORE_I, "2"})
    @ExpectedFailure(ruleId = 2, phase = CompilePhase.PRINT_IDEAL, counts = 3)

    @IR(failOn = {IRNode.STORE, IRNode.OOPMAP_WITH, "asdf", IRNode.COUNTED_LOOP})
    @ExpectedFailure(ruleId = 3, phase = CompilePhase.PRINT_IDEAL, failOn = 1)

    @IR(counts = {IRNode.STORE, "3", IRNode.FIELD_ACCESS, "2", IRNode.COUNTED_LOOP, "2"})
    @ExpectedFailure(ruleId = 4, phase = CompilePhase.PRINT_IDEAL, counts = {1, 3})

    @IR(failOn = {IRNode.STORE, IRNode.OOPMAP_WITH, "asdf", IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE, "3", IRNode.FIELD_ACCESS, "2", IRNode.COUNTED_LOOP, "2"})
    @ExpectedFailure(ruleId = 5, phase = CompilePhase.PRINT_IDEAL, failOn = 1, counts = {1, 3})

    @IR(counts = {IRNode.STORE_I, "2"})
    @ExpectedFailure(ruleId = 6, phase = CompilePhase.PRINT_IDEAL, counts = 1)
    public void defaultOnIdeal() {
        i = 34;
        l = 34;
    }

    // Test failures on mach phases only.
    @Test
    @IR(failOn = {IRNode.STORE_F, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE_I, "1", IRNode.FIELD_ACCESS, "2", IRNode.OOPMAP_WITH, "asdf", "< 2"})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2)

    @IR(failOn = {IRNode.STORE_F, IRNode.OOPMAP_WITH, "asdf", IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE_I, "1", IRNode.FIELD_ACCESS, "1", IRNode.OOPMAP_WITH, "asdf", "< 2"})
    @ExpectedFailure(ruleId = 2, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, counts = 2)

    @IR(failOn = {IRNode.STORE_F, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP})
    @ExpectedFailure(ruleId = 3, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2)

    @IR(counts = {IRNode.STORE_I, "1", IRNode.FIELD_ACCESS, "1", IRNode.OOPMAP_WITH, "asdf", "3"})
    @ExpectedFailure(ruleId = 4, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, counts = {2, 3})

    @IR(failOn = {IRNode.STORE_F, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE_I, "1", IRNode.FIELD_ACCESS, "1", IRNode.OOPMAP_WITH, "asdf", "3"})
    @ExpectedFailure(ruleId = 5, phase = CompilePhase.PRINT_OPTO_ASSEMBLY,  failOn = 2, counts = {2, 3})

    @IR(counts = {IRNode.FIELD_ACCESS, "1"})
    @ExpectedFailure(ruleId = 6, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, counts = 1)
    public void defaultOnOptoAssembly() {
        i = 34;
        l = 34;
    }

    // Test failures on ideal and mach phases.
    @Test
    @IR(failOn = {IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE, "2", IRNode.FIELD_ACCESS, "2", IRNode.STORE_I, "1"})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.PRINT_IDEAL, failOn = 1)
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2)

    @IR(failOn = {IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP})
    @ExpectedFailure(ruleId = 2, phase = CompilePhase.PRINT_IDEAL, failOn = 1)
    @ExpectedFailure(ruleId = 2, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2)

    @IR(counts = {IRNode.STORE, "20", IRNode.FIELD_ACCESS, "1", IRNode.STORE_I, "1"})
    @ExpectedFailure(ruleId = 3, phase = CompilePhase.PRINT_IDEAL, counts = 1)
    @ExpectedFailure(ruleId = 3, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, counts = 2)

    @IR(failOn = {IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP},
        counts = {IRNode.STORE, "20", IRNode.FIELD_ACCESS, "1", IRNode.COUNTED_LOOP, "2"})
    @ExpectedFailure(ruleId = 4, phase = CompilePhase.PRINT_IDEAL, failOn = 1, counts = {1, 3})
    @ExpectedFailure(ruleId = 4, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2, counts = 2)

    @IR(failOn = {IRNode.STORE, IRNode.FIELD_ACCESS, IRNode.COUNTED_LOOP, IRNode.STORE_I},
        counts = {IRNode.STORE, "20", IRNode.FIELD_ACCESS, "1", IRNode.COUNTED_LOOP, "2", IRNode.OOPMAP_WITH, "asdf", "2"})
    @ExpectedFailure(ruleId = 5, phase = CompilePhase.PRINT_IDEAL, failOn = {1, 4}, counts = {1, 3})
    @ExpectedFailure(ruleId = 5, phase = CompilePhase.PRINT_OPTO_ASSEMBLY, failOn = 2, counts = {2, 4})
    public void defaultOnBoth() {
        i = 34;
        l = 34;
    }

    @Test
    @IR(failOn = IRNode.LOOP,
        counts = {IRNode.LOOP, "0", IRNode.LOOP, "1", IRNode.LOOP, "2"},
        phase = {CompilePhase.BEFORE_CLOOPS, CompilePhase.AFTER_CLOOPS})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.BEFORE_CLOOPS, failOn = 1, counts = {1, 2})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.AFTER_CLOOPS, counts = {2, 3})
    public int removeLoopsWithMultipleCompilations(int k) {
        if (k == 4) {
            // On first compilation, we exit here and emit an UCT for the remaining code.
            return 3;
        }
        int x = 0;
        for (int i = 0; i < 1000; i++) {}
        for (int i = 0; i < 10000; i++) {
            x++;
        }
        return x;
    }

    @Run(test = "removeLoopsWithMultipleCompilations")
    @Warmup(1)
    public void run() {
        for (int i = 0; i < 10000; i++) {
            removeLoopsWithMultipleCompilations(4);
        }
        for (int i = 0; i < 10000; i++) {
            removeLoopsWithMultipleCompilations(3);
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP}, counts = {IRNode.LOOP, "0", IRNode.LOOP, "1", IRNode.COUNTED_LOOP, "0", IRNode.COUNTED_LOOP, "1"}, phase = {CompilePhase.AFTER_CLOOPS, CompilePhase.BEFORE_MATCHING, CompilePhase.FINAL_CODE})
    @ExpectedFailure(ruleId = 1, phase = CompilePhase.AFTER_CLOOPS, failOn = {1, 2}, counts = {1, 3})
    // LOOP + COUNTED_LOOP
    @ExpectedFailure(ruleId = 1, phase = {CompilePhase.BEFORE_MATCHING, CompilePhase.FINAL_CODE}, failOn = 1, counts = {1, 4})
    // Only LOOP
    public int removeLoops2() {
        int x = 0;
        while (x < limit()) {
            x++;
        }
        for (int i = 0; i < 10000; i++) {
            x++;
        }
        return x;
    }

    @DontInline
    public int limit() {
        return 3;
    }

    @Test
    @IR(failOn = {IRNode.ALLOC, IRNode.ALLOC_ARRAY},
        counts = {IRNode.ALLOC, "0", IRNode.ALLOC_ARRAY, "0"},
        phase = {CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.CCP1, CompilePhase.DEFAULT})
    @ExpectedFailure(ruleId = 1, failOn = {1, 2}, counts = {1, 2},
                     phase = {CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.CCP1, CompilePhase.BEFORE_MACRO_EXPANSION})
    public void alloc() {
        obj = new Object();
        obj2 = new Object[1];
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "2", IRNode.ALLOC_ARRAY, "2"}, // works for all phases
        phase = {CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.CCP1, CompilePhase.DEFAULT})
    public void alloc2() {
        obj = new Object();
        obj2 = new Object[1];
        obj3 = new Object();
        obj4 = new Object[2];
    }
}

class NoCompilationOutput {

    @Test
    @IR(failOn = IRNode.STORE, phase = CompilePhase.AFTER_CLOOPS)
    @ExpectedFailure(ruleId = 1, hasCompilation = false, phase = CompilePhase.AFTER_CLOOPS)
    public void badPhase1() {}

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
    @ExpectedFailure(ruleId = 1, hasCompilation = false,
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
    public void badPhase2() {}

    @Test
    @IR(failOn = IRNode.STORE, phase = CompilePhase.DEFAULT)
    @ExpectedFailure(ruleId = -1) // No compilation found at all
    public void badMethod() {
    }

    @Run(test = "badMethod", mode = RunMode.STANDALONE)
    public void run() {
    }

}


@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedFailures {
    ExpectedFailure[] value();
}

/**
 * Define an expected failure for the @IR rule with id ruleId().
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExpectedFailures.class)
@interface ExpectedFailure {
    /**
     * Which @IR rule are we referring to?
     */
    int ruleId();

    /**
     * We expect the same failures for all the specified compile phases.
     */
    CompilePhase[] phase() default { CompilePhase.PRINT_IDEAL };

    /**
     * List of failOn constraint IDs that failed when applying the @IR rule.
     */
    int[] failOn() default {};

    /**
     * List of counts constraint IDs that failed when applying the @IR rule.
     */
    int[] counts() default {};

    /**
     * Is a compilation expected for the specified compilation phases?
     */
    boolean hasCompilation() default true;
}


class FailureBuilder implements MatchResultVisitor {
    private String methodName;
    private int ruleId;
    private CompilePhase compilePhase;
    private final Set<Failure> failures = new HashSet<>();

    public List<Failure> build(MatchResult testClassResult) {
        testClassResult.accept(this);
        return Failure.sort(failures);
    }

    @Override
    public void visitTestClass(AcceptChildren acceptChildren) {
        acceptChildren.accept(this);
    }

    @Override
    public void visitIRMethod(AcceptChildren acceptChildren, Method method, int failedIRRules) {
        methodName = method.getName();
        acceptChildren.accept(this);
    }

    @Override
    public void visitMethodNotCompiled(Method method, int failedIRRules) {
        methodName = method.getName();
        failures.add(new Failure(methodName, -1, CompilePhase.DEFAULT, CheckAttributeType.FAIL_ON, -1));
    }

    @Override
    public void visitMethodNotCompilable(Method method, int failedIRRules) {
        throw new RuntimeException("No test should bailout from compilation");
    }

    @Override
    public void visitIRRule(AcceptChildren acceptChildren, int irRuleId, IR irAnno) {
        ruleId = irRuleId;
        acceptChildren.accept(this);
    }

    @Override
    public void visitCompilePhaseIRRule(AcceptChildren acceptChildren, CompilePhase compilePhase, String compilationOutput) {
        this.compilePhase = compilePhase;
        acceptChildren.accept(this);
    }

    @Override
    public void visitNoCompilePhaseCompilation(CompilePhase compilePhase) {
        failures.add(new Failure(methodName, ruleId, compilePhase, CheckAttributeType.FAIL_ON, -1));
    }

    @Override
    public void visitCheckAttribute(AcceptChildren acceptChildren, CheckAttributeType checkAttributeType) {
        acceptChildren.accept(this);
    }

    @Override
    public void visitFailOnConstraint(FailOnConstraintFailure matchResult) {
        failures.add(new Failure(methodName, ruleId, compilePhase, CheckAttributeType.FAIL_ON,
                                 matchResult.constraintId()));
    }

    @Override
    public void visitCountsConstraint(CountsConstraintFailure matchResult) {
        failures.add(new Failure(methodName, ruleId, compilePhase, CheckAttributeType.COUNTS,
                                 matchResult.constraintId()));
    }
}

class ExpectedFailsBuilder {
    private final Set<Failure> expectedFails = new HashSet<>();

    public List<Failure> build(Class<?> testClass) {
        List<Method> irAnnotatedMethods = getIRAnnotatedMethods(testClass);
        for (Method method : irAnnotatedMethods) {
            processMethod(method);
        }
        return Failure.sort(expectedFails);
    }

    private static List<Method> getIRAnnotatedMethods(Class<?> testClass) {
        return Arrays.stream(testClass.getDeclaredMethods()).filter(m -> m.getAnnotationsByType(IR.class).length > 0).toList();
    }

    private void processMethod(Method method) {
        ExpectedFailure[] expectedFailures = method.getAnnotationsByType(ExpectedFailure.class);
        for (ExpectedFailure expectedFailure : expectedFailures) {
            if (expectedFailure.ruleId() == -1) {
                expectedFails.add(new Failure(method.getName(), -1, CompilePhase.DEFAULT, CheckAttributeType.FAIL_ON, -1));
            } else {
                processFail(method, expectedFailure);
            }
        }
    }

    private void processFail(Method method, ExpectedFailure expectedFailure) {
        for (CompilePhase phase : expectedFailure.phase()) {
            if (expectedFailure.hasCompilation()) {
                addFailureWithCompilation(method, expectedFailure, phase);
            } else {
                addFailureWithoutCompilation(method, expectedFailure, phase);
            }
        }
    }

    private void addFailureWithCompilation(Method method, ExpectedFailure expectedFailure, CompilePhase phase) {
        if (expectedFailure.failOn().length > 0) {
            addFailure(method.getName(), expectedFailure.ruleId(), phase, CheckAttributeType.FAIL_ON,
                       expectedFailure.failOn());
        }
        if (expectedFailure.counts().length > 0) {
            addFailure(method.getName(), expectedFailure.ruleId(), phase, CheckAttributeType.COUNTS,
                       expectedFailure.counts());
        }
    }

    private void addFailureWithoutCompilation(Method method, ExpectedFailure expectedFailure, CompilePhase phase) {
        expectedFails.add(new Failure(method.getName(), expectedFailure.ruleId(), phase, CheckAttributeType.FAIL_ON, -1));
    }

    private void addFailure(String methodName, int ruleId, CompilePhase phase, CheckAttributeType checkAttributeType,
                            int[] constraintIds) {
        for (int constraintId : constraintIds) {
            expectedFails.add(new Failure(methodName, ruleId, phase, checkAttributeType,
                                          constraintId));
        }
    }
}

record Failure(String methodName, int irRuleId, CompilePhase compilePhase, CheckAttributeType checkAttributeType,
               int constraintId) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Failure that = (Failure)o;
        return methodName.equals(that.methodName)
               && irRuleId == that.irRuleId
               && compilePhase == that.compilePhase
               && checkAttributeType == that.checkAttributeType
               && constraintId == that.constraintId;
    }

    public static List<Failure> sort(Set<Failure> failures) {
        return failures.stream().sorted(Comparator.comparing(Failure::methodName)
                                                  .thenComparing(Failure::irRuleId)
                                                  .thenComparing(Failure::compilePhase)
                                                  .thenComparing(Failure::checkAttributeType)
                                                  .thenComparing(Failure::constraintId)).collect(Collectors.toList());
    }
}

// Test load and store regexes
class LoadStore {
    int i;
    float f;
    interface I1 {}
    static class Base implements I1 {
        int i;
    }
    interface I2 {}
    static class Derived extends Base implements I2 {
        long l;
    }
    Base base = new Base();
    Derived derived = new Derived();

    static class SingleNest {
        static class DoubleNest {
            int i;
        }
    }

    SingleNest.DoubleNest doubleNest = new SingleNest.DoubleNest();


    @Test
    @IR(failOn = {IRNode.LOAD_OF_CLASS, ".*", IRNode.STORE_OF_CLASS, ".*"})
    public void triviallyFailBoth() {
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_OF_CLASS, "LoadS[a-z]+", "1",
            IRNode.LOAD_OF_CLASS, "Load.tore", "1",
            IRNode.LOAD_OF_CLASS, "LoadStore", "1",
            IRNode.LOAD_OF_CLASS, "/LoadStore", "1",
            IRNode.LOAD_OF_CLASS, "tests/LoadStore", "1",
            IRNode.LOAD_OF_CLASS, "/tests/LoadStore", "1",
            IRNode.LOAD_OF_CLASS, "ir_framework/tests/LoadStore", "1",
            IRNode.LOAD_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore", "1",  // To assert it's the whole qualification
            IRNode.LOAD_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.LOAD_OF_CLASS, "oadStore",
            IRNode.LOAD_OF_CLASS, "LoadStor",
            IRNode.LOAD_OF_CLASS, "/ir_framework/tests/LoadStore",
            IRNode.LOAD_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore+12 *
    public float simpleLoad() {
        return f;
    }

    @Test
    @IR(counts = {
            IRNode.STORE_OF_CLASS, "LoadS[a-z]+", "1",
            IRNode.STORE_OF_CLASS, "Load.tore", "1",
            IRNode.STORE_OF_CLASS, "LoadStore", "1",
            IRNode.STORE_OF_CLASS, "/LoadStore", "1",
            IRNode.STORE_OF_CLASS, "tests/LoadStore", "1",
            IRNode.STORE_OF_CLASS, "/tests/LoadStore", "1",
            IRNode.STORE_OF_CLASS, "ir_framework/tests/LoadStore", "1",
            IRNode.STORE_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore", "1",
            IRNode.STORE_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.STORE_OF_CLASS, "oadStore",
            IRNode.STORE_OF_CLASS, "LoadStor",
            IRNode.STORE_OF_CLASS, "/ir_framework/tests/LoadStore",
            IRNode.STORE_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore+12 *
    public void simpleStore() {
        i = 1;
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_I_OF_CLASS, "Base", "1",
            IRNode.LOAD_I_OF_CLASS, "\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "LoadS[a-z]+\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "Load.tore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "/LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "tests/LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "/tests/LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "ir_framework/tests/LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore\\$Base", "1",
            IRNode.LOAD_I_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.LOAD_I_OF_CLASS, "/Base",
            IRNode.LOAD_I_OF_CLASS, "oadStore\\$Base",
            IRNode.LOAD_I_OF_CLASS, "LoadStore\\$Bas",
            IRNode.LOAD_I_OF_CLASS, "LoadStore",
            IRNode.LOAD_I_OF_CLASS, "/ir_framework/tests/LoadStore\\$Base",
            IRNode.LOAD_I_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore$Base (ir_framework/tests/LoadStore$I1)+12 *
    public int loadWithInterface() {
        return base.i;
    }

    @Test
    @IR(counts = {
            IRNode.STORE_I_OF_CLASS, "Base", "1",
            IRNode.STORE_I_OF_CLASS, "\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "LoadS[a-z]+\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "Load.tore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "/LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "tests/LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "/tests/LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "ir_framework/tests/LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore\\$Base", "1",
            IRNode.STORE_I_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.STORE_I_OF_CLASS, "/Base",
            IRNode.STORE_I_OF_CLASS, "oadStore\\$Base",
            IRNode.STORE_I_OF_CLASS, "LoadStore\\$Bas",
            IRNode.STORE_I_OF_CLASS, "LoadStore",
            IRNode.STORE_I_OF_CLASS, "/ir_framework/tests/LoadStore\\$Base",
            IRNode.STORE_I_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore$Base (ir_framework/tests/LoadStore$I1)+12 *
    public void storeWithInterface() {
        base.i = 1;
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_L_OF_CLASS, "Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "LoadS[a-z]+\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "Load.tore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "/LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "tests/LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "/tests/LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "ir_framework/tests/LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore\\$Derived", "1",
            IRNode.LOAD_L_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.LOAD_L_OF_CLASS, "/Derived",
            IRNode.LOAD_L_OF_CLASS, "oadStore\\$Derived",
            IRNode.LOAD_L_OF_CLASS, "LoadStore\\$Derive",
            IRNode.LOAD_L_OF_CLASS, "LoadStore",
            IRNode.LOAD_L_OF_CLASS, "/ir_framework/tests/LoadStore\\$Derived",
            IRNode.LOAD_L_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore$Derived (ir_framework/tests/LoadStore$I1,ir_framework/tests/LoadStore$I2)+24 *
    public long loadWithInterfaces() {
        return derived.l;
    }

    @Test
    @IR(counts = {
            IRNode.STORE_L_OF_CLASS, "Derived", "1",
            IRNode.STORE_L_OF_CLASS, "\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "LoadS[a-z]+\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "Load.tore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "/LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "tests/LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "/tests/LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "ir_framework/tests/LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "(?<=[@: ])ir_framework/tests/LoadStore\\$Derived", "1",
            IRNode.STORE_L_OF_CLASS, "(?<=[@: ])[\\w\\$/]*tests[\\w\\$/]*", "1",
        },
        failOn = {
            IRNode.STORE_L_OF_CLASS, "/Derived",
            IRNode.STORE_L_OF_CLASS, "oadStore\\$Derived",
            IRNode.STORE_L_OF_CLASS, "LoadStore\\$Derive",
            IRNode.STORE_L_OF_CLASS, "LoadStore",
            IRNode.STORE_L_OF_CLASS, "/ir_framework/tests/LoadStore\\$Derived",
            IRNode.STORE_L_OF_CLASS, "(?<=[@: ])[\\w\\$]*tests[\\w\\$]*",
        }
    )
    // @ir_framework/tests/LoadStore$Derived (ir_framework/tests/LoadStore$I1,ir_framework/tests/LoadStore$I2)+24 *
    public void storeWithInterfaces() {
        derived.l = 1;
    }

    @Test
    @IR(counts = {
            IRNode.LOAD_I_OF_CLASS, "DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "\\$SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "/tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.LOAD_I_OF_CLASS, "ir_framework/tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
        },
        failOn = {
            IRNode.LOAD_I_OF_CLASS, "SingleNest",
            IRNode.LOAD_I_OF_CLASS, "LoadStore",
            IRNode.LOAD_I_OF_CLASS, "LoadStore\\$SingleNest",
        }
    )
    // @ir_framework/tests/LoadStore$SingleNest$DoubleNest+12 *
    public int loadDoubleNested() {
        return doubleNest.i;
    }

    @Test
    @IR(counts = {
            IRNode.STORE_I_OF_CLASS, "DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "\\$SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "/tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
            IRNode.STORE_I_OF_CLASS, "ir_framework/tests/LoadStore\\$SingleNest\\$DoubleNest", "1",
        },
        failOn = {
            IRNode.STORE_I_OF_CLASS, "SingleNest",
            IRNode.STORE_I_OF_CLASS, "LoadStore",
            IRNode.STORE_I_OF_CLASS, "LoadStore\\$SingleNest",
        }
    )
    // @ir_framework/tests/LoadStore$SingleNest$DoubleNest+12 *
    public void storeDoubleNested() {
        doubleNest.i = 1;
    }
}