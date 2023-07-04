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
#include "gc/shared/trimNative.hpp"
#include "gc/shared/trimNativeStepDown.hpp"
#include "logging/log.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

class NativeTrimmerThread : public ConcurrentGCThread {

  Monitor* _lock;

  // Periodic trimming state
  const int64_t _interval_ms;
  const bool _periodic_trim_enabled;

  int64_t _next_trim_time;
  int64_t _next_trim_time_saved; // for pause

  static const int64_t never = INT64_MAX;

  static int64_t now() { return os::javaTimeMillis(); }

  void run_service() override {

    log_info(gc, trim)("NativeTrimmer start.");

    int64_t ntt = 0;
    int64_t tnow = 0;

    for (;;) {
      // 1 - Wait for _next_trim_time. Handle spurious wakeups and shutdown.
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

      // 2 - Trimming happens outside of lock protection. GC threads can issue new commands
      //     concurrently.
      const bool explicitly_scheduled = (ntt == 0);
      TrimResult result = execute_trim_and_log(explicitly_scheduled);

      // 3 - Update _next_trim_time; but give concurrent setters preference.
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        tnow = now();
        int64_t ntt2 = _next_trim_time;

        if (ntt2 == ntt) { // not changed concurrently?

          if (_periodic_trim_enabled) {
            int64_t interval_length = _interval_ms;
            _next_trim_time = tnow + interval_length;
          } else {
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
  TrimResult execute_trim_and_log(bool explicitly_scheduled) const {
    assert(os::can_trim_native_heap(), "Unexpected");
    const int64_t tnow = now();
    os::size_change_t sc;
    Ticks start = Ticks::now();
    log_debug(gc, trim)("Trim native heap started...");
    if (os::trim_native_heap(&sc)) {
      Tickspan trim_time = (Ticks::now() - start);
      if (sc.after != SIZE_MAX) {
        const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
        const char sign = sc.after < sc.before ? '-' : '+';
        log_info(gc, trim)("Trim native heap (%s): RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           (explicitly_scheduled ? "explicit" : "periodic"),
                           PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                           trim_time.seconds() * 1000);
        return TrimResult(tnow, now() - tnow, sc.before, sc.after);
      } else {
        log_info(gc, trim)("Trim native heap (no details)");
      }
    }
    return TrimResult();
  }

public:

  NativeTrimmerThread() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock")),
    _interval_ms(TrimNativeHeapInterval * 1000),
    _periodic_trim_enabled(TrimNativeHeapInterval > 0),
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

  void unpause_and_trim() {
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      _next_trim_time = 0;
      ml.notify_all();
    }
    if (_periodic_trim_enabled) {
      log_debug(gc, trim)("NativeTrimmer unpause + request explicit trim");
    } else {
      log_debug(gc, trim)("NativeTrimmer request explicit trim");
    }
  }

}; // NativeTrimmer

static NativeTrimmerThread* g_trimmer_thread = nullptr;

/// GCTrimNative outside facing methods

void TrimNative::initialize() {
  if (TrimNativeHeap) {
    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(TrimNativeHeap, false);
      log_info(gc, trim)("Native trim not supported on this platform.");
      return;
    }

    log_info(gc, trim)("Native trim enabled.");

    if (TrimNativeHeapInterval == 0) {
      log_info(gc, trim)("Periodic native trim disabled.");
    } else {
      log_info(gc, trim)("Periodic native trim enabled (interval: %u seconds)", TrimNativeHeapInterval);
    }
    g_trimmer_thread = new NativeTrimmerThread();
  }
}

void TrimNative::cleanup() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->stop();
  }
}

void TrimNative::pause_periodic_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->pause();
  }
}

void TrimNative::unpause_periodic_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->unpause();
  }
}

void TrimNative::schedule_trim() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->unpause_and_trim();
  }
}
