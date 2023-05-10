/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_METADATAFLAGS_HPP
#define SHARE_OOPS_METADATAFLAGS_HPP

#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Somewhere to put the atomic bit set functions not supported in Atomic for some reason.
class MetadataFlags {

 protected:
  inline void atomic_set_bits(u1& flags, u1 mask) {
    // Atomically update the flags with the bits given
    u1 old_flags, new_flags, witness;
    do {
      old_flags = flags;
      new_flags = old_flags | mask;
      witness = Atomic::cmpxchg(&flags, old_flags, new_flags);
    } while (witness != old_flags);
  }

  inline void atomic_clear_bits(u1& flags, u1 mask) {
    // Atomically update the flags with the bits given
    u1 old_flags, new_flags, witness;
    do {
      old_flags = flags;
      new_flags = old_flags & ~mask;
      witness = Atomic::cmpxchg(&flags, old_flags, new_flags);
    } while (witness != old_flags);
  }

  void atomic_set_bits(u4& status, u4 bits)   { Atomic::fetch_then_or(&status, bits); }
  void atomic_clear_bits(u4& status, u4 bits) { Atomic::fetch_then_and(&status, ~bits); }
};

#endif // SHARE_OOPS_METADATAFLAGS_HPP
