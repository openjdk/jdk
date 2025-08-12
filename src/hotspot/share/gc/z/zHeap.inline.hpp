/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZHEAP_INLINE_HPP
#define SHARE_GC_Z_ZHEAP_INLINE_HPP

#include "gc/z/zHeap.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zRemembered.inline.hpp"
#include "utilities/debug.hpp"

inline ZHeap* ZHeap::heap() {
  assert(_heap != nullptr, "Not initialized");
  return _heap;
}

inline bool ZHeap::is_young(zaddress addr) const {
  return page(addr)->is_young();
}

inline bool ZHeap::is_young(volatile zpointer* ptr) const {
  return page(ptr)->is_young();
}

inline bool ZHeap::is_old(zaddress addr) const {
  return !is_young(addr);
}

inline bool ZHeap::is_old(volatile zpointer* ptr) const {
  return !is_young(ptr);
}

inline ZPage* ZHeap::page(zaddress addr) const {
  return _page_table.get(addr);
}

inline ZPage* ZHeap::page(volatile zpointer* ptr) const {
  return _page_table.get(ptr);
}

inline bool ZHeap::is_object_live(zaddress addr) const {
  const ZPage* const page = _page_table.get(addr);
  return page->is_object_live(addr);
}

inline bool ZHeap::is_object_strongly_live(zaddress addr) const {
  const ZPage* const page = _page_table.get(addr);
  return page->is_object_strongly_live(addr);
}

inline void ZHeap::retire_allocating_pages(ZPageAgeRange range) {
  _object_allocator.retire_pages(range);
}

inline zaddress ZHeap::alloc_object(size_t size) {
  const zaddress addr = _object_allocator.alloc(size);

  if (is_null(addr)) {
    out_of_memory();
  }

  return addr;
}

inline zaddress ZHeap::alloc_tlab(size_t size) {
  guarantee(size <= max_tlab_size(), "TLAB too large");
  return _object_allocator.alloc(size);
}

inline zaddress ZHeap::alloc_object_for_relocation(size_t size, ZPageAge age) {
  return _object_allocator.alloc_for_relocation(size, age);
}

inline bool ZHeap::is_alloc_stalling() const {
  return _page_allocator.is_alloc_stalling();
}

inline bool ZHeap::is_alloc_stalling_for_old() const {
  return _page_allocator.is_alloc_stalling_for_old();
}

inline void ZHeap::handle_alloc_stalling_for_young() {
  _page_allocator.handle_alloc_stalling_for_young();
}

inline void ZHeap::handle_alloc_stalling_for_old(bool cleared_all_soft_refs) {
  _page_allocator.handle_alloc_stalling_for_old(cleared_all_soft_refs);
}

inline bool ZHeap::is_oop(uintptr_t addr) const {
  return is_in(addr);
}

#endif // SHARE_GC_Z_ZHEAP_INLINE_HPP
