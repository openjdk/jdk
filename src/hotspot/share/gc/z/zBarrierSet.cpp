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
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "runtime/thread.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#endif

class ZBarrierSetC1;
class ZBarrierSetC2;

ZBarrierSet::ZBarrierSet() :
    BarrierSet(make_barrier_set_assembler<ZBarrierSetAssembler>(),
               make_barrier_set_c1<ZBarrierSetC1>(),
               make_barrier_set_c2<ZBarrierSetC2>(),
               new ZBarrierSetNMethod(),
               BarrierSet::FakeRtti(BarrierSet::ZBarrierSet)) {}

ZBarrierSetAssembler* ZBarrierSet::assembler() {
  BarrierSetAssembler* const bsa = BarrierSet::barrier_set()->barrier_set_assembler();
  return reinterpret_cast<ZBarrierSetAssembler*>(bsa);
}

bool ZBarrierSet::barrier_needed(DecoratorSet decorators, BasicType type) {
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

void ZBarrierSet::on_thread_create(Thread* thread) {
  // Create thread local data
  ZThreadLocalData::create(thread);
}

void ZBarrierSet::on_thread_destroy(Thread* thread) {
  // Destroy thread local data
  ZThreadLocalData::destroy(thread);
}

void ZBarrierSet::on_thread_attach(Thread* thread) {
  // Set thread local address bad mask
  ZThreadLocalData::set_address_load_bad_mask(thread, ZAddressLoadBadMask);
  ZThreadLocalData::set_address_load_good_mask(thread, ZAddressLoadGoodMask);
  ZThreadLocalData::set_address_mark_bad_mask(thread, ZAddressMarkBadMask);
  ZThreadLocalData::set_address_store_bad_mask(thread, ZAddressStoreBadMask);
  ZThreadLocalData::set_address_store_good_mask(thread, ZAddressStoreGoodMask);
  if (thread->is_Java_thread()) {
    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermark* const watermark = new ZStackWatermark(jt);
    StackWatermarkSet::add_watermark(jt, watermark);
    ZThreadLocalData::store_barrier_buffer(jt)->initialize();
  }
}

void ZBarrierSet::on_thread_detach(Thread* thread) {
  // Flush and free any remaining mark stacks
  ZHeap::heap()->mark_flush_and_free(thread);
}

void ZBarrierSet::on_slowpath_allocation_exit(JavaThread* thread, oop new_obj) {
  // An allocation slow path can expose an object that gets promoted to the old generation.
  // However, the compiler optimizes away barriers on new allocations, which implies that
  // no remset entries will be tracked for this object, by the barrier. Therefore we
  // explicitly remember such objects here. In most cases this is a no-op as the object
  // that escapes the runtime is very likely to be young.
  ZHeap::heap()->remember_fields_filtered(to_zaddress(new_obj));
}

void ZBarrierSet::print_on(outputStream* st) const {
  st->print_cr("ZBarrierSet");
}
