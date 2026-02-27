/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveBuilder.hpp"
#include "cppstdlib/type_traits.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"

static_assert(std::is_trivially_copyable_v<ResolvedFieldEntry>);

// Detect inadvertently introduced trailing padding.
class ResolvedFieldEntryWithExtra : public ResolvedFieldEntry {
  u1 _extra_field;
};
static_assert(sizeof(ResolvedFieldEntryWithExtra) > sizeof(ResolvedFieldEntry));

void ResolvedFieldEntry::fill_in(const fieldDescriptor& info, u1 tos_state, u1 get_code, u1 put_code) {
  set_flags(info.access_flags().is_final(), info.access_flags().is_volatile());
  _field_holder = info.field_holder();
  _field_offset = info.offset();
  _field_index = checked_cast<u2>(info.index());
  _tos_state = tos_state;

  // These must be set after the other fields
  set_bytecode(&_get_code, get_code);
  set_bytecode(&_put_code, put_code);
  assert_is_valid();
}

void ResolvedFieldEntry::print_on(outputStream* st) const {
  st->print_cr("Field Entry:");

  if (field_holder() != nullptr) {
    st->print_cr(" - Holder: " INTPTR_FORMAT " %s", p2i(field_holder()), field_holder()->external_name());
  } else {
    st->print_cr("- Holder: null");
  }
  st->print_cr(" - Offset: %d", field_offset());
  st->print_cr(" - Field Index: %d", field_index());
  st->print_cr(" - CP Index: %d", constant_pool_index());
  st->print_cr(" - TOS: %s", type2name(as_BasicType((TosState)tos_state())));
  st->print_cr(" - Is Final: %d", is_final());
  st->print_cr(" - Is Volatile: %d", is_volatile());
  st->print_cr(" - Get Bytecode: %s", Bytecodes::name((Bytecodes::Code)get_code()));
  st->print_cr(" - Put Bytecode: %s", Bytecodes::name((Bytecodes::Code)put_code()));
}

#ifdef ASSERT
void ResolvedFieldEntry::assert_is_valid() const {
  assert(field_holder()->is_instance_klass(), "should be instanceKlass");
  assert(field_offset() >= instanceOopDesc::base_offset_in_bytes(),
         "field offset out of range %d >= %d", field_offset(), instanceOopDesc::base_offset_in_bytes());
  assert(as_BasicType((TosState)tos_state()) != T_ILLEGAL, "tos_state is ILLEGAL");
  assert(_flags < (1 << (max_flag_shift + 1)), "flags are too large %d", _flags);
  assert((get_code() == 0 || get_code() == Bytecodes::_getstatic || get_code() == Bytecodes::_getfield),
         "invalid get bytecode %d", get_code());
  assert((put_code() == 0 || put_code() == Bytecodes::_putstatic || put_code() == Bytecodes::_putfield),
          "invalid put bytecode %d", put_code());
}
#endif

#if INCLUDE_CDS
void ResolvedFieldEntry::remove_unshareable_info() {
  *this = ResolvedFieldEntry(_cpool_index);
}

void ResolvedFieldEntry::mark_and_relocate() {
  ArchiveBuilder::current()->mark_and_relocate_to_buffered_addr(&_field_holder);
}
#endif
