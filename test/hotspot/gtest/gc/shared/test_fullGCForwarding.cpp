/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#include "gc/shared/fullGCForwarding.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

// This gives us 2 bits to address forwarding in object header,
// which corresponds to 4 words/32 bytes block size.
using TestFullGCForwarding = FullGCForwardingImpl<4>;

class FullGCForwardingTest : public testing::Test {
  // Size of fake heap, in words.
  static const int FAKE_HEAP_SIZE = 64;
  // Alignment of fake heap, in words;
  static const int FAKE_HEAP_ALIGNMENT = 8;
  // Real size of fake heap, considering alignment.
  static const int FAKE_HEAP_SIZE_UNALIGNED = FAKE_HEAP_SIZE + FAKE_HEAP_ALIGNMENT;
  // Bit-pattern which must not change.
  static const uintptr_t BIT_PATTERN = LP64_ONLY(0xA5A5A5A5A5A5A5A0) NOT_LP64(0xA5A5A5A0);
  // Number of bits used for forwarding.
  static const int NUM_FWD_BITS = 4;
  // Forwarding bit mask.
  static const uintptr_t FWD_BIT_MASK = right_n_bits(NUM_FWD_BITS);

  HeapWord* _unaligned_heap;
protected:
  HeapWord* _heap;

public:
  FullGCForwardingTest() {
    _unaligned_heap = NEW_C_HEAP_ARRAY(HeapWord, FAKE_HEAP_SIZE_UNALIGNED, mtGC);
    _heap = align_up(_unaligned_heap, FAKE_HEAP_ALIGNMENT * sizeof(HeapWord));
    TestFullGCForwarding::set_fallback_table_log2_start_size(2);
    TestFullGCForwarding::initialize(MemRegion(&_heap[0], &_heap[64]));
    TestFullGCForwarding::begin();
  }
  ~FullGCForwardingTest() {
    TestFullGCForwarding::end();
    FREE_C_HEAP_ARRAY(HeapWord, _unaligned_heap);
  }

  oop new_oop(int index) {
    HeapWord* oop_addr = _heap + index;
    // Initialize 'mark-word'
    // Bit-pattern in upper 58 bits (which must not change)
    // and 000001 in lowest 6 bits (which corresponds to not-forwarded).
    oop obj = cast_to_oop(oop_addr);
    obj->set_mark(markWord(BIT_PATTERN | markWord::unlocked_value));
    return obj;
  }

  void assert_forwarding(oop obj, oop fwd, uintptr_t bits) {
    ASSERT_EQ(fwd, TestFullGCForwarding::forwardee(obj));
    ASSERT_TRUE(TestFullGCForwarding::is_forwarded(obj));
    uintptr_t mark = obj->mark().value();
    ASSERT_EQ(bits, mark & FWD_BIT_MASK);
    uintptr_t pattern = BIT_PATTERN;
    ASSERT_EQ(pattern, mark & ~FWD_BIT_MASK);
  }
};

TEST_VM_F(FullGCForwardingTest, basic) {

  oop o1 = new_oop(0);
  oop o2 = new_oop(1);

  // Create a single forwarding.
  TestFullGCForwarding::forward_to(o1, o2);
  // Check that forwarding is correct.
  assert_forwarding(o1, o2, 0b0011);

}

TEST_VM_F(FullGCForwardingTest, full_block) {

  oop o1 = new_oop(0);
  oop o2 = new_oop(1);
  oop o3 = new_oop(2);
  oop o4 = new_oop(3);
  oop o5 = new_oop(4);
  oop o6 = new_oop(5);
  oop o7 = new_oop(6);
  oop o8 = new_oop(7);

  // Forward objects in first block to objects in second block.
  TestFullGCForwarding::forward_to(o1, o5);
  TestFullGCForwarding::forward_to(o2, o6);
  TestFullGCForwarding::forward_to(o3, o7);
  // Note: this would be recorded in the fallback table.
  TestFullGCForwarding::forward_to(o4, o8);

  // Check that forwardings are correct.
  assert_forwarding(o1, o5, 0b0011);
  assert_forwarding(o2, o6, 0b0111);
  assert_forwarding(o3, o7, 0b1011);
  assert_forwarding(o4, o8, 0b1111); // Fallback-pattern

}

TEST_VM_F(FullGCForwardingTest, full_block_cross_boundary) {

  oop o1 = new_oop(0);
  oop o2 = new_oop(1);
  oop o3 = new_oop(2);
  oop o4 = new_oop(3);
  oop o5 = new_oop(6);
  oop o6 = new_oop(7);
  oop o7 = new_oop(8);
  oop o8 = new_oop(9);

  // Forward objects in first block to objects in second block.
  TestFullGCForwarding::forward_to(o1, o5);
  TestFullGCForwarding::forward_to(o2, o6);
  TestFullGCForwarding::forward_to(o3, o7);
  // Note: this would be recorded in the fallback table.
  TestFullGCForwarding::forward_to(o4, o8);

  // Check that forwardings are correct.
  assert_forwarding(o1, o5, 0b0011);
  assert_forwarding(o2, o6, 0b0111);
  assert_forwarding(o3, o7, 0b1011);
  assert_forwarding(o4, o8, 0b1111); // Fallback-pattern

}

TEST_VM_F(FullGCForwardingTest, full_block_out_of_order) {

  oop o1 = new_oop(0);
  oop o2 = new_oop(1);
  oop o3 = new_oop(2);
  oop o4 = new_oop(3);
  oop o5 = new_oop(4);
  oop o6 = new_oop(5);
  oop o7 = new_oop(6);
  oop o8 = new_oop(7);

  // Forward objects in first block to objects in second block.
  TestFullGCForwarding::forward_to(o1, o7);
  TestFullGCForwarding::forward_to(o2, o8);
  // This should go to fallback table, because the base offset is at o7.
  TestFullGCForwarding::forward_to(o3, o5);
  // This should go to fallback table, because the base offset is at o7.
  TestFullGCForwarding::forward_to(o4, o6);

  // Check that forwardings are correct.
  assert_forwarding(o1, o7, 0b0011);
  assert_forwarding(o2, o8, 0b0111);
  assert_forwarding(o3, o5, 0b1111); // Fallback-pattern
  assert_forwarding(o4, o6, 0b1111); // Fallback-pattern

}

TEST_VM_F(FullGCForwardingTest, stress_fallback) {

  oop _objs[32];
  for (int i = 0; i < 32; i++) {
    _objs[i] = new_oop(i);
  }

  // Forward objects reverse order to put most in fallback.
  for (int i = 0; i < 32; i++) {
    TestFullGCForwarding::forward_to(_objs[i], _objs[31 - i]);
  }
  // Check that forwardings are correct.
  for (int i = 0; i < 32; i++) {
    uintptr_t bits = i % 4 == 0 ? 0b0011 : 0b1111;
    assert_forwarding(_objs[i], _objs[31 - i], bits);
  }
}
