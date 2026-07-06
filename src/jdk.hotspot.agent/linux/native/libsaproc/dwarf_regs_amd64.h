/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

#ifndef DWARF_REGS_AMD64_H
#define DWARF_REGS_AMD64_H

#define THREAD_CONTEXT_CLASS "sun/jvm/hotspot/debugger/amd64/AMD64ThreadContext"

/*
 * from System V Application Binary Interface
 *      AMD64 Architecture Processor Supplement
 *   https://refspecs.linuxbase.org/elf/x86_64-abi-0.99.pdf
 *       Figure 3.36: DWARF Register Number Mapping
 */
#define DWARF_REGLIST \
  DWARF_REG(RAX,  0) \
  DWARF_REG(RDX,  1) \
  DWARF_REG(RCX,  2) \
  DWARF_REG(RBX,  3) \
  DWARF_REG(RSI,  4) \
  DWARF_REG(RDI,  5) \
  DWARF_REG(RBP,  6) \
  DWARF_REG(RSP,  7) \
  DWARF_REG(R8,   8) \
  DWARF_REG(R9,   9) \
  DWARF_REG(R10, 10) \
  DWARF_REG(R11, 11) \
  DWARF_REG(R12, 12) \
  DWARF_REG(R13, 13) \
  DWARF_REG(R14, 14) \
  DWARF_REG(R15, 15)

#define DWARF_PSEUDO_REGLIST \
  DWARF_REG(RA,  16)

/* Aliases */
#define BP RBP

#endif
