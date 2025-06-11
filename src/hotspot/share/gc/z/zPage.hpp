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

#ifndef SHARE_GC_Z_ZPAGE_HPP
#define SHARE_GC_Z_ZPAGE_HPP

#include "gc/z/zGenerationId.hpp"
#include "gc/z/zLiveMap.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zRememberedSet.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

class ZGeneration;
class ZMultiPartitionTracker;

class ZPage : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class ZForwardingTest;

private:
  const ZPageType               _type;
  ZGenerationId                 _generation_id;
  ZPageAge                      _age;
  uint32_t                      _seqnum;
  uint32_t                      _seqnum_other;
  const uint32_t                _single_partition_id;
  const ZVirtualMemory          _virtual;
  volatile zoffset_end          _top;
  ZLiveMap                      _livemap;
  ZRememberedSet                _remembered_set;
  ZMultiPartitionTracker* const _multi_partition_tracker;

  const char* type_to_string() const;

  BitMap::idx_t bit_index(zaddress addr) const;
  zoffset offset_from_bit_index(BitMap::idx_t index) const;
  oop object_from_bit_index(BitMap::idx_t index) const;

  bool is_live_bit_set(zaddress addr) const;
  bool is_strong_bit_set(zaddress addr) const;

  ZGeneration* generation();
  const ZGeneration* generation() const;

  void reset_seqnum();

  ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, ZMultiPartitionTracker* multi_partition_tracker, uint32_t partition_id);

public:
  ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, uint32_t partition_id);
  ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, ZMultiPartitionTracker* multi_partition_tracker);

  ZPage* clone_for_promotion() const;

  uint32_t object_max_count() const;
  size_t object_alignment_shift() const;
  size_t object_alignment() const;

  ZPageType type() const;

  bool is_small() const;
  bool is_medium() const;
  bool is_large() const;

  ZGenerationId generation_id() const;
  bool is_young() const;
  bool is_old() const;
  zoffset start() const;
  zoffset_end end() const;
  size_t size() const;
  zoffset_end top() const;
  size_t remaining() const;
  size_t used() const;

  const ZVirtualMemory& virtual_memory() const;

  uint32_t single_partition_id() const;
  bool is_multi_partition() const;
  ZMultiPartitionTracker* multi_partition_tracker() const;

  ZPageAge age() const;

  uint32_t seqnum() const;
  bool is_allocating() const;
  bool is_relocatable() const;

  ZPage* reset(ZPageAge age);
  void reset_livemap();
  void reset_top_for_allocation();

  bool is_in(zoffset offset) const;
  bool is_in(zaddress addr) const;

  uintptr_t local_offset(zoffset offset) const;
  uintptr_t local_offset(zoffset_end offset) const;
  uintptr_t local_offset(zaddress addr) const;
  uintptr_t local_offset(zaddress_unsafe addr) const;

  zoffset global_offset(uintptr_t local_offset) const;

  bool is_object_live(zaddress addr) const;
  bool is_object_strongly_live(zaddress addr) const;

  bool is_marked() const;
  bool is_object_marked_live(zaddress addr) const;
  bool is_object_marked_strong(zaddress addr) const;
  bool is_object_marked(zaddress addr, bool finalizable) const;
  bool mark_object(zaddress addr, bool finalizable, bool& inc_live);

  void inc_live(uint32_t objects, size_t bytes);
  uint32_t live_objects() const;
  size_t live_bytes() const;

  template <typename Function>
  void object_iterate(Function function);

  void remember(volatile zpointer* p);

  // In-place relocation support
  void clear_remset_bit_non_par_current(uintptr_t l_offset);
  void clear_remset_range_non_par_current(uintptr_t l_offset, size_t size);
  void swap_remset_bitmaps();

  void remset_alloc();

  ZBitMap::ReverseIterator remset_reverse_iterator_previous();
  BitMap::Iterator remset_iterator_limited_current(uintptr_t l_offset, size_t size);
  BitMap::Iterator remset_iterator_limited_previous(uintptr_t l_offset, size_t size);

  zaddress_unsafe find_base_unsafe(volatile zpointer* p);
  zaddress_unsafe find_base(volatile zpointer* p);

  template <typename Function>
  void oops_do_remembered(Function function);

  // Only visits remembered set entries for live objects
  template <typename Function>
  void oops_do_remembered_in_live(Function function);

  template <typename Function>
  void oops_do_current_remembered(Function function);

  bool is_remset_cleared_current() const;
  bool is_remset_cleared_previous() const;

  void verify_remset_cleared_current() const;
  void verify_remset_cleared_previous() const;

  void clear_remset_previous();

  void* remset_current();

  zaddress alloc_object(size_t size);
  zaddress alloc_object_atomic(size_t size);

  bool undo_alloc_object(zaddress addr, size_t size);
  bool undo_alloc_object_atomic(zaddress addr, size_t size);

  void log_msg(const char* msg_format, ...) const ATTRIBUTE_PRINTF(2, 3);

  void print_on_msg(outputStream* st, const char* msg) const;
  void print_on(outputStream* st) const;
  void print() const;

  // Verification
  bool was_remembered(volatile zpointer* p);
  bool is_remembered(volatile zpointer* p);
  void verify_live(uint32_t live_objects, size_t live_bytes, bool in_place) const;

  void fatal_msg(const char* msg) const;
};

class ZPageClosure {
public:
  virtual void do_page(const ZPage* page) = 0;
};

#endif // SHARE_GC_Z_ZPAGE_HPP
