/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run driver compiler.c2.irTests.scalarReplacement.AllocationMergesNestedPhiTests
 */
public class AllocationMergesNestedPhiTests {
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

    @Run(test = {
                 "testRematerialize_SingleObj_C2",
                 "testRematerialize_TryCatch_C2",
                 "testMerge_TryCatchFinally_C2",
                 "testRematerialize_MultiObj_C2",
                 "testGlobalEscapeInThread_C2",
                 "testGlobalEscapeInThreadWithSync_C2",
                 "testFieldEscapeWithMerge_C2",
                 "testNestedPhi_FieldLoad_C2",
                 "testThreeLevelNestedPhi_C2",
                 "testNestedPhiProcessOrder_C2",
                 "testNestedPhi_TryCatch_C2",
                 "testBailOut_C2",
                 "testNestedPhiPolymorphic_C2",
                 "testNestedPhiWithTrap_C2",
                 "testNestedPhiWithLambda_C2",
                 "testMultiParentPhi_C2"
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
        try {
            Asserts.assertEQ(testRematerialize_SingleObj_Interp(cond1, x, y),       testRematerialize_SingleObj_C2(cond1, x, y));
        } catch (Exception e) {}
        Asserts.assertEQ(testRematerialize_TryCatch_Interp(cond1, l, x, y),         testRematerialize_TryCatch_C2(cond1, l, x, y));
        Asserts.assertEQ(testMerge_TryCatchFinally_Interp(cond1, l, x, y),          testMerge_TryCatchFinally_C2(cond1, l, x, y));
        Asserts.assertEQ(testRematerialize_MultiObj_Interp(cond1, cond2, x, y),     testRematerialize_MultiObj_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testGlobalEscapeInThread_Intrep(cond1, l, x, y),           testGlobalEscapeInThread_C2(cond1, l, x, y));
        Asserts.assertEQ(testGlobalEscapeInThreadWithSync_Intrep(cond1, x, y),      testGlobalEscapeInThreadWithSync_C2(cond1, x, y));
        Asserts.assertEQ(testFieldEscapeWithMerge_Intrep(cond1, x, y),              testFieldEscapeWithMerge_C2(cond1, x, y));
        Asserts.assertEQ(testNestedPhi_FieldLoad_Interp(cond1, cond2, x, y),        testNestedPhi_FieldLoad_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testThreeLevelNestedPhi_Interp(cond1, cond2, x, y),        testThreeLevelNestedPhi_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNestedPhiProcessOrder_Interp(cond1, cond2, x, y),      testNestedPhiProcessOrder_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNestedPhi_TryCatch_Interp(cond1, cond2, x, y),         testNestedPhi_TryCatch_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testBailOut_Interp(cond1, cond2, x, y),                    testBailOut_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNestedPhiPolymorphic_Interp(cond1, cond2, x, y),       testNestedPhiPolymorphic_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNestedPhiWithTrap_Interp(cond1, cond2, x, y),          testNestedPhiWithTrap_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testNestedPhiWithLambda_Interp(cond1, cond2, x, y),        testNestedPhiWithLambda_C2(cond1, cond2, x, y));
        Asserts.assertEQ(testMultiParentPhi_Interp(cond1, x, y),             testMultiParentPhi_C2(cond1, x, y));
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testRematerialize_SingleObj(boolean cond1, int x, int y) throws Exception {
        Point p = new Point(x, y);

        if (cond1) {
            p = new Point(x+1, y+1);
            global_escape = p;
        }

        if (!cond1)
            throw new Exception();

        return p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=1", IRNode.SAFEPOINT_SCALAR_MERGE, ">=1"}, phase = CompilePhase.ITER_GVN_AFTER_EA)
    int testRematerialize_SingleObj_C2(boolean cond1,int x, int y) throws Exception { return testRematerialize_SingleObj(cond1, x, y); }

    @DontCompile
    int testRematerialize_SingleObj_Interp(boolean cond1, int x, int y) throws Exception { return testRematerialize_SingleObj(cond1, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    int testRematerialize_TryCatch(boolean cond1, int n, int x, int y) {
        Point p = new Point(x, y);
        if (cond1) {
            p = new Point(x+1, y+1);
            global_escape = p;
        }
        try {
            p.y = n/0;
        } catch (Exception e) {}

        return p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=1", IRNode.SAFEPOINT_SCALAR_MERGE, ">=1",  IRNode.SAFEPOINT_SCALAR_OBJECT, ">=2"}, phase = CompilePhase.ITER_GVN_AFTER_EA)
    int testRematerialize_TryCatch_C2(boolean cond1, int n, int x, int y) { return testRematerialize_TryCatch(cond1, n, x, y); }

    @DontCompile
    int testRematerialize_TryCatch_Interp(boolean cond1, int n, int x, int y) { return testRematerialize_TryCatch(cond1, n, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    int testMerge_TryCatchFinally(boolean cond1, int n, int x, int y) {

        Point p = new Point(x, y);
        try {
            if (cond1) {
                p = new Point(x+1, y+1);
            }
        } catch (Exception e) {
            p.y = n;
        } finally {
            dummy_defaults();
            p = new Point(n, x+y);
        }

        return p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=2"}, phase = CompilePhase.ITER_GVN_AFTER_EA)
    int testMerge_TryCatchFinally_C2(boolean cond1, int n, int x, int y) { return testMerge_TryCatchFinally(cond1, n, x, y); }

    @DontCompile
    int testMerge_TryCatchFinally_Interp(boolean cond1, int n, int x, int y) { return testMerge_TryCatchFinally(cond1, n, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    int testRematerialize_MultiObj(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = new Point(x+2, y+4);

        if (cond1) {
            p1 = new Point(x+1, y+1);
            global_escape = p1;
        }

        if (x%2 == 1) {
            p2 = new Point(x*2, y/4);
        }

        try {
            String s = null;
            s.length();
        } catch (Exception e) {}

        if (cond2)
            return p1.y;
        return p2.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=1", IRNode.SAFEPOINT_SCALAR_MERGE, ">=1", IRNode.SAFEPOINT_SCALAR_OBJECT, ">=2"}, phase= CompilePhase.ITER_GVN_AFTER_EA)
    int testRematerialize_MultiObj_C2(boolean cond1, boolean cond2, int x, int y) { return testRematerialize_MultiObj(cond1, cond2, x, y); }

    @DontCompile
    int testRematerialize_MultiObj_Interp(boolean cond1, boolean cond2, int x, int y) { return testRematerialize_MultiObj(cond1, cond2, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    public int testGlobalEscapeInThread(boolean cond, int n, int x, int y) {
        Point p = new Point(x, y);
        Object syncObject = new Object();
        Runnable threadLoop = () -> {
            if (cond)
                global_escape = new Point( x+n, y+n);
        };
        Thread thLoop = new Thread(threadLoop);
        thLoop.start();
        try {
            thLoop.join();
        } catch (InterruptedException e) {}

        if (cond && n % 2 == 1)
            p.x = global_escape.x;

        return p.y;
    }

    @DontCompile
    int testGlobalEscapeInThread_Intrep(boolean cond1, int n, int x, int y) { return testGlobalEscapeInThread(cond1, n, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=4"}, phase= CompilePhase.ITER_GVN_AFTER_EA)
    int testGlobalEscapeInThread_C2(boolean cond1, int n, int x, int y) { return testGlobalEscapeInThread(cond1, n, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    public int testGlobalEscapeInThreadWithSync(boolean cond, int x, int y) {
        Point p = new Point(x, y);
        for (int i = 0; i < 2; i++) {
            if (cond)
                p = new Point(x+i, y+i);
            TestThread th = new TestThread(p);
            th.start();
        }
        return p.y;
    }

    @DontCompile
    int testGlobalEscapeInThreadWithSync_Intrep(boolean cond1, int x, int y) { return testGlobalEscapeInThreadWithSync(cond1, x, y); }

    @Test
    int testGlobalEscapeInThreadWithSync_C2(boolean cond1, int x, int y) { return testGlobalEscapeInThreadWithSync(cond1, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    public int testFieldEscapeWithMerge(boolean cond, int x, int y) {

        Point p1 = new Point(x, y);
        Point p2 = new Point(x+y, x*y);
        Line ln = new Line(p1, p2);
        if (cond) {
            ln.p1 = new Point(x-y, x/y);
            global_escape = ln.p2;
        }
        return ln.p1.y;
    }

    @DontCompile
    int testFieldEscapeWithMerge_Intrep(boolean cond1, int x, int y) { return testFieldEscapeWithMerge(cond1, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, ">=1"}, phase= CompilePhase.ITER_GVN_AFTER_EA)
    int testFieldEscapeWithMerge_C2(boolean cond1, int x, int y) { return testFieldEscapeWithMerge(cond1, x, y); }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @ForceInline
    int testNestedPhi_FieldLoad(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        if (cond1) {
            p1 = new Point(x+30, y+40);
        }
        Point p2 = p1;
        if (cond2) {
          p2 = new Point(x+50, y+60);
        }
        return  p2.x + p2.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhi_FieldLoad_C2(boolean cond1, boolean cond2, int x, int y) {
       return testNestedPhi_FieldLoad(cond1, cond2, x, y);
    }

    @DontCompile
    int testNestedPhi_FieldLoad_Interp(boolean cond1, boolean cond2, int x, int y) {
       return testNestedPhi_FieldLoad(cond1, cond2, x, y);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testThreeLevelNestedPhi_Interp(boolean cond1, boolean cond2, int x, int y) { return testThreeLevelNestedPhi(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "2" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testThreeLevelNestedPhi_C2(boolean cond1, boolean cond2, int x, int y) { return testThreeLevelNestedPhi(cond1, cond2, x, y); }

    @ForceInline
    int testThreeLevelNestedPhi(boolean cond1, boolean cond2, int x, int y) {

        Point p1 = new Point(x, y);
        if (cond1) {
            p1 = new Point(x, y);
        }

        Point p2 = p1;
        if (cond2) {
            p2 = new Point(x, y);
        }

        Point p3 = p2;
        if (cond1 && cond2) {
            p3 = new Point(x, y);
        }
        return  p3.x + p3.y;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testNestedPhiProcessOrder_Interp(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiProcessOrder(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhiProcessOrder_C2(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiProcessOrder(cond1, cond2, x, y); }

    @ForceInline
    // make sure the child phis are processed fist
    int testNestedPhiProcessOrder(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = p1;
        if (cond1)
            p1 = new Point(x, y);

        if (cond2)
            p2 = p1;

       return p2.x;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testNestedPhi_TryCatch_Interp(boolean cond1, boolean cond2, int x, int y) { return testNestedPhi_TryCatch(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "2" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhi_TryCatch_C2(boolean cond1, boolean cond2, int x, int y) { return testNestedPhi_TryCatch(cond1, cond2, x, y); }

    @ForceInline
    int testNestedPhi_TryCatch(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = p1;
        try {
            if (cond1)
            p1 = new Point(x, y);
            if (cond2)
                p2 = p1;
        } catch (Exception e) {
            p2 = new Point (x, y);
        }
        return p2.x;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testBailOut_Interp(boolean cond1, boolean cond2, int x, int y) { return testBailOut(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testBailOut_C2(boolean cond1, boolean cond2, int x, int y) { return testBailOut(cond1, cond2, x, y); }

    @ForceInline
    int testBailOut(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        Point p2 = p1;
        if (cond1)
          p1 = new Point(x, y);

        if (cond2)
          p2 = new Point(x, y);

        try {
            if (cond1 && cond2)
                throw new Exception();
        } catch (Exception e) {}

        return p2.getX();
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testNestedPhiPolymorphic_Interp(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiPolymorphic(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhiPolymorphic_C2(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiPolymorphic(cond1, cond2, x, y); }

    @ForceInline
    int testNestedPhiPolymorphic(boolean cond1, boolean cond2, int x, int l) {
        Shape obj1 = new Square(l);
        if (cond1)
            obj1 = new Circle(l);
        Shape obj2 = obj1;
        if (cond2)
            obj2 = new Circle(x + l/5);
        return obj2.l;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @DontCompile
    int testNestedPhiWithTrap_Interp(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiWithTrap(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "3" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "2" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhiWithTrap_C2(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiWithTrap(cond1, cond2, x, y); }

    @ForceInline
    int testNestedPhiWithTrap(boolean cond1, boolean cond2, int x, int y) {
        Point p1 = new Point(x, y);
        if (cond1)
            p1 = new Point(x, y);

        Point p2 = p1;
        dummy();
        if (cond2)
            p2 = new Point(x, y);

        return p2.x;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

   @DontCompile
    int testNestedPhiWithLambda_Interp(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiWithLambda(cond1, cond2, x, y); }

    @Test
    @IR(counts = { IRNode.ALLOC, "4" }, phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
    @IR(counts = { IRNode.ALLOC, "0" }, phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
    int testNestedPhiWithLambda_C2(boolean cond1, boolean cond2, int x, int y) { return testNestedPhiWithLambda(cond1, cond2, x, y); }

    @ForceInline
    public int testNestedPhiWithLambda(boolean cond1, boolean cond2, int x, int y) {
        Point1 p1 = new Point1(x, y);
        if (cond1)
            p1 = new Point1(x, y);

        Point1 p2 = p1;
        PointSupplier ps = () -> (cond2? (new Point1(x, y)) : (new Point1(x+70, y+80)));
        if (cond2)
            p2 = ps.getPoint();
        return p2.x;
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------
    @DontCompile
    int testMultiParentPhi_Interp(boolean cond1, int x, int y) { return testMultiParentPhi(cond1, x, y); }

    @Test
    int testMultiParentPhi_C2(boolean cond1, int x, int y) { return testMultiParentPhi(cond1, x, y); }

    @ForceInline
    public static int testMultiParentPhi(boolean cond1, int x, int y) {
        Point p1 = new Point(x, y);
        Point p3 = new Point(x+30, y+31);
        if (cond1) {
          p1 = new Point(x+12, y+13);
          p3 = new Point(x+32, y+33);
        }
        Point p2 = new Point(x+20, y+21);
        try {
           Point p4 = new Point(x+40, y+41);
        } catch (NullPointerException ne) {
            p2 = p3;
        } catch (Exception e) {
            p2 = p1;
        }
        return p2.x;
    }

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

        int getX() {
            return x;
        }
    }

   class Line {
       Point p1, p2;
       Line(Point p1, Point p2) {
           this.p1 = p1;
           this.p2 = p2;
       }
    }

    interface PointSupplier {
        Point1 getPoint();
    }

   class Point1 implements PointSupplier {
       int x, y;
       Point1(int x, int y) {
           this.x = x;
           this.y = y;
       }
       public Point1 getPoint() {
           return this;
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

    class TestThread extends Thread {
        private static Object syncObject = new Object();
        Point p;

        TestThread(Point p) {
            this.p = p;
        }

        public void run() {
            try {
                synchronized(syncObject) {
                    p = new Point(1,1);
                    global_escape = p;
                }
            } catch(Exception e){}
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
