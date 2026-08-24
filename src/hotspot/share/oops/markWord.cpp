/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "oops/markWord.hpp"
#include "utilities/ostream.hpp"

#ifdef _LP64
STATIC_ASSERT(markWord::klass_shift + markWord::klass_bits == 64);
// The hash (preceding klass bits) shall be a direct neighbor but not interleave
STATIC_ASSERT(markWord::klass_shift == markWord::hash_bits + markWord::hash_shift);
#endif

void markWord::print_on(outputStream* st) const {
  if (is_marked()) {           // last bits = 11
    st->print(" marked(" INTPTR_FORMAT ")", value());
    return;
  }
  st->print(" mark(");
  if (has_monitor()) {         // last bits = 10
    st->print("has_monitor");
  } else if (is_unlocked()) {  // last bits = 01
    st->print("is_unlocked");
  } else {                     // last bits = 00
    assert(is_fast_locked(), "should be");
    st->print("is_locked");
  }
  if (is_inline_type()) {
    st->print(" inline_type");
  }
  if (has_no_hash()) {
    st->print(" no_hash");
  } else {
    st->print(" hash=" INTPTR_FORMAT, hash());
  }
#ifdef _LP64 // 64 bit encodings have array information
  // flat or null-free do not imply each other
  const bool flat = is_flat_array();
  const bool null_free = is_null_free_array();
  if (flat && !null_free) {
    st->print(" flat_array");
  } else if (!flat && null_free) {
    st->print(" null_free_array");
  } else if (flat && null_free) {
    st->print(" flat_null_free_array");
  }
#endif
  st->print(" age=%d)", age());
}
