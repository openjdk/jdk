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
private:
  ZAddressOffsetMaxSetter _zaddress_offset_max_setter;
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
