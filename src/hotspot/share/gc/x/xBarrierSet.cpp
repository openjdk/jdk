/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xBarrierSet.hpp"
#include "gc/x/xBarrierSetAssembler.hpp"
#include "gc/x/xBarrierSetNMethod.hpp"
#include "gc/x/xBarrierSetStackChunk.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xStackWatermark.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "gc/x/c1/xBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/x/c2/xBarrierSetC2.hpp"
#endif

class XBarrierSetC1;
class XBarrierSetC2;

XBarrierSet::XBarrierSet() :
    BarrierSet(make_barrier_set_assembler<XBarrierSetAssembler>(),
               make_barrier_set_c1<XBarrierSetC1>(),
               make_barrier_set_c2<XBarrierSetC2>(),
               new XBarrierSetNMethod(),
               new XBarrierSetStackChunk(),
               BarrierSet::FakeRtti(BarrierSet::XBarrierSet)) {}

XBarrierSetAssembler* XBarrierSet::assembler() {
  BarrierSetAssembler* const bsa = BarrierSet::barrier_set()->barrier_set_assembler();
  return reinterpret_cast<XBarrierSetAssembler*>(bsa);
}

bool XBarrierSet::barrier_needed(DecoratorSet decorators, BasicType type) {
  assert((decorators & AS_RAW) == 0, "Unexpected decorator");
  //assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Unexpected decorator");

  if (is_reference_type(type)) {
    assert((decorators & (IN_HEAP | IN_NATIVE)) != 0, "Where is reference?");
    // Barrier needed even when IN_NATIVE, to allow concurrent scanning.
    return true;
  }

  // Barrier not needed
  return false;
}

void XBarrierSet::on_thread_create(Thread* thread) {
  // Create thread local data
  XThreadLocalData::create(thread);
}

void XBarrierSet::on_thread_destroy(Thread* thread) {
  // Destroy thread local data
  XThreadLocalData::destroy(thread);
}

void XBarrierSet::on_thread_attach(Thread* thread) {
  // Set thread local address bad mask
  XThreadLocalData::set_address_bad_mask(thread, XAddressBadMask);
  if (thread->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermark* const watermark = new XStackWatermark(jt);
    StackWatermarkSet::add_watermark(jt, watermark);
  }
}

void XBarrierSet::on_thread_detach(Thread* thread) {
  // Flush and free any remaining mark stacks
  XHeap::heap()->mark_flush_and_free(thread);
}

void XBarrierSet::print_on(outputStream* st) const {
  st->print_cr("XBarrierSet");
}
