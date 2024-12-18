/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/contiguousAllocator.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"

char* NMTContiguousAllocator::reserve_virtual_address_range() {
  char* addr = os::pd_reserve_memory(_size, false);
  assert(addr == nullptr || is_aligned(addr, _chunk_size), "must be");

  return addr;
}

char* NMTContiguousAllocator::allocate_chunk(size_t requested_size) {
  char* next_offset = this->_offset + requested_size;

  if (next_offset > _start + this->_size) {
    return nullptr;
  }

  if (next_offset <= _committed_boundary) {
    char* addr = _offset;
    this->_offset = next_offset;
    return addr;
  }
  // Commit the missing amount of memory in page-sized chunks
  size_t bytes_available = _committed_boundary - _offset;
  size_t chunk_size_missing = align_up(requested_size - bytes_available, _chunk_size);

  bool success = os::pd_commit_memory(this->_committed_boundary, chunk_size_missing, false);
  if (!success) {
    return nullptr;
  }

  this->_committed_boundary += chunk_size_missing;

  char* addr = this->_offset;
  this->_offset = next_offset;
  return addr;
}

NMTContiguousAllocator::~NMTContiguousAllocator() {
  if (is_reserved()) {
    unreserve();
  }
}
