/*
 * Copyright (c) 2025, IBM Corporation. and/or its affiliates. All rights reserved.
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

#if defined(S390) && !defined(ZERO)

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "unittest.hpp"

// ---------------------------------------------------------------------------
// Tests for Assembler::is_z_illtrap
//
// The three emitter forms and what they write into memory (big-endian):
//
//   z_illtrap()                -> 0x00 0x00   (id == 0)
//   z_illtrap(int id)          -> 0x00 <id>   (e.g. 0x00 0xba)
//   z_illtrap_eyecatcher(...)  -> ends with z_illtrap(xpattern) -> 0x00 <xp>
//
// All forms share: high byte (first byte in memory) == 0x00.
// is_z_illtrap must recognise all of them, not just 0x0000.
// ---------------------------------------------------------------------------

TEST(AssemblerS390, is_z_illtrap_no_id) {
  // z_illtrap() emits 0x0000 — must be detected.
  uint8_t buf[] = { 0x00, 0x00 };
  EXPECT_TRUE(Assembler::is_z_illtrap((address)buf))
      << "z_illtrap() (0x0000) must be recognised as illtrap";
}

TEST(AssemblerS390, is_z_illtrap_with_id) {
  // z_illtrap(id) emits 0x00<id> — must also be detected.
  // Tests a representative set of ids actually used in the source.
  const uint8_t ids[] = { 0x22, 0x55, 0x66, 0x99, 0xba, 0xd1, 0xd2, 0xee };
  for (uint8_t id : ids) {
    uint8_t buf[] = { 0x00, id };
    EXPECT_TRUE(Assembler::is_z_illtrap((address)buf))
        << "z_illtrap(0x" << std::hex << (int)id << ") must be recognised as illtrap";
  }
}

TEST(AssemblerS390, is_z_illtrap_false_positive) {
  // A non-zero high byte must NOT be recognised as an illtrap.
  uint8_t buf[] = { 0x07, 0x00 };  // BCR 0,0  (a NOP — not an illtrap)
  EXPECT_FALSE(Assembler::is_z_illtrap((address)buf))
      << "BCR 0,0 (0x0700) must not be recognised as illtrap";
}

#endif // S390 && !ZERO

