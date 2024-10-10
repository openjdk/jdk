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

#ifndef SHARE_GC_SHARED_FULLGCFORWARDING_HPP
#define SHARE_GC_SHARED_FULLGCFORWARDING_HPP

#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"

/*
 * Implements forwarding for the Full GCs of Serial, Parallel, G1 and Shenandoah in
 * a way that preserves upper N bits of object mark-words, which contain crucial
 * Klass* information when running with compact headers. The encoding is similar to
 * compressed-oops encoding: it basically subtracts the forwardee address from the
 * heap-base, shifts that difference into the right place, and sets the lowest two
 * bits (to indicate 'forwarded' state as usual).
 * With compact-headers, we have 40 bits to encode forwarding pointers. This is
 * enough to address 8TB of heap. If the heap size exceeds that limit, we turn off
 * compact headers.
 */
class FullGCForwarding : public AllStatic {
  static const int NumLowBitsNarrow = LP64_ONLY(markWord::klass_shift) NOT_LP64(0 /*unused*/);
  static const int NumLowBitsWide   = BitsPerWord;
  static const int Shift            = markWord::lock_bits + markWord::lock_shift;

  static HeapWord* _heap_base;
  static int _num_low_bits;
public:
  static void initialize_flags(size_t max_heap_size);
  static void initialize(MemRegion heap);
  static inline void forward_to(oop from, oop to);
  static inline oop forwardee(oop from);
  static inline bool is_forwarded(oop obj);
};

#endif // SHARE_GC_SHARED_FULLGCFORWARDING_HPP
