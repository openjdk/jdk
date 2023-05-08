/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_WINDOWS_PARK_WINDOWS_HPP
#define OS_WINDOWS_PARK_WINDOWS_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class PlatformEvent : public CHeapObj<mtSynchronizer> {
  private:
    double CachePad [4] ;   // increase odds that _Event is sole occupant of cache line
    volatile int _Event ;
    HANDLE _ParkHandle ;

  public:       // TODO-FIXME: make dtor private
    ~PlatformEvent() { guarantee (0, "invariant") ; }

  public:
    PlatformEvent() {
      _Event   = 0 ;
      _ParkHandle = CreateEvent (nullptr, false, false, nullptr) ;
      guarantee (_ParkHandle != nullptr, "invariant") ;
    }

    // Exercise caution using reset() and fired() - they may require MEMBARs
    void reset() { _Event = 0 ; }
    int  fired() { return _Event; }
    void park();
    void unpark();
    int  park(jlong millis);
    int  park_nanos(jlong nanos);
};

class PlatformParker {
  NONCOPYABLE(PlatformParker);

 protected:
  HANDLE _ParkHandle;

 public:
  PlatformParker() {
    _ParkHandle = CreateEvent (nullptr, true, false, nullptr) ;
    guarantee(_ParkHandle != nullptr, "invariant") ;
  }
  ~PlatformParker() {
    CloseHandle(_ParkHandle);
  }
};

#endif // OS_WINDOWS_PARK_WINDOWS_HPP
