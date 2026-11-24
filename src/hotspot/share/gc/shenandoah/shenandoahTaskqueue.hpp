/*
 * Copyright (c) 2016, 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP

#include "gc/shenandoah/shenandoahPadding.hpp"
#include "memory/reservedSpace.hpp"
#include "nmt/memTag.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

class ShenandoahTaskQueueSet;
class ShenandoahHeap;

// ShenandoahTask
//
// Encodes both regular oops, and the array oops plus chunking data for parallel array processing.
// The design goal is to make the regular oop ops very fast, because that would be the prevailing
// case. On the other hand, it should not block parallel array processing from efficiently dividing
// the array work.
//
// The idea is to steal the bits from the 64-bit oop to encode array data, if needed. For the
// proper divide-and-conquer strategies, we want to encode the "blocking" data. It turns out, the
// most efficient way to do this is to encode the array block as (chunk * 2^pow), where it is assumed
// that the block has the size of 2^pow. This requires for pow to have only 5 bits (2^32) to encode
// all possible arrays.
//
//    |xx-------oop---------|-pow-|--chunk---|
//    0                    49     54        64
//
// By definition, chunk == 0 means "no chunk", i.e. chunking starts from 1.
//
// Lower bits of oop are reserved to handle "skip_live" and "strong" properties. Since this encoding
// stores uncompressed oops, those bits are always available. These bits default to zero for "skip_live"
// and "weak". This aligns with their frequent values: strong/counted-live references.
//
// This encoding gives a few interesting benefits:
//
// a) Encoding/decoding regular oops is very simple, because the upper bits are zero in that task:
//
//    |---------oop---------|00000|0000000000| // no chunk data
//
//    This helps the most ubiquitous path. The initialization amounts to putting the oop into the word
//    with zero padding. Testing for "chunkedness" is testing for zero with chunk mask.
//
// b) Splitting tasks for divide-and-conquer is possible. Suppose we have chunk <C, P> that covers
// interval [ (C-1)*2^P; C*2^P ). We can then split it into two chunks:
//      <2*C - 1, P-1>, that covers interval [ (2*C - 2)*2^(P-1); (2*C - 1)*2^(P-1) )
//      <2*C, P-1>,     that covers interval [ (2*C - 1)*2^(P-1);       2*C*2^(P-1) )
//
//    Observe that the union of these two intervals is:
//      [ (2*C - 2)*2^(P-1); 2*C*2^(P-1) )
//
//    ...which is the original interval:
//      [ (C-1)*2^P; C*2^P )
//
// c) The divide-and-conquer strategy could even start with chunk <1, round-log2-len(arr)>, and split
//    down in the parallel threads, which alleviates the upfront (serial) splitting costs.
//
// Encoding limitations caused by current bitscales mean:
//    10 bits for chunk: max 1024 blocks per array
//     5 bits for power: max 2^32 array
//    49 bits for   oop: max 512 TB of addressable space
//
// Stealing bits from oop trims down the addressable space. Stealing too few bits for chunk ID limits
// potential parallelism. Stealing too few bits for pow limits the maximum array size that can be handled.
// In future, these might be rebalanced to favor one degree of freedom against another. For example,
// if/when Arrays 2.0 bring 2^64-sized arrays, we might need to steal another bit for power. We could regain
// some bits back if chunks are counted in ObjArrayMarkingStride units.

#ifdef _MSC_VER
#pragma warning(push)
// warning C4522: multiple assignment operators specified
#pragma warning( disable:4522 )
#endif

class ShenandoahTask
{
private:
  // Everything is encoded into this field...
  uint64_t _obj;

  // ...with these:
  static const uint8_t chunk_bits  = 10;
  static const uint8_t pow_bits    = 5;
  static const uint8_t oop_bits    = sizeof(uintptr_t)*8 LP64_ONLY(- chunk_bits - pow_bits);

  static const uint8_t oop_shift   = 0;
  static const uint8_t pow_shift   = oop_bits;
  static const uint8_t chunk_shift = oop_bits + pow_bits;

  static const uint64_t oop_extract_mask       = right_n_bits(oop_bits) - 3;
  static const uint64_t skip_live_extract_mask = 1 << 0;
  static const uint64_t weak_extract_mask      = 1 << 1;
  static const uint64_t chunk_pow_extract_mask = ~right_n_bits(oop_bits);

  static const int chunk_range_mask = right_n_bits<int>(chunk_bits);
  static const int pow_range_mask   = right_n_bits<int>(pow_bits);

  inline oop decode_oop(uint64_t val) const {
    static_assert(oop_shift == 0);
    return cast_to_oop(val & oop_extract_mask);
  }

  inline bool decode_not_chunked(uint64_t val) const {
    // No need to shift for a comparison to zero
    return (val & chunk_pow_extract_mask) == 0;
  }

  inline int decode_chunk(uint64_t val) const {
    return (int) ((val >> chunk_shift) & chunk_range_mask);
  }

  inline int decode_pow(uint64_t val) const {
    return (int) ((val >> pow_shift) & pow_range_mask);
  }

  inline bool decode_weak(uint64_t val) const {
    return (val & weak_extract_mask) != 0;
  }

  inline bool decode_cnt_live(uint64_t val) const {
    return (val & skip_live_extract_mask) == 0;
  }

  inline uint64_t encode_oop(oop obj, bool skip_live, bool weak) const {
    static_assert(oop_shift == 0);
    uint64_t encoded = cast_from_oop<uint64_t>(obj);
    if (skip_live) {
      encoded |= skip_live_extract_mask;
    }
    if (weak) {
      encoded |= weak_extract_mask;
    }
    return encoded;
  }

  inline uint64_t encode_chunk(int chunk) const {
    return ((uint64_t) chunk) << chunk_shift;
  }

  inline uint64_t encode_pow(int pow) const {
    return ((uint64_t) pow) << pow_shift;
  }

public:
  ShenandoahTask(oop o = nullptr, bool skip_live = false, bool weak = false) {
    uint64_t enc = encode_oop(o, skip_live, weak);
    assert(decode_oop(enc) == o,     "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(decode_cnt_live(enc) == !skip_live, "skip_live encoding should work");
    assert(decode_weak(enc) == weak, "weak encoding should work");
    assert(decode_not_chunked(enc),  "task should not be chunked");
    _obj = enc;
  }

  ShenandoahTask(oop o, bool skip_live, bool weak, int chunk, int pow) {
    uint64_t enc_oop = encode_oop(o, skip_live, weak);
    uint64_t enc_chunk = encode_chunk(chunk);
    uint64_t enc_pow = encode_pow(pow);
    uint64_t enc = enc_oop | enc_chunk | enc_pow;
    assert(decode_oop(enc) == o,       "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(decode_cnt_live(enc) == !skip_live, "skip_live should be true for chunked tasks");
    assert(decode_weak(enc) == weak,   "weak encoding should work");
    assert(decode_chunk(enc) == chunk, "chunk encoding should work: %d", chunk);
    assert(decode_pow(enc) == pow,     "pow encoding should work: %d", pow);
    assert(!decode_not_chunked(enc),   "task should be chunked");
    _obj = enc;
  }

  // Trivially copyable.

public:
  inline oop  obj()            const { return decode_oop(_obj);   }
  inline int  chunk()          const { return decode_chunk(_obj); }
  inline int  pow()            const { return decode_pow(_obj);   }

  inline bool is_not_chunked() const { return decode_not_chunked(_obj); }
  inline bool is_weak()        const { return decode_weak(_obj);        }
  inline bool count_liveness() const { return decode_cnt_live(_obj);    }

  DEBUG_ONLY(bool is_valid() const;) // Tasks to be pushed/popped must be valid.

  static uintptr_t max_addressable() {
    return LP64_ONLY(nth_bit(oop_bits)) NOT_LP64(UINTPTR_MAX);
  }

  static int chunk_size() {
    return nth_bit<int>(chunk_bits);
  }
};

#ifdef _MSC_VER
#pragma warning(pop)
#endif

class ShenandoahTaskQueueSegment;

// Stack is the unit of load balancing.
//
// Each stack can be:
//   used:   currently in use by a single thread
//   shared: not in use by any thread, has tasks to share
//   free:   not in use by any thread, has no tasks
//
class ShenandoahTaskQueueStack {
public:
  static const int MAX_SIZE = (4096 - sizeof(size_t) * 2 - sizeof(ShenandoahTaskQueueSegment*)) / sizeof(ShenandoahTask);
  static const int MIN_SIZE = 1;

private:
  ShenandoahTaskQueueSegment* _segment;
  size_t _index;
  size_t _top;
  ShenandoahTask _tasks[MAX_SIZE];

public:
  ShenandoahTaskQueueStack() : _segment(nullptr), _index(SIZE_MAX), _top(SIZE_MAX) {}
  ShenandoahTaskQueueStack(ShenandoahTaskQueueSegment* segment, size_t index) : _segment(segment), _index(index), _top(0) {}

  ALWAYSINLINE
  bool push(ShenandoahTask t);

  ALWAYSINLINE
  bool pop(ShenandoahTask& t);

  bool is_empty();
  void clear();

  ShenandoahTaskQueueSegment* segment() { return _segment; }
  size_t index() { return _index; }

  bool is_sliceable();
  void slice_top(ShenandoahTaskQueueStack* to_stack);
};

// Segment is the unit of allocation.
//
// Each segment is committed in full, carries the storage for stacks, plus small bitmaps
// that describe the stack state. Segment is aligned by its size, which prevents false sharing
// between adjacent segments and any stacks within the segment or outside the segment.
//
class ShenandoahTaskQueueSegment {
public:
  static const int SEGMENT_SIZE_SHIFT = 6;
  static const int SEGMENT_SIZE = 1 << SEGMENT_SIZE_SHIFT;
  static_assert(SEGMENT_SIZE <= 64, "Bitmap encoding");

  ShenandoahTaskQueueStack _stacks[SEGMENT_SIZE];

  shenandoah_padding(0);
  Atomic<uint64_t> _free_bm;
  shenandoah_padding(1);
  Atomic<uint64_t> _shared_bm;
  shenandoah_padding(2);
  ShenandoahTaskQueueSegment* _next;

  ReservedSpace _rs;

  static ShenandoahTaskQueueSegment* create();
  static void destroy(ShenandoahTaskQueueSegment* seg);

  inline ShenandoahTaskQueueStack* pick(Atomic<uint64_t>* bm);
  inline ShenandoahTaskQueueStack* pick_shared();
  inline ShenandoahTaskQueueStack* pick_free();

  inline void mark(ShenandoahTaskQueueStack* stack, Atomic<uint64_t>* bm);
  inline void mark_shared(ShenandoahTaskQueueStack* stack);
  inline void mark_free(ShenandoahTaskQueueStack* stack);

  bool all_are_free() const;

  void clear();

  void set_next(ShenandoahTaskQueueSegment * next) { _next = next; }

  ShenandoahTaskQueueSegment * next() { return _next; }

private:
  ShenandoahTaskQueueSegment(ReservedSpace& rs);
};

// TaskQueue is the external interface: worker threads use push/pop from it,
// and the rest is wired up automatically.
//
class ShenandoahTaskQueue : public CHeapObj<mtGC> {
  shenandoah_padding(0);
  ShenandoahTaskQueueStack* _stack;
  ShenandoahTaskQueueSet* const _qs;
  shenandoah_padding(1);

public:
  ShenandoahTaskQueue(ShenandoahTaskQueueSet* qs);
  ~ShenandoahTaskQueue();

  ALWAYSINLINE
  void push(ShenandoahTask task);

  ALWAYSINLINE
  bool pop(ShenandoahTask& task);

  void flush_to_shared();
  void compact();
  void clear();

  bool is_empty();

  void share_work();
};

class ShenandoahTaskQueueSet : public CHeapObj<mtGC> {
  static const size_t STACK_SIZE = sizeof(ShenandoahTaskQueueStack);
  static_assert(is_power_of_2(STACK_SIZE));

  ShenandoahTaskQueue** _queues;
  size_t _queue_count;

  Atomic<ShenandoahTaskQueueSegment*> _segments_head;
  Atomic<size_t> _num_segments;
  Atomic<size_t> _peak_segments;

  shenandoah_padding(0);
  Atomic<size_t> _shared_count;
  shenandoah_padding(1);

  inline void try_grow(ShenandoahTaskQueueSegment* seen_head);

public:
  ShenandoahTaskQueueSet(uint queue_count);
  ~ShenandoahTaskQueueSet();

  inline ShenandoahTaskQueueStack* pick_shared();
  inline ShenandoahTaskQueueStack* pick_free();

  inline void mark_shared(ShenandoahTaskQueueStack* stack);
  inline void mark_free(ShenandoahTaskQueueStack* stack);

  ShenandoahTaskQueue* queue(uint i) {
    return _queues[i];
  }

  bool is_empty();
  inline bool is_shared_empty();
  inline size_t shared_count();

  void clear();
  void flush_to_shared();
  void trim();
};

class ShenandoahTaskTerminator {
  ShenandoahHeap* _heap;
  ShenandoahTaskQueueSet* _queues;
  Atomic<size_t> _active_workers;
  size_t _total_workers;

public:
  ShenandoahTaskTerminator(int workers, ShenandoahTaskQueueSet* queues);

  bool offer_termination(bool cancellable);
  int waiters();
  bool has_waiters();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP
