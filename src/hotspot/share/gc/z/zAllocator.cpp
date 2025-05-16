/*
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

#include "gc/z/zAllocator.hpp"
#include "gc/z/zObjectAllocator.hpp"

ZAllocatorEden*          ZAllocator::_eden;
ZAllocatorForRelocation* ZAllocator::_relocation[ZAllocator::_relocation_allocators];

ZAllocator::ZAllocator(ZPageAge age)
  : _object_allocator(age) {}

void ZAllocator::retire_pages() {
  _object_allocator.retire_pages();
}

ZAllocatorEden::ZAllocatorEden()
  : ZAllocator(ZPageAge::eden) {
  ZAllocator::_eden = this;
}

size_t ZAllocatorEden::remaining() const {
  return _object_allocator.remaining();
}

ZPageAge ZAllocatorForRelocation::install() {
  for (uint i = 0; i < ZAllocator::_relocation_allocators; ++i) {
    if (_relocation[i] == nullptr) {
      _relocation[i] = this;
      return static_cast<ZPageAge>(i + 1);
    }
  }

  ShouldNotReachHere();
  return ZPageAge::eden;
}

ZAllocatorForRelocation::ZAllocatorForRelocation()
  : ZAllocator(install()) {}

zaddress ZAllocatorForRelocation::alloc_object(size_t size) {
  return _object_allocator.alloc_object_for_relocation(size);
}

void ZAllocatorForRelocation::undo_alloc_object(zaddress addr, size_t size) {
  _object_allocator.undo_alloc_object_for_relocation(addr, size);
}

ZPage* ZAllocatorForRelocation::alloc_page_for_relocation(ZPageType type, size_t size, ZAllocationFlags flags) {
  return _object_allocator.alloc_page_for_relocation(type, size, flags);
}
