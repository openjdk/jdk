/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_SERVICES_HEAPOBJECTSTATISTICS_HPP
#define SHARE_SERVICES_HEAPOBJECTSTATISTICS_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/task.hpp"
#include "runtime/vmOperation.hpp"

class outputStream;

class HeapObjectStatisticsTask : public PeriodicTask {
public:
  HeapObjectStatisticsTask();
  void task();
};

class HeapObjectStatistics : public CHeapObj<mtGC> {
private:
  static const int HISTOGRAM_SIZE = 16;

  static HeapObjectStatistics* _instance;

  HeapObjectStatisticsTask _task;
  uint64_t _num_samples;
  uint64_t _num_objects;
  uint64_t _num_ihashed;
  uint64_t _num_ihashed_moved;
  uint64_t _num_locked;
  uint64_t _lds;

  static void increase_counter(uint64_t& counter, uint64_t val = 1);

  void print(outputStream* out) const;

public:
  static void initialize();
  static void shutdown();

  static HeapObjectStatistics* instance();

  HeapObjectStatistics();
  void start();
  void stop();

  void begin_sample();
  void visit_object(oop object);
};

#endif // SHARE_SERVICES_HEAPOBJECTSTATISTICS_HPP
