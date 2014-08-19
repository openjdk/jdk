/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1PageBasedVirtualSpace.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "services/memTracker.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "os_aix.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif
#include "utilities/bitMap.inline.hpp"

G1PageBasedVirtualSpace::G1PageBasedVirtualSpace() : _low_boundary(NULL),
  _high_boundary(NULL), _committed(), _page_size(0), _special(false), _executable(false) {
}

bool G1PageBasedVirtualSpace::initialize_with_granularity(ReservedSpace rs, size_t page_size) {
  if (!rs.is_reserved()) {
    return false;  // Allocation failed.
  }
  assert(_low_boundary == NULL, "VirtualSpace already initialized");
  assert(page_size > 0, "Granularity must be non-zero.");

  _low_boundary  = rs.base();
  _high_boundary = _low_boundary + rs.size();

  _special = rs.special();
  _executable = rs.executable();

  _page_size = page_size;

  assert(_committed.size() == 0, "virtual space initialized more than once");
  uintx size_in_bits = rs.size() / page_size;
  _committed.resize(size_in_bits, /* in_resource_area */ false);

  if (_special) {
    _committed.set_range(0, size_in_bits);
  }

  return true;
}


G1PageBasedVirtualSpace::~G1PageBasedVirtualSpace() {
  release();
}

void G1PageBasedVirtualSpace::release() {
  // This does not release memory it never reserved.
  // Caller must release via rs.release();
  _low_boundary           = NULL;
  _high_boundary          = NULL;
  _special                = false;
  _executable             = false;
  _page_size              = 0;
  _committed.resize(0, false);
}

size_t G1PageBasedVirtualSpace::committed_size() const {
  return _committed.count_one_bits() * _page_size;
}

size_t G1PageBasedVirtualSpace::reserved_size() const {
  return pointer_delta(_high_boundary, _low_boundary, sizeof(char));
}

size_t G1PageBasedVirtualSpace::uncommitted_size()  const {
  return reserved_size() - committed_size();
}

uintptr_t G1PageBasedVirtualSpace::addr_to_page_index(char* addr) const {
  return (addr - _low_boundary) / _page_size;
}

bool G1PageBasedVirtualSpace::is_area_committed(uintptr_t start, size_t size_in_pages) const {
  uintptr_t end = start + size_in_pages;
  return _committed.get_next_zero_offset(start, end) >= end;
}

bool G1PageBasedVirtualSpace::is_area_uncommitted(uintptr_t start, size_t size_in_pages) const {
  uintptr_t end = start + size_in_pages;
  return _committed.get_next_one_offset(start, end) >= end;
}

char* G1PageBasedVirtualSpace::page_start(uintptr_t index) {
  return _low_boundary + index * _page_size;
}

size_t G1PageBasedVirtualSpace::byte_size_for_pages(size_t num) {
  return num * _page_size;
}

MemRegion G1PageBasedVirtualSpace::commit(uintptr_t start, size_t size_in_pages) {
  // We need to make sure to commit all pages covered by the given area.
  guarantee(is_area_uncommitted(start, size_in_pages), "Specified area is not uncommitted");

  if (!_special) {
    os::commit_memory_or_exit(page_start(start), byte_size_for_pages(size_in_pages), _executable,
                              err_msg("Failed to commit pages from "SIZE_FORMAT" of length "SIZE_FORMAT, start, size_in_pages));
  }
  _committed.set_range(start, start + size_in_pages);

  MemRegion result((HeapWord*)page_start(start), byte_size_for_pages(size_in_pages) / HeapWordSize);
  return result;
}

MemRegion G1PageBasedVirtualSpace::uncommit(uintptr_t start, size_t size_in_pages) {
  guarantee(is_area_committed(start, size_in_pages), "checking");

  if (!_special) {
    os::uncommit_memory(page_start(start), byte_size_for_pages(size_in_pages));
  }

  _committed.clear_range(start, start + size_in_pages);

  MemRegion result((HeapWord*)page_start(start), byte_size_for_pages(size_in_pages) / HeapWordSize);
  return result;
}

bool G1PageBasedVirtualSpace::contains(const void* p) const {
  return _low_boundary <= (const char*) p && (const char*) p < _high_boundary;
}

#ifndef PRODUCT
void G1PageBasedVirtualSpace::print_on(outputStream* out) {
  out->print   ("Virtual space:");
  if (special()) out->print(" (pinned in memory)");
  out->cr();
  out->print_cr(" - committed: " SIZE_FORMAT, committed_size());
  out->print_cr(" - reserved:  " SIZE_FORMAT, reserved_size());
  out->print_cr(" - [low_b, high_b]: [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  p2i(_low_boundary), p2i(_high_boundary));
}

void G1PageBasedVirtualSpace::print() {
  print_on(tty);
}
#endif
