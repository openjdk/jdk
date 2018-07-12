/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_X86_ZBACKINGFILE_LINUX_X86_HPP
#define OS_CPU_LINUX_X86_ZBACKINGFILE_LINUX_X86_HPP

#include "memory/allocation.hpp"

class ZBackingFile {
private:
  static bool _hugetlbfs_mmap_retry;

  int      _fd;
  uint64_t _filesystem;
  size_t   _available;
  bool     _initialized;

  int create_mem_fd(const char* name) const;
  int create_file_fd(const char* name) const;
  int create_fd(const char* name) const;

  bool is_tmpfs() const;
  bool is_hugetlbfs() const;
  bool tmpfs_supports_transparent_huge_pages() const;

  bool try_split_and_expand_tmpfs(size_t offset, size_t length, size_t alignment) const;
  bool try_expand_tmpfs(size_t offset, size_t length, size_t alignment) const;
  bool try_expand_tmpfs(size_t offset, size_t length) const;
  bool try_expand_hugetlbfs(size_t offset, size_t length) const;
  bool try_expand_tmpfs_or_hugetlbfs(size_t offset, size_t length, size_t alignment) const;

public:
  ZBackingFile();

  bool is_initialized() const;

  int fd() const;
  size_t available() const;

  size_t try_expand(size_t offset, size_t length, size_t alignment) const;
};

#endif // OS_CPU_LINUX_X86_ZBACKINGFILE_LINUX_X86_HPP
