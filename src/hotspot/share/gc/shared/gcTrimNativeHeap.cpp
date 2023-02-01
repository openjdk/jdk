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
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcTrimNativeHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

// A class holding trim results for a single trim operation.
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

  void print_on(outputStream* st) const {
    st->print("time: " INT64_FORMAT ", duration " INT64_FORMAT
              ", rss1: " SIZE_FORMAT ", rss2: " SIZE_FORMAT " (" SSIZE_FORMAT ")",
              _time, _duration, _rss_before, _rss_after, size_reduction());
  }
};

// A FIFO of the last n trim results
class TrimHistory {
  static const int _max = 4;

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

class NativeTrimmer : public ConcurrentGCThread {

  Monitor* _lock;

  // Periodic trimming state
  const int64_t _interval_ms;
  const int64_t _max_interval_ms;
  const bool _periodic_trim_enabled;

  int64_t _next_trim_time;
  int64_t _next_trim_time_saved; // for pause

  TrimHistory _trim_history;

  static const int64_t never = INT64_MAX;

  static int64_t now() { return os::javaTimeMillis(); }

  void run_service() override {

    assert(GCTrimNativeHeap, "Sanity");
    assert(os::can_trim_native_heap(), "Sanity");

    log_info(gc, trim)("NativeTrimmer start.");

    int64_t ntt = 0;
    int64_t tnow = 0;

    for (;;) {
      // 1 - Wait for the next trim point
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        do {
          tnow = now();
          ntt = _next_trim_time;
          if (ntt == never) {
            ml.wait(0); // infinite sleep
          } else if (ntt > tnow) {
            ml.wait(ntt - tnow); // sleep till next point
          }
          if (should_terminate()) {
            log_info(gc, trim)("NativeTrimmer stop.");
            return;
          }
          tnow = now();
          ntt = _next_trim_time;
        } while (ntt > tnow);
      }

      // 2 - Trim
      TrimResult result = execute_trim_and_log();

      // 3 - Update next trim time
      // Note: outside setters have preference -  if we paused/unpaused/scheduled trim concurrently while the last trim
      // was in progress, we do that. Note that if this causes two back-to-back trims, that is harmless since usually
      // the second trim is cheap.
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        tnow = now();
        int64_t ntt2 = _next_trim_time;
        if (ntt2 == ntt) { // not changed concurrently?
          if (_periodic_trim_enabled) {
            // Feed trim data into history and examine history.
            // do that.
            _trim_history.add(result);
            if (recommend_pause()) {
              log_debug(gc, trim)("NativeTrimmer: long pause (" INT64_FORMAT " ms)", _max_interval_ms);
              _next_trim_time = tnow + _max_interval_ms;
            } else {
              _next_trim_time = tnow + _interval_ms;
            }
          } else {
            // periodic trim disabled
            _next_trim_time = never;
          }
        }
      } // Mutex scope
    }
  }

  void stop_service() override {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    ml.notify_all();
  }

  // Execute the native trim, log results.
  // Return true if trim succeeded *and* we have valid size change data.
  TrimResult execute_trim_and_log() {
    assert(os::can_trim_native_heap(), "Unexpected");
    if (!os::should_trim_native_heap()) {
      log_trace(gc, trim)("Trim native heap: not necessary");
      return TrimResult();
    }
    const int64_t tnow = now();
    os::size_change_t sc;
    Ticks start = Ticks::now();
    if (os::trim_native_heap(&sc)) {
      Tickspan trim_time = (Ticks::now() - start);
      if (sc.after != SIZE_MAX) {
        const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
        const char sign = sc.after < sc.before ? '-' : '+';
        log_info(gc, trim)("Trim native heap: RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                           trim_time.seconds() * 1000);
        return TrimResult(tnow, now() - tnow, sc.before, sc.after);
      } else {
        log_info(gc, trim)("Trim native heap (no details)");
      }
    }
    return TrimResult();
  }

  ////// Heuristics /////////////////////////////////////

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

  // Given two results of subsequent trims, return the lasting gain of the
  // first trim, in bytes. Negative numbers mean a loss.
  static ssize_t calc_lasting_gain(const TrimResult& s1, const TrimResult& s2) {
    ssize_t gain = s1.size_reduction();
    ssize_t loss = checked_cast<ssize_t>(s2.rss_before()) -
                   checked_cast<ssize_t>(s1.rss_after());
    return gain - loss;
  }

  // Given two results of subsequent trims, return the interval time
  // between them. This includes the trim time itself.
  static int64_t interval_time(const TrimResult& s1, const TrimResult& s2) {
    return s2.time() - s1.time();
  }

  // Given two results of subsequent trims, returns true if the first trim is considered
  // "bad" - a trim that had been not worth the cost.
  static bool is_bad_trim(const TrimResult& s1, const TrimResult& s2) {
    assert(s1.is_valid() && s2.is_valid(), "Sanity");
    const int64_t tinterval = interval_time(s1, s2);
    assert(tinterval >= 0, "negative interval? " INT64_FORMAT, tinterval);
    if (tinterval <= 0) {
      return false;
    }
    assert(tinterval >= s1.duration(), "trim duration cannot be larger than trim interval ("
           INT64_FORMAT ", " INT64_FORMAT ")", tinterval, s1.duration());

    // Cost: ratio of trim time to total interval time (which contains trim time)
    const double ratio_trim_time_to_interval_time =
        (double)s1.duration() / (double)tinterval;
    assert(ratio_trim_time_to_interval_time >= 0, "Sanity");

    // Any ratio of less than 1% trim time to interval time we regard as harmless
    // (e.g. less than 10ms for 1second of interval)
    if (ratio_trim_time_to_interval_time < 0.01) {
      return false;
    }

    // Benefit: Ratio of lasting size reduction to RSS before the first trim.
    const double rss_gain_ratio = (double)calc_lasting_gain(s1, s2) / s1.rss_before();

    // We consider paying 1% (or more) time-per-interval for
    // 1% (or less, maybe even negative) rss size reduction as bad.
    bool bad = ratio_trim_time_to_interval_time > rss_gain_ratio;

tty->print_cr("%s", bad ? "BAD" : "");

    return false;
  }

  bool recommend_pause() {
    struct { int trims, bad, ignored; } counts = { 0, 0, 0 };
    const TrimResult* previous = nullptr;
    auto trim_evaluater = [&counts, &previous] (const TrimResult* r) {

tty->print("??  ");
r->print_on(tty);

      if (!r->is_valid() || previous == nullptr || !previous->is_valid()) {
        // Note: we always ignore the very youngest trim, since we don't know the
        // RSS bounce back to the next trim yet.
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
    _trim_history.iterate_oldest_to_youngest(trim_evaluater);
    log_trace(gc, trim)("Heuristics: trims: %d, bad trims: %d, ignored: %d",
                        counts.trims, counts.bad, counts.ignored);
    return counts.ignored <= 1 && counts.bad == counts.trims;
  }

public:

  NativeTrimmer() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock")),
    _interval_ms(GCTrimNativeHeapInterval * 1000),
    _max_interval_ms(GCTrimNativeHeapIntervalMax * 1000),
    _periodic_trim_enabled(GCTrimNativeHeapInterval > 0),
    _next_trim_time(0),
    _next_trim_time_saved(0)
  {
    set_name("Native Heap Trimmer");
    _next_trim_time = _periodic_trim_enabled ? (now() + _interval_ms) : never;
    create_and_start();
  }

  void pause() {
    if (!_periodic_trim_enabled) {
      return;
    }
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time_saved = _next_trim_time;
      _next_trim_time = never;
      ml.notify_all();
    }
    log_debug(gc, trim)("NativeTrimmer pause");
  }

  void unpause() {
    if (!_periodic_trim_enabled) {
      return;
    }
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time = _next_trim_time_saved;
      ml.notify_all();
    }
    log_debug(gc, trim)("NativeTrimmer unpause");
  }

  void schedule_trim() {
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time = 0;
      ml.notify_all();
    }
    if (_periodic_trim_enabled) {
      log_debug(gc, trim)("NativeTrimmer unpause+trim");
    } else {
      log_debug(gc, trim)("NativeTrimmer trim");
    }
  }

}; // NativeTrimmer

static NativeTrimmer* g_trimmer_thread = nullptr;

/// GCTrimNative outside facing methods

void GCTrimNative::initialize() {

  if (GCTrimNativeHeap) {

    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(GCTrimNativeHeap, false);
      log_info(gc, trim)("GCTrimNativeHeap disabled - trim-native not supported on this platform.");
      return;
    }

    log_info(gc, trim)("Native trim enabled.");

    if (GCTrimNativeHeapInterval > 0) { // periodic trimming enabled
      assert(GCTrimNativeHeapIntervalMax == 0 ||
             GCTrimNativeHeapIntervalMax > GCTrimNativeHeapInterval, "Sanity"); // see flag constraint
      if (GCTrimNativeHeapIntervalMax == 0) { // default
       FLAG_SET_ERGO(GCTrimNativeHeapIntervalMax, GCTrimNativeHeapInterval * 4);
      }
      log_info(gc, trim)("Periodic native trim enabled (interval: %u seconds, step-down-interval: %u seconds).",
                          GCTrimNativeHeapInterval, GCTrimNativeHeapIntervalMax);
    } else {
      log_info(gc, trim)("Periodic native trim disabled (we trim at full gc only).");
    }

    g_trimmer_thread = new NativeTrimmer();
  }
}

void GCTrimNative::cleanup() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->stop();
  }
}

void GCTrimNative::pause_periodic_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->pause();
  }
}

void GCTrimNative::unpause_periodic_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->unpause();
  }
}

void GCTrimNative::schedule_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->schedule_trim();
  }
}
