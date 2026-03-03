/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1CardSetMemory.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/globalDefinitions.hpp"

G1CardSet::ContainerPtr G1CardSet::FullCardSet = (G1CardSet::ContainerPtr)-1;
uint G1CardSet::_split_card_shift = 0;
size_t G1CardSet::_split_card_mask = 0;

class G1CardSetHashTableConfig : public StackObj {
public:
  using Value = G1CardSetHashTableValue;

  static uintx get_hash(Value const& value, bool* is_dead);
  static void* allocate_node(void* context, size_t size, Value const& value);
  static void free_node(void* context, void* memory, Value const& value);
};

using CardSetHash = ConcurrentHashTable<G1CardSetHashTableConfig, mtGCCardSet>;

static uint default_log2_card_regions_per_region() {
  uint log2_card_regions_per_heap_region = 0;

  const uint card_container_limit = G1CardSetContainer::LogCardsPerRegionLimit;
  if (card_container_limit < (uint)G1HeapRegion::LogCardsPerRegion) {
    log2_card_regions_per_heap_region = (uint)G1HeapRegion::LogCardsPerRegion - card_container_limit;
  }

  return log2_card_regions_per_heap_region;
}

G1CardSetConfiguration::G1CardSetConfiguration() :
  G1CardSetConfiguration(G1HeapRegion::LogCardsPerRegion - default_log2_card_regions_per_region(),                                                                                   /* inline_ptr_bits_per_card */
                         G1RemSetArrayOfCardsEntries,                               /* max_cards_in_array */
                         (double)G1RemSetCoarsenHowlBitmapToHowlFullPercent / 100,  /* cards_in_bitmap_threshold_percent */
                         G1RemSetHowlNumBuckets,                                    /* num_buckets_in_howl */
                         (double)G1RemSetCoarsenHowlToFullPercent / 100,            /* cards_in_howl_threshold_percent */
                         (uint)G1HeapRegion::CardsPerRegion >> default_log2_card_regions_per_region(),
                                                                                    /* max_cards_in_card_set */
                         default_log2_card_regions_per_region())                    /* log2_card_regions_per_region */
{
  assert((_log2_card_regions_per_heap_region + _log2_cards_per_card_region) == (uint)G1HeapRegion::LogCardsPerRegion,
         "inconsistent heap region virtualization setup");
}

G1CardSetConfiguration::G1CardSetConfiguration(uint max_cards_in_array,
                                               double cards_in_bitmap_threshold_percent,
                                               uint max_buckets_in_howl,
                                               double cards_in_howl_threshold_percent,
                                               uint max_cards_in_card_set,
                                               uint log2_card_regions_per_region) :
  G1CardSetConfiguration(log2i_exact(max_cards_in_card_set),                   /* inline_ptr_bits_per_card */
                         max_cards_in_array,                                   /* max_cards_in_array */
                         cards_in_bitmap_threshold_percent,                    /* cards_in_bitmap_threshold_percent */
                         G1CardSetHowl::num_buckets(max_cards_in_card_set,     /* num_buckets_in_howl */
                                                    max_cards_in_array,
                                                    max_buckets_in_howl),
                         cards_in_howl_threshold_percent,                      /* cards_in_howl_threshold_percent */
                         max_cards_in_card_set,                                /* max_cards_in_card_set */
                         log2_card_regions_per_region)
{ }

G1CardSetConfiguration::G1CardSetConfiguration(uint inline_ptr_bits_per_card,
                                               uint max_cards_in_array,
                                               double cards_in_bitmap_threshold_percent,
                                               uint num_buckets_in_howl,
                                               double cards_in_howl_threshold_percent,
                                               uint max_cards_in_card_set,
                                               uint log2_card_regions_per_heap_region) :
  _inline_ptr_bits_per_card(inline_ptr_bits_per_card),
  _max_cards_in_array(max_cards_in_array),
  _num_buckets_in_howl(num_buckets_in_howl),
  _max_cards_in_card_set(max_cards_in_card_set),
  _cards_in_howl_threshold(max_cards_in_card_set * cards_in_howl_threshold_percent),
  _max_cards_in_howl_bitmap(G1CardSetHowl::bitmap_size(_max_cards_in_card_set, _num_buckets_in_howl)),
  _cards_in_howl_bitmap_threshold(_max_cards_in_howl_bitmap * cards_in_bitmap_threshold_percent),
  _log2_max_cards_in_howl_bitmap(log2i_exact(_max_cards_in_howl_bitmap)),
  _bitmap_hash_mask((1U << _log2_max_cards_in_howl_bitmap) - 1),
  _log2_card_regions_per_heap_region(log2_card_regions_per_heap_region),
  _log2_cards_per_card_region(log2i_exact(_max_cards_in_card_set)) {

  assert(_inline_ptr_bits_per_card <= G1CardSetContainer::LogCardsPerRegionLimit,
         "inline_ptr_bits_per_card (%u) is wasteful, can represent more than maximum possible card indexes (%u)",
         _inline_ptr_bits_per_card, G1CardSetContainer::LogCardsPerRegionLimit);
  assert(_inline_ptr_bits_per_card >= _log2_cards_per_card_region,
         "inline_ptr_bits_per_card (%u) must be larger than possible card indexes (%u)",
         _inline_ptr_bits_per_card, _log2_cards_per_card_region);

  assert(cards_in_bitmap_threshold_percent >= 0.0 && cards_in_bitmap_threshold_percent <= 1.0,
         "cards_in_bitmap_threshold_percent (%1.2f) out of range", cards_in_bitmap_threshold_percent);

  assert(cards_in_howl_threshold_percent >= 0.0 && cards_in_howl_threshold_percent <= 1.0,
         "cards_in_howl_threshold_percent (%1.2f) out of range", cards_in_howl_threshold_percent);

  assert(is_power_of_2(_max_cards_in_card_set),
         "max_cards_in_card_set must be a power of 2: %u", _max_cards_in_card_set);
  assert(_max_cards_in_card_set <= G1CardSetContainer::cards_per_region_limit(),
         "Specified number of cards (%u) exceeds maximum representable (%u)",
         _max_cards_in_card_set, G1CardSetContainer::cards_per_region_limit());

  assert(_cards_in_howl_bitmap_threshold <= _max_cards_in_howl_bitmap,
         "Threshold to coarsen Howl Bitmap to Howl Full (%u) must be "
         "smaller than or equal to max number of cards in Howl bitmap (%u)",
         _cards_in_howl_bitmap_threshold, _max_cards_in_howl_bitmap);
  assert(_cards_in_howl_threshold <= _max_cards_in_card_set,
         "Threshold to coarsen Howl to Full (%u) must be "
         "smaller than or equal to max number of cards in card region (%u)",
         _cards_in_howl_threshold, _max_cards_in_card_set);

  init_card_set_alloc_options();
  log_configuration();
}

G1CardSetConfiguration::~G1CardSetConfiguration() {
  FREE_C_HEAP_ARRAY(size_t, _card_set_alloc_options);
}

void G1CardSetConfiguration::init_card_set_alloc_options() {
  _card_set_alloc_options = NEW_C_HEAP_ARRAY(G1CardSetAllocOptions, num_mem_object_types(), mtGC);
  new (&_card_set_alloc_options[0]) G1CardSetAllocOptions((uint)CardSetHash::get_node_size());
  new (&_card_set_alloc_options[1]) G1CardSetAllocOptions((uint)G1CardSetArray::size_in_bytes(_max_cards_in_array), 2, 256);
  new (&_card_set_alloc_options[2]) G1CardSetAllocOptions((uint)G1CardSetBitMap::size_in_bytes(_max_cards_in_howl_bitmap), 2, 256);
  new (&_card_set_alloc_options[3]) G1CardSetAllocOptions((uint)G1CardSetHowl::size_in_bytes(_num_buckets_in_howl), 2, 256);
}

void G1CardSetConfiguration::log_configuration() {
  log_debug_p(gc, remset)("Card Set container configuration: "
                          "InlinePtr #cards %u size %zu "
                          "Array Of Cards #cards %u size %zu "
                          "Howl #buckets %u coarsen threshold %u "
                          "Howl Bitmap #cards %u size %zu coarsen threshold %u "
                          "Card regions per heap region %u cards per card region %u",
                          max_cards_in_inline_ptr(), sizeof(void*),
                          max_cards_in_array(), G1CardSetArray::size_in_bytes(max_cards_in_array()),
                          num_buckets_in_howl(), cards_in_howl_threshold(),
                          max_cards_in_howl_bitmap(), G1CardSetBitMap::size_in_bytes(max_cards_in_howl_bitmap()), cards_in_howl_bitmap_threshold(),
                          (uint)1 << log2_card_regions_per_heap_region(),
                          max_cards_in_region());
}

uint G1CardSetConfiguration::max_cards_in_inline_ptr() const {
  return max_cards_in_inline_ptr(_inline_ptr_bits_per_card);
}

uint G1CardSetConfiguration::max_cards_in_inline_ptr(uint bits_per_card) {
  return G1CardSetInlinePtr::max_cards_in_inline_ptr(bits_per_card);
}

const G1CardSetAllocOptions* G1CardSetConfiguration::mem_object_alloc_options(uint idx) {
  return &_card_set_alloc_options[idx];
}

const char* G1CardSetConfiguration::mem_object_type_name_str(uint index) {
  const char* names[] = { "Node", "Array", "Bitmap", "Howl" };
  return names[index];
}

void G1CardSetCoarsenStats::reset() {
  STATIC_ASSERT(ARRAY_SIZE(_coarsen_from) == ARRAY_SIZE(_coarsen_collision));
  for (uint i = 0; i < ARRAY_SIZE(_coarsen_from); i++) {
    _coarsen_from[i].store_relaxed(0);
    _coarsen_collision[i].store_relaxed(0);
  }
}

void G1CardSetCoarsenStats::set(G1CardSetCoarsenStats& other) {
  STATIC_ASSERT(ARRAY_SIZE(_coarsen_from) == ARRAY_SIZE(_coarsen_collision));
  for (uint i = 0; i < ARRAY_SIZE(_coarsen_from); i++) {
    _coarsen_from[i].store_relaxed(other._coarsen_from[i].load_relaxed());
    _coarsen_collision[i].store_relaxed(other._coarsen_collision[i].load_relaxed());
  }
}

void G1CardSetCoarsenStats::subtract_from(G1CardSetCoarsenStats& other) {
  STATIC_ASSERT(ARRAY_SIZE(_coarsen_from) == ARRAY_SIZE(_coarsen_collision));
  for (uint i = 0; i < ARRAY_SIZE(_coarsen_from); i++) {
    _coarsen_from[i].store_relaxed(other._coarsen_from[i].load_relaxed() - _coarsen_from[i].load_relaxed());
    _coarsen_collision[i].store_relaxed(other._coarsen_collision[i].load_relaxed() - _coarsen_collision[i].load_relaxed());
  }
}

void G1CardSetCoarsenStats::record_coarsening(uint tag, bool collision) {
  assert(tag < ARRAY_SIZE(_coarsen_from), "tag %u out of bounds", tag);
  _coarsen_from[tag].add_then_fetch(1u, memory_order_relaxed);
  if (collision) {
    _coarsen_collision[tag].add_then_fetch(1u, memory_order_relaxed);
  }
}

void G1CardSetCoarsenStats::print_on(outputStream* out) {
  out->print_cr("Inline->AoC %zu (%zu) "
                "AoC->Howl %zu (%zu) "
                "Howl->Full %zu (%zu) "
                "Inline->AoC %zu (%zu) "
                "AoC->BitMap %zu (%zu) "
                "BitMap->Full %zu (%zu) ",
                _coarsen_from[0].load_relaxed(), _coarsen_collision[0].load_relaxed(),
                _coarsen_from[1].load_relaxed(), _coarsen_collision[1].load_relaxed(),
                // There is no BitMap at the first level so we can't .
                _coarsen_from[3].load_relaxed(), _coarsen_collision[3].load_relaxed(),
                _coarsen_from[4].load_relaxed(), _coarsen_collision[4].load_relaxed(),
                _coarsen_from[5].load_relaxed(), _coarsen_collision[5].load_relaxed(),
                _coarsen_from[6].load_relaxed(), _coarsen_collision[6].load_relaxed()
               );
}

class G1CardSetHashTable : public CHeapObj<mtGCCardSet> {
  using ContainerPtr = G1CardSet::ContainerPtr;
  using CHTScanTask = CardSetHash::ScanTask;

  const static uint BucketClaimSize = 16;
  // The claim size for group cardsets should be smaller to facilitate
  // better work distribution. The group cardsets should be larger than
  // the per region cardsets.
  const static uint GroupBucketClaimSize = 4;
  // Did we insert at least one card in the table?
  Atomic<bool> _inserted_card;

  G1CardSetMemoryManager* _mm;
  CardSetHash _table;
  CHTScanTask _table_scanner;

  class G1CardSetHashTableLookUp : public StackObj {
    uint _region_idx;
  public:
    explicit G1CardSetHashTableLookUp(uint region_idx) : _region_idx(region_idx) { }

    uintx get_hash() const { return G1CardSetHashTable::get_hash(_region_idx); }

    bool equals(G1CardSetHashTableValue* value) {
      return value->_region_idx == _region_idx;
    }

    bool is_dead(G1CardSetHashTableValue*) {
      return false;
    }
  };

  class G1CardSetHashTableFound : public StackObj {
    G1CardSetHashTableValue* _value;
  public:
    G1CardSetHashTableFound() : _value(nullptr) { }

    void operator()(G1CardSetHashTableValue* value) {
      _value = value;
    }

    G1CardSetHashTableValue* value() const { return _value; }
  };

public:
  static const size_t InitialLogTableSize = 2;

  G1CardSetHashTable(G1CardSetMemoryManager* mm,
                     size_t initial_log_table_size = InitialLogTableSize) :
    _inserted_card(false),
    _mm(mm),
    _table(Mutex::service-1,
           mm,
           initial_log_table_size,
           false /* enable_statistics */),
    _table_scanner(&_table, BucketClaimSize) {
  }

  ~G1CardSetHashTable() {
    reset();
  }

  G1CardSetHashTableValue* get_or_add(uint region_idx, bool* should_grow) {
    G1CardSetHashTableLookUp lookup(region_idx);
    G1CardSetHashTableFound found;

    if (_table.get(Thread::current(), lookup, found)) {
      return found.value();
    }

    G1CardSetHashTableValue value(region_idx, G1CardSetInlinePtr());
    bool inserted = _table.insert_get(Thread::current(), lookup, value, found, should_grow);

    if (!_inserted_card.load_relaxed() && inserted) {
      // It does not matter to us who is setting the flag so a regular atomic store
      // is sufficient.
      _inserted_card.store_relaxed(true);
    }

    return found.value();
  }

  static uint get_hash(uint region_idx) {
    return region_idx;
  }

  G1CardSetHashTableValue* get(uint region_idx) {
    G1CardSetHashTableLookUp lookup(region_idx);
    G1CardSetHashTableFound found;

    _table.get(Thread::current(), lookup, found);
    return found.value();
  }

  template <typename SCAN_FUNC>
  void iterate_safepoint(SCAN_FUNC& scan_f) {
    _table_scanner.do_safepoint_scan(scan_f);
  }

  template <typename SCAN_FUNC>
  void iterate(SCAN_FUNC& scan_f) {
    _table.do_scan(Thread::current(), scan_f);
  }

  void reset() {
    if (_inserted_card.load_relaxed()) {
      _table.unsafe_reset(InitialLogTableSize);
      _inserted_card.store_relaxed(false);
    }
  }

  void reset_table_scanner() {
    reset_table_scanner(BucketClaimSize);
  }

  void reset_table_scanner_for_groups() {
    reset_table_scanner(GroupBucketClaimSize);
  }

  void reset_table_scanner(uint claim_size) {
    _table_scanner.set(&_table, claim_size);
  }

  void grow() {
    size_t new_limit = _table.get_size_log2(Thread::current()) + 1;
    _table.grow(Thread::current(), new_limit);
  }

  size_t mem_size() {
    return sizeof(*this) +
      _table.get_mem_size(Thread::current()) - sizeof(_table);
  }

  size_t log_table_size() { return _table.get_size_log2(Thread::current()); }
};

uintx G1CardSetHashTableConfig::get_hash(Value const& value, bool* is_dead) {
  *is_dead = false;
  return G1CardSetHashTable::get_hash(value._region_idx);
}

void* G1CardSetHashTableConfig::allocate_node(void* context, size_t size, Value const& value) {
  G1CardSetMemoryManager* mm = (G1CardSetMemoryManager*)context;
  return mm->allocate_node();
}

void G1CardSetHashTableConfig::free_node(void* context, void* memory, Value const& value) {
  G1CardSetMemoryManager* mm = (G1CardSetMemoryManager*)context;
  mm->free_node(memory);
}

G1CardSetCoarsenStats G1CardSet::_coarsen_stats;
G1CardSetCoarsenStats G1CardSet::_last_coarsen_stats;

G1CardSet::G1CardSet(G1CardSetConfiguration* config, G1CardSetMemoryManager* mm) :
  _mm(mm),
  _config(config),
  _table(new G1CardSetHashTable(mm)),
  _num_occupied(0) {
}

G1CardSet::~G1CardSet() {
  delete _table;
  _mm->flush();
}

void G1CardSet::initialize(MemRegion reserved) {
  const uint BitsInUint = sizeof(uint) * BitsPerByte;
  const uint CardBitsWithinCardRegion = MIN2((uint)G1HeapRegion::LogCardsPerRegion, G1CardSetContainer::LogCardsPerRegionLimit);

  // Check if the number of cards within a region fits an uint.
  if (CardBitsWithinCardRegion > BitsInUint) {
    vm_exit_during_initialization("Can not represent all cards in a card region within uint.");
  }

  _split_card_shift = CardBitsWithinCardRegion;
  _split_card_mask = ((size_t)1 << _split_card_shift) - 1;

  // Check if the card region/region within cards combination can cover the heap.
  const uint HeapSizeBits = log2i_exact(round_up_power_of_2(reserved.byte_size()));
  if (HeapSizeBits > (BitsInUint + _split_card_shift + G1CardTable::card_shift())) {
    FormatBuffer<> fmt("Can not represent all cards in the heap with card region/card within region. "
                       "Heap %zuB (%u bits) Card set only covers %u bits.",
                       reserved.byte_size(),
                       HeapSizeBits,
                       BitsInUint + _split_card_shift + G1CardTable::card_shift());
    vm_exit_during_initialization(fmt, "Decrease heap size.");
  }
}

uint G1CardSet::container_type_to_mem_object_type(uintptr_t type) const {
  assert(type == G1CardSet::ContainerArrayOfCards ||
         type == G1CardSet::ContainerBitMap ||
         type == G1CardSet::ContainerHowl, "should not allocate container type %zu", type);

  return (uint)type;
}

uint8_t* G1CardSet::allocate_mem_object(uintptr_t type) {
  return _mm->allocate(container_type_to_mem_object_type(type));
}

void G1CardSet::free_mem_object(ContainerPtr container) {
  assert(container != G1CardSet::FreeCardSet, "should not free container FreeCardSet");
  assert(container != G1CardSet::FullCardSet, "should not free container FullCardSet");

  uintptr_t type = container_type(container);
  void* value = strip_container_type(container);

  assert(type == G1CardSet::ContainerArrayOfCards ||
         type == G1CardSet::ContainerBitMap ||
         type == G1CardSet::ContainerHowl, "should not free card set type %zu", type);
  assert(static_cast<G1CardSetContainer*>(value)->refcount() == 1, "must be");

  _mm->free(container_type_to_mem_object_type(type), value);
}

G1CardSet::ContainerPtr G1CardSet::acquire_container(Atomic<ContainerPtr>* container_addr) {
  // Update reference counts under RCU critical section to avoid a
  // use-after-cleapup bug where we increment a reference count for
  // an object whose memory has already been cleaned up and reused.
  GlobalCounter::CriticalSection cs(Thread::current());
  while (true) {
    // Get ContainerPtr and increment refcount atomically wrt to memory reuse.
    ContainerPtr container = container_addr->load_acquire();
    uint cs_type = container_type(container);
    if (container == FullCardSet || cs_type == ContainerInlinePtr) {
      return container;
    }

    G1CardSetContainer* container_on_heap = (G1CardSetContainer*)strip_container_type(container);

    if (container_on_heap->try_increment_refcount()) {
      assert(container_on_heap->refcount() >= 3, "Smallest value is 3");
      return container;
    }
  }
}

bool G1CardSet::release_container(ContainerPtr container) {
  uint cs_type = container_type(container);
  if (container == FullCardSet || cs_type == ContainerInlinePtr) {
    return false;
  }

  G1CardSetContainer* container_on_heap = (G1CardSetContainer*)strip_container_type(container);
  return container_on_heap->decrement_refcount() == 1;
}

void G1CardSet::release_and_maybe_free_container(ContainerPtr container) {
  if (release_container(container)) {
    free_mem_object(container);
  }
}

void G1CardSet::release_and_must_free_container(ContainerPtr container) {
  bool should_free = release_container(container);
  assert(should_free, "should have been the only one having a reference");
  free_mem_object(container);
}

class G1ReleaseCardsets : public StackObj {
  G1CardSet* _card_set;
  using ContainerPtr = G1CardSet::ContainerPtr;

  void coarsen_to_full(Atomic<ContainerPtr>* container_addr) {
    while (true) {
      ContainerPtr cur_container = container_addr->load_acquire();
      uint cs_type = G1CardSet::container_type(cur_container);
      if (cur_container == G1CardSet::FullCardSet) {
        return;
      }

      ContainerPtr old_value = container_addr->compare_exchange(cur_container, G1CardSet::FullCardSet);

      if (old_value == cur_container) {
        _card_set->release_and_maybe_free_container(cur_container);
        return;
      }
    }
  }

public:
  explicit G1ReleaseCardsets(G1CardSet* card_set) : _card_set(card_set) { }

  void operator ()(Atomic<ContainerPtr>* container_addr) {
    coarsen_to_full(container_addr);
  }
};

G1AddCardResult G1CardSet::add_to_array(ContainerPtr container, uint card_in_region) {
  G1CardSetArray* array = container_ptr<G1CardSetArray>(container);
  return array->add(card_in_region);
}

G1AddCardResult G1CardSet::add_to_howl(ContainerPtr parent_container,
                                       uint card_region,
                                       uint card_in_region,
                                       bool increment_total) {
  G1CardSetHowl* howl = container_ptr<G1CardSetHowl>(parent_container);

  G1AddCardResult add_result;
  ContainerPtr to_transfer = nullptr;
  ContainerPtr container;

  uint bucket = _config->howl_bucket_index(card_in_region);
  Atomic<ContainerPtr>* bucket_entry = howl->container_addr(bucket);

  while (true) {
    if (howl->_num_entries.load_relaxed() >= _config->cards_in_howl_threshold()) {
      return Overflow;
    }

    container = acquire_container(bucket_entry);
    add_result = add_to_container(bucket_entry, container, card_region, card_in_region);

    if (add_result != Overflow) {
      break;
    }
    // Card set container has overflown. Coarsen or retry.
    bool coarsened = coarsen_container(bucket_entry, container, card_in_region, true /* within_howl */);
    _coarsen_stats.record_coarsening(container_type(container) + G1CardSetCoarsenStats::CoarsenHowlOffset, !coarsened);
    if (coarsened) {
      // We successful coarsened this card set container (and in the process added the card).
      add_result = Added;
      to_transfer = container;
      break;
    }
    // Somebody else beat us to coarsening. Retry.
    release_and_maybe_free_container(container);
  }

  if (increment_total && add_result == Added) {
    howl->_num_entries.add_then_fetch(1u, memory_order_relaxed);
  }

  if (to_transfer != nullptr) {
    transfer_cards_in_howl(parent_container, to_transfer, card_region);
  }

  release_and_maybe_free_container(container);
  return add_result;
}

G1AddCardResult G1CardSet::add_to_bitmap(ContainerPtr container, uint card_in_region) {
  G1CardSetBitMap* bitmap = container_ptr<G1CardSetBitMap>(container);
  uint card_offset = _config->howl_bitmap_offset(card_in_region);
  return bitmap->add(card_offset, _config->cards_in_howl_bitmap_threshold(), _config->max_cards_in_howl_bitmap());
}

G1AddCardResult G1CardSet::add_to_inline_ptr(Atomic<ContainerPtr>* container_addr, ContainerPtr container, uint card_in_region) {
  G1CardSetInlinePtr value(container_addr, container);
  return value.add(card_in_region, _config->inline_ptr_bits_per_card(), _config->max_cards_in_inline_ptr());
}

G1CardSet::ContainerPtr G1CardSet::create_coarsened_array_of_cards(uint card_in_region, bool within_howl) {
  uint8_t* data = nullptr;
  ContainerPtr new_container;
  if (within_howl) {
    uint const size_in_bits = _config->max_cards_in_howl_bitmap();
    uint container_offset = _config->howl_bitmap_offset(card_in_region);
    data = allocate_mem_object(ContainerBitMap);
    new (data) G1CardSetBitMap(container_offset, size_in_bits);
    new_container = make_container_ptr(data, ContainerBitMap);
  } else {
    data = allocate_mem_object(ContainerHowl);
    new (data) G1CardSetHowl(card_in_region, _config);
    new_container = make_container_ptr(data, ContainerHowl);
  }
  return new_container;
}

bool G1CardSet::coarsen_container(Atomic<ContainerPtr>* container_addr,
                                  ContainerPtr cur_container,
                                  uint card_in_region,
                                  bool within_howl) {
  ContainerPtr new_container = nullptr;

  switch (container_type(cur_container)) {
    case ContainerArrayOfCards: {
      new_container = create_coarsened_array_of_cards(card_in_region, within_howl);
      break;
    }
    case ContainerBitMap: {
      new_container = FullCardSet;
      break;
    }
    case ContainerInlinePtr: {
      uint const size = _config->max_cards_in_array();
      uint8_t* data = allocate_mem_object(ContainerArrayOfCards);
      new (data) G1CardSetArray(card_in_region, size);
      new_container = make_container_ptr(data, ContainerArrayOfCards);
      break;
    }
    case ContainerHowl: {
      new_container = FullCardSet; // anything will do at this point.
      break;
    }
    default:
      ShouldNotReachHere();
  }

  ContainerPtr old_value = container_addr->compare_exchange(cur_container, new_container); // Memory order?
  if (old_value == cur_container) {
    // Success. Indicate that the cards from the current card set must be transferred
    // by this caller.
    // Release the hash table reference to the card. The caller still holds the
    // reference to this card set, so it can never be released (and we do not need to
    // check its result).
    bool should_free = release_container(cur_container);
    assert(!should_free, "must have had more than one reference");
    // Free containers if cur_container is ContainerHowl
    if (container_type(cur_container) == ContainerHowl) {
      G1ReleaseCardsets rel(this);
      container_ptr<G1CardSetHowl>(cur_container)->iterate(rel, _config->num_buckets_in_howl());
    }
    return true;
  } else {
    // Somebody else beat us to coarsening that card set. Exit, but clean up first.
    if (new_container != FullCardSet) {
      assert(new_container != nullptr, "must not be");
      release_and_must_free_container(new_container);
    }
    return false;
  }
}

class G1TransferCard : public StackObj {
  G1CardSet* _card_set;
  uint _region_idx;
public:
  G1TransferCard(G1CardSet* card_set, uint region_idx) : _card_set(card_set), _region_idx(region_idx) { }

  void operator ()(uint card_idx) {
    _card_set->add_card(_region_idx, card_idx, false);
  }
};

void G1CardSet::transfer_cards(G1CardSetHashTableValue* table_entry, ContainerPtr source_container, uint card_region) {
  assert(source_container != FullCardSet, "Should not need to transfer from FullCardSet");
  // Need to transfer old entries unless there is a Full card set container in place now, i.e.
  // the old type has been ContainerBitMap. "Full" contains all elements anyway.
  if (container_type(source_container) != ContainerHowl) {
    G1TransferCard iter(this, card_region);
    iterate_cards_during_transfer(source_container, iter);
  } else {
    assert(container_type(source_container) == ContainerHowl, "must be");
    // Need to correct for that the Full remembered set occupies more cards than the
    // AoCS before.
    _num_occupied.add_then_fetch(_config->max_cards_in_region() - table_entry->_num_occupied.load_relaxed(), memory_order_relaxed);
  }
}

void G1CardSet::transfer_cards_in_howl(ContainerPtr parent_container,
                                       ContainerPtr source_container,
                                       uint card_region) {
  assert(container_type(parent_container) == ContainerHowl, "must be");
  assert(source_container != FullCardSet, "Should not need to transfer from full");
  // Need to transfer old entries unless there is a Full card set in place now, i.e.
  // the old type has been ContainerBitMap.
  if (container_type(source_container) != ContainerBitMap) {
    // We only need to transfer from anything below ContainerBitMap.
    G1TransferCard iter(this, card_region);
    iterate_cards_during_transfer(source_container, iter);
  } else {
    uint diff = _config->max_cards_in_howl_bitmap() - container_ptr<G1CardSetBitMap>(source_container)->num_bits_set();

    // Need to correct for that the Full remembered set occupies more cards than the
    // bitmap before.
    // We add 1 card less because the values will be incremented
    // in G1CardSet::add_card for the current addition or where already incremented in
    // G1CardSet::add_to_howl after coarsening.
    diff -= 1;

    G1CardSetHowl* howling_array = container_ptr<G1CardSetHowl>(parent_container);
    howling_array->_num_entries.add_then_fetch(diff, memory_order_relaxed);

    G1CardSetHashTableValue* table_entry = get_container(card_region);
    assert(table_entry != nullptr, "Table entry not found for transferred cards");

    table_entry->_num_occupied.add_then_fetch(diff, memory_order_relaxed);

    _num_occupied.add_then_fetch(diff, memory_order_relaxed);
  }
}

G1AddCardResult G1CardSet::add_to_container(Atomic<ContainerPtr>* container_addr,
                                            ContainerPtr container,
                                            uint card_region,
                                            uint card_in_region,
                                            bool increment_total) {
  assert(container_addr != nullptr, "must be");

  G1AddCardResult add_result;

  switch (container_type(container)) {
    case ContainerInlinePtr: {
      add_result = add_to_inline_ptr(container_addr, container, card_in_region);
      break;
    }
    case ContainerArrayOfCards: {
      add_result = add_to_array(container, card_in_region);
      break;
    }
    case ContainerBitMap: {
      add_result = add_to_bitmap(container, card_in_region);
      break;
    }
    case ContainerHowl: {
      assert(ContainerHowl == container_type(FullCardSet), "must be");
      if (container == FullCardSet) {
        return Found;
      }
      add_result = add_to_howl(container, card_region, card_in_region, increment_total);
      break;
    }
    default:
      ShouldNotReachHere();
  }
  return add_result;
}

G1CardSetHashTableValue* G1CardSet::get_or_add_container(uint card_region, bool* should_grow_table) {
  return _table->get_or_add(card_region, should_grow_table);
}

G1CardSetHashTableValue* G1CardSet::get_container(uint card_region) {
  return _table->get(card_region);
}

void G1CardSet::split_card(uintptr_t card, uint& card_region, uint& card_within_region) const {
  card_region = (uint)(card >> _split_card_shift);
  card_within_region = (uint)(card & _split_card_mask);
  assert(card_within_region < _config->max_cards_in_region(), "must be");
}

G1AddCardResult G1CardSet::add_card(uintptr_t card) {
  uint card_region;
  uint card_within_region;
  split_card(card, card_region, card_within_region);

#ifdef ASSERT
  {
    uint region_idx = card_region >> config()->log2_card_regions_per_heap_region();
    G1HeapRegion* r = G1CollectedHeap::heap()->region_at(region_idx);
    assert(!r->rem_set()->has_cset_group() ||
           r->rem_set()->cset_group()->card_set() != this, "Should not be sharing a cardset");
  }
#endif

  return add_card(card_region, card_within_region, true /* increment_total */);
}

bool G1CardSet::contains_card(uintptr_t card) {
  uint card_region;
  uint card_within_region;
  split_card(card, card_region, card_within_region);

  return contains_card(card_region, card_within_region);
}

G1AddCardResult G1CardSet::add_card(uint card_region, uint card_in_region, bool increment_total) {
  G1AddCardResult add_result;
  ContainerPtr to_transfer = nullptr;
  ContainerPtr container;

  bool should_grow_table = false;
  G1CardSetHashTableValue* table_entry = get_or_add_container(card_region, &should_grow_table);
  while (true) {
    container = acquire_container(&table_entry->_container);
    add_result = add_to_container(&table_entry->_container, container, card_region, card_in_region, increment_total);

    if (add_result != Overflow) {
      break;
    }
    // Card set has overflown. Coarsen or retry.
    bool coarsened = coarsen_container(&table_entry->_container, container, card_in_region);
    _coarsen_stats.record_coarsening(container_type(container), !coarsened);
    if (coarsened) {
      // We successful coarsened this card set container (and in the process added the card).
      add_result = Added;
      to_transfer = container;
      break;
    }
    // Somebody else beat us to coarsening. Retry.
    release_and_maybe_free_container(container);
  }

  if (increment_total && add_result == Added) {
    table_entry->_num_occupied.add_then_fetch(1u, memory_order_relaxed);
    _num_occupied.add_then_fetch(1u, memory_order_relaxed);
  }
  if (should_grow_table) {
    _table->grow();
  }
  if (to_transfer != nullptr) {
    transfer_cards(table_entry, to_transfer, card_region);
  }

  release_and_maybe_free_container(container);

  return add_result;
}

bool G1CardSet::contains_card(uint card_region, uint card_in_region) {
  assert(card_in_region < _config->max_cards_in_region(),
         "Card %u is beyond max %u", card_in_region, _config->max_cards_in_region());

  // Protect the card set container from reclamation.
  GlobalCounter::CriticalSection cs(Thread::current());
  G1CardSetHashTableValue* table_entry = get_container(card_region);
  if (table_entry == nullptr) {
    return false;
  }

  ContainerPtr container = table_entry->_container.load_relaxed();
  if (container == FullCardSet) {
    // contains_card() is not a performance critical method so we do not hide that
    // case in the switch below.
    return true;
  }

  switch (container_type(container)) {
    case ContainerInlinePtr: {
      G1CardSetInlinePtr ptr(container);
      return ptr.contains(card_in_region, _config->inline_ptr_bits_per_card());
    }
    case ContainerArrayOfCards: return container_ptr<G1CardSetArray>(container)->contains(card_in_region);
    case ContainerBitMap: return container_ptr<G1CardSetBitMap>(container)->contains(card_in_region, _config->max_cards_in_howl_bitmap());
    case ContainerHowl: {
      G1CardSetHowl* howling_array = container_ptr<G1CardSetHowl>(container);

      return howling_array->contains(card_in_region, _config);
    }
  }
  ShouldNotReachHere();
  return false;
}

void G1CardSet::print_info(outputStream* st, uintptr_t card) {
  uint card_region;
  uint card_in_region;

  split_card(card, card_region, card_in_region);

  G1CardSetHashTableValue* table_entry = get_container(card_region);
  if (table_entry == nullptr) {
    st->print("null card set");
    return;
  }

  ContainerPtr container = table_entry->_container.load_relaxed();
  if (container == FullCardSet) {
    st->print("FULL card set)");
    return;
  }
  switch (container_type(container)) {
    case ContainerInlinePtr: {
      st->print("InlinePtr not containing %u", card_in_region);
      break;
    }
    case ContainerArrayOfCards: {
      st->print("AoC not containing %u", card_in_region);
      break;
    }
    case ContainerBitMap: {
      st->print("BitMap not containing %u", card_in_region);
      break;
    }
    case ContainerHowl: {
      st->print("ContainerHowl not containing %u", card_in_region);
      break;
    }
    default: st->print("Unknown card set container type %u", container_type(container)); ShouldNotReachHere(); break;
  }
}

template <class CardVisitor>
void G1CardSet::iterate_cards_during_transfer(ContainerPtr const container, CardVisitor& cl) {
  uint type = container_type(container);
  assert(type == ContainerInlinePtr || type == ContainerArrayOfCards,
         "invalid card set type %d to transfer from",
         container_type(container));

  switch (type) {
    case ContainerInlinePtr: {
      G1CardSetInlinePtr ptr(container);
      ptr.iterate(cl, _config->inline_ptr_bits_per_card());
      return;
    }
    case ContainerArrayOfCards: {
      container_ptr<G1CardSetArray>(container)->iterate(cl);
      return;
    }
    default:
      ShouldNotReachHere();
  }
}

void G1CardSet::iterate_containers(ContainerPtrClosure* cl, bool at_safepoint) {
  auto do_value =
    [&] (G1CardSetHashTableValue* value) {
      cl->do_containerptr(value->_region_idx, value->_num_occupied.load_relaxed(), value->_container.load_relaxed());
      return true;
    };

  if (at_safepoint) {
    _table->iterate_safepoint(do_value);
  } else {
    _table->iterate(do_value);
  }
}

// Applied to all card (ranges) of the containers.
template <typename Closure>
class G1ContainerCardsClosure {
  Closure& _cl;
  uint _region_idx;

public:
  G1ContainerCardsClosure(Closure& cl, uint region_idx) : _cl(cl), _region_idx(region_idx) { }

  bool start_iterate(uint tag) { return true; }

  void operator()(uint card_idx) {
    _cl.do_card(_region_idx, card_idx);
  }

  void operator()(uint card_idx, uint length) {
    for (uint i = 0; i < length; i++) {
      _cl.do_card(_region_idx, card_idx);
    }
  }
};

template <typename Closure, template <typename> class CardOrRanges>
class G1CardSetContainersClosure : public G1CardSet::ContainerPtrClosure {
  G1CardSet* _card_set;
  Closure& _cl;

public:

  G1CardSetContainersClosure(G1CardSet* card_set,
                             Closure& cl) :
    _card_set(card_set),
    _cl(cl) { }

  void do_containerptr(uint region_idx, size_t num_occupied, G1CardSet::ContainerPtr container) override {
    CardOrRanges<Closure> cl(_cl, region_idx);
    _card_set->iterate_cards_or_ranges_in_container(container, cl);
  }
};

void G1CardSet::iterate_cards(CardClosure& cl) {
  G1CardSetContainersClosure<CardClosure, G1ContainerCardsClosure> cl2(this, cl);
  iterate_containers(&cl2);
}

bool G1CardSet::occupancy_less_or_equal_to(size_t limit) const {
  return occupied() <= limit;
}

bool G1CardSet::is_empty() const {
  return _num_occupied.load_relaxed() == 0;
}

size_t G1CardSet::occupied() const {
  return _num_occupied.load_relaxed();
}

size_t G1CardSet::num_containers() {
  class GetNumberOfContainers : public ContainerPtrClosure {
  public:
    size_t _count;

    GetNumberOfContainers() : ContainerPtrClosure(), _count(0) { }

    void do_containerptr(uint region_idx, size_t num_occupied, ContainerPtr container) override {
      _count++;
    }
  } cl;

  iterate_containers(&cl);
  return cl._count;
}

void G1CardSet::print_coarsen_stats(outputStream* out) {
  _last_coarsen_stats.subtract_from(_coarsen_stats);

  out->print("Coarsening (recent): ");
  _last_coarsen_stats.print_on(out);
  out->print("Coarsening (all): ");
  _coarsen_stats.print_on(out);

  _last_coarsen_stats.set(_coarsen_stats);
}

size_t G1CardSet::mem_size() const {
  return sizeof(*this) +
         _table->mem_size() +
         _mm->mem_size();
}

size_t G1CardSet::unused_mem_size() const {
  return _mm->unused_mem_size();
}

size_t G1CardSet::static_mem_size() {
  return sizeof(FullCardSet) + sizeof(_coarsen_stats);
}

void G1CardSet::clear() {
  _table->reset();
  _num_occupied.store_relaxed(0);
  _mm->flush();
}

void G1CardSet::reset_table_scanner() {
  _table->reset_table_scanner();
}

void G1CardSet::reset_table_scanner_for_groups() {
  _table->reset_table_scanner_for_groups();
}
