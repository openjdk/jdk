/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zList.hpp"
#include "gc/z/zLiveMap.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPhysicalMemory.hpp"
#include "gc/z/zRememberedSet.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"

class ZPage : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class ZList<ZPage>;
  friend class ZForwardingTest;

private:
  uint8_t          _type;
  ZGenerationId    _generation_id;
  ZPageAge         _age;
  uint8_t          _numa_id;
  uint32_t         _seqnum;
  uint32_t         _seqnum_other;
  ZVirtualMemory   _virtual;
  volatile zoffset _top;
  ZLiveMap         _livemap;
  ZRememberedSet   _remembered_set;
  uint64_t         _last_used;
  ZPhysicalMemory  _physical;
  ZListNode<ZPage> _node;

  uint8_t type_from_size(size_t size) const;
  const char* type_to_string() const;

  size_t bit_index(zaddress addr) const;
  zoffset offset_from_bit_index(size_t index) const;
  oop object_from_bit_index(BitMap::idx_t index) const;

  bool is_live_bit_set(zaddress addr) const;
  bool is_strong_bit_set(zaddress addr) const;

  void reset_seqnum(ZGenerationId generation_id);

public:
  ZPage(const ZVirtualMemory& vmem, const ZPhysicalMemory& pmem);
  ZPage(uint8_t type, const ZVirtualMemory& vmem, const ZPhysicalMemory& pmem);
  ZPage(const ZPage& other);
  ~ZPage();

  uint32_t object_max_count() const;
  size_t object_alignment_shift() const;
  size_t object_alignment() const;

  uint8_t type() const;

  bool is_small() const;
  bool is_medium() const;
  bool is_large() const;

  ZGenerationId generation_id() const;
  bool is_young() const;
  bool is_old() const;
  zoffset start() const;
  zoffset end() const;
  size_t size() const;
  zoffset top() const;
  size_t remaining() const;

  const ZVirtualMemory& virtual_memory() const;
  const ZPhysicalMemory& physical_memory() const;
  ZPhysicalMemory& physical_memory();

  uint8_t numa_id();
  ZPageAge age() const;

  uint32_t seqnum() const;
  bool is_allocating() const;
  bool is_relocatable() const;

  uint64_t last_used() const;
  void set_last_used();

  enum ZPageResetType {
    NormalReset,
    InPlaceReset,
    FlipReset
  };

  void reset(ZGenerationId generation_id, ZPageAge age, ZPageResetType type);

  void finalize_reset_for_in_place_relocation();

  ZPage* retype(uint8_t type);
  ZPage* split(size_t split_of_size);
  ZPage* split(uint8_t type, size_t split_of_size);
  ZPage* split_committed();

  bool is_in(zoffset offset) const;
  bool is_in(zaddress addr) const;

  uintptr_t local_offset(zoffset offset) const;
  uintptr_t local_offset(zaddress addr) const;

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
  void clear_remset_non_par(uintptr_t l_offset);
  void clear_remset_range_non_par(uintptr_t l_offset, size_t size);

  ZRememberedSetReverseIterator remset_reverse_iterator();
  ZRememberedSetIterator remset_iterator_current_limited(uintptr_t l_offset, size_t size);

  zaddress_unsafe find_base(volatile zpointer* p);

  template <typename Function>
  void oops_do_remembered(Function function);

  // Only visits remembered set entries for live objects
  template <typename Function>
  void oops_do_remembered_in_live(Function function);

  template <typename Function>
  void oops_do_current_remembered(Function function);

  void clear_current_remembered();
  void clear_previous_remembered();

  zaddress alloc_object(size_t size);
  zaddress alloc_object_atomic(size_t size);

  bool undo_alloc_object(zaddress addr, size_t size);
  bool undo_alloc_object_atomic(zaddress addr, size_t size);

  void log_msg(const char* msg_format, ...) const ATTRIBUTE_PRINTF(2, 3);

  void print_on_msg(outputStream* out, const char* msg) const;
  void print_on(outputStream* out) const;
  void print() const;

  // Verification
  bool is_remembered(volatile zpointer* p);
  void verify_live(uint32_t live_objects, size_t live_bytes, bool in_place) const;
};

class ZPageClosure {
public:
  virtual void do_page(const ZPage* page) = 0;
};

#endif // SHARE_GC_Z_ZPAGE_HPP
