/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPTHREAD_HPP
#define SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPTHREAD_HPP

#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "runtime/thread.hpp"

class ConcurrentMarkSweepGeneration;
class CMSCollector;

// The Concurrent Mark Sweep GC Thread
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

  // CMS thread should yield for a young gen collection and direct allocations
  static char _pad_1[64 - sizeof(jint)];    // prevent cache-line sharing
  static volatile jint _pending_yields;
  static char _pad_2[64 - sizeof(jint)];    // prevent cache-line sharing

  // debugging
  void verify_ok_to_terminate() const PRODUCT_RETURN;

 public:
  // Constructor
  ConcurrentMarkSweepThread(CMSCollector* collector);

  static void makeSurrogateLockerThread(TRAPS);
  static SurrogateLockerThread* slt() { return _slt; }

  static void threads_do(ThreadClosure* tc);

  // Printing
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
  // or given timeout, whichever is earlier. A timeout value
  // of 0 indicates that there is no upper bound on the wait time.
  // A concurrent full gc request terminates the wait.
  void wait_on_cms_lock(long t_millis);

  // Wait on CMS lock until the next synchronous GC
  // or given timeout, whichever is earlier. A timeout value
  // of 0 indicates that there is no upper bound on the wait time.
  // A concurrent full gc request terminates the wait.
  void wait_on_cms_lock_for_scavenge(long t_millis);

  // The CMS thread will yield during the work portion of its cycle
  // only when requested to.
  // A synchronous request is used for young gen collections and
  // for direct allocations.  The requesting thread increments
  // _pending_yields at the beginning of an operation, and decrements
  // _pending_yields when that operation is completed.
  // In turn, the CMS thread yields when _pending_yields is positive,
  // and continues to yield until the value reverts to 0.

  static void increment_pending_yields()   {
    Atomic::inc(&_pending_yields);
    assert(_pending_yields >= 0, "can't be negative");
  }
  static void decrement_pending_yields()   {
    Atomic::dec(&_pending_yields);
    assert(_pending_yields >= 0, "can't be negative");
  }
  static bool should_yield()   { return _pending_yields > 0; }
};

// For scoped increment/decrement of (synchronous) yield requests
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
      warning("%s has looped " INTX_FORMAT " times %s", _src, _ticks, _msg);
    }
  }
};

#endif // SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPTHREAD_HPP
