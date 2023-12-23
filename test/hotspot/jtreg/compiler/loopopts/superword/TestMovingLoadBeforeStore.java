/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316679
 * @summary In SuperWord::output, LoadVector can be moved before StoreVector, but only if it is proven to be safe.
 * @key randomness
 * @library /test/lib
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestMovingLoadBeforeStore::test*
 *                   -Xbatch -XX:LoopUnrollLimit=100
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM
 *                   compiler.loopopts.superword.TestMovingLoadBeforeStore
 */

package compiler.loopopts.superword;
import java.util.Random;
import jdk.test.lib.Utils;

public class TestMovingLoadBeforeStore {
    static int RANGE = 1024*64;

    private static final Random random = Utils.getRandomInstance();

    public static void main(String[] strArr) {
        byte a[] = new byte[RANGE];
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
}
