/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "unittest.hpp"

class ZAddressTest : public ::testing::Test {
protected:
  static zpointer color(uintptr_t value, uintptr_t color) {
    return ZAddress::color(zaddress(value | ZAddressHeapBase), color);
  }

  static const uintptr_t valid_value = (1 << 3 /* LogMinObjectAlignment */);
  static const uintptr_t null_value = 0;

  enum ZColor {
    Uncolored,
    RemappedYoung0,
    RemappedYoung1,
    RemappedOld0,
    RemappedOld1,
    MarkedYoung0,
    MarkedYoung1,
    MarkedOld0,
    MarkedOld1,
    Finalizable0,
    Finalizable1,
    Remembered0,
    Remembered1,
    Remembered11
  };

  static uintptr_t make_color(ZColor remembered, ZColor remapped_young, ZColor remapped_old, ZColor marked_young, ZColor marked_old) {
    uintptr_t color = 0;
    switch (remapped_young) {
    case RemappedYoung0: {
      switch (remapped_old) {
      case RemappedOld0:
        color |= ZPointer::remap_bits(ZPointerRemapped00);
        break;
      case RemappedOld1:
        color |= ZPointer::remap_bits(ZPointerRemapped10);
        break;
      default:
        EXPECT_TRUE(false);
      }
      break;
    }
    case RemappedYoung1: {
      switch (remapped_old) {
      case RemappedOld0:
        color |= ZPointer::remap_bits(ZPointerRemapped01);
        break;
      case RemappedOld1:
        color |= ZPointer::remap_bits(ZPointerRemapped11);
        break;
      default:
        EXPECT_TRUE(false);
      }
      break;
    }
    default:
      EXPECT_TRUE(false);
    }

    switch (marked_young) {
    case MarkedYoung0:
      color |= ZPointerMarkedYoung0;
      break;
    case MarkedYoung1:
      color |= ZPointerMarkedYoung1;
      break;
    default:
      EXPECT_TRUE(false);
    }

    switch (marked_old) {
    case MarkedOld0:
      color |= ZPointerMarkedOld0;
      break;
    case MarkedOld1:
      color |= ZPointerMarkedOld1;
      break;
    case Finalizable0:
      color |= ZPointerFinalizable0;
      break;
    case Finalizable1:
      color |= ZPointerFinalizable1;
      break;
    default:
      EXPECT_TRUE(false);
    }

    switch (remembered) {
    case Remembered0:
      color |= ZPointerRemembered0;
      break;
    case Remembered1:
      color |= ZPointerRemembered1;
      break;
    case Remembered11:
      color |= ZPointerRemembered0 | ZPointerRemembered1;
      break;
    default:
      EXPECT_TRUE(false);
    }

    return color;
  }

  static zpointer color(uintptr_t addr,
                        ZColor remembered,
                        ZColor remapped_young,
                        ZColor remapped_old,
                        ZColor marked_young,
                        ZColor marked_old) {
    if (remembered == Uncolored &&
        remapped_young == Uncolored &&
        remapped_old == Uncolored &&
        marked_young == Uncolored &&
        marked_old == Uncolored) {
      return zpointer(addr);
    } else {
      return color(addr, make_color(remembered, remapped_young, remapped_old, marked_young, marked_old));
    }
  }

  static bool is_remapped_young_odd(uintptr_t bits) {
    return ZPointer::remap_bits(bits) & (ZPointerRemapped01 | ZPointerRemapped11);
  }

  static bool is_remapped_old_odd(uintptr_t bits) {
    return ZPointer::remap_bits(bits) & (ZPointerRemapped10 | ZPointerRemapped11);
  }

  static bool is_marked_young_odd(uintptr_t bits) {
    return bits & ZPointerMarkedYoung1;
  }

  static bool is_marked_old_odd(uintptr_t bits) {
    return bits & (ZPointerMarkedOld1 | ZPointerFinalizable1);
  }

  static bool is_remembered(uintptr_t bits) {
    return bits & (ZPointerRemembered0 | ZPointerRemembered1);
  }

  static bool is_remembered_odd(uintptr_t bits) {
    return bits & (ZPointerRemembered1);
  }

  static bool is_remembered_even(uintptr_t bits) {
    return bits & (ZPointerRemembered0);
  }

  static void test_is_checks_on(uintptr_t value,
                                ZColor remembered,
                                ZColor remapped_young,
                                ZColor remapped_old,
                                ZColor marked_young,
                                ZColor marked_old) {
    const zpointer ptr = color(value, remembered, remapped_young, remapped_old, marked_young, marked_old);
    uintptr_t ptr_raw = untype(ptr);

    EXPECT_TRUE(ZPointerLoadGoodMask != 0);
    EXPECT_TRUE(ZPointerStoreGoodMask != 0);

    bool ptr_raw_null = ptr_raw == 0;
    bool global_remapped_old_odd = is_remapped_old_odd(ZPointerLoadGoodMask);
    bool global_remapped_young_odd = is_remapped_young_odd(ZPointerLoadGoodMask);
    bool global_marked_old_odd = is_marked_old_odd(ZPointerStoreGoodMask);
    bool global_marked_young_odd = is_marked_young_odd(ZPointerStoreGoodMask);
    bool global_remembered_odd = is_remembered_odd(ZPointerStoreGoodMask);
    bool global_remembered_even = is_remembered_even(ZPointerStoreGoodMask);

    if (ptr_raw_null) {
      EXPECT_FALSE(ZPointer::is_marked_any_old(ptr));
      EXPECT_FALSE(ZPointer::is_load_good(ptr));
      EXPECT_TRUE(ZPointer::is_load_good_or_null(ptr));
      EXPECT_FALSE(ZPointer::is_load_bad(ptr));
      EXPECT_FALSE(ZPointer::is_mark_good(ptr));
      EXPECT_TRUE(ZPointer::is_mark_good_or_null(ptr));
      EXPECT_FALSE(ZPointer::is_mark_bad(ptr));
      EXPECT_FALSE(ZPointer::is_store_good(ptr));
      EXPECT_TRUE(ZPointer::is_store_good_or_null(ptr));
      EXPECT_FALSE(ZPointer::is_store_bad(ptr));
    } else {
      bool ptr_remapped_old_odd = is_remapped_old_odd(ptr_raw);
      bool ptr_remapped_young_odd = is_remapped_young_odd(ptr_raw);
      bool ptr_marked_old_odd = is_marked_old_odd(ptr_raw);
      bool ptr_marked_young_odd = is_marked_young_odd(ptr_raw);
      bool ptr_final = ptr_raw & (ZPointerFinalizable0 | ZPointerFinalizable1);
      bool ptr_remembered = is_power_of_2(ptr_raw & (ZPointerRemembered0 | ZPointerRemembered1));
      bool ptr_remembered_odd = is_remembered_odd(ptr_raw);
      bool ptr_remembered_even = is_remembered_even(ptr_raw);
      bool ptr_colored_null = !ptr_raw_null && (ptr_raw & ~ZPointerAllMetadataMask) == 0;

      bool same_old_marking = global_marked_old_odd == ptr_marked_old_odd;
      bool same_young_marking = global_marked_young_odd == ptr_marked_young_odd;
      bool same_old_remapping = global_remapped_old_odd == ptr_remapped_old_odd;
      bool same_young_remapping = global_remapped_young_odd == ptr_remapped_young_odd;
      bool same_remembered = ptr_remembered_even == global_remembered_even && ptr_remembered_odd == global_remembered_odd;

      EXPECT_EQ(ZPointer::is_marked_finalizable(ptr), same_old_marking && ptr_final);
      EXPECT_EQ(ZPointer::is_marked_any_old(ptr), same_old_marking);
      EXPECT_EQ(ZPointer::is_remapped(ptr), same_old_remapping && same_young_remapping);
      EXPECT_EQ(ZPointer::is_load_good(ptr), same_old_remapping && same_young_remapping);
      EXPECT_EQ(ZPointer::is_load_good_or_null(ptr), same_old_remapping && same_young_remapping);
      EXPECT_EQ(ZPointer::is_load_bad(ptr), !same_old_remapping || !same_young_remapping);
      EXPECT_EQ(ZPointer::is_mark_good(ptr), same_young_remapping && same_old_remapping && same_young_marking && same_old_marking);
      EXPECT_EQ(ZPointer::is_mark_good_or_null(ptr), same_young_remapping && same_old_remapping && same_young_marking && same_old_marking);
      EXPECT_EQ(ZPointer::is_mark_bad(ptr), !same_young_remapping || !same_old_remapping || !same_young_marking || !same_old_marking);
      EXPECT_EQ(ZPointer::is_store_good(ptr), same_young_remapping && same_old_remapping && same_young_marking && same_old_marking && ptr_remembered && same_remembered);
      EXPECT_EQ(ZPointer::is_store_good_or_null(ptr), same_young_remapping && same_old_remapping && same_young_marking && same_old_marking && ptr_remembered && same_remembered);
      EXPECT_EQ(ZPointer::is_store_bad(ptr), !same_young_remapping || !same_old_remapping || !same_young_marking || !same_old_marking || !ptr_remembered || !same_remembered);
    }
  }

  static void test_is_checks_on_all() {
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered0, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered1, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung0, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld0, MarkedYoung1, MarkedOld1);

    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung0, MarkedOld1);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld0);
    test_is_checks_on(valid_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);
    test_is_checks_on(null_value, Remembered11, RemappedYoung1, RemappedOld1, MarkedYoung1, MarkedOld1);

    test_is_checks_on(null_value, Uncolored, Uncolored, Uncolored, Uncolored, Uncolored);
  }

  static void advance_and_test_young_phase(int& phase, int amount) {
    for (int i = 0; i < amount; ++i) {
      if (++phase & 1) {
        ZGlobalsPointers::flip_young_mark_start();
      } else {
        ZGlobalsPointers::flip_young_relocate_start();
      }
      test_is_checks_on_all();
    }
  }

  static void advance_and_test_old_phase(int& phase, int amount) {
    for (int i = 0; i < amount; ++i) {
      if (++phase & 1) {
        ZGlobalsPointers::flip_old_mark_start();
      } else {
        ZGlobalsPointers::flip_old_relocate_start();
      }
      test_is_checks_on_all();
    }
  }

  static void is_checks() {
    int young_phase = 0;
    int old_phase = 0;
    // Setup
    ZGlobalsPointers::initialize();
    test_is_checks_on_all();

    advance_and_test_old_phase(old_phase, 4);
    advance_and_test_young_phase(young_phase, 4);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 4);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 4);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 4);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 4);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 3);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 3);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 3);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 3);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 2);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 2);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 2);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 2);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 1);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 1);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 1);

    advance_and_test_old_phase(old_phase, 1);
    advance_and_test_young_phase(young_phase, 1);
  }
};

TEST_F(ZAddressTest, is_checks) {
  is_checks();
}
