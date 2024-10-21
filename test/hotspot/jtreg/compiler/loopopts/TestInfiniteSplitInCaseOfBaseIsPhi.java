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
 * @bug 8340602
 * @requires vm.compiler2.enabled & vm.gc.Parallel
 * @summary C2: LoadNode::split_through_phi might exhaust nodes in case of base_is_phi
 * @run main/othervm -Xbatch -XX:+UseParallelGC
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   compiler.loopopts.TestInfiniteSplitInCaseOfBaseIsPhi
 */

package compiler.loopopts;

import java.util.Random;

public class TestInfiniteSplitInCaseOfBaseIsPhi {

    static class Obj {
        final Integer[] array;
        final int start;
        final int end;

        Integer max = Integer.MIN_VALUE;

        Obj(Integer[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }

        Integer cmp(Integer i, Integer j) {
            return i > j ? i : j;
        }

        void calc() {
            int i = start;
            do {
                max = cmp(max, array[i]);
                i++;
            } while (i < end);
        }
    }

    static final int LEN = 2000;
    static final Integer[] a = new Integer[LEN];
    static {
        Random r = new Random();
        for (int i = 0; i < LEN; i++) {
            a[i] = Integer.valueOf(r.nextInt());
        }
    }

    public static void main (String[] args) {
        Obj o = new Obj(a, 0, LEN);
        for (int i = 0; i < 1000; i++) {
            o.calc();
        }
        System.out.println(o.max);
    }
}
