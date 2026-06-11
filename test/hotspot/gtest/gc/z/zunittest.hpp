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
#include "gc/z/zPhysicalMemoryManager.hpp"
#include "gc/z/zRangeRegistry.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

#include <ostream>

inline std::ostream& operator<<(std::ostream& str, const ZVirtualMemory& vmem) {
  return str << "ZVirtualMemory{start=" << (void*)untype(vmem.start()) << ", size=" << vmem.size() << "}";
}

class ZAddressOffsetMaxSetter {
  friend class ZTest;

private:
  size_t _old_max;
  size_t _old_mask;

public:
  ZAddressOffsetMaxSetter(size_t zaddress_offset_max)
    : _old_max(ZAddressOffsetMax),
      _old_mask(ZAddressOffsetMask) {
    ZAddressOffsetMax = zaddress_offset_max;
    ZAddressOffsetMask = ZAddressOffsetMax - 1;
  }
  ~ZAddressOffsetMaxSetter() {
    ZAddressOffsetMax = _old_max;
    ZAddressOffsetMask = _old_mask;
  }
};

class ZTest : public testing::Test {
public:
  class ZAddressReserver {
    ZVirtualMemoryReserver* _reserver;
    bool _active;

    public:
      ZAddressReserver()
        : _reserver(nullptr),
          _active(false) {}

      ~ZAddressReserver() {
        GTEST_EXPECT_FALSE(_active) << "ZAddressReserver deconstructed without calling TearDown";
      }

      void SetUp(size_t reservation_size) {
        GTEST_EXPECT_FALSE(_active) << "SetUp called twice without a TearDown";
        _active = true;

        _reserver = (ZVirtualMemoryReserver*)os::malloc(sizeof(ZVirtualMemoryManager), mtTest);
        _reserver = ::new (_reserver) ZVirtualMemoryReserver(reservation_size);
      }

      void TearDown() {
        GTEST_EXPECT_TRUE(_active) << "TearDown called without a preceding SetUp";
        _active = false;

        // Best-effort cleanup
        _reserver->unreserve_all();
        _reserver->~ZVirtualMemoryReserver();
        os::free(_reserver);
      }

      ZVirtualMemoryReserver* reserver() {
        GTEST_EXPECT_TRUE(_active) << "Should only use HeapReserver while active";
        return _reserver;
      }

      ZVirtualMemoryRegistry* registry() {
        GTEST_EXPECT_TRUE(_active) << "Should only use HeapReserver while active";
        return &_reserver->_registry;
      }
  };

  class ZPhysicalMemoryBackingMocker {
    size_t                  _old_max;
    ZPhysicalMemoryBacking* _backing;
    bool                    _active;

    static size_t set_max(size_t max_capacity) {
      size_t old_max = ZBackingOffsetMax;

      ZBackingOffsetMax = max_capacity;
      ZBackingIndexMax = checked_cast<uint32_t>(ZBackingOffsetMax >> ZGranuleSizeShift);

      return old_max;
    }

  public:
    ZPhysicalMemoryBackingMocker()
      : _old_max(0),
        _backing(nullptr),
        _active(false) {}

    void SetUp(size_t max_capacity) {
      GTEST_EXPECT_FALSE(_active) << "SetUp called twice without a TearDown";

      _old_max = set_max(max_capacity);

      char* const mem = (char*)os::malloc(sizeof(ZPhysicalMemoryBacking), mtTest);
      _backing = new (mem) ZPhysicalMemoryBacking(ZGranuleSize);

      _active = true;
    }

    void TearDown() {
      GTEST_EXPECT_TRUE(_active) << "TearDown called without a preceding SetUp";

      _active = false;

      _backing->~ZPhysicalMemoryBacking();
      os::free(_backing);
      _backing = nullptr;

      set_max(_old_max);
    }

    ZPhysicalMemoryBacking* operator()() {
      return _backing;
    }
  };

private:
  ZAddressOffsetMaxSetter _zaddress_offset_max_setter;
  unsigned int _rand_seed;

  void skip_all_tests() {
    // Skipping from the constructor currently works, but according to the
    // documentation the GTEST_SKIP macro should be used from the test or
    // from the SetUp function. If this start to fail down the road, then
    // we'll have to explicitly call this for each inheriting gtest.
    GTEST_SKIP() << "OS not supported";
  }

protected:
  ZTest()
    : _zaddress_offset_max_setter(ZAddressOffsetMax),
      _rand_seed(static_cast<unsigned int>(::testing::UnitTest::GetInstance()->random_seed())) {
    if (!is_os_supported()) {
      // If the OS does not support ZGC do not run initialization, as it may crash the VM.
      skip_all_tests();
      return;
    }

    // Initialize ZGC subsystems for gtests, may only be called once per process.
    static bool runs_once = [&]() {
      ZInitialize::pd_initialize();
      ZNUMA::pd_initialize();
      ZGlobalsPointers::initialize();

      // ZGlobalsPointers::initialize() sets ZAddressOffsetMax, make sure the
      // first test fixture invocation has a correct ZAddressOffsetMaxSetter.
      _zaddress_offset_max_setter._old_max = ZAddressOffsetMax;
      _zaddress_offset_max_setter._old_mask = ZAddressOffsetMask;
      return true;
    }();
  }

  int random() {
    const int next_seed = os::next_random(_rand_seed);
    _rand_seed = static_cast<unsigned int>(next_seed);
    return next_seed;
  }

  bool is_os_supported() {
    return ZArguments::is_os_supported();
  }
};

#endif // ZUNITTEST_HPP
