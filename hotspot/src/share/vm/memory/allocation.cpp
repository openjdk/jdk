/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/ostream.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif

void* CHeapObj::operator new(size_t size){
  return (void *) AllocateHeap(size, "CHeapObj-new");
}

void CHeapObj::operator delete(void* p){
 FreeHeap(p);
}

void* StackObj::operator new(size_t size)  { ShouldNotCallThis(); return 0; };
void  StackObj::operator delete(void* p)   { ShouldNotCallThis(); };
void* _ValueObj::operator new(size_t size)  { ShouldNotCallThis(); return 0; };
void  _ValueObj::operator delete(void* p)   { ShouldNotCallThis(); };

void* ResourceObj::operator new(size_t size, allocation_type type) {
  address res;
  switch (type) {
   case C_HEAP:
    res = (address)AllocateHeap(size, "C_Heap: ResourceOBJ");
    DEBUG_ONLY(set_allocation_type(res, C_HEAP);)
    break;
   case RESOURCE_AREA:
    // new(size) sets allocation type RESOURCE_AREA.
    res = (address)operator new(size);
    break;
   default:
    ShouldNotReachHere();
  }
  return res;
}

void ResourceObj::operator delete(void* p) {
  assert(((ResourceObj *)p)->allocated_on_C_heap(),
         "delete only allowed for C_HEAP objects");
  DEBUG_ONLY(((ResourceObj *)p)->_allocation = (uintptr_t)badHeapOopVal;)
  FreeHeap(p);
}

#ifdef ASSERT
void ResourceObj::set_allocation_type(address res, allocation_type type) {
    // Set allocation type in the resource object
    uintptr_t allocation = (uintptr_t)res;
    assert((allocation & allocation_mask) == 0, "address should be aligned to 4 bytes at least");
    assert(type <= allocation_mask, "incorrect allocation type");
    ((ResourceObj *)res)->_allocation = ~(allocation + type);
}

ResourceObj::allocation_type ResourceObj::get_allocation_type() const {
    assert(~(_allocation | allocation_mask) == (uintptr_t)this, "lost resource object");
    return (allocation_type)((~_allocation) & allocation_mask);
}

ResourceObj::ResourceObj() { // default constructor
    if (~(_allocation | allocation_mask) != (uintptr_t)this) {
      set_allocation_type((address)this, STACK_OR_EMBEDDED);
    } else if (allocated_on_stack()) {
      // For some reason we got a value which looks like an allocation on stack.
      // Pass if it is really allocated on stack.
      assert(Thread::current()->on_local_stack((address)this),"should be on stack");
    } else {
      assert(allocated_on_res_area() || allocated_on_C_heap() || allocated_on_arena(),
             "allocation_type should be set by operator new()");
    }
}

ResourceObj::ResourceObj(const ResourceObj& r) { // default copy constructor
    // Used in ClassFileParser::parse_constant_pool_entries() for ClassFileStream.
    set_allocation_type((address)this, STACK_OR_EMBEDDED);
}

ResourceObj& ResourceObj::operator=(const ResourceObj& r) { // default copy assignment
    // Used in InlineTree::ok_to_inline() for WarmCallInfo.
    assert(allocated_on_stack(), "copy only into local");
    // Keep current _allocation value;
    return *this;
}

ResourceObj::~ResourceObj() {
    // allocated_on_C_heap() also checks that encoded (in _allocation) address == this.
    if (!allocated_on_C_heap()) {  // ResourceObj::delete() zaps _allocation for C_heap.
      _allocation = (uintptr_t)badHeapOopVal; // zap type
    }
}
#endif // ASSERT


void trace_heap_malloc(size_t size, const char* name, void* p) {
  // A lock is not needed here - tty uses a lock internally
  tty->print_cr("Heap malloc " INTPTR_FORMAT " %7d %s", p, size, name == NULL ? "" : name);
}


void trace_heap_free(void* p) {
  // A lock is not needed here - tty uses a lock internally
  tty->print_cr("Heap free   " INTPTR_FORMAT, p);
}

bool warn_new_operator = false; // see vm_main

//--------------------------------------------------------------------------------------
// ChunkPool implementation

// MT-safe pool of chunks to reduce malloc/free thrashing
// NB: not using Mutex because pools are used before Threads are initialized
class ChunkPool {
  Chunk*       _first;        // first cached Chunk; its first word points to next chunk
  size_t       _num_chunks;   // number of unused chunks in pool
  size_t       _num_used;     // number of chunks currently checked out
  const size_t _size;         // size of each chunk (must be uniform)

  // Our three static pools
  static ChunkPool* _large_pool;
  static ChunkPool* _medium_pool;
  static ChunkPool* _small_pool;

  // return first element or null
  void* get_first() {
    Chunk* c = _first;
    if (_first) {
      _first = _first->next();
      _num_chunks--;
    }
    return c;
  }

 public:
  // All chunks in a ChunkPool has the same size
   ChunkPool(size_t size) : _size(size) { _first = NULL; _num_chunks = _num_used = 0; }

  // Allocate a new chunk from the pool (might expand the pool)
  void* allocate(size_t bytes) {
    assert(bytes == _size, "bad size");
    void* p = NULL;
    { ThreadCritical tc;
      _num_used++;
      p = get_first();
      if (p == NULL) p = os::malloc(bytes);
    }
    if (p == NULL)
      vm_exit_out_of_memory(bytes, "ChunkPool::allocate");

    return p;
  }

  // Return a chunk to the pool
  void free(Chunk* chunk) {
    assert(chunk->length() + Chunk::aligned_overhead_size() == _size, "bad size");
    ThreadCritical tc;
    _num_used--;

    // Add chunk to list
    chunk->set_next(_first);
    _first = chunk;
    _num_chunks++;
  }

  // Prune the pool
  void free_all_but(size_t n) {
    // if we have more than n chunks, free all of them
    ThreadCritical tc;
    if (_num_chunks > n) {
      // free chunks at end of queue, for better locality
      Chunk* cur = _first;
      for (size_t i = 0; i < (n - 1) && cur != NULL; i++) cur = cur->next();

      if (cur != NULL) {
        Chunk* next = cur->next();
        cur->set_next(NULL);
        cur = next;

        // Free all remaining chunks
        while(cur != NULL) {
          next = cur->next();
          os::free(cur);
          _num_chunks--;
          cur = next;
        }
      }
    }
  }

  // Accessors to preallocated pool's
  static ChunkPool* large_pool()  { assert(_large_pool  != NULL, "must be initialized"); return _large_pool;  }
  static ChunkPool* medium_pool() { assert(_medium_pool != NULL, "must be initialized"); return _medium_pool; }
  static ChunkPool* small_pool()  { assert(_small_pool  != NULL, "must be initialized"); return _small_pool;  }

  static void initialize() {
    _large_pool  = new ChunkPool(Chunk::size        + Chunk::aligned_overhead_size());
    _medium_pool = new ChunkPool(Chunk::medium_size + Chunk::aligned_overhead_size());
    _small_pool  = new ChunkPool(Chunk::init_size   + Chunk::aligned_overhead_size());
  }

  static void clean() {
    enum { BlocksToKeep = 5 };
     _small_pool->free_all_but(BlocksToKeep);
     _medium_pool->free_all_but(BlocksToKeep);
     _large_pool->free_all_but(BlocksToKeep);
  }
};

ChunkPool* ChunkPool::_large_pool  = NULL;
ChunkPool* ChunkPool::_medium_pool = NULL;
ChunkPool* ChunkPool::_small_pool  = NULL;

void chunkpool_init() {
  ChunkPool::initialize();
}

void
Chunk::clean_chunk_pool() {
  ChunkPool::clean();
}


//--------------------------------------------------------------------------------------
// ChunkPoolCleaner implementation
//

class ChunkPoolCleaner : public PeriodicTask {
  enum { CleaningInterval = 5000 };      // cleaning interval in ms

 public:
   ChunkPoolCleaner() : PeriodicTask(CleaningInterval) {}
   void task() {
     ChunkPool::clean();
   }
};

//--------------------------------------------------------------------------------------
// Chunk implementation

void* Chunk::operator new(size_t requested_size, size_t length) {
  // requested_size is equal to sizeof(Chunk) but in order for the arena
  // allocations to come out aligned as expected the size must be aligned
  // to expected arean alignment.
  // expect requested_size but if sizeof(Chunk) doesn't match isn't proper size we must align it.
  assert(ARENA_ALIGN(requested_size) == aligned_overhead_size(), "Bad alignment");
  size_t bytes = ARENA_ALIGN(requested_size) + length;
  switch (length) {
   case Chunk::size:        return ChunkPool::large_pool()->allocate(bytes);
   case Chunk::medium_size: return ChunkPool::medium_pool()->allocate(bytes);
   case Chunk::init_size:   return ChunkPool::small_pool()->allocate(bytes);
   default: {
     void *p =  os::malloc(bytes);
     if (p == NULL)
       vm_exit_out_of_memory(bytes, "Chunk::new");
     return p;
   }
  }
}

void Chunk::operator delete(void* p) {
  Chunk* c = (Chunk*)p;
  switch (c->length()) {
   case Chunk::size:        ChunkPool::large_pool()->free(c); break;
   case Chunk::medium_size: ChunkPool::medium_pool()->free(c); break;
   case Chunk::init_size:   ChunkPool::small_pool()->free(c); break;
   default:                 os::free(c);
  }
}

Chunk::Chunk(size_t length) : _len(length) {
  _next = NULL;         // Chain on the linked list
}


void Chunk::chop() {
  Chunk *k = this;
  while( k ) {
    Chunk *tmp = k->next();
    // clear out this chunk (to detect allocation bugs)
    if (ZapResourceArea) memset(k->bottom(), badResourceValue, k->length());
    delete k;                   // Free chunk (was malloc'd)
    k = tmp;
  }
}

void Chunk::next_chop() {
  _next->chop();
  _next = NULL;
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

Arena::Arena(size_t init_size) {
  size_t round_size = (sizeof (char *)) - 1;
  init_size = (init_size+round_size) & ~round_size;
  _first = _chunk = new (init_size) Chunk(init_size);
  _hwm = _chunk->bottom();      // Save the cached hwm, max
  _max = _chunk->top();
  set_size_in_bytes(init_size);
}

Arena::Arena() {
  _first = _chunk = new (Chunk::init_size) Chunk(Chunk::init_size);
  _hwm = _chunk->bottom();      // Save the cached hwm, max
  _max = _chunk->top();
  set_size_in_bytes(Chunk::init_size);
}

Arena::Arena(Arena *a) : _chunk(a->_chunk), _hwm(a->_hwm), _max(a->_max), _first(a->_first) {
  set_size_in_bytes(a->size_in_bytes());
}

Arena *Arena::move_contents(Arena *copy) {
  copy->destruct_contents();
  copy->_chunk = _chunk;
  copy->_hwm   = _hwm;
  copy->_max   = _max;
  copy->_first = _first;
  copy->set_size_in_bytes(size_in_bytes());
  // Destroy original arena
  reset();
  return copy;            // Return Arena with contents
}

Arena::~Arena() {
  destruct_contents();
}

// Destroy this arenas contents and reset to empty
void Arena::destruct_contents() {
  if (UseMallocOnly && _first != NULL) {
    char* end = _first->next() ? _first->top() : _hwm;
    free_malloced_objects(_first, _first->bottom(), end, _hwm);
  }
  _first->chop();
  reset();
}


// Total of all Chunks in arena
size_t Arena::used() const {
  size_t sum = _chunk->length() - (_max-_hwm); // Size leftover in this Chunk
  register Chunk *k = _first;
  while( k != _chunk) {         // Whilst have Chunks in a row
    sum += k->length();         // Total size of this Chunk
    k = k->next();              // Bump along to next Chunk
  }
  return sum;                   // Return total consumed space.
}


// Grow a new Chunk
void* Arena::grow( size_t x ) {
  // Get minimal required size.  Either real big, or even bigger for giant objs
  size_t len = MAX2(x, (size_t) Chunk::size);

  Chunk *k = _chunk;            // Get filled-up chunk address
  _chunk = new (len) Chunk(len);

  if (_chunk == NULL)
      vm_exit_out_of_memory(len * Chunk::aligned_overhead_size(), "Arena::grow");

  if (k) k->set_next(_chunk);   // Append new chunk to end of linked list
  else _first = _chunk;
  _hwm  = _chunk->bottom();     // Save the cached hwm, max
  _max =  _chunk->top();
  set_size_in_bytes(size_in_bytes() + len);
  void* result = _hwm;
  _hwm += x;
  return result;
}



// Reallocate storage in Arena.
void *Arena::Arealloc(void* old_ptr, size_t old_size, size_t new_size) {
  assert(new_size >= 0, "bad size");
  if (new_size == 0) return NULL;
#ifdef ASSERT
  if (UseMallocOnly) {
    // always allocate a new object  (otherwise we'll free this one twice)
    char* copy = (char*)Amalloc(new_size);
    size_t n = MIN2(old_size, new_size);
    if (n > 0) memcpy(copy, old_ptr, n);
    Afree(old_ptr,old_size);    // Mostly done to keep stats accurate
    return copy;
  }
#endif
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
  void *new_ptr = Amalloc(new_size);
  memcpy( new_ptr, c_old, old_size );
  Afree(c_old,old_size);        // Mostly done to keep stats accurate
  return new_ptr;
}


// Determine if pointer belongs to this Arena or not.
bool Arena::contains( const void *ptr ) const {
#ifdef ASSERT
  if (UseMallocOnly) {
    // really slow, but not easy to make fast
    if (_chunk == NULL) return false;
    char** bottom = (char**)_chunk->bottom();
    for (char** p = (char**)_hwm - 1; p >= bottom; p--) {
      if (*p == ptr) return true;
    }
    for (Chunk *c = _first; c != NULL; c = c->next()) {
      if (c == _chunk) continue;  // current chunk has been processed
      char** bottom = (char**)c->bottom();
      for (char** p = (char**)c->top() - 1; p >= bottom; p--) {
        if (*p == ptr) return true;
      }
    }
    return false;
  }
#endif
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


#ifdef ASSERT
void* Arena::malloc(size_t size) {
  assert(UseMallocOnly, "shouldn't call");
  // use malloc, but save pointer in res. area for later freeing
  char** save = (char**)internal_malloc_4(sizeof(char*));
  return (*save = (char*)os::malloc(size));
}

// for debugging with UseMallocOnly
void* Arena::internal_malloc_4(size_t x) {
  assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
  if (_hwm + x > _max) {
    return grow(x);
  } else {
    char *old = _hwm;
    _hwm += x;
    return old;
  }
}
#endif


//--------------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
// The global operator new should never be called since it will usually indicate
// a memory leak.  Use CHeapObj as the base class of such objects to make it explicit
// that they're allocated on the C heap.
// Commented out in product version to avoid conflicts with third-party C++ native code.
// %% note this is causing a problem on solaris debug build. the global
// new is being called from jdk source and causing data corruption.
// src/share/native/sun/awt/font/fontmanager/textcache/hsMemory.cpp::hsSoftNew
// define CATCH_OPERATOR_NEW_USAGE if you want to use this.
#ifdef CATCH_OPERATOR_NEW_USAGE
void* operator new(size_t size){
  static bool warned = false;
  if (!warned && warn_new_operator)
    warning("should not call global (default) operator new");
  warned = true;
  return (void *) AllocateHeap(size, "global operator new");
}
#endif

void AllocatedObj::print() const       { print_on(tty); }
void AllocatedObj::print_value() const { print_value_on(tty); }

void AllocatedObj::print_on(outputStream* st) const {
  st->print_cr("AllocatedObj(" INTPTR_FORMAT ")", this);
}

void AllocatedObj::print_value_on(outputStream* st) const {
  st->print("AllocatedObj(" INTPTR_FORMAT ")", this);
}

size_t Arena::_bytes_allocated = 0;

AllocStats::AllocStats() {
  start_mallocs = os::num_mallocs;
  start_frees = os::num_frees;
  start_malloc_bytes = os::alloc_bytes;
  start_res_bytes = Arena::_bytes_allocated;
}

int     AllocStats::num_mallocs() { return os::num_mallocs - start_mallocs; }
size_t  AllocStats::alloc_bytes() { return os::alloc_bytes - start_malloc_bytes; }
size_t  AllocStats::resource_bytes() { return Arena::_bytes_allocated - start_res_bytes; }
int     AllocStats::num_frees() { return os::num_frees - start_frees; }
void    AllocStats::print() {
  tty->print("%d mallocs (%ldK), %d frees, %ldK resrc",
             num_mallocs(), alloc_bytes()/K, num_frees(), resource_bytes()/K);
}


// debugging code
inline void Arena::free_all(char** start, char** end) {
  for (char** p = start; p < end; p++) if (*p) os::free(*p);
}

void Arena::free_malloced_objects(Chunk* chunk, char* hwm, char* max, char* hwm2) {
  assert(UseMallocOnly, "should not call");
  // free all objects malloced since resource mark was created; resource area
  // contains their addresses
  if (chunk->next()) {
    // this chunk is full, and some others too
    for (Chunk* c = chunk->next(); c != NULL; c = c->next()) {
      char* top = c->top();
      if (c->next() == NULL) {
        top = hwm2;     // last junk is only used up to hwm2
        assert(c->contains(hwm2), "bad hwm2");
      }
      free_all((char**)c->bottom(), (char**)top);
    }
    assert(chunk->contains(hwm), "bad hwm");
    assert(chunk->contains(max), "bad max");
    free_all((char**)hwm, (char**)max);
  } else {
    // this chunk was partially used
    assert(chunk->contains(hwm), "bad hwm");
    assert(chunk->contains(hwm2), "bad hwm2");
    free_all((char**)hwm, (char**)hwm2);
  }
}


ReallocMark::ReallocMark() {
#ifdef ASSERT
  Thread *thread = ThreadLocalStorage::get_thread_slow();
  _nesting = thread->resource_area()->nesting();
#endif
}

void ReallocMark::check() {
#ifdef ASSERT
  if (_nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: array could grow within nested ResourceMark");
  }
#endif
}

#endif // Non-product
