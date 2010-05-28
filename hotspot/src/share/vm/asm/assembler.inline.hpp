/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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

inline void AbstractAssembler::sync() {
  CodeSection* cs = code_section();
  guarantee(cs->start() == _code_begin, "must not shift code buffer");
  cs->set_end(_code_pos);
}

inline void AbstractAssembler::emit_byte(int x) {
  assert(isByte(x), "not a byte");
  *(unsigned char*)_code_pos = (unsigned char)x;
  _code_pos += sizeof(unsigned char);
  sync();
}


inline void AbstractAssembler::emit_word(int x) {
  *(short*)_code_pos = (short)x;
  _code_pos += sizeof(short);
  sync();
}


inline void AbstractAssembler::emit_long(jint x) {
  *(jint*)_code_pos = x;
  _code_pos += sizeof(jint);
  sync();
}

inline void AbstractAssembler::emit_address(address x) {
  *(address*)_code_pos = x;
  _code_pos += sizeof(address);
  sync();
}

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
         || inst_mark() == NULL || inst_mark() == _code_pos,
         "call relocate() between instructions");
  code_section()->relocate(_code_pos, rspec, format);
}


inline CodeBuffer* AbstractAssembler::code() const {
  return code_section()->outer();
}

inline int AbstractAssembler::sect() const {
  return code_section()->index();
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

address AbstractAssembler::address_constant(Label& L) {
  address c = NULL;
  address ptr = start_a_const(sizeof(c), sizeof(c));
  if (ptr != NULL) {
    relocate(Relocation::spec_simple(relocInfo::internal_word_type));
    *(address*)ptr = c = code_section()->target(L, ptr);
    _code_pos = ptr + sizeof(c);
    end_a_const();
  }
  return ptr;
}

address AbstractAssembler::address_table_constant(GrowableArray<Label*> labels) {
  int addressSize = sizeof(address);
  int sizeLabel = addressSize * labels.length();
  address ptr = start_a_const(sizeLabel, addressSize);

  if (ptr != NULL) {
    address *labelLoc = (address*)ptr;
    for (int i=0; i < labels.length(); i++) {
      emit_address(code_section()->target(*labels.at(i), (address)&labelLoc[i]));
      code_section()->relocate((address)&labelLoc[i], relocInfo::internal_word_type);
    }
    end_a_const();
  }
  return ptr;
}
