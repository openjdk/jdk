///*
// * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
// * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
// *
// * This code is free software; you can redistribute it and/or modify it
// * under the terms of the GNU General Public License version 2 only, as
// * published by the Free Software Foundation.
// *
// * This code is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// * version 2 for more details (a copy is included in the LICENSE file that
// * accompanied this code).
// *
// * You should have received a copy of the GNU General Public License version
// * 2 along with this work; if not, write to the Free Software Foundation,
// * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
// *
// * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// * or visit www.oracle.com if you need additional information or have any
// * questions.
// */
//
//package compiler.lib.ir_framework.flag;
//
//import compiler.lib.ir_framework.*;
//import org.junit.Assert;
//
//import java.util.List;
//
///*
// * @test
// * @library /test/lib /
// * @run junit compiler.lib.ir_framework.flag.TestCompilationOutputFlags
// */
//public class TestCompilationOutputFlags {
//
//    @org.junit.Test
//    public void level1() {
//        checkIsLevelOnly(Level1A.class, 1);
//        checkIsLevelOnly(Level1B.class, 1);
//        checkIsLevelOnly(Level1C.class, 1);
//        checkIsLevelOnly(Level1All.class, 1);
//    }
//
//    @org.junit.Test
//    public void level2() {
//        checkIsLevelOnly(Level2A.class, 2);
//        checkIsLevelOnly(Level2B.class, 2);
//        checkIsLevelOnly(Level2C.class, 2);
//        checkIsLevelOnly(Level2D.class, 2);
//        checkIsLevelOnly(Level2All.class, 2);
//    }
//
//    @org.junit.Test
//    public void level3() {
//        checkIsLevelOnly(Level3A.class, 3);
//        checkIsLevelOnly(Level3B.class, 3);
//        checkIsLevelOnly(Level3C.class, 3);
//        checkIsLevelOnly(Level3D.class, 3);
//        checkIsLevelOnly(Level3E.class, 3);
//        checkIsLevelOnly(Level3All.class, 3);
//    }
//
//    @org.junit.Test
//    public void defaultLevel() {
//        checkIsDefaultLevelOnly(LevelDefaultA.class);
//        checkIsDefaultLevelOnly(LevelDefaultB.class);
//        checkIsDefaultLevelOnly(LevelDefaultC.class);
//    }
//
//    @org.junit.Test
//    public void levelAndDefaultLevel() {
//        checkLevelAndDefault(Level1DefaultMix.class, 1);
//        checkLevelAndDefault(Level2DefaultMix.class, 2);
//        checkLevelAndDefault(Level3DefaultMix1.class, 3);
//        checkLevelAndDefault(Level3DefaultMix2.class, 3);
//    }
//
//    private static void checkIsLevelOnly(Class<?> testClass, int level) {
//        isLevelOnly(getFlagsForClass(testClass), testClass, level);
//    }
//
//    private static List<String> getFlagsForClass(Class<?> testClass) {
//        CompilationOutputFlags compilationOutputFlags = new CompilationOutputFlags(testClass);
//        return compilationOutputFlags.getFlags();
//    }
//
//    private static void isLevelOnly(List<String> flags, Class<?> testClass, int level) {
//        Assert.assertEquals("only level command", 1, flags.size());
//        Assert.assertEquals("PrintIdealLevel command", flags.get(0), getPrintIdealLevelCommand(testClass, level));
//    }
//
//    private static String getPrintIdealLevelCommand(Class<?> testClass, int level) {
//        return "-XX:CompileCommand=PrintIdealLevel," + testClass.getCanonicalName() + "::*," + level;
//    }
//
//    private static void checkIsDefaultLevelOnly(Class<?> testClass) {
//        isDefaultLevelOnly(getFlagsForClass(testClass), testClass);
//    }
//
//    private static void isDefaultLevelOnly(List<String> flags, Class<?> testClass) {
//        Assert.assertEquals("only level command", 2, flags.size());
//        Assert.assertEquals("PrintIdeal", flags.get(0), getCommandForDefaultFlag(testClass, "PrintIdeal"));
//        Assert.assertEquals("PrintOptoAssembly", flags.get(1), getCommandForDefaultFlag(testClass, "PrintOptoAssembly"));
//    }
//
//    private static String getCommandForDefaultFlag(Class<?> testClass, String flag) {
//        return "-XX:CompileCommand=" + flag + "," + testClass.getCanonicalName() + "::*,true";
//    }
//
//    private static void checkLevelAndDefault(Class<?> testClass, int level) {
//        isDefaultAndLevel(getFlagsForClass(testClass), testClass, level);
//    }
//
//    private static void isDefaultAndLevel(List<String> flags, Class<?> testClass, int level) {
//        Assert.assertEquals("all commands", 3, flags.size());
//        Assert.assertEquals("PrintIdeal", flags.get(0), getCommandForDefaultFlag(testClass, "PrintIdeal"));
//        Assert.assertEquals("PrintOptoAssembly", flags.get(1), getCommandForDefaultFlag(testClass, "PrintOptoAssembly"));
//        Assert.assertEquals("PrintIdealLevel command", flags.get(2), getPrintIdealLevelCommand(testClass, level));
//    }
//}
//
//class Level1A {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    public void test() {
//    }
//}
//
//class Level1B {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.FINAL_CODE)
//    public void test() {
//    }
//}
//
//class Level1C {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.FINAL_CODE)
//    public void test() {
//    }
//
//    @Run(test = "test2")
//    public void run() {
//        test2();
//    }
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.BEFORE_MATCHING)
//    public void test2() {}
//}
//
//class Level1All {
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.FINAL_CODE)
//    public void test() {
//    }
//}
//
//class Level2A {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN1)
//    public void test() {
//    }
//}
//
//class Level2B {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP1)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP2)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP3)
//    public void test() {
//    }
//}
//
//class Level2C {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    public void test() {
//    }
//
//    @Run(test = "test2")
//    public void run() {
//        test2();
//    }
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.ITER_GVN2)
//    public void test2() {}
//}
//
//class Level2D {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
//    public void test() {
//    }
//}
//
//class Level2All {
//    @Test
//    // Level 1
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.FINAL_CODE)
//
//    // Level 2
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN1)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_AFTER_EA)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP1)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP2)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP3)
//    @IR(failOn = "foo", phase = CompilePhase.CCP1)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN2)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
//    @IR(failOn = "foo", phase = CompilePhase.OPTIMIZE_FINISHED)
//    @IR(failOn = "foo", phase = CompilePhase.GLOBAL_CODE_MOTION)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_EA)
//    @IR(failOn = "foo", phase = CompilePhase.MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_INLINE)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_BOXING_INLINE)
//    @IR(failOn = "foo", phase = CompilePhase.MACRO_EXPANSION)
//    @IR(failOn = "foo", phase = CompilePhase.BARRIER_EXPANSION)
//    public void test() {
//    }
//}
//
//class Level3A {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN1)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_REMOVEUSELESS)
//    public void test() {
//    }
//}
//
//class Level3B {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_REMOVEUSELESS)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_BEFORE_EA)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_CLOOPS)
//    public void test() {
//    }
//}
//
//class Level3C {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_REMOVEUSELESS)
//    public void test() {
//    }
//
//    @Run(test = "test2")
//    public void run() {
//        test2();
//    }
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.AFTER_CLOOPS)
//    public void test2() {}
//}
//
//class Level3D {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_BEAUTIFY_LOOPS)
//    public void test() {
//    }
//}
//
//class Level3E {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_REMOVEUSELESS)
//    public void test() {
//    }
//
//    @Run(test = "test2")
//    public void run() {
//        test2();
//    }
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.AFTER_PARSING)
//    public void test2() {}
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.INCREMENTAL_INLINE_STEP)
//    public void test3() {}
//
//    @Check(test = "test3")
//    public void check() {
//    }
//}
//
//class Level3All {
//    @Test
//    // Level 1
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.FINAL_CODE)
//
//    // Level 2
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN1)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEAL_BEFORE_EA)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_AFTER_EA)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_AFTER_ELIMINATION)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP1)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP2)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP3)
//    @IR(failOn = "foo", phase = CompilePhase.CCP1)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN2)
//    @IR(failOn = "foo", phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
//    @IR(failOn = "foo", phase = CompilePhase.OPTIMIZE_FINISHED)
//    @IR(failOn = "foo", phase = CompilePhase.GLOBAL_CODE_MOTION)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_EA)
//    @IR(failOn = "foo", phase = CompilePhase.MATCHING)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_INLINE)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_BOXING_INLINE)
//    @IR(failOn = "foo", phase = CompilePhase.MACRO_EXPANSION)
//    @IR(failOn = "foo", phase = CompilePhase.BARRIER_EXPANSION)
//
//    // Level 3
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_STRINGOPTS)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_STRINGOPTS)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_REMOVEUSELESS)
//    @IR(failOn = "foo", phase = CompilePhase.EXPAND_VUNBOX)
//    @IR(failOn = "foo", phase = CompilePhase.SCALARIZE_VBOX)
//    @IR(failOn = "foo", phase = CompilePhase.INLINE_VECTOR_REBOX)
//    @IR(failOn = "foo", phase = CompilePhase.EXPAND_VBOX)
//    @IR(failOn = "foo", phase = CompilePhase.ELIMINATE_VBOX_ALLOC)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_AFTER_VECTOR)
//    @IR(failOn = "foo", phase = CompilePhase.ITER_GVN_BEFORE_EA)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_CLOOPS)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_CLOOPS)
//    @IR(failOn = "foo", phase = CompilePhase.BEFORE_BEAUTIFY_LOOPS)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_BEAUTIFY_LOOPS)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_INLINE_STEP)
//    @IR(failOn = "foo", phase = CompilePhase.INCREMENTAL_INLINE_CLEANUP)
//    @IR(failOn = "foo", phase = CompilePhase.END)
////    @IR(failOn = "foo", phase = CompilePhase.ALL)
//    public void test() {
//    }
//}
//
//class LevelDefaultA {
//
//    @Test
//    @IR(failOn = "foo")
//    public void test() {
//    }
//}
//
//class LevelDefaultB {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.DEFAULT)
//    public void test() {
//    }
//
//    @Test
//    public void test2() {
//    }
//}
//
//class LevelDefaultC {
//
//    @Test
//    public void test() {
//    }
//
//    @Run(test = "test2")
//    public void run() {
//        test2();
//    }
//
//    @Test
//    @IR(counts = {"foo", "3"}, phase = CompilePhase.DEFAULT)
//    public void test2() {}
//}
//
//class Level1DefaultMix {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    public void test() {
//    }
//
//    @Test
//    @IR(failOn = "foo")
//    public void test2() {
//    }
//}
//
//
//class Level2DefaultMix {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.GLOBAL_CODE_MOTION)
//    public void test() {
//    }
//
//    @Test
//    @IR(failOn = "foo")
//    public void test2() {
//    }
//}
//
//
//class Level3DefaultMix1 {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.GLOBAL_CODE_MOTION)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_CLOOPS)
//    public void test() {
//    }
//
//    @Test
//    @IR(failOn = "foo")
//    public void test2() {
//    }
//}
//
//class Level3DefaultMix2 {
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_PARSING)
//    @IR(failOn = "foo", phase = CompilePhase.DEFAULT)
//    @IR(failOn = "foo", phase = CompilePhase.AFTER_CLOOPS)
//    public void test() {
//    }
//
//    @Test
//    @IR(failOn = "foo")
//    public void test2() {
//    }
//
//    @Test
//    @IR(failOn = "foo", phase = CompilePhase.DEFAULT)
//    public void test3() {
//    }
//}
