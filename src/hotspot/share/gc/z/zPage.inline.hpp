/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGE_INLINE_HPP
#define SHARE_GC_Z_ZPAGE_INLINE_HPP

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLiveMap.inline.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

inline const char* ZPage::type_to_string() const {
  switch (type()) {
  case ZPageTypeSmall:
    return "Small";

  case ZPageTypeMedium:
    return "Medium";

  default:
    assert(type() == ZPageTypeLarge, "Invalid page type");
    return "Large";
  }
}

inline uint32_t ZPage::object_max_count() const {
  switch (type()) {
  case ZPageTypeLarge:
    // A large page can only contain a single
    // object aligned to the start of the page.
    return 1;

  default:
    return (uint32_t)(size() >> object_alignment_shift());
  }
}

inline size_t ZPage::object_alignment_shift() const {
  switch (type()) {
  case ZPageTypeSmall:
    return ZObjectAlignmentSmallShift;

  case ZPageTypeMedium:
    return ZObjectAlignmentMediumShift;

  default:
    assert(type() == ZPageTypeLarge, "Invalid page type");
    return ZObjectAlignmentLargeShift;
  }
}

inline size_t ZPage::object_alignment() const {
  switch (type()) {
  case ZPageTypeSmall:
    return ZObjectAlignmentSmall;

  case ZPageTypeMedium:
    return ZObjectAlignmentMedium;

  default:
    assert(type() == ZPageTypeLarge, "Invalid page type");
    return ZObjectAlignmentLarge;
  }
}

inline uint8_t ZPage::type() const {
  return _type;
}

inline uintptr_t ZPage::start() const {
  return _virtual.start();
}

inline uintptr_t ZPage::end() const {
  return _virtual.end();
}

inline size_t ZPage::size() const {
  return _virtual.size();
}

inline uintptr_t ZPage::top() const {
  return _top;
}

inline size_t ZPage::remaining() const {
  return end() - top();
}

inline ZPhysicalMemory& ZPage::physical_memory() {
  return _physical;
}

inline const ZVirtualMemory& ZPage::virtual_memory() const {
  return _virtual;
}

inline uint8_t ZPage::numa_id() {
  if (_numa_id == (uint8_t)-1) {
    _numa_id = (uint8_t)ZNUMA::memory_id(ZAddress::good(start()));
  }

  return _numa_id;
}

inline bool ZPage::inc_refcount() {
  for (uint32_t prev_refcount = _refcount; prev_refcount > 0; prev_refcount = _refcount) {
    if (Atomic::cmpxchg(prev_refcount + 1, &_refcount, prev_refcount) == prev_refcount) {
      return true;
    }
  }
  return false;
}

inline bool ZPage::dec_refcount() {
  assert(is_active(), "Should be active");
  return Atomic::sub(1u, &_refcount) == 0;
}

inline bool ZPage::is_in(uintptr_t addr) const {
  const uintptr_t offset = ZAddress::offset(addr);
  return offset >= start() && offset < top();
}

inline uintptr_t ZPage::block_start(uintptr_t addr) const {
  if (block_is_obj(addr)) {
    return addr;
  } else {
    return ZAddress::good(top());
  }
}

inline size_t ZPage::block_size(uintptr_t addr) const {
  if (block_is_obj(addr)) {
    return ZUtils::object_size(addr);
  } else {
    return end() - top();
  }
}

inline bool ZPage::block_is_obj(uintptr_t addr) const {
  return ZAddress::offset(addr) < top();
}

inline bool ZPage::is_active() const {
  return _refcount > 0;
}

inline bool ZPage::is_allocating() const {
  return is_active() && _seqnum == ZGlobalSeqNum;
}

inline bool ZPage::is_relocatable() const {
  return is_active() && _seqnum < ZGlobalSeqNum;
}

inline bool ZPage::is_detached() const {
  return _physical.is_null();
}

inline bool ZPage::is_mapped() const {
  return _seqnum > 0;
}

inline void ZPage::set_pre_mapped() {
  // The _seqnum variable is also used to signal that the virtual and physical
  // memory has been mapped. So, we need to set it to non-zero when the memory
  // has been pre-mapped.
  _seqnum = 1;
}

inline bool ZPage::is_pinned() const {
  return _pinned;
}

inline void ZPage::set_pinned() {
  _pinned = 1;
}

inline bool ZPage::is_forwarding() const {
  return !_forwarding.is_null();
}

inline void ZPage::set_forwarding() {
  assert(is_marked(), "Should be marked");
  _forwarding.setup(_livemap.live_objects());
}

inline void ZPage::reset_forwarding() {
  _forwarding.reset();
  _pinned = 0;
}

inline void ZPage::verify_forwarding() const {
  _forwarding.verify(object_max_count(), _livemap.live_objects());
}

inline bool ZPage::is_marked() const {
  assert(is_relocatable(), "Invalid page state");
  return _livemap.is_marked();
}

inline bool ZPage::is_object_marked(uintptr_t addr) const {
  const size_t index = ((ZAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.get(index);
}

inline bool ZPage::is_object_strongly_marked(uintptr_t addr) const {
  const size_t index = ((ZAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.get(index + 1);
}

inline bool ZPage::is_object_live(uintptr_t addr) const {
  return is_allocating() || is_object_marked(addr);
}

inline bool ZPage::is_object_strongly_live(uintptr_t addr) const {
  return is_allocating() || is_object_strongly_marked(addr);
}

inline bool ZPage::mark_object(uintptr_t addr, bool finalizable, bool& inc_live) {
  assert(ZAddress::is_marked(addr), "Invalid address");
  assert(is_relocatable(), "Invalid page state");
  assert(is_in(addr), "Invalid address");

  // Set mark bit
  const size_t index = ((ZAddress::offset(addr) - start()) >> object_alignment_shift()) * 2;
  return _livemap.set_atomic(index, finalizable, inc_live);
}

inline void ZPage::inc_live_atomic(uint32_t objects, size_t bytes) {
  _livemap.inc_live_atomic(objects, bytes);
}

inline size_t ZPage::live_bytes() const {
  assert(is_marked(), "Should be marked");
  return _livemap.live_bytes();
}

inline void ZPage::object_iterate(ObjectClosure* cl) {
  _livemap.iterate(cl, ZAddress::good(start()), object_alignment_shift());
}

inline uintptr_t ZPage::alloc_object(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  const uintptr_t addr = top();
  const uintptr_t new_top = addr + aligned_size;

  if (new_top > end()) {
    // Not enough space left
    return 0;
  }

  _top = new_top;

  // Fill alignment padding if needed
  if (aligned_size != size) {
    ZUtils::insert_filler_object(addr + size, aligned_size - size);
  }

  return ZAddress::good(addr);
}

inline uintptr_t ZPage::alloc_object_atomic(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  uintptr_t addr = top();

  for (;;) {
    const uintptr_t new_top = addr + aligned_size;
    if (new_top > end()) {
      // Not enough space left
      return 0;
    }

    const uintptr_t prev_top = Atomic::cmpxchg(new_top, &_top, addr);
    if (prev_top == addr) {
      // Fill alignment padding if needed
      if (aligned_size != size) {
        ZUtils::insert_filler_object(addr + size, aligned_size - size);
      }

      // Success
      return ZAddress::good(addr);
    }

    // Retry
    addr = prev_top;
  }
}

inline bool ZPage::undo_alloc_object(uintptr_t addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const uintptr_t offset = ZAddress::offset(addr);
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

inline bool ZPage::undo_alloc_object_atomic(uintptr_t addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const uintptr_t offset = ZAddress::offset(addr);
  const size_t aligned_size = align_up(size, object_alignment());
  uintptr_t old_top = top();

  for (;;) {
    const uintptr_t new_top = old_top - aligned_size;
    if (new_top != offset) {
      // Failed to undo allocation, not the last allocated object
      return false;
    }

    const uintptr_t prev_top = Atomic::cmpxchg(new_top, &_top, old_top);
    if (prev_top == old_top) {
      // Success
      return true;
    }

    // Retry
    old_top = prev_top;
  }
}

#endif // SHARE_GC_Z_ZPAGE_INLINE_HPP
