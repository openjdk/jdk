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

#ifndef OS_CPU_LINUX_X86_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_X86_HPP
#define OS_CPU_LINUX_X86_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_X86_HPP

#include "gc/z/zBackingFile_linux_x86.hpp"
#include "gc/z/zMemory.hpp"

class ZErrno;
class ZPhysicalMemory;

class ZPhysicalMemoryBacking {
private:
  ZMemoryManager _manager;
  ZBackingFile   _file;

  void check_max_map_count(size_t max_capacity) const;
  void check_available_space_on_filesystem(size_t max_capacity) const;
  void map_failed(ZErrno err) const;

  void advise_view(uintptr_t addr, size_t size) const;
  void pretouch_view(uintptr_t addr, size_t size) const;
  void map_view(ZPhysicalMemory pmem, uintptr_t addr, bool pretouch) const;
  void unmap_view(ZPhysicalMemory pmem, uintptr_t addr) const;

public:
  ZPhysicalMemoryBacking(size_t max_capacity);

  bool is_initialized() const;

  size_t try_expand(size_t old_capacity, size_t new_capacity);

  ZPhysicalMemory alloc(size_t size);
  void free(ZPhysicalMemory pmem);

  uintptr_t nmt_address(uintptr_t offset) const;

  void map(ZPhysicalMemory pmem, uintptr_t offset) const;
  void unmap(ZPhysicalMemory pmem, uintptr_t offset) const;

  void debug_map(ZPhysicalMemory pmem, uintptr_t offset) const;
  void debug_unmap(ZPhysicalMemory pmem, uintptr_t offset) const;
};

#endif // OS_CPU_LINUX_X86_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_X86_HPP
