/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XVIRTUALMEMORY_INLINE_HPP
#define SHARE_GC_X_XVIRTUALMEMORY_INLINE_HPP

#include "gc/x/xVirtualMemory.hpp"

#include "gc/x/xMemory.inline.hpp"

inline XVirtualMemory::XVirtualMemory() :
    _start(UINTPTR_MAX),
    _end(UINTPTR_MAX) {}

inline XVirtualMemory::XVirtualMemory(uintptr_t start, size_t size) :
    _start(start),
    _end(start + size) {}

inline bool XVirtualMemory::is_null() const {
  return _start == UINTPTR_MAX;
}

inline uintptr_t XVirtualMemory::start() const {
  return _start;
}

inline uintptr_t XVirtualMemory::end() const {
  return _end;
}

inline size_t XVirtualMemory::size() const {
  return _end - _start;
}

inline XVirtualMemory XVirtualMemory::split(size_t size) {
  _start += size;
  return XVirtualMemory(_start - size, size);
}

inline size_t XVirtualMemoryManager::reserved() const {
  return _reserved;
}

inline uintptr_t XVirtualMemoryManager::lowest_available_address() const {
  return _manager.peek_low_address();
}

#endif // SHARE_GC_X_XVIRTUALMEMORY_INLINE_HPP
