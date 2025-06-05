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

#ifndef SHARE_GC_Z_ZUTILS_HPP
#define SHARE_GC_Z_ZUTILS_HPP

#include "gc/z/zAddress.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class ZUtils : public AllStatic {
public:
  // Thread
  static const char* thread_name();

  // Allocation
  static uintptr_t alloc_aligned_unfreeable(size_t alignment, size_t size);

  // Size conversion
  static size_t bytes_to_words(size_t size_in_words);
  static size_t words_to_bytes(size_t size_in_words);

  // Object
  static size_t object_size(zaddress addr);
  static void object_copy_disjoint(zaddress from, zaddress to, size_t size);
  static void object_copy_conjoint(zaddress from, zaddress to, size_t size);

  // Memory
  static void fill(uintptr_t* addr, size_t count, uintptr_t value);
  template <typename T>
  static void copy_disjoint(T* dest, const T* src, size_t count);
  template <typename T>
  static void copy_disjoint(T* dest, const T* src, int count);

  // Sort
  template <typename T, typename Comparator>
  static void sort(T* array, size_t count, Comparator comparator);
  template <typename T, typename Comparator>
  static void sort(T* array, int count, Comparator comparator);
};

#endif // SHARE_GC_Z_ZUTILS_HPP
