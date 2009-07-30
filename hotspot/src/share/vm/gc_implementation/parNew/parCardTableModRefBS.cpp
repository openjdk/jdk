/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_parCardTableModRefBS.cpp.incl"

void CardTableModRefBS::par_non_clean_card_iterate_work(Space* sp, MemRegion mr,
                                                        DirtyCardToOopClosure* dcto_cl,
                                                        MemRegionClosure* cl,
                                                        bool clear,
                                                        int n_threads) {
  if (n_threads > 0) {
    assert((n_threads == 1 && ParallelGCThreads == 0) ||
           n_threads <= (int)ParallelGCThreads,
           "# worker threads != # requested!");
    // Make sure the LNC array is valid for the space.
    jbyte**   lowest_non_clean;
    uintptr_t lowest_non_clean_base_chunk_index;
    size_t    lowest_non_clean_chunk_size;
    get_LNC_array_for_space(sp, lowest_non_clean,
                            lowest_non_clean_base_chunk_index,
                            lowest_non_clean_chunk_size);

    int n_strides = n_threads * StridesPerThread;
    SequentialSubTasksDone* pst = sp->par_seq_tasks();
    pst->set_par_threads(n_threads);
    pst->set_n_tasks(n_strides);

    int stride = 0;
    while (!pst->is_task_claimed(/* reference */ stride)) {
      process_stride(sp, mr, stride, n_strides, dcto_cl, cl, clear,
                     lowest_non_clean,
                     lowest_non_clean_base_chunk_index,
                     lowest_non_clean_chunk_size);
    }
    if (pst->all_tasks_completed()) {
      // Clear lowest_non_clean array for next time.
      intptr_t first_chunk_index = addr_to_chunk_index(mr.start());
      uintptr_t last_chunk_index  = addr_to_chunk_index(mr.last());
      for (uintptr_t ch = first_chunk_index; ch <= last_chunk_index; ch++) {
        intptr_t ind = ch - lowest_non_clean_base_chunk_index;
        assert(0 <= ind && ind < (intptr_t)lowest_non_clean_chunk_size,
               "Bounds error");
        lowest_non_clean[ind] = NULL;
      }
    }
  }
}

void
CardTableModRefBS::
process_stride(Space* sp,
               MemRegion used,
               jint stride, int n_strides,
               DirtyCardToOopClosure* dcto_cl,
               MemRegionClosure* cl,
               bool clear,
               jbyte** lowest_non_clean,
               uintptr_t lowest_non_clean_base_chunk_index,
               size_t    lowest_non_clean_chunk_size) {
  // We don't have to go downwards here; it wouldn't help anyway,
  // because of parallelism.

  // Find the first card address of the first chunk in the stride that is
  // at least "bottom" of the used region.
  jbyte*    start_card  = byte_for(used.start());
  jbyte*    end_card    = byte_after(used.last());
  uintptr_t start_chunk = addr_to_chunk_index(used.start());
  uintptr_t start_chunk_stride_num = start_chunk % n_strides;
  jbyte* chunk_card_start;

  if ((uintptr_t)stride >= start_chunk_stride_num) {
    chunk_card_start = (jbyte*)(start_card +
                                (stride - start_chunk_stride_num) *
                                CardsPerStrideChunk);
  } else {
    // Go ahead to the next chunk group boundary, then to the requested stride.
    chunk_card_start = (jbyte*)(start_card +
                                (n_strides - start_chunk_stride_num + stride) *
                                CardsPerStrideChunk);
  }

  while (chunk_card_start < end_card) {
    // We don't have to go downwards here; it wouldn't help anyway,
    // because of parallelism.  (We take care with "min_done"; see below.)
    // Invariant: chunk_mr should be fully contained within the "used" region.
    jbyte*    chunk_card_end = chunk_card_start + CardsPerStrideChunk;
    MemRegion chunk_mr       = MemRegion(addr_for(chunk_card_start),
                                         chunk_card_end >= end_card ?
                                           used.end() : addr_for(chunk_card_end));
    assert(chunk_mr.word_size() > 0, "[chunk_card_start > used_end)");
    assert(used.contains(chunk_mr), "chunk_mr should be subset of used");

    // Process the chunk.
    process_chunk_boundaries(sp,
                             dcto_cl,
                             chunk_mr,
                             used,
                             lowest_non_clean,
                             lowest_non_clean_base_chunk_index,
                             lowest_non_clean_chunk_size);

    non_clean_card_iterate_work(chunk_mr, cl, clear);

    // Find the next chunk of the stride.
    chunk_card_start += CardsPerStrideChunk * n_strides;
  }
}

void
CardTableModRefBS::
process_chunk_boundaries(Space* sp,
                         DirtyCardToOopClosure* dcto_cl,
                         MemRegion chunk_mr,
                         MemRegion used,
                         jbyte** lowest_non_clean,
                         uintptr_t lowest_non_clean_base_chunk_index,
                         size_t    lowest_non_clean_chunk_size)
{
  // We must worry about the chunk boundaries.

  // First, set our max_to_do:
  HeapWord* max_to_do = NULL;
  uintptr_t cur_chunk_index = addr_to_chunk_index(chunk_mr.start());
  cur_chunk_index           = cur_chunk_index - lowest_non_clean_base_chunk_index;

  if (chunk_mr.end() < used.end()) {
    // This is not the last chunk in the used region.  What is the last
    // object?
    HeapWord* last_block = sp->block_start(chunk_mr.end());
    assert(last_block <= chunk_mr.end(), "In case this property changes.");
    if (last_block == chunk_mr.end()
        || !sp->block_is_obj(last_block)) {
      max_to_do = chunk_mr.end();

    } else {
      // It is an object and starts before the end of the current chunk.
      // last_obj_card is the card corresponding to the start of the last object
      // in the chunk.  Note that the last object may not start in
      // the chunk.
      jbyte* last_obj_card = byte_for(last_block);
      if (!card_may_have_been_dirty(*last_obj_card)) {
        // The card containing the head is not dirty.  Any marks in
        // subsequent cards still in this chunk must have been made
        // precisely; we can cap processing at the end.
        max_to_do = chunk_mr.end();
      } else {
        // The last object must be considered dirty, and extends onto the
        // following chunk.  Look for a dirty card in that chunk that will
        // bound our processing.
        jbyte* limit_card = NULL;
        size_t last_block_size = sp->block_size(last_block);
        jbyte* last_card_of_last_obj =
          byte_for(last_block + last_block_size - 1);
        jbyte* first_card_of_next_chunk = byte_for(chunk_mr.end());
        // This search potentially goes a long distance looking
        // for the next card that will be scanned.  For example,
        // an object that is an array of primitives will not
        // have any cards covering regions interior to the array
        // that will need to be scanned. The scan can be terminated
        // at the last card of the next chunk.  That would leave
        // limit_card as NULL and would result in "max_to_do"
        // being set with the LNC value or with the end
        // of the last block.
        jbyte* last_card_of_next_chunk = first_card_of_next_chunk +
          CardsPerStrideChunk;
        assert(byte_for(chunk_mr.end()) - byte_for(chunk_mr.start())
          == CardsPerStrideChunk, "last card of next chunk may be wrong");
        jbyte* last_card_to_check = (jbyte*) MIN2(last_card_of_last_obj,
                                                  last_card_of_next_chunk);
        for (jbyte* cur = first_card_of_next_chunk;
             cur <= last_card_to_check; cur++) {
          if (card_will_be_scanned(*cur)) {
            limit_card = cur; break;
          }
        }
        assert(0 <= cur_chunk_index+1 &&
               cur_chunk_index+1 < lowest_non_clean_chunk_size,
               "Bounds error.");
        // LNC for the next chunk
        jbyte* lnc_card = lowest_non_clean[cur_chunk_index+1];
        if (limit_card == NULL) {
          limit_card = lnc_card;
        }
        if (limit_card != NULL) {
          if (lnc_card != NULL) {
            limit_card = (jbyte*)MIN2((intptr_t)limit_card,
                                      (intptr_t)lnc_card);
          }
          max_to_do = addr_for(limit_card);
        } else {
          max_to_do = last_block + last_block_size;
        }
      }
    }
    assert(max_to_do != NULL, "OOPS!");
  } else {
    max_to_do = used.end();
  }
  // Now we can set the closure we're using so it doesn't to beyond
  // max_to_do.
  dcto_cl->set_min_done(max_to_do);
#ifndef PRODUCT
  dcto_cl->set_last_bottom(max_to_do);
#endif

  // Now we set *our" lowest_non_clean entry.
  // Find the object that spans our boundary, if one exists.
  // Nothing to do on the first chunk.
  if (chunk_mr.start() > used.start()) {
    // first_block is the block possibly spanning the chunk start
    HeapWord* first_block = sp->block_start(chunk_mr.start());
    // Does the block span the start of the chunk and is it
    // an object?
    if (first_block < chunk_mr.start() &&
        sp->block_is_obj(first_block)) {
      jbyte* first_dirty_card = NULL;
      jbyte* last_card_of_first_obj =
          byte_for(first_block + sp->block_size(first_block) - 1);
      jbyte* first_card_of_cur_chunk = byte_for(chunk_mr.start());
      jbyte* last_card_of_cur_chunk = byte_for(chunk_mr.last());
      jbyte* last_card_to_check =
        (jbyte*) MIN2((intptr_t) last_card_of_cur_chunk,
                      (intptr_t) last_card_of_first_obj);
      for (jbyte* cur = first_card_of_cur_chunk;
           cur <= last_card_to_check; cur++) {
        if (card_will_be_scanned(*cur)) {
          first_dirty_card = cur; break;
        }
      }
      if (first_dirty_card != NULL) {
        assert(0 <= cur_chunk_index &&
                 cur_chunk_index < lowest_non_clean_chunk_size,
               "Bounds error.");
        lowest_non_clean[cur_chunk_index] = first_dirty_card;
      }
    }
  }
}

void
CardTableModRefBS::
get_LNC_array_for_space(Space* sp,
                        jbyte**& lowest_non_clean,
                        uintptr_t& lowest_non_clean_base_chunk_index,
                        size_t& lowest_non_clean_chunk_size) {

  int       i        = find_covering_region_containing(sp->bottom());
  MemRegion covered  = _covered[i];
  size_t    n_chunks = chunks_to_cover(covered);

  // Only the first thread to obtain the lock will resize the
  // LNC array for the covered region.  Any later expansion can't affect
  // the used_at_save_marks region.
  // (I observed a bug in which the first thread to execute this would
  // resize, and then it would cause "expand_and_allocates" that would
  // Increase the number of chunks in the covered region.  Then a second
  // thread would come and execute this, see that the size didn't match,
  // and free and allocate again.  So the first thread would be using a
  // freed "_lowest_non_clean" array.)

  // Do a dirty read here. If we pass the conditional then take the rare
  // event lock and do the read again in case some other thread had already
  // succeeded and done the resize.
  int cur_collection = Universe::heap()->total_collections();
  if (_last_LNC_resizing_collection[i] != cur_collection) {
    MutexLocker x(ParGCRareEvent_lock);
    if (_last_LNC_resizing_collection[i] != cur_collection) {
      if (_lowest_non_clean[i] == NULL ||
          n_chunks != _lowest_non_clean_chunk_size[i]) {

        // Should we delete the old?
        if (_lowest_non_clean[i] != NULL) {
          assert(n_chunks != _lowest_non_clean_chunk_size[i],
                 "logical consequence");
          FREE_C_HEAP_ARRAY(CardPtr, _lowest_non_clean[i]);
          _lowest_non_clean[i] = NULL;
        }
        // Now allocate a new one if necessary.
        if (_lowest_non_clean[i] == NULL) {
          _lowest_non_clean[i]                  = NEW_C_HEAP_ARRAY(CardPtr, n_chunks);
          _lowest_non_clean_chunk_size[i]       = n_chunks;
          _lowest_non_clean_base_chunk_index[i] = addr_to_chunk_index(covered.start());
          for (int j = 0; j < (int)n_chunks; j++)
            _lowest_non_clean[i][j] = NULL;
        }
      }
      _last_LNC_resizing_collection[i] = cur_collection;
    }
  }
  // In any case, now do the initialization.
  lowest_non_clean                  = _lowest_non_clean[i];
  lowest_non_clean_base_chunk_index = _lowest_non_clean_base_chunk_index[i];
  lowest_non_clean_chunk_size       = _lowest_non_clean_chunk_size[i];
}
