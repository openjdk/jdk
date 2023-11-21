/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "oops/compressedKlass.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  // Optimize for base=0 shift=0
  if (optimize_for_zero_base) {
    result = reserve_address_space_for_unscaled_encoding(size, aslr);
  }

  // If this fails, we don't bother aiming for zero-based encoding (base=0 shift>0), since it has no
  // advantages over movk mode.

  // Allocate for movk mode: aim for a base address that has only bits set in the third quadrant.
  if (result == nullptr) {
    result = reserve_address_space_for_16bit_move(size, aslr);
  }

  // If that failed, attempt to allocate at any 4G-aligned address. Let the system decide where. For ASLR,
  // we now rely on the system.
  // Compared with the probing done above, this has two disadvantages:
  // - on a kernel with 52-bit address space we may get an address that has bits set between [48, 52).
  //   In that case, we may need two movk moves (not yet implemented).
  // - this technique leads to temporary over-reservation of address space; it will spike the vsize of
  //   the process. Therefore it may fail if a vsize limit is in place (e.g. ulimit -v).
  if (result == nullptr) {
    constexpr size_t alignment = nth_bit(32);
    log_debug(metaspace, map)("Trying to reserve at a 32-bit-aligned address");
    result = os::reserve_memory_aligned(size, alignment, false);
  }

  return result;
}

void CompressedKlassPointers::initialize(address addr, size_t len) {
  constexpr uintptr_t unscaled_max = nth_bit(32);
  assert(len <= unscaled_max, "Klass range larger than 32 bits?");

  // Shift is always 0 on aarch64.
  _shift = 0;

  // On aarch64, we don't bother with zero-based encoding (base=0 shift>0).
  address const end = addr + len;
  _base = (end <= (address)unscaled_max) ? nullptr : addr;

  _range = end - _base;
}
