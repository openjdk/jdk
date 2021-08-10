/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @bug 8272131
 * @requires vm.compiler2.enabled
 * @summary ArrayCopy with negative index before infinite loop
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,"*TestIllegalArrayCopyBeforeInfiniteLoop::foo"
 *                   compiler.arraycopy.TestIllegalArrayCopyBeforeInfiniteLoop
 */

package compiler.arraycopy;

import java.util.Arrays;

public class TestIllegalArrayCopyBeforeInfiniteLoop {
    private static char src[] = new char[10];
    private static int count = 0;
    private static final int iter = 10_000;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < iter; ++i) {
            foo();
        }
        if (count != iter) {
            throw new RuntimeException("test failed");
        }
    }

    static void foo() {
        try {
            Arrays.copyOfRange(src, -1, 128);
            do {
            } while (true);
        } catch (ArrayIndexOutOfBoundsException ex) {
            count++;
        }
    }
}
