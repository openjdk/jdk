/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"
#include "jvm.h"

#include "runtime/orderAccess.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "services/memBaseline.hpp"
#include "services/memReporter.hpp"
#include "services/mallocTracker.inline.hpp"
#include "services/memTracker.hpp"
#include "services/threadStackTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/vmError.hpp"

#ifdef _WINDOWS
#include <windows.h>
#endif

#ifdef SOLARIS
  volatile bool NMT_stack_walkable = false;
#else
  volatile bool NMT_stack_walkable = true;
#endif

volatile NMT_TrackingLevel MemTracker::_tracking_level = NMT_unknown;
NMT_TrackingLevel MemTracker::_cmdline_tracking_level = NMT_unknown;

MemBaseline MemTracker::_baseline;
bool MemTracker::_is_nmt_env_valid = true;

static const size_t buffer_size = 64;

NMT_TrackingLevel MemTracker::init_tracking_level() {
  // Memory type is encoded into tracking header as a byte field,
  // make sure that we don't overflow it.
  STATIC_ASSERT(mt_number_of_types <= max_jubyte);

  char nmt_env_variable[buffer_size];
  jio_snprintf(nmt_env_variable, sizeof(nmt_env_variable), "NMT_LEVEL_%d", os::current_process_id());
  const char* nmt_env_value;
#ifdef _WINDOWS
  // Read the NMT environment variable from the PEB instead of the CRT
  char value[buffer_size];
  nmt_env_value = GetEnvironmentVariable(nmt_env_variable, value, (DWORD)sizeof(value)) != 0 ? value : NULL;
#else
  nmt_env_value = ::getenv(nmt_env_variable);
#endif
  NMT_TrackingLevel level = NMT_off;
  if (nmt_env_value != NULL) {
    if (strcmp(nmt_env_value, "summary") == 0) {
      level = NMT_summary;
    } else if (strcmp(nmt_env_value, "detail") == 0) {
      level = NMT_detail;
    } else if (strcmp(nmt_env_value, "off") != 0) {
      // The value of the environment variable is invalid
      _is_nmt_env_valid = false;
    }
    // Remove the environment variable to avoid leaking to child processes
    os::unsetenv(nmt_env_variable);
  }

  if (!MallocTracker::initialize(level) ||
      !VirtualMemoryTracker::initialize(level)) {
    level = NMT_off;
  }
  return level;
}

void MemTracker::init() {
  NMT_TrackingLevel level = tracking_level();
  if (level >= NMT_summary) {
    if (!VirtualMemoryTracker::late_initialize(level) ||
        !ThreadStackTracker::late_initialize(level)) {
      shutdown();
      return;
    }
  }
}

bool MemTracker::check_launcher_nmt_support(const char* value) {
  if (strcmp(value, "=detail") == 0) {
    if (MemTracker::tracking_level() != NMT_detail) {
      return false;
    }
  } else if (strcmp(value, "=summary") == 0) {
    if (MemTracker::tracking_level() != NMT_summary) {
      return false;
    }
  } else if (strcmp(value, "=off") == 0) {
    if (MemTracker::tracking_level() != NMT_off) {
      return false;
    }
  } else {
    _is_nmt_env_valid = false;
  }

  return true;
}

bool MemTracker::verify_nmt_option() {
  return _is_nmt_env_valid;
}

void* MemTracker::malloc_base(void* memblock) {
  return MallocTracker::get_base(memblock);
}

void Tracker::record(address addr, size_t size) {
  if (MemTracker::tracking_level() < NMT_summary) return;
  switch(_type) {
    case uncommit:
      VirtualMemoryTracker::remove_uncommitted_region(addr, size);
      break;
    case release:
      VirtualMemoryTracker::remove_released_region(addr, size);
        break;
    default:
      ShouldNotReachHere();
  }
}


// Shutdown can only be issued via JCmd, and NMT JCmd is serialized by lock
void MemTracker::shutdown() {
  // We can only shutdown NMT to minimal tracking level if it is ever on.
  if (tracking_level () > NMT_minimal) {
    transition_to(NMT_minimal);
  }
}

bool MemTracker::transition_to(NMT_TrackingLevel level) {
  NMT_TrackingLevel current_level = tracking_level();

  assert(level != NMT_off || current_level == NMT_off, "Cannot transition NMT to off");

  if (current_level == level) {
    return true;
  } else if (current_level > level) {
    // Downgrade tracking level, we want to lower the tracking level first
    _tracking_level = level;
    // Make _tracking_level visible immediately.
    OrderAccess::fence();
    VirtualMemoryTracker::transition(current_level, level);
    MallocTracker::transition(current_level, level);
    ThreadStackTracker::transition(current_level, level);
  } else {
    // Upgrading tracking level is not supported and has never been supported.
    // Allocating and deallocating malloc tracking structures is not thread safe and
    // leads to inconsistencies unless a lot coarser locks are added.
  }
  return true;
}

void MemTracker::report(bool summary_only, outputStream* output) {
 assert(output != NULL, "No output stream");
  MemBaseline baseline;
  if (baseline.baseline(summary_only)) {
    if (summary_only) {
      MemSummaryReporter rpt(baseline, output);
      rpt.report();
    } else {
      MemDetailReporter rpt(baseline, output);
      rpt.report();
      output->print("Metaspace:");
      // Metadata reporting requires a safepoint, so avoid it if VM is not in good state.
      assert(!VMError::fatal_error_in_progress(), "Do not report metadata in error report");
      VM_PrintMetadata vmop(output, K,
          MetaspaceUtils::rf_show_loaders |
          MetaspaceUtils::rf_break_down_by_spacetype);
      VMThread::execute(&vmop);
    }
  }
}

// This is a walker to gather malloc site hashtable statistics,
// the result is used for tuning.
class StatisticsWalker : public MallocSiteWalker {
 private:
  enum Threshold {
    // aggregates statistics over this threshold into one
    // line item.
    report_threshold = 20
  };

 private:
  // Number of allocation sites that have all memory freed
  int   _empty_entries;
  // Total number of allocation sites, include empty sites
  int   _total_entries;
  // Number of captured call stack distribution
  int   _stack_depth_distribution[NMT_TrackingStackDepth];
  // Hash distribution
  int   _hash_distribution[report_threshold];
  // Number of hash buckets that have entries over the threshold
  int   _bucket_over_threshold;

  // The hash bucket that walker is currently walking
  int   _current_hash_bucket;
  // The length of current hash bucket
  int   _current_bucket_length;
  // Number of hash buckets that are not empty
  int   _used_buckets;
  // Longest hash bucket length
  int   _longest_bucket_length;

 public:
  StatisticsWalker() : _empty_entries(0), _total_entries(0) {
    int index = 0;
    for (index = 0; index < NMT_TrackingStackDepth; index ++) {
      _stack_depth_distribution[index] = 0;
    }
    for (index = 0; index < report_threshold; index ++) {
      _hash_distribution[index] = 0;
    }
    _bucket_over_threshold = 0;
    _longest_bucket_length = 0;
    _current_hash_bucket = -1;
    _current_bucket_length = 0;
    _used_buckets = 0;
  }

  virtual bool do_malloc_site(const MallocSite* e) {
    if (e->size() == 0) _empty_entries ++;
    _total_entries ++;

    // stack depth distrubution
    int frames = e->call_stack()->frames();
    _stack_depth_distribution[frames - 1] ++;

    // hash distribution
    int hash_bucket = e->hash() % MallocSiteTable::hash_buckets();
    if (_current_hash_bucket == -1) {
      _current_hash_bucket = hash_bucket;
      _current_bucket_length = 1;
    } else if (_current_hash_bucket == hash_bucket) {
      _current_bucket_length ++;
    } else {
      record_bucket_length(_current_bucket_length);
      _current_hash_bucket = hash_bucket;
      _current_bucket_length = 1;
    }
    return true;
  }

  // walk completed
  void completed() {
    record_bucket_length(_current_bucket_length);
  }

  void report_statistics(outputStream* out) {
    int index;
    out->print_cr("Malloc allocation site table:");
    out->print_cr("\tTotal entries: %d", _total_entries);
    out->print_cr("\tEmpty entries: %d (%2.2f%%)", _empty_entries, ((float)_empty_entries * 100) / _total_entries);
    out->print_cr(" ");
    out->print_cr("Hash distribution:");
    if (_used_buckets < MallocSiteTable::hash_buckets()) {
      out->print_cr("empty bucket: %d", (MallocSiteTable::hash_buckets() - _used_buckets));
    }
    for (index = 0; index < report_threshold; index ++) {
      if (_hash_distribution[index] != 0) {
        if (index == 0) {
          out->print_cr("  %d    entry: %d", 1, _hash_distribution[0]);
        } else if (index < 9) { // single digit
          out->print_cr("  %d  entries: %d", (index + 1), _hash_distribution[index]);
        } else {
          out->print_cr(" %d entries: %d", (index + 1), _hash_distribution[index]);
        }
      }
    }
    if (_bucket_over_threshold > 0) {
      out->print_cr(" >%d entries: %d", report_threshold,  _bucket_over_threshold);
    }
    out->print_cr("most entries: %d", _longest_bucket_length);
    out->print_cr(" ");
    out->print_cr("Call stack depth distribution:");
    for (index = 0; index < NMT_TrackingStackDepth; index ++) {
      if (_stack_depth_distribution[index] > 0) {
        out->print_cr("\t%d: %d", index + 1, _stack_depth_distribution[index]);
      }
    }
  }

 private:
  void record_bucket_length(int length) {
    _used_buckets ++;
    if (length <= report_threshold) {
      _hash_distribution[length - 1] ++;
    } else {
      _bucket_over_threshold ++;
    }
    _longest_bucket_length = MAX2(_longest_bucket_length, length);
  }
};


void MemTracker::tuning_statistics(outputStream* out) {
  // NMT statistics
  StatisticsWalker walker;
  MallocSiteTable::walk_malloc_site(&walker);
  walker.completed();

  out->print_cr("Native Memory Tracking Statistics:");
  out->print_cr("Malloc allocation site table size: %d", MallocSiteTable::hash_buckets());
  out->print_cr("             Tracking stack depth: %d", NMT_TrackingStackDepth);
  NOT_PRODUCT(out->print_cr("Peak concurrent access: %d", MallocSiteTable::access_peak_count());)
  out->print_cr(" ");
  walker.report_statistics(out);
}
