/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CARDSET_HPP
#define SHARE_GC_G1_G1CARDSET_HPP

#include "memory/allocation.hpp"
#include "utilities/concurrentHashTable.hpp"

class G1CardSetAllocOptions;
class G1CardSetHashTable;
class G1CardSetHashTableValue;
class G1CardSetMemoryManager;
class Mutex;

// The result of an attempt to add a card to that card set.
enum G1AddCardResult {
  Overflow,  // The card set is more than full. The entry may have been added. Need
             // Coarsen and retry.
  Found,     // The card is already in the set.
  Added      // The card has been added to the set by this attempt.
};

class G1CardSetConfiguration {
  // Holds the number of bits required to cover the maximum card index for the
  // regions covered by this card set.
  uint _inline_ptr_bits_per_card;

  uint _max_cards_in_array;
  uint _num_buckets_in_howl;
  uint _max_cards_in_card_set;
  uint _cards_in_howl_threshold;
  uint _max_cards_in_howl_bitmap;
  uint _cards_in_howl_bitmap_threshold;
  uint _log2_max_cards_in_howl_bitmap;
  size_t _bitmap_hash_mask;
  uint _log2_card_regions_per_heap_region;
  uint _log2_cards_per_card_region;

  G1CardSetAllocOptions* _card_set_alloc_options;

  G1CardSetConfiguration(uint inline_ptr_bits_per_card,
                         uint max_cards_in_array,
                         double cards_in_bitmap_threshold_percent,
                         uint num_buckets_in_howl,
                         double cards_in_howl_threshold_percent,
                         uint max_cards_in_card_set,
                         uint log2_card_regions_per_heap_region);
  void init_card_set_alloc_options();

  void log_configuration();
public:

  // Initialize card set configuration from globals.
  G1CardSetConfiguration();
  // Initialize card set configuration from parameters.
  // Testing only.
  G1CardSetConfiguration(uint max_cards_in_array,
                         double cards_in_bitmap_threshold_percent,
                         uint max_buckets_in_howl,
                         double cards_in_howl_threshold_percent,
                         uint max_cards_in_cardset,
                         uint log2_card_region_per_region);

  ~G1CardSetConfiguration();

  // Inline pointer configuration
  uint inline_ptr_bits_per_card() const { return _inline_ptr_bits_per_card; }
  uint max_cards_in_inline_ptr() const;
  static uint max_cards_in_inline_ptr(uint bits_per_card);

  // Array of Cards configuration
  // Maximum number of cards in "Array of Cards" set; 0 to disable.
  // Always coarsen to next level if full, so no specific threshold.
  uint max_cards_in_array() const { return _max_cards_in_array; }

  // Bitmap within Howl card set container configuration
  uint max_cards_in_howl_bitmap() const { return _max_cards_in_howl_bitmap; }
  // (Approximate) Number of cards in bitmap to coarsen Howl Bitmap to Howl Full.
  uint cards_in_howl_bitmap_threshold() const { return _cards_in_howl_bitmap_threshold; }
  uint log2_max_cards_in_howl_bitmap() const {return _log2_max_cards_in_howl_bitmap;}

  // Howl card set container configuration
  uint num_buckets_in_howl() const { return _num_buckets_in_howl; }
  // Threshold at which to turn howling arrays into Full.
  uint cards_in_howl_threshold() const { return _cards_in_howl_threshold; }
  uint howl_bitmap_offset(uint card_idx) const { return card_idx & _bitmap_hash_mask; }
  // Given a card index, return the bucket in the array of card sets.
  uint howl_bucket_index(uint card_idx) { return card_idx >> _log2_max_cards_in_howl_bitmap; }

  // Full card configuration
  // Maximum number of cards in a non-full card set for a single region. Card sets
  // with more entries per region are coarsened to Full.
  uint max_cards_in_region() const { return _max_cards_in_card_set; }

  // Heap region virtualization: there are some limitations to how many cards the
  // containers can cover to save memory for the common case. Heap region virtualization
  // allows to use multiple entries in the G1CardSet hash table per area covered
  // by the remembered set (e.g. heap region); each such entry is called "card_region".
  //
  // The next two members give information about how many card regions are there
  // per area (heap region) and how many cards each card region has.

  // The log2 of the number of card regions per heap region configured.
  uint log2_card_regions_per_heap_region() const { return _log2_card_regions_per_heap_region; }
  // The log2 of the number of cards per card region. This is calculated from max_cards_in_region()
  // and above.
  uint log2_cards_per_card_region() const { return _log2_cards_per_card_region; }

  // Memory object types configuration
  // Number of distinctly sized memory objects on the card set heap.
  // Currently contains CHT-Nodes, ArrayOfCards, BitMaps, Howl
  static constexpr uint num_mem_object_types() { return 4; }
  // Returns the memory allocation options for the memory objects on the card set heap.
  const G1CardSetAllocOptions* mem_object_alloc_options(uint idx);

  // For a given memory object, get a descriptive name.
  static const char* mem_object_type_name_str(uint index);
};

// Collects coarsening statistics: how many attempts of each kind and how many
// failed due to a competing thread doing the coarsening first.
class G1CardSetCoarsenStats {
public:
  // Number of entries in the statistics tables: since we index with the source
  // container of the coarsening, this is the total number of combinations of
  // card set containers - 1.
  static constexpr size_t NumCoarsenCategories = 7;
  // Coarsening statistics for the possible ContainerPtr in the Howl card set
  // start from this offset.
  static constexpr size_t CoarsenHowlOffset = 4;

private:
  // Indices are "from" indices.
  size_t _coarsen_from[NumCoarsenCategories];
  size_t _coarsen_collision[NumCoarsenCategories];

public:
  G1CardSetCoarsenStats() { reset(); }

  void reset();

  void subtract_from(G1CardSetCoarsenStats& other);

  // Record a coarsening for the given tag/category. Collision should be true if
  // this coarsening lost the race to do the coarsening of that category.
  void record_coarsening(uint tag, bool collision);

  void print_on(outputStream* out);
};

// Set of card indexes comprising a remembered set on the Java heap. Card
// size is assumed to be card table card size.
//
// Technically it is implemented using a ConcurrentHashTable that stores a card
// set container for every region containing at least one card.
//
// There are in total five different containers, encoded in the ConcurrentHashTable
// node as ContainerPtr. A ContainerPtr may cover the whole region or just a part of
// it.
// See its description below for more information.
class G1CardSet : public CHeapObj<mtGCCardSet> {
  friend class G1CardSetTest;
  friend class G1CardSetMtTestTask;

  friend class G1TransferCard;

  friend class G1ReleaseCardsets;

  static G1CardSetCoarsenStats _coarsen_stats; // Coarsening statistics since VM start.
  static G1CardSetCoarsenStats _last_coarsen_stats; // Coarsening statistics at last GC.
public:
  // Two lower bits are used to encode the card set container types
  static const uintptr_t ContainerPtrHeaderSize = 2;

  // ContainerPtr represents the card set container  type of a given covered area.
  // It encodes a type in the LSBs, in addition to having a few significant values.
  //
  // Possible encodings:
  //
  // 0...00000 free               (Empty, should never happen)
  // 1...11111 full               All card indexes in the whole area this ContainerPtr covers are part of this container.
  // X...XXX00 inline-ptr-cards   A handful of card indexes covered by this ContainerPtr are encoded within the ContainerPtr.
  // X...XXX01 array of cards     The container is a contiguous array of card indexes.
  // X...XXX10 bitmap             The container uses a bitmap to determine whether a given index is part of this set.
  // X...XXX11 howl               This is a card set container containing an array of ContainerPtr, with each ContainerPtr
  //                              limited to a sub-range of the original range. Currently only one level of this
  //                              container is supported.
  using ContainerPtr = void*;
  // Coarsening happens in the order below:
  // ContainerInlinePtr -> ContainerArrayOfCards -> ContainerHowl -> Full
  // Corsening of containers inside the ContainerHowl happens in the order:
  // ContainerInlinePtr -> ContainerArrayOfCards -> ContainerBitMap -> Full
  static const uintptr_t ContainerInlinePtr      = 0x0;
  static const uintptr_t ContainerArrayOfCards   = 0x1;
  static const uintptr_t ContainerBitMap         = 0x2;
  static const uintptr_t ContainerHowl           = 0x3;

  // The special sentinel values
  static constexpr ContainerPtr FreeCardSet = nullptr;
  // Unfortunately we can't make (G1CardSet::ContainerPtr)-1 constexpr because
  // reinterpret_casts are forbidden in constexprs. Use a regular static instead.
  static ContainerPtr FullCardSet;

  static const uintptr_t ContainerPtrTypeMask = ((uintptr_t)1 << ContainerPtrHeaderSize) - 1;

  static ContainerPtr strip_container_type(ContainerPtr ptr) { return (ContainerPtr)((uintptr_t)ptr & ~ContainerPtrTypeMask); }

  static uint container_type(ContainerPtr ptr) { return (uintptr_t)ptr & ContainerPtrTypeMask; }

  template <class T>
  static T* container_ptr(ContainerPtr ptr);

private:
  G1CardSetMemoryManager* _mm;
  G1CardSetConfiguration* _config;

  G1CardSetHashTable* _table;

  // Total number of cards in this card set. This is a best-effort value, i.e. there may
  // be (slightly) more cards in the card set than this value in reality.
  size_t _num_occupied;

  ContainerPtr make_container_ptr(void* value, uintptr_t type);

  ContainerPtr acquire_container(ContainerPtr volatile* container_addr);
  // Returns true if the card set container should be released
  bool release_container(ContainerPtr container);
  // Release card set and free if needed.
  void release_and_maybe_free_container(ContainerPtr container);
  // Release card set and free (and it must be freeable).
  void release_and_must_free_container(ContainerPtr container);

  // Coarsens the card set container cur_container to the next level; tries to replace the
  // previous ContainerPtr with a new one which includes the given card_in_region.
  // coarsen_container does not transfer cards from cur_container
  // to the new container. Transfer is achieved by transfer_cards.
  // Returns true if this was the thread that coarsened the container (and added the card).
  bool coarsen_container(ContainerPtr volatile* container_addr,
                         ContainerPtr cur_container,
                         uint card_in_region, bool within_howl = false);

  ContainerPtr create_coarsened_array_of_cards(uint card_in_region, bool within_howl);

  // Transfer entries from source_card_set to a recently installed coarser storage type
  // We only need to transfer anything finer than ContainerBitMap. "Full" contains
  // all elements anyway.
  void transfer_cards(G1CardSetHashTableValue* table_entry, ContainerPtr source_container, uint card_region);
  void transfer_cards_in_howl(ContainerPtr parent_container, ContainerPtr source_container, uint card_region);

  G1AddCardResult add_to_container(ContainerPtr volatile* container_addr, ContainerPtr container, uint card_region, uint card, bool increment_total = true);

  G1AddCardResult add_to_inline_ptr(ContainerPtr volatile* container_addr, ContainerPtr container, uint card_in_region);
  G1AddCardResult add_to_array(ContainerPtr container, uint card_in_region);
  G1AddCardResult add_to_bitmap(ContainerPtr container, uint card_in_region);
  G1AddCardResult add_to_howl(ContainerPtr parent_container, uint card_region, uint card_in_region, bool increment_total = true);

  G1CardSetHashTableValue* get_or_add_container(uint card_region, bool* should_grow_table);
  G1CardSetHashTableValue* get_container(uint card_region);

  // Iterate over cards of a card set container during transfer of the cards from
  // one container to another. Executes
  //
  //     void operator ()(uint card_idx)
  //
  // on the given class.
  template <class CardVisitor>
  void iterate_cards_during_transfer(ContainerPtr const container, CardVisitor& vl);

  uint container_type_to_mem_object_type(uintptr_t type) const;
  uint8_t* allocate_mem_object(uintptr_t type);
  void free_mem_object(ContainerPtr container);

public:
  G1CardSetConfiguration* config() const { return _config; }

  // Create a new remembered set for a particular heap region.
  G1CardSet(G1CardSetConfiguration* config, G1CardSetMemoryManager* mm);
  virtual ~G1CardSet();

  // Adds the given card to this set, returning an appropriate result.
  // If incremental_count is true and the card has been added, updates the total count.
  G1AddCardResult add_card(uint card_region, uint card_in_region, bool increment_total = true);

  bool contains_card(uint card_region, uint card_in_region);

  void print_info(outputStream* st, uint card_region, uint card_in_region);

  // Returns whether this remembered set (and all sub-sets) have an occupancy
  // that is less or equal to the given occupancy.
  bool occupancy_less_or_equal_to(size_t limit) const;

  // Returns whether this remembered set (and all sub-sets) does not contain any entry.
  bool is_empty() const;

  // Returns the number of cards contained in this remembered set.
  size_t occupied() const;

  size_t num_containers();

  static G1CardSetCoarsenStats coarsen_stats();
  static void print_coarsen_stats(outputStream* out);

  // Returns size of the actual remembered set containers in bytes.
  size_t mem_size() const;
  size_t wasted_mem_size() const;
  // Returns the size of static data in bytes.
  static size_t static_mem_size();

  // Clear the entire contents of this remembered set.
  void clear();

  void print(outputStream* os);

  // Iterate over the container, calling a method on every card or card range contained
  // in the card container.
  // For every container, first calls
  //
  //   void start_iterate(uint tag, uint region_idx);
  //
  // Then for every card or card range it calls
  //
  //   void do_card(uint card_idx);
  //   void do_card_range(uint card_idx, uint length);
  //
  // where card_idx is the card index within that region_idx passed before in
  // start_iterate().
  //
  template <class CardOrRangeVisitor>
  void iterate_cards_or_ranges_in_container(ContainerPtr const container, CardOrRangeVisitor& cl);

  class ContainerPtrClosure {
  public:
    virtual void do_containerptr(uint region_idx, size_t num_occupied, ContainerPtr container) = 0;
  };

  void iterate_containers(ContainerPtrClosure* cl, bool safepoint = false);

  class CardClosure {
  public:
    virtual void do_card(uint region_idx, uint card_idx) = 0;
  };

  void iterate_cards(CardClosure& cl);
};

class G1CardSetHashTableValue {
public:
  using ContainerPtr = G1CardSet::ContainerPtr;

  const uint _region_idx;
  uint volatile _num_occupied;
  ContainerPtr volatile _container;

  G1CardSetHashTableValue(uint region_idx, ContainerPtr container) : _region_idx(region_idx), _num_occupied(0), _container(container) { }
};

class G1CardSetHashTableConfig : public StackObj {
public:
  using Value = G1CardSetHashTableValue;

  static uintx get_hash(Value const& value, bool* is_dead) {
    *is_dead = false;
    return value._region_idx;
  }
  static void* allocate_node(void* context, size_t size, Value const& value);
  static void free_node(void* context, void* memory, Value const& value);
};

using CardSetHash = ConcurrentHashTable<G1CardSetHashTableConfig, mtGCCardSet>;

#endif // SHARE_GC_G1_G1CARDSET_HPP
