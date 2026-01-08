/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_BSMATTRIBUTE_INLINE_HPP
#define SHARE_OOPS_BSMATTRIBUTE_INLINE_HPP

#include "oops/bsmAttribute.hpp"

inline BSMAttributeEntry* BSMAttributeEntries::InsertionIterator::reserve_new_entry(u2 bsmi, u2 argc) {
  assert(_insert_into->offsets() != nullptr, "must");
  assert(_insert_into->bootstrap_methods() != nullptr, "must");

  if (_cur_offset + 1 > _insert_into->offsets()->length() ||
      _cur_array + BSMAttributeEntry::u2s_required(argc) > _insert_into->bootstrap_methods()->length()) {
    return nullptr;
  }
  _insert_into->offsets()->at_put(_cur_offset, _cur_array);
  BSMAttributeEntry* e = _insert_into->entry(_cur_offset);
  e->_bootstrap_method_index = bsmi;
  e->_argument_count = argc;

  _cur_array += 1 + 1 + argc;
  _cur_offset += 1;
  return e;
}

inline void BSMAttributeEntry::copy_args_into(BSMAttributeEntry* entry) const {
  assert(entry->argument_count() == this->argument_count(), "must be same");
  for (int i = 0; i < argument_count(); i++) {
    entry->set_argument(i, this->argument(i));
  }
}

#endif // SHARE_OOPS_BSMATTRIBUTE_INLINE_HPP
