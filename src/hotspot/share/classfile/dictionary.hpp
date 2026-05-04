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

#ifndef SHARE_CLASSFILE_DICTIONARY_HPP
#define SHARE_CLASSFILE_DICTIONARY_HPP

#include "utilities/concurrentHashTable.hpp"

class ClassLoaderData;
class InstanceKlass;
class outputStream;

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// The data structure for the class loader data dictionaries.

class Dictionary : public CHeapObj<mtClass> {
  int _number_of_entries;

  class Config {
   public:
    using Value = InstanceKlass*;
    static uintx get_hash(Value const& value, bool* is_dead);
    static void* allocate_node(void* context, size_t size, Value const& value);
    static void free_node(void* context, void* memory, Value const& value);
  };

  using ConcurrentTable = ConcurrentHashTable<Config, mtClass>;
  ConcurrentTable* _table;

  ClassLoaderData* _loader_data;  // backpointer to owning loader
  ClassLoaderData* loader_data() const { return _loader_data; }

  bool check_if_needs_resize();
  int table_size() const;

public:
  Dictionary(ClassLoaderData* loader_data, size_t table_size);
  ~Dictionary();

  void add_klass(JavaThread* current, Symbol* class_name, InstanceKlass* obj);

  InstanceKlass* find_class(Thread* current, Symbol* name);

  void classes_do(void f(InstanceKlass*));
  void all_entries_do(KlassClosure* closure);
  void classes_do(MetaspaceClosure* it);

  void print_table_statistics(outputStream* st, const char* table_name);

  void print_on(outputStream* st) const;
  void print_size(outputStream* st) const;
  void verify();
};

#endif // SHARE_CLASSFILE_DICTIONARY_HPP
