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
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;

/**
 * <pre>
 *                     ALGORITHM DESCRIPTION - TAN()
 *                     ---------------------
 *
 * Polynomials coefficients and other constants.
 *
 * Note that in this algorithm, there is a different polynomial for
 * each breakpoint, so there are 32 sets of polynomial coefficients
 * as well as 32 instances of the other constants.
 *
 * The polynomial coefficients and constants are offset from the start
 * of the main block as follows:
 *
 *   0:  c8 | c0
 *  16:  c9 | c1
 *  32: c10 | c2
 *  48: c11 | c3
 *  64: c12 | c4
 *  80: c13 | c5
 *  96: c14 | c6
 * 112: c15 | c7
 * 128: T_hi
 * 136: T_lo
 * 144: Sigma
 * 152: T_hl
 * 160: Tau
 * 168: Mask
 * 176: (end of block)
 *
 * The total table size is therefore 5632 bytes.
 *
 * Note that c0 and c1 are always zero. We could try storing
 * other constants here, and just loading the low part of the
 * SIMD register in these cases, after ensuring the high part
 * is zero.
 *
 * The higher terms of the polynomial are computed in the *low*
 * part of the SIMD register. This is so we can overlap the
 * multiplication by r^8 and the unpacking of the other part.
 *
 * The constants are:
 * T_hi + T_lo = accurate constant term in power series
 * Sigma + T_hl = accurate coefficient of r in power series (Sigma=1 bit)
 * Tau = multiplier for the reciprocal, always -1 or 0
 *
 * The basic reconstruction formula using these constants is:
 *
 * High = tau * recip_hi + t_hi
 * Med = (sgn * r + t_hl * r)_hi
 * Low = (sgn * r + t_hl * r)_lo +
 *       tau * recip_lo + T_lo + (T_hl + sigma) * c + pol
 *
 * where pol = c0 + c1 * r + c2 * r^2 + ... + c15 * r^15
 *
 * (c0 = c1 = 0, but using them keeps SIMD regularity)
 *
 * We then do a compensated sum High + Med, add the low parts together
 * and then do the final sum.
 *
 * Here recip_hi + recip_lo is an accurate reciprocal of the remainder
 * modulo pi/2
 *
 * Special cases:
 *  tan(NaN) = quiet NaN, and raise invalid exception
 *  tan(INF) = NaN and raise invalid exception
 *  tan(+/-0) = +/-0
 * </pre>
 */
public final class AMD64MathTanOp extends AMD64MathIntrinsicUnaryOp {

    public static final LIRInstructionClass<AMD64MathTanOp> TYPE = LIRInstructionClass.create(AMD64MathTanOp.class);

    public AMD64MathTanOp() {
        super(TYPE, /* GPR */ rax, rcx, rdx, rbx, rsi, rdi, r8, r9, r10, r11,
                        /* XMM */ xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7);
    }

    private ArrayDataPointerConstant onehalf = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x3fe00000, 0x00000000, 0x3fe00000
            // @formatter:on
    });

    private ArrayDataPointerConstant mul16 = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x40300000, 0x00000000, 0x3ff00000
            // @formatter:on
    });

    private ArrayDataPointerConstant signMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x80000000, 0x00000000, 0x80000000
            // @formatter:on
    });

    private ArrayDataPointerConstant pi32Inv = pointerConstant(16, new int[]{
            // @formatter:off
            0x6dc9c883, 0x3fe45f30, 0x6dc9c883, 0x40245f30
            // @formatter:on
    });

    private ArrayDataPointerConstant p1 = pointerConstant(16, new int[]{
            // @formatter:off
            0x54444000, 0x3fb921fb, 0x54440000, 0x3fb921fb
            // @formatter:on
    });

    private ArrayDataPointerConstant p2 = pointerConstant(16, new int[]{
            // @formatter:off
            0x67674000, 0xbd32e7b9, 0x4c4c0000, 0x3d468c23
            // @formatter:on
    });

    private ArrayDataPointerConstant p3 = pointerConstant(16, new int[]{
            // @formatter:off
            0x3707344a, 0x3aa8a2e0, 0x03707345, 0x3ae98a2e
            // @formatter:on
    });

    private ArrayDataPointerConstant ctable = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x882c10fa,
            0x3f9664f4, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x55e6c23d, 0x3f8226e3, 0x55555555,
            0x3fd55555, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x0e157de0, 0x3f6d6d3d, 0x11111111, 0x3fc11111, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0x452b75e3, 0x3f57da36,
            0x1ba1ba1c, 0x3faba1ba, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x3ff00000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x4e435f9b,
            0x3f953f83, 0x00000000, 0x00000000, 0x3c6e8e46, 0x3f9b74ea,
            0x00000000, 0x00000000, 0xda5b7511, 0x3f85ad63, 0xdc230b9b,
            0x3fb97558, 0x26cb3788, 0x3f881308, 0x76fc4985, 0x3fd62ac9,
            0x77bb08ba, 0x3f757c85, 0xb6247521, 0x3fb1381e, 0x5922170c,
            0x3f754e95, 0x8746482d, 0x3fc27f83, 0x11055b30, 0x3f64e391,
            0x3e666320, 0x3fa3e609, 0x0de9dae3, 0x3f6301df, 0x1f1dca06,
            0x3fafa8ae, 0x8c5b2da2, 0x3fb936bb, 0x4e88f7a5, 0x3c587d05,
            0x00000000, 0x3ff00000, 0xa8935dd9, 0x3f83dde2, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0x5a279ea3, 0x3faa3407,
            0x00000000, 0x00000000, 0x432d65fa, 0x3fa70153, 0x00000000,
            0x00000000, 0x891a4602, 0x3f9d03ef, 0xd62ca5f8, 0x3fca77d9,
            0xb35f4628, 0x3f97a265, 0x433258fa, 0x3fd8cf51, 0xb58fd909,
            0x3f8f88e3, 0x01771cea, 0x3fc2b154, 0xf3562f8e, 0x3f888f57,
            0xc028a723, 0x3fc7370f, 0x20b7f9f0, 0x3f80f44c, 0x214368e9,
            0x3fb6dfaa, 0x28891863, 0x3f79b4b6, 0x172dbbf0, 0x3fb6cb8e,
            0xe0553158, 0x3fc975f5, 0x593fe814, 0x3c2ef5d3, 0x00000000,
            0x3ff00000, 0x03dec550, 0x3fa44203, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x9314533e, 0x3fbb8ec5, 0x00000000,
            0x00000000, 0x09aa36d0, 0x3fb6d3f4, 0x00000000, 0x00000000,
            0xdcb427fd, 0x3fb13950, 0xd87ab0bb, 0x3fd5335e, 0xce0ae8a5,
            0x3fabb382, 0x79143126, 0x3fddba41, 0x5f2b28d4, 0x3fa552f1,
            0x59f21a6d, 0x3fd015ab, 0x22c27d95, 0x3fa0e984, 0xe19fc6aa,
            0x3fd0576c, 0x8f2c2950, 0x3f9a4898, 0xc0b3f22c, 0x3fc59462,
            0x1883a4b8, 0x3f94b61c, 0x3f838640, 0x3fc30eb8, 0x355c63dc,
            0x3fd36a08, 0x1dce993d, 0xbc6d704d, 0x00000000, 0x3ff00000,
            0x2b82ab63, 0x3fb78e92, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x56f37042, 0x3fccfc56, 0x00000000, 0x00000000,
            0xaa563951, 0x3fc90125, 0x00000000, 0x00000000, 0x3d0e7c5d,
            0x3fc50533, 0x9bed9b2e, 0x3fdf0ed9, 0x5fe7c47c, 0x3fc1f250,
            0x96c125e5, 0x3fe2edd9, 0x5a02bbd8, 0x3fbe5c71, 0x86362c20,
            0x3fda08b7, 0x4b4435ed, 0x3fb9d342, 0x4b494091, 0x3fd911bd,
            0xb56658be, 0x3fb5e4c7, 0x93a2fd76, 0x3fd3c092, 0xda271794,
            0x3fb29910, 0x3303df2b, 0x3fd189be, 0x99fcef32, 0x3fda8279,
            0xb68c1467, 0x3c708b2f, 0x00000000, 0x3ff00000, 0x980c4337,
            0x3fc5f619, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0xcc03e501, 0x3fdff10f, 0x00000000, 0x00000000, 0x44a4e845,
            0x3fddb63b, 0x00000000, 0x00000000, 0x3768ad9f, 0x3fdb72a4,
            0x3dd01cca, 0x3fe5fdb9, 0xa61d2811, 0x3fd972b2, 0x5645ad0b,
            0x3fe977f9, 0xd013b3ab, 0x3fd78ca3, 0xbf0bf914, 0x3fe4f192,
            0x4d53e730, 0x3fd5d060, 0x3f8b9000, 0x3fe49933, 0xe2b82f08,
            0x3fd4322a, 0x5936a835, 0x3fe27ae1, 0xb1c61c9b, 0x3fd2b3fb,
            0xef478605, 0x3fe1659e, 0x190834ec, 0x3fe11ab7, 0xcdb625ea,
            0xbc8e564b, 0x00000000, 0x3ff00000, 0xb07217e3, 0x3fd248f1,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x2b2c49d0,
            0x3ff2de9c, 0x00000000, 0x00000000, 0x2655bc98, 0x3ff33e58,
            0x00000000, 0x00000000, 0xff691fa2, 0x3ff3972e, 0xe93463bd,
            0x3feeed87, 0x070e10a0, 0x3ff3f5b2, 0xf4d790a4, 0x3ff20c10,
            0xa04e8ea3, 0x3ff4541a, 0x386accd3, 0x3ff1369e, 0x222a66dd,
            0x3ff4b521, 0x22a9777e, 0x3ff20817, 0x52a04a6e, 0x3ff5178f,
            0xddaa0031, 0x3ff22137, 0x4447d47c, 0x3ff57c01, 0x1e9c7f1d,
            0x3ff29311, 0x2ab7f990, 0x3fe561b8, 0x209c7df1, 0x3c87a8c5,
            0x00000000, 0x3ff00000, 0x4170bcc6, 0x3fdc92d8, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0xc7ab4d5a, 0x40085e24,
            0x00000000, 0x00000000, 0xe93ea75d, 0x400b963d, 0x00000000,
            0x00000000, 0x94a7f25a, 0x400f37e2, 0x4b6261cb, 0x3ff5f984,
            0x5a9dd812, 0x4011aab0, 0x74c30018, 0x3ffaf5a5, 0x7f2ce8e3,
            0x4013fe8b, 0xfe8e54fa, 0x3ffd7334, 0x670d618d, 0x4016a10c,
            0x4db97058, 0x4000e012, 0x24df44dd, 0x40199c5f, 0x697d6ece,
            0x4003006e, 0x83298b82, 0x401cfc4d, 0x19d490d6, 0x40058c19,
            0x2ae42850, 0x3fea4300, 0x118e20e6, 0xbc7a6db8, 0x00000000,
            0x40000000, 0xe33345b8, 0xbfd4e526, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x65965966, 0x40219659, 0x00000000,
            0x00000000, 0x882c10fa, 0x402664f4, 0x00000000, 0x00000000,
            0x83cd3723, 0x402c8342, 0x00000000, 0x40000000, 0x55e6c23d,
            0x403226e3, 0x55555555, 0x40055555, 0x34451939, 0x40371c96,
            0xaaaaaaab, 0x400aaaaa, 0x0e157de0, 0x403d6d3d, 0x11111111,
            0x40111111, 0xa738201f, 0x4042bbce, 0x05b05b06, 0x4015b05b,
            0x452b75e3, 0x4047da36, 0x1ba1ba1c, 0x401ba1ba, 0x00000000,
            0x3ff00000, 0x00000000, 0x00000000, 0x00000000, 0x40000000,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x4f48b8d3, 0xbf33eaf9, 0x00000000, 0x00000000,
            0x0cf7586f, 0x3f20b8ea, 0x00000000, 0x00000000, 0xd0258911,
            0xbf0abaf3, 0x23e49fe9, 0xbfab5a8c, 0x2d53222e, 0x3ef60d15,
            0x21169451, 0x3fa172b2, 0xbb254dbc, 0xbee1d3b5, 0xdbf93b8e,
            0xbf84c7db, 0x05b4630b, 0x3ecd3364, 0xee9aada7, 0x3f743924,
            0x794a8297, 0xbeb7b7b9, 0xe015f797, 0xbf5d41f5, 0xe41a4a56,
            0x3ea35dfb, 0xe4c2a251, 0x3f49a2ab, 0x5af9e000, 0xbfce49ce,
            0x8c743719, 0x3d1eb860, 0x00000000, 0x00000000, 0x1b4863cf,
            0x3fd78294, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
            0x535ad890, 0xbf2b9320, 0x00000000, 0x00000000, 0x018fdf1f,
            0x3f16d61d, 0x00000000, 0x00000000, 0x0359f1be, 0xbf0139e4,
            0xa4317c6d, 0xbfa67e17, 0x82672d0f, 0x3eebb405, 0x2f1b621e,
            0x3f9f455b, 0x51ccf238, 0xbed55317, 0xf437b9ac, 0xbf804bee,
            0xc791a2b5, 0x3ec0e993, 0x919a1db2, 0x3f7080c2, 0x336a5b0e,
            0xbeaa48a2, 0x0a268358, 0xbf55a443, 0xdfd978e4, 0x3e94b61f,
            0xd7767a58, 0x3f431806, 0x2aea0000, 0xbfc9bbe8, 0x7723ea61,
            0xbd3a2369, 0x00000000, 0x00000000, 0xdf7796ff, 0x3fd6e642,
            0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0xb9ff07ce,
            0xbf231c78, 0x00000000, 0x00000000, 0xa5517182, 0x3f0ff0e0,
            0x00000000, 0x00000000, 0x790b4cbc, 0xbef66191, 0x848a46c6,
            0xbfa21ac0, 0xb16435fa, 0x3ee1d3ec, 0x2a1aa832, 0x3f9c71ea,
            0xfdd299ef, 0xbec9dd1a, 0x3f8dbaaf, 0xbf793363, 0x309fc6ea,
            0x3eb415d6, 0xbee60471, 0x3f6b83ba, 0x94a0a697, 0xbe9dae11,
            0x3e5c67b3, 0xbf4fd07b, 0x9a8f3e3e, 0x3e86bd75, 0xa4beb7a4,
            0x3f3d1eb1, 0x29cfc000, 0xbfc549ce, 0xbf159358, 0xbd397b33,
            0x00000000, 0x00000000, 0x871fee6c, 0x3fd666f0, 0x00000000,
            0x3ff00000, 0x00000000, 0xfffffff8, 0x7d98a556, 0xbf1a3958,
            0x00000000, 0x00000000, 0x9d88dc01, 0x3f0704c2, 0x00000000,
            0x00000000, 0x73742a2b, 0xbeed054a, 0x58844587, 0xbf9c2a13,
            0x55688a79, 0x3ed7a326, 0xee33f1d6, 0x3f9a48f4, 0xa8dc9888,
            0xbebf8939, 0xaad4b5b8, 0xbf72f746, 0x9102efa1, 0x3ea88f82,
            0xdabc29cf, 0x3f678228, 0x9289afb8, 0xbe90f456, 0x741fb4ed,
            0xbf46f3a3, 0xa97f6663, 0x3e79b4bf, 0xca89ff3f, 0x3f36db70,
            0xa8a2a000, 0xbfc0ee13, 0x3da24be1, 0xbd338b9f, 0x00000000,
            0x00000000, 0x11cd6c69, 0x3fd601fd, 0x00000000, 0x3ff00000,
            0x00000000, 0xfffffff8, 0x1a154b97, 0xbf116b01, 0x00000000,
            0x00000000, 0x2d427630, 0x3f0147bf, 0x00000000, 0x00000000,
            0xb93820c8, 0xbee264d4, 0xbb6cbb18, 0xbf94ab8c, 0x888d4d92,
            0x3ed0568b, 0x60730f7c, 0x3f98b19b, 0xe4b1fb11, 0xbeb2f950,
            0x22cf9f74, 0xbf6b21cd, 0x4a3ff0a6, 0x3e9f499e, 0xfd2b83ce,
            0x3f64aad7, 0x637b73af, 0xbe83487c, 0xe522591a, 0xbf3fc092,
            0xa158e8bc, 0x3e6e3aae, 0xe5e82ffa, 0x3f329d2f, 0xd636a000,
            0xbfb9477f, 0xc2c2d2bc, 0xbd135ef9, 0x00000000, 0x00000000,
            0xf2fdb123, 0x3fd5b566, 0x00000000, 0x3ff00000, 0x00000000,
            0xfffffff8, 0xc41acb64, 0xbf05448d, 0x00000000, 0x00000000,
            0xdbb03d6f, 0x3efb7ad2, 0x00000000, 0x00000000, 0x9e42962d,
            0xbed5aea5, 0x2579f8ef, 0xbf8b2398, 0x288a1ed9, 0x3ec81441,
            0xb0198dc5, 0x3f979a3a, 0x2fdfe253, 0xbea57cd3, 0x5766336f,
            0xbf617caa, 0x600944c3, 0x3e954ed6, 0xa4e0aaf8, 0x3f62c646,
            0x6b8fb29c, 0xbe74e3a3, 0xdc4c0409, 0xbf33f952, 0x9bffe365,
            0x3e6301ec, 0xb8869e44, 0x3f2fc566, 0xe1e04000, 0xbfb0cc62,
            0x016b907f, 0xbd119cbc, 0x00000000, 0x00000000, 0xe6b9d8fa,
            0x3fd57fb3, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
            0x5daf22a6, 0xbef429d7, 0x00000000, 0x00000000, 0x06bca545,
            0x3ef7a27d, 0x00000000, 0x00000000, 0x7211c19a, 0xbec41c3e,
            0x956ed53e, 0xbf7ae3f4, 0xee750e72, 0x3ec3901b, 0x91d443f5,
            0x3f96f713, 0x36661e6c, 0xbe936e09, 0x506f9381, 0xbf5122e8,
            0xcb6dd43f, 0x3e9041b9, 0x6698b2ff, 0x3f61b0c7, 0x576bf12b,
            0xbe625a8a, 0xe5a0e9dc, 0xbf23499d, 0x110384dd, 0x3e5b1c2c,
            0x68d43db6, 0x3f2cb899, 0x6ecac000, 0xbfa0c414, 0xcd7dd58c,
            0x3d13500f, 0x00000000, 0x00000000, 0x85a2c8fb, 0x3fd55fe0,
            0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0x2bf70ebe, 0x3ef66a8f,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0xd644267f, 0x3ec22805, 0x16c16c17, 0x3f96c16c,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0xc4e09162,
            0x3e8d6db2, 0xbc011567, 0x3f61566a, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x1f79955c, 0x3e57da4e, 0x9334ef0b,
            0x3f2bbd77, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x55555555, 0x3fd55555, 0x00000000,
            0x3ff00000, 0x00000000, 0xfffffff8, 0x5daf22a6, 0x3ef429d7,
            0x00000000, 0x00000000, 0x06bca545, 0x3ef7a27d, 0x00000000,
            0x00000000, 0x7211c19a, 0x3ec41c3e, 0x956ed53e, 0x3f7ae3f4,
            0xee750e72, 0x3ec3901b, 0x91d443f5, 0x3f96f713, 0x36661e6c,
            0x3e936e09, 0x506f9381, 0x3f5122e8, 0xcb6dd43f, 0x3e9041b9,
            0x6698b2ff, 0x3f61b0c7, 0x576bf12b, 0x3e625a8a, 0xe5a0e9dc,
            0x3f23499d, 0x110384dd, 0x3e5b1c2c, 0x68d43db6, 0x3f2cb899,
            0x6ecac000, 0x3fa0c414, 0xcd7dd58c, 0xbd13500f, 0x00000000,
            0x00000000, 0x85a2c8fb, 0x3fd55fe0, 0x00000000, 0x3ff00000,
            0x00000000, 0xfffffff8, 0xc41acb64, 0x3f05448d, 0x00000000,
            0x00000000, 0xdbb03d6f, 0x3efb7ad2, 0x00000000, 0x00000000,
            0x9e42962d, 0x3ed5aea5, 0x2579f8ef, 0x3f8b2398, 0x288a1ed9,
            0x3ec81441, 0xb0198dc5, 0x3f979a3a, 0x2fdfe253, 0x3ea57cd3,
            0x5766336f, 0x3f617caa, 0x600944c3, 0x3e954ed6, 0xa4e0aaf8,
            0x3f62c646, 0x6b8fb29c, 0x3e74e3a3, 0xdc4c0409, 0x3f33f952,
            0x9bffe365, 0x3e6301ec, 0xb8869e44, 0x3f2fc566, 0xe1e04000,
            0x3fb0cc62, 0x016b907f, 0x3d119cbc, 0x00000000, 0x00000000,
            0xe6b9d8fa, 0x3fd57fb3, 0x00000000, 0x3ff00000, 0x00000000,
            0xfffffff8, 0x1a154b97, 0x3f116b01, 0x00000000, 0x00000000,
            0x2d427630, 0x3f0147bf, 0x00000000, 0x00000000, 0xb93820c8,
            0x3ee264d4, 0xbb6cbb18, 0x3f94ab8c, 0x888d4d92, 0x3ed0568b,
            0x60730f7c, 0x3f98b19b, 0xe4b1fb11, 0x3eb2f950, 0x22cf9f74,
            0x3f6b21cd, 0x4a3ff0a6, 0x3e9f499e, 0xfd2b83ce, 0x3f64aad7,
            0x637b73af, 0x3e83487c, 0xe522591a, 0x3f3fc092, 0xa158e8bc,
            0x3e6e3aae, 0xe5e82ffa, 0x3f329d2f, 0xd636a000, 0x3fb9477f,
            0xc2c2d2bc, 0x3d135ef9, 0x00000000, 0x00000000, 0xf2fdb123,
            0x3fd5b566, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
            0x7d98a556, 0x3f1a3958, 0x00000000, 0x00000000, 0x9d88dc01,
            0x3f0704c2, 0x00000000, 0x00000000, 0x73742a2b, 0x3eed054a,
            0x58844587, 0x3f9c2a13, 0x55688a79, 0x3ed7a326, 0xee33f1d6,
            0x3f9a48f4, 0xa8dc9888, 0x3ebf8939, 0xaad4b5b8, 0x3f72f746,
            0x9102efa1, 0x3ea88f82, 0xdabc29cf, 0x3f678228, 0x9289afb8,
            0x3e90f456, 0x741fb4ed, 0x3f46f3a3, 0xa97f6663, 0x3e79b4bf,
            0xca89ff3f, 0x3f36db70, 0xa8a2a000, 0x3fc0ee13, 0x3da24be1,
            0x3d338b9f, 0x00000000, 0x00000000, 0x11cd6c69, 0x3fd601fd,
            0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0xb9ff07ce,
            0x3f231c78, 0x00000000, 0x00000000, 0xa5517182, 0x3f0ff0e0,
            0x00000000, 0x00000000, 0x790b4cbc, 0x3ef66191, 0x848a46c6,
            0x3fa21ac0, 0xb16435fa, 0x3ee1d3ec, 0x2a1aa832, 0x3f9c71ea,
            0xfdd299ef, 0x3ec9dd1a, 0x3f8dbaaf, 0x3f793363, 0x309fc6ea,
            0x3eb415d6, 0xbee60471, 0x3f6b83ba, 0x94a0a697, 0x3e9dae11,
            0x3e5c67b3, 0x3f4fd07b, 0x9a8f3e3e, 0x3e86bd75, 0xa4beb7a4,
            0x3f3d1eb1, 0x29cfc000, 0x3fc549ce, 0xbf159358, 0x3d397b33,
            0x00000000, 0x00000000, 0x871fee6c, 0x3fd666f0, 0x00000000,
            0x3ff00000, 0x00000000, 0xfffffff8, 0x535ad890, 0x3f2b9320,
            0x00000000, 0x00000000, 0x018fdf1f, 0x3f16d61d, 0x00000000,
            0x00000000, 0x0359f1be, 0x3f0139e4, 0xa4317c6d, 0x3fa67e17,
            0x82672d0f, 0x3eebb405, 0x2f1b621e, 0x3f9f455b, 0x51ccf238,
            0x3ed55317, 0xf437b9ac, 0x3f804bee, 0xc791a2b5, 0x3ec0e993,
            0x919a1db2, 0x3f7080c2, 0x336a5b0e, 0x3eaa48a2, 0x0a268358,
            0x3f55a443, 0xdfd978e4, 0x3e94b61f, 0xd7767a58, 0x3f431806,
            0x2aea0000, 0x3fc9bbe8, 0x7723ea61, 0x3d3a2369, 0x00000000,
            0x00000000, 0xdf7796ff, 0x3fd6e642, 0x00000000, 0x3ff00000,
            0x00000000, 0xfffffff8, 0x4f48b8d3, 0x3f33eaf9, 0x00000000,
            0x00000000, 0x0cf7586f, 0x3f20b8ea, 0x00000000, 0x00000000,
            0xd0258911, 0x3f0abaf3, 0x23e49fe9, 0x3fab5a8c, 0x2d53222e,
            0x3ef60d15, 0x21169451, 0x3fa172b2, 0xbb254dbc, 0x3ee1d3b5,
            0xdbf93b8e, 0x3f84c7db, 0x05b4630b, 0x3ecd3364, 0xee9aada7,
            0x3f743924, 0x794a8297, 0x3eb7b7b9, 0xe015f797, 0x3f5d41f5,
            0xe41a4a56, 0x3ea35dfb, 0xe4c2a251, 0x3f49a2ab, 0x5af9e000,
            0x3fce49ce, 0x8c743719, 0xbd1eb860, 0x00000000, 0x00000000,
            0x1b4863cf, 0x3fd78294, 0x00000000, 0x3ff00000, 0x00000000,
            0xfffffff8, 0x65965966, 0xc0219659, 0x00000000, 0x00000000,
            0x882c10fa, 0x402664f4, 0x00000000, 0x00000000, 0x83cd3723,
            0xc02c8342, 0x00000000, 0xc0000000, 0x55e6c23d, 0x403226e3,
            0x55555555, 0x40055555, 0x34451939, 0xc0371c96, 0xaaaaaaab,
            0xc00aaaaa, 0x0e157de0, 0x403d6d3d, 0x11111111, 0x40111111,
            0xa738201f, 0xc042bbce, 0x05b05b06, 0xc015b05b, 0x452b75e3,
            0x4047da36, 0x1ba1ba1c, 0x401ba1ba, 0x00000000, 0xbff00000,
            0x00000000, 0x00000000, 0x00000000, 0x40000000, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0xc7ab4d5a, 0xc0085e24, 0x00000000, 0x00000000, 0xe93ea75d,
            0x400b963d, 0x00000000, 0x00000000, 0x94a7f25a, 0xc00f37e2,
            0x4b6261cb, 0xbff5f984, 0x5a9dd812, 0x4011aab0, 0x74c30018,
            0x3ffaf5a5, 0x7f2ce8e3, 0xc013fe8b, 0xfe8e54fa, 0xbffd7334,
            0x670d618d, 0x4016a10c, 0x4db97058, 0x4000e012, 0x24df44dd,
            0xc0199c5f, 0x697d6ece, 0xc003006e, 0x83298b82, 0x401cfc4d,
            0x19d490d6, 0x40058c19, 0x2ae42850, 0xbfea4300, 0x118e20e6,
            0x3c7a6db8, 0x00000000, 0x40000000, 0xe33345b8, 0xbfd4e526,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x2b2c49d0,
            0xbff2de9c, 0x00000000, 0x00000000, 0x2655bc98, 0x3ff33e58,
            0x00000000, 0x00000000, 0xff691fa2, 0xbff3972e, 0xe93463bd,
            0xbfeeed87, 0x070e10a0, 0x3ff3f5b2, 0xf4d790a4, 0x3ff20c10,
            0xa04e8ea3, 0xbff4541a, 0x386accd3, 0xbff1369e, 0x222a66dd,
            0x3ff4b521, 0x22a9777e, 0x3ff20817, 0x52a04a6e, 0xbff5178f,
            0xddaa0031, 0xbff22137, 0x4447d47c, 0x3ff57c01, 0x1e9c7f1d,
            0x3ff29311, 0x2ab7f990, 0xbfe561b8, 0x209c7df1, 0xbc87a8c5,
            0x00000000, 0x3ff00000, 0x4170bcc6, 0x3fdc92d8, 0x00000000,
            0x00000000, 0x00000000, 0x00000000, 0xcc03e501, 0xbfdff10f,
            0x00000000, 0x00000000, 0x44a4e845, 0x3fddb63b, 0x00000000,
            0x00000000, 0x3768ad9f, 0xbfdb72a4, 0x3dd01cca, 0xbfe5fdb9,
            0xa61d2811, 0x3fd972b2, 0x5645ad0b, 0x3fe977f9, 0xd013b3ab,
            0xbfd78ca3, 0xbf0bf914, 0xbfe4f192, 0x4d53e730, 0x3fd5d060,
            0x3f8b9000, 0x3fe49933, 0xe2b82f08, 0xbfd4322a, 0x5936a835,
            0xbfe27ae1, 0xb1c61c9b, 0x3fd2b3fb, 0xef478605, 0x3fe1659e,
            0x190834ec, 0xbfe11ab7, 0xcdb625ea, 0x3c8e564b, 0x00000000,
            0x3ff00000, 0xb07217e3, 0x3fd248f1, 0x00000000, 0x00000000,
            0x00000000, 0x00000000, 0x56f37042, 0xbfccfc56, 0x00000000,
            0x00000000, 0xaa563951, 0x3fc90125, 0x00000000, 0x00000000,
            0x3d0e7c5d, 0xbfc50533, 0x9bed9b2e, 0xbfdf0ed9, 0x5fe7c47c,
            0x3fc1f250, 0x96c125e5, 0x3fe2edd9, 0x5a02bbd8, 0xbfbe5c71,
            0x86362c20, 0xbfda08b7, 0x4b4435ed, 0x3fb9d342, 0x4b494091,
            0x3fd911bd, 0xb56658be, 0xbfb5e4c7, 0x93a2fd76, 0xbfd3c092,
            0xda271794, 0x3fb29910, 0x3303df2b, 0x3fd189be, 0x99fcef32,
            0xbfda8279, 0xb68c1467, 0xbc708b2f, 0x00000000, 0x3ff00000,
            0x980c4337, 0x3fc5f619, 0x00000000, 0x00000000, 0x00000000,
            0x00000000, 0x9314533e, 0xbfbb8ec5, 0x00000000, 0x00000000,
            0x09aa36d0, 0x3fb6d3f4, 0x00000000, 0x00000000, 0xdcb427fd,
            0xbfb13950, 0xd87ab0bb, 0xbfd5335e, 0xce0ae8a5, 0x3fabb382,
            0x79143126, 0x3fddba41, 0x5f2b28d4, 0xbfa552f1, 0x59f21a6d,
            0xbfd015ab, 0x22c27d95, 0x3fa0e984, 0xe19fc6aa, 0x3fd0576c,
            0x8f2c2950, 0xbf9a4898, 0xc0b3f22c, 0xbfc59462, 0x1883a4b8,
            0x3f94b61c, 0x3f838640, 0x3fc30eb8, 0x355c63dc, 0xbfd36a08,
            0x1dce993d, 0x3c6d704d, 0x00000000, 0x3ff00000, 0x2b82ab63,
            0x3fb78e92, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
            0x5a279ea3, 0xbfaa3407, 0x00000000, 0x00000000, 0x432d65fa,
            0x3fa70153, 0x00000000, 0x00000000, 0x891a4602, 0xbf9d03ef,
            0xd62ca5f8, 0xbfca77d9, 0xb35f4628, 0x3f97a265, 0x433258fa,
            0x3fd8cf51, 0xb58fd909, 0xbf8f88e3, 0x01771cea, 0xbfc2b154,
            0xf3562f8e, 0x3f888f57, 0xc028a723, 0x3fc7370f, 0x20b7f9f0,
            0xbf80f44c, 0x214368e9, 0xbfb6dfaa, 0x28891863, 0x3f79b4b6,
            0x172dbbf0, 0x3fb6cb8e, 0xe0553158, 0xbfc975f5, 0x593fe814,
            0xbc2ef5d3, 0x00000000, 0x3ff00000, 0x03dec550, 0x3fa44203,
            0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x4e435f9b,
            0xbf953f83, 0x00000000, 0x00000000, 0x3c6e8e46, 0x3f9b74ea,
            0x00000000, 0x00000000, 0xda5b7511, 0xbf85ad63, 0xdc230b9b,
            0xbfb97558, 0x26cb3788, 0x3f881308, 0x76fc4985, 0x3fd62ac9,
            0x77bb08ba, 0xbf757c85, 0xb6247521, 0xbfb1381e, 0x5922170c,
            0x3f754e95, 0x8746482d, 0x3fc27f83, 0x11055b30, 0xbf64e391,
            0x3e666320, 0xbfa3e609, 0x0de9dae3, 0x3f6301df, 0x1f1dca06,
            0x3fafa8ae, 0x8c5b2da2, 0xbfb936bb, 0x4e88f7a5, 0xbc587d05,
            0x00000000, 0x3ff00000, 0xa8935dd9, 0x3f83dde2, 0x00000000,
            0x00000000, 0x00000000, 0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant mask35 = pointerConstant(16, new int[]{
            // @formatter:off
            0xfffc0000, 0xffffffff, 0x00000000, 0x00000000
            // @formatter:on
    });

    private ArrayDataPointerConstant q11 = pointerConstant(16, new int[]{
            // @formatter:off
            0xb8fe4d77, 0x3f82609a
            // @formatter:on
    });

    private ArrayDataPointerConstant q9 = pointerConstant(16, new int[]{
            // @formatter:off
            0xbf847a43, 0x3f9664a0
            // @formatter:on
    });

    private ArrayDataPointerConstant q7 = pointerConstant(16, new int[]{
            // @formatter:off
            0x52c4c8ab, 0x3faba1ba
            // @formatter:on
    });

    private ArrayDataPointerConstant q5 = pointerConstant(16, new int[]{
            // @formatter:off
            0x11092746, 0x3fc11111
            // @formatter:on
    });

    private ArrayDataPointerConstant q3 = pointerConstant(16, new int[]{
            // @formatter:off
            0x55555612, 0x3fd55555
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
            0x00000000, 0x3fe921fb,
    });
    private ArrayDataPointerConstant pi48 = pointerConstant(8, new int[]{
            0x4611a626, 0x3e85110b
            // @formatter:on
    });

    private ArrayDataPointerConstant qq2 = pointerConstant(8, new int[]{
            // @formatter:off
            0x676733af, 0x3d32e7b9
            // @formatter:on
    });

    private ArrayDataPointerConstant one = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x3ff00000
            // @formatter:on
    });

    private ArrayDataPointerConstant twoPow55 = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x43600000
            // @formatter:on
    });

    private ArrayDataPointerConstant twoPowM55 = pointerConstant(4, new int[]{
            // @formatter:off
            0x00000000, 0x3c800000
            // @formatter:on
    });

    private ArrayDataPointerConstant negZero = pointerConstant(4, new int[]{
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

        masm.push(rbx);
        masm.subq(rsp, 16);
        masm.movsd(new AMD64Address(rsp, 8), xmm0);

        masm.pextrw(rax, xmm0, 3);
        masm.andl(rax, 32767);
        masm.subl(rax, 16314);
        masm.cmpl(rax, 270);
        masm.jcc(AMD64Assembler.ConditionFlag.Above, block0);
        masm.movdqu(xmm5, recordExternalAddress(crb, onehalf));        // 0x00000000, 0x3fe00000,
                                                                       // 0x00000000, 0x3fe00000
        masm.movdqu(xmm6, recordExternalAddress(crb, mul16));          // 0x00000000, 0x40300000,
                                                                       // 0x00000000, 0x3ff00000
        masm.unpcklpd(xmm0, xmm0);
        masm.movdqu(xmm4, recordExternalAddress(crb, signMask));       // 0x00000000, 0x80000000,
                                                                       // 0x00000000, 0x80000000
        masm.andpd(xmm4, xmm0);
        masm.movdqu(xmm1, recordExternalAddress(crb, pi32Inv));        // 0x6dc9c883, 0x3fe45f30,
                                                                       // 0x6dc9c883, 0x40245f30
        masm.mulpd(xmm1, xmm0);
        masm.por(xmm5, xmm4);
        masm.addpd(xmm1, xmm5);
        masm.movdqu(xmm7, xmm1);
        masm.unpckhpd(xmm7, xmm7);
        masm.cvttsd2sil(rdx, xmm7);
        masm.cvttpd2dq(xmm1, xmm1);
        masm.cvtdq2pd(xmm1, xmm1);
        masm.mulpd(xmm1, xmm6);
        masm.movdqu(xmm3, recordExternalAddress(crb, p1));             // 0x54444000, 0x3fb921fb,
                                                                       // 0x54440000, 0x3fb921fb
        masm.movq(xmm5, recordExternalAddress(crb, qq2));              // 0x676733af, 0x3d32e7b9
        masm.addq(rdx, 469248);
        masm.movdqu(xmm4, recordExternalAddress(crb, p2));             // 0x67674000, 0xbd32e7b9,
                                                                       // 0x4c4c0000, 0x3d468c23
        masm.mulpd(xmm3, xmm1);
        masm.andq(rdx, 31);
        masm.mulsd(xmm5, xmm1);
        masm.movq(rcx, rdx);
        masm.mulpd(xmm4, xmm1);
        masm.shlq(rcx, 1);
        masm.subpd(xmm0, xmm3);
        masm.mulpd(xmm1, recordExternalAddress(crb, p3));              // 0x3707344a, 0x3aa8a2e0,
                                                                       // 0x03707345, 0x3ae98a2e
        masm.addq(rdx, rcx);
        masm.shlq(rcx, 2);
        masm.addq(rdx, rcx);
        masm.addsd(xmm5, xmm0);
        masm.movdqu(xmm2, xmm0);
        masm.subpd(xmm0, xmm4);
        masm.movq(xmm6, recordExternalAddress(crb, one));              // 0x00000000, 0x3ff00000
        masm.shlq(rdx, 4);
        masm.leaq(rax, recordExternalAddress(crb, ctable));
        masm.andpd(xmm5, recordExternalAddress(crb, mask35));          // 0xfffc0000, 0xffffffff,
                                                                       // 0x00000000, 0x00000000
        masm.movdqu(xmm3, xmm0);
        masm.addq(rax, rdx);
        masm.subpd(xmm2, xmm0);
        masm.unpckhpd(xmm0, xmm0);
        masm.divsd(xmm6, xmm5);
        masm.subpd(xmm2, xmm4);
        masm.movdqu(xmm7, new AMD64Address(rax, 16));
        masm.subsd(xmm3, xmm5);
        masm.mulpd(xmm7, xmm0);
        masm.subpd(xmm2, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 48));
        masm.mulpd(xmm1, xmm0);
        masm.movdqu(xmm4, new AMD64Address(rax, 96));
        masm.mulpd(xmm4, xmm0);
        masm.addsd(xmm2, xmm3);
        masm.movdqu(xmm3, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm7, new AMD64Address(rax, 0));
        masm.addpd(xmm1, new AMD64Address(rax, 32));
        masm.mulpd(xmm1, xmm0);
        masm.addpd(xmm4, new AMD64Address(rax, 80));
        masm.addpd(xmm7, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 112));
        masm.mulpd(xmm1, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm4, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 64));
        masm.mulpd(xmm1, xmm0);
        masm.addpd(xmm7, xmm1);
        masm.movdqu(xmm1, xmm3);
        masm.mulpd(xmm3, xmm0);
        masm.mulsd(xmm0, xmm0);
        masm.mulpd(xmm1, new AMD64Address(rax, 144));
        masm.mulpd(xmm4, xmm3);
        masm.movdqu(xmm3, xmm1);
        masm.addpd(xmm7, xmm4);
        masm.movdqu(xmm4, xmm1);
        masm.mulsd(xmm0, xmm7);
        masm.unpckhpd(xmm7, xmm7);
        masm.addsd(xmm0, xmm7);
        masm.unpckhpd(xmm1, xmm1);
        masm.addsd(xmm3, xmm1);
        masm.subsd(xmm4, xmm3);
        masm.addsd(xmm1, xmm4);
        masm.movdqu(xmm4, xmm2);
        masm.movq(xmm7, new AMD64Address(rax, 144));
        masm.unpckhpd(xmm2, xmm2);
        masm.addsd(xmm7, new AMD64Address(rax, 152));
        masm.mulsd(xmm7, xmm2);
        masm.addsd(xmm7, new AMD64Address(rax, 136));
        masm.addsd(xmm7, xmm1);
        masm.addsd(xmm0, xmm7);
        masm.movq(xmm7, recordExternalAddress(crb, one));              // 0x00000000, 0x3ff00000
        masm.mulsd(xmm4, xmm6);
        masm.movq(xmm2, new AMD64Address(rax, 168));
        masm.andpd(xmm2, xmm6);
        masm.mulsd(xmm5, xmm2);
        masm.mulsd(xmm6, new AMD64Address(rax, 160));
        masm.subsd(xmm7, xmm5);
        masm.subsd(xmm2, new AMD64Address(rax, 128));
        masm.subsd(xmm7, xmm4);
        masm.mulsd(xmm7, xmm6);
        masm.movdqu(xmm4, xmm3);
        masm.subsd(xmm3, xmm2);
        masm.addsd(xmm2, xmm3);
        masm.subsd(xmm4, xmm2);
        masm.addsd(xmm0, xmm4);
        masm.subsd(xmm0, xmm7);
        masm.addsd(xmm0, xmm3);
        masm.jmp(block14);

        masm.bind(block0);
        masm.jcc(AMD64Assembler.ConditionFlag.Greater, block1);
        masm.pextrw(rax, xmm0, 3);
        masm.movl(rdx, rax);
        masm.andl(rax, 32752);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block2);
        masm.andl(rdx, 32767);
        masm.cmpl(rdx, 15904);
        masm.jcc(AMD64Assembler.ConditionFlag.Below, block3);
        masm.movdqu(xmm2, xmm0);
        masm.movdqu(xmm3, xmm0);
        masm.movq(xmm1, recordExternalAddress(crb, q11));              // 0xb8fe4d77, 0x3f82609a
        masm.mulsd(xmm2, xmm0);
        masm.mulsd(xmm3, xmm2);
        masm.mulsd(xmm1, xmm2);
        masm.addsd(xmm1, recordExternalAddress(crb, q9));              // 0xbf847a43, 0x3f9664a0
        masm.mulsd(xmm1, xmm2);
        masm.addsd(xmm1, recordExternalAddress(crb, q7));              // 0x52c4c8ab, 0x3faba1ba
        masm.mulsd(xmm1, xmm2);
        masm.addsd(xmm1, recordExternalAddress(crb, q5));              // 0x11092746, 0x3fc11111
        masm.mulsd(xmm1, xmm2);
        masm.addsd(xmm1, recordExternalAddress(crb, q3));              // 0x55555612, 0x3fd55555
        masm.mulsd(xmm1, xmm3);
        masm.addsd(xmm0, xmm1);
        masm.jmp(block14);

        masm.bind(block3);
        masm.movq(xmm3, recordExternalAddress(crb, twoPow55));         // 0x00000000, 0x43600000
        masm.mulsd(xmm3, xmm0);
        masm.addsd(xmm0, xmm3);
        masm.mulsd(xmm0, recordExternalAddress(crb, twoPowM55));       // 0x00000000, 0x3c800000
        masm.jmp(block14);

        masm.bind(block2);
        masm.movdqu(xmm1, xmm0);
        masm.mulsd(xmm1, xmm1);
        masm.jmp(block14);

        masm.bind(block1);
        masm.pextrw(rax, xmm0, 3);
        masm.andl(rax, 32752);
        masm.cmpl(rax, 32752);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block4);
        masm.pextrw(rcx, xmm0, 3);
        masm.andl(rcx, 32752);
        masm.subl(rcx, 16224);
        masm.shrl(rcx, 7);
        masm.andl(rcx, 65532);
        masm.leaq(r11, recordExternalAddress(crb, piInvTable));
        masm.addq(rcx, r11);
        masm.movdq(rax, xmm0);
        masm.movl(r10, new AMD64Address(rcx, 20));
        masm.movl(r8, new AMD64Address(rcx, 24));
        masm.movl(rdx, rax);
        masm.shrq(rax, 21);
        masm.orl(rax, Integer.MIN_VALUE);
        masm.shrl(rax, 11);
        masm.movl(r9, r10);
        masm.imulq(r10, rdx);
        masm.imulq(r9, rax);
        masm.imulq(r8, rax);
        masm.movl(rsi, new AMD64Address(rcx, 16));
        masm.movl(rdi, new AMD64Address(rcx, 12));
        masm.movl(r11, r10);
        masm.shrq(r10, 32);
        masm.addq(r9, r10);
        masm.addq(r11, r8);
        masm.movl(r8, r11);
        masm.shrq(r11, 32);
        masm.addq(r9, r11);
        masm.movl(r10, rsi);
        masm.imulq(rsi, rdx);
        masm.imulq(r10, rax);
        masm.movl(r11, rdi);
        masm.imulq(rdi, rdx);
        masm.movl(rbx, rsi);
        masm.shrq(rsi, 32);
        masm.addq(r9, rbx);
        masm.movl(rbx, r9);
        masm.shrq(r9, 32);
        masm.addq(r10, rsi);
        masm.addq(r10, r9);
        masm.shlq(rbx, 32);
        masm.orq(r8, rbx);
        masm.imulq(r11, rax);
        masm.movl(r9, new AMD64Address(rcx, 8));
        masm.movl(rsi, new AMD64Address(rcx, 4));
        masm.movl(rbx, rdi);
        masm.shrq(rdi, 32);
        masm.addq(r10, rbx);
        masm.movl(rbx, r10);
        masm.shrq(r10, 32);
        masm.addq(r11, rdi);
        masm.addq(r11, r10);
        masm.movq(rdi, r9);
        masm.imulq(r9, rdx);
        masm.imulq(rdi, rax);
        masm.movl(r10, r9);
        masm.shrq(r9, 32);
        masm.addq(r11, r10);
        masm.movl(r10, r11);
        masm.shrq(r11, 32);
        masm.addq(rdi, r9);
        masm.addq(rdi, r11);
        masm.movq(r9, rsi);
        masm.imulq(rsi, rdx);
        masm.imulq(r9, rax);
        masm.shlq(r10, 32);
        masm.orq(r10, rbx);
        masm.movl(rax, new AMD64Address(rcx, 0));
        masm.movl(r11, rsi);
        masm.shrq(rsi, 32);
        masm.addq(rdi, r11);
        masm.movl(r11, rdi);
        masm.shrq(rdi, 32);
        masm.addq(r9, rsi);
        masm.addq(r9, rdi);
        masm.imulq(rdx, rax);
        masm.pextrw(rbx, xmm0, 3);
        masm.leaq(rdi, recordExternalAddress(crb, piInvTable));
        masm.subq(rcx, rdi);
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
        masm.addq(r9, rdx);
        masm.movl(rdx, rcx);
        masm.addl(rdx, 32);
        masm.cmpl(rcx, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Less, block5);
        masm.negl(rcx);
        masm.addl(rcx, 29);
        masm.shll(r9);
        masm.movl(rdi, r9);
        masm.andl(r9, 1073741823);
        masm.testl(r9, 536870912);
        masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, block6);
        masm.shrl(r9);
        masm.movl(rbx, 0);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);

        masm.bind(block7);

        masm.bind(block8);
        masm.cmpq(r9, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block9);

        masm.bind(block10);
        masm.bsrq(r11, r9);
        masm.movl(rcx, 29);
        masm.subl(rcx, r11);
        masm.jcc(AMD64Assembler.ConditionFlag.LessEqual, block11);
        masm.shlq(r9);
        masm.movq(rax, r10);
        masm.shlq(r10);
        masm.addl(rdx, rcx);
        masm.negl(rcx);
        masm.addl(rcx, 64);
        masm.shrq(rax);
        masm.shrq(r8);
        masm.orq(r9, rax);
        masm.orq(r10, r8);

        masm.bind(block12);
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
        masm.movq(xmm2, recordExternalAddress(crb, pi4));              // 0x00000000, 0x3fe921fb,
                                                                       // 0x4611a626, 0x3e85110b
        masm.movq(xmm7, recordExternalAddress(crb, pi48));             // 0x3fe921fb, 0x4611a626,
                                                                       // 0x3e85110b
        masm.xorpd(xmm5, xmm5);
        masm.subl(rdx, 1008);
        masm.pinsrw(xmm5, rdx, 3);
        masm.mulsd(xmm0, xmm4);
        masm.shll(rsi, 16);
        masm.sarl(rsi, 31);
        masm.mulsd(xmm3, xmm5);
        masm.movdqu(xmm1, xmm0);
        masm.mulsd(xmm0, xmm2);
        masm.shrl(rdi, 30);
        masm.addsd(xmm1, xmm3);
        masm.mulsd(xmm3, xmm2);
        masm.addl(rdi, rsi);
        masm.xorl(rdi, rsi);
        masm.mulsd(xmm7, xmm1);
        masm.movl(rax, rdi);
        masm.addsd(xmm7, xmm3);
        masm.movdqu(xmm2, xmm0);
        masm.addsd(xmm0, xmm7);
        masm.subsd(xmm2, xmm0);
        masm.addsd(xmm7, xmm2);
        masm.movdqu(xmm1, recordExternalAddress(crb, pi32Inv));        // 0x6dc9c883, 0x3fe45f30,
                                                                       // 0x6dc9c883, 0x40245f30
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm0, xmm0);
        } else {
            masm.movlhps(xmm0, xmm0);
        }
        masm.movdqu(xmm4, recordExternalAddress(crb, signMask));       // 0x00000000, 0x80000000,
                                                                       // 0x00000000, 0x80000000
        masm.andpd(xmm4, xmm0);
        masm.mulpd(xmm1, xmm0);
        if (masm.supports(AMD64.CPUFeature.SSE3)) {
            masm.movddup(xmm7, xmm7);
        } else {
            masm.movlhps(xmm7, xmm7);
        }
        masm.movdqu(xmm5, recordExternalAddress(crb, onehalf));        // 0x00000000, 0x3fe00000,
                                                                       // 0x00000000, 0x3fe00000
        masm.movdqu(xmm6, recordExternalAddress(crb, mul16));          // 0x00000000, 0x40300000,
                                                                       // 0x00000000, 0x3ff00000
        masm.por(xmm5, xmm4);
        masm.addpd(xmm1, xmm5);
        masm.movdqu(xmm5, xmm1);
        masm.unpckhpd(xmm5, xmm5);
        masm.cvttsd2sil(rdx, xmm5);
        masm.cvttpd2dq(xmm1, xmm1);
        masm.cvtdq2pd(xmm1, xmm1);
        masm.mulpd(xmm1, xmm6);
        masm.movdqu(xmm3, recordExternalAddress(crb, p1));             // 0x54444000, 0x3fb921fb,
                                                                       // 0x54440000, 0x3fb921fb
        masm.movq(xmm5, recordExternalAddress(crb, qq2));              // 0x676733af, 0x3d32e7b9
        masm.shll(rax, 4);
        masm.addl(rdx, 469248);
        masm.movdqu(xmm4, recordExternalAddress(crb, p2));             // 0x67674000, 0xbd32e7b9,
                                                                       // 0x4c4c0000, 0x3d468c23
        masm.mulpd(xmm3, xmm1);
        masm.addl(rdx, rax);
        masm.andl(rdx, 31);
        masm.mulsd(xmm5, xmm1);
        masm.movl(rcx, rdx);
        masm.mulpd(xmm4, xmm1);
        masm.shll(rcx, 1);
        masm.subpd(xmm0, xmm3);
        masm.mulpd(xmm1, recordExternalAddress(crb, p3));              // 0x3707344a, 0x3aa8a2e0,
                                                                       // 0x03707345, 0x3ae98a2e
        masm.addl(rdx, rcx);
        masm.shll(rcx, 2);
        masm.addl(rdx, rcx);
        masm.addsd(xmm5, xmm0);
        masm.movdqu(xmm2, xmm0);
        masm.subpd(xmm0, xmm4);
        masm.movq(xmm6, recordExternalAddress(crb, one));              // 0x00000000, 0x3ff00000
        masm.shll(rdx, 4);
        masm.leaq(rax, recordExternalAddress(crb, ctable));
        masm.andpd(xmm5, recordExternalAddress(crb, mask35));          // 0xfffc0000, 0xffffffff,
                                                                       // 0x00000000, 0x00000000
        masm.movdqu(xmm3, xmm0);
        masm.addq(rax, rdx);
        masm.subpd(xmm2, xmm0);
        masm.unpckhpd(xmm0, xmm0);
        masm.divsd(xmm6, xmm5);
        masm.subpd(xmm2, xmm4);
        masm.subsd(xmm3, xmm5);
        masm.subpd(xmm2, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 48));
        masm.addpd(xmm2, xmm7);
        masm.movdqu(xmm7, new AMD64Address(rax, 16));
        masm.mulpd(xmm7, xmm0);
        masm.movdqu(xmm4, new AMD64Address(rax, 96));
        masm.mulpd(xmm1, xmm0);
        masm.mulpd(xmm4, xmm0);
        masm.addsd(xmm2, xmm3);
        masm.movdqu(xmm3, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm7, new AMD64Address(rax, 0));
        masm.addpd(xmm1, new AMD64Address(rax, 32));
        masm.mulpd(xmm1, xmm0);
        masm.addpd(xmm4, new AMD64Address(rax, 80));
        masm.addpd(xmm7, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 112));
        masm.mulpd(xmm1, xmm0);
        masm.mulpd(xmm0, xmm0);
        masm.addpd(xmm4, xmm1);
        masm.movdqu(xmm1, new AMD64Address(rax, 64));
        masm.mulpd(xmm1, xmm0);
        masm.addpd(xmm7, xmm1);
        masm.movdqu(xmm1, xmm3);
        masm.mulpd(xmm3, xmm0);
        masm.mulsd(xmm0, xmm0);
        masm.mulpd(xmm1, new AMD64Address(rax, 144));
        masm.mulpd(xmm4, xmm3);
        masm.movdqu(xmm3, xmm1);
        masm.addpd(xmm7, xmm4);
        masm.movdqu(xmm4, xmm1);
        masm.mulsd(xmm0, xmm7);
        masm.unpckhpd(xmm7, xmm7);
        masm.addsd(xmm0, xmm7);
        masm.unpckhpd(xmm1, xmm1);
        masm.addsd(xmm3, xmm1);
        masm.subsd(xmm4, xmm3);
        masm.addsd(xmm1, xmm4);
        masm.movdqu(xmm4, xmm2);
        masm.movq(xmm7, new AMD64Address(rax, 144));
        masm.unpckhpd(xmm2, xmm2);
        masm.addsd(xmm7, new AMD64Address(rax, 152));
        masm.mulsd(xmm7, xmm2);
        masm.addsd(xmm7, new AMD64Address(rax, 136));
        masm.addsd(xmm7, xmm1);
        masm.addsd(xmm0, xmm7);
        masm.movq(xmm7, recordExternalAddress(crb, one));              // 0x00000000, 0x3ff00000
        masm.mulsd(xmm4, xmm6);
        masm.movq(xmm2, new AMD64Address(rax, 168));
        masm.andpd(xmm2, xmm6);
        masm.mulsd(xmm5, xmm2);
        masm.mulsd(xmm6, new AMD64Address(rax, 160));
        masm.subsd(xmm7, xmm5);
        masm.subsd(xmm2, new AMD64Address(rax, 128));
        masm.subsd(xmm7, xmm4);
        masm.mulsd(xmm7, xmm6);
        masm.movdqu(xmm4, xmm3);
        masm.subsd(xmm3, xmm2);
        masm.addsd(xmm2, xmm3);
        masm.subsd(xmm4, xmm2);
        masm.addsd(xmm0, xmm4);
        masm.subsd(xmm0, xmm7);
        masm.addsd(xmm0, xmm3);
        masm.jmp(block14);

        masm.bind(block9);
        masm.addl(rdx, 64);
        masm.movq(r9, r10);
        masm.movq(r10, r8);
        masm.movl(r8, 0);
        masm.cmpq(r9, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, block10);
        masm.addl(rdx, 64);
        masm.movq(r9, r10);
        masm.movq(r10, r8);
        masm.cmpq(r9, 0);
        masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, block10);
        masm.jmp(block12);

        masm.bind(block11);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, block12);
        masm.negl(rcx);
        masm.shrq(r10);
        masm.movq(rax, r9);
        masm.shrq(r9);
        masm.subl(rdx, rcx);
        masm.negl(rcx);
        masm.addl(rcx, 64);
        masm.shlq(rax);
        masm.orq(r10, rax);
        masm.jmp(block12);

        masm.bind(block5);
        masm.notl(rcx);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);
        masm.shlq(r9);
        masm.movq(rdi, r9);
        masm.testl(r9, Integer.MIN_VALUE);
        masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, block13);
        masm.shrl(r9);
        masm.movl(rbx, 0);
        masm.shrq(rdi, 2);
        masm.jmp(block8);

        masm.bind(block6);
        masm.shrl(r9);
        masm.movl(rbx, 1073741824);
        masm.shrl(rbx);
        masm.shlq(r9, 32);
        masm.orq(r9, r11);
        masm.shlq(rbx, 32);
        masm.addl(rdi, 1073741824);
        masm.movl(rcx, 0);
        masm.movl(r11, 0);
        masm.subq(rcx, r8);
        masm.sbbq(r11, r10);
        masm.sbbq(rbx, r9);
        masm.movq(r8, rcx);
        masm.movq(r10, r11);
        masm.movq(r9, rbx);
        masm.movl(rbx, 32768);
        masm.jmp(block7);

        masm.bind(block13);
        masm.shrl(r9);
        masm.movq(rbx, 0x100000000L);
        masm.shrq(rbx);
        masm.movl(rcx, 0);
        masm.movl(r11, 0);
        masm.subq(rcx, r8);
        masm.sbbq(r11, r10);
        masm.sbbq(rbx, r9);
        masm.movq(r8, rcx);
        masm.movq(r10, r11);
        masm.movq(r9, rbx);
        masm.movl(rbx, 32768);
        masm.shrq(rdi, 2);
        masm.addl(rdi, 1073741824);
        masm.jmp(block8);

        masm.bind(block4);
        masm.movq(xmm0, new AMD64Address(rsp, 8));
        masm.mulsd(xmm0, recordExternalAddress(crb, negZero));         // 0x00000000, 0x80000000
        masm.movq(new AMD64Address(rsp, 0), xmm0);

        masm.bind(block14);
        masm.addq(rsp, 16);
        masm.pop(rbx);
    }
}
