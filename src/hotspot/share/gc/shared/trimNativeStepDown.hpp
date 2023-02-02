/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_TRIMNATIVESTEPDOWN_HPP
#define SHARE_GC_SHARED_TRIMNATIVESTEPDOWN_HPP


#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;

/////// Support for TrimNativeHeapAdaptiveStepDown //////
//
// Small heuristic to check if periodic trimming has been fruitful so far.
// If this heuristic finds trimming to be harmful, we will inject one longer
// trim interval (GCTrimNativeIntervalMax).
//
// Trimming costs are the trim itself plus the re-aquisition costs of memory should the
// released memory be malloced again. Trimming gains are the memory reduction over time.
// Lasting gains are good; gains that don't last are not.
//
// There are roughly three usage pattern:
// - rare malloc spikes interspersed with long idle periods. Trimming is beneficial
//   since the relieved memory pressure holds for a long time.
// - a constant low-intensity malloc drone. Trimming does not help much here but its
//   harmless too since trimming is cheap if it does not recover much.
// - frequent malloc spikes with short idle periods; trimmed memory will be re-aquired
//   after only a short relief; here, trimming could be harmful since we pay a lot for
//   not much relief. We want to alleviate these scenarios.
//
// Putting numbers on these things is difficult though. We cannot observe malloc
// load directly, only RSS. For every trim we know the RSS reduction (from, to). So
// for subsequent trims we also can glean from (<next sample>.from) whether RSS bounced
// back. But that is quite vague since RSS may have been influenced by a ton of other
// developments, especially for longer trim intervals.
//
// Therefore this heuristic may produce false positives and negatives. We try to err on
// the side of too much trimming here and to identify only situations that are clearly
// harmful. Note that the GCTrimNativeIntervalMax default (4 * GCTrimNativeInterval)
// is gentle enough for wrong heuristic results to not be too punative.


// A class holding results for a single trim operation.
class TrimResult {

  // time (ms) trim happened (javaTimeMillis)
  int64_t _time;
  // time (ms) trim itself took.
  int64_t _duration;
  // rss
  size_t _rss_before, _rss_after;

public:

  TrimResult() : _time(-1), _duration(0), _rss_before(0), _rss_after(0) {}

  TrimResult(int64_t t, int64_t d, size_t rss1, size_t rss2) :
    _time(t), _duration(d), _rss_before(rss1), _rss_after(rss2)
  {}

  int64_t time() const { return _time; }
  int64_t duration() const { return _duration; }
  size_t rss_before() const { return _rss_before; }
  size_t rss_after() const { return _rss_before; }

  bool is_valid() const {
    return _time >= 0 && _duration >= 0 &&
        _rss_before != 0 && _rss_after != 0;
  }

  // Returns size reduction; positive if memory was reduced
  ssize_t size_reduction() const {
    return checked_cast<ssize_t>(_rss_before) -
           checked_cast<ssize_t>(_rss_after);
  }

  // Return the lasting gain compared with a follow-up trim. Negative numbers mean a loss.
  ssize_t calc_lasting_gain(const TrimResult& followup_trim) const {
    ssize_t gain = size_reduction();
    ssize_t loss = checked_cast<ssize_t>(followup_trim.rss_before()) -
                   checked_cast<ssize_t>(rss_after());
    return gain - loss;
  }

  // Return the interval time between this result and a follow-up trim.
  int64_t interval_time(const TrimResult& followup_trim) const {
    return followup_trim.time() - time();
  }

  void print_on(outputStream* st) const;

};

class TrimNativeStepDownControl {

  static const int _trim_history_length = 4;

  // A FIFO of the last n trim results
  class TrimHistory {
    static const int _max = _trim_history_length;

    // Note: history may contain invalid results; for one, it is
    // initialized with invalid results to keep iterating simple;
    // also invalid results can happen if measuring rss goes wrong.
    TrimResult _histo[_max];
    int _pos; // position of next write

  public:

    TrimHistory() : _pos(0) {}

    void add(const TrimResult& result) {
      _histo[_pos] = result;
      if (++_pos == _max) {
        _pos = 0;
      }
    }

    template <class Functor>
    void iterate_oldest_to_youngest(Functor f) const {
      int idx = _pos;
      do {
        f(_histo + idx);
        if (++idx == _max) {
          idx = 0;
        }
      } while (idx != _pos);
    }
  };

  TrimHistory _history;

  static bool is_bad_trim(const TrimResult& r, const TrimResult& r_followup);

public:

  // Feed a new trim result into control. It will be added to the history,
  // replacing the oldest result.
  // Adding invalid results is allowed; they will be ignored by the heuristics.
  void feed(const TrimResult& r);

  // Returns true if Heuristic recommends stepping down the trim interval
  bool recommend_step_down() const;

};

#endif // SHARE_GC_SHARED_TRIMNATIVESTEPDOWN_HPP
