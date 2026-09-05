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
 */

#include "oops/flatArrayOop.inline.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

// INTENTIONALLY SMALL BACKING, SHOULD ONLY CONTAIN METADATA + A FEW ELEMENTS.
static unsigned char memory[1024];

// Do not perform operations on the array's memory without ensuring that the
// backing is large enough and you will not go out of bounds.
static flatArrayOop fake_flat_array(int length) {
  flatArrayOop farr = flatArrayOop(cast_to_oop(memory));
  // We can't ensure the backing for the length, but we can still do pointer
  // arithmetic and e.g. ensure that the resulting pointers didn't overflow.
  farr->set_length(length);
  return farr;
}

// What FlatArrayKlass::array_layout_helper does, but w/o InlineKlass
static int make_lh(int payload_size_bytes, bool null_free) {
  BasicType etype = T_FLAT_ELEMENT;
  int esize = log2i_exact(round_up_power_of_2(payload_size_bytes));
  int hsize = arrayOopDesc::base_offset_in_bytes(etype);
  return Klass::array_layout_helper(Klass::_lh_array_tag_flat_value, null_free, hsize, etype, esize);
}

static void ensure_no_overflow(flatArrayOop farr, int lh) {
  void* vaa_small = farr->value_at_addr(123, lh);
  EXPECT_TRUE(vaa_small >= farr);
  void* vaa_large = farr->value_at_addr(321999888, lh);
  EXPECT_TRUE(vaa_large >= farr);
}

TEST_VM(flatArrayOopDesc, value_at_addr_intbox_nullable) {
  flatArrayOop farr = fake_flat_array(500000000);
  ensure_no_overflow(farr, make_lh(8, false));
}


TEST_VM(flatArrayOopDesc, value_at_addr_intbox_null_free) {
  flatArrayOop farr = fake_flat_array(500000000);
  ensure_no_overflow(farr, make_lh(4, true));
}
