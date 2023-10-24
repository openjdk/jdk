/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XPAGE_INLINE_HPP
#define SHARE_GC_X_XPAGE_INLINE_HPP

#include "gc/x/xPage.hpp"

#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xLiveMap.inline.hpp"
#include "gc/x/xNUMA.hpp"
#include "gc/x/xPhysicalMemory.inline.hpp"
#include "gc/x/xVirtualMemory.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"

inline uint8_t XPage::type_from_size(size_t size) const {
  if (size == XPageSizeSmall) {
    return XPageTypeSmall;
  } else if (size == XPageSizeMedium) {
    return XPageTypeMedium;
  } else {
    return XPageTypeLarge;
  }
}

inline const char* XPage::type_to_string() const {
  switch (type()) {
  case XPageTypeSmall:
    return "Small";

  case XPageTypeMedium:
    return "Medium";

  default:
    assert(type() == XPageTypeLarge, "Invalid page type");
    return "Large";
  }
}

inline uint32_t XPage::object_max_count() const {
  switch (type()) {
  case XPageTypeLarge:
    // A large page can only contain a single
    // object aligned to the start of the page.
    return 1;

  default:
    return (uint32_t)(size() >> object_alignment_shift());
  }
}

inline size_t XPage::object_alignment_shift() const {
  switch (type()) {
  case XPageTypeSmall:
    return XObjectAlignmentSmallShift;

  case XPageTypeMedium:
    return XObjectAlignmentMediumShift;

  default:
    assert(type() == XPageTypeLarge, "Invalid page type");
    return XObjectAlignmentLargeShift;
  }
}

inline size_t XPage::object_alignment() const {
  switch (type()) {
  case XPageTypeSmall:
    return XObjectAlignmentSmall;

  case XPageTypeMedium:
    return XObjectAlignmentMedium;

  default:
    assert(type() == XPageTypeLarge, "Invalid page type");
    return XObjectAlignmentLarge;
  }
}

inline uint8_t XPage::type() const {
  return _type;
}

inline uintptr_t XPage::start() const {
  return _virtual.start();
}

inline uintptr_t XPage::end() const {
  return _virtual.end();
}

inline size_t XPage::size() const {
  return _virtual.size();
}

inline uintptr_t XPage::top() const {
  return _top;
}

inline size_t XPage::remaining() const {
  return end() - top();
}

inline const XVirtualMemory& XPage::virtual_memory() const {
  return _virtual;
}

inline const XPhysicalMemory& XPage::physical_memory() const {
  return _physical;
}

inline XPhysicalMemory& XPage::physical_memory() {
  return _physical;
}

inline uint8_t XPage::numa_id() {
  if (_numa_id == (uint8_t)-1) {
    _numa_id = checked_cast<uint8_t>(XNUMA::memory_id(XAddress::good(start())));
  }

  return _numa_id;
}

inline bool XPage::is_allocating() const {
  return _seqnum == XGlobalSeqNum;
}

inline bool XPage::is_relocatable() const {
  return _seqnum < XGlobalSeqNum;
}

inline uint64_t XPage::last_used() const {
  return _last_used;
}

inline void XPage::set_last_used() {
  _last_used = (uint64_t)ceil(os::elapsedTime());
}

inline bool XPage::is_in(uintptr_t addr) const {
  const uintptr_t offset = XAddress::offset(addr);
  return offset >= start() && offset < top();
}

inline bool XPage::is_marked() const {
  assert(is_relocatable(), "Invalid page state");
  return _livemap.is_marked();
}

inline bool XPage::is_object_marked(uintptr_t addr) const {
  assert(is_relocatable(), "Invalid page state");
  const size_t index = ((XAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.get(index);
}

inline bool XPage::is_object_strongly_marked(uintptr_t addr) const {
  assert(is_relocatable(), "Invalid page state");
  const size_t index = ((XAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.get(index + 1);
}

template <bool finalizable>
inline bool XPage::is_object_marked(uintptr_t addr) const {
  return finalizable ? is_object_marked(addr) : is_object_strongly_marked(addr);
}

inline bool XPage::is_object_live(uintptr_t addr) const {
  return is_allocating() || is_object_marked(addr);
}

inline bool XPage::is_object_strongly_live(uintptr_t addr) const {
  return is_allocating() || is_object_strongly_marked(addr);
}

inline bool XPage::mark_object(uintptr_t addr, bool finalizable, bool& inc_live) {
  assert(XAddress::is_marked(addr), "Invalid address");
  assert(is_relocatable(), "Invalid page state");
  assert(is_in(addr), "Invalid address");

  // Set mark bit
  const size_t index = ((XAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.set(index, finalizable, inc_live);
}

inline void XPage::inc_live(uint32_t objects, size_t bytes) {
  _livemap.inc_live(objects, bytes);
}

inline uint32_t XPage::live_objects() const {
  assert(is_marked(), "Should be marked");
  return _livemap.live_objects();
}

inline size_t XPage::live_bytes() const {
  assert(is_marked(), "Should be marked");
  return _livemap.live_bytes();
}

inline void XPage::object_iterate(ObjectClosure* cl) {
  _livemap.iterate(cl, XAddress::good(start()), object_alignment_shift());
}

inline uintptr_t XPage::alloc_object(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  const uintptr_t addr = top();
  const uintptr_t new_top = addr + aligned_size;

  if (new_top > end()) {
    // Not enough space left
    return 0;
  }

  _top = new_top;

  return XAddress::good(addr);
}

inline uintptr_t XPage::alloc_object_atomic(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  uintptr_t addr = top();

  for (;;) {
    const uintptr_t new_top = addr + aligned_size;
    if (new_top > end()) {
      // Not enough space left
      return 0;
    }

    const uintptr_t prev_top = Atomic::cmpxchg(&_top, addr, new_top);
    if (prev_top == addr) {
      // Success
      return XAddress::good(addr);
    }

    // Retry
    addr = prev_top;
  }
}

inline bool XPage::undo_alloc_object(uintptr_t addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const uintptr_t offset = XAddress::offset(addr);
  const size_t aligned_size = align_up(size, object_alignment());
  const uintptr_t old_top = top();
  const uintptr_t new_top = old_top - aligned_size;

  if (new_top != offset) {
    // Failed to undo allocation, not the last allocated object
    return false;
  }

  _top = new_top;

  // Success
  return true;
}

inline bool XPage::undo_alloc_object_atomic(uintptr_t addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const uintptr_t offset = XAddress::offset(addr);
  const size_t aligned_size = align_up(size, object_alignment());
  uintptr_t old_top = top();

  for (;;) {
    const uintptr_t new_top = old_top - aligned_size;
    if (new_top != offset) {
      // Failed to undo allocation, not the last allocated object
      return false;
    }

    const uintptr_t prev_top = Atomic::cmpxchg(&_top, old_top, new_top);
    if (prev_top == old_top) {
      // Success
      return true;
    }

    // Retry
    old_top = prev_top;
  }
}

#endif // SHARE_GC_X_XPAGE_INLINE_HPP
