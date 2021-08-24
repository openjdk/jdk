/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zStoreBarrierBuffer.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

#ifdef ASSERT
static bool during_minor_mark() {
  return ZHeap::heap()->minor_collector()->is_phase_mark();
}

static bool during_major_mark() {
  return ZHeap::heap()->major_collector()->is_phase_mark();
}

static bool during_any_mark() {
  return during_minor_mark() || during_major_mark();
}
#endif

zaddress ZBarrier::relocate_or_remap(zaddress_unsafe addr, ZCollector* collector) {
  return collector->relocate_or_remap_object(addr);
}

zaddress ZBarrier::remap(zaddress_unsafe addr, ZCollector* collector) {
  return collector->remap_object(addr);
}

template <bool resurrect, bool gc_thread, bool follow, bool finalizable, bool publish>
static void mark(zaddress addr) {
  // FIXME: Maybe rely on earlier null-filtering
  if (is_null(addr)) {
    return;
  }

  // Mark
  ZHeap::heap()->mark_object<resurrect, gc_thread, follow, finalizable, publish>(addr);
}

template <bool follow,bool publish>
static void mark_minor(zaddress addr) {
  // FIXME: Maybe rely on earlier null-filtering
  if (is_null(addr)) {
    return;
  }

  assert(during_minor_mark(), "Should only be called during marking");

  // Mark
  ZHeap::heap()->mark_minor_object<follow, publish>(addr);
}

//
// Weak load barrier
//

zaddress ZBarrier::blocking_keep_alive_on_weak_slow_path(zaddress addr) {
  if (is_null(addr)) {
    return zaddress::null;
  }

  if (ZHeap::heap()->is_old(addr)) {
    if (!ZHeap::heap()->is_object_strongly_live(addr)) {
      return zaddress::null;
    }
  } else {
    // Young gen objects are never blocked, need to keep alive
    mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);
  }


  // Strongly live
  return addr;
}

zaddress ZBarrier::blocking_keep_alive_on_phantom_slow_path(zaddress addr) {
  if (is_null(addr)) {
    return zaddress::null;
  }

  if (ZHeap::heap()->is_old(addr)) {
    if (!ZHeap::heap()->is_object_live(addr)) {
      return zaddress::null;
    }
  } else {
    // Young gen objects are never blocked, need to keep alive
    mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);
  }

  // Strongly live
  return addr;
}

zaddress ZBarrier::blocking_load_barrier_on_weak_slow_path(zaddress addr) {
  if (is_null(addr)) {
    return zaddress::null;
  }

  if (ZHeap::heap()->is_old(addr)) {
    if (!ZHeap::heap()->is_object_strongly_live(addr)) {
      return zaddress::null;
    }
  } else {
    // Young objects are never considered non-strong
    // Note: Should not need to keep object alive in this operation,
    //       but the barrier colors the pointer mark good, so we need
    //       to mark the object accordingly.
    mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);
  }

  return addr;
}

zaddress ZBarrier::blocking_load_barrier_on_phantom_slow_path(zaddress addr) {
  if (is_null(addr)) {
    return zaddress::null;
  }

  if (ZHeap::heap()->is_old(addr)) {
    if (!ZHeap::heap()->is_object_live(addr)) {
      return zaddress::null;
    }
  } else {
    // Young objects are never considered non-strong
    // Note: Should not need to keep object alive in this operation,
    //       but the barrier colors the pointer mark good, so we need
    //       to mark the object accordingly.
    mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);
  }

  return addr;
}

//
// Clean barrier
//

zaddress ZBarrier::verify_old_object_live_slow_path(zaddress addr) {
  // Verify that the object was indeed alive
  assert(ZHeap::heap()->is_young(addr) || ZHeap::heap()->is_object_live(addr), "Should be live");

  return addr;
}

//
// Mark barrier
//

zaddress ZBarrier::mark_slow_path(zaddress addr) {
  assert(during_any_mark(), "Invalid phase");

  // Mark
  mark<ZMark::DontResurrect, ZMark::GCThread, ZMark::Follow, ZMark::Strong, ZMark::Overflow>(addr);

  return addr;
}

zaddress ZBarrier::mark_minor_slow_path(zaddress addr) {
  assert(during_minor_mark(), "Invalid phase");

  // Mark
  mark_minor<ZMark::Follow, ZMark::Overflow>(addr);

  return addr;
}

zaddress ZBarrier::mark_finalizable_slow_path(zaddress addr) {
  assert(during_any_mark(), "Invalid phase");

  // Mark
  mark<ZMark::DontResurrect, ZMark::GCThread, ZMark::Follow, ZMark::Finalizable, ZMark::Overflow>(addr);

  return addr;
}

void ZBarrier::remember(volatile zpointer* p) {
  ZHeap::heap()->remember_filtered(p);
}

void ZBarrier::mark_and_remember(volatile zpointer* p, zaddress addr) {
  mark<ZMark::DontResurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);
  remember(p);
}

zaddress ZBarrier::heap_store_slow_path(volatile zpointer* p, zaddress addr, zpointer prev, bool heal) {
  ZStoreBarrierBuffer* buffer = ZStoreBarrierBuffer::buffer_for_store(heal);

  if (buffer != NULL) {
    // Buffer store barriers whenever possible
    buffer->add(p, prev);
  } else {
    mark_and_remember(p, addr);
  }

  return addr;
}

zaddress ZBarrier::native_store_slow_path(zaddress addr) {
  mark<ZMark::DontResurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);

  return addr;
}

zaddress ZBarrier::keep_alive_slow_path(zaddress addr) {
  mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong, ZMark::Publish>(addr);

  return addr;
}

#ifdef ASSERT

// ON_WEAK barriers should only ever be applied to j.l.r.Reference.referents.
void ZBarrier::verify_on_weak(volatile zpointer* referent_addr) {
  if (referent_addr != NULL) {
    uintptr_t base = (uintptr_t)referent_addr - java_lang_ref_Reference::referent_offset();
    oop obj = cast_to_oop(base);
    assert(oopDesc::is_oop(obj), "Verification failed for: ref " PTR_FORMAT " obj: " PTR_FORMAT, (uintptr_t)referent_addr, base);
    assert(java_lang_ref_Reference::is_referent_field(obj, java_lang_ref_Reference::referent_offset()), "Sanity");
  }
}

#endif
