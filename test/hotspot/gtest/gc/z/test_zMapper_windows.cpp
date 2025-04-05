/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifdef _WINDOWS

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zMapper_windows.hpp"
#include "gc/z/zMemory.inline.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "runtime/os.hpp"
#include "zunittest.hpp"

using namespace testing;

class ZMapperTest : public ZTest {
private:
  static constexpr size_t ReservationSize = 32 * M;

  ZVirtualMemoryManager* _vmm;
  ZMemoryManager*        _va;

public:
  virtual void SetUp() {
    // Only run test on supported Windows versions
    if (!is_os_supported()) {
      GTEST_SKIP() << "Requires Windows version 1803 or later";
    }

    // Fake a ZVirtualMemoryManager
    _vmm = (ZVirtualMemoryManager*)os::malloc(sizeof(ZVirtualMemoryManager), mtTest);
    _vmm = ::new (_vmm) ZVirtualMemoryManager(ReservationSize);

    // Construct its internal ZMemoryManager
    _va = new (&_vmm->_manager) ZMemoryManager();

    // Reserve address space for the test
    if (_vmm->reserved() != ReservationSize) {
      GTEST_SKIP() << "Failed to reserve address space";
    }
  }

  virtual void TearDown() {
    if (!is_os_supported()) {
      // Test skipped, nothing to cleanup
      return;
    }

    // Best-effort cleanup
    _vmm->unreserve_all();
    _vmm->~ZVirtualMemoryManager();
    os::free(_vmm);
  }

  void test_unreserve() {
    zoffset bottom = _va->alloc_low_address(ZGranuleSize);
    zoffset middle = _va->alloc_low_address(ZGranuleSize);
    zoffset top    = _va->alloc_low_address(ZGranuleSize);

    ASSERT_EQ(bottom, zoffset(0));
    ASSERT_EQ(middle, bottom + 1 * ZGranuleSize);
    ASSERT_EQ(top,    bottom + 2 * ZGranuleSize);

    // Unreserve the middle part
    ZMapper::unreserve(ZOffset::address_unsafe(middle), ZGranuleSize);

    // Make sure that we still can unreserve the memory before and after
    ZMapper::unreserve(ZOffset::address_unsafe(bottom), ZGranuleSize);
    ZMapper::unreserve(ZOffset::address_unsafe(top), ZGranuleSize);
  }
};

TEST_VM_F(ZMapperTest, test_unreserve) {
  test_unreserve();
}

#endif // _WINDOWS
