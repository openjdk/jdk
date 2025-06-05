/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ALLOCATION_INLINE_HPP
#define SHARE_MEMORY_ALLOCATION_INLINE_HPP

#include "memory/allocation.hpp"

#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

// Explicit C-heap memory management

#ifndef PRODUCT
// Increments unsigned long value for statistics (not atomic on MP, but avoids word-tearing on 32 bit).
inline void inc_stat_counter(volatile julong* dest, julong add_value) {
#ifdef _LP64
  *dest += add_value;
#else
  julong value = Atomic::load(dest);
  Atomic::store(dest, value + add_value);
#endif
}
#endif

template <class E>
size_t MmapArrayAllocator<E>::size_for(size_t length) {
  size_t size = length * sizeof(E);
  size_t alignment = os::vm_allocation_granularity();
  return align_up(size, alignment);
}

template <class E>
E* MmapArrayAllocator<E>::allocate_or_null(size_t length, MemTag mem_tag) {
  size_t size = size_for(length);

  char* addr = os::reserve_memory(size, mem_tag);
  if (addr == nullptr) {
    return nullptr;
  }

  if (os::commit_memory(addr, size, !ExecMem)) {
    return (E*)addr;
  } else {
    os::release_memory(addr, size);
    return nullptr;
  }
}

template <class E>
E* MmapArrayAllocator<E>::allocate(size_t length, MemTag mem_tag) {
  size_t size = size_for(length);

  char* addr = os::reserve_memory(size, mem_tag);
  if (addr == nullptr) {
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "Allocator (reserve)");
  }

  os::commit_memory_or_exit(addr, size, !ExecMem, "Allocator (commit)");

  return (E*)addr;
}

template <class E>
void MmapArrayAllocator<E>::free(E* addr, size_t length) {
  bool result = os::release_memory((char*)addr, size_for(length));
  assert(result, "Failed to release memory");
}

template <class E>
size_t MallocArrayAllocator<E>::size_for(size_t length) {
  return length * sizeof(E);
}

template <class E>
E* MallocArrayAllocator<E>::allocate(size_t length, MemTag mem_tag) {
  return (E*)AllocateHeap(size_for(length), mem_tag);
}

template <class E>
E* MallocArrayAllocator<E>::reallocate(E* addr, size_t new_length, MemTag mem_tag) {
  return (E*)ReallocateHeap((char*)addr, size_for(new_length), mem_tag);
}

template <class E>
void MallocArrayAllocator<E>::free(E* addr) {
  FreeHeap(addr);
}

#endif // SHARE_MEMORY_ALLOCATION_INLINE_HPP
