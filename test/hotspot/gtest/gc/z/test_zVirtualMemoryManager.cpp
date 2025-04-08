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
#include "gc/z/zMemory.inline.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zValue.inline.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "runtime/os.hpp"
#include "zunittest.hpp"

using namespace testing;

#define ASSERT_ALLOC_OK(offset) ASSERT_NE(offset, zoffset(UINTPTR_MAX))

class ZCallbacksResetter {
private:
  ZMemoryManager::Callbacks* _callbacks;
  ZMemoryManager::Callbacks  _saved;

public:
  ZCallbacksResetter(ZMemoryManager::Callbacks* callbacks)
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

  ZMemoryManager*        _va;
  ZVirtualMemoryManager* _vmm;

public:
  virtual void SetUp() {
    // Only run test on supported Windows versions
    if (!is_os_supported()) {
      GTEST_SKIP() << "OS not supported";
    }

    void* vmr_mem = os::malloc(sizeof(ZVirtualMemoryManager), mtTest);
    _vmm = ::new (vmr_mem) ZVirtualMemoryManager(ReservationSize);
    _va = &_vmm->_manager;
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
    // then we won't be able to allocate 4 consecutive granules and the code
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

    if (_vmm->reserved() < 4 * ZGranuleSize || !_va->free_is_contiguous()) {
      GTEST_SKIP() << "Fixture failed to reserve adequate memory, reserved "
          << (_vmm->reserved() >> ZGranuleSizeShift) << " * ZGranuleSize";
    }

    // Start at the offset we reserved.
    const zoffset base_offset = _vmm->lowest_available_address();

    // Empty the reserved memory in preparation for the rest of the test.
    _vmm->unreserve_all();

    const zaddress_unsafe base = ZOffset::address_unsafe(base_offset);
    const zaddress_unsafe blocked = base + 3 * ZGranuleSize;

    // Reserve the memory that is acting as a blocking reservation.
    {
      char* const result = os::attempt_reserve_memory_at((char*)untype(blocked), ZGranuleSize, !ExecMem, mtTest);
      if (uintptr_t(result) != untype(blocked)) {
        GTEST_SKIP() << "Failed to reserve requested memory at " << untype(blocked);
      }
    }

    {
      // This ends up reserving 2 granules and then 1 granule adjacent to the
      // first. In previous implementations this resulted in two separate
      // placeholders (4MB and 2MB). This was a bug, because the manager is
      // designed to have one placeholder per memory area. This in turn would
      // lead to a subsequent failure when _vmm->alloc tried to split off the
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

      const size_t reserved = _vmm->reserve_discontiguous(base_offset, 4 * ZGranuleSize, ZGranuleSize);
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
      const ZVirtualMemory vmem = _vmm->alloc(2 * ZGranuleSize, true);
      ASSERT_EQ(vmem.start(), base_offset);
      ASSERT_EQ(vmem.size(), 2 * ZGranuleSize);

      // Cleanup - Must happen in granule-sizes because of how Windows hands
      // out memory in granule-sized placeholder reservations.
      _vmm->unreserve(base_offset, ZGranuleSize);
      _vmm->unreserve(base_offset + ZGranuleSize, ZGranuleSize);
    }

    // Final cleanup
    const ZVirtualMemory vmem = _vmm->alloc(ZGranuleSize, true);
    ASSERT_EQ(vmem.start(), base_offset + 2 * ZGranuleSize);
    ASSERT_EQ(vmem.size(), ZGranuleSize);
    _vmm->unreserve(vmem.start(), vmem.size());

    const bool released = os::release_memory((char*)untype(blocked), ZGranuleSize);
    ASSERT_TRUE(released);
  }

  void test_alloc_low_address() {
    // Verify that we get a placeholder for the first granule
    zoffset bottom = _va->alloc_low_address(ZGranuleSize);
    ASSERT_ALLOC_OK(bottom);

    _va->free(bottom, ZGranuleSize);

    // Alloc something larger than a granule and free it
    bottom = _va->alloc_low_address(ZGranuleSize * 3);
    ASSERT_ALLOC_OK(bottom);

    _va->free(bottom, ZGranuleSize * 3);

    // Free with more memory allocated
    bottom = _va->alloc_low_address(ZGranuleSize);
    ASSERT_ALLOC_OK(bottom);

    zoffset next = _va->alloc_low_address(ZGranuleSize);
    ASSERT_ALLOC_OK(next);

    _va->free(bottom, ZGranuleSize);
    _va->free(next, ZGranuleSize);
  }

  void test_alloc_high_address() {
    // Verify that we get a placeholder for the last granule
    zoffset high = _va->alloc_high_address(ZGranuleSize);
    ASSERT_ALLOC_OK(high);

    zoffset prev = _va->alloc_high_address(ZGranuleSize);
    ASSERT_ALLOC_OK(prev);

    _va->free(high, ZGranuleSize);
    _va->free(prev, ZGranuleSize);

    // Alloc something larger than a granule and return it
    high = _va->alloc_high_address(ZGranuleSize * 2);
    ASSERT_ALLOC_OK(high);

    _va->free(high, ZGranuleSize * 2);
  }

  void test_alloc_whole_area() {
    // Alloc the whole reservation
    zoffset bottom = _va->alloc_low_address(ReservationSize);
    ASSERT_ALLOC_OK(bottom);

    // Free two chunks and then allocate them again
    _va->free(bottom, ZGranuleSize * 4);
    _va->free(bottom + ZGranuleSize * 6, ZGranuleSize * 6);

    zoffset offset = _va->alloc_low_address(ZGranuleSize * 4);
    ASSERT_ALLOC_OK(offset);

    offset = _va->alloc_low_address(ZGranuleSize * 6);
    ASSERT_ALLOC_OK(offset);

    // Now free it all, and verify it can be re-allocated
    _va->free(bottom, ReservationSize);

    bottom = _va->alloc_low_address(ReservationSize);
    ASSERT_ALLOC_OK(bottom);

    _va->free(bottom, ReservationSize);
  }
};

TEST_VM_F(ZVirtualMemoryManagerTest, test_reserve_discontiguous_and_coalesce) {
  test_reserve_discontiguous_and_coalesce();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_alloc_low_address) {
  test_alloc_low_address();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_alloc_high_address) {
  test_alloc_high_address();
}

TEST_VM_F(ZVirtualMemoryManagerTest, test_alloc_whole_area) {
  test_alloc_whole_area();
}
