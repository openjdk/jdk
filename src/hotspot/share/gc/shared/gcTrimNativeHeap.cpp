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

class NativeTrimmer : public ConcurrentGCThread {

  Monitor* _lock;

  // Time of next trim; INT64_MAX: periodic trim disabled
  int64_t _next_trim_time;
  int64_t _next_trim_time_saved;
  static const int64_t never = INT64_MAX;

  int64_t _interval_ms;

  // Intervals in milliseconds;
  bool periodic_trim_enabled() const { return GCTrimNativeHeapInterval != 0; }
  int64_t trim_interval_min() const  { return GCTrimNativeHeapInterval * 1000; }
  int64_t trim_interval_max() const  { return GCTrimNativeHeapIntervalMax * 1000; }

  int64_t trim_interval() const      { return _interval_ms; }
  int64_t next_trim_time() const     { return _next_trim_time; }

  static int64_t now() { return os::javaTimeMillis(); }

  void run_service() override {
    assert(GCTrimNativeHeap, "Sanity");
    assert(os::can_trim_native_heap(), "Sanity");

    log_info(gc, trim)("NativeTrimmer started.");

    for (;;) {

      int64_t ntt = 0;
      int64_t tnow = 0;

      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        do {
          ntt = next_trim_time();
          tnow = now();
          if (ntt > tnow) {
            const int64_t sleep_ms = (ntt == never) ? 0 : ntt - tnow;
            ml.wait(sleep_ms);
            ntt = next_trim_time();
            tnow = now();
          }
          if (should_terminate()) {
            log_info(gc, trim)("NativeTrimmer stopped.");
            return;
          }
        } while (ntt > tnow);
      }

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
