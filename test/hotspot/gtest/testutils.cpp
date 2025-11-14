/*
 * Copyright (c) 2021, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/ostream.hpp"

#include "testutils.hpp"
#include "unittest.hpp"

#include "os_bsd.hpp"
#include <string.h>

// Note: these could be made more suitable for covering large ranges (e.g. just mark one byte per page).

void GtestUtils::mark_range_with(void* p, size_t s, uint8_t mark) {
  if (p != nullptr && s > 0) {
    ::memset(p, mark, s);
  }
}

bool GtestUtils::is_range_marked(const void* p, size_t s, uint8_t expected) {
  if (p == nullptr || s == 0) {
    return true;
  }

  const char* first_wrong = nullptr;
  char* p2 = (char*)p;
  const char* const end = p2 + s;
  while (p2 < end) {
    if (*p2 != (char)expected) {
      first_wrong = p2;
      break;
    }
    p2 ++;
  }

  if (first_wrong != nullptr) {
    tty->print_cr("check_range [" PTR_FORMAT ".." PTR_FORMAT "), 0x%X, : wrong pattern around " PTR_FORMAT,
                  p2i(p), p2i(p) + s, expected, p2i(first_wrong));
    // Note: We deliberately print the surroundings too without bounds check. Might be interesting,
    // and os::print_hex_dump uses SafeFetch, so this is fine without bounds checks.
    os::print_hex_dump(tty, (address)(align_down(p2, 0x10) - 0x10),
                            (address)(align_up(end, 0x10) + 0x10), 1);
  }

  return first_wrong == nullptr;
}

#if APPLE_MEMORY_TAGGING_AVAILABLE
bool GtestUtils::is_memory_tagged_as_java(void* addr, size_t size) {
  // Use mach_vm_region with extended info to get the user_tag
  mach_vm_address_t address = (mach_vm_address_t)addr;
  mach_vm_size_t region_size = 0;
  vm_region_extended_info_data_t extended_info;
  mach_msg_type_number_t info_count = VM_REGION_EXTENDED_INFO_COUNT;
  mach_port_t object_name = MACH_PORT_NULL;

  kern_return_t kr = mach_vm_region(mach_task_self(),
                                    &address,
                                    &region_size,
                                    VM_REGION_EXTENDED_INFO,
                                    (vm_region_info_t)&extended_info,
                                    &info_count,
                                    &object_name);

  if (kr != KERN_SUCCESS) {
    return false;
  }

  // Check if the memory region covers our allocation and has the correct tag
  if (address <= (mach_vm_address_t)addr &&
      (address + region_size) >= ((mach_vm_address_t)addr + size)) {
    return extended_info.user_tag == VM_MEMORY_JAVA;
  }

  return false;
}
#endif // APPLE_MEMORY_TAGGING_AVAILABLE
