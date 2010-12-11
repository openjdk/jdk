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

#ifndef SHARE_VM_MEMORY_ALLOCATION_HPP
#define SHARE_VM_MEMORY_ALLOCATION_HPP

#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif

#define ARENA_ALIGN_M1 (((size_t)(ARENA_AMALLOC_ALIGNMENT)) - 1)
#define ARENA_ALIGN_MASK (~((size_t)ARENA_ALIGN_M1))
#define ARENA_ALIGN(x) ((((size_t)(x)) + ARENA_ALIGN_M1) & ARENA_ALIGN_MASK)

// All classes in the virtual machine must be subclassed
// by one of the following allocation classes:
//
// For objects allocated in the resource area (see resourceArea.hpp).
// - ResourceObj
//
// For objects allocated in the C-heap (managed by: free & malloc).
// - CHeapObj
//
// For objects allocated on the stack.
// - StackObj
//
// For embedded objects.
// - ValueObj
//
// For classes used as name spaces.
// - AllStatic
//
// The printable subclasses are used for debugging and define virtual
// member functions for printing. Classes that avoid allocating the
// vtbl entries in the objects should therefore not be the printable
// subclasses.
//
// The following macros and function should be used to allocate memory
// directly in the resource area or in the C-heap:
//
//   NEW_RESOURCE_ARRAY(type,size)
//   NEW_RESOURCE_OBJ(type)
//   NEW_C_HEAP_ARRAY(type,size)
//   NEW_C_HEAP_OBJ(type)
//   char* AllocateHeap(size_t size, const char* name);
//   void  FreeHeap(void* p);
//
// C-heap allocation can be traced using +PrintHeapAllocation.
// malloc and free should therefore never called directly.

// Base class for objects allocated in the C-heap.

// In non product mode we introduce a super class for all allocation classes
// that supports printing.
// We avoid the superclass in product mode since some C++ compilers add
// a word overhead for empty super classes.

#ifdef PRODUCT
#define ALLOCATION_SUPER_CLASS_SPEC
#else
#define ALLOCATION_SUPER_CLASS_SPEC : public AllocatedObj
class AllocatedObj {
 public:
  // Printing support
  void print() const;
  void print_value() const;

  virtual void print_on(outputStream* st) const;
  virtual void print_value_on(outputStream* st) const;
};
#endif

class CHeapObj ALLOCATION_SUPER_CLASS_SPEC {
 public:
  void* operator new(size_t size);
  void  operator delete(void* p);
  void* new_array(size_t size);
};

// Base class for objects allocated on the stack only.
// Calling new or delete will result in fatal error.

class StackObj ALLOCATION_SUPER_CLASS_SPEC {
 public:
  void* operator new(size_t size);
  void  operator delete(void* p);
};

// Base class for objects used as value objects.
// Calling new or delete will result in fatal error.
//
// Portability note: Certain compilers (e.g. gcc) will
// always make classes bigger if it has a superclass, even
// if the superclass does not have any virtual methods or
// instance fields. The HotSpot implementation relies on this
// not to happen. So never make a ValueObj class a direct subclass
// of this object, but use the VALUE_OBJ_CLASS_SPEC class instead, e.g.,
// like this:
//
//   class A VALUE_OBJ_CLASS_SPEC {
//     ...
//   }
//
// With gcc and possible other compilers the VALUE_OBJ_CLASS_SPEC can
// be defined as a an empty string "".
//
class _ValueObj {
 public:
  void* operator new(size_t size);
  void operator delete(void* p);
};

// Base class for classes that constitute name spaces.

class AllStatic {
 public:
  AllStatic()  { ShouldNotCallThis(); }
  ~AllStatic() { ShouldNotCallThis(); }
};


//------------------------------Chunk------------------------------------------
// Linked list of raw memory chunks
class Chunk: public CHeapObj {
 protected:
  Chunk*       _next;     // Next Chunk in list
  const size_t _len;      // Size of this Chunk
 public:
  void* operator new(size_t size, size_t length);
  void  operator delete(void* p);
  Chunk(size_t length);

  enum {
    // default sizes; make them slightly smaller than 2**k to guard against
    // buddy-system style malloc implementations
#ifdef _LP64
    slack      = 40,            // [RGV] Not sure if this is right, but make it
                                //       a multiple of 8.
#else
    slack      = 20,            // suspected sizeof(Chunk) + internal malloc headers
#endif

    init_size  =  1*K  - slack, // Size of first chunk
    medium_size= 10*K  - slack, // Size of medium-sized chunk
    size       = 32*K  - slack, // Default size of an Arena chunk (following the first)
    non_pool_size = init_size + 32 // An initial size which is not one of above
  };

  void chop();                  // Chop this chunk
  void next_chop();             // Chop next chunk
  static size_t aligned_overhead_size(void) { return ARENA_ALIGN(sizeof(Chunk)); }

  size_t length() const         { return _len;  }
  Chunk* next() const           { return _next;  }
  void set_next(Chunk* n)       { _next = n;  }
  // Boundaries of data area (possibly unused)
  char* bottom() const          { return ((char*) this) + aligned_overhead_size();  }
  char* top()    const          { return bottom() + _len; }
  bool contains(char* p) const  { return bottom() <= p && p <= top(); }

  // Start the chunk_pool cleaner task
  static void start_chunk_pool_cleaner_task();

  static void clean_chunk_pool();
};

//------------------------------Arena------------------------------------------
// Fast allocation of memory
class Arena: public CHeapObj {
protected:
  friend class ResourceMark;
  friend class HandleMark;
  friend class NoHandleMark;
  Chunk *_first;                // First chunk
  Chunk *_chunk;                // current chunk
  char *_hwm, *_max;            // High water mark and max in current chunk
  void* grow(size_t x);         // Get a new Chunk of at least size x
  NOT_PRODUCT(size_t _size_in_bytes;) // Size of arena (used for memory usage tracing)
  NOT_PRODUCT(static size_t _bytes_allocated;) // total #bytes allocated since start
  friend class AllocStats;
  debug_only(void* malloc(size_t size);)
  debug_only(void* internal_malloc_4(size_t x);)
 public:
  Arena();
  Arena(size_t init_size);
  Arena(Arena *old);
  ~Arena();
  void  destruct_contents();
  char* hwm() const             { return _hwm; }

  // Fast allocate in the arena.  Common case is: pointer test + increment.
  void* Amalloc(size_t x) {
    assert(is_power_of_2(ARENA_AMALLOC_ALIGNMENT) , "should be a power of 2");
    x = ARENA_ALIGN(x);
    debug_only(if (UseMallocOnly) return malloc(x);)
    NOT_PRODUCT(_bytes_allocated += x);
    if (_hwm + x > _max) {
      return grow(x);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }
  // Further assume size is padded out to words
  void *Amalloc_4(size_t x) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
    NOT_PRODUCT(_bytes_allocated += x);
    if (_hwm + x > _max) {
      return grow(x);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }

  // Allocate with 'double' alignment. It is 8 bytes on sparc.
  // In other cases Amalloc_D() should be the same as Amalloc_4().
  void* Amalloc_D(size_t x) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
#if defined(SPARC) && !defined(_LP64)
#define DALIGN_M1 7
    size_t delta = (((size_t)_hwm + DALIGN_M1) & ~DALIGN_M1) - (size_t)_hwm;
    x += delta;
#endif
    NOT_PRODUCT(_bytes_allocated += x);
    if (_hwm + x > _max) {
      return grow(x); // grow() returns a result aligned >= 8 bytes.
    } else {
      char *old = _hwm;
      _hwm += x;
#if defined(SPARC) && !defined(_LP64)
      old += delta; // align to 8-bytes
#endif
      return old;
    }
  }

  // Fast delete in area.  Common case is: NOP (except for storage reclaimed)
  void Afree(void *ptr, size_t size) {
#ifdef ASSERT
    if (ZapResourceArea) memset(ptr, badResourceValue, size); // zap freed memory
    if (UseMallocOnly) return;
#endif
    if (((char*)ptr) + size == _hwm) _hwm = (char*)ptr;
  }

  void *Arealloc( void *old_ptr, size_t old_size, size_t new_size );

  // Move contents of this arena into an empty arena
  Arena *move_contents(Arena *empty_arena);

  // Determine if pointer belongs to this Arena or not.
  bool contains( const void *ptr ) const;

  // Total of all chunks in use (not thread-safe)
  size_t used() const;

  // Total # of bytes used
  size_t size_in_bytes() const         NOT_PRODUCT({  return _size_in_bytes; }) PRODUCT_RETURN0;
  void set_size_in_bytes(size_t size)  NOT_PRODUCT({ _size_in_bytes = size;  }) PRODUCT_RETURN;
  static void free_malloced_objects(Chunk* chunk, char* hwm, char* max, char* hwm2)  PRODUCT_RETURN;
  static void free_all(char** start, char** end)                                     PRODUCT_RETURN;

private:
  // Reset this Arena to empty, access will trigger grow if necessary
  void   reset(void) {
    _first = _chunk = NULL;
    _hwm = _max = NULL;
  }
};

// One of the following macros must be used when allocating
// an array or object from an arena
#define NEW_ARENA_ARRAY(arena, type, size) \
  (type*) (arena)->Amalloc((size) * sizeof(type))

#define REALLOC_ARENA_ARRAY(arena, type, old, old_size, new_size)    \
  (type*) (arena)->Arealloc((char*)(old), (old_size) * sizeof(type), \
                            (new_size) * sizeof(type) )

#define FREE_ARENA_ARRAY(arena, type, old, size) \
  (arena)->Afree((char*)(old), (size) * sizeof(type))

#define NEW_ARENA_OBJ(arena, type) \
  NEW_ARENA_ARRAY(arena, type, 1)


//%note allocation_1
extern char* resource_allocate_bytes(size_t size);
extern char* resource_allocate_bytes(Thread* thread, size_t size);
extern char* resource_reallocate_bytes( char *old, size_t old_size, size_t new_size);
extern void resource_free_bytes( char *old, size_t size );

//----------------------------------------------------------------------
// Base class for objects allocated in the resource area per default.
// Optionally, objects may be allocated on the C heap with
// new(ResourceObj::C_HEAP) Foo(...) or in an Arena with new (&arena)
// ResourceObj's can be allocated within other objects, but don't use
// new or delete (allocation_type is unknown).  If new is used to allocate,
// use delete to deallocate.
class ResourceObj ALLOCATION_SUPER_CLASS_SPEC {
 public:
  enum allocation_type { STACK_OR_EMBEDDED = 0, RESOURCE_AREA, C_HEAP, ARENA, allocation_mask = 0x3 };
  static void set_allocation_type(address res, allocation_type type) NOT_DEBUG_RETURN;
#ifdef ASSERT
 private:
  // When this object is allocated on stack the new() operator is not
  // called but garbage on stack may look like a valid allocation_type.
  // Store negated 'this' pointer when new() is called to distinguish cases.
  uintptr_t _allocation;
 public:
  allocation_type get_allocation_type() const;
  bool allocated_on_stack()    const { return get_allocation_type() == STACK_OR_EMBEDDED; }
  bool allocated_on_res_area() const { return get_allocation_type() == RESOURCE_AREA; }
  bool allocated_on_C_heap()   const { return get_allocation_type() == C_HEAP; }
  bool allocated_on_arena()    const { return get_allocation_type() == ARENA; }
  ResourceObj(); // default construtor
  ResourceObj(const ResourceObj& r); // default copy construtor
  ResourceObj& operator=(const ResourceObj& r); // default copy assignment
  ~ResourceObj();
#endif // ASSERT

 public:
  void* operator new(size_t size, allocation_type type);
  void* operator new(size_t size, Arena *arena) {
      address res = (address)arena->Amalloc(size);
      DEBUG_ONLY(set_allocation_type(res, ARENA);)
      return res;
  }
  void* operator new(size_t size) {
      address res = (address)resource_allocate_bytes(size);
      DEBUG_ONLY(set_allocation_type(res, RESOURCE_AREA);)
      return res;
  }
  void  operator delete(void* p);
};

// One of the following macros must be used when allocating an array
// or object to determine whether it should reside in the C heap on in
// the resource area.

#define NEW_RESOURCE_ARRAY(type, size)\
  (type*) resource_allocate_bytes((size) * sizeof(type))

#define NEW_RESOURCE_ARRAY_IN_THREAD(thread, type, size)\
  (type*) resource_allocate_bytes(thread, (size) * sizeof(type))

#define REALLOC_RESOURCE_ARRAY(type, old, old_size, new_size)\
  (type*) resource_reallocate_bytes((char*)(old), (old_size) * sizeof(type), (new_size) * sizeof(type) )

#define FREE_RESOURCE_ARRAY(type, old, size)\
  resource_free_bytes((char*)(old), (size) * sizeof(type))

#define FREE_FAST(old)\
    /* nop */

#define NEW_RESOURCE_OBJ(type)\
  NEW_RESOURCE_ARRAY(type, 1)

#define NEW_C_HEAP_ARRAY(type, size)\
  (type*) (AllocateHeap((size) * sizeof(type), XSTR(type) " in " __FILE__))

#define REALLOC_C_HEAP_ARRAY(type, old, size)\
  (type*) (ReallocateHeap((char*)old, (size) * sizeof(type), XSTR(type) " in " __FILE__))

#define FREE_C_HEAP_ARRAY(type,old) \
  FreeHeap((char*)(old))

#define NEW_C_HEAP_OBJ(type)\
  NEW_C_HEAP_ARRAY(type, 1)

extern bool warn_new_operator;

// for statistics
#ifndef PRODUCT
class AllocStats : StackObj {
  int    start_mallocs, start_frees;
  size_t start_malloc_bytes, start_res_bytes;
 public:
  AllocStats();

  int    num_mallocs();    // since creation of receiver
  size_t alloc_bytes();
  size_t resource_bytes();
  int    num_frees();
  void   print();
};
#endif


//------------------------------ReallocMark---------------------------------
// Code which uses REALLOC_RESOURCE_ARRAY should check an associated
// ReallocMark, which is declared in the same scope as the reallocated
// pointer.  Any operation that could __potentially__ cause a reallocation
// should check the ReallocMark.
class ReallocMark: public StackObj {
protected:
  NOT_PRODUCT(int _nesting;)

public:
  ReallocMark()   PRODUCT_RETURN;
  void check()    PRODUCT_RETURN;
};

#endif // SHARE_VM_MEMORY_ALLOCATION_HPP
