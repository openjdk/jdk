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
#include "gc/shared/tlab_globals.hpp"
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xStackWatermark.hpp"
#include "gc/x/xThreadLocalAllocBuffer.hpp"
#include "gc/x/xValue.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"

XPerWorker<ThreadLocalAllocStats>* XThreadLocalAllocBuffer::_stats = NULL;

void XThreadLocalAllocBuffer::initialize() {
  if (UseTLAB) {
    assert(_stats == NULL, "Already initialized");
    _stats = new XPerWorker<ThreadLocalAllocStats>();
    reset_statistics();
  }
}

void XThreadLocalAllocBuffer::reset_statistics() {
  if (UseTLAB) {
    XPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      stats->reset();
    }
  }
}

void XThreadLocalAllocBuffer::publish_statistics() {
  if (UseTLAB) {
    ThreadLocalAllocStats total;

    XPerWorkerIterator<ThreadLocalAllocStats> iter(_stats);
    for (ThreadLocalAllocStats* stats; iter.next(&stats);) {
      total.update(*stats);
    }

    total.publish();
  }
}

static void fixup_address(HeapWord** p) {
  *p = (HeapWord*)XAddress::good_or_null((uintptr_t)*p);
}

void XThreadLocalAllocBuffer::retire(JavaThread* thread, ThreadLocalAllocStats* stats) {
  if (UseTLAB) {
    stats->reset();
    thread->tlab().addresses_do(fixup_address);
    thread->tlab().retire(stats);
    if (ResizeTLAB) {
      thread->tlab().resize();
    }
  }
}

void XThreadLocalAllocBuffer::remap(JavaThread* thread) {
  if (UseTLAB) {
    thread->tlab().addresses_do(fixup_address);
  }
}

void XThreadLocalAllocBuffer::update_stats(JavaThread* thread) {
  if (UseTLAB) {
    XStackWatermark* const watermark = StackWatermarkSet::get<XStackWatermark>(thread, StackWatermarkKind::gc);
    _stats->addr()->update(watermark->stats());
  }
}
