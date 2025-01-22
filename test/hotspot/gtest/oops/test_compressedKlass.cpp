/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "oops/compressedKlass.inline.hpp"
#include "oops/klass.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"

TEST_VM(CompressedKlass, basics) {
  if (!UseCompressedClassPointers) {
    return;
  }
  ASSERT_LE((address)0, CompressedKlassPointers::base());
  ASSERT_LE(CompressedKlassPointers::base(), CompressedKlassPointers::klass_range_start());
  ASSERT_LT(CompressedKlassPointers::klass_range_start(), CompressedKlassPointers::klass_range_end());
  ASSERT_LE(CompressedKlassPointers::klass_range_end(), CompressedKlassPointers::encoding_range_end());

  switch (CompressedKlassPointers::shift()) {
  case 0:
    ASSERT_EQ(CompressedKlassPointers::encoding_range_end() - CompressedKlassPointers::base(), (ptrdiff_t)(4 * G));
    break;
  case 3:
    ASSERT_EQ(CompressedKlassPointers::encoding_range_end() - CompressedKlassPointers::base(), (ptrdiff_t)(32 * G));
    break;
  default:
    const size_t expected_size = nth_bit(CompressedKlassPointers::narrow_klass_pointer_bits() + CompressedKlassPointers::shift());
    ASSERT_EQ(CompressedKlassPointers::encoding_range_end() - CompressedKlassPointers::base(), (ptrdiff_t)expected_size);
  }
}

TEST_VM(CompressedKlass, ccp_off) {
  if (UseCompressedClassPointers) {
    return;
  }
  ASSERT_EQ(CompressedKlassPointers::klass_range_start(), (address)nullptr);
  ASSERT_EQ(CompressedKlassPointers::klass_range_end(), (address)nullptr);
  // We should be able to call CompressedKlassPointers::is_encodable, and it should
  // always return false
  ASSERT_FALSE(CompressedKlassPointers::is_encodable((address)0x12345));
}


TEST_VM(CompressedKlass, test_too_low_address) {
  if (!UseCompressedClassPointers) {
    return;
  }
  address really_low = (address) 32;
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(really_low));
  address low = CompressedKlassPointers::klass_range_start() - 1;
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(low));
}

TEST_VM(CompressedKlass, test_too_high_address) {
  if (!UseCompressedClassPointers) {
    return;
  }
  address really_high = (address) UINTPTR_MAX;
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(really_high));
  address high = CompressedKlassPointers::klass_range_end();
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(high));
}

TEST_VM(CompressedKlass, test_unaligned_address) {
  if (!UseCompressedClassPointers) {
    return;
  }
  const size_t alignment = CompressedKlassPointers::klass_alignment_in_bytes();
  address addr = CompressedKlassPointers::klass_range_start() + alignment - 1;
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(addr));
  // Try word-aligned, but not sufficiently aligned
  if (alignment > BytesPerWord) {
    addr = CompressedKlassPointers::klass_range_start() + BytesPerWord;
    ASSERT_FALSE(CompressedKlassPointers::is_encodable(addr));
  }
  addr = CompressedKlassPointers::klass_range_end() - 1;
  ASSERT_FALSE(CompressedKlassPointers::is_encodable(addr));
}

TEST_VM(CompressedKlass, test_good_address) {
  if (!UseCompressedClassPointers) {
    return;
  }
  const size_t alignment = CompressedKlassPointers::klass_alignment_in_bytes();
  address addr = CompressedKlassPointers::klass_range_start();
  ASSERT_TRUE(CompressedKlassPointers::is_encodable(addr));
  addr = CompressedKlassPointers::klass_range_end() - alignment;
  ASSERT_TRUE(CompressedKlassPointers::is_encodable(addr));
}

// This tests the protection zone mechanism. If the encoding base is not zero, VM should have
// established a protection zone. Decoding an nKlass==0 should result in a Klass* that, upon
// access, causes a SIGSEGV
static bool test_nklass_protection_zone() {
  if (!UseCompressedClassPointers) {
    tty->print_cr("UseCompressedClassPointers is off, test not possible");
    return true; // skipped
  } else if (CompressedKlassPointers::base() == nullptr) {
    tty->print_cr("Zero-based encoding; test not needed");
    return true; // skipped
  } else {
    constexpr narrowKlass nk = 0;
    Klass* const k = CompressedKlassPointers::decode_not_null_without_asserts(nk, CompressedKlassPointers::base(), CompressedKlassPointers::shift());
    assert(k == (Klass*) CompressedKlassPointers::base(), "Sanity? (" PTR_FORMAT " vs " PTR_FORMAT ")",
           p2i(k), p2i(CompressedKlassPointers::base()));
    // Now call a virtual function on that klass.
    k->print_on(tty); // << loading vtable ptr from protected page, crash expected here
    return false;
  }
}

// This does not work yet, since gtest death tests don't work with real signals. That needs to be fixed first (see JDK-8348028).
TEST_VM_FATAL_ERROR_MSG(CompressedKlass, DISABLED_test_nklass_protection_zone_death_test, ".*SIGSEGV.*") {
  if (test_nklass_protection_zone()) {
    // Still alive but returned true, so we skipped the test.
    // Do a fake assert that matches the regex above to satisfy the death test
    guarantee(false, "fake message ignore this - SIGSEGV");
  }
}
