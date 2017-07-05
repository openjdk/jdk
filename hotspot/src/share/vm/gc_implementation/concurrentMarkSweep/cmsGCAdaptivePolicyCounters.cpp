/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_cmsGCAdaptivePolicyCounters.cpp.incl"

CMSGCAdaptivePolicyCounters::CMSGCAdaptivePolicyCounters(const char* name_arg,
                                        int collectors,
                                        int generations,
                                        AdaptiveSizePolicy* size_policy_arg)
        : GCAdaptivePolicyCounters(name_arg,
                                   collectors,
                                   generations,
                                   size_policy_arg) {
  if (UsePerfData) {
    EXCEPTION_MARK;
    ResourceMark rm;

    const char* cname =
      PerfDataManager::counter_name(name_space(), "cmsCapacity");
    _cms_capacity_counter = PerfDataManager::create_variable(SUN_GC, cname,
      PerfData::U_Bytes, (jlong) OldSize, CHECK);
#ifdef NOT_PRODUCT
    cname =
      PerfDataManager::counter_name(name_space(), "initialPause");
    _initial_pause_counter = PerfDataManager::create_variable(SUN_GC, cname,
      PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_initial_pause()->last_sample(),
      CHECK);

    cname = PerfDataManager::counter_name(name_space(), "remarkPause");
    _remark_pause_counter = PerfDataManager::create_variable(SUN_GC, cname,
      PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_remark_pause()->last_sample(),
      CHECK);
#endif
    cname =
      PerfDataManager::counter_name(name_space(), "avgInitialPause");
    _avg_initial_pause_counter = PerfDataManager::create_variable(SUN_GC, cname,
      PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_initial_pause()->average(),
      CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgRemarkPause");
    _avg_remark_pause_counter = PerfDataManager::create_variable(SUN_GC, cname,
    PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_remark_pause()->average(),
      CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgSTWGcCost");
    _avg_cms_STW_gc_cost_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_cms_STW_gc_cost()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgSTWTime");
    _avg_cms_STW_time_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
      (jlong) cms_size_policy()->avg_cms_STW_time()->average(),
        CHECK);


    cname = PerfDataManager::counter_name(name_space(), "avgConcurrentTime");
    _avg_concurrent_time_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_concurrent_time()->average(),
        CHECK);

    cname =
      PerfDataManager::counter_name(name_space(), "avgConcurrentInterval");
    _avg_concurrent_interval_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_concurrent_interval()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgConcurrentGcCost");
    _avg_concurrent_gc_cost_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_concurrent_gc_cost()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgCMSFreeAtSweep");
    _avg_cms_free_at_sweep_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_cms_free_at_sweep()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgCMSFree");
    _avg_cms_free_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_cms_free()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgCMSPromo");
    _avg_cms_promo_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_cms_promo()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgMscPause");
    _avg_msc_pause_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_msc_pause()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgMscInterval");
    _avg_msc_interval_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_msc_interval()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "mscGcCost");
    _msc_gc_cost_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_msc_gc_cost()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgMsPause");
    _avg_ms_pause_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_ms_pause()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgMsInterval");
    _avg_ms_interval_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_ms_interval()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "msGcCost");
    _ms_gc_cost_counter = PerfDataManager::create_variable(SUN_GC,
        cname,
        PerfData::U_Ticks,
        (jlong) cms_size_policy()->avg_ms_gc_cost()->average(),
        CHECK);

    cname = PerfDataManager::counter_name(name_space(), "majorGcCost");
    _major_gc_cost_counter = PerfDataManager::create_variable(SUN_GC, cname,
       PerfData::U_Ticks, (jlong) cms_size_policy()->cms_gc_cost(), CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgPromotedAvg");
    _promoted_avg_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        cms_size_policy()->calculated_promo_size_in_bytes(), CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgPromotedDev");
    _promoted_avg_dev_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        (jlong) 0 , CHECK);

    cname = PerfDataManager::counter_name(name_space(), "avgPromotedPaddedAvg");
    _promoted_padded_avg_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        cms_size_policy()->calculated_promo_size_in_bytes(), CHECK);

    cname = PerfDataManager::counter_name(name_space(),
      "changeYoungGenForMajPauses");
    _change_young_gen_for_maj_pauses_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Events,
        (jlong)0, CHECK);

    cname = PerfDataManager::counter_name(name_space(), "remarkPauseOldSlope");
    _remark_pause_old_slope_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        (jlong) cms_size_policy()->remark_pause_old_slope(), CHECK);

    cname = PerfDataManager::counter_name(name_space(), "initialPauseOldSlope");
    _initial_pause_old_slope_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        (jlong) cms_size_policy()->initial_pause_old_slope(), CHECK);

    cname =
      PerfDataManager::counter_name(name_space(), "remarkPauseYoungSlope") ;
    _remark_pause_young_slope_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        (jlong) cms_size_policy()->remark_pause_young_slope(), CHECK);

    cname =
      PerfDataManager::counter_name(name_space(), "initialPauseYoungSlope");
    _initial_pause_young_slope_counter =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
        (jlong) cms_size_policy()->initial_pause_young_slope(), CHECK);


  }
  assert(size_policy()->is_gc_cms_adaptive_size_policy(),
    "Wrong type of size policy");
}

void CMSGCAdaptivePolicyCounters::update_counters() {
  if (UsePerfData) {
    GCAdaptivePolicyCounters::update_counters_from_policy();
    update_counters_from_policy();
  }
}

void CMSGCAdaptivePolicyCounters::update_counters(CMSGCStats* gc_stats) {
  if (UsePerfData) {
    update_counters();
    update_promoted((size_t) gc_stats->avg_promoted()->last_sample());
    update_avg_promoted_avg(gc_stats);
    update_avg_promoted_dev(gc_stats);
    update_avg_promoted_padded_avg(gc_stats);
  }
}

void CMSGCAdaptivePolicyCounters::update_counters_from_policy() {
  if (UsePerfData && (cms_size_policy() != NULL)) {

    GCAdaptivePolicyCounters::update_counters_from_policy();

    update_major_gc_cost_counter();
    update_mutator_cost_counter();

    update_eden_size();
    update_promo_size();

    // If these updates from the last_sample() work,
    // revise the update methods for these counters
    // (both here and in PS).
    update_survived((size_t) cms_size_policy()->avg_survived()->last_sample());

    update_avg_concurrent_time_counter();
    update_avg_concurrent_interval_counter();
    update_avg_concurrent_gc_cost_counter();
#ifdef NOT_PRODUCT
    update_initial_pause_counter();
    update_remark_pause_counter();
#endif
    update_avg_initial_pause_counter();
    update_avg_remark_pause_counter();

    update_avg_cms_STW_time_counter();
    update_avg_cms_STW_gc_cost_counter();

    update_avg_cms_free_counter();
    update_avg_cms_free_at_sweep_counter();
    update_avg_cms_promo_counter();

    update_avg_msc_pause_counter();
    update_avg_msc_interval_counter();
    update_msc_gc_cost_counter();

    update_avg_ms_pause_counter();
    update_avg_ms_interval_counter();
    update_ms_gc_cost_counter();

    update_avg_old_live_counter();

    update_survivor_size_counters();
    update_avg_survived_avg_counters();
    update_avg_survived_dev_counters();

    update_decrement_tenuring_threshold_for_gc_cost();
    update_increment_tenuring_threshold_for_gc_cost();
    update_decrement_tenuring_threshold_for_survivor_limit();

    update_change_young_gen_for_maj_pauses();

    update_major_collection_slope_counter();
    update_remark_pause_old_slope_counter();
    update_initial_pause_old_slope_counter();
    update_remark_pause_young_slope_counter();
    update_initial_pause_young_slope_counter();

    update_decide_at_full_gc_counter();
  }
}
