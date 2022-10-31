/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "runtime/stubRoutines.hpp"
#include "macroAssembler_x86.hpp"

/**
 * Provide a macro for AVX and AVX2 implementations of the ChaCha20 quarter
 * round function.
 *
 * @param aVec the SIMD register containing only the "a" values
 * @param bVec the SIMD register containing only the "b" values
 * @param cVec the SIMD register containing only the "c" values
 * @param dVec the SIMD register containing only the "d" values
 * @param scratch SIMD register used for left rotations other than 16-bit.
 * @param vector_len the length of the vector (128 and 256 bit only)
 */
void MacroAssembler::cc20_quarter_round_avx(XMMRegister aVec, XMMRegister bVec,
        XMMRegister cVec, XMMRegister dVec, XMMRegister scratch,
        int vector_len) {

    // a += b; d ^= a; d <<<= 16
    vpaddd(aVec, aVec, bVec, vector_len);
    vpxor(dVec, dVec, aVec, vector_len);
    if (vector_len == Assembler::AVX_512bit) {
        evprold(dVec, dVec, 16, vector_len);
    } else {
        vpshufhw(dVec, dVec, 0xB1, vector_len);
        vpshuflw(dVec, dVec, 0xB1, vector_len);
    }

    // c += d; b ^= c; b <<<= 12 (b << 12 | scratch >>> 20)
    vpaddd(cVec, cVec, dVec, vector_len);
    vpxor(bVec, bVec, cVec, vector_len);
    if (vector_len == Assembler::AVX_512bit) {
        evprold(bVec, bVec, 12, vector_len);
    } else {
        vpsrld(scratch, bVec, 20, vector_len);
        vpslld(bVec, bVec, 12, vector_len);
        vpor(bVec, bVec, scratch, vector_len);
    }

    // a += b; d ^= a; d <<<= 8 (d << 8 | scratch >>> 24)
    vpaddd(aVec, aVec, bVec, vector_len);
    vpxor(dVec, dVec, aVec, vector_len);
    if (vector_len == Assembler::AVX_512bit) {
        evprold(dVec, dVec, 8, vector_len);
    } else {
        vpsrld(scratch, dVec, 24, vector_len);
        vpslld(dVec, dVec, 8, vector_len);
        vpor(dVec, dVec, scratch, vector_len);
    }

    // c += d; b ^= c; b <<<= 7 (b << 7 | scratch >>> 25)
    vpaddd(cVec, cVec, dVec, vector_len);
    vpxor(bVec, bVec, cVec, vector_len);
    if (vector_len == Assembler::AVX_512bit) {
        evprold(bVec, bVec, 7, vector_len);
    } else {
        vpsrld(scratch, bVec, 25, vector_len);
        vpslld(bVec, bVec, 7, vector_len);
        vpor(bVec, bVec, scratch, vector_len);
    }
}

/**
 * Shift the b, c, and d vectors between columnar and diagonal representations.
 * Note that the "a" vector does not shift.
 *
 * @param bVec the SIMD register containing only the "b" values
 * @param cVec the SIMD register containing only the "c" values
 * @param dVec the SIMD register containing only the "d" values
 * @param vector_len the size of the SIMD register to operate upon
 * @param colToDiag true if moving columnar to diagonal, false if
 *                  moving diagonal back to columnar.
 */
void MacroAssembler::cc20_shift_lane_org(XMMRegister bVec, XMMRegister cVec,
        XMMRegister dVec, int vector_len, bool colToDiag) {
    int bShift = colToDiag ? 0x39 : 0x93;
    int cShift = 0x4E;
    int dShift = colToDiag ? 0x93 : 0x39;

    vpshufd(bVec, bVec, bShift, vector_len);
    vpshufd(cVec, cVec, cShift, vector_len);
    vpshufd(dVec, dVec, dShift, vector_len);
}

/**
 * Write 256 bytes of keystream output held in 4 AVX512 SIMD registers
 * in a quarter round parallel organization.
 *
 * @param aVec the SIMD register containing only the "a" values
 * @param bVec the SIMD register containing only the "b" values
 * @param cVec the SIMD register containing only the "c" values
 * @param dVec the SIMD register containing only the "d" values
 * @param baseAddr the register holding the base output address
 * @param baseOffset the offset from baseAddr for writes
 */
void MacroAssembler::cc20_keystream_collate_avx512(XMMRegister aVec,
        XMMRegister bVec, XMMRegister cVec, XMMRegister dVec,
        Register baseAddr, int baseOffset) {
    vextracti32x4(Address(baseAddr, baseOffset + 0), aVec, 0);
    vextracti32x4(Address(baseAddr, baseOffset + 64), aVec, 1);
    vextracti32x4(Address(baseAddr, baseOffset + 128), aVec, 2);
    vextracti32x4(Address(baseAddr, baseOffset + 192), aVec, 3);

    vextracti32x4(Address(baseAddr, baseOffset + 16), bVec, 0);
    vextracti32x4(Address(baseAddr, baseOffset + 80), bVec, 1);
    vextracti32x4(Address(baseAddr, baseOffset + 144), bVec, 2);
    vextracti32x4(Address(baseAddr, baseOffset + 208), bVec, 3);

    vextracti32x4(Address(baseAddr, baseOffset + 32), cVec, 0);
    vextracti32x4(Address(baseAddr, baseOffset + 96), cVec, 1);
    vextracti32x4(Address(baseAddr, baseOffset + 160), cVec, 2);
    vextracti32x4(Address(baseAddr, baseOffset + 224), cVec, 3);

    vextracti32x4(Address(baseAddr, baseOffset + 48), dVec, 0);
    vextracti32x4(Address(baseAddr, baseOffset + 112), dVec, 1);
    vextracti32x4(Address(baseAddr, baseOffset + 176), dVec, 2);
    vextracti32x4(Address(baseAddr, baseOffset + 240), dVec, 3);
}
