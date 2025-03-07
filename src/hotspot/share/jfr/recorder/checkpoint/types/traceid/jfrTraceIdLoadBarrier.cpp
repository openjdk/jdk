/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdKlassQueue.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrEpochQueue.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/powerOfTwo.hpp"

// The queue instances are used by the load barrier to enqueue tagged Klass'es.
static JfrTraceIdKlassQueue* _klass_queue = nullptr; // Generic for all Java threads.
static JfrTraceIdKlassQueue* _sampler_klass_queue = nullptr; // Specialized for the Jfr Thread Sampler using a larger buffer size.

static JfrTraceIdKlassQueue& klass_queue() {
  assert(_klass_queue != nullptr, "invariant");
  return *_klass_queue;
}

static JfrTraceIdKlassQueue& sampler_klass_queue() {
  assert(_sampler_klass_queue != nullptr, "invariant");
  return *_sampler_klass_queue;
}

const constexpr size_t buffer_size_bytes = 1 * K; // min_elem_size of storage unit
const constexpr size_t prealloc_count = 32;
const constexpr size_t sampler_prealloc_count = 2;

// The sampler thread cannot renew a buffer in-flight because it cannot acquire the malloc lock.
// It must therefore pre-allocate at least a full stack trace of buffer space before it can suspend a thread.
// This pre-allocation implies the need for a larger buffer size compared to other threads, a size that is a function
// of the stack depth parameter. For proper accommodation, there is a specialized queue only for the Jfr Sampler Thread.
static size_t derive_sampler_buffer_size() {
  size_t stackdepth_bytes = JfrOptionSet::stackdepth() * 2 * wordSize; // each frame tags at most 2 words
  stackdepth_bytes = round_up_power_of_2(stackdepth_bytes * 2); // accommodate at least two full stacktraces
  return MAX2(stackdepth_bytes, buffer_size_bytes);
}

bool JfrTraceIdLoadBarrier::initialize() {
  assert(_klass_queue == nullptr, "invariant");
  _klass_queue = new JfrTraceIdKlassQueue();
  if (_klass_queue == nullptr || !_klass_queue->initialize(buffer_size_bytes, JFR_MSPACE_UNLIMITED_CACHE_SIZE, prealloc_count)) {
    return false;
  }
  assert(_sampler_klass_queue == nullptr, "invariant");
  const size_t sampler_buffer_size_bytes = derive_sampler_buffer_size();
  assert(is_power_of_2(sampler_buffer_size_bytes), "invariant");
  _sampler_klass_queue = new JfrTraceIdKlassQueue();
  return _sampler_klass_queue != nullptr && _sampler_klass_queue->initialize(sampler_buffer_size_bytes, JFR_MSPACE_UNLIMITED_CACHE_SIZE, sampler_prealloc_count);
}

void JfrTraceIdLoadBarrier::clear() {
  if (_klass_queue != nullptr) {
    _klass_queue->clear();
  }
  if (_sampler_klass_queue != nullptr) {
    _sampler_klass_queue->clear();
  }
}

void JfrTraceIdLoadBarrier::destroy() {
  delete _klass_queue;
  _klass_queue = nullptr;
  delete _sampler_klass_queue;
  _sampler_klass_queue = nullptr;
}

void JfrTraceIdLoadBarrier::enqueue(const Klass* klass) {
  assert(klass != nullptr, "invariant");
  assert(USED_THIS_EPOCH(klass), "invariant");
  klass_queue().enqueue(klass);
}

JfrBuffer* JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(Thread* thread) {
  return sampler_klass_queue().get_enqueue_buffer(thread);
}

JfrBuffer* JfrTraceIdLoadBarrier::renew_sampler_enqueue_buffer(Thread* thread) {
  return sampler_klass_queue().renew_enqueue_buffer(thread);
}

void JfrTraceIdLoadBarrier::do_klasses(klass_callback callback, bool previous_epoch) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  klass_queue().iterate(callback, previous_epoch);
  sampler_klass_queue().iterate(callback, previous_epoch);
}
