/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.parsing;

import jdk.internal.misc.Unsafe;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8386503
 * @summary Test load folding from a field store with a less precise type
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   ${test.main.class}
 */
public class TestTypeUnsafeFieldStore {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static volatile Throwable failure;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i <= 10; i++) {
            testConcurrentClassLoading(i);
        }
        Holder h = new Holder();
        Integer obj = 0;
        for (int i = 0; i < 20000; i++) {
            testUnsafeAccess(h, obj);
        }
    }

    // It's hard to coordinate the compiler thread with the thread that load the child class, so we
    // randomly delay one of the threads
    private static void testConcurrentClassLoading(int idx) throws Exception {
        var parentClass = Class.forName("compiler.parsing.TestTypeUnsafeFieldStore$P" + idx);
        var testMethod = TestTypeUnsafeFieldStore.class.getDeclaredMethod("testMethod" + idx, parentClass);
        Thread compiler = new Thread(() -> {
            try {
                if (idx < 5) {
                    Thread.sleep((5 - idx) * 10L);
                }
                WHITE_BOX.markMethodProfiled(testMethod);
                if (!WHITE_BOX.enqueueMethodForCompilation(testMethod, 4)) {
                    throw new RuntimeException("Could not enqueue the test method for C2 compilation");
                }
                while (WHITE_BOX.isMethodQueuedForCompilation(testMethod)) {
                    Thread.yield();
                }
            } catch (Throwable t) {
                failure = t;
            }
        });
        compiler.start();
        if (idx > 5) {
            Thread.sleep((idx - 5) * 10L);
        }
        Class.forName("compiler.parsing.TestTypeUnsafeFieldStore$C" + idx);
        compiler.join();
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }

    private static Integer testUnsafeAccess(Holder h, Object obj) {
        UNSAFE.putReference(h, Holder.V_OFFSET, obj);
        return h.v;
    }

    private static class Holder {
        private static final long V_OFFSET = UNSAFE.objectFieldOffset(Holder.class, "v");
        Integer v;
    }

    // When the compiler parses the store, C has not been loaded, so obj is of type P. However,
    // when the compiler parses the load, C has been loaded and is observed to be the unique
    // concrete subclass of P, so the result of the load is of type C. Folding the load to obj will
    // drop this information, thus is incorrect.
    private static abstract class P0 {}
    private static class C0 extends P0 {}
    private static P0 staticField0;
    private static P0 testMethod0(P0 obj) {
        staticField0 = obj;
        inline0();
        return staticField0;
    }

    private static abstract class P1 {}
    private static class C1 extends P1 {}
    private static P1 staticField1;
    private static P1 testMethod1(P1 obj) {
        staticField1 = obj;
        inline0();
        return staticField1;
    }

    private static abstract class P2 {}
    private static class C2 extends P2 {}
    private static P2 staticField2;
    private static P2 testMethod2(P2 obj) {
        staticField2 = obj;
        inline0();
        return staticField2;
    }

    private static abstract class P3 {}
    private static class C3 extends P3 {}
    private static P3 staticField3;
    private static P3 testMethod3(P3 obj) {
        staticField3 = obj;
        inline0();
        return staticField3;
    }

    private static abstract class P4 {}
    private static class C4 extends P4 {}
    private static P4 staticField4;
    private static P4 testMethod4(P4 obj) {
        staticField4 = obj;
        inline0();
        return staticField4;
    }

    private static abstract class P5 {}
    private static class C5 extends P5 {}
    private static P5 staticField5;
    private static P5 testMethod5(P5 obj) {
        staticField5 = obj;
        inline0();
        return staticField5;
    }

    private static abstract class P6 {}
    private static class C6 extends P6 {}
    private static P6 staticField6;
    private static P6 testMethod6(P6 obj) {
        staticField6 = obj;
        inline0();
        return staticField6;
    }

    private static abstract class P7 {}
    private static class C7 extends P7 {}
    private static P7 staticField7;
    private static P7 testMethod7(P7 obj) {
        staticField7 = obj;
        inline0();
        return staticField7;
    }

    private static abstract class P8 {}
    private static class C8 extends P8 {}
    private static P8 staticField8;
    private static P8 testMethod8(P8 obj) {
        staticField8 = obj;
        inline0();
        return staticField8;
    }

    private static abstract class P9 {}
    private static class C9 extends P9 {}
    private static P9 staticField9;
    private static P9 testMethod9(P9 obj) {
        staticField9 = obj;
        inline0();
        return staticField9;
    }

    private static abstract class P10 {}
    private static class C10 extends P10 {}
    private static P10 staticField10;
    private static P10 testMethod10(P10 obj) {
        staticField10 = obj;
        inline0();
        return staticField10;
    }

    private static void inline0() {
        inline1();
        inline1();
        inline1();
        inline1();
    }

    private static void inline1() {
        inline2();
        inline2();
        inline2();
        inline2();
    }

    private static void inline2() {
        inline3();
        inline3();
        inline3();
        inline3();
    }

    private static void inline3() {
        inline4();
        inline4();
        inline4();
        inline4();
    }

    private static void inline4() {
        inline5();
        inline5();
        inline5();
        inline5();
    }

    private static void inline5() {}
}
