/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/psVirtualspace.hpp"
#include "logging/log.hpp"
#include "memory/reservedSpace.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"

PSVirtualSpace::PSVirtualSpace(ReservedSpace rs, size_t alignment) :
  _alignment(alignment),
  _page_size(rs.page_size()) {
  set_reserved(rs);
  set_committed(reserved_low_addr(), reserved_low_addr());
  DEBUG_ONLY(verify());
}

PSVirtualSpace::~PSVirtualSpace() {
  release();
}

void PSVirtualSpace::release() {
  DEBUG_ONLY(PSVirtualSpaceVerifier this_verifier(this));
  // This may not release memory it didn't reserve.
  // Use rs.release() to release the underlying memory instead.
  _reserved_low_addr = _reserved_high_addr = nullptr;
  _committed_low_addr = _committed_high_addr = nullptr;
  _special = false;
}

bool PSVirtualSpace::expand_by(size_t bytes) {
  assert(is_aligned(bytes, _alignment), "arg not aligned");
  DEBUG_ONLY(PSVirtualSpaceVerifier this_verifier(this));

  if (uncommitted_size() < bytes) {
    return false;
  }

  char* const base_addr = committed_high_addr();
  bool result = special() ||
         os::commit_memory(base_addr, bytes, alignment(), !ExecMem);
  if (result) {
    _committed_high_addr += bytes;
  } else {
    log_warning(gc)("PSVirtualSpace::expand_by: to commit %zu bytes failed", bytes);
  }

  return result;
}

bool PSVirtualSpace::shrink_by(size_t bytes) {
  assert(is_aligned(bytes, _alignment), "arg not aligned");
  DEBUG_ONLY(PSVirtualSpaceVerifier this_verifier(this));

  if (committed_size() < bytes) {
    return false;
  }

  char* const base_addr = committed_high_addr() - bytes;
  if (!special()) {
    os::uncommit_memory(base_addr, bytes);
  }

  _committed_high_addr -= bytes;

  return true;
}

#ifndef PRODUCT
void PSVirtualSpace::verify() const {
  assert(is_aligned(_page_size, os::vm_page_size()), "bad alignment");
  assert(is_aligned(_alignment, _page_size), "inv");
  assert(is_aligned(reserved_low_addr(), _alignment), "bad reserved_low_addr");
  assert(is_aligned(reserved_high_addr(), _alignment), "bad reserved_high_addr");
  assert(is_aligned(committed_low_addr(), _alignment), "bad committed_low_addr");
  assert(is_aligned(committed_high_addr(), _alignment), "bad committed_high_addr");

  // Reserved region must be non-empty or both addrs must be 0.
  assert(reserved_low_addr() < reserved_high_addr() ||
         (reserved_low_addr() == nullptr && reserved_high_addr() == nullptr),
         "bad reserved addrs");
  assert(committed_low_addr() <= committed_high_addr(), "bad committed addrs");

  // committed addr grows up
  assert(reserved_low_addr() == committed_low_addr(), "bad low addrs");
  assert(reserved_high_addr() >= committed_high_addr(), "bad high addrs");
}

#endif // #ifndef PRODUCT

void PSVirtualSpace::print_space_boundaries_on(outputStream* st) const {
  st->print_cr("[" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
               p2i(low_boundary()), p2i(high()), p2i(high_boundary()));
}
