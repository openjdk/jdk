/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTGC_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTGC_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"

class ShenandoahGeneration;

class VM_ShenandoahInitMark;
class VM_ShenandoahFinalMarkStartEvac;
class VM_ShenandoahInitUpdateRefs;
class VM_ShenandoahFinalUpdateRefs;

class ShenandoahConcurrentGC : public ShenandoahGC {
  friend class VM_ShenandoahInitMark;
  friend class VM_ShenandoahFinalMarkStartEvac;
  friend class VM_ShenandoahInitUpdateRefs;
  friend class VM_ShenandoahFinalUpdateRefs;
  friend class VM_ShenandoahFinalRoots;

protected:
  ShenandoahConcurrentMark    _mark;
  ShenandoahGeneration* const _generation;

private:
  ShenandoahDegenPoint        _degen_point;
  bool                        _abbreviated;
  const bool                  _do_old_gc_bootstrap;

public:
  ShenandoahConcurrentGC(ShenandoahGeneration* generation, bool do_old_gc_bootstrap);

  bool collect(GCCause::Cause cause) override;
  ShenandoahDegenPoint degen_point() const;

  void entry_concurrent_update_refs_prepare(ShenandoahHeap* heap);

  // Return true if this cycle found enough immediate garbage to skip evacuation
  bool abbreviated() const { return _abbreviated; }

protected:
  // Entry points to STW GC operations, these cause a related safepoint, that then
  // call the entry method below
  void vmop_entry_init_mark();
  void vmop_entry_final_mark();
  void vmop_entry_init_update_refs();
  void vmop_entry_final_update_refs();
  void vmop_entry_verify_final_roots();

  // Entry methods to normally STW GC operations. These set up logging, monitoring
  // and workers for next VM operation
  void entry_init_mark();
  void entry_final_mark();
  void entry_init_update_refs();
  void entry_final_update_refs();
  void entry_verify_final_roots();

  // Entry methods to normally concurrent GC operations. These set up logging, monitoring
  // for concurrent operation.
  void entry_reset();
  void entry_mark_roots();
  void entry_scan_remembered_set();
  void entry_mark();
  void entry_thread_roots();
  void entry_weak_refs();
  void entry_weak_roots();
  void entry_class_unloading();
  void entry_strong_roots();
  void entry_cleanup_early();
  void entry_evacuate();
  void entry_update_thread_roots();
  void entry_update_refs();
  void entry_cleanup_complete();

  // This is the last phase of a cycle which performs no evacuations
  bool entry_final_roots();

  // Called when the collection set is empty, but the generational mode has regions to promote in place
  void entry_promote_in_place() const;

  // Actual work for the phases
  void op_reset();
  void op_init_mark();
  void op_mark_roots();
  void op_mark();
  virtual void op_final_mark();
  void op_thread_roots();
  void op_weak_refs();
  void op_weak_roots();
  void op_class_unloading();
  void op_strong_roots();
  void op_cleanup_early();
  void op_evacuate();
  void op_init_update_refs();
  void op_update_refs();
  void op_update_thread_roots();
  void op_final_update_refs();

  void op_verify_final_roots();
  void op_cleanup_complete();
  void op_reset_after_collect();

  // Check GC cancellation and abort concurrent GC
  bool check_cancellation_and_abort(ShenandoahDegenPoint point);

  // Called when concurrent GC succeeds.
  void entry_reset_after_collect();

private:
  void start_mark();

  bool complete_abbreviated_cycle();

  static bool has_in_place_promotions(ShenandoahHeap* heap);

  // Messages for GC trace events, they have to be immortal for
  // passing around the logging/tracing systems
  const char* init_mark_event_message() const;
  const char* final_mark_event_message() const;
  const char* verify_final_roots_event_message() const;
  const char* conc_final_roots_event_message() const;
  const char* conc_mark_event_message() const;
  const char* conc_reset_event_message() const;
  const char* conc_reset_after_collect_event_message() const;
  const char* conc_weak_refs_event_message() const;
  const char* conc_weak_roots_event_message() const;
  const char* conc_cleanup_event_message() const;
  const char* conc_init_update_refs_event_message() const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTGC_HPP
