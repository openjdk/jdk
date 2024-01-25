/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277060
 * @requires vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 *
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,compiler.debug.TestTracePhaseCCP::test
 * -XX:CompileCommand=compileonly,compiler.debug.TestTracePhaseCCP::test -XX:+TracePhaseCCP
 * compiler.debug.TestTracePhaseCCP
 */

package compiler.debug;

import jdk.internal.misc.Unsafe;
import java.nio.ByteOrder;

public class TestTracePhaseCCP {
    static private Unsafe UNSAFE = Unsafe.getUnsafe();

    static final boolean IS_BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    static int[] srcArr = new int[1];
    static int[] dstArr = new int[1];

    static int test(boolean flag) {
        int[]  srcArrIntLocal  = new int[1];
        long[] srcArrLongLocal = new long[1];
        Object srcArrLocal = (flag ? srcArrIntLocal               : srcArrLongLocal);
        long   srcOffset   = (flag ? Unsafe.ARRAY_INT_BASE_OFFSET : Unsafe.ARRAY_LONG_BASE_OFFSET);
        srcOffset += (!flag && IS_BIG_ENDIAN ? 4 : 0);
        UNSAFE.copyMemory(srcArrLocal, srcOffset, dstArr, Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        return dstArr[0];
    }

    static boolean flag = false;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            flag = (i % 2 == 0);
            int r1 = test(flag);
        }
    }
}
