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

/**
 * @test
 * @bug 8325467
 * @summary Ensure C2 can compile methods with many arguments.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.arguments.TestManyMethodArguments::test
 *                   -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   compiler.arguments.TestManyMethodArguments
 */

package compiler.arguments;

public class TestManyMethodArguments {
    public static void main(String[] args) {
        try {
            test(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100);
        } catch (Exception e) {
            // Fine
        }
    }

    public static long test(long x1, long x2, long x3, long x4, long x5, long x6, long x7, long x8, long x9, long x10, long x11, long x12, long x13, long x14, long x15, long x16, long x17, long x18, long x19, long x20, long x21, long x22, long x23, long x24, long x25, long x26, long x27, long x28, long x29, long x30, long x31, long x32, long x33, long x34, long x35, long x36, long x37, long x38, long x39, long x40, long x41, long x42, long x43, long x44, long x45, long x46, long x47, long x48, long x49, long x50, long x51, long x52, long x53, long x54, long x55, long x56, long x57, long x58, long x59, long x60, long x61, long x62, long x63, long x64, long x65, long x66, long x67, long x68, long x69, long x70, long x71, long x72, long x73, long x74, long x75, long x76, long x77, long x78, long x79, long x80, long x81, long x82, long x83, long x84, long x85, long x86, long x87, long x88, long x89, long x90, long x91, long x92, long x93, long x94, long x95, long x96, long x97, long x98, long x99, long x100) throws Exception {
        // Exceptions after every definition of a temporary forces the
        // evaluation order.
        long t1 = x1 % 101; if(t1 == 0) { throw new Exception(); }
        long t2 = x2 % 102; if(t2 == 0) { throw new Exception(); }
        long t3 = x3 % 103; if(t3 == 0) { throw new Exception(); }
        long t4 = x4 % 104; if(t4 == 0) { throw new Exception(); }
        long t5 = x5 % 105; if(t5 == 0) { throw new Exception(); }
        long t6 = x6 % 106; if(t6 == 0) { throw new Exception(); }
        long t7 = x7 % 107; if(t7 == 0) { throw new Exception(); }
        long t8 = x8 % 108; if(t8 == 0) { throw new Exception(); }
        long t9 = x9 % 109; if(t9 == 0) { throw new Exception(); }
        long t10 = x10 % 110; if(t10 == 0) { throw new Exception(); }
        long t11 = x11 % 111; if(t11 == 0) { throw new Exception(); }
        long t12 = x12 % 112; if(t12 == 0) { throw new Exception(); }
        long t13 = x13 % 113; if(t13 == 0) { throw new Exception(); }
        long t14 = x14 % 114; if(t14 == 0) { throw new Exception(); }
        long t15 = x15 % 115; if(t15 == 0) { throw new Exception(); }
        long t16 = x16 % 116; if(t16 == 0) { throw new Exception(); }
        long t17 = x17 % 117; if(t17 == 0) { throw new Exception(); }
        long t18 = x18 % 118; if(t18 == 0) { throw new Exception(); }
        long t19 = x19 % 119; if(t19 == 0) { throw new Exception(); }
        long t20 = x20 % 120; if(t20 == 0) { throw new Exception(); }
        long t21 = x21 % 121; if(t21 == 0) { throw new Exception(); }
        long t22 = x22 % 122; if(t22 == 0) { throw new Exception(); }
        long t23 = x23 % 123; if(t23 == 0) { throw new Exception(); }
        long t24 = x24 % 124; if(t24 == 0) { throw new Exception(); }
        long t25 = x25 % 125; if(t25 == 0) { throw new Exception(); }
        long t26 = x26 % 126; if(t26 == 0) { throw new Exception(); }
        long t27 = x27 % 127; if(t27 == 0) { throw new Exception(); }
        long t28 = x28 % 128; if(t28 == 0) { throw new Exception(); }
        long t29 = x29 % 129; if(t29 == 0) { throw new Exception(); }
        long t30 = x30 % 130; if(t30 == 0) { throw new Exception(); }
        long t31 = x31 % 131; if(t31 == 0) { throw new Exception(); }
        long t32 = x32 % 132; if(t32 == 0) { throw new Exception(); }
        long t33 = x33 % 133; if(t33 == 0) { throw new Exception(); }
        long t34 = x34 % 134; if(t34 == 0) { throw new Exception(); }
        long t35 = x35 % 135; if(t35 == 0) { throw new Exception(); }
        long t36 = x36 % 136; if(t36 == 0) { throw new Exception(); }
        long t37 = x37 % 137; if(t37 == 0) { throw new Exception(); }
        long t38 = x38 % 138; if(t38 == 0) { throw new Exception(); }
        long t39 = x39 % 139; if(t39 == 0) { throw new Exception(); }
        long t40 = x40 % 140; if(t40 == 0) { throw new Exception(); }
        long t41 = x41 % 141; if(t41 == 0) { throw new Exception(); }
        long t42 = x42 % 142; if(t42 == 0) { throw new Exception(); }
        long t43 = x43 % 143; if(t43 == 0) { throw new Exception(); }
        long t44 = x44 % 144; if(t44 == 0) { throw new Exception(); }
        long t45 = x45 % 145; if(t45 == 0) { throw new Exception(); }
        long t46 = x46 % 146; if(t46 == 0) { throw new Exception(); }
        long t47 = x47 % 147; if(t47 == 0) { throw new Exception(); }
        long t48 = x48 % 148; if(t48 == 0) { throw new Exception(); }
        long t49 = x49 % 149; if(t49 == 0) { throw new Exception(); }
        long t50 = x50 % 150; if(t50 == 0) { throw new Exception(); }
        long t51 = x51 % 151; if(t51 == 0) { throw new Exception(); }
        long t52 = x52 % 152; if(t52 == 0) { throw new Exception(); }
        long t53 = x53 % 153; if(t53 == 0) { throw new Exception(); }
        long t54 = x54 % 154; if(t54 == 0) { throw new Exception(); }
        long t55 = x55 % 155; if(t55 == 0) { throw new Exception(); }
        long t56 = x56 % 156; if(t56 == 0) { throw new Exception(); }
        long t57 = x57 % 157; if(t57 == 0) { throw new Exception(); }
        long t58 = x58 % 158; if(t58 == 0) { throw new Exception(); }
        long t59 = x59 % 159; if(t59 == 0) { throw new Exception(); }
        long t60 = x60 % 160; if(t60 == 0) { throw new Exception(); }
        long t61 = x61 % 161; if(t61 == 0) { throw new Exception(); }
        long t62 = x62 % 162; if(t62 == 0) { throw new Exception(); }
        long t63 = x63 % 163; if(t63 == 0) { throw new Exception(); }
        long t64 = x64 % 164; if(t64 == 0) { throw new Exception(); }
        long t65 = x65 % 165; if(t65 == 0) { throw new Exception(); }
        long t66 = x66 % 166; if(t66 == 0) { throw new Exception(); }
        long t67 = x67 % 167; if(t67 == 0) { throw new Exception(); }
        long t68 = x68 % 168; if(t68 == 0) { throw new Exception(); }
        long t69 = x69 % 169; if(t69 == 0) { throw new Exception(); }
        long t70 = x70 % 170; if(t70 == 0) { throw new Exception(); }
        long t71 = x71 % 171; if(t71 == 0) { throw new Exception(); }
        long t72 = x72 % 172; if(t72 == 0) { throw new Exception(); }
        long t73 = x73 % 173; if(t73 == 0) { throw new Exception(); }
        long t74 = x74 % 174; if(t74 == 0) { throw new Exception(); }
        long t75 = x75 % 175; if(t75 == 0) { throw new Exception(); }
        long t76 = x76 % 176; if(t76 == 0) { throw new Exception(); }
        long t77 = x77 % 177; if(t77 == 0) { throw new Exception(); }
        long t78 = x78 % 178; if(t78 == 0) { throw new Exception(); }
        long t79 = x79 % 179; if(t79 == 0) { throw new Exception(); }
        long t80 = x80 % 180; if(t80 == 0) { throw new Exception(); }
        long t81 = x81 % 181; if(t81 == 0) { throw new Exception(); }
        long t82 = x82 % 182; if(t82 == 0) { throw new Exception(); }
        long t83 = x83 % 183; if(t83 == 0) { throw new Exception(); }
        long t84 = x84 % 184; if(t84 == 0) { throw new Exception(); }
        long t85 = x85 % 185; if(t85 == 0) { throw new Exception(); }
        long t86 = x86 % 186; if(t86 == 0) { throw new Exception(); }
        long t87 = x87 % 187; if(t87 == 0) { throw new Exception(); }
        long t88 = x88 % 188; if(t88 == 0) { throw new Exception(); }
        long t89 = x89 % 189; if(t89 == 0) { throw new Exception(); }
        long t90 = x90 % 190; if(t90 == 0) { throw new Exception(); }
        long t91 = x91 % 191; if(t91 == 0) { throw new Exception(); }
        long t92 = x92 % 192; if(t92 == 0) { throw new Exception(); }
        long t93 = x93 % 193; if(t93 == 0) { throw new Exception(); }
        long t94 = x94 % 194; if(t94 == 0) { throw new Exception(); }
        long t95 = x95 % 195; if(t95 == 0) { throw new Exception(); }
        long t96 = x96 % 196; if(t96 == 0) { throw new Exception(); }
        long t97 = x97 % 197; if(t97 == 0) { throw new Exception(); }
        long t98 = x98 % 198; if(t98 == 0) { throw new Exception(); }
        long t99 = x99 % 199; if(t99 == 0) { throw new Exception(); }
        long t100 = x100 % 200; if(t100 == 0) { throw new Exception(); }
        // All temporaries are live here, stressing the register allocator.
        return t1 + t2 + t3 + t4 + t5 + t6 + t7 + t8 + t9 + t10 + t11 + t12 + t13 + t14 + t15 + t16 + t17 + t18 + t19 + t20 + t21 + t22 + t23 + t24 + t25 + t26 + t27 + t28 + t29 + t30 + t31 + t32 + t33 + t34 + t35 + t36 + t37 + t38 + t39 + t40 + t41 + t42 + t43 + t44 + t45 + t46 + t47 + t48 + t49 + t50 + t51 + t52 + t53 + t54 + t55 + t56 + t57 + t58 + t59 + t60 + t61 + t62 + t63 + t64 + t65 + t66 + t67 + t68 + t69 + t70 + t71 + t72 + t73 + t74 + t75 + t76 + t77 + t78 + t79 + t80 + t81 + t82 + t83 + t84 + t85 + t86 + t87 + t88 + t89 + t90 + t91 + t92 + t93 + t94 + t95 + t96 + t97 + t98 + t99 + t100;
    }
}
