/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_CARDTABLE_HPP
#define SHARE_GC_SHARED_CARDTABLE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/align.hpp"

class CardTable: public CHeapObj<mtGC> {
  friend class VMStructs;
public:
  typedef uint8_t CardValue;

  // All code generators assume that the size of a card table entry is one byte.
  // They need to be updated to reflect any change to this.
  // This code can typically be found by searching for the byte_map_base() method.
  STATIC_ASSERT(sizeof(CardValue) == 1);

protected:
  // The declaration order of these const fields is important; see the
  // constructor before changing.
  const MemRegion _whole_heap;       // the region covered by the card table
  const size_t    _page_size;        // page size used when mapping _byte_map
  size_t          _byte_map_size;    // in bytes
  CardValue*      _byte_map;         // the card marking array
  CardValue*      _byte_map_base;

  // Some barrier sets create tables whose elements correspond to parts of
  // the heap; the CardTableBarrierSet is an example.  Such barrier sets will
  // normally reserve space for such tables, and commit parts of the table
  // "covering" parts of the heap that are committed. At most one covered
  // region per generation is needed.
  static constexpr int max_covered_regions = 2;

  // The covered regions should be in address order.
  MemRegion _covered[max_covered_regions];

  // The last card is a guard card; never committed.
  MemRegion _guard_region;

  inline size_t compute_byte_map_size(size_t num_bytes);

  enum CardValues {
    clean_card                  = (CardValue)-1,

    dirty_card                  =  0,
    CT_MR_BS_last_reserved      =  1
  };

  // a word's worth (row) of clean card values
  static const intptr_t clean_card_row = (intptr_t)(-1);

  // CardTable entry size
  static uint _card_shift;
  static uint _card_size;
  static uint _card_size_in_words;

  size_t last_valid_index() const {
    return cards_required(_whole_heap.word_size()) - 1;
  }

private:
  void initialize_covered_region(void* region0_start, void* region1_start);

  MemRegion committed_for(const MemRegion mr) const;
public:
  CardTable(MemRegion whole_heap);
  virtual ~CardTable() = default;

  void initialize(void* region0_start, void* region1_start);

  // *** Barrier set functions.

  // Initialization utilities; covered_words is the size of the covered region
  // in, um, words.
  inline size_t cards_required(size_t covered_words) const {
    assert(is_aligned(covered_words, _card_size_in_words), "precondition");
    return covered_words / _card_size_in_words;
  }

  // Dirty the bytes corresponding to "mr" (not all of which must be
  // covered.)
  void dirty_MemRegion(MemRegion mr);

  // Clear (to clean_card) the bytes entirely contained within "mr" (not
  // all of which must be covered.)
  void clear_MemRegion(MemRegion mr);

  // Return true if "p" is at the start of a card.
  static bool is_card_aligned(HeapWord* p) {
    return is_aligned(p, card_size());
  }

  // Mapping from address to card marking array entry
  CardValue* byte_for(const void* p) const {
    assert(_whole_heap.contains(p),
           "Attempt to access p = " PTR_FORMAT " out of bounds of "
           " card marking array's _whole_heap = [" PTR_FORMAT "," PTR_FORMAT ")",
           p2i(p), p2i(_whole_heap.start()), p2i(_whole_heap.end()));
    CardValue* result = &_byte_map_base[uintptr_t(p) >> _card_shift];
    assert(result >= _byte_map && result < _byte_map + _byte_map_size,
           "out of bounds accessor for card marking array");
    return result;
  }

  // The card table byte one after the card marking array
  // entry for argument address. Typically used for higher bounds
  // for loops iterating through the card table.
  CardValue* byte_after(const void* p) const {
    return byte_for(p) + 1;
  }

  void invalidate(MemRegion mr);

  // Provide read-only access to the card table array.
  const CardValue* byte_for_const(const void* p) const {
    return byte_for(p);
  }
  const CardValue* byte_after_const(const void* p) const {
    return byte_after(p);
  }

  // Mapping from card marking array entry to address of first word
  HeapWord* addr_for(const CardValue* p) const {
    assert(p >= _byte_map && p < _byte_map + _byte_map_size,
           "out of bounds access to card marking array. p: " PTR_FORMAT
           " _byte_map: " PTR_FORMAT " _byte_map + _byte_map_size: " PTR_FORMAT,
           p2i(p), p2i(_byte_map), p2i(_byte_map + _byte_map_size));
    // As _byte_map_base may be "negative" (the card table has been allocated before
    // the heap in memory), do not use pointer_delta() to avoid the assertion failure.
    size_t delta = p - _byte_map_base;
    HeapWord* result = (HeapWord*) (delta << _card_shift);
    assert(_whole_heap.contains(result),
           "Returning result = " PTR_FORMAT " out of bounds of "
           " card marking array's _whole_heap = [" PTR_FORMAT "," PTR_FORMAT ")",
           p2i(result), p2i(_whole_heap.start()), p2i(_whole_heap.end()));
    return result;
  }

  // Mapping from address to card marking array index.
  size_t index_for(void* p) {
    assert(_whole_heap.contains(p),
           "Attempt to access p = " PTR_FORMAT " out of bounds of "
           " card marking array's _whole_heap = [" PTR_FORMAT "," PTR_FORMAT ")",
           p2i(p), p2i(_whole_heap.start()), p2i(_whole_heap.end()));
    return byte_for(p) - _byte_map;
  }

  CardValue* byte_for_index(const size_t card_index) const {
    return _byte_map + card_index;
  }

  // Resize one of the regions covered by the remembered set.
  void resize_covered_region(MemRegion new_region);

  // *** Card-table-RemSet-specific things.

  static uintx ct_max_alignment_constraint();

  static uint card_shift() {
    return _card_shift;
  }

  static uint card_size() {
    return _card_size;
  }

  static uint card_size_in_words() {
    return _card_size_in_words;
  }

  static constexpr CardValue clean_card_val()          { return clean_card; }
  static constexpr CardValue dirty_card_val()          { return dirty_card; }
  static constexpr intptr_t clean_card_row_val()   { return clean_card_row; }

  // Initialize card size
  static void initialize_card_size();

  // Card marking array base (adjusted for heap low boundary)
  // This would be the 0th element of _byte_map, if the heap started at 0x0.
  // But since the heap starts at some higher address, this points to somewhere
  // before the beginning of the actual _byte_map.
  CardValue* byte_map_base() const { return _byte_map_base; }

  virtual bool is_in_young(const void* p) const = 0;

  // Print a description of the memory for the card table
  virtual void print_on(outputStream* st) const;

  // val_equals -> it will check that all cards covered by mr equal val
  // !val_equals -> it will check that all cards covered by mr do not equal val
  void verify_region(MemRegion mr, CardValue val, bool val_equals) PRODUCT_RETURN;
  void verify_not_dirty_region(MemRegion mr) PRODUCT_RETURN;
  void verify_dirty_region(MemRegion mr) PRODUCT_RETURN;
};

#endif // SHARE_GC_SHARED_CARDTABLE_HPP
