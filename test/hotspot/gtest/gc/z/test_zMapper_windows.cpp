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

#include "gc/z/zGlobals.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.inline.hpp"
#include "runtime/os.hpp"
#include "zunittest.hpp"

using namespace testing;

class ZMapperTest : public ZTest {
private:
  static constexpr size_t ReservationSize = 32 * M;

  ZVirtualMemoryReserver* _reserver;
  ZVirtualMemoryRegistry* _registry;

public:
  virtual void SetUp() {
    // Only run test on supported Windows versions
    if (!is_os_supported()) {
      GTEST_SKIP() << "OS not supported";
    }

    _reserver = (ZVirtualMemoryReserver*)os::malloc(sizeof(ZVirtualMemoryManager), mtTest);
    _reserver = ::new (_reserver) ZVirtualMemoryReserver(ReservationSize);
    _registry = &_reserver->_registry;
  }

  virtual void TearDown() {
    if (!is_os_supported()) {
      // Test skipped, nothing to cleanup
      return;
    }

    // Best-effort cleanup
    _reserver->unreserve_all();
    _reserver->~ZVirtualMemoryReserver();
    os::free(_reserver);
  }

  void test_unreserve() {
    ZVirtualMemory bottom = _registry->remove_from_low(ZGranuleSize);
    ZVirtualMemory middle = _registry->remove_from_low(ZGranuleSize);
    ZVirtualMemory top    = _registry->remove_from_low(ZGranuleSize);

    ASSERT_EQ(bottom, ZVirtualMemory(bottom.start(),                    ZGranuleSize));
    ASSERT_EQ(middle, ZVirtualMemory(bottom.start() + 1 * ZGranuleSize, ZGranuleSize));
    ASSERT_EQ(top,    ZVirtualMemory(bottom.start() + 2 * ZGranuleSize, ZGranuleSize));

    // Unreserve the middle part
    _reserver->unreserve(middle);

    // Make sure that we still can unreserve the memory before and after
    _reserver->unreserve(bottom);
    _reserver->unreserve(top);
  }
};

TEST_VM_F(ZMapperTest, test_unreserve) {
  test_unreserve();
}

#endif // _WINDOWS
