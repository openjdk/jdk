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
private:
    uint32_t *_keys;
    uint32_t *_values;
    size_t _num_keys;
public:
    Supplier(uint32_t *keys, uint32_t *values, size_t num_keys):
        _keys(keys), _values(values), _num_keys(num_keys) {}

    bool next(uint32_t *pivot, uint32_t *payload) override {
        if (_num_keys == 0) {
            return false;
        }
        *pivot = *_keys;
        ++_keys;
        if (_values != nullptr) {
            *payload = *_values;
            ++_values;
        } else {
            *payload = 0;
        }
        --_num_keys;
        return true;
    }
};

class Comparator: public PackedTableLookup::Comparator {
private:
    uint32_t _current;
public:
    int compare_to(uint32_t pivot) override {
        return _current < pivot ? -1 : (_current > pivot ? 1 : 0);
    }
    void reset(uint32_t pivot) override {
        _current = pivot;
    }
};

static void test(uint32_t max_pivot, uint32_t max_payload, unsigned int length) {
    if (length > max_pivot + 1) {
        // can't generate more keys, as keys must be unique
        return;
    }
    PackedTableBuilder builder(max_pivot, max_payload);
    size_t table_bytes = length * builder.element_bytes();
    u1 *table = new u1[table_bytes];

    uint32_t *keys = new uint32_t[length];
    uint32_t *values = max_payload != 0 ? new uint32_t[length] : nullptr;
    for (unsigned int i = 0; i < length; ++i) {
        keys[i] = i;
        if (values != nullptr) {
            values[i] = i % max_payload;
        }
    }
    Supplier sup(keys, values, length);
    builder.fill(table, table_bytes, sup);

    Comparator comparator;
    PackedTableLookup lookup(max_pivot, max_payload);
#ifdef ASSERT
    lookup.validate_order(comparator, table, table_bytes);
#endif

    for (unsigned int i = 0; i < length; ++i) {
        uint32_t key, value;
        comparator.reset(keys[i]);
        EXPECT_TRUE(lookup.search(comparator, table, table_bytes, &key, &value));
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

static void test_with_bits(uint32_t max_pivot, uint32_t max_payload) {
    // Some small sizes
    for (unsigned int i = 0; i <= 100; ++i) {
        test(max_pivot, max_payload, 0);
    }
    test(max_pivot, max_payload, 10000);
}

TEST(PackedTableLookup, lookup) {
    for (int pivot_bits = 1; pivot_bits <= 32; ++pivot_bits) {
        for (int payload_bits = 0; payload_bits <= 32; ++payload_bits) {
            test_with_bits(static_cast<uint32_t>((1ULL << pivot_bits) - 1),
                           static_cast<uint32_t>((1ULL << payload_bits) - 1));
        }
    }
}
