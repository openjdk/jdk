/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHPASSIVEHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHPASSIVEHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"

/*
 * The passive heuristic is for use only with the passive mode. In
 * the passive mode, Shenandoah only performs STW (i.e., degenerated)
 * collections. All the barriers are disabled and there are no concurrent
 * activities. Therefore, this heuristic _never_ triggers a cycle. It
 * will select regions for evacuation based on ShenandoahEvacReserve,
 * ShenandoahEvacWaste and ShenandoahGarbageThreshold. Note that it does
 * not attempt to evacuate regions with more garbage.
 */
class ShenandoahPassiveHeuristics : public ShenandoahHeuristics {
public:
  ShenandoahPassiveHeuristics(ShenandoahSpaceInfo* space_info);

  bool should_start_gc() override;

  bool should_unload_classes() override;

  bool should_degenerate_cycle() override;

  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                             RegionData* data, size_t data_size,
                                             size_t free) override;

  const char* name() override     { return "Passive"; }
  bool is_diagnostic() override   { return true; }
  bool is_experimental() override { return false; }
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHPASSIVEHEURISTICS_HPP
