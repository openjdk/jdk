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
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

class NativeHeapTrimmerThread : public NamedThread {

  // Upper limit for the backoff during pending/in-progress safepoint.
  // Chosen as reasonable value to balance the overheads of waking up
  // during the safepoint, which might have undesired effects on latencies,
  // and the accuracy in tracking the trimming interval.
  static constexpr int64_t safepoint_poll_ms = 250;

  Monitor* const _lock;
  bool _stop;
  uint16_t _suspend_count;

  // Statistics
  uint64_t _num_trims_performed;

  bool is_suspended() const {
    assert(_lock->is_locked(), "Must be");
    return _suspend_count > 0;
  }

  uint16_t inc_suspend_count() {
    assert(_lock->is_locked(), "Must be");
    assert(_suspend_count < UINT16_MAX, "Sanity");
    return ++_suspend_count;
  }

  uint16_t dec_suspend_count() {
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

  // in seconds
  static double now() { return os::elapsedTime(); }
  static double to_ms(double seconds) { return seconds * 1000.0; }

  struct LogStartStopMark {
    void log(const char* s) { log_info(trimnative)("Native heap trimmer %s", s); }
    LogStartStopMark()  { log("start"); }
    ~LogStartStopMark() { log("stop"); }
  };

  void run() override {
    assert(NativeHeapTrimmer::enabled(), "Only call if enabled");

    LogStartStopMark lssm;

    const double interval_secs = (double)TrimNativeHeapInterval / 1000;

    while (true) {
      double tnow = now();
      double next_trim_time = tnow + interval_secs;

      unsigned times_suspended = 0;
      unsigned times_waited = 0;
      unsigned times_safepoint = 0;

      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
        if (_stop) return;

        while (at_or_nearing_safepoint() || is_suspended() || next_trim_time > tnow) {
          if (is_suspended()) {
            times_suspended ++;
            ml.wait(0); // infinite
          } else if (next_trim_time > tnow) {
            times_waited ++;
            const int64_t wait_ms = MAX2(1.0, to_ms(next_trim_time - tnow));
            ml.wait(wait_ms);
          } else if (at_or_nearing_safepoint()) {
            times_safepoint ++;
            const int64_t wait_ms = MIN2<int64_t>(TrimNativeHeapInterval, safepoint_poll_ms);
            ml.wait(wait_ms);
          }

          if (_stop) return;

          tnow = now();
        }
      }

      log_trace(trimnative)("Times: %u suspended, %u timed, %u safepoint",
                            times_suspended, times_waited, times_safepoint);

      execute_trim_and_log(tnow);
    }
  }

  // Execute the native trim, log results.
  void execute_trim_and_log(double t1) {
    assert(os::can_trim_native_heap(), "Unexpected");

    os::size_change_t sc = { 0, 0 };
    LogTarget(Info, trimnative) lt;
    const bool logging_enabled = lt.is_enabled();

    // We only collect size change information if we are logging; save the access to procfs otherwise.
    if (os::trim_native_heap(logging_enabled ? &sc : nullptr)) {
      _num_trims_performed++;
      if (logging_enabled) {
        double t2 = now();
        if (sc.after != SIZE_MAX) {
          const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
          const char sign = sc.after < sc.before ? '-' : '+';
          log_info(trimnative)("Periodic Trim (" UINT64_FORMAT "): " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT ") %.3fms",
                               _num_trims_performed,
                               PROPERFMTARGS(sc.before), PROPERFMTARGS(sc.after), sign, PROPERFMTARGS(delta),
                               to_ms(t2 - t1));
        } else {
          log_info(trimnative)("Periodic Trim (" UINT64_FORMAT "): complete (no details) %.3fms",
                               _num_trims_performed,
                               to_ms(t2 - t1));
        }
      }
    }
  }

public:

  NativeHeapTrimmerThread() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeHeapTrimmer_lock")),
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
    assert(NativeHeapTrimmer::enabled(), "Only call if enabled");
    uint16_t n = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      n = inc_suspend_count();
      // No need to wakeup trimmer
    }
    log_debug(trimnative)("Trim suspended for %s (%u suspend requests)", reason, n);
  }

  void resume(const char* reason) {
    assert(NativeHeapTrimmer::enabled(), "Only call if enabled");
    uint16_t n = 0;
    {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      n = dec_suspend_count();
      if (n == 0) {
        ml.notify_all(); // pause end
      }
    }
    if (n == 0) {
      log_debug(trimnative)("Trim resumed after %s", reason);
    } else {
      log_debug(trimnative)("Trim still suspended after %s (%u suspend requests)", reason, n);
    }
  }

  void stop() {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    _stop = true;
    ml.notify_all();
  }

  void print_state(outputStream* st) const {
    // Don't pull lock during error reporting
    Mutex* const lock = VMError::is_error_reported() ? nullptr : _lock;
    int64_t num_trims = 0;
    bool stopped = false;
    uint16_t suspenders = 0;
    {
      MutexLocker ml(lock, Mutex::_no_safepoint_check_flag);
      num_trims = _num_trims_performed;
      stopped = _stop;
      suspenders = _suspend_count;
    }
    st->print_cr("Trims performed: " UINT64_FORMAT ", current suspend count: %d, stopped: %d",
                 num_trims, suspenders, stopped);
  }

}; // NativeHeapTrimmer

static NativeHeapTrimmerThread* g_trimmer_thread = nullptr;

void NativeHeapTrimmer::initialize() {
  assert(g_trimmer_thread == nullptr, "Only once");
  if (TrimNativeHeapInterval > 0) {
    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(TrimNativeHeapInterval, 0);
      log_warning(trimnative)("Native heap trim is not supported on this platform");
      return;
    }
    g_trimmer_thread = new NativeHeapTrimmerThread();
    log_info(trimnative)("Periodic native trim enabled (interval: %u ms)", TrimNativeHeapInterval);
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

void NativeHeapTrimmer::print_state(outputStream* st) {
  if (g_trimmer_thread != nullptr) {
    st->print_cr("Periodic native trim enabled (interval: %u ms)", TrimNativeHeapInterval);
    g_trimmer_thread->print_state(st);
  } else {
    st->print_cr("Periodic native trim disabled");
  }
}
