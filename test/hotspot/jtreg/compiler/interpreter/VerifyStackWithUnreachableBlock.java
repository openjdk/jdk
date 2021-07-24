/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/*
 * @test VerifyStackWithUnreachableBlock
 * @bug 8271055
 * @summary Using VerifyStack for method that contains unreachable basic blocks
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyStack compiler.interpreter.VerifyStackWithUnreachableBlock
 */

package compiler.interpreter;

public class VerifyStackWithUnreachableBlock {
    public static void main(String[] strArr) {
        VerifyStackWithUnreachableBlock _instance = new VerifyStackWithUnreachableBlock();
        for (int i = 0; i < 10000; i++) {
            _instance.test();
        }
    }

    void test() {
        int i8 = 1;
        try {
            ;
        } finally {
            for (; i8 < 100; i8++) {
            }
        }
    }
}