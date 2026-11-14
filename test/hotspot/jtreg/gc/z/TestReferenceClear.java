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
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

public class TestReferenceClear {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Reference<?>[] REFERENCES = new Reference<?>[4];
    private static final WeakReference<?>[] OBSERVERS = new WeakReference<?>[4];
    private static Object[] referents;

    private static void clearWeak(WeakReference<?> reference) {
        reference.clear();
    }

    private static void clearPhantom(PhantomReference<?> reference) {
        reference.clear();
    }

    private static void checkCompiled(Method method) {
        if (WB.getMethodCompilationLevel(method) != 4) {
            throw new RuntimeException("Not C2 compiled: " + method);
        }
    }

    private static void prepare() {
        referents = new Object[4];
        for (int i = 0; i < referents.length; i++) {
            Object object = new Object();
            referents[i] = object;
            REFERENCES[i] = i % 2 == 0 ? new WeakReference<>(object)
                                     : new PhantomReference<>(object, null);
            OBSERVERS[i] = new WeakReference<>(object);
        }
        WB.fullGC();
        for (int i = 0; i < referents.length; i++) {
            if (!WB.isObjectInOldGen(referents[i]) ||
                !WB.isObjectInOldGen(REFERENCES[i]) ||
                !WB.isObjectInOldGen(OBSERVERS[i])) {
                throw new RuntimeException("Objects were not promoted: " + i);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Exercise both the non-null and already-cleared branches before compiling.
        for (int i = 0; i < 20_000; i++) {
            Object object = new Object();
            WeakReference<Object> weak = new WeakReference<>(object);
            PhantomReference<Object> phantom = new PhantomReference<>(object, null);
            clearWeak(weak);
            clearWeak(weak);
            clearPhantom(phantom);
            clearPhantom(phantom);
            Reference.reachabilityFence(object);
        }
        Method weak = TestReferenceClear.class.getDeclaredMethod("clearWeak", WeakReference.class);
        Method phantom = TestReferenceClear.class.getDeclaredMethod("clearPhantom", PhantomReference.class);
        if (!WB.enqueueMethodForCompilation(weak, 4) || !WB.enqueueMethodForCompilation(phantom, 4)) {
            throw new RuntimeException("Could not request C2 compilation");
        }
        checkCompiled(weak);
        checkCompiled(phantom);

        prepare();
        WB.concurrentGCAcquireControl();
        try {
            referents = null;
            WB.concurrentGCRunTo(WB.AFTER_MARKING_STARTED);
            clearWeak((WeakReference<?>) REFERENCES[0]);
            clearPhantom((PhantomReference<?>) REFERENCES[1]);

            WB.concurrentGCRunTo(WB.BEFORE_MARKING_COMPLETED);
            clearWeak((WeakReference<?>) REFERENCES[2]);
            clearPhantom((PhantomReference<?>) REFERENCES[3]);
            checkCompiled(weak);
            checkCompiled(phantom);
            WB.concurrentGCRunToIdle();

            String failures = "";
            for (int i = 0; i < REFERENCES.length; i++) {
                boolean cleared = REFERENCES[i].refersTo(null);
                boolean collected = OBSERVERS[i].refersTo(null);
                System.out.println("case=" + i + " cleared=" + cleared + " collected=" + collected);
                if (!cleared || !collected) {
                    failures += " " + i;
                }
            }
            if (!failures.isEmpty()) {
                throw new RuntimeException("Referents kept alive in cases:" + failures);
            }
        } finally {
            WB.concurrentGCReleaseControl();
        }
    }
}
