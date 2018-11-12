/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"

#ifdef LINUX

#include <sys/mman.h>

#include "runtime/os.hpp"
#include "unittest.hpp"

namespace {
  static void small_page_write(void* addr, size_t size) {
    size_t page_size = os::vm_page_size();

    char* end = (char*)addr + size;
    for (char* p = (char*)addr; p < end; p += page_size) {
      *p = 1;
    }
  }

  class HugeTlbfsMemory : private ::os::Linux {
    char* const _ptr;
    const size_t _size;
   public:
    static char* reserve_memory_special_huge_tlbfs_only(size_t bytes, char* req_addr, bool exec) {
      return os::Linux::reserve_memory_special_huge_tlbfs_only(bytes, req_addr, exec);
    }
    static char* reserve_memory_special_huge_tlbfs_mixed(size_t bytes, size_t alignment, char* req_addr, bool exec) {
      return os::Linux::reserve_memory_special_huge_tlbfs_mixed(bytes, alignment, req_addr, exec);
    }
    HugeTlbfsMemory(char* const ptr, size_t size) : _ptr(ptr), _size(size) { }
    ~HugeTlbfsMemory() {
      if (_ptr != NULL) {
        os::Linux::release_memory_special_huge_tlbfs(_ptr, _size);
      }
    }
  };

  class ShmMemory : private ::os::Linux {
    char* const _ptr;
    const size_t _size;
   public:
    static char* reserve_memory_special_shm(size_t bytes, size_t alignment, char* req_addr, bool exec) {
      return os::Linux::reserve_memory_special_shm(bytes, alignment, req_addr, exec);
    }
    ShmMemory(char* const ptr, size_t size) : _ptr(ptr), _size(size) { }
    ~ShmMemory() {
      os::Linux::release_memory_special_shm(_ptr, _size);
    }
  };

  // have to use these functions, as gtest's _PRED macros don't like is_aligned
  // nor (is_aligned<size_t, size_t>)
  static bool is_size_aligned(size_t size, size_t alignment) {
    return is_aligned(size, alignment);
  }
  static bool is_ptr_aligned(char* ptr, size_t alignment) {
    return is_aligned(ptr, alignment);
  }

  static void test_reserve_memory_special_shm(size_t size, size_t alignment) {
    ASSERT_TRUE(UseSHM) << "must be used only when UseSHM is true";
    char* addr = ShmMemory::reserve_memory_special_shm(size, alignment, NULL, false);
    if (addr != NULL) {
      ShmMemory mr(addr, size);
      EXPECT_PRED2(is_ptr_aligned, addr, alignment);
      EXPECT_PRED2(is_ptr_aligned, addr, os::large_page_size());

      small_page_write(addr, size);
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_only) {
  if (!UseHugeTLBFS) {
    return;
  }
  size_t lp = os::large_page_size();

  for (size_t size = lp; size <= lp * 10; size += lp) {
    char* addr = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs_only(size, NULL, false);

    if (addr != NULL) {
      HugeTlbfsMemory mr(addr, size);
      small_page_write(addr, size);
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_mixed_without_addr) {
  if (!UseHugeTLBFS) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);
  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs_mixed(size, alignment, NULL, false);
      if (p != NULL) {
        HugeTlbfsMemory mr(p, size);
        EXPECT_PRED2(is_ptr_aligned, p, alignment) << " size = " << size;
        small_page_write(p, size);
      }
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_mixed_with_good_req_addr) {
  if (!UseHugeTLBFS) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);

  // Pre-allocate an area as large as the largest allocation
  // and aligned to the largest alignment we will be testing.
  const size_t mapping_size = sizes[num_sizes - 1] * 2;
  char* const mapping = (char*) ::mmap(NULL, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
  ASSERT_TRUE(mapping != NULL) << " mmap failed, mapping_size = " << mapping_size;
  // Unmap the mapping, it will serve as a value for a "good" req_addr
  ::munmap(mapping, mapping_size);

  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      char* const req_addr = align_up(mapping, alignment);
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs_mixed(size, alignment, req_addr, false);
      if (p != NULL) {
        HugeTlbfsMemory mr(p, size);
        ASSERT_EQ(req_addr, p) << " size = " << size << ", alignment = " << alignment;
        small_page_write(p, size);
      }
    }
  }
}


TEST_VM(os_linux, reserve_memory_special_huge_tlbfs_mixed_with_bad_req_addr) {
  if (!UseHugeTLBFS) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  // sizes to test
  const size_t sizes[] = {
    lp, lp + ag, lp + lp / 2, lp * 2,
    lp * 2 + ag, lp * 2 - ag, lp * 2 + lp / 2,
    lp * 10, lp * 10 + lp / 2
  };
  const int num_sizes = sizeof(sizes) / sizeof(size_t);

  // Pre-allocate an area as large as the largest allocation
  // and aligned to the largest alignment we will be testing.
  const size_t mapping_size = sizes[num_sizes - 1] * 2;
  char* const mapping = (char*) ::mmap(NULL, mapping_size,
      PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,
      -1, 0);
  ASSERT_TRUE(mapping != NULL) << " mmap failed, mapping_size = " << mapping_size;
  // Leave the mapping intact, it will server as "bad" req_addr

  class MappingHolder {
    char* const _mapping;
    size_t _size;
   public:
    MappingHolder(char* mapping, size_t size) : _mapping(mapping), _size(size) { }
    ~MappingHolder() {
      ::munmap(_mapping, _size);
    }
  } holder(mapping, mapping_size);

  for (int i = 0; i < num_sizes; i++) {
    const size_t size = sizes[i];
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      char* const req_addr = align_up(mapping, alignment);
      char* p = HugeTlbfsMemory::reserve_memory_special_huge_tlbfs_mixed(size, alignment, req_addr, false);
      HugeTlbfsMemory mr(p, size);
      // as the area around req_addr contains already existing mappings, the API should always
      // return NULL (as per contract, it cannot return another address)
      EXPECT_TRUE(p == NULL) << " size = " << size
                             << ", alignment = " << alignment
                             << ", req_addr = " << req_addr
                             << ", p = " << p;
    }
  }
}

TEST_VM(os_linux, reserve_memory_special_shm) {
  if (!UseSHM) {
    return;
  }
  size_t lp = os::large_page_size();
  size_t ag = os::vm_allocation_granularity();

  for (size_t size = ag; size < lp * 3; size += ag) {
    for (size_t alignment = ag; is_size_aligned(size, alignment); alignment *= 2) {
      EXPECT_NO_FATAL_FAILURE(test_reserve_memory_special_shm(size, alignment));
    }
  }
}

#endif
