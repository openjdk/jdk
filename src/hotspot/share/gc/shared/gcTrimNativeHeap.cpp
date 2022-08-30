/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

bool GCTrimNative::_async_mode = false;
double GCTrimNative::_next_trim_not_before = 0;

// GCTrimNative works in two modes:
//
// - async mode, where GCTrimNative runs a trimmer thread on behalf of the GC.
//   The trimmer thread will be doing all the trims, either periodically or
//   triggered from outside via GCTrimNative::schedule_trim().
//
// - synchronous mode, where the GC does the trimming itself in its own thread,
//   via GCTrimNative::should_trim() and GCTrimNative::execute_trim().
//
// The mode is set as argument to GCTrimNative::initialize().

class NativeTrimmer : public ConcurrentGCThread {

  Monitor* _lock;
  static NativeTrimmer* _the_trimmer;

protected:

  virtual void run_service() {
    assert(GCTrimNativeHeap, "Sanity");
    assert(os::can_trim_native_heap(), "Sanity");

    const int64_t delay_ms = GCTrimNativeHeapInterval * 1000;
    for (;;) {
      MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
      ml.wait(delay_ms); // Note: GCTrimNativeHeapDelay == 0 disables periodic trim, no timeout then
      if (should_terminate()) {
        log_debug(gc, trim)("NativeTrimmer stopped.");
        break;
      }
      if (os::should_trim_native_heap(GCTrimNativeHeapRetainSize)) {
        GCTrimNative::do_trim();
      }
    }
  }

  void wakeup() {
    MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
    ml.notify_all();
  }

  virtual void stop_service() {
    wakeup();
  }

public:

  NativeTrimmer() :
    _lock(new (std::nothrow) PaddedMonitor(Mutex::nosafepoint, "NativeTrimmer_lock"))
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

  static void schedule_trim_now() {
    _the_trimmer->wakeup();
  }

}; // NativeTrimmer

NativeTrimmer* NativeTrimmer::_the_trimmer = nullptr;

#define PROPERFMT         SIZE_FORMAT "%s"
#define PROPERARGS(S)     byte_size_in_proper_unit(S), proper_unit_for_byte_size(S)

void GCTrimNative::do_trim() {
  Ticks start = Ticks::now();
  os::size_change_t sc;
  if (os::trim_native_heap(&sc)) {
    Tickspan trim_time = (Ticks::now() - start);
    if (sc.after != SIZE_MAX) {
      const size_t delta = sc.after < sc.before ? (sc.before - sc.after) : (sc.after - sc.before);
      const char sign = sc.after < sc.before ? '-' : '+';
      log_debug(gc, trim)("Trim native heap (retain size: " PROPERFMT "): "
                          "RSS+Swap: " PROPERFMT "->" PROPERFMT " (%c" PROPERFMT "), %1.3fms",
                          PROPERARGS(GCTrimNativeHeapRetainSize),
                          PROPERARGS(sc.before), PROPERARGS(sc.after),
                          sign, PROPERARGS(delta),
                          trim_time.seconds() * 1000);
    } else {
      log_debug(gc, trim)("Trim native heap (no details)");
    }
  }
}

/// GCTrimNative outside facing methods

void GCTrimNative::initialize(bool async_mode) {

  if (GCTrimNativeHeap) {

    if (!os::can_trim_native_heap()) {
      FLAG_SET_ERGO(GCTrimNativeHeap, false);
      log_info(gc, trim)("GCTrimNativeHeap disabled - trim-native not supported on this platform.");
      return;
    }

    log_debug(gc, trim)("GCTrimNativeHeap enabled.");

    _async_mode = async_mode;

    // If we are to run the trimmer on behalf of the GC:
    if (_async_mode) {
      NativeTrimmer::start_trimmer();
      log_debug(gc, trim)("Native Trimmer enabled.");
    }

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
      (ignore_delay || os::elapsedTime() > _next_trim_not_before) &&
      os::should_trim_native_heap(GCTrimNativeHeapRetainSize);
}

void GCTrimNative::execute_trim() {
  if (GCTrimNativeHeap) {
    assert(!_async_mode, "Only call for non-async mode");
    do_trim();
    _next_trim_not_before = os::elapsedTime() + GCTrimNativeHeapInterval;
  }
}

// Schedule trim-native for execution in the trimmer thread; return immediately.
void GCTrimNative::schedule_trim() {
  if (GCTrimNativeHeap) {
    assert(_async_mode, "Only call for async mode");
    NativeTrimmer::schedule_trim_now();
  }
}
