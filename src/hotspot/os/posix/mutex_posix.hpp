/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_MUTEX_POSIX_HPP
#define OS_POSIX_MUTEX_POSIX_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <pthread.h>


// Workaround for a bug in macOSX kernel's pthread support (fixed in Mojave?).
// Avoid ever allocating a pthread_mutex_t at the same address as one of our
// former pthread_cond_t, by using freelists of mutexes and condvars.
// Conditional to avoid extra indirection and padding loss on other platforms.
#ifdef __APPLE__
#define PLATFORM_MONITOR_IMPL_INDIRECT 1
#else
#define PLATFORM_MONITOR_IMPL_INDIRECT 0
#endif

// Platform specific implementations that underpin VM Mutex/Monitor classes.
// Note that we use "normal" pthread_mutex_t attributes so that recursive
// locking is not supported, which matches the expected semantics of the
// VM Mutex class.

class PlatformMutex : public CHeapObj<mtSynchronizer> {
#if PLATFORM_MONITOR_IMPL_INDIRECT
  class Mutex : public CHeapObj<mtSynchronizer> {
   public:
    pthread_mutex_t _mutex;
    Mutex* _next;

    Mutex();
    ~Mutex();
  };

  Mutex* _impl;

  static pthread_mutex_t _freelist_lock; // used for mutex and cond freelists
  static Mutex* _mutex_freelist;

 protected:
  class WithFreeListLocked;
  pthread_mutex_t* mutex() { return &(_impl->_mutex); }

 public:
  PlatformMutex();              // Use freelist allocation of impl.
  ~PlatformMutex();

  static void init();           // Initialize the freelist.

#else

  pthread_mutex_t _mutex;

 protected:
  pthread_mutex_t* mutex() { return &_mutex; }

 public:
  static void init() {}         // Nothing needed for the non-indirect case.

  PlatformMutex();
  ~PlatformMutex();

#endif // PLATFORM_MONITOR_IMPL_INDIRECT

 private:
  NONCOPYABLE(PlatformMutex);

 public:
  void lock();
  void unlock();
  bool try_lock();
};

class PlatformMonitor : public PlatformMutex {
#if PLATFORM_MONITOR_IMPL_INDIRECT
  class Cond : public CHeapObj<mtSynchronizer> {
   public:
    pthread_cond_t _cond;
    Cond* _next;

    Cond();
    ~Cond();
  };

  Cond* _impl;

  static Cond* _cond_freelist;

  pthread_cond_t* cond() { return &(_impl->_cond); }

 public:
  PlatformMonitor();            // Use freelist allocation of impl.
  ~PlatformMonitor();

#else

  pthread_cond_t _cond;
  pthread_cond_t* cond() { return &_cond; }

 public:
  PlatformMonitor();
  ~PlatformMonitor();

#endif // PLATFORM_MONITOR_IMPL_INDIRECT

 private:
  NONCOPYABLE(PlatformMonitor);

 public:
  int wait(jlong millis);
  void notify();
  void notify_all();
};

#endif // OS_POSIX_MUTEX_POSIX_HPP
