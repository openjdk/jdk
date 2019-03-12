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

import jdk.vm.ci.amd64.AMD64;

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION - LOG()
 *                     ---------------------
 *
 *    x=2^k * mx, mx in [1,2)
 *
 *    Get B~1/mx based on the output of rcpss instruction (B0)
 *    B = int((B0*2^7+0.5))/2^7
 *
 *    Reduced argument: r=B*mx-1.0 (computed accurately in high and low parts)
 *
 *    Result:  k*log(2) - log(B) + p(r) if |x-1| >= small value (2^-6)  and
 *             p(r) is a degree 7 polynomial
 *             -log(B) read from data table (high, low parts)
 *             Result is formed from high and low parts.
 *
 * Special cases:
 *  log(NaN) = quiet NaN, and raise invalid exception
 *  log(+INF) = that INF
 *  log(0) = -INF with divide-by-zero exception raised
 *  log(1) = +0
 *  log(x) = NaN with invalid exception raised if x < -0, including -INF
 * </pre>
 */
public final class AMD64MathLogOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathLogOp> TYPE = LIRInstructionClass.create(AMD64MathLogOp.class);

    public AMD64MathLogOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, r8, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private ArrayDataPointerConstant lTbl = pointerConstant(16, new int[]{
            // @formatter:off
            0xfefa3800, 0x3fe62e42, 0x93c76730, 0x3d2ef357, 0xaa241800,
            0x3fe5ee82, 0x0cda46be, 0x3d220238, 0x5c364800, 0x3fe5af40,
            0xac10c9fb, 0x3d2dfa63, 0x26bb8c00, 0x3fe5707a, 0xff3303dd,
            0x3d09980b, 0x26867800, 0x3fe5322e, 0x5d257531, 0x3d05ccc4,
            0x835a5000, 0x3fe4f45a, 0x6d93b8fb, 0xbd2e6c51, 0x6f970c00,
            0x3fe4b6fd, 0xed4c541c, 0x3cef7115, 0x27e8a400, 0x3fe47a15,
            0xf94d60aa, 0xbd22cb6a, 0xf2f92400, 0x3fe43d9f, 0x481051f7,
            0xbcfd984f, 0x2125cc00, 0x3fe4019c, 0x30f0c74c, 0xbd26ce79,
            0x0c36c000, 0x3fe3c608, 0x7cfe13c2, 0xbd02b736, 0x17197800,
            0x3fe38ae2, 0xbb5569a4, 0xbd218b7a, 0xad9d8c00, 0x3fe35028,
            0x9527e6ac, 0x3d10b83f, 0x44340800, 0x3fe315da, 0xc5a0ed9c,
            0xbd274e93, 0x57b0e000, 0x3fe2dbf5, 0x07b9dc11, 0xbd17a6e5,
            0x6d0ec000, 0x3fe2a278, 0xe797882d, 0x3d206d2b, 0x1134dc00,
            0x3fe26962, 0x05226250, 0xbd0b61f1, 0xd8bebc00, 0x3fe230b0,
            0x6e48667b, 0x3d12fc06, 0x5fc61800, 0x3fe1f863, 0xc9fe81d3,
            0xbd2a7242, 0x49ae6000, 0x3fe1c078, 0xed70e667, 0x3cccacde,
            0x40f23c00, 0x3fe188ee, 0xf8ab4650, 0x3d14cc4e, 0xf6f29800,
            0x3fe151c3, 0xa293ae49, 0xbd2edd97, 0x23c75c00, 0x3fe11af8,
            0xbb9ddcb2, 0xbd258647, 0x8611cc00, 0x3fe0e489, 0x07801742,
            0x3d1c2998, 0xe2d05400, 0x3fe0ae76, 0x887e7e27, 0x3d1f486b,
            0x0533c400, 0x3fe078bf, 0x41edf5fd, 0x3d268122, 0xbe760400,
            0x3fe04360, 0xe79539e0, 0xbd04c45f, 0xe5b20800, 0x3fe00e5a,
            0xb1727b1c, 0xbd053ba3, 0xaf7a4800, 0x3fdfb358, 0x3c164935,
            0x3d0085fa, 0xee031800, 0x3fdf4aa7, 0x6f014a8b, 0x3d12cde5,
            0x56b41000, 0x3fdee2a1, 0x5a470251, 0x3d2f27f4, 0xc3ddb000,
            0x3fde7b42, 0x5372bd08, 0xbd246550, 0x1a272800, 0x3fde148a,
            0x07322938, 0xbd1326b2, 0x484c9800, 0x3fddae75, 0x60dc616a,
            0xbd1ea42d, 0x46def800, 0x3fdd4902, 0xe9a767a8, 0x3d235baf,
            0x18064800, 0x3fdce42f, 0x3ec7a6b0, 0xbd0797c3, 0xc7455800,
            0x3fdc7ff9, 0xc15249ae, 0xbd29b6dd, 0x693fa000, 0x3fdc1c60,
            0x7fe8e180, 0x3d2cec80, 0x1b80e000, 0x3fdbb961, 0xf40a666d,
            0x3d27d85b, 0x04462800, 0x3fdb56fa, 0x2d841995, 0x3d109525,
            0x5248d000, 0x3fdaf529, 0x52774458, 0xbd217cc5, 0x3c8ad800,
            0x3fda93ed, 0xbea77a5d, 0x3d1e36f2, 0x0224f800, 0x3fda3344,
            0x7f9d79f5, 0x3d23c645, 0xea15f000, 0x3fd9d32b, 0x10d0c0b0,
            0xbd26279e, 0x43135800, 0x3fd973a3, 0xa502d9f0, 0xbd152313,
            0x635bf800, 0x3fd914a8, 0x2ee6307d, 0xbd1766b5, 0xa88b3000,
            0x3fd8b639, 0xe5e70470, 0xbd205ae1, 0x776dc800, 0x3fd85855,
            0x3333778a, 0x3d2fd56f, 0x3bd81800, 0x3fd7fafa, 0xc812566a,
            0xbd272090, 0x687cf800, 0x3fd79e26, 0x2efd1778, 0x3d29ec7d,
            0x76c67800, 0x3fd741d8, 0x49dc60b3, 0x3d2d8b09, 0xe6af1800,
            0x3fd6e60e, 0x7c222d87, 0x3d172165, 0x3e9c6800, 0x3fd68ac8,
            0x2756eba0, 0x3d20a0d3, 0x0b3ab000, 0x3fd63003, 0xe731ae00,
            0xbd2db623, 0xdf596000, 0x3fd5d5bd, 0x08a465dc, 0xbd0a0b2a,
            0x53c8d000, 0x3fd57bf7, 0xee5d40ef, 0x3d1faded, 0x0738a000,
            0x3fd522ae, 0x8164c759, 0x3d2ebe70, 0x9e173000, 0x3fd4c9e0,
            0x1b0ad8a4, 0xbd2e2089, 0xc271c800, 0x3fd4718d, 0x0967d675,
            0xbd2f27ce, 0x23d5e800, 0x3fd419b4, 0xec90e09d, 0x3d08e436,
            0x77333000, 0x3fd3c252, 0xb606bd5c, 0x3d183b54, 0x76be1000,
            0x3fd36b67, 0xb0f177c8, 0x3d116ecd, 0xe1d36000, 0x3fd314f1,
            0xd3213cb8, 0xbd28e27a, 0x7cdc9000, 0x3fd2bef0, 0x4a5004f4,
            0x3d2a9cfa, 0x1134d800, 0x3fd26962, 0xdf5bb3b6, 0x3d2c93c1,
            0x6d0eb800, 0x3fd21445, 0xba46baea, 0x3d0a87de, 0x635a6800,
            0x3fd1bf99, 0x5147bdb7, 0x3d2ca6ed, 0xcbacf800, 0x3fd16b5c,
            0xf7a51681, 0x3d2b9acd, 0x8227e800, 0x3fd1178e, 0x63a5f01c,
            0xbd2c210e, 0x67616000, 0x3fd0c42d, 0x163ceae9, 0x3d27188b,
            0x604d5800, 0x3fd07138, 0x16ed4e91, 0x3cf89cdb, 0x5626c800,
            0x3fd01eae, 0x1485e94a, 0xbd16f08c, 0x6cb3b000, 0x3fcf991c,
            0xca0cdf30, 0x3d1bcbec, 0xe4dd0000, 0x3fcef5ad, 0x65bb8e11,
            0xbcca2115, 0xffe71000, 0x3fce530e, 0x6041f430, 0x3cc21227,
            0xb0d49000, 0x3fcdb13d, 0xf715b035, 0xbd2aff2a, 0xf2656000,
            0x3fcd1037, 0x75b6f6e4, 0xbd084a7e, 0xc6f01000, 0x3fcc6ffb,
            0xc5962bd2, 0xbcf1ec72, 0x383be000, 0x3fcbd087, 0x595412b6,
            0xbd2d4bc4, 0x575bd000, 0x3fcb31d8, 0x4eace1aa, 0xbd0c358d,
            0x3c8ae000, 0x3fca93ed, 0x50562169, 0xbd287243, 0x07089000,
            0x3fc9f6c4, 0x6865817a, 0x3d29904d, 0xdcf70000, 0x3fc95a5a,
            0x58a0ff6f, 0x3d07f228, 0xeb390000, 0x3fc8beaf, 0xaae92cd1,
            0xbd073d54, 0x6551a000, 0x3fc823c1, 0x9a631e83, 0x3d1e0ddb,
            0x85445000, 0x3fc7898d, 0x70914305, 0xbd1c6610, 0x8b757000,
            0x3fc6f012, 0xe59c21e1, 0xbd25118d, 0xbe8c1000, 0x3fc6574e,
            0x2c3c2e78, 0x3d19cf8b, 0x6b544000, 0x3fc5bf40, 0xeb68981c,
            0xbd127023, 0xe4a1b000, 0x3fc527e5, 0xe5697dc7, 0x3d2633e8,
            0x8333b000, 0x3fc4913d, 0x54fdb678, 0x3d258379, 0xa5993000,
            0x3fc3fb45, 0x7e6a354d, 0xbd2cd1d8, 0xb0159000, 0x3fc365fc,
            0x234b7289, 0x3cc62fa8, 0x0c868000, 0x3fc2d161, 0xcb81b4a1,
            0x3d039d6c, 0x2a49c000, 0x3fc23d71, 0x8fd3df5c, 0x3d100d23,
            0x7e23f000, 0x3fc1aa2b, 0x44389934, 0x3d2ca78e, 0x8227e000,
            0x3fc1178e, 0xce2d07f2, 0x3d21ef78, 0xb59e4000, 0x3fc08598,
            0x7009902c, 0xbd27e5dd, 0x39dbe000, 0x3fbfe891, 0x4fa10afd,
            0xbd2534d6, 0x830a2000, 0x3fbec739, 0xafe645e0, 0xbd2dc068,
            0x63844000, 0x3fbda727, 0x1fa71733, 0x3d1a8940, 0x01bc4000,
            0x3fbc8858, 0xc65aacd3, 0x3d2646d1, 0x8dad6000, 0x3fbb6ac8,
            0x2bf768e5, 0xbd139080, 0x40b1c000, 0x3fba4e76, 0xb94407c8,
            0xbd0e42b6, 0x5d594000, 0x3fb9335e, 0x3abd47da, 0x3d23115c,
            0x2f40e000, 0x3fb8197e, 0xf96ffdf7, 0x3d0f80dc, 0x0aeac000,
            0x3fb700d3, 0xa99ded32, 0x3cec1e8d, 0x4d97a000, 0x3fb5e95a,
            0x3c5d1d1e, 0xbd2c6906, 0x5d208000, 0x3fb4d311, 0x82f4e1ef,
            0xbcf53a25, 0xa7d1e000, 0x3fb3bdf5, 0xa5db4ed7, 0x3d2cc85e,
            0xa4472000, 0x3fb2aa04, 0xae9c697d, 0xbd20b6e8, 0xd1466000,
            0x3fb1973b, 0x560d9e9b, 0xbd25325d, 0xb59e4000, 0x3fb08598,
            0x7009902c, 0xbd17e5dd, 0xc006c000, 0x3faeea31, 0x4fc93b7b,
            0xbd0e113e, 0xcdddc000, 0x3faccb73, 0x47d82807, 0xbd1a68f2,
            0xd0fb0000, 0x3faaaef2, 0x353bb42e, 0x3d20fc1a, 0x149fc000,
            0x3fa894aa, 0xd05a267d, 0xbd197995, 0xf2d4c000, 0x3fa67c94,
            0xec19afa2, 0xbd029efb, 0xd42e0000, 0x3fa466ae, 0x75bdfd28,
            0xbd2c1673, 0x2f8d0000, 0x3fa252f3, 0xe021b67b, 0x3d283e9a,
            0x89e74000, 0x3fa0415d, 0x5cf1d753, 0x3d0111c0, 0xec148000,
            0x3f9c63d2, 0x3f9eb2f3, 0x3d2578c6, 0x28c90000, 0x3f984925,
            0x325a0c34, 0xbd2aa0ba, 0x25980000, 0x3f9432a9, 0x928637fe,
            0x3d098139, 0x58938000, 0x3f902056, 0x06e2f7d2, 0xbd23dc5b,
            0xa3890000, 0x3f882448, 0xda74f640, 0xbd275577, 0x75890000,
            0x3f801015, 0x999d2be8, 0xbd10c76b, 0x59580000, 0x3f700805,
            0xcb31c67b, 0x3d2166af, 0x00000000, 0x00000000, 0x00000000,
            0x80000000
            // @formatter:on
    });

    private ArrayDataPointerConstant log2 = pointerConstant(8, new int[]{
            // @formatter:off
            0xfefa3800, 0x3fa62e42,
    });
    private ArrayDataPointerConstant log28 = pointerConstant(8, new int[]{
            0x93c76730, 0x3ceef357
            // @formatter:on
    });

    private ArrayDataPointerConstant coeff = pointerConstant(16, new int[]{
            // @formatter:off
            0x92492492, 0x3fc24924, 0x00000000, 0xbfd00000,
    });
    private ArrayDataPointerConstant coeff16 = pointerConstant(16, new int[]{
            0x3d6fb175, 0xbfc5555e, 0x55555555, 0x3fd55555,
    });
    private ArrayDataPointerConstant coeff32 = pointerConstant(16, new int[]{
            0x9999999a, 0x3fc99999, 0x00000000, 0xbfe00000
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // registers,
        // input: xmm0
        // scratch: xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
        // rax, rdx, rcx, r8, r11
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
        masm.movq(rax, 0x3ff0000000000000L);
        masm.movdq(xmm2, rax);
        masm.movq(rdx, 0x77f0000000000000L);
        masm.movdq(xmm3, rdx);
        masm.movl(rcx, 32768);
        masm.movdl(xmm4, rcx);
        masm.movq(r8, 0xffffe00000000000L);
        masm.movdq(xmm5, r8);
        masm.movdqu(xmm1, xmm0);
        masm.pextrw(rax, xmm0, 3);
        masm.por(xmm0, xmm2);
        masm.movl(rcx, 16352);
        masm.psrlq(xmm0, 27);
        masm.leaq(r11, recordExternalAddress(crb, lTbl));
        masm.psrld(xmm0, 2);
        masm.rcpps(xmm0, xmm0);
        masm.psllq(xmm1, 12);
        masm.pshufd(xmm6, xmm5, 228);
        masm.psrlq(xmm1, 12);
        masm.subl(rax, 16);
        masm.cmpl(rax, 32736);
        masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, block0);

        masm.bind(block1);
        masm.paddd(xmm0, xmm4);
        masm.por(xmm1, xmm3);
        masm.movdl(rdx, xmm0);
        masm.psllq(xmm0, 29);
        masm.pand(xmm5, xmm1);
        masm.pand(xmm0, xmm6);
        masm.subsd(xmm1, xmm5);
        masm.mulpd(xmm5, xmm0);
        masm.andl(rax, 32752);
        masm.subl(rax, rcx);
        masm.cvtsi2sdl(xmm7, rax);
        masm.mulsd(xmm1, xmm0);
        masm.movq(xmm6, recordExternalAddress(crb, log2));             // 0xfefa3800, 0x3fa62e42
        masm.movdqu(xmm3, recordExternalAddress(crb, coeff));          // 0x92492492, 0x3fc24924,
                                                                       // 0x00000000, 0xbfd00000
        masm.subsd(xmm5, xmm2);
        masm.andl(rdx, 16711680);
        masm.shrl(rdx, 12);
        masm.movdqu(xmm0, new AMD64Address(r11, rdx, AMD64Address.Scale.Times1));
        masm.movdqu(xmm4, recordExternalAddress(crb, coeff16));        // 0x3d6fb175, 0xbfc5555e,
                                                                       // 0x55555555, 0x3fd55555
        masm.addsd(xmm1, xmm5);
        masm.movdqu(xmm2, recordExternalAddress(crb, coeff32));        // 0x9999999a, 0x3fc99999,
                                                                       // 0x00000000, 0xbfe00000
        masm.mulsd(xmm6, xmm7);
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm5, xmm1);
        } else {
            masm.movdqu(xmm5, xmm1);
            masm.movlhps(xmm5, xmm5);
        }
        masm.mulsd(xmm7, recordExternalAddress(crb, log28));           // 0x93c76730, 0x3ceef357
        masm.mulsd(xmm3, xmm1);
        masm.addsd(xmm0, xmm6);
        masm.mulpd(xmm4, xmm5);
        masm.mulpd(xmm5, xmm5);
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm6, xmm0);
        } else {
            masm.movdqu(xmm6, xmm0);
            masm.movlhps(xmm6, xmm6);
        }
        masm.addsd(xmm0, xmm1);
        masm.addpd(xmm4, xmm2);
        masm.mulpd(xmm3, xmm5);
        masm.subsd(xmm6, xmm0);
        masm.mulsd(xmm4, xmm1);
        masm.pshufd(xmm2, xmm0, 238);
        masm.addsd(xmm1, xmm6);
        masm.mulsd(xmm5, xmm5);
        masm.addsd(xmm7, xmm2);
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
        masm.movdqu(xmm1, xmm0);
        masm.pextrw(rax, xmm0, 3);
        masm.por(xmm0, xmm2);
        masm.psrlq(xmm0, 27);
        masm.movl(rcx, 18416);
        masm.psrld(xmm0, 2);
        masm.rcpps(xmm0, xmm0);
        masm.psllq(xmm1, 12);
        masm.pshufd(xmm6, xmm5, 228);
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
        masm.movl(new AMD64Address(rsp, 16), 3);
        masm.jmp(block8);
        masm.bind(block7);
        masm.xorpd(xmm1, xmm1);
        masm.xorpd(xmm0, xmm0);
        masm.movl(rax, 49136);
        masm.pinsrw(xmm0, rax, 3);
        masm.divsd(xmm0, xmm1);
        masm.movl(new AMD64Address(rsp, 16), 2);

        masm.bind(block8);
        masm.movq(new AMD64Address(rsp, 8), xmm0);

        masm.movq(xmm0, new AMD64Address(rsp, 8));

        masm.bind(block9);
        masm.addq(rsp, 24);
    }
}
