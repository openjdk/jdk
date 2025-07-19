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

/**
 * @test
 * @bug 8325467
 * @requires vm.opt.DeoptimizeALot == null | vm.opt.DeoptimizeALot == false
 * @summary Ensure C2 can compile methods with the maximum number of parameters
 *          (according to the JVM spec).
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.arguments.TestMaxMethodArguments::test
 *                   -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AbortVMOnCompilationFailure
 *                   compiler.arguments.TestMaxMethodArguments
 */

/**
 * @test
 * @bug 8325467
 * @summary Same test as above but do not enforce that compilation succeeds
 *          (first run) or that the test method is compiled at all (second
 *          run).
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.arguments.TestMaxMethodArguments::test
 *                   -Xcomp compiler.arguments.TestMaxMethodArguments
 * @run main compiler.arguments.TestMaxMethodArguments
 */

package compiler.arguments;

public class TestMaxMethodArguments {

    static class TestException extends Exception {}

    public static void main(String[] args) {
        try {
            test(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255);
        } catch (TestException e) {
            // Fine
        }
    }

    public static int test(int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10, int x11, int x12, int x13, int x14, int x15, int x16, int x17, int x18, int x19, int x20, int x21, int x22, int x23, int x24, int x25, int x26, int x27, int x28, int x29, int x30, int x31, int x32, int x33, int x34, int x35, int x36, int x37, int x38, int x39, int x40, int x41, int x42, int x43, int x44, int x45, int x46, int x47, int x48, int x49, int x50, int x51, int x52, int x53, int x54, int x55, int x56, int x57, int x58, int x59, int x60, int x61, int x62, int x63, int x64, int x65, int x66, int x67, int x68, int x69, int x70, int x71, int x72, int x73, int x74, int x75, int x76, int x77, int x78, int x79, int x80, int x81, int x82, int x83, int x84, int x85, int x86, int x87, int x88, int x89, int x90, int x91, int x92, int x93, int x94, int x95, int x96, int x97, int x98, int x99, int x100, int x101, int x102, int x103, int x104, int x105, int x106, int x107, int x108, int x109, int x110, int x111, int x112, int x113, int x114, int x115, int x116, int x117, int x118, int x119, int x120, int x121, int x122, int x123, int x124, int x125, int x126, int x127, int x128, int x129, int x130, int x131, int x132, int x133, int x134, int x135, int x136, int x137, int x138, int x139, int x140, int x141, int x142, int x143, int x144, int x145, int x146, int x147, int x148, int x149, int x150, int x151, int x152, int x153, int x154, int x155, int x156, int x157, int x158, int x159, int x160, int x161, int x162, int x163, int x164, int x165, int x166, int x167, int x168, int x169, int x170, int x171, int x172, int x173, int x174, int x175, int x176, int x177, int x178, int x179, int x180, int x181, int x182, int x183, int x184, int x185, int x186, int x187, int x188, int x189, int x190, int x191, int x192, int x193, int x194, int x195, int x196, int x197, int x198, int x199, int x200, int x201, int x202, int x203, int x204, int x205, int x206, int x207, int x208, int x209, int x210, int x211, int x212, int x213, int x214, int x215, int x216, int x217, int x218, int x219, int x220, int x221, int x222, int x223, int x224, int x225, int x226, int x227, int x228, int x229, int x230, int x231, int x232, int x233, int x234, int x235, int x236, int x237, int x238, int x239, int x240, int x241, int x242, int x243, int x244, int x245, int x246, int x247, int x248, int x249, int x250, int x251, int x252, int x253, int x254, int x255) throws TestException {
        // Exceptions after every definition of a temporary forces the
        // evaluation order.
        int t1 = x1 % 101; if(t1 == 0) { throw new TestException(); }
        int t2 = x2 % 102; if(t2 == 0) { throw new TestException(); }
        int t3 = x3 % 103; if(t3 == 0) { throw new TestException(); }
        int t4 = x4 % 104; if(t4 == 0) { throw new TestException(); }
        int t5 = x5 % 105; if(t5 == 0) { throw new TestException(); }
        int t6 = x6 % 106; if(t6 == 0) { throw new TestException(); }
        int t7 = x7 % 107; if(t7 == 0) { throw new TestException(); }
        int t8 = x8 % 108; if(t8 == 0) { throw new TestException(); }
        int t9 = x9 % 109; if(t9 == 0) { throw new TestException(); }
        int t10 = x10 % 110; if(t10 == 0) { throw new TestException(); }
        int t11 = x11 % 111; if(t11 == 0) { throw new TestException(); }
        int t12 = x12 % 112; if(t12 == 0) { throw new TestException(); }
        int t13 = x13 % 113; if(t13 == 0) { throw new TestException(); }
        int t14 = x14 % 114; if(t14 == 0) { throw new TestException(); }
        int t15 = x15 % 115; if(t15 == 0) { throw new TestException(); }
        int t16 = x16 % 116; if(t16 == 0) { throw new TestException(); }
        int t17 = x17 % 117; if(t17 == 0) { throw new TestException(); }
        int t18 = x18 % 118; if(t18 == 0) { throw new TestException(); }
        int t19 = x19 % 119; if(t19 == 0) { throw new TestException(); }
        int t20 = x20 % 120; if(t20 == 0) { throw new TestException(); }
        int t21 = x21 % 121; if(t21 == 0) { throw new TestException(); }
        int t22 = x22 % 122; if(t22 == 0) { throw new TestException(); }
        int t23 = x23 % 123; if(t23 == 0) { throw new TestException(); }
        int t24 = x24 % 124; if(t24 == 0) { throw new TestException(); }
        int t25 = x25 % 125; if(t25 == 0) { throw new TestException(); }
        int t26 = x26 % 126; if(t26 == 0) { throw new TestException(); }
        int t27 = x27 % 127; if(t27 == 0) { throw new TestException(); }
        int t28 = x28 % 128; if(t28 == 0) { throw new TestException(); }
        int t29 = x29 % 129; if(t29 == 0) { throw new TestException(); }
        int t30 = x30 % 130; if(t30 == 0) { throw new TestException(); }
        int t31 = x31 % 131; if(t31 == 0) { throw new TestException(); }
        int t32 = x32 % 132; if(t32 == 0) { throw new TestException(); }
        int t33 = x33 % 133; if(t33 == 0) { throw new TestException(); }
        int t34 = x34 % 134; if(t34 == 0) { throw new TestException(); }
        int t35 = x35 % 135; if(t35 == 0) { throw new TestException(); }
        int t36 = x36 % 136; if(t36 == 0) { throw new TestException(); }
        int t37 = x37 % 137; if(t37 == 0) { throw new TestException(); }
        int t38 = x38 % 138; if(t38 == 0) { throw new TestException(); }
        int t39 = x39 % 139; if(t39 == 0) { throw new TestException(); }
        int t40 = x40 % 140; if(t40 == 0) { throw new TestException(); }
        int t41 = x41 % 141; if(t41 == 0) { throw new TestException(); }
        int t42 = x42 % 142; if(t42 == 0) { throw new TestException(); }
        int t43 = x43 % 143; if(t43 == 0) { throw new TestException(); }
        int t44 = x44 % 144; if(t44 == 0) { throw new TestException(); }
        int t45 = x45 % 145; if(t45 == 0) { throw new TestException(); }
        int t46 = x46 % 146; if(t46 == 0) { throw new TestException(); }
        int t47 = x47 % 147; if(t47 == 0) { throw new TestException(); }
        int t48 = x48 % 148; if(t48 == 0) { throw new TestException(); }
        int t49 = x49 % 149; if(t49 == 0) { throw new TestException(); }
        int t50 = x50 % 150; if(t50 == 0) { throw new TestException(); }
        int t51 = x51 % 151; if(t51 == 0) { throw new TestException(); }
        int t52 = x52 % 152; if(t52 == 0) { throw new TestException(); }
        int t53 = x53 % 153; if(t53 == 0) { throw new TestException(); }
        int t54 = x54 % 154; if(t54 == 0) { throw new TestException(); }
        int t55 = x55 % 155; if(t55 == 0) { throw new TestException(); }
        int t56 = x56 % 156; if(t56 == 0) { throw new TestException(); }
        int t57 = x57 % 157; if(t57 == 0) { throw new TestException(); }
        int t58 = x58 % 158; if(t58 == 0) { throw new TestException(); }
        int t59 = x59 % 159; if(t59 == 0) { throw new TestException(); }
        int t60 = x60 % 160; if(t60 == 0) { throw new TestException(); }
        int t61 = x61 % 161; if(t61 == 0) { throw new TestException(); }
        int t62 = x62 % 162; if(t62 == 0) { throw new TestException(); }
        int t63 = x63 % 163; if(t63 == 0) { throw new TestException(); }
        int t64 = x64 % 164; if(t64 == 0) { throw new TestException(); }
        int t65 = x65 % 165; if(t65 == 0) { throw new TestException(); }
        int t66 = x66 % 166; if(t66 == 0) { throw new TestException(); }
        int t67 = x67 % 167; if(t67 == 0) { throw new TestException(); }
        int t68 = x68 % 168; if(t68 == 0) { throw new TestException(); }
        int t69 = x69 % 169; if(t69 == 0) { throw new TestException(); }
        int t70 = x70 % 170; if(t70 == 0) { throw new TestException(); }
        int t71 = x71 % 171; if(t71 == 0) { throw new TestException(); }
        int t72 = x72 % 172; if(t72 == 0) { throw new TestException(); }
        int t73 = x73 % 173; if(t73 == 0) { throw new TestException(); }
        int t74 = x74 % 174; if(t74 == 0) { throw new TestException(); }
        int t75 = x75 % 175; if(t75 == 0) { throw new TestException(); }
        int t76 = x76 % 176; if(t76 == 0) { throw new TestException(); }
        int t77 = x77 % 177; if(t77 == 0) { throw new TestException(); }
        int t78 = x78 % 178; if(t78 == 0) { throw new TestException(); }
        int t79 = x79 % 179; if(t79 == 0) { throw new TestException(); }
        int t80 = x80 % 180; if(t80 == 0) { throw new TestException(); }
        int t81 = x81 % 181; if(t81 == 0) { throw new TestException(); }
        int t82 = x82 % 182; if(t82 == 0) { throw new TestException(); }
        int t83 = x83 % 183; if(t83 == 0) { throw new TestException(); }
        int t84 = x84 % 184; if(t84 == 0) { throw new TestException(); }
        int t85 = x85 % 185; if(t85 == 0) { throw new TestException(); }
        int t86 = x86 % 186; if(t86 == 0) { throw new TestException(); }
        int t87 = x87 % 187; if(t87 == 0) { throw new TestException(); }
        int t88 = x88 % 188; if(t88 == 0) { throw new TestException(); }
        int t89 = x89 % 189; if(t89 == 0) { throw new TestException(); }
        int t90 = x90 % 190; if(t90 == 0) { throw new TestException(); }
        int t91 = x91 % 191; if(t91 == 0) { throw new TestException(); }
        int t92 = x92 % 192; if(t92 == 0) { throw new TestException(); }
        int t93 = x93 % 193; if(t93 == 0) { throw new TestException(); }
        int t94 = x94 % 194; if(t94 == 0) { throw new TestException(); }
        int t95 = x95 % 195; if(t95 == 0) { throw new TestException(); }
        int t96 = x96 % 196; if(t96 == 0) { throw new TestException(); }
        int t97 = x97 % 197; if(t97 == 0) { throw new TestException(); }
        int t98 = x98 % 198; if(t98 == 0) { throw new TestException(); }
        int t99 = x99 % 199; if(t99 == 0) { throw new TestException(); }
        int t100 = x100 % 200; if(t100 == 0) { throw new TestException(); }
        int t101 = x101 % 201; if(t101 == 0) { throw new TestException(); }
        int t102 = x102 % 202; if(t102 == 0) { throw new TestException(); }
        int t103 = x103 % 203; if(t103 == 0) { throw new TestException(); }
        int t104 = x104 % 204; if(t104 == 0) { throw new TestException(); }
        int t105 = x105 % 205; if(t105 == 0) { throw new TestException(); }
        int t106 = x106 % 206; if(t106 == 0) { throw new TestException(); }
        int t107 = x107 % 207; if(t107 == 0) { throw new TestException(); }
        int t108 = x108 % 208; if(t108 == 0) { throw new TestException(); }
        int t109 = x109 % 209; if(t109 == 0) { throw new TestException(); }
        int t110 = x110 % 210; if(t110 == 0) { throw new TestException(); }
        int t111 = x111 % 211; if(t111 == 0) { throw new TestException(); }
        int t112 = x112 % 212; if(t112 == 0) { throw new TestException(); }
        int t113 = x113 % 213; if(t113 == 0) { throw new TestException(); }
        int t114 = x114 % 214; if(t114 == 0) { throw new TestException(); }
        int t115 = x115 % 215; if(t115 == 0) { throw new TestException(); }
        int t116 = x116 % 216; if(t116 == 0) { throw new TestException(); }
        int t117 = x117 % 217; if(t117 == 0) { throw new TestException(); }
        int t118 = x118 % 218; if(t118 == 0) { throw new TestException(); }
        int t119 = x119 % 219; if(t119 == 0) { throw new TestException(); }
        int t120 = x120 % 220; if(t120 == 0) { throw new TestException(); }
        int t121 = x121 % 221; if(t121 == 0) { throw new TestException(); }
        int t122 = x122 % 222; if(t122 == 0) { throw new TestException(); }
        int t123 = x123 % 223; if(t123 == 0) { throw new TestException(); }
        int t124 = x124 % 224; if(t124 == 0) { throw new TestException(); }
        int t125 = x125 % 225; if(t125 == 0) { throw new TestException(); }
        int t126 = x126 % 226; if(t126 == 0) { throw new TestException(); }
        int t127 = x127 % 227; if(t127 == 0) { throw new TestException(); }
        int t128 = x128 % 228; if(t128 == 0) { throw new TestException(); }
        int t129 = x129 % 229; if(t129 == 0) { throw new TestException(); }
        int t130 = x130 % 230; if(t130 == 0) { throw new TestException(); }
        int t131 = x131 % 231; if(t131 == 0) { throw new TestException(); }
        int t132 = x132 % 232; if(t132 == 0) { throw new TestException(); }
        int t133 = x133 % 233; if(t133 == 0) { throw new TestException(); }
        int t134 = x134 % 234; if(t134 == 0) { throw new TestException(); }
        int t135 = x135 % 235; if(t135 == 0) { throw new TestException(); }
        int t136 = x136 % 236; if(t136 == 0) { throw new TestException(); }
        int t137 = x137 % 237; if(t137 == 0) { throw new TestException(); }
        int t138 = x138 % 238; if(t138 == 0) { throw new TestException(); }
        int t139 = x139 % 239; if(t139 == 0) { throw new TestException(); }
        int t140 = x140 % 240; if(t140 == 0) { throw new TestException(); }
        int t141 = x141 % 241; if(t141 == 0) { throw new TestException(); }
        int t142 = x142 % 242; if(t142 == 0) { throw new TestException(); }
        int t143 = x143 % 243; if(t143 == 0) { throw new TestException(); }
        int t144 = x144 % 244; if(t144 == 0) { throw new TestException(); }
        int t145 = x145 % 245; if(t145 == 0) { throw new TestException(); }
        int t146 = x146 % 246; if(t146 == 0) { throw new TestException(); }
        int t147 = x147 % 247; if(t147 == 0) { throw new TestException(); }
        int t148 = x148 % 248; if(t148 == 0) { throw new TestException(); }
        int t149 = x149 % 249; if(t149 == 0) { throw new TestException(); }
        int t150 = x150 % 250; if(t150 == 0) { throw new TestException(); }
        int t151 = x151 % 251; if(t151 == 0) { throw new TestException(); }
        int t152 = x152 % 252; if(t152 == 0) { throw new TestException(); }
        int t153 = x153 % 253; if(t153 == 0) { throw new TestException(); }
        int t154 = x154 % 254; if(t154 == 0) { throw new TestException(); }
        int t155 = x155 % 255; if(t155 == 0) { throw new TestException(); }
        int t156 = x156 % 256; if(t156 == 0) { throw new TestException(); }
        int t157 = x157 % 257; if(t157 == 0) { throw new TestException(); }
        int t158 = x158 % 258; if(t158 == 0) { throw new TestException(); }
        int t159 = x159 % 259; if(t159 == 0) { throw new TestException(); }
        int t160 = x160 % 260; if(t160 == 0) { throw new TestException(); }
        int t161 = x161 % 261; if(t161 == 0) { throw new TestException(); }
        int t162 = x162 % 262; if(t162 == 0) { throw new TestException(); }
        int t163 = x163 % 263; if(t163 == 0) { throw new TestException(); }
        int t164 = x164 % 264; if(t164 == 0) { throw new TestException(); }
        int t165 = x165 % 265; if(t165 == 0) { throw new TestException(); }
        int t166 = x166 % 266; if(t166 == 0) { throw new TestException(); }
        int t167 = x167 % 267; if(t167 == 0) { throw new TestException(); }
        int t168 = x168 % 268; if(t168 == 0) { throw new TestException(); }
        int t169 = x169 % 269; if(t169 == 0) { throw new TestException(); }
        int t170 = x170 % 270; if(t170 == 0) { throw new TestException(); }
        int t171 = x171 % 271; if(t171 == 0) { throw new TestException(); }
        int t172 = x172 % 272; if(t172 == 0) { throw new TestException(); }
        int t173 = x173 % 273; if(t173 == 0) { throw new TestException(); }
        int t174 = x174 % 274; if(t174 == 0) { throw new TestException(); }
        int t175 = x175 % 275; if(t175 == 0) { throw new TestException(); }
        int t176 = x176 % 276; if(t176 == 0) { throw new TestException(); }
        int t177 = x177 % 277; if(t177 == 0) { throw new TestException(); }
        int t178 = x178 % 278; if(t178 == 0) { throw new TestException(); }
        int t179 = x179 % 279; if(t179 == 0) { throw new TestException(); }
        int t180 = x180 % 280; if(t180 == 0) { throw new TestException(); }
        int t181 = x181 % 281; if(t181 == 0) { throw new TestException(); }
        int t182 = x182 % 282; if(t182 == 0) { throw new TestException(); }
        int t183 = x183 % 283; if(t183 == 0) { throw new TestException(); }
        int t184 = x184 % 284; if(t184 == 0) { throw new TestException(); }
        int t185 = x185 % 285; if(t185 == 0) { throw new TestException(); }
        int t186 = x186 % 286; if(t186 == 0) { throw new TestException(); }
        int t187 = x187 % 287; if(t187 == 0) { throw new TestException(); }
        int t188 = x188 % 288; if(t188 == 0) { throw new TestException(); }
        int t189 = x189 % 289; if(t189 == 0) { throw new TestException(); }
        int t190 = x190 % 290; if(t190 == 0) { throw new TestException(); }
        int t191 = x191 % 291; if(t191 == 0) { throw new TestException(); }
        int t192 = x192 % 292; if(t192 == 0) { throw new TestException(); }
        int t193 = x193 % 293; if(t193 == 0) { throw new TestException(); }
        int t194 = x194 % 294; if(t194 == 0) { throw new TestException(); }
        int t195 = x195 % 295; if(t195 == 0) { throw new TestException(); }
        int t196 = x196 % 296; if(t196 == 0) { throw new TestException(); }
        int t197 = x197 % 297; if(t197 == 0) { throw new TestException(); }
        int t198 = x198 % 298; if(t198 == 0) { throw new TestException(); }
        int t199 = x199 % 299; if(t199 == 0) { throw new TestException(); }
        int t200 = x200 % 300; if(t200 == 0) { throw new TestException(); }
        int t201 = x201 % 301; if(t201 == 0) { throw new TestException(); }
        int t202 = x202 % 302; if(t202 == 0) { throw new TestException(); }
        int t203 = x203 % 303; if(t203 == 0) { throw new TestException(); }
        int t204 = x204 % 304; if(t204 == 0) { throw new TestException(); }
        int t205 = x205 % 305; if(t205 == 0) { throw new TestException(); }
        int t206 = x206 % 306; if(t206 == 0) { throw new TestException(); }
        int t207 = x207 % 307; if(t207 == 0) { throw new TestException(); }
        int t208 = x208 % 308; if(t208 == 0) { throw new TestException(); }
        int t209 = x209 % 309; if(t209 == 0) { throw new TestException(); }
        int t210 = x210 % 310; if(t210 == 0) { throw new TestException(); }
        int t211 = x211 % 311; if(t211 == 0) { throw new TestException(); }
        int t212 = x212 % 312; if(t212 == 0) { throw new TestException(); }
        int t213 = x213 % 313; if(t213 == 0) { throw new TestException(); }
        int t214 = x214 % 314; if(t214 == 0) { throw new TestException(); }
        int t215 = x215 % 315; if(t215 == 0) { throw new TestException(); }
        int t216 = x216 % 316; if(t216 == 0) { throw new TestException(); }
        int t217 = x217 % 317; if(t217 == 0) { throw new TestException(); }
        int t218 = x218 % 318; if(t218 == 0) { throw new TestException(); }
        int t219 = x219 % 319; if(t219 == 0) { throw new TestException(); }
        int t220 = x220 % 320; if(t220 == 0) { throw new TestException(); }
        int t221 = x221 % 321; if(t221 == 0) { throw new TestException(); }
        int t222 = x222 % 322; if(t222 == 0) { throw new TestException(); }
        int t223 = x223 % 323; if(t223 == 0) { throw new TestException(); }
        int t224 = x224 % 324; if(t224 == 0) { throw new TestException(); }
        int t225 = x225 % 325; if(t225 == 0) { throw new TestException(); }
        int t226 = x226 % 326; if(t226 == 0) { throw new TestException(); }
        int t227 = x227 % 327; if(t227 == 0) { throw new TestException(); }
        int t228 = x228 % 328; if(t228 == 0) { throw new TestException(); }
        int t229 = x229 % 329; if(t229 == 0) { throw new TestException(); }
        int t230 = x230 % 330; if(t230 == 0) { throw new TestException(); }
        int t231 = x231 % 331; if(t231 == 0) { throw new TestException(); }
        int t232 = x232 % 332; if(t232 == 0) { throw new TestException(); }
        int t233 = x233 % 333; if(t233 == 0) { throw new TestException(); }
        int t234 = x234 % 334; if(t234 == 0) { throw new TestException(); }
        int t235 = x235 % 335; if(t235 == 0) { throw new TestException(); }
        int t236 = x236 % 336; if(t236 == 0) { throw new TestException(); }
        int t237 = x237 % 337; if(t237 == 0) { throw new TestException(); }
        int t238 = x238 % 338; if(t238 == 0) { throw new TestException(); }
        int t239 = x239 % 339; if(t239 == 0) { throw new TestException(); }
        int t240 = x240 % 340; if(t240 == 0) { throw new TestException(); }
        int t241 = x241 % 341; if(t241 == 0) { throw new TestException(); }
        int t242 = x242 % 342; if(t242 == 0) { throw new TestException(); }
        int t243 = x243 % 343; if(t243 == 0) { throw new TestException(); }
        int t244 = x244 % 344; if(t244 == 0) { throw new TestException(); }
        int t245 = x245 % 345; if(t245 == 0) { throw new TestException(); }
        int t246 = x246 % 346; if(t246 == 0) { throw new TestException(); }
        int t247 = x247 % 347; if(t247 == 0) { throw new TestException(); }
        int t248 = x248 % 348; if(t248 == 0) { throw new TestException(); }
        int t249 = x249 % 349; if(t249 == 0) { throw new TestException(); }
        int t250 = x250 % 350; if(t250 == 0) { throw new TestException(); }
        int t251 = x251 % 351; if(t251 == 0) { throw new TestException(); }
        int t252 = x252 % 352; if(t252 == 0) { throw new TestException(); }
        int t253 = x253 % 353; if(t253 == 0) { throw new TestException(); }
        int t254 = x254 % 354; if(t254 == 0) { throw new TestException(); }
        int t255 = x255 % 355; if(t255 == 0) { throw new TestException(); }
        // All temporaries are live here, stressing the register allocator.
        return t1 + t2 + t3 + t4 + t5 + t6 + t7 + t8 + t9 + t10 + t11 + t12 + t13 + t14 + t15 + t16 + t17 + t18 + t19 + t20 + t21 + t22 + t23 + t24 + t25 + t26 + t27 + t28 + t29 + t30 + t31 + t32 + t33 + t34 + t35 + t36 + t37 + t38 + t39 + t40 + t41 + t42 + t43 + t44 + t45 + t46 + t47 + t48 + t49 + t50 + t51 + t52 + t53 + t54 + t55 + t56 + t57 + t58 + t59 + t60 + t61 + t62 + t63 + t64 + t65 + t66 + t67 + t68 + t69 + t70 + t71 + t72 + t73 + t74 + t75 + t76 + t77 + t78 + t79 + t80 + t81 + t82 + t83 + t84 + t85 + t86 + t87 + t88 + t89 + t90 + t91 + t92 + t93 + t94 + t95 + t96 + t97 + t98 + t99 + t100 + t101 + t102 + t103 + t104 + t105 + t106 + t107 + t108 + t109 + t110 + t111 + t112 + t113 + t114 + t115 + t116 + t117 + t118 + t119 + t120 + t121 + t122 + t123 + t124 + t125 + t126 + t127 + t128 + t129 + t130 + t131 + t132 + t133 + t134 + t135 + t136 + t137 + t138 + t139 + t140 + t141 + t142 + t143 + t144 + t145 + t146 + t147 + t148 + t149 + t150 + t151 + t152 + t153 + t154 + t155 + t156 + t157 + t158 + t159 + t160 + t161 + t162 + t163 + t164 + t165 + t166 + t167 + t168 + t169 + t170 + t171 + t172 + t173 + t174 + t175 + t176 + t177 + t178 + t179 + t180 + t181 + t182 + t183 + t184 + t185 + t186 + t187 + t188 + t189 + t190 + t191 + t192 + t193 + t194 + t195 + t196 + t197 + t198 + t199 + t200 + t201 + t202 + t203 + t204 + t205 + t206 + t207 + t208 + t209 + t210 + t211 + t212 + t213 + t214 + t215 + t216 + t217 + t218 + t219 + t220 + t221 + t222 + t223 + t224 + t225 + t226 + t227 + t228 + t229 + t230 + t231 + t232 + t233 + t234 + t235 + t236 + t237 + t238 + t239 + t240 + t241 + t242 + t243 + t244 + t245 + t246 + t247 + t248 + t249 + t250 + t251 + t252 + t253 + t254 + t255;
    }
}
