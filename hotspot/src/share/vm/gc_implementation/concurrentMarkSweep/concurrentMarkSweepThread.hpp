/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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

class ConcurrentMarkSweepGeneration;
class CMSCollector;

// The Concurrent Mark Sweep GC Thread (could be several in the future).
class ConcurrentMarkSweepThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class ConcurrentMarkSweepGeneration;   // XXX should remove friendship
  friend class CMSCollector;
 public:
  virtual void run();

 private:
  static ConcurrentMarkSweepThread*     _cmst;
  static CMSCollector*                  _collector;
  static SurrogateLockerThread*         _slt;
  static SurrogateLockerThread::SLT_msg_type _sltBuffer;
  static Monitor*                       _sltMonitor;

  ConcurrentMarkSweepThread*            _next;

  static bool _should_terminate;

  enum CMS_flag_type {
    CMS_nil             = NoBits,
    CMS_cms_wants_token = nth_bit(0),
    CMS_cms_has_token   = nth_bit(1),
    CMS_vm_wants_token  = nth_bit(2),
    CMS_vm_has_token    = nth_bit(3)
  };

  static int _CMS_flag;

  static bool CMS_flag_is_set(int b)        { return (_CMS_flag & b) != 0;   }
  static bool set_CMS_flag(int b)           { return (_CMS_flag |= b) != 0;  }
  static bool clear_CMS_flag(int b)         { return (_CMS_flag &= ~b) != 0; }
  void sleepBeforeNextCycle();

  // CMS thread should yield for a young gen collection, direct allocation,
  // and iCMS activity.
  static char _pad_1[64 - sizeof(jint)];    // prevent cache-line sharing
  static volatile jint _pending_yields;
  static volatile jint _pending_decrements; // decrements to _pending_yields
  static char _pad_2[64 - sizeof(jint)];    // prevent cache-line sharing

  // Tracing messages, enabled by CMSTraceThreadState.
  static inline void trace_state(const char* desc);

  static volatile bool _icms_enabled;   // iCMS enabled?
  static volatile bool _should_run;     // iCMS may run
  static volatile bool _should_stop;    // iCMS should stop

  // debugging
  void verify_ok_to_terminate() const PRODUCT_RETURN;

 public:
  // Constructor
  ConcurrentMarkSweepThread(CMSCollector* collector);

  static void makeSurrogateLockerThread(TRAPS);
  static SurrogateLockerThread* slt() { return _slt; }

  // Tester
  bool is_ConcurrentGC_thread() const { return true;       }

  static void threads_do(ThreadClosure* tc);

  // Printing
  void print_on(outputStream* st) const;
  void print() const                                  { print_on(tty); }
  static void print_all_on(outputStream* st);
  static void print_all()                             { print_all_on(tty); }

  // Returns the CMS Thread
  static ConcurrentMarkSweepThread* cmst()    { return _cmst; }
  static CMSCollector*         collector()    { return _collector;  }

  // Create and start the CMS Thread, or stop it on shutdown
  static ConcurrentMarkSweepThread* start(CMSCollector* collector);
  static void stop();
  static bool should_terminate() { return _should_terminate; }

  // Synchronization using CMS token
  static void synchronize(bool is_cms_thread);
  static void desynchronize(bool is_cms_thread);
  static bool vm_thread_has_cms_token() {
    return CMS_flag_is_set(CMS_vm_has_token);
  }
  static bool cms_thread_has_cms_token() {
    return CMS_flag_is_set(CMS_cms_has_token);
  }
  static bool vm_thread_wants_cms_token() {
    return CMS_flag_is_set(CMS_vm_wants_token);
  }
  static bool cms_thread_wants_cms_token() {
    return CMS_flag_is_set(CMS_cms_wants_token);
  }

  // Wait on CMS lock until the next synchronous GC
  // or given timeout, whichever is earlier.
  void    wait_on_cms_lock(long t); // milliseconds

  // The CMS thread will yield during the work portion of it's cycle
  // only when requested to.  Both synchronous and asychronous requests
  // are provided.  A synchronous request is used for young gen
  // collections and direct allocations.  The requesting thread increments
  // pending_yields at the beginning of an operation, and decrements it when
  // the operation is completed.  The CMS thread yields when pending_yields
  // is positive.  An asynchronous request is used by iCMS in the stop_icms()
  // operation. A single yield satisfies the outstanding asynch yield requests.
  // The requesting thread increments both pending_yields and pending_decrements.
  // After yielding, the CMS thread decrements both by the amount in
  // pending_decrements.
  // Note that, while "_pending_yields >= _pending_decrements" is an invariant,
  // we cannot easily test that invariant, since the counters are manipulated via
  // atomic instructions without explicit locking and we cannot read
  // the two counters atomically together: one suggestion is to
  // use (for example) 16-bit counters so as to be able to read the
  // two counters atomically even on 32-bit platforms. Notice that
  // the second assert in acknowledge_yield_request() does indeed
  // check a form of the above invariant, albeit indirectly.

  static void increment_pending_yields()   {
    Atomic::inc(&_pending_yields);
    assert(_pending_yields >= 0, "can't be negative");
  }
  static void decrement_pending_yields()   {
    Atomic::dec(&_pending_yields);
    assert(_pending_yields >= 0, "can't be negative");
  }
  static void asynchronous_yield_request() {
    increment_pending_yields();
    Atomic::inc(&_pending_decrements);
    assert(_pending_decrements >= 0, "can't be negative");
  }
  static void acknowledge_yield_request() {
    jint decrement = _pending_decrements;
    if (decrement > 0) {
      // Order important to preserve: _pending_yields >= _pending_decrements
      Atomic::add(-decrement, &_pending_decrements);
      Atomic::add(-decrement, &_pending_yields);
      assert(_pending_decrements >= 0, "can't be negative");
      assert(_pending_yields >= 0, "can't be negative");
    }
  }
  static bool should_yield()   { return _pending_yields > 0; }

  // CMS incremental mode.
  static void start_icms(); // notify thread to start a quantum of work
  static void stop_icms();  // request thread to stop working
  void icms_wait();         // if asked to stop, wait until notified to start

  // Incremental mode is enabled globally by the flag CMSIncrementalMode.  It
  // must also be enabled/disabled dynamically to allow foreground collections.
  static inline void enable_icms()              { _icms_enabled = true; }
  static inline void disable_icms()             { _icms_enabled = false; }
  static inline void set_icms_enabled(bool val) { _icms_enabled = val; }
  static inline bool icms_enabled()             { return _icms_enabled; }
};

inline void ConcurrentMarkSweepThread::trace_state(const char* desc) {
  if (CMSTraceThreadState) {
    char buf[128];
    TimeStamp& ts = gclog_or_tty->time_stamp();
    if (!ts.is_updated()) {
      ts.update();
    }
    jio_snprintf(buf, sizeof(buf), " [%.3f:  CMSThread %s] ",
                 ts.seconds(), desc);
    buf[sizeof(buf) - 1] = '\0';
    gclog_or_tty->print(buf);
  }
}

// For scoped increment/decrement of yield requests
class CMSSynchronousYieldRequest: public StackObj {
 public:
  CMSSynchronousYieldRequest() {
    ConcurrentMarkSweepThread::increment_pending_yields();
  }
  ~CMSSynchronousYieldRequest() {
    ConcurrentMarkSweepThread::decrement_pending_yields();
  }
};

// Used to emit a warning in case of unexpectedly excessive
// looping (in "apparently endless loops") in CMS code.
class CMSLoopCountWarn: public StackObj {
 private:
  const char* _src;
  const char* _msg;
  const intx  _threshold;
  intx        _ticks;

 public:
  inline CMSLoopCountWarn(const char* src, const char* msg,
                          const intx threshold) :
    _src(src), _msg(msg), _threshold(threshold), _ticks(0) { }

  inline void tick() {
    _ticks++;
    if (CMSLoopWarn && _ticks % _threshold == 0) {
      warning("%s has looped %d times %s", _src, _ticks, _msg);
    }
  }
};
