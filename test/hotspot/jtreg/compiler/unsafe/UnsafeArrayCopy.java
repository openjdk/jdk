/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316756
 * @summary Test UNSAFE.copyMemory in combination with Escape Analysis
 * @library /test/lib
 *
 * @modules java.base/jdk.internal.misc
 *
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,compiler.unsafe.UnsafeArrayCopy::test*
 *                   compiler.unsafe.UnsafeArrayCopy
 */

package compiler.unsafe;

import java.lang.reflect.*;
import java.util.*;

import jdk.internal.misc.Unsafe;


public class UnsafeArrayCopy {

    private static Unsafe UNSAFE = Unsafe.getUnsafe();

    static long SRC_BASE = UNSAFE.allocateMemory(4);
    static long DST_BASE = UNSAFE.allocateMemory(4);

    static class MyClass {
        int x;
    }

    static int test() {
        MyClass obj = new MyClass(); // Non-escaping to trigger Escape Analysis
        UNSAFE.copyMemory(null, SRC_BASE, null, DST_BASE, 4);
        obj.x = 42;
        return obj.x;
    }

    static int[] test2() {
         int[] src = new int[4];
         int[] dst = new int[4];
         MyClass obj = new MyClass();
         UNSAFE.copyMemory(src, 0, dst, 0, 4);
         obj.x = 42;
         dst[1] = obj.x;
         return dst;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            test();
            test2();
        }
    }
}
