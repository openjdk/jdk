/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023 Red Hat Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class NativeTrimmerThread : public NamedThread {

  Monitor* const _lock;
  bool _stop;
  uint16_t _suspend_count;

  // Statistics
  volatile unsigned _num_trims_performed;

  bool is_suspended() const {
    assert(_lock->is_locked(), "Must be");
    return _suspend_count > 0;
  }

  unsigned inc_suspend_count() {
    assert(_lock->is_locked(), "Must be");
    assert(_suspend_count < UINT16_MAX, "Sanity");
    return ++_suspend_count;
  }

  unsigned dec_suspend_count() {
    assert(_lock->is_locked(), "Must be");
    assert(_suspend_count != 0, "Sanity");
    return --_suspend_count;
  }

  bool is_stopped() const {
    assert(_lock->is_locked(), "Must be");
    return _stop;
  }

  bool at_or_nearing_safepoint() const {
    return SafepointSynchronize::is_at_safepoint() ||
        SafepointSynchronize::is_synchronizing();
  }
  static constexpr int safepoint_poll_ms = 250;

  // in seconds
  static double now() { return os::elapsedTime(); }
  static double to_ms(double seconds) { return seconds * 1000.0; }

  struct LogStartStop {
    void log(const char* s) { log_info(trimnh)("NativeTrimmer %s.", s); }
    LogStartStop()  { log("start"); }
    ~LogStartStop() { log("stop"); }
  };

  void run() override {
    LogStartStop logStartStop;

    for (;;) {
      double tnow = now();
      const double interval_secs = (double)TrimNativeHeapInterval / 1000;
      double next_trim_time = tnow + interval_secs;

      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        if (_stop) return;

        do { // handle spurious wakeups
          if (is_suspended()) {
            ml.wait(0); // infinite
          } else if (next_trim_time > tnow) {
            const int64_t wait_ms = MAX2(1.0, to_ms(next_trim_time - tnow));
            ml.wait(wait_ms);
          } else if (at_or_nearing_safepoint()) {
            ml.wait(safepoint_poll_ms);
          }

          if (_stop) return;

          tnow = now();

        } while (at_or_nearing_safepoint() || is_suspended() || next_trim_time > tnow);
      } // Lock scope

      // 2 - Trim outside of lock protection.
      execute_trim_and_log(tnow);
    } // end for(;;)
  }

  // Execute the native trim, log results.
  void execute_trim_and_log(double t1) {
    assert(os::can_trim_native_heap(), "Unexpected");
    os::size_change_t sc;
    log_debug(trimnh)("Trim native heap started...");
    if (os::trim_native_heap(&sc)) {
      double t2 = now();
      if (sc.after != SIZE_MAX) {
        const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
        const char sign = sc.after < sc.before ? '-' : '+';
        log_info(trimnh)("Trim native heap: RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                           PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                           to_ms(t2 - t1));
        Atomic::inc(&_num_trims_performed);
        log_debug(trimnh)("Total trims: %u.", Atomic::load(&_num_trims_performed));
      } else {
        log_info(trimnh)("Trim native heap: complete, no details, %1.3fms", to_ms(t2 - t1));
      }
    }
  }

public:

  NativeTrimmerThread() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock")),
    _stop(false),
    _suspend_count(0),
    _num_trims_performed(0)
  {
    set_name("Native Heap Trimmer");
    if (os::create_thread(this, os::vm_thread)) {
      os::start_thread(this);
    }
  }

  void suspend(const char* reason) {
    assert(TrimNativeHeap, "Only call if enabled");
    unsigned n = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      n = inc_suspend_count();
      // No need to wakeup trimmer
    }
    log_debug(trimnh)("NativeTrimmer pause for %s (%u suspend requests)", reason, n);
  }

  void resume(const char* reason) {
    assert(TrimNativeHeap, "Only call if enabled");
    unsigned n = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      n = dec_suspend_count();
      if (n == 0) {
        ml.notify_all(); // pause end
      }
    }
    log_debug(trimnh)("NativeTrimmer unpause for %s (%u suspend requests)", reason, n);
  }

  void stop() {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    _stop = true;
    ml.notify_all();
  }

}; // NativeTrimmer

static NativeTrimmerThread* g_trimmer_thread = nullptr;

void NativeHeapTrimmer::initialize() {
  assert(g_trimmer_thread == nullptr, "Only once");
  if (TrimNativeHeap) {
    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(TrimNativeHeap, false);
      log_info(trimnh)("Native trim not supported on this platform.");
      return;
    }
    g_trimmer_thread = new NativeTrimmerThread();
    log_info(trimnh)("Periodic native trim enabled (interval: %u ms)", TrimNativeHeapInterval);
  }
}

void NativeHeapTrimmer::cleanup() {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->stop();
  }
}

void NativeHeapTrimmer::suspend_periodic_trim(const char* reason) {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->suspend(reason);
  }
}

void NativeHeapTrimmer::resume_periodic_trim(const char* reason) {
  if (g_trimmer_thread != nullptr) {
    g_trimmer_thread->resume(reason);
  }
}
