/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "utilities/unsigned5.hpp"

// Most of UNSIGNED5 is in the header file.
// Let's put a few debug functions out-of-line here.

// For the record, UNSIGNED5 was defined around 2001 and was first
// published in the initial Pack200 spec.  See:
// https://docs.oracle.com/en/java/javase/11/docs/specs/pack-spec.html
// in Section 6.1, "Encoding of Small Whole Numbers".

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED

// For debugging, even in product builds (see debug.cpp).
template<typename ARR, typename OFF, typename GET>
void UNSIGNED5::Reader<ARR,OFF,GET>::
print_on(outputStream* st, int count,
         const char* left,   // "U5: ["
         const char* right   // "] (values=%d/length=%d)\n"
         ) {
  if (left == nullptr)   left = "U5: [";
  if (right == nullptr)  right = "] (values=%d/length=%d)\n";
  int printed = 0;
  st->print("%s", left);
  for (;;) {
    if (count >= 0 && printed >= count)  break;
    if (!has_next()) {
      if ((_limit == 0 || _position < _limit) && _array[_position] == 0) {
        st->print(" null");
        ++_position;  // skip null byte
        ++printed;
        if (_limit != 0)  continue;  // keep going to explicit limit
      }
      break;
    }
    u4 value = next_uint();
    if (printed == 0)
      st->print("%d", value);
    else
      st->print(" %d", value);
    ++printed;
  }
  st->print(right,
            // these arguments may or may not be used in the format string:
            printed,
            (int)_position);
}

PRAGMA_DIAG_POP

// Explicit instantiation for supported types.
template void UNSIGNED5::Reader<char*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<u1*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<address,size_t>::
print_on(outputStream* st, int count, const char* left, const char* right);
