/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zContinuation.inline.hpp"
#include "gc/z/zStackChunkGCData.inline.hpp"
#include "runtime/atomic.hpp"

static zpointer materialize_zpointer(stackChunkOop chunk, void* addr) {
  volatile uintptr_t* const value_addr = (volatile uintptr_t*)addr;

  // A stack chunk has two modes:
  //
  // 1) It's recently allocated and the contents is a copy of the native stack.
  //    All oops have the format of oops in the stack. That is, they are
  //    zaddresses, and don't have any colored metadata bits.
  //
  // 2) It has lived long enough that the GC needs to visit the oops.
  //    Before the GC visits the oops, they are converted into zpointers,
  //    and become colored pointers.
  //
  // The load_oop function supports loading oops from chunks in either of the
  // two modes. It even supports loading oops, while another thread is
  // converting the chunk to "gc mode" [transition from (1) to (2)]. So, we
  // load the oop once and perform all checks on that loaded copy.

  // Load once
  const uintptr_t value = Atomic::load(value_addr);

  if ((value & ~ZPointerAllMetadataMask) == 0) {
    // Must be null of some sort - either zaddress or zpointer
    return zpointer::null;
  }

  const uintptr_t impossible_zaddress_mask = ~((ZAddressHeapBase - 1) | ZAddressHeapBase);
  if ((value & impossible_zaddress_mask) != 0) {
    // Must be a zpointer - it has bits forbidden in zaddresses
    return to_zpointer(value);
  }

  // Must be zaddress
  const zaddress_unsafe zaddr = to_zaddress_unsafe(value);

  // A zaddress means that the chunk was recently allocated, and the layout is
  // that of a native stack. That means that oops are uncolored (zaddress). But
  // the oops still have an implicit color, saved away in the chunk.

  // Use the implicit color, and create a zpointer that is equivalent with
  // what we would have written if we where to eagerly create the zpointer
  // when the stack frames where copied into the chunk.
  const uintptr_t color = ZStackChunkGCData::color(chunk);
  return ZAddress::color(zaddr, color);
}

oop ZContinuation::load_oop(stackChunkOop chunk, void* addr) {
  // addr could contain either a zpointer or a zaddress
  const zpointer zptr = materialize_zpointer(chunk, addr);

  // Apply the load barrier, without healing the zaddress/zpointer
  return to_oop(ZBarrier::load_barrier_on_oop_field_preloaded(nullptr /* p */, zptr));
}

ZContinuation::ZColorStackOopClosure::ZColorStackOopClosure(stackChunkOop chunk)
  : _color(ZStackChunkGCData::color(chunk)) {}

void ZContinuation::ZColorStackOopClosure::do_oop(oop* p) {
  // Convert zaddress to zpointer
  // TODO: Comment why this is safe and non volatile
  zaddress_unsafe* const p_zaddress_unsafe = (zaddress_unsafe*)p;
  zpointer* const p_zpointer = (zpointer*)p;
  *p_zpointer = ZAddress::color(*p_zaddress_unsafe, _color);
}

void ZContinuation::ZColorStackOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

void ZContinuation::ZUncolorStackOopClosure::do_oop(oop* p) {
  const zpointer ptr = *(volatile zpointer*)p;
  const zaddress addr = ZPointer::uncolor(ptr);
  *(volatile zaddress*)p = addr;
}

void ZContinuation::ZUncolorStackOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}
