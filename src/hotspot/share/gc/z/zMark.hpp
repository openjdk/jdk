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

#ifndef SHARE_GC_Z_ZMARK_HPP
#define SHARE_GC_Z_ZMARK_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zMarkingSMR.hpp"
#include "gc/z/zMarkStack.hpp"
#include "gc/z/zMarkStackEntry.hpp"
#include "gc/z/zMarkTerminate.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

class Thread;
class ZGeneration;
class ZMarkContext;
class ZPageTable;
class ZWorkers;

class ZMark {
  friend class ZMarkTask;

public:
  static const bool Resurrect     = true;
  static const bool DontResurrect = false;

  static const bool GCThread      = true;
  static const bool AnyThread     = false;

  static const bool Follow        = true;
  static const bool DontFollow    = false;

  static const bool Strong        = false;
  static const bool Finalizable   = true;

private:
  ZGeneration* const _generation;
  ZPageTable* const  _page_table;
  ZMarkingSMR        _marking_smr;
  ZMarkStripeSet     _stripes;
  ZMarkTerminate     _terminate;
  Atomic<size_t>     _work_nproactiveflush;
  Atomic<size_t>     _work_nterminateflush;
  size_t             _nproactiveflush;
  size_t             _nterminateflush;
  size_t             _ntrycomplete;
  size_t             _ncontinue;
  uint               _nworkers;

  size_t calculate_nstripes(uint nworkers) const;

  bool is_array(zaddress addr) const;
  void push_partial_array(zpointer* addr, size_t length, bool finalizable);
  void follow_array_elements_small(zpointer* addr, size_t length, bool finalizable);
  void follow_array_elements_large(zpointer* addr, size_t length, bool finalizable);
  void follow_array_elements(zpointer* addr, size_t length, bool finalizable);
  void follow_partial_array(ZMarkStackEntry entry, bool finalizable);
  void follow_array_object(objArrayOop obj, bool finalizable);
  void follow_object(oop obj, bool finalizable);
  void mark_and_follow(ZMarkContext* context, ZMarkStackEntry entry);

  bool rebalance_work(ZMarkContext* context);
  bool drain(ZMarkContext* context);
  bool try_steal_local(ZMarkContext* context);
  bool try_steal_global(ZMarkContext* context);
  bool try_steal(ZMarkContext* context);
  bool flush();
  bool try_proactive_flush();
  bool try_terminate(ZMarkContext* context);
  void leave();
  bool try_end();

  ZWorkers* workers() const;

  bool follow_work(bool partial);

  void verify_all_stacks_empty() const;
  void verify_worker_stacks_empty() const;

public:
  ZMark(ZGeneration* generation, ZPageTable* page_table);

  template <bool resurrect, bool gc_thread, bool follow, bool finalizable>
  void mark_object(zaddress addr);

  void start();
  void mark_young_roots();
  void mark_old_roots();
  void mark_follow();
  bool end();
  void free();

  bool flush(Thread* thread);

  // Following work
  void prepare_work();
  void finish_work();
  void resize_workers(uint nworkers);
  void follow_work_complete();
  bool follow_work_partial();
  bool try_terminate_flush();
};

#endif // SHARE_GC_Z_ZMARK_HPP
