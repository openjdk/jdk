/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SLIDINGFORWARDING_HPP
#define SHARE_GC_SHARED_SLIDINGFORWARDING_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"

/**
 * SlidingForwarding is a method to store forwarding information in a compressed form into the object header,
 * that has been specifically designed for sliding compaction GCs.
 * It avoids overriding the compressed class pointer in the upper bits of the header, which would otherwise
 * be lost. SlidingForwarding requires only small side tables and guarantees constant-time access and modification.
 *
 * The idea is to use a pointer compression scheme very similar to the one that is used for compressed oops.
 * We divide the heap into number of logical regions. Each region spans maximum of 2^NUM_BITS words.
 * We take advantage of the fact that sliding compaction can forward objects from one region to a maximum of
 * two regions (including itself, but that does not really matter). We need 1 bit to indicate which region is forwarded
 * into. We also currently require the two lowest header bits to indicate that the object is forwarded.
 *
 * For addressing, we need a table with N*2 entries, for N logical regions. For each region, it gives the base
 * address of the two target regions, or a special placeholder if not used.
 *
 * Adding a forwarding then works as follows:
 * Given an original address 'orig', and a 'target' address:
 * - Look-up first target base of region of orig. If not yet used,
 *   establish it to be the base of region of target address. Use that base in step 3.
 * - Else, if first target base is already used, check second target base. This must either be unused, or the
 *   base of the region of our target address. If unused, establish it to be the base of the region of our target
 *   address. Use that base for next step.
 * - Now we found a base address. Encode the target address with that base into lowest NUM_BITS bits, and shift
 *   that up by 3 bits. Set the 3rd bit if we used the secondary target base, otherwise leave it at 0. Set the
 *   lowest two bits to indicate that the object has been forwarded. Store that in the lowest NUM_BITS+3 bits of the
 *   original object's header.
 *
 * Similarily, looking up the target address, given an original object address works as follows:
 * - Load lowest NUM_BITS + 3 from original object header. Extract target region bit and compressed address bits.
 * - Depending on target region bit, load base address from the target base table by looking up the corresponding entry
 *   for the region of the original object.
 * - Decode the target address by using the target base address and the compressed address bits.
 */

class SlidingForwarding : public CHeapObj<mtGC> {
#ifdef _LP64
private:
  static const int NUM_REGION_BITS = 1;

  static const uintptr_t ONE = 1ULL;

  static const size_t NUM_REGIONS = ONE << NUM_REGION_BITS;

  // We need the lowest two bits to indicate a forwarded object.
  static const int BASE_SHIFT = 2;

  // The compressed address bits start here.
  static const int COMPRESSED_BITS_SHIFT = BASE_SHIFT + NUM_REGION_BITS;

  // How many bits we use for the compressed pointer (we are going to need one more bit to indicate target region, and
  // two lowest bits to mark objects as forwarded)
  static const int NUM_COMPRESSED_BITS = 32 - BASE_SHIFT - NUM_REGION_BITS;

  // Indicates an usused base address in the target base table. We cannot use 0, because that may already be
  // a valid base address in zero-based heaps. 0x1 is safe because heap base addresses must be aligned by 2^X.
  static HeapWord* const UNUSED_BASE;

  HeapWord*  const _heap_start;
  size_t     const _num_regions;
  size_t     const _region_size_words_shift;
  HeapWord** const _target_base_table;

  inline size_t region_index_containing(HeapWord* addr) const;
  inline bool region_contains(HeapWord* region_base, HeapWord* addr) const;

  inline uintptr_t encode_forwarding(HeapWord* original, HeapWord* target);
  inline HeapWord* decode_forwarding(HeapWord* original, uintptr_t encoded) const;

#endif

public:
  SlidingForwarding(MemRegion heap);
  SlidingForwarding(MemRegion heap, size_t num_regions);
  ~SlidingForwarding();

  void clear();
  inline void forward_to(oop original, oop target);
  inline oop forwardee(oop original) const;
};

#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_HPP
