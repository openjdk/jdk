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

#ifndef SHARE_GC_SHARED_GCFORWARDING_HPP
#define SHARE_GC_SHARED_GCFORWARDING_HPP

#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"

class GCForwarding : public AllStatic {
  static const int NumKlassBits        = 32; // Will be 22 with Tiny Class-Pointers
  static const int NUM_LOW_BITS_NARROW = BitsPerWord - NumKlassBits;
  static const int NUM_LOW_BITS_WIDE   = BitsPerWord;
  static const int SHIFT = markWord::lock_bits + markWord::lock_shift;

  static HeapWord* _heap_base;
  static int _num_low_bits;
public:
  static void initialize_flags();
  static void initialize(MemRegion heap);
  static inline void forward_to(oop from, oop to);
  static inline oop forwardee(oop from);
  static inline bool is_forwarded(oop obj);
};

#endif // SHARE_GC_SHARED_GCFORWARDING_HPP
