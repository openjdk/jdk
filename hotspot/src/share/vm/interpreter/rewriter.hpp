/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_REWRITER_HPP
#define SHARE_VM_INTERPRETER_REWRITER_HPP

#include "memory/allocation.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/growableArray.hpp"

// The Rewriter adds caches to the constant pool and rewrites bytecode indices
// pointing into the constant pool for better interpreter performance.

class Rewriter: public StackObj {
 private:
  instanceKlassHandle _klass;
  constantPoolHandle  _pool;
  Array<Method*>*     _methods;
  intArray            _cp_map;
  intStack            _cp_cache_map;        // for Methodref, Fieldref,
                                            // InterfaceMethodref and InvokeDynamic
  intArray            _reference_map;       // maps from cp index to resolved_refs index (or -1)
  intStack            _resolved_references_map;    // for strings, methodHandle, methodType
  intStack            _invokedynamic_references_map; // for invokedynamic resolved refs
  intArray            _method_handle_invokers;
  int                 _resolved_reference_limit;

  // For mapping invokedynamic bytecodes, which are discovered during method
  // scanning.  The invokedynamic entries are added at the end of the cpCache.
  // If there are any invokespecial/InterfaceMethodref special case bytecodes,
  // these entries are added before invokedynamic entries so that the
  // invokespecial bytecode 16 bit index doesn't overflow.
  intStack            _invokedynamic_cp_cache_map;

  // For patching.
  GrowableArray<address>* _patch_invokedynamic_bcps;
  GrowableArray<int>*     _patch_invokedynamic_refs;

  void init_maps(int length) {
    _cp_map.initialize(length, -1);
    // Choose an initial value large enough that we don't get frequent
    // calls to grow().
    _cp_cache_map.initialize(length/2);
    // Also cache resolved objects, in another different cache.
    _reference_map.initialize(length, -1);
    _resolved_references_map.initialize(length/2);
    _invokedynamic_references_map.initialize(length/2);
    _resolved_reference_limit = -1;
    _first_iteration_cp_cache_limit = -1;

    // invokedynamic specific fields
    _invokedynamic_cp_cache_map.initialize(length/4);
    _patch_invokedynamic_bcps = new GrowableArray<address>(length/4);
    _patch_invokedynamic_refs = new GrowableArray<int>(length/4);
  }

  int _first_iteration_cp_cache_limit;
  void record_map_limits() {
    // Record initial size of the two arrays generated for the CP cache
    // relative to walking the constant pool.
    _first_iteration_cp_cache_limit = _cp_cache_map.length();
    _resolved_reference_limit = _resolved_references_map.length();
  }

  int cp_cache_delta() {
    // How many cp cache entries were added since recording map limits after
    // cp cache initialization?
    assert(_first_iteration_cp_cache_limit != -1, "only valid after first iteration");
    return _cp_cache_map.length() - _first_iteration_cp_cache_limit;
  }

  int  cp_entry_to_cp_cache(int i) { assert(has_cp_cache(i), "oob"); return _cp_map[i]; }
  bool has_cp_cache(int i) { return (uint)i < (uint)_cp_map.length() && _cp_map[i] >= 0; }

  int add_map_entry(int cp_index, intArray* cp_map, intStack* cp_cache_map) {
    assert(cp_map->at(cp_index) == -1, "not twice on same cp_index");
    int cache_index = cp_cache_map->append(cp_index);
    cp_map->at_put(cp_index, cache_index);
    return cache_index;
  }

  int add_cp_cache_entry(int cp_index) {
    assert(_pool->tag_at(cp_index).value() != JVM_CONSTANT_InvokeDynamic, "use indy version");
    assert(_first_iteration_cp_cache_limit == -1, "do not add cache entries after first iteration");
    int cache_index = add_map_entry(cp_index, &_cp_map, &_cp_cache_map);
    assert(cp_entry_to_cp_cache(cp_index) == cache_index, "");
    assert(cp_cache_entry_pool_index(cache_index) == cp_index, "");
    return cache_index;
  }

  int add_invokedynamic_cp_cache_entry(int cp_index) {
    assert(_pool->tag_at(cp_index).value() == JVM_CONSTANT_InvokeDynamic, "use non-indy version");
    assert(_first_iteration_cp_cache_limit >= 0, "add indy cache entries after first iteration");
    // add to the invokedynamic index map.
    int cache_index = _invokedynamic_cp_cache_map.append(cp_index);
    // do not update _cp_map, since the mapping is one-to-many
    assert(invokedynamic_cp_cache_entry_pool_index(cache_index) == cp_index, "");
    // this index starts at one but in the bytecode it's appended to the end.
    return cache_index + _first_iteration_cp_cache_limit;
  }

  int invokedynamic_cp_cache_entry_pool_index(int cache_index) {
    int cp_index = _invokedynamic_cp_cache_map[cache_index];
    return cp_index;
  }

  // add a new CP cache entry beyond the normal cache for the special case of
  // invokespecial with InterfaceMethodref as cpool operand.
  int add_invokespecial_cp_cache_entry(int cp_index) {
    assert(_first_iteration_cp_cache_limit >= 0, "add these special cache entries after first iteration");
    // Don't add InterfaceMethodref if it already exists at the end.
    for (int i = _first_iteration_cp_cache_limit; i < _cp_cache_map.length(); i++) {
     if (cp_cache_entry_pool_index(i) == cp_index) {
       return i;
     }
    }
    int cache_index = _cp_cache_map.append(cp_index);
    assert(cache_index >= _first_iteration_cp_cache_limit, "");
    // do not update _cp_map, since the mapping is one-to-many
    assert(cp_cache_entry_pool_index(cache_index) == cp_index, "");
    return cache_index;
  }

  int  cp_entry_to_resolved_references(int cp_index) const {
    assert(has_entry_in_resolved_references(cp_index), "oob");
    return _reference_map[cp_index];
  }
  bool has_entry_in_resolved_references(int cp_index) const {
    return (uint)cp_index < (uint)_reference_map.length() && _reference_map[cp_index] >= 0;
  }

  // add a new entry to the resolved_references map
  int add_resolved_references_entry(int cp_index) {
    int ref_index = add_map_entry(cp_index, &_reference_map, &_resolved_references_map);
    assert(cp_entry_to_resolved_references(cp_index) == ref_index, "");
    return ref_index;
  }

  // add a new entries to the resolved_references map (for invokedynamic and invokehandle only)
  int add_invokedynamic_resolved_references_entries(int cp_index, int cache_index) {
    assert(_resolved_reference_limit >= 0, "must add indy refs after first iteration");
    int ref_index = -1;
    for (int entry = 0; entry < ConstantPoolCacheEntry::_indy_resolved_references_entries; entry++) {
      const int index = _resolved_references_map.append(cp_index);  // many-to-one
      assert(index >= _resolved_reference_limit, "");
      if (entry == 0) {
        ref_index = index;
      }
      assert((index - entry) == ref_index, "entries must be consecutive");
      _invokedynamic_references_map.at_put_grow(index, cache_index, -1);
    }
    return ref_index;
  }

  int resolved_references_entry_to_pool_index(int ref_index) {
    int cp_index = _resolved_references_map[ref_index];
    return cp_index;
  }

  // Access the contents of _cp_cache_map to determine CP cache layout.
  int cp_cache_entry_pool_index(int cache_index) {
    int cp_index = _cp_cache_map[cache_index];
    return cp_index;
  }

  // All the work goes in here:
  Rewriter(instanceKlassHandle klass, constantPoolHandle cpool, Array<Method*>* methods, TRAPS);

  void compute_index_maps();
  void make_constant_pool_cache(TRAPS);
  void scan_method(Method* m, bool reverse, bool* invokespecial_error);
  void rewrite_Object_init(methodHandle m, TRAPS);
  void rewrite_member_reference(address bcp, int offset, bool reverse);
  void maybe_rewrite_invokehandle(address opc, int cp_index, int cache_index, bool reverse);
  void rewrite_invokedynamic(address bcp, int offset, bool reverse);
  void maybe_rewrite_ldc(address bcp, int offset, bool is_wide, bool reverse);
  void rewrite_invokespecial(address bcp, int offset, bool reverse, bool* invokespecial_error);

  void patch_invokedynamic_bytecodes();

  // Revert bytecodes in case of an exception.
  void restore_bytecodes();

  static methodHandle rewrite_jsrs(methodHandle m, TRAPS);
 public:
  // Driver routine:
  static void rewrite(instanceKlassHandle klass, TRAPS);
};

#endif // SHARE_VM_INTERPRETER_REWRITER_HPP
