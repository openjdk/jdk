/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/register.hpp"

#ifdef ASSERT

// This function implements a very simple parser to improve the output for
// assert_different_registers.
//
// If the parser succeeds in parsing the entire string, it returns true and
// the caller can use the contents of args and arg_lengths to decorate the
// assert message. Otherwise, false is returned.
//
// The args elements point into the original args_string array.
//
// The parser just tokenizes at commas and trims the tokens. This works well
// for most of our usages, like this example:
//   assert_different_registers(addr, t1, scratch1);
//
// but fails for more complex constructs like:
//   assert_different_registers(addr, t1, compute_register(x, y));
//
// Adding support for the latter is TBD.
bool fill_assert_different_registers_arguments(const char* args_string, size_t args_count, const char* args[], int arg_lengths[]) {
  auto trim_and_set = [&](size_t index, const char* start, int length) {
    while (*start == ' ') {
      start++;
      length--;
    }
    while (length > 0) {
      if (*(start + length - 1) == ' ') {
        length--;
      } else {
        break;
      }
    }
    args[index] = start;
    arg_lengths[index] = length;
  };

  const char* pos = args_string;
  const char* end = args_string + strlen(args_string);

  for (size_t i = 0; i < args_count; i++) {
    const char* comma = strchr(pos, ',');

    if (comma != nullptr) {
      trim_and_set(i, pos, comma - pos);
      pos = comma + 1;
    } else {
      // Hit the end of the string.
      trim_and_set(i, pos, end - pos);

      if (i == args_count - 1) {
        // Looks good. A matching number of commas were found.
        return true;
      }

      fatal("Too few commas found in %s expected %zu", args_string, args_count - 1);
    }
  }

  // Bail out. The arguments themselves have commas, and the simple parsing above
  // can't deal with that.
  return false;
}

#endif
