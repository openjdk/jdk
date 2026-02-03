/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_DUMPALLOCSTATS_HPP
#define SHARE_CDS_DUMPALLOCSTATS_HPP

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceClosureType.hpp"

// This is for dumping detailed statistics for the allocations
// in the shared spaces.
class DumpAllocStats : public StackObj {
public:

#define DUMPED_OBJ_TYPES_DO(f) \
  METASPACE_CLOSURE_TYPES_DO(f) \
  f(SymbolHashentry) \
  f(SymbolBucket) \
  f(StringHashentry) \
  f(StringBucket) \
  f(CppVTables) \
  f(Other)

#define DUMPED_TYPE_DECLARE(name) name ## Type,
#define DUMPED_TYPE_NAME_CASE(name) case name ## Type: return #name;

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    DUMPED_OBJ_TYPES_DO(DUMPED_TYPE_DECLARE)
    _number_of_types
  };

  static const char* type_name(Type type) {
    switch(type) {
    DUMPED_OBJ_TYPES_DO(DUMPED_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return nullptr;
    }
  }

  CompactHashtableStats _symbol_stats;
  CompactHashtableStats _string_stats;

  int _counts[2][_number_of_types];
  int _bytes [2][_number_of_types];

  int _num_field_cp_entries;
  int _num_field_cp_entries_archived;
  int _num_field_cp_entries_reverted;
  int _num_indy_cp_entries;
  int _num_indy_cp_entries_archived;
  int _num_indy_cp_entries_reverted;
  int _num_klass_cp_entries;
  int _num_klass_cp_entries_archived;
  int _num_klass_cp_entries_reverted;
  int _num_method_cp_entries;
  int _num_method_cp_entries_archived;
  int _num_method_cp_entries_reverted;

public:
  enum { RO = 0, RW = 1 };

  DumpAllocStats() {
    memset(_counts, 0, sizeof(_counts));
    memset(_bytes,  0, sizeof(_bytes));
    _num_field_cp_entries           = 0;
    _num_field_cp_entries_archived  = 0;
    _num_field_cp_entries_reverted  = 0;
    _num_indy_cp_entries            = 0;
    _num_indy_cp_entries_archived   = 0;
    _num_indy_cp_entries_reverted   = 0;
    _num_klass_cp_entries           = 0;
    _num_klass_cp_entries_archived  = 0;
    _num_klass_cp_entries_reverted  = 0;
    _num_method_cp_entries          = 0;
    _num_method_cp_entries_archived = 0;
    _num_method_cp_entries_reverted = 0;
  };

  CompactHashtableStats* symbol_stats() { return &_symbol_stats; }
  CompactHashtableStats* string_stats() { return &_string_stats; }

  void record(MetaspaceClosureType type, int byte_size, bool read_only) {
    int t = (int)type;
    assert(t >= 0 && t < (int)MetaspaceClosureType::_number_of_types, "sanity");
    int which = (read_only) ? RO : RW;
    _counts[which][t] ++;
    _bytes [which][t] += byte_size;
  }

  void record_other_type(int byte_size, bool read_only) {
    int which = (read_only) ? RO : RW;
    _bytes [which][OtherType] += byte_size;
  }

  void record_cpp_vtables(int byte_size) {
    _bytes[RW][CppVTablesType] += byte_size;
  }

  void record_field_cp_entry(bool archived, bool reverted) {
    _num_field_cp_entries ++;
    _num_field_cp_entries_archived += archived ? 1 : 0;
    _num_field_cp_entries_reverted += reverted ? 1 : 0;
  }

  void record_indy_cp_entry(bool archived, bool reverted) {
    _num_indy_cp_entries ++;
    _num_indy_cp_entries_archived += archived ? 1 : 0;
    _num_indy_cp_entries_reverted += reverted ? 1 : 0;
  }

  void record_klass_cp_entry(bool archived, bool reverted) {
    _num_klass_cp_entries ++;
    _num_klass_cp_entries_archived += archived ? 1 : 0;
    _num_klass_cp_entries_reverted += reverted ? 1 : 0;
  }

  void record_method_cp_entry(bool archived, bool reverted) {
    _num_method_cp_entries ++;
    _num_method_cp_entries_archived += archived ? 1 : 0;
    _num_method_cp_entries_reverted += reverted ? 1 : 0;
  }

  void print_stats(int ro_all, int rw_all);

  DEBUG_ONLY(void verify(int expected_byte_size, bool read_only) const);

};

#endif // SHARE_CDS_DUMPALLOCSTATS_HPP
