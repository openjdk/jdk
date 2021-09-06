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
 * @bug 9999999
 * @summary Test -XX:+/-OmitStackTraceInFastThrow and -XX:+/-StackFrameInFastThrow
 *
 * @requires vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UseSerialGC -Xbatch -XX:-UseOnStackReplacement -XX:-TieredCompilation
 *                   -XX:CompileCommand=inline,compiler.exceptions.StackFrameInFastThrow::throwImplicitException
 *                   -XX:CompileCommand=inline,compiler.exceptions.StackFrameInFastThrow::level2
 *                   -XX:PerMethodTrapLimit=0 compiler.exceptions.StackFrameInFastThrow
 */

package compiler.exceptions;

import java.lang.reflect.Method;
import java.util.HashMap;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class StackFrameInFastThrow {
    public enum ImplicitException {
        NULL_POINTER_EXCEPTION,
        ARITHMETIC_EXCEPTION,
        ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION,
        ARRAY_STORE_EXCEPTION,
        CLASS_CAST_EXCEPTION
    }
    public enum CompMode {
        INTERPRETED,
        C2,
        C2_RECOMPILED
    }
    public enum TestMode {
        STACKTRACES_IN_FASTTHROW,
        OMIT_STACKTRACES_IN_FASTTHROW,
        OMIT_STACKTRACES_IN_FASTTHROW_WITH_STACKFRAME
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static String[] string_a = new String[1];
    private static boolean DEBUG = Boolean.getBoolean("DEBUG");

    public static Object throwImplicitException(ImplicitException type, Object[] object_a) {
        switch (type) {
            case NULL_POINTER_EXCEPTION: {
                return object_a.length;
            }
            case ARITHMETIC_EXCEPTION: {
                return ((42 / (object_a.length - 1)) > 2) ? null : object_a[0];
            }
            case ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION: {
                return object_a[5];
            }
            case ARRAY_STORE_EXCEPTION: {
                return (object_a[0] = new Object());
            }
            case CLASS_CAST_EXCEPTION: {
                return (ImplicitException[])object_a;
            }
        }
        return null;
    }

    public static Object level2(ImplicitException type, Object[] object_a) {
        return throwImplicitException(type, object_a);
    }

    public static Object level1(ImplicitException type, Object[] object_a) {
        return level2(type, object_a);
    }

    private static void compile(Method m) {
        Asserts.assertFalse(WB.isMethodCompiled(m), "Method shouldn't be compiled.");
        WB.enqueueMethodForCompilation(m, 4);
        Asserts.assertEQ(WB.getMethodCompilationLevel(m), 4, "Method should be compiled at level 4.");
    }

    private static void unload(Method m) {
        Asserts.assertEQ(WB.getMethodCompilationLevel(m), 4, "Method should be compiled at level 4.");
        if (DEBUG) System.console().readLine();
        WB.deoptimizeMethod(m);  // Makes the nmethod "not entrant".
        WB.forceNMethodSweep();  // Makes all "not entrant" nmethods "zombie". This requires
        WB.forceNMethodSweep();  // two sweeps, see 'nmethod::can_convert_to_zombie()' for why.
        WB.forceNMethodSweep();  // Need third sweep to actually unload/free all "zombie" nmethods.
        if (DEBUG) System.console().readLine();
        System.gc();
        if (DEBUG) System.console().readLine();
    }

    private static void recompile(Method m) {
        unload(m);
        compile(m);
    }

    private static boolean exceptionsEqual(Exception e1, Exception e2) {
        if (e1.getClass() == e2.getClass()) {
            if (e1.getMessage() == e2.getMessage() || e1.getMessage().equals(e2.getMessage())) {
                StackTraceElement[] ste1 = e1.getStackTrace();
                StackTraceElement[] ste2 = e2.getStackTrace();
                if (ste1.length == ste2.length) {
                    for (int i = 0; i < ste1.length; i++) {
                        if (!ste1[i].equals(ste2[i])) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static HashMap<ImplicitException, Exception> lastException = new HashMap<>();

    private static void checkResult(ImplicitException implExcp, Exception catchedExcp, CompMode compMode, TestMode testMode) {
        catchedExcp.printStackTrace(System.out);

        if (compMode == CompMode.INTERPRETED) {
            // Exception thrown by the interpreter should have the full stack trace
            Asserts.assertTrue(catchedExcp.getStackTrace()[3].getMethodName().equals("main"),
                               "Can't see main() in interpreter stack trace");
            lastException.put(implExcp, catchedExcp);
        }
        if (compMode == CompMode.C2 || compMode == CompMode.C2_RECOMPILED) {
            switch (testMode) {
                case STACKTRACES_IN_FASTTHROW : {
                    Asserts.assertTrue(catchedExcp.getStackTrace()[3].getMethodName().equals("main"),
                                       "-XX:-OmitStackTraceInFastThrow should generate full stack trace");
                    Asserts.assertTrue(exceptionsEqual(lastException.get(implExcp), catchedExcp),
                                       "With -XX:-OmitStackTraceInFastThrow interpreter and C2 generated exceptions should be equal");
                    break;
                }
                case OMIT_STACKTRACES_IN_FASTTHROW : {
                    Asserts.assertEQ(catchedExcp.getStackTrace().length, 0,
                                     "-XX:+OmitStackTraceInFastThrow should generate an emtpy stack trace");
                    if (compMode == CompMode.C2_RECOMPILED) {
                        Asserts.assertEQ(lastException.get(implExcp), catchedExcp,
                                         "With -XX:+OmitStackTraceInFastThrow all exceptions should be the same singleton instance");
                    }
                    break;
                }
                case OMIT_STACKTRACES_IN_FASTTHROW_WITH_STACKFRAME : {
                    Asserts.assertTrue(catchedExcp.getStackTrace()[2].getMethodName().equals("level1"),
                                       "-XX:+OmitStackTraceInFastThrow -XX:+StackFrameInFastThrow should generate a minimal stack trace");
                    if (compMode == CompMode.C2_RECOMPILED) {
                        Asserts.assertTrue(exceptionsEqual(lastException.get(implExcp), catchedExcp),
                                                           "With -XX:+OmitStackTraceInFastThrow -XX:+StackFrameInFastThrow C2 generated exceptions should be equal");
                        Asserts.assertNE(lastException.get(implExcp), catchedExcp,
                                "With -XX:+OmitStackTraceInFastThrow -XX:+StackFrameInFastThrow new exceptions should be generated for every nmethod");
                    }
                    break;
                }
            }
            if (compMode == CompMode.C2_RECOMPILED) {
                lastException.put(implExcp, null);
            }
            else {
                lastException.put(implExcp, catchedExcp);
            }
        }
    }

    private static void setFlags(TestMode testMode) {
        if (testMode == TestMode.STACKTRACES_IN_FASTTHROW) {
            WB.setBooleanVMFlag("OmitStackTraceInFastThrow", false);
        }
        else {
            WB.setBooleanVMFlag("OmitStackTraceInFastThrow", true);
            WB.setBooleanVMFlag("StackFrameInFastThrow", false);
        }
        if (testMode == TestMode.OMIT_STACKTRACES_IN_FASTTHROW_WITH_STACKFRAME) {
            WB.setBooleanVMFlag("StackFrameInFastThrow", true);
        }
        System.out.println("==========================================================");
        System.out.println("testMode=" + testMode +
                           " OmitStackTraceInFastThrow=" + WB.getBooleanVMFlag("OmitStackTraceInFastThrow") +
                           " StackFrameInFastThrow=" + WB.getBooleanVMFlag("StackFrameInFastThrow"));
        System.out.println("==========================================================");
    }

    private static void printCompMode(CompMode compMode) {
        System.out.println("----------------------------------------------------------");
        System.out.println("compMode=" + compMode);
        System.out.println("----------------------------------------------------------");

    }

    public static void main(String[] args) throws Exception {

        if (!WB.getBooleanVMFlag("ProfileTraps")) {
            // The fast-throw optimzation only works if we're running with -XX:+ProfileTraps
            return;
        }

        Method level1_m = StackFrameInFastThrow.class.getDeclaredMethod("level1", new Class[] { ImplicitException.class, Object[].class});

        for (TestMode testMode : TestMode.values()) {
            setFlags(testMode);
            for (CompMode compMode : CompMode.values()) {
                printCompMode(compMode);
                for (ImplicitException impExcp : ImplicitException.values()) {
                    try {
                        level1(impExcp, impExcp == ImplicitException.NULL_POINTER_EXCEPTION ? null : string_a);
                    } catch (Exception catchedExcp) {
                        checkResult(impExcp, catchedExcp, compMode, testMode);
                        continue;
                    }
                    throw new Exception("Should not happen");
                }
                if (compMode == CompMode.INTERPRETED) {
                    compile(level1_m);
                }
                if (compMode == CompMode.C2) {
                    recompile(level1_m);
                }
            }
            unload(level1_m);
        }
    }
}
