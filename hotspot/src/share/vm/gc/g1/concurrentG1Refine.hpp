/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1YoungRemSetSamplingThread.hpp"
#include "memory/allocation.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward decl
class ConcurrentG1RefineThread;
class G1CollectedHeap;
class G1HotCardCache;
class G1RegionToSpaceMapper;
class G1RemSet;
class DirtyCardQueue;

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
  int _green_zone;
  int _yellow_zone;
  int _red_zone;

  int _thread_threshold_step;

  // We delay the refinement of 'hot' cards using the hot card cache.
  G1HotCardCache _hot_card_cache;

  // Reset the threshold step value based of the current zone boundaries.
  void reset_threshold_step();

  ConcurrentG1Refine(G1CollectedHeap* g1h);

 public:
  ~ConcurrentG1Refine();

  // Returns ConcurrentG1Refine instance if succeeded to create/initialize ConcurrentG1Refine and ConcurrentG1RefineThread.
  // Otherwise, returns NULL with error code.
  static ConcurrentG1Refine* create(G1CollectedHeap* g1h, CardTableEntryClosure* refine_closure, jint* ecode);

  void init(G1RegionToSpaceMapper* card_counts_storage);
  void stop();

  void reinitialize_threads();

  // Iterate over all concurrent refinement threads
  void threads_do(ThreadClosure *tc);

  // Iterate over all worker refinement threads
  void worker_threads_do(ThreadClosure * tc);

  // The RS sampling thread has nothing to do with refinement, but is here for now.
  G1YoungRemSetSamplingThread * sampling_thread() const { return _sample_thread; }

  static uint thread_num();

  void print_worker_threads_on(outputStream* st) const;

  void set_green_zone(int x)  { _green_zone = x;  }
  void set_yellow_zone(int x) { _yellow_zone = x; }
  void set_red_zone(int x)    { _red_zone = x;    }

  int green_zone() const      { return _green_zone;  }
  int yellow_zone() const     { return _yellow_zone; }
  int red_zone() const        { return _red_zone;    }

  uint worker_thread_num() const { return _n_worker_threads; }

  int thread_threshold_step() const { return _thread_threshold_step; }

  G1HotCardCache* hot_card_cache() { return &_hot_card_cache; }

  static bool hot_card_cache_enabled() { return G1HotCardCache::default_use_cache(); }
};

#endif // SHARE_VM_GC_G1_CONCURRENTG1REFINE_HPP
