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

import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
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

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION - EXP()
 *                     ---------------------
 *
 * Description:
 *  Let K = 64 (table size).
 *        x    x/log(2)     n
 *       e  = 2          = 2 * T[j] * (1 + P(y))
 *  where
 *       x = m*log(2)/K + y,    y in [-log(2)/K..log(2)/K]
 *       m = n*K + j,           m,n,j - signed integer, j in [-K/2..K/2]
 *                  j/K
 *       values of 2   are tabulated as T[j] = T_hi[j] ( 1 + T_lo[j]).
 *
 *       P(y) is a minimax polynomial approximation of exp(x)-1
 *       on small interval [-log(2)/K..log(2)/K] (were calculated by Maple V).
 *
 *  To avoid problems with arithmetic overflow and underflow,
 *            n                        n1  n2
 *  value of 2  is safely computed as 2 * 2 where n1 in [-BIAS/2..BIAS/2]
 *  where BIAS is a value of exponent bias.
 *
 * Special cases:
 *  exp(NaN) = NaN
 *  exp(+INF) = +INF
 *  exp(-INF) = 0
 *  exp(x) = 1 for subnormals
 *  for finite argument, only exp(0)=1 is exact
 *  For IEEE double
 *    if x >  709.782712893383973096 then exp(x) overflow
 *    if x < -745.133219101941108420 then exp(x) underflow
 * </pre>
 */
public final class AMD64MathExpOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathExpOp> TYPE = LIRInstructionClass.create(AMD64MathExpOp.class);

    public AMD64MathExpOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private ArrayDataPointerConstant cv = pointerConstant(16, new int[]{
            // @formatter:off
            0x652b82fe, 0x40571547, 0x652b82fe, 0x40571547,
    });
    private ArrayDataPointerConstant cv16 = pointerConstant(16, new int[]{
            0xfefa0000, 0x3f862e42, 0xfefa0000, 0x3f862e42,
    });
    private ArrayDataPointerConstant cv32 = pointerConstant(16, new int[]{
            0xbc9e3b3a, 0x3d1cf79a, 0xbc9e3b3a, 0x3d1cf79a,
    });
    private ArrayDataPointerConstant cv48 = pointerConstant(16, new int[]{
            0xfffffffe, 0x3fdfffff, 0xfffffffe, 0x3fdfffff,
    });
    private ArrayDataPointerConstant cv64 = pointerConstant(16, new int[]{
            0xe3289860, 0x3f56c15c, 0x555b9e25, 0x3fa55555,
    });
    private ArrayDataPointerConstant cv80 = pointerConstant(16, new int[]{
            0xc090cf0f, 0x3f811115, 0x55548ba1, 0x3fc55555
            // @formatter:on
    });

    private ArrayDataPointerConstant shifter = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x43380000, 0x00000000, 0x43380000
            // @formatter:on
    });

    private ArrayDataPointerConstant mmask = pointerConstant(16, new int[]{
            // @formatter:off
            0xffffffc0, 0x00000000, 0xffffffc0, 0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant bias = pointerConstant(16, new int[]{
            // @formatter:off
            0x0000ffc0, 0x00000000, 0x0000ffc0, 0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant tblAddr = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x0e03754d,
            0x3cad7bbf, 0x3e778060, 0x00002c9a, 0x3567f613, 0x3c8cd252,
            0xd3158574, 0x000059b0, 0x61e6c861, 0x3c60f74e, 0x18759bc8,
            0x00008745, 0x5d837b6c, 0x3c979aa6, 0x6cf9890f, 0x0000b558,
            0x702f9cd1, 0x3c3ebe3d, 0x32d3d1a2, 0x0000e3ec, 0x1e63bcd8,
            0x3ca3516e, 0xd0125b50, 0x00011301, 0x26f0387b, 0x3ca4c554,
            0xaea92ddf, 0x0001429a, 0x62523fb6, 0x3ca95153, 0x3c7d517a,
            0x000172b8, 0x3f1353bf, 0x3c8b898c, 0xeb6fcb75, 0x0001a35b,
            0x3e3a2f5f, 0x3c9aecf7, 0x3168b9aa, 0x0001d487, 0x44a6c38d,
            0x3c8a6f41, 0x88628cd6, 0x0002063b, 0xe3a8a894, 0x3c968efd,
            0x6e756238, 0x0002387a, 0x981fe7f2, 0x3c80472b, 0x65e27cdd,
            0x00026b45, 0x6d09ab31, 0x3c82f7e1, 0xf51fdee1, 0x00029e9d,
            0x720c0ab3, 0x3c8b3782, 0xa6e4030b, 0x0002d285, 0x4db0abb6,
            0x3c834d75, 0x0a31b715, 0x000306fe, 0x5dd3f84a, 0x3c8fdd39,
            0xb26416ff, 0x00033c08, 0xcc187d29, 0x3ca12f8c, 0x373aa9ca,
            0x000371a7, 0x738b5e8b, 0x3ca7d229, 0x34e59ff6, 0x0003a7db,
            0xa72a4c6d, 0x3c859f48, 0x4c123422, 0x0003dea6, 0x259d9205,
            0x3ca8b846, 0x21f72e29, 0x0004160a, 0x60c2ac12, 0x3c4363ed,
            0x6061892d, 0x00044e08, 0xdaa10379, 0x3c6ecce1, 0xb5c13cd0,
            0x000486a2, 0xbb7aafb0, 0x3c7690ce, 0xd5362a27, 0x0004bfda,
            0x9b282a09, 0x3ca083cc, 0x769d2ca6, 0x0004f9b2, 0xc1aae707,
            0x3ca509b0, 0x569d4f81, 0x0005342b, 0x18fdd78e, 0x3c933505,
            0x36b527da, 0x00056f47, 0xe21c5409, 0x3c9063e1, 0xdd485429,
            0x0005ab07, 0x2b64c035, 0x3c9432e6, 0x15ad2148, 0x0005e76f,
            0x99f08c0a, 0x3ca01284, 0xb03a5584, 0x0006247e, 0x0073dc06,
            0x3c99f087, 0x82552224, 0x00066238, 0x0da05571, 0x3c998d4d,
            0x667f3bcc, 0x0006a09e, 0x86ce4786, 0x3ca52bb9, 0x3c651a2e,
            0x0006dfb2, 0x206f0dab, 0x3ca32092, 0xe8ec5f73, 0x00071f75,
            0x8e17a7a6, 0x3ca06122, 0x564267c8, 0x00075feb, 0x461e9f86,
            0x3ca244ac, 0x73eb0186, 0x0007a114, 0xabd66c55, 0x3c65ebe1,
            0x36cf4e62, 0x0007e2f3, 0xbbff67d0, 0x3c96fe9f, 0x994cce12,
            0x00082589, 0x14c801df, 0x3c951f14, 0x9b4492ec, 0x000868d9,
            0xc1f0eab4, 0x3c8db72f, 0x422aa0db, 0x0008ace5, 0x59f35f44,
            0x3c7bf683, 0x99157736, 0x0008f1ae, 0x9c06283c, 0x3ca360ba,
            0xb0cdc5e4, 0x00093737, 0x20f962aa, 0x3c95e8d1, 0x9fde4e4f,
            0x00097d82, 0x2b91ce27, 0x3c71affc, 0x82a3f090, 0x0009c491,
            0x589a2ebd, 0x3c9b6d34, 0x7b5de564, 0x000a0c66, 0x9ab89880,
            0x3c95277c, 0xb23e255c, 0x000a5503, 0x6e735ab3, 0x3c846984,
            0x5579fdbf, 0x000a9e6b, 0x92cb3387, 0x3c8c1a77, 0x995ad3ad,
            0x000ae89f, 0xdc2d1d96, 0x3ca22466, 0xb84f15fa, 0x000b33a2,
            0xb19505ae, 0x3ca1112e, 0xf2fb5e46, 0x000b7f76, 0x0a5fddcd,
            0x3c74ffd7, 0x904bc1d2, 0x000bcc1e, 0x30af0cb3, 0x3c736eae,
            0xdd85529c, 0x000c199b, 0xd10959ac, 0x3c84e08f, 0x2e57d14b,
            0x000c67f1, 0x6c921968, 0x3c676b2c, 0xdcef9069, 0x000cb720,
            0x36df99b3, 0x3c937009, 0x4a07897b, 0x000d072d, 0xa63d07a7,
            0x3c74a385, 0xdcfba487, 0x000d5818, 0xd5c192ac, 0x3c8e5a50,
            0x03db3285, 0x000da9e6, 0x1c4a9792, 0x3c98bb73, 0x337b9b5e,
            0x000dfc97, 0x603a88d3, 0x3c74b604, 0xe78b3ff6, 0x000e502e,
            0x92094926, 0x3c916f27, 0xa2a490d9, 0x000ea4af, 0x41aa2008,
            0x3c8ec3bc, 0xee615a27, 0x000efa1b, 0x31d185ee, 0x3c8a64a9,
            0x5b6e4540, 0x000f5076, 0x4d91cd9d, 0x3c77893b, 0x819e90d8,
            0x000fa7c1
            // @formatter:on
    });

    private ArrayDataPointerConstant allones = pointerConstant(16, new int[]{
            // @formatter:off
            0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff
            // @formatter:on
    });

    private ArrayDataPointerConstant ebias = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3ff00000, 0x00000000, 0x3ff00000
            // @formatter:on
    });

    private ArrayDataPointerConstant xmax = pointerConstant(4, new int[]{
            // @formatter:off
            0xffffffff, 0x7fefffff
            // @formatter:on
    });

    private ArrayDataPointerConstant xmin = pointerConstant(4, new int[]{
            // @formatter:off
            0x00000000, 0x00100000
            // @formatter:on
    });

    private ArrayDataPointerConstant inf = pointerConstant(4, new int[]{
            // @formatter:off
            0x00000000, 0x7ff00000
            // @formatter:on
    });

    private ArrayDataPointerConstant zero = pointerConstant(4, new int[]{
            // @formatter:off
            0x00000000, 0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant oneVal = pointerConstant(4, new int[]{
            // @formatter:off
            0x00000000, 0x3ff00000
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // Registers:
        // input: xmm0
        // scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
        // rax, rdx, rcx, tmp - r11

        // Code generated by Intel C compiler for LIBM library
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

        masm.subq(rsp, 24);
        masm.movsd(new AMD64Address(rsp, 8), xmm0);
        masm.unpcklpd(xmm0, xmm0);
        masm.movdqu(xmm1, recordExternalAddress(crb, cv));             // 0x652b82fe, 0x40571547,
                                                                       // 0x652b82fe, 0x40571547
        masm.movdqu(xmm6, recordExternalAddress(crb, shifter));        // 0x00000000, 0x43380000,
                                                                       // 0x00000000, 0x43380000
        masm.movdqu(xmm2, recordExternalAddress(crb, cv16));           // 0xfefa0000, 0x3f862e42,
                                                                       // 0xfefa0000, 0x3f862e42
        masm.movdqu(xmm3, recordExternalAddress(crb, cv32));           // 0xbc9e3b3a, 0x3d1cf79a,
                                                                       // 0xbc9e3b3a, 0x3d1cf79a
        masm.pextrw(rax, xmm0, 3);
        masm.andl(rax, 32767);
        masm.movl(rdx, 16527);
        masm.subl(rdx, rax);
        masm.subl(rax, 15504);
        masm.orl(rdx, rax);
        masm.cmpl(rdx, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.AboveEqual, block0);
        masm.mulpd(xmm1, xmm0);
        masm.addpd(xmm1, xmm6);
        masm.movapd(xmm7, xmm1);
        masm.subpd(xmm1, xmm6);
        masm.mulpd(xmm2, xmm1);
        masm.movdqu(xmm4, recordExternalAddress(crb, cv64));           // 0xe3289860, 0x3f56c15c,
                                                                       // 0x555b9e25, 0x3fa55555
        masm.mulpd(xmm3, xmm1);
        masm.movdqu(xmm5, recordExternalAddress(crb, cv80));           // 0xc090cf0f, 0x3f811115,
                                                                       // 0x55548ba1, 0x3fc55555
        masm.subpd(xmm0, xmm2);
        masm.movdl(rax, xmm7);
        masm.movl(rcx, rax);
        masm.andl(rcx, 63);
        masm.shll(rcx, 4);
        masm.sarl(rax, 6);
        masm.movl(rdx, rax);
        masm.movdqu(xmm6, recordExternalAddress(crb, mmask));          // 0xffffffc0, 0x00000000,
                                                                       // 0xffffffc0, 0x00000000
        masm.pand(xmm7, xmm6);
        masm.movdqu(xmm6, recordExternalAddress(crb, bias));           // 0x0000ffc0, 0x00000000,
                                                                       // 0x0000ffc0, 0x00000000
        masm.paddq(xmm7, xmm6);
        masm.psllq(xmm7, 46);
        masm.subpd(xmm0, xmm3);
        masm.leaq(r11, recordExternalAddress(crb, tblAddr));
        masm.movdqu(xmm2, new AMD64Address(rcx, r11, AMD64Address.Scale.Times1));
        masm.mulpd(xmm4, xmm0);
        masm.movapd(xmm6, xmm0);
        masm.movapd(xmm1, xmm0);
        masm.mulpd(xmm6, xmm6);
        masm.mulpd(xmm0, xmm6);
        masm.addpd(xmm5, xmm4);
        masm.mulsd(xmm0, xmm6);
        masm.mulpd(xmm6, recordExternalAddress(crb, cv48));            // 0xfffffffe, 0x3fdfffff,
                                                                       // 0xfffffffe, 0x3fdfffff
        masm.addsd(xmm1, xmm2);
        masm.unpckhpd(xmm2, xmm2);
        masm.mulpd(xmm0, xmm5);
        masm.addsd(xmm1, xmm0);
        masm.por(xmm2, xmm7);
        masm.unpckhpd(xmm0, xmm0);
        masm.addsd(xmm0, xmm1);
        masm.addsd(xmm0, xmm6);
        masm.addl(rdx, 894);
        masm.cmpl(rdx, 1916);
        masm.jcc(ConditionFlag.Above, block1);
        masm.mulsd(xmm0, xmm2);
        masm.addsd(xmm0, xmm2);
        masm.jmp(block13);

        masm.bind(block1);
        masm.xorpd(xmm3, xmm3);
        masm.movdqu(xmm4, recordExternalAddress(crb, allones));        // 0xffffffff, 0xffffffff,
                                                                       // 0xffffffff, 0xffffffff
        masm.movl(rdx, -1022);
        masm.subl(rdx, rax);
        masm.movdl(xmm5, rdx);
        masm.psllq(xmm4, xmm5);
        masm.movl(rcx, rax);
        masm.sarl(rax, 1);
        masm.pinsrw(xmm3, rax, 3);
        masm.movdqu(xmm6, recordExternalAddress(crb, ebias));          // 0x00000000, 0x3ff00000,
                                                                       // 0x00000000, 0x3ff00000
        masm.psllq(xmm3, 4);
        masm.psubd(xmm2, xmm3);
        masm.mulsd(xmm0, xmm2);
        masm.cmpl(rdx, 52);
        masm.jcc(ConditionFlag.Greater, block2);
        masm.pand(xmm4, xmm2);
        masm.paddd(xmm3, xmm6);
        masm.subsd(xmm2, xmm4);
        masm.addsd(xmm0, xmm2);
        masm.cmpl(rcx, 1023);
        masm.jcc(ConditionFlag.GreaterEqual, block3);
        masm.pextrw(rcx, xmm0, 3);
        masm.andl(rcx, 32768);
        masm.orl(rdx, rcx);
        masm.cmpl(rdx, 0);
        masm.jcc(ConditionFlag.Equal, block4);
        masm.movapd(xmm6, xmm0);
        masm.addsd(xmm0, xmm4);
        masm.mulsd(xmm0, xmm3);
        masm.pextrw(rcx, xmm0, 3);
        masm.andl(rcx, 32752);
        masm.cmpl(rcx, 0);
        masm.jcc(ConditionFlag.Equal, block5);
        masm.jmp(block13);

        masm.bind(block5);
        masm.mulsd(xmm6, xmm3);
        masm.mulsd(xmm4, xmm3);
        masm.movdqu(xmm0, xmm6);
        masm.pxor(xmm6, xmm4);
        masm.psrad(xmm6, 31);
        masm.pshufd(xmm6, xmm6, 85);
        masm.psllq(xmm0, 1);
        masm.psrlq(xmm0, 1);
        masm.pxor(xmm0, xmm6);
        masm.psrlq(xmm6, 63);
        masm.paddq(xmm0, xmm6);
        masm.paddq(xmm0, xmm4);
        masm.movl(new AMD64Address(rsp, 0), 15);
        masm.jmp(block6);

        masm.bind(block4);
        masm.addsd(xmm0, xmm4);
        masm.mulsd(xmm0, xmm3);
        masm.jmp(block13);

        masm.bind(block3);
        masm.addsd(xmm0, xmm4);
        masm.mulsd(xmm0, xmm3);
        masm.pextrw(rcx, xmm0, 3);
        masm.andl(rcx, 32752);
        masm.cmpl(rcx, 32752);
        masm.jcc(ConditionFlag.AboveEqual, block7);
        masm.jmp(block13);

        masm.bind(block2);
        masm.paddd(xmm3, xmm6);
        masm.addpd(xmm0, xmm2);
        masm.mulsd(xmm0, xmm3);
        masm.movl(new AMD64Address(rsp, 0), 15);
        masm.jmp(block6);

        masm.bind(block8);
        masm.cmpl(rax, 2146435072);
        masm.jcc(ConditionFlag.AboveEqual, block9);
        masm.movl(rax, new AMD64Address(rsp, 12));
        masm.cmpl(rax, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.AboveEqual, block10);
        masm.movsd(xmm0, recordExternalAddress(crb, xmax));            // 0xffffffff, 0x7fefffff
        masm.mulsd(xmm0, xmm0);

        masm.bind(block7);
        masm.movl(new AMD64Address(rsp, 0), 14);
        masm.jmp(block6);

        masm.bind(block10);
        masm.movsd(xmm0, recordExternalAddress(crb, xmin));            // 0x00000000, 0x00100000
        masm.mulsd(xmm0, xmm0);
        masm.movl(new AMD64Address(rsp, 0), 15);
        masm.jmp(block6);

        masm.bind(block9);
        masm.movl(rdx, new AMD64Address(rsp, 8));
        masm.cmpl(rax, 2146435072);
        masm.jcc(ConditionFlag.Above, block11);
        masm.cmpl(rdx, 0);
        masm.jcc(ConditionFlag.NotEqual, block11);
        masm.movl(rax, new AMD64Address(rsp, 12));
        masm.cmpl(rax, 2146435072);
        masm.jcc(ConditionFlag.NotEqual, block12);
        masm.movsd(xmm0, recordExternalAddress(crb, inf));             // 0x00000000, 0x7ff00000
        masm.jmp(block13);

        masm.bind(block12);
        masm.movsd(xmm0, recordExternalAddress(crb, zero));            // 0x00000000, 0x00000000
        masm.jmp(block13);

        masm.bind(block11);
        masm.movsd(xmm0, new AMD64Address(rsp, 8));
        masm.addsd(xmm0, xmm0);
        masm.jmp(block13);

        masm.bind(block0);
        masm.movl(rax, new AMD64Address(rsp, 12));
        masm.andl(rax, 2147483647);
        masm.cmpl(rax, 1083179008);
        masm.jcc(ConditionFlag.AboveEqual, block8);
        masm.movsd(new AMD64Address(rsp, 8), xmm0);
        masm.addsd(xmm0, recordExternalAddress(crb, oneVal));          // 0x00000000, 0x3ff00000
        masm.jmp(block13);

        masm.bind(block6);
        masm.movq(new AMD64Address(rsp, 16), xmm0);

        masm.movq(xmm0, new AMD64Address(rsp, 16));

        masm.bind(block13);
        masm.addq(rsp, 24);
    }
}
