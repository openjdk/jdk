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
#include "gc/g1/g1CardSetGroup.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CodeRootSet.hpp"

class G1CardSetGroup;
class G1FromCardCache;
class outputStream;

// Tracks the remembered set parts of a region:
//
// * code roots, i.e. nmethods that have embedded oops into the region associated with this
//   G1HeapRegionRemSet. They are maintained all the time independently of the card set group
//   below.
// * an associated card set group that conservatively records cards that at some time had
//   references into this region.
//   The card set group is maintained on demand; only regions with a card
//   set group in Complete state may be evacuated. If there is no card set group associated
//   with a G1HeapRegionRemSet/G1HeapRegion, it is implicitly in Untracked state, otherwise
//   its card set state is that of the card set group.
//
class G1HeapRegionRemSet : public CHeapObj<mtGC> {
  // A set of nmethods whose code contains pointers into
  // the region that owns this RSet.
  G1CodeRootSet _code_roots;

  G1CardSetGroup* _card_set_group;

  // Cached value of heap base address.
  static HeapWord* _heap_base_address;

  inline G1CardSet* card_set();

  inline const G1CardSet* card_set() const;

  inline bool card_set_is_empty() const;

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

  inline uint card_set_group_id() const;

  inline bool is_empty() const;

  inline bool occupancy_less_or_equal_than(size_t occ) const;

  inline size_t occupied() const;

  static void initialize(MemRegion reserved);

  inline uintptr_t to_card(OopOrNarrowOopStar from) const;

  inline bool is_tracked() const;
  inline bool is_updating() const;
  inline bool is_complete() const;

  inline const char* get_short_state_str() const;
  inline const char* get_state_str() const;

  inline void add_reference(OopOrNarrowOopStar from, G1FromCardCache& from_card_cache);

  // Clear the code roots.
  void clear_code_roots();

  void reset_code_root_table_scanner();

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
