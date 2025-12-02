/*
* Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFREESETPARTITIONID_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFREESETPARTITIONID_HPP

#include "utilities/globalDefinitions.hpp"

// Each ShenandoahHeapRegion is associated with a ShenandoahFreeSetPartitionId.
enum class ShenandoahFreeSetPartitionId : uint8_t {
  Mutator,                      // Region is in the Mutator free set: available memory is available to mutators.
  Collector,                    // Region is in the Collector free set: available memory is reserved for evacuations.
  OldCollector,                 // Region is in the Old Collector free set:
                                //    available memory is reserved for old evacuations and for promotions.
  NotFree                       // Region is in no free set: it has no available memory.  Consult region affiliation
                                //    to determine whether this retired region is young or old.  If young, the region
                                //    is considered to be part of the Mutator partition.  (When we retire from the
                                //    Collector partition, we decrease total_region_count for Collector and increaese
                                //    for Mutator, making similar adjustments to used (net impact on available is neutral).
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHFREESETPARTITIONID_HPP
