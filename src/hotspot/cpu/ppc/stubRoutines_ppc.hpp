/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2018, SAP SE. All rights reserved.
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

#ifndef CPU_PPC_VM_STUBROUTINES_PPC_HPP
#define CPU_PPC_VM_STUBROUTINES_PPC_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool returns_to_call_stub(address return_pc) { return return_pc == _call_stub_return_address; }

enum platform_dependent_constants {
  code_size1 = 20000,          // simply increase if too small (assembler will crash if too small)
  code_size2 = 24000           // simply increase if too small (assembler will crash if too small)
};

// CRC32 Intrinsics.
#define CRC32_COLUMN_SIZE 256
#define CRC32_BYFOUR
#ifdef  CRC32_BYFOUR
  #define CRC32_TABLES 8
#else
  #define CRC32_TABLES 1
#endif
#define CRC32_CONSTANTS_SIZE 1084
#define CRC32_BARRET_CONSTANTS 10

class ppc64 {
 friend class StubGenerator;

 private:

  // CRC32 Intrinsics.
  static juint _crc_table[CRC32_TABLES][CRC32_COLUMN_SIZE];
  static juint _crc32c_table[CRC32_TABLES][CRC32_COLUMN_SIZE];
  static juint *_crc_constants, *_crc_barret_constants;
  static juint *_crc32c_constants, *_crc32c_barret_constants;

 public:

  // CRC32 Intrinsics.
  static void generate_load_table_addr(MacroAssembler* masm, Register table, address table_addr, uint64_t table_contents);
  static void generate_load_crc_table_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc_constants_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc_barret_constants_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc32c_table_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc32c_constants_addr(MacroAssembler* masm, Register table);
  static void generate_load_crc32c_barret_constants_addr(MacroAssembler* masm, Register table);
  static juint* generate_crc_constants(juint reverse_poly);
  static juint* generate_crc_barret_constants(juint reverse_poly);
};

#endif // CPU_PPC_VM_STUBROUTINES_PPC_HPP
