/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "logging/log.hpp"
#include "oops/compressedKlass.hpp"
#include "memory/metaspace.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

// Helper function; reserve at an address that is compatible with EOR
static char* reserve_at_eor_compatible_address(size_t size, bool aslr) {
  char* result = nullptr;

  log_debug(metaspace, map)("Trying to reserve at an EOR-compatible address");

  // We need immediates that are 32-bit aligned, since they should not intersect nKlass
  // bits. They should not be larger than the addressable space either, but we still
  // lack a good abstraction for that (see JDK-8320584), therefore we assume and hard-code
  // 2^48 as a reasonable higher ceiling.
  static const uint16_t immediates[] = {
      0x0001, 0x0002, 0x0003, 0x0004, 0x0006, 0x0007, 0x0008, 0x000c, 0x000e,
      0x000f, 0x0010, 0x0018, 0x001c, 0x001e, 0x001f, 0x0020, 0x0030, 0x0038,
      0x003c, 0x003e, 0x003f, 0x0040, 0x0060, 0x0070, 0x0078, 0x007c, 0x007e,
      0x007f, 0x0080, 0x00c0, 0x00e0, 0x00f0, 0x00f8, 0x00fc, 0x00fe, 0x00ff,
      0x0100, 0x0180, 0x01c0, 0x01e0, 0x01f0, 0x01f8, 0x01fc, 0x01fe, 0x01ff,
      0x0200, 0x0300, 0x0380, 0x03c0, 0x03e0, 0x03f0, 0x03f8, 0x03fc, 0x03fe,
      0x03ff, 0x0400, 0x0600, 0x0700, 0x0780, 0x07c0, 0x07e0, 0x07f0, 0x07f8,
      0x07fc, 0x07fe, 0x07ff, 0x0800, 0x0c00, 0x0e00, 0x0f00, 0x0f80, 0x0fc0,
      0x0fe0, 0x0ff0, 0x0ff8, 0x0ffc, 0x0ffe, 0x0fff, 0x1000, 0x1800, 0x1c00,
      0x1e00, 0x1f00, 0x1f80, 0x1fc0, 0x1fe0, 0x1ff0, 0x1ff8, 0x1ffc, 0x1ffe,
      0x1fff, 0x2000, 0x3000, 0x3800, 0x3c00, 0x3e00, 0x3f00, 0x3f80, 0x3fc0,
      0x3fe0, 0x3ff0, 0x3ff8, 0x3ffc, 0x3ffe, 0x3fff, 0x4000, 0x6000, 0x7000,
      0x7800, 0x7c00, 0x7e00, 0x7f00, 0x7f80, 0x7fc0, 0x7fe0, 0x7ff0, 0x7ff8,
      0x7ffc, 0x7ffe, 0x7fff
  };
  static constexpr unsigned num_immediates = sizeof(immediates) / sizeof(immediates[0]);
  const unsigned start_index = aslr ? os::next_random((int)os::javaTimeNanos()) : 0;
  constexpr int max_tries = 64;
  for (int ntry = 0; result == nullptr && ntry < max_tries; ntry ++) {
    // As in os::attempt_reserve_memory_between, we alternate between higher and lower
    // addresses; this maximizes the chance of early success if part of the address space
    // is not accessible (e.g. 39-bit address space).
    const unsigned alt_index = (ntry & 1) ? 0 : num_immediates / 2;
    const unsigned index = (start_index + ntry + alt_index) % num_immediates;
    const uint64_t immediate = ((uint64_t)immediates[index]) << 32;
    assert(immediate > 0 && Assembler::operand_valid_for_logical_immediate(/*is32*/false, immediate),
           "Invalid immediate %d " UINT64_FORMAT, index, immediate);
    result = os::attempt_reserve_memory_at((char*)immediate, size, false);
    if (result == nullptr) {
      log_trace(metaspace, map)("Failed to attach at " UINT64_FORMAT_X, immediate);
    }
  }
  if (result == nullptr) {
    log_debug(metaspace, map)("Failed to reserve at any EOR-compatible address");
  }
  return result;
}
char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  // Optimize for base=0 shift=0
  if (optimize_for_zero_base) {
    result = reserve_address_space_for_unscaled_encoding(size, aslr);
  }

  // If this fails, we don't bother aiming for zero-based encoding (base=0 shift>0), since it has no
  // advantages over EOR or movk mode.

  // EOR-compatible reservation
  if (result == nullptr) {
    result = reserve_at_eor_compatible_address(size, aslr);
  }

  // Movk-compatible reservation via probing.
  if (result == nullptr) {
    result = reserve_address_space_for_16bit_move(size, aslr);
  }

  // Movk-compatible reservation via overallocation.
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
