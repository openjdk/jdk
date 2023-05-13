/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xOop.inline.hpp"
#include "gc/x/xThread.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

template <bool finalizable>
bool XBarrier::should_mark_through(uintptr_t addr) {
  // Finalizable marked oops can still exists on the heap after marking
  // has completed, in which case we just want to convert this into a
  // good oop and not push it on the mark stack.
  if (!during_mark()) {
    assert(XAddress::is_marked(addr), "Should be marked");
    assert(XAddress::is_finalizable(addr), "Should be finalizable");
    return false;
  }

  // During marking, we mark through already marked oops to avoid having
  // some large part of the object graph hidden behind a pushed, but not
  // yet flushed, entry on a mutator mark stack. Always marking through
  // allows the GC workers to proceed through the object graph even if a
  // mutator touched an oop first, which in turn will reduce the risk of
  // having to flush mark stacks multiple times to terminate marking.
  //
  // However, when doing finalizable marking we don't always want to mark
  // through. First, marking through an already strongly marked oop would
  // be wasteful, since we will then proceed to do finalizable marking on
  // an object which is, or will be, marked strongly. Second, marking
  // through an already finalizable marked oop would also be wasteful,
  // since such oops can never end up on a mutator mark stack and can
  // therefore not hide some part of the object graph from GC workers.
  if (finalizable) {
    return !XAddress::is_marked(addr);
  }

  // Mark through
  return true;
}

template <bool gc_thread, bool follow, bool finalizable, bool publish>
uintptr_t XBarrier::mark(uintptr_t addr) {
  uintptr_t good_addr;

  if (XAddress::is_marked(addr)) {
    // Already marked, but try to mark though anyway
    good_addr = XAddress::good(addr);
  } else if (XAddress::is_remapped(addr)) {
    // Already remapped, but also needs to be marked
    good_addr = XAddress::good(addr);
  } else {
    // Needs to be both remapped and marked
    good_addr = remap(addr);
  }

  // Mark
  if (should_mark_through<finalizable>(addr)) {
    XHeap::heap()->mark_object<gc_thread, follow, finalizable, publish>(good_addr);
  }

  if (finalizable) {
    // Make the oop finalizable marked/good, instead of normal marked/good.
    // This is needed because an object might first becomes finalizable
    // marked by the GC, and then loaded by a mutator thread. In this case,
    // the mutator thread must be able to tell that the object needs to be
    // strongly marked. The finalizable bit in the oop exists to make sure
    // that a load of a finalizable marked oop will fall into the barrier
    // slow path so that we can mark the object as strongly reachable.
    return XAddress::finalizable_good(good_addr);
  }

  return good_addr;
}

uintptr_t XBarrier::remap(uintptr_t addr) {
  assert(!XAddress::is_good(addr), "Should not be good");
  assert(!XAddress::is_weak_good(addr), "Should not be weak good");
  return XHeap::heap()->remap_object(addr);
}

uintptr_t XBarrier::relocate(uintptr_t addr) {
  assert(!XAddress::is_good(addr), "Should not be good");
  assert(!XAddress::is_weak_good(addr), "Should not be weak good");
  return XHeap::heap()->relocate_object(addr);
}

uintptr_t XBarrier::relocate_or_mark(uintptr_t addr) {
  return during_relocate() ? relocate(addr) : mark<AnyThread, Follow, Strong, Publish>(addr);
}

uintptr_t XBarrier::relocate_or_mark_no_follow(uintptr_t addr) {
  return during_relocate() ? relocate(addr) : mark<AnyThread, DontFollow, Strong, Publish>(addr);
}

uintptr_t XBarrier::relocate_or_remap(uintptr_t addr) {
  return during_relocate() ? relocate(addr) : remap(addr);
}

//
// Load barrier
//
uintptr_t XBarrier::load_barrier_on_oop_slow_path(uintptr_t addr) {
  return relocate_or_mark(addr);
}

uintptr_t XBarrier::load_barrier_on_invisible_root_oop_slow_path(uintptr_t addr) {
  return relocate_or_mark_no_follow(addr);
}

void XBarrier::load_barrier_on_oop_fields(oop o) {
  assert(XAddress::is_good(XOop::to_address(o)), "Should be good");
  XLoadBarrierOopClosure cl;
  o->oop_iterate(&cl);
}

//
// Weak load barrier
//
uintptr_t XBarrier::weak_load_barrier_on_oop_slow_path(uintptr_t addr) {
  return XAddress::is_weak_good(addr) ? XAddress::good(addr) : relocate_or_remap(addr);
}

uintptr_t XBarrier::weak_load_barrier_on_weak_oop_slow_path(uintptr_t addr) {
  const uintptr_t good_addr = weak_load_barrier_on_oop_slow_path(addr);
  if (XHeap::heap()->is_object_strongly_live(good_addr)) {
    return good_addr;
  }

  // Not strongly live
  return 0;
}

uintptr_t XBarrier::weak_load_barrier_on_phantom_oop_slow_path(uintptr_t addr) {
  const uintptr_t good_addr = weak_load_barrier_on_oop_slow_path(addr);
  if (XHeap::heap()->is_object_live(good_addr)) {
    return good_addr;
  }

  // Not live
  return 0;
}

//
// Keep alive barrier
//
uintptr_t XBarrier::keep_alive_barrier_on_oop_slow_path(uintptr_t addr) {
  assert(during_mark(), "Invalid phase");

  // Mark
  return mark<AnyThread, Follow, Strong, Overflow>(addr);
}

uintptr_t XBarrier::keep_alive_barrier_on_weak_oop_slow_path(uintptr_t addr) {
  assert(XResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const uintptr_t good_addr = weak_load_barrier_on_oop_slow_path(addr);
  assert(XHeap::heap()->is_object_strongly_live(good_addr), "Should be live");
  return good_addr;
}

uintptr_t XBarrier::keep_alive_barrier_on_phantom_oop_slow_path(uintptr_t addr) {
  assert(XResurrection::is_blocked(), "This operation is only valid when resurrection is blocked");
  const uintptr_t good_addr = weak_load_barrier_on_oop_slow_path(addr);
  assert(XHeap::heap()->is_object_live(good_addr), "Should be live");
  return good_addr;
}

//
// Mark barrier
//
uintptr_t XBarrier::mark_barrier_on_oop_slow_path(uintptr_t addr) {
  assert(during_mark(), "Invalid phase");
  assert(XThread::is_worker(), "Invalid thread");

  // Mark
  return mark<GCThread, Follow, Strong, Overflow>(addr);
}

uintptr_t XBarrier::mark_barrier_on_finalizable_oop_slow_path(uintptr_t addr) {
  assert(during_mark(), "Invalid phase");
  assert(XThread::is_worker(), "Invalid thread");

  // Mark
  return mark<GCThread, Follow, Finalizable, Overflow>(addr);
}

//
// Narrow oop variants, never used.
//
oop XBarrier::load_barrier_on_oop_field(volatile narrowOop* p) {
  ShouldNotReachHere();
  return NULL;
}

oop XBarrier::load_barrier_on_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

void XBarrier::load_barrier_on_oop_array(volatile narrowOop* p, size_t length) {
  ShouldNotReachHere();
}

oop XBarrier::load_barrier_on_weak_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

oop XBarrier::load_barrier_on_phantom_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

oop XBarrier::weak_load_barrier_on_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

oop XBarrier::weak_load_barrier_on_weak_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

oop XBarrier::weak_load_barrier_on_phantom_oop_field_preloaded(volatile narrowOop* p, oop o) {
  ShouldNotReachHere();
  return NULL;
}

#ifdef ASSERT

// ON_WEAK barriers should only ever be applied to j.l.r.Reference.referents.
void XBarrier::verify_on_weak(volatile oop* referent_addr) {
  if (referent_addr != NULL) {
    uintptr_t base = (uintptr_t)referent_addr - java_lang_ref_Reference::referent_offset();
    oop obj = cast_to_oop(base);
    assert(oopDesc::is_oop(obj), "Verification failed for: ref " PTR_FORMAT " obj: " PTR_FORMAT, (uintptr_t)referent_addr, base);
    assert(java_lang_ref_Reference::is_referent_field(obj, java_lang_ref_Reference::referent_offset()), "Sanity");
  }
}

#endif

void XLoadBarrierOopClosure::do_oop(oop* p) {
  XBarrier::load_barrier_on_oop_field(p);
}

void XLoadBarrierOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}
