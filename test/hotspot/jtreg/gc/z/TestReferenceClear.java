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

package gc.z;

/*
 * @test
 * @bug 8394685
 * @summary C2 Reference.clear must not keep ZGC referents alive
 * @requires vm.gc.Z & vm.compiler2.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseZGC -Xms128m -Xmx128m -XX:-TieredCompilation -Xbatch
 *      -XX:CompileCommand=dontinline,gc.z.TestReferenceClear::clear*
 *      -XX:CompileCommand=inline,java.lang.ref.Reference::*
 *      -XX:CompileCommand=inline,java.lang.ref.PhantomReference::*
 *      gc.z.TestReferenceClear
 */

import java.lang.ref.PhantomReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class TestReferenceClear {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static Object weakReferent = new Object();
    private static Object phantomReferent = new Object();
    private static final WeakReference<Object> weak = new WeakReference<>(weakReferent);
    private static final PhantomReference<Object> phantom = new PhantomReference<>(phantomReferent, null);
    // The observers detect whether clear() keeps either referent alive.
    private static final WeakReference<Object> weakObserver = new WeakReference<>(weakReferent);
    private static final WeakReference<Object> phantomObserver = new WeakReference<>(phantomReferent);

    // Fixed receivers let C2 resolve clear() without receiver profiling.
    private static void clearWeak() {
        weak.clear();
    }

    private static void clearPhantom() {
        phantom.clear();
    }

    private static Method compile(String name) throws Exception {
        Method method = TestReferenceClear.class.getDeclaredMethod(name);
        Asserts.assertTrue(WB.enqueueMethodForCompilation(method, 4) &&
                           WB.getMethodCompilationLevel(method) == 4,
                           "Not C2 compiled: " + method);
        return method;
    }

    public static void main(String[] args) throws Exception {
        Method weakMethod = compile("clearWeak");
        Method phantomMethod = compile("clearPhantom");

        // The old-mark store barrier requires old-generation test objects.
        WB.fullGC();
        if (!WB.isObjectInOldGen(weakReferent) || !WB.isObjectInOldGen(phantomReferent) ||
            !WB.isObjectInOldGen(weak) || !WB.isObjectInOldGen(phantom) ||
            !WB.isObjectInOldGen(weakObserver) || !WB.isObjectInOldGen(phantomObserver)) {
            throw new RuntimeException("Objects were not promoted");
        }

        WB.concurrentGCAcquireControl();
        try {
            weakReferent = null;
            phantomReferent = null;
            // Clear before reference discovery, while the old marking barrier is active.
            WB.concurrentGCRunTo(WB.AFTER_MARKING_STARTED);
            clearWeak();
            clearPhantom();
            Asserts.assertEQ(WB.getMethodCompilationLevel(weakMethod), 4, "Weak clear not C2 compiled");
            Asserts.assertEQ(WB.getMethodCompilationLevel(phantomMethod), 4, "Phantom clear not C2 compiled");
            WB.concurrentGCRunToIdle();

            Asserts.assertTrue(weak.refersTo(null), "Weak reference not cleared");
            Asserts.assertTrue(phantom.refersTo(null), "Phantom reference not cleared");
            Asserts.assertTrue(weakObserver.refersTo(null), "Weak referent kept alive");
            Asserts.assertTrue(phantomObserver.refersTo(null), "Phantom referent kept alive");
        } finally {
            WB.concurrentGCReleaseControl();
        }
    }
}
