/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zBackingFile_windows.hpp"
#include "gc/z/zMemory.hpp"

class ZPhysicalMemory;

class ZPhysicalMemoryBacking {
private:
  ZBackingFile   _file;
  ZMemoryManager _committed;
  ZMemoryManager _uncommitted;

  void pretouch_view(uintptr_t addr, size_t size) const;
  void map_view(const ZPhysicalMemory& pmem, uintptr_t addr) const;
  void unmap_view(const ZPhysicalMemory& pmem, uintptr_t addr) const;

public:
  bool is_initialized() const;

  void warn_commit_limits(size_t max) const;
  bool supports_uncommit();

  size_t commit(size_t size);
  size_t uncommit(size_t size);

  ZPhysicalMemory alloc(size_t size);
  void free(const ZPhysicalMemory& pmem);

  uintptr_t nmt_address(uintptr_t offset) const;

  void pretouch(uintptr_t offset, size_t size) const;

  void map(const ZPhysicalMemory& pmem, uintptr_t offset) const;
  void unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const;

  void debug_map(const ZPhysicalMemory& pmem, uintptr_t offset) const;
  void debug_unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const;
};

#endif // OS_WINDOWS_GC_Z_ZPHYSICALMEMORYBACKING_WINDOWS_HPP
