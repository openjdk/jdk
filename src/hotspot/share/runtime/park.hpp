/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_PARK_HPP
#define SHARE_RUNTIME_PARK_HPP

#include "runtime/atomic.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#if defined(LINUX) || defined(AIX) || defined(BSD)
# include "park_posix.hpp"
#else
# include OS_HEADER(park)
#endif

class Thread;

/*
 * Per-thread blocking support for JSR166. See the Java-level
 * documentation for rationale. Basically, park acts like wait, unpark
 * like notify.
 *
 * Parkers are inherently part of their associated JavaThread and are only
 * accessed when the JavaThread is guaranteed to be alive (e.g. by operating
 * on the current thread, or by having the thread protected by a
 * ThreadsListHandle.
 *
 * Class Parker is declared in shared code and extends the platform-specific
 * PlatformParker class, which contains the actual implementation
 * mechanics (condvars/events etc). The implementation for park() and unpark()
 * are also in the platform-specific os_<os>.cpp files.
 *
 * In the future we'll want to think about eliminating Parker and using
 * ParkEvent instead.  There's considerable duplication between the two
 * services.
 *
 */

class Parker : public PlatformParker {
 private:
  NONCOPYABLE(Parker);
 public:
  Parker() : PlatformParker() {}

  // For simplicity of interface with Java, all forms of park (indefinite,
  // relative, and absolute) are multiplexed into one call.
  void park(bool isAbsolute, jlong time);
  void unpark();
};

/////////////////////////////////////////////////////////////
//
// Lifecycle:
//
// 1. Dynamic ParkEvent:
//
//    Each Thread will have a dynamically created _ParkEvent, which will rely
//    on a "usage counter" to track when it's in use, and when it's safe to
//    delete.
//
//    To make sure the park event is not destroyed between getting hold of the
//    reference to it, and unparking the thread, we can "retain the usage".
//    This means that we increase the park event's usage counter when we can
//    guarantee that the thread to be unparked is still alive, until we have
//    completed the retention. Later after unparking we can "drop the usage",
//    which will decrease the usage counter by one, and if it gets down to
//    zero the park event will be deleted.
//
//    This way we can make sure that a (retained) reference to the park event
//    is still valid, even though the park event has outlived the associated
//    thread.
//
// 2. Embedded ParkEvent:
//
//    Each JavaThread will have an embedded _SleepEvent. Because it's embedded
//    in the JavaThread, its usage counter does not matter, instead it will be
//    destroyed with its associated thread. Note: JavaThread::interrupt() does
//    not need to retain usage of the _SleepEvent, since the thread in which
//    the _SleepEvent is embedded, is kept alive by a ThreadsListHandle until
//    after unparking has finished.
//
// 3. Immortal ParkEvent:
//
//    ObjectMonitor::_vthread_unparker_ParkEvent is used to wake up virtual
//    threads, and is never destroyed.
//
// Only the "associated" thread can block (park) on the ParkEvent, although
// any other thread can unpark it using a live park event. The usage counter
// (retain usage) is one such mechanism to keep the park event live during the
// unpark operation. Using a ThreadsListHandle (TLH) is another. But, since a
// TLH can not protect a non-Java thread waiting on a raw monitor, we don't
// use TLH, only usage counting.
//
// Park() is allowed to return spuriously. In fact park-unpark is really just
// an optimization to avoid unbounded spinning and surrender the CPU to be a
// polite system citizen.
//
// The base-class, PlatformEvent, is platform-specific while the ParkEvent is
// platform-independent.  PlatformEvent provides park(), unpark(), etc., and
// is abstract -- that is, a PlatformEvent should never be instantiated except
// as part of a ParkEvent.
// Equivalently we could have defined a platform-independent base-class that
// exported Allocate(), Release(), etc.  The platform-specific class would extend
// that base-class, adding park(), unpark(), etc.
//
// A word of caution: The JVM uses 2 very similar constructs:
// 1. ParkEvent are used for Java-level "monitor" synchronization.
// 2. Parkers are used by JSR166-JUC park-unpark.
//
// We'll want to eventually merge these redundant facilities and use ParkEvent.


class ParkEvent : public PlatformEvent {
  private:
    Atomic<int> _in_use_cnt;
    DEBUG_ONLY(uint32_t _canary;)

#ifdef ASSERT
    static const uint32_t _canary_live_park_event = 0x5041524b; // "PARK"
    static const uint32_t _canary_dead_park_event = 0x7061726b; // "park"
    static constexpr char _canary_assert_msg[] = "ParkEvent is not alive";
#endif

  public:
    ~ParkEvent() {
      assert(_canary == _canary_live_park_event, _canary_assert_msg);
      DEBUG_ONLY(_canary = _canary_dead_park_event;)
    }

    ParkEvent() : PlatformEvent() {
      _in_use_cnt.store_relaxed(1);
      DEBUG_ONLY(_canary = _canary_live_park_event;)
    }

    ParkEvent *retain_usage() {
      assert(_canary == _canary_live_park_event, _canary_assert_msg);
      int old = _in_use_cnt.fetch_then_add(1);
      assert(old > 0, "Can not retain a non-used (%d) ParkEvent", old);
      return this;
    }

    void drop_usage() {
      assert(_canary == _canary_live_park_event, _canary_assert_msg);
      int remain = this->_in_use_cnt.sub_then_fetch(1);
      assert(remain >= 0, "Can not drop usage of a non-used (%d) ParkEvent", remain);
      if (remain == 0) {
        delete this;
      }
    }

    void unpark_and_drop_usage() {
      unpark();
      drop_usage();
    }
} ;

#endif // SHARE_RUNTIME_PARK_HPP
