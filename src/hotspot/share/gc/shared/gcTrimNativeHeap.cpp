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
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

struct trim_result

class NativeTrimmer : public ConcurrentGCThread {

  Monitor* _lock;

  int64_t _interval_ms;
  volatile bool _paused;

  static NativeTrimmer* _the_trimmer;

  bool will_trim_periodically() const { return trim_interval() > 0; }

  // Intervals in milliseconds;
  int64_t trim_interval() const      { return _interval_ms; }
  int64_t trim_interval_min() const  { return GCTrimNativeHeapInterval * 1000; }
  int64_t trim_interval_max() const  { return GCTrimNativeHeapIntervalMax * 1000; }

  void set_trim_interval(uint64_t ms) {
    assert(ms >= trim_interval_min() && ms < trim_interval_max(), "Oob");
    _interval_ms = ms;
  }

  void run_service() override {
    assert(GCTrimNativeHeap, "Sanity");
    assert(os::can_trim_native_heap(), "Sanity");

    log_info(gc, trim)("NativeTrimmer started.");

    for (;;) {
      const int64_t delay_ms =
          will_trim_periodically() ? trim_interval() : 0;
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      ml.wait(delay_ms);

      if (should_terminate()) {
        log_info(gc, trim)("NativeTrimmer stopped.");
        break;
      }

      if (!is_paused() && os::should_trim_native_heap()) {
        GCTrimNative::do_trim();
      }
    }
  }

  void stop_service() override {
    wakeup();
  }

  void wakeup() {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    ml.notify_all();
  }

  bool is_paused() const {
    return Atomic::load(&_paused);
  }

  void pause() {
    Atomic::store(&_paused, true);
    log_debug(gc, trim)("NativeTrimmer paused");
  }

  void unpause() {
    Atomic::store(&_paused, false);
    log_debug(gc, trim)("NativeTrimmer unpaused");
  }

  void do_trim() {
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
    _lock(nullptr),
    _interval_seconds(GCTrimNativeHeapInterval),
    _paused(false)
  {}

  static bool is_enabled() {
    return _the_trimmer != nullptr;
  }

  static void start_trimmer() {
    _the_trimmer = new NativeTrimmer();
    _the_trimmer->create_and_start(NormPriority);
  }

  static void stop_trimmer() {
    _the_trimmer->stop();
  }

  static void pause_periodic_trim() {
    _the_trimmer->pause();
  }

  static void unpause_periodic_trim() {
    _the_trimmer->unpause();
  }

  static void schedule_trim_now() {
    _the_trimmer->unpause();
    _the_trimmer->wakeup();
  }

}; // NativeTrimmer

NativeTrimmer* NativeTrimmer::_the_trimmer = nullptr;

void GCTrimNative::do_trim() {
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
      log_info(gc, trim)("Periodic native trim disabled (we trim at full gc only).",
                          GCTrimNativeHeapInterval, GCTrimNativeHeapIntervalMax);
    }

    NativeTrimmer::start_trimmer();

    _next_trim_not_before = GCTrimNativeHeapInterval;
  }
}

void GCTrimNative::cleanup() {
  if (GCTrimNativeHeap) {
    if (_async_mode) {
      NativeTrimmer::stop_trimmer();
    }
  }
}

bool GCTrimNative::should_trim(bool ignore_delay) {
  return
      GCTrimNativeHeap && os::can_trim_native_heap() &&
      (ignore_delay || (GCTrimNativeHeapInterval > 0 && os::elapsedTime() > _next_trim_not_before)) &&
      os::should_trim_native_heap();
}

void GCTrimNative::execute_trim() {
  if (GCTrimNativeHeap) {
    assert(!_async_mode, "Only call for non-async mode");
    do_trim();
    _next_trim_not_before = os::elapsedTime() + GCTrimNativeHeapInterval;
  }
}

void GCTrimNative::pause_periodic_trim() {
  if (GCTrimNativeHeap) {
    assert(_async_mode, "Only call for async mode");
    NativeTrimmer::pause_periodic_trim();
  }
}

void GCTrimNative::unpause_periodic_trim() {
  if (GCTrimNativeHeap) {
    assert(_async_mode, "Only call for async mode");
    NativeTrimmer::unpause_periodic_trim();
  }
}

void GCTrimNative::schedule_trim() {
  if (GCTrimNativeHeap) {
    assert(_async_mode, "Only call for async mode");
    NativeTrimmer::schedule_trim_now();
  }
}
