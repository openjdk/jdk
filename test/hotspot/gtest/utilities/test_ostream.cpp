/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include "unittest.hpp"

static size_t print_lorem(outputStream* st, bool short_len) {
  // Create a ResourceMark just to make sure the stream does not use ResourceArea
  ResourceMark rm;
  static const char* const lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
      "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Lacinia at quis "
      "risus sed vulputate odio ut enim blandit. Amet risus nullam eget felis eget. Viverra "
      "orci sagittis eu volutpat odio facilisis mauris sit. Erat velit scelerisque in dictum non.";
  static const size_t len_lorem = strlen(lorem);
  size_t len;
  if (short_len) {
    len = os::random() % 10;
  } else {
    len = MAX2(1, (int)(os::random() % len_lorem));
  }
  st->write(lorem, len);
  return len;
}

static void do_test_stringStream_dynamic_realloc(bool short_len) {
  stringStream ss(2); // small buffer to force lots of reallocations.
  size_t written = 0;
  for (int i = 0; i < 1000; i ++) {
    written += print_lorem(&ss, short_len);
    ASSERT_EQ(ss.size(), written);
    // Internal buffer should always be zero-terminated.
    ASSERT_EQ(ss.base()[ss.size()], '\0');
  }
}

TEST_VM(ostream, stringStream_dynamic_realloc_1) {
  do_test_stringStream_dynamic_realloc(false);
}

TEST_VM(ostream, stringStream_dynamic_realloc_2) {
  do_test_stringStream_dynamic_realloc(true);
}
