/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
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
    RemappedMinor0,
    RemappedMinor1,
    RemappedMajor0,
    RemappedMajor1,
    MarkedMinor0,
    MarkedMinor1,
    MarkedMajor0,
    MarkedMajor1,
    Finalizable0,
    Finalizable1,
    Remembered0,
    Remembered1,
    Remembered11
  };

  static uintptr_t make_color(ZColor remembered, ZColor remapped_minor, ZColor remapped_major, ZColor marked_minor, ZColor marked_major) {
    uintptr_t color = 0;
    switch (remapped_minor) {
    case RemappedMinor0: {
      switch (remapped_major) {
      case RemappedMajor0:
        color |= ZPointerRemapped00;
        break;
      case RemappedMajor1:
        color |= ZPointerRemapped10;
        break;
      default:
        EXPECT_TRUE(false);
      }
      break;
    }
    case RemappedMinor1: {
      switch (remapped_major) {
      case RemappedMajor0:
        color |= ZPointerRemapped01;
        break;
      case RemappedMajor1:
        color |= ZPointerRemapped11;
        break;
      default:
        EXPECT_TRUE(false);
      }
      break;
    }
    default:
      EXPECT_TRUE(false);
    }

    switch (marked_minor) {
    case MarkedMinor0:
      color |= ZPointerMarkedMinor0;
      break;
    case MarkedMinor1:
      color |= ZPointerMarkedMinor1;
      break;
    default:
      EXPECT_TRUE(false);
    }

    switch (marked_major) {
    case MarkedMajor0:
      color |= ZPointerMarkedMajor0;
      break;
    case MarkedMajor1:
      color |= ZPointerMarkedMajor1;
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
                        ZColor remapped_minor,
                        ZColor remapped_major,
                        ZColor marked_minor,
                        ZColor marked_major) {
    if (remembered == Uncolored &&
        remapped_minor == Uncolored &&
        remapped_major == Uncolored &&
        marked_minor == Uncolored &&
        marked_major == Uncolored) {
      return zpointer(addr);
    } else {
      return color(addr, make_color(remembered, remapped_minor, remapped_major, marked_minor, marked_major));
    }
  }

  static bool is_remapped_minor_odd(uintptr_t bits) {
    return bits & (ZPointerRemapped01 | ZPointerRemapped11);
  }

  static bool is_remapped_major_odd(uintptr_t bits) {
    return bits & (ZPointerRemapped10 | ZPointerRemapped11);
  }

  static bool is_marked_minor_odd(uintptr_t bits) {
    return bits & ZPointerMarkedMinor1;
  }

  static bool is_marked_major_odd(uintptr_t bits) {
    return bits & (ZPointerMarkedMajor1 | ZPointerFinalizable1);
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
                                ZColor remapped_minor,
                                ZColor remapped_major,
                                ZColor marked_minor,
                                ZColor marked_major) {
    const zpointer ptr = color(value, remembered, remapped_minor, remapped_major, marked_minor, marked_major);
    uintptr_t ptr_raw = untype(ptr);

    EXPECT_TRUE(ZPointerLoadGoodMask != 0);
    EXPECT_TRUE(ZPointerStoreGoodMask != 0);

    bool ptr_raw_null = ptr_raw == 0;
    bool global_remapped_major_odd = is_remapped_major_odd(ZPointerLoadGoodMask);
    bool global_remapped_minor_odd = is_remapped_minor_odd(ZPointerLoadGoodMask);
    bool global_marked_major_odd = is_marked_major_odd(ZPointerStoreGoodMask);
    bool global_marked_minor_odd = is_marked_minor_odd(ZPointerStoreGoodMask);
    bool global_remembered_odd = is_remembered_odd(ZPointerStoreGoodMask);
    bool global_remembered_even = is_remembered_even(ZPointerStoreGoodMask);

    if (ptr_raw_null) {
      EXPECT_FALSE(ZPointer::is_marked_any_major(ptr));
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
      bool ptr_remapped_major_odd = is_remapped_major_odd(ptr_raw);
      bool ptr_remapped_minor_odd = is_remapped_minor_odd(ptr_raw);
      bool ptr_marked_major_odd = is_marked_major_odd(ptr_raw);
      bool ptr_marked_minor_odd = is_marked_minor_odd(ptr_raw);
      bool ptr_final = ptr_raw & (ZPointerFinalizable0 | ZPointerFinalizable1);
      bool ptr_remembered = is_power_of_2(ptr_raw & (ZPointerRemembered0 | ZPointerRemembered1));
      bool ptr_remembered_odd = is_remembered_odd(ptr_raw);
      bool ptr_remembered_even = is_remembered_even(ptr_raw);
      bool ptr_colored_null = !ptr_raw_null && (ptr_raw & ~ZPointerAllMetadataMask) == 0;

      bool same_major_marking = global_marked_major_odd == ptr_marked_major_odd;
      bool same_minor_marking = global_marked_minor_odd == ptr_marked_minor_odd;
      bool same_major_remapping = global_remapped_major_odd == ptr_remapped_major_odd;
      bool same_minor_remapping = global_remapped_minor_odd == ptr_remapped_minor_odd;
      bool same_remembered = ptr_remembered_even == global_remembered_even && ptr_remembered_odd == global_remembered_odd;

      EXPECT_EQ(ZPointer::is_marked_finalizable(ptr), same_major_marking && ptr_final);
      EXPECT_EQ(ZPointer::is_marked_any_major(ptr), same_major_marking);
      EXPECT_EQ(ZPointer::is_remapped(ptr), same_major_remapping && same_minor_remapping);
      EXPECT_EQ(ZPointer::is_load_good(ptr), same_major_remapping && same_minor_remapping);
      EXPECT_EQ(ZPointer::is_load_good_or_null(ptr), same_major_remapping && same_minor_remapping);
      EXPECT_EQ(ZPointer::is_load_bad(ptr), !same_major_remapping || !same_minor_remapping);
      EXPECT_EQ(ZPointer::is_mark_good(ptr), same_minor_remapping && same_major_remapping && same_minor_marking && same_major_marking);
      EXPECT_EQ(ZPointer::is_mark_good_or_null(ptr), same_minor_remapping && same_major_remapping && same_minor_marking && same_major_marking);
      EXPECT_EQ(ZPointer::is_mark_bad(ptr), !same_minor_remapping || !same_major_remapping || !same_minor_marking || !same_major_marking);
      EXPECT_EQ(ZPointer::is_store_good(ptr), same_minor_remapping && same_major_remapping && same_minor_marking && same_major_marking && ptr_remembered && same_remembered);
      EXPECT_EQ(ZPointer::is_store_good_or_null(ptr), same_minor_remapping && same_major_remapping && same_minor_marking && same_major_marking && ptr_remembered && same_remembered);
      EXPECT_EQ(ZPointer::is_store_bad(ptr), !same_minor_remapping || !same_major_remapping || !same_minor_marking || !same_major_marking || !ptr_remembered || !same_remembered);
    }
  }

  static void test_is_checks_on_all() {
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered0, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered1, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor0, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor0, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor0, MarkedMajor1);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor0);
    test_is_checks_on(valid_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);
    test_is_checks_on(null_value, Remembered11, RemappedMinor1, RemappedMajor1, MarkedMinor1, MarkedMajor1);

    test_is_checks_on(null_value, Uncolored, Uncolored, Uncolored, Uncolored, Uncolored);
  }

  static void advance_and_test_minor_phase(int& phase, int amount) {
    for (int i = 0; i < amount; ++i) {
      if (++phase & 1) {
        ZGlobalsPointers::flip_minor_mark_start();
      } else {
        ZGlobalsPointers::flip_minor_relocate_start();
      }
      test_is_checks_on_all();
    }
  }

  static void advance_and_test_major_phase(int& phase, int amount) {
    for (int i = 0; i < amount; ++i) {
      if (++phase & 1) {
        ZGlobalsPointers::flip_major_mark_start();
      } else {
        ZGlobalsPointers::flip_major_relocate_start();
      }
      test_is_checks_on_all();
    }
  }

  static void is_checks() {
    int minor_phase = 0;
    int major_phase = 0;
    // Setup
    ZGlobalsPointers::initialize();
    test_is_checks_on_all();

    advance_and_test_major_phase(major_phase, 4);
    advance_and_test_minor_phase(minor_phase, 4);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 4);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 4);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 4);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 4);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 3);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 3);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 3);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 3);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 2);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 2);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 2);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 2);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 1);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 1);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 1);

    advance_and_test_major_phase(major_phase, 1);
    advance_and_test_minor_phase(minor_phase, 1);
  }
};

TEST_F(ZAddressTest, is_checks) {
  is_checks();
}
