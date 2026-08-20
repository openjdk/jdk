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

/**
 * @test
 * @bug 8390650
 * @summary Test unreachable unsafe accesses at offset zero.
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=delayinline,${test.main.class}::getOffset
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */

import jdk.internal.misc.Unsafe;

public class TestUnsafeAccessWithZeroOffset {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static Object testPlain(Object obj, Object x, boolean b) {
        Object unused = new Object(); // Trigger EA
        long offset = getOffset();
        if (b) {
            // Never reached
            Object result = UNSAFE.getReference(obj, 0L);
            UNSAFE.putReference(obj, offset, x);
            return result;
        }
        return null;
    }

    static Object testLoadStore(Object obj, Object expected, Object x, boolean b) {
        Object unused = new Object(); // Trigger EA
        if (b) {
            // Never reached
            return UNSAFE.compareAndExchangeReference(obj, 0L, expected, x);
        }
        return null;
    }

    static long getOffset() {
        return 0L;
    }

    static Object testPlainDelayed(Object obj, Object x, boolean b) {
        Object unused = new Object(); // Trigger EA
        long offset = getOffset();
        if (b) {
            // Never reached
            Object result = UNSAFE.getReference(obj, offset);
            UNSAFE.putReference(obj, offset, x);
            return result;
        }
        return null;
    }

    static Object testLoadStoreDelayed(Object obj, Object expected, Object x, boolean b) {
        Object unused = new Object(); // Trigger EA
        long offset = getOffset();
        if (b) {
            // Never reached
            return UNSAFE.compareAndExchangeReference(obj, offset, expected, x);
        }
        return null;
    }

    public static void main(String[] args) {
        Object value = new Object();
        testPlain(value, value, false);
        testLoadStore(value, value, value, false);
        testPlainDelayed(value, value, false);
        testLoadStoreDelayed(value, value, value, false);
    }
}

