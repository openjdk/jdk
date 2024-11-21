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
public abstract class AllocationMergesNestedPhi {
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
    int testRematerialize_SingleObj(boolean cond1, int x, int y) throws Exception {
        Load p = new Load(x, y);

        if (cond1) {
            p = new Load(x+1, y+1);
            global_escape = p;
        }

        if (!cond1)
            throw new Exception();

        return p.y;
    }

    @Benchmark
    public void testRematerialize_SingleObj_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            try {
                result += testRematerialize_SingleObj(cond1[i], xs[i], ys[i]);
            } catch (Exception e) {}
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testRematerialize_TryCatch(boolean cond1, int n, int x, int y) {
        Load p = new Load(x, y);
        int a = 5;
        int b = 10-5;
        if (cond1) {
            p = new Load(x+1, y+1);
            global_escape = p;
        }
        try {
            p.y = n/(a-b);
        } catch (Exception e) {}

        return p.y;
    }

    @Benchmark
    public void testRematerialize_TryCatch_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testRematerialize_TryCatch(cond1[i], xs[i], ys[i], zs[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testMerge_TryCatchFinally_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMerge_TryCatchFinally(cond1[i], xs[i], ys[i], zs[i]);
        }
        bh.consume(result);
    }


    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testRematerialize_MultiObj(boolean cond1, boolean cond2, int x, int y) {
        Load p1 = new Load(x, y);
        Load p2 = new Load(x+2, y+4);

        if (cond1) {
            p1 = new Load(x+1, y+1);
            global_escape = p1;
        }

        if (x%2 == 1) {
            p2 = new Load(x*2, y/4);
        }

        try {
            String s = null;
            s.length();
        } catch (Exception e) {}

        if (cond2)
            return p1.y;
        return p2.y;
    }

    @Benchmark
    public void testRematerialize_MultiObj_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testRematerialize_MultiObj(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int testFieldEscapeWithMerge(boolean cond, int x, int y) {

        Load p1 = new Load(x, y);
        Load p2 = new Load(x+y, x*y);
        Line ln = new Line(p1, p2);
        if (cond) {
            ln.p1 = new Load(x-y, x/y);
            global_escape = ln.p2;
        }
        return ln.p1.y;
    }

    @Benchmark
    public void testFieldEscapeWithMerge_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testFieldEscapeWithMerge(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testNestedPhi_FieldLoad_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhi_FieldLoad(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testThreeLevelNestedPhi_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testThreeLevelNestedPhi(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testNestedPhiProcessOrder_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhiProcessOrder(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }


    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testNestedPhi_TryCatch_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhi_TryCatch(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testBailOut_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testBailOut(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }

    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int testNestedPhiPolymorphic(boolean cond1, boolean cond2, int x, int l) {
        Shape obj1 = new Square(l);
        if (cond1)
            obj1 = new Circle(l);
        Shape obj2 = obj1;
        if (cond2)
            obj2 = new Circle(x + l/5);
        return obj2.l;
    }

    @Benchmark
    public void testNestedPhiPolymorphic_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhiPolymorphic(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }


    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testNestedPhiWithTrap_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhiWithTrap(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }


    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testNestedPhiWithLambda_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testNestedPhiWithLambda(cond1[i], cond2[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }


    //--------------------------------------------------------------------------------------------------------------------------------------------

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
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

    @Benchmark
    public void testMultiParentPhi_runner(Blackhole bh) {
        int result = 0;
        for (int i = 0 ; i < SIZE; i++) {
            result += testMultiParentPhi(cond1[i], xs[i], ys[i]);
        }
        bh.consume(result);
    }


   // ------------------ Utility for Testing ------------------- //


    @Fork(value = 3, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+UseTLAB",
        "-XX:-ReduceAllocationMerges",
    })
    public static class NopRAM extends AllocationMergesNestedPhi {
    }

    @Fork(value = 3, jvmArgsPrepend = {
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ReduceAllocationMerges",
    })
    public static class YesRAM extends AllocationMergesNestedPhi {
    }


    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static void dummy() {
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static int dummy(Point p) {
        return p.x * p.y;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static int dummy(int x) {
        return x;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static Point dummy(int x, int y) {
        return new Point(x, y);
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
    static String dummy(String str) {
        return str;
    }

    @CompilerControl(CompilerControl.Mode.EXCLUDE)
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

        @Override
        public int hashCode() {
            return x + y;
        }

        int getX() {
            return x;
        }
    }

   class Line {
       Load p1, p2;
       Line(Load p1, Load p2) {
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
        @CompilerControl(CompilerControl.Mode.EXCLUDE)
        ADefaults(int i) { this.i = i; }
        @CompilerControl(CompilerControl.Mode.EXCLUDE)
        ADefaults() { }
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
