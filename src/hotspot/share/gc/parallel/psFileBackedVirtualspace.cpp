/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/psFileBackedVirtualspace.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/os.inline.hpp"

PSFileBackedVirtualSpace::PSFileBackedVirtualSpace(ReservedSpace rs, size_t alignment, const char* path) : PSVirtualSpace(rs, alignment),
                                                   _file_path(path), _fd(-1), _mapping_succeeded(false) {
  assert(!rs.special(), "ReservedSpace passed to PSFileBackedVirtualSpace cannot be special");
}

bool PSFileBackedVirtualSpace::initialize() {
  _fd = os::create_file_for_heap(_file_path);
  if (_fd == -1) {
    return false;
  }
  // We map the reserved space to a file at initialization.
  char* ret = os::replace_existing_mapping_with_file_mapping(reserved_low_addr(), reserved_size(), _fd);
  if (ret != reserved_low_addr()) {
    os::close(_fd);
    return false;
  }
  // _mapping_succeeded is false if we return before this point.
  // expand calls later check value of this flag and return error if it is false.
  _mapping_succeeded = true;
  _special = true;
  os::close(_fd);
  return true;
}

PSFileBackedVirtualSpace::PSFileBackedVirtualSpace(ReservedSpace rs, const char* path) {
  PSFileBackedVirtualSpace(rs, os::vm_page_size(), path);
}

bool PSFileBackedVirtualSpace::expand_by(size_t bytes) {
  assert(special(), "Since entire space is committed at initialization, _special should always be true for PSFileBackedVirtualSpace");

  // if mapping did not succeed during intialization return false
  if (!_mapping_succeeded) {
    return false;
  }
  return PSVirtualSpace::expand_by(bytes);

}

bool PSFileBackedVirtualSpace::shrink_by(size_t bytes) {
  assert(special(), "Since entire space is committed at initialization, _special should always be true for PSFileBackedVirtualSpace");
  return PSVirtualSpace::shrink_by(bytes);
}

size_t PSFileBackedVirtualSpace::expand_into(PSVirtualSpace* space, size_t bytes) {
  // not supported. Since doing this will change page mapping which will lead to large TLB penalties.
  assert(false, "expand_into() should not be called for PSFileBackedVirtualSpace");
  return 0;
}

void PSFileBackedVirtualSpace::release() {
  os::close(_fd);
  _fd = -1;
  _file_path = NULL;

  PSVirtualSpace::release();
}

