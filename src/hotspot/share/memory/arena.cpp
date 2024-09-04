/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compilationMemoryStatistic.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTracker.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

// Pre-defined default chunk sizes must be arena-aligned, see Chunk::operator new()
STATIC_ASSERT(is_aligned((int)Chunk::tiny_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned((int)Chunk::init_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned((int)Chunk::medium_size, ARENA_AMALLOC_ALIGNMENT));
STATIC_ASSERT(is_aligned((int)Chunk::size, ARENA_AMALLOC_ALIGNMENT));


const char* Arena::tag_name[] = {
#define ARENA_TAG_STRING(name, str, desc) XSTR(name),
  DO_ARENA_TAG(ARENA_TAG_STRING)
#undef ARENA_TAG_STRING
};

const char* Arena::tag_desc[] = {
#define ARENA_TAG_DESC(name, str, desc) XSTR(desc),
  DO_ARENA_TAG(ARENA_TAG_DESC)
#undef ARENA_TAG_DESC
};

// MT-safe pool of same-sized chunks to reduce malloc/free thrashing
// NB: not using Mutex because pools are used before Threads are initialized
class ChunkPool {
  // Our four static pools
  static constexpr int _num_pools = 4;
  static ChunkPool _pools[_num_pools];

  Chunk*       _first;
  const size_t _size;         // (inner payload) size of the chunks this pool serves

  // Returns null if pool is empty.
  Chunk* take_from_pool() {
    ThreadCritical tc;
    Chunk* c = _first;
    if (_first != nullptr) {
      _first = _first->next();
    }
    return c;
  }
  void return_to_pool(Chunk* chunk) {
    assert(chunk->length() == _size, "wrong pool for this chunk");
    ThreadCritical tc;
    chunk->set_next(_first);
    _first = chunk;
  }

  // Clear this pool of all contained chunks
  void prune() {
    // Free all chunks while in ThreadCritical lock
    // so NMT adjustment is stable.
    ThreadCritical tc;
    Chunk* cur = _first;
    Chunk* next = nullptr;
    while (cur != nullptr) {
      next = cur->next();
      os::free(cur);
      cur = next;
    }
    _first = nullptr;
  }

  // Given a (inner payload) size, return the pool responsible for it, or null if the size is non-standard
  static ChunkPool* get_pool_for_size(size_t size) {
    for (int i = 0; i < _num_pools; i++) {
      if (_pools[i]._size == size) {
        return _pools + i;
      }
    }
    return nullptr;
  }

public:
  ChunkPool(size_t size) : _first(nullptr), _size(size) {}

  static void clean() {
    NativeHeapTrimmer::SuspendMark sm("chunk pool cleaner");
    for (int i = 0; i < _num_pools; i++) {
      _pools[i].prune();
    }
  }

  // Returns an initialized and null-terminated Chunk of requested size
  static Chunk* allocate_chunk(size_t length, AllocFailType alloc_failmode);
  static void deallocate_chunk(Chunk* p);
};

Chunk* ChunkPool::allocate_chunk(size_t length, AllocFailType alloc_failmode) {
  // - requested_size = sizeof(Chunk)
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

  assert(is_aligned(length, ARENA_AMALLOC_ALIGNMENT), "chunk payload length misaligned: "
         SIZE_FORMAT ".", length);
  // Try to reuse a freed chunk from the pool
  ChunkPool* pool = ChunkPool::get_pool_for_size(length);
  Chunk* chunk = nullptr;
  if (pool != nullptr) {
    Chunk* c = pool->take_from_pool();
    if (c != nullptr) {
      assert(c->length() == length, "wrong length?");
      chunk = c;
    }
  }
  if (chunk == nullptr) {
    // Either the pool was empty, or this is a non-standard length. Allocate a new Chunk from C-heap.
    size_t bytes = ARENA_ALIGN(sizeof(Chunk)) + length;
    void* p = os::malloc(bytes, mtChunk, CALLER_PC);
    if (p == nullptr && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
      vm_exit_out_of_memory(bytes, OOM_MALLOC_ERROR, "Chunk::new");
    }
    chunk = (Chunk*)p;
  }
  ::new(chunk) Chunk(length);
  // We rely on arena alignment <= malloc alignment.
  assert(is_aligned(chunk, ARENA_AMALLOC_ALIGNMENT), "Chunk start address misaligned.");
  return chunk;
}

void ChunkPool::deallocate_chunk(Chunk* c) {
  // If this is a standard-sized chunk, return it to its pool; otherwise free it.
  ChunkPool* pool = ChunkPool::get_pool_for_size(c->length());
  if (pool != nullptr) {
    pool->return_to_pool(c);
  } else {
    ThreadCritical tc;  // Free chunks under TC lock so that NMT adjustment is stable.
    os::free(c);
  }
}

ChunkPool ChunkPool::_pools[] = { Chunk::size, Chunk::medium_size, Chunk::init_size, Chunk::tiny_size };

class ChunkPoolCleaner : public PeriodicTask {
  static const int cleaning_interval = 5000; // cleaning interval in ms

 public:
   ChunkPoolCleaner() : PeriodicTask(cleaning_interval) {}
   void task() {
     ChunkPool::clean();
   }
};

void Arena::start_chunk_pool_cleaner_task() {
#ifdef ASSERT
  static bool task_created = false;
  assert(!task_created, "should not start chuck pool cleaner twice");
  task_created = true;
#endif
  ChunkPoolCleaner* cleaner = new ChunkPoolCleaner();
  cleaner->enroll();
}

Chunk::Chunk(size_t length) : _len(length) {
  _next = nullptr;         // Chain on the linked list
}

void Chunk::chop(Chunk* k) {
  while (k != nullptr) {
    Chunk* tmp = k->next();
    // clear out this chunk (to detect allocation bugs)
    if (ZapResourceArea) memset(k->bottom(), badResourceValue, k->length());
    ChunkPool::deallocate_chunk(k);
    k = tmp;
  }
}

void Chunk::next_chop(Chunk* k) {
  assert(k != nullptr && k->_next != nullptr, "must be non-null");
  Chunk::chop(k->_next);
  k->_next = nullptr;
}

Arena::Arena(MEMFLAGS flag, Tag tag, size_t init_size) :
  _flags(flag), _tag(tag),
  _size_in_bytes(0),
  _first(nullptr), _chunk(nullptr),
  _hwm(nullptr), _max(nullptr)
{
  init_size = ARENA_ALIGN(init_size);
  _chunk = ChunkPool::allocate_chunk(init_size, AllocFailStrategy::EXIT_OOM);
  _first = _chunk;
  _hwm = _chunk->bottom();      // Save the cached hwm, max
  _max = _chunk->top();
  MemTracker::record_new_arena(flag);
  set_size_in_bytes(init_size);
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
    Chunk::chop(_first);
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
    if (CompilationMemoryStatistic::enabled() && _flags == mtCompiler) {
      Thread* const t = Thread::current();
      if (t != nullptr && t->is_Compiler_thread()) {
        CompilationMemoryStatistic::on_arena_change(delta, this);
      }
    }
  }
}

// Total of all Chunks in arena
size_t Arena::used() const {
  size_t sum = _chunk->length() - (_max-_hwm); // Size leftover in this Chunk
  Chunk* k = _first;
  while( k != _chunk) {         // Whilst have Chunks in a row
    sum += k->length();         // Total size of this Chunk
    k = k->next();              // Bump along to next Chunk
  }
  return sum;                   // Return total consumed space.
}

// Grow a new Chunk
void* Arena::grow(size_t x, AllocFailType alloc_failmode) {
  // Get minimal required size.  Either real big, or even bigger for giant objs
  // (Note: all chunk sizes have to be 64-bit aligned)
  size_t len = MAX2(ARENA_ALIGN(x), (size_t) Chunk::size);

  if (MemTracker::check_exceeds_limit(x, _flags)) {
    return nullptr;
  }

  Chunk* k = _chunk;            // Get filled-up chunk address
  _chunk = ChunkPool::allocate_chunk(len, alloc_failmode);

  if (_chunk == nullptr) {
    _chunk = k;                 // restore the previous value of _chunk
    return nullptr;
  }

  if (k != nullptr) {
    k->set_next(_chunk);        // Append new chunk to end of linked list
  } else {
    _first = _chunk;
  }
  _hwm  = _chunk->bottom();     // Save the cached hwm, max
  _max =  _chunk->top();
  set_size_in_bytes(size_in_bytes() + len);
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
  for (Chunk* c = _first; c; c = c->next()) {
    if (c == _chunk) continue;  // current chunk has been processed
    if ((void*)c->bottom() <= ptr && ptr < (void*)c->top()) {
      return true;              // Check for every chunk in Arena
    }
  }
  return false;                 // Not in any Chunk, so not in Arena
}
