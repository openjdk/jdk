/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_CMS_CMSCARDTABLE_HPP
#define SHARE_GC_CMS_CMSCARDTABLE_HPP

#include "gc/shared/cardTableRS.hpp"
#include "utilities/globalDefinitions.hpp"

class DirtyCardToOopClosure;
class MemRegion;
class OopsInGenClosure;
class Space;

class CMSCardTable : public CardTableRS {
private:
  // Returns the number of chunks necessary to cover "mr".
  size_t chunks_to_cover(MemRegion mr);

  // Returns the index of the chunk in a stride which
  // covers the given address.
  uintptr_t addr_to_chunk_index(const void* addr);

  // Initializes "lowest_non_clean" to point to the array for the region
  // covering "sp", and "lowest_non_clean_base_chunk_index" to the chunk
  // index of the corresponding to the first element of that array.
  // Ensures that these arrays are of sufficient size, allocating if necessary.
  // May be called by several threads concurrently.
  void get_LNC_array_for_space(Space* sp,
                               CardValue**& lowest_non_clean,
                               uintptr_t& lowest_non_clean_base_chunk_index,
                               size_t& lowest_non_clean_chunk_size);

  // Apply cl, which must either itself apply dcto_cl or be dcto_cl,
  // to the cards in the stride (of n_strides) within the given space.
  void process_stride(Space* sp,
                      MemRegion used,
                      jint stride, int n_strides,
                      OopsInGenClosure* cl,
                      CardTableRS* ct,
                      CardValue** lowest_non_clean,
                      uintptr_t lowest_non_clean_base_chunk_index,
                      size_t lowest_non_clean_chunk_size);

  // Makes sure that chunk boundaries are handled appropriately, by
  // adjusting the min_done of dcto_cl, and by using a special card-table
  // value to indicate how min_done should be set.
  void process_chunk_boundaries(Space* sp,
                                DirtyCardToOopClosure* dcto_cl,
                                MemRegion chunk_mr,
                                MemRegion used,
                                CardValue** lowest_non_clean,
                                uintptr_t lowest_non_clean_base_chunk_index,
                                size_t    lowest_non_clean_chunk_size);

  virtual void verify_used_region_at_save_marks(Space* sp) const NOT_DEBUG_RETURN;

protected:
  // Work method used to implement non_clean_card_iterate_possibly_parallel()
  // above in the parallel case.
  virtual void non_clean_card_iterate_parallel_work(Space* sp, MemRegion mr,
                                                    OopsInGenClosure* cl, CardTableRS* ct,
                                                    uint n_threads);

public:
  CMSCardTable(MemRegion whole_heap);
};

#endif // SHARE_GC_CMS_CMSCARDTABLE_HPP
