/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/space.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/spaceDecorator.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/java.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

ContiguousSpace::ContiguousSpace(): Space(),
  _next_compaction_space(nullptr),
  _top(nullptr) {
  _mangler = new GenSpaceMangler(this);
}

ContiguousSpace::~ContiguousSpace() {
  delete _mangler;
}

void ContiguousSpace::initialize(MemRegion mr,
                                 bool clear_space,
                                 bool mangle_space) {
  HeapWord* bottom = mr.start();
  HeapWord* end    = mr.end();
  assert(Universe::on_page_boundary(bottom) && Universe::on_page_boundary(end),
         "invalid space boundaries");
  set_bottom(bottom);
  set_end(end);
  if (clear_space) {
    clear(mangle_space);
  }
  _next_compaction_space = nullptr;
}

void ContiguousSpace::clear(bool mangle_space) {
  set_top(bottom());
  set_saved_mark();
  if (ZapUnusedHeapArea && mangle_space) {
    mangle_unused_area();
  }
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
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
              p2i(bottom()), p2i(top()), p2i(end()));
}
#endif

void ContiguousSpace::verify() const {
  HeapWord* p = bottom();
  HeapWord* t = top();
  while (p < t) {
    oopDesc::verify(cast_to_oop(p));
    p += cast_to_oop(p)->size();
  }
  guarantee(p == top(), "end of last object must match end of space");
}

void ContiguousSpace::object_iterate(ObjectClosure* blk) {
  HeapWord* addr = bottom();
  while (addr < top()) {
    oop obj = cast_to_oop(addr);
    blk->do_object(obj);
    addr += obj->size();
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

// This version requires locking.
inline HeapWord* ContiguousSpace::allocate_impl(size_t size) {
  assert(Heap_lock->owned_by_self() ||
         (SafepointSynchronize::is_at_safepoint() && Thread::current()->is_VM_thread()),
         "not locked");
  HeapWord* obj = top();
  if (pointer_delta(end(), obj) >= size) {
    HeapWord* new_top = obj + size;
    set_top(new_top);
    assert(is_object_aligned(obj) && is_object_aligned(new_top), "checking alignment");
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
        assert(is_object_aligned(obj) && is_object_aligned(new_top), "checking alignment");
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
HeapWord* TenuredSpace::block_start_const(const void* addr) const {
  HeapWord* cur_block = _offsets.block_start_reaching_into_card(addr);

  while (true) {
    HeapWord* next_block = cur_block + cast_to_oop(cur_block)->size();
    if (next_block > addr) {
      assert(cur_block <= addr, "postcondition");
      return cur_block;
    }
    cur_block = next_block;
    // Because the BOT is precise, we should never step into the next card
    // (i.e. crossing the card boundary).
    assert(!SerialBlockOffsetTable::is_crossing_card_boundary(cur_block, (HeapWord*)addr), "must be");
  }
}

TenuredSpace::TenuredSpace(SerialBlockOffsetSharedArray* sharedOffsetArray,
                           MemRegion mr) :
  _offsets(sharedOffsetArray)
{
  initialize(mr, SpaceDecorator::Clear, SpaceDecorator::Mangle);
}

size_t TenuredSpace::allowed_dead_ratio() const {
  return MarkSweepDeadRatio;
}
#endif // INCLUDE_SERIALGC
