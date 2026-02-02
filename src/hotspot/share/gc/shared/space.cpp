/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

ContiguousSpace::ContiguousSpace():
  _bottom(nullptr),
  _end(nullptr),
  _top(nullptr) {}

void ContiguousSpace::initialize(MemRegion mr,
                                 bool clear_space) {
  HeapWord* bottom = mr.start();
  HeapWord* end    = mr.end();
  assert(Universe::on_page_boundary(bottom) && Universe::on_page_boundary(end),
         "invalid space boundaries");
  set_bottom(bottom);
  set_end(end);
  if (clear_space) {
    clear(SpaceDecorator::DontMangle);
  }
  if (ZapUnusedHeapArea) {
    mangle_unused_area();
  }
}

void ContiguousSpace::clear(bool mangle_space) {
  set_top(bottom());
  if (ZapUnusedHeapArea && mangle_space) {
    mangle_unused_area();
  }
}

#ifndef PRODUCT

void ContiguousSpace::mangle_unused_area() {
  mangle_unused_area(MemRegion(top(), _end));
}

void ContiguousSpace::mangle_unused_area(MemRegion mr) {
  SpaceMangler::mangle_region(mr);
}

#endif  // NOT_PRODUCT

void ContiguousSpace::print() const { print_on(tty, ""); }

void ContiguousSpace::print_on(outputStream* st, const char* prefix) const {
  st->print_cr("%sspace %zuK, %3d%% used [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
               prefix,
               capacity() / K, (int) ((double) used() * 100 / capacity()),
               p2i(bottom()), p2i(top()), p2i(end()));
}

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
      // Retry if we did not successfully updated the top pointers.
      if (_top.compare_set(obj, new_top)) {
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
