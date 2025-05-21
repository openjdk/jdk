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

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/markWord.hpp"
#include "oops/oopsHierarchy.hpp"

class FallbackTable;
class Mutex;

/**
 * FullGCForwarding is a method to store forwarding information in a compressed form into the object header,
 * that has been specifically designed for sliding compacting GCs and compact object headers. With compact object
 * headers, we store the compressed class pointer in the header, which would be overwritten by full forwarding
 * pointers, if we allow the legacy forwarding code to act. This would lose the class information for the object,
 * which is required later in GC cycle to iterate the reference fields and get the object size for copying.
 *
 * FullGCForwarding requires only small side tables and guarantees constant-time access and modification.
 *
 * The key advantage of sliding compaction for encoding efficiency:
 * - It forwards objects linearily, starting at the heap bottom and moving up to the top, sliding
 *   live objects towards the bottom of the heap. (The reality in parallel or regionalized GCs is a bit more
 *   complex, but conceptually it is the same.)
 * - Objects starting in any one block can only be forwarded to a memory region that is not larger than
 *   a block. (There are exceptions to this rule which are discussed below.)
 *
 * This is an intuitive property: when we slide the compact block full of data, it can not take up more
 * memory afterwards.
 * This property allows us to use a side table to record the addresses of the target memory region for
 * each block. The table holds N entries for N blocks. For each block, it gives the base
 * address of the target regions, or a special placeholder if not used.
 *
 * This encoding efficiency allows to store the forwarding information in the object header _together_ with the
 * compressed class pointer.
 *
 * The idea is to use a pointer compression scheme very similar to the one that is used for compressed oops.
 * We divide the heap into number of equal-sized blocks. Each block spans a maximum of 2^NUM_OFFSET_BITS words.
 * We maintain a side-table of target-base-addresses, with one address entry per block.
 *
 * When recording the sliding forwarding, the mark word would look roughly like this:
 *
 *   32                               0
 *    [.....................OOOOOOOOOTT]
 *                                    ^------ tag-bits, indicates 'forwarded'
 *                                  ^-------- in-region offset
 *                         ^----------------- protected area, *not touched* by this code, useful for
 *                                            compressed class pointer with compact object headers
 *
 * Adding a forwarding then generally works as follows:
 *   1. Compute the index of the block of the "from" address.
 *   2. Load the target-base-offset of the from-block from the side-table.
 *   3. If the base-offset is not-yet set, set it to the to-address of the forwarding.
 *      (In other words, the first forwarding of a block determines the target base-offset.)
 *   4. Compute the offset of the to-address in the target region.
 *   4. Store offset in the object header.
 *
 * Similarly, looking up the target address, given an original object address generally works as follows:
 *   1. Compute the index of the block of the "from" address.
 *   2. Load the target-base-offset of the from-block from the side-table.
 *   3. Extract the offset from the object header.
 *   4. Compute the "to" address from "to" region base and "offset"
 *
 * We reserve one special value for the offset:
 *  - 111111111: Indicates an exceptional forwarding (see below), for which a fallback hash-table
 *               is used to look up the target address.
 *
 * In order to support this, we need to make a change to the above algorithm:
 *  - Forwardings that would use offsets >= 111111111 (i.e. the last slot)
 *    would also need to use the fallback-table. We expect that to be relatively rare for two reasons:
 *    1. It only affects 1 out of 512 possible offsets, in other words, 1/512th of all situations in an equal
 *       distribution.
 *    2. Forwardings are not equally-distributed, because normally we 'skip' unreachable objects,
 *       thus compacting the block. Forwardings tend to cluster at the beginning of the target region,
 *       and become less likely towards the end of the possible encodable target address range.
 *       Which means in reality it will be much less frequent than 1/512.
 *
 * There are several conditions when the above algorithm would be broken because the assumption that
 * 'objects from each block can only get forwarded to a region of block-size' is violated:
 * - G1 last-ditch serial compaction: there, object from a single region can be forwarded to multiple,
 *   more than two regions. G1 serial compaction is not very common - it is the last-last-ditch GC
 *   that is used when the JVM is scrambling to squeeze more space out of the heap, and at that point,
 *   ultimate performance is no longer the main concern.
 * - When forwarding hits a space (or G1/Shenandoah region) boundary, then latter objects of a block
 *   need to be forwarded to a different address range than earlier objects in the same block.
 *   This is rare.
 * - With compact identity hash-code, objects can grow, and in the worst case use up more memory in
 *   the target block than we can address. We expect that to be rare.
 *
 * To deal with that, we initialize a fallback-hashtable for storing those extra forwardings, and use a special
 * offset pattern (0b11...1) to indicate that the forwardee is not encoded but should be looked-up in the hashtable.
 * This implies that this particular offset (the last word of a block) can not be used directly as forwarding,
 * but also has to be handled by the fallback-table.
 */
class FullGCForwarding : public AllStatic {
private:
  static constexpr int AVAILABLE_LOW_BITS       = 11;
  static constexpr int AVAILABLE_BITS_MASK      = right_n_bits(AVAILABLE_LOW_BITS);
  // The offset bits start after the lock-bits, which are currently used by Serial GC
  // for marking objects. Could be 1 for Serial GC when being clever with the bits,
  // and 0 for all other GCs.
  static constexpr int OFFSET_BITS_SHIFT = markWord::lock_shift + markWord::lock_bits;

  // How many bits we use for the offset
  static constexpr int NUM_OFFSET_BITS = AVAILABLE_LOW_BITS - OFFSET_BITS_SHIFT;
  static constexpr size_t BLOCK_SIZE_WORDS = 1 << NUM_OFFSET_BITS;
  static constexpr int BLOCK_SIZE_BYTES_SHIFT = NUM_OFFSET_BITS + LogHeapWordSize;
  static constexpr size_t MAX_OFFSET = BLOCK_SIZE_WORDS - 2;
  static constexpr uintptr_t OFFSET_MASK = right_n_bits(NUM_OFFSET_BITS) << OFFSET_BITS_SHIFT;

  // This offset bit-pattern indicates that the actual mapping is handled by the
  // fallback-table. This also implies that this cannot be used as a valid offset,
  // and we must also use the fallback-table for mappings to the last word of a
  // block.
  static constexpr uintptr_t FALLBACK_PATTERN = right_n_bits(NUM_OFFSET_BITS);
  static constexpr uintptr_t FALLBACK_PATTERN_IN_PLACE = FALLBACK_PATTERN << OFFSET_BITS_SHIFT;

  // Indicates an unused base address in the target base table.
  static HeapWord* const UNUSED_BASE;

  static HeapWord*      _heap_start;

  static size_t         _heap_start_region_bias;
  static size_t         _num_regions;
  static uintptr_t      _region_mask;

  // The target base table memory.
  static HeapWord**     _bases_table;
  // Entries into the target base tables, biased to the start of the heap.
  static HeapWord**     _biased_bases;

  static FallbackTable* _fallback_table;

#ifndef PRODUCT
  static volatile uint64_t _num_forwardings;
  static volatile uint64_t _num_fallback_forwardings;
#endif

  static inline size_t biased_region_index_containing(HeapWord* addr);

  static inline bool is_fallback(uintptr_t encoded);
  static inline uintptr_t encode_forwarding(HeapWord* from, HeapWord* to);
  static inline HeapWord* decode_forwarding(HeapWord* from, uintptr_t encoded);

  static void fallback_forward_to(HeapWord* from, HeapWord* to);
  static HeapWord* fallback_forwardee(HeapWord* from);

  static inline void forward_to_impl(oop from, oop to);
  static inline oop forwardee_impl(oop from);

public:
  static void initialize(MemRegion heap);

  static void begin();
  static void end();

  static inline bool is_forwarded(oop obj);
  static inline bool is_not_forwarded(oop obj);

  static inline void forward_to(oop from, oop to);
  static inline oop forwardee(oop from);
};

#endif // SHARE_GC_SHARED_FULLGCFORWARDING_HPP
