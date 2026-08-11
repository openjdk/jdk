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

class ZVirtualMemoryManagerTest : public ZTest {
private:
  static constexpr size_t ReservationSize = 32 * M;

  ZAddressReserver        _zaddress_reserver;
  ZVirtualMemoryReserver* _reserver;
  ZVirtualMemoryRegistry* _registry;

public:
  virtual void SetUp() {
    _zaddress_reserver.SetUp(ReservationSize);
    _reserver = _zaddress_reserver.reserver();
    _registry = _zaddress_reserver.registry();

    if (_reserver->reserved() < ReservationSize || !_registry->is_contiguous()) {
      GTEST_SKIP() << "Fixture failed to reserve adequate memory, reserved "
          << (_reserver->reserved() >> ZGranuleSizeShift) << " * ZGranuleSize";
    }
  }

  virtual void TearDown() {
    _registry = nullptr;
    _reserver = nullptr;
    _zaddress_reserver.TearDown();
  }

  void test_reserve_discontiguous_and_coalesce() {
    // Start by ensuring that we have 3 unreserved granules, and then let the
    // fourth granule be pre-reserved and therefore blocking subsequent requests
    // to reserve memory.
    //
    // +----+----+----+----+
    //                -----  pre-reserved - to block contiguous reservation
    // ---------------       unreserved   - to allow reservation of 3 granules
    //
    // If we then asks for 4 granules starting at the first granule above,
    // then we won't be able to reserve 4 consecutive granules and the code
    // reverts into the discontiguous mode. This mode uses interval halving
    // to find the limits of memory areas that have already been reserved.
    // This will lead to the first 2 granules being reserved, then the third
    // granule will be reserved.
    //
    // The problem we had with this is that this would yield two separate
    // placeholder reservations, even though they are adjacent. The callbacks
    // are supposed to fix that by coalescing the placeholders, *but* the
    // callbacks used to be only turned on *after* the reservation call. So,
    // we end up with one 3 granule large memory area in the manager, which
    // unexpectedly was covered by two placeholders (instead of the expected
    // one placeholder).
    //
    // Later when the callbacks had been installed and we tried to fetch memory
    // from the manager, the callbacks would try to split off the placeholder
    // to separate the fetched memory from the memory left in the manager. This
    // used to fail because the memory was already split into two placeholders.

    // Start at the offset we reserved.
    const zoffset base_offset = _registry->peek_low_address();

    // Empty the reserved memory in preparation for the rest of the test.
    _reserver->unreserve_all();

    const zaddress_unsafe base = ZOffset::address_unsafe(base_offset);
    const zaddress_unsafe blocked = base + 3 * ZGranuleSize;

    // Reserve the memory that is acting as a blocking reservation.
    {
      char* const result = os::attempt_reserve_memory_at((char*)untype(blocked), ZGranuleSize, mtTest);
      if (uintptr_t(result) != untype(blocked)) {
        GTEST_SKIP() << "Failed to reserve requested memory at " << untype(blocked);
      }
    }

    {
      // This ends up reserving 2 granules and then 1 granule adjacent to the
      // first. In previous implementations this resulted in two separate
      // placeholders (4MB and 2MB). This was a bug, because the manager is
      // designed to have one placeholder per memory area. This in turn would
      // lead to a subsequent failure when _vmr->remove* tried to split off the
      // 4MB that is already covered by its own placeholder. You can't place
      // a placeholder over an already existing placeholder.

      // To reproduce this, the test needed to mimic the initializing memory
      // reservation code which had the placeholders turned off. This was done
      // with this helper:
      //
      // ZCallbacksResetter resetter(&_va->_callbacks);
      //
      // After the fix, we always have the callbacks turned on, so we don't
      // need this to mimic the initializing memory reservation.

      const size_t reserved = _reserver->reserve_discontiguous(base_offset, 4 * ZGranuleSize, ZGranuleSize);
      ASSERT_LE(reserved, 3 * ZGranuleSize);
      if (reserved < 3 * ZGranuleSize) {
        GTEST_SKIP() << "Failed reserve_discontiguous"
            ", expected 3 * ZGranuleSize, got " << (reserved >> ZGranuleSizeShift)
            << " * ZGranuleSize";
      }
    }

    {
      // The test used to crash here because the 3 granule memory area was
      // inadvertently covered by two place holders (2 granules + 1 granule).
      const ZVirtualMemory vmem = _registry->remove_from_low(2 * ZGranuleSize);
      ASSERT_EQ(vmem, ZVirtualMemory(base_offset, 2 * ZGranuleSize));

      // Cleanup - Must happen in granule-sizes because of how Windows hands
      // out memory in granule-sized placeholder reservations.
      _reserver->unreserve(vmem.first_part(ZGranuleSize));
      _reserver->unreserve(vmem.last_part(ZGranuleSize));
    }

    // Final cleanup
    const ZVirtualMemory vmem = _registry->remove_from_low(ZGranuleSize);
    ASSERT_EQ(vmem, ZVirtualMemory(base_offset + 2 * ZGranuleSize, ZGranuleSize));
    _reserver->unreserve(vmem);

    os::release_memory((char*)untype(blocked), ZGranuleSize);
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

TEST_VM_F(ZVirtualMemoryManagerTest, test_reserve_discontiguous_and_coalesce) {
  test_reserve_discontiguous_and_coalesce();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_remove_from_low) {
  test_remove_from_low();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_remove_from_high) {
  test_remove_from_high();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_remove_whole) {
  test_remove_whole();
}
