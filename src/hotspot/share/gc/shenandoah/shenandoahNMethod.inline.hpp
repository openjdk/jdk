/*
 * Copyright (c) 2019, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_INLINE_HPP

#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahNMethod.hpp"

nmethod* ShenandoahNMethod::nm() const {
  return _nm;
}

ShenandoahReentrantLock* ShenandoahNMethod::lock() {
  return &_lock;
}

int ShenandoahNMethod::oop_count() const {
  return _oops_count + static_cast<int>(nm()->oops_end() - nm()->oops_begin());
}

bool ShenandoahNMethod::has_oops() const {
  return oop_count() > 0;
}

void ShenandoahNMethod::mark_unregistered() {
  _unregistered = true;
}

bool ShenandoahNMethod::is_unregistered() const {
  return _unregistered;
}

void ShenandoahNMethod::disarm_nmethod(nmethod* nm) {
  if (!ShenandoahConcurrentRoots::can_do_concurrent_class_unloading()) {
    return;
  }

  BarrierSetNMethod* const bs = BarrierSet::barrier_set()->barrier_set_nmethod();
  assert(bs != NULL, "Sanity");
  if (bs->is_armed(nm)) {
    bs->disarm(nm);
  }
}

ShenandoahNMethod* ShenandoahNMethod::gc_data(nmethod* nm) {
  return nm->gc_data<ShenandoahNMethod>();
}

void ShenandoahNMethod::attach_gc_data(nmethod* nm, ShenandoahNMethod* gc_data) {
  nm->set_gc_data<ShenandoahNMethod>(gc_data);
}

ShenandoahReentrantLock* ShenandoahNMethod::lock_for_nmethod(nmethod* nm) {
  return gc_data(nm)->lock();
}

bool ShenandoahNMethodTable::iteration_in_progress() const {
  return _iteration_in_progress;
}

template<bool CSET_FILTER>
void ShenandoahNMethodTableSnapshot::parallel_blobs_do(CodeBlobClosure *f) {
  size_t stride = 256; // educated guess

  ShenandoahNMethod** const list = _array;

  size_t max = (size_t)_length;
  while (_claimed < max) {
    size_t cur = Atomic::fetch_and_add(&_claimed, stride);
    size_t start = cur;
    size_t end = MIN2(cur + stride, max);
    if (start >= max) break;

    for (size_t idx = start; idx < end; idx++) {
      ShenandoahNMethod* nmr = list[idx];
      assert(nmr != NULL, "Sanity");
      if (nmr->is_unregistered()) {
        continue;
      }

      nmr->assert_alive_and_correct();

      if (CSET_FILTER && !nmr->has_cset_oops(_heap)) {
        continue;
      }

      f->do_code_blob(nmr->nm());
    }
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHNMETHOD_INLINE_HPP
