/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/markWord.hpp"
#include "runtime/safepoint.hpp"

#ifdef _LP64
narrowKlass markWord::narrow_klass() const {
  return narrowKlass(value() >> klass_shift);
}

Klass* markWord::klass() const {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  assert(!CompressedKlassPointers::is_null(narrow_klass()), "narrow klass must not be null: " INTPTR_FORMAT, value());
  return CompressedKlassPointers::decode_not_null(narrow_klass());
}

Klass* markWord::klass_or_null() const {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return CompressedKlassPointers::decode(narrow_klass());
}

markWord markWord::set_narrow_klass(const narrowKlass nklass) const {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  return markWord((value() & ~klass_mask_in_place) | ((uintptr_t) nklass << klass_shift));
}

Klass* markWord::safe_klass() const {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  assert(SafepointSynchronize::is_at_safepoint(), "only call at safepoint");
  markWord m = *this;
  if (m.has_displaced_mark_helper()) {
    m = m.displaced_mark_helper();
  }
  return CompressedKlassPointers::decode_not_null(m.narrow_klass());
}

markWord markWord::set_klass(const Klass* klass) const {
  assert(UseCompactObjectHeaders, "only used with compact object headers");
  assert(UseCompressedClassPointers, "expect compressed klass pointers");
  // TODO: Don't cast to non-const, change CKP::encode() to accept const Klass* instead.
  narrowKlass nklass = CompressedKlassPointers::encode(const_cast<Klass*>(klass));
  return set_narrow_klass(nklass);
}
#endif

#endif // SHARE_OOPS_MARKWORD_INLINE_HPP
