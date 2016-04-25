/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif

#include <new>

#define ARENA_ALIGN_M1 (((size_t)(ARENA_AMALLOC_ALIGNMENT)) - 1)
#define ARENA_ALIGN_MASK (~((size_t)ARENA_ALIGN_M1))
#define ARENA_ALIGN(x) ((((size_t)(x)) + ARENA_ALIGN_M1) & ARENA_ALIGN_MASK)

class AllocFailStrategy {
public:
  enum AllocFailEnum { EXIT_OOM, RETURN_NULL };
};
typedef AllocFailStrategy::AllocFailEnum AllocFailType;

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
// For classes in Metaspace (class data)
// - MetaspaceObj
//
// The printable subclasses are used for debugging and define virtual
// member functions for printing. Classes that avoid allocating the
// vtbl entries in the objects should therefore not be the printable
// subclasses.
//
// The following macros and function should be used to allocate memory
// directly in the resource area or in the C-heap, The _OBJ variants
// of the NEW/FREE_C_HEAP macros are used for alloc/dealloc simple
// objects which are not inherited from CHeapObj, note constructor and
// destructor are not called. The preferable way to allocate objects
// is using the new operator.
//
// WARNING: The array variant must only be used for a homogenous array
// where all objects are of the exact type specified. If subtypes are
// stored in the array then must pay attention to calling destructors
// at needed.
//
//   NEW_RESOURCE_ARRAY(type, size)
//   NEW_RESOURCE_OBJ(type)
//   NEW_C_HEAP_ARRAY(type, size)
//   NEW_C_HEAP_OBJ(type, memflags)
//   FREE_C_HEAP_ARRAY(type, old)
//   FREE_C_HEAP_OBJ(objname, type, memflags)
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


/*
 * Memory types
 */
enum MemoryType {
  // Memory type by sub systems. It occupies lower byte.
  mtJavaHeap          = 0x00,  // Java heap
  mtClass             = 0x01,  // memory class for Java classes
  mtThread            = 0x02,  // memory for thread objects
  mtThreadStack       = 0x03,
  mtCode              = 0x04,  // memory for generated code
  mtGC                = 0x05,  // memory for GC
  mtCompiler          = 0x06,  // memory for compiler
  mtInternal          = 0x07,  // memory used by VM, but does not belong to
                                 // any of above categories, and not used for
                                 // native memory tracking
  mtOther             = 0x08,  // memory not used by VM
  mtSymbol            = 0x09,  // symbol
  mtNMT               = 0x0A,  // memory used by native memory tracking
  mtClassShared       = 0x0B,  // class data sharing
  mtChunk             = 0x0C,  // chunk that holds content of arenas
  mtTest              = 0x0D,  // Test type for verifying NMT
  mtTracing           = 0x0E,  // memory used for Tracing
  mtLogging           = 0x0F,  // memory for logging
  mtArguments         = 0x10,  // memory for argument processing
  mtNone              = 0x11,  // undefined
  mt_number_of_types  = 0x12   // number of memory types (mtDontTrack
                                 // is not included as validate type)
};

typedef MemoryType MEMFLAGS;


#if INCLUDE_NMT

extern bool NMT_track_callsite;

#else

const bool NMT_track_callsite = false;

#endif // INCLUDE_NMT

class NativeCallStack;


template <MEMFLAGS F> class CHeapObj ALLOCATION_SUPER_CLASS_SPEC {
 public:
  NOINLINE void* operator new(size_t size, const NativeCallStack& stack) throw();
  NOINLINE void* operator new(size_t size) throw();
  NOINLINE void* operator new (size_t size, const std::nothrow_t&  nothrow_constant,
                               const NativeCallStack& stack) throw();
  NOINLINE void* operator new (size_t size, const std::nothrow_t&  nothrow_constant)
                               throw();
  NOINLINE void* operator new [](size_t size, const NativeCallStack& stack) throw();
  NOINLINE void* operator new [](size_t size) throw();
  NOINLINE void* operator new [](size_t size, const std::nothrow_t&  nothrow_constant,
                               const NativeCallStack& stack) throw();
  NOINLINE void* operator new [](size_t size, const std::nothrow_t&  nothrow_constant)
                               throw();
  void  operator delete(void* p);
  void  operator delete [] (void* p);
};

// Base class for objects allocated on the stack only.
// Calling new or delete will result in fatal error.

class StackObj ALLOCATION_SUPER_CLASS_SPEC {
 private:
  void* operator new(size_t size) throw();
  void* operator new [](size_t size) throw();
#ifdef __IBMCPP__
 public:
#endif
  void  operator delete(void* p);
  void  operator delete [](void* p);
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
 private:
  void* operator new(size_t size) throw();
  void  operator delete(void* p);
  void* operator new [](size_t size) throw();
  void  operator delete [](void* p);
};


// Base class for objects stored in Metaspace.
// Calling delete will result in fatal error.
//
// Do not inherit from something with a vptr because this class does
// not introduce one.  This class is used to allocate both shared read-only
// and shared read-write classes.
//

class ClassLoaderData;

class MetaspaceObj {
 public:
  bool is_metaspace_object() const;
  bool is_shared() const;
  void print_address_on(outputStream* st) const;  // nonvirtual address printing

#define METASPACE_OBJ_TYPES_DO(f) \
  f(Unknown) \
  f(Class) \
  f(Symbol) \
  f(TypeArrayU1) \
  f(TypeArrayU2) \
  f(TypeArrayU4) \
  f(TypeArrayU8) \
  f(TypeArrayOther) \
  f(Method) \
  f(ConstMethod) \
  f(MethodData) \
  f(ConstantPool) \
  f(ConstantPoolCache) \
  f(Annotation) \
  f(MethodCounters) \
  f(Deallocated)

#define METASPACE_OBJ_TYPE_DECLARE(name) name ## Type,
#define METASPACE_OBJ_TYPE_NAME_CASE(name) case name ## Type: return #name;

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    METASPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_DECLARE)
    _number_of_types
  };

  static const char * type_name(Type type) {
    switch(type) {
    METASPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return NULL;
    }
  }

  static MetaspaceObj::Type array_type(size_t elem_size) {
    switch (elem_size) {
    case 1: return TypeArrayU1Type;
    case 2: return TypeArrayU2Type;
    case 4: return TypeArrayU4Type;
    case 8: return TypeArrayU8Type;
    default:
      return TypeArrayOtherType;
    }
  }

  void* operator new(size_t size, ClassLoaderData* loader_data,
                     size_t word_size, bool read_only,
                     Type type, Thread* thread) throw();
                     // can't use TRAPS from this header file.
  void operator delete(void* p) { ShouldNotCallThis(); }
};

// Base class for classes that constitute name spaces.

class AllStatic {
 public:
  AllStatic()  { ShouldNotCallThis(); }
  ~AllStatic() { ShouldNotCallThis(); }
};


//------------------------------Chunk------------------------------------------
// Linked list of raw memory chunks
class Chunk: CHeapObj<mtChunk> {
  friend class VMStructs;

 protected:
  Chunk*       _next;     // Next Chunk in list
  const size_t _len;      // Size of this Chunk
 public:
  void* operator new(size_t size, AllocFailType alloc_failmode, size_t length) throw();
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

    tiny_size  =  256  - slack, // Size of first chunk (tiny)
    init_size  =  1*K  - slack, // Size of first chunk (normal aka small)
    medium_size= 10*K  - slack, // Size of medium-sized chunk
    size       = 32*K  - slack, // Default size of an Arena chunk (following the first)
    non_pool_size = init_size + 32 // An initial size which is not one of above
  };

  void chop();                  // Chop this chunk
  void next_chop();             // Chop next chunk
  static size_t aligned_overhead_size(void) { return ARENA_ALIGN(sizeof(Chunk)); }
  static size_t aligned_overhead_size(size_t byte_size) { return ARENA_ALIGN(byte_size); }

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
class Arena : public CHeapObj<mtNone> {
protected:
  friend class ResourceMark;
  friend class HandleMark;
  friend class NoHandleMark;
  friend class VMStructs;

  MEMFLAGS    _flags;           // Memory tracking flags

  Chunk *_first;                // First chunk
  Chunk *_chunk;                // current chunk
  char *_hwm, *_max;            // High water mark and max in current chunk
  // Get a new Chunk of at least size x
  void* grow(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
  size_t _size_in_bytes;        // Size of arena (used for native memory tracking)

  NOT_PRODUCT(static julong _bytes_allocated;) // total #bytes allocated since start
  friend class AllocStats;
  debug_only(void* malloc(size_t size);)
  debug_only(void* internal_malloc_4(size_t x);)
  NOT_PRODUCT(void inc_bytes_allocated(size_t x);)

  void signal_out_of_memory(size_t request, const char* whence) const;

  bool check_for_overflow(size_t request, const char* whence,
      AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) const {
    if (UINTPTR_MAX - request < (uintptr_t)_hwm) {
      if (alloc_failmode == AllocFailStrategy::RETURN_NULL) {
        return false;
      }
      signal_out_of_memory(request, whence);
    }
    return true;
 }

 public:
  Arena(MEMFLAGS memflag);
  Arena(MEMFLAGS memflag, size_t init_size);
  ~Arena();
  void  destruct_contents();
  char* hwm() const             { return _hwm; }

  // new operators
  void* operator new (size_t size) throw();
  void* operator new (size_t size, const std::nothrow_t& nothrow_constant) throw();

  // dynamic memory type tagging
  void* operator new(size_t size, MEMFLAGS flags) throw();
  void* operator new(size_t size, const std::nothrow_t& nothrow_constant, MEMFLAGS flags) throw();
  void  operator delete(void* p);

  // Fast allocate in the arena.  Common case is: pointer test + increment.
  void* Amalloc(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert(is_power_of_2(ARENA_AMALLOC_ALIGNMENT) , "should be a power of 2");
    x = ARENA_ALIGN(x);
    debug_only(if (UseMallocOnly) return malloc(x);)
    if (!check_for_overflow(x, "Arena::Amalloc", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }
  // Further assume size is padded out to words
  void *Amalloc_4(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
    if (!check_for_overflow(x, "Arena::Amalloc_4", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode);
    } else {
      char *old = _hwm;
      _hwm += x;
      return old;
    }
  }

  // Allocate with 'double' alignment. It is 8 bytes on sparc.
  // In other cases Amalloc_D() should be the same as Amalloc_4().
  void* Amalloc_D(size_t x, AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
    assert( (x&(sizeof(char*)-1)) == 0, "misaligned size" );
    debug_only(if (UseMallocOnly) return malloc(x);)
#if defined(SPARC) && !defined(_LP64)
#define DALIGN_M1 7
    size_t delta = (((size_t)_hwm + DALIGN_M1) & ~DALIGN_M1) - (size_t)_hwm;
    x += delta;
#endif
    if (!check_for_overflow(x, "Arena::Amalloc_D", alloc_failmode))
      return NULL;
    NOT_PRODUCT(inc_bytes_allocated(x);)
    if (_hwm + x > _max) {
      return grow(x, alloc_failmode); // grow() returns a result aligned >= 8 bytes.
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

  void *Arealloc( void *old_ptr, size_t old_size, size_t new_size,
      AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);

  // Move contents of this arena into an empty arena
  Arena *move_contents(Arena *empty_arena);

  // Determine if pointer belongs to this Arena or not.
  bool contains( const void *ptr ) const;

  // Total of all chunks in use (not thread-safe)
  size_t used() const;

  // Total # of bytes used
  size_t size_in_bytes() const         {  return _size_in_bytes; };
  void set_size_in_bytes(size_t size);

  static void free_malloced_objects(Chunk* chunk, char* hwm, char* max, char* hwm2)  PRODUCT_RETURN;
  static void free_all(char** start, char** end)                                     PRODUCT_RETURN;

private:
  // Reset this Arena to empty, access will trigger grow if necessary
  void   reset(void) {
    _first = _chunk = NULL;
    _hwm = _max = NULL;
    set_size_in_bytes(0);
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
extern char* resource_allocate_bytes(size_t size,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
extern char* resource_allocate_bytes(Thread* thread, size_t size,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
extern char* resource_reallocate_bytes( char *old, size_t old_size, size_t new_size,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM);
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
  // Use second array's element for verification value to distinguish garbage.
  uintptr_t _allocation_t[2];
  bool is_type_set() const;
 public:
  allocation_type get_allocation_type() const;
  bool allocated_on_stack()    const { return get_allocation_type() == STACK_OR_EMBEDDED; }
  bool allocated_on_res_area() const { return get_allocation_type() == RESOURCE_AREA; }
  bool allocated_on_C_heap()   const { return get_allocation_type() == C_HEAP; }
  bool allocated_on_arena()    const { return get_allocation_type() == ARENA; }
  ResourceObj(); // default constructor
  ResourceObj(const ResourceObj& r); // default copy constructor
  ResourceObj& operator=(const ResourceObj& r); // default copy assignment
  ~ResourceObj();
#endif // ASSERT

 public:
  void* operator new(size_t size, allocation_type type, MEMFLAGS flags) throw();
  void* operator new [](size_t size, allocation_type type, MEMFLAGS flags) throw();
  void* operator new(size_t size, const std::nothrow_t&  nothrow_constant,
      allocation_type type, MEMFLAGS flags) throw();
  void* operator new [](size_t size, const std::nothrow_t&  nothrow_constant,
      allocation_type type, MEMFLAGS flags) throw();

  void* operator new(size_t size, Arena *arena) throw() {
      address res = (address)arena->Amalloc(size);
      DEBUG_ONLY(set_allocation_type(res, ARENA);)
      return res;
  }

  void* operator new [](size_t size, Arena *arena) throw() {
      address res = (address)arena->Amalloc(size);
      DEBUG_ONLY(set_allocation_type(res, ARENA);)
      return res;
  }

  void* operator new(size_t size) throw() {
      address res = (address)resource_allocate_bytes(size);
      DEBUG_ONLY(set_allocation_type(res, RESOURCE_AREA);)
      return res;
  }

  void* operator new(size_t size, const std::nothrow_t& nothrow_constant) throw() {
      address res = (address)resource_allocate_bytes(size, AllocFailStrategy::RETURN_NULL);
      DEBUG_ONLY(if (res != NULL) set_allocation_type(res, RESOURCE_AREA);)
      return res;
  }

  void* operator new [](size_t size) throw() {
      address res = (address)resource_allocate_bytes(size);
      DEBUG_ONLY(set_allocation_type(res, RESOURCE_AREA);)
      return res;
  }

  void* operator new [](size_t size, const std::nothrow_t& nothrow_constant) throw() {
      address res = (address)resource_allocate_bytes(size, AllocFailStrategy::RETURN_NULL);
      DEBUG_ONLY(if (res != NULL) set_allocation_type(res, RESOURCE_AREA);)
      return res;
  }

  void  operator delete(void* p);
  void  operator delete [](void* p);
};

// One of the following macros must be used when allocating an array
// or object to determine whether it should reside in the C heap on in
// the resource area.

#define NEW_RESOURCE_ARRAY(type, size)\
  (type*) resource_allocate_bytes((size) * sizeof(type))

#define NEW_RESOURCE_ARRAY_RETURN_NULL(type, size)\
  (type*) resource_allocate_bytes((size) * sizeof(type), AllocFailStrategy::RETURN_NULL)

#define NEW_RESOURCE_ARRAY_IN_THREAD(thread, type, size)\
  (type*) resource_allocate_bytes(thread, (size) * sizeof(type))

#define NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(thread, type, size)\
  (type*) resource_allocate_bytes(thread, (size) * sizeof(type), AllocFailStrategy::RETURN_NULL)

#define REALLOC_RESOURCE_ARRAY(type, old, old_size, new_size)\
  (type*) resource_reallocate_bytes((char*)(old), (old_size) * sizeof(type), (new_size) * sizeof(type))

#define REALLOC_RESOURCE_ARRAY_RETURN_NULL(type, old, old_size, new_size)\
  (type*) resource_reallocate_bytes((char*)(old), (old_size) * sizeof(type),\
                                    (new_size) * sizeof(type), AllocFailStrategy::RETURN_NULL)

#define FREE_RESOURCE_ARRAY(type, old, size)\
  resource_free_bytes((char*)(old), (size) * sizeof(type))

#define FREE_FAST(old)\
    /* nop */

#define NEW_RESOURCE_OBJ(type)\
  NEW_RESOURCE_ARRAY(type, 1)

#define NEW_RESOURCE_OBJ_RETURN_NULL(type)\
  NEW_RESOURCE_ARRAY_RETURN_NULL(type, 1)

#define NEW_C_HEAP_ARRAY3(type, size, memflags, pc, allocfail)\
  (type*) AllocateHeap((size) * sizeof(type), memflags, pc, allocfail)

#define NEW_C_HEAP_ARRAY2(type, size, memflags, pc)\
  (type*) (AllocateHeap((size) * sizeof(type), memflags, pc))

#define NEW_C_HEAP_ARRAY(type, size, memflags)\
  (type*) (AllocateHeap((size) * sizeof(type), memflags))

#define NEW_C_HEAP_ARRAY2_RETURN_NULL(type, size, memflags, pc)\
  NEW_C_HEAP_ARRAY3(type, (size), memflags, pc, AllocFailStrategy::RETURN_NULL)

#define NEW_C_HEAP_ARRAY_RETURN_NULL(type, size, memflags)\
  NEW_C_HEAP_ARRAY3(type, (size), memflags, CURRENT_PC, AllocFailStrategy::RETURN_NULL)

#define REALLOC_C_HEAP_ARRAY(type, old, size, memflags)\
  (type*) (ReallocateHeap((char*)(old), (size) * sizeof(type), memflags))

#define REALLOC_C_HEAP_ARRAY_RETURN_NULL(type, old, size, memflags)\
  (type*) (ReallocateHeap((char*)(old), (size) * sizeof(type), memflags, AllocFailStrategy::RETURN_NULL))

#define FREE_C_HEAP_ARRAY(type, old) \
  FreeHeap((char*)(old))

// allocate type in heap without calling ctor
#define NEW_C_HEAP_OBJ(type, memflags)\
  NEW_C_HEAP_ARRAY(type, 1, memflags)

#define NEW_C_HEAP_OBJ_RETURN_NULL(type, memflags)\
  NEW_C_HEAP_ARRAY_RETURN_NULL(type, 1, memflags)

// deallocate obj of type in heap without calling dtor
#define FREE_C_HEAP_OBJ(objname)\
  FreeHeap((char*)objname);

// for statistics
#ifndef PRODUCT
class AllocStats : StackObj {
  julong start_mallocs, start_frees;
  julong start_malloc_bytes, start_mfree_bytes, start_res_bytes;
 public:
  AllocStats();

  julong num_mallocs();    // since creation of receiver
  julong alloc_bytes();
  julong num_frees();
  julong free_bytes();
  julong resource_bytes();
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

// Helper class to allocate arrays that may become large.
// Uses the OS malloc for allocations smaller than ArrayAllocatorMallocLimit
// and uses mapped memory for larger allocations.
// Most OS mallocs do something similar but Solaris malloc does not revert
// to mapped memory for large allocations. By default ArrayAllocatorMallocLimit
// is set so that we always use malloc except for Solaris where we set the
// limit to get mapped memory.
template <class E, MEMFLAGS F>
class ArrayAllocator : public AllStatic {
 private:
  static bool should_use_malloc(size_t length);

  static E* allocate_malloc(size_t length);
  static E* allocate_mmap(size_t length);

  static void free_malloc(E* addr, size_t length);
  static void free_mmap(E* addr, size_t length);

 public:
  static E* allocate(size_t length);
  static E* reallocate(E* old_addr, size_t old_length, size_t new_length);
  static void free(E* addr, size_t length);
};

// Uses mmaped memory for all allocations. All allocations are initially
// zero-filled. No pre-touching.
template <class E, MEMFLAGS F>
class MmapArrayAllocator : public AllStatic {
 private:
  static size_t size_for(size_t length);

 public:
  static E* allocate(size_t length);
  static void free(E* addr, size_t length);
};

// Uses malloc:ed memory for all allocations.
template <class E, MEMFLAGS F>
class MallocArrayAllocator : public AllStatic {
 public:
  static size_t size_for(size_t length);

  static E* allocate(size_t length);
  static void free(E* addr, size_t length);
};

#endif // SHARE_VM_MEMORY_ALLOCATION_HPP
