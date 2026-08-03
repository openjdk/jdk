/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_MEMORYRESERVER_HPP
#define SHARE_MEMORY_MEMORYRESERVER_HPP

#include "memory/allStatic.hpp"
#include "memory/reservedSpace.hpp"
#include "nmt/memTag.hpp"
#include "utilities/globalDefinitions.hpp"

class MemoryReserver : AllStatic {
  static ReservedSpace reserve_memory(char* requested_address,
                                      size_t size,
                                      size_t alignment,
                                      size_t page_size,
                                      bool exec,
                                      MemTag mem_tag);

  static ReservedSpace reserve_memory_special(char* requested_address,
                                              size_t size,
                                              size_t alignment,
                                              size_t page_size,
                                              bool exec);

public:
  // Final destination
  static ReservedSpace reserve(char* requested_address,
                               size_t size,
                               size_t alignment,
                               size_t page_size,
                               bool executable,
                               MemTag mem_tag);

  // Convenience overloads

  static ReservedSpace reserve(char* requested_address,
                               size_t size,
                               size_t alignment,
                               size_t page_size,
                               MemTag mem_tag);

  static ReservedSpace reserve(size_t size,
                               size_t alignment,
                               size_t page_size,
                               MemTag mem_tag);

  static ReservedSpace reserve(size_t size,
                               MemTag mem_tag);

  // Release reserved memory
  static void release(const ReservedSpace& reserved);
};

class CodeMemoryReserver : AllStatic {
public:
  static ReservedSpace reserve(size_t size,
                              size_t alignment,
                              size_t page_size);
};

class FileMappedMemoryReserver : AllStatic {
public:
  static ReservedSpace reserve(char* requested_address,
                               size_t size,
                               size_t alignment,
                               int fd,
                               MemTag mem_tag);
};

class HeapReserver : AllStatic {
  class Instance {
    const int _fd;

    NONCOPYABLE(Instance);

    ReservedSpace reserve_memory(size_t size,
                                 size_t alignment,
                                 size_t page_size,
                                 char* requested_address = nullptr);

    void release(const ReservedSpace& reserved);

    // CompressedOops support
#ifdef _LP64

    ReservedSpace try_reserve_memory(size_t size,
                                     size_t alignment,
                                     size_t page_size,
                                     char* requested_address);

    ReservedSpace try_reserve_range(char *highest_start,
                                    char *lowest_start,
                                    size_t attach_point_alignment,
                                    char *aligned_heap_base_min_address,
                                    char *upper_bound,
                                    size_t size,
                                    size_t alignment,
                                    size_t page_size);

    ReservedHeapSpace reserve_compressed_oops_heap(size_t size,
                                                   size_t alignment,
                                                   size_t page_size);

#endif // _LP64

    ReservedHeapSpace reserve_uncompressed_oops_heap(size_t size,
                                                     size_t alignment,
                                                     size_t page_size);

  public:
    Instance(const char* heap_allocation_directory);
    ~Instance();

    ReservedHeapSpace reserve_heap(size_t size,
                                   size_t alignment,
                                   size_t page_size);
  }; // Instance

public:
  static ReservedHeapSpace reserve(size_t size,
                                   size_t alignment,
                                   size_t page_size,
                                   const char* heap_allocation_directory);
};

#endif // SHARE_MEMORY_MEMORYRESERVER_HPP
