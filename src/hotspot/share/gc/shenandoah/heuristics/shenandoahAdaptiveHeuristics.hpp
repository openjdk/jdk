/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
#define SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP

#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "utilities/numberSeq.hpp"

class ShenandoahAdaptiveHeuristics : public ShenandoahHeuristics {
private:
  TruncatedSeq* _cycle_gap_history;
  TruncatedSeq* _conc_mark_duration_history;
  TruncatedSeq* _conc_uprefs_duration_history;

public:
  ShenandoahAdaptiveHeuristics();

  virtual ~ShenandoahAdaptiveHeuristics();

  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                     RegionData* data, size_t size,
                                                     size_t actual_free);

  void record_cycle_start();

  virtual void record_phase_time(ShenandoahPhaseTimings::Phase phase, double secs);

  virtual bool should_start_normal_gc() const;

  virtual bool should_start_update_refs();

  virtual const char* name();

  virtual bool is_diagnostic();

  virtual bool is_experimental();
};

#endif // SHARE_VM_GC_SHENANDOAH_HEURISTICS_SHENANDOAHADAPTIVEHEURISTICS_HPP
