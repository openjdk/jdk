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
#include "oops/compressedKlass.hpp"
#include "utilities/globalDefinitions.hpp"

char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  // RiscV loads a 64-bit immediate in up to four separate steps, splitting it into four different sections
  // (two 32-bit sections, each split into two subsections of 20/12 bits).
  //
  // 63 ....... 44 43 ... 32 31 ....... 12 11 ... 0
  //       D           C          B           A
  //
  // A "good" base is, in this order:
  // 1) only bits in A; this would be an address < 4KB, which is unrealistic on normal Linux boxes since
  //    the typical default for vm.mmap_min_address is 64KB. We ignore that.
  // 2) only bits in B: a 12-bit-aligned address below 4GB. 12 bit = 4KB, but since mmap reserves at
  //    page boundaries, we can ignore the alignment.
  // 3) only bits in C: a 4GB-aligned address that is lower than 16TB.
  // 4) only bits in D: a 16TB-aligned address.

  // First, attempt to allocate < 4GB. We do this unconditionally:
  // - if can_optimize_for_zero_base, a <4GB mapping start would allow us to run unscaled (base = 0, shift = 0)
  // - if !can_optimize_for_zero_base, a <4GB mapping start is still good, the resulting immediate can be encoded
  //   with one instruction (2)
  result = reserve_address_space_for_unscaled_encoding(size, aslr);

  // Failing that, attempt to reserve for base=zero shift>0
  if (result == nullptr && optimize_for_zero_base) {
    result = reserve_address_space_for_zerobased_encoding(size, aslr);
  }

  // Failing that, optimize for case (3) - a base with only bits set between [33-44)
  if (result == nullptr) {
    const uintptr_t from = nth_bit(32 + (optimize_for_zero_base ? LogKlassAlignmentInBytes : 0));
    constexpr uintptr_t to = nth_bit(44);
    constexpr size_t alignment = nth_bit(32);
    result = reserve_address_space_X(from, to, size, alignment, aslr);
  }

  // Failing that, optimize for case (4) - a base with only bits set between [44-64)
  if (result == nullptr) {
    constexpr uintptr_t from = nth_bit(44);
    constexpr uintptr_t to = UINT64_MAX;
    constexpr size_t alignment = nth_bit(44);
    result = reserve_address_space_X(from, to, size, alignment, aslr);
  }

  return result;
}
