/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

TEST(arguments, atojulong) {
  char ullong_max[32];
  int ret = jio_snprintf(ullong_max, sizeof(ullong_max), JULONG_FORMAT, ULLONG_MAX);
  ASSERT_NE(-1, ret);

  julong value;
  const char* invalid_strings[] = {
    "", "-1", "-100", " 1", "2 ", "3 2", "1.0",
    "0x4.5", "0x", "0x0x1" "0.001", "4e10", "e"
    "K", "M", "G", "1MB", "1KM", "AA", "0B",
    "18446744073709551615K", "17179869184G",
    "999999999999999999999999999999"
  };
  for (uint i = 0; i < ARRAY_SIZE(invalid_strings); i++) {
    ASSERT_FALSE(Arguments::atojulong(invalid_strings[i], &value))
        << "Invalid string '" << invalid_strings[i] << "' parsed without error.";
  }

  struct {
    const char* str;
    julong expected_value;
  } valid_strings[] = {
      { "0", 0 },
      { "4711", 4711 },
      { "1K", 1ULL * K },
      { "1k", 1ULL * K },
      { "2M", 2ULL * M },
      { "2m", 2ULL * M },
      { "4G", 4ULL * G },
      { "4g", 4ULL * G },
      { "0K", 0 },
      { ullong_max, ULLONG_MAX },
      { "0xcafebabe", 0xcafebabe },
      { "0XCAFEBABE", 0xcafebabe },
      { "0XCAFEbabe", 0xcafebabe },
      { "0x10K", 0x10 * K }
  };
  for (uint i = 0; i < ARRAY_SIZE(valid_strings); i++) {
    ASSERT_TRUE(Arguments::atojulong(valid_strings[i].str, &value))
        << "Valid string '" << valid_strings[i].str << "' did not parse.";
    ASSERT_EQ(valid_strings[i].expected_value, value);
  }
}
