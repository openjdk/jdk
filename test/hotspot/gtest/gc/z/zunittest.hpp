/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef ZUNITTEST_HPP
#define ZUNITTEST_HPP

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zArguments.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zRangeRegistry.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

#include <ostream>

inline std::ostream& operator<<(std::ostream& str, const ZVirtualMemory& vmem) {
  return str << "ZVirtualMemory{start=" << (void*)untype(vmem.start()) << ", size=" << vmem.size() << "}";
}

class ZAddressOffsetLimitsSetter {
  friend class ZTest;

private:
  size_t _old_max;
  size_t _old_mask;
  size_t _old_upper_limit;

public:
  ZAddressOffsetLimitsSetter(size_t zaddress_offset_max, size_t zaddress_offset_limit)
    : _old_max(ZAddressOffsetMax),
      _old_mask(ZAddressOffsetMask),
      _old_upper_limit(ZAddressOffsetUpperLimit) {
    ZAddressOffsetMax = zaddress_offset_max;
    ZAddressOffsetMask = ZAddressOffsetMax - 1;

    ZAddressOffsetUpperLimit = zaddress_offset_max;
  }
  ZAddressOffsetLimitsSetter(size_t zaddress_offset_max)
    : ZAddressOffsetLimitsSetter(zaddress_offset_max, zaddress_offset_max) {}

  ~ZAddressOffsetLimitsSetter() {
    ZAddressOffsetMax = _old_max;
    ZAddressOffsetMask = _old_mask;
    ZAddressOffsetUpperLimit = _old_upper_limit;
  }
};

class ZTest : public testing::Test {
private:
  ZAddressOffsetLimitsSetter _zaddress_offset_max_setter;
  unsigned int _rand_seed;

protected:
  ZTest()
    : _zaddress_offset_max_setter(ZAddressOffsetMax),
      _rand_seed(static_cast<unsigned int>(::testing::UnitTest::GetInstance()->random_seed())) {
    if (!is_os_supported()) {
      // If the OS does not support ZGC do not run initialization, as it may crash the VM.
      return;
    }

    // Initialize ZGC subsystems for gtests, may only be called once per process.
    static bool runs_once = [&]() {
      ZInitialize::pd_initialize();
      ZNUMA::pd_initialize();
      ZGlobalsPointers::initialize();

      auto initialize_heap_settings = [&]() {
        assert(MaxHeapSize > 0, "Expecting heap size to be initialized");
        for (size_t heap_base_shift = ZAddressHeapBaseMinShift;
            heap_base_shift <= ZAddressHeapBaseMaxShift;
            heap_base_shift++) {
          const size_t heap_base = uintptr_t(1) << heap_base_shift;
          const size_t max_offset = (size_t)heap_base;
          if (MaxHeapSize <= max_offset) {
            ZGlobalsPointers::set_heap_limits(heap_base, heap_base + size_t(heap_base));
            return true;
          }
        }

        return false;
      };

      GTEST_EXPECT_TRUE(initialize_heap_settings()) << "Failed to setup test fixture";

      // ZGlobalsPointers::set_heap_limits() sets ZAddressOffsetMax and ZAddressOffsetUpperLimit,
      // make sure the first test fixture invocation has a correct ZAddressOffsetLimitsSetter.
      _zaddress_offset_max_setter._old_max = ZAddressOffsetMax;
      _zaddress_offset_max_setter._old_mask = ZAddressOffsetMask;
      _zaddress_offset_max_setter._old_upper_limit = ZAddressOffsetMax;
      return true;
    }();
  }

  int random() {
    const int next_seed = os::next_random(_rand_seed);
    _rand_seed = static_cast<unsigned int>(next_seed);
    return next_seed;
  }

public:
  static bool is_os_supported() {
    return ZArguments::is_os_supported();
  }
};

class ZTestAddressReserver {
  ZVirtualMemoryAdaptiveReserver* _reserver;
  ZVirtualMemoryReservation*      _reservation;
  bool                            _active;

public:
  ZTestAddressReserver()
  : _reserver(nullptr),
    _reservation(nullptr),
    _active(false) {}

  ~ZTestAddressReserver() {
    GTEST_EXPECT_FALSE(_active) << "ZTestAddressReserver deconstructed without calling TearDown";
  }

  void SetUp(size_t reservation_size) {
    GTEST_EXPECT_TRUE(ZTest::is_os_supported()) << "Should not use SetUp on unsupported systems";
    GTEST_EXPECT_FALSE(_active) << "SetUp called twice without a TearDown";
    _active = true;

    ZVirtualMemoryAdaptiveReserver reserver;

    const size_t reserved = reserver.reserve(reservation_size, reservation_size);

    GTEST_EXPECT_TRUE(reserved == reservation_size);

    ZGlobalsPointers::set_heap_limits(reserver.heap_base(), reserver.end());

    _reservation = (ZVirtualMemoryReservation*)os::malloc(sizeof(ZVirtualMemoryReservation), mtTest);
    _reservation = ::new (_reservation) ZVirtualMemoryReservation(reserver.reserved_ranges());
  }

  void TearDown() {
    GTEST_EXPECT_TRUE(_active) << "TearDown called without a preceding SetUp";
    _active = false;

    // Best-effort cleanup
    _reservation->unreserve_all();
    _reservation->~ZVirtualMemoryReservation();
    os::free(_reservation);
  }

  ZVirtualMemoryReservation* reservation() {
    GTEST_EXPECT_TRUE(_active) << "Should only use HeapReserver while active";
    return _reservation;
  }

  ZVirtualMemoryRegistry* registry() {
    GTEST_EXPECT_TRUE(_active) << "Should only use HeapReserver while active";
    return &_reservation->_registry;
  }
};

#endif // ZUNITTEST_HPP
