/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "cds/archiveBuilder.hpp"
#include "oops/resolvedFieldEntry.hpp"

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

#if INCLUDE_CDS
void ResolvedFieldEntry::remove_unshareable_info() {
  u2 saved_cpool_index = _cpool_index;
  memset(this, 0, sizeof(*this));
  _cpool_index = saved_cpool_index;
}

void ResolvedFieldEntry::mark_and_relocate() {
  ArchiveBuilder::current()->mark_and_relocate_to_buffered_addr(&_field_holder);
}
#endif
