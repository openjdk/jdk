/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_BIASEDLOCKING_HPP
#define SHARE_VM_RUNTIME_BIASEDLOCKING_HPP

#include "runtime/handles.hpp"
#include "utilities/growableArray.hpp"

// This class describes operations to implement Store-Free Biased
// Locking. The high-level properties of the scheme are similar to
// IBM's lock reservation, Dice-Moir-Scherer QR locks, and other biased
// locking mechanisms. The principal difference is in the handling of
// recursive locking which is how this technique achieves a more
// efficient fast path than these other schemes.
//
// The basic observation is that in HotSpot's current fast locking
// scheme, recursive locking (in the fast path) causes no update to
// the object header. The recursion is described simply by stack
// records containing a specific value (NULL). Only the last unlock by
// a given thread causes an update to the object header.
//
// This observation, coupled with the fact that HotSpot only compiles
// methods for which monitor matching is obeyed (and which therefore
// can not throw IllegalMonitorStateException), implies that we can
// completely eliminate modifications to the object header for
// recursive locking in compiled code, and perform similar recursion
// checks and throwing of IllegalMonitorStateException in the
// interpreter with little or no impact on the performance of the fast
// path.
//
// The basic algorithm is as follows (note, see below for more details
// and information). A pattern in the low three bits is reserved in
// the object header to indicate whether biasing of a given object's
// lock is currently being done or is allowed at all.  If the bias
// pattern is present, the contents of the rest of the header are
// either the JavaThread* of the thread to which the lock is biased,
// or NULL, indicating that the lock is "anonymously biased". The
// first thread which locks an anonymously biased object biases the
// lock toward that thread. If another thread subsequently attempts to
// lock the same object, the bias is revoked.
//
// Because there are no updates to the object header at all during
// recursive locking while the lock is biased, the biased lock entry
// code is simply a test of the object header's value. If this test
// succeeds, the lock has been acquired by the thread. If this test
// fails, a bit test is done to see whether the bias bit is still
// set. If not, we fall back to HotSpot's original CAS-based locking
// scheme. If it is set, we attempt to CAS in a bias toward this
// thread. The latter operation is expected to be the rarest operation
// performed on these locks. We optimistically expect the biased lock
// entry to hit most of the time, and want the CAS-based fallthrough
// to occur quickly in the situations where the bias has been revoked.
//
// Revocation of the lock's bias is fairly straightforward. We want to
// restore the object's header and stack-based BasicObjectLocks and
// BasicLocks to the state they would have been in had the object been
// locked by HotSpot's usual fast locking scheme. To do this, we bring
// the system to a safepoint and walk the stack of the thread toward
// which the lock is biased. We find all of the lock records on the
// stack corresponding to this object, in particular the first /
// "highest" record. We fill in the highest lock record with the
// object's displaced header (which is a well-known value given that
// we don't maintain an identity hash nor age bits for the object
// while it's in the biased state) and all other lock records with 0,
// the value for recursive locks. When the safepoint is released, the
// formerly-biased thread and all other threads revert back to
// HotSpot's CAS-based locking.
//
// This scheme can not handle transfers of biases of single objects
// from thread to thread efficiently, but it can handle bulk transfers
// of such biases, which is a usage pattern showing up in some
// applications and benchmarks. We implement "bulk rebias" and "bulk
// revoke" operations using a "bias epoch" on a per-data-type basis.
// If too many bias revocations are occurring for a particular data
// type, the bias epoch for the data type is incremented at a
// safepoint, effectively meaning that all previous biases are
// invalid. The fast path locking case checks for an invalid epoch in
// the object header and attempts to rebias the object with a CAS if
// found, avoiding safepoints or bulk heap sweeps (the latter which
// was used in a prior version of this algorithm and did not scale
// well). If too many bias revocations persist, biasing is completely
// disabled for the data type by resetting the prototype header to the
// unbiased markOop. The fast-path locking code checks to see whether
// the instance's bias pattern differs from the prototype header's and
// causes the bias to be revoked without reaching a safepoint or,
// again, a bulk heap sweep.

// Biased locking counters
class BiasedLockingCounters VALUE_OBJ_CLASS_SPEC {
 private:
  int _total_entry_count;
  int _biased_lock_entry_count;
  int _anonymously_biased_lock_entry_count;
  int _rebiased_lock_entry_count;
  int _revoked_lock_entry_count;
  int _fast_path_entry_count;
  int _slow_path_entry_count;

 public:
  BiasedLockingCounters() :
    _total_entry_count(0),
    _biased_lock_entry_count(0),
    _anonymously_biased_lock_entry_count(0),
    _rebiased_lock_entry_count(0),
    _revoked_lock_entry_count(0),
    _fast_path_entry_count(0),
    _slow_path_entry_count(0) {}

  int slow_path_entry_count(); // Compute this field if necessary

  int* total_entry_count_addr()                   { return &_total_entry_count; }
  int* biased_lock_entry_count_addr()             { return &_biased_lock_entry_count; }
  int* anonymously_biased_lock_entry_count_addr() { return &_anonymously_biased_lock_entry_count; }
  int* rebiased_lock_entry_count_addr()           { return &_rebiased_lock_entry_count; }
  int* revoked_lock_entry_count_addr()            { return &_revoked_lock_entry_count; }
  int* fast_path_entry_count_addr()               { return &_fast_path_entry_count; }
  int* slow_path_entry_count_addr()               { return &_slow_path_entry_count; }

  bool nonzero() { return _total_entry_count > 0; }

  void print_on(outputStream* st);
  void print() { print_on(tty); }
};


class BiasedLocking : AllStatic {
private:
  static BiasedLockingCounters _counters;

public:
  static int* total_entry_count_addr();
  static int* biased_lock_entry_count_addr();
  static int* anonymously_biased_lock_entry_count_addr();
  static int* rebiased_lock_entry_count_addr();
  static int* revoked_lock_entry_count_addr();
  static int* fast_path_entry_count_addr();
  static int* slow_path_entry_count_addr();

  enum Condition {
    NOT_BIASED = 1,
    BIAS_REVOKED = 2,
    BIAS_REVOKED_AND_REBIASED = 3
  };

  // This initialization routine should only be called once and
  // schedules a PeriodicTask to turn on biased locking a few seconds
  // into the VM run to avoid startup time regressions
  static void init();

  // This provides a global switch for leaving biased locking disabled
  // for the first part of a run and enabling it later
  static bool enabled();

  // This should be called by JavaThreads to revoke the bias of an object
  static Condition revoke_and_rebias(Handle obj, bool attempt_rebias, TRAPS);

  // These do not allow rebiasing; they are used by deoptimization to
  // ensure that monitors on the stack can be migrated
  static void revoke(GrowableArray<Handle>* objs);
  static void revoke_at_safepoint(Handle obj);
  static void revoke_at_safepoint(GrowableArray<Handle>* objs);

  static void print_counters() { _counters.print(); }
  static BiasedLockingCounters* counters() { return &_counters; }

  // These routines are GC-related and should not be called by end
  // users. GCs which do not do preservation of mark words do not need
  // to call these routines.
  static void preserve_marks();
  static void restore_marks();
};

#endif // SHARE_VM_RUNTIME_BIASEDLOCKING_HPP
