/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests.scalarReplacement;

import java.util.Random;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8281429
 * @summary Tests that C2 can correctly scalar replace some object allocation merges.
 * @library /test/lib /
 * @requires vm.debug == true & vm.flagless & vm.bits == 64 & vm.compiler2.enabled & vm.opt.final.EliminateAllocations
 * @run driver compiler.c2.irTests.scalarReplacement.AllocationMergesTests
 */
public class AllocationMergesTests {
    private int invocations = 0;
    private static Point global_escape = new Point(2022, 2023);

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();

        Scenario scenario0 = new Scenario(0, "-XX:+UnlockDiagnosticVMOptions",
                                             "-XX:+ReduceAllocationMerges",
                                             "-XX:+TraceReduceAllocationMerges",
                                             "-XX:+DeoptimizeALot",
                                             "-XX:+UseCompressedOops",
                                             "-XX:+UseCompressedClassPointers",
                                             "-XX:CompileCommand=inline,*::charAt*",
                                             "-XX:CompileCommand=inline,*PicturePositions::*",
                                             "-XX:CompileCommand=inline,*Point::*",
                                             "-XX:CompileCommand=inline,*Nested::*",
                                             "-XX:CompileCommand=exclude,*::dummy*");

        Scenario scenario1 = new Scenario(1, "-XX:+UnlockDiagnosticVMOptions",
                                             "-XX:+ReduceAllocationMerges",
                                             "-XX:+TraceReduceAllocationMerges",
                                             "-XX:+DeoptimizeALot",
                                             "-XX:+UseCompressedOops",
                                             "-XX:-UseCompressedClassPointers",
                                             "-XX:CompileCommand=inline,*::charAt*",
                                             "-XX:CompileCommand=inline,*PicturePositions::*",
                                             "-XX:CompileCommand=inline,*Point::*",
                                             "-XX:CompileCommand=inline,*Nested::*",
                                             "-XX:CompileCommand=exclude,*::dummy*");

        Scenario scenario2 = new Scenario(2, "-XX:+UnlockDiagnosticVMOptions",
                                             "-XX:+ReduceAllocationMerges",
                                             "-XX:+TraceReduceAllocationMerges",
                                             "-XX:+DeoptimizeALot",
                                             "-XX:-UseCompressedOops",
                                             "-XX:CompileCommand=inline,*::charAt*",
                                             "-XX:CompileCommand=inline,*PicturePositions::*",
                                             "-XX:CompileCommand=inline,*Point::*",
                                             "-XX:CompileCommand=inline,*Nested::*",
                                             "-XX:CompileCommand=exclude,*::dummy*");

        framework.addScenarios(scenario0, scenario1, scenario2).start();
    }

    // ------------------ No Scalar Replacement Should Happen in The Tests Below ------------------- //

    @Run(test = {"testGlobalEscape_C2",
                 "testArgEscape_C2",
                 "testEscapeInCallAfterMerge_C2",
                 "testNoEscapeWithWriteInLoop_C2",
                 "testPollutedWithWrite_C2",
                 "testPollutedPolymorphic_C2",
                 "testMergedLoadAfterDirectStore_C2",
                 "testMergedAccessAfterCallWithWrite_C2",
                 "testLoadAfterTrap_C2",
                 "testCondAfterMergeWithNull_C2",
                 "testLoadAfterLoopAlias_C2",
                 "testCallTwoSide_C2",
                 "testMergedAccessAfterCallNoWrite_C2",
                 "testCmpMergeWithNull_Second_C2",
                 "testObjectIdentity_C2",
                 "testSubclassesTrapping_C2",
                 "testCmpMergeWithNull_C2",
                 "testSubclasses_C2",
                 "testPartialPhis_C2",
                 "testPollutedNoWrite_C2",
                 "testThreeWayAliasedAlloc_C2",
                 "TestTrapAfterMerge_C2",
                 "testNestedObjectsObject_C2",
                 "testNestedObjectsNoEscapeObject_C2",
                 "testNestedObjectsArray_C2",
                 "testTrappingAfterMerge_C2",
                 "testSimpleAliasedAlloc_C2",
                 "testSimpleDoubleMerge_C2",
                 "testConsecutiveSimpleMerge_C2",
                 "testDoubleIfElseMerge_C2",
                 "testNoEscapeWithLoadInLoop_C2",
                 "testCmpAfterMerge_C2",
                 "testCondAfterMergeWithAllocate_C2",
                 "testCondLoadAfterMerge_C2",
                 "testIfElseInLoop_C2",
                 "testLoadInCondAfterMerge_C2",
                 "testLoadInLoop_C2",
                 "testMergesAndMixedEscape_C2",
                 "testSRAndNSR_NoTrap_C2",
                 "testSRAndNSR_Trap_C2",
                 "testString_one_C2",
                 "testString_two_C2",
                 "testLoadKlassFromCast_C2",
                 "testLoadKlassFromPhi_C2",
                 "testReReduce_C2"
                })
    public void runner(RunInfo info) {
        invocations++;

        Random random = info.getRandom();
        boolean cond1 = invocations % 2 == 0;
        boolean cond2 = !cond1;

        int l = random.nextInt();
        int w = random.nextInt();
        int x = random.nextInt();
        int y = random.nextInt();
        int z = random.nextInt();

        Asserts.assertEQ(testGlobalEscape_Interp(x, y),                             testGlobalEscape_C2(x, y));
        Asserts.assertEQ(testArgEscape_Interp(x, y),                                testArgEscape_C2(x, y));
        Asserts.assertEQ(testEscapeInCallAfterMerge_Interp(cond1, cond2, x, y),     testEscapeInCallAfterMerge_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNoEscapeWithWriteInLoop_Interp(cond1, cond2, x, y),    testNoEscapeWithWriteInLoop_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testPollutedWithWrite_Interp(cond1, x),                    testPollutedWithWrite_C2(cond1, x));
        Asserts.assertEQ(testPollutedPolymorphic_Interp(cond1, x),                  testPollutedPolymorphic_C2(cond1, x));
        Asserts.assertEQ(testMergedLoadAfterDirectStore_Interp(cond1, x, y),        testMergedLoadAfterDirectStore_C2(cond1, x, y));
        Asserts.assertEQ(testMergedAccessAfterCallWithWrite_Interp(cond1, x, y),    testMergedAccessAfterCallWithWrite_C2(cond1, x, y));
        Asserts.assertEQ(testLoadAfterTrap_Interp(cond1, x, y),                     testLoadAfterTrap_C2(cond1, x, y));
        Asserts.assertEQ(testCondAfterMergeWithNull_Interp(cond1, cond2, x, y),     testCondAfterMergeWithNull_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testLoadAfterLoopAlias_Interp(x, y),                       testLoadAfterLoopAlias_C2(x, y));
        Asserts.assertEQ(testCallTwoSide_Interp(cond1, x, y),                       testCallTwoSide_C2(cond1, x, y));
        Asserts.assertEQ(testMergedAccessAfterCallNoWrite_Interp(cond1, x, y),      testMergedAccessAfterCallNoWrite_C2(cond1, x, y));
        Asserts.assertEQ(testCmpMergeWithNull_Second_Interp(cond1, x, y),           testCmpMergeWithNull_Second_C2(cond1, x, y));
        Asserts.assertEQ(testObjectIdentity_Interp(cond1, 42, y),                   testObjectIdentity_C2(cond1, 42, y));
        Asserts.assertEQ(testSubclassesTrapping_Interp(cond1, cond2, x, y, w, z),   testSubclassesTrapping_C2(cond1, cond2, x, y, w, z));
        Asserts.assertEQ(testCmpMergeWithNull_Interp(cond1, x, y),                  testCmpMergeWithNull_C2(cond1, x, y));
        Asserts.assertEQ(testSubclasses_Interp(cond1, cond2, x, y, w, z),           testSubclasses_C2(cond1, cond2, x, y, w, z));
        Asserts.assertEQ(testPartialPhis_Interp(cond1, l, x, y),                    testPartialPhis_C2(cond1, l, x, y));
        Asserts.assertEQ(testPollutedNoWrite_Interp(cond1, l),                      testPollutedNoWrite_C2(cond1, l));
        Asserts.assertEQ(testThreeWayAliasedAlloc_Interp(cond1, x, y),              testThreeWayAliasedAlloc_C2(cond1, x, y));
        Asserts.assertEQ(TestTrapAfterMerge_Interp(cond1, x, y),                    TestTrapAfterMerge_C2(cond1, x, y));
        Asserts.assertEQ(testNestedObjectsObject_Interp(cond1, x, y),               testNestedObjectsObject_C2(cond1, x, y));
        Asserts.assertEQ(testNestedObjectsNoEscapeObject_Interp(cond1, x, y),       testNestedObjectsNoEscapeObject_C2(cond1, x, y));
        Asserts.assertEQ(testTrappingAfterMerge_Interp(cond1, x, y),                testTrappingAfterMerge_C2(cond1, x, y));
        Asserts.assertEQ(testSimpleAliasedAlloc_Interp(cond1, x, y),                testSimpleAliasedAlloc_C2(cond1, x, y));
        Asserts.assertEQ(testSimpleDoubleMerge_Interp(cond1, x, y),                 testSimpleDoubleMerge_C2(cond1, x, y));
        Asserts.assertEQ(testConsecutiveSimpleMerge_Interp(cond1, cond2, x, y),     testConsecutiveSimpleMerge_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testDoubleIfElseMerge_Interp(cond1, x, y),                 testDoubleIfElseMerge_C2(cond1, x, y));
        Asserts.assertEQ(testNoEscapeWithLoadInLoop_Interp(cond1, x, y),            testNoEscapeWithLoadInLoop_C2(cond1, x, y));
        Asserts.assertEQ(testCmpAfterMerge_Interp(cond1, cond2, x, y),              testCmpAfterMerge_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testCondAfterMergeWithAllocate_Interp(cond1, cond2, x, y), testCondAfterMergeWithAllocate_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testCondLoadAfterMerge_Interp(cond1, cond2, x, y),         testCondLoadAfterMerge_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testIfElseInLoop_Interp(),                                 testIfElseInLoop_C2());
        Asserts.assertEQ(testLoadInCondAfterMerge_Interp(cond1, x, y),              testLoadInCondAfterMerge_C2(cond1, x, y));
        Asserts.assertEQ(testLoadInLoop_Interp(cond1, x, y),                        testLoadInLoop_C2(cond1, x, y));
        Asserts.assertEQ(testMergesAndMixedEscape_Interp(cond1, x, y),              testMergesAndMixedEscape_C2(cond1, x, y));
        Asserts.assertEQ(testSRAndNSR_NoTrap_Interp(cond1, x, y),                   testSRAndNSR_NoTrap_C2(cond1, x, y));
        Asserts.assertEQ(testString_one_Interp(cond1),                              testString_one_C2(cond1));
        Asserts.assertEQ(testString_two_Interp(cond1),                              testString_two_C2(cond1));
        Asserts.assertEQ(testLoadKlassFromCast_Interp(cond1),                       testLoadKlassFromCast_C2(cond1));
        Asserts.assertEQ(testLoadKlassFromPhi_Interp(cond1),                        testLoadKlassFromPhi_C2(cond1));
        Asserts.assertEQ(testReReduce_Interp(cond1, x, y),                          testReReduce_C2(cond1, x, y));

        Asserts.assertEQ(testSRAndNSR_Trap_Interp(false, cond1, cond2, x, y),
                         testSRAndNSR_Trap_C2(info.isTestC2Compiled("testSRAndNSR_Trap_C2"), cond1, cond2, x, y));

        var arr1 = testNestedObjectsArray_Interp(cond1, x, y);
        var arr2 = testNestedObjectsArray_C2(cond1, x, y);

        if (arr1.length != arr2.length) Asserts.fail("testNestedObjectsArray result size mismatch.");
        for (int i=0; i<arr1.length; i++) {
            if (!arr1[i].equals(arr2[i])) {
                Asserts.fail("testNestedObjectsArray objects mismatch.");
            }
        }

    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testGlobalEscape(int x, int y) {
        Point p = new Point(x, y);

        AllocationMergesTests.global_escape = p;

        return p.x * p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    int testGlobalEscape_C2(int x, int y) { return testGlobalEscape(x, y); }

    @DontCompile
    int testGlobalEscape_Interp(int x, int y) { return testGlobalEscape(x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testArgEscape(int x, int y) {
        Point p = new Point(x, y);

        int val = dummy(p);

        return val + p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    int testArgEscape_C2(int x, int y) { return testArgEscape(x, y); }

    @DontCompile
    int testArgEscape_Interp(int x, int y) { return testArgEscape(x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testEscapeInCallAfterMerge(boolean cond, boolean cond2, int x, int y) {
        Point p = new Point(x, x);

        if (cond) {
            p = new Point(y, y);
        }

        if (cond2) {
            dummy(p);
        }

        return p.x * p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    int testEscapeInCallAfterMerge_C2(boolean cond, boolean cond2, int x, int y) { return testEscapeInCallAfterMerge(cond, cond2, x, y); }

    @DontCompile
    int testEscapeInCallAfterMerge_Interp(boolean cond, boolean cond2, int x, int y) { return testEscapeInCallAfterMerge(cond, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testNoEscapeWithWriteInLoop(boolean cond, boolean cond2, int x, int y) {
        Point p = new Point(x, y);
        int res = 0;

        if (cond) {
            p = new Point(y, x);
        }

        for (int i=0; i<100; i++) {
            p.x += p.y + i;
            p.y += p.x + i;
        }

        return res + p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because of the write to the fields
    int testNoEscapeWithWriteInLoop_C2(boolean cond, boolean cond2, int x, int y) { return testNoEscapeWithWriteInLoop(cond, cond2, x, y); }

    @DontCompile
    int testNoEscapeWithWriteInLoop_Interp(boolean cond, boolean cond2, int x, int y) { return testNoEscapeWithWriteInLoop(cond, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testPollutedWithWrite(boolean cond, int l) {
        Shape obj1 = new Square(l);
        Shape obj2 = new Square(l);
        Shape obj = null;

        if (cond) {
            obj = obj1;
        } else {
            obj = obj2;
        }

        for (int i=1; i<132; i++) {
            obj.x++;
        }

        return obj1.x + obj2.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because of the write to the field
    int testPollutedWithWrite_C2(boolean cond, int l) { return testPollutedWithWrite(cond, l); }

    @DontCompile
    int testPollutedWithWrite_Interp(boolean cond, int l) { return testPollutedWithWrite(cond, l); }

    // -------------------------------------------------------------------------
    @ForceInline
    int testPollutedPolymorphic(boolean cond, int l) {
        Shape obj1 = new Square(l);
        Shape obj2 = new Circle(l);
        Shape obj = (cond ? obj1 : obj2);
        int res = 0;

        for (int i=1; i<232; i++) {
            res += obj.x;
        }

        return res + obj1.x + obj2.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testPollutedPolymorphic_C2(boolean cond, int l) { return testPollutedPolymorphic(cond, l); }

    @DontCompile
    int testPollutedPolymorphic_Interp(boolean cond, int l) { return testPollutedPolymorphic(cond, l); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testMergedLoadAfterDirectStore(boolean cond, int x, int y) {
        Point p0 = new Point(x, x);
        Point p1 = new Point(y, y);
        Point p = null;

        if (cond) {
            p = p0;
        } else {
            p = p1;
        }

        p0.x = x * y;

        return p.x;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because write to one of the inputs *after* the merge
    int testMergedLoadAfterDirectStore_C2(boolean cond, int x, int y) { return testMergedLoadAfterDirectStore(cond, x, y); }

    @DontCompile
    int testMergedLoadAfterDirectStore_Interp(boolean cond, int x, int y) { return testMergedLoadAfterDirectStore(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testMergedAccessAfterCallWithWrite(boolean cond, int x, int y) {
        Point p2 = new Point(x, x);
        Point p = new Point(y, y);

        p.x = p.x * y;

        if (cond) {
            p = new Point(x, x);
        }

        dummy(p2);

        for (int i=3; i<324; i++) {
            p.x += i * x;
        }

        return p.x;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" })
    // Objects won't be scalar replaced because:
    //  - p is written inside the loop.
    //  - p2 is ArgEscape
    int testMergedAccessAfterCallWithWrite_C2(boolean cond, int x, int y) { return testMergedAccessAfterCallWithWrite(cond, x, y); }

    @DontCompile
    int testMergedAccessAfterCallWithWrite_Interp(boolean cond, int x, int y) { return testMergedAccessAfterCallWithWrite(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testLoadAfterTrap(boolean cond, int x, int y) {
        Point p = null;

        if (cond) {
            p = new Point(x, x);
        } else {
            p = new Point(y, y);
        }

        dummy(x+y);

        return p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // The allocations won't be removed because 'split_through_phi' won't split the load through the bases.
    int testLoadAfterTrap_C2(boolean cond, int x, int y) { return testLoadAfterTrap(cond, x, y); }

    @DontCompile
    int testLoadAfterTrap_Interp(boolean cond, int x, int y) { return testLoadAfterTrap(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCondAfterMergeWithNull(boolean cond1, boolean cond2, int x, int y) {
        Point p = null;

        if (cond1) {
            p = new Point(y, x);
        }

        if (cond2 && cond1) {
            return p.x;
        } else {
            return 321;
        }
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testCondAfterMergeWithNull_C2(boolean cond1, boolean cond2, int x, int y) { return testCondAfterMergeWithNull(cond1, cond2, x, y); }

    @DontCompile
    int testCondAfterMergeWithNull_Interp(boolean cond1, boolean cond2, int x, int y) { return testCondAfterMergeWithNull(cond1, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testLoadAfterLoopAlias(int x, int y) {
        Point a = new Point(x, y);
        Point b = new Point(y, x);
        int acc = 0;

        for (int i=10; i<232; i++) {
            Point c = (i % 2 == 0) ? a : b;
            acc += c.x + c.y;
        }

        return acc;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations should be removed
    int testLoadAfterLoopAlias_C2(int x, int y) { return testLoadAfterLoopAlias(x, y); }

    @DontCompile
    int testLoadAfterLoopAlias_Interp(int x, int y) { return testLoadAfterLoopAlias(x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCallTwoSide(boolean cond1, int x, int y) {
        Point p = dummy(x, y);

        if (cond1) {
            p = dummy(y, x);
        }

        return (p != null) ? p.x : 0;
    }

    @Test
    @IR(counts = { IRNode.CALL, "<=3" })
    // Merge won't be reduced because both of the inputs are NSR.
    // There could be 3 call nodes because one if can became an unstable trap.
    int testCallTwoSide_C2(boolean cond1, int x, int y) { return testCallTwoSide(cond1, x, y); }

    @DontCompile
    int testCallTwoSide_Interp(boolean cond1, int x, int y) { return testCallTwoSide(cond1, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testMergedAccessAfterCallNoWrite(boolean cond, int x, int y) {
        Point p2 = new Point(x, x);
        Point p = new Point(y, y);
        int res = 0;

        p.x = p.x * y;

        if (cond) {
            p = new Point(y, y);
        }

        dummy(p2);

        for (int i=3; i<324; i++) {
            res += p.x + i * x;
        }

        return res;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" })
    // p2 escapes and therefore won't be removed.
    // The allocations won't be removed because 'split_through_phi' won't split the load through the bases.
    int testMergedAccessAfterCallNoWrite_C2(boolean cond, int x, int y) { return testMergedAccessAfterCallNoWrite(cond, x, y); }

    @DontCompile
    int testMergedAccessAfterCallNoWrite_Interp(boolean cond, int x, int y) { return testMergedAccessAfterCallNoWrite(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCmpMergeWithNull_Second(boolean cond, int x, int y) {
        Point p = null;

        if (cond) {
            p = new Point(x*x, y*y);
        }

        dummy(x);

        if (p != null) {
            return p.x * p.y;
        } else {
            return 1984;
        }
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    int testCmpMergeWithNull_Second_C2(boolean cond, int x, int y) { return testCmpMergeWithNull_Second(cond, x, y); }

    @DontCompile
    int testCmpMergeWithNull_Second_Interp(boolean cond, int x, int y) { return testCmpMergeWithNull_Second(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testObjectIdentity(boolean cond, int x, int y) {
        Point o = new Point(x, y);

        if (cond && x == 42) {
            o = global_escape;
        }

        return o.x + o.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testObjectIdentity_C2(boolean cond, int x, int y) { return testObjectIdentity(cond, x, y); }

    @DontCompile
    int testObjectIdentity_Interp(boolean cond, int x, int y) { return testObjectIdentity(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSubclassesTrapping(boolean c1, boolean c2, int x, int y, int w, int z) {
        new A();
        Root s = new Home(x, y);
        new B();

        if (c1) {
            new C();
            s = new Etc("Hello");
            new D();
        } else {
            new E();
            s = new Usr(y, x, z);
            new F();
        }

        int res = s.a;
        dummy();

        return res;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testSubclassesTrapping_C2(boolean c1, boolean c2, int x, int y, int w, int z) { return testSubclassesTrapping(c1, c2, x, y, w, z); }

    @DontCompile
    int testSubclassesTrapping_Interp(boolean c1, boolean c2, int x, int y, int w, int z) { return testSubclassesTrapping(c1, c2, x, y, w, z); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCmpMergeWithNull(boolean cond, int x, int y) {
        Point p = null;

        if (cond) {
            p = new Point(x*x, y*y);
        } else if (x > y) {
            p = new Point(x+y, x*y);
        }

        if (p != null) {
            return p.x * p.y;
        } else {
            return 1984;
        }
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testCmpMergeWithNull_C2(boolean cond, int x, int y) { return testCmpMergeWithNull(cond, x, y); }

    @DontCompile
    int testCmpMergeWithNull_Interp(boolean cond, int x, int y) { return testCmpMergeWithNull(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSubclasses(boolean c1, boolean c2, int x, int y, int w, int z) {
        new A();
        Root s = new Home(x, y);
        new B();

        if (c1) {
            new C();
            s = new Etc("Hello");
            new D();
        } else {
            new E();
            s = new Usr(y, x, z);
            new F();
        }

        new G();

        return s.a;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    int testSubclasses_C2(boolean c1, boolean c2, int x, int y, int w, int z) { return testSubclasses(c1, c2, x, y, w, z); }

    @DontCompile
    int testSubclasses_Interp(boolean c1, boolean c2, int x, int y, int w, int z) { return testSubclasses(c1, c2, x, y, w, z); }


    // ------------------ Some Scalar Replacement Should Happen in The Tests Below ------------------- //

    @ForceInline
    int testPartialPhis(boolean cond, int l, int x, int y) {
        int k = l;

        if (l == 0) {
            k = l + 1;
        } else if (l == 2) {
            k = l + 2;
        } else if (l == 3) {
            new Point(x, y);
        } else if (l == 4) {
            new Point(y, x);
        }

        return k;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // all allocations will be dead
    int testPartialPhis_C2(boolean cond, int l, int x, int y) { return testPartialPhis(cond, l, x, y); }

    @DontCompile
    int testPartialPhis_Interp(boolean cond, int l, int x, int y) { return testPartialPhis(cond, l, x, y); }


    // -------------------------------------------------------------------------

    @ForceInline
    int testPollutedNoWrite(boolean cond, int l) {
        Shape obj1 = new Square(l);
        Shape obj2 = new Square(l);
        Shape obj = null;
        int res = 0;

        if (cond) {
            obj = obj1;
        } else {
            obj = obj2;
        }

        for (int i=1; i<132; i++) {
            res += obj.x;
        }

        return res + obj1.x + obj2.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be removed. After initialization they are read-only objects.
    // Access to the input of the merge, after the merge, is fine.
    int testPollutedNoWrite_C2(boolean cond, int l) { return testPollutedNoWrite(cond, l); }

    @DontCompile
    int testPollutedNoWrite_Interp(boolean cond, int l) { return testPollutedNoWrite(cond, l); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testThreeWayAliasedAlloc(boolean cond, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(x+1, y+1);
        Point p3 = new Point(x+2, y+2);

        if (cond) {
            p3 = p1;
        } else {
            p3 = p2;
        }

        return p3.x + p3.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Initial p3 will always be dead.
    // The other two allocations will be reduced and scaled
    int testThreeWayAliasedAlloc_C2(boolean cond, int x, int y) { return testThreeWayAliasedAlloc(cond, x, y); }

    @DontCompile
    int testThreeWayAliasedAlloc_Interp(boolean cond, int x, int y) { return testThreeWayAliasedAlloc(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int TestTrapAfterMerge(boolean cond, int x, int y) {
        Point p = new Point(x, x);

        if (cond) {
            p = new Point(y, y);
        }

        for (int i=402; i<432; i+=x) {
            x++;
        }

        return p.x + x;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
    int TestTrapAfterMerge_C2(boolean cond, int x, int y) { return TestTrapAfterMerge(cond, x, y); }

    @DontCompile
    int TestTrapAfterMerge_Interp(boolean cond, int x, int y) { return TestTrapAfterMerge(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    Point testNestedObjectsObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // The allocation of "Picture" will be removed and only allocations of "Position" will be kept
    Point testNestedObjectsObject_C2(boolean cond, int x, int y) { return testNestedObjectsObject(cond, x, y); }

    @DontCompile
    Point testNestedObjectsObject_Interp(boolean cond, int x, int y) { return testNestedObjectsObject(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testNestedObjectsNoEscapeObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position.x;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" }, applyIf = {"UseCompressedOops", "true"} )
    @IR(failOn = { IRNode.ALLOC }, applyIf = {"UseCompressedOops", "false"} )
    // The two Picture objects will be removed. The nested Point objects won't
    // be removed, if CompressedOops is enabled, because the Phi merging them will
    // have a DecodeN user - which currently isn't supported.
    int testNestedObjectsNoEscapeObject_C2(boolean cond, int x, int y) { return testNestedObjectsNoEscapeObject(cond, x, y); }

    @DontCompile
    int testNestedObjectsNoEscapeObject_Interp(boolean cond, int x, int y) { return testNestedObjectsNoEscapeObject(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    Point[] testNestedObjectsArray(boolean cond, int x, int y) {
        PicturePositions p = new PicturePositions(x, y, x+y);

        if (cond) {
            p = new PicturePositions(x+1, y+1, x+y+1);
        }

        return p.positions;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "4" })
    // The two PicturePositions objects will be reduced and scaled.
    Point[] testNestedObjectsArray_C2(boolean cond, int x, int y) { return testNestedObjectsArray(cond, x, y); }

    @DontCompile
    Point[] testNestedObjectsArray_Interp(boolean cond, int x, int y) { return testNestedObjectsArray(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testTrappingAfterMerge(boolean cond, int x, int y) {
        Point p = new Point(x, y);
        int res = 0;

        if (cond) {
            p = new Point(y, y);
        }

        for (int i=832; i<932; i++) {
            res += p.x;
        }

        if (x > y) {
            res += new Point(p.x, p.y).x;
        }

        return res;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // The allocation inside the last if will be removed because it's not part of a merge
    // The other two allocations will be reduced and removed
    int testTrappingAfterMerge_C2(boolean cond, int x, int y) { return testTrappingAfterMerge(cond, x, y); }

    @DontCompile
    int testTrappingAfterMerge_Interp(boolean cond, int x, int y) { return testTrappingAfterMerge(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSimpleAliasedAlloc(boolean cond, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(y, x);
        Point p = p1;

        if (cond) {
            p = p2;
        }

        return p.x * p.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both merges will be reduced and removed
    int testSimpleAliasedAlloc_C2(boolean cond, int x, int y) { return testSimpleAliasedAlloc(cond, x, y); }

    @DontCompile
    int testSimpleAliasedAlloc_Interp(boolean cond, int x, int y) { return testSimpleAliasedAlloc(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSimpleDoubleMerge(boolean cond, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(x+1, y+1);

        if (cond) {
            p1 = new Point(y, x);
            p2 = new Point(y+1, x+1);
        }

        return p1.x + p2.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both merges will be reduced and removed
    int testSimpleDoubleMerge_C2(boolean cond, int x, int y) { return testSimpleDoubleMerge(cond, x, y); }

    @DontCompile
    int testSimpleDoubleMerge_Interp(boolean cond, int x, int y) { return testSimpleDoubleMerge(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testConsecutiveSimpleMerge(boolean cond1, boolean cond2, int x, int y) {
        Point p0 = new Point(x, x);
        Point p1 = new Point(x, y);
        Point pA = null;

        Point p2 = new Point(y, x);
        Point p3 = new Point(y, y);
        Point pB = null;

        if (cond1) {
            pA = p0;
        } else {
            pA = p1;
        }

        if (cond2) {
            pB = p2;
        } else {
            pB = p3;
        }

        return pA.x * pA.y + pB.x * pB.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // All allocations will be removed.
    int testConsecutiveSimpleMerge_C2(boolean cond1, boolean cond2, int x, int y) { return testConsecutiveSimpleMerge(cond1, cond2, x, y); }

    @DontCompile
    int testConsecutiveSimpleMerge_Interp(boolean cond1, boolean cond2, int x, int y) { return testConsecutiveSimpleMerge(cond1, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testDoubleIfElseMerge(boolean cond, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(x+1, y+1);

        if (cond) {
            p1 = new Point(y, x);
            p2 = new Point(y, x);
        } else {
            p1 = new Point(x, y);
            p2 = new Point(x+1, y+1);
        }

        return p1.x * p2.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // The initial allocation is always dead. The other
    // two will be reduced and scaled.
    int testDoubleIfElseMerge_C2(boolean cond, int x, int y) { return testDoubleIfElseMerge(cond, x, y); }

    @DontCompile
    int testDoubleIfElseMerge_Interp(boolean cond, int x, int y) { return testDoubleIfElseMerge(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testNoEscapeWithLoadInLoop(boolean cond, int x, int y) {
        Point p = new Point(x, y);
        int res = 0;

        if (cond) {
            p = new Point(y, x);
        }

        for (int i=3342; i<4234; i++) {
            res += p.x + p.y + i;
        }

        return res + p.x + p.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and scaled.
    int testNoEscapeWithLoadInLoop_C2(boolean cond, int x, int y) { return testNoEscapeWithLoadInLoop(cond, x, y); }

    @DontCompile
    int testNoEscapeWithLoadInLoop_Interp(boolean cond, int x, int y) { return testNoEscapeWithLoadInLoop(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCmpAfterMerge(boolean cond, boolean cond2, int x, int y) {
        Point a = new Point(x, y);
        Point b = new Point(y, x);
        Point c = null;

        if (x+2 >= y-5) {
            c = a;
        } else {
            c = b;
        }

        return cond2 ? c.x : c.y;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and scaled
    int testCmpAfterMerge_C2(boolean cond, boolean cond2, int x, int y) { return testCmpAfterMerge(cond, cond2, x, y); }

    @DontCompile
    int testCmpAfterMerge_Interp(boolean cond, boolean cond2, int x, int y) { return testCmpAfterMerge(cond, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCondAfterMergeWithAllocate(boolean cond1, boolean cond2, int x, int y) {
        Point p = new Point(x, y);

        if (cond1) {
            p = new Point(y, x);
        }

        if (cond2 && cond1) {
            return p.x;
        } else {
            return 321;
        }
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
    int testCondAfterMergeWithAllocate_C2(boolean cond1, boolean cond2, int x, int y) { return testCondAfterMergeWithAllocate(cond1, cond2, x, y); }

    @DontCompile
    int testCondAfterMergeWithAllocate_Interp(boolean cond1, boolean cond2, int x, int y) { return testCondAfterMergeWithAllocate(cond1, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testCondLoadAfterMerge(boolean cond1, boolean cond2, int x, int y) {
        Point p = new Point(x, y);

        if (cond1) {
            p = new Point(y, x);
        }

        if (cond1 == false && cond2 == false) {
            return p.x + 1;
        } else if (cond1 == false && cond2 == true) {
            return p.x + 30;
        } else if (cond1 == true && cond2 == false) {
            return p.x + 40;
        } else if (cond1 == true && cond2 == true) {
            return p.x + 50;
        } else {
            return -1;
        }
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
    int testCondLoadAfterMerge_C2(boolean cond1, boolean cond2, int x, int y) { return testCondLoadAfterMerge(cond1, cond2, x, y); }

    @DontCompile
    int testCondLoadAfterMerge_Interp(boolean cond1, boolean cond2, int x, int y) { return testCondLoadAfterMerge(cond1, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testIfElseInLoop() {
        int res = 0;

        for (int i=1; i<1000; i++) {
            Point obj = new Point(i, i);

            if (i % 2 == 1) {
                obj = new Point(i, i+1);
            } else {
                obj = new Point(i-1, i);
            }

            res += obj.x;
        }

        return res;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // The initial allocation is always dead. The other
    // two will be reduced and scaled.
    int testIfElseInLoop_C2() { return testIfElseInLoop(); }

    @DontCompile
    int testIfElseInLoop_Interp() { return testIfElseInLoop(); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testLoadInCondAfterMerge(boolean cond, int x, int y) {
        Point p = new Point(x, y);

        if (cond) {
            p = new Point(y, x);
        }

        if (p.x == 10) {
            if (p.y == 10) {
                return dummy(10);
            } else {
                return dummy(20);
            }
        } else if (p.x == 20) {
            if (p.y == 20) {
                return dummy(30);
            } else {
                return dummy(40);
            }
        }

        return 1984;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and removed.
    int testLoadInCondAfterMerge_C2(boolean cond, int x, int y) { return testLoadInCondAfterMerge(cond, x, y); }

    @DontCompile
    int testLoadInCondAfterMerge_Interp(boolean cond, int x, int y) { return testLoadInCondAfterMerge(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testLoadInLoop(boolean cond, int x, int y) {
        Point obj1 = new Point(x, y);
        Point obj2 = new Point(y, x);
        Point obj = null;
        int res = 0;

        if (cond) {
            obj = obj1;
        } else {
            obj = obj2;
        }

        for (int i = 0; i < 532; i++) {
            res += obj.x;
        }

        return res;
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and removed.
    int testLoadInLoop_C2(boolean cond, int x, int y) { return testLoadInLoop(cond, x, y); }

    @DontCompile
    int testLoadInLoop_Interp(boolean cond, int x, int y) { return testLoadInLoop(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testMergesAndMixedEscape(boolean cond, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(x, y);
        int val  = 0;

        if (cond) {
            p1 = new Point(x+1, y+1);
            val = dummy(p2);
        }

        return val + p1.x + p2.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    // p2 escapes and will remain. The other two allocations will be reduced and scaled.
    int testMergesAndMixedEscape_C2(boolean cond, int x, int y) { return testMergesAndMixedEscape(cond, x, y); }

    @DontCompile
    int testMergesAndMixedEscape_Interp(boolean cond, int x, int y) { return testMergesAndMixedEscape(cond, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSRAndNSR_NoTrap(boolean cond1, int x, int y) {
        Point p = new Point(x, y);

        if (cond1) {
            p = new Point(x+1, y+1);
            global_escape = p;
        }

        return p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "<=1" })
    int testSRAndNSR_NoTrap_C2(boolean cond1, int x, int y) { return testSRAndNSR_NoTrap(cond1, x, y); }

    @DontCompile
    int testSRAndNSR_NoTrap_Interp(boolean cond1, int x, int y) { return testSRAndNSR_NoTrap(cond1, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testSRAndNSR_Trap(boolean is_c2, boolean cond1, boolean cond2, int x, int y) {
        Point p = new Point(x, y);

        if (cond1) {
            p = new Point(x+1, y+1);
            global_escape = p;
        }

        int res = p.x;
        if (is_c2) {
            // This will show up to C2 as a trap.
            dummy_defaults();
        }

        return res;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "<=1" })
    int testSRAndNSR_Trap_C2(boolean is_c2, boolean cond1, boolean cond2, int x, int y) { return testSRAndNSR_Trap(is_c2, cond1, cond2, x, y); }

    @DontCompile
    int testSRAndNSR_Trap_Interp(boolean is_c2, boolean cond1, boolean cond2, int x, int y) { return testSRAndNSR_Trap(is_c2, cond1, cond2, x, y); }

    // -------------------------------------------------------------------------

    @ForceInline
    char testString_one(boolean cond1) {
        String p = new String("Java");

        if (cond1) {
            p = new String("HotSpot");
        }

        return p.charAt(0);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    char testString_one_C2(boolean cond1) { return testString_one(cond1); }

    @DontCompile
    char testString_one_Interp(boolean cond1) { return testString_one(cond1); }

    // -------------------------------------------------------------------------

    @ForceInline
    char testString_two(boolean cond1) {
        String p = new String("HotSpot");

        if (cond1) {
            p = dummy("String");
            if (p == null) return 'J';
        }

        return p.charAt(0);
    }

    @Test
    @IR(failOn = { IRNode.ALLOC })
    char testString_two_C2(boolean cond1) { return testString_two(cond1); }

    @DontCompile
    char testString_two_Interp(boolean cond1) { return testString_two(cond1); }

    // -------------------------------------------------------------------------

    @ForceInline
    Class testLoadKlassFromCast(boolean cond1) {
        Object p = new Circle(10);

        if (cond1) {
            p = dummy(1, 2);
        }

        return p.getClass();
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    // The allocation won't be reduced because we don't support [Narrow]Klass loads
    Class testLoadKlassFromCast_C2(boolean cond1) { return testLoadKlassFromCast(cond1); }

    @DontCompile
    Class testLoadKlassFromCast_Interp(boolean cond1) { return testLoadKlassFromCast(cond1); }

    // -------------------------------------------------------------------------

    @ForceInline
    Class testLoadKlassFromPhi(boolean cond1) {
        Shape p = new Square(20);

        if (cond1) {
            p = new Circle(10);
        }

        return p.getClass();
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" })
    // The allocation won't be reduced because we don't support [Narrow]Klass loads
    Class testLoadKlassFromPhi_C2(boolean cond1) { return testLoadKlassFromPhi(cond1); }

    @DontCompile
    Class testLoadKlassFromPhi_Interp(boolean cond1) { return testLoadKlassFromPhi(cond1); }

    // -------------------------------------------------------------------------

    @ForceInline
    int testReReduce(boolean cond, int x, int y) {
        Nested A = new Nested(x, y);
        Nested B = new Nested(y, x);
        Nested C = new Nested(y, x);
        Nested P = null;

        if (x == y) {
            A.other = B;
            P = A;
        } else if (x > y) {
            P = B;
        } else {
            C.other = B;
            P = C;
        }

        if (x == y)
            dummy_defaults();

        return P.x;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    // The last allocation won't be reduced because it would cause the creation
    // of a nested SafePointScalarMergeNode.
    int testReReduce_C2(boolean cond1, int x, int y) { return testReReduce(cond1, x, y); }

    @DontCompile
    int testReReduce_Interp(boolean cond1, int x, int y) { return testReReduce(cond1, x, y); }

    // ------------------ Utility for Testing ------------------- //

    @DontCompile
    static void dummy() {
    }

    @DontCompile
    static int dummy(Point p) {
        return p.x * p.y;
    }

    @DontCompile
    static int dummy(int x) {
        return x;
    }

    @DontCompile
    static Point dummy(int x, int y) {
        return new Point(x, y);
    }

    @DontCompile
    static String dummy(String str) {
        return str;
    }

    @DontCompile
    static ADefaults dummy_defaults() {
        return new ADefaults();
    }

    static class Nested {
        int x, y;
        Nested other;
        Nested(int x, int y) {
            this.x = x;
            this.y = y;
            this.other = null;
        }
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return (p.x == x) && (p.y == y);
        }
    }

    class Shape {
        int x, y, l;
        Shape(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    class Square extends Shape {
        Square(int l) {
            super(0, 0);
            this.l = l;
        }
    }

    class Circle extends Shape {
        Circle(int l) {
            super(0, 0);
            this.l = l;
        }
    }

    static class ADefaults {
        static int ble;
        int i;
        @DontCompile
        ADefaults(int i) { this.i = i; }
        @DontCompile
        ADefaults() { }
    }

    static class Picture {
        public int id;
        public Point position;

        public Picture(int id, int x, int y) {
            this.id = id;
            this.position = new Point(x, y);
        }
    }

    static class PicturePositions {
        public int id;
        public Point[] positions;

        public PicturePositions(int id, int x, int y) {
            this.id = id;
            this.positions = new Point[] { new Point(x, y), new Point(y, x) };
        }
    }

    class Root {
        public int a;
        public int b;
        public int c;
        public int d;
        public int e;

        public Root(int a, int b, int c, int d, int e) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }
    }

    class Usr extends Root {
        public float flt;

        public Usr(float a, float b, float c) {
            super((int)a, (int)b, (int)c, 0, 0);
            this.flt = a;
        }
    }

    class Home extends Root {
        public double[] arr;

        public Home(double a, double b) {
            super((int)a, (int)b, 0, 0, 0);
            this.arr = new double[] {a, b};
        }

    }

    class Tmp extends Root {
        public String s;

        public Tmp(String s) {
            super((int)s.length(), 0, 0, 0, 0);
            this.s = s;
        }
    }

    class Etc extends Root {
        public String a;

        public Etc(String s) {
            super((int)s.length(), 0, 0, 0, 0);
            this.a = s;
        }
    }

    class A { }
    class B { }
    class C { }
    class D { }
    class E { }
    class F { }
    class G { }
}
