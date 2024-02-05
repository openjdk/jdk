/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_MUTEX_HPP
#define SHARE_RUNTIME_MUTEX_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

#if defined(LINUX) || defined(AIX) || defined(BSD)
# include "mutex_posix.hpp"
#else
# include OS_HEADER(mutex)
#endif


// A Mutex/Monitor is a simple wrapper around a native lock plus condition
// variable that supports lock ownership tracking, lock ranking for deadlock
// detection and coordinates with the safepoint protocol.

// Locking is non-recursive: if you try to lock a mutex you already own then you
// will get an assertion failure in a debug build (which should suffice to expose
// usage bugs). If you call try_lock on a mutex you already own it will return false.
// The underlying PlatformMutex may support recursive locking but this is not exposed
// and we account for that possibility in try_lock.

// A thread is not allowed to safepoint while holding a mutex whose rank
// is nosafepoint or lower.

class Mutex : public CHeapObj<mtSynchronizer> {

 public:
  // Special low level locks are given names and ranges avoid overlap.
  enum class Rank {
       event,
       service        = event          +   6,
       stackwatermark = service        +   3,
       tty            = stackwatermark +   3,
       oopstorage     = tty            +   3,
       nosafepoint    = oopstorage     +   6,
       safepoint      = nosafepoint    +  20
  };

  // want C++later "using enum" directives.
  static const Rank event          = Rank::event;
  static const Rank service        = Rank::service;
  static const Rank stackwatermark = Rank::stackwatermark;
  static const Rank tty            = Rank::tty;
  static const Rank oopstorage     = Rank::oopstorage;
  static const Rank nosafepoint    = Rank::nosafepoint;
  static const Rank safepoint      = Rank::safepoint;

  static void assert_no_overlap(Rank orig, Rank adjusted, int adjust);

  friend Rank operator-(Rank base, int adjust) {
    Rank result = static_cast<Rank>(static_cast<int>(base) - adjust);
    DEBUG_ONLY(assert_no_overlap(base, result, adjust));
    return result;
  }

  friend constexpr bool operator<(Rank lhs, Rank rhs) {
    return static_cast<int>(lhs) < static_cast<int>(rhs);
  }

  friend constexpr bool operator>(Rank lhs, Rank rhs)  { return rhs < lhs; }
  friend constexpr bool operator<=(Rank lhs, Rank rhs) { return !(lhs > rhs); }
  friend constexpr bool operator>=(Rank lhs, Rank rhs) { return !(lhs < rhs); }

 private:
  // The _owner field is only set by the current thread, either to itself after it has acquired
  // the low-level _lock, or to null before it has released the _lock. Accesses by any thread other
  // than the lock owner are inherently racy.
  Thread* volatile _owner;
  void raw_set_owner(Thread* new_owner) { Atomic::store(&_owner, new_owner); }

 protected:                              // Monitor-Mutex metadata
  PlatformMonitor _lock;                 // Native monitor implementation
  const char* _name;                     // Name of mutex/monitor

  // Debugging fields for naming, deadlock detection, etc. (some only used in debug mode)
#ifndef PRODUCT
  bool    _allow_vm_block;
#endif
#ifdef ASSERT
  Rank    _rank;                 // rank (to avoid/detect potential deadlocks)
  Mutex*  _next;                 // Used by a Thread to link up owned locks
  Thread* _last_owner;           // the last thread to own the lock
  bool _skip_rank_check;         // read only by owner when doing rank checks

  static Mutex* get_least_ranked_lock(Mutex* locks);
  Mutex* get_least_ranked_lock_besides_this(Mutex* locks);
  bool skip_rank_check() {
    assert(owned_by_self(), "only the owner should call this");
    return _skip_rank_check;
  }

 public:
  Rank   rank() const          { return _rank; }
  const char*  rank_name() const;
  Mutex* next()  const         { return _next; }
#endif // ASSERT

 protected:
  void set_owner_implementation(Thread* owner)                        NOT_DEBUG({ raw_set_owner(owner);});
  void check_block_state       (Thread* thread)                       NOT_DEBUG_RETURN;
  void check_safepoint_state   (Thread* thread)                       NOT_DEBUG_RETURN;
  void check_no_safepoint_state(Thread* thread)                       NOT_DEBUG_RETURN;
  void check_rank              (Thread* thread)                       NOT_DEBUG_RETURN;
  void assert_owner            (Thread* expected)                     NOT_DEBUG_RETURN;

 public:
  static const bool _allow_vm_block_flag        = true;

  // Locks can be acquired with or without a safepoint check. NonJavaThreads do not follow
  // the safepoint protocol when acquiring locks.

  // Each lock can be acquired by only JavaThreads, only NonJavaThreads, or shared between
  // Java and NonJavaThreads. When the lock is initialized with rank > nosafepoint,
  // that means that whenever the lock is acquired by a JavaThread, it will verify that
  // it is done with a safepoint check. In corollary, when the lock is initialized with
  // rank <= nosafepoint, that means that whenever the lock is acquired by a JavaThread
  // it will verify that it is done without a safepoint check.

  // TODO: Locks that are shared between JavaThreads and NonJavaThreads
  // should never encounter a safepoint check while they are held, or else a
  // deadlock can occur. We should check this by noting which
  // locks are shared, and walk held locks during safepoint checking.

  enum class SafepointCheckFlag {
    _safepoint_check_flag,
    _no_safepoint_check_flag
  };
  // Bring the enumerator names into class scope.
  static const SafepointCheckFlag _safepoint_check_flag =
    SafepointCheckFlag::_safepoint_check_flag;
  static const SafepointCheckFlag _no_safepoint_check_flag =
    SafepointCheckFlag::_no_safepoint_check_flag;

 public:
  Mutex(Rank rank, const char *name, bool allow_vm_block);

  Mutex(Rank rank, const char *name) :
    Mutex(rank, name, rank > nosafepoint ? false : true) {}

  ~Mutex();

  void lock(); // prints out warning if VM thread blocks
  void lock(Thread *thread); // overloaded with current thread
  void unlock();
  bool is_locked() const                     { return owner() != nullptr; }

  bool try_lock(); // Like lock(), but unblocking. It returns false instead
 private:
  void lock_contended(Thread *thread); // contended slow-path
  bool try_lock_inner(bool do_rank_checks);
 public:

  void release_for_safepoint();

  // Lock without safepoint check. Should ONLY be used by safepoint code and other code
  // that is guaranteed not to block while running inside the VM.
  void lock_without_safepoint_check();
  void lock_without_safepoint_check(Thread* self);
  // A thread should not call this if failure to acquire ownership will blocks its progress
  bool try_lock_without_rank_check();

  // Current owner - note not MT-safe. Can only be used to guarantee that
  // the current running thread owns the lock
  Thread* owner() const         { return Atomic::load(&_owner); }
  void set_owner(Thread* owner) { set_owner_implementation(owner); }
  bool owned_by_self() const;

  const char *name() const                  { return _name; }

  void print_on_error(outputStream* st) const;
  #ifndef PRODUCT
    void print_on(outputStream* st) const;
    void print() const;
  #endif
};

class Monitor : public Mutex {
 public:
  Monitor(Rank rank, const char *name, bool allow_vm_block)  :
    Mutex(rank, name, allow_vm_block) {}

  Monitor(Rank rank, const char *name) :
    Mutex(rank, name) {}
  // default destructor

  // Wait until monitor is notified (or times out).
  // Defaults are to make safepoint checks, wait time is forever (i.e.,
  // zero). Returns true if wait times out; otherwise returns false.
  bool wait(uint64_t timeout = 0);
  bool wait_without_safepoint_check(uint64_t timeout = 0);
  void notify();
  void notify_all();
};


class PaddedMutex : public Mutex {
  enum {
    CACHE_LINE_PADDING = (int)DEFAULT_PADDING_SIZE - (int)sizeof(Mutex),
    PADDING_LEN = CACHE_LINE_PADDING > 0 ? CACHE_LINE_PADDING : 1
  };
  char _padding[PADDING_LEN];
public:
  PaddedMutex(Rank rank, const char *name, bool allow_vm_block) : Mutex(rank, name, allow_vm_block) {};
  PaddedMutex(Rank rank, const char *name) : Mutex(rank, name) {};
};

class PaddedMonitor : public Monitor {
  enum {
    CACHE_LINE_PADDING = (int)DEFAULT_PADDING_SIZE - (int)sizeof(Monitor),
    PADDING_LEN = CACHE_LINE_PADDING > 0 ? CACHE_LINE_PADDING : 1
  };
  char _padding[PADDING_LEN];
 public:
  PaddedMonitor(Rank rank, const char *name, bool allow_vm_block) : Monitor(rank, name, allow_vm_block) {};
  PaddedMonitor(Rank rank, const char *name) : Monitor(rank, name) {};
};

#endif // SHARE_RUNTIME_MUTEX_HPP
