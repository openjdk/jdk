/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP

#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"

template <typename IsAlive, typename KeepAlive>
void ShenandoahWeakRoots::oops_do(IsAlive* is_alive, KeepAlive* keep_alive, uint worker_id) {
  _task.work<IsAlive, KeepAlive>(worker_id, is_alive, keep_alive);
}

template <typename ITR>
ShenandoahCodeCacheRoots<ITR>::ShenandoahCodeCacheRoots() {
  nmethod::oops_do_marking_prologue();
}

template <typename ITR>
void ShenandoahCodeCacheRoots<ITR>::code_blobs_do(CodeBlobClosure* blob_cl, uint worker_id) {
  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
  _coderoots_iterator.possibly_parallel_blobs_do(blob_cl);
}

template <typename ITR>
ShenandoahCodeCacheRoots<ITR>::~ShenandoahCodeCacheRoots() {
  nmethod::oops_do_marking_epilogue();
}

template <typename IsAlive, typename KeepAlive>
void ShenandoahRootUpdater::roots_do(uint worker_id, IsAlive* is_alive, KeepAlive* keep_alive) {
  CodeBlobToOopClosure update_blobs(keep_alive, CodeBlobToOopClosure::FixRelocations);
  CLDToOopClosure clds(keep_alive, ClassLoaderData::_claim_strong);
  CLDToOopClosure* weak_clds = ShenandoahHeap::heap()->unload_classes() ? NULL : &clds;

  _serial_roots.oops_do(keep_alive, worker_id);

  _thread_roots.oops_do(keep_alive, NULL, worker_id);
  _cld_roots.clds_do(&clds, weak_clds, worker_id);

  if(_update_code_cache) {
    _code_roots.code_blobs_do(&update_blobs, worker_id);
  }

  _weak_roots.oops_do<IsAlive, KeepAlive>(is_alive, keep_alive, worker_id);
  _dedup_roots.oops_do(is_alive, keep_alive, worker_id);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP
