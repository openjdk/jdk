/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test CorrectnessTest
 * @bug 8038418
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @ignore 8066173
 * @compile execution/TypeConflict.java execution/TypeProfile.java
 *          execution/MethodHandleDelegate.java
 * @build CorrectnessTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:TypeProfileLevel=222 -XX:+UseTypeSpeculation
 *                   -XX:CompileCommand=exclude,execution/*::methodNotToCompile
 *                   -XX:CompileCommand=dontinline,scenarios/Scenario::collectReturnType
 *                   CorrectnessTest RETURN
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:TypeProfileLevel=222 -XX:+UseTypeSpeculation
 *                   -XX:CompileCommand=exclude,execution/*::methodNotToCompile
 *                   -XX:CompileCommand=dontinline,scenarios/Scenario::collectReturnType
 *                   CorrectnessTest PARAMETERS
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockExperimentalVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:TypeProfileLevel=222 -XX:+UseTypeSpeculation
 *                   -XX:CompileCommand=exclude,execution/*::methodNotToCompile
 *                   -XX:CompileCommand=dontinline,scenarios/Scenario::collectReturnType
 *                   CorrectnessTest ARGUMENTS
 * @summary Tests correctness of type usage with type profiling and speculations
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import execution.Execution;
import execution.MethodHandleDelegate;
import execution.TypeConflict;
import execution.TypeProfile;
import hierarchies.*;
import scenarios.*;
import sun.hotspot.WhiteBox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class CorrectnessTest {
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        if (!Platform.isServer()) {
            System.out.println("ALL TESTS SKIPPED");
        }
        Asserts.assertGTE(args.length, 1);
        ProfilingType profilingType = ProfilingType.valueOf(args[0]);
        if (runTests(profilingType)) {
            System.out.println("ALL TESTS PASSED");
        } else {
            throw new RuntimeException("SOME TESTS FAILED");
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean runTests(ProfilingType profilingType) {
        boolean result = true;

        List<Execution> executionList = new ArrayList<>();
        executionList.add(new TypeConflict());
        executionList.add(new TypeProfile());
        for (int i = 0, n = executionList.size(); i < n; i++) {
            executionList.add(new MethodHandleDelegate(executionList.get(i)));
        }

        List<TypeHierarchy> hierarchyList = new ArrayList<>();
        hierarchyList.add(new DefaultMethodInterface.Hierarchy());
        hierarchyList.add(new DefaultMethodInterface2.Hierarchy());
        hierarchyList.add(new Linear.Hierarchy());
        hierarchyList.add(new Linear2.Hierarchy());
        hierarchyList.add(new OneRank.Hierarchy());
        for (int i = 0, n = hierarchyList.size(); i < n; i++) {
            hierarchyList.add(new NullableType(hierarchyList.get(i)));
        }

        List<BiFunction<ProfilingType, TypeHierarchy, Scenario<?, ?>>> testCasesConstructors
                = new ArrayList<>();
        testCasesConstructors.add(ArrayCopy::new);
        testCasesConstructors.add(ArrayReferenceStore::new);
        testCasesConstructors.add(ClassIdentity::new);
        testCasesConstructors.add(ClassInstanceOf::new);
        testCasesConstructors.add(ClassIsInstance::new);
        testCasesConstructors.add(ReceiverAtInvokes::new);
        testCasesConstructors.add(CheckCast::new);

        for (TypeHierarchy hierarchy : hierarchyList) {
            for (BiFunction<ProfilingType, TypeHierarchy, Scenario<?, ?>> constructor : testCasesConstructors) {
                for (Execution execution : executionList) {
                    Scenario<?, ?> scenario = constructor.apply(profilingType, hierarchy);
                    if (scenario.isApplicable()) {
                        result &= executeTest(hierarchy, execution, scenario);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Executes test case
     *
     * @param hierarchy type hierarchy for the test
     * @param execution execution scenario
     * @param scenario  test scenario executed with given Execution
     */
    private static boolean executeTest(TypeHierarchy hierarchy, Execution execution, Scenario<?, ?> scenario) {
        boolean testCaseResult = false;
        String testName = hierarchy.getClass().getName() + " :: " + scenario.getName() + " @ " + execution.getName();
        clearAllMethodsState(scenario.getClass());
        try {
            execution.execute(scenario);
            testCaseResult = true;
        } catch (Exception e) {
            System.err.println(testName + " failed with exception " + e);
            e.printStackTrace();
        }
        System.out.println((testCaseResult ? "PASSED: " : "FAILED: ") + testName);
        return testCaseResult;
    }

    private static void clearAllMethodsState(Class aClass) {
        while (aClass != null) {
            for (Method m : aClass.getDeclaredMethods()) {
                WHITE_BOX.clearMethodState(m);
            }
            aClass = aClass.getSuperclass();
        }
    }
}
