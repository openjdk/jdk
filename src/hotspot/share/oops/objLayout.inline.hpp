/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_OOPS_OBJLAYOUT_INLINE_HPP
#define SHARE_OOPS_OBJLAYOUT_INLINE_HPP

// Be frugal with includes here to prevent circularities.

#include "oops/objLayout.hpp"

template<HeaderMode mode>
constexpr inline bool ObjLayoutHelpers::oop_has_klass_gap() {
  return mode == HeaderMode::Compressed;
}

template<HeaderMode mode>
constexpr inline int ObjLayoutHelpers::markword_plus_klass_in_bytes() {
  switch (mode) {
  case HeaderMode::Uncompressed:  return sizeof(markWord) + sizeof(Klass*);
  case HeaderMode::Compressed:    return sizeof(markWord) + sizeof(narrowKlass);
  case HeaderMode::Compact:       return sizeof(markWord);
  }
  return 0;
}

template<HeaderMode headermode, typename elemtype>
constexpr inline int ObjLayoutHelpers::array_first_element_offset_in_bytes() {
  return align_up(markword_plus_klass_in_bytes<headermode>() + BytesPerInt, sizeof(elemtype));
}

#endif // SHARE_OOPS_OBJLAYOUT_INLINE_HPP
