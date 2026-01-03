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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zArguments.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zValue.inline.hpp"
#include "gc/z/zVirtualMemoryManager.inline.hpp"
#include "runtime/os.hpp"
#include "zunittest.hpp"

using namespace testing;

#define ASSERT_REMOVAL_OK(range, sz) ASSERT_FALSE(range.is_null()); ASSERT_EQ(range.size(), (sz))

class ZCallbacksResetter {
private:
  ZVirtualMemoryRegistry::Callbacks* _callbacks;
  ZVirtualMemoryRegistry::Callbacks  _saved;

public:
  ZCallbacksResetter(ZVirtualMemoryRegistry::Callbacks* callbacks)
    : _callbacks(callbacks),
      _saved(*callbacks) {
    *_callbacks = {};
  }
  ~ZCallbacksResetter() {
    *_callbacks = _saved;
  }
};

class ZVirtualMemoryRegistryTest : public ZTest {
private:
  static constexpr size_t ReservationSize = 32 * M;

  ZTestAddressReserver       _zaddress_reserver;
  ZVirtualMemoryReservation* _reservation;
  ZVirtualMemoryRegistry*    _registry;

public:
  virtual void SetUp() {
    // Only run test on supported Windows versions
    if (!is_os_supported()) {
      GTEST_SKIP() << "OS not supported";
    }

    _zaddress_reserver.SetUp(ReservationSize);
    _reservation = _zaddress_reserver.reservation();
    _registry = _zaddress_reserver.registry();

    if (_reservation->reserved() < ReservationSize || !_registry->is_contiguous()) {
      GTEST_SKIP() << "Fixture failed to reserve adequate memory, reserved "
          << (_reservation->reserved() >> ZGranuleSizeShift) << " * ZGranuleSize";
    }
  }

  virtual void TearDown() {
    if (!is_os_supported()) {
      // Test skipped, nothing to cleanup
      return;
    }

    _registry = nullptr;
    _reservation = nullptr;
    _zaddress_reserver.TearDown();
  }

  void test_remove_from_low() {
    {
      // Verify that we get a placeholder for the first granule
      const ZVirtualMemory removed = _registry->remove_from_low(ZGranuleSize);
      ASSERT_REMOVAL_OK(removed, ZGranuleSize);

      _registry->insert(removed);
    }

    {
      // Remove something larger than a granule and then insert it
      const ZVirtualMemory removed = _registry->remove_from_low(3 * ZGranuleSize);
      ASSERT_REMOVAL_OK(removed, 3 * ZGranuleSize);

      _registry->insert(removed);
    }

    {
      // Insert with more memory removed
      const ZVirtualMemory removed = _registry->remove_from_low(ZGranuleSize);
      ASSERT_REMOVAL_OK(removed, ZGranuleSize);

      ZVirtualMemory next = _registry->remove_from_low(ZGranuleSize);
      ASSERT_REMOVAL_OK(next, ZGranuleSize);

      _registry->insert(removed);
      _registry->insert(next);
    }
  }

  void test_remove_from_high() {
    {
      // Verify that we get a placeholder for the last granule
      const ZVirtualMemory high = _registry->remove_from_high(ZGranuleSize);
      ASSERT_REMOVAL_OK(high, ZGranuleSize);

      const ZVirtualMemory prev = _registry->remove_from_high(ZGranuleSize);
      ASSERT_REMOVAL_OK(prev, ZGranuleSize);

      _registry->insert(high);
      _registry->insert(prev);
    }

    {
      // Remove something larger than a granule and return it
      const ZVirtualMemory high = _registry->remove_from_high(2 * ZGranuleSize);
      ASSERT_REMOVAL_OK(high, 2 * ZGranuleSize);

      _registry->insert(high);
    }
  }

  void test_remove_whole() {
    // Need a local variable to appease gtest
    const size_t reservation_size = ReservationSize;

    // Remove the whole reservation
    const ZVirtualMemory reserved = _registry->remove_from_low(reservation_size);
    ASSERT_REMOVAL_OK(reserved, reservation_size);

    const ZVirtualMemory first(reserved.start(), 4 * ZGranuleSize);
    const ZVirtualMemory second(reserved.start() + 6 * ZGranuleSize, 6 * ZGranuleSize);

    // Insert two chunks and then remove them again
    _registry->insert(first);
    _registry->insert(second);

    const ZVirtualMemory removed_first = _registry->remove_from_low(first.size());
    ASSERT_EQ(removed_first, first);

    const ZVirtualMemory removed_second = _registry->remove_from_low(second.size());
    ASSERT_EQ(removed_second, second);

    // Now insert it all, and verify it can be re-removed
    _registry->insert(reserved);

    const ZVirtualMemory removed_reserved = _registry->remove_from_low(reservation_size);
    ASSERT_EQ(removed_reserved, reserved);

    _registry->insert(reserved);
  }
};

TEST_VM_F(ZVirtualMemoryRegistryTest, test_remove_from_low) {
  test_remove_from_low();
}

TEST_VM_F(ZVirtualMemoryRegistryTest, test_remove_from_high) {
  test_remove_from_high();
}

TEST_VM_F(ZVirtualMemoryRegistryTest, test_remove_whole) {
  test_remove_whole();
}
