/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * questioSns.
 *
 */

#include "precompiled.hpp"
#include "gc/shared/trimNativeStepDown.hpp"
#include "logging/log.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

void TrimResult::print_on(outputStream* st) const {
  st->print("time: " INT64_FORMAT ", duration " INT64_FORMAT
            ", rss1: " SIZE_FORMAT ", rss2: " SIZE_FORMAT " (" SSIZE_FORMAT ")",
            _time, _duration, _rss_before, _rss_after, size_reduction());
}

// Given two results of subsequent trims, returns true if the first trim is considered
// "bad" - a trim that had been not worth the cost.
bool TrimNativeStepDownControl::is_bad_trim(const TrimResult& r, const TrimResult& r_followup) {
  assert(r.is_valid() && r_followup.is_valid(), "Sanity");

  const int64_t tinterval = r.interval_time(r_followup);
  assert(tinterval >= 0, "negative interval? " INT64_FORMAT, tinterval);
  if (tinterval == 0) {
    return false;
  }
  assert(tinterval >= r.duration(), "trim duration cannot be larger than trim interval ("
         INT64_FORMAT ", " INT64_FORMAT ")", tinterval, r.duration());

  // Cost: ratio of trim time to total interval time (which contains trim time)
  const double ratio_trim_time_to_interval_time =
      (double)r.duration() / (double)tinterval;
  assert(ratio_trim_time_to_interval_time >= 0, "Sanity");

  // Any ratio of less than 1% trim time to interval time we regard as harmless
  // (e.g. less than 10ms for 1second of interval)
  if (ratio_trim_time_to_interval_time < 0.01) {
    return false;
  }

  // Benefit: Ratio of lasting size reduction to RSS before the first trim.
  const double rss_gain_ratio = (double)r.calc_lasting_gain(r_followup) / (double)r.rss_before();

  // We consider paying 1% (or more) time-per-interval for
  // 1% (or less, maybe even negative) rss size reduction as bad.
  bool bad = ratio_trim_time_to_interval_time > rss_gain_ratio;

  return false;
}

bool TrimNativeStepDownControl::recommend_step_down() const {
  struct { int trims, bad, ignored; } counts = { 0, 0, 0 };

  const TrimResult* previous = nullptr;
  auto trim_evaluater = [&counts, &previous] (const TrimResult* r) {
tty->print("??  ");
r->print_on(tty);
    if (!r->is_valid() || previous == nullptr || !previous->is_valid()) {
      // Note: we ignore:
      // - the very youngest trim, since we don't know the
      //   RSS bounce back to the next trim yet.
      // - invalid trim results
      counts.ignored++;
    } else {
      counts.trims++;
      if (is_bad_trim(*previous, *r)) {
        counts.bad++;
      }
    }
tty->cr();
    previous = r;
  };
  _history.iterate_oldest_to_youngest(trim_evaluater);

  log_trace(gc, trim)("Heuristic says: trims: %d, bad trims: %d, ignored: %d",
                      counts.trims, counts.bad, counts.ignored);

  // If all trims in the history had been bad (excluding the youngest, for which we cannot
  // evaluate the lasting gains yet), step down.
  return counts.ignored <= 1 && counts.bad == counts.trims;
}

void TrimNativeStepDownControl::feed(const TrimResult& r) {
  _history.add(r);
}
