/*
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

#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "jfr/jfrEvents.hpp"

void ShenandoahTracer::report_evacuation_info(const ShenandoahCollectionSet* cset,
    size_t free_regions, size_t regions_immediate, size_t immediate_size) {

  EventShenandoahEvacuationInformation e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_cSetRegions(cset->count());
    e.set_cSetUsedBefore(cset->used());
    e.set_cSetUsedAfter(cset->live());
    e.set_freeRegions(free_regions);
    e.set_regionsImmediate(regions_immediate);
    e.set_immediateBytes(immediate_size);

    e.commit();
  }
}

void ShenandoahTracer::report_promotion_info(const ShenandoahCollectionSet* cset, const ShenandoahInPlacePromotionPlanner& planner) {
  EventShenandoahPromotionInformation e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_collectedOld(cset->get_live_bytes_in_old_regions());
    e.set_collectedPromoted(cset->get_live_bytes_in_tenurable_regions());
    e.set_collectedYoung(cset->get_live_bytes_in_untenurable_regions());
    e.set_regionsPromotedHumongous(planner.humongous_region_stats().count);
    e.set_humongousPromotedGarbage(planner.humongous_region_stats().garbage);
    e.set_humongousPromotedFree(planner.humongous_region_stats().free);
    e.set_regionsPromotedRegular(planner.regular_region_stats().count);
    e.set_regularPromotedGarbage(planner.regular_region_stats().garbage);
    e.set_regularPromotedFree(planner.regular_region_stats().free);

    e.commit();
  }
}
