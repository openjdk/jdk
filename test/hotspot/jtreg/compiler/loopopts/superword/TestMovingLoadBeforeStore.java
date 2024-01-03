/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.compiler2.enabled
 * @bug 8316679 8316594
 * @summary In SuperWord::output, LoadVector can be moved before StoreVector, but only if it is proven to be safe.
 * @key randomness
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestMovingLoadBeforeStore::test*
 *                   -Xbatch -XX:LoopUnrollLimit=100
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM
 *                   --add-modules java.base --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
 *                   compiler.loopopts.superword.TestMovingLoadBeforeStore
 */

package compiler.loopopts.superword;
import java.util.Random;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;

public class TestMovingLoadBeforeStore {
    static int RANGE = 1024*64;

    static int NINE = 9;

    private static final Random random = Utils.getRandomInstance();
    static Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] strArr) {
        byte a[] = new byte[RANGE];
        byte b[] = new byte[RANGE];
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < a.length; j++) {
                a[j] = (byte)random.nextInt();
            }
            byte[] a_ref = a.clone();
            byte[] a_res = a.clone();
            ref1(a_ref, a_ref, i % 2);
            test1(a_res, a_res, i % 2);
            verify("a in test1", a_ref, a_res, a);
        }
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < a.length; j++) {
                a[j] = (byte)random.nextInt();
                b[j] = (byte)random.nextInt();
            }
            byte[] a_ref = a.clone();
            byte[] b_ref = b.clone();
            byte[] a_res = a.clone();
            byte[] b_res = b.clone();
            ref2(a_ref, b_ref);
            test2(a_res, b_res);
            verify("a in test2", a_ref, a_res, a);
            verify("b in test2", b_ref, b_res, b);
        }
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < a.length; j++) {
                a[j] = (byte)random.nextInt();
            }
            byte[] a_ref = a.clone();
            byte[] a_res = a.clone();
            ref3(a_ref);
            test3(a_res);
            verify("a in test3", a_ref, a_res, a);
        }
    }

    static void verify(String name, byte[] ref, byte[] res, byte[] orig) {
        boolean fail = false;
        for (int j = 0; j < ref.length; j++) {
            if (ref[j] != res[j]) {
                System.out.println("Wrong: " + j + ":" + ref[j] + " vs " + res[j] + " from " + orig[j]);
                fail = true;
            }
        }
        if (fail) {
            throw new RuntimeException("wrong result for array " + name);
        }
    }

    static void test1(byte[] a, byte[] b, int inv) {
        for (int i = 0; i < RANGE-4; i+=4) {
            a[i + 0]++;
            a[i + 1]++;
            a[i + 2]++;
            a[i + 3]++;
            b[inv + i + 0]++;
            b[inv + i + 1]++;
            b[inv + i + 2]++;
            b[inv + i + 3]++;
        }
    }

    static void ref1(byte[] a, byte[] b, int inv) {
        for (int i = 0; i < RANGE-4; i+=4) {
            a[i + 0]++;
            a[i + 1]++;
            a[i + 2]++;
            a[i + 3]++;
            b[inv + i + 0]++;
            b[inv + i + 1]++;
            b[inv + i + 2]++;
            b[inv + i + 3]++;
        }
    }

    static void test2(byte[] a, byte[] b) {
        for (int i = 46; i < 6000; i++) {
            a[47 + i + 0]++;
            a[47 + i + 1]++;
            a[47 + i + 2]++;
            a[47 + i + 3]++;
            b[NINE + i + 0]++;
            b[NINE + i + 1]++;
            b[NINE + i + 2]++;
            b[NINE + i + 3]++;
        }
    }

    static void ref2(byte[] a, byte[] b) {
        for (int i = 46; i < 6000; i++) {
            a[47 + i + 0]++;
            a[47 + i + 1]++;
            a[47 + i + 2]++;
            a[47 + i + 3]++;
            b[NINE + i + 0]++;
            b[NINE + i + 1]++;
            b[NINE + i + 2]++;
            b[NINE + i + 3]++;
        }
    }

    static void test3(byte[] a) {
        for (int i = 51; i < 6000; i++) {
            int adr = UNSAFE.ARRAY_BYTE_BASE_OFFSET + 42 + i;
            UNSAFE.putIntUnaligned(a, adr + 0*4, UNSAFE.getIntUnaligned(a, adr + 0*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 1*4, UNSAFE.getIntUnaligned(a, adr + 1*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 2*4, UNSAFE.getIntUnaligned(a, adr + 2*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 3*4, UNSAFE.getIntUnaligned(a, adr + 3*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 4*4, UNSAFE.getIntUnaligned(a, adr + 4*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 5*4, UNSAFE.getIntUnaligned(a, adr + 5*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 6*4, UNSAFE.getIntUnaligned(a, adr + 6*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 7*4, UNSAFE.getIntUnaligned(a, adr + 7*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 8*4, UNSAFE.getIntUnaligned(a, adr + 8*4) + 1);
        }
    }

    static void ref3(byte[] a) {
        for (int i = 51; i < 6000; i++) {
            int adr = UNSAFE.ARRAY_BYTE_BASE_OFFSET + 42 + i;
            UNSAFE.putIntUnaligned(a, adr + 0*4, UNSAFE.getIntUnaligned(a, adr + 0*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 1*4, UNSAFE.getIntUnaligned(a, adr + 1*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 2*4, UNSAFE.getIntUnaligned(a, adr + 2*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 3*4, UNSAFE.getIntUnaligned(a, adr + 3*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 4*4, UNSAFE.getIntUnaligned(a, adr + 4*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 5*4, UNSAFE.getIntUnaligned(a, adr + 5*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 6*4, UNSAFE.getIntUnaligned(a, adr + 6*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 7*4, UNSAFE.getIntUnaligned(a, adr + 7*4) + 1);
            UNSAFE.putIntUnaligned(a, adr + 8*4, UNSAFE.getIntUnaligned(a, adr + 8*4) + 1);
        }
    }
}
