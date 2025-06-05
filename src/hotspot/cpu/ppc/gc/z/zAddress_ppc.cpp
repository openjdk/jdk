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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#ifdef LINUX
#include <sys/mman.h>
#endif // LINUX

// Default value if probing is not implemented for a certain platform
// Max address bit is restricted by implicit assumptions in the code, for instance
// the bit layout of ZForwardingEntry or Partial array entry (see ZMarkStackEntry) in mark stack
static const size_t DEFAULT_MAX_ADDRESS_BIT = 46;
// Minimum value returned, if probing fail
static const size_t MINIMUM_MAX_ADDRESS_BIT = 36;

static size_t probe_valid_max_address_bit() {
#ifdef LINUX
  size_t max_address_bit = 0;
  const size_t page_size = os::vm_page_size();
  for (size_t i = DEFAULT_MAX_ADDRESS_BIT; i > MINIMUM_MAX_ADDRESS_BIT; --i) {
    const uintptr_t base_addr = ((uintptr_t) 1U) << i;
    if (msync((void*)base_addr, page_size, MS_ASYNC) == 0) {
      // msync suceeded, the address is valid, and maybe even already mapped.
      max_address_bit = i;
      break;
    }
    if (errno != ENOMEM) {
      // Some error occured. This should never happen, but msync
      // has some undefined behavior, hence ignore this bit.
#ifdef ASSERT
      fatal("Received '%s' while probing the address space for the highest valid bit", os::errno_name(errno));
#else // ASSERT
      log_warning_p(gc)("Received '%s' while probing the address space for the highest valid bit", os::errno_name(errno));
#endif // ASSERT
      continue;
    }
    // Since msync failed with ENOMEM, the page might not be mapped.
    // Try to map it, to see if the address is valid.
    void* const result_addr = mmap((void*) base_addr, page_size, PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE, -1, 0);
    if (result_addr != MAP_FAILED) {
      munmap(result_addr, page_size);
    }
    if ((uintptr_t) result_addr == base_addr) {
      // address is valid
      max_address_bit = i;
      break;
    }
  }
  if (max_address_bit == 0) {
    // probing failed, allocate a very high page and take that bit as the maximum
    const uintptr_t high_addr = ((uintptr_t) 1U) << DEFAULT_MAX_ADDRESS_BIT;
    void* const result_addr = mmap((void*) high_addr, page_size, PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE, -1, 0);
    if (result_addr != MAP_FAILED) {
      max_address_bit = BitsPerSize_t - count_leading_zeros((size_t) result_addr) - 1;
      munmap(result_addr, page_size);
    }
  }
  log_info_p(gc, init)("Probing address space for the highest valid bit: %zu", max_address_bit);
  return MAX2(max_address_bit, MINIMUM_MAX_ADDRESS_BIT);
#else // LINUX
  return DEFAULT_MAX_ADDRESS_BIT;
#endif // LINUX
}

size_t ZPlatformAddressOffsetBits() {
  static const size_t valid_max_address_offset_bits = probe_valid_max_address_bit() + 1;
  const size_t max_address_offset_bits = valid_max_address_offset_bits - 3;
#ifdef ADDRESS_SANITIZER
  // The max supported value is 44 because of other internal data structures.
  return MIN2(valid_max_address_offset_bits, (size_t)44);
#else
  const size_t min_address_offset_bits = max_address_offset_bits - 2;
  const size_t address_offset = ZGlobalsPointers::min_address_offset_request();
  const size_t address_offset_bits = log2i_exact(address_offset);
  return clamp(address_offset_bits, min_address_offset_bits, max_address_offset_bits);
#endif
}

size_t ZPlatformAddressHeapBaseShift() {
  return ZPlatformAddressOffsetBits();
}

void ZGlobalsPointers::pd_set_good_masks() {
}
