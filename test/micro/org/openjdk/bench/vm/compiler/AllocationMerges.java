/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public abstract class AllocationMerges {
    private static final int SIZE        = 1000000;
    private static final boolean cond1[] = new boolean[SIZE];
    private static final boolean cond2[] = new boolean[SIZE];
    private static final int ws[]        = new int[SIZE];
    private static final int xs[]        = new int[SIZE];
    private static final int ys[]        = new int[SIZE];
    private static final int zs[]        = new int[SIZE];
    private static Load global_escape   = new Load(2022, 2023);
    private RandomGenerator rng          = RandomGeneratorFactory.getDefault().create();

    // -------------------------------------------------------------------------

    @Setup
    public void setup() {
        for (int i = 0; i < SIZE; i++) {
            cond1[i] = i % 2 == 0;
            cond2[i] = i % 2 == 1;

            ws[i] = rng.nextInt();
            xs[i] = rng.nextInt();
            ys[i] = rng.nextInt();
            zs[i] = rng.nextInt();
        }
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testGlobalEscape(int x, int y) {
        Load p = new Load(x, y);

        AllocationMerges.global_escape = p;

        return p.x * p.y;
    }

    @Benchmark
    public void testGlobalEscape_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testGlobalEscape(xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testArgEscape(int x, int y) {
        Load p = new Load(x, y);

        int val = dummy(p);

        return val + p.x + p.y;
    }

    @Benchmark
    public void testArgEscape_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testArgEscape(xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testEscapeInCallAfterMerge(boolean cond, boolean cond2, int x, int y) {
        Load p = new Load(x, x);

        if (cond) {
            p = new Load(y, y);
        }

        if (cond2) {
            dummy(p);
        }

        return p.x * p.y;
    }

    @Benchmark
    public void testEscapeInCallAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testEscapeInCallAfterMerge(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testNoEscapeWithWriteInLoop(boolean cond, boolean cond2, int x, int y) {
        Load p = new Load(x, y);
        int res = 0;

        if (cond) {
            p = new Load(y, x);
        }

        for (int i=0; i<100; i++) {
            p.x += p.y + i;
            p.y += p.x + i;
        }

        return res + p.x + p.y;
    }

    @Benchmark
    public void testNoEscapeWithWriteInLoop_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNoEscapeWithWriteInLoop(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testPollutedWithWrite_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testPollutedWithWrite(cond1[i], xs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testPollutedPolymorphic_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testPollutedPolymorphic(cond1[i], xs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testMergedLoadAfterDirectStore(boolean cond, int x, int y) {
        Load p0 = new Load(x, x);
        Load p1 = new Load(y, y);
        Load p = null;

        if (cond) {
            p = p0;
        } else {
            p = p1;
        }

        p0.x = x * y;

        return p.x;
    }

    @Benchmark
    public void testMergedLoadAfterDirectStore_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMergedLoadAfterDirectStore(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testMergedAccessAfterCallWithWrite(boolean cond, int x, int y) {
        Load p2 = new Load(x, x);
        Load p = new Load(y, y);

        p.x = p.x * y;

        if (cond) {
            p = new Load(x, x);
        }

        dummy(p2);

        for (int i=3; i<324; i++) {
            p.x += i * x;
        }

        return p.x;
    }

    @Benchmark
    public void testMergedAccessAfterCallWithWrite_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMergedAccessAfterCallWithWrite(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testLoadAfterTrap(boolean cond, int x, int y) {
        Load p = null;

        if (cond) {
            p = new Load(x, x);
        } else {
            p = new Load(y, y);
        }

        dummy(x+y);

        return p.x + p.y;
    }

    @Benchmark
    public void testLoadAfterTrap_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testLoadAfterTrap(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCondAfterMergeWithNull(boolean cond1, boolean cond2, int x, int y) {
        Load p = null;

        if (cond1) {
            p = new Load(y, x);
        }

        if (cond2 && cond1) {
            return p.x;
        } else {
            return 321;
        }
    }

    @Benchmark
    public void testCondAfterMergeWithNull_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCondAfterMergeWithNull(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testLoadAfterLoopAlias(boolean cond, int x, int y) {
        Load a = new Load(x, y);
        Load b = new Load(y, x);
        Load c = a;

        for (int i=10; i<232; i++) {
            if (i == x) {
                c = b;
            }
        }

        return cond ? c.x : c.y;
    }

    @Benchmark
    public void testLoadAfterLoopAlias_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testLoadAfterLoopAlias(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCallTwoSide(boolean cond1, int x, int y) {
        Load p = dummy(x, y);

        if (cond1) {
            p = dummy(y, x);
        }

        return (p != null) ? p.x : 0;
    }

    @Benchmark
    public void testCallTwoSide_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCallTwoSide(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testMergedAccessAfterCallNoWrite(boolean cond, int x, int y) {
        Load p2 = new Load(x, x);
        Load p = new Load(y, y);
        int res = 0;

        p.x = p.x * y;

        if (cond) {
            p = new Load(y, y);
        }

        dummy(p2);

        for (int i=3; i<324; i++) {
            res += p.x + i * x;
        }

        return res;
    }

    @Benchmark
    public void testMergedAccessAfterCallNoWrite_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMergedAccessAfterCallNoWrite(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCmpMergeWithNull_Second(boolean cond, int x, int y) {
        Load p = null;

        if (cond) {
            p = new Load(x*x, y*y);
        }

        dummy(x);

        if (p != null) {
            return p.x * p.y;
        } else {
            return 1984;
        }
    }

    @Benchmark
    public void testCmpMergeWithNull_Second_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCmpMergeWithNull_Second(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testObjectIdentity(boolean cond, int x, int y) {
        Load o = new Load(x, y);

        if (cond && x == 42) {
            o = global_escape;
        }

        return o.x + o.y;
    }

    @Benchmark
    public void testObjectIdentity_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testObjectIdentity(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testSubclassesTrapping_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSubclassesTrapping(cond1[i], cond2[i], xs[i], ys[i], ws[i], zs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCmpMergeWithNull(boolean cond, int x, int y) {
        Load p = null;

        if (cond) {
            p = new Load(x*x, y*y);
        } else if (x > y) {
            p = new Load(x+y, x*y);
        }

        if (p != null) {
            return p.x * p.y;
        } else {
            return 1984;
        }
    }

    @Benchmark
    public void testCmpMergeWithNull_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCmpMergeWithNull(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testSubclasses_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSubclasses(cond1[i], cond2[i], xs[i], ys[i], ws[i], zs[i]);
        }
        bh.consume(result);
    }

    // ------------------ Some Scalar Replacement Should Happen in The Tests Below ------------------- //

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testPartialPhis(boolean cond, int l, int x, int y) {
        int k = l;

        if (l == 0) {
            k = l + 1;
        } else if (l == 2) {
            k = l + 2;
        } else if (l == 3) {
            new Load(x, y);
        } else if (l == 4) {
            new Load(y, x);
        }

        return k;
    }

    @Benchmark
    public void testPartialPhis_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testPartialPhis(cond1[i], xs[i], ys[i], zs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testPollutedNoWrite_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testPollutedNoWrite(cond1[i], xs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testThreeWayAliasedAlloc(boolean cond, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(x+1, y+1);
        Load p3 = new Load(x+2, y+2);

        if (cond) {
            p3 = p1;
        } else {
            p3 = p2;
        }

        return p3.x + p3.y;
    }

    @Benchmark
    public void testThreeWayAliasedAlloc_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testThreeWayAliasedAlloc(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int TestTrapAfterMerge(boolean cond, int x, int y) {
        Load p = new Load(x, x);

        if (cond) {
            p = new Load(y, y);
        }

        for (int i=402; i<432; i+=x) {
            x++;
        }

        return p.x + x;
    }

    @Benchmark
    public void TestTrapAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += TestTrapAfterMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    Load testNestedObjectsObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position;
    }

    @Benchmark
    public void testNestedObjectsObject_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedObjectsObject(cond1[i], xs[i], ys[i]).x;
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testNestedObjectsNoEscapeObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position.x;
    }

    @Benchmark
    public void testNestedObjectsNoEscapeObject_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedObjectsNoEscapeObject(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    Load[] testNestedObjectsArray(boolean cond, int x, int y) {
        PicturePositions p = new PicturePositions(x, y, x+y);

        if (cond) {
            p = new PicturePositions(x+1, y+1, x+y+1);
        }

        return p.positions;
    }

    @Benchmark
    public void testNestedObjectsArray_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            Load[] partial = testNestedObjectsArray(cond1[i], xs[i], ys[i]);
            result += partial[0].x;
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testTrappingAfterMerge(boolean cond, int x, int y) {
        Load p = new Load(x, y);
        int res = 0;

        if (cond) {
            p = new Load(y, y);
        }

        for (int i=832; i<932; i++) {
            res += p.x;
        }

        if (x > y) {
            res += new Load(p.x, p.y).x;
        }

        return res;
    }

    @Benchmark
    public void testTrappingAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testTrappingAfterMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testSimpleAliasedAlloc(boolean cond, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(y, x);
        Load p = p1;

        if (cond) {
            p = p2;
        }

        return p.x * p.y;
    }

    @Benchmark
    public void testSimpleAliasedAlloc_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSimpleAliasedAlloc(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testSimpleDoubleMerge(boolean cond, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(x+1, y+1);

        if (cond) {
            p1 = new Load(y, x);
            p2 = new Load(y+1, x+1);
        }

        return p1.x + p2.y;
    }

    @Benchmark
    public void testSimpleDoubleMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSimpleDoubleMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testConsecutiveSimpleMerge(boolean cond1, boolean cond2, int x, int y) {
        Load p0 = new Load(x, x);
        Load p1 = new Load(x, y);
        Load pA = null;

        Load p2 = new Load(y, x);
        Load p3 = new Load(y, y);
        Load pB = null;

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

    @Benchmark
    public void testConsecutiveSimpleMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testConsecutiveSimpleMerge(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testDoubleIfElseMerge(boolean cond, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(x+1, y+1);

        if (cond) {
            p1 = new Load(y, x);
            p2 = new Load(y, x);
        } else {
            p1 = new Load(x, y);
            p2 = new Load(x+1, y+1);
        }

        return p1.x * p2.y;
    }

    @Benchmark
    public void testDoubleIfElseMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testDoubleIfElseMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testNoEscapeWithLoadInLoop(boolean cond, int x, int y) {
        Load p = new Load(x, y);
        int res = 0;

        if (cond) {
            p = new Load(y, x);
        }

        for (int i=3342; i<4234; i++) {
            res += p.x + p.y + i;
        }

        return res + p.x + p.y;
    }

    @Benchmark
    public void testNoEscapeWithLoadInLoop_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNoEscapeWithLoadInLoop(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCmpAfterMerge(boolean cond, boolean cond2, int x, int y) {
        Load a = new Load(x, y);
        Load b = new Load(y, x);
        Load c = null;

        if (x+2 >= y-5) {
            c = a;
        } else {
            c = b;
        }

        return cond2 ? c.x : c.y;
    }

    @Benchmark
    public void testCmpAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCmpAfterMerge(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCondAfterMergeWithAllocate(boolean cond1, boolean cond2, int x, int y) {
        Load p = new Load(x, y);

        if (cond1) {
            p = new Load(y, x);
        }

        if (cond2 && cond1) {
            return p.x;
        } else {
            return 321;
        }
    }

    @Benchmark
    public void testCondAfterMergeWithAllocate_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCondAfterMergeWithAllocate(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testCondLoadAfterMerge(boolean cond1, boolean cond2, int x, int y) {
        Load p = new Load(x, y);

        if (cond1) {
            p = new Load(y, x);
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

    @Benchmark
    public void testCondLoadAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testCondLoadAfterMerge(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testIfElseInLoop(int x, int y, int w, int z) {
        int res = 0;

        for (int i=x; i<y; i++) {
            Load obj = new Load(w, z);

            if (i % 2 == 1) {
                obj = new Load(i, i+1);
            } else {
                obj = new Load(i-1, i);
            }

            res += obj.x;
        }

        return res;
    }

    @Benchmark
    public void testIfElseInLoop_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testIfElseInLoop(xs[i] % 100, ys[i] % 100, ws[i], zs[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testLoadInCondAfterMerge(boolean cond, int x, int y) {
        Load p = new Load(x, y);

        if (cond) {
            p = new Load(y, x);
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

    @Benchmark
    public void testLoadInCondAfterMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testLoadInCondAfterMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testLoadInLoop(boolean cond, int x, int y) {
        Load obj1 = new Load(x, y);
        Load obj2 = new Load(y, x);
        Load obj = null;
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

    @Benchmark
    public void testLoadInLoop_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testLoadInLoop(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testMergesAndMixedEscape(boolean cond, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(x, y);
        int val = 0;

        if (cond) {
            p1 = new Load(x+1, y+1);
            val = dummy(p2);
        }

        return val + p1.x + p2.y;
    }

    @Benchmark
    public void testMergesAndMixedEscape_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMergesAndMixedEscape(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testSRAndNSR_NoTrap(boolean cond1, int x, int y) {
        Load p = new Load(x, y);

        if (cond1) {
            p = new Load(x+1, y+1);
            global_escape = p;
        }

        return p.y;
    }

    @Benchmark
    public void testSRAndNSR_NoTrap_caller(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSRAndNSR_NoTrap(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testSRAndNSR_Trap(boolean is_c2, boolean cond1, boolean cond2, int x, int y) {
        Load p = new Load(x, y);

        if (cond1) {
            p = new Load(x+1, y+1);
            global_escape = p;
        }

        int res = p.x;
        if (is_c2) {
            // This will show up to C2 as a trap.
            dummy_defaults();
        }

        return res;
    }

    @Benchmark
    public void testSRAndNSR_Trap_caller(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testSRAndNSR_Trap(true, cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    char testString_one(boolean cond1) {
        String p = new String("Java");

        if (cond1) {
            p = new String("HotSpot");
        }

        return p.charAt(0);
    }

    @Benchmark
    public void testString_one_caller(Blackhole bh) {
        char result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testString_two(cond1[i]);
        }
        bh.consume(result);
    }

    // -------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private char testString_two(boolean cond1) {
        String p = new String("HotSpot");

        if (cond1) {
            p = dummy("String");
            if (p == null) return 'J';
        }

        return p.charAt(0);
    }

    @Benchmark
    public void testString_two_caller(Blackhole bh) {
        char result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testString_two(cond1[i]);
        }
        bh.consume(result);
    }

    // ------------------ Utility for Benchmarking ------------------- //

    @Fork(value = 3, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UseTLAB",
        "-XX:-ReduceAllocationMerges",
    })
    public static class NopRAM extends AllocationMerges {
    }

    @Fork(value = 3, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ReduceAllocationMerges",
    })
    public static class YesRAM extends AllocationMerges {
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static void dummy() {
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static int dummy(Load p) {
        return p.x * p.y;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static int dummy(int x) {
        return x;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static Load dummy(int x, int y) {
        return new Load(x, y);
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static String dummy(String str) {
        return str;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static ADefaults dummy_defaults() {
        return new ADefaults();
    }

    static class Load {
        long id;
        String name;
        Integer[] values = new Integer[10];
        int x, y;

        @CompilerControl(CompilerControl.Mode.INLINE)
        Load(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Load)) return false;
            Load p = (Load) o;
            return (p.x == x) && (p.y == y);
        }

        @Override
        public int hashCode() {
            return x + y;
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
        @CompilerControl(CompilerControl.Mode.EXCLUDE)
        ADefaults(int i) { this.i = i; }
        @CompilerControl(CompilerControl.Mode.EXCLUDE)
        ADefaults() { }
    }

    static class Picture {
        public int id;
        public Load position;

        public Picture(int id, int x, int y) {
            this.id = id;
            this.position = new Load(x, y);
        }
    }

    static class PicturePositions {
        public int id;
        public Load[] positions;

        @CompilerControl(CompilerControl.Mode.INLINE)
        public PicturePositions(int id, int x, int y) {
            this.id = id;
            this.positions = new Load[] { new Load(x, y), new Load(y, x) };
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
