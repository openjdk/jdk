/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XHEAP_INLINE_HPP
#define SHARE_GC_X_XHEAP_INLINE_HPP

#include "gc/x/xHeap.hpp"

#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xForwardingTable.inline.hpp"
#include "gc/x/xMark.inline.hpp"
#include "gc/x/xPage.inline.hpp"
#include "gc/x/xPageTable.inline.hpp"
#include "utilities/debug.hpp"

inline XHeap* XHeap::heap() {
  assert(_heap != NULL, "Not initialized");
  return _heap;
}

inline ReferenceDiscoverer* XHeap::reference_discoverer() {
  return &_reference_processor;
}

inline bool XHeap::is_object_live(uintptr_t addr) const {
  XPage* page = _page_table.get(addr);
  return page->is_object_live(addr);
}

inline bool XHeap::is_object_strongly_live(uintptr_t addr) const {
  XPage* page = _page_table.get(addr);
  return page->is_object_strongly_live(addr);
}

template <bool gc_thread, bool follow, bool finalizable, bool publish>
inline void XHeap::mark_object(uintptr_t addr) {
  assert(XGlobalPhase == XPhaseMark, "Mark not allowed");
  _mark.mark_object<gc_thread, follow, finalizable, publish>(addr);
}

inline uintptr_t XHeap::alloc_tlab(size_t size) {
  guarantee(size <= max_tlab_size(), "TLAB too large");
  return _object_allocator.alloc_object(size);
}

inline uintptr_t XHeap::alloc_object(size_t size) {
  uintptr_t addr = _object_allocator.alloc_object(size);
  assert(XAddress::is_good_or_null(addr), "Bad address");

  if (addr == 0) {
    out_of_memory();
  }

  return addr;
}

inline uintptr_t XHeap::alloc_object_for_relocation(size_t size) {
  const uintptr_t addr = _object_allocator.alloc_object_for_relocation(&_page_table, size);
  assert(XAddress::is_good_or_null(addr), "Bad address");
  return addr;
}

inline void XHeap::undo_alloc_object_for_relocation(uintptr_t addr, size_t size) {
  XPage* const page = _page_table.get(addr);
  _object_allocator.undo_alloc_object_for_relocation(page, addr, size);
}

inline uintptr_t XHeap::relocate_object(uintptr_t addr) {
  assert(XGlobalPhase == XPhaseRelocate, "Relocate not allowed");

  XForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return XAddress::good(addr);
  }

  // Relocate object
  return _relocate.relocate_object(forwarding, XAddress::good(addr));
}

inline uintptr_t XHeap::remap_object(uintptr_t addr) {
  assert(XGlobalPhase == XPhaseMark ||
         XGlobalPhase == XPhaseMarkCompleted, "Forward not allowed");

  XForwarding* const forwarding = _forwarding_table.get(addr);
  if (forwarding == NULL) {
    // Not forwarding
    return XAddress::good(addr);
  }

  // Forward object
  return _relocate.forward_object(forwarding, XAddress::good(addr));
}

inline bool XHeap::has_alloc_stalled() const {
  return _page_allocator.has_alloc_stalled();
}

inline void XHeap::check_out_of_memory() {
  _page_allocator.check_out_of_memory();
}

inline bool XHeap::is_oop(uintptr_t addr) const {
  return XAddress::is_good(addr) && is_object_aligned(addr) && is_in(addr);
}

#endif // SHARE_GC_X_XHEAP_INLINE_HPP
