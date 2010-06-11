/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

// WARNING:
//   This is a very sensitive and fragile class. DO NOT make any
// change unless you are fully aware of the underlying semantics.

//   This class can not inherit from any other class, because I have
// to let the displaced header be the very first word. Otherwise I
// have to let markOop include this file, which would export the
// monitor data structure to everywhere.
//
// The ObjectMonitor class is used to implement JavaMonitors which have
// transformed from the lightweight structure of the thread stack to a
// heavy weight lock due to contention

// It is also used as RawMonitor by the JVMTI


class ObjectWaiter;

class ObjectMonitor {
 public:
  enum {
    OM_OK,                    // no error
    OM_SYSTEM_ERROR,          // operating system error
    OM_ILLEGAL_MONITOR_STATE, // IllegalMonitorStateException
    OM_INTERRUPTED,           // Thread.interrupt()
    OM_TIMED_OUT              // Object.wait() timed out
  };

 public:
  // TODO-FIXME: the "offset" routines should return a type of off_t instead of int ...
  // ByteSize would also be an appropriate type.
  static int header_offset_in_bytes()      { return offset_of(ObjectMonitor, _header);     }
  static int object_offset_in_bytes()      { return offset_of(ObjectMonitor, _object);     }
  static int owner_offset_in_bytes()       { return offset_of(ObjectMonitor, _owner);      }
  static int count_offset_in_bytes()       { return offset_of(ObjectMonitor, _count);      }
  static int recursions_offset_in_bytes()  { return offset_of(ObjectMonitor, _recursions); }
  static int cxq_offset_in_bytes()         { return offset_of(ObjectMonitor, _cxq) ;       }
  static int succ_offset_in_bytes()        { return offset_of(ObjectMonitor, _succ) ;      }
  static int EntryList_offset_in_bytes()   { return offset_of(ObjectMonitor, _EntryList);  }
  static int FreeNext_offset_in_bytes()    { return offset_of(ObjectMonitor, FreeNext);    }
  static int WaitSet_offset_in_bytes()     { return offset_of(ObjectMonitor, _WaitSet) ;   }
  static int Responsible_offset_in_bytes() { return offset_of(ObjectMonitor, _Responsible);}
  static int Spinner_offset_in_bytes()     { return offset_of(ObjectMonitor, _Spinner);    }

 public:
  // Eventaully we'll make provisions for multiple callbacks, but
  // now one will suffice.
  static int (*SpinCallbackFunction)(intptr_t, int) ;
  static intptr_t SpinCallbackArgument ;


 public:
  ObjectMonitor();
  ~ObjectMonitor();

  markOop   header() const;
  void      set_header(markOop hdr);

  intptr_t  is_busy() const;
  intptr_t  is_entered(Thread* current) const;

  void*     owner() const;
  void      set_owner(void* owner);

  intptr_t  waiters() const;

  intptr_t  count() const;
  void      set_count(intptr_t count);
  intptr_t  contentions() const ;

  // JVM/DI GetMonitorInfo() needs this
  Thread *  thread_of_waiter (ObjectWaiter *) ;
  ObjectWaiter * first_waiter () ;
  ObjectWaiter * next_waiter(ObjectWaiter* o);

  intptr_t  recursions() const { return _recursions; }

  void*     object() const;
  void*     object_addr();
  void      set_object(void* obj);

  bool      check(TRAPS);       // true if the thread owns the monitor.
  void      check_slow(TRAPS);
  void      clear();
#ifndef PRODUCT
  void      verify();
  void      print();
#endif

  bool      try_enter (TRAPS) ;
  void      enter(TRAPS);
  void      exit(TRAPS);
  void      wait(jlong millis, bool interruptable, TRAPS);
  void      notify(TRAPS);
  void      notifyAll(TRAPS);

// Use the following at your own risk
  intptr_t  complete_exit(TRAPS);
  void      reenter(intptr_t recursions, TRAPS);

  int       raw_enter(TRAPS);
  int       raw_exit(TRAPS);
  int       raw_wait(jlong millis, bool interruptable, TRAPS);
  int       raw_notify(TRAPS);
  int       raw_notifyAll(TRAPS);

 private:
  // JVMTI support -- remove ASAP
  int       SimpleEnter (Thread * Self) ;
  int       SimpleExit  (Thread * Self) ;
  int       SimpleWait  (Thread * Self, jlong millis) ;
  int       SimpleNotify (Thread * Self, bool All) ;

 private:
  void      Recycle () ;
  void      AddWaiter (ObjectWaiter * waiter) ;

  ObjectWaiter * DequeueWaiter () ;
  void      DequeueSpecificWaiter (ObjectWaiter * waiter) ;
  void      EnterI (TRAPS) ;
  void      ReenterI (Thread * Self, ObjectWaiter * SelfNode) ;
  void      UnlinkAfterAcquire (Thread * Self, ObjectWaiter * SelfNode) ;
  int       TryLock (Thread * Self) ;
  int       NotRunnable (Thread * Self, Thread * Owner) ;
  int       TrySpin_Fixed (Thread * Self) ;
  int       TrySpin_VaryFrequency (Thread * Self) ;
  int       TrySpin_VaryDuration  (Thread * Self) ;
  void      ctAsserts () ;
  void      ExitEpilog (Thread * Self, ObjectWaiter * Wakee) ;
  bool      ExitSuspendEquivalent (JavaThread * Self) ;

 private:
  friend class ObjectSynchronizer;
  friend class ObjectWaiter;
  friend class VMStructs;

  // WARNING: this must be the very first word of ObjectMonitor
  // This means this class can't use any virtual member functions.
  // TODO-FIXME: assert that offsetof(_header) is 0 or get rid of the
  // implicit 0 offset in emitted code.

  volatile markOop   _header;       // displaced object header word - mark
  void*     volatile _object;       // backward object pointer - strong root

  double SharingPad [1] ;           // temp to reduce false sharing

  // All the following fields must be machine word aligned
  // The VM assumes write ordering wrt these fields, which can be
  // read from other threads.

  void *  volatile _owner;          // pointer to owning thread OR BasicLock
  volatile intptr_t  _recursions;   // recursion count, 0 for first entry
  int OwnerIsThread ;               // _owner is (Thread *) vs SP/BasicLock
  ObjectWaiter * volatile _cxq ;    // LL of recently-arrived threads blocked on entry.
                                    // The list is actually composed of WaitNodes, acting
                                    // as proxies for Threads.
  ObjectWaiter * volatile _EntryList ;     // Threads blocked on entry or reentry.
  Thread * volatile _succ ;          // Heir presumptive thread - used for futile wakeup throttling
  Thread * volatile _Responsible ;
  int _PromptDrain ;                // rqst to drain cxq into EntryList ASAP

  volatile int _Spinner ;           // for exit->spinner handoff optimization
  volatile int _SpinFreq ;          // Spin 1-out-of-N attempts: success rate
  volatile int _SpinClock ;
  volatile int _SpinDuration ;
  volatile intptr_t _SpinState ;    // MCS/CLH list of spinners

  // TODO-FIXME: _count, _waiters and _recursions should be of
  // type int, or int32_t but not intptr_t.  There's no reason
  // to use 64-bit fields for these variables on a 64-bit JVM.

  volatile intptr_t  _count;        // reference count to prevent reclaimation/deflation
                                    // at stop-the-world time.  See deflate_idle_monitors().
                                    // _count is approximately |_WaitSet| + |_EntryList|
  volatile intptr_t  _waiters;      // number of waiting threads
  ObjectWaiter * volatile _WaitSet; // LL of threads wait()ing on the monitor
  volatile int _WaitSetLock;        // protects Wait Queue - simple spinlock

 public:
  int _QMix ;                       // Mixed prepend queue discipline
  ObjectMonitor * FreeNext ;        // Free list linkage
  intptr_t StatA, StatsB ;

};
