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

// A class holding trim results for a single trim operation.
class TrimResult {

  // time (ms) trim happened (javaTimeMillis)
  const uint64_t _time;
  // time (ms) trim itself took.
  const uint64_t _duration;
  // memory relief
  const os::size_change_t _size_change;

public:
  TrimResult(uint64_t t, uint64_t d, os::size_change_t size_change) :
    _time(t), _duration(d), _size_change(size_change) {}
  TrimResult(const TrimResult& other) :
    _time(other._time), _duration(other._duration), _size_change(other._size_change) {}
  uint64_t time() const { return _time; }
  uint64_t duration() const { return _duration; }
  const os::size_change_t& size_change() const { return _size_change; }

  // Returns size reduction; positive if memory was reduced
  ssize_t size_reduction() const {
    return checked_cast<ssize_t>(_size_change.before) -
           checked_cast<ssize_t>(_size_change.after);
  }

};

// A FIFO of the last n trim results
class TrimHistory {

  static const int _max = 4;

  // Size changes for the last n trims, young to old
  TrimResult _histo[_max];
  int _num;

  void push_elements() {
    for (int i = _max - 1; i > 0; i--) {
      _histo[i] = _histo[i - 1];
    }
  }

public:

  TrimHistory() : _num(0) {}

  void reset() { _num = 0; }

  void add(const TrimResult& result) {
    push_elements();
    _histo[0] = result;
    if (_num < _max) {
      _num++;
    }
  }

  // Small heuristic to check if periodic trimming has been fruitful so far.
  // If this heuristic finds trimming to be harmful, we will inject one longer
  // trim interval (standard interval * GCTrimNativeStepDownFactor).
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
  // harmful. Note that the GCTrimNativeStepDownFactor (4) is gentle enough for wrong
  // heuristic results not to be too harmful.
  bool recommend_pause() {
    if (_num < _max / 2) {
      return false; // not enough data;
    }
    int num_significant_trims = 0;
    int num_bouncebacks = 0;
    for (int i = _num - 1; i > 0; i--) { // oldest to youngest
      const ssize_t sz_before = checked_cast<ssize_t>(_histo[i].size_change().before);
      const ssize_t sz_after = checked_cast<ssize_t>(_histo[i].after);
      const ssize_t gains = sz_before - sz_after;
      if (gains > (ssize_t)MIN2(32 * M, _histo[i].before / 10)) { // considered significant
        num_significant_trims++;
        const ssize_t sz_before_next = checked_cast<ssize_t>(_histo[i - 1].before);
        const ssize_t bounceback = sz_before_next - sz_after;
        // We consider it to have bounced back if RSS for the followup sample returns to
        // within at least -2% of post-trim-RSS.
        if (bounceback >= (gains - (gains / 50))) {
          num_bouncebacks++;
        }
      }
    }
    log_trace(gc, trim)("Last %d trims yielded significant gains; %d showed bounceback.",
                        num_significant_trims, num_bouncebacks);
    return (num_significant_trims >= (_max / 2) &&
            num_significant_trims == num_bouncebacks);
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
      os::size_change_t sc;
      bool have_trim_results = execute_trim_and_log(&sc);

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
            // Feed trim data into history; then, if it recommends stepping down the trim interval,
            // do that.
            bool long_pause = false;
            if (have_trim_results) {
              _trim_history.add(&sc);
              long_pause = _trim_history.recommend_pause();
            } else {
              // Sample was invalid, we lost it and hence history is torn: reset history and start from
              // scratch next time.
              _trim_history.reset();
            }

            if (long_pause) {
              log_debug(gc, trim)("NativeTrimmer: long pause (" INT64_FORMAT " ms)", _max_interval_ms);
              _trim_history.reset();
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
  bool execute_trim_and_log(os::size_change_t* sc) {
    assert(os::can_trim_native_heap(), "Unexpected");
    if (!os::should_trim_native_heap()) {
      log_trace(gc, trim)("Trim native heap: not necessary");
      return false;
    }
    Ticks start = Ticks::now();
    if (os::trim_native_heap(sc)) {
      Tickspan trim_time = (Ticks::now() - start);
      if (sc->after != SIZE_MAX) {
        const size_t delta = sc->after < sc->before ? (sc->before - sc->after) : (sc->after - sc->before);
        const char sign = sc->after < sc->before ? '-' : '+';
        log_info(gc, trim)("Trim native heap: RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           PROPERFMTARGS(sc->before), PROPERFMTARGS(sc->after), sign, PROPERFMTARGS(delta),
                           trim_time.seconds() * 1000);
        return true;
      } else {
        log_info(gc, trim)("Trim native heap (no details)");
      }
    }
    return false;
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
      _trim_history.reset();
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
