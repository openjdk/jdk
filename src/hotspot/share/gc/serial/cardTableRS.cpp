/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"

// A dirty card to oop closure for contiguous spaces (ContiguousSpace and
// sub-classes). It knows how to filter out objects that are outside of the
// _boundary.
// (Note that because of the imprecise nature of the write barrier, this may
// iterate over oops beyond the region.)
//
// Assumptions:
// 1. That the actual top of any area in a memory region
//    contained by the space is bounded by the end of the contiguous
//    region of the space.
// 2. That the space is really made up of objects and not just
//    blocks.

class DirtyCardToOopClosure: public MemRegionClosure {
protected:
  OopIterateClosure* _cl;
  Space* _sp;
  HeapWord* _min_done;          // Need a downwards traversal to compensate
                                // imprecise write barrier; this is the
                                // lowest location already done (or,
                                // alternatively, the lowest address that
                                // shouldn't be done again.  null means infinity.)
  NOT_PRODUCT(HeapWord* _last_bottom;)

  // Get the actual top of the area on which the closure will
  // operate, given where the top is assumed to be (the end of the
  // memory region passed to do_MemRegion) and where the object
  // at the top is assumed to start. For example, an object may
  // start at the top but actually extend past the assumed top,
  // in which case the top becomes the end of the object.
  HeapWord* get_actual_top(HeapWord* top, HeapWord* top_obj);

  // Walk the given memory region from bottom to (actual) top
  // looking for objects and applying the oop closure (_cl) to
  // them. The base implementation of this treats the area as
  // blocks, where a block may or may not be an object. Sub-
  // classes should override this to provide more accurate
  // or possibly more efficient walking.
  void walk_mem_region(MemRegion mr, HeapWord* bottom, HeapWord* top);

  // Walk the given memory region, from bottom to top, applying
  // the given oop closure to (possibly) all objects found. The
  // given oop closure may or may not be the same as the oop
  // closure with which this closure was created, as it may
  // be a filtering closure which makes use of the _boundary.
  // We offer two signatures, so the FilteringClosure static type is
  // apparent.
  void walk_mem_region_with_cl(MemRegion mr,
                               HeapWord* bottom, HeapWord* top,
                               OopIterateClosure* cl);
public:
  DirtyCardToOopClosure(Space* sp, OopIterateClosure* cl) :
    _cl(cl), _sp(sp), _min_done(nullptr) {
    NOT_PRODUCT(_last_bottom = nullptr);
  }

  void do_MemRegion(MemRegion mr) override;
};

HeapWord* DirtyCardToOopClosure::get_actual_top(HeapWord* top,
                                                HeapWord* top_obj) {
  if (top_obj != nullptr && top_obj < (_sp->toContiguousSpace())->top()) {
    if (cast_to_oop(top_obj)->is_objArray() || cast_to_oop(top_obj)->is_typeArray()) {
      // An arrayOop is starting on the dirty card - since we do exact
      // store checks for objArrays we are done.
    } else {
      // Otherwise, it is possible that the object starting on the dirty
      // card spans the entire card, and that the store happened on a
      // later card.  Figure out where the object ends.
      assert(_sp->block_size(top_obj) == cast_to_oop(top_obj)->size(),
             "Block size and object size mismatch");
      top = top_obj + cast_to_oop(top_obj)->size();
    }
  } else {
    top = (_sp->toContiguousSpace())->top();
  }
  return top;
}

void DirtyCardToOopClosure::walk_mem_region(MemRegion mr,
                                            HeapWord* bottom,
                                            HeapWord* top) {
  // Note that this assumption won't hold if we have a concurrent
  // collector in this space, which may have freed up objects after
  // they were dirtied and before the stop-the-world GC that is
  // examining cards here.
  assert(bottom < top, "ought to be at least one obj on a dirty card.");

  walk_mem_region_with_cl(mr, bottom, top, _cl);
}

// We get called with "mr" representing the dirty region
// that we want to process. Because of imprecise marking,
// we may need to extend the incoming "mr" to the right,
// and scan more. However, because we may already have
// scanned some of that extended region, we may need to
// trim its right-end back some so we do not scan what
// we (or another worker thread) may already have scanned
// or planning to scan.
void DirtyCardToOopClosure::do_MemRegion(MemRegion mr) {
  HeapWord* bottom = mr.start();
  HeapWord* last = mr.last();
  HeapWord* top = mr.end();
  HeapWord* bottom_obj;
  HeapWord* top_obj;

  assert(_last_bottom == nullptr || top <= _last_bottom,
         "Not decreasing");
  NOT_PRODUCT(_last_bottom = mr.start());

  bottom_obj = _sp->block_start(bottom);
  top_obj    = _sp->block_start(last);

  assert(bottom_obj <= bottom, "just checking");
  assert(top_obj    <= top,    "just checking");

  // Given what we think is the top of the memory region and
  // the start of the object at the top, get the actual
  // value of the top.
  top = get_actual_top(top, top_obj);

  // If the previous call did some part of this region, don't redo.
  if (_min_done != nullptr && _min_done < top) {
    top = _min_done;
  }

  // Top may have been reset, and in fact may be below bottom,
  // e.g. the dirty card region is entirely in a now free object
  // -- something that could happen with a concurrent sweeper.
  bottom = MIN2(bottom, top);
  MemRegion extended_mr = MemRegion(bottom, top);
  assert(bottom <= top &&
         (_min_done == nullptr || top <= _min_done),
         "overlap!");

  // Walk the region if it is not empty; otherwise there is nothing to do.
  if (!extended_mr.is_empty()) {
    walk_mem_region(extended_mr, bottom_obj, top);
  }

  _min_done = bottom;
}

void DirtyCardToOopClosure::walk_mem_region_with_cl(MemRegion mr,
                                                    HeapWord* bottom,
                                                    HeapWord* top,
                                                    OopIterateClosure* cl) {
  bottom += cast_to_oop(bottom)->oop_iterate_size(cl, mr);
  if (bottom < top) {
    HeapWord* next_obj = bottom + cast_to_oop(bottom)->size();
    while (next_obj < top) {
      /* Bottom lies entirely below top, so we can call the */
      /* non-memRegion version of oop_iterate below. */
      cast_to_oop(bottom)->oop_iterate(cl);
      bottom = next_obj;
      next_obj = bottom + cast_to_oop(bottom)->size();
    }
    /* Last object. */
    cast_to_oop(bottom)->oop_iterate(cl, mr);
  }
}

class ClearNoncleanCardWrapper: public MemRegionClosure {
  DirtyCardToOopClosure* _dirty_card_closure;
  CardTableRS* _ct;

public:

  typedef CardTable::CardValue CardValue;
private:
  // Clears the given card, return true if the corresponding card should be
  // processed.
  inline bool clear_card(CardValue* entry);
  // check alignment of pointer
  bool is_word_aligned(CardValue* entry);

public:
  ClearNoncleanCardWrapper(DirtyCardToOopClosure* dirty_card_closure, CardTableRS* ct);
  void do_MemRegion(MemRegion mr) override;
};

inline bool ClearNoncleanCardWrapper::clear_card(CardValue* entry) {
  assert(*entry == CardTableRS::dirty_card_val(), "Only look at dirty cards.");
  *entry = CardTableRS::clean_card_val();
  return true;
}

ClearNoncleanCardWrapper::ClearNoncleanCardWrapper(
  DirtyCardToOopClosure* dirty_card_closure, CardTableRS* ct) :
    _dirty_card_closure(dirty_card_closure), _ct(ct) {
}

bool ClearNoncleanCardWrapper::is_word_aligned(CardTable::CardValue* entry) {
  return (((intptr_t)entry) & (BytesPerWord-1)) == 0;
}

// The regions are visited in *decreasing* address order.
// This order aids with imprecise card marking, where a dirty
// card may cause scanning, and summarization marking, of objects
// that extend onto subsequent cards.
void ClearNoncleanCardWrapper::do_MemRegion(MemRegion mr) {
  assert(mr.word_size() > 0, "Error");
  assert(_ct->is_aligned(mr.start()), "mr.start() should be card aligned");
  // mr.end() may not necessarily be card aligned.
  CardValue* cur_entry = _ct->byte_for(mr.last());
  const CardValue* limit = _ct->byte_for(mr.start());
  HeapWord* end_of_non_clean = mr.end();
  HeapWord* start_of_non_clean = end_of_non_clean;
  while (cur_entry >= limit) {
    HeapWord* cur_hw = _ct->addr_for(cur_entry);
    if ((*cur_entry != CardTableRS::clean_card_val()) && clear_card(cur_entry)) {
      // Continue the dirty range by opening the
      // dirty window one card to the left.
      start_of_non_clean = cur_hw;
    } else {
      // We hit a "clean" card; process any non-empty
      // "dirty" range accumulated so far.
      if (start_of_non_clean < end_of_non_clean) {
        const MemRegion mrd(start_of_non_clean, end_of_non_clean);
        _dirty_card_closure->do_MemRegion(mrd);
      }

      // fast forward through potential continuous whole-word range of clean cards beginning at a word-boundary
      if (is_word_aligned(cur_entry)) {
        CardValue* cur_row = cur_entry - BytesPerWord;
        while (cur_row >= limit && *((intptr_t*)cur_row) ==  CardTableRS::clean_card_row_val()) {
          cur_row -= BytesPerWord;
        }
        cur_entry = cur_row + BytesPerWord;
        cur_hw = _ct->addr_for(cur_entry);
      }

      // Reset the dirty window, while continuing to look
      // for the next dirty card that will start a
      // new dirty window.
      end_of_non_clean = cur_hw;
      start_of_non_clean = cur_hw;
    }
    // Note that "cur_entry" leads "start_of_non_clean" in
    // its leftward excursion after this point
    // in the loop and, when we hit the left end of "mr",
    // will point off of the left end of the card-table
    // for "mr".
    cur_entry--;
  }
  // If the first card of "mr" was dirty, we will have
  // been left with a dirty window, co-initial with "mr",
  // which we now process.
  if (start_of_non_clean < end_of_non_clean) {
    const MemRegion mrd(start_of_non_clean, end_of_non_clean);
    _dirty_card_closure->do_MemRegion(mrd);
  }
}

void CardTableRS::younger_refs_in_space_iterate(TenuredSpace* sp,
                                                OopIterateClosure* cl) {
  verify_used_region_at_save_marks(sp);

  const MemRegion urasm = sp->used_region_at_save_marks();
  non_clean_card_iterate(sp, urasm, cl, this);
}

#ifdef ASSERT
void CardTableRS::verify_used_region_at_save_marks(Space* sp) const {
  MemRegion ur    = sp->used_region();
  MemRegion urasm = sp->used_region_at_save_marks();

  assert(ur.contains(urasm),
         "Did you forget to call save_marks()? "
         "[" PTR_FORMAT ", " PTR_FORMAT ") is not contained in "
         "[" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(urasm.start()), p2i(urasm.end()), p2i(ur.start()), p2i(ur.end()));
}
#endif

void CardTableRS::maintain_old_to_young_invariant(Generation* old_gen, bool is_young_gen_empty) {
  assert(SerialHeap::heap()->is_old_gen(old_gen), "precondition");

  if (is_young_gen_empty) {
    clear_MemRegion(old_gen->prev_used_region());
  } else {
    MemRegion used_mr = old_gen->used_region();
    MemRegion prev_used_mr = old_gen->prev_used_region();
    if (used_mr.end() < prev_used_mr.end()) {
      // Shrunk; need to clear the previously-used but now-unused parts.
      clear_MemRegion(MemRegion(used_mr.end(), prev_used_mr.end()));
    }
    // No idea which card contains old-to-young pointer, so dirtying cards for
    // the entire used part of old-gen conservatively.
    dirty_MemRegion(used_mr);
  }
}

class VerifyCleanCardClosure: public BasicOopIterateClosure {
private:
  HeapWord* _boundary;
  HeapWord* _begin;
  HeapWord* _end;
protected:
  template <class T> void do_oop_work(T* p) {
    HeapWord* jp = (HeapWord*)p;
    assert(jp >= _begin && jp < _end,
           "Error: jp " PTR_FORMAT " should be within "
           "[_begin, _end) = [" PTR_FORMAT "," PTR_FORMAT ")",
           p2i(jp), p2i(_begin), p2i(_end));
    oop obj = RawAccess<>::oop_load(p);
    guarantee(obj == nullptr || cast_from_oop<HeapWord*>(obj) >= _boundary,
              "pointer " PTR_FORMAT " at " PTR_FORMAT " on "
              "clean card crosses boundary" PTR_FORMAT,
              p2i(obj), p2i(jp), p2i(_boundary));
  }

public:
  VerifyCleanCardClosure(HeapWord* b, HeapWord* begin, HeapWord* end) :
    _boundary(b), _begin(begin), _end(end) {
    assert(b <= begin,
           "Error: boundary " PTR_FORMAT " should be at or below begin " PTR_FORMAT,
           p2i(b), p2i(begin));
    assert(begin <= end,
           "Error: begin " PTR_FORMAT " should be strictly below end " PTR_FORMAT,
           p2i(begin), p2i(end));
  }

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

class VerifyCTGenClosure: public SerialHeap::GenClosure {
  CardTableRS* _ct;
public:
  VerifyCTGenClosure(CardTableRS* ct) : _ct(ct) {}
  void do_generation(Generation* gen) {
    // Skip the youngest generation.
    if (SerialHeap::heap()->is_young_gen(gen)) {
      return;
    }
    // Normally, we're interested in pointers to younger generations.
    VerifyCTSpaceClosure blk(_ct, gen->reserved().start());
    gen->space_iterate(&blk, true);
  }
};

void CardTableRS::verify_space(Space* s, HeapWord* gen_boundary) {
  // We don't need to do young-gen spaces.
  if (s->end() <= gen_boundary) return;
  MemRegion used = s->used_region();

  CardValue* cur_entry = byte_for(used.start());
  CardValue* limit = byte_after(used.last());
  while (cur_entry < limit) {
    if (*cur_entry == clean_card_val()) {
      CardValue* first_dirty = cur_entry+1;
      while (first_dirty < limit &&
             *first_dirty == clean_card_val()) {
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
          oop boundary_obj = cast_to_oop(boundary_block);
          if (!boundary_obj->is_objArray() &&
              !boundary_obj->is_typeArray()) {
            guarantee(cur_entry > byte_for(used.start()),
                      "else boundary would be boundary_block");
            if (*byte_for(boundary_block) != clean_card_val()) {
              begin = boundary_block + s->block_size(boundary_block);
              start_block = begin;
            }
          }
        }
      }
      // Now traverse objects until end.
      if (begin < end) {
        MemRegion mr(begin, end);
        VerifyCleanCardClosure verify_blk(gen_boundary, begin, end);
        for (HeapWord* cur = start_block; cur < end; cur += s->block_size(cur)) {
          if (s->block_is_obj(cur) && s->obj_is_alive(cur)) {
            cast_to_oop(cur)->oop_iterate(&verify_blk, mr);
          }
        }
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
      // cases to consider where we have a stale "young gen" value and a
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
      // ClearNonCleanCardWrapper and in CardTable for details.)
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
      // It is nonetheless advisable to try and get rid of some of this
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
  SerialHeap::heap()->generation_iterate(&blk, false);
}

CardTableRS::CardTableRS(MemRegion whole_heap) :
  CardTable(whole_heap) { }

void CardTableRS::non_clean_card_iterate(TenuredSpace* sp,
                                         MemRegion mr,
                                         OopIterateClosure* cl,
                                         CardTableRS* ct)
{
  if (mr.is_empty()) {
    return;
  }
  // clear_cl finds contiguous dirty ranges of cards to process and clear.

  DirtyCardToOopClosure dcto_cl{sp, cl};
  ClearNoncleanCardWrapper clear_cl(&dcto_cl, ct);

  clear_cl.do_MemRegion(mr);
}

bool CardTableRS::is_in_young(const void* p) const {
  return SerialHeap::heap()->is_in_young(p);
}
