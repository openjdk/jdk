/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "oops/layoutKind.hpp"
#include "utilities/ostream.hpp"

const char* LayoutKindHelper::layout_kind_as_string(LayoutKind lk) {
  switch(lk) {
    case LayoutKind::REFERENCE:
      return "REFERENCE";
    case LayoutKind::BUFFERED:
      return "BUFFERED";
    case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT:
      return "NULL_FREE_NON_ATOMIC_FLAT";
    case LayoutKind::NULL_FREE_ATOMIC_FLAT:
      return "NULL_FREE_ATOMIC_FLAT";
    case LayoutKind::NULLABLE_ATOMIC_FLAT:
      return "NULLABLE_ATOMIC_FLAT";
    case LayoutKind::NULLABLE_NON_ATOMIC_FLAT:
      return "NULLABLE_NON_ATOMIC_FLAT";
    case LayoutKind::UNKNOWN:
      return "UNKNOWN";
    default:
      ShouldNotReachHere();
  }
}

#ifdef ASSERT
void LayoutKindHelper::print_on(LayoutKind lk, outputStream* st) {
  st->print("LayoutKind: %s", layout_kind_as_string(lk));
}
#endif // ASSERT

