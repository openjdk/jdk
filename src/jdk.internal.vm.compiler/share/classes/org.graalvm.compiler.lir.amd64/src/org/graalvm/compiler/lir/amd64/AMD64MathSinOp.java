/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, Intel Corporation. All rights reserved.
 * Intel Math Library (LIBM) Source Code
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


package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.pointerConstant;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.recordExternalAddress;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION - SIN()
 *                     ---------------------
 *
 *     1. RANGE REDUCTION
 *
 *     We perform an initial range reduction from X to r with
 *
 *          X =~= N * pi/32 + r
 *
 *     so that |r| <= pi/64 + epsilon. We restrict inputs to those
 *     where |N| <= 932560. Beyond this, the range reduction is
 *     insufficiently accurate. For extremely small inputs,
 *     denormalization can occur internally, impacting performance.
 *     This means that the main path is actually only taken for
 *     2^-252 <= |X| < 90112.
 *
 *     To avoid branches, we perform the range reduction to full
 *     accuracy each time.
 *
 *          X - N * (P_1 + P_2 + P_3)
 *
 *     where P_1 and P_2 are 32-bit numbers (so multiplication by N
 *     is exact) and P_3 is a 53-bit number. Together, these
 *     approximate pi well enough for all cases in the restricted
 *     range.
 *
 *     The main reduction sequence is:
 *
 *             y = 32/pi * x
 *             N = integer(y)
 *     (computed by adding and subtracting off SHIFTER)
 *
 *             m_1 = N * P_1
 *             m_2 = N * P_2
 *             r_1 = x - m_1
 *             r = r_1 - m_2
 *     (this r can be used for most of the calculation)
 *
 *             c_1 = r_1 - r
 *             m_3 = N * P_3
 *             c_2 = c_1 - m_2
 *             c = c_2 - m_3
 *
 *     2. MAIN ALGORITHM
 *
 *     The algorithm uses a table lookup based on B = M * pi / 32
 *     where M = N mod 64. The stored values are:
 *       sigma             closest power of 2 to cos(B)
 *       C_hl              53-bit cos(B) - sigma
 *       S_hi + S_lo       2 * 53-bit sin(B)
 *
 *     The computation is organized as follows:
 *
 *          sin(B + r + c) = [sin(B) + sigma * r] +
 *                           r * (cos(B) - sigma) +
 *                           sin(B) * [cos(r + c) - 1] +
 *                           cos(B) * [sin(r + c) - r]
 *
 *     which is approximately:
 *
 *          [S_hi + sigma * r] +
 *          C_hl * r +
 *          S_lo + S_hi * [(cos(r) - 1) - r * c] +
 *          (C_hl + sigma) * [(sin(r) - r) + c]
 *
 *     and this is what is actually computed. We separate this sum
 *     into four parts:
 *
 *          hi + med + pols + corr
 *
 *     where
 *
 *          hi       = S_hi + sigma r
 *          med      = C_hl * r
 *          pols     = S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r)
 *          corr     = S_lo + c * ((C_hl + sigma) - S_hi * r)
 *
 *     3. POLYNOMIAL
 *
 *     The polynomial S_hi * (cos(r) - 1) + (C_hl + sigma) *
 *     (sin(r) - r) can be rearranged freely, since it is quite
 *     small, so we exploit parallelism to the fullest.
 *
 *          psc4       =   SC_4 * r_1
 *          msc4       =   psc4 * r
 *          r2         =   r * r
 *          msc2       =   SC_2 * r2
 *          r4         =   r2 * r2
 *          psc3       =   SC_3 + msc4
 *          psc1       =   SC_1 + msc2
 *          msc3       =   r4 * psc3
 *          sincospols =   psc1 + msc3
 *          pols       =   sincospols *
 *                         <S_hi * r^2 | (C_hl + sigma) * r^3>
 *
 *     4. CORRECTION TERM
 *
 *     This is where the "c" component of the range reduction is
 *     taken into account; recall that just "r" is used for most of
 *     the calculation.
 *
 *          -c   = m_3 - c_2
 *          -d   = S_hi * r - (C_hl + sigma)
 *          corr = -c * -d + S_lo
 *
 *     5. COMPENSATED SUMMATIONS
 *
 *     The two successive compensated summations add up the high
 *     and medium parts, leaving just the low parts to add up at
 *     the end.
 *
 *          rs        =  sigma * r
 *          res_int   =  S_hi + rs
 *          k_0       =  S_hi - res_int
 *          k_2       =  k_0 + rs
 *          med       =  C_hl * r
 *          res_hi    =  res_int + med
 *          k_1       =  res_int - res_hi
 *          k_3       =  k_1 + med
 *
 *     6. FINAL SUMMATION
 *
 *     We now add up all the small parts:
 *
 *          res_lo = pols(hi) + pols(lo) + corr + k_1 + k_3
 *
 *     Now the overall result is just:
 *
 *          res_hi + res_lo
 *
 *     7. SMALL ARGUMENTS
 *
 *     If |x| < SNN (SNN meaning the smallest normal number), we
 *     simply perform 0.1111111 cdots 1111 * x. For SNN <= |x|, we
 *     do 2^-55 * (2^55 * x - x).
 *
 * Special cases:
 *  sin(NaN) = quiet NaN, and raise invalid exception
 *  sin(INF) = NaN and raise invalid exception
 *  sin(+/-0) = +/-0
 * </pre>
 */
public final class AMD64MathSinOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathSinOp> TYPE = LIRInstructionClass.create(AMD64MathSinOp.class);

    public AMD64MathSinOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, rbx, rsi, rdi, r8, r9, r10, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private ArrayDataPointerConstant onehalf = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3fe00000, 0x00000000, 0x3fe00000
            // @formatter:on
    });

    private ArrayDataPointerConstant p2 = pointerConstant(16, new int[]{
            // @formatter:off
            0x1a600000, 0x3d90b461, 0x1a600000, 0x3d90b461
            // @formatter:on
    });

    private ArrayDataPointerConstant sc4 = pointerConstant(16, new int[]{
            // @formatter:off
            0xa556c734, 0x3ec71de3, 0x1a01a01a, 0x3efa01a0
            // @formatter:on
    });

    private ArrayDataPointerConstant ctable = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x3ff00000, 0x176d6d31, 0xbf73b92e,
            0xbc29b42c, 0x3fb917a6, 0xe0000000, 0xbc3e2718, 0x00000000,
            0x3ff00000, 0x011469fb, 0xbf93ad06, 0x3c69a60b, 0x3fc8f8b8,
            0xc0000000, 0xbc626d19, 0x00000000, 0x3ff00000, 0x939d225a,
            0xbfa60bea, 0x2ed59f06, 0x3fd29406, 0xa0000000, 0xbc75d28d,
            0x00000000, 0x3ff00000, 0x866b95cf, 0xbfb37ca1, 0xa6aea963,
            0x3fd87de2, 0xe0000000, 0xbc672ced, 0x00000000, 0x3ff00000,
            0x73fa1279, 0xbfbe3a68, 0x3806f63b, 0x3fde2b5d, 0x20000000,
            0x3c5e0d89, 0x00000000, 0x3ff00000, 0x5bc57974, 0xbfc59267,
            0x39ae68c8, 0x3fe1c73b, 0x20000000, 0x3c8b25dd, 0x00000000,
            0x3ff00000, 0x53aba2fd, 0xbfcd0dfe, 0x25091dd6, 0x3fe44cf3,
            0x20000000, 0x3c68076a, 0x00000000, 0x3ff00000, 0x99fcef32,
            0x3fca8279, 0x667f3bcd, 0x3fe6a09e, 0x20000000, 0xbc8bdd34,
            0x00000000, 0x3fe00000, 0x94247758, 0x3fc133cc, 0x6b151741,
            0x3fe8bc80, 0x20000000, 0xbc82c5e1, 0x00000000, 0x3fe00000,
            0x9ae68c87, 0x3fac73b3, 0x290ea1a3, 0x3fea9b66, 0xe0000000,
            0x3c39f630, 0x00000000, 0x3fe00000, 0x7f909c4e, 0xbf9d4a2c,
            0xf180bdb1, 0x3fec38b2, 0x80000000, 0xbc76e0b1, 0x00000000,
            0x3fe00000, 0x65455a75, 0xbfbe0875, 0xcf328d46, 0x3fed906b,
            0x20000000, 0x3c7457e6, 0x00000000, 0x3fe00000, 0x76acf82d,
            0x3fa4a031, 0x56c62dda, 0x3fee9f41, 0xe0000000, 0x3c8760b1,
            0x00000000, 0x3fd00000, 0x0e5967d5, 0xbfac1d1f, 0xcff75cb0,
            0x3fef6297, 0x20000000, 0x3c756217, 0x00000000, 0x3fd00000,
            0x0f592f50, 0xbf9ba165, 0xa3d12526, 0x3fefd88d, 0x40000000,
            0xbc887df6, 0x00000000, 0x3fc00000, 0x00000000, 0x00000000,
            0x00000000, 0x3ff00000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x0f592f50, 0x3f9ba165, 0xa3d12526, 0x3fefd88d,
            0x40000000, 0xbc887df6, 0x00000000, 0xbfc00000, 0x0e5967d5,
            0x3fac1d1f, 0xcff75cb0, 0x3fef6297, 0x20000000, 0x3c756217,
            0x00000000, 0xbfd00000, 0x76acf82d, 0xbfa4a031, 0x56c62dda,
            0x3fee9f41, 0xe0000000, 0x3c8760b1, 0x00000000, 0xbfd00000,
            0x65455a75, 0x3fbe0875, 0xcf328d46, 0x3fed906b, 0x20000000,
            0x3c7457e6, 0x00000000, 0xbfe00000, 0x7f909c4e, 0x3f9d4a2c,
            0xf180bdb1, 0x3fec38b2, 0x80000000, 0xbc76e0b1, 0x00000000,
            0xbfe00000, 0x9ae68c87, 0xbfac73b3, 0x290ea1a3, 0x3fea9b66,
            0xe0000000, 0x3c39f630, 0x00000000, 0xbfe00000, 0x94247758,
            0xbfc133cc, 0x6b151741, 0x3fe8bc80, 0x20000000, 0xbc82c5e1,
            0x00000000, 0xbfe00000, 0x99fcef32, 0xbfca8279, 0x667f3bcd,
            0x3fe6a09e, 0x20000000, 0xbc8bdd34, 0x00000000, 0xbfe00000,
            0x53aba2fd, 0x3fcd0dfe, 0x25091dd6, 0x3fe44cf3, 0x20000000,
            0x3c68076a, 0x00000000, 0xbff00000, 0x5bc57974, 0x3fc59267,
            0x39ae68c8, 0x3fe1c73b, 0x20000000, 0x3c8b25dd, 0x00000000,
            0xbff00000, 0x73fa1279, 0x3fbe3a68, 0x3806f63b, 0x3fde2b5d,
            0x20000000, 0x3c5e0d89, 0x00000000, 0xbff00000, 0x866b95cf,
            0x3fb37ca1, 0xa6aea963, 0x3fd87de2, 0xe0000000, 0xbc672ced,
            0x00000000, 0xbff00000, 0x939d225a, 0x3fa60bea, 0x2ed59f06,
            0x3fd29406, 0xa0000000, 0xbc75d28d, 0x00000000, 0xbff00000,
            0x011469fb, 0x3f93ad06, 0x3c69a60b, 0x3fc8f8b8, 0xc0000000,
            0xbc626d19, 0x00000000, 0xbff00000, 0x176d6d31, 0x3f73b92e,
            0xbc29b42c, 0x3fb917a6, 0xe0000000, 0xbc3e2718, 0x00000000,
            0xbff00000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0xbff00000, 0x176d6d31,
            0x3f73b92e, 0xbc29b42c, 0xbfb917a6, 0xe0000000, 0x3c3e2718,
            0x00000000, 0xbff00000, 0x011469fb, 0x3f93ad06, 0x3c69a60b,
            0xbfc8f8b8, 0xc0000000, 0x3c626d19, 0x00000000, 0xbff00000,
            0x939d225a, 0x3fa60bea, 0x2ed59f06, 0xbfd29406, 0xa0000000,
            0x3c75d28d, 0x00000000, 0xbff00000, 0x866b95cf, 0x3fb37ca1,
            0xa6aea963, 0xbfd87de2, 0xe0000000, 0x3c672ced, 0x00000000,
            0xbff00000, 0x73fa1279, 0x3fbe3a68, 0x3806f63b, 0xbfde2b5d,
            0x20000000, 0xbc5e0d89, 0x00000000, 0xbff00000, 0x5bc57974,
            0x3fc59267, 0x39ae68c8, 0xbfe1c73b, 0x20000000, 0xbc8b25dd,
            0x00000000, 0xbff00000, 0x53aba2fd, 0x3fcd0dfe, 0x25091dd6,
            0xbfe44cf3, 0x20000000, 0xbc68076a, 0x00000000, 0xbff00000,
            0x99fcef32, 0xbfca8279, 0x667f3bcd, 0xbfe6a09e, 0x20000000,
            0x3c8bdd34, 0x00000000, 0xbfe00000, 0x94247758, 0xbfc133cc,
            0x6b151741, 0xbfe8bc80, 0x20000000, 0x3c82c5e1, 0x00000000,
            0xbfe00000, 0x9ae68c87, 0xbfac73b3, 0x290ea1a3, 0xbfea9b66,
            0xe0000000, 0xbc39f630, 0x00000000, 0xbfe00000, 0x7f909c4e,
            0x3f9d4a2c, 0xf180bdb1, 0xbfec38b2, 0x80000000, 0x3c76e0b1,
            0x00000000, 0xbfe00000, 0x65455a75, 0x3fbe0875, 0xcf328d46,
            0xbfed906b, 0x20000000, 0xbc7457e6, 0x00000000, 0xbfe00000,
            0x76acf82d, 0xbfa4a031, 0x56c62dda, 0xbfee9f41, 0xe0000000,
            0xbc8760b1, 0x00000000, 0xbfd00000, 0x0e5967d5, 0x3fac1d1f,
            0xcff75cb0, 0xbfef6297, 0x20000000, 0xbc756217, 0x00000000,
            0xbfd00000, 0x0f592f50, 0x3f9ba165, 0xa3d12526, 0xbfefd88d,
            0x40000000, 0x3c887df6, 0x00000000, 0xbfc00000, 0x00000000,
            0x00000000, 0x00000000, 0xbff00000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x0f592f50, 0xbf9ba165, 0xa3d12526,
            0xbfefd88d, 0x40000000, 0x3c887df6, 0x00000000, 0x3fc00000,
            0x0e5967d5, 0xbfac1d1f, 0xcff75cb0, 0xbfef6297, 0x20000000,
            0xbc756217, 0x00000000, 0x3fd00000, 0x76acf82d, 0x3fa4a031,
            0x56c62dda, 0xbfee9f41, 0xe0000000, 0xbc8760b1, 0x00000000,
            0x3fd00000, 0x65455a75, 0xbfbe0875, 0xcf328d46, 0xbfed906b,
            0x20000000, 0xbc7457e6, 0x00000000, 0x3fe00000, 0x7f909c4e,
            0xbf9d4a2c, 0xf180bdb1, 0xbfec38b2, 0x80000000, 0x3c76e0b1,
            0x00000000, 0x3fe00000, 0x9ae68c87, 0x3fac73b3, 0x290ea1a3,
            0xbfea9b66, 0xe0000000, 0xbc39f630, 0x00000000, 0x3fe00000,
            0x94247758, 0x3fc133cc, 0x6b151741, 0xbfe8bc80, 0x20000000,
            0x3c82c5e1, 0x00000000, 0x3fe00000, 0x99fcef32, 0x3fca8279,
            0x667f3bcd, 0xbfe6a09e, 0x20000000, 0x3c8bdd34, 0x00000000,
            0x3fe00000, 0x53aba2fd, 0xbfcd0dfe, 0x25091dd6, 0xbfe44cf3,
            0x20000000, 0xbc68076a, 0x00000000, 0x3ff00000, 0x5bc57974,
            0xbfc59267, 0x39ae68c8, 0xbfe1c73b, 0x20000000, 0xbc8b25dd,
            0x00000000, 0x3ff00000, 0x73fa1279, 0xbfbe3a68, 0x3806f63b,
            0xbfde2b5d, 0x20000000, 0xbc5e0d89, 0x00000000, 0x3ff00000,
            0x866b95cf, 0xbfb37ca1, 0xa6aea963, 0xbfd87de2, 0xe0000000,
            0x3c672ced, 0x00000000, 0x3ff00000, 0x939d225a, 0xbfa60bea,
            0x2ed59f06, 0xbfd29406, 0xa0000000, 0x3c75d28d, 0x00000000,
            0x3ff00000, 0x011469fb, 0xbf93ad06, 0x3c69a60b, 0xbfc8f8b8,
            0xc0000000, 0x3c626d19, 0x00000000, 0x3ff00000, 0x176d6d31,
            0xbf73b92e, 0xbc29b42c, 0xbfb917a6, 0xe0000000, 0x3c3e2718,
            0x00000000, 0x3ff00000
            // @formatter:on
    });

    private ArrayDataPointerConstant sc2 = pointerConstant(16, new int[]{
            // @formatter:off
            0x11111111, 0x3f811111, 0x55555555, 0x3fa55555
            // @formatter:on
    });

    private ArrayDataPointerConstant sc3 = pointerConstant(16, new int[]{
            // @formatter:off
            0x1a01a01a, 0xbf2a01a0, 0x16c16c17, 0xbf56c16c
            // @formatter:on
    });

    private ArrayDataPointerConstant sc1 = pointerConstant(16, new int[]{
            // @formatter:off
            0x55555555, 0xbfc55555, 0x00000000, 0xbfe00000
            // @formatter:on
    });

    private ArrayDataPointerConstant piInvTable = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x00000000, 0xa2f9836e, 0x4e441529, 0xfc2757d1,
            0xf534ddc0, 0xdb629599, 0x3c439041, 0xfe5163ab, 0xdebbc561,
            0xb7246e3a, 0x424dd2e0, 0x06492eea, 0x09d1921c, 0xfe1deb1c,
            0xb129a73e, 0xe88235f5, 0x2ebb4484, 0xe99c7026, 0xb45f7e41,
            0x3991d639, 0x835339f4, 0x9c845f8b, 0xbdf9283b, 0x1ff897ff,
            0xde05980f, 0xef2f118b, 0x5a0a6d1f, 0x6d367ecf, 0x27cb09b7,
            0x4f463f66, 0x9e5fea2d, 0x7527bac7, 0xebe5f17b, 0x3d0739f7,
            0x8a5292ea, 0x6bfb5fb1, 0x1f8d5d08, 0x56033046, 0xfc7b6bab,
            0xf0cfbc21
            // @formatter:on
    });

    private ArrayDataPointerConstant pi4 = pointerConstant(8, new int[]{
            // @formatter:off
            0x40000000, 0x3fe921fb,
    });
    private ArrayDataPointerConstant pi48 = pointerConstant(8, new int[]{
            0x18469899, 0x3e64442d
            // @formatter:on
    });

    private ArrayDataPointerConstant pi32Inv = pointerConstant(8, new int[]{
            // @formatter:off
            0x6dc9c883, 0x40245f30
            // @formatter:on
    });

    private ArrayDataPointerConstant shifter = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x43380000
            // @formatter:on
    });

    private ArrayDataPointerConstant signMask = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x80000000
            // @formatter:on
    });

    private ArrayDataPointerConstant p3 = pointerConstant(8, new int[]{
            // @formatter:off
            0x2e037073, 0x3b63198a
            // @formatter:on
    });

    private ArrayDataPointerConstant allOnes = pointerConstant(8, new int[]{
            // @formatter:off
            0xffffffff, 0x3fefffff
            // @formatter:on
    });

    private ArrayDataPointerConstant twoPow55 = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x43600000
            // @formatter:on
    });

    private ArrayDataPointerConstant twoPowM55 = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x3c800000
            // @formatter:on
    });

    private ArrayDataPointerConstant p1 = pointerConstant(8, new int[]{
            // @formatter:off
            0x54400000, 0x3fb921fb
            // @formatter:on
    });

    private ArrayDataPointerConstant negZero = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x80000000
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label block0 = new Label();
        Label block1 = new Label();
        Label block2 = new Label();
        Label block3 = new Label();
        Label block4 = new Label();
        Label block5 = new Label();
        Label block6 = new Label();
        Label block7 = new Label();
        Label block8 = new Label();
        Label block9 = new Label();
        Label block10 = new Label();
        Label block11 = new Label();
        Label block12 = new Label();
        Label block13 = new Label();
        Label block14 = new Label();

        masm.push(AMD64.rbx);
        masm.subq(rsp, 16);
        masm.movsd(new AMD64Address(rsp, 8), xmm0);
        masm.movl(rax, new AMD64Address(rsp, 12));
        masm.movq(xmm1, recordExternalAddress(crb, pi32Inv));          // 0x6dc9c883, 0x40245f30
        masm.movq(xmm2, recordExternalAddress(crb, shifter));          // 0x00000000, 0x43380000
        masm.andl(rax, 2147418112);
        masm.subl(rax, 808452096);
        masm.cmpl(rax, 281346048);
        masm.jcc(ConditionFlag.Above, block0);
        masm.mulsd(xmm1, xmm0);
        masm.movdqu(xmm5, recordExternalAddress(crb, onehalf));        // 0x00000000, 0x3fe00000,
                                                                       // 0x00000000, 0x3fe00000
        masm.movq(xmm4, recordExternalAddress(crb, signMask));         // 0x00000000, 0x80000000
        masm.pand(xmm4, xmm0);
        masm.por(xmm5, xmm4);
        masm.addpd(xmm1, xmm5);
        masm.cvttsd2sil(rdx, xmm1);
        masm.cvtsi2sdl(xmm1, rdx);
        masm.movdqu(xmm6, recordExternalAddress(crb, p2));             // 0x1a600000, 0x3d90b461,
                                                                       // 0x1a600000, 0x3d90b461
        masm.movq(r8, 0x3fb921fb54400000L);
        masm.movdq(xmm3, r8);
        masm.movdqu(xmm5, recordExternalAddress(crb, sc4));            // 0xa556c734, 0x3ec71de3,
                                                                       // 0x1a01a01a, 0x3efa01a0
        masm.pshufd(xmm4, xmm0, 68);
        masm.mulsd(xmm3, xmm1);
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm1, xmm1);
        } else {
            masm.movlhps(xmm1, xmm1);
        }
        masm.andl(rdx, 63);
        masm.shll(rdx, 5);
        masm.leaq(AMD64.rax, recordExternalAddress(crb, ctable));
        masm.addq(AMD64.rax, AMD64.rdx);
        masm.mulpd(xmm6, xmm1);
        masm.mulsd(xmm1, recordExternalAddress(crb, p3));              // 0x2e037073, 0x3b63198a
        masm.subsd(xmm4, xmm3);
        masm.movq(xmm7, new AMD64Address(AMD64.rax, 8));
        masm.subsd(xmm0, xmm3);
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm3, xmm4);
        } else {
            masm.movdqu(xmm3, xmm4);
            masm.movlhps(xmm3, xmm3);
        }
        masm.subsd(xmm4, xmm6);
        masm.pshufd(xmm0, xmm0, 68);
        masm.movdqu(xmm2, new AMD64Address(AMD64.rax, 0));
        masm.mulpd(xmm5, xmm0);
        masm.subpd(xmm0, xmm6);
        masm.mulsd(xmm7, xmm4);
        masm.subsd(xmm3, xmm4);
        masm.mulpd(xmm5, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.subsd(xmm3, xmm6);
        masm.movdqu(xmm6, recordExternalAddress(crb, sc2));            // 0x11111111, 0x3f811111,
                                                                       // 0x55555555, 0x3fa55555
        masm.subsd(xmm1, xmm3);
        masm.movq(xmm3, new AMD64Address(AMD64.rax, 24));
        masm.addsd(xmm2, xmm3);
        masm.subsd(xmm7, xmm2);
        masm.mulsd(xmm2, xmm4);
        masm.mulpd(xmm6, xmm0);
        masm.mulsd(xmm3, xmm4);
        masm.mulpd(xmm2, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm5, recordExternalAddress(crb, sc3));             // 0x1a01a01a, 0xbf2a01a0,
                                                                       // 0x16c16c17, 0xbf56c16c
        masm.mulsd(xmm4, new AMD64Address(AMD64.rax, 0));
        masm.addpd(xmm6, recordExternalAddress(crb, sc1));             // 0x55555555, 0xbfc55555,
                                                                       // 0x00000000, 0xbfe00000
        masm.mulpd(xmm5, xmm0);
        masm.movdqu(xmm0, xmm3);
        masm.addsd(xmm3, new AMD64Address(AMD64.rax, 8));
        masm.mulpd(xmm1, xmm7);
        masm.movdqu(xmm7, xmm4);
        masm.addsd(xmm4, xmm3);
        masm.addpd(xmm6, xmm5);
        masm.movq(xmm5, new AMD64Address(AMD64.rax, 8));
        masm.subsd(xmm5, xmm3);
        masm.subsd(xmm3, xmm4);
        masm.addsd(xmm1, new AMD64Address(AMD64.rax, 16));
        masm.mulpd(xmm6, xmm2);
        masm.addsd(xmm5, xmm0);
        masm.addsd(xmm3, xmm7);
        masm.addsd(xmm1, xmm5);
        masm.addsd(xmm1, xmm3);
        masm.addsd(xmm1, xmm6);
        masm.unpckhpd(xmm6, xmm6);
        masm.movdqu(xmm0, xmm4);
        masm.addsd(xmm1, xmm6);
        masm.addsd(xmm0, xmm1);
        masm.jmp(block14);

        masm.bind(block0);
        masm.jcc(ConditionFlag.Greater, block1);
        masm.shrl(rax, 20);
        masm.cmpl(rax, 3325);
        masm.jcc(ConditionFlag.NotEqual, block2);
        masm.mulsd(xmm0, recordExternalAddress(crb, allOnes));         // 0xffffffff, 0x3fefffff
        masm.jmp(block14);

        masm.bind(block2);
        masm.movq(xmm3, recordExternalAddress(crb, twoPow55));         // 0x00000000, 0x43600000
        masm.mulsd(xmm3, xmm0);
        masm.subsd(xmm3, xmm0);
        masm.mulsd(xmm3, recordExternalAddress(crb, twoPowM55));       // 0x00000000, 0x3c800000
        masm.jmp(block14);

        masm.bind(block1);
        masm.pextrw(rax, xmm0, 3);
        masm.andl(rax, 32752);
        masm.cmpl(rax, 32752);
        masm.jcc(ConditionFlag.Equal, block3);
        masm.pextrw(rcx, xmm0, 3);
        masm.andl(rcx, 32752);
        masm.subl(rcx, 16224);
        masm.shrl(rcx, 7);
        masm.andl(rcx, 65532);
        masm.leaq(r11, recordExternalAddress(crb, piInvTable));
        masm.addq(AMD64.rcx, r11);
        masm.movdq(AMD64.rax, xmm0);
        masm.movl(r10, new AMD64Address(AMD64.rcx, 20));
        masm.movl(r8, new AMD64Address(AMD64.rcx, 24));
        masm.movl(rdx, rax);
        masm.shrq(AMD64.rax, 21);
        masm.orl(rax, Integer.MIN_VALUE);
        masm.shrl(rax, 11);
        masm.movl(r9, r10);
        masm.imulq(r10, AMD64.rdx);
        masm.imulq(r9, AMD64.rax);
        masm.imulq(r8, AMD64.rax);
        masm.movl(rsi, new AMD64Address(AMD64.rcx, 16));
        masm.movl(rdi, new AMD64Address(AMD64.rcx, 12));
        masm.movl(r11, r10);
        masm.shrq(r10, 32);
        masm.addq(r9, r10);
        masm.addq(r11, r8);
        masm.movl(r8, r11);
        masm.shrq(r11, 32);
        masm.addq(r9, r11);
        masm.movl(r10, rsi);
        masm.imulq(rsi, AMD64.rdx);
        masm.imulq(r10, AMD64.rax);
        masm.movl(r11, rdi);
        masm.imulq(rdi, AMD64.rdx);
        masm.movl(rbx, rsi);
        masm.shrq(rsi, 32);
        masm.addq(r9, AMD64.rbx);
        masm.movl(rbx, r9);
        masm.shrq(r9, 32);
        masm.addq(r10, rsi);
        masm.addq(r10, r9);
        masm.shlq(AMD64.rbx, 32);
        masm.orq(r8, AMD64.rbx);
        masm.imulq(r11, AMD64.rax);
        masm.movl(r9, new AMD64Address(AMD64.rcx, 8));
        masm.movl(rsi, new AMD64Address(AMD64.rcx, 4));
        masm.movl(rbx, rdi);
        masm.shrq(rdi, 32);
        masm.addq(r10, AMD64.rbx);
        masm.movl(rbx, r10);
        masm.shrq(r10, 32);
        masm.addq(r11, rdi);
        masm.addq(r11, r10);
        masm.movq(rdi, r9);
        masm.imulq(r9, AMD64.rdx);
        masm.imulq(rdi, AMD64.rax);
        masm.movl(r10, r9);
        masm.shrq(r9, 32);
        masm.addq(r11, r10);
        masm.movl(r10, r11);
        masm.shrq(r11, 32);
        masm.addq(rdi, r9);
        masm.addq(rdi, r11);
        masm.movq(r9, rsi);
        masm.imulq(rsi, AMD64.rdx);
        masm.imulq(r9, AMD64.rax);
        masm.shlq(r10, 32);
        masm.orq(r10, AMD64.rbx);
        masm.movl(rax, new AMD64Address(AMD64.rcx, 0));
        masm.movl(r11, rsi);
        masm.shrq(rsi, 32);
        masm.addq(rdi, r11);
        masm.movl(r11, rdi);
        masm.shrq(rdi, 32);
        masm.addq(r9, rsi);
        masm.addq(r9, rdi);
        masm.imulq(AMD64.rdx, AMD64.rax);
        masm.pextrw(rbx, xmm0, 3);
        masm.leaq(rdi, recordExternalAddress(crb, piInvTable));
        masm.subq(AMD64.rcx, rdi);
        masm.addl(rcx, rcx);
        masm.addl(rcx, rcx);
        masm.addl(rcx, rcx);
        masm.addl(rcx, 19);
        masm.movl(rsi, 32768);
        masm.andl(rsi, rbx);
        masm.shrl(rbx, 4);
        masm.andl(rbx, 2047);
        masm.subl(rbx, 1023);
        masm.subl(rcx, rbx);
        masm.addq(r9, AMD64.rdx);
        masm.movl(rdx, rcx);
        masm.addl(rdx, 32);
        masm.cmpl(rcx, 1);
        masm.jcc(ConditionFlag.Less, block4);
        masm.negl(rcx);
        masm.addl(rcx, 29);
        masm.shll(r9);
        masm.movl(rdi, r9);
        masm.andl(r9, 536870911);
        masm.testl(r9, 268435456);
        masm.jcc(ConditionFlag.NotEqual, block5);
        masm.shrl(r9);
        masm.movl(rbx, 0);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);

        masm.bind(block6);

        masm.bind(block7);

        masm.cmpq(r9, 0);
        masm.jcc(ConditionFlag.Equal, block8);

        masm.bind(block9);
        masm.bsrq(r11, r9);
        masm.movl(rcx, 29);
        masm.subl(rcx, r11);
        masm.jcc(ConditionFlag.LessEqual, block10);
        masm.shlq(r9);
        masm.movq(AMD64.rax, r10);
        masm.shlq(r10);
        masm.addl(rdx, rcx);
        masm.negl(rcx);
        masm.addl(rcx, 64);
        masm.shrq(AMD64.rax);
        masm.shrq(r8);
        masm.orq(r9, AMD64.rax);
        masm.orq(r10, r8);

        masm.bind(block11);
        masm.cvtsi2sdq(xmm0, r9);
        masm.shrq(r10, 1);
        masm.cvtsi2sdq(xmm3, r10);
        masm.xorpd(xmm4, xmm4);
        masm.shll(rdx, 4);
        masm.negl(rdx);
        masm.addl(rdx, 16368);
        masm.orl(rdx, rsi);
        masm.xorl(rdx, rbx);
        masm.pinsrw(xmm4, rdx, 3);
        masm.movq(xmm2, recordExternalAddress(crb, pi4));              // 0x40000000, 0x3fe921fb,
                                                                       // 0x18469899, 0x3e64442d
        masm.movq(xmm6, recordExternalAddress(crb, pi48));             // 0x3fe921fb, 0x18469899,
                                                                       // 0x3e64442d
        masm.xorpd(xmm5, xmm5);
        masm.subl(rdx, 1008);
        masm.pinsrw(xmm5, rdx, 3);
        masm.mulsd(xmm0, xmm4);
        masm.shll(rsi, 16);
        masm.sarl(rsi, 31);
        masm.mulsd(xmm3, xmm5);
        masm.movdqu(xmm1, xmm0);
        masm.mulsd(xmm0, xmm2);
        masm.shrl(rdi, 29);
        masm.addsd(xmm1, xmm3);
        masm.mulsd(xmm3, xmm2);
        masm.addl(rdi, rsi);
        masm.xorl(rdi, rsi);
        masm.mulsd(xmm6, xmm1);
        masm.movl(rax, rdi);
        masm.addsd(xmm6, xmm3);
        masm.movdqu(xmm2, xmm0);
        masm.addsd(xmm0, xmm6);
        masm.subsd(xmm2, xmm0);
        masm.addsd(xmm6, xmm2);

        masm.bind(block12);
        masm.movq(xmm1, recordExternalAddress(crb, pi32Inv));          // 0x6dc9c883, 0x40245f30
        masm.mulsd(xmm1, xmm0);
        masm.movq(xmm5, recordExternalAddress(crb, onehalf));          // 0x00000000, 0x3fe00000,
                                                                       // 0x00000000, 0x3fe00000
        masm.movq(xmm4, recordExternalAddress(crb, signMask));         // 0x00000000, 0x80000000
        masm.pand(xmm4, xmm0);
        masm.por(xmm5, xmm4);
        masm.addpd(xmm1, xmm5);
        masm.cvttsd2sil(rdx, xmm1);
        masm.cvtsi2sdl(xmm1, rdx);
        masm.movq(xmm3, recordExternalAddress(crb, p1));               // 0x54400000, 0x3fb921fb
        masm.movdqu(xmm2, recordExternalAddress(crb, p2));             // 0x1a600000, 0x3d90b461,
                                                                       // 0x1a600000, 0x3d90b461
        masm.mulsd(xmm3, xmm1);
        masm.unpcklpd(xmm1, xmm1);
        masm.shll(rax, 3);
        masm.addl(rdx, 1865216);
        masm.movdqu(xmm4, xmm0);
        masm.addl(rdx, rax);
        masm.andl(rdx, 63);
        masm.movdqu(xmm5, recordExternalAddress(crb, sc4));            // 0x54400000, 0x3fb921fb
        masm.leaq(AMD64.rax, recordExternalAddress(crb, ctable));
        masm.shll(rdx, 5);
        masm.addq(AMD64.rax, AMD64.rdx);
        masm.mulpd(xmm2, xmm1);
        masm.subsd(xmm0, xmm3);
        masm.mulsd(xmm1, recordExternalAddress(crb, p3));              // 0x2e037073, 0x3b63198a
        masm.subsd(xmm4, xmm3);
        masm.movq(xmm7, new AMD64Address(AMD64.rax, 8));
        masm.unpcklpd(xmm0, xmm0);
        masm.movdqu(xmm3, xmm4);
        masm.subsd(xmm4, xmm2);
        masm.mulpd(xmm5, xmm0);
        masm.subpd(xmm0, xmm2);
        masm.mulsd(xmm7, xmm4);
        masm.subsd(xmm3, xmm4);
        masm.mulpd(xmm5, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.subsd(xmm3, xmm2);
        masm.movdqu(xmm2, new AMD64Address(AMD64.rax, 0));
        masm.subsd(xmm1, xmm3);
        masm.movq(xmm3, new AMD64Address(AMD64.rax, 24));
        masm.addsd(xmm2, xmm3);
        masm.subsd(xmm7, xmm2);
        masm.subsd(xmm1, xmm6);
        masm.movdqu(xmm6, recordExternalAddress(crb, sc2));            // 0x11111111, 0x3f811111,
                                                                       // 0x55555555, 0x3fa55555
        masm.mulsd(xmm2, xmm4);
        masm.mulpd(xmm6, xmm0);
        masm.mulsd(xmm3, xmm4);
        masm.mulpd(xmm2, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm5, recordExternalAddress(crb, sc3));             // 0x1a01a01a, 0xbf2a01a0,
                                                                       // 0x16c16c17, 0xbf56c16c
        masm.mulsd(xmm4, new AMD64Address(AMD64.rax, 0));
        masm.addpd(xmm6, recordExternalAddress(crb, sc1));             // 0x55555555, 0xbfc55555,
                                                                       // 0x00000000, 0xbfe00000
        masm.mulpd(xmm5, xmm0);
        masm.movdqu(xmm0, xmm3);
        masm.addsd(xmm3, new AMD64Address(AMD64.rax, 8));
        masm.mulpd(xmm1, xmm7);
        masm.movdqu(xmm7, xmm4);
        masm.addsd(xmm4, xmm3);
        masm.addpd(xmm6, xmm5);
        masm.movq(xmm5, new AMD64Address(AMD64.rax, 8));
        masm.subsd(xmm5, xmm3);
        masm.subsd(xmm3, xmm4);
        masm.addsd(xmm1, new AMD64Address(AMD64.rax, 16));
        masm.mulpd(xmm6, xmm2);
        masm.addsd(xmm5, xmm0);
        masm.addsd(xmm3, xmm7);
        masm.addsd(xmm1, xmm5);
        masm.addsd(xmm1, xmm3);
        masm.addsd(xmm1, xmm6);
        masm.unpckhpd(xmm6, xmm6);
        masm.movdqu(xmm0, xmm4);
        masm.addsd(xmm1, xmm6);
        masm.addsd(xmm0, xmm1);
        masm.jmp(block14);

        masm.bind(block8);
        masm.addl(rdx, 64);
        masm.movq(r9, r10);
        masm.movq(r10, r8);
        masm.movl(r8, 0);
        masm.cmpq(r9, 0);
        masm.jcc(ConditionFlag.NotEqual, block9);
        masm.addl(rdx, 64);
        masm.movq(r9, r10);
        masm.movq(r10, r8);
        masm.cmpq(r9, 0);
        masm.jcc(ConditionFlag.NotEqual, block9);
        masm.xorpd(xmm0, xmm0);
        masm.xorpd(xmm6, xmm6);
        masm.jmp(block12);

        masm.bind(block10);
        masm.jcc(ConditionFlag.Equal, block11);
        masm.negl(rcx);
        masm.shrq(r10);
        masm.movq(AMD64.rax, r9);
        masm.shrq(r9);
        masm.subl(rdx, rcx);
        masm.negl(rcx);
        masm.addl(rcx, 64);
        masm.shlq(AMD64.rax);
        masm.orq(r10, AMD64.rax);
        masm.jmp(block11);

        masm.bind(block4);
        masm.negl(rcx);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);
        masm.shlq(r9);
        masm.movq(rdi, r9);
        masm.testl(r9, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotEqual, block13);
        masm.shrl(r9);
        masm.movl(rbx, 0);
        masm.shrq(rdi, 3);
        masm.jmp(block7);

        masm.bind(block5);
        masm.shrl(r9);
        masm.movl(rbx, 536870912);
        masm.shrl(rbx);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);
        masm.shlq(AMD64.rbx, 32);
        masm.addl(rdi, 536870912);
        masm.movl(AMD64.rcx, 0);
        masm.movl(r11, 0);
        masm.subq(AMD64.rcx, r8);
        masm.sbbq(r11, r10);
        masm.sbbq(AMD64.rbx, r9);
        masm.movq(r8, AMD64.rcx);
        masm.movq(r10, r11);
        masm.movq(r9, AMD64.rbx);
        masm.movl(rbx, 32768);
        masm.jmp(block6);

        masm.bind(block13);
        masm.shrl(r9);
        masm.movq(AMD64.rbx, 0x100000000L);
        masm.shrq(AMD64.rbx);
        masm.movl(AMD64.rcx, 0);
        masm.movl(r11, 0);
        masm.subq(AMD64.rcx, r8);
        masm.sbbq(r11, r10);
        masm.sbbq(AMD64.rbx, r9);
        masm.movq(r8, AMD64.rcx);
        masm.movq(r10, r11);
        masm.movq(r9, AMD64.rbx);
        masm.movl(rbx, 32768);
        masm.shrq(rdi, 3);
        masm.addl(rdi, 536870912);
        masm.jmp(block7);

        masm.bind(block3);
        masm.movq(xmm0, new AMD64Address(rsp, 8));
        masm.mulsd(xmm0, recordExternalAddress(crb, negZero));         // 0x00000000, 0x80000000
        masm.movq(new AMD64Address(rsp, 0), xmm0);

        masm.bind(block14);
        masm.addq(rsp, 16);
        masm.pop(AMD64.rbx);
    }
}
