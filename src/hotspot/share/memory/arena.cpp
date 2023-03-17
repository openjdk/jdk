/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2023 SAP SE. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memTracker.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

// Pre-defined default chunk sizes must be arena-aligned, see Chunk::operator new()
STATIC_ASSERT(is_aligned(Chunk::tiny_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned(Chunk::init_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned(Chunk::medium_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned(Chunk::size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned(Chunk::non_pool_size, ARENA_AMALLOC_ALIGNMENT));

//--------------------------------------------------------------------------------------
// ChunkPool implementation

// MT-safe pool of same-sized chunks to reduce malloc/free thrashing
// NB: not using Mutex because pools are used before Threads are initialized
class ChunkPool {
  Chunk*       _first;        // first cached Chunk; its first word points to next chunk
  const size_t _size;         // (inner payload) size of the chunks this pool serves

  // Our four static pools.
  static ChunkPool _pools[4];

 public:
  constexpr ChunkPool(size_t size) : _first(nullptr), _size(size) {}

  // Allocate a chunk from the pool; returns null if pool is empty.
  Chunk* allocate() {
    ThreadCritical tc;
    Chunk* c = _first;
    if (_first != nullptr) {
      _first = _first->next();
    }
    if (c != nullptr) {
      c->set_next(nullptr);
    }
    return c;
  }

  // Return a chunk to the pool
  void free(Chunk* chunk) {
    assert(chunk->pool_size() == Chunk::pool_size(_size), "wrong pool for this chunk");
    assert(chunk->length() >= _size, "wrong pool for this chunk");
    ThreadCritical tc;
    chunk->set_next(_first);
    _first = chunk;
  }

  // Prune the pool
  void prune() {
    // Free all chunks while in ThreadCritical lock
    // so NMT adjustment is stable.
    ThreadCritical tc;
    Chunk* cur = _first;
    Chunk* next = nullptr;
    while (cur != nullptr) {
      next = cur->next();
      cur->deallocate();
      cur = next;
    }
    _first = nullptr;
  }

  static void clean() {
    for (size_t i = 0; i < ARRAY_SIZE(_pools); i++) {
      _pools[i].prune();
    }
  }

  // Given a (inner payload) size, return the pool responsible for it, or null if the size is non-standard
  static ChunkPool* get_pool_for_size(size_t size) {
    for (size_t i = 0; i < ARRAY_SIZE(_pools); i++) {
      if (_pools[i]._size == size) {
        return _pools + i;
      }
    }
    return nullptr;
  }

  static ChunkPool* get_pool_for_chunk(Chunk* c) {
    ChunkPool* pool;
    const ChunkPoolSize pool_size = c->pool_size();
    const size_t pool_index = static_cast<size_t>(pool_size) - 1;  // Underflow is fine.
    if (pool_index < ARRAY_SIZE(_pools)) {
      pool = &_pools[pool_index];
      assert(pool_size == Chunk::pool_size(pool->_size), "unexpected chunk pool");
      assert(c->length() >= pool->_size,
             "chunk too small, expected at least: " SIZE_FORMAT " got: " SIZE_FORMAT,
             pool->_size, c->length());
    } else {
      assert(pool_size == ChunkPoolSize::NONE, "unexpected chunk pool");
      pool = nullptr;
    }
    return pool;
  }

};

// Must be in the same order as ChunkPoolSize. The index is ChunkPoolSize minus 1.
ChunkPool ChunkPool::_pools[] = { Chunk::tiny_size, Chunk::small_size, Chunk::medium_size, Chunk::large_size };

//--------------------------------------------------------------------------------------
// ChunkPoolCleaner implementation
//

class ChunkPoolCleaner : public PeriodicTask {
  static const int cleaning_interval = 5000; // cleaning interval in ms

 public:
   ChunkPoolCleaner() : PeriodicTask(cleaning_interval) {}

   void task() override {
     ChunkPool::clean();
   }
};

//--------------------------------------------------------------------------------------
// Chunk implementation

Chunk* Chunk::allocate(size_t length, AllocFailType alloc_failmode) {
  assert(is_aligned(length, ARENA_AMALLOC_ALIGNMENT),
         "chunk payload length misaligned: " SIZE_FORMAT, length);
  const size_t overhead = aligned_overhead_size();
  size_t bytes = length + overhead;
  size_t actual_bytes;
  void* p = os::malloc(bytes, mtChunk, CALLER_PC, &actual_bytes);
  if (p == nullptr) {
    if (alloc_failmode == AllocFailStrategy::EXIT_OOM) {
      vm_exit_out_of_memory(bytes, OOM_MALLOC_ERROR, "Chunk::allocate");
    }
    return nullptr;
  }
  assert(is_aligned(p, ARENA_AMALLOC_ALIGNMENT), "chunk start address misaligned");
  actual_bytes -= overhead;
  return ::new(p) Chunk(actual_bytes, pool_size(length));
}

void Chunk::deallocate() {
  size_t size = raw_length() + aligned_overhead_size();
  this->~Chunk();
  os::free_sized(this, size);
}

Chunk* Chunk::acquire(size_t length, AllocFailType alloc_failmode) {
  // - length = payload size
  // We must ensure that the boundaries of the payload (C and D) are aligned to 64-bit:
  //
  // +-----------+--+--------------------------------------------+
  // |           |g |                                            |
  // | Chunk     |a |               Payload                      |
  // |           |p |                                            |
  // +-----------+--+--------------------------------------------+
  // A           B  C                                            D
  //
  // - The Chunk is allocated from C-heap, therefore its start address (A) should be
  //   64-bit aligned on all our platforms, including 32-bit.
  // - sizeof(Chunk) (B) may not be aligned to 64-bit, and we have to take that into
  //   account when calculating the Payload bottom (C) (see Chunk::bottom())
  // - the payload size (length) must be aligned to 64-bit, which takes care of 64-bit
  //   aligning (D)
  const size_t aligned_length = ARENA_ALIGN(length);
  ChunkPool* pool = ChunkPool::get_pool_for_size(aligned_length);
  if (pool != nullptr) {
    Chunk* c = pool->allocate();
    if (c != nullptr) {
      assert(c->length() >= aligned_length,
             "chunk too small, expected at least: " SIZE_FORMAT " got: " SIZE_FORMAT,
             aligned_length, c->length());
      return c;
    }
  }
  return allocate(aligned_length, alloc_failmode);
}

void Chunk::release() {
  ChunkPool* pool = ChunkPool::get_pool_for_chunk(this);
  if (pool != nullptr) {
    pool->free(this);
  } else {
    ThreadCritical tc;  // Free chunks under TC lock so that NMT adjustment is stable.
    deallocate();
  }
}

Chunk::Chunk(size_t length, ChunkPoolSize pool_size)
    : _next(static_cast<uintptr_t>(pool_size)), _len(length) {
#if defined(ASSERT)
  if (is_aligned(length, aligned_overhead_size())) {
    assert(this->length() == length, "size mismatch");
  }
#endif
}

void Chunk::chop() {
  Chunk* c = this;
  while (c != nullptr) {
    Chunk* next = c->next();
    // clear out this chunk (to detect allocation bugs)
    if (ZapResourceArea) memset(c->bottom(), badResourceValue, c->length());
    c->release();
    c = next;
  }
}

void Chunk::next_chop() {
  next()->chop();
  set_next(nullptr);
}

void Chunk::start_chunk_pool_cleaner_task() {
#ifdef ASSERT
  static bool task_created = false;
  assert(!task_created, "should not start chuck pool cleaner twice");
  task_created = true;
#endif
  ChunkPoolCleaner* cleaner = new ChunkPoolCleaner();
  cleaner->enroll();
}

//------------------------------Arena------------------------------------------

Arena::Arena(MEMFLAGS flag, size_t init_size) : _flags(flag), _size_in_bytes(0)  {
  _first = _chunk = Chunk::acquire(init_size, AllocFailStrategy::EXIT_OOM);
  _hwm = _chunk->bottom();      // Save the cached hwm, max
  _max = _chunk->top();
  MemTracker::record_new_arena(flag);
  set_size_in_bytes(_chunk->length());
}

Arena::Arena(MEMFLAGS flag) : Arena(flag, Chunk::small_size) {}

Arena *Arena::move_contents(Arena *copy) {
  copy->destruct_contents();
  copy->_chunk = _chunk;
  copy->_hwm   = _hwm;
  copy->_max   = _max;
  copy->_first = _first;

  // workaround rare racing condition, which could double count
  // the arena size by native memory tracking
  size_t size = size_in_bytes();
  set_size_in_bytes(0);
  copy->set_size_in_bytes(size);
  // Destroy original arena
  reset();
  return copy;            // Return Arena with contents
}

Arena::~Arena() {
  destruct_contents();
  MemTracker::record_arena_free(_flags);
}

// Destroy this arenas contents and reset to empty
void Arena::destruct_contents() {
  // reset size before chop to avoid a rare racing condition
  // that can have total arena memory exceed total chunk memory
  set_size_in_bytes(0);
  if (_first != nullptr) {
    _first->chop();
  }
  reset();
}

// This is high traffic method, but many calls actually don't
// change the size
void Arena::set_size_in_bytes(size_t size) {
  if (_size_in_bytes != size) {
    ssize_t delta = size - size_in_bytes();
    _size_in_bytes = size;
    MemTracker::record_arena_size_change(delta, _flags);
  }
}

// Total of all Chunks in arena
size_t Arena::used() const {
  size_t sum = _chunk->length() - (_max-_hwm); // Size leftover in this Chunk
  Chunk *k = _first;
  while( k != _chunk) {         // Whilst have Chunks in a row
    sum += k->length();         // Total size of this Chunk
    k = k->next();              // Bump along to next Chunk
  }
  return sum;                   // Return total consumed space.
}

// Grow a new Chunk
void* Arena::grow(size_t x, AllocFailType alloc_failmode) {
  if (MemTracker::check_exceeds_limit(x, _flags)) {
    return nullptr;
  }

  Chunk *k = _chunk;            // Get filled-up chunk address
  // Get minimal required size.  Either real big, or even bigger for giant objs
  // (Note: all chunk sizes have to be 64-bit aligned)
  _chunk = Chunk::acquire(MAX2(ARENA_ALIGN(x), Chunk::size), alloc_failmode);

  if (_chunk == nullptr) {
    _chunk = k;                 // restore the previous value of _chunk
    return nullptr;
  }
  if (k) k->set_next(_chunk);   // Append new chunk to end of linked list
  else _first = _chunk;
  _hwm  = _chunk->bottom();     // Save the cached hwm, max
  _max =  _chunk->top();
  set_size_in_bytes(size_in_bytes() + _chunk->length());
  void* result = _hwm;
  _hwm += x;
  return result;
}



// Reallocate storage in Arena.
void *Arena::Arealloc(void* old_ptr, size_t old_size, size_t new_size, AllocFailType alloc_failmode) {
  if (new_size == 0) {
    Afree(old_ptr, old_size); // like realloc(3)
    return nullptr;
  }
  if (old_ptr == nullptr) {
    assert(old_size == 0, "sanity");
    return Amalloc(new_size, alloc_failmode); // as with realloc(3), a null old ptr is equivalent to malloc(3)
  }
  char *c_old = (char*)old_ptr; // Handy name
  // Stupid fast special case
  if( new_size <= old_size ) {  // Shrink in-place
    if( c_old+old_size == _hwm) // Attempt to free the excess bytes
      _hwm = c_old+new_size;    // Adjust hwm
    return c_old;
  }

  // make sure that new_size is legal
  size_t corrected_new_size = ARENA_ALIGN(new_size);

  // See if we can resize in-place
  if( (c_old+old_size == _hwm) &&       // Adjusting recent thing
      (c_old+corrected_new_size <= _max) ) {      // Still fits where it sits
    _hwm = c_old+corrected_new_size;      // Adjust hwm
    return c_old;               // Return old pointer
  }

  // Oops, got to relocate guts
  void *new_ptr = Amalloc(new_size, alloc_failmode);
  if (new_ptr == nullptr) {
    return nullptr;
  }
  memcpy( new_ptr, c_old, old_size );
  Afree(c_old,old_size);        // Mostly done to keep stats accurate
  return new_ptr;
}


// Determine if pointer belongs to this Arena or not.
bool Arena::contains( const void *ptr ) const {
  if (_chunk == nullptr) return false;
  if( (void*)_chunk->bottom() <= ptr && ptr < (void*)_hwm )
    return true;                // Check for in this chunk
  for (Chunk *c = _first; c; c = c->next()) {
    if (c == _chunk) continue;  // current chunk has been processed
    if ((void*)c->bottom() <= ptr && ptr < (void*)c->top()) {
      return true;              // Check for every chunk in Arena
    }
  }
  return false;                 // Not in any Chunk, so not in Arena
}
