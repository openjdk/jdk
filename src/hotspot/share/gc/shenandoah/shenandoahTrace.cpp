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
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "jfr/jfrEvents.hpp"

void ShenandoahTracer::report_evacuation_info(const ShenandoahCollectionSet* cset,
    size_t free_regions, size_t regions_promoted_humongous, size_t regions_promoted_regular,
    size_t regular_promoted_garbage, size_t regular_promoted_free, size_t regions_immediate,
    size_t immediate_size) {

  EventShenandoahEvacuationInformation e;
  if (e.should_commit()) {
    e.set_gcId(GCId::current());
    e.set_cSetRegions(cset->count());
    e.set_cSetUsedBefore(cset->used());
    e.set_cSetUsedAfter(cset->live());
    e.set_collectedOld(cset->get_old_bytes_reserved_for_evacuation());
    e.set_collectedPromoted(cset->get_young_bytes_to_be_promoted());
    e.set_collectedYoung(cset->get_young_bytes_reserved_for_evacuation());
    e.set_regionsPromotedHumongous(regions_promoted_humongous);
    e.set_regionsPromotedRegular(regions_promoted_regular);
    e.set_regularPromotedGarbage(regular_promoted_garbage);
    e.set_regularPromotedFree(regular_promoted_free);
    e.set_freeRegions(free_regions);
    e.set_regionsImmediate(regions_immediate);
    e.set_immediateBytes(immediate_size);

    e.commit();
  }
}
