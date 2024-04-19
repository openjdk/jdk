/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

using namespace testing;

class ZMapperTest : public Test {
private:
  static constexpr size_t reservation_size = 32 * M;
public:
  ZVirtualMemoryManager* _vmm;
  static ZMemoryManager* _va;

  bool reserve_for_test() {
    const zaddress_unsafe addr = ZOffset::address_unsafe(zoffset(0));

    // Initialize platform specific parts before reserving address space
    _vmm->pd_initialize_before_reserve();

    // Reserve address space
    if (!_vmm->pd_reserve(addr, reservation_size)) {
      return false;
    }

    // Make the address range free before setting up callbacks below
    _va->free(zoffset(0), reservation_size);

    // Initialize platform specific parts after reserving address space
    _vmm->pd_initialize_after_reserve();

    return true;
  }

  virtual void SetUp() {
    ZSyscall::initialize();
    ZGlobalsPointers::initialize();

    // Fake a ZVirtualMemoryManager
    _vmm = (ZVirtualMemoryManager*)os::malloc(sizeof(ZVirtualMemoryManager), mtTest);
    // And construct its internal ZMemoryManager
    _va = new (&_vmm->_manager) ZMemoryManager();

    reserve_for_test();
  }

  virtual void TearDown() {
    const zaddress_unsafe addr = ZOffset::address_unsafe(zoffset(0));
    _vmm->pd_unreserve(addr, 0);
    os::free(_vmm);
  }

  static void test_memory_manager_callbacks() {
    // Verify that we get placeholder for last granule
    zoffset highest2m = _va->alloc_high_address(2*M);
    zoffset high2m = _va->alloc_high_address(2*M);
    _va->free(highest2m, 2 * M);
    _va->free(high2m, 2 * M);

    // Verify that we get placeholder for first granule
    zoffset lowest2m = _va->alloc_low_address(2*M);
    zoffset low2m = _va->alloc_low_address(2*M);
    _va->free(lowest2m, 2 * M);
    _va->free(low2m, 2 * M);

    // Destroy a 2M granule
    highest2m = _va->alloc_high_address(2*M);
    high2m = _va->alloc_high_address(2*M);
    _va->free(highest2m, 2 * M);
    highest2m = _va->alloc_high_address(2*M);
    _va->free(highest2m, 2 * M);
    _va->free(high2m, 2 * M);
  }
};

ZMemoryManager* ZMapperTest::_va = nullptr;

TEST_F(ZMapperTest, test_memory_manager_callbacks) {
  test_memory_manager_callbacks();
}
#endif
