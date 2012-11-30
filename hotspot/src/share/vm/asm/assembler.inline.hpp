/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_ASM_ASSEMBLER_INLINE_HPP
#define SHARE_VM_ASM_ASSEMBLER_INLINE_HPP

#include "asm/assembler.hpp"
#include "asm/codeBuffer.hpp"
#include "compiler/disassembler.hpp"
#include "runtime/threadLocalStorage.hpp"

inline address AbstractAssembler::addr_at(int pos) const {
  return code_section()->start() + pos;
}

void AbstractAssembler::emit_int8(int8_t x)     { code_section()->emit_int8 (x); }
void AbstractAssembler::emit_int16(int16_t x)   { code_section()->emit_int16(x); }
void AbstractAssembler::emit_int32(int32_t x)   { code_section()->emit_int32(x); }
void AbstractAssembler::emit_int64(int64_t x)   { code_section()->emit_int64(x); }

void AbstractAssembler::emit_float(jfloat  x)   { code_section()->emit_float(x); }
void AbstractAssembler::emit_double(jdouble x)  { code_section()->emit_double(x); }
void AbstractAssembler::emit_address(address x) { code_section()->emit_address(x); }

inline address AbstractAssembler::inst_mark() const {
  return code_section()->mark();
}

inline void AbstractAssembler::set_inst_mark() {
  code_section()->set_mark();
}

inline void AbstractAssembler::clear_inst_mark() {
  code_section()->clear_mark();
}


inline void AbstractAssembler::relocate(RelocationHolder const& rspec, int format) {
  assert(!pd_check_instruction_mark()
         || inst_mark() == NULL || inst_mark() == code_section()->end(),
         "call relocate() between instructions");
  code_section()->relocate(code_section()->end(), rspec, format);
}


inline CodeBuffer* AbstractAssembler::code() const {
  return code_section()->outer();
}

inline int AbstractAssembler::sect() const {
  return code_section()->index();
}

inline address AbstractAssembler::pc() const {
  return code_section()->end();
}

inline int AbstractAssembler::offset() const {
  return code_section()->size();
}

inline int AbstractAssembler::locator() const {
  return CodeBuffer::locator(offset(), sect());
}

inline address AbstractAssembler::target(Label& L) {
  return code_section()->target(L, pc());
}

inline int Label::loc_pos() const {
  return CodeBuffer::locator_pos(loc());
}

inline int Label::loc_sect() const {
  return CodeBuffer::locator_sect(loc());
}

inline void Label::bind_loc(int pos, int sect) {
  bind_loc(CodeBuffer::locator(pos, sect));
}

#endif // SHARE_VM_ASM_ASSEMBLER_INLINE_HPP
