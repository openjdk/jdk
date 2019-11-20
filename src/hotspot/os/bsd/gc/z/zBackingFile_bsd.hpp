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

#ifndef OS_BSD_GC_Z_ZBACKINGFILE_BSD_HPP
#define OS_BSD_GC_Z_ZBACKINGFILE_BSD_HPP

#include "memory/allocation.hpp"

class ZPhysicalMemory;

// On macOS, we use a virtual backing file. It is represented by a reserved virtual
// address space, in which we commit physical memory using the mach_vm_map() API.
// The multi-mapping API simply remaps these addresses using mach_vm_remap() into
// the different heap views. This works as-if there was a backing file, it's just
// that the file is represented with memory mappings instead.

class ZBackingFile {
private:
  uintptr_t _base;
  size_t    _size;
  bool      _initialized;

  bool commit_inner(size_t offset, size_t length);

public:
  ZBackingFile();

  bool is_initialized() const;

  size_t size() const;

  size_t commit(size_t offset, size_t length);
  size_t uncommit(size_t offset, size_t length);

  void map(uintptr_t addr, size_t size, uintptr_t offset) const;
  void unmap(uintptr_t addr, size_t size) const;
};

#endif // OS_BSD_GC_Z_ZBACKINGFILE_BSD_HPP
