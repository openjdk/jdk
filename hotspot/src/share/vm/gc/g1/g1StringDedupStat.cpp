/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1StringDedupStat.hpp"
#include "logging/log.hpp"

G1StringDedupStat::G1StringDedupStat() :
  _inspected(0),
  _skipped(0),
  _hashed(0),
  _known(0),
  _new(0),
  _new_bytes(0),
  _deduped(0),
  _deduped_bytes(0),
  _deduped_young(0),
  _deduped_young_bytes(0),
  _deduped_old(0),
  _deduped_old_bytes(0),
  _idle(0),
  _exec(0),
  _block(0),
  _start(0.0),
  _idle_elapsed(0.0),
  _exec_elapsed(0.0),
  _block_elapsed(0.0) {
}

void G1StringDedupStat::add(const G1StringDedupStat& stat) {
  _inspected           += stat._inspected;
  _skipped             += stat._skipped;
  _hashed              += stat._hashed;
  _known               += stat._known;
  _new                 += stat._new;
  _new_bytes           += stat._new_bytes;
  _deduped             += stat._deduped;
  _deduped_bytes       += stat._deduped_bytes;
  _deduped_young       += stat._deduped_young;
  _deduped_young_bytes += stat._deduped_young_bytes;
  _deduped_old         += stat._deduped_old;
  _deduped_old_bytes   += stat._deduped_old_bytes;
  _idle                += stat._idle;
  _exec                += stat._exec;
  _block               += stat._block;
  _idle_elapsed        += stat._idle_elapsed;
  _exec_elapsed        += stat._exec_elapsed;
  _block_elapsed       += stat._block_elapsed;
}

void G1StringDedupStat::print_summary(const G1StringDedupStat& last_stat, const G1StringDedupStat& total_stat) {
  double total_deduped_bytes_percent = 0.0;

  if (total_stat._new_bytes > 0) {
    // Avoid division by zero
    total_deduped_bytes_percent = (double)total_stat._deduped_bytes / (double)total_stat._new_bytes * 100.0;
  }

  log_info(gc, stringdedup)(
    "Concurrent String Deduplication "
    G1_STRDEDUP_BYTES_FORMAT_NS "->" G1_STRDEDUP_BYTES_FORMAT_NS "(" G1_STRDEDUP_BYTES_FORMAT_NS "), avg "
    G1_STRDEDUP_PERCENT_FORMAT_NS ", " G1_STRDEDUP_TIME_FORMAT "]",
    G1_STRDEDUP_BYTES_PARAM(last_stat._new_bytes),
    G1_STRDEDUP_BYTES_PARAM(last_stat._new_bytes - last_stat._deduped_bytes),
    G1_STRDEDUP_BYTES_PARAM(last_stat._deduped_bytes),
    total_deduped_bytes_percent,
    last_stat._exec_elapsed);
}

void G1StringDedupStat::print_statistics(const G1StringDedupStat& stat, bool total) {
  double young_percent               = 0.0;
  double old_percent                 = 0.0;
  double skipped_percent             = 0.0;
  double hashed_percent              = 0.0;
  double known_percent               = 0.0;
  double new_percent                 = 0.0;
  double deduped_percent             = 0.0;
  double deduped_bytes_percent       = 0.0;
  double deduped_young_percent       = 0.0;
  double deduped_young_bytes_percent = 0.0;
  double deduped_old_percent         = 0.0;
  double deduped_old_bytes_percent   = 0.0;

  if (stat._inspected > 0) {
    // Avoid division by zero
    skipped_percent = (double)stat._skipped / (double)stat._inspected * 100.0;
    hashed_percent  = (double)stat._hashed / (double)stat._inspected * 100.0;
    known_percent   = (double)stat._known / (double)stat._inspected * 100.0;
    new_percent     = (double)stat._new / (double)stat._inspected * 100.0;
  }

  if (stat._new > 0) {
    // Avoid division by zero
    deduped_percent = (double)stat._deduped / (double)stat._new * 100.0;
  }

  if (stat._deduped > 0) {
    // Avoid division by zero
    deduped_young_percent = (double)stat._deduped_young / (double)stat._deduped * 100.0;
    deduped_old_percent   = (double)stat._deduped_old / (double)stat._deduped * 100.0;
  }

  if (stat._new_bytes > 0) {
    // Avoid division by zero
    deduped_bytes_percent = (double)stat._deduped_bytes / (double)stat._new_bytes * 100.0;
  }

  if (stat._deduped_bytes > 0) {
    // Avoid division by zero
    deduped_young_bytes_percent = (double)stat._deduped_young_bytes / (double)stat._deduped_bytes * 100.0;
    deduped_old_bytes_percent   = (double)stat._deduped_old_bytes / (double)stat._deduped_bytes * 100.0;
  }

  if (total) {
    log_debug(gc, stringdedup)(
      "   [Total Exec: " UINTX_FORMAT "/" G1_STRDEDUP_TIME_FORMAT ", Idle: " UINTX_FORMAT "/" G1_STRDEDUP_TIME_FORMAT ", Blocked: " UINTX_FORMAT "/" G1_STRDEDUP_TIME_FORMAT "]",
      stat._exec, stat._exec_elapsed, stat._idle, stat._idle_elapsed, stat._block, stat._block_elapsed);
  } else {
    log_debug(gc, stringdedup)(
      "   [Last Exec: " G1_STRDEDUP_TIME_FORMAT ", Idle: " G1_STRDEDUP_TIME_FORMAT ", Blocked: " UINTX_FORMAT "/" G1_STRDEDUP_TIME_FORMAT "]",
      stat._exec_elapsed, stat._idle_elapsed, stat._block, stat._block_elapsed);
  }
  log_debug(gc, stringdedup)("      [Inspected:    " G1_STRDEDUP_OBJECTS_FORMAT "]", stat._inspected);
  log_debug(gc, stringdedup)("         [Skipped:   " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]", stat._skipped, skipped_percent);
  log_debug(gc, stringdedup)("         [Hashed:    " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]", stat._hashed, hashed_percent);
  log_debug(gc, stringdedup)("         [Known:     " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]", stat._known, known_percent);
  log_debug(gc, stringdedup)("         [New:       " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ") " G1_STRDEDUP_BYTES_FORMAT "]",
                             stat._new, new_percent, G1_STRDEDUP_BYTES_PARAM(stat._new_bytes));
  log_debug(gc, stringdedup)("      [Deduplicated: " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ") " G1_STRDEDUP_BYTES_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]",
                             stat._deduped, deduped_percent, G1_STRDEDUP_BYTES_PARAM(stat._deduped_bytes), deduped_bytes_percent);
  log_debug(gc, stringdedup)("         [Young:     " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ") " G1_STRDEDUP_BYTES_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]",
                             stat._deduped_young, deduped_young_percent, G1_STRDEDUP_BYTES_PARAM(stat._deduped_young_bytes), deduped_young_bytes_percent);
  log_debug(gc, stringdedup)("         [Old:       " G1_STRDEDUP_OBJECTS_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ") " G1_STRDEDUP_BYTES_FORMAT "(" G1_STRDEDUP_PERCENT_FORMAT ")]",
                             stat._deduped_old, deduped_old_percent, G1_STRDEDUP_BYTES_PARAM(stat._deduped_old_bytes), deduped_old_bytes_percent);
}
