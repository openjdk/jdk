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
 * Perform the vectorized add for a group of 4 quarter round operations.
 * In the ChaCha20 quarter round, there are two add ops: a += b and c += d.
 * Each parameter is a set of 4 registers representing the 4 registers
 * for the each addend in the add operation for each of the quarter rounds.
 * (e.g. for "a" it would consist of v0/v1/v2/v3).  The result of the add
 * is placed into the vectors in the "addFirst" array.
 *
 * @param addFirst array of SIMD registers representing the first addend.
 * @param addSecond array of SIMD registers representing the second addend.
 */
void MacroAssembler::cc20_qr_add4(FloatRegister (&addFirst)[4],
    FloatRegister (&addSecond)[4]) {
  for (int i = 0; i < 4; i++) {
      addv(addFirst[i], T4S, addFirst[i], addSecond[i]);
  }
}


/**
 * Perform the vectorized XOR for a group of 4 quarter round operations.
 * In the ChaCha20 quarter round, there are two XOR ops: d ^= a and b ^= c
 * Each parameter is a set of 4 registers representing the 4 registers
 * for the each element in the xor operation for each of the quarter rounds.
 * (e.g. for "a" it would consist of v0/v1/v2/v3)
 * Note: because the b ^= c ops precede a non-byte-aligned left-rotation,
 *       there is a third parameter which can take a set of scratch registers
 *       for the result, which facilitates doing the subsequent operations for
 *       the left rotation.
 *
 * @param firstElem array of SIMD registers representing the first element.
 * @param secondElem array of SIMD registers representing the second element.
 * @param result array of SIMD registers representing the destination.
 *        May be the same as firstElem or secondElem, or a separate array.
 */
void MacroAssembler::cc20_qr_xor4(FloatRegister (&firstElem)[4],
    FloatRegister (&secondElem)[4], FloatRegister (&result)[4]) {
  for (int i = 0; i < 4; i++) {
    eor(result[i], T16B, firstElem[i], secondElem[i]);
  }
}

/**
 * Perform the vectorized left-rotation on 32-bit lanes for a group of
 * 4 quarter round operations.
 * Each parameter is a set of 4 registers representing the 4 registers
 * for the each element in the source and destination for each of the quarter
 * rounds (e.g. for "d" it would consist of v12/v13/v14/v15 on columns and
 * v15/v12/v13/v14 on diagonal alignments).
 *
 * @param sourceReg array of SIMD registers representing the source
 * @param destReg array of SIMD registers representing the destination
 * @param bits the distance of the rotation in bits, must be 16/12/8/7 per
 *        the ChaCha20 specification.
 */
void MacroAssembler::cc20_qr_lrot4(FloatRegister (&sourceReg)[4],
    FloatRegister (&destReg)[4], int bits, FloatRegister table) {
  switch (bits) {
  case 16:      // reg <<<= 16, in-place swap of half-words
    for (int i = 0; i < 4; i++) {
      rev32(destReg[i], T8H, sourceReg[i]);
    }
    break;

  case 7:       // reg <<<= (12 || 7)
  case 12:      // r-shift src -> dest, l-shift src & ins to dest
    for (int i = 0; i < 4; i++) {
      ushr(destReg[i], T4S, sourceReg[i], 32 - bits);
    }

    for (int i = 0; i < 4; i++) {
      sli(destReg[i], T4S, sourceReg[i], bits);
    }
    break;

  case 8:       // reg <<<= 8, simulate left rotation with table reorg
    for (int i = 0; i < 4; i++) {
      tbl(destReg[i], T16B, sourceReg[i], 1, table);
    }
    break;

  default:
    // The caller shouldn't be sending bit rotation values outside
    // of the 16/12/8/7 as defined in the specification.
    ShouldNotReachHere();
  }
}

/**
 * Set the FloatRegisters for a 4-vector register set.  These will be used
 * during various quarter round transformations (adds, xors and left-rotations).
 * This method itself does not result in the output of any assembly
 * instructions.  It just organizes the vectors so they can be in columnar or
 * diagonal alignments.
 *
 * @param vectorSet a 4-vector array to be altered into a new alignment
 * @param stateVectors the 16-vector array that represents the current
 *        working state.  The indices of this array match up with the
 *        organization of the ChaCha20 state per RFC 7539 (e.g. stateVectors[12]
 *        would contain the vector that holds the 32-bit counter, etc.)
 * @param idx1 the index of the stateVectors array to be assigned to the
 *        first vectorSet element.
 * @param idx2 the index of the stateVectors array to be assigned to the
 *        second vectorSet element.
 * @param idx3 the index of the stateVectors array to be assigned to the
 *        third vectorSet element.
 * @param idx4 the index of the stateVectors array to be assigned to the
 *        fourth vectorSet element.
 */
void MacroAssembler::cc20_set_qr_registers(FloatRegister (&vectorSet)[4],
    const FloatRegister (&stateVectors)[16], int idx1, int idx2,
    int idx3, int idx4) {
  vectorSet[0] = stateVectors[idx1];
  vectorSet[1] = stateVectors[idx2];
  vectorSet[2] = stateVectors[idx3];
  vectorSet[3] = stateVectors[idx4];
}
