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
size_t ArrayAllocator<E, F>::size_for_malloc(size_t length) {
  return length * sizeof(E);
}

template <class E, MEMFLAGS F>
size_t ArrayAllocator<E, F>::size_for_mmap(size_t length) {
  size_t size = length * sizeof(E);
  int alignment = os::vm_allocation_granularity();
  return align_size_up(size, alignment);
}

template <class E, MEMFLAGS F>
bool ArrayAllocator<E, F>::should_use_malloc(size_t length) {
  return size_for_malloc(length) < ArrayAllocatorMallocLimit;
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::allocate_malloc(size_t length) {
  return (E*)AllocateHeap(size_for_malloc(length), F);
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::allocate_mmap(size_t length) {
  size_t size = size_for_mmap(length);
  int alignment = os::vm_allocation_granularity();

  char* addr = os::reserve_memory(size, NULL, alignment, F);
  if (addr == NULL) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Allocator (reserve)");
  }

  os::commit_memory_or_exit(addr, size, !ExecMem, "Allocator (commit)");

  return (E*)addr;
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::allocate(size_t length) {
  if (should_use_malloc(length)) {
    return allocate_malloc(length);
  }

  return allocate_mmap(length);
}

template <class E, MEMFLAGS F>
E* ArrayAllocator<E, F>::reallocate(E* old_addr, size_t old_length, size_t new_length) {
  E* new_addr = (new_length > 0)
      ? allocate(new_length)
      : NULL;

  if (new_addr != NULL && old_addr != NULL) {
    memcpy(new_addr, old_addr, MIN2(old_length, new_length) * sizeof(E));
  }

  if (old_addr != NULL) {
    free(old_addr, old_length);
  }

  return new_addr;
}

template<class E, MEMFLAGS F>
void ArrayAllocator<E, F>::free_malloc(E* addr, size_t /*length*/) {
  FreeHeap(addr);
}

template<class E, MEMFLAGS F>
void ArrayAllocator<E, F>::free_mmap(E* addr, size_t length) {
  bool result = os::release_memory((char*)addr, size_for_mmap(length));
  assert(result, "Failed to release memory");
}

template<class E, MEMFLAGS F>
void ArrayAllocator<E, F>::free(E* addr, size_t length) {
  if (addr != NULL) {
    if (should_use_malloc(length)) {
      free_malloc(addr, length);
    } else {
      free_mmap(addr, length);
    }
  }
}

#endif // SHARE_VM_MEMORY_ALLOCATION_INLINE_HPP
