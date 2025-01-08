/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/partialArrayTaskStats.hpp"
#include "logging/log.hpp"
#include "logging/logHandle.hpp"
#include "logging/logStream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

#if TASKQUEUE_STATS

PartialArrayTaskStats::PartialArrayTaskStats()
  : _split(0), _pushed(0), _stolen(0), _processed(0)
{}

void PartialArrayTaskStats::accumulate(const PartialArrayTaskStats& stats) {
  _split += stats._split;
  _pushed += stats._pushed;
  _stolen += stats._stolen;
  _processed += stats._processed;
}

void PartialArrayTaskStats::reset() {
  *this = PartialArrayTaskStats();
}

LogTargetHandle PartialArrayTaskStats::log_target() {
  LogTarget(Trace, gc, task, stats) lt;
  return LogTargetHandle(lt);
}

bool PartialArrayTaskStats::is_log_enabled() {
  return log_target().is_enabled();
}

static const char* const stats_hdr[] = {
  "     ----partial array----      arrays      array",
  "thread       push      steal    chunked     chunks",
  "------ ---------- ---------- ---------- ----------"
};

void PartialArrayTaskStats::print_header(outputStream* s, const char* title) {
  s->print_cr("%s:", title);
  for (uint i = 0; i < ARRAY_SIZE(stats_hdr); ++i) {
    s->print_cr("%s", stats_hdr[i]);
  }
}

void PartialArrayTaskStats::print_values_impl(outputStream* s) const {
  // 10 digits for each counter, matching the segments in stats_hdr.
  s->print_cr(" %10zu %10zu %10zu %10zu",
              _pushed, _stolen, _split, _processed);
}

void PartialArrayTaskStats::print_values(outputStream* s, uint id) const {
  // 6 digits for thread number, matching the segement in stats_hdr.
  s->print("%6u", id);
  print_values_impl(s);
}

void PartialArrayTaskStats::print_total(outputStream* s) const {
  // 6 characters for "total" id, matching the segment in stats_hdr.
  s->print("%6s", "total");
  print_values_impl(s);
}

#endif // TASKQUEUE_STATS
