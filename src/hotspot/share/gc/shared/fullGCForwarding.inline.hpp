/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP
#define SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP

#include "gc/shared/fullGCForwarding.hpp"
#include "logging/log.hpp"
#include "nmt/memTag.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/fastHash.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"

// We cannot use 0, because that may already be a valid base address in zero-based heaps.
// 0x1 is safe because heap base addresses must be aligned by much larger alignment
template <int BITS>
HeapWord* const FullGCForwardingImpl<BITS>::UNUSED_BASE = reinterpret_cast<HeapWord*>(0x1);

template <int BITS>
HeapWord* FullGCForwardingImpl<BITS>::_heap_start = nullptr;
template <int BITS>
size_t FullGCForwardingImpl<BITS>::_heap_start_region_bias = 0;
template <int BITS>
size_t FullGCForwardingImpl<BITS>::_num_regions = 0;
template <int BITS>
uintptr_t FullGCForwardingImpl<BITS>::_region_mask = 0;
template <int BITS>
HeapWord** FullGCForwardingImpl<BITS>::_biased_bases = nullptr;
template <int BITS>
HeapWord** FullGCForwardingImpl<BITS>::_bases_table = nullptr;
template <int BITS>
size_t FullGCForwardingImpl<BITS>::_fallback_table_log2_start_size = 9; // 512 entries.
template <int BITS>
FallbackTable* FullGCForwardingImpl<BITS>::_fallback_table = nullptr;
#ifndef PRODUCT
template <int BITS>
volatile uint64_t FullGCForwardingImpl<BITS>::_num_forwardings = 0;
template <int BITS>
volatile uint64_t FullGCForwardingImpl<BITS>::_num_fallback_forwardings = 0;
#endif

template <int BITS>
bool FullGCForwardingImpl<BITS>::is_forwarded(oop obj) {
  return obj->is_forwarded();
}

template <int BITS>
size_t FullGCForwardingImpl<BITS>::biased_region_index_containing(HeapWord* addr) {
  return reinterpret_cast<uintptr_t>(addr) >> BLOCK_SIZE_BYTES_SHIFT;
}

template <int BITS>
bool FullGCForwardingImpl<BITS>::is_fallback(uintptr_t encoded) {
  return (encoded & OFFSET_MASK) == FALLBACK_PATTERN_IN_PLACE;
}

template <int BITS>
uintptr_t FullGCForwardingImpl<BITS>::encode_forwarding(HeapWord* from, HeapWord* to) {
  size_t from_block_idx = biased_region_index_containing(from);

  HeapWord* to_region_base = _biased_bases[from_block_idx];
  if (to_region_base == UNUSED_BASE) {
    HeapWord* prev = Atomic::cmpxchg(&_biased_bases[from_block_idx], UNUSED_BASE, to);
    if (prev == UNUSED_BASE) {
      to_region_base = to;
    } else {
      to_region_base = prev;
    }
    // _biased_bases[from_block_idx] = to_region_base = to;
  }
  // Avoid pointer_delta() on purpose: using an unsigned subtraction,
  // we get an underflow when to < to_region_base, which means
  // we can use a single comparison instead of:
  // if (to_region_base > to || (to - to_region_base) > MAX_OFFSET) { .. }
  size_t offset = static_cast<size_t>(to - to_region_base);
  if (offset > MAX_OFFSET) {
    offset = FALLBACK_PATTERN;
  }
  uintptr_t encoded = (offset << OFFSET_BITS_SHIFT) | markWord::marked_value;

  assert(is_fallback(encoded) || to == decode_forwarding(from, encoded), "must be reversible: " PTR_FORMAT " -> " PTR_FORMAT ", reversed: " PTR_FORMAT ", encoded: " INTPTR_FORMAT ", to_region_base: " PTR_FORMAT ", from_block_idx: %lu", p2i(from), p2i(to), p2i(decode_forwarding(from, encoded)), encoded, p2i(to_region_base), from_block_idx);
  assert((encoded & ~AVAILABLE_BITS_MASK) == 0, "must encode to available bits");
  return encoded;
}

template <int BITS>
HeapWord* FullGCForwardingImpl<BITS>::decode_forwarding(HeapWord* from, uintptr_t encoded) {
  assert(!is_fallback(encoded), "must not be fallback-forwarded, encoded: " INTPTR_FORMAT ", OFFSET_MASK: " INTPTR_FORMAT ", FALLBACK_PATTERN_IN_PLACE: " INTPTR_FORMAT, encoded, OFFSET_MASK, FALLBACK_PATTERN_IN_PLACE);
  assert((encoded & ~AVAILABLE_BITS_MASK) == 0, "must decode from available bits, encoded: " INTPTR_FORMAT, encoded);
  uintptr_t offset = (encoded >> OFFSET_BITS_SHIFT);

  size_t from_idx = biased_region_index_containing(from);
  HeapWord* base = _biased_bases[from_idx];
  assert(base != UNUSED_BASE, "must not be unused base: encoded: " INTPTR_FORMAT, encoded);
  HeapWord* decoded = base + offset;
  assert(decoded >= _heap_start,
         "Address must be above heap start. encoded: " INTPTR_FORMAT ", base: " PTR_FORMAT,
          encoded, p2i(base));

  return decoded;
}

template <int BITS>
void FullGCForwardingImpl<BITS>::forward_to_impl(oop from, oop to) {
  assert(_bases_table != nullptr, "call begin() before forwarding");

  markWord from_header = from->mark();
  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  HeapWord* to_hw   = cast_from_oop<HeapWord*>(to);
  uintptr_t encoded = encode_forwarding(from_hw, to_hw);
  markWord new_header = markWord((from_header.value() & ~OFFSET_MASK) | encoded);
  from->set_mark(new_header);

  if (is_fallback(encoded)) {
    fallback_forward_to(from_hw, to_hw);
  }
  NOT_PRODUCT(Atomic::inc(&_num_forwardings);)
}

template <int BITS>
void FullGCForwardingImpl<BITS>::forward_to(oop obj, oop fwd) {
  assert(fwd != nullptr, "no null forwarding");
#ifdef _LP64
  assert(_bases_table != nullptr, "expect sliding forwarding initialized");
  forward_to_impl(obj, fwd);
  // assert(forwardee(obj) == fwd, "must be forwarded to correct forwardee, obj: " PTR_FORMAT ", forwardee(obj): " PTR_FORMAT ", fwd: " PTR_FORMAT ", mark: " INTPTR_FORMAT ", num-regions: %lu, base: " PTR_FORMAT ", OFFSET_MASK: " INTPTR_FORMAT ", encoded: " PTR_FORMAT ", biased-base: " PTR_FORMAT ", heap-start: " PTR_FORMAT, p2i(obj), p2i(forwardee(obj)), p2i(fwd), obj->mark().value(), _num_regions, p2i(_bases_table[0]), OFFSET_MASK, encode_forwarding(cast_from_oop<HeapWord*>(obj), cast_from_oop<HeapWord*>(fwd)), p2i(_biased_bases[biased_region_index_containing(cast_from_oop<HeapWord*>(obj))]), p2i(_heap_start));
#else
  obj->forward_to(fwd);
#endif
}

template <int BITS>
oop FullGCForwardingImpl<BITS>::forwardee_impl(oop from) {
  assert(_bases_table != nullptr, "call begin() before asking for forwarding");

  markWord header = from->mark();
  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  if (is_fallback(header.value())) {
    HeapWord* to = fallback_forwardee(from_hw);
    return cast_to_oop(to);
  }
  uintptr_t encoded = header.value() & OFFSET_MASK;
  HeapWord* to = decode_forwarding(from_hw, encoded);
  return cast_to_oop(to);
}

template <int BITS>
oop FullGCForwardingImpl<BITS>::forwardee(oop obj) {
#ifdef _LP64
  assert(_bases_table != nullptr, "expect sliding forwarding initialized");
  return forwardee_impl(obj);
#else
  return obj->forwardee();
#endif
}

static uintx hash(HeapWord* const& addr) {
  uint64_t val = reinterpret_cast<uint64_t>(addr);
  uint32_t hash = FastHash::get_hash32(static_cast<uint32_t>(val), static_cast<uint32_t>(val >> 32));
  return hash;
}

struct ForwardingEntry {
  HeapWord* _from;
  HeapWord* _to;
  ForwardingEntry(HeapWord* from, HeapWord* to) : _from(from), _to(to) {}
};

struct FallbackTableConfig {
  using Value = ForwardingEntry;
  static uintx get_hash(Value const& entry, bool* is_dead) {
    return hash(entry._from);
  }
  static void* allocate_node(void* context, size_t size, Value const& value) {
    return AllocateHeap(size, mtGC);
  }
  static void free_node(void* context, void* memory, Value const& value) {
    FreeHeap(memory);
  }
};

class FallbackTable : public ConcurrentHashTable<FallbackTableConfig, mtGC> {
public:
  explicit FallbackTable(size_t log2size) : ConcurrentHashTable(log2size) {}
};

class FallbackTableLookup : public StackObj {
  ForwardingEntry const _entry;
public:
  explicit FallbackTableLookup(HeapWord* from) : _entry(from, nullptr) {}
  uintx get_hash() const {
    return hash(_entry._from);
  }
  bool equals(const ForwardingEntry* value) const {
    return _entry._from == value->_from;
  }
  static bool is_dead(ForwardingEntry* value) { return false; }
};

template <int BITS>
void FullGCForwardingImpl<BITS>::initialize(MemRegion heap) {
#ifdef _LP64
  _heap_start = heap.start();

  size_t rounded_heap_size = MAX2(round_up_power_of_2(heap.byte_size()) / BytesPerWord, BLOCK_SIZE_WORDS);

  _num_regions = rounded_heap_size / BLOCK_SIZE_WORDS;

  _heap_start_region_bias = reinterpret_cast<uintptr_t>(_heap_start) >> BLOCK_SIZE_BYTES_SHIFT;
  _region_mask = ~((static_cast<uintptr_t>(1) << BLOCK_SIZE_BYTES_SHIFT) - 1);

  assert(_bases_table == nullptr, "should not be initialized yet");
  assert(_fallback_table == nullptr, "should not be initialized yet");
#endif
}

template <int BITS>
void FullGCForwardingImpl<BITS>::begin() {
#ifdef _LP64
  assert(_bases_table == nullptr, "should not be initialized yet");
  assert(_fallback_table == nullptr, "should not be initialized yet");

  _fallback_table = nullptr;

#ifndef PRODUCT
  _num_forwardings = 0;
  _num_fallback_forwardings = 0;
#endif

  size_t max = _num_regions;
  _bases_table = NEW_C_HEAP_ARRAY(HeapWord*, max, mtGC);
  HeapWord** biased_start = _bases_table - _heap_start_region_bias;
  _biased_bases = biased_start;
  if (max == 1) {
    // Optimize the case when the block-size >= heap-size.
    // In this case we can use the heap-start as block-start,
    // and don't risk that competing GC threads set a higher
    // address as block-start, which would lead to unnecessary
    // fallback-usage.
    _bases_table[0] = _heap_start;
  } else {
    for (size_t i = 0; i < max; i++) {
      _bases_table[i] = UNUSED_BASE;
    }
  }
#endif
}

template <int BITS>
void FullGCForwardingImpl<BITS>::end() {
#ifndef PRODUCT
  size_t fallback_table_size = _fallback_table != nullptr ? _fallback_table->get_mem_size(Thread::current()) : 0;
  log_info(gc)("Total forwardings: " UINT64_FORMAT ", fallback forwardings: " UINT64_FORMAT
                ", ratio: %f, memory used by fallback table: %zu%s, memory used by bases table: %zu%s",
               _num_forwardings, _num_fallback_forwardings, static_cast<float>(_num_forwardings) / static_cast<float>(_num_fallback_forwardings),
               byte_size_in_proper_unit(fallback_table_size),
               proper_unit_for_byte_size(fallback_table_size),
               byte_size_in_proper_unit(sizeof(HeapWord*) * _num_regions),
               proper_unit_for_byte_size(sizeof(HeapWord*) * _num_regions));
#endif
#ifdef _LP64
  assert(_bases_table != nullptr, "should be initialized");
  FREE_C_HEAP_ARRAY(HeapWord*, _bases_table);
  _bases_table = nullptr;
  if (_fallback_table != nullptr) {
    delete _fallback_table;
    _fallback_table = nullptr;
  }
#endif
}

template <int BITS>
void FullGCForwardingImpl<BITS>::maybe_init_fallback_table() {
  if (_fallback_table == nullptr) {
    FallbackTable* fallback_table = new FallbackTable(_fallback_table_log2_start_size);
    FallbackTable* prev = Atomic::cmpxchg(&_fallback_table, static_cast<FallbackTable*>(nullptr), fallback_table);
    if (prev != nullptr) {
      // Another thread won, discard our table.
      delete fallback_table;
    }
  }
}

template <int BITS>
void FullGCForwardingImpl<BITS>::fallback_forward_to(HeapWord* from, HeapWord* to) {
  assert(to != nullptr, "no null forwarding");
  maybe_init_fallback_table();
  assert(_fallback_table != nullptr, "should be initialized");
  FallbackTableLookup lookup_f(from);
  ForwardingEntry entry(from, to);
  auto found_f = [&](ForwardingEntry* found) {
    // If dupe has been found, override it with new value.
    // This is also called when new entry is succussfully inserted.
    if (found->_to != to) {
      found->_to = to;
    }
  };
  Thread* current_thread = Thread::current();
  bool grow;
  bool added = _fallback_table->insert_get(current_thread, lookup_f, entry, found_f, &grow);
  NOT_PRODUCT(Atomic::inc(&_num_fallback_forwardings);)
#ifdef ASSERT
  assert(fallback_forwardee(from) != nullptr, "must have entered forwarding");
  assert(fallback_forwardee(from) == to, "forwarding must be correct, added: %s, from: " PTR_FORMAT ", to: " PTR_FORMAT ", fwd: " PTR_FORMAT, BOOL_TO_STR(added), p2i(from), p2i(to), p2i(fallback_forwardee(from)));
#endif
  if (grow) {
    _fallback_table->grow(current_thread);
    log_debug(gc)("grow fallback table to size: %zu bytes", _fallback_table->get_mem_size(current_thread));
  }
}

template <int BITS>
HeapWord* FullGCForwardingImpl<BITS>::fallback_forwardee(HeapWord* from) {
  assert(_fallback_table != nullptr, "fallback table must be present");
  HeapWord* result;
  FallbackTableLookup lookup_f(from);
  auto found_f = [&](const ForwardingEntry* found) {
    result = found->_to;
  };
  bool found = _fallback_table->get(Thread::current(), lookup_f, found_f);
  assert(found, "something must have been found");
  assert(result != nullptr, "must have found forwarding");
  return result;
}

#endif // SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP
