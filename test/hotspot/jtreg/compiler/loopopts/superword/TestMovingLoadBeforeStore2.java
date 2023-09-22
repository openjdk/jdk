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
 * @bug 8316594
 * @summary In SuperWord::output, LoadVector can be moved before StoreVector, but only if it is proven to be safe.
 * @library /test/lib
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestMovingLoadBeforeStore2::test
 *                   -XX:LoopUnrollLimit=250 -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM
 *                   compiler.loopopts.superword.TestMovingLoadBeforeStore2
 */

package compiler.loopopts.superword;
import java.util.Random;
import jdk.test.lib.Utils;

public class TestMovingLoadBeforeStore2 {
    static int RANGE = 1024*64;

    static int NINE = 9;

    private static final Random random = Utils.getRandomInstance();

    public static void main(String[] strArr) {
        byte a[] = new byte[RANGE];
        byte b[] = new byte[RANGE];
	for (int i = 0; i < 100; i++) {
            for (int j = 0; j < a.length; j++) {
                a[j] = (byte)random.nextInt();
                b[j] = (byte)random.nextInt();
            }
            byte[] a_ref = a.clone();
            byte[] b_ref = b.clone();
            byte[] a_res = a.clone();
            byte[] b_res = b.clone();
            test_ref(a_ref, b_ref);
            test(a_res, b_res);
            verify("a", a_ref, a_res);
            verify("b", b_ref, b_res);
        }
    }

    static void verify(String name, byte[] ref, byte[] res) {
        boolean fail = false;
        for (int j = 0; j < ref.length; j++) {
            if (ref[j] != res[j]) {
                System.out.println("Wrong: " + j + ":" + ref[j] + " vs " + res[j]);
                fail = true;
            }
        }
        if (fail) {
            throw new RuntimeException("wrong result on array " + name);
        }
    }

    static void test(byte[] a, byte[] b) {
        for (int i = 46; i < 6000; i++) {
            a[47 + i +  0]++;
            a[47 + i +  1]++;
            a[47 + i +  2]++;
            a[47 + i +  3]++;
            b[NINE + i +  0]++;
            b[NINE + i +  1]++;
            b[NINE + i +  2]++;
            b[NINE + i +  3]++;
        }
    }

    static void test_ref(byte[] a, byte[] b) {
        for (int i = 46; i < 6000; i++) {
            a[47 + i +  0]++;
            a[47 + i +  1]++;
            a[47 + i +  2]++;
            a[47 + i +  3]++;
            b[NINE + i +  0]++;
            b[NINE + i +  1]++;
            b[NINE + i +  2]++;
            b[NINE + i +  3]++;
        }
    }
}

