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

package compiler.valhalla.valuetypes;

import jdk.internal.misc.Unsafe;

/*
 * @test
 * @bug 8396025
 * @summary Test control restoration when an Unsafe access intrinsic bails out
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @run main ${test.main.class}
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */
public class TestUnsafeAccessIntrinsicBailout {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final long OFFSET = UNSAFE.objectFieldOffset(Integer.class, "value");

    static Object test(Integer value, boolean alwaysFalse) {
        return alwaysFalse ? UNSAFE.getReference(value, OFFSET) : null;
    }

    public static void main(String[] args) {
        test(42, false);
    }
}
