/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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
 *
 */

#ifndef OS_LINUX_SHORTHIST_LINUX_HPP
#define OS_LINUX_SHORTHIST_LINUX_HPP

#include "utilities/globalDefinitions.hpp"

class ShortHistoryData_pd {
  size_t _vmsize;
  size_t _vmrss;
  size_t _vmhwm;
  size_t _vmswap;
  size_t _glibc_heap_allocated;
  size_t _glibc_heap_retained;
  int _glibc_num_trims;
  int _threads;
  int _fdsize;

  int num_open_files(bool& is_exact) const {
    if (_fdsize > 0) {
      is_exact = false;
      return -_fdsize;
    } else {
      is_exact = true;
      return _fdsize;
    }
  }
public:
  void measure();
  static void print_header_1(outputStream* st);
  static void print_header_2(outputStream* st);
  void print_on(outputStream* st) const;
};

#endif // OS_LINUX_SHORTHIST_LINUX_HPP
