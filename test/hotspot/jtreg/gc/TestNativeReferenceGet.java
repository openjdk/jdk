/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 * @test
 * @bug 8352565
 * @summary Determine whether the native method implementation of
 * Reference.get() works as expected.  Disable the intrinsic implementation to
 * force use of the native method.
 * @library /test/lib
 * @modules java.base/java.lang.ref:open
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *    -Xbootclasspath/a:.
 *    -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *    -XX:DisableIntrinsic=_Reference_get0
 *    gc.TestNativeReferenceGet
 */

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import jdk.test.whitebox.WhiteBox;

public final class TestNativeReferenceGet {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static void gcUntilOld(Object o) {
        while (!WB.isObjectInOldGen(o)) {
            WB.fullGC();
        }
    }

    private static final class TestObject {
        public final int value;

        public TestObject(int value) {
            this.value = value;
        }
    }

    private static final ReferenceQueue<TestObject> queue =
        new ReferenceQueue<TestObject>();

    private static final class Ref extends WeakReference<TestObject> {
        public Ref(TestObject obj) {
            super(obj, queue);
        }
    }

    private static final int NUM_REFS = 100;

    private static List<Ref> references = null;
    private static List<TestObject> referents = null;

    // Create all the objects used by the test, and ensure they are all in the
    // old generation.
    private static void setup() {
        references = new ArrayList<Ref>(NUM_REFS);
        referents = new ArrayList<TestObject>(NUM_REFS);

        for (int i = 0; i < NUM_REFS; ++i) {
            TestObject obj = new TestObject(i);
            referents.add(obj);
            references.add(new Ref(obj));
        }

        gcUntilOld(references);
        gcUntilOld(referents);
        for (int i = 0; i < NUM_REFS; ++i) {
            gcUntilOld(references.get(i));
            gcUntilOld(referents.get(i));
        }
    }

    // Discard all the strong references.
    private static void dropReferents() {
        // Not using List.clear() because it doesn't document null'ing elements.
        for (int i = 0; i < NUM_REFS; ++i) {
            referents.set(i, null);
        }
    }

    // Create new strong references from the weak references, by using the
    // native method implementation of Reference.get() and recording the value
    // in references.
    private static void strengthenReferents() {
        for (int i = 0; i < NUM_REFS; ++i) {
            referents.set(i, references.get(i).get());
        }
    }

    private static void check() {
        // None of the references should have been cleared and enqueued,
        // because we have strong references to all the referents.
        try {
            while (WB.waitForReferenceProcessing()) {}
        } catch (InterruptedException e) {
            throw new RuntimeException("Test interrupted");
        }
        if (queue.poll() != null) {
            throw new RuntimeException("Reference enqueued");
        }

        // Check details of expected state.
        for (int i = 0; i < NUM_REFS; ++i) {
            Ref reference = (Ref) references.get(i);
            TestObject referent = reference.get();
            if (referent == null) {
                throw new RuntimeException("Referent not strengthened");
            } else if (referent != referents.get(i)) {
                throw new RuntimeException(
                    "Reference referent differs from saved referent: " + i);
            } else if (referent.value != i) {
                throw new RuntimeException(
                    "Referent " + i + " value: " + referent.value);
            }
        }
    }

    private static void testConcurrent() {
        System.out.println("Testing concurrent GC");
        try {
            WB.concurrentGCAcquireControl();
            dropReferents();
            WB.concurrentGCRunTo(WB.BEFORE_MARKING_COMPLETED);
            strengthenReferents();
            WB.concurrentGCRunToIdle();
            check();
        } finally {
            WB.concurrentGCReleaseControl();
        }
    }

    private static void testNonconcurrent() {
        System.out.println("Testing nonconcurrent GC");
        // A GC between clearing and strengthening will result in test failure.
        // We try to make that unlikely via this immediately preceeding GC.
        WB.fullGC();
        dropReferents();
        strengthenReferents();
        WB.fullGC();
        check();
    }

    public static final void main(String[] args) {
        setup();
        if (WB.supportsConcurrentGCBreakpoints()) {
            testConcurrent();
        } else {
            testNonconcurrent();
        }
    }
}
