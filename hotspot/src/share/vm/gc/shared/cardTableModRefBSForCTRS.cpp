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

#include "precompiled.hpp"
#include "gc/shared/cardTableModRefBS.inline.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "memory/allocation.inline.hpp"
#include "gc/shared/space.inline.hpp"

CardTableModRefBSForCTRS::CardTableModRefBSForCTRS(MemRegion whole_heap) :
  CardTableModRefBS(
    whole_heap,
    BarrierSet::FakeRtti(BarrierSet::CardTableForRS)),
  // LNC functionality
  _lowest_non_clean(NULL),
  _lowest_non_clean_chunk_size(NULL),
  _lowest_non_clean_base_chunk_index(NULL),
  _last_LNC_resizing_collection(NULL)
{ }

void CardTableModRefBSForCTRS::initialize() {
  CardTableModRefBS::initialize();
  _lowest_non_clean =
    NEW_C_HEAP_ARRAY(CardArr, _max_covered_regions, mtGC);
  _lowest_non_clean_chunk_size =
    NEW_C_HEAP_ARRAY(size_t, _max_covered_regions, mtGC);
  _lowest_non_clean_base_chunk_index =
    NEW_C_HEAP_ARRAY(uintptr_t, _max_covered_regions, mtGC);
  _last_LNC_resizing_collection =
    NEW_C_HEAP_ARRAY(int, _max_covered_regions, mtGC);
  if (_lowest_non_clean == NULL
      || _lowest_non_clean_chunk_size == NULL
      || _lowest_non_clean_base_chunk_index == NULL
      || _last_LNC_resizing_collection == NULL)
    vm_exit_during_initialization("couldn't allocate an LNC array.");
  for (int i = 0; i < _max_covered_regions; i++) {
    _lowest_non_clean[i] = NULL;
    _lowest_non_clean_chunk_size[i] = 0;
    _last_LNC_resizing_collection[i] = -1;
  }
}

CardTableModRefBSForCTRS::~CardTableModRefBSForCTRS() {
  if (_lowest_non_clean) {
    FREE_C_HEAP_ARRAY(CardArr, _lowest_non_clean);
    _lowest_non_clean = NULL;
  }
  if (_lowest_non_clean_chunk_size) {
    FREE_C_HEAP_ARRAY(size_t, _lowest_non_clean_chunk_size);
    _lowest_non_clean_chunk_size = NULL;
  }
  if (_lowest_non_clean_base_chunk_index) {
    FREE_C_HEAP_ARRAY(uintptr_t, _lowest_non_clean_base_chunk_index);
    _lowest_non_clean_base_chunk_index = NULL;
  }
  if (_last_LNC_resizing_collection) {
    FREE_C_HEAP_ARRAY(int, _last_LNC_resizing_collection);
    _last_LNC_resizing_collection = NULL;
  }
}

bool CardTableModRefBSForCTRS::card_will_be_scanned(jbyte cv) {
  return
    card_is_dirty_wrt_gen_iter(cv) ||
    _rs->is_prev_nonclean_card_val(cv);
}

bool CardTableModRefBSForCTRS::card_may_have_been_dirty(jbyte cv) {
  return
    cv != clean_card &&
    (card_is_dirty_wrt_gen_iter(cv) ||
     CardTableRS::youngergen_may_have_been_dirty(cv));
}

void CardTableModRefBSForCTRS::non_clean_card_iterate_possibly_parallel(
  Space* sp,
  MemRegion mr,
  OopsInGenClosure* cl,
  CardTableRS* ct,
  uint n_threads)
{
  if (!mr.is_empty()) {
    if (n_threads > 0) {
#if INCLUDE_ALL_GCS
      non_clean_card_iterate_parallel_work(sp, mr, cl, ct, n_threads);
#else  // INCLUDE_ALL_GCS
      fatal("Parallel gc not supported here.");
#endif // INCLUDE_ALL_GCS
    } else {
      // clear_cl finds contiguous dirty ranges of cards to process and clear.

      // This is the single-threaded version used by DefNew.
      const bool parallel = false;

      DirtyCardToOopClosure* dcto_cl = sp->new_dcto_cl(cl, precision(), cl->gen_boundary(), parallel);
      ClearNoncleanCardWrapper clear_cl(dcto_cl, ct, parallel);

      clear_cl.do_MemRegion(mr);
    }
  }
}

