/*
 * Copyright (c) 2018, 2026, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHCOMPACTHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHCOMPACTHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"

/*
 * This heuristic has simpler triggers than the adaptive heuristic. The
 * size of the collection set is limited to 3/4 of available memory.
 */
class ShenandoahCompactHeuristics : public ShenandoahHeuristics {
public:
  explicit ShenandoahCompactHeuristics(ShenandoahSpaceInfo* space_info);

  bool should_start_gc() override;
  const char* name() override     { return "Compact"; }
  bool is_diagnostic() override   { return false; }
  bool is_experimental() override { return false; }

  void record_cycle_end() override;

protected:
  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;

private:
  size_t _bytes_used_at_end_of_gc;
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHCOMPACTHEURISTICS_HPP
