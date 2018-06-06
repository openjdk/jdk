/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP
#define SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/macros.hpp"

#ifdef VM_LITTLE_ENDIAN
static const int low_offset = 0;
static const int leakp_offset = low_offset + 1;
#else
static const int low_offset = 7;
static const int leakp_offset = low_offset - 1;
#endif

inline void set_bits(jbyte bits, jbyte* const dest) {
  assert(dest != NULL, "invariant");
  const jbyte current = OrderAccess::load_acquire(dest);
  if (bits != (current & bits)) {
    *dest |= bits;
  }
}

inline void set_mask(jbyte mask, jbyte* const dest) {
  assert(dest != NULL, "invariant");
  const jbyte current = OrderAccess::load_acquire(dest);
  if (mask != (current & mask)) {
    *dest &= mask;
  }
}

inline void set_bits_cas(jbyte bits, jbyte* const dest) {
  assert(dest != NULL, "invariant");
  do {
    const jbyte current = OrderAccess::load_acquire(dest);
    if (bits == (current & bits)) {
      return;
    }
    const jbyte new_value = current | bits;
    if (Atomic::cmpxchg(new_value, dest, current) == current) {
      return;
    }
  } while (true);
}

inline void clear_bits_cas(jbyte bits, jbyte* const dest) {
  assert(dest != NULL, "invariant");
  do {
    const jbyte current = OrderAccess::load_acquire(dest);
    if (bits != (current & bits)) {
      return;
    }
    const jbyte new_value = current ^ bits;
    if (Atomic::cmpxchg(new_value, dest, current) == current) {
      return;
    }
  } while (true);
}

inline void set_traceid_bits(jbyte bits, traceid* dest) {
  set_bits(bits, ((jbyte*)dest) + low_offset);
}

inline void set_traceid_bits_cas(jbyte bits, traceid* dest) {
  set_bits_cas(bits, ((jbyte*)dest) + low_offset);
}

inline void set_traceid_mask(jbyte mask, traceid* dest) {
  set_mask(mask, ((jbyte*)dest) + low_offset);
}

inline void set_leakp_traceid_bits(jbyte bits, traceid* dest) {
  set_bits(bits, ((jbyte*)dest) + leakp_offset);
}

inline void set_leakp_traceid_bits_cas(jbyte bits, traceid* dest) {
  set_bits_cas(bits, ((jbyte*)dest) + leakp_offset);
}

inline void set_leakp_traceid_mask(jbyte mask, traceid* dest) {
  set_mask(mask, ((jbyte*)dest) + leakp_offset);
}

#endif // SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP

