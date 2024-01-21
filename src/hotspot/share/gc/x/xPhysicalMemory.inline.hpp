/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XPHYSICALMEMORY_INLINE_HPP
#define SHARE_GC_X_XPHYSICALMEMORY_INLINE_HPP

#include "gc/x/xPhysicalMemory.hpp"

#include "gc/x/xAddress.inline.hpp"
#include "utilities/debug.hpp"

inline XPhysicalMemorySegment::XPhysicalMemorySegment() :
    _start(UINTPTR_MAX),
    _end(UINTPTR_MAX),
    _committed(false) {}

inline XPhysicalMemorySegment::XPhysicalMemorySegment(uintptr_t start, size_t size, bool committed) :
    _start(start),
    _end(start + size),
    _committed(committed) {}

inline uintptr_t XPhysicalMemorySegment::start() const {
  return _start;
}

inline uintptr_t XPhysicalMemorySegment::end() const {
  return _end;
}

inline size_t XPhysicalMemorySegment::size() const {
  return _end - _start;
}

inline bool XPhysicalMemorySegment::is_committed() const {
  return _committed;
}

inline void XPhysicalMemorySegment::set_committed(bool committed) {
  _committed = committed;
}

inline bool XPhysicalMemory::is_null() const {
  return _segments.length() == 0;
}

inline int XPhysicalMemory::nsegments() const {
  return _segments.length();
}

inline const XPhysicalMemorySegment& XPhysicalMemory::segment(int index) const {
  return _segments.at(index);
}

#endif // SHARE_GC_X_XPHYSICALMEMORY_INLINE_HPP
