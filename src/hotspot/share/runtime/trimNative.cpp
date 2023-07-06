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
#include "runtime/safepoint.hpp"
#include "runtime/trimNative.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

class NativeTrimmerThread : public NamedThread {

  Monitor* const _lock;
  bool _stop;
  unsigned _suspend_count;

  // Statistics
  uint64_t _num_trims_performed;

  bool suspended() const {
    assert(_lock->is_locked(), "Must be");
    return _suspend_count > 0;
  }

  unsigned inc_suspend_count() {
    assert(_lock->is_locked(), "Must be");
    assert(_suspend_count < UINT_MAX, "Sanity");
    return ++_suspend_count;
  }

  unsigned dec_suspend_count() {
    assert(_lock->is_locked(), "Must be");
    assert(_suspend_count != 0, "Sanity");
    return --_suspend_count;
  }

  bool stopped() const {
    assert(_lock->is_locked(), "Must be");
    return _stop;
  }

  bool at_or_nearing_safepoint() const {
    return
        SafepointSynchronize::is_at_safepoint() ||
        SafepointSynchronize::is_synchronizing();
  }
  static constexpr int safepoint_poll_ms = 250;

  static int64_t now() { return os::javaTimeMillis(); }

  void run() override {
    log_info(trim)("NativeTrimmer start.");
    run_inner();
    log_info(trim)("NativeTrimmer stop.");
  }

  void run_inner() {

    bool trim_result = false;

    for (;;) {

      int64_t tnow = now();
      int64_t next_trim_time = tnow + TrimNativeHeapInterval;

      {
        MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);

        if (trim_result) {
          _num_trims_performed++;
        }

        do { // handle spurious wakeups

          if (_stop) {
            return;
          }

          if (suspended()) {
            ml.wait(0);
          } else if (next_trim_time > tnow) {
            ml.wait(next_trim_time - tnow);
          } else if (at_or_nearing_safepoint()) {
            ml.wait(safepoint_poll_ms);
          }

          if (_stop) {
            return;
          }

          tnow = now();

        } while (at_or_nearing_safepoint() || suspended() || next_trim_time > tnow);

      } // Lock scope

      // 2 - Trim outside of lock protection.
      trim_result = execute_trim_and_log(tnow);

    }
  }

  // Execute the native trim, log results.
  bool execute_trim_and_log(int64_t tnow) const {
    assert(os::can_trim_native_heap(), "Unexpected");
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
        log_debug(trim)("Total trims: " UINT64_FORMAT ".", _num_trims_performed);
        return true;
      } else {
        log_info(trim)("Trim native heap (no details)");
      }
    }
    return false;
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
    log_debug(trim)("NativeTrimmer pause (%s) (%u)", reason, n);
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
    log_debug(trim)("NativeTrimmer unpause (%s) (%u)", reason, n);
  }

  uint64_t num_trims_performed() const {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    return _num_trims_performed;
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

uint64_t TrimNative::num_trims_performed() {
  if (g_trimmer_thread != nullptr) {
    return g_trimmer_thread->num_trims_performed();
  }
  return 0;
}

