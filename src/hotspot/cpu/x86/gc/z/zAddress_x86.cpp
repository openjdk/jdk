/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

size_t ZPointerLoadShift;

size_t ZPlatformAddressOffsetBits() {
#ifdef ADDRESS_SANITIZER
  return 44;
#else
  const size_t min_address_offset_bits = 42; // 4TB
  const size_t max_address_offset_bits = 44; // 16TB
  const size_t address_offset = ZGlobalsPointers::min_address_offset_request();
  const size_t address_offset_bits = log2i_exact(address_offset);
  return clamp(address_offset_bits, min_address_offset_bits, max_address_offset_bits);
#endif
}

size_t ZPlatformAddressHeapBaseShift() {
  return ZPlatformAddressOffsetBits();
}

void ZGlobalsPointers::pd_set_good_masks() {
  ZPointerLoadShift = ZPointer::load_shift_lookup(ZPointerLoadGoodMask);
}
