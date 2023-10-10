/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
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
#include "gc/serial/serialBlockOffsetTable.inline.hpp"
#include "gc/serial/defNewGeneration.hpp"
#endif

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

ContiguousSpace::ContiguousSpace(): Space(),
  _compaction_top(nullptr),
  _next_compaction_space(nullptr),
  _top(nullptr) {
  _mangler = new GenSpaceMangler(this);
}

ContiguousSpace::~ContiguousSpace() {
  delete _mangler;
}

void ContiguousSpace::initialize(MemRegion mr,
                                 bool clear_space,
                                 bool mangle_space)
{
  Space::initialize(mr, clear_space, mangle_space);
  set_compaction_top(bottom());
  _next_compaction_space = nullptr;
}

void ContiguousSpace::clear(bool mangle_space) {
  set_top(bottom());
  set_saved_mark();
  Space::clear(mangle_space);
  _compaction_top = bottom();
}

bool ContiguousSpace::is_free_block(const HeapWord* p) const {
  return p >= _top;
}

#if INCLUDE_SERIALGC
void TenuredSpace::clear(bool mangle_space) {
  ContiguousSpace::clear(mangle_space);
  _offsets.initialize_threshold();
}

void TenuredSpace::set_bottom(HeapWord* new_bottom) {
  Space::set_bottom(new_bottom);
  _offsets.set_bottom(new_bottom);
}

void TenuredSpace::set_end(HeapWord* new_end) {
  // Space should not advertise an increase in size
  // until after the underlying offset table has been enlarged.
  _offsets.resize(pointer_delta(new_end, bottom()));
  Space::set_end(new_end);
}
#endif // INCLUDE_SERIALGC

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


HeapWord* ContiguousSpace::forward(oop q, size_t size,
                                    CompactPoint* cp, HeapWord* compact_top) {
  // q is alive
  // First check if we should switch compaction space
  assert(this == cp->space, "'this' should be current compaction space.");
  size_t compaction_max_size = pointer_delta(end(), compact_top);
  while (size > compaction_max_size) {
    // switch to next compaction space
    cp->space->set_compaction_top(compact_top);
    cp->space = cp->space->next_compaction_space();
    if (cp->space == nullptr) {
      cp->gen = GenCollectedHeap::heap()->young_gen();
      assert(cp->gen != nullptr, "compaction must succeed");
      cp->space = cp->gen->first_compaction_space();
      assert(cp->space != nullptr, "generation must have a first compaction space");
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

  if (cp->space == nullptr) {
    assert(cp->gen != nullptr, "need a generation");
    assert(cp->gen->first_compaction_space() == this, "just checking");
    cp->space = cp->gen->first_compaction_space();
    cp->space->initialize_threshold();
    cp->space->set_compaction_top(cp->space->bottom());
  }

  HeapWord* compact_top = cp->space->compaction_top(); // This is where we are currently compacting to.

  DeadSpacer dead_spacer(this);

  HeapWord*  end_of_live = bottom();  // One byte beyond the last byte of the last live object.
  HeapWord*  first_dead = nullptr; // The first dead object.

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
        if (first_dead == nullptr) {
          first_dead = cur_obj;
        }
      }

      // move on to the next object
      cur_obj = end;
    }
  }

  assert(cur_obj == scan_limit, "just checking");
  _end_of_live = end_of_live;
  if (first_dead != nullptr) {
    _first_dead = first_dead;
  } else {
    _first_dead = end_of_live;
  }

  // save the compaction_top of the compaction space.
  cp->space->set_compaction_top(compact_top);
}

void ContiguousSpace::adjust_pointers() {
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

  debug_only(HeapWord* prev_obj = nullptr);
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

void ContiguousSpace::compact() {
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

  debug_only(HeapWord* prev_obj = nullptr);
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
      oop new_obj = cast_to_oop(compaction_top);

      ContinuationGCSupport::transform_stack_chunk(new_obj);

      new_obj->init_mark();
      assert(new_obj->klass() != nullptr, "should have a class");

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
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ")",
                p2i(bottom()), p2i(end()));
}

void ContiguousSpace::print_on(outputStream* st) const {
  print_short_on(st);
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
                p2i(bottom()), p2i(top()), p2i(end()));
}

#if INCLUDE_SERIALGC
void TenuredSpace::print_on(outputStream* st) const {
  print_short_on(st);
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", "
                PTR_FORMAT ", " PTR_FORMAT ")",
              p2i(bottom()), p2i(top()), p2i(_offsets.threshold()), p2i(end()));
}
#endif

void ContiguousSpace::verify() const {
  HeapWord* p = bottom();
  HeapWord* t = top();
  HeapWord* prev_p = nullptr;
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

bool Space::obj_is_alive(const HeapWord* p) const {
  assert (block_is_obj(p), "The address should point to an object");
  return true;
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
    return nullptr;
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
      return nullptr;
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

#if INCLUDE_SERIALGC
void TenuredSpace::initialize_threshold() {
  _offsets.initialize_threshold();
}

void TenuredSpace::alloc_block(HeapWord* start, HeapWord* end) {
  _offsets.alloc_block(start, end);
}

TenuredSpace::TenuredSpace(BlockOffsetSharedArray* sharedOffsetArray,
                           MemRegion mr) :
  _offsets(sharedOffsetArray, mr),
  _par_alloc_lock(Mutex::safepoint, "TenuredSpaceParAlloc_lock", true)
{
  _offsets.set_contig_space(this);
  initialize(mr, SpaceDecorator::Clear, SpaceDecorator::Mangle);
}

#define OBJ_SAMPLE_INTERVAL 0
#define BLOCK_SAMPLE_INTERVAL 100

void TenuredSpace::verify() const {
  HeapWord* p = bottom();
  HeapWord* prev_p = nullptr;
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
#endif // INCLUDE_SERIALGC
