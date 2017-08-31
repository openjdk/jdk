/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.hpp"

void SafepointMechanism::pd_initialize() {
  char* map_address = MAP_FAILED;
  size_t page_size = os::vm_page_size();
  // Use optimized addresses for the polling page,
  // e.g. map it to a special 32-bit address.
  if (OptimizePollingPageLocation) {
    // architecture-specific list of address wishes:
    char* address_wishes[] = {
        // AIX: addresses lower than 0x30000000 don't seem to work on AIX.
        // PPC64: all address wishes are non-negative 32 bit values where
        // the lower 16 bits are all zero. we can load these addresses
        // with a single ppc_lis instruction.
        (address) 0x30000000, (address) 0x31000000,
        (address) 0x32000000, (address) 0x33000000,
        (address) 0x40000000, (address) 0x41000000,
        (address) 0x42000000, (address) 0x43000000,
        (address) 0x50000000, (address) 0x51000000,
        (address) 0x52000000, (address) 0x53000000,
        (address) 0x60000000, (address) 0x61000000,
        (address) 0x62000000, (address) 0x63000000
    };
    int address_wishes_length = sizeof(address_wishes)/sizeof(address);

    // iterate over the list of address wishes:
    for (int i=0; i<address_wishes_length; i++) {
      // Try to map with current address wish.
      // AIX: AIX needs MAP_FIXED if we provide an address and mmap will
      // fail if the address is already mapped.
      map_address = os::attempt_reserve_memory_at(page_size, address_wishes[i] - page_size);
      log_debug(os)("SafePoint Polling  Page address: %p (wish) => %p",
          address_wishes[i], map_address + (ssize_t)page_size);

      if (map_address + (ssize_t)page_size == address_wishes[i]) {
        // Map succeeded and map_address is at wished address, exit loop.
        break;
      }

      if (map_address != (address) MAP_FAILED) {
        // Map succeeded, but polling_page is not at wished address, unmap and continue.
        os::release_memory(map_address, page_size);
        map_address = (address) MAP_FAILED;
      }
      // Map failed, continue loop.
    }
  }
  if (map_address == (address) MAP_FAILED) {
    map_address = os::reserve_memory(page_size, NULL, page_size);
  }
  guarantee(map_address != MAP_FAILED, "SafepointMechanism::pd_initialize: failed to allocate polling page");
  os::commit_memory_or_exit(map_address, page_size, false, "Unable to commit memory for polling page");
  os::set_polling_page((address)(map_address));
}
