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

#ifndef SHARE_GC_SHARED_PARTIALARRAYTASKSTATS_HPP
#define SHARE_GC_SHARED_PARTIALARRAYTASKSTATS_HPP

#include "logging/logHandle.hpp"
#include "logging/logStream.hpp"
#include "utilities/globalDefinitions.hpp"

#if TASKQUEUE_STATS

class outputStream;

// Repository for collecting and reporting statistics about partial array task
// processing.  Not thread-safe; each processing thread should have its own
// stats object.
class PartialArrayTaskStats {
  size_t _split;
  size_t _pushed;
  size_t _stolen;
  size_t _processed;

  static LogTargetHandle log_target();
  static bool is_log_enabled();
  static void print_header(outputStream* s, const char* title);
  void print_values(outputStream* s, uint id) const;
  void print_total(outputStream* s) const;
  void print_values_impl(outputStream* s) const;

  void accumulate(const PartialArrayTaskStats& stats);

public:
  // All counters are initially zero.
  PartialArrayTaskStats();

  // Trivially copied and destroyed.

  // Number of arrays split into partial array tasks.
  size_t split() const { return _split; }

  // Number of partial array tasks pushed onto a queue.
  size_t pushed() const { return _pushed; }

  // Number of partial array tasks stolen from some other queue.
  size_t stolen() const { return _stolen; }

  // Number of partial array tasks processed.
  size_t processed() const { return _processed; }

  void inc_split() { _split += 1; }
  void inc_pushed(size_t n) { _pushed += n; }
  void inc_stolen() { _stolen += 1; }
  void inc_processed() { _processed += 1; }

  // Set all counters to zero.
  void reset();

  // Log a table of statistics, if logging is enabled (gc+task+stats=trace).
  //
  // num_stats: The number of stats objects to include in the table, one row
  // for each.
  //
  // access: A function taking a uint value < num_stats, and returning a
  // pointer to the corresponding stats object.
  //
  // title: A string title for the table.
  template<typename StatsAccess>
  static void log_set(uint num_stats, StatsAccess access, const char* title) {
    if (is_log_enabled()) {
      LogStream ls(log_target());
      PartialArrayTaskStats total;
      print_header(&ls, title);
      for (uint i = 0; i < num_stats; ++i) {
        const PartialArrayTaskStats* stats = access(i);
        stats->print_values(&ls, i);
        total.accumulate(*stats);
      }
      total.print_total(&ls);
    }
  }
};

#endif // TASKQUEUE_STATS

#endif // SHARE_GC_SHARED_PARTIALARRAYTASKSTATS_HPP
