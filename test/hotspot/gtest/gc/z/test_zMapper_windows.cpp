/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifdef _WIN64

#include "gc/z/zGlobals.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "unittest.hpp"

class TestZVirtualMemoryManager : public ZVirtualMemoryManager {
private:
  bool reserve_for_test(size_t size) {
    const zaddress_unsafe addr = ZOffset::address_unsafe(zoffset(0));

    // Reserve address space
    if (!pd_reserve(addr, size)) {
      return false;
    }

    // Make the address range free
    _manager.free(zoffset(0), size);

    return true;
  }

public:
  TestZVirtualMemoryManager(size_t size) : ZVirtualMemoryManager() {
    // Initialize platform specific parts before reserving address space
    pd_initialize_before_reserve();

    // Reserve address space
    if (!reserve_for_test(size)) {
      return;
    }

    // Initialize platform specific parts after reserving address space
    pd_initialize_after_reserve();
  }

  ~TestZVirtualMemoryManager() {
    // Empty the manager to avoid ZList assesrtion
    for (zoffset offset = _manager.alloc_low_address(2*M);
         offset != zoffset(UINTPTR_MAX);
         offset = _manager.alloc_low_address(2*M)) {

    }
  }

  void test_alloc_patterns() {
    // Verify that we get placeholder for last granule
    zoffset highest2m = _manager.alloc_high_address(2*M);
    zoffset high2m = _manager.alloc_high_address(2*M);
    _manager.free(highest2m, 2 * M);
    _manager.free(high2m, 2 * M);

    // Verify that we get placeholder for first granule
    zoffset lowest2m = _manager.alloc_low_address(2*M);
    zoffset low2m = _manager.alloc_low_address(2*M);
    _manager.free(lowest2m, 2 * M);
    _manager.free(low2m, 2 * M);

    // Destroy a 2M granule
    highest2m = _manager.alloc_high_address(2*M);
    high2m = _manager.alloc_high_address(2*M);
    _manager.free(highest2m, 2 * M);
    highest2m = _manager.alloc_high_address(2*M);
    _manager.free(highest2m, 2 * M);
    _manager.free(high2m, 2 * M);
  }
};

TEST(ZMapper, alloc_from_back) {
  ZSyscall::initialize();
  ZGlobalsPointers::initialize();
  TestZVirtualMemoryManager manager(32 * M);
  manager.test_alloc_patterns();
}
#endif
