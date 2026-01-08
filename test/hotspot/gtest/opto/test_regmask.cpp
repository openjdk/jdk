/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/chaitin.hpp"
#include "opto/opcodes.hpp"
#include "opto/regmask.hpp"
#include "unittest.hpp"

// Sanity tests for RegMask and RegMaskIterator. The file tests operations on
// combinations of different RegMask versions ("basic", i.e. only statically
// allocated and "extended", i.e. extended with dynamically allocated memory).

static void contains_expected_num_of_registers(const RegMask& rm, unsigned int expected) {

  ASSERT_TRUE(rm.size() == expected);
  if (expected > 0) {
    ASSERT_TRUE(!rm.is_empty());
  } else {
    ASSERT_TRUE(rm.is_empty());
    ASSERT_TRUE(!rm.is_infinite_stack());
  }

  RegMaskIterator rmi(rm);
  unsigned int count = 0;
  OptoReg::Name reg = OptoReg::Bad;
  while (rmi.has_next()) {
    reg = rmi.next();
    ASSERT_TRUE(OptoReg::is_valid(reg));
    count++;
  }
  ASSERT_EQ(OptoReg::Bad, rmi.next());
  ASSERT_TRUE(count == expected);
}

TEST_VM(RegMask, empty) {
  RegMask rm;
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, iteration) {
  RegMask rm;
  rm.insert(30);
  rm.insert(31);
  rm.insert(32);
  rm.insert(33);
  rm.insert(62);
  rm.insert(63);
  rm.insert(64);
  rm.insert(65);

  RegMaskIterator rmi(rm);
  ASSERT_TRUE(rmi.next() == OptoReg::Name(30));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(31));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(32));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(33));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(62));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(63));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(64));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(65));
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, Set_ALL) {
  // Check that set_all doesn't add bits outside of rm.rm_size_bits()
  RegMask rm;
  rm.set_all();
  ASSERT_TRUE(rm.size() == rm.rm_size_in_bits());
  ASSERT_TRUE(!rm.is_empty());
  // set_all sets infinite_stack
  ASSERT_TRUE(rm.is_infinite_stack());
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits());
}

TEST_VM(RegMask, Clear) {
  // Check that Clear doesn't leave any stray bits
  RegMask rm;
  rm.set_all();
  rm.clear();
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, and_with) {
  RegMask rm1;
  rm1.insert(OptoReg::Name(1));
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(1)));

  rm1.and_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  rm1.and_with(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, or_with) {
  RegMask rm1;
  rm1.insert(OptoReg::Name(1));
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(1)));

  rm1.or_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  rm1.or_with(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, subtract) {
  RegMask rm1;
  RegMask rm2;

  rm2.set_all();
  for (int i = 17; i < (int)rm1.rm_size_in_bits(); i++) {
    rm1.insert(i);
  }
  rm1.set_infinite_stack(true);
  ASSERT_TRUE(rm1.is_infinite_stack());
  rm2.subtract(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_in_bits() - 17);
  contains_expected_num_of_registers(rm2, 17);
}

TEST_VM(RegMask, subtract_inner) {
  RegMask rm1;
  RegMask rm2;
  rm2.set_all();
  for (int i = 17; i < (int)rm1.rm_size_in_bits(); i++) {
    rm1.insert(i);
  }
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_in_bits() - 17);
  contains_expected_num_of_registers(rm2, 17);
}

TEST_VM(RegMask, is_bound1) {
  RegMask rm;
  ASSERT_FALSE(rm.is_bound1());
  for (int i = 0; i < (int)rm.rm_size_in_bits() - 1; i++) {
    rm.insert(i);
    ASSERT_TRUE(rm.is_bound1())       << "Index " << i;
    ASSERT_TRUE(rm.is_bound(Op_RegI)) << "Index " << i;
    contains_expected_num_of_registers(rm, 1);
    rm.remove(i);
  }
  // infinite_stack does not count as a bound register
  rm.set_infinite_stack(true);
  ASSERT_FALSE(rm.is_bound1());
}

TEST_VM(RegMask, is_bound_pair) {
  RegMask rm;
  ASSERT_TRUE(rm.is_bound_pair());
  for (int i = 0; i < (int)rm.rm_size_in_bits() - 2; i++) {
    rm.insert(i);
    rm.insert(i + 1);
    ASSERT_TRUE(rm.is_bound_pair())   << "Index " << i;
    ASSERT_TRUE(rm.is_bound_set(2))   << "Index " << i;
    ASSERT_TRUE(rm.is_bound(Op_RegI)) << "Index " << i;
    contains_expected_num_of_registers(rm, 2);
    rm.clear();
  }
  // A pair with the infinite bit does not count as a bound pair
  rm.clear();
  rm.insert(rm.rm_size_in_bits() - 2);
  rm.insert(rm.rm_size_in_bits() - 1);
  rm.set_infinite_stack(true);
  ASSERT_FALSE(rm.is_bound_pair());
}

TEST_VM(RegMask, is_bound_set) {
  RegMask rm;
  for (int size = 1; size <= 16; size++) {
    ASSERT_TRUE(rm.is_bound_set(size));
    for (int i = 0; i < (int)rm.rm_size_in_bits() - size; i++) {
      for (int j = i; j < i + size; j++) {
        rm.insert(j);
      }
      ASSERT_TRUE(rm.is_bound_set(size))   << "Size " << size << " Index " << i;
      contains_expected_num_of_registers(rm, size);
      rm.clear();
    }
    // A set with infinite_stack does not count as a bound set
    for (int j = rm.rm_size_in_bits() - size; j < (int)rm.rm_size_in_bits(); j++) {
      rm.insert(j);
    }
    rm.set_infinite_stack(true);
    ASSERT_FALSE(rm.is_bound_set(size));
    rm.clear();
  }
}

TEST_VM(RegMask, external_member) {
  RegMask rm;
  rm.set_infinite_stack(false);
  ASSERT_FALSE(rm.member(OptoReg::Name(rm.rm_size_in_bits())));
  rm.set_infinite_stack(true);
  ASSERT_TRUE(rm.member(OptoReg::Name(rm.rm_size_in_bits())));
}

TEST_VM(RegMask, find_element) {
  RegMask rm;
  rm.insert(OptoReg::Name(44));
  rm.insert(OptoReg::Name(30));
  rm.insert(OptoReg::Name(54));
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Name(30));
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Name(54));
  rm.set_infinite_stack(true);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Name(54));
  rm.clear();
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Bad);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Bad);
}

TEST_VM(RegMask, find_first_set) {
  RegMask rm;
  LRG lrg;
  lrg._is_scalable = 0;
  lrg._is_vector = 0;
  ASSERT_EQ(rm.find_first_set(lrg, 2), OptoReg::Bad);
  rm.insert(OptoReg::Name(24));
  rm.insert(OptoReg::Name(25));
  rm.insert(OptoReg::Name(26));
  rm.insert(OptoReg::Name(27));
  rm.insert(OptoReg::Name(16));
  rm.insert(OptoReg::Name(17));
  rm.insert(OptoReg::Name(18));
  rm.insert(OptoReg::Name(19));
  ASSERT_EQ(rm.find_first_set(lrg, 4), OptoReg::Name(19));
}

TEST_VM(RegMask, alignment) {
  RegMask rm;
  rm.insert(OptoReg::Name(30));
  rm.insert(OptoReg::Name(31));
  ASSERT_TRUE(rm.is_aligned_sets(2));
  rm.insert(OptoReg::Name(32));
  rm.insert(OptoReg::Name(37));
  rm.insert(OptoReg::Name(62));
  rm.insert(OptoReg::Name(71));
  rm.insert(OptoReg::Name(74));
  rm.insert(OptoReg::Name(75));
  ASSERT_FALSE(rm.is_aligned_pairs());
  rm.clear_to_pairs();
  ASSERT_TRUE(rm.is_aligned_sets(2));
  ASSERT_TRUE(rm.is_aligned_pairs());
  contains_expected_num_of_registers(rm, 4);
  ASSERT_TRUE(rm.member(OptoReg::Name(30)));
  ASSERT_TRUE(rm.member(OptoReg::Name(31)));
  ASSERT_TRUE(rm.member(OptoReg::Name(74)));
  ASSERT_TRUE(rm.member(OptoReg::Name(75)));
  ASSERT_FALSE(rm.is_misaligned_pair());
  rm.remove(OptoReg::Name(30));
  rm.remove(OptoReg::Name(74));
  ASSERT_TRUE(rm.is_misaligned_pair());
}

TEST_VM(RegMask, clear_to_sets) {
  RegMask rm;
  rm.insert(OptoReg::Name(3));
  rm.insert(OptoReg::Name(20));
  rm.insert(OptoReg::Name(21));
  rm.insert(OptoReg::Name(22));
  rm.insert(OptoReg::Name(23));
  rm.insert(OptoReg::Name(25));
  rm.insert(OptoReg::Name(26));
  rm.insert(OptoReg::Name(27));
  rm.insert(OptoReg::Name(40));
  rm.insert(OptoReg::Name(42));
  rm.insert(OptoReg::Name(43));
  rm.insert(OptoReg::Name(44));
  rm.insert(OptoReg::Name(45));
  rm.clear_to_sets(2);
  ASSERT_TRUE(rm.is_aligned_sets(2));
  contains_expected_num_of_registers(rm, 10);
  rm.clear_to_sets(4);
  ASSERT_TRUE(rm.is_aligned_sets(4));
  contains_expected_num_of_registers(rm, 4);
  rm.clear_to_sets(8);
  ASSERT_TRUE(rm.is_aligned_sets(8));
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, smear_to_sets) {
  RegMask rm;
  rm.insert(OptoReg::Name(3));
  rm.smear_to_sets(2);
  ASSERT_TRUE(rm.is_aligned_sets(2));
  contains_expected_num_of_registers(rm, 2);
  rm.smear_to_sets(4);
  ASSERT_TRUE(rm.is_aligned_sets(4));
  contains_expected_num_of_registers(rm, 4);
  rm.smear_to_sets(8);
  ASSERT_TRUE(rm.is_aligned_sets(8));
  contains_expected_num_of_registers(rm, 8);
  rm.smear_to_sets(16);
  ASSERT_TRUE(rm.is_aligned_sets(16));
  contains_expected_num_of_registers(rm, 16);
}

TEST_VM(RegMask, overlap) {
  RegMask rm1;
  RegMask rm2;
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.insert(OptoReg::Name(23));
  rm1.insert(OptoReg::Name(2));
  rm1.insert(OptoReg::Name(12));
  rm2.insert(OptoReg::Name(1));
  rm2.insert(OptoReg::Name(4));
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.insert(OptoReg::Name(4));
  ASSERT_TRUE(rm1.overlap(rm2));
  ASSERT_TRUE(rm2.overlap(rm1));
}

TEST_VM(RegMask, valid_reg) {
  RegMask rm;
  ASSERT_FALSE(rm.is_valid_reg(OptoReg::Name(42), 1));
  rm.insert(OptoReg::Name(3));
  rm.insert(OptoReg::Name(5));
  rm.insert(OptoReg::Name(6));
  rm.insert(OptoReg::Name(7));
  ASSERT_FALSE(rm.is_valid_reg(OptoReg::Name(7), 4));
  ASSERT_TRUE(rm.is_valid_reg(OptoReg::Name(7), 2));
}

TEST_VM(RegMask, rollover_and_insert_remove) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_in_bits() + 42);
  OptoReg::Name reg2(rm.rm_size_in_bits() * 2 + 42);
  rm.set_infinite_stack(true);
  ASSERT_TRUE(rm.member(reg1));
  rm.rollover();
  rm.clear();
  rm.insert(reg1);
  ASSERT_TRUE(rm.member(reg1));
  rm.remove(reg1);
  ASSERT_FALSE(rm.member(reg1));
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  rm.insert(reg2);
  ASSERT_FALSE(rm.member(reg1));
  ASSERT_TRUE(rm.member(reg2));
}

TEST_VM(RegMask, rollover_and_find) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_in_bits() + 42);
  OptoReg::Name reg2(rm.rm_size_in_bits() + 7);
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  ASSERT_EQ(rm.find_first_elem(), OptoReg::Bad);
  ASSERT_EQ(rm.find_last_elem(), OptoReg::Bad);
  rm.insert(reg1);
  rm.insert(reg2);
  ASSERT_EQ(rm.find_first_elem(), reg2);
  ASSERT_EQ(rm.find_last_elem(), reg1);
}

TEST_VM(RegMask, rollover_and_find_first_set) {
  LRG lrg;
  lrg._is_scalable = 0;
  lrg._is_vector = 0;
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_in_bits() + 24);
  OptoReg::Name reg2(rm.rm_size_in_bits() + 25);
  OptoReg::Name reg3(rm.rm_size_in_bits() + 26);
  OptoReg::Name reg4(rm.rm_size_in_bits() + 27);
  OptoReg::Name reg5(rm.rm_size_in_bits() + 16);
  OptoReg::Name reg6(rm.rm_size_in_bits() + 17);
  OptoReg::Name reg7(rm.rm_size_in_bits() + 18);
  OptoReg::Name reg8(rm.rm_size_in_bits() + 19);
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  ASSERT_EQ(rm.find_first_set(lrg, 2), OptoReg::Bad);
  rm.insert(reg1);
  rm.insert(reg2);
  rm.insert(reg3);
  rm.insert(reg4);
  rm.insert(reg5);
  rm.insert(reg6);
  rm.insert(reg7);
  rm.insert(reg8);
  ASSERT_EQ(rm.find_first_set(lrg, 4), reg8);
}

TEST_VM(RegMask, rollover_and_set_all_from) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_in_bits() + 42);
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  rm.set_all_from(reg1);
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits() - 42);
}

TEST_VM(RegMask, rollover_and_set_all_from_offset) {
  RegMask rm;
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  rm.set_all_from_offset();
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits());
}

TEST_VM(RegMask, rollover_and_iterate) {
  RegMask rm;
  OptoReg::Name reg1(rm.rm_size_in_bits() + 2);
  OptoReg::Name reg2(rm.rm_size_in_bits() + 6);
  OptoReg::Name reg3(rm.rm_size_in_bits() + 17);
  OptoReg::Name reg4(rm.rm_size_in_bits() + 43);
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.clear();
  rm.insert(reg1);
  rm.insert(reg2);
  rm.insert(reg3);
  rm.insert(reg4);
  RegMaskIterator rmi(rm);
  ASSERT_EQ(rmi.next(), reg1);
  ASSERT_EQ(rmi.next(), reg2);
  ASSERT_EQ(rmi.next(), reg3);
  ASSERT_EQ(rmi.next(), reg4);
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, rollover_and_subtract_inner_disjoint) {
  RegMask rm1;
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_in_bits() + 42);
  rm1.set_infinite_stack(true);
  rm1.rollover();
  rm1.clear();
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.insert(reg1);
  rm2.insert(42);
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 1);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 1);
}

TEST_VM(RegMask, rollover_and_subtract_inner_overlap) {
  RegMask rm1;
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_in_bits() + 42);
  rm1.set_infinite_stack(true);
  rm1.rollover();
  rm1.clear();
  rm2.set_infinite_stack(true);
  rm2.rollover();
  rm2.clear();
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.insert(reg1);
  rm2.insert(reg1);
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm1.insert(reg1);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
}

#ifdef ASSERT

TEST_VM_ASSERT_MSG(RegMask, unexpected_clone, ".*clone sanity check") {
  RegMask rm1;
  RegMask rm2;
  // Copy contents of rm1 to rm2 inappropriately (no copy constructor)
  memcpy((void*)&rm2, (void*)&rm1, sizeof(RegMask));
  rm2.member(0); // Safeguard in RegMask must catch this.
}

TEST_VM_ASSERT_MSG(RegMask, unexpected_growth, ".*unexpected register mask growth") {
  RegMask rm;
  // Add clearly out of range OptoReg::Name
  rm.insert(std::numeric_limits<OptoReg::Name>::max());
}

TEST_VM_ASSERT_MSG(RegMask, not_growable, ".*register mask not growable") {
  RegMask rm;
  // Add a bit just outside the mask, without having specified an arena for
  // extension.
  rm.insert(rm.rm_size_in_bits());
}

TEST_VM_ASSERT_MSG(RegMask, offset_mismatch, ".*offset mismatch") {
  RegMask rm1;
  RegMask rm2;
  rm1.set_infinite_stack(true);
  rm1.rollover();
  // Cannot assign with different offsets
  rm2.assignFrom(rm1);
}

#endif

#ifndef PRODUCT

Arena* arena() {
  return Thread::current()->resource_area();
}

static void is_basic(const RegMask& rm) {
  ASSERT_EQ(rm.rm_size_in_words(), RegMask::gtest_basic_rm_size_in_words());
}

static void is_extended(const RegMask& rm) {
  ASSERT_TRUE(rm.rm_size_in_words() > RegMask::gtest_basic_rm_size_in_words());
}

static int first_extended() {
  return RegMask::gtest_basic_rm_size_in_words() * BitsPerWord;
}

static void extend(RegMask& rm, unsigned int n = 4) {
  // Extend the given RegMask with at least n dynamically-allocated words.
  rm.insert(OptoReg::Name(first_extended() + (BitsPerWord * n) - 1));
  rm.clear();
  ASSERT_TRUE(rm.rm_size_in_words() >= RegMask::gtest_basic_rm_size_in_words() + n);
}

TEST_VM(RegMask, static_by_default) {
  // Check that a freshly created RegMask does not allocate dynamic memory.
  RegMask rm;
  is_basic(rm);
}

TEST_VM(RegMask, iteration_extended) {
  RegMask rm(arena());
  rm.insert(30);
  rm.insert(31);
  rm.insert(33);
  rm.insert(62);
  rm.insert(first_extended());
  rm.insert(first_extended() + 42);
  rm.insert(first_extended() + 55);
  rm.insert(first_extended() + 456);

  RegMaskIterator rmi(rm);
  ASSERT_TRUE(rmi.next() == OptoReg::Name(30));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(31));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(33));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(62));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended()));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 42));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 55));
  ASSERT_TRUE(rmi.next() == OptoReg::Name(first_extended() + 456));
  ASSERT_FALSE(rmi.has_next());
}

TEST_VM(RegMask, set_all_extended) {
  // Check that set_all doesn't add bits outside of rm.rm_size_bits() on
  // extended RegMasks.
  RegMask rm(arena());
  extend(rm);
  rm.set_all();
  ASSERT_EQ(rm.size(), rm.rm_size_in_bits());
  ASSERT_TRUE(!rm.is_empty());
  // set_all sets infinite_stack bit
  ASSERT_TRUE(rm.is_infinite_stack());
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits());
}

TEST_VM(RegMask, set_all_from_extended) {
  RegMask rm(arena());
  extend(rm);
  rm.set_all_from(OptoReg::Name(42));
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits() - 42);
}

TEST_VM(RegMask, set_all_from_extended_grow) {
  RegMask rm(arena());
  rm.set_all_from(first_extended() + OptoReg::Name(42));
  is_extended(rm);
  contains_expected_num_of_registers(rm, rm.rm_size_in_bits() - first_extended() - 42);
}

TEST_VM(RegMask, clear_extended) {
  // Check that clear doesn't leave any stray bits on extended RegMasks.
  RegMask rm(arena());
  rm.insert(first_extended());
  is_extended(rm);
  rm.set_all();
  rm.clear();
  contains_expected_num_of_registers(rm, 0);
}

TEST_VM(RegMask, and_with_extended_basic) {
  RegMask rm1(arena());
  rm1.insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(first_extended())));

  rm1.and_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  is_basic(rm2);
  rm1.and_with(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, and_with_extended_extended) {
  RegMask rm1(arena());
  rm1.insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(first_extended())));

  rm1.and_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2(arena());
  extend(rm2);
  rm1.and_with(rm2);
  contains_expected_num_of_registers(rm1, 0);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, or_with_extended_basic) {
  RegMask rm1(arena());
  rm1.insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(first_extended())));

  rm1.or_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2;
  is_basic(rm2);
  rm1.or_with(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, or_with_extended_extended) {
  RegMask rm1(arena());
  rm1.insert(OptoReg::Name(first_extended()));
  is_extended(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(first_extended())));

  rm1.or_with(rm1);
  contains_expected_num_of_registers(rm1, 1);

  RegMask rm2(arena());
  extend(rm2);
  rm1.or_with(rm2);
  contains_expected_num_of_registers(rm1, 1);
  contains_expected_num_of_registers(rm2, 0);
}

TEST_VM(RegMask, subtract_extended) {
  RegMask rm1(arena());
  extend(rm1);
  RegMask rm2(arena());
  extend(rm2);

  rm2.set_all();
  ASSERT_TRUE(rm2.is_infinite_stack());
  for (int i = first_extended() + 17; i < (int)rm1.rm_size_in_bits(); i++) {
    rm1.insert(i);
  }
  rm1.set_infinite_stack(true);
  ASSERT_TRUE(rm1.is_infinite_stack());
  rm2.subtract(rm1);
  contains_expected_num_of_registers(rm1, rm1.rm_size_in_bits() - first_extended() - 17);
  contains_expected_num_of_registers(rm2, first_extended() + 17);
}

TEST_VM(RegMask, external_member_extended) {
  RegMask rm(arena());
  extend(rm);
  rm.set_infinite_stack(false);
  ASSERT_FALSE(rm.member(OptoReg::Name(rm.rm_size_in_bits())));
  rm.set_infinite_stack(true);
  ASSERT_TRUE(rm.member(OptoReg::Name(rm.rm_size_in_bits())));
}

TEST_VM(RegMask, overlap_extended) {
  RegMask rm1(arena());
  extend(rm1);
  RegMask rm2(arena());
  extend(rm2);
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.insert(OptoReg::Name(23));
  rm1.insert(OptoReg::Name(2));
  rm1.insert(OptoReg::Name(first_extended() + 12));
  rm2.insert(OptoReg::Name(1));
  rm2.insert(OptoReg::Name(first_extended() + 4));
  ASSERT_FALSE(rm1.overlap(rm2));
  ASSERT_FALSE(rm2.overlap(rm1));
  rm1.insert(OptoReg::Name(first_extended() + 4));
  ASSERT_TRUE(rm1.overlap(rm2));
  ASSERT_TRUE(rm2.overlap(rm1));
}

TEST_VM(RegMask, up_extended) {
  RegMask rm(arena());
  extend(rm);
  ASSERT_TRUE(rm.is_UP());
  rm.insert(OptoReg::Name(1));
  ASSERT_TRUE(rm.is_UP());
  rm.insert(OptoReg::Name(first_extended()));
  ASSERT_FALSE(rm.is_UP());
  rm.clear();
  rm.set_infinite_stack(true);
  ASSERT_FALSE(rm.is_UP());
}

TEST_VM(RegMask, subtract_inner_basic_extended) {
  RegMask rm1;
  RegMask rm2(arena());
  rm1.insert(OptoReg::Name(1));
  rm1.insert(OptoReg::Name(42));
  is_basic(rm1);
  rm2.insert(OptoReg::Name(1));
  rm2.insert(OptoReg::Name(first_extended() + 20));
  is_extended(rm2);
  rm1.subtract_inner(rm2);
  is_basic(rm1);
  contains_expected_num_of_registers(rm1, 1);
  ASSERT_TRUE(rm1.member(OptoReg::Name(42)));
}

TEST_VM(RegMask, subtract_inner_extended_basic) {
  RegMask rm1(arena());
  RegMask rm2;
  rm1.insert(OptoReg::Name(1));
  rm1.insert(OptoReg::Name(42));
  rm1.insert(OptoReg::Name(first_extended() + 20));
  is_extended(rm1);
  rm2.insert(OptoReg::Name(1));
  is_basic(rm2);
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 2);
  ASSERT_TRUE(rm1.member(OptoReg::Name(42)));
  ASSERT_TRUE(rm1.member(OptoReg::Name(first_extended() + 20)));
}

TEST_VM(RegMask, rollover_extended) {
  RegMask rm(arena());
  extend(rm);
  is_extended(rm);
  OptoReg::Name reg1(rm.rm_size_in_bits() + 42);
  rm.set_infinite_stack(true);
  rm.rollover();
  rm.insert(reg1);
  ASSERT_TRUE(rm.member(reg1));
}

TEST_VM(RegMask, rollover_and_subtract_inner_disjoint_extended) {
  RegMask rm1(arena());
  RegMask rm2;
  extend(rm1);
  OptoReg::Name reg1(rm1.rm_size_in_bits() + 42);
  rm1.set_infinite_stack(true);
  rm1.rollover();
  rm1.clear();
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.insert(reg1);
  rm2.insert(42);
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 1);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 1);
}

TEST_VM(RegMask, rollover_and_subtract_inner_overlap_extended) {
  RegMask rm1(arena());
  RegMask rm2;
  OptoReg::Name reg1(rm1.rm_size_in_bits() + 42);
  extend(rm1);
  rm2.set_infinite_stack(true);
  rm2.rollover();
  rm2.clear();
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
  rm1.insert(reg1);
  rm2.insert(reg1);
  rm1.subtract_inner(rm2);
  contains_expected_num_of_registers(rm1, 0);
  rm1.insert(reg1);
  rm2.subtract_inner(rm1);
  contains_expected_num_of_registers(rm2, 0);
}

const uint iterations = 50000;

static uint r;
static uint next_random() {
  r = os::next_random(r);
  return r;
}
static void init_random() {
  if (StressSeed == 0) {
    r = static_cast<uint>(Ticks::now().nanoseconds());
    tty->print_cr("seed: %u", r);
  } else {
    r = StressSeed;
  }
}

static void print(const char* name, const RegMask& mask) {
  tty->print("%s: ", name);
  mask.print();
  tty->print_cr(", size: %u, offset: %u, infinite_stack: %u", mask.rm_size_in_bits(),
                mask.offset_bits(), mask.is_infinite_stack());
}

static void assert_equivalent(const RegMask& mask,
                              const ResourceBitMap& mask_ref,
                              bool infinite_stack_ref) {
  ASSERT_EQ(mask_ref.count_one_bits(), mask.size());
  RegMaskIterator it(mask);
  OptoReg::Name reg = OptoReg::Bad;
  while (it.has_next()) {
    reg = it.next();
    ASSERT_TRUE(OptoReg::is_valid(reg));
    ASSERT_TRUE(mask_ref.at(reg));
  }
  ASSERT_EQ(infinite_stack_ref, mask.is_infinite_stack());
}

static void populate_auxiliary_sets(RegMask& mask_aux,
                                    ResourceBitMap& mask_aux_ref,
                                    uint reg_capacity, uint offset,
                                    bool random_offset) {
  mask_aux.clear();
  mask_aux_ref.clear();
  if (random_offset) {
    uint offset_in_words = offset / BitsPerWord;
    uint capacity_in_words = reg_capacity / BitsPerWord;
    uint new_offset_in_words;
    uint offset_target = next_random() % 3;
    switch (offset_target) {
    case 0: // before
      if (offset_in_words == 0) {
        new_offset_in_words = 0;
      } else {
        new_offset_in_words = next_random() % offset_in_words;
      }
      break;
    case 1: // within
      new_offset_in_words =
          (next_random() % capacity_in_words) + offset_in_words;
      break;
    case 2: // after
      new_offset_in_words = offset_in_words + capacity_in_words +
                            (next_random() % (capacity_in_words));
      break;
    default:
      FAIL();
    }
    offset = new_offset_in_words * BitsPerWord;
    if (offset + RegMask::gtest_rm_size_in_bits_max() > mask_aux_ref.size()) {
      // Ensure that there is space in the reference mask.
      offset = 0;
    }
  }
  mask_aux.gtest_set_offset(offset / BitsPerWord);
  assert_equivalent(mask_aux, mask_aux_ref, false);
  uint max_size;
  uint size_target = next_random() % 3;
  switch (size_target) {
  case 0: // smaller
    max_size = reg_capacity / 2;
    break;
  case 1: // equal
    max_size = reg_capacity;
    break;
  case 2: // larger (if possible)
    max_size = RegMask::gtest_rm_size_in_bits_max();
    break;
  default:
    FAIL();
  }
  uint regs;
  uint regs_target = next_random() % 3;
  switch (regs_target) {
  case 0: // sparse
    regs = next_random() % 8;
    break;
  case 1: // medium
    regs = next_random() % (max_size / 8);
    break;
  case 2: // dense
    regs = next_random() % max_size;
    break;
  default:
    FAIL();
  }
  for (uint i = 0; i < regs; i++) {
    uint reg = (next_random() % max_size) + offset;
    mask_aux.insert(reg);
    mask_aux_ref.set_bit(reg);
  }
  mask_aux.set_infinite_stack(next_random() % 2);
  assert_equivalent(mask_aux, mask_aux_ref, mask_aux.is_infinite_stack());

  if (Verbose) {
    print("mask_aux", mask_aux);
  }
}

static void stack_extend_ref_masks(ResourceBitMap& mask1, bool infinite_stack1,
                                   uint size_bits1, uint offset1,
                                   ResourceBitMap& mask2, bool infinite_stack2,
                                   uint size_bits2, uint offset2) {
  uint size_bits_after = MAX2(size_bits1, size_bits2);
  if (infinite_stack1) {
    mask1.set_range(size_bits1 + offset1, size_bits_after + offset1);
  }
  if (infinite_stack2) {
    mask2.set_range(size_bits2 + offset2, size_bits_after + offset2);
  }
}

TEST_VM(RegMask, random) {
  ResourceMark rm;
  RegMask mask(arena());
  ResourceBitMap mask_ref(std::numeric_limits<short>::max() + 1);
  bool infinite_stack_ref = false;
  uint offset_ref = 0;
  init_random();

  for (uint i = 0; i < iterations; i++) {
    if (Verbose) {
      print("mask    ", mask);
      tty->print("%u. ", i);
    }
    uint action = next_random() % 13;
    uint reg;
    uint size_bits_before = mask.rm_size_in_bits();
    // This copy is used for stack-extension in overlap.
    ResourceBitMap mask_ref_copy(std::numeric_limits<short>::max() + 1);
    mask_ref_copy.clear();
    mask_ref.iterate([&](BitMap::idx_t index) {
      mask_ref_copy.set_bit(index);
      return true;
    });
    ResourceBitMap mask_aux_ref(std::numeric_limits<short>::max() + 1);
    RegMask mask_aux(arena());
    switch (action) {
    case 0:
      reg = (next_random() % RegMask::gtest_rm_size_in_bits_max()) + offset_ref;
      if (Verbose) {
        tty->print_cr("action: Insert");
        tty->print("value   : ");
        OptoReg::dump(reg);
        tty->cr();
      }
      mask.insert(reg);
      mask_ref.set_bit(reg);
      if (mask.is_infinite_stack() && reg >= size_bits_before) {
        // Stack-extend reference bitset.
        mask_ref.set_range(size_bits_before + offset_ref,
                           mask.rm_size_in_bits() + offset_ref);
      }
      break;
    case 1:
      reg = (next_random() % size_bits_before) + offset_ref;
      if (Verbose) {
        tty->print_cr("action: Remove");
        tty->print("value   : ");
        OptoReg::dump(reg);
        tty->cr();
      }
      mask.remove(reg);
      mask_ref.clear_bit(reg);
      break;
    case 2:
      if (Verbose) {
        tty->print_cr("action: Clear");
      }
      mask.clear();
      mask_ref.clear();
      infinite_stack_ref = false;
      break;
    case 3:
      if (offset_ref > 0) {
        // set_all expects a zero-offset.
        break;
      }
      if (Verbose) {
        tty->print_cr("action: set_all");
      }
      mask.set_all();
      mask_ref.set_range(0, size_bits_before);
      infinite_stack_ref = true;
      break;
    case 4:
      if (Verbose) {
        tty->print_cr("action: and_with");
      }
      populate_auxiliary_sets(mask_aux, mask_aux_ref, mask.rm_size_in_bits(),
                              offset_ref, /*random_offset*/ false);
      mask.and_with(mask_aux);
      stack_extend_ref_masks(mask_ref, infinite_stack_ref, size_bits_before,
                             offset_ref, mask_aux_ref, mask_aux.is_infinite_stack(),
                             mask_aux.rm_size_in_bits(), mask_aux.offset_bits());
      mask_ref.set_intersection(mask_aux_ref);
      infinite_stack_ref = infinite_stack_ref && mask_aux.is_infinite_stack();
      break;
    case 5:
      if (Verbose) {
        tty->print_cr("action: or_with");
      }
      populate_auxiliary_sets(mask_aux, mask_aux_ref, mask.rm_size_in_bits(),
                              offset_ref, /*random_offset*/ false);
      mask.or_with(mask_aux);
      stack_extend_ref_masks(mask_ref, infinite_stack_ref, size_bits_before,
                             offset_ref, mask_aux_ref, mask_aux.is_infinite_stack(),
                             mask_aux.rm_size_in_bits(), mask_aux.offset_bits());
      mask_ref.set_union(mask_aux_ref);
      infinite_stack_ref = infinite_stack_ref || mask_aux.is_infinite_stack();
      break;
    case 6:
      if (Verbose) {
        tty->print_cr("action: subtract");
      }
      populate_auxiliary_sets(mask_aux, mask_aux_ref, mask.rm_size_in_bits(),
                              offset_ref, /*random_offset*/ false);
      mask.subtract(mask_aux);
      stack_extend_ref_masks(mask_ref, infinite_stack_ref, size_bits_before,
                             offset_ref, mask_aux_ref, mask_aux.is_infinite_stack(),
                             mask_aux.rm_size_in_bits(), mask_aux.offset_bits());
      mask_ref.set_difference(mask_aux_ref);
      if (mask_aux.is_infinite_stack()) {
        infinite_stack_ref = false;
      }
      break;
    case 7:
      if (Verbose) {
        tty->print_cr("action: subtract_inner");
      }
      populate_auxiliary_sets(mask_aux, mask_aux_ref, mask.rm_size_in_bits(),
                              offset_ref, /*random_offset*/ true);
      // subtract_inner expects an argument register mask with infinite_stack =
      // false.
      mask_aux.set_infinite_stack(false);
      mask.subtract_inner(mask_aux);
      // subtract_inner does not have "stack-extension semantics".
      mask_ref.set_difference(mask_aux_ref);
      break;
    case 8:
      if (Verbose) {
        tty->print_cr("action: overlap");
      }
      populate_auxiliary_sets(mask_aux, mask_aux_ref, mask.rm_size_in_bits(),
                              offset_ref, /*random_offset*/ false);
      // Stack-extend a copy of mask_ref to avoid mutating the original.
      stack_extend_ref_masks(mask_ref_copy, infinite_stack_ref, size_bits_before,
                             offset_ref, mask_aux_ref, mask_aux.is_infinite_stack(),
                             mask_aux.rm_size_in_bits(), mask_aux.offset_bits());
      ASSERT_EQ(mask_ref_copy.intersects(mask_aux_ref) ||
                    (infinite_stack_ref && mask_aux.is_infinite_stack()),
                mask.overlap(mask_aux));
      break;
    case 9:
      if (Verbose) {
        tty->print_cr("action: rollover");
      }
      // rollover expects the mask to be cleared and with infinite_stack = true
      mask.clear();
      mask.set_infinite_stack(true);
      mask_ref.clear();
      infinite_stack_ref = true;
      if (mask.rollover()) {
        offset_ref += size_bits_before;
        mask_ref.set_range(offset_ref, offset_ref + size_bits_before);
      }
      break;
    case 10:
      if (Verbose) {
        tty->print_cr("action: reset");
      }
      mask.gtest_set_offset(0);
      mask.clear();
      mask_ref.clear();
      infinite_stack_ref = false;
      offset_ref = 0;
      break;
    case 11:
      if (Verbose) {
        tty->print_cr("action: set_all_from_offset");
      }
      mask.set_all_from_offset();
      mask_ref.set_range(offset_ref, offset_ref + size_bits_before);
      infinite_stack_ref = true;
      break;
    case 12:
      reg = (next_random() % size_bits_before) + offset_ref;
      if (Verbose) {
        tty->print_cr("action: set_all_from");
        tty->print("value   : ");
        OptoReg::dump(reg);
        tty->cr();
      }
      mask.set_all_from(reg);
      mask_ref.set_range(reg, offset_ref + size_bits_before);
      infinite_stack_ref = true;
      break;
    default:
      FAIL() << "Unimplemented action";
    }
    ASSERT_NO_FATAL_FAILURE(assert_equivalent(mask, mask_ref, infinite_stack_ref));
  }
}

// Randomly sets register mask contents. Does not change register mask size.
static void randomize(RegMask& rm) {
  rm.clear();
  // Uniform distribution over number of registers.
  uint regs = next_random() % (rm.rm_size_in_bits() + 1);
  for (uint i = 0; i < regs; i++) {
    uint reg = (next_random() % rm.rm_size_in_bits()) + rm.offset_bits();
    rm.insert(reg);
  }
  rm.set_infinite_stack(next_random() % 2);
}

static uint grow_randomly(RegMask& rm, uint min_growth = 1,
                          uint max_growth = 3) {
  // Grow between min_growth and max_growth times.
  uint grow = min_growth + (max_growth > 0 ? next_random() % max_growth : 0);
  for (uint i = 0; i < grow; ++i) {
    uint reg = rm.rm_size_in_bits();
    if (reg >= RegMask::gtest_rm_size_in_bits_max()) {
      // Cannot grow more
      break;
    }
    // Force grow
    rm.insert(reg);
    if (!rm.is_infinite_stack()) {
      // Restore
      rm.remove(reg);
    }
  }
  // Return how many times we grew
  return grow;
}

TEST_VM(RegMask, random_copy) {
  init_random();

  auto print_failure = [&](const RegMask& src, const RegMask& dst) {
    tty->print_cr("Failure, src and dst not equal");
    tty->print("src: ");
    src.dump_hex();
    tty->cr();
    tty->print("dst: ");
    dst.dump_hex();
    tty->cr();
  };

  // Test copying a larger register mask
  for (uint i = 0; i < iterations; i++) {
    ResourceMark rm;

    // Create source RegMask
    RegMask src(arena());

    // Grow source randomly
    grow_randomly(src);

    // Randomly initialize source
    randomize(src);

    // Copy construct source to destination
    RegMask dst(src, arena());

    // Check equality
    bool passed = src.gtest_equals(dst);
    if (Verbose && !passed) {
      print_failure(src, dst);
    }
    ASSERT_TRUE(passed);
  }

  // Test copying a smaller register mask
  for (uint i = 0; i < iterations; i++) {
    ResourceMark rm;

    // Create destination RegMask
    RegMask dst(arena());

    // Grow destination arbitrarily (1-3 times)
    uint growth = grow_randomly(dst, 1, 3);

    // Create source RegMask
    RegMask src(arena());

    // Grow source arbitrarily, but not as much as destination
    grow_randomly(src, 0, growth - 1);

    // Randomly initialize source
    randomize(src);

    // Set destination to source
    dst.assignFrom(src);

    // Check equality
    bool passed = src.gtest_equals(dst);
    if (Verbose && !passed) {
      print_failure(src, dst);
    }
    ASSERT_TRUE(passed);
  }
}

#endif // !PRODUCT
