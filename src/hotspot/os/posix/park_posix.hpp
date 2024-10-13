/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_PARK_POSIX_HPP
#define OS_POSIX_PARK_POSIX_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <pthread.h>

/*
 * This is the platform-specific implementation underpinning
 * the ParkEvent class, which itself underpins Java-level monitor
 * operations. See park.hpp for details.
 * These event objects are type-stable and immortal - we never delete them.
 * Events are associated with a thread for the lifetime of the thread.
 */
class PlatformEvent : public CHeapObj<mtSynchronizer> {
 private:
  double cachePad[4];        // Increase odds that _mutex is sole occupant of cache line
  volatile int _event;       // Event count/permit: -1, 0 or 1
  volatile int _nParked;     // Indicates if associated thread is blocked: 0 or 1
  pthread_mutex_t _mutex[1]; // Native mutex for locking
  pthread_cond_t  _cond[1];  // Native condition variable for blocking
  double postPad[2];

 protected:       // TODO-FIXME: make dtor private
  ~PlatformEvent() { guarantee(false, "invariant"); } // immortal so can't delete

 public:
  PlatformEvent();
  void park();
  int  park(jlong millis);
  int  park_nanos(jlong nanos);
  void unpark();

  // Use caution with reset() and fired() -- they may require MEMBARs
  void reset() { _event = 0; }
  int  fired() { return _event; }
};

// JSR166 support
// PlatformParker provides the platform dependent base class for the
// Parker class. It basically provides the internal data structures:
// - mutex and convars
// which are then used directly by the Parker methods defined in the OS
// specific implementation files.
// There is significant overlap between the funcionality supported in the
// combination of Parker+PlatformParker and PlatformEvent (above). If Parker
// were more like ObjectMonitor we could use PlatformEvent in both (with some
// API updates of course). But Parker methods use fastpaths that break that
// level of encapsulation - so combining the two remains a future project.

class PlatformParker {
  NONCOPYABLE(PlatformParker);
 protected:
  enum {
    REL_INDEX = 0,
    ABS_INDEX = 1
  };
  volatile int _counter;
  int _cur_index;  // which cond is in use: -1, 0, 1
  pthread_mutex_t _mutex[1];
  pthread_cond_t  _cond[2]; // one for relative times and one for absolute

 public:
  PlatformParker();
  ~PlatformParker();
};

#endif // OS_POSIX_PARK_POSIX_HPP
