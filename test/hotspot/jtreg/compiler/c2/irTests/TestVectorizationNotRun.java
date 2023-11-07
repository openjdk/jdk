/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;
import java.util.Objects;
import java.util.Random;

/*
 * @test
 * @bug 8300256
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorizationNotRun
 */

public class TestVectorizationNotRun {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    static int size = 1024;
    static int sizeBytes = 8 * size;
    static byte[] byteArray = new byte[sizeBytes];
    static long[] longArray = new long[size];

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void test(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            if ((i < 0) || (8 > sizeBytes - i)) {
                throw new IndexOutOfBoundsException();
            }
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + i * 8, src[i]);
        }
    }

    @Run(test = "test")
    public static void test_runner() {
        test(byteArray, longArray);
    }

}
