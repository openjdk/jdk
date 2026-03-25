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

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import static compiler.valhalla.inlinetypes.InlineTypes.rI;

/*
 * @test
 * @key randomness
 * @summary Test the handling of fields of unloaded value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile hack/GetUnresolvedInlineFieldWrongSignature.java
 * @compile TestUnloadedInlineTypeField.java
 * @run main/othervm/timeout=300 compiler.valhalla.inlinetypes.TestUnloadedInlineTypeField
 */

public class TestUnloadedInlineTypeField {
    // Only prevent loading of classes when testing with C1. Load classes
    // early when executing with C2 to prevent uncommon traps. It's still
    // beneficial to execute this test with C2 because it also checks handling
    // of type mismatches.

    public static void main(String[] args) {
        final Scenario[] scenarios = {
                new Scenario(0),
                new Scenario(1, "-XX:-UseFieldFlattening"),
                new Scenario(2, "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+PatchALot"),
                new Scenario(3, "-XX:-UseFieldFlattening",
                                "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+PatchALot")
        };
        InlineTypes.getFramework()
                   .addScenarios(scenarios)
                   .addFlags("--enable-preview",
                             // Prevent IR Test Framework from loading classes
                             "-DIgnoreCompilerControls=true",
                             // Some tests trigger frequent re-compilation. Don't mark them as non-compilable.
                             "-XX:PerMethodRecompilationCutoff=-1", "-XX:PerBytecodeRecompilationCutoff=-1")
                   .start();
    }

    // Test case 1:
    // The value class field class has been loaded, but the holder class has not been loaded.
    //
    //     aload_0
    //     getfield  MyValue1Holder.v:LMyValue1;
    //               ^ not loaded      ^ already loaded
    //
    // MyValue1 has already been loaded, because it's in the preload attribute of
    // TestUnloadedInlineTypeField, due to TestUnloadedInlineTypeField.test1_precondition().
    @LooselyConsistentValue
    static value class MyValue1 {
        int foo;

        MyValue1() {
            foo = rI;
        }
    }

    static class MyValue1Holder {
        @NullRestricted
        MyValue1 v;

        public MyValue1Holder() {
            v = new MyValue1();
            super();
        }
    }

    static MyValue1 test1_precondition() {
        return new MyValue1();
    }

    @Test
    public int test1(Object holder) {
        if (holder != null) {
            // Don't use MyValue1Holder in the signature, it might trigger class loading
            return ((MyValue1Holder)holder).v.foo;
        } else {
            return 0;
        }
    }

    @Run(test = "test1")
    public void test1_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test1(null);
        } else {
            MyValue1Holder holder = new MyValue1Holder();
            Asserts.assertEQ(test1(holder), rI);
        }
    }

    // Test case 2:
    // Both the value class field class, and the holder class have not been loaded.
    //
    //     aload_0
    //     getfield  MyValue2Holder.v:LMyValue2;
    //               ^ not loaded     ^ not loaded
    //
    // MyValue2 has not been loaded, because it is not explicitly referenced by
    // TestUnloadedInlineTypeField.
    @LooselyConsistentValue
    static value class MyValue2 {
        int foo;

        public MyValue2(int n) {
            foo = n;
        }
    }

    static class MyValue2Holder {
        @NullRestricted
        MyValue2 v;

        public MyValue2Holder() {
            v = new MyValue2(rI);
            super();
        }
    }

    @Test
    public int test2(Object holder) {
        if (holder != null) {
            // Don't use MyValue2Holder in the signature, it might trigger class loading
            return ((MyValue2Holder)holder).v.foo;
        } else {
            return 0;
        }
    }

    @Run(test = "test2")
    public void test2_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test2(null);
        } else {
            MyValue2Holder holder = new MyValue2Holder();
            Asserts.assertEQ(test2(holder), rI);
        }
    }

    // Test case 4:
    // Same as case 1, except we use putfield instead of getfield.
    @LooselyConsistentValue
    static value class MyValue4 {
        int foo;

        MyValue4(int n) {
            foo = n;
        }
    }

    static class MyValue4Holder {
        @NullRestricted
        MyValue4 v;

        public MyValue4Holder() {
            v = new MyValue4(0);
            super();
        }
    }

    @Test
    public void test4(Object holder, MyValue4 v) {
        if (holder != null) {
            // Don't use MyValue4Holder in the signature, it might trigger class loading
            ((MyValue4Holder)holder).v = v;
        }
    }

    @Run(test = "test4")
    public void test4_verifier(RunInfo info) {
        MyValue4 v = new MyValue4(rI);
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test4(null, v);
        } else {
            MyValue4Holder holder = new MyValue4Holder();
            test4(holder, v);
            Asserts.assertEQ(holder.v.foo, rI);
        }
    }

    // Test case 5:
    // Same as case 2, except we use putfield instead of getfield.
    @LooselyConsistentValue
    static value class MyValue5 {
        int foo;

        MyValue5(int n) {
            foo = n;
        }
    }

    static class MyValue5Holder {
        @NullRestricted
        MyValue5 v;

        public MyValue5Holder() {
            v = new MyValue5(0);
            super();
        }

        public Object make(int n) {
            return new MyValue5(n);
        }
    }

    @Test
    public void test5(Object holder, Object o) {
        if (holder != null) {
            // Don't use MyValue5 and MyValue5Holder in the signature, it might trigger class loading
            MyValue5 v = (MyValue5)o;
            ((MyValue5Holder)holder).v = v;
        }
    }

    @Run(test = "test5")
    public void test5_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test5(null, null);
        } else {
            MyValue5Holder holder = new MyValue5Holder();
            Object v = holder.make(rI);
            test5(holder, v);
            Asserts.assertEQ(holder.v.foo, rI);
        }
    }


    // Test case 6: (same as test1, except we use getstatic instead of getfield)
    // The value class field class has been loaded, but the holder class has not been loaded.
    //
    //     getstatic  MyValue6Holder.v:LMyValue1;
    //                ^ not loaded       ^ already loaded
    //
    // MyValue6 has already been loaded, because it's in the preload attribute of
    // TestUnloadedInlineTypeField, due to TestUnloadedInlineTypeField.test1_precondition().
    @LooselyConsistentValue
    static value class MyValue6 {
        int foo;

        MyValue6() {
            foo = rI;
        }
    }

    static class MyValue6Holder {
        @NullRestricted
        static MyValue6 v = new MyValue6();
    }

    static MyValue6 test6_precondition() {
        return new MyValue6();
    }

    @Test
    public int test6(int n) {
        if (n == 0) {
            return 0;
        } else {
            return MyValue6Holder.v.foo + n;
        }
    }

    @Run(test = "test6")
    public void test6_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test6(0);
        } else {
            Asserts.assertEQ(test6(rI), 2*rI);
        }
    }


    // Test case 7:  (same as test2, except we use getstatic instead of getfield)
    // Both the value class field class, and the holder class have not been loaded.
    //
    //     getstatic  MyValue7Holder.v:LMyValue7;
    //                ^ not loaded       ^ not loaded
    //
    // MyValue7 has not been loaded, because it is not explicitly referenced by
    // TestUnloadedInlineTypeField.
    @LooselyConsistentValue
    static value class MyValue7 {
        int foo;

        MyValue7(int n) {
            foo = n;
        }
    }

    static class MyValue7Holder {
        @NullRestricted
        static MyValue7 v = new MyValue7(rI);
    }

    @Test
    public int test7(int n) {
        if (n == 0) {
            return 0;
        } else {
            return MyValue7Holder.v.foo + n;
        }
    }

    @Run(test = "test7")
    public void test7_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test7(0);
        } else {
            Asserts.assertEQ(test7(rI), 2*rI);
        }
    }

    // Test case 8:
    // Same as case 1, except holder is allocated in test method (-> no holder null check required)
    @LooselyConsistentValue
    static value class MyValue8 {
        int foo;

        MyValue8() {
            foo = rI;
        }
    }

    static class MyValue8Holder {
        @NullRestricted
        MyValue8 v;

        public MyValue8Holder() {
            v = new MyValue8();
            super();
        }
    }

    static MyValue8 test8_precondition() {
        return new MyValue8();
    }

    @Test
    public int test8(boolean warmup) {
        if (!warmup) {
            MyValue8Holder holder = new MyValue8Holder();
            return holder.v.foo;
        } else {
            return 0;
        }
    }

    @Run(test = "test8")
    public void test8_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test8(true);
        } else {
            Asserts.assertEQ(test8(false), rI);
        }
    }

    // Test case 9:
    // Same as case 2, except holder is allocated in test method (-> no holder null check required)
    @LooselyConsistentValue
    static value class MyValue9 {
        int foo;

        public MyValue9(int n) {
            foo = n;
        }
    }

    static class MyValue9Holder {
        @NullRestricted
        MyValue9 v;

        public MyValue9Holder() {
            v = new MyValue9(rI);
            super();
        }
    }

    @Test
    public int test9(boolean warmup) {
        if (!warmup) {
            MyValue9Holder holder = new MyValue9Holder();
            return holder.v.foo;
        } else {
            return 0;
        }
    }

    @Run(test = "test9")
    public void test9_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test9(true);
        } else {
            Asserts.assertEQ(test9(false), rI);
        }
    }

    // Test case 11:
    // Same as case 4, except holder is allocated in test method (-> no holder null check required)
    @LooselyConsistentValue
    static value class MyValue11 {
        int foo;

        MyValue11(int n) {
            foo = n;
        }
    }

    static class MyValue11Holder {
        @NullRestricted
        MyValue11 v;

        public MyValue11Holder() {
            v = new MyValue11(0);
            super();
        }
    }

    @Test
    public Object test11(boolean warmup, MyValue11 v) {
        if (!warmup) {
            MyValue11Holder holder = new MyValue11Holder();
            holder.v = v;
            return holder;
        } else {
            return null;
        }
    }

    @Run(test = "test11")
    public void test11_verifier(RunInfo info) {
        MyValue11 v = new MyValue11(rI);
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test11(true, v);
        } else {
            MyValue11Holder holder = (MyValue11Holder)test11(false, v);
            Asserts.assertEQ(holder.v.foo, rI);
        }
    }

    // Test case 12:
    // Same as case 5, except holder is allocated in test method (-> no holder null check required)
    @LooselyConsistentValue
    static value class MyValue12 {
        int foo;

        MyValue12(int n) {
            foo = n;
        }
    }

    static class MyValue12Holder {
        @NullRestricted
        MyValue12 v;

        public MyValue12Holder() {
            v = new MyValue12(0);
            super();
        }
    }

    @Test
    public Object test12(boolean warmup, Object o) {
        if (!warmup) {
            // Don't use MyValue12 in the signature, it might trigger class loading
            MyValue12Holder holder = new MyValue12Holder();
            holder.v = (MyValue12)o;
            return holder;
        } else {
            return null;
        }
    }

    @Run(test = "test12")
    public void test12_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test12(true, null);
        } else {
            MyValue12 v = new MyValue12(rI);
            MyValue12Holder holder = (MyValue12Holder)test12(false, v);
            Asserts.assertEQ(holder.v.foo, rI);
        }
    }

    // Test case 13:
    // Same as case 10, except MyValue13 is allocated in test method
    @LooselyConsistentValue
    static value class MyValue13 {
        int foo;

        public MyValue13() {
            foo = rI;
        }
    }

    static class MyValue13Holder {
        @NullRestricted
        MyValue13 v;

        public MyValue13Holder() {
            v = new MyValue13();
            super();
        }
    }

    static MyValue13 test13_precondition() {
        return new MyValue13();
    }

    @Test
    public void test13(Object holder) {
        // Don't use MyValue13Holder in the signature, it might trigger class loading
        GetUnresolvedInlineFieldWrongSignature.test13(holder);
    }

    @Run(test = "test13")
    public void test13_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test13(null);
        } else {
            // Make sure klass is resolved
            for (int i = 0; i < 10; ++i) {
                test13(new MyValue13Holder());
            }
        }
    }

    // Test case 15:
    // Same as case 13, except MyValue15 is unloaded
    @LooselyConsistentValue
    static value class MyValue15 {
        int foo;

        public MyValue15() {
            foo = rI;
        }
    }

    static class MyValue15Holder {
        @NullRestricted
        MyValue15 v;

        public MyValue15Holder() {
            v = new MyValue15();
            super();
        }
    }

    @Test
    public void test15(Object holder) {
        // Don't use MyValue15Holder in the signature, it might trigger class loading
        GetUnresolvedInlineFieldWrongSignature.test15(holder);
    }

    @Run(test = "test15")
    public void test15_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test15(null);
        } else {
            // Make sure klass is resolved
            for (int i = 0; i < 10; ++i) {
                test15(new MyValue15Holder());
            }
        }
    }

    // Test case 16:
    // Value class with field which is not a value class
    static class MyValue16 {
        int foo;

        public MyValue16() {
            foo = rI;
        }
    }

    static MyValue16 test16_precondition() {
        return new MyValue16();
    }

    @Test
    public Object test16(boolean warmup) {
        return GetUnresolvedInlineFieldWrongSignature.test16(warmup);
    }

    @Run(test = "test16")
    public void test16_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test16(true);
        } else {
            // Make sure klass is resolved
            for (int i = 0; i < 10; ++i) {
                test16(false);
            }
        }
    }

    // Test case 17:
    // Same as test16 but with unloaded type at init
    static class MyValue17 {
        int foo;

        public MyValue17() {
            foo = rI;
        }
    }

    @Test
    public Object test17(boolean warmup) {
        return GetUnresolvedInlineFieldWrongSignature.test17(warmup);
    }

    @Run(test = "test17")
    public void test17_verifier(RunInfo info) {
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test17(true);
        } else {
            // Make sure klass is resolved
            for (int i = 0; i < 10; ++i) {
                test17(false);
            }
        }
    }

    // Test case 18:
    // Same as test7 but with the holder being loaded
    @LooselyConsistentValue
    static value class MyValue18 {
        int foo;

        MyValue18(int n) {
            foo = n;
        }
    }

    static class MyValue18Holder {
        @NullRestricted
        static MyValue18 v = new MyValue18(rI);
    }

    @Test
    public int test18(int n) {
        if (n == 0) {
            return 0;
        } else {
            return MyValue18Holder.v.foo + n;
        }
    }

    @Run(test = "test18")
    public void test18_verifier(RunInfo info) {
        // Make sure MyValue18Holder is loaded
        MyValue18Holder holder = new MyValue18Holder();
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test18(0);
        } else {
            Asserts.assertEQ(test18(rI), 2*rI);
        }
    }

    // Test case 19:
    // Same as test18 but uninitialized (null) static value class field
    @LooselyConsistentValue
    static value class MyValue19 {
        int foo;

        MyValue19(int n) {
            foo = n;
        }
    }

    static class MyValue19Holder {
        @NullRestricted
        static MyValue19 v = new MyValue19(0);
    }

    @Test
    public int test19(int n) {
        if (n == 0) {
            return 0;
        } else {
            return MyValue19Holder.v.foo + n;
        }
    }

    @Run(test = "test19")
    public void test19_verifier(RunInfo info) {
        // Make sure MyValue19Holder is loaded
        MyValue19Holder holder = new MyValue19Holder();
        if (info.isWarmUp() && !info.isC2CompilationEnabled()) {
            test19(0);
        } else {
            Asserts.assertEQ(test19(rI), rI);
        }
    }

    // Test case 20:
    // Value class with object field of unloaded type.
    static class MyObject20 {
        int x = 42;
    }

    @LooselyConsistentValue
    static value class MyValue20 {
        MyObject20 obj;

        MyValue20() {
            this.obj = null;
        }
    }

    @Test
    public MyValue20 test20() {
        return new MyValue20();
    }

    @Run(test = "test20")
    public void test20_verifier() {
        MyValue20 vt = test20();
        Asserts.assertEQ(vt.obj, null);
    }

    @LooselyConsistentValue
    static value class Test21ClassA {
        static Test21ClassB b;
        @NullRestricted
        static Test21ClassC c = new Test21ClassC();
    }

    @LooselyConsistentValue
    static value class Test21ClassB {
        static int x = Test21ClassA.c.x;
    }

    @LooselyConsistentValue
    static value class Test21ClassC {
        int x = 42;
    }

    // Test access to static value class field with unloaded type
    @Test
    public Object test21() {
        return new Test21ClassA();
    }

    @Run(test = "test21")
    public void test21_verifier() {
        Object ret = test21();
        Asserts.assertEQ(Test21ClassA.b.x, 42);
        Asserts.assertEQ(Test21ClassA.c.x, 42);
    }

    static boolean test22FailInit = true;

    @LooselyConsistentValue
    static value class Test22ClassA {
        int x = 0;
        @NullRestricted
        static Test22ClassB b = new Test22ClassB();
    }

    @LooselyConsistentValue
    static value class Test22ClassB {
        int x = 0;
        static {
            if (test22FailInit) {
                throw new RuntimeException("Init failed");
            }
        }
    }

    // Test that load from static field of uninitialized value class throws an exception
    @Test
    public Object test22() {
        return Test22ClassA.b;
    }

    @Run(test = "test22")
    public void test22_verifier() {
        // Trigger initialization error in Test22ClassB
        try {
            Test22ClassB b = new Test22ClassB();
            throw new RuntimeException("Should have thrown error during initialization");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected
        }
        try {
            test22();
            throw new RuntimeException("Should have thrown NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            // Expected
        }
    }

    static boolean test23FailInit = true;

    @LooselyConsistentValue
    static value class Test23ClassA {
        int x = 0;
        @NullRestricted
        static Test23ClassB b = new Test23ClassB();
    }

    @LooselyConsistentValue
    static value class Test23ClassB {
        static {
            if (test23FailInit) {
                throw new RuntimeException("Init failed");
            }
        }
    }

    // Same as test22 but with empty ClassB
    @Test
    public Object test23() {
        return Test23ClassA.b;
    }

    @Run(test = "test23")
    public void test23_verifier() {
        // Trigger initialization error in Test23ClassB
        try {
            Test23ClassB b = new Test23ClassB();
            throw new RuntimeException("Should have thrown error during initialization");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected
        }
        try {
            test23();
            throw new RuntimeException("Should have thrown NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            // Expected
        }
    }

    static boolean test24FailInit = true;

    @LooselyConsistentValue
    static value class Test24ClassA {
        @NullRestricted
        Test24ClassB b = new Test24ClassB();
    }

    @LooselyConsistentValue
    static value class Test24ClassB {
        int x = 0;
        static {
            if (test24FailInit) {
                throw new RuntimeException("Init failed");
            }
        }
    }

    // Test that access to non-static field of uninitialized value class throws an exception
    @Test
    public Object test24() {
        return (new Test24ClassA()).b.x;
    }

    @Run(test = "test24")
    public void test24_verifier() {
        // Trigger initialization error in Test24ClassB
        try {
            Test24ClassB b = new Test24ClassB();
            throw new RuntimeException("Should have thrown error during initialization");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected
        }
        try {
            test24();
            throw new RuntimeException("Should have thrown NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            // Expected
        }
    }

    static boolean test25FailInit = true;

    @LooselyConsistentValue
    static value class Test25ClassA {
        @NullRestricted
        Test25ClassB b = new Test25ClassB();
    }

    @LooselyConsistentValue
    static value class Test25ClassB {
        int x = 24;
        static {
            if (test25FailInit) {
                throw new RuntimeException("Init failed");
            }
        }
    }

    // Same as test24 but with field access outside of test method
    @Test
    public Test25ClassB test25() {
        return (new Test25ClassA()).b;
    }

    @Run(test = "test25")
    public void test25_verifier() {
        // Trigger initialization error in Test25ClassB
        try {
            Test25ClassB b = new Test25ClassB();
            throw new RuntimeException("Should have thrown error during initialization");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected
        }
        try {
            Test25ClassB res = test25();
            Asserts.assertEQ(res.x, 0);
            throw new RuntimeException("Should have thrown NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            // Expected
        }
    }

    static boolean test26FailInit = true;

    @LooselyConsistentValue
    static value class Test26ClassA {
        @NullRestricted
        Test26ClassB b = new Test26ClassB();
    }

    @LooselyConsistentValue
    static value class Test26ClassB {
        static {
            if (test26FailInit) {
                throw new RuntimeException("Init failed");
            }
        }
    }

    // Same as test25 but with empty ClassB
    @Test
    public Object test26() {
        return (new Test26ClassA()).b;
    }

    @Run(test = "test26")
    public void test26_verifier() {
        // Trigger initialization error in Test26ClassB
        try {
            Test26ClassB b = new Test26ClassB();
            throw new RuntimeException("Should have thrown error during initialization");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected
        }
        try {
            test26();
            throw new RuntimeException("Should have thrown NoClassDefFoundError");
        } catch (NoClassDefFoundError e) {
            // Expected
        }
    }

    @LooselyConsistentValue
    static value class MyValue27 {
        int foo = 42;
    }

    static class MyValue27Holder {
        MyValue27 v;
    }

    // Make sure MyValue27Holder is loaded but MyValue27 is not
    Class test27Class = MyValue27Holder.class;

    // Test unloaded value class field load from loaded holder
    @Test
    public static Object test27() {
        MyValue27Holder holder = new MyValue27Holder();
        return holder.v;
    }

    @Run(test = "test27")
    public void test27_verifier() {
        Asserts.assertEQ(test27(), null);
    }

    @LooselyConsistentValue
    static value class MyValue28 {
        @NullRestricted
        static MyValue28 field1 = new MyValue28();
    }

    // Test null store to null restricted field with unloaded holder
    @Test
    public static void test28() {
        MyValue28.field1 = null;
    }

    @Run(test = "test28")
    @Warmup(0) // Make sure that MyValue28 is not loaded
    public void test28_verifier() {
        try {
            test28();
            throw new RuntimeException("No exception thrown");
        } catch (NullPointerException e) {
            // Expected
        }
    }
}
