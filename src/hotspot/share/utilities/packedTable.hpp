/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_PACKEDTABLE_HPP
#define SHARE_UTILITIES_PACKEDTABLE_HPP

#include "oops/array.hpp"
#include "utilities/globalDefinitions.hpp"

// Base for space-optimized structure supporting binary search. Each element
// consists of up to 32-bit key, and up to 32-bit value; these are packed
// into a bit-record with 1-byte alignment.
// The keys are ordered according to a custom comparator.
class PackedTableBase {
protected:
  unsigned int _element_bytes;
  uint32_t _key_mask;
  unsigned int _value_shift;
  uint32_t _value_mask;

public:
  PackedTableBase(uint32_t max_key, uint32_t max_value);

  // Returns number of bytes each element will occupy.
  inline unsigned int element_bytes(void) const { return _element_bytes; }
};

// Helper class for constructing a packed table in the provided array.
class PackedTableBuilder: public PackedTableBase {
public:
  class Supplier {
  public:
    // Returns elements with already ordered keys.
    // This function should return true when the key and value was set,
    // and false when there's no more elements.
    // Packed table does NOT support duplicate keys.
    virtual bool next(uint32_t* key, uint32_t* value) = 0;
  };

  // The thresholds are inclusive, and in practice the limits are rounded
  // to the nearest power-of-two - 1.
  // See PackedTableBase constructor for details.
  PackedTableBuilder(uint32_t max_key, uint32_t max_value): PackedTableBase(max_key, max_value) {}

  // Constructs a packed table in the provided array, filling it with elements
  // from the supplier. Note that no comparator is requied by this method -
  // the supplier must return elements with already ordered keys.
  // The table_length (in bytes) should match number of elements provided
  // by the supplier (when Supplier::next() returns false the whole array should
  // be filled).
  void fill(u1* table, size_t table_length, Supplier &supplier) const;
};

// Helper class for lookup in a packed table.
class PackedTableLookup: public PackedTableBase {
  const u1* const _table;
  const size_t _table_length;

  uint64_t read_element(size_t offset) const;

public:

  // The comparator implementation does not have to store a key (uint32_t);
  // the idea is that key can point into a different structure that hosts data
  // suitable for the actual comparison. That's why PackedTableLookup::search(...)
  // returns the key it found as well as the value.
  class Comparator {
  public:
    // Returns negative/0/positive if the target referred to by this comparator
    // is lower/equal/higher than the target referred to by the key.
    virtual int compare_to(uint32_t key) = 0;
    // Changes the target this comparator refers to.
    DEBUG_ONLY(virtual void reset(uint32_t key) = 0);
  };

  // The thresholds are inclusive, and in practice the limits are rounded
  // to the nearest power-of-two - 1.
  // See PackedTableBase constructor for details.
  PackedTableLookup(uint32_t max_key, uint32_t max_value, const u1 *table, size_t table_length):
    PackedTableBase(max_key, max_value), _table(table), _table_length(table_length) {}

  PackedTableLookup(uint32_t max_key, uint32_t max_value, const Array<u1> *table):
    PackedTableLookup(max_key, max_value, table->data(), static_cast<size_t>(table->length())) {}

  // Performs a binary search in the packed table, looking for an element with key
  // referring to a target equal according to the comparator.
  // When the element is found, found_key and found_value are updated from the element
  // and the function returns true.
  // When the element is not found, found_key and found_value are not changed and
  // the function returns false.
  bool search(Comparator& comparator, uint32_t* found_key, uint32_t* found_value) const;

  // Asserts that elements in the packed table follow the order defined by the comparator.
  DEBUG_ONLY(void validate_order(Comparator &comparator) const);

  template<typename Function>
  void iterate(Function func) const {
    for (size_t offset = 0; offset < _table_length; offset += _element_bytes) {
      uint64_t element = read_element(offset);
      uint32_t key = static_cast<uint32_t>(element) & _key_mask;
      uint32_t value = checked_cast<uint32_t>(element >> _value_shift) & _value_mask;
      func(offset, key, value);
    }
  }
};

#endif // SHARE_UTILITIES_PACKEDTABLE_HPP
