/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
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
 *
 */

/**
 * @test id=COMP_NONE
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyContinuations -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_NONE
 */

/**
 * @test id=COMP_WINDOW_LENGTH_1
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyContinuations -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_1
 */

/**
 * @test id=COMP_WINDOW_LENGTH_2
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyContinuations -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_2
 */

/**
 * @test id=COMP_WINDOW_LENGTH_3
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyContinuations -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_3
 */

/**
 * @test id=COMP_ALL
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyContinuations -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_ALL
 */

/**
 * @test id=COMP_NONE-GC_AFTER_YIELD
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_NONE GC_AFTER_YIELD
 */

/**
 * @test id=COMP_WINDOW_LENGTH_1-GC_AFTER_YIELD
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_1 GC_AFTER_YIELD
 */

/**
 * @test id=COMP_WINDOW_LENGTH_2-GC_AFTER_YIELD
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_2 GC_AFTER_YIELD
 */

/**
 * @test id=COMP_WINDOW_LENGTH_3-GC_AFTER_YIELD
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_WINDOW_LENGTH_3 GC_AFTER_YIELD
 */

/**
 * @test id=COMP_ALL-GC_AFTER_YIELD
 * @summary Collection of basic continuation tests. CompilationPolicy controls which frames in a sequence should be compiled when calling Continuation.yield().
 * @requires vm.continuations
 * @requires vm.flavor == "server" & vm.opt.TieredCompilation != true
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build java.base/java.lang.StackWalkerHelper
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                     -Xbatch -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     BasicExt COMP_ALL GC_AFTER_YIELD
 */

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;
import static jdk.test.lib.Asserts.*;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

public class BasicExt {
    static final ContinuationScope THE_SCOPE = new ContinuationScope() {};

    public static final Pattern PAT_COMP_NONE  = Pattern.compile("COMP_NONE");
    public static final Pattern PAT_COMP_ALL   = Pattern.compile("COMP_ALL");
    public static final Pattern PAT_CONT_METHS = Pattern.compile("^(enter|enter0|yield|yield0)$");

    public static CompilationPolicy compPolicySelection;
    public static DeoptBehaviour deoptBehaviour;
    public static GCBehaviour gcBehaviour;
    public static int compLevel;

    public static final WhiteBox WB = WhiteBox.getWhiteBox();

    enum TestCaseVariants {
        NO_VARIANT,
        // Exception
        THROW_HANDLED_EXCEPTION,
        THROW_UNHANDLED_EXCEPTION,
        // Synchronization
        ALLOC_MONITOR,
        // There are values on the expression stack that are not call parameters
        EXPR_STACK_NOT_EMPTY,
    }

    enum GCBehaviour {
        GC_AFTER_YIELD,
        NO_GC_AFTER_YIELD,
    }

    enum DeoptBehaviour {
        DEOPT_AFTER_YIELD,
        NO_DEOPT_AFTER_YIELD,
    }

    public static class HandledException extends Exception { }
    public static class UnhandledException extends Error { }

    public static void main(String[] args) {
        try {
            // Run tests with C2 compilations
            compLevel = CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
            // // Run tests with C1 compilations
            // compLevel = CompilerWhiteBoxTest.COMP_LEVEL_FULL_PROFILE;

            parseArgument(args);
            runTests();
        } catch (Throwable t) {
            throw t;
        }
    }

    public static void parseArgument(String[] args) {
        compPolicySelection = CompilationPolicy.COMP_NONE;
        deoptBehaviour = DeoptBehaviour.NO_DEOPT_AFTER_YIELD;
        gcBehaviour = GCBehaviour.NO_GC_AFTER_YIELD;
        for (int i = 0; i < args.length; i++) {
            try {
                compPolicySelection = CompilationPolicy.valueOf(args[i]);
            } catch(IllegalArgumentException e) { /* ignored */ }
            try {
                deoptBehaviour = DeoptBehaviour.valueOf(args[i]);
            } catch(IllegalArgumentException e) { /* ignored */ }
            try {
                gcBehaviour = GCBehaviour.valueOf(args[i]);
            } catch(IllegalArgumentException e) { /* ignored */ }
        }
    }

    public static void runTests() {
        System.out.println("$$$0 Running test cases with the following settings:");
        System.out.println("compLevel=" + compLevel);
        System.out.println("compPolicySelection=" + compPolicySelection);
        System.out.println("deoptbehaviour=" + deoptBehaviour);
        System.out.println("gcBehaviour=" + gcBehaviour);
        System.out.println();

        WB.deoptimizeAll();

        runTests(compPolicySelection);
    }

    public static void runTests(CompilationPolicy compPolicy) {
        System.out.println("$$$1 Running test cases with the following policy:");
        compPolicy.print(); System.out.println();

        new ContinuationRunYieldRunTest().runTestCase(3, compPolicy);
        new Continuation3Frames(TestCaseVariants.NO_VARIANT).runTestCase(4, compPolicy);
        new Continuation3Frames(TestCaseVariants.THROW_HANDLED_EXCEPTION).runTestCase(4, compPolicy);
        new Continuation3Frames(TestCaseVariants.THROW_UNHANDLED_EXCEPTION).runTestCase(4, compPolicy);
        new Continuation3Frames(TestCaseVariants.ALLOC_MONITOR).runTestCase(4, compPolicy);
        new Continuation3Frames(TestCaseVariants.EXPR_STACK_NOT_EMPTY).runTestCase(4, compPolicy);
        new ContinuationRunYieldRunTest().runTestCase( 1, compPolicy);
        new ContinuationYieldEnlargeStackYield().runTestCase(1, compPolicy);
        new ContinuationYieldReduceStackYield().runTestCase(1, compPolicy);
        new ContinuationCompiledFramesWithStackArgs_3c0().runTestCase(1, compPolicy);
        new ContinuationCompiledFramesWithStackArgs_3c4().runTestCase(1, compPolicy);
        if (deoptBehaviour == DeoptBehaviour.NO_DEOPT_AFTER_YIELD) {
            DeoptBehaviour savedDeoptBehaviour = deoptBehaviour;
            try {
                // run at least the following test case with deoptimization
                deoptBehaviour = DeoptBehaviour.DEOPT_AFTER_YIELD;
                new ContinuationCompiledFramesWithStackArgs_3c4().runTestCase(1, compPolicy);
            } finally {
                deoptBehaviour = savedDeoptBehaviour;
            }
        }
        new ContinuationCompiledFramesWithStackArgs().runTestCase(1, compPolicy);
        new ContinuationDeepRecursion().runTestCase(3, compPolicy);
        new ContinuationDeepRecursionStackargs().runTestCase(3, compPolicy);
    }

    // Control which frames are compiled/interpreted when calling Continuation.yield()
    // With COMP_WINDOW the methods in the window are supposed to be compiled and others
    // are interpreted. With DEOPT_WINDOW vice versa.
    // The methods that are subject to the CompilationPolicy are set with setMethods().
    // Their order has to correspond to the stack order when calling yield().
    public static enum CompilationPolicy {
        COMP_NONE(7 /*warmup*/, PAT_COMP_NONE, PAT_COMP_NONE /*Cont. pattern*/),
        COMP_WINDOW_LENGTH_1(7 /*warmup*/, 1 /* length comp. window */),
        COMP_WINDOW_LENGTH_2(7 /*warmup*/, 2 /* length comp. window */),
        COMP_WINDOW_LENGTH_3(7 /*warmup*/, 3 /* length comp. window */),
        COMP_ALL(7 /*warmup*/, PAT_COMP_ALL, PAT_CONT_METHS /*Cont. pattern*/);
        public int warmupIterations;
        public Pattern methodPattern;
        public Pattern contMethPattern;

        public CompWindowMode compWindowMode;
        public int winPos;
        public int winLen;

        public Method[] methods;

        public enum CompWindowMode {
            NO_COMP_WINDOW, COMP_WINDOW, DEOPT_WINDOW
        }

        CompilationPolicy(int warmupIterations, Pattern methodPattern,
                                 Pattern contMethPattern) {
            this(warmupIterations, 0, methodPattern, contMethPattern,
                 CompWindowMode.NO_COMP_WINDOW);
        }

        CompilationPolicy(int warmupIterations, int windowLength,
                                 Pattern methodPattern, Pattern contMethPattern) {
            this(warmupIterations, windowLength, methodPattern, contMethPattern,
                 CompWindowMode.COMP_WINDOW);
        }

        CompilationPolicy(int warmupIterations, int windowLength,
                                 Pattern methodPattern, Pattern contMethPattern,
                                 CompWindowMode startMode) {
            this.warmupIterations = warmupIterations;
            this.methodPattern = methodPattern;
            this.contMethPattern = contMethPattern;
            this.winPos = 0;
            this.winLen = windowLength;
            this.compWindowMode = startMode;
        }

        CompilationPolicy(int warmupIterations, int windowLength) {
            this(warmupIterations, windowLength, PAT_COMP_ALL, PAT_CONT_METHS);
        }

        public int warmupIterations() {
            return this.warmupIterations;
        }

        public boolean compileMethods() {
            boolean newCompilation = false;
            log("@@ Compiling test methods according to compilation policy");
            print();
            for (int i = 0; i < methods.length; i++) {
                Method meth = methods[i];
                boolean inWindow = i >= winPos && i < (winPos + winLen);
                boolean shouldBeCompiled = compWindowMode == CompWindowMode.NO_COMP_WINDOW
                    || (inWindow && compWindowMode == CompWindowMode.COMP_WINDOW)
                    || (!inWindow && compWindowMode == CompWindowMode.DEOPT_WINDOW);
                boolean isCompiled = WB.isMethodCompiled(meth);
                log("methods[" + i + "] inWindow=" + inWindow + " isCompiled=" + isCompiled +
                    " shouldBeCompiled=" + shouldBeCompiled + " method=`" + meth + "`");
                if (isCompiled != shouldBeCompiled) {
                    if (shouldBeCompiled) {
                        log("           Compiling methods[" + i + "]");
                        enqForCompilation(meth);
                        newCompilation = true;
                        assertTrue(WB.isMethodCompiled(meth), "Run with -Xbatch");
                    } else {
                        assertFalse(WB.isMethodQueuedForCompilation(meth), "Run with -Xbatch");
                        log("           Deoptimizing methods[" + i + "]");
                        WB.deoptimizeMethod(meth);
                    }
                }
            }
            return newCompilation;
        }

        @SuppressWarnings("deprecation")
        public boolean enqForCompilation(Method meth) {
            return WB.enqueueMethodForCompilation(meth, compLevel);
        }

        public void log(String m) {
            System.out.println(m);
        }

        public void print() {
            log("warmupIterations=" + warmupIterations);
            log("methodPattern=" + methodPattern);
            log("continuationMethPattern=" + contMethPattern);
            log("compWindowMode=" + compWindowMode);
            log("winLen=" + winLen);
        }

        public void setMethods(Method[] methods) {
            this.methods = methods;
            if (compWindowMode == CompWindowMode.NO_COMP_WINDOW) {
                winLen = methods.length;
            }
        }

        public boolean shiftWindow() {
            if (compWindowMode == CompWindowMode.NO_COMP_WINDOW) return false;
            if (++winPos == methods.length) {
                winPos = 0;
                if (compWindowMode == CompWindowMode.DEOPT_WINDOW) {
                    compWindowMode = CompWindowMode.COMP_WINDOW;
                    return false; // we're done
                }
                compWindowMode = CompWindowMode.DEOPT_WINDOW;
            }
            return true; // continue
        }
    }

    /**
     * Base class for test cases
     */
    public static abstract class TestCaseBase implements Runnable {
        public int yieldCalls;
        public int warmUpCount;
        public CompilationPolicy compPolicy;
        public final TestCaseVariants testVariant;

        public TestCaseBase() {
            testVariant = TestCaseVariants.NO_VARIANT;
        }

        public TestCaseBase(TestCaseVariants excBehav) {
            this.testVariant = excBehav;
        }

        public void log_dontjit() {
            System.out.println();
        }

        public void log_dontjit(String m) {
            if (warmUpCount > 0) {
                System.out.print("[" + warmUpCount + "] ");
            }
            System.out.println(m);
        }

        public void runTestCase(int yieldCalls, CompilationPolicy compPolicy) {
            this.yieldCalls = yieldCalls;
            log_dontjit(">>>> Executing test case " + getClass().getName() +
                        " (yieldCalls=" + yieldCalls + ", " + "testVariant=" + testVariant + ")");
            init(compPolicy);
            try {
                log_dontjit("Warm-up test case");
                setup_dontjit(true /* for warmup */);
                for(warmUpCount = 1; warmUpCount <= compPolicy.warmupIterations(); warmUpCount++) {
                    testEntry_dontinline();
                }
                warmUpCount = 0;
                log_dontjit("Warm-up test case DONE");

                setup_dontjit(false /* for warmup */);
                do {
                    compPolicy.compileMethods();
                    do {
                        log_dontjit("Running test case (Reresolve Call Sites)");
                        testEntry_dontinline();
                        log_dontjit("Running test case DONE  (Reresolve Call Sites)");
                    } while(compPolicy.compileMethods());

                    log_dontjit("Running test case BEGIN");
                    testEntry_dontinline();
                    log_dontjit("Running test case DONE");
                } while(compPolicy.shiftWindow());
            } finally {
                log_dontjit("<<<< Finished test case " + getClass().getName()); log_dontjit();
            }
        }

        public void setup_dontjit(boolean warmup) {
        }

        public void init(CompilationPolicy compPolicy) {
            this.compPolicy = compPolicy;
            ArrayList<Method> selectedMethods = new ArrayList<Method>();
            Pattern p = compPolicy.methodPattern;
            if (p != PAT_COMP_NONE) {
                Class<? extends TestCaseBase> c = getClass();
                Method methods[] = c.getDeclaredMethods();
                for (Method meth : methods) {
                    if (p == PAT_COMP_ALL || p.matcher(meth.getName()).matches()) {
                        if (!meth.getName().contains("dontjit")) {
                            selectedMethods.add(meth);
                        }
                    }
                }
            }

            p = compPolicy.contMethPattern;
            if (compPolicy.contMethPattern != PAT_COMP_NONE) {
                Class<?> c = Continuation.class;
                Method methods[] = c .getDeclaredMethods();
                for (Method meth : methods) {
                    if (p.matcher(meth.getName()).matches()) {
                        selectedMethods.add(meth);
                    }
                }
            }
            // Sort in caller/callee order
            selectedMethods.sort(new Comparator<Method>() {
                    @Override
                    public int compare(Method m1, Method m2) {
                        String n1 = m1.getName();
                        String n2 = m2.getName();
                        // log_dontjit("n1=" + n1 + " n2=" + n2);
                        int p1 = -1;
                        int p2 = -1;
                        int i = n1.indexOf("ord");
                        if (i >= 0) {
                            p1 = Integer.parseInt(n1.substring(i + 3, i + 6));
                        }
                        i = n2.indexOf("ord");
                        if (i >= 0) {
                            p2 = Integer.parseInt(n2.substring(i + 3, i + 6));
                        }
                        if (p1 < 0) p1 = getScoreKnownMethods(n1);
                        if (p2 < 0) p2 = getScoreKnownMethods(n2);
                        assertFalse(p1 == -1 || p2 == -1, "Cannot compare " + n1 + " with " + n2);
                        return p1 - p2;
                    }

                    private int getScoreKnownMethods(String n) {
                        int p = -1;
                        if (n.equals("enter"))  p = 20;   // Continuation.enter
                        if (n.equals("enter0")) p = 30;   // Continuation.enter0
                        if (n.equals("run"))    p = 50;   // Called by Continuation.enter0
                        if (n.equals("yield"))  p = 1000; // caller of yield0
                        if (n.equals("yield0")) p = 2000; // top frame
                        return p;
                    }
                });
            compPolicy.setMethods(selectedMethods.toArray(new Method[selectedMethods.size()]));
        }

        public void testEntry_dontinline() {
            Continuation cont = new Continuation(THE_SCOPE, this);
            do {
                try {
                    cont.run();
                } catch (UnhandledException e) {
                    log_dontjit("Exc: " + e);
                }
                if (gcBehaviour == GCBehaviour.GC_AFTER_YIELD) WB.youngGC();
                checkFrames_dontjit(cont);
            } while (!cont.isDone());
        }

        public void checkFrames_dontjit(Continuation cont) {
        } // Override in subclass as appropriate

        @Override
        public void run() {
            fail("Should not call TestCaseBase::run");
        }

        public void sleep(Duration d) {
            try { Thread.sleep(d); }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        static final long i1=1; static final long i2=2; static final long i3=3;
        static final long i4=4; static final long i5=5; static final long i6=6;
        static final long i7=7; static final long i8=8; static final long i9=9;
        static final long i10=10; static final long i11=11; static final long
        i12=12; static final long i13=13; static final long i14=14; static final
        long i15=15; static final long i16=16;
    }

    /**
     * Trivial run/yield/run test
     */
    public static class ContinuationRunYieldRunTest extends TestCaseBase {
        public String sField;

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            for(int i = 0; i < yieldCalls; i++) {
                log_dontjit("Yield #" + i);
                String s1 = "str1";
                Continuation.yield(THE_SCOPE);
                if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                    WB.deoptimizeFrames(false /* makeNotEntrant */);
                }
                String s2 = s1 + "str2";
                sField = s2;
            }
        }
    }

    /**
     * Yield, make continuation (stack) larger, yield again.
     */
    public static class ContinuationYieldEnlargeStackYield extends TestCaseBase {
        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("Back from 1st yield. Now call a method to make the stack larger.");
            ord101_callYieldWithLargerStackAgain_dontinline();
        }

        private void ord101_callYieldWithLargerStackAgain_dontinline() {
            log_dontjit("Now there's a new frame on stack. Call yield again.");
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("Back again after 2nd yield.");
        }
    }


    /**
     * Yield, make continuation (stack) larger, yield again.
     */
    public static class ContinuationYieldReduceStackYield extends TestCaseBase {
        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            ord101_methodWithFirstYield_dontinline();
            log_dontjit("The frame of ord101_methodWithFirstYield_dontinline has been removed now. Call yield again.");
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("Back again after 2nd yield.");
        }

        public void ord101_methodWithFirstYield_dontinline() {
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("Back from 1st yield. Now return to reduce stack size.");
        }
    }

    /**
     * Freeze/thaw 3 compiled frames.
     */
    public static class Continuation3Frames extends TestCaseBase {
        public int yieldCount;
        public long resLong;
        public volatile String putOnExprStack;

        public Continuation3Frames(TestCaseVariants excBehav) {
            super(excBehav);
        }

        @Override
        public void run() {
            for(int i = 0; i < yieldCalls; i++) {
                Throwable caughtException = null;
                putOnExprStack = "exprStckVal ";
                resLong = 0;
                try {
                    String s1 = "str1";
                    String result = ord101_testMethod_dontinline(i1, i2, i3, s1);
                    assertEQ(resLong, testVariant == TestCaseVariants.ALLOC_MONITOR ? 7L : 6L);
                    assertEQ(result, testVariant == TestCaseVariants.EXPR_STACK_NOT_EMPTY ?
                             "exprStckVal str1str2str3" : "str1str2str3");
                } catch (HandledException e) {
                    caughtException = e;
                }
                assertTrue(testVariant != TestCaseVariants.THROW_HANDLED_EXCEPTION
                           || (caughtException instanceof HandledException),
                           "Exception handling error");
            }
        }

        public String ord101_testMethod_dontinline(long a1, long a2, long a3, String s1)
            throws HandledException {
            String s2 = s1 + "str2";
            return ord102_testMethod_dontinline(a1, a2, a3, s2);
        }

        public String ord102_testMethod_dontinline(long a1, long a2, long a3, String s2)
            throws HandledException {
            if (testVariant == TestCaseVariants.ALLOC_MONITOR) {
                synchronized (this) {
                    resLong++;
                }
            }
            if (testVariant == TestCaseVariants.EXPR_STACK_NOT_EMPTY) {
                return putOnExprStack_testMethod_dontjit_dontinline()
                    + ord103_testMethod_dontinline(a1, a2, a3, s2);
            } else {
                return ord103_testMethod_dontinline(a1, a2, a3, s2);
            }
        }

        public String ord103_testMethod_dontinline(long a1, long a2, long a3, String s2)
            throws HandledException {
            return ord104_testMethod_dontinline(a1, a2, a3, s2);
        }

        public String ord104_testMethod_dontinline(long a1, long a2, long a3, String s2)
            throws HandledException {
            long res = a2;
            String s3 = s2 + "str3";
            log_dontjit("Yield #" + yieldCount++);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("/Yield #" + yieldCount);
            if (testVariant == TestCaseVariants.THROW_HANDLED_EXCEPTION) {
                log_dontjit("Exc: throw handled");
                throw new HandledException();
            }
            if (testVariant == TestCaseVariants.THROW_UNHANDLED_EXCEPTION) {
                log_dontjit("Exc: throw unhandled");
                throw new UnhandledException();
            }
            resLong += res + a1 + a3;
            return s3;
        }

        public String putOnExprStack_testMethod_dontjit_dontinline() {
            return putOnExprStack;
        }

        @Override
        public void checkFrames_dontjit(Continuation cont) {
            List<String> frames =
                cont.stackWalker()
                .walk(fs -> fs.map(StackWalker.StackFrame::getMethodName).collect(Collectors.toList()));
            assertEquals(frames, cont.isDone() ? List.of()
                         : Arrays.asList("yield0", "yield", "ord104_testMethod_dontinline",
                                         "ord103_testMethod_dontinline",
                                         "ord102_testMethod_dontinline",
                                         "ord101_testMethod_dontinline",
                                         "run", "enter0", "enter"));
        }
    }

    /**
     * Deep recursion to exercise fast freezing into non-empty chunk
     */
    public static class ContinuationDeepRecursion extends TestCaseBase {
        public int limit;
        public int yield1_depth;
        public int yield2_depth;

        @Override
        public void setup_dontjit(boolean warmup) {
            if (warmup) {
                limit = 10;
                yield1_depth = 7;
                yield2_depth = 3;
            } else {
                limit = 100;
                yield1_depth = 70;
                yield2_depth = 60;
            }
        }

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            ord101_recurse_dontinline(0);
        }

        public void ord101_recurse_dontinline(int depth) {
            if (depth >= limit) {
                log_dontjit("yield at depth " + depth);
                ord102_yield_dontinline(0);
                log_dontjit("After yield at depth " + depth);
                return;
            }
            ord101_recurse_dontinline(depth + 1);
            if (depth == yield1_depth || depth == yield2_depth) {
                log_dontjit("yield at depth " + depth);
                ord102_yield_dontinline(0);
                log_dontjit("After yield at depth " + depth);
            }
        }

        // Add a few frames before yield
        public void ord102_yield_dontinline(int depth) {
            if (depth >= 2) {
                Continuation.yield(THE_SCOPE);
                if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                    WB.deoptimizeFrames(false /* makeNotEntrant */);
                }
                return;
            }
            ord102_yield_dontinline(depth + 1);
        }
    }

    /**
     * Deep recursion to exercise fast freezing into non-empty chunk.
     * nmethods have stack arguments.
     */
    public static class ContinuationDeepRecursionStackargs extends TestCaseBase {
        public int limit;
        public int yield1_depth;
        public int yield2_depth;

        @Override
        public void setup_dontjit(boolean warmup) {
            if (warmup) {
                limit = 10;
                yield1_depth = 7;
                yield2_depth = 3;
            } else {
                limit = 100;
                yield1_depth = 70;
                yield2_depth = 60;
            }
        }

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            long res = ord101_recurse_dontinline(0, i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11);
            if (res != i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10 + i11) {
                throw new Error();
            }
        }

        public long ord101_recurse_dontinline(int depth, long l1, long l2, long
                                              l3, long l4, long l5, long l6,
                                              long l7, long l8, long l9, long
                                              l10, long l11) {
            if (depth >= limit) {
                log_dontjit("yield at depth " + depth);
                ord102_yield_dontinline(0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11);
                log_dontjit("After yield at depth " + depth);
                return l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11;
            }
            long res = ord101_recurse_dontinline(depth + 1, l1, l2, l3, l4, l5,
                                                 l6, l7, l8, l9, l10, l11);
            if (res != l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11) {
                throw new Error();
            }
            if (depth == yield1_depth || depth == yield2_depth) {
                log_dontjit("yield at depth " + depth);
                long res1 = ord102_yield_dontinline(0, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11);
                if (res1 != l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11) {
                    throw new Error();
                }
                log_dontjit("After yield at depth " + depth);
            }
            return res;
        }

        // Add a few frames before yield
        public long ord102_yield_dontinline(int depth, long l1, long l2, long l3, long l4, long l5,
                                            long l6, long l7, long l8, long l9, long l10, long l11) {
            if (depth >= 2) {
                Continuation.yield(THE_SCOPE);
                if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                    WB.deoptimizeFrames(false /* makeNotEntrant */);
                }
                return l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11;
            }
            long res = ord102_yield_dontinline(depth + 1, l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11);
            if (res != l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11) {
                throw new Error();
            }
            return res;
        }
    }

    /**
     * Freeze/thaw compiled frame with a few stack arguments
     * icj is a call with i incoming stack parameters and j outgoing stack parameters.
     */
    public static class ContinuationCompiledFramesWithStackArgs_3c0 extends TestCaseBase {
        public int yieldCount;

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            yieldCount = 0;
            long result = ord101_testMethod_dontinline();
            assertEQ(result, 136L);
        }

        public long ord101_testMethod_dontinline() {
            long res = ord102_testMethod_dontinline(i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11);
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord108_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord108_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }

        public long ord102_testMethod_dontinline(long a1, long a2, long a3, long
                                                 a4, long a5, long a6, long a7,
                                                 long a8, long a9, long a10,
                                                 long a11) {
            long res = a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11
                       + i12 + i13 + i14 + i15 + i16;
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord109_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord109_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }
    }

    /**
     * Freeze/thaw compiled frame with a few stack arguments, incoming _and_ outgoing
     * icj is a call with i incoming stack parameters and j outgoing stack parameters.
     */
    public static class ContinuationCompiledFramesWithStackArgs_3c4 extends TestCaseBase {
        public int yieldCount;

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            yieldCount = 0;
            long result = ord101_testMethod_dontinline();
            assertEQ(result, 136L);
        }

        public long ord101_testMethod_dontinline() {
            long res = ord102_testMethod_dontinline(i1, i2, i3, i4, i5, i6, i7, i8, i9, i10, i11, i12, i13, i14);
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord108_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord108_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }

        public long ord102_testMethod_dontinline(long a1, long a2, long a3, long
                                                 a4, long a5, long a6, long a7,
                                                 long a8, long a9, long a10,
                                                 long a11, long a12, long a13,
                                                 long a14) {
            long res = ord103_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8,
                                                    a9, a10, a11, a12, a13, a14, i15);
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord109_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord109_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }

        public long ord103_testMethod_dontinline(long a1, long a2, long a3, long
                                                 a4, long a5, long a6, long a7,
                                                 long a8, long a9, long a10,
                                                 long a11, long a12, long a13,
                                                 long a14, long a15) {
            long res = a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11
                       + a12 + a13 + a14 + a15 + i16;
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord109_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord109_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }
    }

    /**
     * Freeze/thaw compiled frame with many stack arguments
     */
    public static class ContinuationCompiledFramesWithStackArgs extends TestCaseBase {
        public int yieldCount;

        @Override
        public void run() {
            log_dontjit("Continuation running on thread " + Thread.currentThread());
            yieldCount = 0;
            long result = ord101_testMethod_dontinline(i1);
            assertEQ(result, 136L);
        }

       public long ord101_testMethod_dontinline(long a1) {
           long res = ord102_testMethod_dontinline(a1, i2);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord102_testMethod_dontinline(long a1, long a2) {
           long res = ord103_testMethod_dontinline(a1, a2, i3);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord103_testMethod_dontinline(long a1, long a2, long a3) {
           long res = ord104_testMethod_dontinline(a1, a2, a3, i4);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord104_testMethod_dontinline(long a1, long a2, long a3, long a4) {
           long res = ord105_testMethod_dontinline(a1, a2, a3, a4, i5);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord105_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5) {
           long res = ord106_testMethod_dontinline(a1, a2, a3, a4, a5, i6);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord106_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6) {
           long res = ord107_testMethod_dontinline(a1, a2, a3, a4, a5, a6, i7);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

        public long ord107_testMethod_dontinline(long a1, long a2, long a3, long
                                                 a4, long a5, long a6, long a7) {
            long res = ord108_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, i8);
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord108_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord108_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }

        public long ord108_testMethod_dontinline(long a1, long a2, long a3, long
                                                 a4, long a5, long a6, long a7, long a8) {
            long res = ord109_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, i9);
            log_dontjit("Yield #" + yieldCount);
            log_dontjit("ord109_testMethod_dontinline res=" + res);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("a/y ord109_testMethod_dontinline res=" + res);
            log_dontjit("/Yield #" + yieldCount++);
            return res;
        }

        public long ord109_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                 long a7, long a8, long a9) {
            long res = ord110_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, i10);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            return res;
        }

       public long ord110_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10) {
           long res = ord111_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, i11);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord111_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10, long a11) {
           long res = ord112_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, i12);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord112_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10, long a11, long a12) {
           long res = ord113_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, i13);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord113_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10, long a11, long a12,
                                                long a13) {
           long res = ord114_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, i14);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord114_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10, long a11, long a12,
                                                long a13, long a14) {
           long res = ord115_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, i15);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

       public long ord115_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                long a7, long a8, long a9, long a10, long a11, long a12,
                                                long a13, long a14, long a15) {
           long res = ord116_testMethod_dontinline(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, i16);
           log_dontjit("Yield #" + yieldCount);
           Continuation.yield(THE_SCOPE);
           if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
               WB.deoptimizeFrames(false /* makeNotEntrant */);
           }
           log_dontjit("/Yield #" + yieldCount++);
           return res;
       }

        public long ord116_testMethod_dontinline(long a1, long a2, long a3, long a4, long a5, long a6,
                                                 long a7, long a8, long a9, long a10, long a11, long a12,
                                                 long a13, long a14, long a15, long a16) {
            long res = a2 + a4 + a6 + a8 + a10 + a12 + a14 + a16;
            log_dontjit("Yield #" + yieldCount);
            Continuation.yield(THE_SCOPE);
            if (deoptBehaviour == DeoptBehaviour.DEOPT_AFTER_YIELD) {
                WB.deoptimizeFrames(false /* makeNotEntrant */);
            }
            log_dontjit("/Yield #" + yieldCount++);
            res += a1 + a3 + a5 + a7 + a9 + a11 + a13 + a15;
            return res;
        }
    }
}
