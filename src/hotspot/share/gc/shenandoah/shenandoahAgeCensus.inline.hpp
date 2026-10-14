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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_INLINE_HPP

#include "gc/shenandoah/shenandoahAgeCensus.hpp"

CENSUS_NOISE(void ShenandoahAgeCensus::add(uint obj_age, uint region_age, uint region_youth, size_t size, uint worker_id) {)
NO_CENSUS_NOISE(void ShenandoahAgeCensus::add(uint obj_age, uint region_age, size_t size, uint worker_id) {)
  if (obj_age <= markWord::max_age) {
    assert(obj_age < MAX_COHORTS && region_age < MAX_COHORTS, "Should have been tenured");
#ifdef SHENANDOAH_CENSUS_NOISE
    // Region ageing is stochastic and non-monotonic; this vitiates mortality
    // demographics in ways that might defeat our algorithms. Marking may be a
    // time when we might be able to correct this, but we currently do not do
    // this. Like skipped statistics further below, we want to track the
    // impact of this noise to see if this may be worthwhile. JDK-<TBD>.
    uint age = obj_age;
    if (region_age > 0) {
      add_aged(size, worker_id);   // this tracking is coarse for now
      age += region_age;
      if (age >= MAX_COHORTS) {
        age = (uint)(MAX_COHORTS - 1);  // clamp
        add_clamped(size, worker_id);
      }
    }
    if (region_youth > 0) {   // track object volume with retrograde age
      add_young(size, worker_id);
    }
#else   // SHENANDOAH_CENSUS_NOISE
    uint age = MIN2(obj_age + region_age, (uint)(MAX_COHORTS - 1));  // clamp
#endif  // SHENANDOAH_CENSUS_NOISE
    get_local_age_table(worker_id)->add(age, size);
  } else {
    // update skipped statistics
    CENSUS_NOISE(add_skipped(size, worker_id);)
  }
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHAGECENSUS_INLINE_HPP
