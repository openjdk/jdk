/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8378897
 * @summary assertion failure due to missing depends_only_on_test_impl definition in SqrtHFNode
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @requires vm.debug & vm.compiler2.enabled
 * @run main/othervm --add-modules=jdk.incubator.vector -Xbatch compiler.c2.TestDependsOnTestSqrtHFAssertion
 */

package compiler.c2;

import jdk.incubator.vector.*;

public class TestDependsOnTestSqrtHFAssertion {
    public static int x1 = 10;

    public static int x2 = 20;

    public static int y = 30;

    public static int micro(int x1, int x2, int y, int ctr) {
        int res = 0;
        for (int i = 0; i < ctr; i++) {
            if (y != 0) {
                if (x1 > 0) {
                    if (x2 > 0) {
                        if (y != 0) {
                            res += (int)Float16.float16ToRawShortBits(Float16.sqrt(Float16.shortBitsToFloat16((short)(x1/y))));
                            res += (int)Float16.float16ToRawShortBits(Float16.sqrt(Float16.shortBitsToFloat16((short)(x2/y))));
                        }
                    }
                }
            }
        }
        return res;
    }

    public static void main(String [] args) {
        int res = 0;
        for (int i = 0 ; i < 10000; i++) {
            res += micro(x1, x2, y, i % 100);
        }
        IO.println("PASS" + res);
    }
}
