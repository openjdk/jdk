/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// CMSGCAdaptivePolicyCounters is a holder class for performance counters
// that track the data and decisions for the ergonomics policy for the
// concurrent mark sweep collector

class CMSGCAdaptivePolicyCounters : public GCAdaptivePolicyCounters {
  friend class VMStructs;

 private:

  // Capacity of tenured generation recorded at the end of
  // any collection.
  PerfVariable* _cms_capacity_counter; // Make this common with PS _old_capacity

  // Average stop-the-world pause time for both initial and
  // remark pauses sampled at the end of the checkpointRootsFinalWork.
  PerfVariable* _avg_cms_STW_time_counter;
  // Average stop-the-world (STW) GC cost for the STW pause time
  // _avg_cms_STW_time_counter.
  PerfVariable* _avg_cms_STW_gc_cost_counter;

#ifdef NOT_PRODUCT
  // These are useful to see how the most recent values of these
  // counters compare to their respective averages but
  // do not control behavior.
  PerfVariable* _initial_pause_counter;
  PerfVariable* _remark_pause_counter;
#endif

  // Average of the initial marking pause for a concurrent collection.
  PerfVariable* _avg_initial_pause_counter;
  // Average of the remark pause for a concurrent collection.
  PerfVariable* _avg_remark_pause_counter;

  // Average for the sum of all the concurrent times per collection.
  PerfVariable* _avg_concurrent_time_counter;
  // Average for the time between the most recent end of a
  // concurrent collection and the beginning of the next
  // concurrent collection.
  PerfVariable* _avg_concurrent_interval_counter;
  // Average of the concurrent GC costs based on _avg_concurrent_time_counter
  // and _avg_concurrent_interval_counter.
  PerfVariable* _avg_concurrent_gc_cost_counter;

  // Average of the free space in the tenured generation at the
  // end of the sweep of the tenured generation.
  PerfVariable* _avg_cms_free_counter;
  // Average of the free space in the tenured generation at the
  // start of the sweep of the tenured generation.
  PerfVariable* _avg_cms_free_at_sweep_counter;
  // Average of the free space in the tenured generation at the
  // after any resizing of the tenured generation at the end
  // of a collection of the tenured generation.
  PerfVariable* _avg_cms_promo_counter;

  // Average of  the mark-sweep-compact (MSC) pause time for a collection
  // of the tenured generation.
  PerfVariable* _avg_msc_pause_counter;
  // Average for the time between the most recent end of a
  // MSC collection and the beginning of the next
  // MSC collection.
  PerfVariable* _avg_msc_interval_counter;
  // Average for the GC cost of a MSC collection based on
  // _avg_msc_pause_counter and _avg_msc_interval_counter.
  PerfVariable* _msc_gc_cost_counter;

  // Average of  the mark-sweep (MS) pause time for a collection
  // of the tenured generation.
  PerfVariable* _avg_ms_pause_counter;
  // Average for the time between the most recent end of a
  // MS collection and the beginning of the next
  // MS collection.
  PerfVariable* _avg_ms_interval_counter;
  // Average for the GC cost of a MS collection based on
  // _avg_ms_pause_counter and _avg_ms_interval_counter.
  PerfVariable* _ms_gc_cost_counter;

  // Average of the bytes promoted per minor collection.
  PerfVariable* _promoted_avg_counter;
  // Average of the deviation of the promoted average
  PerfVariable* _promoted_avg_dev_counter;
  // Padded average of the bytes promoted per minor colleciton
  PerfVariable* _promoted_padded_avg_counter;

  // See description of the _change_young_gen_for_maj_pauses
  // variable recently in cmsAdaptiveSizePolicy.hpp.
  PerfVariable* _change_young_gen_for_maj_pauses_counter;

  // See descriptions of _remark_pause_old_slope, _initial_pause_old_slope,
  // etc. variables recently in cmsAdaptiveSizePolicy.hpp.
  PerfVariable* _remark_pause_old_slope_counter;
  PerfVariable* _initial_pause_old_slope_counter;
  PerfVariable* _remark_pause_young_slope_counter;
  PerfVariable* _initial_pause_young_slope_counter;

  CMSAdaptiveSizePolicy* cms_size_policy() {
    assert(_size_policy->kind() ==
      AdaptiveSizePolicy::_gc_cms_adaptive_size_policy,
      "Wrong size policy");
    return (CMSAdaptiveSizePolicy*)_size_policy;
  }

  inline void update_avg_cms_STW_time_counter() {
    _avg_cms_STW_time_counter->set_value(
      (jlong) (cms_size_policy()->avg_cms_STW_time()->average() *
      (double) MILLIUNITS));
  }

  inline void update_avg_cms_STW_gc_cost_counter() {
    _avg_cms_STW_gc_cost_counter->set_value(
      (jlong) (cms_size_policy()->avg_cms_STW_gc_cost()->average() * 100.0));
  }

  inline void update_avg_initial_pause_counter() {
    _avg_initial_pause_counter->set_value(
      (jlong) (cms_size_policy()->avg_initial_pause()->average() *
      (double) MILLIUNITS));
  }
#ifdef NOT_PRODUCT
  inline void update_avg_remark_pause_counter() {
    _avg_remark_pause_counter->set_value(
      (jlong) (cms_size_policy()-> avg_remark_pause()->average() *
      (double) MILLIUNITS));
  }

  inline void update_initial_pause_counter() {
    _initial_pause_counter->set_value(
      (jlong) (cms_size_policy()->avg_initial_pause()->average() *
      (double) MILLIUNITS));
  }
#endif
  inline void update_remark_pause_counter() {
    _remark_pause_counter->set_value(
      (jlong) (cms_size_policy()-> avg_remark_pause()->last_sample() *
      (double) MILLIUNITS));
  }

  inline void update_avg_concurrent_time_counter() {
    _avg_concurrent_time_counter->set_value(
      (jlong) (cms_size_policy()->avg_concurrent_time()->last_sample() *
      (double) MILLIUNITS));
  }

  inline void update_avg_concurrent_interval_counter() {
    _avg_concurrent_interval_counter->set_value(
      (jlong) (cms_size_policy()->avg_concurrent_interval()->average() *
      (double) MILLIUNITS));
  }

  inline void update_avg_concurrent_gc_cost_counter() {
    _avg_concurrent_gc_cost_counter->set_value(
      (jlong) (cms_size_policy()->avg_concurrent_gc_cost()->average() * 100.0));
  }

  inline void update_avg_cms_free_counter() {
    _avg_cms_free_counter->set_value(
      (jlong) cms_size_policy()->avg_cms_free()->average());
  }

  inline void update_avg_cms_free_at_sweep_counter() {
    _avg_cms_free_at_sweep_counter->set_value(
      (jlong) cms_size_policy()->avg_cms_free_at_sweep()->average());
  }

  inline void update_avg_cms_promo_counter() {
    _avg_cms_promo_counter->set_value(
      (jlong) cms_size_policy()->avg_cms_promo()->average());
  }

  inline void update_avg_old_live_counter() {
    _avg_old_live_counter->set_value(
      (jlong)(cms_size_policy()->avg_old_live()->average())
    );
  }

  inline void update_avg_msc_pause_counter() {
    _avg_msc_pause_counter->set_value(
      (jlong) (cms_size_policy()->avg_msc_pause()->average() *
      (double) MILLIUNITS));
  }

  inline void update_avg_msc_interval_counter() {
    _avg_msc_interval_counter->set_value(
      (jlong) (cms_size_policy()->avg_msc_interval()->average() *
      (double) MILLIUNITS));
  }

  inline void update_msc_gc_cost_counter() {
    _msc_gc_cost_counter->set_value(
      (jlong) (cms_size_policy()->avg_msc_gc_cost()->average() * 100.0));
  }

  inline void update_avg_ms_pause_counter() {
    _avg_ms_pause_counter->set_value(
      (jlong) (cms_size_policy()->avg_ms_pause()->average() *
      (double) MILLIUNITS));
  }

  inline void update_avg_ms_interval_counter() {
    _avg_ms_interval_counter->set_value(
      (jlong) (cms_size_policy()->avg_ms_interval()->average() *
      (double) MILLIUNITS));
  }

  inline void update_ms_gc_cost_counter() {
    _ms_gc_cost_counter->set_value(
      (jlong) (cms_size_policy()->avg_ms_gc_cost()->average() * 100.0));
  }

  inline void update_major_gc_cost_counter() {
    _major_gc_cost_counter->set_value(
      (jlong)(cms_size_policy()->cms_gc_cost() * 100.0)
    );
  }
  inline void update_mutator_cost_counter() {
    _mutator_cost_counter->set_value(
      (jlong)(cms_size_policy()->mutator_cost() * 100.0)
    );
  }

  inline void update_avg_promoted_avg(CMSGCStats* gc_stats) {
    _promoted_avg_counter->set_value(
      (jlong)(gc_stats->avg_promoted()->average())
    );
  }
  inline void update_avg_promoted_dev(CMSGCStats* gc_stats) {
    _promoted_avg_dev_counter->set_value(
      (jlong)(gc_stats->avg_promoted()->deviation())
    );
  }
  inline void update_avg_promoted_padded_avg(CMSGCStats* gc_stats) {
    _promoted_padded_avg_counter->set_value(
      (jlong)(gc_stats->avg_promoted()->padded_average())
    );
  }
  inline void update_remark_pause_old_slope_counter() {
    _remark_pause_old_slope_counter->set_value(
      (jlong)(cms_size_policy()->remark_pause_old_slope() * 1000)
    );
  }
  inline void update_initial_pause_old_slope_counter() {
    _initial_pause_old_slope_counter->set_value(
      (jlong)(cms_size_policy()->initial_pause_old_slope() * 1000)
    );
  }
  inline void update_remark_pause_young_slope_counter() {
    _remark_pause_young_slope_counter->set_value(
      (jlong)(cms_size_policy()->remark_pause_young_slope() * 1000)
    );
  }
  inline void update_initial_pause_young_slope_counter() {
    _initial_pause_young_slope_counter->set_value(
      (jlong)(cms_size_policy()->initial_pause_young_slope() * 1000)
    );
  }
  inline void update_change_young_gen_for_maj_pauses() {
    _change_young_gen_for_maj_pauses_counter->set_value(
      cms_size_policy()->change_young_gen_for_maj_pauses());
  }

 public:
  CMSGCAdaptivePolicyCounters(const char* name, int collectors, int generations,
                              AdaptiveSizePolicy* size_policy);

  // update counters
  void update_counters();
  void update_counters(CMSGCStats* gc_stats);
  void update_counters_from_policy();

  inline void update_cms_capacity_counter(size_t size_in_bytes) {
    _cms_capacity_counter->set_value(size_in_bytes);
  }

  virtual GCPolicyCounters::Name kind() const {
    return GCPolicyCounters::CMSGCAdaptivePolicyCountersKind;
  }
};
