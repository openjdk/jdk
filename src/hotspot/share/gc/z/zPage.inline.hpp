/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zPage.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLiveMap.inline.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zRememberedSet.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "logging/logStream.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"

inline ZPageType ZPage::type_from_size(size_t size) const {
  if (size == ZPageSizeSmall) {
    return ZPageType::small;
  } else if (size == ZPageSizeMedium) {
    return ZPageType::medium;
  } else {
    return ZPageType::large;
  }
}

inline const char* ZPage::type_to_string() const {
  switch (type()) {
  case ZPageType::small:
    return "Small";

  case ZPageType::medium:
    return "Medium";

  case ZPageType::large:
    return "Large";

  default:
    fatal("Unexpected page type");
  }
}

inline uint32_t ZPage::object_max_count() const {
  switch (type()) {
  case ZPageType::large:
    // A large page can only contain a single
    // object aligned to the start of the page.
    return 1;

  default:
    return (uint32_t)(size() >> object_alignment_shift());
  }
}

inline size_t ZPage::object_alignment_shift() const {
  switch (type()) {
  case ZPageType::small:
    return ZObjectAlignmentSmallShift;

  case ZPageType::medium:
    return ZObjectAlignmentMediumShift;

  case ZPageType::large:
    return ZObjectAlignmentLargeShift;

  default:
    fatal("Unexpected page type");
    return 0;
  }
}

inline size_t ZPage::object_alignment() const {
  switch (type()) {
  case ZPageType::small:
    return ZObjectAlignmentSmall;

  case ZPageType::medium:
    return ZObjectAlignmentMedium;

  case ZPageType::large:
    return ZObjectAlignmentLarge;

  default:
    fatal("Unexpected page type");
    return 0;
  }
}

inline ZPageType ZPage::type() const {
  return _type;
}

inline bool ZPage::is_small() const {
  return _type == ZPageType::small;
}

inline bool ZPage::is_medium() const {
  return _type == ZPageType::medium;
}

inline bool ZPage::is_large() const {
  return _type == ZPageType::large;
}

inline ZGenerationId ZPage::generation_id() const {
  return _generation_id;
}

inline bool ZPage::is_young() const {
  return _generation_id == ZGenerationId::young;
}

inline bool ZPage::is_old() const {
  return _generation_id == ZGenerationId::old;
}

inline zoffset ZPage::start() const {
  return _virtual.start();
}

inline zoffset_end ZPage::end() const {
  return _virtual.end();
}

inline size_t ZPage::size() const {
  return _virtual.size();
}

inline zoffset_end ZPage::top() const {
  return _top;
}

inline size_t ZPage::remaining() const {
  return end() - top();
}

inline size_t ZPage::used() const {
  return top() - start();
}

inline const ZVirtualMemory& ZPage::virtual_memory() const {
  return _virtual;
}

inline const ZPhysicalMemory& ZPage::physical_memory() const {
  return _physical;
}

inline ZPhysicalMemory& ZPage::physical_memory() {
  return _physical;
}

inline uint8_t ZPage::numa_id() {
  if (_numa_id == (uint8_t)-1) {
    _numa_id = checked_cast<uint8_t>(ZNUMA::memory_id(untype(ZOffset::address(start()))));
  }

  return _numa_id;
}

inline ZPageAge ZPage::age() const {
  return _age;
}

inline uint32_t ZPage::seqnum() const {
  return _seqnum;
}

inline bool ZPage::is_allocating() const {
  return _seqnum == generation()->seqnum();
}

inline bool ZPage::is_relocatable() const {
  return _seqnum < generation()->seqnum();
}

inline uint64_t ZPage::last_used() const {
  return _last_used;
}

inline void ZPage::set_last_used() {
  _last_used = (uint64_t)ceil(os::elapsedTime());
}

inline bool ZPage::is_in(zoffset offset) const {
  return offset >= start() && offset < top();
}

inline bool ZPage::is_in(zaddress addr) const {
  const zoffset offset = ZAddress::offset(addr);
  return is_in(offset);
}

inline uintptr_t ZPage::local_offset(zoffset offset) const {
  assert(ZHeap::heap()->is_in_page_relaxed(this, ZOffset::address(offset)),
         "Invalid offset " PTR_FORMAT " page [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
         untype(offset), untype(start()), untype(top()), untype(end()));
  return offset - start();
}

inline uintptr_t ZPage::local_offset(zoffset_end offset) const {
  assert(offset <= end(), "Wrong offset");
  return offset - start();
}

inline uintptr_t ZPage::local_offset(zaddress addr) const {
  const zoffset offset = ZAddress::offset(addr);
  return local_offset(offset);
}

inline uintptr_t ZPage::local_offset(zaddress_unsafe addr) const {
  const zoffset offset = ZAddress::offset(addr);
  return local_offset(offset);
}

inline zoffset ZPage::global_offset(uintptr_t local_offset) const {
  return start() + local_offset;
}

inline bool ZPage::is_marked() const {
  assert(is_relocatable(), "Invalid page state");
  return _livemap.is_marked(_generation_id);
}

inline BitMap::idx_t ZPage::bit_index(zaddress addr) const {
  return (local_offset(addr) >> object_alignment_shift()) * 2;
}

inline zoffset ZPage::offset_from_bit_index(BitMap::idx_t index) const {
  const uintptr_t l_offset = ((index / 2) << object_alignment_shift());
  return start() + l_offset;
}

inline oop ZPage::object_from_bit_index(BitMap::idx_t index) const {
  const zoffset offset = offset_from_bit_index(index);
  return to_oop(ZOffset::address(offset));
}

inline bool ZPage::is_live_bit_set(zaddress addr) const {
  assert(is_relocatable(), "Invalid page state");
  const BitMap::idx_t index = bit_index(addr);
  return _livemap.get(_generation_id, index);
}

inline bool ZPage::is_strong_bit_set(zaddress addr) const {
  assert(is_relocatable(), "Invalid page state");
  const BitMap::idx_t index = bit_index(addr);
  return _livemap.get(_generation_id, index + 1);
}

inline bool ZPage::is_object_live(zaddress addr) const {
  return is_allocating() || is_live_bit_set(addr);
}

inline bool ZPage::is_object_strongly_live(zaddress addr) const {
  return is_allocating() || is_strong_bit_set(addr);
}

inline bool ZPage::is_object_marked_live(zaddress addr) const {
  // This function is only used by the marking code and therefore has stronger
  // asserts that are not always valid to ask when checking for liveness.
  assert(!is_old() || ZGeneration::old()->is_phase_mark(), "Location should match phase");
  assert(!is_young() || ZGeneration::young()->is_phase_mark(), "Location should match phase");

  return is_object_live(addr);
}

inline bool ZPage::is_object_marked_strong(zaddress addr) const {
  // This function is only used by the marking code and therefore has stronger
  // asserts that are not always valid to ask when checking for liveness.
  assert(!is_old() || ZGeneration::old()->is_phase_mark(), "Location should match phase");
  assert(!is_young() || ZGeneration::young()->is_phase_mark(), "Location should match phase");

  return is_object_strongly_live(addr);
}

inline bool ZPage::is_object_marked(zaddress addr, bool finalizable) const {
  return finalizable ? is_object_marked_live(addr) : is_object_marked_strong(addr);
}

inline bool ZPage::mark_object(zaddress addr, bool finalizable, bool& inc_live) {
  assert(is_relocatable(), "Invalid page state");
  assert(is_in(addr), "Invalid address");

  // Verify oop
  (void)to_oop(addr);

  // Set mark bit
  const BitMap::idx_t index = bit_index(addr);
  return _livemap.set(_generation_id, index, finalizable, inc_live);
}

inline void ZPage::inc_live(uint32_t objects, size_t bytes) {
  _livemap.inc_live(objects, bytes);
}

#define assert_zpage_mark_state()                                                  \
  do {                                                                             \
    assert(is_marked(), "Should be marked");                                       \
    assert(!is_young() || !ZGeneration::young()->is_phase_mark(), "Wrong phase");  \
    assert(!is_old() || !ZGeneration::old()->is_phase_mark(), "Wrong phase");      \
  } while (0)

inline uint32_t ZPage::live_objects() const {
  assert_zpage_mark_state();

  return _livemap.live_objects();
}

inline size_t ZPage::live_bytes() const {
  assert_zpage_mark_state();

  return _livemap.live_bytes();
}

template <typename Function>
inline void ZPage::object_iterate(Function function) {
  auto do_bit = [&](BitMap::idx_t index) -> bool {
    const oop obj = object_from_bit_index(index);

    // Apply function
    function(obj);

    return true;
  };

  _livemap.iterate(_generation_id, do_bit);
}

inline void ZPage::remember(volatile zpointer* p) {
  const zaddress addr = to_zaddress((uintptr_t)p);
  const uintptr_t l_offset = local_offset(addr);
  _remembered_set.set_current(l_offset);
}

inline void ZPage::clear_remset_bit_non_par_current(uintptr_t l_offset) {
  _remembered_set.unset_non_par_current(l_offset);
}

inline void ZPage::clear_remset_range_non_par_current(uintptr_t l_offset, size_t size) {
  _remembered_set.unset_range_non_par_current(l_offset, size);
}

inline ZBitMap::ReverseIterator ZPage::remset_reverse_iterator_previous() {
  return _remembered_set.iterator_reverse_previous();
}

inline BitMap::Iterator ZPage::remset_iterator_limited_current(uintptr_t l_offset, size_t size) {
  return _remembered_set.iterator_limited_current(l_offset, size);
}

inline BitMap::Iterator ZPage::remset_iterator_limited_previous(uintptr_t l_offset, size_t size) {
  return _remembered_set.iterator_limited_previous(l_offset, size);
}

inline bool ZPage::is_remembered(volatile zpointer* p) {
  const zaddress addr = to_zaddress((uintptr_t)p);
  const uintptr_t l_offset = local_offset(addr);
  return _remembered_set.at_current(l_offset);
}

inline bool ZPage::was_remembered(volatile zpointer* p) {
  const zaddress addr = to_zaddress((uintptr_t)p);
  const uintptr_t l_offset = local_offset(addr);
  return _remembered_set.at_previous(l_offset);
}


inline zaddress_unsafe ZPage::find_base_unsafe(volatile zpointer* p) {
  if (is_large()) {
    return ZOffset::address_unsafe(start());
  }

  // Note: when thinking about excluding looking at the index corresponding to
  // the field address p, it's important to note that for medium pages both p
  // and it's associated base could map to the same index.
  const BitMap::idx_t index = bit_index(zaddress(uintptr_t(p)));
  const BitMap::idx_t base_index = _livemap.find_base_bit(index);
  if (base_index == BitMap::idx_t(-1)) {
    return zaddress_unsafe::null;
  } else {
    return ZOffset::address_unsafe(offset_from_bit_index(base_index));
  }
}

inline zaddress_unsafe ZPage::find_base(volatile zpointer* p) {
  assert_zpage_mark_state();

  return find_base_unsafe(p);
}

template <typename Function>
inline void ZPage::oops_do_remembered(Function function) {
  _remembered_set.iterate_previous([&](uintptr_t local_offset) {
    const zoffset offset = start() + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);
  });
}

template <typename Function>
inline void ZPage::oops_do_remembered_in_live(Function function) {
  assert(!is_allocating(), "Must have liveness information");
  assert(!ZGeneration::old()->is_phase_mark(), "Must have liveness information");
  assert(is_marked(), "Must have liveness information");

  ZRememberedSetContainingInLiveIterator iter(this);
  for (ZRememberedSetContaining containing; iter.next(&containing);) {
    function((volatile zpointer*)containing._field_addr);
  }

  iter.print_statistics();
}

template <typename Function>
inline void ZPage::oops_do_current_remembered(Function function) {
  _remembered_set.iterate_current([&](uintptr_t local_offset) {
    const zoffset offset = start() + local_offset;
    const zaddress addr = ZOffset::address(offset);

    function((volatile zpointer*)addr);
  });
}

inline zaddress ZPage::alloc_object(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  const zoffset_end addr = top();

  zoffset_end new_top;

  if (!to_zoffset_end(&new_top, addr, aligned_size)) {
    // Next top would be outside of the heap - bail
    return zaddress::null;
  }

  if (new_top > end()) {
    // Not enough space left in the page
    return zaddress::null;
  }

  _top = new_top;

  return ZOffset::address(to_zoffset(addr));
}

inline zaddress ZPage::alloc_object_atomic(size_t size) {
  assert(is_allocating(), "Invalid state");

  const size_t aligned_size = align_up(size, object_alignment());
  zoffset_end addr = top();

  for (;;) {
    zoffset_end new_top;

    if (!to_zoffset_end(&new_top, addr, aligned_size)) {
      // Next top would be outside of the heap - bail
      return zaddress::null;
    }

    if (new_top > end()) {
      // Not enough space left
      return zaddress::null;
    }

    const zoffset_end prev_top = Atomic::cmpxchg(&_top, addr, new_top);
    if (prev_top == addr) {
      // Success
      return ZOffset::address(to_zoffset(addr));
    }

    // Retry
    addr = prev_top;
  }
}

inline bool ZPage::undo_alloc_object(zaddress addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const zoffset offset = ZAddress::offset(addr);
  const size_t aligned_size = align_up(size, object_alignment());
  const zoffset_end old_top = top();
  const zoffset_end new_top = old_top - aligned_size;

  if (new_top != offset) {
    // Failed to undo allocation, not the last allocated object
    return false;
  }

  _top = new_top;

  // Success
  return true;
}

inline bool ZPage::undo_alloc_object_atomic(zaddress addr, size_t size) {
  assert(is_allocating(), "Invalid state");

  const zoffset offset = ZAddress::offset(addr);
  const size_t aligned_size = align_up(size, object_alignment());
  zoffset_end old_top = top();

  for (;;) {
    const zoffset_end new_top = old_top - aligned_size;
    if (new_top != offset) {
      // Failed to undo allocation, not the last allocated object
      return false;
    }

    const zoffset_end prev_top = Atomic::cmpxchg(&_top, old_top, new_top);
    if (prev_top == old_top) {
      // Success
      return true;
    }

    // Retry
    old_top = prev_top;
  }
}

inline void ZPage::log_msg(const char* msg_format, ...) const {
  LogTarget(Trace, gc, page) target;
  if (target.is_enabled()) {
    va_list argp;
    va_start(argp, msg_format);
    LogStream stream(target);
    print_on_msg(&stream, err_msg(FormatBufferDummy(), msg_format, argp));
    va_end(argp);
  }
}

#endif // SHARE_GC_Z_ZPAGE_INLINE_HPP
