/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/mutableSpace.hpp"
#include "gc/shared/pretouchTask.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"

MutableSpace::MutableSpace(size_t alignment) :
  _last_setup_region(),
  _alignment(alignment),
  _bottom(nullptr),
  _top(nullptr),
  _end(nullptr)
{
  assert(MutableSpace::alignment() % os::vm_page_size() == 0,
         "Space should be aligned");
}

void MutableSpace::numa_setup_pages(MemRegion mr, size_t page_size, bool clear_space) {
  if (!mr.is_empty()) {
    HeapWord *start = align_up(mr.start(), page_size);
    HeapWord *end =   align_down(mr.end(), page_size);
    if (end > start) {
      size_t size = pointer_delta(end, start, sizeof(char));
      if (clear_space) {
        // Prefer page reallocation to migration.
        os::disclaim_memory((char*)start, size);
      }
      os::numa_make_global((char*)start, size);
    }
  }
}

void MutableSpace::initialize(MemRegion mr,
                              bool clear_space,
                              bool mangle_space,
                              bool setup_pages,
                              WorkerThreads* pretouch_workers) {

  assert(Universe::on_page_boundary(mr.start()) && Universe::on_page_boundary(mr.end()),
         "invalid space boundaries");

  if (setup_pages && (UseNUMA || AlwaysPreTouch)) {
    // The space may move left and right or expand/shrink.
    // We'd like to enforce the desired page placement.
    MemRegion head, tail;
    if (last_setup_region().is_empty()) {
      // If it's the first initialization don't limit the amount of work.
      head = mr;
      tail = MemRegion(mr.end(), mr.end());
    } else {
      // Is there an intersection with the address space?
      MemRegion intersection = last_setup_region().intersection(mr);
      if (intersection.is_empty()) {
        intersection = MemRegion(mr.end(), mr.end());
      }
      // All the sizes below are in words.
      size_t head_size = 0, tail_size = 0;
      if (mr.start() <= intersection.start()) {
        head_size = pointer_delta(intersection.start(), mr.start());
      }
      if(intersection.end() <= mr.end()) {
        tail_size = pointer_delta(mr.end(), intersection.end());
      }
      // Limit the amount of page manipulation if necessary.
      if (NUMASpaceResizeRate > 0 && !AlwaysPreTouch) {
        const size_t change_size = head_size + tail_size;
        const float setup_rate_words = NUMASpaceResizeRate >> LogBytesPerWord;
        head_size = MIN2((size_t)(setup_rate_words * head_size / change_size),
                         head_size);
        tail_size = MIN2((size_t)(setup_rate_words * tail_size / change_size),
                         tail_size);
      }
      head = MemRegion(intersection.start() - head_size, intersection.start());
      tail = MemRegion(intersection.end(), intersection.end() + tail_size);
    }
    assert(mr.contains(head) && mr.contains(tail), "Sanity");

    size_t page_size = alignment();

    if (UseNUMA) {
      numa_setup_pages(head, page_size, clear_space);
      numa_setup_pages(tail, page_size, clear_space);
    }

    if (AlwaysPreTouch) {
      size_t pretouch_page_size = UseLargePages ? page_size : os::vm_page_size();
      PretouchTask::pretouch("ParallelGC PreTouch head", (char*)head.start(), (char*)head.end(),
                             pretouch_page_size, pretouch_workers);

      PretouchTask::pretouch("ParallelGC PreTouch tail", (char*)tail.start(), (char*)tail.end(),
                             pretouch_page_size, pretouch_workers);
    }

    // Remember where we stopped so that we can continue later.
    set_last_setup_region(MemRegion(head.start(), tail.end()));
  }

  set_bottom(mr.start());
  // When expanding concurrently with callers of cas_allocate, setting end
  // makes the new space available for allocation by other threads.  So this
  // assignment must follow all other configuration and initialization that
  // might be done for expansion.
  Atomic::release_store(end_addr(), mr.end());

  if (clear_space) {
    clear(mangle_space);
  }
}

void MutableSpace::clear(bool mangle_space) {
  set_top(bottom());
  if (ZapUnusedHeapArea && mangle_space) {
    mangle_unused_area();
  }
}

#ifndef PRODUCT

void MutableSpace::mangle_unused_area() {
  mangle_region(MemRegion(_top, _end));
}

void MutableSpace::mangle_region(MemRegion mr) {
  SpaceMangler::mangle_region(mr);
}

#endif

HeapWord* MutableSpace::cas_allocate(size_t size) {
  do {
    // Read top before end, else the range check may pass when it shouldn't.
    // If end is read first, other threads may advance end and top such that
    // current top > old end and current top + size > current end.  Then
    // pointer_delta underflows, allowing installation of top > current end.
    HeapWord* obj = Atomic::load_acquire(top_addr());
    if (pointer_delta(end(), obj) >= size) {
      HeapWord* new_top = obj + size;
      HeapWord* result = Atomic::cmpxchg(top_addr(), obj, new_top);
      // result can be one of two:
      //  the old top value: the exchange succeeded
      //  otherwise: the new value of the top is returned.
      if (result != obj) {
        continue; // another thread beat us to the allocation, try again
      }
      assert(is_object_aligned(obj) && is_object_aligned(new_top),
             "checking alignment");
      return obj;
    } else {
      return nullptr;
    }
  } while (true);
}

// Try to deallocate previous allocation. Returns true upon success.
bool MutableSpace::cas_deallocate(HeapWord *obj, size_t size) {
  HeapWord* expected_top = obj + size;
  return Atomic::cmpxchg(top_addr(), expected_top, obj) == expected_top;
}

// Only used by oldgen allocation.
bool MutableSpace::needs_expand(size_t word_size) const {
#ifdef ASSERT
  // If called by VM thread, locking is not needed.
  if (!Thread::current()->is_VM_thread()) {
    assert_lock_strong(PSOldGenExpand_lock);
  }
#endif
  // Holding the lock means end is stable.  So while top may be advancing
  // via concurrent allocations, there is no need to order the reads of top
  // and end here, unlike in cas_allocate.
  return pointer_delta(end(), top()) < word_size;
}

void MutableSpace::oop_iterate(OopIterateClosure* cl) {
  HeapWord* obj_addr = bottom();
  HeapWord* t = top();
  // Could call objects iterate, but this is easier.
  while (obj_addr < t) {
    obj_addr += cast_to_oop(obj_addr)->oop_iterate_size(cl);
  }
}

void MutableSpace::object_iterate(ObjectClosure* cl) {
  HeapWord* p = bottom();
  while (p < top()) {
    oop obj = cast_to_oop(p);
    // When promotion-failure occurs during Young GC, eden/from space is not cleared,
    // so we can encounter objects with "forwarded" markword.
    // They are essentially dead, so skipping them
    if (obj->is_forwarded()) {
      assert(!obj->is_self_forwarded(), "must not be self-forwarded");
      // It is safe to use the forwardee here. Parallel GC only uses
      // header-based forwarding during promotion. Full GC doesn't
      // use the object header for forwarding at all.
      p += obj->forwardee()->size();
    } else {
      cl->do_object(obj);
      p += obj->size();
    }
  }
}

void MutableSpace::print_short() const { print_short_on(tty); }
void MutableSpace::print_short_on( outputStream* st) const {
  st->print(" space " SIZE_FORMAT "K, %d%% used", capacity_in_bytes() / K,
            (int) ((double) used_in_bytes() * 100 / capacity_in_bytes()));
}

void MutableSpace::print() const { print_on(tty); }
void MutableSpace::print_on(outputStream* st) const {
  MutableSpace::print_short_on(st);
  st->print_cr(" [" PTR_FORMAT "," PTR_FORMAT "," PTR_FORMAT ")",
                 p2i(bottom()), p2i(top()), p2i(end()));
}

void MutableSpace::verify() {
  HeapWord* p = bottom();
  HeapWord* t = top();
  while (p < t) {
    oopDesc::verify(cast_to_oop(p));
    p += cast_to_oop(p)->size();
  }
  guarantee(p == top(), "end of last object must match end of space");
}
