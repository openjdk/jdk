/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All rights reserved.
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
 * @bug 8278125
 * @summary Test if OOME has proper stacktrace
 * @library /test/lib
 * @run main/othervm -Xmx100m -Xms100m GenOutOfMemoryError
 */

import jdk.test.lib.Asserts;

public class GenOutOfMemoryError {
    private static int OOME_HAS_STACK_CNT = 0;

    private void badMethod(int n){
        try {
            System.out.format("bad method was invoked %n", n);
            // Try to allocate an array the same size as the heap - it will throw OOME without
            // actually consuming available memory.
            Integer[] array = new Integer[1000 * 1000 * 100];
            array.hashCode();
        } catch (Throwable t){
            StackTraceElement[] traces  = t.getStackTrace();
            if (traces.length != 0) {
                OOME_HAS_STACK_CNT++;
            }
            t.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GenOutOfMemoryError genOutOfMemoryError = new GenOutOfMemoryError();

        for (int i = 0; i < 7; i++) {
            genOutOfMemoryError.badMethod(i + 1);
        }
        Asserts.assertTrue(4/*PreallocatedOutOfMemoryErrorCount defaults to 4*/ == OOME_HAS_STACK_CNT, "Some OOMEs do not have stacktraces");
    }
}
