/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/cardTableRS.hpp"
#include "memory/sharedHeap.hpp"
#include "memory/space.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/virtualspace.hpp"
#include "runtime/vmThread.hpp"

void CardTableModRefBS::non_clean_card_iterate_parallel_work(Space* sp, MemRegion mr,
                                                             OopsInGenClosure* cl,
                                                             CardTableRS* ct,
                                                             int n_threads) {
  assert(n_threads > 0, "Error: expected n_threads > 0");
  assert((n_threads == 1 && ParallelGCThreads == 0) ||
         n_threads <= (int)ParallelGCThreads,
         "# worker threads != # requested!");
  assert(!Thread::current()->is_VM_thread() || (n_threads == 1), "There is only 1 VM thread");
  assert(UseDynamicNumberOfGCThreads ||
         !FLAG_IS_DEFAULT(ParallelGCThreads) ||
         n_threads == (int)ParallelGCThreads,
         "# worker threads != # requested!");
  // Make sure the LNC array is valid for the space.
  jbyte**   lowest_non_clean;
  uintptr_t lowest_non_clean_base_chunk_index;
  size_t    lowest_non_clean_chunk_size;
  get_LNC_array_for_space(sp, lowest_non_clean,
                          lowest_non_clean_base_chunk_index,
                          lowest_non_clean_chunk_size);

  uint n_strides = n_threads * ParGCStridesPerThread;
  SequentialSubTasksDone* pst = sp->par_seq_tasks();
  // Sets the condition for completion of the subtask (how many threads
  // need to finish in order to be done).
  pst->set_n_threads(n_threads);
  pst->set_n_tasks(n_strides);

  uint stride = 0;
  while (!pst->is_task_claimed(/* reference */ stride)) {
    process_stride(sp, mr, stride, n_strides, cl, ct,
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

void
CardTableModRefBS::
process_stride(Space* sp,
               MemRegion used,
               jint stride, int n_strides,
               OopsInGenClosure* cl,
               CardTableRS* ct,
               jbyte** lowest_non_clean,
               uintptr_t lowest_non_clean_base_chunk_index,
               size_t    lowest_non_clean_chunk_size) {
  // We go from higher to lower addresses here; it wouldn't help that much
  // because of the strided parallelism pattern used here.

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
                                ParGCCardsPerStrideChunk);
  } else {
    // Go ahead to the next chunk group boundary, then to the requested stride.
    chunk_card_start = (jbyte*)(start_card +
                                (n_strides - start_chunk_stride_num + stride) *
                                ParGCCardsPerStrideChunk);
  }

  while (chunk_card_start < end_card) {
    // Even though we go from lower to higher addresses below, the
    // strided parallelism can interleave the actual processing of the
    // dirty pages in various ways. For a specific chunk within this
    // stride, we take care to avoid double scanning or missing a card
    // by suitably initializing the "min_done" field in process_chunk_boundaries()
    // below, together with the dirty region extension accomplished in
    // DirtyCardToOopClosure::do_MemRegion().
    jbyte*    chunk_card_end = chunk_card_start + ParGCCardsPerStrideChunk;
    // Invariant: chunk_mr should be fully contained within the "used" region.
    MemRegion chunk_mr       = MemRegion(addr_for(chunk_card_start),
                                         chunk_card_end >= end_card ?
                                           used.end() : addr_for(chunk_card_end));
    assert(chunk_mr.word_size() > 0, "[chunk_card_start > used_end)");
    assert(used.contains(chunk_mr), "chunk_mr should be subset of used");

    DirtyCardToOopClosure* dcto_cl = sp->new_dcto_cl(cl, precision(),
                                                     cl->gen_boundary());
    ClearNoncleanCardWrapper clear_cl(dcto_cl, ct);


    // Process the chunk.
    process_chunk_boundaries(sp,
                             dcto_cl,
                             chunk_mr,
                             used,
                             lowest_non_clean,
                             lowest_non_clean_base_chunk_index,
                             lowest_non_clean_chunk_size);

    // We want the LNC array updates above in process_chunk_boundaries
    // to be visible before any of the card table value changes as a
    // result of the dirty card iteration below.
    OrderAccess::storestore();

    // We do not call the non_clean_card_iterate_serial() version because
    // we want to clear the cards: clear_cl here does the work of finding
    // contiguous dirty ranges of cards to process and clear.
    clear_cl.do_MemRegion(chunk_mr);

    // Find the next chunk of the stride.
    chunk_card_start += ParGCCardsPerStrideChunk * n_strides;
  }
}


// If you want a talkative process_chunk_boundaries,
// then #define NOISY(x) x
#ifdef NOISY
#error "Encountered a global preprocessor flag, NOISY, which might clash with local definition to follow"
#else
#define NOISY(x)
#endif

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
  // We must worry about non-array objects that cross chunk boundaries,
  // because such objects are both precisely and imprecisely marked:
  // .. if the head of such an object is dirty, the entire object
  //    needs to be scanned, under the interpretation that this
  //    was an imprecise mark
  // .. if the head of such an object is not dirty, we can assume
  //    precise marking and it's efficient to scan just the dirty
  //    cards.
  // In either case, each scanned reference must be scanned precisely
  // once so as to avoid cloning of a young referent. For efficiency,
  // our closures depend on this property and do not protect against
  // double scans.

  uintptr_t cur_chunk_index = addr_to_chunk_index(chunk_mr.start());
  cur_chunk_index           = cur_chunk_index - lowest_non_clean_base_chunk_index;

  NOISY(tty->print_cr("===========================================================================");)
  NOISY(tty->print_cr(" process_chunk_boundary: Called with [" PTR_FORMAT "," PTR_FORMAT ")",
                      chunk_mr.start(), chunk_mr.end());)

  // First, set "our" lowest_non_clean entry, which would be
  // used by the thread scanning an adjoining left chunk with
  // a non-array object straddling the mutual boundary.
  // Find the object that spans our boundary, if one exists.
  // first_block is the block possibly straddling our left boundary.
  HeapWord* first_block = sp->block_start(chunk_mr.start());
  assert((chunk_mr.start() != used.start()) || (first_block == chunk_mr.start()),
         "First chunk should always have a co-initial block");
  // Does the block straddle the chunk's left boundary, and is it
  // a non-array object?
  if (first_block < chunk_mr.start()        // first block straddles left bdry
      && sp->block_is_obj(first_block)      // first block is an object
      && !(oop(first_block)->is_objArray()  // first block is not an array (arrays are precisely dirtied)
           || oop(first_block)->is_typeArray())) {
    // Find our least non-clean card, so that a left neighbour
    // does not scan an object straddling the mutual boundary
    // too far to the right, and attempt to scan a portion of
    // that object twice.
    jbyte* first_dirty_card = NULL;
    jbyte* last_card_of_first_obj =
        byte_for(first_block + sp->block_size(first_block) - 1);
    jbyte* first_card_of_cur_chunk = byte_for(chunk_mr.start());
    jbyte* last_card_of_cur_chunk = byte_for(chunk_mr.last());
    jbyte* last_card_to_check =
      (jbyte*) MIN2((intptr_t) last_card_of_cur_chunk,
                    (intptr_t) last_card_of_first_obj);
    // Note that this does not need to go beyond our last card
    // if our first object completely straddles this chunk.
    for (jbyte* cur = first_card_of_cur_chunk;
         cur <= last_card_to_check; cur++) {
      jbyte val = *cur;
      if (card_will_be_scanned(val)) {
        first_dirty_card = cur; break;
      } else {
        assert(!card_may_have_been_dirty(val), "Error");
      }
    }
    if (first_dirty_card != NULL) {
      NOISY(tty->print_cr(" LNC: Found a dirty card at " PTR_FORMAT " in current chunk",
                    first_dirty_card);)
      assert(0 <= cur_chunk_index && cur_chunk_index < lowest_non_clean_chunk_size,
             "Bounds error.");
      assert(lowest_non_clean[cur_chunk_index] == NULL,
             "Write exactly once : value should be stable hereafter for this round");
      lowest_non_clean[cur_chunk_index] = first_dirty_card;
    } NOISY(else {
      tty->print_cr(" LNC: Found no dirty card in current chunk; leaving LNC entry NULL");
      // In the future, we could have this thread look for a non-NULL value to copy from its
      // right neighbour (up to the end of the first object).
      if (last_card_of_cur_chunk < last_card_of_first_obj) {
        tty->print_cr(" LNC: BEWARE!!! first obj straddles past right end of chunk:\n"
                      "   might be efficient to get value from right neighbour?");
      }
    })
  } else {
    // In this case we can help our neighbour by just asking them
    // to stop at our first card (even though it may not be dirty).
    NOISY(tty->print_cr(" LNC: first block is not a non-array object; setting LNC to first card of current chunk");)
    assert(lowest_non_clean[cur_chunk_index] == NULL, "Write once : value should be stable hereafter");
    jbyte* first_card_of_cur_chunk = byte_for(chunk_mr.start());
    lowest_non_clean[cur_chunk_index] = first_card_of_cur_chunk;
  }
  NOISY(tty->print_cr(" process_chunk_boundary: lowest_non_clean[" INTPTR_FORMAT "] = " PTR_FORMAT
                "   which corresponds to the heap address " PTR_FORMAT,
                cur_chunk_index, lowest_non_clean[cur_chunk_index],
                (lowest_non_clean[cur_chunk_index] != NULL)
                ? addr_for(lowest_non_clean[cur_chunk_index])
                : NULL);)
  NOISY(tty->print_cr("---------------------------------------------------------------------------");)

  // Next, set our own max_to_do, which will strictly/exclusively bound
  // the highest address that we will scan past the right end of our chunk.
  HeapWord* max_to_do = NULL;
  if (chunk_mr.end() < used.end()) {
    // This is not the last chunk in the used region.
    // What is our last block? We check the first block of
    // the next (right) chunk rather than strictly check our last block
    // because it's potentially more efficient to do so.
    HeapWord* const last_block = sp->block_start(chunk_mr.end());
    assert(last_block <= chunk_mr.end(), "In case this property changes.");
    if ((last_block == chunk_mr.end())     // our last block does not straddle boundary
        || !sp->block_is_obj(last_block)   // last_block isn't an object
        || oop(last_block)->is_objArray()  // last_block is an array (precisely marked)
        || oop(last_block)->is_typeArray()) {
      max_to_do = chunk_mr.end();
      NOISY(tty->print_cr(" process_chunk_boundary: Last block on this card is not a non-array object;\n"
                         "   max_to_do left at " PTR_FORMAT, max_to_do);)
    } else {
      assert(last_block < chunk_mr.end(), "Tautology");
      // It is a non-array object that straddles the right boundary of this chunk.
      // last_obj_card is the card corresponding to the start of the last object
      // in the chunk.  Note that the last object may not start in
      // the chunk.
      jbyte* const last_obj_card = byte_for(last_block);
      const jbyte val = *last_obj_card;
      if (!card_will_be_scanned(val)) {
        assert(!card_may_have_been_dirty(val), "Error");
        // The card containing the head is not dirty.  Any marks on
        // subsequent cards still in this chunk must have been made
        // precisely; we can cap processing at the end of our chunk.
        max_to_do = chunk_mr.end();
        NOISY(tty->print_cr(" process_chunk_boundary: Head of last object on this card is not dirty;\n"
                            "   max_to_do left at " PTR_FORMAT,
                            max_to_do);)
      } else {
        // The last object must be considered dirty, and extends onto the
        // following chunk.  Look for a dirty card in that chunk that will
        // bound our processing.
        jbyte* limit_card = NULL;
        const size_t last_block_size = sp->block_size(last_block);
        jbyte* const last_card_of_last_obj =
          byte_for(last_block + last_block_size - 1);
        jbyte* const first_card_of_next_chunk = byte_for(chunk_mr.end());
        // This search potentially goes a long distance looking
        // for the next card that will be scanned, terminating
        // at the end of the last_block, if no earlier dirty card
        // is found.
        assert(byte_for(chunk_mr.end()) - byte_for(chunk_mr.start()) == ParGCCardsPerStrideChunk,
               "last card of next chunk may be wrong");
        for (jbyte* cur = first_card_of_next_chunk;
             cur <= last_card_of_last_obj; cur++) {
          const jbyte val = *cur;
          if (card_will_be_scanned(val)) {
            NOISY(tty->print_cr(" Found a non-clean card " PTR_FORMAT " with value 0x%x",
                                cur, (int)val);)
            limit_card = cur; break;
          } else {
            assert(!card_may_have_been_dirty(val), "Error: card can't be skipped");
          }
        }
        if (limit_card != NULL) {
          max_to_do = addr_for(limit_card);
          assert(limit_card != NULL && max_to_do != NULL, "Error");
          NOISY(tty->print_cr(" process_chunk_boundary: Found a dirty card at " PTR_FORMAT
                        "   max_to_do set at " PTR_FORMAT " which is before end of last block in chunk: "
                        PTR_FORMAT " + " PTR_FORMAT " = " PTR_FORMAT,
                        limit_card, max_to_do, last_block, last_block_size, (last_block+last_block_size));)
        } else {
          // The following is a pessimistic value, because it's possible
          // that a dirty card on a subsequent chunk has been cleared by
          // the time we get to look at it; we'll correct for that further below,
          // using the LNC array which records the least non-clean card
          // before cards were cleared in a particular chunk.
          limit_card = last_card_of_last_obj;
          max_to_do = last_block + last_block_size;
          assert(limit_card != NULL && max_to_do != NULL, "Error");
          NOISY(tty->print_cr(" process_chunk_boundary: Found no dirty card before end of last block in chunk\n"
                              "   Setting limit_card to " PTR_FORMAT
                              " and max_to_do " PTR_FORMAT " + " PTR_FORMAT " = " PTR_FORMAT,
                              limit_card, last_block, last_block_size, max_to_do);)
        }
        assert(0 < cur_chunk_index+1 && cur_chunk_index+1 < lowest_non_clean_chunk_size,
               "Bounds error.");
        // It is possible that a dirty card for the last object may have been
        // cleared before we had a chance to examine it. In that case, the value
        // will have been logged in the LNC for that chunk.
        // We need to examine as many chunks to the right as this object
        // covers. However, we need to bound this checking to the largest
        // entry in the LNC array: this is because the heap may expand
        // after the LNC array has been created but before we reach this point,
        // and the last block in our chunk may have been expanded to include
        // the expansion delta (and possibly subsequently allocated from, so
        // it wouldn't be sufficient to check whether that last block was
        // or was not an object at this point).
        uintptr_t last_chunk_index_to_check = addr_to_chunk_index(last_block + last_block_size - 1)
                                              - lowest_non_clean_base_chunk_index;
        const uintptr_t last_chunk_index    = addr_to_chunk_index(used.last())
                                              - lowest_non_clean_base_chunk_index;
        if (last_chunk_index_to_check > last_chunk_index) {
          assert(last_block + last_block_size > used.end(),
                 err_msg("Inconsistency detected: last_block [" PTR_FORMAT "," PTR_FORMAT "]"
                         " does not exceed used.end() = " PTR_FORMAT ","
                         " yet last_chunk_index_to_check " INTPTR_FORMAT
                         " exceeds last_chunk_index " INTPTR_FORMAT,
                         last_block, last_block + last_block_size,
                         used.end(),
                         last_chunk_index_to_check, last_chunk_index));
          assert(sp->used_region().end() > used.end(),
                 err_msg("Expansion did not happen: "
                         "[" PTR_FORMAT "," PTR_FORMAT ") -> [" PTR_FORMAT "," PTR_FORMAT ")",
                         sp->used_region().start(), sp->used_region().end(), used.start(), used.end()));
          NOISY(tty->print_cr(" process_chunk_boundary: heap expanded; explicitly bounding last_chunk");)
          last_chunk_index_to_check = last_chunk_index;
        }
        for (uintptr_t lnc_index = cur_chunk_index + 1;
             lnc_index <= last_chunk_index_to_check;
             lnc_index++) {
          jbyte* lnc_card = lowest_non_clean[lnc_index];
          if (lnc_card != NULL) {
            // we can stop at the first non-NULL entry we find
            if (lnc_card <= limit_card) {
              NOISY(tty->print_cr(" process_chunk_boundary: LNC card " PTR_FORMAT " is lower than limit_card " PTR_FORMAT,
                                  "   max_to_do will be lowered to " PTR_FORMAT " from " PTR_FORMAT,
                                  lnc_card, limit_card, addr_for(lnc_card), max_to_do);)
              limit_card = lnc_card;
              max_to_do = addr_for(limit_card);
              assert(limit_card != NULL && max_to_do != NULL, "Error");
            }
            // In any case, we break now
            break;
          }  // else continue to look for a non-NULL entry if any
        }
        assert(limit_card != NULL && max_to_do != NULL, "Error");
      }
      assert(max_to_do != NULL, "OOPS 1 !");
    }
    assert(max_to_do != NULL, "OOPS 2!");
  } else {
    max_to_do = used.end();
    NOISY(tty->print_cr(" process_chunk_boundary: Last chunk of this space;\n"
                  "   max_to_do left at " PTR_FORMAT,
                  max_to_do);)
  }
  assert(max_to_do != NULL, "OOPS 3!");
  // Now we can set the closure we're using so it doesn't to beyond
  // max_to_do.
  dcto_cl->set_min_done(max_to_do);
#ifndef PRODUCT
  dcto_cl->set_last_bottom(max_to_do);
#endif
  NOISY(tty->print_cr("===========================================================================\n");)
}

#undef NOISY

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
  // resize, and then it would cause "expand_and_allocate" that would
  // increase the number of chunks in the covered region.  Then a second
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
          FREE_C_HEAP_ARRAY(CardPtr, _lowest_non_clean[i], mtGC);
          _lowest_non_clean[i] = NULL;
        }
        // Now allocate a new one if necessary.
        if (_lowest_non_clean[i] == NULL) {
          _lowest_non_clean[i]                  = NEW_C_HEAP_ARRAY(CardPtr, n_chunks, mtGC);
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
