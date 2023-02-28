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
 * @test
 * @bug 8292278
 * @summary Basic tests where jit compilation of test methods is controlled with a compilation policy
 * @requires vm.continuations
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm   --enable-preview
 *                     -XX:+UnlockDiagnosticVMOptions   -XX:+WhiteBoxAPI
 *                     -Xbootclasspath/a:.
 *                     -Xbatch
 *                     -XX:-TieredCompilation
 *                     -XX:CompileCommand=dontinline,*::*dontinline*
 *                     -XX:CompileCommand=dontinline,*::*dontjit*
 *                     -XX:CompileCommand=exclude,*::*dontjit*
 *                     -XX:CompileCommand=dontinline,java/lang/String*.*
 *                     MovingCompWindow
 */

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Pattern;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;
import static jdk.test.lib.Asserts.*;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;

public class MovingCompWindow {
    static final ContinuationScope THE_SCOPE = new ContinuationScope() {};

    public static final Pattern COMP_NONE  = Pattern.compile("COMP_NONE");
    public static final Pattern COMP_ALL   = Pattern.compile("COMP_ALL");
    public static final Pattern CONT_METHS = Pattern.compile("^(enter|enter0|yield|yield0)$");

    public static boolean callSystemGC;
    public static int compLevel;

    public static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        // Run tests with C2 compilations
        compLevel = CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;

        // Run tests, call System.GC().
        // GC forces allocation of new StackChunk because the existing will get FLAG_GC_MODE set.
        callSystemGC = true;
        runTests();
    }

    public static void runTests() {
        System.out.println("$$$0 Running test cases with the following settings:");
        System.out.println("compLevel=" + compLevel);
        System.out.println("callSystemGC=" + callSystemGC);
        System.out.println();

        runTests(new CompilationPolicy(7 /*warmup*/, 1 /* length comp. window */));
    }

    public static void runTests(CompilationPolicy compPolicy) {
        System.out.println("$$$1 Running test cases with the following policy:");
        compPolicy.print(); System.out.println();

        new ContinuationRunYieldRunTest().runTestCase(3, compPolicy);
    }

    // Control which frames are compiled/interpreted when calling Continuation.yield()
    // With COMP_WINDOW the methods in the window are supposed to be compiled and others
    // are interpreted. With DEOPT_WINDOW vice versa.
    // The methods that are subject to the CompilationPolicy are set with setMethods().
    // Their order has to correspond to the stack order when calling yield().
    public static class CompilationPolicy {
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

        public CompilationPolicy(int warmupIterations, Pattern methodPattern, Pattern contMethPattern) {
            this(warmupIterations, 0, methodPattern, contMethPattern, CompWindowMode.NO_COMP_WINDOW);
        }

        public CompilationPolicy(int warmupIterations, int windowLength, Pattern methodPattern, Pattern contMethPattern) {
            this(warmupIterations, windowLength, methodPattern, contMethPattern, CompWindowMode.COMP_WINDOW);
        }

        public CompilationPolicy(int warmupIterations, int windowLength, Pattern methodPattern, Pattern contMethPattern,
                                 CompWindowMode startMode) {
            this.warmupIterations = warmupIterations;
            this.methodPattern = methodPattern;
            this.contMethPattern = contMethPattern;
            this.winPos = 0;
            this.winLen = windowLength;
            this.compWindowMode = startMode;
        }

        public CompilationPolicy(int warmupIterations, int windowLength) {
            this(warmupIterations, windowLength, COMP_ALL, CONT_METHS);
        }

        public int warmupIterations() {
            return this.warmupIterations;
        }

        public void compileMethods() {
            log("Compilation window mode: " + compWindowMode + " winPos=" + winPos + " winLen=" + winLen);
            for (int i = 0; i < methods.length; i++) {
                Method meth = methods[i];
                boolean inWindow = i >= winPos && i < (winPos+winLen);
                boolean shouldBeCompiled = compWindowMode == CompWindowMode.NO_COMP_WINDOW
                    || (inWindow && compWindowMode == CompWindowMode.COMP_WINDOW)
                    || (!inWindow && compWindowMode == CompWindowMode.DEOPT_WINDOW);
                boolean isCompiled = WB.isMethodCompiled(meth);
                log("methods["+i+"] inWindow="+inWindow + " isCompiled="+isCompiled+" shouldBeCompiled="+shouldBeCompiled+" method=`"+meth+"`");
                if (isCompiled != shouldBeCompiled) {
                    if (shouldBeCompiled) {
                        log("           Compiling methods["+i+"]");
                        enqForCompilation(meth);
                        assertTrue(WB.isMethodCompiled(meth), "Run with -Xbatch");
                    } else {
                        assertFalse(WB.isMethodQueuedForCompilation(meth), "Run with -Xbatch");
                        log("           Deoptimizing methods["+i+"]");
                        WB.deoptimizeMethod(meth);
                    }
                }
            }
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
            if(compWindowMode == CompWindowMode.NO_COMP_WINDOW) return false;
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
            log_dontjit(">>>> Executing test case " + getClass().getName() + " (yieldCalls=" + yieldCalls + ")");
            init(compPolicy);
            try {
                log_dontjit("Warm-up test case");
                for(warmUpCount = 1; warmUpCount <= compPolicy.warmupIterations(); warmUpCount++) {
                    testEntry_dontinline();
                }
                warmUpCount = 0;
                log_dontjit("Warm-up test case DONE");

                do {
                    log_dontjit("@@ Compiling test methods according to compilation policy");
                    compPolicy.compileMethods();

                    log_dontjit("Running test case (Reresolve Call Sites)");
                    testEntry_dontinline();
                    log_dontjit("Running test case DONE  (Reresolve Call Sites)");

                    log_dontjit("Running test case");
                    testEntry_dontinline();
                    log_dontjit("Running test case DONE");
                } while(compPolicy.shiftWindow());
            } finally {
                log_dontjit("<<<< Finished test case " + getClass().getName()); log_dontjit();
            }
        }

        public void init(CompilationPolicy compPolicy) {
            this.compPolicy = compPolicy;
            ArrayList<Method> selectedMethods = new ArrayList<Method>();
            Pattern p = compPolicy.methodPattern;
            if (p != COMP_NONE) {
                Class<? extends TestCaseBase> c = getClass();
                Method methods[] = c.getDeclaredMethods();
                for (Method meth : methods) {
                    if (p == COMP_ALL || p.matcher(meth.getName()).matches()) {
                        if (!meth.getName().contains("dontjit")) {
                            selectedMethods.add(meth);
                        }
                    }
                }
            }

            p = compPolicy.contMethPattern;
            if (compPolicy.contMethPattern != COMP_NONE) {
                Class<?> c = Continuation.class;
                Method methods[] = c .getDeclaredMethods();
                for (Method meth : methods) {
                    if (p.matcher(meth.getName()).matches()) {
                        selectedMethods.add(meth);
                    }
                }
            }
            // Sort in caller/callee order by the "ord" method name prefix
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
                            p1 = Integer.parseInt(n1.substring(i+3, i+6));
                        }
                        i = n2.indexOf("ord");
                        if (i >= 0) {
                            p2 = Integer.parseInt(n2.substring(i+3, i+6));
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
                cont.run();
                if (callSystemGC) System.gc();
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
                String s2 = s1+"str2";
                sField = s2;
            }
        }
    }
}
