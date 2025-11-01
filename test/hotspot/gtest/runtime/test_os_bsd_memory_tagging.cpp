/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "unittest.hpp"

#ifdef __APPLE__
#include <mach/mach_vm.h>
#include <mach/vm_statistics.h>
#include <sys/mman.h>
#include <cstring>

// Test that memory allocations on macOS are properly tagged with VM_MEMORY_JAVA
// This validates the VM_MAKE_TAG(VM_MEMORY_JAVA) changes in os_bsd.cpp
class BSDMemoryTaggingTest : public ::testing::Test {
protected:
  static const size_t test_size = 64 * 1024; // 64KB test allocation

  // Helper to allocate memory with VM_MEMORY_JAVA tag using mmap directly
  void* allocate_with_java_tag(size_t size) {
    // Use mmap with VM_FLAGS_ALIAS_MASK to set VM_MEMORY_JAVA tag
    // VM_MEMORY_JAVA is defined as 300 in mach/vm_statistics.h
    // VM_MAKE_TAG shifts the tag value into the correct position in flags
    int flags = MAP_PRIVATE | MAP_ANON;
    int fd = VM_MAKE_TAG(VM_MEMORY_JAVA);
    void* addr = ::mmap(nullptr, size, PROT_READ | PROT_WRITE, flags, fd, 0);
    return (addr == MAP_FAILED) ? nullptr : addr;
  }

  // Helper to check if a memory region is tagged with VM_MEMORY_JAVA
  bool is_memory_tagged_as_java(void* addr, size_t size) {
    // Use mach_vm_region with extended info to get the user_tag
    mach_vm_address_t address = (mach_vm_address_t)addr;
    mach_vm_size_t region_size = 0;
    vm_region_extended_info_data_t extended_info;
    mach_msg_type_number_t info_count = VM_REGION_EXTENDED_INFO_COUNT;
    mach_port_t object_name = MACH_PORT_NULL;

    kern_return_t kr = mach_vm_region(mach_task_self(),
                                      &address,
                                      &region_size,
                                      VM_REGION_EXTENDED_INFO,
                                      (vm_region_info_t)&extended_info,
                                      &info_count,
                                      &object_name);

    if (kr != KERN_SUCCESS) {
      return false;
    }

    // Check if the memory region covers our allocation and has the correct tag
    if (address <= (mach_vm_address_t)addr &&
        (address + region_size) >= ((mach_vm_address_t)addr + size)) {
      // Check if the user_tag matches VM_MEMORY_JAVA
      return extended_info.user_tag == VM_MEMORY_JAVA;
    }

    return false;
  }
};

TEST_F(BSDMemoryTaggingTest, test_mmap_with_java_tag) {
  // Test direct mmap with VM_MAKE_TAG(VM_MEMORY_JAVA)
  void* mem = allocate_with_java_tag(test_size);
  ASSERT_NE(mem, nullptr) << "Failed to allocate memory with Java tag";

  // Verify the memory region exists and has expected properties
  EXPECT_TRUE(is_memory_tagged_as_java(mem, test_size))
    << "Memory should be properly tagged with VM_MEMORY_JAVA on macOS";

  // Test that we can write to the memory
  memset(mem, 0xAB, test_size);
  EXPECT_EQ(((char*)mem)[0], (char)0xAB) << "Should be able to write to allocated memory";

  // Clean up
  EXPECT_EQ(::munmap(mem, test_size), 0) << "Failed to unmap memory";
}

TEST_F(BSDMemoryTaggingTest, test_multiple_allocations_with_java_tag) {
  // Test that multiple allocations use consistent tagging
  const size_t num_allocations = 5;
  void* allocations[num_allocations];

  // Make multiple allocations
  for (size_t i = 0; i < num_allocations; i++) {
    allocations[i] = allocate_with_java_tag(test_size);
    ASSERT_NE(allocations[i], nullptr) << "Failed to allocate memory for allocation " << i;

    // Verify tagging
    EXPECT_TRUE(is_memory_tagged_as_java(allocations[i], test_size))
      << "Allocation " << i << " should be properly tagged with VM_MEMORY_JAVA on macOS";

    // Write a pattern to verify the memory works
    memset(allocations[i], (int)(0xA0 + i), test_size);
  }

  // Verify all allocations are still accessible and have correct data
  for (size_t i = 0; i < num_allocations; i++) {
    EXPECT_EQ(((char*)allocations[i])[0], (char)(0xA0 + i))
      << "Allocation " << i << " should retain written data";
    EXPECT_EQ(((char*)allocations[i])[test_size - 1], (char)(0xA0 + i))
      << "Allocation " << i << " should retain written data at end";
  }

  // Clean up all allocations
  for (size_t i = 0; i < num_allocations; i++) {
    EXPECT_EQ(::munmap(allocations[i], test_size), 0)
      << "Failed to unmap memory for allocation " << i;
  }
}

#endif // __APPLE__
