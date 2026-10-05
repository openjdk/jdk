/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273359
 *
 * @modules java.base/jdk.internal.misc:+open
 * @run main/othervm -Xbatch compiler.unsafe.AlignmentGapAccess
 */

package compiler.unsafe;

import jdk.internal.misc.Unsafe;

public class AlignmentGapAccess {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static class A           { int  fa; }
    static class B extends A { byte fb; }
    static class C extends B { int  fc; }

    static final long FA_OFFSET = UNSAFE.objectFieldOffset(A.class, "fa");
    static final long FB_OFFSET = UNSAFE.objectFieldOffset(B.class, "fb");
    static final long FC_OFFSET = UNSAFE.objectFieldOffset(C.class, "fc");

    static int test(B obj) {
        return UNSAFE.getInt(obj, FB_OFFSET + 1);
    }

    public static void main(String[] args) {
        System.out.printf("Layout: +%d: fa; +%d: fb; +%d: fc\n",
                          FA_OFFSET, FB_OFFSET, FC_OFFSET);

        for (int i = 0; i < 20_000; i++) {
            test(new C());
        }
        System.out.println("TEST PASSED");
    }
}
