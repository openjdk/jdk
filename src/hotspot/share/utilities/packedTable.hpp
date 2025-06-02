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

#include "utilities/globalDefinitions.hpp"

/*
 * Base for space-optimized structure supporting binary search. Each element
 * consists of up to 32-bit key, and up to 32-bit value; these are packed
 * into a bit-record aligned on bytes.
 * The keys are ordered according to a custom comparator.
 */
class PackedTableBase {
protected:
  unsigned int _element_bytes;
  uint32_t _key_mask;
  unsigned int _value_shift;
  uint32_t _value_mask;

public:
  PackedTableBase(uint32_t max_key, uint32_t max_value);

  inline unsigned int element_bytes(void) const { return _element_bytes; }
};

class PackedTableBuilder: public PackedTableBase {
public:
  class Supplier {
  public:
    /* Returns elements with already ordered keys. */
    virtual bool next(uint32_t *key, uint32_t *value) = 0;
  };

  PackedTableBuilder(uint32_t max_key, uint32_t max_value): PackedTableBase(max_key, max_value) {}

  // The supplier should return elements with already ordered keys.
  // We can't easily sort within the builder because qsort() accepts
  // only pure function as comparator.
  void fill(u1 *search_table, size_t search_table_length, Supplier &supplier) const;
};

class PackedTableLookup: public PackedTableBase {
private:
  uint64_t read_element(const u1* data, size_t length, size_t offset) const;

public:
  /*
   * The comparator implementation does not have to store a key (uint32_t);
   * the idea is that key can point into a different structure that hosts data
   * suitable for the actual comparison. That's why PackedTableLookup::search(...)
   * returns the key it found as well as the value.
   */
  class Comparator {
  public:
    virtual int compare_to(uint32_t key) = 0;
    virtual void reset(uint32_t key) = 0;
  };

  PackedTableLookup(uint32_t max_key, uint32_t max_value): PackedTableBase(max_key, max_value) {}

  bool search(Comparator& comparator, const u1* search_table, size_t search_table_length, uint32_t* found_key, uint32_t* found_value) const;
  DEBUG_ONLY(void validate_order(Comparator &comparator, const u1 *search_table, size_t search_table_length) const);
};
