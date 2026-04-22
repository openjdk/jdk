/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
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

/* @test
 * @summary Pass values on stack.
 * @requires os.arch == "riscv64"
 * @run main/native compiler.calls.TestManyArgs
 */

package compiler.calls;

public class TestManyArgs {
    static {
        System.loadLibrary("TestManyArgs");
    }

    native static void scramblestack();

    native static int checkargs(int arg0, short arg1, byte arg2,
                                int arg3, short arg4, byte arg5,
                                int arg6, short arg7, byte arg8,
                                int arg9, short arg10, byte arg11);

    static int compiledbridge(int arg0, short arg1, byte arg2,
                              int arg3, short arg4, byte arg5,
                              int arg6, short arg7, byte arg8,
                              int arg9, short arg10, byte arg11) {
        return checkargs(arg0, arg1, arg2, arg3, arg4, arg5,
                         arg6, arg7, arg8, arg9, arg10, arg11);
    }

    static public void main(String[] args) {
        scramblestack();
        for (int i = 0; i < 20000; i++) {
            int res = compiledbridge((int)0xf, (short)0xf, (byte)0xf,
                                     (int)0xf, (short)0xf, (byte)0xf,
                                     (int)0xf, (short)0xf, (byte)0xf,
                                     (int)0xf, (short)0xf, (byte)0xf);
            if (res != 0) {
                throw new RuntimeException("Test failed");
            }
        }
    }
}
