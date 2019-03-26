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
import static jdk.vm.ci.amd64.AMD64.r8;
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
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION - LOG10()
 *                     ---------------------
 *
 *    Let x=2^k * mx, mx in [1,2)
 *
 *    Get B~1/mx based on the output of rcpss instruction (B0)
 *    B = int((B0*LH*2^7+0.5))/2^7
 *    LH is a short approximation for log10(e)
 *
 *    Reduced argument: r=B*mx-LH (computed accurately in high and low parts)
 *
 *    Result:  k*log10(2) - log(B) + p(r)
 *             p(r) is a degree 7 polynomial
 *             -log(B) read from data table (high, low parts)
 *             Result is formed from high and low parts.
 *
 * Special cases:
 *  log10(0) = -INF with divide-by-zero exception raised
 *  log10(1) = +0
 *  log10(x) = NaN with invalid exception raised if x < -0, including -INF
 *  log10(+INF) = +INF
 * </pre>
 */
public final class AMD64MathLog10Op extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathLog10Op> TYPE = LIRInstructionClass.create(AMD64MathLog10Op.class);

    public AMD64MathLog10Op() {
        super(TYPE, /* GPR */ rax, rcx, rdx, r8, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private ArrayDataPointerConstant highsigmask = pointerConstant(16, new int[]{
            // @formatter:off
            0xf8000000, 0xffffffff, 0x00000000, 0xffffe000
            // @formatter:on
    });

    private ArrayDataPointerConstant log10E = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x3fdbc000,
    });
    private ArrayDataPointerConstant log10E8 = pointerConstant(8, new int[]{
            0xbf2e4108, 0x3f5a7a6c
            // @formatter:on
    });

    private ArrayDataPointerConstant lTbl = pointerConstant(16, new int[]{
            // @formatter:off
            0x509f7800, 0x3fd34413, 0x1f12b358, 0x3d1fef31, 0x80333400,
            0x3fd32418, 0xc671d9d0, 0xbcf542bf, 0x51195000, 0x3fd30442,
            0x78a4b0c3, 0x3d18216a, 0x6fc79400, 0x3fd2e490, 0x80fa389d,
            0xbc902869, 0x89d04000, 0x3fd2c502, 0x75c2f564, 0x3d040754,
            0x4ddd1c00, 0x3fd2a598, 0xd219b2c3, 0xbcfa1d84, 0x6baa7c00,
            0x3fd28651, 0xfd9abec1, 0x3d1be6d3, 0x94028800, 0x3fd2672d,
            0xe289a455, 0xbd1ede5e, 0x78b86400, 0x3fd2482c, 0x6734d179,
            0x3d1fe79b, 0xcca3c800, 0x3fd2294d, 0x981a40b8, 0xbced34ea,
            0x439c5000, 0x3fd20a91, 0xcc392737, 0xbd1a9cc3, 0x92752c00,
            0x3fd1ebf6, 0x03c9afe7, 0x3d1e98f8, 0x6ef8dc00, 0x3fd1cd7d,
            0x71dae7f4, 0x3d08a86c, 0x8fe4dc00, 0x3fd1af25, 0xee9185a1,
            0xbcff3412, 0xace59400, 0x3fd190ee, 0xc2cab353, 0x3cf17ed9,
            0x7e925000, 0x3fd172d8, 0x6952c1b2, 0x3cf1521c, 0xbe694400,
            0x3fd154e2, 0xcacb79ca, 0xbd0bdc78, 0x26cbac00, 0x3fd1370d,
            0xf71f4de1, 0xbd01f8be, 0x72fa0800, 0x3fd11957, 0x55bf910b,
            0x3c946e2b, 0x5f106000, 0x3fd0fbc1, 0x39e639c1, 0x3d14a84b,
            0xa802a800, 0x3fd0de4a, 0xd3f31d5d, 0xbd178385, 0x0b992000,
            0x3fd0c0f3, 0x3843106f, 0xbd1f602f, 0x486ce800, 0x3fd0a3ba,
            0x8819497c, 0x3cef987a, 0x1de49400, 0x3fd086a0, 0x1caa0467,
            0x3d0faec7, 0x4c30cc00, 0x3fd069a4, 0xa4424372, 0xbd1618fc,
            0x94490000, 0x3fd04cc6, 0x946517d2, 0xbd18384b, 0xb7e84000,
            0x3fd03006, 0xe0109c37, 0xbd19a6ac, 0x798a0c00, 0x3fd01364,
            0x5121e864, 0xbd164cf7, 0x38ce8000, 0x3fcfedbf, 0x46214d1a,
            0xbcbbc402, 0xc8e62000, 0x3fcfb4ef, 0xdab93203, 0x3d1e0176,
            0x2cb02800, 0x3fcf7c5a, 0x2a2ea8e4, 0xbcfec86a, 0xeeeaa000,
            0x3fcf43fd, 0xc18e49a4, 0x3cf110a8, 0x9bb6e800, 0x3fcf0bda,
            0x923cc9c0, 0xbd15ce99, 0xc093f000, 0x3fced3ef, 0x4d4b51e9,
            0x3d1a04c7, 0xec58f800, 0x3fce9c3c, 0x163cad59, 0x3cac8260,
            0x9a907000, 0x3fce2d7d, 0x3fa93646, 0x3ce4a1c0, 0x37311000,
            0x3fcdbf99, 0x32abd1fd, 0x3d07ea9d, 0x6744b800, 0x3fcd528c,
            0x4dcbdfd4, 0xbd1b08e2, 0xe36de800, 0x3fcce653, 0x0b7b7f7f,
            0xbd1b8f03, 0x77506800, 0x3fcc7aec, 0xa821c9fb, 0x3d13c163,
            0x00ff8800, 0x3fcc1053, 0x536bca76, 0xbd074ee5, 0x70719800,
            0x3fcba684, 0xd7da9b6b, 0xbd1fbf16, 0xc6f8d800, 0x3fcb3d7d,
            0xe2220bb3, 0x3d1a295d, 0x16c15800, 0x3fcad53c, 0xe724911e,
            0xbcf55822, 0x82533800, 0x3fca6dbc, 0x6d982371, 0x3cac567c,
            0x3c19e800, 0x3fca06fc, 0x84d17d80, 0x3d1da204, 0x85ef8000,
            0x3fc9a0f8, 0x54466a6a, 0xbd002204, 0xb0ac2000, 0x3fc93bae,
            0xd601fd65, 0x3d18840c, 0x1bb9b000, 0x3fc8d71c, 0x7bf58766,
            0xbd14f897, 0x34aae800, 0x3fc8733e, 0x3af6ac24, 0xbd0f5c45,
            0x76d68000, 0x3fc81012, 0x4303e1a1, 0xbd1f9a80, 0x6af57800,
            0x3fc7ad96, 0x43fbcb46, 0x3cf4c33e, 0xa6c51000, 0x3fc74bc7,
            0x70f0eac5, 0xbd192e3b, 0xccab9800, 0x3fc6eaa3, 0xc0093dfe,
            0xbd0faf15, 0x8b60b800, 0x3fc68a28, 0xde78d5fd, 0xbc9ea4ee,
            0x9d987000, 0x3fc62a53, 0x962bea6e, 0xbd194084, 0xc9b0e800,
            0x3fc5cb22, 0x888dd999, 0x3d1fe201, 0xe1634800, 0x3fc56c93,
            0x16ada7ad, 0x3d1b1188, 0xc176c000, 0x3fc50ea4, 0x4159b5b5,
            0xbcf09c08, 0x51766000, 0x3fc4b153, 0x84393d23, 0xbcf6a89c,
            0x83695000, 0x3fc4549d, 0x9f0b8bbb, 0x3d1c4b8c, 0x538d5800,
            0x3fc3f881, 0xf49df747, 0x3cf89b99, 0xc8138000, 0x3fc39cfc,
            0xd503b834, 0xbd13b99f, 0xf0df0800, 0x3fc3420d, 0xf011b386,
            0xbd05d8be, 0xe7466800, 0x3fc2e7b2, 0xf39c7bc2, 0xbd1bb94e,
            0xcdd62800, 0x3fc28de9, 0x05e6d69b, 0xbd10ed05, 0xd015d800,
            0x3fc234b0, 0xe29b6c9d, 0xbd1ff967, 0x224ea800, 0x3fc1dc06,
            0x727711fc, 0xbcffb30d, 0x01540000, 0x3fc183e8, 0x39786c5a,
            0x3cc23f57, 0xb24d9800, 0x3fc12c54, 0xc905a342, 0x3d003a1d,
            0x82835800, 0x3fc0d54a, 0x9b9920c0, 0x3d03b25a, 0xc72ac000,
            0x3fc07ec7, 0x46f26a24, 0x3cf0fa41, 0xdd35d800, 0x3fc028ca,
            0x41d9d6dc, 0x3d034a65, 0x52474000, 0x3fbfa6a4, 0x44f66449,
            0x3d19cad3, 0x2da3d000, 0x3fbefcb8, 0x67832999, 0x3d18400f,
            0x32a10000, 0x3fbe53ce, 0x9c0e3b1a, 0xbcff62fd, 0x556b7000,
            0x3fbdabe3, 0x02976913, 0xbcf8243b, 0x97e88000, 0x3fbd04f4,
            0xec793797, 0x3d1c0578, 0x09647000, 0x3fbc5eff, 0x05fc0565,
            0xbd1d799e, 0xc6426000, 0x3fbbb9ff, 0x4625f5ed, 0x3d1f5723,
            0xf7afd000, 0x3fbb15f3, 0xdd5aae61, 0xbd1a7e1e, 0xd358b000,
            0x3fba72d8, 0x3314e4d3, 0x3d17bc91, 0x9b1f5000, 0x3fb9d0ab,
            0x9a4d514b, 0x3cf18c9b, 0x9cd4e000, 0x3fb92f69, 0x7e4496ab,
            0x3cf1f96d, 0x31f4f000, 0x3fb88f10, 0xf56479e7, 0x3d165818,
            0xbf628000, 0x3fb7ef9c, 0x26bf486d, 0xbd1113a6, 0xb526b000,
            0x3fb7510c, 0x1a1c3384, 0x3ca9898d, 0x8e31e000, 0x3fb6b35d,
            0xb3875361, 0xbd0661ac, 0xd01de000, 0x3fb6168c, 0x2a7cacfa,
            0xbd1bdf10, 0x0af23000, 0x3fb57a98, 0xff868816, 0x3cf046d0,
            0xd8ea0000, 0x3fb4df7c, 0x1515fbe7, 0xbd1fd529, 0xde3b2000,
            0x3fb44538, 0x6e59a132, 0x3d1faeee, 0xc8df9000, 0x3fb3abc9,
            0xf1322361, 0xbd198807, 0x505f1000, 0x3fb3132d, 0x0888e6ab,
            0x3d1e5380, 0x359bd000, 0x3fb27b61, 0xdfbcbb22, 0xbcfe2724,
            0x429ee000, 0x3fb1e463, 0x6eb4c58c, 0xbcfe4dd6, 0x4a673000,
            0x3fb14e31, 0x4ce1ac9b, 0x3d1ba691, 0x28b96000, 0x3fb0b8c9,
            0x8c7813b8, 0xbd0b3872, 0xc1f08000, 0x3fb02428, 0xc2bc8c2c,
            0x3cb5ea6b, 0x05a1a000, 0x3faf209c, 0x72e8f18e, 0xbce8df84,
            0xc0b5e000, 0x3fadfa6d, 0x9fdef436, 0x3d087364, 0xaf416000,
            0x3facd5c2, 0x1068c3a9, 0x3d0827e7, 0xdb356000, 0x3fabb296,
            0x120a34d3, 0x3d101a9f, 0x5dfea000, 0x3faa90e6, 0xdaded264,
            0xbd14c392, 0x6034c000, 0x3fa970ad, 0x1c9d06a9, 0xbd1b705e,
            0x194c6000, 0x3fa851e8, 0x83996ad9, 0xbd0117bc, 0xcf4ac000,
            0x3fa73492, 0xb1a94a62, 0xbca5ea42, 0xd67b4000, 0x3fa618a9,
            0x75aed8ca, 0xbd07119b, 0x9126c000, 0x3fa4fe29, 0x5291d533,
            0x3d12658f, 0x6f4d4000, 0x3fa3e50e, 0xcd2c5cd9, 0x3d1d5c70,
            0xee608000, 0x3fa2cd54, 0xd1008489, 0x3d1a4802, 0x9900e000,
            0x3fa1b6f9, 0x54fb5598, 0xbd16593f, 0x06bb6000, 0x3fa0a1f9,
            0x64ef57b4, 0xbd17636b, 0xb7940000, 0x3f9f1c9f, 0xee6a4737,
            0x3cb5d479, 0x91aa0000, 0x3f9cf7f5, 0x3a16373c, 0x3d087114,
            0x156b8000, 0x3f9ad5ed, 0x836c554a, 0x3c6900b0, 0xd4764000,
            0x3f98b67f, 0xed12f17b, 0xbcffc974, 0x77dec000, 0x3f9699a7,
            0x232ce7ea, 0x3d1e35bb, 0xbfbf4000, 0x3f947f5d, 0xd84ffa6e,
            0x3d0e0a49, 0x82c7c000, 0x3f92679c, 0x8d170e90, 0xbd14d9f2,
            0xadd20000, 0x3f90525d, 0x86d9f88e, 0x3cdeb986, 0x86f10000,
            0x3f8c7f36, 0xb9e0a517, 0x3ce29faa, 0xb75c8000, 0x3f885e9e,
            0x542568cb, 0xbd1f7bdb, 0x46b30000, 0x3f8442e8, 0xb954e7d9,
            0x3d1e5287, 0xb7e60000, 0x3f802c07, 0x22da0b17, 0xbd19fb27,
            0x6c8b0000, 0x3f7833e3, 0x821271ef, 0xbd190f96, 0x29910000,
            0x3f701936, 0xbc3491a5, 0xbd1bcf45, 0x354a0000, 0x3f600fe3,
            0xc0ff520a, 0xbd19d71c, 0x00000000, 0x00000000, 0x00000000,
            0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant log2 = pointerConstant(8, new int[]{
            // @formatter:off
            0x509f7800, 0x3f934413,
    });
    private ArrayDataPointerConstant log28 = pointerConstant(8, new int[]{
            0x1f12b358, 0x3cdfef31
            // @formatter:on
    });

    private ArrayDataPointerConstant coeff = pointerConstant(16, new int[]{
            // @formatter:off
            0xc1a5f12e, 0x40358874, 0x64d4ef0d, 0xc0089309,
    });
    private ArrayDataPointerConstant coeff16 = pointerConstant(16, new int[]{
            0x385593b1, 0xc025c917, 0xdc963467, 0x3ffc6a02,
    });
    private ArrayDataPointerConstant coeff32 = pointerConstant(16, new int[]{
            0x7f9d3aa1, 0x4016ab9f, 0xdc77b115, 0xbff27af2
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

        masm.subq(rsp, 24);
        masm.movsd(new AMD64Address(rsp, 0), xmm0);

        masm.xorpd(xmm2, xmm2);
        masm.movl(rax, 16368);
        masm.pinsrw(xmm2, rax, 3);
        masm.movl(rcx, 1054736384);
        masm.movdl(xmm7, rcx);
        masm.xorpd(xmm3, xmm3);
        masm.movl(rdx, 30704);
        masm.pinsrw(xmm3, rdx, 3);
        masm.movdqu(xmm1, xmm0);
        masm.movl(rdx, 32768);
        masm.movdl(xmm4, rdx);
        masm.movdqu(xmm5, recordExternalAddress(crb, highsigmask));    // 0xf8000000, 0xffffffff,
                                                                       // 0x00000000, 0xffffe000
        masm.pextrw(rax, xmm0, 3);
        masm.por(xmm0, xmm2);
        masm.movl(rcx, 16352);
        masm.psrlq(xmm0, 27);
        masm.movdqu(xmm2, recordExternalAddress(crb, log10E));         // 0x00000000, 0x3fdbc000,
                                                                       // 0xbf2e4108, 0x3f5a7a6c
        masm.psrld(xmm0, 2);
        masm.rcpps(xmm0, xmm0);
        masm.psllq(xmm1, 12);
        masm.pshufd(xmm6, xmm5, 78);
        masm.psrlq(xmm1, 12);
        masm.subl(rax, 16);
        masm.cmpl(rax, 32736);
        masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, block0);

        masm.bind(block1);
        masm.mulss(xmm0, xmm7);
        masm.por(xmm1, xmm3);
        masm.leaq(r11, recordExternalAddress(crb, lTbl));
        masm.andpd(xmm5, xmm1);
        masm.paddd(xmm0, xmm4);
        masm.subsd(xmm1, xmm5);
        masm.movdl(rdx, xmm0);
        masm.psllq(xmm0, 29);
        masm.andpd(xmm0, xmm6);
        masm.andl(rax, 32752);
        masm.subl(rax, rcx);
        masm.cvtsi2sdl(xmm7, rax);
        masm.mulpd(xmm5, xmm0);
        masm.mulsd(xmm1, xmm0);
        masm.movq(xmm6, recordExternalAddress(crb, log2));             // 0x509f7800, 0x3f934413,
                                                                       // 0x1f12b358, 0x3cdfef31
        masm.movdqu(xmm3, recordExternalAddress(crb, coeff));          // 0xc1a5f12e, 0x40358874,
                                                                       // 0x64d4ef0d, 0xc0089309
        masm.subsd(xmm5, xmm2);
        masm.andl(rdx, 16711680);
        masm.shrl(rdx, 12);
        masm.movdqu(xmm0, new AMD64Address(r11, rdx, AMD64Address.Scale.Times1, -1504));
        masm.movdqu(xmm4, recordExternalAddress(crb, coeff16));        // 0x385593b1, 0xc025c917,
                                                                       // 0xdc963467, 0x3ffc6a02
        masm.addsd(xmm1, xmm5);
        masm.movdqu(xmm2, recordExternalAddress(crb, coeff32));        // 0x7f9d3aa1, 0x4016ab9f,
                                                                       // 0xdc77b115, 0xbff27af2
        masm.mulsd(xmm6, xmm7);
        masm.pshufd(xmm5, xmm1, 68);
        masm.mulsd(xmm7, recordExternalAddress(crb, log28));           // 0x1f12b358, 0x3cdfef31
        masm.mulsd(xmm3, xmm1);
        masm.addsd(xmm0, xmm6);
        masm.mulpd(xmm4, xmm5);
        masm.movq(xmm6, recordExternalAddress(crb, log10E8));          // 0xbf2e4108, 0x3f5a7a6c
        masm.mulpd(xmm5, xmm5);
        masm.addpd(xmm4, xmm2);
        masm.mulpd(xmm3, xmm5);
        masm.pshufd(xmm2, xmm0, 228);
        masm.addsd(xmm0, xmm1);
        masm.mulsd(xmm4, xmm1);
        masm.subsd(xmm2, xmm0);
        masm.mulsd(xmm6, xmm1);
        masm.addsd(xmm1, xmm2);
        masm.pshufd(xmm2, xmm0, 238);
        masm.mulsd(xmm5, xmm5);
        masm.addsd(xmm7, xmm2);
        masm.addsd(xmm1, xmm6);
        masm.addpd(xmm4, xmm3);
        masm.addsd(xmm1, xmm7);
        masm.mulpd(xmm4, xmm5);
        masm.addsd(xmm1, xmm4);
        masm.pshufd(xmm5, xmm4, 238);
        masm.addsd(xmm1, xmm5);
        masm.addsd(xmm0, xmm1);
        masm.jmp(block9);

        masm.bind(block0);
        masm.movq(xmm0, new AMD64Address(rsp, 0));
        masm.movq(xmm1, new AMD64Address(rsp, 0));
        masm.addl(rax, 16);
        masm.cmpl(rax, 32768);
        masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, block2);
        masm.cmpl(rax, 16);
        masm.jcc(AMD64Assembler.ConditionFlag.Below, block3);

        masm.bind(block4);
        masm.addsd(xmm0, xmm0);
        masm.jmp(block9);

        masm.bind(block5);
        masm.jcc(AMD64Assembler.ConditionFlag.Above, block4);
        masm.cmpl(rdx, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Above, block4);
        masm.jmp(block6);

        masm.bind(block3);
        masm.xorpd(xmm1, xmm1);
        masm.addsd(xmm1, xmm0);
        masm.movdl(rdx, xmm1);
        masm.psrlq(xmm1, 32);
        masm.movdl(rcx, xmm1);
        masm.orl(rdx, rcx);
        masm.cmpl(rdx, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block7);
        masm.xorpd(xmm1, xmm1);
        masm.movl(rax, 18416);
        masm.pinsrw(xmm1, rax, 3);
        masm.mulsd(xmm0, xmm1);
        masm.xorpd(xmm2, xmm2);
        masm.movl(rax, 16368);
        masm.pinsrw(xmm2, rax, 3);
        masm.movdqu(xmm1, xmm0);
        masm.pextrw(rax, xmm0, 3);
        masm.por(xmm0, xmm2);
        masm.movl(rcx, 18416);
        masm.psrlq(xmm0, 27);
        masm.movdqu(xmm2, recordExternalAddress(crb, log10E));         // 0x00000000, 0x3fdbc000,
                                                                       // 0xbf2e4108, 0x3f5a7a6c
        masm.psrld(xmm0, 2);
        masm.rcpps(xmm0, xmm0);
        masm.psllq(xmm1, 12);
        masm.pshufd(xmm6, xmm5, 78);
        masm.psrlq(xmm1, 12);
        masm.jmp(block1);

        masm.bind(block2);
        masm.movdl(rdx, xmm1);
        masm.psrlq(xmm1, 32);
        masm.movdl(rcx, xmm1);
        masm.addl(rcx, rcx);
        masm.cmpl(rcx, -2097152);
        masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, block5);
        masm.orl(rdx, rcx);
        masm.cmpl(rdx, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block7);

        masm.bind(block6);
        masm.xorpd(xmm1, xmm1);
        masm.xorpd(xmm0, xmm0);
        masm.movl(rax, 32752);
        masm.pinsrw(xmm1, rax, 3);
        masm.mulsd(xmm0, xmm1);
        masm.movl(new AMD64Address(rsp, 16), 9);
        masm.jmp(block8);

        masm.bind(block7);
        masm.xorpd(xmm1, xmm1);
        masm.xorpd(xmm0, xmm0);
        masm.movl(rax, 49136);
        masm.pinsrw(xmm0, rax, 3);
        masm.divsd(xmm0, xmm1);
        masm.movl(new AMD64Address(rsp, 16), 8);

        masm.bind(block8);
        masm.movq(new AMD64Address(rsp, 8), xmm0);
        masm.movq(xmm0, new AMD64Address(rsp, 8));

        masm.bind(block9);
        masm.addq(rsp, 24);
    }
}
