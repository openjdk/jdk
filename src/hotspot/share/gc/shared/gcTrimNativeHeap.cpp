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
 * questions.
 *
 */

#include "precompiled.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcTrimNativeHeap.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

// A list of n subsequent trim results
class TrimData {

  const size_t _max = 5;
  os::size_change_t _results[_max];
  int _num;

public:

  TrimData() : _num(0) {}

  bool is_full() const { _num == _max; }

  void reset() { _num = 0; }

  bool add(const os::size_change_t* sc) {
    assert(!is_full(), "full");
    _results[_num] = *sc;
    _num++;
    return is_full();
  }

  // Small heuristic to evaluate the pointlessness of trimming:
  // If we have enough historical data, would it make sense to slow down
  // periodic trimming?
  // If we keep reclaiming a lot of memory on each trim, but RSS bounces back
  // each time, the answer is yes.
  bool recommend_pause() {
    if (_num < _max) {
      return false; // not enough data
    }
    int num_significant_trims = 0;
    int num_significant_trims_bouncebacks = 0;
    for (int i = 0; i < _max; i++) {
      const ssize_t gain_size = (ssize_t)_results[i].before - (ssize_t)_results[i].after;
      if (gain_size > MIN2(32 * M, _results[i].after / 10)) {
        // This trim showed a significant gain. But did we bounce back?
        num_significant_trims++;
        if (i < _max - 1) {
          const ssize_t bounce_back_size = (ssize_t)_results[i].after - _results[i].before;
          if (bounce_back_size >= gain_size) {
            num_significant_trims_bouncebacks++;
          }
        }
      }
    }
    // We recommend slowing down trimming if each trim showed significant gains but
    // RSS bounced back right afterwards. Note that since we compare RSS, all of this is
    // fuzzy and can yield false positives and negatives.
    return (num_significant_trims == _max &&
            num_significant_trims_bouncebacks == (num_significant_trims - 1));
  }
};

class NativeTrimmer : public ConcurrentGCThread {

  Monitor* _lock;

  // Periodic trimming state
  const int64_t _interval_ms;
  const int64_t _max_interval_ms;
  const bool _do_periodic_trim;

  int64_t _next_trim_time;
  bool _paused;

  // Auto-step-down
  int64_t _last_long_pause;
  TrimData _trim_history;

  static const int64_t never = INT64_MAX;

  static int64_t now() { return os::javaTimeMillis(); }

  void run_service() override {

    assert(GCTrimNativeHeap, "Sanity");
    assert(os::can_trim_native_heap(), "Sanity");

    log_info(gc, trim)("NativeTrimmer started.");

    int64_t ntt = 0;

    for (;;) {
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        do {
          int64_t tnow = now();
          ntt = _next_trim_time;
          wait_some = ntt > tnow || _paused;
          if (wait_some) {
            const int64_t sleep_ms = (_paused || ntt == never) ?
                                     0 /* infinite */ :
                                     ntt - tnow;
            ml.wait(sleep_ms);
          }
          if (should_terminate()) {
            log_info(gc, trim)("NativeTrimmer stopped.");
            return;
          }
          ntt = _next_trim_time;
          tnow = now();
          wait_some = ntt > tnow || _paused;
        } while (wait_some);
      } // end mutex scope

      do_trim(); // may take some time...

      // Update next trim time, but give outside setters preference.
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        const int64_t ntt2 = next_trim_time();
        if (ntt2 == ntt) {
          _next_trim_time = (periodic_trim_enabled() ?
                             (now() + trim_interval()) : never);
        }
      }
    }
  }

  void stop_service() override {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    ml.notify_all();
  }

  void do_trim() {
    if (!os::should_trim_native_heap()) {
      log_trace(gc, trim)("Trim native heap: not necessary");
      return;
    }
    Ticks start = Ticks::now();
    os::size_change_t sc;
    if (os::trim_native_heap(&sc)) {
      Tickspan trim_time = (Ticks::now() - start);
      if (sc.after != SIZE_MAX) {
        const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
        const char sign = sc.after < sc.before ? '-' : '+';
        log_info(gc, trim)("Trim native heap: RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                           trim_time.seconds() * 1000);
      } else {
        log_info(gc, trim)("Trim native heap (no details)");
      }
    }
  }

public:

  NativeTrimmer() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock")),
    _next_trim_time((GCTrimNativeHeapInterval == 0) ? never : GCTrimNativeHeapInterval),
    _next_trim_time_saved(0),
    _interval_ms(GCTrimNativeHeapInterval)
  {
    set_name("Native Heap Trimmer");
    create_and_start();
  }

  void pause() {
    if (!periodic_trim_enabled()) {
      return;
    }
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time_saved = _next_trim_time;
      _next_trim_time = never;
      ml.notify_all();
    }
    log_debug(gc, trim)("NativeTrimmer paused");
  }

  void unpause() {
    if (!periodic_trim_enabled()) {
      return;
    }
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time = _next_trim_time_saved;
      ml.notify_all();
    }
    log_debug(gc, trim)("NativeTrimmer paused");
  }

  void schedule_trim() {
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time = 0;
      ml.notify_all();
    }
    log_debug(gc, trim)("NativeTrimmer immediate trim");
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
        // The default for interval upper bound: 10 * the lower bound, but at least 3 minutes.
        const uint upper_bound = MAX2(GCTrimNativeHeapInterval * 10, (uint)(3 * 60));
        log_debug(gc, trim)("Setting GCTrimNativeHeapIntervalMax to %u.", upper_bound);
        FLAG_SET_ERGO(GCTrimNativeHeapIntervalMax, upper_bound);
      }
      log_info(gc, trim)("Periodic native trim enabled (interval: %u-%u seconds).",
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
