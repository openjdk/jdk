/*
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

#ifndef RUNTIME_THREADHEAPSAMPLER_HPP
#define RUNTIME_THREADHEAPSAMPLER_HPP

#include "memory/allocation.hpp"

class ThreadHeapSampler {
 private:
  size_t _bytes_until_sample;
  // Cheap random number generator
  static uint64_t _rnd;

  void pick_next_geometric_sample();
  void pick_next_sample(size_t overflowed_bytes = 0);
  static int _enabled;
  static int _sampling_interval;

  // Used for assertion mode to determine if there is a path to a TLAB slow path
  // without a collector present.
  size_t _collectors_present;

  static void init_log_table();

 public:
  ThreadHeapSampler() : _bytes_until_sample(0) {
    _rnd = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(this));
    if (_rnd == 0) {
      _rnd = 1;
    }

    _collectors_present = 0;
  }

  size_t bytes_until_sample()                    { return _bytes_until_sample;   }
  void set_bytes_until_sample(size_t bytes)      { _bytes_until_sample = bytes;  }

  void check_for_sampling(oop obj, size_t size_in_bytes, size_t bytes_allocated_before);

  static int enabled();
  static void enable();
  static void disable();

  static void set_sampling_interval(int sampling_interval);
  static int get_sampling_interval();

  bool sampling_collector_present() const;
  bool remove_sampling_collector();
  bool add_sampling_collector();
};

#endif // SHARE_RUNTIME_THREADHEAPSAMPLER_HPP
