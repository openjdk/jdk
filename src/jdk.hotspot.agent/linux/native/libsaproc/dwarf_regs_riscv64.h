/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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

#ifndef DWARF_REGS_RISCV64_H
#define DWARF_REGS_RISCV64_H

#define THREAD_CONTEXT_CLASS "sun/jvm/hotspot/debugger/riscv64/RISCV64ThreadContext"

/*
 * RISC-V ELF psABI, DWARF Register Numbers:
 *   https://riscv-non-isa.github.io/riscv-elf-psabi-doc/#_dwarf_register_numbers
 *
 * DWARF registers 1-31 correspond to x1-x31. Do not map DWARF x0 to
 * RISCV64ThreadContext.R0: that SA register holds PC, not the zero register.
 */
#define DWARF_REGLIST \
  DWARF_REG(R1,   1) \
  DWARF_REG(R2,   2) \
  DWARF_REG(R3,   3) \
  DWARF_REG(R4,   4) \
  DWARF_REG(R5,   5) \
  DWARF_REG(R6,   6) \
  DWARF_REG(R7,   7) \
  DWARF_REG(R8,   8) \
  DWARF_REG(R9,   9) \
  DWARF_REG(R10, 10) \
  DWARF_REG(R11, 11) \
  DWARF_REG(R12, 12) \
  DWARF_REG(R13, 13) \
  DWARF_REG(R14, 14) \
  DWARF_REG(R15, 15) \
  DWARF_REG(R16, 16) \
  DWARF_REG(R17, 17) \
  DWARF_REG(R18, 18) \
  DWARF_REG(R19, 19) \
  DWARF_REG(R20, 20) \
  DWARF_REG(R21, 21) \
  DWARF_REG(R22, 22) \
  DWARF_REG(R23, 23) \
  DWARF_REG(R24, 24) \
  DWARF_REG(R25, 25) \
  DWARF_REG(R26, 26) \
  DWARF_REG(R27, 27) \
  DWARF_REG(R28, 28) \
  DWARF_REG(R29, 29) \
  DWARF_REG(R30, 30) \
  DWARF_REG(R31, 31)

#define DWARF_PSEUDO_REGLIST

/* Aliases */
#define BP R8

#endif
