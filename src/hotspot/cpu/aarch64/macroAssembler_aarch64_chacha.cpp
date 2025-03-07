/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "macroAssembler_aarch64.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/stubRoutines.hpp"

/**
 * Perform the quarter round calculations on values contained within
 * four SIMD registers.
 *
 * @param aVec the SIMD register containing only the "a" values
 * @param bVec the SIMD register containing only the "b" values
 * @param cVec the SIMD register containing only the "c" values
 * @param dVec the SIMD register containing only the "d" values
 * @param scratch scratch SIMD register used for 12 and 7 bit left rotations
 * @param table the SIMD register used as a table for 8 bit left rotations
 */
void MacroAssembler::cc20_quarter_round(FloatRegister aVec, FloatRegister bVec,
    FloatRegister cVec, FloatRegister dVec, FloatRegister scratch,
     FloatRegister table) {

  // a += b, d ^= a, d <<<= 16
  addv(aVec, T4S, aVec, bVec);
  eor(dVec, T16B, dVec, aVec);
  rev32(dVec, T8H, dVec);

  // c += d, b ^= c, b <<<= 12
  addv(cVec, T4S, cVec, dVec);
  eor(scratch, T16B, bVec, cVec);
  ushr(bVec, T4S, scratch, 20);
  sli(bVec, T4S, scratch, 12);

  // a += b, d ^= a, d <<<= 8
  addv(aVec, T4S, aVec, bVec);
  eor(dVec, T16B, dVec, aVec);
  tbl(dVec, T16B, dVec,  1, table);

  // c += d, b ^= c, b <<<= 7
  addv(cVec, T4S, cVec, dVec);
  eor(scratch, T16B, bVec, cVec);
  ushr(bVec, T4S, scratch, 25);
  sli(bVec, T4S, scratch, 7);
}

/**
 * Shift the b, c, and d vectors between columnar and diagonal representations.
 * Note that the "a" vector does not shift.
 *
 * @param bVec the SIMD register containing only the "b" values
 * @param cVec the SIMD register containing only the "c" values
 * @param dVec the SIMD register containing only the "d" values
 * @param colToDiag true if moving columnar to diagonal, false if
 *                  moving diagonal back to columnar.
 */
void MacroAssembler::cc20_shift_lane_org(FloatRegister bVec, FloatRegister cVec,
    FloatRegister dVec, bool colToDiag) {
  int bShift = colToDiag ? 4 : 12;
  int cShift = 8;
  int dShift = colToDiag ? 12 : 4;

  ext(bVec, T16B, bVec, bVec, bShift);
  ext(cVec, T16B, cVec, cVec, cShift);
  ext(dVec, T16B, dVec, dVec, dShift);
}
