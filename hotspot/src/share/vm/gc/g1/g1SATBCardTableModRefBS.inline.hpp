/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP
#define SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP

#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "oops/oop.inline.hpp"

// We export this to make it available in cases where the static
// type of the barrier set is known.  Note that it is non-virtual.
template <class T> void G1SATBCardTableModRefBS::inline_write_ref_field_pre(T* field, oop newVal) {
  T heap_oop = oopDesc::load_heap_oop(field);
  if (!oopDesc::is_null(heap_oop)) {
    enqueue(oopDesc::decode_heap_oop(heap_oop));
  }
}

// These are the more general virtual versions.
void G1SATBCardTableModRefBS::write_ref_field_pre_work(oop* field, oop new_val) {
  inline_write_ref_field_pre(field, new_val);
}
void G1SATBCardTableModRefBS::write_ref_field_pre_work(narrowOop* field, oop new_val) {
  inline_write_ref_field_pre(field, new_val);
}

void G1SATBCardTableModRefBS::set_card_claimed(size_t card_index) {
  jbyte val = _byte_map[card_index];
  if (val == clean_card_val()) {
    val = (jbyte)claimed_card_val();
  } else {
    val |= (jbyte)claimed_card_val();
  }
  _byte_map[card_index] = val;
}

#endif // SHARE_VM_GC_G1_G1SATBCARDTABLEMODREFBS_INLINE_HPP
