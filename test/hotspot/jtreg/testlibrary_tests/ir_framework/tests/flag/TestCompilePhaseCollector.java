/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.flag;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static compiler.lib.ir_framework.CompilePhase.*;

/*
 * @test
 * @requires vm.debug == true & vm.flagless
 * @summary Test compile phase collector required for writing the compile commands file.
 * @library /test/lib /testlibrary_tests /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run junit/othervm -Xbootclasspath/a:. -DSkipWhiteBoxInstall=true -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                    -XX:+WhiteBoxAPI compiler.lib.ir_framework.flag.TestCompilePhaseCollector
 */
public class TestCompilePhaseCollector {

    @org.junit.Test
    public void testIdeal() {
        testDefault(Ideal.class, PRINT_IDEAL);
    }

    @org.junit.Test
    public void testOpto() {
        testDefault(Opto.class, PRINT_OPTO_ASSEMBLY);
    }

    @org.junit.Test
    public void testIdealAndOpto() {
        testDefault(IdealAndOpto.class, PRINT_IDEAL, PRINT_OPTO_ASSEMBLY);
    }

    @org.junit.Test
    public void testOnlyOtherPhases() {
        Class<?> testClass = OnlyOtherPhases.class;
        Map<String, Set<CompilePhase>> methodToCompilePhases = CompilePhaseCollector.collect(testClass);
        assertSize(methodToCompilePhases, 8);
        assertContainsOnly(methodToCompilePhases, testClass, "test1", AFTER_PARSING);
        assertContainsOnly(methodToCompilePhases, testClass, "test2", AFTER_PARSING, BEFORE_MATCHING);
        assertContainsOnly(methodToCompilePhases, testClass, "test3", PHASEIDEALLOOP1, PHASEIDEALLOOP2);
        assertContainsOnly(methodToCompilePhases, testClass, "test4", PHASEIDEALLOOP1, PHASEIDEALLOOP2, ITER_GVN1, ITER_GVN2);
        assertContainsOnly(methodToCompilePhases, testClass, "test5", AFTER_PARSING);
        assertContainsOnly(methodToCompilePhases, testClass, "test6", AFTER_PARSING, BEFORE_MATCHING);
        assertContainsOnly(methodToCompilePhases, testClass, "test7", PHASEIDEALLOOP1, PHASEIDEALLOOP2);
        assertContainsOnly(methodToCompilePhases, testClass, "test8", PHASEIDEALLOOP1, PHASEIDEALLOOP2, ITER_GVN1, ITER_GVN2);
    }


    @org.junit.Test
    public void testMixedPhases() {
        Class<?> testClass = MixedPhases.class;
        Map<String, Set<CompilePhase>> methodToCompilePhases = CompilePhaseCollector.collect(testClass);
        assertSize(methodToCompilePhases, 33);
        assertContainsOnly(methodToCompilePhases, testClass, "test1", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test2", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test3", AFTER_PARSING, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "test4", PHASEIDEALLOOP1, PHASEIDEALLOOP2);
        assertContainsOnly(methodToCompilePhases, testClass, "test5", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test6", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test7", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "test8", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test1A", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test2A", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test3A", AFTER_PARSING, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "test4A", PHASEIDEALLOOP1, PHASEIDEALLOOP2);
        assertContainsOnly(methodToCompilePhases, testClass, "test5A", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test6A", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "test7A", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "test8A", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix1", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix2", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix3", AFTER_PARSING, PRINT_IDEAL, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "mix4", AFTER_PARSING, PRINT_IDEAL, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "mix5", AFTER_PARSING, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix6", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "mix7", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix8", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_IDEAL, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "mix9", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix10", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_OPTO_ASSEMBLY);
        assertContainsOnly(methodToCompilePhases, testClass, "mix11", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_OPTO_ASSEMBLY,
                           FINAL_CODE, OPTIMIZE_FINISHED);
        assertContainsOnly(methodToCompilePhases, testClass, "mix12", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_OPTO_ASSEMBLY,
                           FINAL_CODE, OPTIMIZE_FINISHED, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix13", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL,
                           FINAL_CODE, OPTIMIZE_FINISHED);
        assertContainsOnly(methodToCompilePhases, testClass, "mix14", PHASEIDEALLOOP1, PHASEIDEALLOOP2, PRINT_IDEAL,
                           FINAL_CODE, OPTIMIZE_FINISHED);
        assertContainsOnly(methodToCompilePhases, testClass, "mix15", PHASEIDEALLOOP1, PHASEIDEALLOOP2,
                           FINAL_CODE, OPTIMIZE_FINISHED);
        assertContainsOnly(methodToCompilePhases, testClass, "mix16", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_IDEAL);
        assertContainsOnly(methodToCompilePhases, testClass, "mix17", PHASEIDEALLOOP1, PHASEIDEALLOOP2, FINAL_CODE,
                           OPTIMIZE_FINISHED, PRINT_OPTO_ASSEMBLY);
    }

    private void testDefault(Class<?> testClass, CompilePhase... compilePhases) {
        Map<String, Set<CompilePhase>> methodToCompilePhases = CompilePhaseCollector.collect(testClass);
        assertSize(methodToCompilePhases, 6);
        assertFindOnly(methodToCompilePhases, testClass, compilePhases);
    }

    private void assertFindOnly(Map<String, Set<CompilePhase>> methodToCompilePhases, Class<?> testClass, CompilePhase... compilePhases) {
        Arrays.stream(testClass.getDeclaredMethods())
              .forEach(m -> assertContainsOnly(methodToCompilePhases, testClass, m.getName(), compilePhases));
    }

    private void assertContainsOnly(Map<String, Set<CompilePhase>> methodToCompilePhases, Class<?> testClass,
                                    String simpleMethodName, CompilePhase... compilePhases) {
        String methodName = getFullMethodName(testClass, simpleMethodName);
        Set<CompilePhase> compilePhaseSet = methodToCompilePhases.get(methodName);
        Assert.assertEquals("In method " + simpleMethodName + ": must be equal", compilePhases.length, compilePhaseSet.size());
        for (CompilePhase compilePhase : compilePhases) {
            Assert.assertTrue("In method " + simpleMethodName + ": did not find " + compilePhase + " for " + methodName,
                              methodToCompilePhases.get(methodName).contains(compilePhase));
        }
    }

    private void assertSize(Map<String, Set<CompilePhase>> compilePhases, int size) {
        Assert.assertEquals("wrong number of results", size, compilePhases.size());
    }

    public String getFullMethodName(Class<?> testClass, String methodName) {
        return testClass.getCanonicalName() + "::" + methodName;
    }

    static class Ideal {
        @Test
        @IR(failOn = IRNode.LOAD)
        public void test1() {}


        @Test
        @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld"})
        public void test2() {}

        @Test
        @IR(counts = {IRNode.STORE, "2"})
        public void test3() {}

        @Test
        @IR(counts = {IRNode.STORE_OF_FIELD, "iFld", "!= 4"})
        public void test4() {}


        @Test
        @IR(failOn = IRNode.LOAD)
        @IR(counts = {IRNode.STORE, "2"})
        public void test5() {}

        @Test
        @IR(failOn = {IRNode.LOOP, IRNode.CALL_OF_METHOD, "foo"})
        @IR(counts = {IRNode.CLASS_CHECK_TRAP, "2", IRNode.LOAD_B_OF_CLASS, "Foo", "> 1"})
        public void test6() {}
    }

    static class Opto {

        @Test
        @IR(counts = {IRNode.SCOPE_OBJECT, "2"})
        public void test1() {}

        @Test
        @IR(counts = {IRNode.OOPMAP_WITH, "Foo", "2"})
        public void test2() {}

        @Test
        @IR(failOn = IRNode.FIELD_ACCESS)
        @IR(counts = {IRNode.CHECKCAST_ARRAY, "2"})
        public void test3() {}

        @Test
        @IR(failOn = {IRNode.CHECKCAST_ARRAYCOPY, IRNode.CHECKCAST_ARRAY_OF, "Foo"})
        @IR(counts = {IRNode.CBZ_HI, "> 1"})
        public void test4() {}

        @Test
        @IR(failOn = {IRNode.CBNZW_HI})
        @IR(counts = {IRNode.CBZ_LS, "2", IRNode.CBZW_LS, "> 1"})
        public void test5() {}

        @Test
        @IR(failOn = {IRNode.CBNZW_HI})
        @IR(counts = {IRNode.CBZW_LS, "> 1"})
        public void test6() {}
    }

    static class IdealAndOpto {
        @Test
        @IR(failOn = IRNode.FIELD_ACCESS)
        @IR(failOn = IRNode.STORE)
        public void test1() {}


        @Test
        @IR(failOn = {IRNode.CHECKCAST_ARRAY_OF, "Foo"})
        @IR(failOn = {IRNode.STORE_OF_FIELD, "iFld"})
        public void test2() {}

        @Test
        @IR(counts = {IRNode.LOAD, "2"})
        @IR(counts = {IRNode.SCOPE_OBJECT, "2"})
        public void test3() {}

        @Test
        @IR(counts = {IRNode.LOAD_OF_FIELD, "iFld", "!= 4"})
        @IR(counts = {IRNode.OOPMAP_WITH, "Foo", "!= 4"})
        public void test4() {}


        @Test
        @IR(failOn = IRNode.FIELD_ACCESS)
        @IR(failOn = IRNode.LOOP)
        @IR(counts = {IRNode.CHECKCAST_ARRAY, "2"})
        @IR(counts = {IRNode.STORE, "2"})
        public void test5() {}

        @Test
        @IR(failOn = {IRNode.STORE, IRNode.CHECKCAST_ARRAY_OF, "Foo"})
        @IR(counts = {IRNode.FIELD_ACCESS, "2", IRNode.STORE_OF_FIELD, "iFld", "> 1"})
        public void test6() {}
    }

    static class OnlyOtherPhases {
        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        public void test1() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(failOn = {IRNode.STORE_OF_FIELD, "fld"}, phase = BEFORE_MATCHING)
        public void test2() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        public void test3() {}

        @Test
        @IR(failOn = {IRNode.CHECKCAST_ARRAY_OF, "Foo"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = {IRNode.STORE_F}, phase = {ITER_GVN1, ITER_GVN2})
        public void test4() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = AFTER_PARSING)
        public void test5() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = AFTER_PARSING)
        @IR(counts = {IRNode.STORE_OF_FIELD, "fld", ">4"}, phase = BEFORE_MATCHING)
        public void test6() {}

        @Test
        @IR(counts = {IRNode.STORE, "!=3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        public void test7() {}

        @Test
        @IR(counts = {IRNode.CHECKCAST_ARRAY_OF, "Foo", ">= 23"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.STORE_F, "2"}, phase = {ITER_GVN1, ITER_GVN2})
        public void test8() {}
    }

    static class MixedPhases {
        @Test
        @IR(failOn = IRNode.STORE, phase = {AFTER_PARSING, DEFAULT})
        public void test1() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(failOn = {IRNode.STORE_OF_FIELD, "fld"}, phase = DEFAULT)
        public void test2() {}

        @Test
        @IR(failOn = IRNode.ALLOC, phase = AFTER_PARSING)
        @IR(failOn = {IRNode.OOPMAP_WITH, "Foo"}, phase = DEFAULT)
        public void test3() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        public void test4() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, DEFAULT, PHASEIDEALLOOP2})
        public void test5() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = {IRNode.STORE_OF_FIELD, "fld"}, phase = DEFAULT)
        public void test6() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = IRNode.FIELD_ACCESS, phase = DEFAULT)
        @IR(failOn = IRNode.STORE, phase = {FINAL_CODE, OPTIMIZE_FINISHED})
        public void test7() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = IRNode.STORE, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void test8() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {AFTER_PARSING, DEFAULT})
        public void test1A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = AFTER_PARSING)
        @IR(counts = {IRNode.STORE_OF_FIELD, "fld", "3"}, phase = DEFAULT)
        public void test2A() {}

        @Test
        @IR(counts = {IRNode.ALLOC, "< 3"}, phase = AFTER_PARSING)
        @IR(counts = {IRNode.OOPMAP_WITH, "Foo", ">=3"}, phase = DEFAULT)
        public void test3A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        public void test4A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {PHASEIDEALLOOP1, DEFAULT, PHASEIDEALLOOP2})
        public void test5A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.STORE_OF_FIELD, "fld", "4"}, phase = DEFAULT)
        public void test6A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.FIELD_ACCESS, "3"}, phase = DEFAULT)
        @IR(counts = {IRNode.STORE, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED})
        public void test7A() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.STORE, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void test8A() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(counts = {IRNode.STORE, "3"}, phase = DEFAULT)
        public void mix1() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = DEFAULT)
        @IR(counts = {IRNode.ALLOC, "3"}, phase = AFTER_PARSING)
        public void mix2() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = AFTER_PARSING)
        @IR(counts = {IRNode.STORE, "3"}, phase = DEFAULT)
        @IR(failOn = {IRNode.OOPMAP_WITH, "Foo"}, phase = DEFAULT)
        public void mix3() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = AFTER_PARSING)
        @IR(counts = {IRNode.STORE_OF_CLASS, "Foo", "3"}, phase = DEFAULT)
        @IR(failOn = IRNode.FIELD_ACCESS, phase = DEFAULT)
        public void mix4() {}

        @Test
        @IR(counts = {IRNode.STORE, "3"}, phase = {AFTER_PARSING, DEFAULT})
        @IR(failOn = {IRNode.STORE_OF_CLASS, "Foo"}, phase = DEFAULT)
        public void mix5() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.FIELD_ACCESS, "3"}, phase = DEFAULT)
        @IR(failOn = IRNode.STORE, phase = {FINAL_CODE, OPTIMIZE_FINISHED})
        public void mix6() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.STORE, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix7() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, DEFAULT, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.FIELD_ACCESS, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix8() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PRINT_IDEAL, PHASEIDEALLOOP2})
        public void mix9() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PRINT_OPTO_ASSEMBLY, PHASEIDEALLOOP2})
        public void mix10() {}

        @Test
        @IR(failOn = IRNode.FIELD_ACCESS, phase = {PHASEIDEALLOOP1, PRINT_OPTO_ASSEMBLY, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.FIELD_ACCESS, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix11() {}

        @Test
        @IR(failOn = IRNode.STORE, phase = {PHASEIDEALLOOP1, PRINT_IDEAL, PHASEIDEALLOOP2})
        @IR(counts = {IRNode.FIELD_ACCESS, "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix12() {}

        @Test
        @IR(failOn = "foo", phase = {PHASEIDEALLOOP1, PRINT_IDEAL, PHASEIDEALLOOP2})
        @IR(counts = {"foo", "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED})
        public void mix13() {}

        @Test
        @IR(failOn = "foo", phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(counts = {"foo", "3"}, phase = {FINAL_CODE, OPTIMIZE_FINISHED, PRINT_IDEAL})
        public void mix14() {}

        @Test
        @IR(counts = {"foo", "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = "foo", phase = {FINAL_CODE, OPTIMIZE_FINISHED})
        public void mix15() {}

        @Test
        @IR(counts = {"foo", "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = IRNode.STORE, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix16() {}

        @Test
        @IR(counts = {"foo", "3"}, phase = {PHASEIDEALLOOP1, PHASEIDEALLOOP2})
        @IR(failOn = IRNode.FIELD_ACCESS, phase = {FINAL_CODE, OPTIMIZE_FINISHED, DEFAULT})
        public void mix17() {}
    }
}
