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

#ifndef OS_WINDOWS_GC_Z_ZBACKINGFILE_WINDOWS_HPP
#define OS_WINDOWS_GC_Z_ZBACKINGFILE_WINDOWS_HPP

#include "gc/z/zGranuleMap.hpp"
#include "memory/allocation.hpp"

#include <Windows.h>

class ZBackingFile {
private:
  ZGranuleMap<HANDLE> _handles;
  size_t              _size;

  HANDLE get_handle(uintptr_t offset) const;
  void put_handle(uintptr_t offset, HANDLE handle);
  void clear_handle(uintptr_t offset);

  size_t commit_from_paging_file(size_t offset, size_t size);
  size_t uncommit_from_paging_file(size_t offset, size_t size);

public:
  ZBackingFile();

  size_t size() const;

  size_t commit(size_t offset, size_t length);
  size_t uncommit(size_t offset, size_t length);

  void map(uintptr_t addr, size_t size, size_t offset) const;
  void unmap(uintptr_t addr, size_t size) const;
};

#endif // OS_WINDOWS_GC_Z_ZBACKINGFILE_WINDOWS_HPP
