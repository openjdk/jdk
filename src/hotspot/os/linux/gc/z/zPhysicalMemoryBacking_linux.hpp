/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_HPP
#define OS_LINUX_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_HPP

#include "gc/z/zAddress.hpp"

class ZErrno;

class ZPhysicalMemoryBacking {
private:
  int      _fd;
  size_t   _size;
  uint64_t _filesystem;
  size_t   _block_size;
  size_t   _available;
  bool     _initialized;

  void warn_available_space(size_t max_capacity) const;
  void warn_max_map_count(size_t max_capacity) const;

  int create_mem_fd(const char* name) const;
  int create_file_fd(const char* name) const;
  int create_fd(const char* name) const;

  bool is_tmpfs() const;
  bool is_hugetlbfs() const;
  bool tmpfs_supports_transparent_huge_pages() const;

  ZErrno fallocate_compat_mmap_hugetlbfs(zbacking_offset offset, size_t length, bool touch) const;
  ZErrno fallocate_compat_mmap_tmpfs(zbacking_offset offset, size_t length) const;
  ZErrno fallocate_compat_pwrite(zbacking_offset offset, size_t length) const;
  ZErrno fallocate_fill_hole_compat(zbacking_offset offset, size_t length) const;
  ZErrno fallocate_fill_hole_syscall(zbacking_offset offset, size_t length) const;
  ZErrno fallocate_fill_hole(zbacking_offset offset, size_t length) const;
  ZErrno fallocate_punch_hole(zbacking_offset offset, size_t length) const;
  ZErrno split_and_fallocate(bool punch_hole, zbacking_offset offset, size_t length) const;
  ZErrno fallocate(bool punch_hole, zbacking_offset offset, size_t length) const;

  bool commit_inner(zbacking_offset offset, size_t length) const;
  size_t commit_numa_preferred(zbacking_offset offset, size_t length, uint32_t numa_id) const;
  size_t commit_default(zbacking_offset offset, size_t length) const;

public:
  ZPhysicalMemoryBacking(size_t max_capacity);

  bool is_initialized() const;

  void warn_commit_limits(size_t max_capacity) const;

  size_t commit(zbacking_offset offset, size_t length, uint32_t numa_id) const;
  size_t uncommit(zbacking_offset offset, size_t length) const;

  void map(zaddress_unsafe addr, size_t size, zbacking_offset offset) const;
  void unmap(zaddress_unsafe addr, size_t size) const;
};

#endif // OS_LINUX_GC_Z_ZPHYSICALMEMORYBACKING_LINUX_HPP
