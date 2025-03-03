/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP
#define SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP

#include "gc/z/zVirtualMemory.hpp"

#include "gc/z/zMemory.inline.hpp"

inline ZVirtualMemory::ZVirtualMemory()
  : _start(zoffset(UINTPTR_MAX)),
    _end(zoffset_end(UINTPTR_MAX)) {}

inline ZVirtualMemory::ZVirtualMemory(zoffset start, size_t size)
  : _start(start),
    _end(to_zoffset_end(start, size)) {}

inline bool ZVirtualMemory::is_null() const {
  return _start == zoffset(UINTPTR_MAX);
}

inline zoffset ZVirtualMemory::start() const {
  return _start;
}

inline zoffset_end ZVirtualMemory::end() const {
  return _end;
}

inline size_t ZVirtualMemory::size() const {
  return _end - _start;
}

inline ZVirtualMemory ZVirtualMemory::split(size_t size) {
  _start += size;
  return ZVirtualMemory(_start - size, size);
}

inline size_t ZVirtualMemoryManager::reserved() const {
  return _reserved;
}

inline zoffset ZVirtualMemoryManager::lowest_available_address() const {
  return _manager.peek_low_address();
}

inline zoffset_end ZVirtualMemoryManager::highest_available_address_end() const {
  return _manager.peak_high_address_end();
}

#endif // SHARE_GC_Z_ZVIRTUALMEMORY_INLINE_HPP
