/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8382815
 * @run main/othervm -XX:CompileCommand=dontinline,${test.main.class}::test_* ${test.main.class}
 */

package compiler.intrinsics;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.PhantomReference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public class TestReferenceGet {
    private static final void fail(String msg) throws Exception {
        throw new RuntimeException(msg);
    }

    private static final void test0(Reference ref,
                                    Object expectedValue,
                                    Object unexpectedValue,
                                    String kind) throws Exception {
        if ((expectedValue != null) && ref.get() == null) {
            fail(kind + " refers to null");
        }
        if (ref.get() != expectedValue) {
            fail(kind + " doesn't refer to expected value");
        }
        if (ref.get() == unexpectedValue) {
            fail(kind + " refers to unexpected value");
        }
    }

    private static final void test_phantom0(PhantomReference ref,
                                            String kind) throws Exception {
        if (ref.get() != null) {
            fail(kind + " does not refer to null");
        }
    }

    // Entry points to the test, important to push down type information to
    // individual test methods.

    private static final void test_phantom(PhantomReference ref) throws Exception {
        test_phantom0(ref, "phantom");
    }

    private static final void test_phantom_shadow(ShadowPhantomReference ref) throws Exception {
        test_phantom0(ref, "phantom shadow");
    }

    private static final void test_weak(WeakReference ref,
                                        Object expectedValue,
                                        Object unexpectedValue) throws Exception {
        test0(ref, expectedValue, unexpectedValue, "weak");
    }

    private static final void test_weak_shadow(ShadowWeakReference ref,
                                        Object expectedValue,
                                        Object unexpectedValue) throws Exception {
        test0(ref, expectedValue, unexpectedValue, "weak shadow");
    }

    private static final void test_soft(SoftReference ref,
                                        Object expectedValue,
                                        Object unexpectedValue) throws Exception {
        test0(ref, expectedValue, unexpectedValue, "soft");
    }

    private static final void test_soft_shadow(ShadowSoftReference ref,
                                        Object expectedValue,
                                        Object unexpectedValue) throws Exception {
        test0(ref, expectedValue, unexpectedValue, "soft shadow");
    }

    static Object unexpected = new Object();

    static Object obj0 = new Object();
    static Object obj1 = new Object();
    static Object obj2 = new Object();
    static Object obj3 = new Object();
    static Object obj4 = new Object();
    static Object obj5 = new Object();

    public static void main(String[] args) throws Exception {
        var queue = new ReferenceQueue<Object>();

        // It is important to do all test methods in the loop, so that we
        // exercise all paths in intrinsics.
        for (int i = 0; i < 100000; i++) {
            System.out.println("Create");
            var pref = new PhantomReference(obj0, queue);
            var wref = new WeakReference(obj1);
            var sref = new SoftReference(obj2);
            var psref = new ShadowPhantomReference<>(obj3, queue);
            var wsref = new ShadowWeakReference<>(obj4);
            var ssref = new ShadowSoftReference<>(obj5);

            System.out.println("After creation");
            test_phantom(pref);
            test_weak(wref, obj1, unexpected);
            test_soft(sref, obj2, unexpected);
            test_phantom_shadow(psref);
            test_weak_shadow(wsref, obj4, unexpected);
            test_soft_shadow(ssref, obj5, unexpected);

            System.out.println("Cleaning references");
            pref.clear();
            wref.clear();
            sref.clear();
            psref.clear();
            wsref.clear();
            ssref.clear();

            System.out.println("Testing after cleaning");
            test_phantom(pref);
            test_weak(wref, null, unexpected);
            test_soft(sref, null, unexpected);
            test_phantom_shadow(psref);
            test_weak_shadow(wsref, null, unexpected);
            test_soft_shadow(ssref, null, unexpected);
        }
    }

    // References that have their own "shadow" referent. Check that intrinsics
    // hit the right referent.

    static class ShadowSoftReference<T> extends SoftReference<T> {
        T referent;
        public ShadowSoftReference(T ref) {
            super(ref);
            referent = ref;
        }
    }

    static class ShadowWeakReference<T> extends WeakReference<T> {
        T referent;
        public ShadowWeakReference(T ref) {
            super(ref);
            referent = ref;
        }
    }

    static class ShadowPhantomReference<T> extends PhantomReference<T> {
        T referent;
        public ShadowPhantomReference(T ref, ReferenceQueue<Object> q) {
            super(ref, q);
            referent = ref;
        }
    }
}
