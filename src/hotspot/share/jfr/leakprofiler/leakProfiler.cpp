/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jfr/leakprofiler/emitEventOperation.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/startOperation.hpp"
#include "jfr/leakprofiler/stopOperation.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ostream.hpp"

// Only to be updated during safepoint
ObjectSampler* LeakProfiler::_object_sampler = NULL;

static volatile jbyte suspended = 0;
bool LeakProfiler::start(jint sample_count) {
  if (UseZGC) {
    log_warning(jfr)("LeakProfiler is currently not supported in combination with ZGC");
    return false;
  }

  if (UseShenandoahGC) {
    log_warning(jfr)("LeakProfiler is currently not supported in combination with Shenandoah GC");
    return false;
  }

  if (_object_sampler != NULL) {
    // already started
    return true;
  }
  // Allows user to disable leak profiler on command line by setting queue size to zero.
  if (sample_count > 0) {
    StartOperation op(sample_count);
    VMThread::execute(&op);
    return _object_sampler != NULL;
  }
  return false;
}

bool LeakProfiler::stop() {
  if (_object_sampler == NULL) {
    // already stopped/not started
    return true;
  }
  StopOperation op;
  VMThread::execute(&op);
  return _object_sampler == NULL;
}

void LeakProfiler::emit_events(jlong cutoff_ticks, bool emit_all) {
  if (!is_running()) {
    return;
  }
  EmitEventOperation op(cutoff_ticks, emit_all);
  VMThread::execute(&op);
}

void LeakProfiler::oops_do(BoolObjectClosure* is_alive, OopClosure* f) {
  assert(SafepointSynchronize::is_at_safepoint(),
    "Leak Profiler::oops_do(...) may only be called during safepoint");

  if (_object_sampler != NULL) {
    _object_sampler->oops_do(is_alive, f);
  }
}

void LeakProfiler::sample(HeapWord* object,
                          size_t size,
                          JavaThread* thread) {
  assert(is_running(), "invariant");
  assert(thread != NULL, "invariant");
  assert(thread->thread_state() == _thread_in_vm, "invariant");

  // exclude compiler threads and code sweeper thread
  if (thread->is_hidden_from_external_view()) {
    return;
  }

  _object_sampler->add(object, size, thread);
}

ObjectSampler* LeakProfiler::object_sampler() {
  assert(is_suspended() || SafepointSynchronize::is_at_safepoint(),
    "Leak Profiler::object_sampler() may only be called during safepoint");
  return _object_sampler;
}

void LeakProfiler::set_object_sampler(ObjectSampler* object_sampler) {
  assert(SafepointSynchronize::is_at_safepoint(),
    "Leak Profiler::set_object_sampler() may only be called during safepoint");
  _object_sampler = object_sampler;
}

bool LeakProfiler::is_running() {
  return _object_sampler != NULL && !suspended;
}

bool LeakProfiler::is_suspended() {
  return _object_sampler != NULL && suspended;
}

void LeakProfiler::resume() {
  assert(is_suspended(), "invariant");
  OrderAccess::storestore();
  Atomic::store((jbyte)0, &suspended);
  assert(is_running(), "invariant");
}

void LeakProfiler::suspend() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(_object_sampler != NULL, "invariant");
  assert(!is_suspended(), "invariant");
  suspended = (jbyte)1; // safepoint visible
}
