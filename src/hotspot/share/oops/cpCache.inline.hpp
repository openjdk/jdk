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

#ifndef SHARE_OOPS_CPCACHE_INLINE_HPP
#define SHARE_OOPS_CPCACHE_INLINE_HPP

#include "oops/cpCache.hpp"

#include "oops/oopHandle.inline.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "runtime/atomic.hpp"

// Constructor
inline ConstantPoolCache::ConstantPoolCache(const intStack& invokedynamic_references_map,
                                            Array<ResolvedIndyEntry>* invokedynamic_info,
                                            Array<ResolvedFieldEntry>* field_entries,
                                            Array<ResolvedMethodEntry>* method_entries) :
                                                  _constant_pool(nullptr),
                                                  _gc_epoch(0),
                                                  _resolved_indy_entries(invokedynamic_info),
                                                  _resolved_field_entries(field_entries),
                                                  _resolved_method_entries(method_entries) {
  CDS_JAVA_HEAP_ONLY(_archived_references_index = -1;)
}

inline objArrayOop ConstantPoolCache::resolved_references() {
  oop obj = _resolved_references.resolve();
  assert(obj == nullptr || obj->is_objArray(), "should be objArray");
  return (objArrayOop)obj;
}

inline ResolvedFieldEntry* ConstantPoolCache::resolved_field_entry_at(int field_index) const {
  return _resolved_field_entries->adr_at(field_index);
}

inline int ConstantPoolCache::resolved_field_entries_length() const {
  return _resolved_field_entries->length();
}

inline ResolvedMethodEntry* ConstantPoolCache::resolved_method_entry_at(int method_index) const {
  return _resolved_method_entries->adr_at(method_index);
}

inline int ConstantPoolCache::resolved_method_entries_length() const {
  return _resolved_method_entries->length();
}

inline ResolvedIndyEntry* ConstantPoolCache::resolved_indy_entry_at(int index) const {
  return _resolved_indy_entries->adr_at(index);
}

inline int ConstantPoolCache::resolved_indy_entries_length() const {
  return _resolved_indy_entries->length();
}
#endif // SHARE_OOPS_CPCACHE_INLINE_HPP
