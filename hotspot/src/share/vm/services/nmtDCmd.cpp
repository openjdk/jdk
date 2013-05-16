/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "services/nmtDCmd.hpp"
#include "services/memReporter.hpp"
#include "services/memTracker.hpp"
#include "utilities/globalDefinitions.hpp"

NMTDCmd::NMTDCmd(outputStream* output,
  bool heap): DCmdWithParser(output, heap),
  _summary("summary", "request runtime to report current memory summary, " \
           "which includes total reserved and committed memory, along " \
           "with memory usage summary by each subsytem.",
           "BOOLEAN", false, "false"),
  _detail("detail", "request runtime to report memory allocation >= "
           "1K by each callsite.",
           "BOOLEAN", false, "false"),
  _baseline("baseline", "request runtime to baseline current memory usage, " \
            "so it can be compared against in later time.",
            "BOOLEAN", false, "false"),
  _summary_diff("summary.diff", "request runtime to report memory summary " \
            "comparison against previous baseline.",
            "BOOLEAN", false, "false"),
  _detail_diff("detail.diff", "request runtime to report memory detail " \
            "comparison against previous baseline, which shows the memory " \
            "allocation activities at different callsites.",
            "BOOLEAN", false, "false"),
  _shutdown("shutdown", "request runtime to shutdown itself and free the " \
            "memory used by runtime.",
            "BOOLEAN", false, "false"),
  _auto_shutdown("autoShutdown", "automatically shutdown itself under "    \
            "stress situation",
            "BOOLEAN", true, "true"),
#ifndef PRODUCT
  _debug("debug", "print tracker statistics. Debug only, not thread safe", \
            "BOOLEAN", false, "false"),
#endif
  _scale("scale", "Memory usage in which scale, KB, MB or GB",
       "STRING", false, "KB") {
  _dcmdparser.add_dcmd_option(&_summary);
  _dcmdparser.add_dcmd_option(&_detail);
  _dcmdparser.add_dcmd_option(&_baseline);
  _dcmdparser.add_dcmd_option(&_summary_diff);
  _dcmdparser.add_dcmd_option(&_detail_diff);
  _dcmdparser.add_dcmd_option(&_shutdown);
  _dcmdparser.add_dcmd_option(&_auto_shutdown);
#ifndef PRODUCT
  _dcmdparser.add_dcmd_option(&_debug);
#endif
  _dcmdparser.add_dcmd_option(&_scale);
}

void NMTDCmd::execute(DCmdSource source, TRAPS) {
  const char* scale_value = _scale.value();
  size_t scale_unit;
  if (strcmp(scale_value, "KB") == 0 || strcmp(scale_value, "kb") == 0) {
    scale_unit = K;
  } else if (strcmp(scale_value, "MB") == 0 ||
             strcmp(scale_value, "mb") == 0) {
    scale_unit = M;
  } else if (strcmp(scale_value, "GB") == 0 ||
             strcmp(scale_value, "gb") == 0) {
    scale_unit = G;
  } else {
    output()->print_cr("Incorrect scale value: %s", scale_value);
    return;
  }

  int nopt = 0;
  if (_summary.is_set() && _summary.value()) { ++nopt; }
  if (_detail.is_set() && _detail.value()) { ++nopt; }
  if (_baseline.is_set() && _baseline.value()) { ++nopt; }
  if (_summary_diff.is_set() && _summary_diff.value()) { ++nopt; }
  if (_detail_diff.is_set() && _detail_diff.value()) { ++nopt; }
  if (_shutdown.is_set() && _shutdown.value()) { ++nopt; }
  if (_auto_shutdown.is_set()) { ++nopt; }

#ifndef PRODUCT
  if (_debug.is_set() && _debug.value()) { ++nopt; }
#endif

  if (nopt > 1) {
      output()->print_cr("At most one of the following option can be specified: " \
        "summary, detail, baseline, summary.diff, detail.diff, shutdown"
#ifndef PRODUCT
        ", debug"
#endif
      );
      return;
  } else if (nopt == 0) {
    if (_summary.is_set()) {
      output()->print_cr("No command to execute");
      return;
    } else {
      _summary.set_value(true);
    }
  }

#ifndef PRODUCT
  if (_debug.value()) {
    output()->print_cr("debug command is NOT thread-safe, may cause crash");
    MemTracker::print_tracker_stats(output());
    return;
  }
#endif

  // native memory tracking has to be on
  if (!MemTracker::is_on() || MemTracker::shutdown_in_progress()) {
    // if it is not on, what's the reason?
    output()->print_cr(MemTracker::reason());
    return;
  }

  if (_summary.value()) {
    BaselineTTYOutputer outputer(output());
    MemTracker::print_memory_usage(outputer, scale_unit, true);
  } else if (_detail.value()) {
    BaselineTTYOutputer outputer(output());
    MemTracker::print_memory_usage(outputer, scale_unit, false);
  } else if (_baseline.value()) {
    if (MemTracker::baseline()) {
      output()->print_cr("Successfully baselined.");
    } else {
      output()->print_cr("Baseline failed.");
    }
  } else if (_summary_diff.value()) {
    if (MemTracker::has_baseline()) {
      BaselineTTYOutputer outputer(output());
      MemTracker::compare_memory_usage(outputer, scale_unit, true);
    } else {
      output()->print_cr("No baseline to compare, run 'baseline' command first");
    }
  } else if (_detail_diff.value()) {
    if (MemTracker::has_baseline()) {
      BaselineTTYOutputer outputer(output());
      MemTracker::compare_memory_usage(outputer, scale_unit, false);
    } else {
      output()->print_cr("No baseline to compare to, run 'baseline' command first");
    }
  } else if (_shutdown.value()) {
    MemTracker::shutdown(MemTracker::NMT_shutdown_user);
    output()->print_cr("Shutdown is in progress, it will take a few moments to " \
      "completely shutdown");
  } else if (_auto_shutdown.is_set()) {
    MemTracker::set_autoShutdown(_auto_shutdown.value());
  } else {
    ShouldNotReachHere();
    output()->print_cr("Unknown command");
  }
}

int NMTDCmd::num_arguments() {
  ResourceMark rm;
  NMTDCmd* dcmd = new NMTDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

