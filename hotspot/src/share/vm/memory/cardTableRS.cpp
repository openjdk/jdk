/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_cardTableRS.cpp.incl"

CardTableRS::CardTableRS(MemRegion whole_heap,
                         int max_covered_regions) :
  GenRemSet(),
  _cur_youngergen_card_val(youngergenP1_card),
  _regions_to_iterate(max_covered_regions - 1)
{
#ifndef SERIALGC
  if (UseG1GC) {
      _ct_bs = new G1SATBCardTableLoggingModRefBS(whole_heap,
                                                  max_covered_regions);
  } else {
    _ct_bs = new CardTableModRefBSForCTRS(whole_heap, max_covered_regions);
  }
#else
  _ct_bs = new CardTableModRefBSForCTRS(whole_heap, max_covered_regions);
#endif
  set_bs(_ct_bs);
  _last_cur_val_in_gen = new jbyte[GenCollectedHeap::max_gens + 1];
  if (_last_cur_val_in_gen == NULL) {
    vm_exit_during_initialization("Could not last_cur_val_in_gen array.");
  }
  for (int i = 0; i < GenCollectedHeap::max_gens + 1; i++) {
    _last_cur_val_in_gen[i] = clean_card_val();
  }
  _ct_bs->set_CTRS(this);
}

void CardTableRS::resize_covered_region(MemRegion new_region) {
  _ct_bs->resize_covered_region(new_region);
}

jbyte CardTableRS::find_unused_youngergenP_card_value() {
  for (jbyte v = youngergenP1_card;
       v < cur_youngergen_and_prev_nonclean_card;
       v++) {
    bool seen = false;
    for (int g = 0; g < _regions_to_iterate; g++) {
      if (_last_cur_val_in_gen[g] == v) {
        seen = true;
        break;
      }
    }
    if (!seen) return v;
  }
  ShouldNotReachHere();
  return 0;
}

void CardTableRS::prepare_for_younger_refs_iterate(bool parallel) {
  // Parallel or sequential, we must always set the prev to equal the
  // last one written.
  if (parallel) {
    // Find a parallel value to be used next.
    jbyte next_val = find_unused_youngergenP_card_value();
    set_cur_youngergen_card_val(next_val);

  } else {
    // In an sequential traversal we will always write youngergen, so that
    // the inline barrier is  correct.
    set_cur_youngergen_card_val(youngergen_card);
  }
}

void CardTableRS::younger_refs_iterate(Generation* g,
                                       OopsInGenClosure* blk) {
  _last_cur_val_in_gen[g->level()+1] = cur_youngergen_card_val();
  g->younger_refs_iterate(blk);
}

class ClearNoncleanCardWrapper: public MemRegionClosure {
  MemRegionClosure* _dirty_card_closure;
  CardTableRS* _ct;
  bool _is_par;
private:
  // Clears the given card, return true if the corresponding card should be
  // processed.
  bool clear_card(jbyte* entry) {
    if (_is_par) {
      while (true) {
        // In the parallel case, we may have to do this several times.
        jbyte entry_val = *entry;
        assert(entry_val != CardTableRS::clean_card_val(),
               "We shouldn't be looking at clean cards, and this should "
               "be the only place they get cleaned.");
        if (CardTableRS::card_is_dirty_wrt_gen_iter(entry_val)
            || _ct->is_prev_youngergen_card_val(entry_val)) {
          jbyte res =
            Atomic::cmpxchg(CardTableRS::clean_card_val(), entry, entry_val);
          if (res == entry_val) {
            break;
          } else {
            assert(res == CardTableRS::cur_youngergen_and_prev_nonclean_card,
                   "The CAS above should only fail if another thread did "
                   "a GC write barrier.");
          }
        } else if (entry_val ==
                   CardTableRS::cur_youngergen_and_prev_nonclean_card) {
          // Parallelism shouldn't matter in this case.  Only the thread
          // assigned to scan the card should change this value.
          *entry = _ct->cur_youngergen_card_val();
          break;
        } else {
          assert(entry_val == _ct->cur_youngergen_card_val(),
                 "Should be the only possibility.");
          // In this case, the card was clean before, and become
          // cur_youngergen only because of processing of a promoted object.
          // We don't have to look at the card.
          return false;
        }
      }
      return true;
    } else {
      jbyte entry_val = *entry;
      assert(entry_val != CardTableRS::clean_card_val(),
             "We shouldn't be looking at clean cards, and this should "
             "be the only place they get cleaned.");
      assert(entry_val != CardTableRS::cur_youngergen_and_prev_nonclean_card,
             "This should be possible in the sequential case.");
      *entry = CardTableRS::clean_card_val();
      return true;
    }
  }

public:
  ClearNoncleanCardWrapper(MemRegionClosure* dirty_card_closure,
                           CardTableRS* ct) :
    _dirty_card_closure(dirty_card_closure), _ct(ct) {
    _is_par = (SharedHeap::heap()->n_par_threads() > 0);
  }
  void do_MemRegion(MemRegion mr) {
    // We start at the high end of "mr", walking backwards
    // while accumulating a contiguous dirty range of cards in
    // [start_of_non_clean, end_of_non_clean) which we then
    // process en masse.
    HeapWord* end_of_non_clean = mr.end();
    HeapWord* start_of_non_clean = end_of_non_clean;
    jbyte*       entry = _ct->byte_for(mr.last());
    const jbyte* first_entry = _ct->byte_for(mr.start());
    while (entry >= first_entry) {
      HeapWord* cur = _ct->addr_for(entry);
      if (!clear_card(entry)) {
        // We hit a clean card; process any non-empty
        // dirty range accumulated so far.
        if (start_of_non_clean < end_of_non_clean) {
          MemRegion mr2(start_of_non_clean, end_of_non_clean);
          _dirty_card_closure->do_MemRegion(mr2);
        }
        // Reset the dirty window while continuing to
        // look for the next dirty window to process.
        end_of_non_clean = cur;
        start_of_non_clean = end_of_non_clean;
      }
      // Open the left end of the window one card to the left.
      start_of_non_clean = cur;
      // Note that "entry" leads "start_of_non_clean" in
      // its leftward excursion after this point
      // in the loop and, when we hit the left end of "mr",
      // will point off of the left end of the card-table
      // for "mr".
      entry--;
    }
    // If the first card of "mr" was dirty, we will have
    // been left with a dirty window, co-initial with "mr",
    // which we now process.
    if (start_of_non_clean < end_of_non_clean) {
      MemRegion mr2(start_of_non_clean, end_of_non_clean);
      _dirty_card_closure->do_MemRegion(mr2);
    }
  }
};
// clean (by dirty->clean before) ==> cur_younger_gen
// dirty                          ==> cur_youngergen_and_prev_nonclean_card
// precleaned                     ==> cur_youngergen_and_prev_nonclean_card
// prev-younger-gen               ==> cur_youngergen_and_prev_nonclean_card
// cur-younger-gen                ==> cur_younger_gen
// cur_youngergen_and_prev_nonclean_card ==> no change.
void CardTableRS::write_ref_field_gc_par(void* field, oop new_val) {
  jbyte* entry = ct_bs()->byte_for(field);
  do {
    jbyte entry_val = *entry;
    // We put this first because it's probably the most common case.
    if (entry_val == clean_card_val()) {
      // No threat of contention with cleaning threads.
      *entry = cur_youngergen_card_val();
      return;
    } else if (card_is_dirty_wrt_gen_iter(entry_val)
               || is_prev_youngergen_card_val(entry_val)) {
      // Mark it as both cur and prev youngergen; card cleaning thread will
      // eventually remove the previous stuff.
      jbyte new_val = cur_youngergen_and_prev_nonclean_card;
      jbyte res = Atomic::cmpxchg(new_val, entry, entry_val);
      // Did the CAS succeed?
      if (res == entry_val) return;
      // Otherwise, retry, to see the new value.
      continue;
    } else {
      assert(entry_val == cur_youngergen_and_prev_nonclean_card
             || entry_val == cur_youngergen_card_val(),
             "should be only possibilities.");
      return;
    }
  } while (true);
}

void CardTableRS::younger_refs_in_space_iterate(Space* sp,
                                                OopsInGenClosure* cl) {
  DirtyCardToOopClosure* dcto_cl = sp->new_dcto_cl(cl, _ct_bs->precision(),
                                                   cl->gen_boundary());
  ClearNoncleanCardWrapper clear_cl(dcto_cl, this);

  _ct_bs->non_clean_card_iterate(sp, sp->used_region_at_save_marks(),
                                dcto_cl, &clear_cl, false);
}

void CardTableRS::clear_into_younger(Generation* gen, bool clear_perm) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  // Generations younger than gen have been evacuated. We can clear
  // card table entries for gen (we know that it has no pointers
  // to younger gens) and for those below. The card tables for
  // the youngest gen need never be cleared, and those for perm gen
  // will be cleared based on the parameter clear_perm.
  // There's a bit of subtlety in the clear() and invalidate()
  // methods that we exploit here and in invalidate_or_clear()
  // below to avoid missing cards at the fringes. If clear() or
  // invalidate() are changed in the future, this code should
  // be revisited. 20040107.ysr
  Generation* g = gen;
  for(Generation* prev_gen = gch->prev_gen(g);
      prev_gen != NULL;
      g = prev_gen, prev_gen = gch->prev_gen(g)) {
    MemRegion to_be_cleared_mr = g->prev_used_region();
    clear(to_be_cleared_mr);
  }
  // Clear perm gen cards if asked to do so.
  if (clear_perm) {
    MemRegion to_be_cleared_mr = gch->perm_gen()->prev_used_region();
    clear(to_be_cleared_mr);
  }
}

void CardTableRS::invalidate_or_clear(Generation* gen, bool younger,
                                      bool perm) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  // For each generation gen (and younger and/or perm)
  // invalidate the cards for the currently occupied part
  // of that generation and clear the cards for the
  // unoccupied part of the generation (if any, making use
  // of that generation's prev_used_region to determine that
  // region). No need to do anything for the youngest
  // generation. Also see note#20040107.ysr above.
  Generation* g = gen;
  for(Generation* prev_gen = gch->prev_gen(g); prev_gen != NULL;
      g = prev_gen, prev_gen = gch->prev_gen(g))  {
    MemRegion used_mr = g->used_region();
    MemRegion to_be_cleared_mr = g->prev_used_region().minus(used_mr);
    if (!to_be_cleared_mr.is_empty()) {
      clear(to_be_cleared_mr);
    }
    invalidate(used_mr);
    if (!younger) break;
  }
  // Clear perm gen cards if asked to do so.
  if (perm) {
    g = gch->perm_gen();
    MemRegion used_mr = g->used_region();
    MemRegion to_be_cleared_mr = g->prev_used_region().minus(used_mr);
    if (!to_be_cleared_mr.is_empty()) {
      clear(to_be_cleared_mr);
    }
    invalidate(used_mr);
  }
}


class VerifyCleanCardClosure: public OopClosure {
private:
  HeapWord* _boundary;
  HeapWord* _begin;
  HeapWord* _end;
protected:
  template <class T> void do_oop_work(T* p) {
    HeapWord* jp = (HeapWord*)p;
    if (jp >= _begin && jp < _end) {
      oop obj = oopDesc::load_decode_heap_oop(p);
      guarantee(obj == NULL ||
                (HeapWord*)p < _boundary ||
                (HeapWord*)obj >= _boundary,
                "pointer on clean card crosses boundary");
    }
  }
public:
  VerifyCleanCardClosure(HeapWord* b, HeapWord* begin, HeapWord* end) :
    _boundary(b), _begin(begin), _end(end) {}
  virtual void do_oop(oop* p)       { VerifyCleanCardClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { VerifyCleanCardClosure::do_oop_work(p); }
};

class VerifyCTSpaceClosure: public SpaceClosure {
private:
  CardTableRS* _ct;
  HeapWord* _boundary;
public:
  VerifyCTSpaceClosure(CardTableRS* ct, HeapWord* boundary) :
    _ct(ct), _boundary(boundary) {}
  virtual void do_space(Space* s) { _ct->verify_space(s, _boundary); }
};

class VerifyCTGenClosure: public GenCollectedHeap::GenClosure {
  CardTableRS* _ct;
public:
  VerifyCTGenClosure(CardTableRS* ct) : _ct(ct) {}
  void do_generation(Generation* gen) {
    // Skip the youngest generation.
    if (gen->level() == 0) return;
    // Normally, we're interested in pointers to younger generations.
    VerifyCTSpaceClosure blk(_ct, gen->reserved().start());
    gen->space_iterate(&blk, true);
  }
};

void CardTableRS::verify_space(Space* s, HeapWord* gen_boundary) {
  // We don't need to do young-gen spaces.
  if (s->end() <= gen_boundary) return;
  MemRegion used = s->used_region();

  jbyte* cur_entry = byte_for(used.start());
  jbyte* limit = byte_after(used.last());
  while (cur_entry < limit) {
    if (*cur_entry == CardTableModRefBS::clean_card) {
      jbyte* first_dirty = cur_entry+1;
      while (first_dirty < limit &&
             *first_dirty == CardTableModRefBS::clean_card) {
        first_dirty++;
      }
      // If the first object is a regular object, and it has a
      // young-to-old field, that would mark the previous card.
      HeapWord* boundary = addr_for(cur_entry);
      HeapWord* end = (first_dirty >= limit) ? used.end() : addr_for(first_dirty);
      HeapWord* boundary_block = s->block_start(boundary);
      HeapWord* begin = boundary;             // Until proven otherwise.
      HeapWord* start_block = boundary_block; // Until proven otherwise.
      if (boundary_block < boundary) {
        if (s->block_is_obj(boundary_block) && s->obj_is_alive(boundary_block)) {
          oop boundary_obj = oop(boundary_block);
          if (!boundary_obj->is_objArray() &&
              !boundary_obj->is_typeArray()) {
            guarantee(cur_entry > byte_for(used.start()),
                      "else boundary would be boundary_block");
            if (*byte_for(boundary_block) != CardTableModRefBS::clean_card) {
              begin = boundary_block + s->block_size(boundary_block);
              start_block = begin;
            }
          }
        }
      }
      // Now traverse objects until end.
      HeapWord* cur = start_block;
      VerifyCleanCardClosure verify_blk(gen_boundary, begin, end);
      while (cur < end) {
        if (s->block_is_obj(cur) && s->obj_is_alive(cur)) {
          oop(cur)->oop_iterate(&verify_blk);
        }
        cur += s->block_size(cur);
      }
      cur_entry = first_dirty;
    } else {
      // We'd normally expect that cur_youngergen_and_prev_nonclean_card
      // is a transient value, that cannot be in the card table
      // except during GC, and thus assert that:
      // guarantee(*cur_entry != cur_youngergen_and_prev_nonclean_card,
      //        "Illegal CT value");
      // That however, need not hold, as will become clear in the
      // following...

      // We'd normally expect that if we are in the parallel case,
      // we can't have left a prev value (which would be different
      // from the current value) in the card table, and so we'd like to
      // assert that:
      // guarantee(cur_youngergen_card_val() == youngergen_card
      //           || !is_prev_youngergen_card_val(*cur_entry),
      //           "Illegal CT value");
      // That, however, may not hold occasionally, because of
      // CMS or MSC in the old gen. To wit, consider the
      // following two simple illustrative scenarios:
      // (a) CMS: Consider the case where a large object L
      //     spanning several cards is allocated in the old
      //     gen, and has a young gen reference stored in it, dirtying
      //     some interior cards. A young collection scans the card,
      //     finds a young ref and installs a youngergenP_n value.
      //     L then goes dead. Now a CMS collection starts,
      //     finds L dead and sweeps it up. Assume that L is
      //     abutting _unallocated_blk, so _unallocated_blk is
      //     adjusted down to (below) L. Assume further that
      //     no young collection intervenes during this CMS cycle.
      //     The next young gen cycle will not get to look at this
      //     youngergenP_n card since it lies in the unoccupied
      //     part of the space.
      //     Some young collections later the blocks on this
      //     card can be re-allocated either due to direct allocation
      //     or due to absorbing promotions. At this time, the
      //     before-gc verification will fail the above assert.
      // (b) MSC: In this case, an object L with a young reference
      //     is on a card that (therefore) holds a youngergen_n value.
      //     Suppose also that L lies towards the end of the used
      //     the used space before GC. An MSC collection
      //     occurs that compacts to such an extent that this
      //     card is no longer in the occupied part of the space.
      //     Since current code in MSC does not always clear cards
      //     in the unused part of old gen, this stale youngergen_n
      //     value is left behind and can later be covered by
      //     an object when promotion or direct allocation
      //     re-allocates that part of the heap.
      //
      // Fortunately, the presence of such stale card values is
      // "only" a minor annoyance in that subsequent young collections
      // might needlessly scan such cards, but would still never corrupt
      // the heap as a result. However, it's likely not to be a significant
      // performance inhibitor in practice. For instance,
      // some recent measurements with unoccupied cards eagerly cleared
      // out to maintain this invariant, showed next to no
      // change in young collection times; of course one can construct
      // degenerate examples where the cost can be significant.)
      // Note, in particular, that if the "stale" card is modified
      // after re-allocation, it would be dirty, not "stale". Thus,
      // we can never have a younger ref in such a card and it is
      // safe not to scan that card in any collection. [As we see
      // below, we do some unnecessary scanning
      // in some cases in the current parallel scanning algorithm.]
      //
      // The main point below is that the parallel card scanning code
      // deals correctly with these stale card values. There are two main
      // cases to consider where we have a stale "younger gen" value and a
      // "derivative" case to consider, where we have a stale
      // "cur_younger_gen_and_prev_non_clean" value, as will become
      // apparent in the case analysis below.
      // o Case 1. If the stale value corresponds to a younger_gen_n
      //   value other than the cur_younger_gen value then the code
      //   treats this as being tantamount to a prev_younger_gen
      //   card. This means that the card may be unnecessarily scanned.
      //   There are two sub-cases to consider:
      //   o Case 1a. Let us say that the card is in the occupied part
      //     of the generation at the time the collection begins. In
      //     that case the card will be either cleared when it is scanned
      //     for young pointers, or will be set to cur_younger_gen as a
      //     result of promotion. (We have elided the normal case where
      //     the scanning thread and the promoting thread interleave
      //     possibly resulting in a transient
      //     cur_younger_gen_and_prev_non_clean value before settling
      //     to cur_younger_gen. [End Case 1a.]
      //   o Case 1b. Consider now the case when the card is in the unoccupied
      //     part of the space which becomes occupied because of promotions
      //     into it during the current young GC. In this case the card
      //     will never be scanned for young references. The current
      //     code will set the card value to either
      //     cur_younger_gen_and_prev_non_clean or leave
      //     it with its stale value -- because the promotions didn't
      //     result in any younger refs on that card. Of these two
      //     cases, the latter will be covered in Case 1a during
      //     a subsequent scan. To deal with the former case, we need
      //     to further consider how we deal with a stale value of
      //     cur_younger_gen_and_prev_non_clean in our case analysis
      //     below. This we do in Case 3 below. [End Case 1b]
      //   [End Case 1]
      // o Case 2. If the stale value corresponds to cur_younger_gen being
      //   a value not necessarily written by a current promotion, the
      //   card will not be scanned by the younger refs scanning code.
      //   (This is OK since as we argued above such cards cannot contain
      //   any younger refs.) The result is that this value will be
      //   treated as a prev_younger_gen value in a subsequent collection,
      //   which is addressed in Case 1 above. [End Case 2]
      // o Case 3. We here consider the "derivative" case from Case 1b. above
      //   because of which we may find a stale
      //   cur_younger_gen_and_prev_non_clean card value in the table.
      //   Once again, as in Case 1, we consider two subcases, depending
      //   on whether the card lies in the occupied or unoccupied part
      //   of the space at the start of the young collection.
      //   o Case 3a. Let us say the card is in the occupied part of
      //     the old gen at the start of the young collection. In that
      //     case, the card will be scanned by the younger refs scanning
      //     code which will set it to cur_younger_gen. In a subsequent
      //     scan, the card will be considered again and get its final
      //     correct value. [End Case 3a]
      //   o Case 3b. Now consider the case where the card is in the
      //     unoccupied part of the old gen, and is occupied as a result
      //     of promotions during thus young gc. In that case,
      //     the card will not be scanned for younger refs. The presence
      //     of newly promoted objects on the card will then result in
      //     its keeping the value cur_younger_gen_and_prev_non_clean
      //     value, which we have dealt with in Case 3 here. [End Case 3b]
      //   [End Case 3]
      //
      // (Please refer to the code in the helper class
      // ClearNonCleanCardWrapper and in CardTableModRefBS for details.)
      //
      // The informal arguments above can be tightened into a formal
      // correctness proof and it behooves us to write up such a proof,
      // or to use model checking to prove that there are no lingering
      // concerns.
      //
      // Clearly because of Case 3b one cannot bound the time for
      // which a card will retain what we have called a "stale" value.
      // However, one can obtain a Loose upper bound on the redundant
      // work as a result of such stale values. Note first that any
      // time a stale card lies in the occupied part of the space at
      // the start of the collection, it is scanned by younger refs
      // code and we can define a rank function on card values that
      // declines when this is so. Note also that when a card does not
      // lie in the occupied part of the space at the beginning of a
      // young collection, its rank can either decline or stay unchanged.
      // In this case, no extra work is done in terms of redundant
      // younger refs scanning of that card.
      // Then, the case analysis above reveals that, in the worst case,
      // any such stale card will be scanned unnecessarily at most twice.
      //
      // It is nonethelss advisable to try and get rid of some of this
      // redundant work in a subsequent (low priority) re-design of
      // the card-scanning code, if only to simplify the underlying
      // state machine analysis/proof. ysr 1/28/2002. XXX
      cur_entry++;
    }
  }
}

void CardTableRS::verify() {
  // At present, we only know how to verify the card table RS for
  // generational heaps.
  VerifyCTGenClosure blk(this);
  CollectedHeap* ch = Universe::heap();
  // We will do the perm-gen portion of the card table, too.
  Generation* pg = SharedHeap::heap()->perm_gen();
  HeapWord* pg_boundary = pg->reserved().start();

  if (ch->kind() == CollectedHeap::GenCollectedHeap) {
    GenCollectedHeap::heap()->generation_iterate(&blk, false);
    _ct_bs->verify();

    // If the old gen collections also collect perm, then we are only
    // interested in perm-to-young pointers, not perm-to-old pointers.
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    CollectorPolicy* cp = gch->collector_policy();
    if (cp->is_mark_sweep_policy() || cp->is_concurrent_mark_sweep_policy()) {
      pg_boundary = gch->get_gen(1)->reserved().start();
    }
  }
  VerifyCTSpaceClosure perm_space_blk(this, pg_boundary);
  SharedHeap::heap()->perm_gen()->space_iterate(&perm_space_blk, true);
}


void CardTableRS::verify_aligned_region_empty(MemRegion mr) {
  if (!mr.is_empty()) {
    jbyte* cur_entry = byte_for(mr.start());
    jbyte* limit = byte_after(mr.last());
    // The region mr may not start on a card boundary so
    // the first card may reflect a write to the space
    // just prior to mr.
    if (!is_aligned(mr.start())) {
      cur_entry++;
    }
    for (;cur_entry < limit; cur_entry++) {
      guarantee(*cur_entry == CardTableModRefBS::clean_card,
                "Unexpected dirty card found");
    }
  }
}
