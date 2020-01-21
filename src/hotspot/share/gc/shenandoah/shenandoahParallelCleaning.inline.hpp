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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPARALLELCLEANING_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPARALLELCLEANING_INLINE_HPP

#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/thread.hpp"
#include "runtime/safepoint.hpp"

template<typename IsAlive, typename KeepAlive>
ShenandoahParallelWeakRootsCleaningTask<IsAlive, KeepAlive>::ShenandoahParallelWeakRootsCleaningTask(IsAlive* is_alive,
                                                                                                     KeepAlive* keep_alive,
                                                                                                     uint num_workers,
                                                                                                     bool include_concurrent_roots) :
  AbstractGangTask("Parallel Weak Root Cleaning Task"),
  _weak_processing_task(num_workers),
  _is_alive(is_alive), _keep_alive(keep_alive), _include_concurrent_roots(include_concurrent_roots) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");

  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_prologue(false);
  }
}

template<typename IsAlive, typename KeepAlive>
ShenandoahParallelWeakRootsCleaningTask<IsAlive, KeepAlive>::~ShenandoahParallelWeakRootsCleaningTask() {
  if (StringDedup::is_enabled()) {
    StringDedup::gc_epilogue();
  }
}

template<typename IsAlive, typename KeepAlive>
void ShenandoahParallelWeakRootsCleaningTask<IsAlive, KeepAlive>::work(uint worker_id) {
  if (_include_concurrent_roots) {
    _weak_processing_task.work<IsAlive, KeepAlive>(worker_id, _is_alive, _keep_alive);
  } else {
    _serial_weak_roots.weak_oops_do(_is_alive, _keep_alive, worker_id);
  }

  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::parallel_oops_do(_is_alive, _keep_alive, worker_id);
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARALLELCLEANING_INLINE_HPP
