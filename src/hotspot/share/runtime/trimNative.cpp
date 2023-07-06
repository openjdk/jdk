/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat Inc. All rights reserved.
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
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/trimNative.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

class NativeTrimmerThread : public NamedThread {

  Monitor* const _lock;
  bool _stop;

  // Pausing
  int _pausers;

  bool paused() const { return _pausers > 0; }
  static int64_t now() { return os::javaTimeMillis(); }

  void run() override {
    log_info(trim)("NativeTrimmer start.");
    run_inner();
    log_info(trim)("NativeTrimmer stop.");
  }

  void run_inner() {

    int64_t ntt = 0; // next trim time
    int64_t tnow = 0;

    for (;;) {
      // 1 - Wait for _next_trim_time. Handle spurious wakeups and shutdown.
      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);

        tnow = now();

        // Set next trim time
        ntt = tnow + TrimNativeHeapInterval;

        do { // handle spurious wakeups
          if (_stop) {
            return;
          }
          if (paused()) {
            ml.wait(0);
          } else if (ntt > tnow) {
            ml.wait(ntt - tnow);
          }
          if (_stop) {
            return;
          }
          tnow = now();
        } while (paused() || ntt > tnow);
      }

      // 2 - Trim outside of lock protection.
      execute_trim_and_log();

    }
  }

  // Execute the native trim, log results.
  void execute_trim_and_log() const {
    assert(os::can_trim_native_heap(), "Unexpected");
    const int64_t tnow = now();
    os::size_change_t sc;
    Ticks start = Ticks::now();
    log_debug(trim)("Trim native heap started...");
    if (os::trim_native_heap(&sc)) {
      Tickspan trim_time = (Ticks::now() - start);
      if (sc.after != SIZE_MAX) {
        const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
        const char sign = sc.after < sc.before ? '-' : '+';
        log_info(trim)("Trim native heap: RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                           trim_time.seconds() * 1000);
      } else {
        log_info(trim)("Trim native heap (no details)");
      }
    }
  }

public:

  NativeTrimmerThread() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock")),
    _stop(false),
    _pausers(0)
  {
    set_name("Native Heap Trimmer");
    if (os::create_thread(this, os::vm_thread)) {
      os::start_thread(this);
    }
  }

  void suspend(const char* reason) {
    assert(TrimNativeHeap, "Only call if enabled");
    assert(TrimNativeHeapInterval > 0, "Only call if periodic trimming is enabled");
    int lvl = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      lvl = ++_pausers;
      // No need to wakeup trimmer
    }
    log_debug(trim)("NativeTrimmer pause (%s) (%d)", reason, lvl);
  }

  void resume(const char* reason) {
    assert(TrimNativeHeap, "Only call if enabled");
    assert(TrimNativeHeapInterval > 0, "Only call if periodic trimming is enabled");
    int lvl = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      lvl = _pausers--;
      if (_pausers == 0) {
        ml.notify_all(); // pause end
      }
    }
    log_debug(trim)("NativeTrimmer unpause (%s) (%d)", reason, lvl);
  }

  void stop() {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    _stop = true;
    ml.notify_all();
  }

}; // NativeTrimmer

static NativeTrimmerThread* g_trimmer_thread = nullptr;

/// GCTrimNative outside facing methods

void TrimNative::initialize() {
  if (TrimNativeHeap) {
    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(TrimNativeHeap, false);
      log_info(trim)("Native trim not supported on this platform.");
      return;
    }
    g_trimmer_thread = new NativeTrimmerThread();
    log_info(trim)("Periodic native trim enabled (interval: %u ms)", TrimNativeHeapInterval);
  }
}

void TrimNative::cleanup() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->stop();
  }
}

void TrimNative::suspend_periodic_trim(const char* reason) {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->suspend(reason);
  }
}

void TrimNative::resume_periodic_trim(const char* reason) {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->resume(reason);
  }
}

