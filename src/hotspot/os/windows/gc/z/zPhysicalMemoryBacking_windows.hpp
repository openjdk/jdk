/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_WINDOWS_GC_Z_ZPHYSICALMEMORYBACKING_WINDOWS_HPP
#define OS_WINDOWS_GC_Z_ZPHYSICALMEMORYBACKING_WINDOWS_HPP

#include "gc/z/zAddress.hpp"
#include "utilities/globalDefinitions.hpp"

#include <Windows.h>

class ZPhysicalMemoryBackingImpl;

class ZPhysicalMemoryBacking {
private:
  ZPhysicalMemoryBackingImpl* _impl;

public:
  ZPhysicalMemoryBacking(size_t max_capacity);

  bool is_initialized() const;

  void warn_commit_limits(size_t max_capacity) const;

  size_t commit(zbacking_offset offset, size_t length, uint32_t numa_id);
  size_t uncommit(zbacking_offset offset, size_t length);

  void map(zaddress_unsafe addr, size_t size, zbacking_offset offset) const;
  void unmap(zaddress_unsafe addr, size_t size) const;
};

#endif // OS_WINDOWS_GC_Z_ZPHYSICALMEMORYBACKING_WINDOWS_HPP
