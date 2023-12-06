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
#include "gc/serial/generation.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "utilities/align.hpp"

void CardTableRS::younger_refs_in_space_iterate(TenuredSpace* sp,
                                                OopIterateClosure* cl) {
  verify_used_region_at_save_marks(sp);

  const MemRegion urasm = sp->used_region_at_save_marks();
  if (!urasm.is_empty()) {
    non_clean_card_iterate(sp, urasm, cl, this);
  }
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

// Implemented word-iteration to skip long consecutive clean cards.
CardTable::CardValue* CardTableRS::find_first_dirty_card(CardValue* const start_card,
                                                         CardValue* const end_card) {
  using Word = uintptr_t;

  CardValue* current_card = start_card;

  while (!is_aligned(current_card, sizeof(Word))) {
    if (current_card >= end_card) {
      return end_card;
    }
    if (is_dirty(current_card)) {
      return current_card;
    }
    ++current_card;
  }

  // Word comparison
  while (current_card + sizeof(Word) <= end_card) {
    Word* current_word = reinterpret_cast<Word*>(current_card);
    if (*current_word != (Word)clean_card_row_val()) {
      // Found a dirty card in this word; fall back to per-CardValue comparison.
      break;
    }
    current_card += sizeof(Word);
  }

  // Per-CardValue comparison.
  for (/* empty */; current_card < end_card; ++current_card) {
    if (is_dirty(current_card)) {
      return current_card;
    }
  }

  return end_card;
}

// Because non-objArray objs can be imprecisely-marked (only obj-start card is
// dirty instead of the part containing old-to-young pointers), if the
// obj-start of a non-objArray is dirty, all cards that obj completely resides
// on are considered as dirty, since that obj will be iterated (scanned for
// old-to-young pointers) as a whole.
template<typename Func>
CardTable::CardValue* CardTableRS::find_first_clean_card(CardValue* const start_card,
                                                         CardValue* const end_card,
                                                         CardTableRS* ct,
                                                         Func& object_start) {

  // end_card might be just beyond the heap, so need to use the _raw variant.
  HeapWord* end_address = ct->addr_for_raw(end_card);

  for (CardValue* current_card = start_card; current_card < end_card; /* empty */) {
    if (is_dirty(current_card)) {
      current_card++;
      continue;
    }

    // A potential candidate.
    HeapWord* addr = ct->addr_for(current_card);
    HeapWord* obj_start_addr = object_start(addr);

    if (obj_start_addr == addr) {
      return current_card;
    }

    // Final obj in dirty-chunk crosses card-boundary.
    oop obj = cast_to_oop(obj_start_addr);
    if (obj->is_objArray()) {
      // ObjArrays are always precisely-marked so we are not allowed to jump to
      // the end of the current object.
      return current_card;
    }

    // This might be the last object in this area, avoid trying to access the
    // card beyond the allowed area.
    HeapWord* next_address = obj_start_addr + obj->size();
    if (next_address >= end_address) {
      break;
    }

    // Card occupied by next obj.
    CardValue* next_obj_card = ct->byte_for(next_address);
    if (is_clean(next_obj_card)) {
      return next_obj_card;
    }

    // Continue the search after this known-dirty card...
    current_card = next_obj_card + 1;
  }

  return end_card;
}

void CardTableRS::clear_cards(CardValue* start, CardValue* end) {
  size_t num_cards = pointer_delta(end, start, sizeof(CardValue));
  memset(start, clean_card_val(), num_cards);
}

static void prefetch_write(void *p) {
  if (PrefetchScanIntervalInBytes >= 0) {
    Prefetch::write(p, PrefetchScanIntervalInBytes);
  }
}

static void scan_obj_with_limit(oop obj,
                                OopIterateClosure* cl,
                                HeapWord* start,
                                HeapWord* end) {
  if (!obj->is_typeArray()) {
    prefetch_write(start);
    obj->oop_iterate(cl, MemRegion(start, end));
  }
}

void CardTableRS::non_clean_card_iterate(TenuredSpace* sp,
                                         MemRegion mr,
                                         OopIterateClosure* cl,
                                         CardTableRS* ct) {
  struct {
    HeapWord* start_addr;
    HeapWord* end_addr;
  } cached_obj { nullptr, mr.start() };

  auto object_start = [&] (const HeapWord* const addr) {
    if (addr < cached_obj.end_addr) {
      assert(cached_obj.start_addr != nullptr, "inv");
      return cached_obj.start_addr;
    }
    HeapWord* result = sp->block_start_const(addr);

    cached_obj.start_addr = result;
    cached_obj.end_addr = result + cast_to_oop(result)->size();

    return result;
  };

  CardValue* const start_card = ct->byte_for(mr.start());
  CardValue* const end_card = ct->byte_for(mr.last()) + 1;

  // if mr.end() is not card-aligned, that final card should not be cleared
  // because it can be annotated dirty due to old-to-young pointers in
  // newly-promoted objs on that card.
  CardValue* const clear_limit_card = is_card_aligned(mr.end()) ? end_card - 1
                                                                : end_card - 2;

  for (CardValue* current_card = start_card; current_card < end_card; /* empty */) {
    CardValue* const dirty_l = find_first_dirty_card(current_card, end_card);

    if (dirty_l == end_card) {
      // No dirty cards to iterate.
      return;
    }

    HeapWord* const addr_l = ct->addr_for(dirty_l);
    HeapWord* obj_addr = object_start(addr_l);

    CardValue* const dirty_r = find_first_clean_card(dirty_l + 1,
                                                     end_card,
                                                     ct,
                                                     object_start);
    assert(dirty_l < dirty_r, "inv");
    HeapWord* const addr_r = dirty_r == end_card ? mr.end()
                                                 : ct->addr_for(dirty_r);

    clear_cards(MIN2(dirty_l, clear_limit_card),
                MIN2(dirty_r, clear_limit_card));

    while (true) {
      assert(obj_addr < addr_r, "inv");

      oop obj = cast_to_oop(obj_addr);
      const bool is_obj_array = obj->is_objArray();
      HeapWord* const obj_end_addr = obj_addr + obj->size();

      if (is_obj_array) {
        // ObjArrays are always precise-marked.
        scan_obj_with_limit(obj, cl, addr_l, addr_r);
      } else {
        scan_obj_with_limit(obj, cl, addr_l, obj_end_addr);
      }

      if (obj_end_addr >= addr_r) {
        current_card = dirty_r + 1;
        break;
      }

      // Move to next obj inside this dirty chunk.
      obj_addr = obj_end_addr;
    }
  }
}

bool CardTableRS::is_in_young(const void* p) const {
  return SerialHeap::heap()->is_in_young(p);
}
