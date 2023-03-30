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
package compiler.c2.irTests.scalarReplacement;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8281429
 * @summary Tests that C2 can correctly scalar replace some object allocation merges.
 * @library /test/lib /
 * @requires vm.bits == 64 & vm.compiler2.enabled & vm.opt.final.UseCompressedOops & vm.opt.final.EliminateAllocations
 * @run driver compiler.c2.irTests.scalarReplacement.AllocationMergesTests
 */
public class AllocationMergesTests {
    private static Point global_escape = new Point(2022, 2023);

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+ReduceAllocationMerges",
                                   "-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:+TraceReduceAllocationMerges",
                                   "-XX:CompileCommand=exclude,*::dummy*");
    }

    // ------------------ No Scalar Replacement Should Happen in The Tests Below ------------------- //

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    int testGlobalEscape(int x, int y) {
        Point p = new Point(x, y);

        AllocationMergesTests.global_escape = p;

        return p.x * p.y;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    int testArgEscape(int x, int y) {
        Point p = new Point(x, y);

        int val = dummy(p);

        return val + p.x + p.y;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because of the write to the fields
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because of the write to the field
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because the inputs to the Phi have different Klasses
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // Merge won't be reduced because write to one of the inputs *after* the merge
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "3" })
    // Objects won't be scalar replaced because:
    //  - p is written inside the loop.
    //  - p2 is ArgEscape
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
    @Arguments({ Argument.BOOLEAN_TOGGLE_FIRST_FALSE, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // The allocations won't be removed because 'split_through_phi' won't split the load through the bases.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    // The merge won't be simplified because the merge with NULL
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations should be removed
    int testLoadAfterLoopAlias(boolean cond, int x, int y) {
        Point a = new Point(x, y);
        Point b = new Point(y, x);
        Point c = a;

        for (int i=10; i<232; i++) {
            if (i == x) {
                c = b;
            }
        }

        return cond ? c.x : c.y;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.CALL, "3" })
    // Merge won't be reduced because both of the inputs are NSR
    int testCallTwoSide(boolean cond1, int x, int y) {
        Point p = dummy(x, y);

        if (cond1) {
            p = dummy(y, x);
        }

        return p.x;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "3" })
    // p2 escapes and therefore won't be removed.
    // The allocations won't be removed because 'split_through_phi' won't split the load through the bases.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    // Merge won't be reduced because, among other things, one of the inputs is null.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.NUMBER_42, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    // The allocation won't be removed because the merge doesn't have exact type
    int testObjectIdentity(boolean cond, int x, int y) {
        Point o = new Point(x, y);

        if (cond && x == 42) {
            o = global_escape;
        }

        return o.x + o.y;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // The initial allocation assigned to 's' will always be dead.
    // The other two allocations assigned to 's' won't be removed because they have different type.
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

        dummy();

        return s.a;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // The unused allocation will be removed.
    // The other two allocations assigned to 's' won't be removed because they have different type.
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


    // ------------------ Some Scalar Replacement Should Happen in The Tests Below ------------------- //

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // all allocations will be dead
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be removed. After initialization they are read-only objects.
    // Access to the input of the merge, after the merge, is fine.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Initial p3 will always be dead.
    // The other two allocations will be reduced and scaled
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" })
    // The allocation of "Picture" will be removed and only allocations of "Position" will be kept
    Point testNestedObjectsObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "2" } )
    // The two Picture objects will be removed. The nested Point objects won't
    // be removed because the Phi merging them will have a DecodeN user - which
    // currently isn't supported.
    int testNestedObjectsNoEscapeObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position.x;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "4" })
    // The two PicturePositions objects will be reduced and scaled.
    Point[] testNestedObjectsArray(boolean cond, int x, int y) {
        PicturePositions p = new PicturePositions(x, y, x+y);

        if (cond) {
            p = new PicturePositions(x+1, y+1, x+y+1);
        }

        return p.positions;
    }

    @Test
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // The allocation inside the last if will be removed because it's not part of a merge
    // The other two allocations will be reduced and removed
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both merges will be reduced and removed
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both merges will be reduced and removed
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // All allocations will be removed.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // The initial allocation is always dead. The other
    // two will be reduced and scaled.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and scaled.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and scaled
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be eliminated.
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
    // The initial allocation is always dead. The other
    // two will be reduced and scaled.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and removed.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(failOn = { IRNode.ALLOC })
    // Both allocations will be reduced and removed.
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
    @Arguments({ Argument.RANDOM_EACH, Argument.RANDOM_EACH, Argument.RANDOM_EACH })
    @IR(counts = { IRNode.ALLOC, "1" })
    // p2 escapes and will remain. The other two allocations will be reduced and scaled.
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
    static ADefaults dummy_defaults() {
        return new ADefaults();
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
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
