/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8325372
 * @summary fusion of heap stable test causes GetAndSet node to be removed
 * @requires vm.gc.Shenandoah
 * @modules java.base/jdk.internal.misc:+open
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:-BackgroundCompilation TestUnsafeLoadStoreMergedHeapStableTests
 */

import jdk.internal.misc.Unsafe;

import java.lang.reflect.Field;

public class TestUnsafeLoadStoreMergedHeapStableTests {

    static final jdk.internal.misc.Unsafe UNSAFE = Unsafe.getUnsafe();
    static long F_OFFSET;

    static class A {
        Object f;
    }

    static {
        try {
            Field fField = A.class.getDeclaredField("f");
            F_OFFSET = UNSAFE.objectFieldOffset(fField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Object testHelper(boolean flag, Object o, long offset, Object x) {
        if (flag) {
            return UNSAFE.getAndSetObject(o, offset, x);
        }
        return null;
    }

    static Object field;


    static Object test1(boolean flag, Object o, long offset) {
        return testHelper(flag, null, offset, field);
    }

    static Object test2(Object o, long offset) {
        return UNSAFE.getAndSetObject(o, offset, field);
    }

    static public void main(String[] args) {
        A a = new A();
        for (int i = 0; i < 20_000; i++) {
            testHelper(true, a, F_OFFSET, null);
            test1(false, a, F_OFFSET);
            test2(a, F_OFFSET);
        }
    }
}
