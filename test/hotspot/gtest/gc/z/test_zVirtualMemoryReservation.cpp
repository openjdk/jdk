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

class ZVirtualMemoryReservationTest : public ZTest {
private:
  static constexpr size_t ReservationSize = 32 * M;

public:
  virtual void SetUp() {
    // Only run test on supported Windows versions
    if (!is_os_supported()) {
      GTEST_SKIP() << "OS not supported";
    }
  }

  virtual void TearDown() {
    // Nothing to cleanup
  }

  void test_reserve_discontiguous_and_coalesce() {
    ZVirtualMemoryAdaptiveReserver reserver;

    reserver.reserve(4 * ZGranuleSize, 4 * ZGranuleSize);

    if (reserver.reserved() != 4 * ZGranuleSize) {
      GTEST_SKIP() << "Failed to reserve requested memory";
    }

    if (reserver._reserved_ranges.length() != 1) {
      GTEST_SKIP() << "Failed to reserve single reserved area";
    }

    ZGlobalsPointers::set_heap_limits(reserver.heap_base(), reserver.end());

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
    const uintptr_t bottom = reserver.bottom();

    // Empty the reserved memory in preparation for the rest of the test.
    reserver.unreserve_all();

    const uintptr_t blocked = bottom + 3 * ZGranuleSize;

    // Reserve the memory that is acting as a blocking reservation.
    {
      char* const result = os::attempt_reserve_memory_at((char*)blocked, ZGranuleSize, mtTest);
      if (uintptr_t(result) != blocked) {
        GTEST_SKIP() << "Failed to reserve requested memory at " << blocked;
      }
    }

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

    ZVirtualMemoryWithHeapBaseReserver reserver2(reserver.heap_base());

    const size_t reserved = reserver2.reserve_discontiguous(bottom, 4 * ZGranuleSize, ZGranuleSize);
    ASSERT_LE(reserved, 3 * ZGranuleSize);
    if (reserved < 3 * ZGranuleSize) {
      GTEST_SKIP() << "Failed reserve_discontiguous"
          ", expected 3 * ZGranuleSize, got " << (reserved >> ZGranuleSizeShift)
          << " * ZGranuleSize";
    }

    // Transfer over to the reservation instance
    ZVirtualMemoryReservation reservation(&reserver2._reserved_ranges);

    const zoffset bottom_offset = ZAddress::offset(to_zaddress(bottom));

    {
      // The test used to crash here because the 3 granule memory area was
      // inadvertently covered by two place holders (2 granules + 1 granule).
      const ZVirtualMemory vmem = reservation._registry.remove_from_low(2 * ZGranuleSize);
      ASSERT_EQ(vmem, ZVirtualMemory(bottom_offset, 2 * ZGranuleSize));

      // Cleanup - Must happen in granule-sizes because of how Windows hands
      // out memory in granule-sized placeholder reservations.
      reservation.unreserve(vmem.first_part(ZGranuleSize));
      reservation.unreserve(vmem.last_part(ZGranuleSize));
    }

    // Final cleanup
    const ZVirtualMemory vmem = reservation._registry.remove_from_low(ZGranuleSize);
    ASSERT_EQ(vmem, ZVirtualMemory(bottom_offset + 2 * ZGranuleSize, ZGranuleSize));
    reservation.unreserve(vmem);

    const bool released = os::release_memory((char*)blocked, ZGranuleSize);
    ASSERT_TRUE(released);
  }
};

TEST_VM_F(ZVirtualMemoryReservationTest, test_reserve_discontiguous_and_coalesce) {
  test_reserve_discontiguous_and_coalesce();
}
