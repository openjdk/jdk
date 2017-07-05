/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_KLASS_INLINE_HPP
#define SHARE_VM_OOPS_KLASS_INLINE_HPP

#include "memory/universe.hpp"
#include "oops/klass.hpp"
#include "oops/markOop.hpp"

inline void Klass::set_prototype_header(markOop header) {
  assert(!header->has_bias_pattern() || oop_is_instance(), "biased locking currently only supported for Java instances");
  _prototype_header = header;
}

inline bool Klass::is_null(Klass* obj)  { return obj == NULL; }
inline bool Klass::is_null(narrowKlass obj) { return obj == 0; }

// Encoding and decoding for klass field.

inline bool check_klass_alignment(Klass* obj) {
  return (intptr_t)obj % KlassAlignmentInBytes == 0;
}

inline narrowKlass Klass::encode_klass_not_null(Klass* v) {
  assert(!is_null(v), "klass value can never be zero");
  assert(check_klass_alignment(v), "Address not aligned");
  int    shift = Universe::narrow_klass_shift();
  uint64_t pd = (uint64_t)(pointer_delta((void*)v, Universe::narrow_klass_base(), 1));
  assert(KlassEncodingMetaspaceMax > pd, "change encoding max if new encoding");
  uint64_t result = pd >> shift;
  assert((result & CONST64(0xffffffff00000000)) == 0, "narrow klass pointer overflow");
  assert(decode_klass(result) == v, "reversibility");
  return (narrowKlass)result;
}

inline narrowKlass Klass::encode_klass(Klass* v) {
  return is_null(v) ? (narrowKlass)0 : encode_klass_not_null(v);
}

inline Klass* Klass::decode_klass_not_null(narrowKlass v) {
  assert(!is_null(v), "narrow klass value can never be zero");
  int    shift = Universe::narrow_klass_shift();
  Klass* result = (Klass*)(void*)((uintptr_t)Universe::narrow_klass_base() + ((uintptr_t)v << shift));
  assert(check_klass_alignment(result), err_msg("address not aligned: " INTPTR_FORMAT, p2i((void*) result)));
  return result;
}

inline Klass* Klass::decode_klass(narrowKlass v) {
  return is_null(v) ? (Klass*)NULL : decode_klass_not_null(v);
}

#endif // SHARE_VM_OOPS_KLASS_INLINE_HPP
