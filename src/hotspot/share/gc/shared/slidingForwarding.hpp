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

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/fastHash.hpp"
#include "utilities/resourceHash.hpp"

/**
 * SlidingForwarding is a method to store forwarding information in a compressed form into the object header,
 * that has been specifically designed for sliding compaction GCs and compact object headers. With compact object
 * headers, we store the compressed class pointer in the header, which would be overwritten by full forwarding
 * pointer, if we allow the legacy forwarding code to act. This would lose the class information for the object,
 * which is required later in GC cycle to iterate the reference fields and get the object size for copying.
 *
 * SlidingForwarding requires only small side tables and guarantees constant-time access and modification.
 *
 * The idea is to use a pointer compression scheme very similar to the one that is used for compressed oops.
 * We divide the heap into number of logical regions. Each region spans maximum of 2^NUM_OFFSET_BITS words.
 *
 * The key advantage of sliding compaction for encoding efficiency: it can forward objects from one region to a
 * maximum of two regions. This is an intuitive property: when we slide the compact region full of data, it can
 * only span two adjacent regions. This property allows us to use the off-side table to record the addresses of
 * two target regions. The table holds N*2 entries for N logical regions. For each region, it gives the base
 * address of the two target regions, or a special placeholder if not used. A single bit in forwarding would
 * indicate to which of the two "to" regions the object is forwarded into.
 *
 * This encoding efficiency allows to store the forwarding information in the object header _together_ with the
 * compressed class pointer.
 *
 * When recording the sliding forwarding, the mark word would look roughly like this:
 *
 *   64                              32                                0
 *    [................................OOOOOOOOOOOOOOOOOOOOOOOOOOOOAFTT]
 *                                                                    ^----- normal lock bits, would record "object is forwarded"
 *                                                                  ^------- fallback bit (explained below)
 *                                                                 ^-------- alternate region select
 *                                     ^------------------------------------ in-region offset
 *     ^-------------------------------------------------------------------- protected area, *not touched* by this code, useful for
 *                                                                           compressed class pointer with compact object headers
 *
 * Adding a forwarding then generally works as follows:
 *   1. Compute the "to" offset in the "to" region, this gives "offset".
 *   2. Check if the primary "from" offset at base table contains "to" region base, use it.
 *      If not usable, continue to next step. If usable, set "alternate" = "false" and jump to (4).
 *   3. Check if the alternate "from" offset at base table contains "to" region base, use it.
 *      This gives us "alternate" = "true". This should always complete for sliding forwarding.
 *   4. Compute the mark word from "offset" and "alternate", write it out
 *
 * Similarly, looking up the target address, given an original object address generally works as follows:
 *   1. Load the mark from object, and decode "offset" and "alternate" from there
 *   2. Compute the "from" base offset from the object
 *   3. Look up "to" region base from the base table either at primary or alternate indices, using "alternate" flag
 *   4. Compute the "to" address from "to" region base and "offset"
 *
 * This algorithm is broken by G1 last-ditch serial compaction: there, object from a single region can be
 * forwarded to multiple, more than two regions. To deal with that, we initialize a fallback-hashtable for
 * storing those extra forwardings, and set another bit in the header to indicate that the forwardee is not
 * encoded but should be looked-up in the hashtable. G1 serial compaction is not very common - it is the
 * last-last-ditch GC that is used when the JVM is scrambling to squeeze more space out of the heap, and at
 * that point, ultimate performance is no longer the main concern.
 */
class SlidingForwarding : public AllStatic {
private:

  /*
   * A simple hash-table that acts as fallback for the sliding forwarding.
   * This is used in the case of G1 serial compaction, which violates the
   * assumption of sliding forwarding that each object of any region is only
   * ever forwarded to one of two target regions. At this point, the GC is
   * scrambling to free up more Java heap memory, and therefore performance
   * is not the major concern.
   *
   * The implementation is a straightforward open hashtable.
   * It is a single-threaded (not thread-safe) implementation, and that
   * is sufficient because G1 serial compaction is single-threaded.
   */
  inline static unsigned hash(HeapWord* const& from) {
    uint64_t val = reinterpret_cast<uint64_t>(from);
    uint64_t hash = FastHash::get_hash64(val, UCONST64(0xAAAAAAAAAAAAAAAA));
    return checked_cast<unsigned>(hash >> 32);
  }
  inline static bool equals(HeapWord* const& lhs, HeapWord* const& rhs) {
    return lhs == rhs;
  }
  typedef ResourceHashtable<HeapWord* /* key-type */, HeapWord* /* value-type */,
                            1024 /* size */, AnyObj::C_HEAP /* alloc-type */, mtGC,
                            SlidingForwarding::hash, SlidingForwarding::equals> FallbackTable;

  static const uintptr_t MARK_LOWER_HALF_MASK = right_n_bits(32);

  // We need the lowest two bits to indicate a forwarded object.
  // The next bit indicates that the forwardee should be looked-up in a fallback-table.
  static const int FALLBACK_SHIFT = markWord::lock_bits;
  static const int FALLBACK_BITS = 1;
  static const int FALLBACK_MASK = right_n_bits(FALLBACK_BITS) << FALLBACK_SHIFT;

  // Next bit selects the target region
  static const int ALT_REGION_SHIFT = FALLBACK_SHIFT + FALLBACK_BITS;
  static const int ALT_REGION_BITS = 1;
  // This will be "2" always, but expose it as named constant for clarity
  static const size_t NUM_TARGET_REGIONS = 1 << ALT_REGION_BITS;

  // The offset bits start then
  static const int OFFSET_BITS_SHIFT = ALT_REGION_SHIFT + ALT_REGION_BITS;

  // How many bits we use for the offset
  static const int NUM_OFFSET_BITS = 32 - OFFSET_BITS_SHIFT;

  // Indicates an unused base address in the target base table.
  static HeapWord* const UNUSED_BASE;

  static HeapWord*      _heap_start;
  static size_t         _region_size_words;

  static size_t         _heap_start_region_bias;
  static size_t         _num_regions;
  static uint           _region_size_bytes_shift;
  static uintptr_t      _region_mask;

  // The target base table memory.
  static HeapWord**     _bases_table;
  // Entries into the target base tables, biased to the start of the heap.
  static HeapWord**     _biased_bases[NUM_TARGET_REGIONS];

  static FallbackTable* _fallback_table;

  static inline size_t biased_region_index_containing(HeapWord* addr);

  static inline uintptr_t encode_forwarding(HeapWord* from, HeapWord* to);
  static inline HeapWord* decode_forwarding(HeapWord* from, uintptr_t encoded);

  static void fallback_forward_to(HeapWord* from, HeapWord* to);
  static HeapWord* fallback_forwardee(HeapWord* from);

  static inline void forward_to_impl(oop from, oop to);
  static inline oop forwardee_impl(oop from);

public:
  static void initialize(MemRegion heap, size_t region_size_words);

  static void begin();
  static void end();

  static inline bool is_forwarded(oop obj);
  static inline bool is_not_forwarded(oop obj);

  template <bool ALT_FWD>
  static inline void forward_to(oop from, oop to);
  template <bool ALT_FWD>
  static inline oop forwardee(oop from);
};

#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_HPP
