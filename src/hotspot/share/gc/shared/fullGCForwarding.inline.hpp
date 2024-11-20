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

#ifndef GC_SHARED_FULLGCFORWARDING_INLINE_HPP
#define GC_SHARED_FULLGCFORWARDING_INLINE_HPP

#include "gc/shared/fullGCForwarding.hpp"

#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"

void FullGCForwarding::forward_to(oop from, oop to) {
#ifdef _LP64
  uintptr_t encoded = pointer_delta(cast_from_oop<HeapWord*>(to), _heap_base) << Shift;
  assert(encoded <= static_cast<uintptr_t>(right_n_bits(_num_low_bits)), "encoded forwardee must fit");
  uintptr_t mark = from->mark().value();
  mark &= ~right_n_bits(_num_low_bits);
  mark |= (encoded | markWord::marked_value);
  from->set_mark(markWord(mark));
#else
  from->forward_to(to);
#endif
}

oop FullGCForwarding::forwardee(oop from) {
#ifdef _LP64
  uintptr_t mark = from->mark().value();
  HeapWord* decoded = _heap_base + ((mark & right_n_bits(_num_low_bits)) >> Shift);
  return cast_to_oop(decoded);
#else
  return from->forwardee();
#endif
}

bool FullGCForwarding::is_forwarded(oop obj) {
  return obj->mark().is_forwarded();
}

#endif // GC_SHARED_FULLGCFORWARDING_INLINE_HPP
