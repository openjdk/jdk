/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
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

/*
 * @test
 * @bug 8296440
 * @summary Test if the holder object of the target of compiled static or
 *          optimized virtual call is reachable from the caller nmethod. This
 *          prevents unloading of the target if the caller is not
 *          unloading. This is of course necessary if the call is still
 *          reachable but also if it is not reachable we want to avoid dangling
 *          Method* in the static stub.
 *
 * @library /test/lib /
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -Xbatch
 *                   -XX:CompileCommand=dontinline,*::*dontinline*
 *                   compiler.dependencies.staticallyBound.TestStaticallyBoundTargetIsReachable
 */

package compiler.dependencies.staticallyBound;

import jdk.test.lib.classloader.DirectLeveledClassLoader;

public class TestStaticallyBoundTargetIsReachable {
    public static void main(String[] args) {
        try {
            new InvokeInterfaceConstReceiver().runTest(args);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // Testcase where an invokeinterface is compiled to a static call because
    // the receiver object is constant.
    // The holder of the target is reachable following the receiver in the oop constants.
    //
    // - invokeinterface with constant receiver in ClassA_LVL_1::testMethod_dojit
    // - ClassC_LVL_3 overrides the target method. This prevents CHA based optimization.
    //   With CHA based optimization the target holder would be reachable following the
    //   dependencies.
    // - The receiver class ClassB_LVL_3 is loaded by a child classloader.
    //
    public static class InvokeInterfaceConstReceiver {

        // Callee loaded by classloader L3 which is a child of L1
        public static class ClassB_LVL_3 implements RecvInterface {
            @Override
            public int testMethod_statically_bound_callee_dontinline_dojit() {
                return 2;
            }
        }

        // Override target method to prevent CHA based optimization.
        public static class ClassC_LVL_3 extends ClassB_LVL_3 {
            @Override
            public int testMethod_statically_bound_callee_dontinline_dojit() {
                throw new Error("Should not reach here");
            }
        }

        public static interface RecvInterface {
            public int testMethod_statically_bound_callee_dontinline_dojit();
        }

        // Decouple Caller/Callee
        public static class Factory_LVL_2 {
            public static RecvInterface getReceiver() {
                new ClassC_LVL_3();              // load class that overrides target method
                return new ClassB_LVL_3();
            }
        }

        // Caller loaded by L1. The target is loaded by L3 which is a child of L1.
        public static class ClassA_LVL_1 implements Runnable {
            static final RecvInterface constReceiver = Factory_LVL_2.getReceiver();

            public void testMethod_dojit() {
                // the receiver for the invokeinterface is a constant
                constReceiver.testMethod_statically_bound_callee_dontinline_dojit();
            }

            @Override
            public void run() {
                testMethod_dojit();
            }
        }

        public void runTest(String[] args) throws Throwable {
            ClassLoader thisLoader = getClass().getClassLoader();
            System.err.println("CL: " + thisLoader);
            ClassLoader ldl = new DirectLeveledClassLoader(thisLoader, 3);
            Class<?> cls = ldl.loadClass(getClass().getName() + "$ClassA_LVL_1");
            Runnable test = (Runnable) cls.getDeclaredConstructor().newInstance();

            for (int i=0; i<30_000; i++) {
                test.run();
            }
        }
    }
}
