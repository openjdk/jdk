/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
#include "incls/_precompiled.incl"
#include "incls/_cmsAdaptiveSizePolicy.cpp.incl"

elapsedTimer CMSAdaptiveSizePolicy::_concurrent_timer;
elapsedTimer CMSAdaptiveSizePolicy::_STW_timer;

// Defined if the granularity of the time measurements is potentially too large.
#define CLOCK_GRANULARITY_TOO_LARGE

CMSAdaptiveSizePolicy::CMSAdaptiveSizePolicy(size_t init_eden_size,
                                             size_t init_promo_size,
                                             size_t init_survivor_size,
                                             double max_gc_minor_pause_sec,
                                             double max_gc_pause_sec,
                                             uint gc_cost_ratio) :
  AdaptiveSizePolicy(init_eden_size,
                     init_promo_size,
                     init_survivor_size,
                     max_gc_pause_sec,
                     gc_cost_ratio) {

  clear_internal_time_intervals();

  _processor_count = os::active_processor_count();

  if (CMSConcurrentMTEnabled && (ConcGCThreads > 1)) {
    assert(_processor_count > 0, "Processor count is suspect");
    _concurrent_processor_count = MIN2((uint) ConcGCThreads,
                                       (uint) _processor_count);
  } else {
    _concurrent_processor_count = 1;
  }

  _avg_concurrent_time  = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_concurrent_interval = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_concurrent_gc_cost = new AdaptiveWeightedAverage(AdaptiveTimeWeight);

  _avg_initial_pause    = new AdaptivePaddedAverage(AdaptiveTimeWeight,
                                                    PausePadding);
  _avg_remark_pause     = new AdaptivePaddedAverage(AdaptiveTimeWeight,
                                                    PausePadding);

  _avg_cms_STW_time     = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_cms_STW_gc_cost  = new AdaptiveWeightedAverage(AdaptiveTimeWeight);

  _avg_cms_free         = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_cms_free_at_sweep = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_cms_promo        = new AdaptiveWeightedAverage(AdaptiveTimeWeight);

  // Mark-sweep-compact
  _avg_msc_pause        = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_msc_interval     = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_msc_gc_cost      = new AdaptiveWeightedAverage(AdaptiveTimeWeight);

  // Mark-sweep
  _avg_ms_pause = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_ms_interval      = new AdaptiveWeightedAverage(AdaptiveTimeWeight);
  _avg_ms_gc_cost       = new AdaptiveWeightedAverage(AdaptiveTimeWeight);

  // Variables that estimate pause times as a function of generation
  // size.
  _remark_pause_old_estimator =
    new LinearLeastSquareFit(AdaptiveSizePolicyWeight);
  _initial_pause_old_estimator =
    new LinearLeastSquareFit(AdaptiveSizePolicyWeight);
  _remark_pause_young_estimator =
    new LinearLeastSquareFit(AdaptiveSizePolicyWeight);
  _initial_pause_young_estimator =
    new LinearLeastSquareFit(AdaptiveSizePolicyWeight);

  // Alignment comes from that used in ReservedSpace.
  _generation_alignment = os::vm_allocation_granularity();

  // Start the concurrent timer here so that the first
  // concurrent_phases_begin() measures a finite mutator
  // time.  A finite mutator time is used to determine
  // if a concurrent collection has been started.  If this
  // proves to be a problem, use some explicit flag to
  // signal that a concurrent collection has been started.
  _concurrent_timer.start();
  _STW_timer.start();
}

double CMSAdaptiveSizePolicy::concurrent_processor_fraction() {
  // For now assume no other daemon threads are taking alway
  // cpu's from the application.
  return ((double) _concurrent_processor_count / (double) _processor_count);
}

double CMSAdaptiveSizePolicy::concurrent_collection_cost(
                                                  double interval_in_seconds) {
  //  When the precleaning and sweeping phases use multiple
  // threads, change one_processor_fraction to
  // concurrent_processor_fraction().
  double one_processor_fraction = 1.0 / ((double) processor_count());
  double concurrent_cost =
    collection_cost(_latest_cms_concurrent_marking_time_secs,
                interval_in_seconds) * concurrent_processor_fraction() +
    collection_cost(_latest_cms_concurrent_precleaning_time_secs,
                interval_in_seconds) * one_processor_fraction +
    collection_cost(_latest_cms_concurrent_sweeping_time_secs,
                interval_in_seconds) * one_processor_fraction;
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "\nCMSAdaptiveSizePolicy::scaled_concurrent_collection_cost(%f) "
      "_latest_cms_concurrent_marking_cost %f "
      "_latest_cms_concurrent_precleaning_cost %f "
      "_latest_cms_concurrent_sweeping_cost %f "
      "concurrent_processor_fraction %f "
      "concurrent_cost %f ",
      interval_in_seconds,
      collection_cost(_latest_cms_concurrent_marking_time_secs,
        interval_in_seconds),
      collection_cost(_latest_cms_concurrent_precleaning_time_secs,
        interval_in_seconds),
      collection_cost(_latest_cms_concurrent_sweeping_time_secs,
        interval_in_seconds),
      concurrent_processor_fraction(),
      concurrent_cost);
  }
  return concurrent_cost;
}

double CMSAdaptiveSizePolicy::concurrent_collection_time() {
  double latest_cms_sum_concurrent_phases_time_secs =
    _latest_cms_concurrent_marking_time_secs +
    _latest_cms_concurrent_precleaning_time_secs +
    _latest_cms_concurrent_sweeping_time_secs;
  return latest_cms_sum_concurrent_phases_time_secs;
}

double CMSAdaptiveSizePolicy::scaled_concurrent_collection_time() {
  //  When the precleaning and sweeping phases use multiple
  // threads, change one_processor_fraction to
  // concurrent_processor_fraction().
  double one_processor_fraction = 1.0 / ((double) processor_count());
  double latest_cms_sum_concurrent_phases_time_secs =
    _latest_cms_concurrent_marking_time_secs * concurrent_processor_fraction() +
    _latest_cms_concurrent_precleaning_time_secs * one_processor_fraction +
    _latest_cms_concurrent_sweeping_time_secs * one_processor_fraction ;
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "\nCMSAdaptiveSizePolicy::scaled_concurrent_collection_time "
      "_latest_cms_concurrent_marking_time_secs %f "
      "_latest_cms_concurrent_precleaning_time_secs %f "
      "_latest_cms_concurrent_sweeping_time_secs %f "
      "concurrent_processor_fraction %f "
      "latest_cms_sum_concurrent_phases_time_secs %f ",
      _latest_cms_concurrent_marking_time_secs,
      _latest_cms_concurrent_precleaning_time_secs,
      _latest_cms_concurrent_sweeping_time_secs,
      concurrent_processor_fraction(),
      latest_cms_sum_concurrent_phases_time_secs);
  }
  return latest_cms_sum_concurrent_phases_time_secs;
}

void CMSAdaptiveSizePolicy::update_minor_pause_old_estimator(
    double minor_pause_in_ms) {
  // Get the equivalent of the free space
  // that is available for promotions in the CMS generation
  // and use that to update _minor_pause_old_estimator

  // Don't implement this until it is needed. A warning is
  // printed if _minor_pause_old_estimator is used.
}

void CMSAdaptiveSizePolicy::concurrent_marking_begin() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": concurrent_marking_begin ");
  }
  //  Update the interval time
  _concurrent_timer.stop();
  _latest_cms_collection_end_to_collection_start_secs = _concurrent_timer.seconds();
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::concurrent_marking_begin: "
    "mutator time %f", _latest_cms_collection_end_to_collection_start_secs);
  }
  _concurrent_timer.reset();
  _concurrent_timer.start();
}

void CMSAdaptiveSizePolicy::concurrent_marking_end() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::concurrent_marking_end()");
  }

  _concurrent_timer.stop();
  _latest_cms_concurrent_marking_time_secs = _concurrent_timer.seconds();

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("\n CMSAdaptiveSizePolicy::concurrent_marking_end"
      ":concurrent marking time (s) %f",
      _latest_cms_concurrent_marking_time_secs);
  }
}

void CMSAdaptiveSizePolicy::concurrent_precleaning_begin() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::concurrent_precleaning_begin()");
  }
  _concurrent_timer.reset();
  _concurrent_timer.start();
}


void CMSAdaptiveSizePolicy::concurrent_precleaning_end() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::concurrent_precleaning_end()");
  }

  _concurrent_timer.stop();
  // May be set again by a second call during the same collection.
  _latest_cms_concurrent_precleaning_time_secs = _concurrent_timer.seconds();

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("\n CMSAdaptiveSizePolicy::concurrent_precleaning_end"
      ":concurrent precleaning time (s) %f",
      _latest_cms_concurrent_precleaning_time_secs);
  }
}

void CMSAdaptiveSizePolicy::concurrent_sweeping_begin() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::concurrent_sweeping_begin()");
  }
  _concurrent_timer.reset();
  _concurrent_timer.start();
}


void CMSAdaptiveSizePolicy::concurrent_sweeping_end() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::concurrent_sweeping_end()");
  }

  _concurrent_timer.stop();
  _latest_cms_concurrent_sweeping_time_secs = _concurrent_timer.seconds();

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("\n CMSAdaptiveSizePolicy::concurrent_sweeping_end"
      ":concurrent sweeping time (s) %f",
      _latest_cms_concurrent_sweeping_time_secs);
  }
}

void CMSAdaptiveSizePolicy::concurrent_phases_end(GCCause::Cause gc_cause,
                                                  size_t cur_eden,
                                                  size_t cur_promo) {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": concurrent_phases_end ");
  }

  // Update the concurrent timer
  _concurrent_timer.stop();

  if (gc_cause != GCCause::_java_lang_system_gc ||
      UseAdaptiveSizePolicyWithSystemGC) {

    avg_cms_free()->sample(cur_promo);
    double latest_cms_sum_concurrent_phases_time_secs =
      concurrent_collection_time();

    _avg_concurrent_time->sample(latest_cms_sum_concurrent_phases_time_secs);

    // Cost of collection (unit-less)

    // Total interval for collection.  May not be valid.  Tests
    // below determine whether to use this.
    //
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("\nCMSAdaptiveSizePolicy::concurrent_phases_end \n"
      "_latest_cms_reset_end_to_initial_mark_start_secs %f \n"
      "_latest_cms_initial_mark_start_to_end_time_secs %f \n"
      "_latest_cms_remark_start_to_end_time_secs %f \n"
      "_latest_cms_concurrent_marking_time_secs %f \n"
      "_latest_cms_concurrent_precleaning_time_secs %f \n"
      "_latest_cms_concurrent_sweeping_time_secs %f \n"
      "latest_cms_sum_concurrent_phases_time_secs %f \n"
      "_latest_cms_collection_end_to_collection_start_secs %f \n"
      "concurrent_processor_fraction %f",
      _latest_cms_reset_end_to_initial_mark_start_secs,
      _latest_cms_initial_mark_start_to_end_time_secs,
      _latest_cms_remark_start_to_end_time_secs,
      _latest_cms_concurrent_marking_time_secs,
      _latest_cms_concurrent_precleaning_time_secs,
      _latest_cms_concurrent_sweeping_time_secs,
      latest_cms_sum_concurrent_phases_time_secs,
      _latest_cms_collection_end_to_collection_start_secs,
      concurrent_processor_fraction());
  }
    double interval_in_seconds =
      _latest_cms_initial_mark_start_to_end_time_secs +
      _latest_cms_remark_start_to_end_time_secs +
      latest_cms_sum_concurrent_phases_time_secs +
      _latest_cms_collection_end_to_collection_start_secs;
    assert(interval_in_seconds >= 0.0,
      "Bad interval between cms collections");

    // Sample for performance counter
    avg_concurrent_interval()->sample(interval_in_seconds);

    // STW costs (initial and remark pauses)
    // Cost of collection (unit-less)
    assert(_latest_cms_initial_mark_start_to_end_time_secs >= 0.0,
      "Bad initial mark pause");
    assert(_latest_cms_remark_start_to_end_time_secs >= 0.0,
      "Bad remark pause");
    double STW_time_in_seconds =
      _latest_cms_initial_mark_start_to_end_time_secs +
      _latest_cms_remark_start_to_end_time_secs;
    double STW_collection_cost = 0.0;
    if (interval_in_seconds > 0.0) {
      // cost for the STW phases of the concurrent collection.
      STW_collection_cost = STW_time_in_seconds / interval_in_seconds;
      avg_cms_STW_gc_cost()->sample(STW_collection_cost);
    }
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print("cmsAdaptiveSizePolicy::STW_collection_end: "
        "STW gc cost: %f  average: %f", STW_collection_cost,
        avg_cms_STW_gc_cost()->average());
      gclog_or_tty->print_cr("  STW pause: %f (ms) STW period %f (ms)",
        (double) STW_time_in_seconds * MILLIUNITS,
        (double) interval_in_seconds * MILLIUNITS);
    }

    double concurrent_cost = 0.0;
    if (latest_cms_sum_concurrent_phases_time_secs > 0.0) {
      concurrent_cost = concurrent_collection_cost(interval_in_seconds);

      avg_concurrent_gc_cost()->sample(concurrent_cost);
      // Average this ms cost into all the other types gc costs

      if (PrintAdaptiveSizePolicy && Verbose) {
        gclog_or_tty->print("cmsAdaptiveSizePolicy::concurrent_phases_end: "
          "concurrent gc cost: %f  average: %f",
          concurrent_cost,
          _avg_concurrent_gc_cost->average());
        gclog_or_tty->print_cr("  concurrent time: %f (ms) cms period %f (ms)"
          " processor fraction: %f",
          latest_cms_sum_concurrent_phases_time_secs * MILLIUNITS,
          interval_in_seconds * MILLIUNITS,
          concurrent_processor_fraction());
      }
    }
    double total_collection_cost = STW_collection_cost + concurrent_cost;
    avg_major_gc_cost()->sample(total_collection_cost);

    // Gather information for estimating future behavior
    double initial_pause_in_ms = _latest_cms_initial_mark_start_to_end_time_secs * MILLIUNITS;
    double remark_pause_in_ms = _latest_cms_remark_start_to_end_time_secs * MILLIUNITS;

    double cur_promo_size_in_mbytes = ((double)cur_promo)/((double)M);
    initial_pause_old_estimator()->update(cur_promo_size_in_mbytes,
      initial_pause_in_ms);
    remark_pause_old_estimator()->update(cur_promo_size_in_mbytes,
      remark_pause_in_ms);
    major_collection_estimator()->update(cur_promo_size_in_mbytes,
      total_collection_cost);

    // This estimate uses the average eden size.  It could also
    // have used the latest eden size.  Which is better?
    double cur_eden_size_in_mbytes = ((double)cur_eden)/((double) M);
    initial_pause_young_estimator()->update(cur_eden_size_in_mbytes,
      initial_pause_in_ms);
    remark_pause_young_estimator()->update(cur_eden_size_in_mbytes,
      remark_pause_in_ms);
  }

  clear_internal_time_intervals();

  set_first_after_collection();

  // The concurrent phases keeps track of it's own mutator interval
  // with this timer.  This allows the stop-the-world phase to
  // be included in the mutator time so that the stop-the-world time
  // is not double counted.  Reset and start it.
  _concurrent_timer.reset();
  _concurrent_timer.start();

  // The mutator time between STW phases does not include the
  // concurrent collection time.
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::checkpoint_roots_initial_begin() {
  //  Update the interval time
  _STW_timer.stop();
  _latest_cms_reset_end_to_initial_mark_start_secs = _STW_timer.seconds();
  // Reset for the initial mark
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::checkpoint_roots_initial_end(
    GCCause::Cause gc_cause) {
  _STW_timer.stop();

  if (gc_cause != GCCause::_java_lang_system_gc ||
      UseAdaptiveSizePolicyWithSystemGC) {
    _latest_cms_initial_mark_start_to_end_time_secs = _STW_timer.seconds();
    avg_initial_pause()->sample(_latest_cms_initial_mark_start_to_end_time_secs);

    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print(
        "cmsAdaptiveSizePolicy::checkpoint_roots_initial_end: "
        "initial pause: %f ", _latest_cms_initial_mark_start_to_end_time_secs);
    }
  }

  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::checkpoint_roots_final_begin() {
  _STW_timer.stop();
  _latest_cms_initial_mark_end_to_remark_start_secs = _STW_timer.seconds();
  // Start accumumlating time for the remark in the STW timer.
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::checkpoint_roots_final_end(
    GCCause::Cause gc_cause) {
  _STW_timer.stop();
  if (gc_cause != GCCause::_java_lang_system_gc ||
      UseAdaptiveSizePolicyWithSystemGC) {
    // Total initial mark pause + remark pause.
    _latest_cms_remark_start_to_end_time_secs = _STW_timer.seconds();
    double STW_time_in_seconds = _latest_cms_initial_mark_start_to_end_time_secs +
      _latest_cms_remark_start_to_end_time_secs;
    double STW_time_in_ms = STW_time_in_seconds * MILLIUNITS;

    avg_remark_pause()->sample(_latest_cms_remark_start_to_end_time_secs);

    // Sample total for initial mark + remark
    avg_cms_STW_time()->sample(STW_time_in_seconds);

    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print("cmsAdaptiveSizePolicy::checkpoint_roots_final_end: "
        "remark pause: %f", _latest_cms_remark_start_to_end_time_secs);
    }

  }
  // Don't start the STW times here because the concurrent
  // sweep and reset has not happened.
  //  Keep the old comment above in case I don't understand
  // what is going on but now
  // Start the STW timer because it is used by ms_collection_begin()
  // and ms_collection_end() to get the sweep time if a MS is being
  // done in the foreground.
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::msc_collection_begin() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": msc_collection_begin ");
  }
  _STW_timer.stop();
  _latest_cms_msc_end_to_msc_start_time_secs = _STW_timer.seconds();
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::msc_collection_begin: "
      "mutator time %f",
      _latest_cms_msc_end_to_msc_start_time_secs);
  }
  avg_msc_interval()->sample(_latest_cms_msc_end_to_msc_start_time_secs);
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::msc_collection_end(GCCause::Cause gc_cause) {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": msc_collection_end ");
  }
  _STW_timer.stop();
  if (gc_cause != GCCause::_java_lang_system_gc ||
        UseAdaptiveSizePolicyWithSystemGC) {
    double msc_pause_in_seconds = _STW_timer.seconds();
    if ((_latest_cms_msc_end_to_msc_start_time_secs > 0.0) &&
        (msc_pause_in_seconds > 0.0)) {
      avg_msc_pause()->sample(msc_pause_in_seconds);
      double mutator_time_in_seconds = 0.0;
      if (_latest_cms_collection_end_to_collection_start_secs == 0.0) {
        // This assertion may fail because of time stamp gradularity.
        // Comment it out and investiage it at a later time.  The large
        // time stamp granularity occurs on some older linux systems.
#ifndef CLOCK_GRANULARITY_TOO_LARGE
        assert((_latest_cms_concurrent_marking_time_secs == 0.0) &&
               (_latest_cms_concurrent_precleaning_time_secs == 0.0) &&
               (_latest_cms_concurrent_sweeping_time_secs == 0.0),
          "There should not be any concurrent time");
#endif
        // A concurrent collection did not start.  Mutator time
        // between collections comes from the STW MSC timer.
        mutator_time_in_seconds = _latest_cms_msc_end_to_msc_start_time_secs;
      } else {
        // The concurrent collection did start so count the mutator
        // time to the start of the concurrent collection.  In this
        // case the _latest_cms_msc_end_to_msc_start_time_secs measures
        // the time between the initial mark or remark and the
        // start of the MSC.  That has no real meaning.
        mutator_time_in_seconds = _latest_cms_collection_end_to_collection_start_secs;
      }

      double latest_cms_sum_concurrent_phases_time_secs =
        concurrent_collection_time();
      double interval_in_seconds =
        mutator_time_in_seconds +
        _latest_cms_initial_mark_start_to_end_time_secs +
        _latest_cms_remark_start_to_end_time_secs +
        latest_cms_sum_concurrent_phases_time_secs +
        msc_pause_in_seconds;

      if (PrintAdaptiveSizePolicy && Verbose) {
        gclog_or_tty->print_cr("  interval_in_seconds %f \n"
          "     mutator_time_in_seconds %f \n"
          "     _latest_cms_initial_mark_start_to_end_time_secs %f\n"
          "     _latest_cms_remark_start_to_end_time_secs %f\n"
          "     latest_cms_sum_concurrent_phases_time_secs %f\n"
          "     msc_pause_in_seconds %f\n",
          interval_in_seconds,
          mutator_time_in_seconds,
          _latest_cms_initial_mark_start_to_end_time_secs,
          _latest_cms_remark_start_to_end_time_secs,
          latest_cms_sum_concurrent_phases_time_secs,
          msc_pause_in_seconds);
      }

      // The concurrent cost is wasted cost but it should be
      // included.
      double concurrent_cost = concurrent_collection_cost(interval_in_seconds);

      // Initial mark and remark, also wasted.
      double STW_time_in_seconds = _latest_cms_initial_mark_start_to_end_time_secs +
        _latest_cms_remark_start_to_end_time_secs;
      double STW_collection_cost =
        collection_cost(STW_time_in_seconds, interval_in_seconds) +
        concurrent_cost;

      if (PrintAdaptiveSizePolicy && Verbose) {
        gclog_or_tty->print_cr(" msc_collection_end:\n"
          "_latest_cms_collection_end_to_collection_start_secs %f\n"
          "_latest_cms_msc_end_to_msc_start_time_secs %f\n"
          "_latest_cms_initial_mark_start_to_end_time_secs %f\n"
          "_latest_cms_remark_start_to_end_time_secs %f\n"
          "latest_cms_sum_concurrent_phases_time_secs %f\n",
          _latest_cms_collection_end_to_collection_start_secs,
          _latest_cms_msc_end_to_msc_start_time_secs,
          _latest_cms_initial_mark_start_to_end_time_secs,
          _latest_cms_remark_start_to_end_time_secs,
          latest_cms_sum_concurrent_phases_time_secs);

        gclog_or_tty->print_cr(" msc_collection_end: \n"
          "latest_cms_sum_concurrent_phases_time_secs %f\n"
          "STW_time_in_seconds %f\n"
          "msc_pause_in_seconds %f\n",
          latest_cms_sum_concurrent_phases_time_secs,
          STW_time_in_seconds,
          msc_pause_in_seconds);
      }

      double cost = concurrent_cost + STW_collection_cost +
        collection_cost(msc_pause_in_seconds, interval_in_seconds);

      _avg_msc_gc_cost->sample(cost);

      // Average this ms cost into all the other types gc costs
      avg_major_gc_cost()->sample(cost);

      // Sample for performance counter
      _avg_msc_interval->sample(interval_in_seconds);
      if (PrintAdaptiveSizePolicy && Verbose) {
        gclog_or_tty->print("cmsAdaptiveSizePolicy::msc_collection_end: "
          "MSC gc cost: %f  average: %f", cost,
          _avg_msc_gc_cost->average());

        double msc_pause_in_ms = msc_pause_in_seconds * MILLIUNITS;
        gclog_or_tty->print_cr("  MSC pause: %f (ms) MSC period %f (ms)",
          msc_pause_in_ms, (double) interval_in_seconds * MILLIUNITS);
      }
    }
  }

  clear_internal_time_intervals();

  // Can this call be put into the epilogue?
  set_first_after_collection();

  // The concurrent phases keeps track of it's own mutator interval
  // with this timer.  This allows the stop-the-world phase to
  // be included in the mutator time so that the stop-the-world time
  // is not double counted.  Reset and start it.
  _concurrent_timer.stop();
  _concurrent_timer.reset();
  _concurrent_timer.start();

  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::ms_collection_begin() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": ms_collection_begin ");
  }
  _STW_timer.stop();
  _latest_cms_ms_end_to_ms_start = _STW_timer.seconds();
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::ms_collection_begin: "
      "mutator time %f",
      _latest_cms_ms_end_to_ms_start);
  }
  avg_ms_interval()->sample(_STW_timer.seconds());
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::ms_collection_end(GCCause::Cause gc_cause) {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print(" ");
    gclog_or_tty->stamp();
    gclog_or_tty->print(": ms_collection_end ");
  }
  _STW_timer.stop();
  if (gc_cause != GCCause::_java_lang_system_gc ||
        UseAdaptiveSizePolicyWithSystemGC) {
    // The MS collection is a foreground collection that does all
    // the parts of a mostly concurrent collection.
    //
    // For this collection include the cost of the
    //  initial mark
    //  remark
    //  all concurrent time (scaled down by the
    //    concurrent_processor_fraction).  Some
    //    may be zero if the baton was passed before
    //    it was reached.
    //    concurrent marking
    //    sweeping
    //    resetting
    //  STW after baton was passed (STW_in_foreground_in_seconds)
    double STW_in_foreground_in_seconds = _STW_timer.seconds();

    double latest_cms_sum_concurrent_phases_time_secs =
      concurrent_collection_time();
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("\nCMSAdaptiveSizePolicy::ms_collecton_end "
        "STW_in_foreground_in_seconds %f "
        "_latest_cms_initial_mark_start_to_end_time_secs %f "
        "_latest_cms_remark_start_to_end_time_secs %f "
        "latest_cms_sum_concurrent_phases_time_secs %f "
        "_latest_cms_ms_marking_start_to_end_time_secs %f "
        "_latest_cms_ms_end_to_ms_start %f",
        STW_in_foreground_in_seconds,
        _latest_cms_initial_mark_start_to_end_time_secs,
        _latest_cms_remark_start_to_end_time_secs,
        latest_cms_sum_concurrent_phases_time_secs,
        _latest_cms_ms_marking_start_to_end_time_secs,
        _latest_cms_ms_end_to_ms_start);
    }

    double STW_marking_in_seconds = _latest_cms_initial_mark_start_to_end_time_secs +
      _latest_cms_remark_start_to_end_time_secs;
#ifndef CLOCK_GRANULARITY_TOO_LARGE
    assert(_latest_cms_ms_marking_start_to_end_time_secs == 0.0 ||
           latest_cms_sum_concurrent_phases_time_secs == 0.0,
           "marking done twice?");
#endif
    double ms_time_in_seconds = STW_marking_in_seconds +
      STW_in_foreground_in_seconds +
      _latest_cms_ms_marking_start_to_end_time_secs +
      scaled_concurrent_collection_time();
    avg_ms_pause()->sample(ms_time_in_seconds);
    // Use the STW costs from the initial mark and remark plus
    // the cost of the concurrent phase to calculate a
    // collection cost.
    double cost = 0.0;
    if ((_latest_cms_ms_end_to_ms_start > 0.0) &&
        (ms_time_in_seconds > 0.0)) {
      double interval_in_seconds =
        _latest_cms_ms_end_to_ms_start + ms_time_in_seconds;

      if (PrintAdaptiveSizePolicy && Verbose) {
        gclog_or_tty->print_cr("\n ms_time_in_seconds  %f  "
          "latest_cms_sum_concurrent_phases_time_secs %f  "
          "interval_in_seconds %f",
          ms_time_in_seconds,
          latest_cms_sum_concurrent_phases_time_secs,
          interval_in_seconds);
      }

      cost = collection_cost(ms_time_in_seconds, interval_in_seconds);

      _avg_ms_gc_cost->sample(cost);
      // Average this ms cost into all the other types gc costs
      avg_major_gc_cost()->sample(cost);

      // Sample for performance counter
      _avg_ms_interval->sample(interval_in_seconds);
    }
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print("cmsAdaptiveSizePolicy::ms_collection_end: "
        "MS gc cost: %f  average: %f", cost, _avg_ms_gc_cost->average());

      double ms_time_in_ms = ms_time_in_seconds * MILLIUNITS;
      gclog_or_tty->print_cr("  MS pause: %f (ms) MS period %f (ms)",
        ms_time_in_ms,
        _latest_cms_ms_end_to_ms_start * MILLIUNITS);
    }
  }

  // Consider putting this code (here to end) into a
  // method for convenience.
  clear_internal_time_intervals();

  set_first_after_collection();

  // The concurrent phases keeps track of it's own mutator interval
  // with this timer.  This allows the stop-the-world phase to
  // be included in the mutator time so that the stop-the-world time
  // is not double counted.  Reset and start it.
  _concurrent_timer.stop();
  _concurrent_timer.reset();
  _concurrent_timer.start();

  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::clear_internal_time_intervals() {
  _latest_cms_reset_end_to_initial_mark_start_secs = 0.0;
  _latest_cms_initial_mark_end_to_remark_start_secs = 0.0;
  _latest_cms_collection_end_to_collection_start_secs = 0.0;
  _latest_cms_concurrent_marking_time_secs = 0.0;
  _latest_cms_concurrent_precleaning_time_secs = 0.0;
  _latest_cms_concurrent_sweeping_time_secs = 0.0;
  _latest_cms_msc_end_to_msc_start_time_secs = 0.0;
  _latest_cms_ms_end_to_ms_start = 0.0;
  _latest_cms_remark_start_to_end_time_secs = 0.0;
  _latest_cms_initial_mark_start_to_end_time_secs = 0.0;
  _latest_cms_ms_marking_start_to_end_time_secs = 0.0;
}

void CMSAdaptiveSizePolicy::clear_generation_free_space_flags() {
  AdaptiveSizePolicy::clear_generation_free_space_flags();

  set_change_young_gen_for_maj_pauses(0);
}

void CMSAdaptiveSizePolicy::concurrent_phases_resume() {
  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::concurrent_phases_resume()");
  }
  _concurrent_timer.start();
}

double CMSAdaptiveSizePolicy::time_since_major_gc() const {
  _concurrent_timer.stop();
  double time_since_cms_gc = _concurrent_timer.seconds();
  _concurrent_timer.start();
  _STW_timer.stop();
  double time_since_STW_gc = _STW_timer.seconds();
  _STW_timer.start();

  return MIN2(time_since_cms_gc, time_since_STW_gc);
}

double CMSAdaptiveSizePolicy::major_gc_interval_average_for_decay() const {
  double cms_interval = _avg_concurrent_interval->average();
  double msc_interval = _avg_msc_interval->average();
  double ms_interval = _avg_ms_interval->average();

  return MAX3(cms_interval, msc_interval, ms_interval);
}

double CMSAdaptiveSizePolicy::cms_gc_cost() const {
  return avg_major_gc_cost()->average();
}

void CMSAdaptiveSizePolicy::ms_collection_marking_begin() {
  _STW_timer.stop();
  // Start accumumlating time for the marking in the STW timer.
  _STW_timer.reset();
  _STW_timer.start();
}

void CMSAdaptiveSizePolicy::ms_collection_marking_end(
    GCCause::Cause gc_cause) {
  _STW_timer.stop();
  if (gc_cause != GCCause::_java_lang_system_gc ||
      UseAdaptiveSizePolicyWithSystemGC) {
    _latest_cms_ms_marking_start_to_end_time_secs = _STW_timer.seconds();
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr("CMSAdaptiveSizePolicy::"
        "msc_collection_marking_end: mutator time %f",
        _latest_cms_ms_marking_start_to_end_time_secs);
    }
  }
  _STW_timer.reset();
  _STW_timer.start();
}

double CMSAdaptiveSizePolicy::gc_cost() const {
  double cms_gen_cost = cms_gc_cost();
  double result =  MIN2(1.0, minor_gc_cost() + cms_gen_cost);
  assert(result >= 0.0, "Both minor and major costs are non-negative");
  return result;
}

// Cost of collection (unit-less)
double CMSAdaptiveSizePolicy::collection_cost(double pause_in_seconds,
                                              double interval_in_seconds) {
  // Cost of collection (unit-less)
  double cost = 0.0;
  if ((interval_in_seconds > 0.0) &&
      (pause_in_seconds > 0.0)) {
    cost =
      pause_in_seconds / interval_in_seconds;
  }
  return cost;
}

size_t CMSAdaptiveSizePolicy::adjust_eden_for_pause_time(size_t cur_eden) {
  size_t change = 0;
  size_t desired_eden = cur_eden;

  // reduce eden size
  change = eden_decrement_aligned_down(cur_eden);
  desired_eden = cur_eden - change;

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_eden_for_pause_time "
      "adjusting eden for pause time. "
      " starting eden size " SIZE_FORMAT
      " reduced eden size " SIZE_FORMAT
      " eden delta " SIZE_FORMAT,
      cur_eden, desired_eden, change);
  }

  return desired_eden;
}

size_t CMSAdaptiveSizePolicy::adjust_eden_for_throughput(size_t cur_eden) {

  size_t desired_eden = cur_eden;

  set_change_young_gen_for_throughput(increase_young_gen_for_througput_true);

  size_t change = eden_increment_aligned_up(cur_eden);
  size_t scaled_change = scale_by_gen_gc_cost(change, minor_gc_cost());

  if (cur_eden + scaled_change > cur_eden) {
    desired_eden = cur_eden + scaled_change;
  }

  _young_gen_change_for_minor_throughput++;

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_eden_for_throughput "
      "adjusting eden for throughput. "
      " starting eden size " SIZE_FORMAT
      " increased eden size " SIZE_FORMAT
      " eden delta " SIZE_FORMAT,
      cur_eden, desired_eden, scaled_change);
  }

  return desired_eden;
}

size_t CMSAdaptiveSizePolicy::adjust_eden_for_footprint(size_t cur_eden) {

  set_decrease_for_footprint(decrease_young_gen_for_footprint_true);

  size_t change = eden_decrement(cur_eden);
  size_t desired_eden_size = cur_eden - change;

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_eden_for_footprint "
      "adjusting eden for footprint. "
      " starting eden size " SIZE_FORMAT
      " reduced eden size " SIZE_FORMAT
      " eden delta " SIZE_FORMAT,
      cur_eden, desired_eden_size, change);
  }
  return desired_eden_size;
}

// The eden and promo versions should be combined if possible.
// They are the same except that the sizes of the decrement
// and increment are different for eden and promo.
size_t CMSAdaptiveSizePolicy::eden_decrement_aligned_down(size_t cur_eden) {
  size_t delta = eden_decrement(cur_eden);
  return align_size_down(delta, generation_alignment());
}

size_t CMSAdaptiveSizePolicy::eden_increment_aligned_up(size_t cur_eden) {
  size_t delta = eden_increment(cur_eden);
  return align_size_up(delta, generation_alignment());
}

size_t CMSAdaptiveSizePolicy::promo_decrement_aligned_down(size_t cur_promo) {
  size_t delta = promo_decrement(cur_promo);
  return align_size_down(delta, generation_alignment());
}

size_t CMSAdaptiveSizePolicy::promo_increment_aligned_up(size_t cur_promo) {
  size_t delta = promo_increment(cur_promo);
  return align_size_up(delta, generation_alignment());
}


void CMSAdaptiveSizePolicy::compute_young_generation_free_space(size_t cur_eden,
                                          size_t max_eden_size)
{
  size_t desired_eden_size = cur_eden;
  size_t eden_limit = max_eden_size;

  // Printout input
  if (PrintGC && PrintAdaptiveSizePolicy) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::compute_young_generation_free_space: "
      "cur_eden " SIZE_FORMAT,
      cur_eden);
  }

  // Used for diagnostics
  clear_generation_free_space_flags();

  if (_avg_minor_pause->padded_average() > gc_pause_goal_sec()) {
    if (minor_pause_young_estimator()->decrement_will_decrease()) {
      // If the minor pause is too long, shrink the young gen.
      set_change_young_gen_for_min_pauses(
        decrease_young_gen_for_min_pauses_true);
      desired_eden_size = adjust_eden_for_pause_time(desired_eden_size);
    }
  } else if ((avg_remark_pause()->padded_average() > gc_pause_goal_sec()) ||
             (avg_initial_pause()->padded_average() > gc_pause_goal_sec())) {
    // The remark or initial pauses are not meeting the goal.  Should
    // the generation be shrunk?
    if (get_and_clear_first_after_collection() &&
        ((avg_remark_pause()->padded_average() > gc_pause_goal_sec() &&
          remark_pause_young_estimator()->decrement_will_decrease()) ||
         (avg_initial_pause()->padded_average() > gc_pause_goal_sec() &&
          initial_pause_young_estimator()->decrement_will_decrease()))) {

       set_change_young_gen_for_maj_pauses(
         decrease_young_gen_for_maj_pauses_true);

      // If the remark or initial pause is too long and this is the
      // first young gen collection after a cms collection, shrink
      // the young gen.
      desired_eden_size = adjust_eden_for_pause_time(desired_eden_size);
    }
    // If not the first young gen collection after a cms collection,
    // don't do anything.  In this case an adjustment has already
    // been made and the results of the adjustment has not yet been
    // measured.
  } else if ((minor_gc_cost() >= 0.0) &&
             (adjusted_mutator_cost() < _throughput_goal)) {
    desired_eden_size = adjust_eden_for_throughput(desired_eden_size);
  } else {
    desired_eden_size = adjust_eden_for_footprint(desired_eden_size);
  }

  if (PrintGC && PrintAdaptiveSizePolicy) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::compute_young_generation_free_space limits:"
      " desired_eden_size: " SIZE_FORMAT
      " old_eden_size: " SIZE_FORMAT,
      desired_eden_size, cur_eden);
  }

  set_eden_size(desired_eden_size);
}

size_t CMSAdaptiveSizePolicy::adjust_promo_for_pause_time(size_t cur_promo) {
  size_t change = 0;
  size_t desired_promo = cur_promo;
  // Move this test up to caller like the adjust_eden_for_pause_time()
  // call.
  if ((AdaptiveSizePausePolicy == 0) &&
      ((avg_remark_pause()->padded_average() > gc_pause_goal_sec()) ||
      (avg_initial_pause()->padded_average() > gc_pause_goal_sec()))) {
    set_change_old_gen_for_maj_pauses(decrease_old_gen_for_maj_pauses_true);
    change = promo_decrement_aligned_down(cur_promo);
    desired_promo = cur_promo - change;
  } else if ((AdaptiveSizePausePolicy > 0) &&
      (((avg_remark_pause()->padded_average() > gc_pause_goal_sec()) &&
       remark_pause_old_estimator()->decrement_will_decrease()) ||
      ((avg_initial_pause()->padded_average() > gc_pause_goal_sec()) &&
       initial_pause_old_estimator()->decrement_will_decrease()))) {
    set_change_old_gen_for_maj_pauses(decrease_old_gen_for_maj_pauses_true);
    change = promo_decrement_aligned_down(cur_promo);
    desired_promo = cur_promo - change;
  }

  if ((change != 0) &&PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_promo_for_pause_time "
      "adjusting promo for pause time. "
      " starting promo size " SIZE_FORMAT
      " reduced promo size " SIZE_FORMAT
      " promo delta " SIZE_FORMAT,
      cur_promo, desired_promo, change);
  }

  return desired_promo;
}

// Try to share this with PS.
size_t CMSAdaptiveSizePolicy::scale_by_gen_gc_cost(size_t base_change,
                                                  double gen_gc_cost) {

  // Calculate the change to use for the tenured gen.
  size_t scaled_change = 0;
  // Can the increment to the generation be scaled?
  if (gc_cost() >= 0.0 && gen_gc_cost >= 0.0) {
    double scale_by_ratio = gen_gc_cost / gc_cost();
    scaled_change =
      (size_t) (scale_by_ratio * (double) base_change);
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr(
        "Scaled tenured increment: " SIZE_FORMAT " by %f down to "
          SIZE_FORMAT,
        base_change, scale_by_ratio, scaled_change);
    }
  } else if (gen_gc_cost >= 0.0) {
    // Scaling is not going to work.  If the major gc time is the
    // larger than the other GC costs, give it a full increment.
    if (gen_gc_cost >= (gc_cost() - gen_gc_cost)) {
      scaled_change = base_change;
    }
  } else {
    // Don't expect to get here but it's ok if it does
    // in the product build since the delta will be 0
    // and nothing will change.
    assert(false, "Unexpected value for gc costs");
  }

  return scaled_change;
}

size_t CMSAdaptiveSizePolicy::adjust_promo_for_throughput(size_t cur_promo) {

  size_t desired_promo = cur_promo;

  set_change_old_gen_for_throughput(increase_old_gen_for_throughput_true);

  size_t change = promo_increment_aligned_up(cur_promo);
  size_t scaled_change = scale_by_gen_gc_cost(change, major_gc_cost());

  if (cur_promo + scaled_change > cur_promo) {
    desired_promo = cur_promo + scaled_change;
  }

  _old_gen_change_for_major_throughput++;

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_promo_for_throughput "
      "adjusting promo for throughput. "
      " starting promo size " SIZE_FORMAT
      " increased promo size " SIZE_FORMAT
      " promo delta " SIZE_FORMAT,
      cur_promo, desired_promo, scaled_change);
  }

  return desired_promo;
}

size_t CMSAdaptiveSizePolicy::adjust_promo_for_footprint(size_t cur_promo,
                                                         size_t cur_eden) {

  set_decrease_for_footprint(decrease_young_gen_for_footprint_true);

  size_t change = promo_decrement(cur_promo);
  size_t desired_promo_size = cur_promo - change;

  if (PrintAdaptiveSizePolicy && Verbose) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::adjust_promo_for_footprint "
      "adjusting promo for footprint. "
      " starting promo size " SIZE_FORMAT
      " reduced promo size " SIZE_FORMAT
      " promo delta " SIZE_FORMAT,
      cur_promo, desired_promo_size, change);
  }
  return desired_promo_size;
}

void CMSAdaptiveSizePolicy::compute_tenured_generation_free_space(
                                size_t cur_tenured_free,
                                size_t max_tenured_available,
                                size_t cur_eden) {
  // This can be bad if the desired value grows/shrinks without
  // any connection to the read free space
  size_t desired_promo_size = promo_size();
  size_t tenured_limit = max_tenured_available;

  // Printout input
  if (PrintGC && PrintAdaptiveSizePolicy) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::compute_tenured_generation_free_space: "
      "cur_tenured_free " SIZE_FORMAT
      " max_tenured_available " SIZE_FORMAT,
      cur_tenured_free, max_tenured_available);
  }

  // Used for diagnostics
  clear_generation_free_space_flags();

  set_decide_at_full_gc(decide_at_full_gc_true);
  if (avg_remark_pause()->padded_average() > gc_pause_goal_sec() ||
      avg_initial_pause()->padded_average() > gc_pause_goal_sec()) {
    desired_promo_size = adjust_promo_for_pause_time(cur_tenured_free);
  } else if (avg_minor_pause()->padded_average() > gc_pause_goal_sec()) {
    // Nothing to do since the minor collections are too large and
    // this method only deals with the cms generation.
  } else if ((cms_gc_cost() >= 0.0) &&
             (adjusted_mutator_cost() < _throughput_goal)) {
    desired_promo_size = adjust_promo_for_throughput(cur_tenured_free);
  } else {
    desired_promo_size = adjust_promo_for_footprint(cur_tenured_free,
                                                    cur_eden);
  }

  if (PrintGC && PrintAdaptiveSizePolicy) {
    gclog_or_tty->print_cr(
      "CMSAdaptiveSizePolicy::compute_tenured_generation_free_space limits:"
      " desired_promo_size: " SIZE_FORMAT
      " old_promo_size: " SIZE_FORMAT,
      desired_promo_size, cur_tenured_free);
  }

  set_promo_size(desired_promo_size);
}

int CMSAdaptiveSizePolicy::compute_survivor_space_size_and_threshold(
                                             bool is_survivor_overflow,
                                             int tenuring_threshold,
                                             size_t survivor_limit) {
  assert(survivor_limit >= generation_alignment(),
         "survivor_limit too small");
  assert((size_t)align_size_down(survivor_limit, generation_alignment())
         == survivor_limit, "survivor_limit not aligned");

  // Change UsePSAdaptiveSurvivorSizePolicy -> UseAdaptiveSurvivorSizePolicy?
  if (!UsePSAdaptiveSurvivorSizePolicy ||
      !young_gen_policy_is_ready()) {
    return tenuring_threshold;
  }

  // We'll decide whether to increase or decrease the tenuring
  // threshold based partly on the newly computed survivor size
  // (if we hit the maximum limit allowed, we'll always choose to
  // decrement the threshold).
  bool incr_tenuring_threshold = false;
  bool decr_tenuring_threshold = false;

  set_decrement_tenuring_threshold_for_gc_cost(false);
  set_increment_tenuring_threshold_for_gc_cost(false);
  set_decrement_tenuring_threshold_for_survivor_limit(false);

  if (!is_survivor_overflow) {
    // Keep running averages on how much survived

    // We use the tenuring threshold to equalize the cost of major
    // and minor collections.
    // ThresholdTolerance is used to indicate how sensitive the
    // tenuring threshold is to differences in cost betweent the
    // collection types.

    // Get the times of interest. This involves a little work, so
    // we cache the values here.
    const double major_cost = major_gc_cost();
    const double minor_cost = minor_gc_cost();

    if (minor_cost > major_cost * _threshold_tolerance_percent) {
      // Minor times are getting too long;  lower the threshold so
      // less survives and more is promoted.
      decr_tenuring_threshold = true;
      set_decrement_tenuring_threshold_for_gc_cost(true);
    } else if (major_cost > minor_cost * _threshold_tolerance_percent) {
      // Major times are too long, so we want less promotion.
      incr_tenuring_threshold = true;
      set_increment_tenuring_threshold_for_gc_cost(true);
    }

  } else {
    // Survivor space overflow occurred, so promoted and survived are
    // not accurate. We'll make our best guess by combining survived
    // and promoted and count them as survivors.
    //
    // We'll lower the tenuring threshold to see if we can correct
    // things. Also, set the survivor size conservatively. We're
    // trying to avoid many overflows from occurring if defnew size
    // is just too small.

    decr_tenuring_threshold = true;
  }

  // The padded average also maintains a deviation from the average;
  // we use this to see how good of an estimate we have of what survived.
  // We're trying to pad the survivor size as little as possible without
  // overflowing the survivor spaces.
  size_t target_size = align_size_up((size_t)_avg_survived->padded_average(),
                                     generation_alignment());
  target_size = MAX2(target_size, generation_alignment());

  if (target_size > survivor_limit) {
    // Target size is bigger than we can handle. Let's also reduce
    // the tenuring threshold.
    target_size = survivor_limit;
    decr_tenuring_threshold = true;
    set_decrement_tenuring_threshold_for_survivor_limit(true);
  }

  // Finally, increment or decrement the tenuring threshold, as decided above.
  // We test for decrementing first, as we might have hit the target size
  // limit.
  if (decr_tenuring_threshold && !(AlwaysTenure || NeverTenure)) {
    if (tenuring_threshold > 1) {
      tenuring_threshold--;
    }
  } else if (incr_tenuring_threshold && !(AlwaysTenure || NeverTenure)) {
    if (tenuring_threshold < MaxTenuringThreshold) {
      tenuring_threshold++;
    }
  }

  // We keep a running average of the amount promoted which is used
  // to decide when we should collect the old generation (when
  // the amount of old gen free space is less than what we expect to
  // promote).

  if (PrintAdaptiveSizePolicy) {
    // A little more detail if Verbose is on
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    if (Verbose) {
      gclog_or_tty->print( "  avg_survived: %f"
                  "  avg_deviation: %f",
                  _avg_survived->average(),
                  _avg_survived->deviation());
    }

    gclog_or_tty->print( "  avg_survived_padded_avg: %f",
                _avg_survived->padded_average());

    if (Verbose) {
      gclog_or_tty->print( "  avg_promoted_avg: %f"
                  "  avg_promoted_dev: %f",
                  gch->gc_stats(1)->avg_promoted()->average(),
                  gch->gc_stats(1)->avg_promoted()->deviation());
    }

    gclog_or_tty->print( "  avg_promoted_padded_avg: %f"
                "  avg_pretenured_padded_avg: %f"
                "  tenuring_thresh: %d"
                "  target_size: " SIZE_FORMAT
                "  survivor_limit: " SIZE_FORMAT,
                gch->gc_stats(1)->avg_promoted()->padded_average(),
                _avg_pretenured->padded_average(),
                tenuring_threshold, target_size, survivor_limit);
    gclog_or_tty->cr();
  }

  set_survivor_size(target_size);

  return tenuring_threshold;
}

bool CMSAdaptiveSizePolicy::get_and_clear_first_after_collection() {
  bool result = _first_after_collection;
  _first_after_collection = false;
  return result;
}

bool CMSAdaptiveSizePolicy::print_adaptive_size_policy_on(
                                                    outputStream* st) const {

  if (!UseAdaptiveSizePolicy) return false;

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  Generation* gen0 = gch->get_gen(0);
  DefNewGeneration* def_new = gen0->as_DefNewGeneration();
  return
    AdaptiveSizePolicy::print_adaptive_size_policy_on(
                                         st,
                                         def_new->tenuring_threshold());
}
