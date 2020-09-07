/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/threadHeapSampler.hpp"

// Default is 512kb.
volatile int ThreadHeapSampler::_sampling_interval = 512 * 1024;

void ThreadHeapSampler::pick_next_sample(size_t overflowed_bytes) {
  // Explicitly test if the sampling interval is 0, return 0 to sample every
  // allocation.
  int interval = get_sampling_interval();
  if (interval == 0) {
    _bytes_until_sample = 0;
    return;
  }

  _bytes_until_sample = _sampler_support.pick_next_geometric_sample(interval);
}

void ThreadHeapSampler::check_for_sampling(oop obj, size_t allocation_size, size_t bytes_since_allocation) {
  size_t total_allocated_bytes = bytes_since_allocation + allocation_size;

  // If not yet time for a sample, skip it.
  if (total_allocated_bytes < _bytes_until_sample) {
    _bytes_until_sample -= total_allocated_bytes;
  }

  size_t overflow_bytes = total_allocated_bytes - _bytes_until_sample;
  pick_next_sample(overflow_bytes);
}

int ThreadHeapSampler::get_sampling_interval() {
  return Atomic::load_acquire(&_sampling_interval);
}

void ThreadHeapSampler::set_sampling_interval(int sampling_interval) {
  Atomic::release_store(&_sampling_interval, sampling_interval);
}
