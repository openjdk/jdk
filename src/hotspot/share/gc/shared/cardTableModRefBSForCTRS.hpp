/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_CARDTABLEMODREFBSFORCTRS_HPP
#define SHARE_VM_GC_SHARED_CARDTABLEMODREFBSFORCTRS_HPP

#include "gc/shared/cardTableModRefBS.hpp"

class CardTableRS;
class DirtyCardToOopClosure;
class OopsInGenClosure;

// A specialization for the CardTableRS gen rem set.
class CardTableModRefBSForCTRS: public CardTableModRefBS {
  friend class CardTableRS;

public:
  CardTableModRefBSForCTRS(MemRegion whole_heap);
  ~CardTableModRefBSForCTRS();

  virtual void initialize();

  void set_CTRS(CardTableRS* rs) { _rs = rs; }

private:
  CardTableRS* _rs;

  // *** Support for parallel card scanning.

  // dirty and precleaned are equivalent wrt younger_refs_iter.
  static bool card_is_dirty_wrt_gen_iter(jbyte cv) {
    return cv == dirty_card || cv == precleaned_card;
  }

  // Returns "true" iff the value "cv" will cause the card containing it
  // to be scanned in the current traversal.  May be overridden by
  // subtypes.
  bool card_will_be_scanned(jbyte cv);

  // Returns "true" iff the value "cv" may have represented a dirty card at
  // some point.
  bool card_may_have_been_dirty(jbyte cv);

  // Iterate over the portion of the card-table which covers the given
  // region mr in the given space and apply cl to any dirty sub-regions
  // of mr. Clears the dirty cards as they are processed.
  void non_clean_card_iterate_possibly_parallel(Space* sp, MemRegion mr,
                                                OopsInGenClosure* cl, CardTableRS* ct,
                                                uint n_threads);

  // Work method used to implement non_clean_card_iterate_possibly_parallel()
  // above in the parallel case.
  void non_clean_card_iterate_parallel_work(Space* sp, MemRegion mr,
                                            OopsInGenClosure* cl, CardTableRS* ct,
                                            uint n_threads);

  // This is an array, one element per covered region of the card table.
  // Each entry is itself an array, with one element per chunk in the
  // covered region.  Each entry of these arrays is the lowest non-clean
  // card of the corresponding chunk containing part of an object from the
  // previous chunk, or else NULL.
  typedef jbyte*  CardPtr;
  typedef CardPtr* CardArr;
  CardArr* _lowest_non_clean;
  size_t*  _lowest_non_clean_chunk_size;
  uintptr_t* _lowest_non_clean_base_chunk_index;
  volatile int* _last_LNC_resizing_collection;

  // Initializes "lowest_non_clean" to point to the array for the region
  // covering "sp", and "lowest_non_clean_base_chunk_index" to the chunk
  // index of the corresponding to the first element of that array.
  // Ensures that these arrays are of sufficient size, allocating if necessary.
  // May be called by several threads concurrently.
  void get_LNC_array_for_space(Space* sp,
                               jbyte**& lowest_non_clean,
                               uintptr_t& lowest_non_clean_base_chunk_index,
                               size_t& lowest_non_clean_chunk_size);

  // Returns the number of chunks necessary to cover "mr".
  size_t chunks_to_cover(MemRegion mr) {
    return (size_t)(addr_to_chunk_index(mr.last()) -
                    addr_to_chunk_index(mr.start()) + 1);
  }

  // Returns the index of the chunk in a stride which
  // covers the given address.
  uintptr_t addr_to_chunk_index(const void* addr) {
    uintptr_t card = (uintptr_t) byte_for(addr);
    return card / ParGCCardsPerStrideChunk;
  }

  // Apply cl, which must either itself apply dcto_cl or be dcto_cl,
  // to the cards in the stride (of n_strides) within the given space.
  void process_stride(Space* sp,
                      MemRegion used,
                      jint stride, int n_strides,
                      OopsInGenClosure* cl,
                      CardTableRS* ct,
                      jbyte** lowest_non_clean,
                      uintptr_t lowest_non_clean_base_chunk_index,
                      size_t lowest_non_clean_chunk_size);

  // Makes sure that chunk boundaries are handled appropriately, by
  // adjusting the min_done of dcto_cl, and by using a special card-table
  // value to indicate how min_done should be set.
  void process_chunk_boundaries(Space* sp,
                                DirtyCardToOopClosure* dcto_cl,
                                MemRegion chunk_mr,
                                MemRegion used,
                                jbyte** lowest_non_clean,
                                uintptr_t lowest_non_clean_base_chunk_index,
                                size_t    lowest_non_clean_chunk_size);

};

template<>
struct BarrierSet::GetName<CardTableModRefBSForCTRS> {
  static const BarrierSet::Name value = BarrierSet::CardTableForRS;
};

#endif // include guard

