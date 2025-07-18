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

#ifndef SHARE_OOPS_MARKWORD_INLINE_HPP
#define SHARE_OOPS_MARKWORD_INLINE_HPP

#include "oops/markWord.hpp"

#include "oops/compressedOops.inline.hpp"

narrowKlass markWord::narrow_klass() const {
#ifdef _LP64
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return narrowKlass(value() >> klass_shift);
#else
  ShouldNotReachHere();
  return 0;
#endif
}

markWord markWord::set_narrow_klass(narrowKlass narrow_klass) const {
#ifdef _LP64
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return markWord((value() & ~klass_mask_in_place) | ((uintptr_t) narrow_klass << klass_shift));
#else
  ShouldNotReachHere();
  return markWord(0);
#endif
}

Klass* markWord::klass() const {
#ifdef _LP64
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return CompressedKlassPointers::decode_not_null(narrow_klass());
#else
  ShouldNotReachHere();
  return nullptr;
#endif
}

Klass* markWord::klass_or_null() const {
#ifdef _LP64
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return CompressedKlassPointers::decode(narrow_klass());
#else
  ShouldNotReachHere();
  return nullptr;
#endif
}

Klass* markWord::klass_without_asserts() const {
#ifdef _LP64
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return CompressedKlassPointers::decode_without_asserts(narrow_klass());
#else
  ShouldNotReachHere();
  return nullptr;
#endif
}

#endif // SHARE_OOPS_MARKWORD_INLINE_HPP
