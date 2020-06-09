/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"

// A Mutex/Monitor is a simple wrapper around a native lock plus condition
// variable that supports lock ownership tracking, lock ranking for deadlock
// detection and coordinates with the safepoint protocol.

// The default length of mutex name was originally chosen to be 64 to avoid
// false sharing. Now, PaddedMutex and PaddedMonitor are available for this purpose.
// TODO: Check if _name[MUTEX_NAME_LEN] should better get replaced by const char*.
static const int MUTEX_NAME_LEN = 64;

class Mutex : public CHeapObj<mtSynchronizer> {

 public:
  // A special lock: Is a lock where you are guaranteed not to block while you are
  // holding it, i.e., no vm operation can happen, taking other (blocking) locks, etc.
  // The rank 'access' is similar to 'special' and has the same restrictions on usage.
  // It is reserved for locks that may be required in order to perform memory accesses
  // that require special barriers, e.g. SATB GC barriers, that in turn uses locks.
  // The rank 'tty' is also similar to 'special' and has the same restrictions.
  // It is reserved for the tty_lock.
  // Since memory accesses should be able to be performed pretty much anywhere
  // in the code, that requires locks required for performing accesses being
  // inherently a bit more special than even locks of the 'special' rank.
  // NOTE: It is critical that the rank 'special' be the lowest (earliest)
  // (except for "event" and "access") for the deadlock detection to work correctly.
  // The rank native was only for use in Mutexes created by JVM_RawMonitorCreate,
  // which being external to the VM are not subject to deadlock detection,
  // however it has now been used by other locks that don't fit into the
  // deadlock detection scheme.
  // While at a safepoint no mutexes of rank safepoint are held by any thread.
  // The rank named "leaf" is probably historical (and should
  // be changed) -- mutexes of this rank aren't really leaf mutexes
  // at all.
  enum lock_types {
       event,
       access         = event          +   1,
       tty            = access         +   2,
       special        = tty            +   3,
       suspend_resume = special        +   1,
       oopstorage     = suspend_resume +   2,
       leaf           = oopstorage     +   2,
       safepoint      = leaf           +  10,
       barrier        = safepoint      +   1,
       nonleaf        = barrier        +   1,
       max_nonleaf    = nonleaf        + 900,
       native         = max_nonleaf    +   1
  };

 protected:                              // Monitor-Mutex metadata
  Thread * volatile _owner;              // The owner of the lock
  os::PlatformMonitor _lock;             // Native monitor implementation
  char _name[MUTEX_NAME_LEN];            // Name of mutex/monitor

  // Debugging fields for naming, deadlock detection, etc. (some only used in debug mode)
#ifndef PRODUCT
  bool    _allow_vm_block;
  int     _rank;                 // rank (to avoid/detect potential deadlocks)
  Mutex*  _next;                 // Used by a Thread to link up owned locks
  Thread* _last_owner;           // the last thread to own the lock
  static bool contains(Mutex* locks, Mutex* lock);
  static Mutex* get_least_ranked_lock(Mutex* locks);
  Mutex* get_least_ranked_lock_besides_this(Mutex* locks);
#endif  // ASSERT

  void set_owner_implementation(Thread* owner)                        NOT_DEBUG({ _owner = owner;});
  void check_block_state       (Thread* thread)                       NOT_DEBUG_RETURN;
  void check_safepoint_state   (Thread* thread)                       NOT_DEBUG_RETURN;
  void check_no_safepoint_state(Thread* thread)                       NOT_DEBUG_RETURN;
  void assert_owner            (Thread* expected)                     NOT_DEBUG_RETURN;
  void no_safepoint_verifier   (Thread* thread, bool enable)          NOT_DEBUG_RETURN;

 public:
  enum {
    _allow_vm_block_flag        = true,
    _as_suspend_equivalent_flag = true
  };

  // Locks can be acquired with or without a safepoint check. NonJavaThreads do not follow
  // the safepoint protocol when acquiring locks.

  // Each lock can be acquired by only JavaThreads, only NonJavaThreads, or shared between
  // Java and NonJavaThreads. When the lock is initialized with _safepoint_check_always,
  // that means that whenever the lock is acquired by a JavaThread, it will verify that
  // it is done with a safepoint check. In corollary, when the lock is initialized with
  // _safepoint_check_never, that means that whenever the lock is acquired by a JavaThread
  // it will verify that it is done without a safepoint check.


  // There are a couple of existing locks that will sometimes have a safepoint check and
  // sometimes not when acquired by a JavaThread, but these locks are set up carefully
  // to avoid deadlocks. TODO: Fix these locks and remove _safepoint_check_sometimes.

  // TODO: Locks that are shared between JavaThreads and NonJavaThreads
  // should never encounter a safepoint check while they are held, or else a
  // deadlock can occur. We should check this by noting which
  // locks are shared, and walk held locks during safepoint checking.

  enum SafepointCheckFlag {
    _safepoint_check_flag,
    _no_safepoint_check_flag
  };

  enum SafepointCheckRequired {
    _safepoint_check_never,       // Mutexes with this value will cause errors
                                  // when acquired by a JavaThread with a safepoint check.
    _safepoint_check_sometimes,   // A couple of special locks are acquired by JavaThreads sometimes
                                  // with and sometimes without safepoint checks. These
                                  // locks will not produce errors when locked.
    _safepoint_check_always       // Mutexes with this value will cause errors
                                  // when acquired by a JavaThread without a safepoint check.
  };

  NOT_PRODUCT(SafepointCheckRequired _safepoint_check_required;)

 public:
  Mutex(int rank, const char *name, bool allow_vm_block = false,
        SafepointCheckRequired safepoint_check_required = _safepoint_check_always);
  ~Mutex();

  void lock(); // prints out warning if VM thread blocks
  void lock(Thread *thread); // overloaded with current thread
  void unlock();
  bool is_locked() const                     { return _owner != NULL; }

  bool try_lock(); // Like lock(), but unblocking. It returns false instead
 private:
  void lock_contended(Thread *thread); // contended slow-path
 public:

  void release_for_safepoint();

  // Lock without safepoint check. Should ONLY be used by safepoint code and other code
  // that is guaranteed not to block while running inside the VM.
  void lock_without_safepoint_check();
  void lock_without_safepoint_check(Thread* self);

  // Current owner - not not MT-safe. Can only be used to guarantee that
  // the current running thread owns the lock
  Thread* owner() const         { return _owner; }
  bool owned_by_self() const;

  const char *name() const                  { return _name; }

  void print_on_error(outputStream* st) const;

  #ifndef PRODUCT
    void print_on(outputStream* st) const;
    void print() const                      { print_on(::tty); }
  #endif
  #ifdef ASSERT
    int    rank() const          { return _rank; }
    bool   allow_vm_block()      { return _allow_vm_block; }

    Mutex *next()  const         { return _next; }
    void   set_next(Mutex *next) { _next = next; }
  #endif // ASSERT

  void set_owner(Thread* owner)             { set_owner_implementation(owner); }
};

class Monitor : public Mutex {
  void assert_wait_lock_state  (Thread* self)                         NOT_DEBUG_RETURN;
 public:
   Monitor(int rank, const char *name, bool allow_vm_block = false,
         SafepointCheckRequired safepoint_check_required = _safepoint_check_always);
   // default destructor

  // Wait until monitor is notified (or times out).
  // Defaults are to make safepoint checks, wait time is forever (i.e.,
  // zero), and not a suspend-equivalent condition. Returns true if wait
  // times out; otherwise returns false.
  bool wait(long timeout = 0,
            bool as_suspend_equivalent = !_as_suspend_equivalent_flag);
  bool wait_without_safepoint_check(long timeout = 0);
  void notify();
  void notify_all();
};


class PaddedMutex : public Mutex {
  enum {
    CACHE_LINE_PADDING = (int)DEFAULT_CACHE_LINE_SIZE - (int)sizeof(Mutex),
    PADDING_LEN = CACHE_LINE_PADDING > 0 ? CACHE_LINE_PADDING : 1
  };
  char _padding[PADDING_LEN];
public:
  PaddedMutex(int rank, const char *name, bool allow_vm_block = false,
              SafepointCheckRequired safepoint_check_required = _safepoint_check_always) :
    Mutex(rank, name, allow_vm_block, safepoint_check_required) {};
};

class PaddedMonitor : public Monitor {
  enum {
    CACHE_LINE_PADDING = (int)DEFAULT_CACHE_LINE_SIZE - (int)sizeof(Monitor),
    PADDING_LEN = CACHE_LINE_PADDING > 0 ? CACHE_LINE_PADDING : 1
  };
  char _padding[PADDING_LEN];
 public:
  PaddedMonitor(int rank, const char *name, bool allow_vm_block = false,
               SafepointCheckRequired safepoint_check_required = _safepoint_check_always) :
    Monitor(rank, name, allow_vm_block, safepoint_check_required) {};
};

#endif // SHARE_RUNTIME_MUTEX_HPP
