/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
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
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/mallocLimit.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memBaseline.hpp"
#include "nmt/memReporter.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtPreInit.hpp"
#include "nmt/threadStackTracker.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/vmError.hpp"

#ifdef _WINDOWS
#include <windows.h>
#endif

NMT_TrackingLevel MemTracker::_tracking_level = NMT_unknown;

DeferredStatic<MemBaseline> MemTracker::_baseline;

bool MemTracker::NmtVirtualMemoryLocker::_safe_to_use;

void MemTracker::initialize() {
  bool rc = true;
  assert(_tracking_level == NMT_unknown, "only call once");

  NMT_TrackingLevel level = NMTUtil::parse_tracking_level(NativeMemoryTracking);
  // Should have been validated before in arguments.cpp
  assert(level == NMT_off || level == NMT_summary || level == NMT_detail,
         "Invalid setting for NativeMemoryTracking (%s)", NativeMemoryTracking);

  // Memory tag is encoded into tracking header as a byte field,
  // make sure that we don't overflow it.
  STATIC_ASSERT(mt_number_of_tags <= max_jubyte);

  if (level > NMT_off) {
    _baseline.initialize();
    if (!MallocTracker::initialize(level) ||
        !MemoryFileTracker::Instance::initialize(level) ||
        !VirtualMemoryTracker::Instance::initialize(level)) {
      assert(false, "NMT initialization failed");
      level = NMT_off;
      log_warning(nmt)("NMT initialization failed. NMT disabled.");
      return;
    }
  } else {
    if (MallocLimit != nullptr) {
      warning("MallocLimit will be ignored since NMT is disabled.");
    }
  }

  NMTPreInit::pre_to_post(level == NMT_off);

  _tracking_level = level;

  // Log state right after NMT initialization
  if (log_is_enabled(Info, nmt)) {
    LogTarget(Info, nmt) lt;
    LogStream ls(lt);
    ls.print_cr("NMT initialized: %s", NMTUtil::tracking_level_to_string(_tracking_level));
    ls.print_cr("Preinit state: ");
    NMTPreInit::print_state(&ls);
    MallocLimitHandler::print_on(&ls);
  }
}

// Report during error reporting.
void MemTracker::error_report(outputStream* output) {
  if (enabled()) {
    report(true, output, MemReporterBase::default_scale); // just print summary for error case.
    output->print("Preinit state:");
    NMTPreInit::print_state(output);
    MallocLimitHandler::print_on(output);
  }
}

// Report when handling PrintNMTStatistics before VM shutdown.
static volatile bool g_final_report_did_run = false;
void MemTracker::final_report(outputStream* output) {
  // This function is called during both error reporting and normal VM exit.
  // However, it should only ever run once.  E.g. if the VM crashes after
  // printing the final report during normal VM exit, it should not print
  // the final report again. In addition, it should be guarded from
  // recursive calls in case NMT reporting itself crashes.
  if (enabled() && Atomic::cmpxchg(&g_final_report_did_run, false, true) == false) {
    report(tracking_level() == NMT_summary, output, 1);
  }
}

// Given an unknown pointer, check if it points into a known region; print region if found
// and return true; false if not found.
bool MemTracker::print_containing_region(const void* p, outputStream* out) {
  return enabled() &&
      (MallocTracker::print_pointer_information(p, out) ||
       VirtualMemoryTracker::Instance::print_containing_region(p, out));
}

void MemTracker::report(bool summary_only, outputStream* output, size_t scale) {
 assert(output != nullptr, "No output stream");
  MemBaseline baseline;
  baseline.baseline(summary_only);
  if (summary_only) {
    MemSummaryReporter rpt(baseline, output, scale);
    rpt.report();
  } else {
    MemDetailReporter rpt(baseline, output, scale);
    rpt.report();
    output->print("Metaspace:");
    // The basic metaspace report avoids any locking and should be safe to
    // be called at any time.
    MetaspaceUtils::print_basic_report(output, scale);
  }
}

void MemTracker::tuning_statistics(outputStream* out) {
  // NMT statistics
  out->print_cr("Native Memory Tracking Statistics:");
  out->print_cr("State: %s",
                NMTUtil::tracking_level_to_string(_tracking_level));
  if (_tracking_level == NMT_detail) {
    out->print_cr("Malloc allocation site table size: %d",
                  MallocSiteTable::hash_buckets());
    out->print_cr("             Tracking stack depth: %d",
                  NMT_TrackingStackDepth);
    out->cr();
    MallocSiteTable::print_tuning_statistics(out);
    out->cr();
  }
  out->print_cr("Preinit state:");
  NMTPreInit::print_state(out);
  MallocLimitHandler::print_on(out);
  out->cr();
}
