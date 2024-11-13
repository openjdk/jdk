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

#include "oops/objLayout.hpp"

inline ObjLayout::Mode ObjLayout::klass_mode() {
#ifdef ASSERT
  assert(_klass_mode != Undefined, "KlassMode not yet initialized");
  if (UseCompactObjectHeaders) {
    assert(_klass_mode == Compact, "Klass mode does not match flags");
  } else if (UseCompressedClassPointers) {
    assert(_klass_mode == Compressed, "Klass mode does not match flags");
  } else {
    assert(_klass_mode == Uncompressed, "Klass mode does not match flags");
  }
#endif
#ifdef _LP64
  return _klass_mode;
#else
  return Uncompressed;
#endif
}

#endif // SHARE_OOPS_OBJLAYOUT_INLINE_HPP
