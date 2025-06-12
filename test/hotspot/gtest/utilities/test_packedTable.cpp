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
#include "utilities/packedTable.hpp"
#include "unittest.hpp"

class Supplier: public PackedTableBuilder::Supplier {
  uint32_t* _keys;
  uint32_t* _values;
  size_t _num_keys;

public:
  Supplier(uint32_t* keys, uint32_t* values, size_t num_keys):
    _keys(keys), _values(values), _num_keys(num_keys) {}

  bool next(uint32_t* key, uint32_t* value) override {
    if (_num_keys == 0) {
      return false;
    }
    *key = *_keys;
    ++_keys;
    if (_values != nullptr) {
      *value = *_values;
      ++_values;
    } else {
      *value = 0;
    }
    --_num_keys;
    return true;
  }
};

class Comparator: public PackedTableLookup::Comparator {
  uint32_t _current;

public:
  int compare_to(uint32_t key) override {
    return _current < key ? -1 : (_current > key ? 1 : 0);
  }

  void reset(uint32_t key) DEBUG_ONLY(override) {
    _current = key;
  }
};

static void test(uint32_t max_key, uint32_t max_value, unsigned int length) {
  if (length > max_key + 1) {
    // can't generate more keys, as keys must be unique
    return;
  }
  PackedTableBuilder builder(max_key, max_value);
  size_t table_length = length * builder.element_bytes();
  u1* table = new u1[table_length];

  uint32_t* keys = new uint32_t[length];
  uint32_t* values = max_value != 0 ? new uint32_t[length] : nullptr;
  for (unsigned int i = 0; i < length; ++i) {
    keys[i] = i;
    if (values != nullptr) {
      values[i] = i % max_value;
    }
  }
  Supplier sup(keys, values, length);
  builder.fill(table, table_length, sup);

  Comparator comparator;
  PackedTableLookup lookup(max_key, max_value, table, table_length);
#ifdef ASSERT
  lookup.validate_order(comparator);
#endif

  for (unsigned int i = 0; i < length; ++i) {
    uint32_t key, value;
    comparator.reset(keys[i]);
    EXPECT_TRUE(lookup.search(comparator, &key, &value));
    EXPECT_EQ(key, keys[i]);
    if (values != nullptr) {
      EXPECT_EQ(value, values[i]);
    } else {
      EXPECT_EQ(value, 0U);
    }
  }

  delete[] keys;
  delete[] values;
}

static void test_with_bits(uint32_t max_key, uint32_t max_value) {
  // Some small sizes
  for (unsigned int i = 0; i <= 100; ++i) {
    test(max_key, max_value, i);
  }
  test(max_key, max_value, 10000);
}

TEST(PackedTableLookup, lookup) {
  for (int key_bits = 1; key_bits <= 32; ++key_bits) {
    for (int value_bits = 0; value_bits <= 32; ++value_bits) {
      test_with_bits(static_cast<uint32_t>((1ULL << key_bits) - 1),
                     static_cast<uint32_t>((1ULL << value_bits) - 1));
    }
  }
}

TEST(PackedTableBase, element_bytes) {
  {
    PackedTableBuilder builder(1, 0);
    EXPECT_EQ(builder.element_bytes(), 1U);
  }
  {
    PackedTableBuilder builder(15, 15);
    EXPECT_EQ(builder.element_bytes(), 1U);
  }
  {
    PackedTableBuilder builder(15, 16);
    EXPECT_EQ(builder.element_bytes(), 2U);
  }
  {
    PackedTableBuilder builder(31, 7);
    EXPECT_EQ(builder.element_bytes(), 1U);
  }
  {
    PackedTableBuilder builder(32, 7);
    EXPECT_EQ(builder.element_bytes(), 2U);
  }
  {
    PackedTableBuilder builder(-1, 0);
    EXPECT_EQ(builder.element_bytes(), 4U);
  }
  {
    PackedTableBuilder builder(-1, 1);
    EXPECT_EQ(builder.element_bytes(), 5U);
  }
  {
    PackedTableBuilder builder(-1, -1);
    EXPECT_EQ(builder.element_bytes(), 8U);
  }
}
