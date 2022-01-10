/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

 /*
 * @test
 * @bug 8275908
 * @summary Record null_check traps for calls and array_check traps in the interpreter
 *
 * @requires vm.compiler2.enabled & vm.compMode != "Xcomp"
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-UseOnStackReplacement -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.exceptions.OptimizeImplicitExceptions::throwImplicitException
 *                   compiler.exceptions.OptimizeImplicitExceptions
 */

package compiler.exceptions;

import java.lang.reflect.Method;
import java.util.HashMap;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class OptimizeImplicitExceptions {
    // ImplicitException represents the various implicit (aka. 'built-in') exceptions
    // which can be thrown implicitely by the JVM when executing bytecodes.
    public enum ImplicitException {
        // NullPointerException during field access
        NULL_POINTER_EXCEPTION("null_check"),
        // NullPointerException during invoke
        INVOKE_NULL_POINTER_EXCEPTION("null_check"),
        ARITHMETIC_EXCEPTION("div0_check"),
        ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION("range_check"),
        ARRAY_STORE_EXCEPTION("array_check"),
        CLASS_CAST_EXCEPTION("class_check");
        private final String reason;
        ImplicitException(String reason) {
            this.reason = reason;
        }
        public String getReason() {
            return reason;
        }
    }
    // TestMode represents a specific combination of the OmitStackTraceInFastThrow command line options.
    // They will be set up in 'setFlags(TestMode testMode)' before a new test run starts.
    public enum TestMode {
        OMIT_STACKTRACES_IN_FASTTHROW,
        STACKTRACES_IN_FASTTHROW
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    // The number of deoptimizations after which a method will be made not-entrant
    private static final int PerBytecodeTrapLimit = WB.getIntxVMFlag("PerBytecodeTrapLimit").intValue();
    // The number of interpreter invocations after which a decompiled method will be re-compiled.
    private static final int Tier0InvokeNotifyFreq = (int)Math.pow(2, WB.getIntxVMFlag("Tier0InvokeNotifyFreqLog"));
    // The following variables are used to track the value of the global deopt counters between the various test phases.
    private static int oldDeoptCount = 0;
    private static HashMap<String, Integer> oldDeoptCountReason = new HashMap<String, Integer>(ImplicitException.values().length);
    // The following two objects are declared statically to simplify the test method.
    private static String[] string_a = new String[1];
    private static final Object o = new Object();

    // This is the main test method. It will repeatedly called with the same ImplicitException 'type' to
    // JIT-compile it, deoptimized it, re-compile it again and do various checks on the way.
    // This process will be repeated then for each kind of ImplicitException 'type'.
    public static Object throwImplicitException(ImplicitException type, Object[] object_a) {
        switch (type) {
            case NULL_POINTER_EXCEPTION: {
                return object_a.length;
            }
            case INVOKE_NULL_POINTER_EXCEPTION: {
                return object_a.hashCode();
            }
            case ARITHMETIC_EXCEPTION: {
                return ((42 / (object_a.length - 1)) > 2) ? null : object_a[0];
            }
            case ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION: {
                return object_a[5];
            }
            case ARRAY_STORE_EXCEPTION: {
                return (object_a[0] = o);
            }
            case CLASS_CAST_EXCEPTION: {
                return (ImplicitException[])object_a;
            }
        }
        return null;
    }

    // Completely unload (i.e. make "not-entrant"->"zombie"->"unload/free") a JIT-compiled
    // version of a method and clear the method's profiling counters.
    private static void unloadAndClean(Method m) {
        WB.deoptimizeMethod(m);  // Makes the nmethod "not entrant".
        WB.forceNMethodSweep();  // Makes all "not entrant" nmethods "zombie". This requires
        WB.forceNMethodSweep();  // two sweeps, see 'nmethod::can_convert_to_zombie()' for why.
        WB.forceNMethodSweep();  // Need third sweep to actually unload/free all "zombie" nmethods.
        System.gc();
        WB.clearMethodState(m);
    }

    // Set '-XX' flags according to 'TestMode'
    private static void setFlags(TestMode testMode) {
        if (testMode == TestMode.OMIT_STACKTRACES_IN_FASTTHROW) {
            WB.setBooleanVMFlag("OmitStackTraceInFastThrow", true);
        } else {
            WB.setBooleanVMFlag("OmitStackTraceInFastThrow", false);
        }

        System.out.println("==========================================================");
        System.out.println("testMode=" + testMode +
                           " OmitStackTraceInFastThrow=" + WB.getBooleanVMFlag("OmitStackTraceInFastThrow"));
        System.out.println("==========================================================");
    }

    private static void printCounters(TestMode testMode, ImplicitException impExcp, Method throwImplicitException_m, int invocations) {
        System.out.println("testMode=" + testMode + " exception=" + impExcp + " invocations=" + invocations + "\n" +
                           "decompilecount=" + WB.getMethodDecompileCount(throwImplicitException_m) + " " +
                           "trapCount=" + WB.getMethodTrapCount(throwImplicitException_m) + " " +
                           "trapCount(" + impExcp.getReason() + ")=" +
                           WB.getMethodTrapCount(throwImplicitException_m, impExcp.getReason()) + " " +
                           "globalDeoptCount=" + WB.getDeoptCount() + " " +
                           "globalDeoptCount(" + impExcp.getReason() + ")=" + WB.getDeoptCount(impExcp.getReason(), null));
        System.out.println("method compiled=" + WB.isMethodCompiled(throwImplicitException_m));
    }

    // Checks after the test method has been JIT-compiled but before the compiled version has been invoked.
    private static void checkSimple(TestMode testMode, ImplicitException impExcp, Exception ex, Method throwImplicitException_m, int invocations) {

        printCounters(testMode, impExcp, throwImplicitException_m, invocations);
        // At this point, throwImplicitException() has been compiled but the compiled version has not been invoked yet.
        Asserts.assertEQ(WB.getMethodCompilationLevel(throwImplicitException_m), 4, "Method should be compiled at level 4.");

        int trapCount = WB.getMethodTrapCount(throwImplicitException_m);
        int trapCountSpecific = WB.getMethodTrapCount(throwImplicitException_m, impExcp.getReason());
        Asserts.assertEQ(trapCount, invocations, "Trap count must much invocation count.");
        Asserts.assertEQ(trapCountSpecific, invocations, "Trap count must much invocation count.");
        Asserts.assertNotNull(ex.getMessage(), "Exceptions thrown in the interpreter should have a message.");
    }

    // Checks after the JIT-compiled test method has been invoked 'invocations' times.
    private static void check(TestMode testMode, ImplicitException impExcp, Exception ex,
                              Method throwImplicitException_m, int invocations, int totalInvocations) {

        printCounters(testMode, impExcp, throwImplicitException_m, totalInvocations);
        // At this point, the compiled version of 'throwImplicitException()' has been invoked 'invocations' times.
        Asserts.assertEQ(WB.getMethodCompilationLevel(throwImplicitException_m), 4, "Method should be compiled at level 4.");
        int deoptCount = WB.getDeoptCount();
        int deoptCountReason = WB.getDeoptCount(impExcp.getReason(), null/*action*/);
        if (testMode == TestMode.OMIT_STACKTRACES_IN_FASTTHROW) {
            // No deoptimizations for '-XX:+OmitStackTraceInFastThrow'
            Asserts.assertEQ(oldDeoptCount, deoptCount, "Wrong number of deoptimizations.");
            Asserts.assertEQ(oldDeoptCountReason.get(impExcp.getReason()), deoptCountReason, "Wrong number of deoptimizations.");
            // '-XX:+OmitStackTraceInFastThrow' never has message because it is using a global singleton exception.
            Asserts.assertNull(ex.getMessage(), "Optimized exceptions have no message.");
        } else if (testMode == TestMode.STACKTRACES_IN_FASTTHROW) {
            // We always deoptimize for '-XX:-OmitStackTraceInFastThrow
            Asserts.assertEQ(oldDeoptCount + invocations, deoptCount, "Wrong number of deoptimizations.");
            Asserts.assertEQ(oldDeoptCountReason.get(impExcp.getReason()) + invocations, deoptCountReason, "Wrong number of deoptimizations.");
            Asserts.assertNotNull(ex.getMessage(), "Exceptions thrown in the interpreter should have a message.");
        } else {
            Asserts.fail("Unknown test mode.");
        }
        oldDeoptCount = deoptCount;
        oldDeoptCountReason.put(impExcp.getReason(), deoptCountReason);
    }

    public static void main(String[] args) throws Exception {

        if (!WB.getBooleanVMFlag("ProfileTraps")) {
            // The fast-throw optimzation only works if we're running with -XX:+ProfileTraps
            return;
        }

        // Initialize global deopt counts to zero.
        for (ImplicitException impExcp : ImplicitException.values()) {
            oldDeoptCountReason.put(impExcp.getReason(), 0);
        }
        // Get a handle of the test method for usage with the WhiteBox API.
        Method throwImplicitException_m = OptimizeImplicitExceptions.class
            .getDeclaredMethod("throwImplicitException", new Class[] { ImplicitException.class, Object[].class});

        for (TestMode testMode : TestMode.values()) {
            setFlags(testMode);
            for (ImplicitException impExcp : ImplicitException.values()) {
                int invocations = 0;
                Exception lastException = null;

                // Warmup and compile, but don't invoke compiled code.
                while(!WB.isMethodCompiled(throwImplicitException_m)) {
                    invocations++;
                    try {
                        throwImplicitException(impExcp, impExcp.getReason().equals("null_check") ? null : string_a);
                    } catch (Exception catchedExcp) {
                        lastException = catchedExcp;
                        continue;
                    }
                    throw new Exception("Should not happen");
                }

                checkSimple(testMode, impExcp, lastException, throwImplicitException_m, invocations);

                // Invoke compiled code 'PerBytecodeTrapLimit' times.
                for (int i = 0; i < PerBytecodeTrapLimit; i++) {
                    invocations++;
                    try {
                        throwImplicitException(impExcp, impExcp.getReason().equals("null_check") ? null : string_a);
                    } catch (Exception catchedExcp) {
                        lastException = catchedExcp;
                        continue;
                    }
                    throw new Exception("Should not happen");
                }

                check(testMode, impExcp, lastException, throwImplicitException_m, PerBytecodeTrapLimit, invocations);

                // Invoke compiled code 'Tier0InvokeNotifyFreq' times.
                // If the method was de-compiled before, this will re-compile it again.
                for (int i = 0; i < Tier0InvokeNotifyFreq; i++) {
                    invocations++;
                    try {
                        throwImplicitException(impExcp, impExcp.getReason().equals("null_check") ? null : string_a);
                    } catch (Exception catchedExcp) {
                        lastException = catchedExcp;
                        continue;
                    }
                    throw new Exception("Should not happen");
                }

                check(testMode, impExcp, lastException, throwImplicitException_m, Tier0InvokeNotifyFreq, invocations);

                // Invoke compiled code 'PerBytecodeTrapLimit' times.
                for (int i = 0; i < PerBytecodeTrapLimit; i++) {
                    invocations++;
                    try {
                        throwImplicitException(impExcp, impExcp.getReason().equals("null_check") ? null : string_a);
                    } catch (Exception catchedExcp) {
                        lastException = catchedExcp;
                        continue;
                    }
                    throw new Exception("Should not happen");
                }

                check(testMode, impExcp, lastException, throwImplicitException_m, PerBytecodeTrapLimit, invocations);

                System.out.println("------------------------------------------------------------------");

                unloadAndClean(throwImplicitException_m);
            }
        }
    }
}
