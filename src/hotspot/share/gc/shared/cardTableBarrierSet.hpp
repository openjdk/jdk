/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_CARDTABLEBARRIERSET_HPP
#define SHARE_GC_SHARED_CARDTABLEBARRIERSET_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/memRegion.hpp"
#include "runtime/atomic.hpp"
#include "utilities/align.hpp"

// This kind of "BarrierSet" allows a "CollectedHeap" to detect and
// enumerate ref fields that have been modified (since the last
// enumeration.)

// As it currently stands, this barrier is *imprecise*: when a ref field in
// an object "o" is modified, the card table entry for the card containing
// the head of "o" is dirtied, not necessarily the card containing the
// modified field itself.  For object arrays, however, the barrier *is*
// precise; only the card containing the modified element is dirtied.
// Closures used to scan dirty cards should take these
// considerations into account.

class CardTableBarrierSet: public BarrierSet {
  // Some classes get to look at some private stuff.
  friend class VMStructs;

protected:
  typedef CardTable::CardValue CardValue;
  Atomic<CardTable*> _card_table;

  CardTableBarrierSet(BarrierSetAssembler* barrier_set_assembler,
                      BarrierSetC1* barrier_set_c1,
                      BarrierSetC2* barrier_set_c2,
                      CardTable* card_table,
                      const BarrierSet::FakeRtti& fake_rtti);

public:
  CardTableBarrierSet(CardTable* card_table);
  virtual ~CardTableBarrierSet();

  inline static CardTableBarrierSet* barrier_set() {
    return barrier_set_cast<CardTableBarrierSet>(BarrierSet::barrier_set());
  }

  template <DecoratorSet decorators, typename T>
  inline void write_ref_field_pre(T* addr) {}

  // Record a reference update. Note that these versions are precise!
  // The scanning code has to handle the fact that the write barrier may be
  // either precise or imprecise. We make non-virtual inline variants of
  // these functions here for performance.
  template <DecoratorSet decorators, typename T>
  inline void write_ref_field_post(T *addr);

  // Causes all refs in "mr" to be assumed to be modified (by this JavaThread).
  virtual void write_region(MemRegion mr);

  // Operations on arrays, or general regions (e.g., for "clone") may be
  // optimized by some barriers.

  // Below length is the # array elements being written
  virtual void write_ref_array_pre(oop* dst, size_t length,
                                   bool dest_uninitialized) {}
  virtual void write_ref_array_pre(narrowOop* dst, size_t length,
                                   bool dest_uninitialized) {}
  // Below count is the # array elements being written, starting
  // at the address "start", which may not necessarily be HeapWord-aligned
  inline void write_ref_array(HeapWord* start, size_t count);

  CardTable* card_table() { return _card_table.load_relaxed(); }
  CardTable* card_table() const { return _card_table.load_relaxed(); }

  CardValue* card_table_base_const() const {
    assert(UseSerialGC || UseParallelGC, "Only these GCs have constant card table base");
    return card_table()->byte_map_base();
  }

  virtual void on_slowpath_allocation_exit(JavaThread* thread, oop new_obj);

  virtual void print_on(outputStream* st) const;

  template <DecoratorSet decorators, typename BarrierSetT = CardTableBarrierSet>
  class AccessBarrier: public BarrierSet::AccessBarrier<decorators, BarrierSetT> {
    typedef BarrierSet::AccessBarrier<decorators, BarrierSetT> Raw;

  public:
    template <typename T>
    static void oop_store_in_heap(T* addr, oop value);
    template <typename T>
    static oop oop_atomic_cmpxchg_in_heap(T* addr, oop compare_value, oop new_value);
    template <typename T>
    static oop oop_atomic_xchg_in_heap(T* addr, oop new_value);

    template <typename T>
    static OopCopyResult oop_arraycopy_in_heap(arrayOop src_obj, size_t src_offset_in_bytes, T* src_raw,
                                               arrayOop dst_obj, size_t dst_offset_in_bytes, T* dst_raw,
                                               size_t length);

    static void clone_in_heap(oop src, oop dst, size_t size);

    static void oop_store_in_heap_at(oop base, ptrdiff_t offset, oop value) {
      oop_store_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), value);
    }

    static oop oop_atomic_xchg_in_heap_at(oop base, ptrdiff_t offset, oop new_value) {
      return oop_atomic_xchg_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), new_value);
    }

    static oop oop_atomic_cmpxchg_in_heap_at(oop base, ptrdiff_t offset, oop compare_value, oop new_value) {
      return oop_atomic_cmpxchg_in_heap(AccessInternal::oop_field_addr<decorators>(base, offset), compare_value, new_value);
    }
  };
};

template<>
struct BarrierSet::GetName<CardTableBarrierSet> {
  static const BarrierSet::Name value = BarrierSet::CardTableBarrierSet;
};

template<>
struct BarrierSet::GetType<BarrierSet::CardTableBarrierSet> {
  typedef ::CardTableBarrierSet type;
};

#endif // SHARE_GC_SHARED_CARDTABLEBARRIERSET_HPP
