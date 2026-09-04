/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONREMSET_HPP
#define SHARE_GC_G1_G1HEAPREGIONREMSET_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CodeRootSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"

class G1FromCardCache;
class outputStream;

class G1HeapRegionRemSet : public CHeapObj<mtGC> {
  // A set of nmethods whose code contains pointers into
  // the region that owns this RSet.
  G1CodeRootSet _code_roots;

  G1CardSetGroup* _card_set_group;

  // Cached value of heap base address.
  static HeapWord* _heap_base_address;

  G1CardSet* card_set() {
    assert(has_card_set_group(), "pre-condition");
    return card_set_group()->card_set();
  }

  const G1CardSet* card_set() const {
    assert(has_card_set_group(), "pre-condition");
    return card_set_group()->card_set();
  }

  bool card_set_is_empty() const {
    return !has_card_set_group() || card_set()->is_empty();
  }

public:
  G1HeapRegionRemSet();
  ~G1HeapRegionRemSet();

  void install_card_set_group(G1CardSetGroup* card_set_group) {
    assert(card_set_group != nullptr, "pre-condition");
    assert(_card_set_group == nullptr, "pre-condition");

    _card_set_group = card_set_group;
  }

  void uninstall_card_set_group();

  bool has_card_set_group() const {
    return _card_set_group != nullptr;
  }

  G1CardSetGroup* card_set_group() {
    return _card_set_group;
  }

  const G1CardSetGroup* card_set_group() const {
    return _card_set_group;
  }

  uint card_set_group_id() const {
    assert(has_card_set_group(), "pre-condition");
    return card_set_group()->group_id();
  }

  bool is_empty() const {
    return (code_roots_length() == 0) && card_set_is_empty();
  }

  bool occupancy_less_or_equal_than(size_t occ) const {
    return (code_roots_length() == 0) && card_set()->occupancy_less_or_equal_to(occ);
  }

  // Iterate the cards in this remembered set for merging them into the card table.
  // The passed closure must be a CardOrRangeVisitor; we use a template parameter
  // to pass it in to facilitate inlining as much as possible.
  template <class CardOrRangeVisitor>
  inline void iterate_for_merge(CardOrRangeVisitor& cl);

  template <class CardOrRangeVisitor>
  inline static void iterate_for_merge(G1CardSet* card_set, CardOrRangeVisitor& cl);

  size_t occupied() {
    assert(has_card_set_group(), "pre-condition");
    return card_set()->occupied();
  }

  static void initialize(MemRegion reserved);

  inline uintptr_t to_card(OopOrNarrowOopStar from) const;

private:
  enum RemSetState {
    Untracked,
    Updating,
    Complete
  };

  RemSetState _state;

  static const char* _state_strings[];
  static const char* _short_state_strings[];
public:

  const char* get_state_str() const { return _state_strings[_state]; }
  const char* get_short_state_str() const { return _short_state_strings[_state]; }

  bool is_tracked() { return _state != Untracked; }
  bool is_updating() { return _state == Updating; }
  bool is_complete() { return _state == Complete; }

  inline void set_state_untracked();
  inline void set_state_updating();
  inline void set_state_complete();

  inline void add_reference(OopOrNarrowOopStar from, G1FromCardCache& from_card_cache);

  // Clear the region-specific remset state.
  void clear();

  void reset_code_root_table_scanner();
  void reset_table_scanner();

  G1MonotonicArenaMemoryStats card_set_memory_stats() const;

  // The actual # of bytes this hr_remset takes up. Also includes the code
  // root set.
  size_t mem_size() {
    return sizeof(G1HeapRegionRemSet) - sizeof(G1CodeRootSet) + code_roots_mem_size();
  }

  // Returns the memory occupancy of all static data structures associated
  // with remembered sets.
  static size_t static_mem_size() {
    return G1CardSet::static_mem_size();
  }

  static void print_static_mem_size(outputStream* out);

  inline bool contains_reference(OopOrNarrowOopStar from);

  inline void print_info(outputStream* st, OopOrNarrowOopStar from);

  // Routines for managing the code roots that point into the heap region
  // that owns this RSet.
  void add_code_root(nmethod* nm);
  void bulk_remove_code_roots();
  void prepare_for_adding_code_roots(size_t num_code_roots);

  // Applies blk->do_nmethod() to each of the entries in _code_roots
  void code_roots_do(NMethodClosure* blk) const;
  // Clean out code roots not having an oop pointing into this region any more.
  void clean_code_roots(G1HeapRegion* hr);

  // Returns the number of elements in _code_roots
  size_t code_roots_length() const {
    return _code_roots.length();
  }

  // Returns true if the code roots contains the given
  // nmethod.
  bool code_roots_contains(nmethod* nm) {
    return _code_roots.contains(nm);
  }

  // Returns the amount of memory, in bytes, currently
  // consumed by the code roots.
  size_t code_roots_mem_size();

#ifndef PRODUCT
  static void test();
#endif
};

#endif // SHARE_GC_G1_G1HEAPREGIONREMSET_HPP
