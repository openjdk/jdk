/*
 * Copyright (c) 2016, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP
#include "gc/shared/owstTaskTerminator.hpp"
#include "gc/shared/taskqueue.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"

template<class E, MEMFLAGS F, unsigned int N = TASKQUEUE_SIZE>
class BufferedOverflowTaskQueue: public OverflowTaskQueue<E, F, N>
{
public:
  typedef OverflowTaskQueue<E, F, N> taskqueue_t;

  BufferedOverflowTaskQueue() : _buf_empty(true) {};

  TASKQUEUE_STATS_ONLY(using taskqueue_t::stats;)

  // Push task t into the queue. Returns true on success.
  inline bool push(E t);

  // Attempt to pop from the queue. Returns true on success.
  inline bool pop(E &t);

  inline void clear()  {
    _buf_empty = true;
    taskqueue_t::set_empty();
    taskqueue_t::overflow_stack()->clear();
  }

  inline bool is_empty()        const {
    return _buf_empty && taskqueue_t::is_empty();
  }

private:
  bool _buf_empty;
  E _elem;
};

// ObjArrayChunkedTask
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
//    |---------oop---------|-pow-|--chunk---|
//    0                    49     54        64
//
// By definition, chunk == 0 means "no chunk", i.e. chunking starts from 1.
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
//
// There is also a fallback version that uses plain fields, when we don't have enough space to steal the
// bits from the native pointer. It is useful to debug the _LP64 version.
//

#ifdef _MSC_VER
#pragma warning(push)
// warning C4522: multiple assignment operators specified
#pragma warning( disable:4522 )
#endif

#ifdef _LP64
class ObjArrayChunkedTask
{
public:
  enum {
    chunk_bits   = 10,
    pow_bits     = 5,
    oop_bits     = sizeof(uintptr_t)*8 - chunk_bits - pow_bits,
  };
  enum {
    oop_shift    = 0,
    pow_shift    = oop_shift + oop_bits,
    chunk_shift  = pow_shift + pow_bits,
  };

public:
  ObjArrayChunkedTask(oop o = NULL) {
    _obj = ((uintptr_t)(void*) o) << oop_shift;
  }
  ObjArrayChunkedTask(oop o, int chunk, int mult) {
    assert(0 <= chunk && chunk < nth_bit(chunk_bits), "chunk is sane: %d", chunk);
    assert(0 <= mult && mult < nth_bit(pow_bits), "pow is sane: %d", mult);
    uintptr_t t_b = ((uintptr_t) chunk) << chunk_shift;
    uintptr_t t_m = ((uintptr_t) mult) << pow_shift;
    uintptr_t obj = (uintptr_t)(void*)o;
    assert(obj < nth_bit(oop_bits), "obj ref is sane: " PTR_FORMAT, obj);
    intptr_t t_o = obj << oop_shift;
    _obj = t_o | t_m | t_b;
  }
  ObjArrayChunkedTask(const ObjArrayChunkedTask& t): _obj(t._obj) { }

  ObjArrayChunkedTask& operator =(const ObjArrayChunkedTask& t) {
    _obj = t._obj;
    return *this;
  }
  volatile ObjArrayChunkedTask&
  operator =(const volatile ObjArrayChunkedTask& t) volatile {
    (void)const_cast<uintptr_t&>(_obj = t._obj);
    return *this;
  }

  inline oop obj()   const { return (oop) reinterpret_cast<void*>((_obj >> oop_shift) & right_n_bits(oop_bits)); }
  inline int chunk() const { return (int) (_obj >> chunk_shift) & right_n_bits(chunk_bits); }
  inline int pow()   const { return (int) ((_obj >> pow_shift) & right_n_bits(pow_bits)); }
  inline bool is_not_chunked() const { return (_obj & ~right_n_bits(oop_bits + pow_bits)) == 0; }

  DEBUG_ONLY(bool is_valid() const); // Tasks to be pushed/popped must be valid.

  static size_t max_addressable() {
    return nth_bit(oop_bits);
  }

  static int chunk_size() {
    return nth_bit(chunk_bits);
  }

private:
  uintptr_t _obj;
};
#else
class ObjArrayChunkedTask
{
public:
  enum {
    chunk_bits  = 10,
    pow_bits    = 5,
  };
public:
  ObjArrayChunkedTask(oop o = NULL, int chunk = 0, int pow = 0): _obj(o) {
    assert(0 <= chunk && chunk < nth_bit(chunk_bits), "chunk is sane: %d", chunk);
    assert(0 <= pow && pow < nth_bit(pow_bits), "pow is sane: %d", pow);
    _chunk = chunk;
    _pow = pow;
  }
  ObjArrayChunkedTask(const ObjArrayChunkedTask& t): _obj(t._obj), _chunk(t._chunk), _pow(t._pow) { }

  ObjArrayChunkedTask& operator =(const ObjArrayChunkedTask& t) {
    _obj = t._obj;
    _chunk = t._chunk;
    _pow = t._pow;
    return *this;
  }
  volatile ObjArrayChunkedTask&
  operator =(const volatile ObjArrayChunkedTask& t) volatile {
    (void)const_cast<oop&>(_obj = t._obj);
    _chunk = t._chunk;
    _pow = t._pow;
    return *this;
  }

  inline oop obj()   const { return _obj; }
  inline int chunk() const { return _chunk; }
  inline int pow()  const { return _pow; }

  inline bool is_not_chunked() const { return _chunk == 0; }

  DEBUG_ONLY(bool is_valid() const); // Tasks to be pushed/popped must be valid.

  static size_t max_addressable() {
    return sizeof(oop);
  }

  static int chunk_size() {
    return nth_bit(chunk_bits);
  }

private:
  oop _obj;
  int _chunk;
  int _pow;
};
#endif

#ifdef _MSC_VER
#pragma warning(pop)
#endif

typedef ObjArrayChunkedTask ShenandoahMarkTask;
typedef BufferedOverflowTaskQueue<ShenandoahMarkTask, mtGC> ShenandoahBufferedOverflowTaskQueue;
typedef Padded<ShenandoahBufferedOverflowTaskQueue> ShenandoahObjToScanQueue;

template <class T, MEMFLAGS F>
class ParallelClaimableQueueSet: public GenericTaskQueueSet<T, F> {
private:
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile jint));
  volatile jint     _claimed_index;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

  debug_only(uint   _reserved;  )

public:
  using GenericTaskQueueSet<T, F>::size;

public:
  ParallelClaimableQueueSet(int n) : GenericTaskQueueSet<T, F>(n), _claimed_index(0) {
    debug_only(_reserved = 0; )
  }

  void clear_claimed() { _claimed_index = 0; }
  T*   claim_next();

  // reserve queues that not for parallel claiming
  void reserve(uint n) {
    assert(n <= size(), "Sanity");
    _claimed_index = (jint)n;
    debug_only(_reserved = n;)
  }

  debug_only(uint get_reserved() const { return (uint)_reserved; })
};

template <class T, MEMFLAGS F>
T* ParallelClaimableQueueSet<T, F>::claim_next() {
  jint size = (jint)GenericTaskQueueSet<T, F>::size();

  if (_claimed_index >= size) {
    return NULL;
  }

  jint index = Atomic::add(1, &_claimed_index);

  if (index <= size) {
    return GenericTaskQueueSet<T, F>::queue((uint)index - 1);
  } else {
    return NULL;
  }
}

class ShenandoahObjToScanQueueSet: public ParallelClaimableQueueSet<ShenandoahObjToScanQueue, mtGC> {
public:
  ShenandoahObjToScanQueueSet(int n) : ParallelClaimableQueueSet<ShenandoahObjToScanQueue, mtGC>(n) {}

  bool is_empty();
  void clear();

#if TASKQUEUE_STATS
  static void print_taskqueue_stats_hdr(outputStream* const st);
  void print_taskqueue_stats() const;
  void reset_taskqueue_stats();
#endif // TASKQUEUE_STATS
};

class ShenandoahTerminatorTerminator : public TerminatorTerminator {
private:
  ShenandoahHeap* _heap;
public:
  ShenandoahTerminatorTerminator(ShenandoahHeap* const heap) : _heap(heap) { }
  // return true, terminates immediately, even if there's remaining work left
  virtual bool should_exit_termination() { return _heap->cancelled_gc(); }
};

class ShenandoahTaskTerminator : public StackObj {
private:
  OWSTTaskTerminator* const   _terminator;
public:
  ShenandoahTaskTerminator(uint n_threads, TaskQueueSetSuper* queue_set);
  ~ShenandoahTaskTerminator();

  bool offer_termination(ShenandoahTerminatorTerminator* terminator) {
    return _terminator->offer_termination(terminator);
  }

  void reset_for_reuse() { _terminator->reset_for_reuse(); }
  bool offer_termination() { return offer_termination((ShenandoahTerminatorTerminator*)NULL); }
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_HPP
