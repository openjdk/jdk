/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_BSMATTRIBUTE_HPP
#define SHARE_OOPS_BSMATTRIBUTE_HPP

#include "oops/array.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"

class ClassLoaderData;

class BSMAttributeEntry {
  friend class ConstantPool;
  friend class BSMAttributeEntries;

  u2 _bootstrap_method_index;
  u2 _argument_count;

  // The argument indexes are stored right after the object, in a contiguous array.
  // [ bsmi_0 argc_0 arg_00 arg_01 ... arg_0N bsmi_1 argc_1 arg_10 ... arg_1N ... ]
  // So in order to find the argument array, jump over ourselves.
  const u2* argument_indexes() const {
    return reinterpret_cast<const u2*>(this + 1);
  }
  u2* argument_indexes() {
    return reinterpret_cast<u2*>(this + 1);
  }
  // These are overlays on top of the BSMAttributeEntries data array, do not construct.
  BSMAttributeEntry() = delete;
  NONCOPYABLE(BSMAttributeEntry);

  void copy_args_into(BSMAttributeEntry* entry) const;

public:
  // Offsets for SA
  enum {
    _bsmi_offset = 0,
    _argc_offset = 1,
    _argv_offset = 2
  };

  int bootstrap_method_index() const {
    return _bootstrap_method_index;
  }
  int argument_count() const {
    return _argument_count;
  }
  int argument(int n) const {
    assert(checked_cast<u2>(n) < _argument_count, "oob");
    return argument_indexes()[n];
  }

  void set_argument(int index, u2 value) {
    assert(index >= 0 && index < argument_count(), "invariant");
    argument_indexes()[index] = value;
  }

  // How many u2s are required to store a BSM entry with argc arguments?
  static int u2s_required (u2 argc) {
    return 1 /* index */ + 1  /* argc */ + argc /* argv */;
  }
};

// The BSMAttributeEntries stores the state of the BootstrapMethods attribute.
class BSMAttributeEntries {
  friend class VMStructs;
  friend class JVMCIVMStructs;

public:
  class InsertionIterator {
    friend BSMAttributeEntries;
    BSMAttributeEntries* _insert_into;
    // Current unused offset into BSMAEs offset array.
    int _cur_offset;
    // Current unused offset into BSMAEs bsm-data array.
    int _cur_array;
  public:
    InsertionIterator() : _insert_into(nullptr), _cur_offset(-1), _cur_array(-1) {}
    InsertionIterator(BSMAttributeEntries* insert_into, int cur_offset, int cur_array)
    : _insert_into(insert_into),
      _cur_offset(cur_offset),
      _cur_array(cur_array) {}
    InsertionIterator(const InsertionIterator&) = default;
    InsertionIterator& operator=(const InsertionIterator&) = default;

    int current_offset() const { return _cur_offset; }
    // Add a new BSMAE, reserving the necessary memory for filling the argument vector.
    // Returns null if there isn't enough space.
    inline BSMAttributeEntry* reserve_new_entry(u2 bsmi, u2 argc);
  };

private:
  // Each bootstrap method has a variable-sized array associated with it.
  // We want constant-time lookup of the Nth BSM. Therefore, we use an offset table,
  // such that the Nth BSM is located at _bootstrap_methods[_offsets[N]].
  Array<u4>* _offsets;
  Array<u2>* _bootstrap_methods;

  // Copy the first num_entries into iter.
  void copy_into(InsertionIterator& iter, int num_entries) const;

public:
  BSMAttributeEntries() : _offsets(nullptr), _bootstrap_methods(nullptr) {}
  BSMAttributeEntries(Array<u4>* offsets, Array<u2>* bootstrap_methods)
    : _offsets(offsets),
      _bootstrap_methods(bootstrap_methods) {}

  bool is_empty() const {
    return _offsets == nullptr && _bootstrap_methods == nullptr;
  }

  Array<u4>*& offsets() { return _offsets; }
  const Array<u4>* const& offsets() const { return _offsets; }
  Array<u2>*& bootstrap_methods() { return _bootstrap_methods; }
  const Array<u2>* const& bootstrap_methods() const { return _bootstrap_methods; }

  BSMAttributeEntry* entry(int bsms_attribute_index) {
    return reinterpret_cast<BSMAttributeEntry*>(_bootstrap_methods->adr_at(_offsets->at(bsms_attribute_index)));
  }
  const BSMAttributeEntry* entry(int bsms_attribute_index) const {
    return reinterpret_cast<BSMAttributeEntry*>(_bootstrap_methods->adr_at(_offsets->at(bsms_attribute_index)));
  }

  int number_of_entries() const {
    return _offsets == nullptr ? 0 : _offsets->length();
  }

  // The number of U2s the BSM data consists of.
  int array_length() const {
    return _bootstrap_methods == nullptr ? 0 :  _bootstrap_methods->length();
  }

  void deallocate_contents(ClassLoaderData* loader_data);

  // Extend to have the space for both this BSMAEntries and other's.
  // Does not copy in the other's BSMAEntrys, that must be done via the InsertionIterator.
  // This starts an insertion iterator. Any call to start_extension must have a matching end_extension call.
  InsertionIterator start_extension(const BSMAttributeEntries& other, ClassLoaderData* loader_data, TRAPS);
  // Extend the BSMAEntries with an additional number_of_entries with a total data_size.
  InsertionIterator start_extension(int number_of_entries, int data_size, ClassLoaderData* loader_data, TRAPS);
  // Reallocates the underlying memory to fit the limits of the InsertionIterator precisely.
  // This ends an insertion iteration. The memory is truncated to fit exactly the data used.
  void end_extension(InsertionIterator& iter, ClassLoaderData* loader_data, TRAPS);
  // Append all of the BSMAEs in other into this.
  void append(const BSMAttributeEntries& other, ClassLoaderData* loader_data, TRAPS);
};

#endif // SHARE_OOPS_BSMATTRIBUTE_HPP
