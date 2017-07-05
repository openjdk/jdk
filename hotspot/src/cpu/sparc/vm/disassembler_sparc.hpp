/*
 * Copyright 1997-1999 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// The disassembler prints out sparc code annotated
// with Java specific information.

class Disassembler {
#ifndef PRODUCT
 private:
  // points to the library.
  static void*    _library;
  // points to the print_insn_sparc function.
  static dll_func _print_insn_sparc;
  // tries to load library and return whether it succedded.
  static bool load_library();
  // decodes one instruction and return the start of the next instruction.
  static address decode_instruction(address start, DisassemblerEnv* env);
#endif
 public:
  static void decode(CodeBlob *cb,               outputStream* st = NULL) PRODUCT_RETURN;
  static void decode(nmethod* nm,                outputStream* st = NULL) PRODUCT_RETURN;
  static void decode(u_char* begin, u_char* end, outputStream* st = NULL) PRODUCT_RETURN;
};

//Reconciliation History
// 1.9 98/04/29 10:45:51 disassembler_i486.hpp
// 1.10 98/05/11 16:47:20 disassembler_i486.hpp
// 1.12 99/06/22 16:37:37 disassembler_i486.hpp
// 1.13 99/08/06 10:09:04 disassembler_i486.hpp
//End
