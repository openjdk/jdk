/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/tlab_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zValue.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"

ZPerWorker<ThreadLocalAllocStats>* ZThreadLocalAllocBuffer::_stats = nullptr;

void ZThreadLocalAllocBuffer::initialize() {
  if (UseTLAB) {
    assert(_stats == nullptr, "Already initialized");
    _stats = new ZPerWorker<ThreadLocalAllocStats>();
    reset_statistics();
  }
}

void ZThreadLocalAllocBuffer::reset_statistics() {
  if (UseTLAB) {
    ZPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      stats->reset();
    }
  }
}

void ZThreadLocalAllocBuffer::publish_statistics() {
  if (UseTLAB) {
    ThreadLocalAllocStats total;

    ZPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      total.update(*stats);
    }

    total.publish();
  }
}

void ZThreadLocalAllocBuffer::retire(JavaThread* thread, ThreadLocalAllocStats* stats) {
  if (UseTLAB) {
    stats->reset();
    thread->retire_tlab(stats);
    if (ResizeTLAB) {
      thread->tlab().resize();
    }
  }
}

void ZThreadLocalAllocBuffer::update_stats(JavaThread* thread) {
  if (UseTLAB) {
    ZStackWatermark* const watermark = StackWatermarkSet::get<ZStackWatermark>(thread, StackWatermarkKind::gc);
    _stats->addr()->update(watermark->stats());
  }
}
