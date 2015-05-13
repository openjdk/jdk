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

#ifndef SHARE_VM_MEMORY_ALLOCATION_INLINE_HPP
#define SHARE_VM_MEMORY_ALLOCATION_INLINE_HPP

#include "runtime/atomic.inline.hpp"
#include "runtime/os.hpp"
#include "services/memTracker.hpp"

// Explicit C-heap memory management

void trace_heap_malloc(size_t size, const char* name, void *p);
void trace_heap_free(void *p);

#ifndef PRODUCT
// Increments unsigned long value for statistics (not atomic on MP).
inline void inc_stat_counter(volatile julong* dest, julong add_value) {
#if defined(SPARC) || defined(X86)
  // Sparc and X86 have atomic jlong (8 bytes) instructions
  julong value = Atomic::load((volatile jlong*)dest);
  value += add_value;
  Atomic::store((jlong)value, (volatile jlong*)dest);
#else
  // possible word-tearing during load/store
  *dest += add_value;
#endif
}
#endif

// allocate using malloc; will fail if no memory available
inline char* AllocateHeap(size_t size, MEMFLAGS flags,
    const NativeCallStack& stack,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
  char* p = (char*) os::malloc(size, flags, stack);
  #ifdef ASSERT
  if (PrintMallocFree) trace_heap_malloc(size, "AllocateHeap", p);
  #endif
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "AllocateHeap");
  }
  return p;
}

#ifdef __GNUC__
__attribute__((always_inline))
#endif
inline char* AllocateHeap(size_t size, MEMFLAGS flags,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
  return AllocateHeap(size, flags, CURRENT_PC, alloc_failmode);
}

#ifdef __GNUC__
__attribute__((always_inline))
#endif
inline char* ReallocateHeap(char *old, size_t size, MEMFLAGS flag,
    AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM) {
  char* p = (char*) os::realloc(old, size, flag, CURRENT_PC);
  #ifdef ASSERT
  if (PrintMallocFree) trace_heap_malloc(size, "ReallocateHeap", p);
  #endif
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "ReallocateHeap");
  }
  return p;
}

inline void FreeHeap(void* p) {
  #ifdef ASSERT
  if (PrintMallocFree) trace_heap_free(p);
  #endif
  os::free(p);
}


template <MEMFLAGS F> void* CHeapObj<F>::operator new(size_t size,
      const NativeCallStack& stack) throw() {
  void* p = (void*)AllocateHeap(size, F, stack);
#ifdef ASSERT
  if (PrintMallocFree) trace_heap_malloc(size, "CHeapObj-new", p);
#endif
  return p;
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new(size_t size) throw() {
  return CHeapObj<F>::operator new(size, CALLER_PC);
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new (size_t size,
  const std::nothrow_t&  nothrow_constant, const NativeCallStack& stack) throw() {
  void* p = (void*)AllocateHeap(size, F, stack,
      AllocFailStrategy::RETURN_NULL);
#ifdef ASSERT
    if (PrintMallocFree) trace_heap_malloc(size, "CHeapObj-new", p);
#endif
    return p;
  }

template <MEMFLAGS F> void* CHeapObj<F>::operator new (size_t size,
  const std::nothrow_t& nothrow_constant) throw() {
  return CHeapObj<F>::operator new(size, nothrow_constant, CALLER_PC);
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new [](size_t size,
      const NativeCallStack& stack) throw() {
  return CHeapObj<F>::operator new(size, stack);
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new [](size_t size)
  throw() {
  return CHeapObj<F>::operator new(size, CALLER_PC);
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new [](size_t size,
  const std::nothrow_t&  nothrow_constant, const NativeCallStack& stack) throw() {
  return CHeapObj<F>::operator new(size, nothrow_constant, stack);
}

template <MEMFLAGS F> void* CHeapObj<F>::operator new [](size_t size,
  const std::nothrow_t& nothrow_constant) throw() {
  return CHeapObj<F>::operator new(size, nothrow_constant, CALLER_PC);
}

template <MEMFLAGS F> void CHeapObj<F>::operator delete(void* p){
    FreeHeap(p);
}

template <MEMFLAGS F> void CHeapObj<F>::operator delete [](void* p){
    FreeHeap(p);
}

template <class E, MEMFLAGS F>
char* ArrayAllocator<E, F>::allocate_inner(size_t &size, bool &use_malloc) {
  char* addr = NULL;

  if (use_malloc) {
    addr = AllocateHeap(size, F);
    if (addr == NULL && size >= (size_t)os::vm_allocation_granularity()) {
      // malloc failed let's try with mmap instead
      use_malloc = false;
    } else {
      return addr;
    }
  }

  int alignment = os::vm_allocation_granularity();
  size = align_size_up(size, alignment);

  addr = os::reserve_memory(size, NULL, alignment, F);
  if (addr == NULL) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Allocator (reserve)");
  }

  os::commit_memory_or_exit(addr, size, !ExecMem, "Allocator (commit)");
  return addr;
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::allocate(size_t length) {
  assert(_addr == NULL, "Already in use");

  _size = sizeof(E) * length;
  _use_malloc = should_use_malloc(_size);
  _addr = allocate_inner(_size, _use_malloc);

  return (E*)_addr;
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::reallocate(size_t new_length) {
  size_t new_size = sizeof(E) * new_length;
  bool use_malloc = should_use_malloc(new_size);
  char* new_addr = allocate_inner(new_size, use_malloc);

  memcpy(new_addr, _addr, MIN2(new_size, _size));

  free();
  _size = new_size;
  _use_malloc = use_malloc;
  _addr = new_addr;
  return (E*)new_addr;
}

template<class E, MEMFLAGS F>
void ArrayAllocator<E, F>::free() {
  if (_addr != NULL) {
    if (_use_malloc) {
      FreeHeap(_addr);
    } else {
      os::release_memory(_addr, _size);
    }
    _addr = NULL;
  }
}

#endif // SHARE_VM_MEMORY_ALLOCATION_INLINE_HPP
