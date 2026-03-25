/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypes.rI;
import static compiler.valhalla.inlinetypes.InlineTypes.rL;

/*
 * @test
 * @key randomness
 * @summary Test calls from {C1} to {C2, Interpreter}, and vice versa.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestCallingConventionC1
 */

@ForceCompileClassInitializer
public class TestCallingConventionC1 {

    public static void main(String[] args) {
        final Scenario[] scenarios = {
                // Default: both C1 and C2 are enabled, tiered compilation enabled
                new Scenario(0,
                             "--enable-preview",
                             "-XX:CICompilerCount=2",
                             "-XX:TieredStopAtLevel=4",
                             "-XX:+TieredCompilation"),
                // Default: both C1 and C2 are enabled, tiered compilation enabled
                new Scenario(1,
                             "--enable-preview",
                             "-XX:CICompilerCount=2",
                             "-XX:TieredStopAtLevel=4",
                             "-XX:+TieredCompilation",
                             "-XX:+IgnoreUnrecognizedVMOptions",
                             "-XX:+StressCallingConvention"),
                // Same as above, but flip all the compLevel=CompLevel.C1_SIMPLE and compLevel=CompLevel.C2, so we test
                // the compliment of the above scenario.
                new Scenario(2,
                             "--enable-preview",
                             "-XX:CICompilerCount=2",
                             "-XX:TieredStopAtLevel=4",
                             "-XX:+TieredCompilation",
                             "-DFlipC1C2=true"),
                // Only C1. Tiered compilation disabled.
                new Scenario(3,
                             "--enable-preview",
                             "-XX:TieredStopAtLevel=1",
                             "-XX:+TieredCompilation",
                             "-XX:+IgnoreUnrecognizedVMOptions",
                             "-XX:-PatchALot"),
                // Only C2.
                new Scenario(4,
                             "--enable-preview",
                             "-XX:TieredStopAtLevel=4",
                             "-XX:-TieredCompilation")
        };

        System.gc(); // Resolve this call, to avoid C1 code patching in the test cases.

        InlineTypes.getFramework()
                   .addScenarios(scenarios)
                   .start();
    }

    // Helper methods and classes
    @LooselyConsistentValue
    static value class Point {
        int x;
        int y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @DontCompile
        public int func() {
            return x + y;
        }

        @ForceCompile(CompLevel.C1_SIMPLE)
        @DontInline
        public int func_c1(Point p) {
            return x + y + p.x + p.y;
        }
    }

    static interface FunctorInterface {
        public int apply_interp(Point p);
    }

    static class Functor implements FunctorInterface {
        @DontCompile
        public int apply_interp(Point p) {
            return p.func() + 0;
        }
    }

    static class Functor1 extends Functor {
        @DontCompile
        public int apply_interp(Point p) {
            return p.func() + 10000;
        }
    }

    static class Functor2 extends Functor {
        @DontCompile
        public int apply_interp(Point p) {
            return p.func() + 20000;
        }
    }

    static class Functor3 extends Functor {
        @DontCompile
        public int apply_interp(Point p) {
            return p.func() + 30000;
        }
    }

    static class Functor4 extends Functor {
        @DontCompile
        public int apply_interp(Point p) {
            return p.func() + 40000;
        }
    }

    static Functor functors[] = {
        new Functor(),
        new Functor1(),
        new Functor2(),
        new Functor3(),
        new Functor4()
    };
    static int functorCounter = 0;
    static Functor getFunctor() {
        int n = (++ functorCounter) % functors.length;
        return functors[n];
    }

    @NullRestricted
    static Point pointField  = new Point(123, 456);
    @NullRestricted
    static Point pointField1 = new Point(1123, 1456);
    @NullRestricted
    static Point pointField2 = new Point(2123, 2456);

    static interface Intf {
        public int func1(int a, int b);
        public int func2(int a, int b, Point p);
    }

    static class MyImplPojo0 implements Intf {
        int field = 0;
        @DontCompile
        public int func1(int a, int b)             { return field + a + b + 1; }
        @DontCompile
        public int func2(int a, int b, Point p)     { return field + a + b + p.x + p.y + 1; }
    }

    static class MyImplPojo1 implements Intf {
        int field = 1000;

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func1(int a, int b)             { return field + a + b + 20; }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 20; }
    }

    static class MyImplPojo2 implements Intf {
        int field = 2000;

        @DontInline
        @ForceCompile(CompLevel.C2)
        public int func1(int a, int b)             { return field + a + b + 20; }

        @DontInline
        @ForceCompile(CompLevel.C2)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 20; }
    }

    static class MyImplPojo3 implements Intf {
        int field = 0;
        @DontInline // will be compiled with counters
        public int func1(int a, int b)             { return field + a + b + 1; }
        @DontInline // will be compiled with counters
        public int func2(int a, int b, Point p)     { return field + a + b + p.x + p.y + 1; }
    }

    @LooselyConsistentValue
    static value class MyImplVal1 implements Intf {
        int field;
        MyImplVal1() {
            field = 11000;
        }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func1(int a, int b) { return field + a + b + 300; }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 300; }
    }

    @LooselyConsistentValue
    static value class MyImplVal2 implements Intf {
        int field;
        MyImplVal2() {
            field = 12000;
        }

        @DontInline
        @ForceCompile(CompLevel.C2)
        public int func1(int a, int b)             { return field + a + b + 300; }

        @DontInline
        @ForceCompile(CompLevel.C2)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 300; }
    }

    @LooselyConsistentValue
    static value class MyImplVal1X implements Intf {
        int field;
        MyImplVal1X() {
            field = 11000;
        }

        @DontCompile
        public int func1(int a, int b)             { return field + a + b + 300; }

        @DontCompile
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 300; }
    }

    @LooselyConsistentValue
    static value class MyImplVal2X implements Intf {
        int field;
        MyImplVal2X() {
            field = 12000;
        }

        @DontInline // will be compiled with counters
        public int func1(int a, int b)             { return field + a + b + 300; }

        @DontInline // will be compiled with counters
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 300; }
    }

    static Intf intfs[] = {
        new MyImplPojo0(), // methods not compiled
        new MyImplPojo1(), // methods compiled by C1
        new MyImplPojo2(), // methods compiled by C2
        new MyImplVal1(),  // methods compiled by C1
        new MyImplVal2()   // methods compiled by C2
    };
    static Intf getIntf(int i) {
        int n = i % intfs.length;
        return intfs[n];
    }

    @LooselyConsistentValue
    static value class FixedPoints {
        boolean Z0 = false;
        boolean Z1 = true;
        byte    B  = (byte)2;
        char    C  = (char)34;
        short   S  = (short)456;
        int     I  = 5678;
        long    J  = 0x1234567800abcdefL;
    }
    @NullRestricted
    static FixedPoints fixedPointsField = new FixedPoints();

    @LooselyConsistentValue
    static value class FloatPoint {
        float x;
        float y;
        public FloatPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @LooselyConsistentValue
    static value class DoublePoint {
        double x;
        double y;
        public DoublePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    @NullRestricted
    static FloatPoint floatPointField = new FloatPoint(123.456f, 789.012f);
    @NullRestricted
    static DoublePoint doublePointField = new DoublePoint(123.456, 789.012);

    @LooselyConsistentValue
    static value class EightFloats {
        float f1, f2, f3, f4, f5, f6, f7, f8;
        public EightFloats() {
            f1 = 1.1f;
            f2 = 2.2f;
            f3 = 3.3f;
            f4 = 4.4f;
            f5 = 5.5f;
            f6 = 6.6f;
            f7 = 7.7f;
            f8 = 8.8f;
        }
    }

    static EightFloats eightFloatsField = new EightFloats();

    static class Number {
        int n;
        Number(int v) {
            n = v;
        }
        void set(int v) {
            n = v;
        }
    }

    static interface RefPointAccess {
        public int func1(RefPoint rp2);
        public int func2(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2);
    }

    @LooselyConsistentValue
    static value class RefPoint implements RefPointAccess {
        Number x;
        Number y;
        public RefPoint(int x, int y) {
            this.x = new Number(x);
            this.y = new Number(y);
        }
        public RefPoint(Number x, Number y) {
            this.x = x;
            this.y = y;
        }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public final int final_func(RefPoint rp2) { // opt_virtual_call
            return this.x.n + this.y.n + rp2.x.n + rp2.y.n;
        }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func1(RefPoint rp2) {
            return this.x.n + this.y.n + rp2.x.n + rp2.y.n;
        }

        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func2(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
            return x.n + y.n +
                   rp1.x.n + rp1.y.n +
                   rp2.x.n + rp2.y.n +
                   n1.n +
                   rp3.x.n + rp3.y.n +
                   rp4.x.n + rp4.y.n +
                   n2.n;
        }
    }

    static class RefPointAccessImpl1 implements RefPointAccess {
        @DontCompile
        public int func1(RefPoint rp2) {
            return rp2.x.n + rp2.y.n + 1111111;
        }
        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func2(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
            return 111111 +
                   rp1.x.n + rp1.y.n +
                   rp2.x.n + rp2.y.n +
                   n1.n +
                   rp3.x.n + rp3.y.n +
                   rp4.x.n + rp4.y.n +
                   n2.n;
        }
    }

    static class RefPointAccessImpl2 implements RefPointAccess {
        @DontCompile
        public int func1(RefPoint rp2) {
            return rp2.x.n + rp2.y.n + 2222222;
        }
        @DontInline
        @ForceCompile(CompLevel.C1_SIMPLE)
        public int func2(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
            return 222222 +
                   rp1.x.n + rp1.y.n +
                   rp2.x.n + rp2.y.n +
                   n1.n +
                   rp3.x.n + rp3.y.n +
                   rp4.x.n + rp4.y.n +
                   n2.n;
        }
    }

    static RefPointAccess refPoint_Access_impls[] = {
        new RefPointAccessImpl1(),
        new RefPointAccessImpl2(),
        new RefPoint(0x12345, 0x6789a)
    };

    static int next_RefPointAccess = 0;
    static RefPointAccess get_RefPointAccess() {
        int i = next_RefPointAccess ++;
        return refPoint_Access_impls[i % refPoint_Access_impls.length];
    }

    @NullRestricted
    static RefPoint refPointField1 = new RefPoint(12, 34);
    @NullRestricted
    static RefPoint refPointField2 = new RefPoint(56789, 0x12345678);

    // This value class has too many fields to fit in registers on x64 for
    // InlineTypeReturnedAsFields.
    @LooselyConsistentValue
    static value class TooBigToReturnAsFields {
        int a0 = 0;
        int a1 = 1;
        int a2 = 2;
        int a3 = 3;
        int a4 = 4;
        int a5 = 5;
        int a6 = 6;
        int a7 = 7;
        int a8 = 8;
        int a9 = 9;
    }

    @NullRestricted
    static TooBigToReturnAsFields tooBig = new TooBigToReturnAsFields();

    //**********************************************************************
    // PART 1 - C1 calls interpreted code
    //**********************************************************************

    //** C1 passes value object to interpreter (static)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test1() {
        return test1_helper(pointField);
    }

    @DontCompile
    private static int test1_helper(Point p) {
        return p.func();
    }

    @Run(test = "test1")
    public void test1_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 10;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test1() + i;
            Asserts.assertEQ(result, pointField.func() + i);
        }
    }

    //** C1 passes value object to interpreter (monomorphic)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test2() {
        return test2_helper(pointField);
    }

    @DontCompile
    private int test2_helper(Point p) {
        return p.func();
    }

    @Run(test = "test2")
    public void test2_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 10;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test2() + i;
            Asserts.assertEQ(result, pointField.func() + i);
        }
    }

    // C1 passes value object to interpreter (megamorphic: vtable)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test3(Functor functor) {
        return functor.apply_interp(pointField);
    }

    @Run(test = "test3")
    public void test3_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 100;
        for (int i = 0; i < count; i++) {  // need a loop to test inline cache and vtable indexing
            Functor functor = info.isWarmUp() ? functors[0] : getFunctor();
            int result = test3(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    // Same as test3, but compiled with C2. Test the hastable of VtableStubs
    @Test(compLevel = CompLevel.C2)
    public int test3b(Functor functor) {
        return functor.apply_interp(pointField);
    }

    @Run(test = "test3b")
    public void test3b_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 100;
        for (int i = 0; i < count; i++) {  // need a loop to test inline cache and vtable indexing
            Functor functor = info.isWarmUp() ? functors[0] : getFunctor();
            int result = test3b(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    // C1 passes value object to interpreter (megamorphic: itable)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test4(FunctorInterface fi) {
        return fi.apply_interp(pointField);
    }

    @Run(test = "test4")
    public void test4_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 100;
        for (int i = 0; i < count; i++) {  // need a loop to test inline cache and itable indexing
            Functor functor = info.isWarmUp() ? functors[0] : getFunctor();
            int result = test4(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    //**********************************************************************
    // PART 2 - interpreter calls C1
    //**********************************************************************

    // Interpreter passes value object to C1 (static)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    static public int test20(Point p1, long l, Point p2) {
        return p1.x + p2.y;
    }

    @Run(test = "test20")
    public void test20_verifier() {
        int result = test20(pointField1, 0, pointField2);
        int n = pointField1.x + pointField2.y;
        Asserts.assertEQ(result, n);
    }

    // Interpreter passes value object to C1 (instance method in value class)
    @Test
    public int test21(Point p) {
        return test21_helper(p);
    }

    @DontCompile
    int test21_helper(Point p) {
        return p.func_c1(p);
    }

    @Run(test = "test21")
    public void test21_verifier() {
        int result = test21(pointField);
        int n = 2 * (pointField.x + pointField.y);
        Asserts.assertEQ(result, n);
    }


    //**********************************************************************
    // PART 3 - C2 calls C1
    //**********************************************************************

    // C2->C1 invokestatic, single inline arg
    @Test(compLevel = CompLevel.C2)
    public int test30() {
        return test30_helper(pointField);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test30_helper(Point p) {
        return p.x + p.y;
    }

    @Run(test = "test30")
    public void test30_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test30();
            int n = pointField.x + pointField.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, two single inline args
    @Test(compLevel = CompLevel.C2)
    public int test31() {
      return test31_helper(pointField1, pointField2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test31_helper(Point p1, Point p2) {
        return p1.x + p2.y;
    }

    @Run(test = "test31")
    public void test31_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test31();
            int n = pointField1.x + pointField2.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, two single inline args and interleaving ints (all passed in registers on x64)
    @Test(compLevel = CompLevel.C2)
    public int test32() {
      return test32_helper(0, pointField1, 1, pointField2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test32_helper(int x, Point p1, int y, Point p2) {
        return p1.x + p2.y + x + y;
    }

    @Run(test = "test32")
    public void test32_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test32();
            int n = pointField1.x + pointField2.y + 0 + 1;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokeinterface -- no verified_ro_entry (no inline args except for receiver)
    @Test(compLevel = CompLevel.C2)
    public int test33(Intf intf, int a, int b) {
        return intf.func1(a, b);
    }

    @Run(test = "test33")
    public void test33_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = info.isWarmUp() ? intfs[0] : getIntf(i+1);
            int result = test33(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func1(123, 456) + i);
        }
    }

    // C2->C1 invokeinterface -- use verified_ro_entry (has inline args other than receiver)
    @Test(compLevel = CompLevel.C2)
    public int test34(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    @Run(test = "test34")
    public void test34_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = info.isWarmUp() ? intfs[0] : getIntf(i+1);
            int result = test34(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C1 invokestatic, Point.y is on stack (x64)
    @Test(compLevel = CompLevel.C2)
    public int test35() {
        return test35_helper(1, 2, 3, 4, 5, pointField);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test35_helper(int a1, int a2, int a3, int a4, int a5, Point p) {
        return a1 + a2 + a3 + a4 + a5 + p.x + p.y;
    }

    @Run(test = "test35")
    public void test35_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test35();
            int n = 1 + 2 + 3  + 4 + 5 + pointField.x + pointField.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling arguments that are passed on stack
    @Test(compLevel = CompLevel.C2)
    public int test36() {
        return test36_helper(pointField, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test36_helper(Point p, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return a6 + a8;
    }

    @Run(test = "test36")
    public void test36_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test36();
            int n = 6 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling long arguments
    @Test(compLevel = CompLevel.C2)
    public int test37() {
        return test37_helper(pointField, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test37_helper(Point p, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8) {
        return (int)(a6 + a8);
    }

    @Run(test = "test37")
    public void test37_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test37();
            int n = 6 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling boolean, byte, char, short, int, long arguments
    @Test(compLevel = CompLevel.C2)
    public int test38() {
        return test38_helper(pointField, true, (byte)1, (char)2, (short)3, 4, 5, (byte)6, (short)7, 8);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test38_helper(Point p, boolean a0, byte a1, char a2, short a3, int a4, long a5, byte a6, short a7, int a8) {
        if (a0) {
            return (int)(a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8);
        } else {
            return -1;
        }
    }

    @Run(test = "test38")
    public void test38_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test38();
            int n = 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing a value object with all types of fixed point primitive fields.
    @Test(compLevel = CompLevel.C2)
    public long test39() {
        return test39_helper(1, fixedPointsField, 2, fixedPointsField);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static long test39_helper(int a1, FixedPoints f1, int a2, FixedPoints f2) {
        if (f1.Z0 == false && f1.Z1 == true && f2.Z0 == false && f2.Z1 == true) {
            return f1.B + f2.C + f1.S + f2.I + f1.J;
        } else {
            return -1;
        }
    }

    @Run(test = "test39")
    public void test39_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            long result = test39();
            long n = test39_helper(1, fixedPointsField, 2, fixedPointsField);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling floating point args
    @Test(compLevel = CompLevel.C2)
    public double test40() {
        return test40_helper(1.1f, 1.2, floatPointField, doublePointField, 1.3f, 1.4, 1.5f, 1.7, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static double test40_helper(float a1, double a2, FloatPoint fp, DoublePoint dp, float a3, double a4, float a5, double a6, double a7, double a8, double a9, double a10, double a11, double a12) {
        return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + fp.x + fp.y - dp.x - dp.y;
    }

    @Run(test = "test40")
    public void test40_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            double result = test40();
            double n = test40_helper(1.1f, 1.2, floatPointField, doublePointField, 1.3f, 1.4, 1.5f, 1.7, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, mixing floats and ints
    @Test(compLevel = CompLevel.C2)
    public double test41() {
        return test41_helper(1, 1.2, pointField, floatPointField, doublePointField, 1.3f, 4, 1.5f, 1.7, 1.7, 1.8, 9, 1.10, 1.11, 1.12);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static double test41_helper(int a1, double a2, Point p, FloatPoint fp, DoublePoint dp, float a3, int a4, float a5, double a6, double a7, double a8, long a9, double a10, double a11, double a12) {
      return a1 + a2  + fp.x + fp.y - dp.x - dp.y + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12;
    }

    @Run(test = "test41")
    public void test41_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            double result = test41();
            double n = test41_helper(1, 1.2, pointField, floatPointField, doublePointField, 1.3f, 4, 1.5f, 1.7, 1.7, 1.8, 9, 1.10, 1.11, 1.12);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, circular dependency (between rdi and first stack slot on x64)
    @Test(compLevel = CompLevel.C2)
    public float test42() {
        return test42_helper(eightFloatsField, pointField, 3, 4, 5, floatPointField, 7);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test42_helper(EightFloats ep1, // (xmm0 ... xmm7) -> rsi
                                       Point p2,        // (rsi, rdx) -> rdx
                                       int i3,          // rcx -> rcx
                                       int i4,          // r8 -> r8
                                       int i5,          // r9 -> r9
                                       FloatPoint fp6,  // (stk[0], stk[1]) -> rdi   ** circ depend
                                       int i7)          // rdi -> stk[0]             ** circ depend
    {
        return ep1.f1 + ep1.f2 + ep1.f3 + ep1.f4 + ep1.f5 + ep1.f6 + ep1.f7 + ep1.f8 +
            p2.x + p2.y + i3 + i4 + i5 + fp6.x + fp6.y + i7;
    }

    @Run(test = "test42")
    public void test42_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            float result = test42();
            float n = test42_helper(eightFloatsField, pointField, 3, 4, 5, floatPointField, 7);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (1 extra stack word)
    @Test(compLevel = CompLevel.C2)
    public float test43() {
        return test43_helper(floatPointField, 1, 2, 3, 4, 5, 6);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test43_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        // On x64:
        //    Scalarized entry -- all parameters are passed in registers
        //    Non-scalarized entry -- a6 is passed on stack[0]
        return fp.x + fp.y + a1 + a2 + a3 + a4 + a5 + a6;
    }

    @Run(test = "test43")
    public void test43_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            float result = test43();
            float n = test43_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (2 extra stack words)
    @Test(compLevel = CompLevel.C2)
    public float test44() {
      return test44_helper(floatPointField, floatPointField, 1, 2, 3, 4, 5, 6);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test44_helper(FloatPoint fp1, FloatPoint fp2, int a1, int a2, int a3, int a4, int a5, int a6) {
        // On x64:
        //    Scalarized entry -- all parameters are passed in registers
        //    Non-scalarized entry -- a5 is passed on stack[0]
        //    Non-scalarized entry -- a6 is passed on stack[1]
        return fp1.x + fp1.y +
               fp2.x + fp2.y +
               a1 + a2 + a3 + a4 + a5 + a6;
    }

    @Run(test = "test44")
    public void test44_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            float result = test44();
            float n = test44_helper(floatPointField, floatPointField, 1, 2, 3, 4, 5, 6);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (5 extra stack words)
    @Test(compLevel = CompLevel.C2)
    public float test45() {
      return test45_helper(floatPointField, floatPointField, floatPointField, floatPointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test45_helper(FloatPoint fp1, FloatPoint fp2, FloatPoint fp3, FloatPoint fp4, FloatPoint fp5, int a1, int a2, int a3, int a4, int a5, int a6, int a7) {
        return fp1.x + fp1.y +
               fp2.x + fp2.y +
               fp3.x + fp3.y +
               fp4.x + fp4.y +
               fp5.x + fp5.y +
               a1 + a2 + a3 + a4 + a5 + a6 + a7;
    }

    @Run(test = "test45")
    public void test45_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            float result = test45();
            float n = test45_helper(floatPointField, floatPointField, floatPointField, floatPointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (1 extra stack word -- mixing Point and FloatPoint)
    @Test(compLevel = CompLevel.C2)
    public float test46() {
      return test46_helper(floatPointField, floatPointField, pointField, floatPointField, floatPointField, pointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test46_helper(FloatPoint fp1, FloatPoint fp2, Point p1, FloatPoint fp3, FloatPoint fp4, Point p2, FloatPoint fp5, int a1, int a2, int a3, int a4, int a5, int a6, int a7) {
        return p1.x + p1.y +
               p2.x + p2.y +
               fp1.x + fp1.y +
               fp2.x + fp2.y +
               fp3.x + fp3.y +
               fp4.x + fp4.y +
               fp5.x + fp5.y +
               a1 + a2 + a3 + a4 + a5 + a6 + a7;
    }

    @Run(test = "test46")
    public void test46_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 2;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            float result = test46();
            float n = test46_helper(floatPointField, floatPointField, pointField, floatPointField, floatPointField, pointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
            Asserts.assertEQ(result, n);
        }
    }

    static class MyRuntimeException extends RuntimeException {
        MyRuntimeException(String s) {
            super(s);
        }
    }

    static void checkStackTrace(Throwable t, String... methodNames) {
        StackTraceElement[] trace = t.getStackTrace();
        for (int i = 0; i < methodNames.length; i++) {
            if (!methodNames[i].equals(trace[i].getMethodName())) {
                String error = "Unexpected stack trace: level " + i + " should be " + methodNames[i];
                System.out.println(error);
                t.printStackTrace(System.out);
                throw new RuntimeException(error, t);
            }
        }
    }
    //*

    // C2->C1 invokestatic, make sure stack walking works (with static variable)
    @Test(compLevel = CompLevel.C2)
    public void test47(int n) {
        try {
            test47_helper(floatPointField, 1, 2, 3, 4, 5);
            test47_value = 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        test47_value = n;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test47_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5) {
        test47_thrower();
        return 0.0f;
    }

    @DontCompile
    private static void test47_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test47_thrower", "test47_helper", "test47", "test47_verifier");
        throw e;
    }

    static int test47_value = 999;

    @Run(test = "test47")
    public void test47_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            test47_value = 777 + i;
            test47(i);
            Asserts.assertEQ(test47_value, i);
        }
    }

    // C2->C1 invokestatic, make sure stack walking works (with returned value object)
    @Test(compLevel = CompLevel.C2)
    public int test48(int n) {
        try {
            test48_helper(floatPointField, 1, 2, 3, 4, 5);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test48_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5) {
        test48_thrower();
        return 0.0f;
    }

    @DontCompile
    private static void test48_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test48_thrower", "test48_helper", "test48", "test48_verifier");
        throw e;
    }

    @Run(test = "test48")
    public void test48_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int n = test48(i);
            Asserts.assertEQ(n, i);
        }
    }

    // C2->interpreter invokestatic, make sure stack walking works (same as test 48, but with stack extension/repair)
    // (this is the baseline for test50 --
    // the only difference is: test49_helper is interpreted but test50_helper is compiled by C1).
    @Test(compLevel = CompLevel.C2)
    public int test49(int n) {
        try {
            test49_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontCompile
    private static float test49_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        test49_thrower();
        return 0.0f;
    }

    @DontCompile
    private static void test49_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test49_thrower", "test49_helper", "test49", "test49_verifier");
        throw e;
    }

    @Run(test = "test49")
    public void test49_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int n = test49(i);
            Asserts.assertEQ(n, i);
        }
    }

    // C2->C1 invokestatic, make sure stack walking works (same as test 48, but with stack extension/repair)
    @Test(compLevel = CompLevel.C2)
    public int test50(int n) {
        try {
            test50_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test50_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        test50_thrower();
        return 0.0f;
    }

    @DontCompile
    private static void test50_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test50_thrower", "test50_helper", "test50", "test50_verifier");
        throw e;
    }

    @Run(test = "test50")
    public void test50_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int n = test50(i);
            Asserts.assertEQ(n, i);
        }
    }


    // C2->C1 invokestatic, value class with ref fields (RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test51() {
        return test51_helper(refPointField1);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test51_helper(RefPoint rp1) {
        return rp1.x.n + rp1.y.n;
    }

    @Run(test = "test51")
    public void test51_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test51();
            int n = test51_helper(refPointField1);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, value class with ref fields (Point, RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test52() {
        return test52_helper(pointField, refPointField1);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test52_helper(Point p1, RefPoint rp1) {
        return p1.x + p1.y + rp1.x.n + rp1.y.n;
    }

    @Run(test = "test52")
    public void test52_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test52();
            int n = test52_helper(pointField, refPointField1);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, value class with ref fields (RefPoint, RefPoint, RefPoint, RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test53() {
        return test53_helper(refPointField1, refPointField2, refPointField1, refPointField2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test53_helper(RefPoint rp1, RefPoint rp2, RefPoint rp3, RefPoint rp4) {
        return rp1.x.n + rp1.y.n +
               rp2.x.n + rp2.y.n +
               rp3.x.n + rp3.y.n +
               rp4.x.n + rp4.y.n;
    }

    @Run(test = "test53")
    public void test53_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test53();
            int n = test53_helper(refPointField1, refPointField2, refPointField1, refPointField2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, value class with ref fields (RefPoint, RefPoint, float, int, RefPoint, RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test54() {
        return test54_helper(refPointField1, refPointField2, 1.0f, 2, refPointField1, refPointField2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test54_helper(RefPoint rp1, RefPoint rp2, float f, int i, RefPoint rp3, RefPoint rp4) {
        return rp1.x.n + rp1.y.n +
               rp2.x.n + rp2.y.n +
               (int)(f) + i +
               rp3.x.n + rp3.y.n +
               rp4.x.n + rp4.y.n;
    }

    @Run(test = "test54")
    public void test54_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            int result = test54();
            int n = test54_helper(refPointField1, refPointField2, 1.0f, 2, refPointField1, refPointField2);
            Asserts.assertEQ(result, n);
        }
    }

    /**
     * Each allocation with a "try" block like this will cause a GC
     *
     *       try (ForceGCMarker m = ForceGCMarker.mark(warmup)) {
     *           result = test55(p1);
     *       }
     */

    static final String ScavengeALot = "ScavengeALot";

    static class ForceGCMarker implements java.io.Closeable {
        ForceGCMarker() {
            WhiteBox.getWhiteBox().setBooleanVMFlag(ScavengeALot, true);
        }
        public void close() {
            WhiteBox.getWhiteBox().setBooleanVMFlag(ScavengeALot, false);
        }

        static ForceGCMarker mark(boolean warmup) {
            return warmup ? null : new ForceGCMarker();
        }
    }

    // C2->C1 invokestatic, force GC for every allocation when entering a C1 VEP (Point)
    @Test(compLevel = CompLevel.C2)
    public int test55(Point p1) {
        return test55_helper(p1);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test55_helper(Point p1) {
        return p1.x + p1.y;
    }

    @Run(test = "test55")
    public void test55_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            Point p1 = new Point(1, 2);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test55(p1);
            }
            int n = test55_helper(p1);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, force GC for every allocation when entering a C1 VEP (RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test56(RefPoint rp1) {
        return test56_helper(rp1);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test56_helper(RefPoint rp1) {
        return rp1.x.n + rp1.y.n;
    }

    @Run(test = "test56")
    public void test56_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test56(rp1);
            }
            int n = test56_helper(rp1);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->Interpreter (same as test56, but test C2i entry instead of C1)
    @Test(compLevel = CompLevel.C2)
    public int test57(RefPoint rp1) {
        return test57_helper(rp1);
    }

    @DontCompile
    private static int test57_helper(RefPoint rp1) {
        return rp1.x.n + rp1.y.n;
    }

    @Run(test = "test57")
    public void test57_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test57(rp1);
            }
            int n = test57_helper(rp1);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, force GC for every allocation when entering a C1 VEP (a bunch of RefPoints and Numbers);
    @Test(compLevel = CompLevel.C2)
    public int test58(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
        return test58_helper(rp1, rp2, n1, rp3, rp4, n2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test58_helper(RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
        return rp1.x.n + rp1.y.n +
               rp2.x.n + rp2.y.n +
               n1.n +
               rp3.x.n + rp3.y.n +
               rp4.x.n + rp4.y.n +
               n2.n;
    }

    @Run(test = "test58")
    public void test58_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            RefPoint rp2 = refPointField1;
            RefPoint rp3 = new RefPoint(222, 777);
            RefPoint rp4 = refPointField2;
            Number n1 = new Number(5878);
            Number n2 = new Number(1234);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test58(rp1, rp2, n1, rp3, rp4, n2);
            }
            int n = test58_helper(rp1, rp2, n1, rp3, rp4, n2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, GC inside main body of C1-compiled method (caller's args should not be GC'ed).
    @Test(compLevel = CompLevel.C2)
    public int test59(RefPoint rp1, boolean doGC) {
      return test59_helper(rp1, 11, 222, 3333, 4444, doGC);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test59_helper(RefPoint rp1, int a1, int a2, int a3, int a4, boolean doGC) {
        if (doGC) {
            System.gc();
        }
        return rp1.x.n + rp1.y.n + a1 + a2 + a3 + a4;
    }

    @Run(test = "test59")
    public void test59_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        boolean doGC = !info.isWarmUp();
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            int result = test59(rp1, doGC);
            int n = test59_helper(rp1, 11, 222, 3333, 4444, doGC);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, GC inside main body of C1-compiled method (caller's args should not be GC'ed).
    // same as test59, but the incoming (scalarized) oops are passed in both registers and stack.
    @Test(compLevel = CompLevel.C2)
    public int test60(RefPoint rp1, RefPoint rp2, boolean doGC) {
        return test60_helper(555, 6666, 77777, rp1, rp2, 11, 222, 3333, 4444, doGC);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static int test60_helper(int x0, int x1, int x2, RefPoint rp1, RefPoint rp2,int a1, int a2, int a3, int a4, boolean doGC) {
        // On x64, C2 passes:   reg0=x1, reg1=x1, reg2=x2, reg3=rp1.x, reg4=rp1.y, reg5=rp2.x stack0=rp2.y ....
        //         C1 expects:  reg0=x1, reg1=x1, reg2=x2, reg3=rp1,   reg4=rp2,   reg5=a1    stack0=a2 ...
        // When GC happens, make sure it does not treat reg5 and stack0 as oops!
        if (doGC) {
            System.gc();
        }
        return x0 + x1 + x2 + rp1.x.n + rp1.y.n + rp2.x.n + rp2.y.n + a1 + a2 + a3 + a4;
    }

    @Run(test = "test60")
    public void test60_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        boolean doGC = !info.isWarmUp();
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            RefPoint rp2 = new RefPoint(33, 44);
            int result = test60(rp1, rp2, doGC);
            int n = test60_helper(555, 6666, 77777, rp1, rp2, 11, 222, 3333, 4444, doGC);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokeinterface via VVEP(RO)
    @Test(compLevel = CompLevel.C2)
    public int test61(RefPointAccess rpa, RefPoint rp2) {
        return rpa.func1(rp2);
    }

    @Run(test = "test61")
    public void test61_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPointAccess rpa = get_RefPointAccess();
            RefPoint rp2 = refPointField2;
            int result = test61(rpa, rp2);
            int n = rpa.func1(rp2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokeinterface via VVEP(RO) -- force GC for every allocation when entering a C1 VVEP(RO) (RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test62(RefPointAccess rpa, RefPoint rp2) {
        return rpa.func1(rp2);
    }

    @Run(test = "test62")
    public void test62_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPointAccess rpa = get_RefPointAccess();
            RefPoint rp2 = new RefPoint(111, 2222);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test62(rpa, rp2);
            }
            int n = rpa.func1(rp2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokeinterface via VVEP(RO) -- force GC for every allocation when entering a C1 VVEP(RO) (a bunch of RefPoints and Numbers)
    @Test(compLevel = CompLevel.C2)
    public int test63(RefPointAccess rpa, RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
        return rpa.func2(rp1, rp2, n1, rp3, rp4, n2);
    }

    @Run(test = "test63")
    public void test63_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPointAccess rpa = get_RefPointAccess();
            RefPoint rp1 = new RefPoint(1, 2);
            RefPoint rp2 = refPointField1;
            RefPoint rp3 = new RefPoint(222, 777);
            RefPoint rp4 = refPointField2;
            Number n1 = new Number(5878);
            Number n2 = new Number(1234);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test63(rpa, rp1, rp2, n1, rp3, rp4, n2);
            }
            int n = rpa.func2(rp1, rp2, n1, rp3, rp4, n2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic (same as test63, but use invokestatic instead)
    @Test(compLevel = CompLevel.C2)
    public int test64(RefPointAccess rpa, RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
        return test64_helper(rpa, rp1, rp2, n1, rp3, rp4, n2);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    public static int test64_helper(RefPointAccess rpa, RefPoint rp1, RefPoint rp2, Number n1, RefPoint rp3, RefPoint rp4, Number n2) {
        return rp3.y.n;
    }

    @Run(test = "test64")
    public void test64_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPointAccess rpa = get_RefPointAccess();
            RefPoint rp1 = new RefPoint(1, 2);
            RefPoint rp2 = refPointField1;
            RefPoint rp3 = new RefPoint(222, 777);
            RefPoint rp4 = refPointField2;
            Number n1 = new Number(5878);
            Number n2 = new Number(1234);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test64(rpa, rp1, rp2, n1, rp3, rp4, n2);
            }
            int n = test64_helper(rpa, rp1, rp2, n1, rp3, rp4, n2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokevirtual via VVEP(RO) (opt_virtual_call)
    @Test(compLevel = CompLevel.C2)
    public int test76(RefPoint rp1, RefPoint rp2) {
        return rp1.final_func(rp2);
    }

    @Run(test = "test76")
    public void test76_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = refPointField1;
            RefPoint rp2 = refPointField2;
            int result = test76(rp1, rp2);
            int n = rp1.final_func(rp2);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokevirtual, force GC for every allocation when entering a C1 VEP (RefPoint)
    // Same as test56, except we call the VVEP(RO) instead of VEP.
    @Test(compLevel = CompLevel.C2)
    public int test77(RefPoint rp1, RefPoint rp2) {
        return rp1.final_func(rp2);
    }

    @Run(test = "test77")
    public void test77_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) { // need a loop to test inline cache
            RefPoint rp1 = new RefPoint(1, 2);
            RefPoint rp2 = new RefPoint(22, 33);
            int result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test77(rp1, rp2);
            }
            int n = rp1.final_func(rp2);
            Asserts.assertEQ(result, n);
        }
    }

    //-------------------------------------------------------------------------------
    // Tests for how C1 handles InlineTypeReturnedAsFields in both calls and returns
    //-------------------------------------------------------------------------------
    // C2->C1 invokestatic with InlineTypeReturnedAsFields (Point)
    @Test(compLevel = CompLevel.C2)
    public int test78(Point p) {
        Point np = test78_helper(p);
        return np.x + np.y;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static Point test78_helper(Point p) {
        return p;
    }

    @Run(test = "test78")
    public void test78_verifier() {
        int result = test78(pointField1);
        int n = pointField1.x + pointField1.y;
        Asserts.assertEQ(result, n);
    }

    // C2->C1 invokestatic with InlineTypeReturnedAsFields (RefPoint)
    @Test(compLevel = CompLevel.C2)
    public int test79(RefPoint p) {
        RefPoint np = test79_helper(p);
        return np.x.n + np.y.n;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static RefPoint test79_helper(RefPoint p) {
        return p;
    }

    @Run(test = "test79")
    public void test79_verifier() {
        int result = test79(refPointField1);
        int n = refPointField1.x.n + refPointField1.y.n;
        Asserts.assertEQ(result, n);
    }

    // C1->C2 invokestatic with InlineTypeReturnedAsFields (RefPoint)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test80(RefPoint p) {
        RefPoint np = test80_helper(p);
        return np.x.n + np.y.n;
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    private static RefPoint test80_helper(RefPoint p) {
        return p;
    }

    @Run(test = "test80")
    public void test80_verifier() {
        int result = test80(refPointField1);
        int n = refPointField1.x.n + refPointField1.y.n;
        Asserts.assertEQ(result, n);
    }

    // Interpreter->C1 invokestatic with InlineTypeReturnedAsFields (Point)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public Point test81(Point p) {
        return p;
    }

    @Run(test = "test81")
    public void test81_verifier() {
        Point p = test81(pointField1);
        Asserts.assertEQ(p.x, pointField1.x);
        Asserts.assertEQ(p.y, pointField1.y);
        p = test81(pointField2);
        Asserts.assertEQ(p.x, pointField2.x);
        Asserts.assertEQ(p.y, pointField2.y);
    }

    // C1->Interpreter invokestatic with InlineTypeReturnedAsFields (RefPoint)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test82(RefPoint p) {
        RefPoint np = test82_helper(p);
        return np.x.n + np.y.n;
    }

    @DontCompile
    private static RefPoint test82_helper(RefPoint p) {
        return p;
    }

    @Run(test = "test82")
    public void test82_verifier() {
        int result = test82(refPointField1);
        int n = refPointField1.x.n + refPointField1.y.n;
        Asserts.assertEQ(result, n);
    }

    //-------------------------------------------------------------------------------
    // Tests for InlineTypeReturnedAsFields vs the value class TooBigToReturnAsFields
    //-------------------------------------------------------------------------------

    // C2->C1 invokestatic with InlineTypeReturnedAsFields (TooBigToReturnAsFields)
    @Test(compLevel = CompLevel.C2)
    public int test83(TooBigToReturnAsFields p) {
        TooBigToReturnAsFields np = test83_helper(p);
        return p.a0 + p.a5;
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static TooBigToReturnAsFields test83_helper(TooBigToReturnAsFields p) {
        return p;
    }

    @Run(test = "test83")
    public void test83_verifier() {
        int result = test83(tooBig);
        int n = tooBig.a0 + tooBig.a5;
        Asserts.assertEQ(result, n);
    }

    // C1->C2 invokestatic with InlineTypeReturnedAsFields (TooBigToReturnAsFields)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test84(TooBigToReturnAsFields p) {
        TooBigToReturnAsFields np = test84_helper(p);
        return p.a0 + p.a5;
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    private static TooBigToReturnAsFields test84_helper(TooBigToReturnAsFields p) {
        return p;
    }

    @Run(test = "test84")
    public void test84_verifier() {
        int result = test84(tooBig);
        int n = tooBig.a0 + tooBig.a5;
        Asserts.assertEQ(result, n);
    }

    // Interpreter->C1 invokestatic with InlineTypeReturnedAsFields (TooBigToReturnAsFields)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public TooBigToReturnAsFields test85(TooBigToReturnAsFields p) {
        return p;
    }

    @Run(test = "test85")
    public void test85_verifier() {
        TooBigToReturnAsFields p = test85(tooBig);
        Asserts.assertEQ(p.a0, tooBig.a0);
        Asserts.assertEQ(p.a2, tooBig.a2);
    }

    // C1->Interpreter invokestatic with InlineTypeReturnedAsFields (TooBigToReturnAsFields)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test86(TooBigToReturnAsFields p) {
        TooBigToReturnAsFields np = test86_helper(p);
        return p.a0 + p.a5;
    }

    @DontCompile
    private static TooBigToReturnAsFields test86_helper(TooBigToReturnAsFields p) {
        return p;
    }

    @Run(test = "test86")
    public void test86_verifier() {
        int result = test86(tooBig);
        int n = tooBig.a0 + tooBig.a5;
        Asserts.assertEQ(result, n);
    }

    //-------------------------------------------------------------------------------
    // Tests for how C1 handles InlineTypeReturnedAsFields in both calls and returns
    //-------------------------------------------------------------------------------

    // C2->C1 invokestatic with InlineTypeReturnedAsFields (RefPoint)
    @Test(compLevel = CompLevel.C2)
    public RefPoint test87(RefPoint p) {
        return test87_helper(p);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static RefPoint test87_helper(RefPoint p) {
        return p;
    }

    @Run(test = "test87")
    public void test87_verifier() {
        Asserts.assertEQ(test87(null), null);
        Asserts.assertEQ(test87(refPointField1), refPointField1);
    }

    // C2->C1 invokestatic with InlineTypeReturnedAsFields (RefPoint with constant null)
    @Test(compLevel = CompLevel.C2)
    public RefPoint test88() {
        return test88_helper();
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static RefPoint test88_helper() {
        return null;
    }

    @Run(test = "test88")
    public void test88_verifier() {
        Asserts.assertEQ(test88(), null);
    }

    // C1->C2 invokestatic with InlineTypeReturnedAsFields (RefPoint)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public RefPoint test89(RefPoint p) {
        return test89_helper(p);
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    private static RefPoint test89_helper(RefPoint p) {
        return p;
    }

    @Run(test = "test89")
    public void test89_verifier() {
        Asserts.assertEQ(test89(null), null);
        Asserts.assertEQ(test89(refPointField1), refPointField1);
    }

    //----------------------------------------------------------------------------------
    // Tests for unverified entries: there are 6 cases:
    // C1 -> Unverified Value Entry compiled by C1
    // C1 -> Unverified Value Entry compiled by C2
    // C2 -> Unverified Entry compiled by C1 (target is NOT a value class)
    // C2 -> Unverified Entry compiled by C2 (target is NOT a value class)
    // C2 -> Unverified Entry compiled by C1 (target IS a value class, i.e., has VVEP_RO)
    // C2 -> Unverified Entry compiled by C2 (target IS a value class, i.e., has VVEP_RO)
    //----------------------------------------------------------------------------------

    // C1->C1 invokeinterface -- call Unverified Value Entry of MyImplPojo1.func2 (compiled by C1)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test90(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test90_intfs[] = {
        new MyImplPojo1(),
        new MyImplPojo2(),
    };

    @Run(test = "test90")
    public void test90_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test90_intfs[i % test90_intfs.length];
            int result = test90(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C1->C2 invokeinterface -- call Unverified Value Entry of MyImplPojo2.func2 (compiled by C2)
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test91(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test91_intfs[] = {
        new MyImplPojo2(),
        new MyImplPojo1(),
    };

    @Run(test = "test91")
    public void test91_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test91_intfs[i % test91_intfs.length];
            int result = test91(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C1 invokeinterface -- call Unverified Entry of MyImplPojo1.func2 (compiled by C1)
    @Test(compLevel = CompLevel.C2)
    public int test92(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test92_intfs[] = {
        new MyImplPojo1(),
        new MyImplPojo2(),
    };

    @Run(test = "test92")
    public void test92_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test92_intfs[i % test92_intfs.length];
            int result = test92(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C2 invokeinterface -- call Unverified Entry of MyImplPojo2.func2 (compiled by C2)
    @Test(compLevel = CompLevel.C2)
    public int test93(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test93_intfs[] = {
        new MyImplPojo2(),
        new MyImplPojo1(),
    };

    @Run(test = "test93")
    public void test93_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test93_intfs[i % test93_intfs.length];
            int result = test93(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C1 invokeinterface -- call Unverified Entry of MyImplVal1.func2 (compiled by C1 - has VVEP_RO)
    @Test(compLevel = CompLevel.C2)
    public int test94(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test94_intfs[] = {
        new MyImplVal1(),
        new MyImplVal2(),
    };

    @Run(test = "test94")
    public void test94_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test94_intfs[i % test94_intfs.length];
            int result = test94(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C2 invokeinterface -- call Unverified Entry of MyImplVal2.func2 (compiled by C2 - has VVEP_RO)
    @Test(compLevel = CompLevel.C2)
    public int test95(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    static Intf test95_intfs[] = {
        new MyImplVal2(),
        new MyImplVal1(),
    };

    @Run(test = "test95")
    public void test95_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test95_intfs[i % test95_intfs.length];
            int result = test95(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C1->C2 GC handling in StubRoutines::store_inline_type_fields_to_buf()
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public RefPoint test96(RefPoint rp, boolean b) {
        RefPoint p = test96_helper(rp);
        if (b) {
            return rp;
        }
        return p;
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    public RefPoint test96_helper(RefPoint rp) {
        return rp;
    }

    @Run(test = "test96")
    public void test96_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20000; // Do enough iteration to cause GC inside StubRoutines::store_inline_type_fields_to_buf
        Number x = new Number(10); // old object
        for (int i = 0; i < count; i++) {
            Number y = new Number(i); // new object for each iteraton
            RefPoint rp1 = new RefPoint(x, y);
            RefPoint rp2 = test96(rp1, info.isWarmUp());

            Asserts.assertEQ(rp1.x, x);
            Asserts.assertEQ(rp1.y, y);
            Asserts.assertEQ(rp1.y.n, i);
        }
    }

    // C1->C1  - caller is compiled first. It invokes callee(test97) a few times while the
    //           callee is executed by the interpreter. Then, callee is compiled
    //           and SharedRuntime::fixup_callers_callsite is called to fix up the
    //           callsite from test97_verifier->test97.
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test97(Point p1, Point p2) {
        return test97_helper(p1, p2);
    }

    @DontCompile
    public int test97_helper(Point p1, Point p2) {
        return p1.x + p1.y + p2.x + p2.y;
    }

    @ForceCompile(CompLevel.C1_SIMPLE)
    public void test97_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            int result = test97(pointField1, pointField2);
            int n = test97_helper(pointField1, pointField2);
            Asserts.assertEQ(result, n);
        }
    }

    @Run(test = "test97")
    public void run_test97_verifier(RunInfo info) {
        test97_verifier(info);
    }

    // C1->C2  - same as test97, except the callee is compiled by C2.
    @Test(compLevel = CompLevel.C2)
    public int test98(Point p1, Point p2) {
        return test98_helper(p1, p2);
    }

    @DontCompile
    public int test98_helper(Point p1, Point p2) {
        return p1.x + p1.y + p2.x + p2.y;
    }

    @ForceCompile(CompLevel.C1_SIMPLE)
    public void test98_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            int result = test98(pointField1, pointField2);
            int n = test98_helper(pointField1, pointField2);
            Asserts.assertEQ(result, n);
        }
    }

    @Run(test = "test98")
    public void run_test98_verifier(RunInfo info) {
        test98_verifier(info);
    }

    // C1->C2  - same as test97, except the callee is a static method.
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public static int test99(Point p1, Point p2) {
        return test99_helper(p1, p2);
    }

    @DontCompile
    public static int test99_helper(Point p1, Point p2) {
        return p1.x + p1.y + p2.x + p2.y;
    }

    @ForceCompile(CompLevel.C1_SIMPLE)
    public void test99_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            int result = test99(pointField1, pointField2);
            int n = test99_helper(pointField1, pointField2);
            Asserts.assertEQ(result, n);
        }
    }

    @Run(test = "test99")
    public void run_test99_verifier(RunInfo info) {
        test99_verifier(info);
    }

    // C2->C1 invokestatic, packing causes stack growth (1 extra stack word).
    // Make sure stack frame is set up properly for GC.
    @Test(compLevel = CompLevel.C2)
    public float test100(FloatPoint fp1, FloatPoint fp2, RefPoint rp, int a1, int a2, int a3, int a4) {
        return test100_helper(fp1, fp2, rp, a1, a2, a3, a4);
    }

    @DontInline
    @ForceCompile(CompLevel.C1_SIMPLE)
    private static float test100_helper(FloatPoint fp1, FloatPoint fp2, RefPoint rp, int a1, int a2, int a3, int a4) {
        // On x64:
        //    Scalarized entry -- all parameters are passed in registers
        //          xmm0 = fp1.x
        //          xmm1 = fp1.y
        //          xmm2 = fp2.x
        //          xmm3 = fp2.y
        //          rsi  = rp.x  (oop)
        //          rdx  = rp.y  (oop)
        //          cx   = a1
        //          r8   = a2
        //          r9   = a3
        //          di   = a4
        //    Non-scalarized entry -- a6 is passed on stack[0]
        //          rsi  = fp1
        //          rdx  = fp2
        //          rcx  = rp
        //          r8   = a1
        //          r9   = a2
        //          di   = a3
        //    [sp + ??]  = a4
        return fp1.x + fp1.y + fp2.x + fp2.y + rp.x.n + rp.y.n + a1 + a2 + a3 + a4;
    }

    @Run(test = "test100")
    public void test100_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 4;
        for (int i = 0; i < count; i++) {
            FloatPoint fp1 = new FloatPoint(i+0,  i+11);
            FloatPoint fp2 = new FloatPoint(i+222, i+3333);
            RefPoint rp = new RefPoint(i+44444, i+555555);
            float result;
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test100(fp1, fp2, rp, 1, 2, 3, 4);
            }
            float n = test100_helper(fp1, fp2, rp, 1, 2, 3, 4);
            Asserts.assertEQ(result, n);
        }
    }

    // C1->C2 force GC for every allocation when storing the returned
    // fields back into a buffered object.
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public RefPoint test101(RefPoint rp) {
        return test101_helper(rp);
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    public RefPoint test101_helper(RefPoint rp) {
        return rp;
    }

    @Run(test = "test101")
    public void test101_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) {
            RefPoint rp = new RefPoint(1, 2);
            Object x = rp.x;
            Object y = rp.y;
            RefPoint result = new RefPoint(3, 4);
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test101(rp);
            }
            Asserts.assertEQ(rp.x, result.x);
            Asserts.assertEQ(rp.y, result.y);
            Asserts.assertEQ(x, result.x);
            Asserts.assertEQ(y, result.y);
        }
    }

    // Same as test101, except we have Interpreter->C2 instead.
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public RefPoint test102(RefPoint rp) {
        return test102_interp(rp);
    }

    @DontInline
    public RefPoint test102_interp(RefPoint rp) {
        return test102_helper(rp);
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    public RefPoint test102_helper(RefPoint rp) {
        return rp;
    }

    @Run(test = "test102")
    public void test102_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 5;
        for (int i = 0; i < count; i++) {
            RefPoint rp = new RefPoint(11, 22);
            Object x = rp.x;
            Object y = rp.y;
            RefPoint result = new RefPoint(333, 444);
            try (ForceGCMarker m = ForceGCMarker.mark(info.isWarmUp())) {
                result = test102(rp);
            }
            Asserts.assertEQ(rp.x, result.x);
            Asserts.assertEQ(rp.y, result.y);
            Asserts.assertEQ(x, result.x);
            Asserts.assertEQ(y, result.y);
        }
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    public void test103() {
        // when this method is compiled by C1, the Test103Value class is not yet loaded.
        test103_v = new Test103Value(); // invokestatic "Test103Value.<init>()QTest103Value;"
    }

    @LooselyConsistentValue
    static value class Test103Value {
        int x = rI;
    }

    static Object test103_v;

    @Run(test = "test103")
    public void test103_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // Make sure test103() is compiled before Test103Value is loaded
            return;
        }
        test103();
        Test103Value v = (Test103Value)test103_v;
        Asserts.assertEQ(v.x, rI);
    }


    // Same as test103, but with a value class that's too big to return as fields.
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public void test104() {
        // when this method is compiled by C1, the Test104Value class is not yet loaded.
        test104_v = new Test104Value(); // invokestatic "Test104Value.<init>()QTest104Value;"
    }

    @LooselyConsistentValue
    static value class Test104Value {
        long x0 = rL;
        long x1 = rL;
        long x2 = rL;
        long x3 = rL;
        long x4 = rL;
        long x5 = rL;
        long x6 = rL;
        long x7 = rL;
        long x8 = rL;
        long x9 = rL;
        long xa = rL;
        long xb = rL;
        long xc = rL;
        long xd = rL;
        long xe = rL;
        long xf = rL;
    }

    static Object test104_v;

    @Run(test = "test104")
    public void test104_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // Make sure test104() is compiled before Test104Value is loaded
            return;
        }
        test104();
        Test104Value v = (Test104Value)test104_v;
        Asserts.assertEQ(v.x0, rL);
    }

    // C2->C1 invokeinterface -- call Unverified Entry of MyImplVal1.func1 (compiled by C1 - has VVEP_RO)
    /// (same as test94, except we are calling func1, which shares VVEP and VVEP_RO
    @Test(compLevel = CompLevel.C2)
    public int test105(Intf intf, int a, int b) {
        return intf.func1(a, b);
    }

    static Intf test105_intfs[] = {
        new MyImplVal1(),
        new MyImplVal2(),
    };

    @Run(test = "test105")
    public void test105_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test105_intfs[i % test105_intfs.length];
            int result = test105(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func1(123, 456) + i);
        }
    }

    // C2->C2 invokeinterface -- call Unverified Entry of MyImplVal2.func1 (compiled by C2 - has VVEP_RO)
    /// (same as test95, except we are calling func1, which shares VVEP and VVEP_RO
    @Test(compLevel = CompLevel.C2)
    public int test106(Intf intf, int a, int b) {
        return intf.func1(a, b);
    }

    static Intf test106_intfs[] = {
        new MyImplVal2(),
        new MyImplVal1(),
    };

    @Run(test = "test106")
    public void test106_verifier(RunInfo info) {
        int count = info.isWarmUp() ? 1 : 20;
        for (int i = 0; i < count; i++) {
            Intf intf = test106_intfs[i % test106_intfs.length];
            int result = test106(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func1(123, 456) + i);
        }
    }

    // C2->C1 invokeinterface -- C2 calls call Unverified Entry of MyImplVal2X.func1 (compiled by
    //                           C1, with VVEP_RO==VVEP)
    // This test is developed to validate JDK-8230325.
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public int test107(Intf intf, int a, int b) {
        return intf.func1(a, b);
    }

    @ForceCompile
    public void test107_verifier() {
        Intf intf1 = new MyImplVal1X();
        Intf intf2 = new MyImplVal2X();

        for (int i = 0; i < 1000; i++) {
            test107(intf1, 123, 456);
        }
        for (int i = 0; i < 500_000; i++) {
            // Run enough loops so that test107 will be compiled by C2.
            if (i % 30 == 0) {
                // This will indirectly call MyImplVal2X.func1, but the call frequency is low, so
                // test107 will be compiled by C2, but MyImplVal2X.func1 will compiled by C1 only.
                int result = test107(intf2, 123, 456) + i;
                Asserts.assertEQ(result, intf2.func1(123, 456) + i);
            } else {
                // Call test107 with a mix of intf1 and intf2, so C2 will use a virtual call (not an optimized call)
                // for the invokeinterface bytecode in test107.
                test107(intf1, 123, 456);
            }
        }
    }

    @Run(test = "test107")
    @Warmup(0)
    public void run_test107_verifier() {
        test107_verifier();
    }

    // Same as test107, except we call MyImplVal2X.func2 (compiled by C1, VVEP_RO != VVEP)
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public int test108(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    @ForceCompile
    public void test108_verifier() {
        Intf intf1 = new MyImplVal1X();
        Intf intf2 = new MyImplVal2X();

        for (int i = 0; i < 1000; i++) {
            test108(intf1, 123, 456);
        }
        for (int i = 0; i < 500_000; i++) {
            // Run enough loops so that test108 will be compiled by C2.
            if (i % 30 == 0) {
                // This will indirectly call MyImplVal2X.func2, but the call frequency is low, so
                // test108 will be compiled by C2, but MyImplVal2X.func2 will compiled by C1 only.
                int result = test108(intf2, 123, 456) + i;
                Asserts.assertEQ(result, intf2.func2(123, 456, pointField) + i);
            } else {
                // Call test108 with a mix of intf1 and intf2, so C2 will use a virtual call (not an optimized call)
                // for the invokeinterface bytecode in test108.
                test108(intf1, 123, 456);
            }
        }
    }

    @Run(test = "test108")
    @Warmup(0)
    public void run_test108_verifier() {
        test108_verifier();
    }

    // Same as test107, except we call MyImplPojo3.func2 (compiled by C1, VVEP_RO == VEP)
    @Test(compLevel = CompLevel.WAIT_FOR_COMPILATION)
    public int test109(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    @ForceCompile
    public void test109_verifier() {
        Intf intf1 = new MyImplPojo0();
        Intf intf2 = new MyImplPojo3();

        for (int i = 0; i < 1000; i++) {
            test109(intf1, 123, 456);
        }
        for (int i = 0; i < 500_000; i++) {
            // Run enough loops so that test109 will be compiled by C2.
            if (i % 30 == 0) {
                // This will indirectly call MyImplPojo3.func2, but the call frequency is low, so
                // test109 will be compiled by C2, but MyImplPojo3.func2 will compiled by C1 only.
                int result = test109(intf2, 123, 456) + i;
                Asserts.assertEQ(result, intf2.func2(123, 456, pointField) + i);
            } else {
                // Call test109 with a mix of intf1 and intf2, so C2 will use a virtual call (not an optimized call)
                // for the invokeinterface bytecode in test109.
                test109(intf1, 123, 456);
            }
        }
    }

    @Run(test = "test109")
    @Warmup(0)
    public void run_test109_verifier() {
        test109_verifier();
    }
}
