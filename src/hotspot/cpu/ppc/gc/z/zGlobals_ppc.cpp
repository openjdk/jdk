/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 SAP SE. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zGlobals.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include <cstddef>

#ifdef LINUX
#include <sys/mman.h>
#endif // LINUX

//
// The overall memory layouts across different power platforms are similar and only differ with regards to
// the position of the highest addressable bit; the position of the metadata bits and the size of the actual
// addressable heap address space are adjusted accordingly.
//
// The following memory schema shows an exemplary layout in which bit '45' is the highest addressable bit.
// It is assumed that this virtual memroy address space layout is predominant on the power platform.
//
// Standard Address Space & Pointer Layout
// ---------------------------------------
//
//  +--------------------------------+ 0x00007FFFFFFFFFFF (127 TiB - 1)
//  .                                .
//  .                                .
//  .                                .
//  +--------------------------------+ 0x0000140000000000 (20 TiB)
//  |         Remapped View          |
//  +--------------------------------+ 0x0000100000000000 (16 TiB)
//  .                                .
//  +--------------------------------+ 0x00000c0000000000 (12 TiB)
//  |         Marked1 View           |
//  +--------------------------------+ 0x0000080000000000 (8  TiB)
//  |         Marked0 View           |
//  +--------------------------------+ 0x0000040000000000 (4  TiB)
//  .                                .
//  +--------------------------------+ 0x0000000000000000
//
//   6                  4 4  4 4
//   3                  6 5  2 1                                             0
//  +--------------------+----+-----------------------------------------------+
//  |00000000 00000000 00|1111|11 11111111 11111111 11111111 11111111 11111111|
//  +--------------------+----+-----------------------------------------------+
//  |                    |    |
//  |                    |    * 41-0 Object Offset (42-bits, 4TB address space)
//  |                    |
//  |                    * 45-42 Metadata Bits (4-bits)  0001 = Marked0      (Address view 4-8TB)
//  |                                                    0010 = Marked1      (Address view 8-12TB)
//  |                                                    0100 = Remapped     (Address view 16-20TB)
//  |                                                    1000 = Finalizable  (Address view N/A)
//  |
//  * 63-46 Fixed (18-bits, always zero)
//

// Maximum value as per spec (Power ISA v2.07): 2 ^ 60 bytes, i.e. 1 EiB (exbibyte)
static const unsigned int MAXIMUM_MAX_ADDRESS_BIT = 60;

// Most modern power processors provide an address space with not more than 45 bit addressable bit,
// that is an address space of 32 TiB in size.
static const unsigned int DEFAULT_MAX_ADDRESS_BIT = 45;

// Minimum value returned, if probing fails: 64 GiB
static const unsigned int MINIMUM_MAX_ADDRESS_BIT = 36;

// Determines the highest addressable bit of the virtual address space (depends on platform)
// by trying to interact with memory in that address range,
// i.e. by syncing existing mappings (msync) or by temporarily mapping the memory area (mmap).
// If one of those operations succeeds, it is proven that the targeted memory area is within the virtual address space.
//
// To reduce the number of required system calls to a bare minimum, the DEFAULT_MAX_ADDRESS_BIT is intentionally set
// lower than what the ABI would theoretically permit.
// Such an avoidance strategy, however, might impose unnecessary limits on processors that exceed this limit.
// If DEFAULT_MAX_ADDRESS_BIT is addressable, the next higher bit will be tested as well to ensure that
// the made assumption does not artificially restrict the memory availability.
static unsigned int probe_valid_max_address_bit(size_t init_bit, size_t min_bit) {
  assert(init_bit >= min_bit, "Sanity");
  assert(init_bit <= MAXIMUM_MAX_ADDRESS_BIT, "Test bit is outside the assumed address space range");

#ifdef LINUX
  unsigned int max_valid_address_bit = 0;
  void* last_allocatable_address = nullptr;

  const unsigned int page_size = os::vm_page_size();

  for (size_t i = init_bit; i >= min_bit; --i) {
    void* base_addr = (void*) (((unsigned long) 1U) << i);

    /* ==== Try msync-ing already mapped memory page ==== */
    if (msync(base_addr, page_size, MS_ASYNC) == 0) {
      // The page of the given address was synced by the linux kernel and must thus be both, mapped and valid.
      max_valid_address_bit = i;
      break;
    }
    if (errno != ENOMEM) {
      // An unexpected error occurred, i.e. an error not indicating that the targeted memory page is unmapped,
      // but pointing out another type of issue.
      // Even though this should never happen, those issues may come up due to undefined behavior.
#ifdef ASSERT
      fatal("Received '%s' while probing the address space for the highest valid bit", os::errno_name(errno));
#else // ASSERT
      log_warning_p(gc)("Received '%s' while probing the address space for the highest valid bit", os::errno_name(errno));
#endif // ASSERT
      continue;
    }

    /* ==== Try mapping memory page on our own ==== */
    last_allocatable_address = mmap(base_addr, page_size, PROT_NONE, MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE, -1, 0);
    if (last_allocatable_address != MAP_FAILED) {
      munmap(last_allocatable_address, page_size);
    }

    if (last_allocatable_address == base_addr) {
      // As the linux kernel mapped exactly the page we have requested, the address must be valid.
      max_valid_address_bit = i;
      break;
    }

    log_info_p(gc, init)("Probe failed for bit '%zu'", i);
  }

  if (max_valid_address_bit == 0) {
    // Probing did not bring up any usable address bit.
    // As an alternative, the VM evaluates the address returned by mmap as it is expected that the reserved page
    // will be close to the probed address that was out-of-range.
    // As per mmap(2), "the kernel [will take] [the address] as a hint about where to
    // place the mapping; on Linux, the mapping will be created at a nearby page boundary".
    // It should thus be a "close enough" approximation to the real virtual memory address space limit.
    //
    // This recovery strategy is only applied in production builds.
    // In debug builds, an assertion in 'ZPlatformAddressOffsetBits' will bail out the VM to indicate that
    // the assumed address space is no longer up-to-date.
    if (last_allocatable_address != MAP_FAILED) {
      const unsigned int bitpos = BitsPerSize_t - count_leading_zeros((size_t) last_allocatable_address) - 1;
      log_info_p(gc, init)("Did not find any valid addresses within the range, using address '%u' instead", bitpos);
      return bitpos;
    }

#ifdef ASSERT
    fatal("Available address space can not be determined");
#else // ASSERT
    log_warning_p(gc)("Cannot determine available address space. Falling back to default value.");
    return DEFAULT_MAX_ADDRESS_BIT;
#endif // ASSERT
  } else {
    if (max_valid_address_bit == init_bit) {
      // An usable address bit has been found immediately.
      // To ensure that the entire virtual address space is exploited, the next highest bit will be tested as well.
      log_info_p(gc, init)("Hit valid address '%u' on first try, retrying with next higher bit", max_valid_address_bit);
      return MAX2(max_valid_address_bit, probe_valid_max_address_bit(init_bit + 1, init_bit + 1));
    }
  }

  log_info_p(gc, init)("Found valid address '%u'", max_valid_address_bit);
  return max_valid_address_bit;
#else // LINUX
  return DEFAULT_MAX_ADDRESS_BIT;
#endif // LINUX
}

size_t ZPlatformAddressOffsetBits() {
  const static unsigned int valid_max_address_offset_bits =
      probe_valid_max_address_bit(DEFAULT_MAX_ADDRESS_BIT, MINIMUM_MAX_ADDRESS_BIT) + 1;
  assert(valid_max_address_offset_bits >= MINIMUM_MAX_ADDRESS_BIT,
         "Highest addressable bit is outside the assumed address space range");

  const size_t max_address_offset_bits = valid_max_address_offset_bits - 3;
  const size_t min_address_offset_bits = max_address_offset_bits - 2;
  const size_t address_offset = round_up_power_of_2(MaxHeapSize * ZVirtualToPhysicalRatio);
  const size_t address_offset_bits = log2i_exact(address_offset);

  return clamp(address_offset_bits, min_address_offset_bits, max_address_offset_bits);
}

size_t ZPlatformAddressMetadataShift() {
  return ZPlatformAddressOffsetBits();
}
