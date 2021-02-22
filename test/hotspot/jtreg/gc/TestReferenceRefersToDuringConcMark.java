/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

package gc;

/* @test
 * @requires vm.gc != "Epsilon"
 * @requires vm.gc != "Shenandoah"
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @modules java.base
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      gc.TestReferenceRefersToDuringConcMark
 */

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import sun.hotspot.WhiteBox;

public class TestReferenceRefersToDuringConcMark {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final class TestObject {
        public final int value;

        public TestObject(int value) {
            this.value = value;
        }
    }

    private static volatile TestObject testObjectNone = null;
    private static volatile TestObject testObject = null;

    private static ReferenceQueue<TestObject> queue = null;

    private static WeakReference<TestObject> testWeak = null;

    private static void setup() {
        testObjectNone = new TestObject(0);
        testObject = new TestObject(4);

        queue = new ReferenceQueue<TestObject>();

        testWeak = new WeakReference<TestObject>(testObject, queue);
    }

    private static void gcUntilOld(Object o) throws Exception {
        if (!WB.isObjectInOldGen(o)) {
            WB.fullGC();
            if (!WB.isObjectInOldGen(o)) {
                fail("object not promoted by full gc");
            }
        }
    }

    private static void gcUntilOld() throws Exception {
        gcUntilOld(testObjectNone);
        gcUntilOld(testObject);

        gcUntilOld(testWeak);
    }

    private static void progress(String msg) {
        System.out.println(msg);
    }

    private static void fail(String msg) throws Exception {
        throw new RuntimeException(msg);
    }

    private static void expectCleared(Reference<TestObject> ref,
                                      String which) throws Exception {
        expectNotValue(ref, testObjectNone, which);
        if (!ref.refersTo(null)) {
            fail("expected " + which + " to be cleared");
        }
    }

    private static void expectNotCleared(Reference<TestObject> ref,
                                         String which) throws Exception {
        expectNotValue(ref, testObjectNone, which);
        if (ref.refersTo(null)) {
            fail("expected " + which + " to not be cleared");
        }
    }

    private static void expectValue(Reference<TestObject> ref,
                                    TestObject value,
                                    String which) throws Exception {
        expectNotValue(ref, testObjectNone, which);
        expectNotCleared(ref, which);
        if (!ref.refersTo(value)) {
            fail(which + " doesn't refer to expected value");
        }
    }

    private static void expectNotValue(Reference<TestObject> ref,
                                       TestObject value,
                                       String which) throws Exception {
        if (ref.refersTo(value)) {
            fail(which + " refers to unexpected value");
        }
    }

    private static void checkInitialStates() throws Exception {
        expectValue(testWeak, testObject, "testWeak");
    }

    private static void discardStrongReferences() {
        testObject = null;
    }

    private static void testConcurrentCollection() throws Exception {
        progress("setup concurrent collection test");
        setup();
        progress("gcUntilOld");
        gcUntilOld();

        progress("acquire control of concurrent cycles");
        WB.concurrentGCAcquireControl();
        try {
            progress("check initial states");
            checkInitialStates();

            progress("discard strong references");
            discardStrongReferences();

            progress("run GC to before marking completed");
            WB.concurrentGCRunTo(WB.BEFORE_MARKING_COMPLETED);

            progress("fetch test objects, possibly keeping some alive");
            // For some collectors, calling get() will keep testObject alive.
            if (testWeak.get() == null) {
                fail("testWeak unexpectedly == null");
            }

            progress("finish collection");
            WB.concurrentGCRunToIdle();

            progress("verify expected clears");
            expectNotCleared(testWeak, "testWeak");

            progress("verify get returns expected values");
            TestObject obj = testWeak.get();
            if (obj == null) {
                fail("testWeak.get() returned null");
            } else if (obj.value != 4) {
                fail("testWeak.get().value is " + obj.value);
            }

            progress("verify queue entries");
            long timeout = 60000; // 1 minute of milliseconds.
            while (true) {
                Reference<? extends TestObject> ref = queue.remove(timeout);
                if (ref == null) {
                    break;
                } else if (ref == testWeak) {
                    testWeak = null;
                } else {
                    fail("unexpected reference in queue");
                }
            }
            if (testWeak == null) {
                if (obj != null) {
                    fail("testWeak notified");
                }
            }

        } finally {
            progress("release control of concurrent cycles");
            WB.concurrentGCReleaseControl();
        }
        progress("finished concurrent collection test");
    }

    public static void main(String[] args) throws Exception {
        if (WB.supportsConcurrentGCBreakpoints()) {
            testConcurrentCollection();
        }
    }
}
