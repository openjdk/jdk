/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/oop.hpp"

// Functions for encoding and decoding compressed oops.
// If the oops are compressed, the type passed to these overloaded functions
// is narrowOop.  All functions are overloaded so they can be called by
// template functions without conditionals (the compiler instantiates via
// the right type and inlines the appropriate code).

// Algorithm for encoding and decoding oops from 64 bit pointers to 32 bit
// offset from the heap base.  Saving the check for null can save instructions
// in inner GC loops so these are separated.

namespace CompressedOops {
  inline bool is_null(oop obj)       { return obj == NULL; }
  inline bool is_null(narrowOop obj) { return obj == 0; }

  inline oop decode_not_null(narrowOop v) {
    assert(!is_null(v), "narrow oop value can never be zero");
    address base = Universe::narrow_oop_base();
    int    shift = Universe::narrow_oop_shift();
    oop result = (oop)(void*)((uintptr_t)base + ((uintptr_t)v << shift));
    assert(check_obj_alignment(result), "address not aligned: " INTPTR_FORMAT, p2i((void*) result));
    return result;
  }

  inline oop decode(narrowOop v) {
    return is_null(v) ? (oop)NULL : decode_not_null(v);
  }

  inline narrowOop encode_not_null(oop v) {
    assert(!is_null(v), "oop value can never be zero");
    assert(check_obj_alignment(v), "Address not aligned");
    assert(Universe::heap()->is_in_reserved(v), "Address not in heap");
    address base = Universe::narrow_oop_base();
    int    shift = Universe::narrow_oop_shift();
    uint64_t  pd = (uint64_t)(pointer_delta((void*)v, (void*)base, 1));
    assert(OopEncodingHeapMax > pd, "change encoding max if new encoding");
    uint64_t result = pd >> shift;
    assert((result & CONST64(0xffffffff00000000)) == 0, "narrow oop overflow");
    assert(decode(result) == v, "reversibility");
    return (narrowOop)result;
  }

  inline narrowOop encode(oop v) {
    return is_null(v) ? (narrowOop)0 : encode_not_null(v);
  }

  // No conversions needed for these overloads
  inline oop decode_not_null(oop v)             { return v; }
  inline oop decode(oop v)                      { return v; }
  inline narrowOop encode_not_null(narrowOop v) { return v; }
  inline narrowOop encode(narrowOop v)          { return v; }
}

#endif // SHARE_OOPS_COMPRESSEDOOPS_INLINE_HPP
