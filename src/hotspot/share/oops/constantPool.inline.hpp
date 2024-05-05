/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_CONSTANTPOOL_INLINE_HPP
#define SHARE_OOPS_CONSTANTPOOL_INLINE_HPP

#include "oops/constantPool.hpp"

#include "oops/cpCache.inline.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "runtime/atomic.hpp"

inline Klass* ConstantPool::resolved_klass_at(int which) const {  // Used by Compiler
  guarantee(tag_at(which).is_klass(), "Corrupted constant pool");
  // Must do an acquire here in case another thread resolved the klass
  // behind our back, lest we later load stale values thru the oop.
  CPKlassSlot kslot = klass_slot_at(which);
  assert(tag_at(kslot.name_index()).is_symbol(), "sanity");

  Klass** adr = resolved_klasses()->adr_at(kslot.resolved_klass_index());
  return Atomic::load_acquire(adr);
}

inline ResolvedFieldEntry* ConstantPool::resolved_field_entry_at(int field_index) {
    return cache()->resolved_field_entry_at(field_index);
}

inline int ConstantPool::resolved_field_entries_length() const {
    return cache()->resolved_field_entries_length();
}

inline ResolvedMethodEntry* ConstantPool::resolved_method_entry_at(int method_index) {
    return cache()->resolved_method_entry_at(method_index);
}

inline int ConstantPool::resolved_method_entries_length() const {
    return cache()->resolved_method_entries_length();
}

inline oop ConstantPool::appendix_if_resolved(int method_index) const {
  ResolvedMethodEntry* entry = cache()->resolved_method_entry_at(method_index);
  if (!entry->has_appendix())
    return nullptr;
  const int ref_index = entry->resolved_references_index();
  return resolved_reference_at(ref_index);
}

inline u2 ConstantPool::invokedynamic_bootstrap_ref_index_at(int indy_index) const {
  return cache()->resolved_indy_entry_at(indy_index)->constant_pool_index();
}

inline ResolvedIndyEntry* ConstantPool::resolved_indy_entry_at(int index) {
  return cache()->resolved_indy_entry_at(index);
}

inline int ConstantPool::resolved_indy_entries_length() const {
  return cache()->resolved_indy_entries_length();
}

inline oop ConstantPool::resolved_reference_from_indy(int index) const {
  return resolved_references()->obj_at(cache()->resolved_indy_entry_at(index)->resolved_references_index());
}

inline oop ConstantPool::resolved_reference_from_method(int index) const {
  return resolved_references()->obj_at(cache()->resolved_method_entry_at(index)->resolved_references_index());
}
#endif // SHARE_OOPS_CONSTANTPOOL_INLINE_HPP
