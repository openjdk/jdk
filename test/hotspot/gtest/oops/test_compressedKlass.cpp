/*
 * Copyright (c) 2024, 2025, Red Hat, Inc. All rights reserved.
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

#include "classfile/vmClasses.hpp"
#include "oops/compressedKlass.inline.hpp"
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

TEST_VM(CompressedKlass, test_is_valid_narrow_klass) {
  if (!UseCompressedClassPointers) {
    return;
  }
  ASSERT_FALSE(CompressedKlassPointers::is_valid_narrow_klass_id(0));
  narrowKlass nk_jlC = CompressedKlassPointers::encode((Klass*)vmClasses::Class_klass());
  ASSERT_TRUE(CompressedKlassPointers::is_valid_narrow_klass_id(nk_jlC));
  if (CompressedClassSpaceSize < 4 * G && CompressedKlassPointers::base() != nullptr) {
    ASSERT_FALSE(CompressedKlassPointers::is_valid_narrow_klass_id(0xFFFFFFFF));
  }
}
