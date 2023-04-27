/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SLIDINGFORWARDING_HPP
#define SHARE_GC_SHARED_SLIDINGFORWARDING_HPP

#ifdef _LP64

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"

class FallbackTable;

/**
 * SlidingForwarding is a method to store forwarding information in a compressed form into the object header,
 * that has been specifically designed for sliding compaction GCs.
 * It avoids overriding the compressed class pointer in the upper bits of the header, which would otherwise
 * be lost. SlidingForwarding requires only small side tables and guarantees constant-time access and modification.
 *
 * The idea is to use a pointer compression scheme very similar to the one that is used for compressed oops.
 * We divide the heap into number of logical regions. Each region spans maximum of 2^NUM_COMPRESSED_BITS words.
 * We take advantage of the fact that sliding compaction can forward objects from one region to a maximum of
 * two regions (including itself, but that does not really matter). We need 1 bit to indicate which region is forwarded
 * into. We also currently require the two lowest header bits to indicate that the object is forwarded. In addition to that,
 * we use 1 more bit to indicate that we should use a fallback-lookup-table instead of using the sliding encoding.
 *
 * For addressing, we need a table with N*2 entries, for N logical regions. For each region, it gives the base
 * address of the two target regions, or a special placeholder if not used.
 *
 * Adding a forwarding then works as follows:
 * Given an original address 'orig', and a 'target' address:
 * - Look-up first target base of region of orig. If it is already established and the region
 *   that 'target' is in, then use it in step 3. If not yet used, establish it to be the base of region of target
     address. Use that base in step 3.
 * - Else, if first target base is already used, check second target base. This must either be unused, or the
 *   base of the region of our target address. If unused, establish it to be the base of the region of our target
 *   address. Use that base for next step.
 * - Now we found a base address. Encode the target address with that base into lowest NUM_COMPRESSED_BITS bits, and shift
 *   that up by 4 bits. Set the 3rd bit if we used the secondary target base, otherwise leave it at 0. Set the
 *   lowest two bits to indicate that the object has been forwarded. Store that in the lowest 32 bits of the
 *   original object's header.
 *
 * Similarily, looking up the target address, given an original object address works as follows:
 * - Load lowest 32 from original object header. Extract target region bit and compressed address bits.
 * - Depending on target region bit, load base address from the target base table by looking up the corresponding entry
 *   for the region of the original object.
 * - Decode the target address by using the target base address and the compressed address bits.
 *
 * One complication is that G1 serial compaction breaks the assumption that we only forward
 * to two target regions. When that happens, we initialize a fallback-hashtable for storing those extra
 * forwardings, and set the 4th bit in the header to indicate that the forwardee is not encoded but
 * should be looked-up in the hashtable. G1 serial compaction is not very common -  it is the last-last-ditch
 * GC that is used when the JVM is scrambling to squeeze more space out of the heap, and at that
 * point, ultimate performance is no longer the main concern.
 */
class SlidingForwarding : public CHeapObj<mtGC> {
private:
  static const uintptr_t MARK_LOWER_HALF_MASK = 0xffffffff;

  // We need the lowest two bits to indicate a forwarded object.
  // The 3rd bit (fallback-bit) indicates that the forwardee should be
  // looked-up in a fallback-table.
  static const int FALLBACK_SHIFT = markWord::lock_bits;
  static const int FALLBACK_BITS = 1;
  static const int FALLBACK_MASK = right_n_bits(FALLBACK_BITS) << FALLBACK_SHIFT;
  // The 4th bit selects the target region.
  static const int REGION_SHIFT = FALLBACK_SHIFT + FALLBACK_BITS;
  static const int REGION_BITS = 1;

  // The compressed address bits start here.
  static const int COMPRESSED_BITS_SHIFT = REGION_SHIFT + REGION_BITS;

  // How many bits we use for the compressed pointer
  static const int NUM_COMPRESSED_BITS = 32 - COMPRESSED_BITS_SHIFT;

  static const size_t NUM_TARGET_REGIONS = 1 << REGION_BITS;

  // Indicates an usused base address in the target base table. We cannot use 0, because that may already be
  // a valid base address in zero-based heaps. 0x1 is safe because heap base addresses must be aligned by 2^X.
  static HeapWord* const UNUSED_BASE;

  HeapWord*  const _heap_start;
  size_t           _num_regions;
  size_t           _region_size_words_shift;
  HeapWord**       _target_base_table;

  FallbackTable* _fallback_table;

  inline size_t region_index_containing(HeapWord* addr) const;
  inline bool region_contains(HeapWord* region_base, HeapWord* addr) const;

  inline uintptr_t encode_forwarding(HeapWord* original, HeapWord* target);
  inline HeapWord* decode_forwarding(HeapWord* original, uintptr_t encoded) const;

  void fallback_forward_to(HeapWord* from, HeapWord* to);
  HeapWord* fallback_forwardee(HeapWord* from) const;

public:
  SlidingForwarding(MemRegion heap, size_t region_size_words);
  ~SlidingForwarding();

  void begin();
  void end();

  inline void forward_to(oop original, oop target);
  inline oop forwardee(oop original) const;
};

/*
 * A simple hash-table that acts as fallback for the sliding forwarding.
 * This is used in the case of G1 serial compactio, which violates the
 * assumption of sliding forwarding that each object of any region is only
 * ever forwarded to one of two target regions. At this point, the GC is
 * scrambling to free up more Java heap memory, and therefore performance
 * is not the major concern.
 *
 * The implementation is a straightforward open hashtable.
 * It is a single-threaded (not thread-safe) implementation, and that
 * is sufficient because G1 serial compaction is single-threaded.
 */
class FallbackTable : public CHeapObj<mtGC>{
private:
  struct FallbackTableEntry {
    FallbackTableEntry* _next;
    HeapWord* _from;
    HeapWord* _to;
  };

  static const size_t TABLE_SIZE = 128;
  FallbackTableEntry _table[TABLE_SIZE];

  static size_t home_index(HeapWord* from);

public:
  FallbackTable();
  ~FallbackTable();

  void forward_to(HeapWord* from, HeapWord* to);
  HeapWord* forwardee(HeapWord* from) const;
};

#endif // _LP64
#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_HPP
