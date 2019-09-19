/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_COMPRESSEDOOPS_INLINE_HPP
#define SHARE_OOPS_COMPRESSEDOOPS_INLINE_HPP

#include "gc/shared/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/oop.hpp"
#include "utilities/align.hpp"

// Functions for encoding and decoding compressed oops.
// If the oops are compressed, the type passed to these overloaded functions
// is narrowOop.  All functions are overloaded so they can be called by
// template functions without conditionals (the compiler instantiates via
// the right type and inlines the appropriate code).

// Algorithm for encoding and decoding oops from 64 bit pointers to 32 bit
// offset from the heap base.  Saving the check for null can save instructions
// in inner GC loops so these are separated.

inline oop CompressedOops::decode_raw(narrowOop v) {
  return (oop)(void*)((uintptr_t)base() + ((uintptr_t)v << shift()));
}

inline oop CompressedOops::decode_not_null(narrowOop v) {
  assert(!is_null(v), "narrow oop value can never be zero");
  oop result = decode_raw(v);
  assert(is_object_aligned(result), "address not aligned: " INTPTR_FORMAT, p2i((void*) result));
  return result;
}

inline oop CompressedOops::decode(narrowOop v) {
  return is_null(v) ? (oop)NULL : decode_not_null(v);
}

inline narrowOop CompressedOops::encode_not_null(oop v) {
  assert(!is_null(v), "oop value can never be zero");
  assert(is_object_aligned(v), "address not aligned: " PTR_FORMAT, p2i((void*)v));
  assert(is_in(v), "address not in heap range: " PTR_FORMAT, p2i((void*)v));
  uint64_t  pd = (uint64_t)(pointer_delta((void*)v, (void*)base(), 1));
  assert(OopEncodingHeapMax > pd, "change encoding max if new encoding");
  uint64_t result = pd >> shift();
  assert((result & CONST64(0xffffffff00000000)) == 0, "narrow oop overflow");
  assert(decode(result) == v, "reversibility");
  return (narrowOop)result;
}

inline narrowOop CompressedOops::encode(oop v) {
  return is_null(v) ? (narrowOop)0 : encode_not_null(v);
}

static inline bool check_alignment(Klass* v) {
  return (intptr_t)v % KlassAlignmentInBytes == 0;
}

inline Klass* CompressedKlassPointers::decode_raw(narrowKlass v) {
    return (Klass*)(void*)((uintptr_t)base() +((uintptr_t)v << shift()));
  }

inline Klass* CompressedKlassPointers::decode_not_null(narrowKlass v) {
  assert(!is_null(v), "narrow klass value can never be zero");
  Klass* result = decode_raw(v);
  assert(check_alignment(result), "address not aligned: " INTPTR_FORMAT, p2i((void*) result));
  return result;
}

inline Klass* CompressedKlassPointers::decode(narrowKlass v) {
  return is_null(v) ? (Klass*)NULL : decode_not_null(v);
}

inline narrowKlass CompressedKlassPointers::encode_not_null(Klass* v) {
  assert(!is_null(v), "klass value can never be zero");
  assert(check_alignment(v), "Address not aligned");
  uint64_t pd = (uint64_t)(pointer_delta((void*)v, base(), 1));
  assert(KlassEncodingMetaspaceMax > pd, "change encoding max if new encoding");
  uint64_t result = pd >> shift();
  assert((result & CONST64(0xffffffff00000000)) == 0, "narrow klass pointer overflow");
  assert(decode(result) == v, "reversibility");
  return (narrowKlass)result;
}

inline narrowKlass CompressedKlassPointers::encode(Klass* v) {
  return is_null(v) ? (narrowKlass)0 : encode_not_null(v);
}

#endif // SHARE_OOPS_COMPRESSEDOOPS_INLINE_HPP
