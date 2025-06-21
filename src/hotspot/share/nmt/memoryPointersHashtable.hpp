/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_POINTERS_HASHTABLE_HPP
#define SHARE_MEMORY_POINTERS_HASHTABLE_HPP

#include "utilities/debug.hpp"
#include "utilities/concurrentHashTable.hpp"
#include "nmt/nmtCommon.hpp"

class MemoryPointersHashtable : public CHeapObj<mtNMT_MP> {
  int _number_of_entries;

  class Config {
   public:
    using Value = void*;
    static uintx get_hash(Value const& value, bool* is_dead);
    static void* allocate_node(void* context, size_t size, Value const& value);
    static void free_node(void* context, void* memory, Value const& value);
  };

  using ConcurrentTable = ConcurrentHashTable<Config, mtNMT_MP>;
  ConcurrentTable* _local_table;

  bool check_if_needs_resize();
  int table_size() const;

public:
  MemoryPointersHashtable(size_t table_size);
  ~MemoryPointersHashtable();
  static void createMemoryPointersHashtable();
  static bool record_alloc(MemTag mem_tag, void* ptr);
  static bool record_free(void* ptr);
  
  void add_ptr(Thread* current, void* ptr);
  void remove_ptr(Thread* current, void* ptr);

  void* find_pointer(Thread* current, void* ptr);

  void pointers_do(void f(void*));

  void print_table_statistics(outputStream* st, const char* table_name);

  void print_on(outputStream* st) const;
  void print_size(outputStream* st) const;
  void verify();
};

#endif // SHARE_MEMORY_POINTERS_HASHTABLE_HPP
