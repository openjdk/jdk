/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1BiasedArray.hpp"
#include "memory/padded.inline.hpp"

// Allocate a new array, generic version.
address G1BiasedMappedArrayBase::create_new_base_array(size_t length, size_t elem_size) {
  assert(length > 0, "just checking");
  assert(elem_size > 0, "just checking");
  return PaddedPrimitiveArray<u_char, mtGC>::create_unfreeable(length * elem_size);
}

#ifndef PRODUCT
void G1BiasedMappedArrayBase::verify_index(idx_t index) const {
  guarantee(_base != NULL, "Array not initialized");
  guarantee(index < length(), err_msg("Index out of bounds index: "SIZE_FORMAT" length: "SIZE_FORMAT, index, length()));
}

void G1BiasedMappedArrayBase::verify_biased_index(idx_t biased_index) const {
  guarantee(_biased_base != NULL, "Array not initialized");
  guarantee(biased_index >= bias() && biased_index < (bias() + length()),
    err_msg("Biased index out of bounds, index: "SIZE_FORMAT" bias: "SIZE_FORMAT" length: "SIZE_FORMAT, biased_index, bias(), length()));
}

void G1BiasedMappedArrayBase::verify_biased_index_inclusive_end(idx_t biased_index) const {
  guarantee(_biased_base != NULL, "Array not initialized");
  guarantee(biased_index >= bias() && biased_index <= (bias() + length()),
    err_msg("Biased index out of inclusive bounds, index: "SIZE_FORMAT" bias: "SIZE_FORMAT" length: "SIZE_FORMAT, biased_index, bias(), length()));
}

class TestMappedArray : public G1BiasedMappedArray<int> {
protected:
  virtual int default_value() const { return 0xBAADBABE; }
public:
  static void test_biasedarray() {
    const size_t REGION_SIZE_IN_WORDS = 512;
    const size_t NUM_REGIONS = 20;
    HeapWord* fake_heap = (HeapWord*)LP64_ONLY(0xBAAA00000) NOT_LP64(0xBA000000); // Any value that is non-zero

    TestMappedArray array;
    array.initialize(fake_heap, fake_heap + REGION_SIZE_IN_WORDS * NUM_REGIONS,
            REGION_SIZE_IN_WORDS * HeapWordSize);
    // Check address calculation (bounds)
    assert(array.bottom_address_mapped() == fake_heap,
      err_msg("bottom mapped address should be " PTR_FORMAT ", but is " PTR_FORMAT, p2i(fake_heap), p2i(array.bottom_address_mapped())));
    assert(array.end_address_mapped() == (fake_heap + REGION_SIZE_IN_WORDS * NUM_REGIONS), "must be");

    int* bottom = array.address_mapped_to(fake_heap);
    assert((void*)bottom == (void*) array.base(), "must be");
    int* end = array.address_mapped_to(fake_heap + REGION_SIZE_IN_WORDS * NUM_REGIONS);
    assert((void*)end == (void*)(array.base() + array.length()), "must be");
    // The entire array should contain default value elements
    for (int* current = bottom; current < end; current++) {
      assert(*current == array.default_value(), "must be");
    }

    // Test setting values in the table

    HeapWord* region_start_address = fake_heap + REGION_SIZE_IN_WORDS * (NUM_REGIONS / 2);
    HeapWord* region_end_address = fake_heap + (REGION_SIZE_IN_WORDS * (NUM_REGIONS / 2) + REGION_SIZE_IN_WORDS - 1);

    // Set/get by address tests: invert some value; first retrieve one
    int actual_value = array.get_by_index(NUM_REGIONS / 2);
    array.set_by_index(NUM_REGIONS / 2, ~actual_value);
    // Get the same value by address, should correspond to the start of the "region"
    int value = array.get_by_address(region_start_address);
    assert(value == ~actual_value, "must be");
    // Get the same value by address, at one HeapWord before the start
    value = array.get_by_address(region_start_address - 1);
    assert(value == array.default_value(), "must be");
    // Get the same value by address, at the end of the "region"
    value = array.get_by_address(region_end_address);
    assert(value == ~actual_value, "must be");
    // Make sure the next value maps to another index
    value = array.get_by_address(region_end_address + 1);
    assert(value == array.default_value(), "must be");

    // Reset the value in the array
    array.set_by_address(region_start_address + (region_end_address - region_start_address) / 2, actual_value);

    // The entire array should have the default value again
    for (int* current = bottom; current < end; current++) {
      assert(*current == array.default_value(), "must be");
    }

    // Set/get by index tests: invert some value
    idx_t index = NUM_REGIONS / 2;
    actual_value = array.get_by_index(index);
    array.set_by_index(index, ~actual_value);

    value = array.get_by_index(index);
    assert(value == ~actual_value, "must be");

    value = array.get_by_index(index - 1);
    assert(value == array.default_value(), "must be");

    value = array.get_by_index(index + 1);
    assert(value == array.default_value(), "must be");

    array.set_by_index(0, 0);
    value = array.get_by_index(0);
    assert(value == 0, "must be");

    array.set_by_index(array.length() - 1, 0);
    value = array.get_by_index(array.length() - 1);
    assert(value == 0, "must be");

    array.set_by_index(index, 0);

    // The array should have three zeros, and default values otherwise
    size_t num_zeros = 0;
    for (int* current = bottom; current < end; current++) {
      assert(*current == array.default_value() || *current == 0, "must be");
      if (*current == 0) {
        num_zeros++;
      }
    }
    assert(num_zeros == 3, "must be");
  }
};

void TestG1BiasedArray_test() {
  TestMappedArray::test_biasedarray();
}

#endif
