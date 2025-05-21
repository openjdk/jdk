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
 */

#ifndef SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP
#define SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/fullGCForwarding.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/macros.hpp"

inline bool FullGCForwarding::is_forwarded(oop obj) {
  return obj->is_forwarded();
}

size_t FullGCForwarding::biased_region_index_containing(HeapWord* addr) {
  return (uintptr_t)addr >> BLOCK_SIZE_BYTES_SHIFT;
}

bool FullGCForwarding::is_fallback(uintptr_t encoded) {
  return (encoded & OFFSET_MASK) == FALLBACK_PATTERN_IN_PLACE;
}

uintptr_t FullGCForwarding::encode_forwarding(HeapWord* from, HeapWord* to) {
  size_t from_block_idx = biased_region_index_containing(from);

  HeapWord* to_region_base = _biased_bases[from_block_idx];
  if (to_region_base == UNUSED_BASE) {
    _biased_bases[from_block_idx] = to_region_base = to;
  }

  // Avoid pointer_delta() on purpose: using an unsigned subtraction,
  // we get an underflow when to < to_region_base, which means
  // we can use a single comparison instead of:
  // if (to_region_base > to || (to - to_region_base) > MAX_OFFSET) { .. }
  size_t offset = size_t(to - to_region_base);
  if (offset > MAX_OFFSET) {
    offset = FALLBACK_PATTERN;
  }
  uintptr_t encoded = (offset << OFFSET_BITS_SHIFT) | markWord::marked_value;

  assert(is_fallback(encoded) || to == decode_forwarding(from, encoded), "must be reversible");
  assert((encoded & ~AVAILABLE_BITS_MASK) == 0, "must encode to available bits");
  return encoded;
}

HeapWord* FullGCForwarding::decode_forwarding(HeapWord* from, uintptr_t encoded) {
  assert(!is_fallback(encoded), "must not be fallback-forwarded, encoded: " INTPTR_FORMAT ", OFFSET_MASK: " INTPTR_FORMAT ", FALLBACK_PATTERN_IN_PLACE: " INTPTR_FORMAT, encoded, OFFSET_MASK, FALLBACK_PATTERN_IN_PLACE);
  assert((encoded & ~AVAILABLE_BITS_MASK) == 0, "must decode from available bits, encoded: " INTPTR_FORMAT, encoded);
  uintptr_t offset = (encoded >> OFFSET_BITS_SHIFT);

  size_t from_idx = biased_region_index_containing(from);
  HeapWord* base = _biased_bases[from_idx];
  assert(base != UNUSED_BASE, "must not be unused base: encoded: " INTPTR_FORMAT, encoded);
  HeapWord* decoded = base + offset;
  assert(decoded >= _heap_start,
         "Address must be above heap start. encoded: " INTPTR_FORMAT ", base: " PTR_FORMAT,
          encoded, p2i(base));

  return decoded;
}

inline void FullGCForwarding::forward_to_impl(oop from, oop to) {
  assert(_bases_table != nullptr, "call begin() before forwarding");

  markWord from_header = from->mark();
  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  HeapWord* to_hw   = cast_from_oop<HeapWord*>(to);
  uintptr_t encoded = encode_forwarding(from_hw, to_hw);
  markWord new_header = markWord((from_header.value() & ~OFFSET_MASK) | encoded);
  from->set_mark(new_header);

  if (is_fallback(encoded)) {
    fallback_forward_to(from_hw, to_hw);
  }
  NOT_PRODUCT(Atomic::inc(&_num_forwardings);)
}

inline void FullGCForwarding::forward_to(oop obj, oop fwd) {
  assert(fwd != nullptr, "no null forwarding");
#ifdef _LP64
  assert(_bases_table != nullptr, "expect sliding forwarding initialized");
  forward_to_impl(obj, fwd);
  assert(forwardee(obj) == fwd, "must be forwarded to correct forwardee, obj: " PTR_FORMAT ", forwardee(obj): " PTR_FORMAT ", fwd: " PTR_FORMAT ", mark: " INTPTR_FORMAT, p2i(obj), p2i(forwardee(obj)), p2i(fwd), obj->mark().value());
#else
  obj->forward_to(fwd);
#endif
}

inline oop FullGCForwarding::forwardee_impl(oop from) {
  assert(_bases_table != nullptr, "call begin() before asking for forwarding");

  markWord header = from->mark();
  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  if (is_fallback(header.value())) {
    HeapWord* to = fallback_forwardee(from_hw);
    return cast_to_oop(to);
  }
  uintptr_t encoded = header.value() & OFFSET_MASK;
  HeapWord* to = decode_forwarding(from_hw, encoded);
  return cast_to_oop(to);
}

inline oop FullGCForwarding::forwardee(oop obj) {
#ifdef _LP64
  assert(_bases_table != nullptr, "expect sliding forwarding initialized");
  return forwardee_impl(obj);
#else
  return obj->forwardee();
#endif
}

#endif // SHARE_GC_SHARED_FULLGCFORWARDING_INLINE_HPP
