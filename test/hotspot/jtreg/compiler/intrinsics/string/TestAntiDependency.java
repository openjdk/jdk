/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.intrinsics.string;

import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8373591
 * @summary Verify that StringLatin1::inflate and StringUTF16::compress are scheduled properly
 * @library /test/lib /
 * @modules java.base/java.lang:+open
 * @run driver ${test.main.class}
 */
public class TestAntiDependency {
    static final MethodHandle COMPRESS_HANDLE;
    static final MethodHandle INFLATE_HANDLE;
    static {
        try {
            var lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
            Class<?> stringUtf16Class = lookup.findClass("java.lang.StringUTF16");
            COMPRESS_HANDLE = lookup.findStatic(stringUtf16Class, "compress",
                    MethodType.methodType(int.class, char[].class, int.class, byte[].class, int.class, int.class));
            Class<?> stringLatin1Class = lookup.findClass("java.lang.StringLatin1");
            INFLATE_HANDLE = lookup.findStatic(stringLatin1Class, "inflate",
                    MethodType.methodType(void.class, byte[].class, int.class, char[].class, int.class, int.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-opens=java.base/java.lang=ALL-UNNAMED");
    }

    @DontInline
    static void consume(Object o1, Object o2) {}

    @Test
    static int testStringCompress() throws Throwable {
        byte[] dst = new byte[4];
        char[] src = new char[4];
        consume(dst, src);

        // The compiler must not schedule this after the store to src, either by having
        // StringCompressedCopyNode kill the whole memory, or by taking into consideration the
        // anti-dependency between 2 nodes
        int _ = (int) COMPRESS_HANDLE.invokeExact(src, 0, dst, 0, 4);
        src[0] = 1;
        return dst[0];
    }

    @Test
    static int testStringInflate() throws Throwable {
        char[] dst = new char[4];
        byte[] src = new byte[4];
        consume(dst, src);

        // The compiler must not schedule this after the store to src, either by having
        // StringInflatedCopyNode kill the whole memory, or by taking into consideration the
        // anti-dependency between 2 nodes
        INFLATE_HANDLE.invokeExact(src, 0, dst, 0, 4);
        src[0] = 1;
        return dst[0];
    }

    @Run(test = {"testStringCompress", "testStringInflate"})
    public void run() throws Throwable {
        Asserts.assertEQ(0, testStringCompress());
        Asserts.assertEQ(0, testStringInflate());
    }
}
