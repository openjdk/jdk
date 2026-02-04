/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZRELOCATE_HPP
#define SHARE_GC_Z_ZRELOCATE_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zValue.hpp"
#include "runtime/atomic.hpp"

class ZForwarding;
class ZGeneration;
class ZWorkers;

typedef size_t ZForwardingCursor;

class ZRelocateQueue {
private:
  ZConditionLock       _lock;
  ZArray<ZForwarding*> _queue;
  uint                 _nworkers;
  uint                 _nsynchronized;
  bool                 _synchronize;
  Atomic<bool>         _is_active;
  Atomic<int>          _needs_attention;

  bool needs_attention() const;
  void inc_needs_attention();
  void dec_needs_attention();

  bool prune();
  ZForwarding* prune_and_claim();

public:
  ZRelocateQueue();

  void activate(uint nworkers);
  void deactivate();
  bool is_active() const;

  void join(uint nworkers);
  void resize_workers(uint nworkers);
  void leave();

  void add_and_wait(ZForwarding* forwarding);

  ZForwarding* synchronize_poll();
  void synchronize_thread();
  void desynchronize_thread();

  void clear();

  void synchronize();
  void desynchronize();
};

class ZRelocationTargets {
private:
  using TargetArray = ZPage*[ZNumRelocationAges];

  ZPerNUMA<TargetArray> _targets;

public:
  ZRelocationTargets();

  ZPage* get(uint32_t partition_id, ZPageAge age);
  void set(uint32_t partition_id, ZPageAge age, ZPage* page);

  template <typename Function>
  void apply_and_clear_targets(Function function);
};

class ZRelocate {
  friend class ZRelocateTask;

private:
  ZGeneration* const                       _generation;
  ZRelocateQueue                           _queue;
  ZPerNUMA<ZRelocationSetParallelIterator> _iters;
  ZPerWorker<ZRelocationTargets>           _small_targets;
  ZPerWorker<ZRelocationTargets>           _medium_targets;
  ZRelocationTargets                       _shared_medium_targets;

  ZWorkers* workers() const;

public:
  ZRelocate(ZGeneration* generation);

  void start();

  static void add_remset(volatile zpointer* p);

  static ZPageAge compute_to_age(ZPageAge from_age);

  zaddress relocate_object(ZForwarding* forwarding, zaddress_unsafe from_addr);
  zaddress forward_object(ZForwarding* forwarding, zaddress_unsafe from_addr);

  void relocate(ZRelocationSet* relocation_set);

  void flip_age_pages(const ZArray<ZPage*>* pages);
  void barrier_promoted_pages(const ZArray<ZPage*>* flip_promoted_pages,
                              const ZArray<ZPage*>* relocate_promoted_pages);

  void synchronize();
  void desynchronize();

  ZRelocateQueue* queue();

  bool is_queue_active() const;
};

#endif // SHARE_GC_Z_ZRELOCATE_HPP
