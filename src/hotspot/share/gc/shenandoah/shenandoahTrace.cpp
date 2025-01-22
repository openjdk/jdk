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

#include "gc/shenandoah/shenandoahEvacInfo.hpp"
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "jfr/jfrEvents.hpp"

void ShenandoahTracer::report_evacuation_info(ShenandoahEvacuationInformation* info) {
  send_evacuation_info_event(info);
}

void ShenandoahTracer::send_evacuation_info_event(ShenandoahEvacuationInformation* info) {
  EventShenandoahEvacuationInformation e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_cSetRegions(info->collection_set_regions());
    e.set_cSetUsedBefore(info->collection_set_used_before());
    e.set_cSetUsedAfter(info->collection_set_used_after());
    e.set_collectedOld(info->collected_old());
    e.set_collectedPromoted(info->collected_promoted());
    e.set_collectedYoung(info->collected_young());
    e.set_regionsPromotedHumongous(info->regions_promoted_humongous());
    e.set_regionsPromotedRegular(info->regions_promoted_regular());
    e.set_regularPromotedGarbage(info->regular_promoted_garbage());
    e.set_regularPromotedFree(info->regular_promoted_free());
    e.set_regionsFreed(info->regions_freed());
    e.set_regionsImmediate(info->regions_immediate());
    e.set_immediateBytes(info->immediate_size());

    e.commit();
  }
}
