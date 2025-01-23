/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_INTERPRETER_REWRITER_HPP
#define SHARE_INTERPRETER_REWRITER_HPP

#include "memory/allocation.hpp"
#include "oops/constantPool.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "utilities/growableArray.hpp"

// The Rewriter adds caches to the constant pool and rewrites bytecode indices
// pointing into the constant pool for better interpreter performance.

class Rewriter: public StackObj {
 private:
  InstanceKlass*      _klass;
  constantPoolHandle  _pool;
  Array<Method*>*     _methods;
  GrowableArray<int>  _cp_map;
  GrowableArray<int>  _reference_map; // maps from cp index to resolved_refs index (or -1)
  GrowableArray<int>  _resolved_references_map; // for strings, methodHandle, methodType
  GrowableArray<int>  _invokedynamic_references_map; // for invokedynamic resolved refs
  GrowableArray<int>  _method_handle_invokers;
  int                 _resolved_reference_limit;
  int                 _invokedynamic_index;
  int                 _field_entry_index;
  int                 _method_entry_index;

  // For collecting initialization information for field, method, and invokedynamic
  // constant pool cache entries. The number of entries of each type will be known
  // at the end of rewriting and these arrays will be used to build the proper arrays
  // in the Constant Pool Cache.
  GrowableArray<ResolvedIndyEntry> _initialized_indy_entries;
  GrowableArray<ResolvedFieldEntry> _initialized_field_entries;
  GrowableArray<ResolvedMethodEntry> _initialized_method_entries;

  void init_maps(int length) {
    _cp_map.trunc_to(0);
    _cp_map.at_grow(length, -1);

    // Also cache resolved objects, in another different cache.
    _reference_map.trunc_to(0);
    _reference_map.at_grow(length, -1);

    _method_handle_invokers.trunc_to(0);
    _resolved_references_map.trunc_to(0);
    _invokedynamic_references_map.trunc_to(0);
    _resolved_reference_limit = -1;
  }

  void record_map_limits() {
    // Record initial size of the two arrays generated for the CP cache
    // relative to walking the constant pool.
    _resolved_reference_limit = _resolved_references_map.length();
  }

  int  cp_entry_to_cp_cache(int i) { assert(has_cp_cache(i), "oob"); return _cp_map.at(i); }
  bool has_cp_cache(int i) { return (uint) i < (uint) _cp_map.length() && _cp_map.at(i) >= 0; }

  // FIXME: inline add_map_entry into its single use point
  int add_map_entry(int cp_index, GrowableArray<int>* cp_map, GrowableArray<int>* cp_cache_map) {
    assert(cp_map->at(cp_index) == -1, "not twice on same cp_index");
    int cache_index = cp_cache_map->append(cp_index);
    cp_map->at_put(cp_index, cache_index);
    return cache_index;
  }

  int  cp_entry_to_resolved_references(int cp_index) const {
    assert(has_entry_in_resolved_references(cp_index), "oob");
    return _reference_map.at(cp_index);
  }
  bool has_entry_in_resolved_references(int cp_index) const {
    return (uint) cp_index < (uint) _reference_map.length() && _reference_map.at(cp_index) >= 0;
  }

  // add a new entry to the resolved_references map
  int add_resolved_references_entry(int cp_index) {
    int ref_index = add_map_entry(cp_index, &_reference_map, &_resolved_references_map);
    assert(cp_entry_to_resolved_references(cp_index) == ref_index, "");
    return ref_index;
  }

  int add_invokedynamic_resolved_references_entry(int cp_index, int cache_index);

  int resolved_references_entry_to_pool_index(int ref_index) {
    int cp_index = _resolved_references_map.at(ref_index);
    return cp_index;
  }

  // All the work goes in here:
  Rewriter(InstanceKlass* klass, const constantPoolHandle& cpool, Array<Method*>* methods, TRAPS);

  void compute_index_maps();
  void make_constant_pool_cache(TRAPS);
  void scan_method(Thread* thread, Method* m, bool reverse, bool* invokespecial_error);
  void rewrite_Object_init(const methodHandle& m, TRAPS);
  void rewrite_field_reference(address bcp, int offset, bool reverse);
  void rewrite_method_reference(address bcp, int offset, bool reverse);
  void rewrite_member_reference(address bcp, int offset, bool reverse);
  void maybe_rewrite_invokehandle(address opc, int cp_index, int cache_index, bool reverse);
  void rewrite_invokedynamic(address bcp, int offset, bool reverse, Method* method);
  void maybe_rewrite_ldc(address bcp, int offset, bool is_wide, bool reverse);
  void rewrite_invokespecial(address bcp, int offset, bool reverse, bool* invokespecial_error);

  // Do all the work.
  void rewrite_bytecodes(TRAPS);

  // Revert bytecodes in case of an exception.
  void restore_bytecodes(Thread* thread);

  static methodHandle rewrite_jsrs(const methodHandle& m, TRAPS);
 public:
  // Driver routine:
  static void rewrite(InstanceKlass* klass, TRAPS);
};

#endif // SHARE_INTERPRETER_REWRITER_HPP
