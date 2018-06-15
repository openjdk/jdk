/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/stringdedup/stringDedupStat.hpp"
#include "logging/log.hpp"

StringDedupStat::StringDedupStat() :
  _inspected(0),
  _skipped(0),
  _hashed(0),
  _known(0),
  _new(0),
  _new_bytes(0),
  _deduped(0),
  _deduped_bytes(0),
  _idle(0),
  _exec(0),
  _block(0),
  _start_concurrent(0.0),
  _end_concurrent(0.0),
  _start_phase(0.0),
  _idle_elapsed(0.0),
  _exec_elapsed(0.0),
  _block_elapsed(0.0) {
}

void StringDedupStat::add(const StringDedupStat* const stat) {
  _inspected           += stat->_inspected;
  _skipped             += stat->_skipped;
  _hashed              += stat->_hashed;
  _known               += stat->_known;
  _new                 += stat->_new;
  _new_bytes           += stat->_new_bytes;
  _deduped             += stat->_deduped;
  _deduped_bytes       += stat->_deduped_bytes;
  _idle                += stat->_idle;
  _exec                += stat->_exec;
  _block               += stat->_block;
  _idle_elapsed        += stat->_idle_elapsed;
  _exec_elapsed        += stat->_exec_elapsed;
  _block_elapsed       += stat->_block_elapsed;
}

void StringDedupStat::print_start(const StringDedupStat* last_stat) {
  log_info(gc, stringdedup)(
     "Concurrent String Deduplication (" STRDEDUP_TIME_FORMAT ")",
     STRDEDUP_TIME_PARAM(last_stat->_start_concurrent));
}

void StringDedupStat::print_end(const StringDedupStat* last_stat, const StringDedupStat* total_stat) {
  double total_deduped_bytes_percent = 0.0;

  if (total_stat->_new_bytes > 0) {
    // Avoid division by zero
    total_deduped_bytes_percent = percent_of(total_stat->_deduped_bytes, total_stat->_new_bytes);
  }

  log_info(gc, stringdedup)(
    "Concurrent String Deduplication "
    STRDEDUP_BYTES_FORMAT_NS "->" STRDEDUP_BYTES_FORMAT_NS "(" STRDEDUP_BYTES_FORMAT_NS ") "
    "avg " STRDEDUP_PERCENT_FORMAT_NS " "
    "(" STRDEDUP_TIME_FORMAT ", " STRDEDUP_TIME_FORMAT ") " STRDEDUP_TIME_FORMAT_MS,
    STRDEDUP_BYTES_PARAM(last_stat->_new_bytes),
    STRDEDUP_BYTES_PARAM(last_stat->_new_bytes - last_stat->_deduped_bytes),
    STRDEDUP_BYTES_PARAM(last_stat->_deduped_bytes),
    total_deduped_bytes_percent,
    STRDEDUP_TIME_PARAM(last_stat->_start_concurrent),
    STRDEDUP_TIME_PARAM(last_stat->_end_concurrent),
    STRDEDUP_TIME_PARAM_MS(last_stat->_exec_elapsed));
}

void StringDedupStat::reset() {
  _inspected = 0;
  _skipped = 0;
  _hashed = 0;
  _known = 0;
  _new = 0;
  _new_bytes = 0;
  _deduped = 0;
  _deduped_bytes = 0;
  _idle = 0;
  _exec = 0;
  _block = 0;
  _start_concurrent = 0.0;
  _end_concurrent = 0.0;
  _start_phase = 0.0;
  _idle_elapsed = 0.0;
  _exec_elapsed = 0.0;
  _block_elapsed = 0.0;
}

void StringDedupStat::print_statistics(bool total) const {
  double skipped_percent             = percent_of(_skipped, _inspected);
  double hashed_percent              = percent_of(_hashed, _inspected);
  double known_percent               = percent_of(_known, _inspected);
  double new_percent                 = percent_of(_new, _inspected);
  double deduped_percent             = percent_of(_deduped, _new);
  double deduped_bytes_percent       = percent_of(_deduped_bytes, _new_bytes);
/*
  double deduped_young_percent       = percent_of(stat._deduped_young, stat._deduped);
  double deduped_young_bytes_percent = percent_of(stat._deduped_young_bytes, stat._deduped_bytes);
  double deduped_old_percent         = percent_of(stat._deduped_old, stat._deduped);
  double deduped_old_bytes_percent   = percent_of(stat._deduped_old_bytes, stat._deduped_bytes);
*/
  if (total) {
    log_debug(gc, stringdedup)(
      "  Total Exec: " UINTX_FORMAT "/" STRDEDUP_TIME_FORMAT_MS
      ", Idle: " UINTX_FORMAT "/" STRDEDUP_TIME_FORMAT_MS
      ", Blocked: " UINTX_FORMAT "/" STRDEDUP_TIME_FORMAT_MS,
      _exec, STRDEDUP_TIME_PARAM_MS(_exec_elapsed),
      _idle, STRDEDUP_TIME_PARAM_MS(_idle_elapsed),
      _block, STRDEDUP_TIME_PARAM_MS(_block_elapsed));
  } else {
    log_debug(gc, stringdedup)(
      "  Last Exec: " STRDEDUP_TIME_FORMAT_MS
      ", Idle: " STRDEDUP_TIME_FORMAT_MS
      ", Blocked: " UINTX_FORMAT "/" STRDEDUP_TIME_FORMAT_MS,
      STRDEDUP_TIME_PARAM_MS(_exec_elapsed),
      STRDEDUP_TIME_PARAM_MS(_idle_elapsed),
      _block, STRDEDUP_TIME_PARAM_MS(_block_elapsed));
  }
  log_debug(gc, stringdedup)("    Inspected:    " STRDEDUP_OBJECTS_FORMAT, _inspected);
  log_debug(gc, stringdedup)("      Skipped:    " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")", _skipped, skipped_percent);
  log_debug(gc, stringdedup)("      Hashed:     " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")", _hashed, hashed_percent);
  log_debug(gc, stringdedup)("      Known:      " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")", _known, known_percent);
  log_debug(gc, stringdedup)("      New:        " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ") " STRDEDUP_BYTES_FORMAT,
                             _new, new_percent, STRDEDUP_BYTES_PARAM(_new_bytes));
  log_debug(gc, stringdedup)("    Deduplicated: " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ") " STRDEDUP_BYTES_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")",
                             _deduped, deduped_percent, STRDEDUP_BYTES_PARAM(_deduped_bytes), deduped_bytes_percent);
}
