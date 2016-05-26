/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_CONCURRENTG1REFINE_HPP
#define SHARE_VM_GC_G1_CONCURRENTG1REFINE_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward decl
class CardTableEntryClosure;
class ConcurrentG1RefineThread;
class G1YoungRemSetSamplingThread;
class outputStream;
class ThreadClosure;

class ConcurrentG1Refine: public CHeapObj<mtGC> {
  G1YoungRemSetSamplingThread* _sample_thread;

  ConcurrentG1RefineThread** _threads;
  uint _n_worker_threads;
 /*
  * The value of the update buffer queue length falls into one of 3 zones:
  * green, yellow, red. If the value is in [0, green) nothing is
  * done, the buffers are left unprocessed to enable the caching effect of the
  * dirtied cards. In the yellow zone [green, yellow) the concurrent refinement
  * threads are gradually activated. In [yellow, red) all threads are
  * running. If the length becomes red (max queue length) the mutators start
  * processing the buffers.
  *
  * There are some interesting cases (when G1UseAdaptiveConcRefinement
  * is turned off):
  * 1) green = yellow = red = 0. In this case the mutator will process all
  *    buffers. Except for those that are created by the deferred updates
  *    machinery during a collection.
  * 2) green = 0. Means no caching. Can be a good way to minimize the
  *    amount of time spent updating rsets during a collection.
  */
  size_t _green_zone;
  size_t _yellow_zone;
  size_t _red_zone;
  size_t _min_yellow_zone_size;

  ConcurrentG1Refine(size_t green_zone,
                     size_t yellow_zone,
                     size_t red_zone,
                     size_t min_yellow_zone_size);

  // Update green/yellow/red zone values based on how well goals are being met.
  void update_zones(double update_rs_time,
                    size_t update_rs_processed_buffers,
                    double goal_ms);

  // Update thread thresholds to account for updated zone values.
  void update_thread_thresholds();

 public:
  ~ConcurrentG1Refine();

  // Returns ConcurrentG1Refine instance if succeeded to create/initialize ConcurrentG1Refine and ConcurrentG1RefineThread.
  // Otherwise, returns NULL with error code.
  static ConcurrentG1Refine* create(CardTableEntryClosure* refine_closure, jint* ecode);

  void stop();

  void adjust(double update_rs_time, size_t update_rs_processed_buffers, double goal_ms);

  // Iterate over all concurrent refinement threads
  void threads_do(ThreadClosure *tc);

  // Iterate over all worker refinement threads
  void worker_threads_do(ThreadClosure * tc);

  // The RS sampling thread has nothing to do with refinement, but is here for now.
  G1YoungRemSetSamplingThread * sampling_thread() const { return _sample_thread; }

  static uint thread_num();

  void print_worker_threads_on(outputStream* st) const;

  size_t green_zone() const      { return _green_zone;  }
  size_t yellow_zone() const     { return _yellow_zone; }
  size_t red_zone() const        { return _red_zone;    }
};

#endif // SHARE_VM_GC_G1_CONCURRENTG1REFINE_HPP
