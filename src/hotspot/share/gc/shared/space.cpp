/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/blockOffsetTable.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/genOopClosures.inline.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/spaceDecorator.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/java.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_SERIALGC
#include "gc/serial/defNewGeneration.hpp"
#endif

HeapWord* DirtyCardToOopClosure::get_actual_top(HeapWord* top,
                                                HeapWord* top_obj) {
  if (top_obj != NULL) {
    if (_sp->block_is_obj(top_obj)) {
      if (_precision == CardTable::ObjHeadPreciseArray) {
        if (cast_to_oop(top_obj)->is_objArray() || cast_to_oop(top_obj)->is_typeArray()) {
          // An arrayOop is starting on the dirty card - since we do exact
          // store checks for objArrays we are done.
        } else {
          // Otherwise, it is possible that the object starting on the dirty
          // card spans the entire card, and that the store happened on a
          // later card.  Figure out where the object ends.
          // Use the block_size() method of the space over which
          // the iteration is being done.  That space (e.g. CMS) may have
          // specific requirements on object sizes which will
          // be reflected in the block_size() method.
          top = top_obj + cast_to_oop(top_obj)->size();
        }
      }
    } else {
      top = top_obj;
    }
  } else {
    assert(top == _sp->end(), "only case where top_obj == NULL");
  }
  return top;
}

void DirtyCardToOopClosure::walk_mem_region(MemRegion mr,
                                            HeapWord* bottom,
                                            HeapWord* top) {
  // 1. Blocks may or may not be objects.
  // 2. Even when a block_is_obj(), it may not entirely
  //    occupy the block if the block quantum is larger than
  //    the object size.
  // We can and should try to optimize by calling the non-MemRegion
  // version of oop_iterate() for all but the extremal objects
  // (for which we need to call the MemRegion version of
  // oop_iterate()) To be done post-beta XXX
  for (; bottom < top; bottom += _sp->block_size(bottom)) {
    // As in the case of contiguous space above, we'd like to
    // just use the value returned by oop_iterate to increment the
    // current pointer; unfortunately, that won't work in CMS because
    // we'd need an interface change (it seems) to have the space
    // "adjust the object size" (for instance pad it up to its
    // block alignment or minimum block size restrictions. XXX
    if (_sp->block_is_obj(bottom) &&
        !_sp->obj_allocated_since_save_marks(cast_to_oop(bottom))) {
      cast_to_oop(bottom)->oop_iterate(_cl, mr);
    }
  }
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

  assert(_precision == CardTable::ObjHeadPreciseArray ||
         _precision == CardTable::Precise,
         "Only ones we deal with for now.");

  assert(_precision != CardTable::ObjHeadPreciseArray ||
         _last_bottom == NULL || top <= _last_bottom,
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
  if (_precision == CardTable::ObjHeadPreciseArray &&
      _min_done != NULL &&
      _min_done < top) {
    top = _min_done;
  }

  // Top may have been reset, and in fact may be below bottom,
  // e.g. the dirty card region is entirely in a now free object
  // -- something that could happen with a concurrent sweeper.
  bottom = MIN2(bottom, top);
  MemRegion extended_mr = MemRegion(bottom, top);
  assert(bottom <= top &&
         (_precision != CardTable::ObjHeadPreciseArray ||
          _min_done == NULL ||
          top <= _min_done),
         "overlap!");

  // Walk the region if it is not empty; otherwise there is nothing to do.
  if (!extended_mr.is_empty()) {
    walk_mem_region(extended_mr, bottom_obj, top);
  }

  _min_done = bottom;
}

DirtyCardToOopClosure* Space::new_dcto_cl(OopIterateClosure* cl,
                                          CardTable::PrecisionStyle precision,
                                          HeapWord* boundary) {
  return new DirtyCardToOopClosure(this, cl, precision, boundary);
}

HeapWord* ContiguousSpaceDCTOC::get_actual_top(HeapWord* top,
                                               HeapWord* top_obj) {
  if (top_obj != NULL && top_obj < (_sp->toContiguousSpace())->top()) {
    if (_precision == CardTable::ObjHeadPreciseArray) {
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
    }
  } else {
    top = (_sp->toContiguousSpace())->top();
  }
  return top;
}

void FilteringDCTOC::walk_mem_region(MemRegion mr,
                                     HeapWord* bottom,
                                     HeapWord* top) {
  // Note that this assumption won't hold if we have a concurrent
  // collector in this space, which may have freed up objects after
  // they were dirtied and before the stop-the-world GC that is
  // examining cards here.
  assert(bottom < top, "ought to be at least one obj on a dirty card.");

  if (_boundary != NULL) {
    // We have a boundary outside of which we don't want to look
    // at objects, so create a filtering closure around the
    // oop closure before walking the region.
    FilteringClosure filter(_boundary, _cl);
    walk_mem_region_with_cl(mr, bottom, top, &filter);
  } else {
    // No boundary, simply walk the heap with the oop closure.
    walk_mem_region_with_cl(mr, bottom, top, _cl);
  }

}

// We must replicate this so that the static type of "FilteringClosure"
// (see above) is apparent at the oop_iterate calls.
#define ContiguousSpaceDCTOC__walk_mem_region_with_cl_DEFN(ClosureType) \
void ContiguousSpaceDCTOC::walk_mem_region_with_cl(MemRegion mr,        \
                                                   HeapWord* bottom,    \
                                                   HeapWord* top,       \
                                                   ClosureType* cl) {   \
  bottom += cast_to_oop(bottom)->oop_iterate_size(cl, mr);              \
  if (bottom < top) {                                                   \
    HeapWord* next_obj = bottom + cast_to_oop(bottom)->size();          \
    while (next_obj < top) {                                            \
      /* Bottom lies entirely below top, so we can call the */          \
      /* non-memRegion version of oop_iterate below. */                 \
      cast_to_oop(bottom)->oop_iterate(cl);                             \
      bottom = next_obj;                                                \
      next_obj = bottom + cast_to_oop(bottom)->size();                  \
    }                                                                   \
    /* Last object. */                                                  \
    cast_to_oop(bottom)->oop_iterate(cl, mr);                           \
  }                                                                     \
}

// (There are only two of these, rather than N, because the split is due
// only to the introduction of the FilteringClosure, a local part of the
// impl of this abstraction.)
ContiguousSpaceDCTOC__walk_mem_region_with_cl_DEFN(OopIterateClosure)
ContiguousSpaceDCTOC__walk_mem_region_with_cl_DEFN(FilteringClosure)

DirtyCardToOopClosure*
ContiguousSpace::new_dcto_cl(OopIterateClosure* cl,
                             CardTable::PrecisionStyle precision,
                             HeapWord* boundary) {
  return new ContiguousSpaceDCTOC(this, cl, precision, boundary);
}

void Space::initialize(MemRegion mr,
                       bool clear_space,
                       bool mangle_space) {
  HeapWord* bottom = mr.start();
  HeapWord* end    = mr.end();
  assert(Universe::on_page_boundary(bottom) && Universe::on_page_boundary(end),
         "invalid space boundaries");
  set_bottom(bottom);
  set_end(end);
  if (clear_space) clear(mangle_space);
}

void Space::clear(bool mangle_space) {
  if (ZapUnusedHeapArea && mangle_space) {
    mangle_unused_area();
  }
}

ContiguousSpace::ContiguousSpace(): CompactibleSpace(), _top(NULL) {
  _mangler = new GenSpaceMangler(this);
}

ContiguousSpace::~ContiguousSpace() {
  delete _mangler;
}

void ContiguousSpace::initialize(MemRegion mr,
                                 bool clear_space,
                                 bool mangle_space)
{
  CompactibleSpace::initialize(mr, clear_space, mangle_space);
}

void ContiguousSpace::clear(bool mangle_space) {
  set_top(bottom());
  set_saved_mark();
  CompactibleSpace::clear(mangle_space);
}

bool ContiguousSpace::is_free_block(const HeapWord* p) const {
  return p >= _top;
}

void OffsetTableContigSpace::clear(bool mangle_space) {
  ContiguousSpace::clear(mangle_space);
  _offsets.initialize_threshold();
}

void OffsetTableContigSpace::set_bottom(HeapWord* new_bottom) {
  Space::set_bottom(new_bottom);
  _offsets.set_bottom(new_bottom);
}

void OffsetTableContigSpace::set_end(HeapWord* new_end) {
  // Space should not advertise an increase in size
  // until after the underlying offset table has been enlarged.
  _offsets.resize(pointer_delta(new_end, bottom()));
  Space::set_end(new_end);
}

#ifndef PRODUCT

void ContiguousSpace::set_top_for_allocations(HeapWord* v) {
  mangler()->set_top_for_allocations(v);
}
void ContiguousSpace::set_top_for_allocations() {
  mangler()->set_top_for_allocations(top());
}
void ContiguousSpace::check_mangled_unused_area(HeapWord* limit) {
  mangler()->check_mangled_unused_area(limit);
}

void ContiguousSpace::check_mangled_unused_area_complete() {
  mangler()->check_mangled_unused_area_complete();
}

// Mangled only the unused space that has not previously
// been mangled and that has not been allocated since being
// mangled.
void ContiguousSpace::mangle_unused_area() {
  mangler()->mangle_unused_area();
}
void ContiguousSpace::mangle_unused_area_complete() {
  mangler()->mangle_unused_area_complete();
}
#endif  // NOT_PRODUCT

void CompactibleSpace::initialize(MemRegion mr,
                                  bool clear_space,
                                  bool mangle_space) {
  Space::initialize(mr, clear_space, mangle_space);
  set_compaction_top(bottom());
  _next_compaction_space = NULL;
}

void CompactibleSpace::clear(bool mangle_space) {
  Space::clear(mangle_space);
  _compaction_top = bottom();
}

HeapWord* CompactibleSpace::forward(oop q, size_t size,
                                    CompactPoint* cp, HeapWord* compact_top) {
  // q is alive
  // First check if we should switch compaction space
  assert(this == cp->space, "'this' should be current compaction space.");
  size_t compaction_max_size = pointer_delta(end(), compact_top);
  while (size > compaction_max_size) {
    // switch to next compaction space
    cp->space->set_compaction_top(compact_top);
    cp->space = cp->space->next_compaction_space();
    if (cp->space == NULL) {
      cp->gen = GenCollectedHeap::heap()->young_gen();
      assert(cp->gen != NULL, "compaction must succeed");
      cp->space = cp->gen->first_compaction_space();
      assert(cp->space != NULL, "generation must have a first compaction space");
    }
    compact_top = cp->space->bottom();
    cp->space->set_compaction_top(compact_top);
    cp->space->initialize_threshold();
    compaction_max_size = pointer_delta(cp->space->end(), compact_top);
  }

  // store the forwarding pointer into the mark word
  if (cast_from_oop<HeapWord*>(q) != compact_top) {
    q->forward_to(cast_to_oop(compact_top));
    assert(q->is_gc_marked(), "encoding the pointer should preserve the mark");
  } else {
    // if the object isn't moving we can just set the mark to the default
    // mark and handle it specially later on.
    q->init_mark();
    assert(!q->is_forwarded(), "should not be forwarded");
  }

  compact_top += size;

  // We need to update the offset table so that the beginnings of objects can be
  // found during scavenge.  Note that we are updating the offset table based on
  // where the object will be once the compaction phase finishes.
  cp->space->alloc_block(compact_top - size, compact_top);
  return compact_top;
}

#if INCLUDE_SERIALGC

void ContiguousSpace::prepare_for_compaction(CompactPoint* cp) {
  // Compute the new addresses for the live objects and store it in the mark
  // Used by universe::mark_sweep_phase2()

  // We're sure to be here before any objects are compacted into this
  // space, so this is a good time to initialize this:
  set_compaction_top(bottom());

  if (cp->space == NULL) {
    assert(cp->gen != NULL, "need a generation");
    assert(cp->gen->first_compaction_space() == this, "just checking");
    cp->space = cp->gen->first_compaction_space();
    cp->space->initialize_threshold();
    cp->space->set_compaction_top(cp->space->bottom());
  }

  HeapWord* compact_top = cp->space->compaction_top(); // This is where we are currently compacting to.

  DeadSpacer dead_spacer(this);

  HeapWord*  end_of_live = bottom();  // One byte beyond the last byte of the last live object.
  HeapWord*  first_dead = NULL; // The first dead object.

  const intx interval = PrefetchScanIntervalInBytes;

  HeapWord* cur_obj = bottom();
  HeapWord* scan_limit = top();

  while (cur_obj < scan_limit) {
    if (cast_to_oop(cur_obj)->is_gc_marked()) {
      // prefetch beyond cur_obj
      Prefetch::write(cur_obj, interval);
      size_t size = cast_to_oop(cur_obj)->size();
      compact_top = cp->space->forward(cast_to_oop(cur_obj), size, cp, compact_top);
      cur_obj += size;
      end_of_live = cur_obj;
    } else {
      // run over all the contiguous dead objects
      HeapWord* end = cur_obj;
      do {
        // prefetch beyond end
        Prefetch::write(end, interval);
        end += cast_to_oop(end)->size();
      } while (end < scan_limit && !cast_to_oop(end)->is_gc_marked());

      // see if we might want to pretend this object is alive so that
      // we don't have to compact quite as often.
      if (cur_obj == compact_top && dead_spacer.insert_deadspace(cur_obj, end)) {
        oop obj = cast_to_oop(cur_obj);
        compact_top = cp->space->forward(obj, obj->size(), cp, compact_top);
        end_of_live = end;
      } else {
        // otherwise, it really is a free region.

        // cur_obj is a pointer to a dead object. Use this dead memory to store a pointer to the next live object.
        *(HeapWord**)cur_obj = end;

        // see if this is the first dead region.
        if (first_dead == NULL) {
          first_dead = cur_obj;
        }
      }

      // move on to the next object
      cur_obj = end;
    }
  }

  assert(cur_obj == scan_limit, "just checking");
  _end_of_live = end_of_live;
  if (first_dead != NULL) {
    _first_dead = first_dead;
  } else {
    _first_dead = end_of_live;
  }

  // save the compaction_top of the compaction space.
  cp->space->set_compaction_top(compact_top);
}

void CompactibleSpace::adjust_pointers() {
  // Check first is there is any work to do.
  if (used() == 0) {
    return;   // Nothing to do.
  }

  // adjust all the interior pointers to point at the new locations of objects
  // Used by MarkSweep::mark_sweep_phase3()

  HeapWord* cur_obj = bottom();
  HeapWord* const end_of_live = _end_of_live;  // Established by prepare_for_compaction().
  HeapWord* const first_dead = _first_dead;    // Established by prepare_for_compaction().

  assert(first_dead <= end_of_live, "Stands to reason, no?");

  const intx interval = PrefetchScanIntervalInBytes;

  debug_only(HeapWord* prev_obj = NULL);
  while (cur_obj < end_of_live) {
    Prefetch::write(cur_obj, interval);
    if (cur_obj < first_dead || cast_to_oop(cur_obj)->is_gc_marked()) {
      // cur_obj is alive
      // point all the oops to the new location
      size_t size = MarkSweep::adjust_pointers(cast_to_oop(cur_obj));
      debug_only(prev_obj = cur_obj);
      cur_obj += size;
    } else {
      debug_only(prev_obj = cur_obj);
      // cur_obj is not a live object, instead it points at the next live object
      cur_obj = *(HeapWord**)cur_obj;
      assert(cur_obj > prev_obj, "we should be moving forward through memory, cur_obj: " PTR_FORMAT ", prev_obj: " PTR_FORMAT, p2i(cur_obj), p2i(prev_obj));
    }
  }

  assert(cur_obj == end_of_live, "just checking");
}

void CompactibleSpace::compact() {
  // Copy all live objects to their new location
  // Used by MarkSweep::mark_sweep_phase4()

  verify_up_to_first_dead(this);

  HeapWord* const start = bottom();
  HeapWord* const end_of_live = _end_of_live;

  assert(_first_dead <= end_of_live, "Invariant. _first_dead: " PTR_FORMAT " <= end_of_live: " PTR_FORMAT, p2i(_first_dead), p2i(end_of_live));
  if (_first_dead == end_of_live && (start == end_of_live || !cast_to_oop(start)->is_gc_marked())) {
    // Nothing to compact. The space is either empty or all live object should be left in place.
    clear_empty_region(this);
    return;
  }

  const intx scan_interval = PrefetchScanIntervalInBytes;
  const intx copy_interval = PrefetchCopyIntervalInBytes;

  assert(start < end_of_live, "bottom: " PTR_FORMAT " should be < end_of_live: " PTR_FORMAT, p2i(start), p2i(end_of_live));
  HeapWord* cur_obj = start;
  if (_first_dead > cur_obj && !cast_to_oop(cur_obj)->is_gc_marked()) {
    // All object before _first_dead can be skipped. They should not be moved.
    // A pointer to the first live object is stored at the memory location for _first_dead.
    cur_obj = *(HeapWord**)(_first_dead);
  }

  debug_only(HeapWord* prev_obj = NULL);
  while (cur_obj < end_of_live) {
    if (!cast_to_oop(cur_obj)->is_forwarded()) {
      debug_only(prev_obj = cur_obj);
      // The first word of the dead object contains a pointer to the next live object or end of space.
      cur_obj = *(HeapWord**)cur_obj;
      assert(cur_obj > prev_obj, "we should be moving forward through memory");
    } else {
      // prefetch beyond q
      Prefetch::read(cur_obj, scan_interval);

      // size and destination
      size_t size = cast_to_oop(cur_obj)->size();
      HeapWord* compaction_top = cast_from_oop<HeapWord*>(cast_to_oop(cur_obj)->forwardee());

      // prefetch beyond compaction_top
      Prefetch::write(compaction_top, copy_interval);

      // copy object and reinit its mark
      assert(cur_obj != compaction_top, "everything in this pass should be moving");
      Copy::aligned_conjoint_words(cur_obj, compaction_top, size);
      cast_to_oop(compaction_top)->init_mark();
      assert(cast_to_oop(compaction_top)->klass() != NULL, "should have a class");

      debug_only(prev_obj = cur_obj);
      cur_obj += size;
    }
  }

  clear_empty_region(this);
}

#endif // INCLUDE_SERIALGC

void Space::print_short() const { print_short_on(tty); }

void Space::print_short_on(outputStream* st) const {
  st->print(" space " SIZE_FORMAT "K, %3d%% used", capacity() / K,
              (int) ((double) used() * 100 / capacity()));
}

void Space::print() const { print_on(tty); }

void Space::print_on(outputStream* st) const {
  print_short_on(st);
  st->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                p2i(bottom()), p2i(end()));
}

void ContiguousSpace::print_on(outputStream* st) const {
  print_short_on(st);
  st->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                p2i(bottom()), p2i(top()), p2i(end()));
}

void OffsetTableContigSpace::print_on(outputStream* st) const {
  print_short_on(st);
  st->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", "
                INTPTR_FORMAT ", " INTPTR_FORMAT ")",
              p2i(bottom()), p2i(top()), p2i(_offsets.threshold()), p2i(end()));
}

void ContiguousSpace::verify() const {
  HeapWord* p = bottom();
  HeapWord* t = top();
  HeapWord* prev_p = NULL;
  while (p < t) {
    oopDesc::verify(cast_to_oop(p));
    prev_p = p;
    p += cast_to_oop(p)->size();
  }
  guarantee(p == top(), "end of last object must match end of space");
  if (top() != end()) {
    guarantee(top() == block_start_const(end()-1) &&
              top() == block_start_const(top()),
              "top should be start of unallocated block, if it exists");
  }
}

void Space::oop_iterate(OopIterateClosure* blk) {
  ObjectToOopClosure blk2(blk);
  object_iterate(&blk2);
}

bool Space::obj_is_alive(const HeapWord* p) const {
  assert (block_is_obj(p), "The address should point to an object");
  return true;
}

void ContiguousSpace::oop_iterate(OopIterateClosure* blk) {
  if (is_empty()) return;
  HeapWord* obj_addr = bottom();
  HeapWord* t = top();
  // Could call objects iterate, but this is easier.
  while (obj_addr < t) {
    obj_addr += cast_to_oop(obj_addr)->oop_iterate_size(blk);
  }
}

void ContiguousSpace::object_iterate(ObjectClosure* blk) {
  if (is_empty()) return;
  object_iterate_from(bottom(), blk);
}

void ContiguousSpace::object_iterate_from(HeapWord* mark, ObjectClosure* blk) {
  while (mark < top()) {
    blk->do_object(cast_to_oop(mark));
    mark += cast_to_oop(mark)->size();
  }
}

// Very general, slow implementation.
HeapWord* ContiguousSpace::block_start_const(const void* p) const {
  assert(MemRegion(bottom(), end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(bottom()), p2i(end()));
  if (p >= top()) {
    return top();
  } else {
    HeapWord* last = bottom();
    HeapWord* cur = last;
    while (cur <= p) {
      last = cur;
      cur += cast_to_oop(cur)->size();
    }
    assert(oopDesc::is_oop(cast_to_oop(last)), PTR_FORMAT " should be an object start", p2i(last));
    return last;
  }
}

size_t ContiguousSpace::block_size(const HeapWord* p) const {
  assert(MemRegion(bottom(), end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(bottom()), p2i(end()));
  HeapWord* current_top = top();
  assert(p <= current_top,
         "p > current top - p: " PTR_FORMAT ", current top: " PTR_FORMAT,
         p2i(p), p2i(current_top));
  assert(p == current_top || oopDesc::is_oop(cast_to_oop(p)),
         "p (" PTR_FORMAT ") is not a block start - "
         "current_top: " PTR_FORMAT ", is_oop: %s",
         p2i(p), p2i(current_top), BOOL_TO_STR(oopDesc::is_oop(cast_to_oop(p))));
  if (p < current_top) {
    return cast_to_oop(p)->size();
  } else {
    assert(p == current_top, "just checking");
    return pointer_delta(end(), (HeapWord*) p);
  }
}

// This version requires locking.
inline HeapWord* ContiguousSpace::allocate_impl(size_t size) {
  assert(Heap_lock->owned_by_self() ||
         (SafepointSynchronize::is_at_safepoint() && Thread::current()->is_VM_thread()),
         "not locked");
  HeapWord* obj = top();
  if (pointer_delta(end(), obj) >= size) {
    HeapWord* new_top = obj + size;
    set_top(new_top);
    assert(is_aligned(obj) && is_aligned(new_top), "checking alignment");
    return obj;
  } else {
    return NULL;
  }
}

// This version is lock-free.
inline HeapWord* ContiguousSpace::par_allocate_impl(size_t size) {
  do {
    HeapWord* obj = top();
    if (pointer_delta(end(), obj) >= size) {
      HeapWord* new_top = obj + size;
      HeapWord* result = Atomic::cmpxchg(top_addr(), obj, new_top);
      // result can be one of two:
      //  the old top value: the exchange succeeded
      //  otherwise: the new value of the top is returned.
      if (result == obj) {
        assert(is_aligned(obj) && is_aligned(new_top), "checking alignment");
        return obj;
      }
    } else {
      return NULL;
    }
  } while (true);
}

// Requires locking.
HeapWord* ContiguousSpace::allocate(size_t size) {
  return allocate_impl(size);
}

// Lock-free.
HeapWord* ContiguousSpace::par_allocate(size_t size) {
  return par_allocate_impl(size);
}

void OffsetTableContigSpace::initialize_threshold() {
  _offsets.initialize_threshold();
}

void OffsetTableContigSpace::alloc_block(HeapWord* start, HeapWord* end) {
  _offsets.alloc_block(start, end);
}

OffsetTableContigSpace::OffsetTableContigSpace(BlockOffsetSharedArray* sharedOffsetArray,
                                               MemRegion mr) :
  _offsets(sharedOffsetArray, mr),
  _par_alloc_lock(Mutex::safepoint, "OffsetTableContigSpaceParAlloc_lock", true)
{
  _offsets.set_contig_space(this);
  initialize(mr, SpaceDecorator::Clear, SpaceDecorator::Mangle);
}

#define OBJ_SAMPLE_INTERVAL 0
#define BLOCK_SAMPLE_INTERVAL 100

void OffsetTableContigSpace::verify() const {
  HeapWord* p = bottom();
  HeapWord* prev_p = NULL;
  int objs = 0;
  int blocks = 0;

  if (VerifyObjectStartArray) {
    _offsets.verify();
  }

  while (p < top()) {
    size_t size = cast_to_oop(p)->size();
    // For a sampling of objects in the space, find it using the
    // block offset table.
    if (blocks == BLOCK_SAMPLE_INTERVAL) {
      guarantee(p == block_start_const(p + (size/2)),
                "check offset computation");
      blocks = 0;
    } else {
      blocks++;
    }

    if (objs == OBJ_SAMPLE_INTERVAL) {
      oopDesc::verify(cast_to_oop(p));
      objs = 0;
    } else {
      objs++;
    }
    prev_p = p;
    p += size;
  }
  guarantee(p == top(), "end of last object must match end of space");
}


size_t TenuredSpace::allowed_dead_ratio() const {
  return MarkSweepDeadRatio;
}
