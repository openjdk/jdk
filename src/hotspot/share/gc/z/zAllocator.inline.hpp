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

#ifndef SHARE_GC_Z_ZALLOCATOR_INLINE_HPP
#define SHARE_GC_Z_ZALLOCATOR_INLINE_HPP

#include "gc/z/zAllocator.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zPageAge.inline.hpp"
#include "gc/z/zHeap.hpp"

inline ZAllocatorEden* ZAllocator::eden() {
  return _eden;
}

inline ZAllocatorForRelocation* ZAllocator::relocation(ZPageAge page_age) {
  return _relocation[untype(page_age - 1)];
}

inline ZAllocatorForRelocation* ZAllocator::old() {
  return relocation(ZPageAge::old);
}

inline zaddress ZAllocatorEden::alloc_tlab(size_t size) {
  guarantee(size <= ZHeap::heap()->max_tlab_size(), "TLAB too large");
  return _object_allocator.alloc_object(size);
}

inline zaddress ZAllocatorEden::alloc_object(size_t size) {
  const zaddress addr = _object_allocator.alloc_object(size);

  if (is_null(addr)) {
    ZHeap::heap()->out_of_memory();
  }

  return addr;
}

#endif // SHARE_GC_Z_ZALLOCATOR_INLINE_HPP
