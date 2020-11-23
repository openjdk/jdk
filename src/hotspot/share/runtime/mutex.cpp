/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"

#ifdef ASSERT
void Mutex::check_block_state(Thread* thread) {
  if (!_allow_vm_block && thread->is_VM_thread()) {
    // JavaThreads are checked to make sure that they do not hold _allow_vm_block locks during operations
    // that could safepoint.  Make sure the vm thread never uses locks with _allow_vm_block == false.
    fatal("VM thread could block on lock that may be held by a JavaThread during safepoint: %s", name());
  }

  assert(!os::ThreadCrashProtection::is_crash_protected(thread),
         "locking not allowed when crash protection is set");
}

void Mutex::check_safepoint_state(Thread* thread) {
  check_block_state(thread);

  // If the JavaThread checks for safepoint, verify that the lock wasn't created with safepoint_check_never.
  if (thread->is_active_Java_thread()) {
    assert(_safepoint_check_required != _safepoint_check_never,
           "This lock should never have a safepoint check for Java threads: %s",
           name());

    // Also check NoSafepointVerifier, and thread state is _thread_in_vm
    thread->check_for_valid_safepoint_state();
  } else {
    // If initialized with safepoint_check_never, a NonJavaThread should never ask to safepoint check either.
    assert(_safepoint_check_required != _safepoint_check_never,
           "NonJavaThread should not check for safepoint");
  }
}

void Mutex::check_no_safepoint_state(Thread* thread) {
  check_block_state(thread);
  assert(!thread->is_active_Java_thread() || _safepoint_check_required != _safepoint_check_always,
         "This lock should always have a safepoint check for Java threads: %s",
         name());
}
#endif // ASSERT

void Mutex::lock_contended(Thread* self) {
  Mutex *in_flight_mutex = NULL;
  DEBUG_ONLY(int retry_cnt = 0;)
  bool is_active_Java_thread = self->is_active_Java_thread();
  do {
    #ifdef ASSERT
    if (retry_cnt++ > 3) {
      log_trace(vmmutex)("JavaThread " INTPTR_FORMAT " on %d attempt trying to acquire vmmutex %s", p2i(self), retry_cnt, _name);
    }
    #endif // ASSERT

    // Is it a JavaThread participating in the safepoint protocol.
    if (is_active_Java_thread) {
      assert(rank() > Mutex::special, "Potential deadlock with special or lesser rank mutex");
      { ThreadBlockInVMWithDeadlockCheck tbivmdc(self->as_Java_thread(), &in_flight_mutex);
        in_flight_mutex = this;  // save for ~ThreadBlockInVMWithDeadlockCheck
        _lock.lock();
      }
      if (in_flight_mutex != NULL) {
        // Not unlocked by ~ThreadBlockInVMWithDeadlockCheck
        break;
      }
    } else {
      _lock.lock();
      break;
    }
  } while (!_lock.try_lock());
}

void Mutex::lock(Thread* self) {
  assert(owner() != self, "invariant");

  check_safepoint_state(self);
  check_rank(self);

  if (!_lock.try_lock()) {
    // The lock is contended, use contended slow-path function to lock
    lock_contended(self);
  }

  assert_owner(NULL);
  set_owner(self);
}

void Mutex::lock() {
  lock(Thread::current());
}

// Lock without safepoint check - a degenerate variant of lock() for use by
// JavaThreads when it is known to be safe to not check for a safepoint when
// acquiring this lock. If the thread blocks acquiring the lock it is not
// safepoint-safe and so will prevent a safepoint from being reached. If used
// in the wrong way this can lead to a deadlock with the safepoint code.

void Mutex::lock_without_safepoint_check(Thread * self) {
  assert(owner() != self, "invariant");

  check_no_safepoint_state(self);
  check_rank(self);

  _lock.lock();
  assert_owner(NULL);
  set_owner(self);
}

void Mutex::lock_without_safepoint_check() {
  lock_without_safepoint_check(Thread::current());
}


// Returns true if thread succeeds in grabbing the lock, otherwise false.
bool Mutex::try_lock_inner(bool do_rank_checks) {
  Thread * const self = Thread::current();
  // Checking the owner hides the potential difference in recursive locking behaviour
  // on some platforms.
  if (owner() == self) {
    return false;
  }

  if (do_rank_checks) {
    check_rank(self);
  }
  // Some safepoint_check_always locks use try_lock, so cannot check
  // safepoint state, but can check blocking state.
  check_block_state(self);

  if (_lock.try_lock()) {
    assert_owner(NULL);
    set_owner(self);
    return true;
  }
  return false;
}

bool Mutex::try_lock() {
  return try_lock_inner(true /* do_rank_checks */);
}

bool Mutex::try_lock_without_rank_check() {
  bool res = try_lock_inner(false /* do_rank_checks */);
  DEBUG_ONLY(if (res) _skip_rank_check = true;)
  return res;
}

void Mutex::release_for_safepoint() {
  assert_owner(NULL);
  _lock.unlock();
}

void Mutex::unlock() {
  DEBUG_ONLY(assert_owner(Thread::current()));
  set_owner(NULL);
  _lock.unlock();
}

void Monitor::notify() {
  DEBUG_ONLY(assert_owner(Thread::current()));
  _lock.notify();
}

void Monitor::notify_all() {
  DEBUG_ONLY(assert_owner(Thread::current()));
  _lock.notify_all();
}

bool Monitor::wait_without_safepoint_check(int64_t timeout) {
  Thread* const self = Thread::current();

  // timeout is in milliseconds - with zero meaning never timeout
  assert(timeout >= 0, "negative timeout");
  assert_owner(self);
  check_rank(self);

  // conceptually set the owner to NULL in anticipation of
  // abdicating the lock in wait
  set_owner(NULL);

  // Check safepoint state after resetting owner and possible NSV.
  check_no_safepoint_state(self);

  int wait_status = _lock.wait(timeout);
  set_owner(self);
  return wait_status != 0;          // return true IFF timeout
}

bool Monitor::wait(int64_t timeout, bool as_suspend_equivalent) {
  JavaThread* const self = JavaThread::current();
  // Safepoint checking logically implies an active JavaThread.
  assert(self->is_active_Java_thread(), "invariant");

  // timeout is in milliseconds - with zero meaning never timeout
  assert(timeout >= 0, "negative timeout");
  assert_owner(self);
  check_rank(self);

  // conceptually set the owner to NULL in anticipation of
  // abdicating the lock in wait
  set_owner(NULL);

  // Check safepoint state after resetting owner and possible NSV.
  check_safepoint_state(self);

  int wait_status;
  Mutex* in_flight_mutex = NULL;

  {
    ThreadBlockInVMWithDeadlockCheck tbivmdc(self, &in_flight_mutex);
    OSThreadWaitState osts(self->osthread(), false /* not Object.wait() */);
    if (as_suspend_equivalent) {
      self->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or
      // java_suspend_self()
    }

    wait_status = _lock.wait(timeout);
    in_flight_mutex = this;  // save for ~ThreadBlockInVMWithDeadlockCheck

    // were we externally suspended while we were waiting?
    if (as_suspend_equivalent && self->handle_special_suspend_equivalent_condition()) {
      // Our event wait has finished and we own the lock, but
      // while we were waiting another thread suspended us. We don't
      // want to hold the lock while suspended because that
      // would surprise the thread that suspended us.
      _lock.unlock();
      self->java_suspend_self();
      _lock.lock();
    }
  }

  if (in_flight_mutex != NULL) {
    // Not unlocked by ~ThreadBlockInVMWithDeadlockCheck
    assert_owner(NULL);
    // Conceptually reestablish ownership of the lock.
    set_owner(self);
  } else {
    lock(self);
  }

  return wait_status != 0;          // return true IFF timeout
}

Mutex::~Mutex() {
  assert_owner(NULL);
}

// Only Threads_lock, Heap_lock and SR_lock may be safepoint_check_sometimes.
bool is_sometimes_ok(const char* name) {
  return (strcmp(name, "Threads_lock") == 0 || strcmp(name, "Heap_lock") == 0 || strcmp(name, "SR_lock") == 0);
}

Mutex::Mutex(int Rank, const char * name, bool allow_vm_block,
             SafepointCheckRequired safepoint_check_required) : _owner(NULL) {
  assert(os::mutex_init_done(), "Too early!");
  if (name == NULL) {
    strcpy(_name, "UNKNOWN");
  } else {
    strncpy(_name, name, MUTEX_NAME_LEN - 1);
    _name[MUTEX_NAME_LEN - 1] = '\0';
  }
#ifdef ASSERT
  _allow_vm_block  = allow_vm_block;
  _rank            = Rank;
  _safepoint_check_required = safepoint_check_required;
  _skip_rank_check = false;

  assert(_safepoint_check_required != _safepoint_check_sometimes || is_sometimes_ok(name),
         "Lock has _safepoint_check_sometimes %s", name);

  assert(_rank > special || _safepoint_check_required == _safepoint_check_never,
         "Special locks or below should never safepoint");
#endif
}

Monitor::Monitor(int Rank, const char * name, bool allow_vm_block,
             SafepointCheckRequired safepoint_check_required) :
  Mutex(Rank, name, allow_vm_block, safepoint_check_required) {}

bool Mutex::owned_by_self() const {
  return owner() == Thread::current();
}

void Mutex::print_on_error(outputStream* st) const {
  st->print("[" PTR_FORMAT, p2i(this));
  st->print("] %s", _name);
  st->print(" - owner thread: " PTR_FORMAT, p2i(owner()));
}

// ----------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
const char* print_safepoint_check(Mutex::SafepointCheckRequired safepoint_check) {
  switch (safepoint_check) {
  case Mutex::_safepoint_check_never:     return "safepoint_check_never";
  case Mutex::_safepoint_check_sometimes: return "safepoint_check_sometimes";
  case Mutex::_safepoint_check_always:    return "safepoint_check_always";
  default: return "";
  }
}

void Mutex::print_on(outputStream* st) const {
  st->print("Mutex: [" PTR_FORMAT "] %s - owner: " PTR_FORMAT,
            p2i(this), _name, p2i(owner()));
  if (_allow_vm_block) {
    st->print("%s", " allow_vm_block");
  }
  st->print(" %s", print_safepoint_check(_safepoint_check_required));
  st->cr();
}
#endif // PRODUCT

#ifdef ASSERT
void Mutex::assert_owner(Thread * expected) {
  const char* msg = "invalid owner";
  if (expected == NULL) {
    msg = "should be un-owned";
  }
  else if (expected == Thread::current()) {
    msg = "should be owned by current thread";
  }
  assert(owner() == expected,
         "%s: owner=" INTPTR_FORMAT ", should be=" INTPTR_FORMAT,
         msg, p2i(owner()), p2i(expected));
}

Mutex* Mutex::get_least_ranked_lock(Mutex* locks) {
  Mutex *res, *tmp;
  for (res = tmp = locks; tmp != NULL; tmp = tmp->next()) {
    if (tmp->rank() < res->rank()) {
      res = tmp;
    }
  }
  return res;
}

Mutex* Mutex::get_least_ranked_lock_besides_this(Mutex* locks) {
  Mutex *res, *tmp;
  for (res = NULL, tmp = locks; tmp != NULL; tmp = tmp->next()) {
    if (tmp != this && (res == NULL || tmp->rank() < res->rank())) {
      res = tmp;
    }
  }
  assert(res != this, "invariant");
  return res;
}

// Tests for rank violations that might indicate exposure to deadlock.
void Mutex::check_rank(Thread* thread) {
  assert(this->rank() >= 0, "bad lock rank");
  Mutex* locks_owned = thread->owned_locks();

  if (!SafepointSynchronize::is_at_safepoint()) {
    // We expect the locks already acquired to be in increasing rank order,
    // modulo locks of native rank or acquired in try_lock_without_rank_check()
    for (Mutex* tmp = locks_owned; tmp != NULL; tmp = tmp->next()) {
      if (tmp->next() != NULL) {
        assert(tmp->rank() == Mutex::native || tmp->rank() < tmp->next()->rank()
               || tmp->skip_rank_check(), "mutex rank anomaly?");
      }
    }
  }

  // Locks with rank native or suspend_resume are an exception and are not
  // subject to the verification rules.
  bool check_can_be_skipped = this->rank() == Mutex::native || this->rank() == Mutex::suspend_resume
                              || SafepointSynchronize::is_at_safepoint();
  if (owned_by_self()) {
    // wait() case
    Mutex* least = get_least_ranked_lock_besides_this(locks_owned);
    // We enforce not holding locks of rank special or lower while waiting.
    // Also "this" should be the monitor with lowest rank owned by this thread.
    if (least != NULL && (least->rank() <= special ||
        (least->rank() <= this->rank() && !check_can_be_skipped))) {
      assert(false, "Attempting to wait on monitor %s/%d while holding lock %s/%d -- "
             "possible deadlock. %s", name(), rank(), least->name(), least->rank(),
             least->rank() <= this->rank() ? "Should wait on the least ranked monitor from "
             "all owned locks." : "Should not block(wait) while holding a lock of rank special.");
    }
  } else if (!check_can_be_skipped) {
    // lock()/lock_without_safepoint_check()/try_lock() case
    Mutex* least = get_least_ranked_lock(locks_owned);
    // Deadlock prevention rules require us to acquire Mutexes only in
    // a global total order. For example, if m1 is the lowest ranked mutex
    // that the thread holds and m2 is the mutex the thread is trying
    // to acquire, then deadlock prevention rules require that the rank
    // of m2 be less than the rank of m1. This prevents circular waits.
    if (least != NULL && least->rank() <= this->rank()) {
      thread->print_owned_locks();
      assert(false, "Attempting to acquire lock %s/%d out of order with lock %s/%d -- "
             "possible deadlock", this->name(), this->rank(), least->name(), least->rank());
    }
  }
}

bool Mutex::contains(Mutex* locks, Mutex* lock) {
  for (; locks != NULL; locks = locks->next()) {
    if (locks == lock) {
      return true;
    }
  }
  return false;
}

// NSV implied with locking allow_vm_block or !safepoint_check locks.
void Mutex::no_safepoint_verifier(Thread* thread, bool enable) {
  // The tty_lock is special because it is released for the safepoint by
  // the safepoint mechanism.
  if (this == tty_lock) {
    return;
  }

  if (_allow_vm_block) {
    if (enable) {
      thread->_no_safepoint_count++;
    } else {
      thread->_no_safepoint_count--;
    }
  }
}

// Called immediately after lock acquisition or release as a diagnostic
// to track the lock-set of the thread.
// Rather like an EventListener for _owner (:>).

void Mutex::set_owner_implementation(Thread *new_owner) {
  // This function is solely responsible for maintaining
  // and checking the invariant that threads and locks
  // are in a 1/N relation, with some some locks unowned.
  // It uses the Mutex::_owner, Mutex::_next, and
  // Thread::_owned_locks fields, and no other function
  // changes those fields.
  // It is illegal to set the mutex from one non-NULL
  // owner to another--it must be owned by NULL as an
  // intermediate state.

  if (new_owner != NULL) {
    // the thread is acquiring this lock

    assert(new_owner == Thread::current(), "Should I be doing this?");
    assert(owner() == NULL, "setting the owner thread of an already owned mutex");
    raw_set_owner(new_owner); // set the owner

    // link "this" into the owned locks list
    this->_next = new_owner->_owned_locks;
    new_owner->_owned_locks = this;

    // NSV implied with locking allow_vm_block flag.
    no_safepoint_verifier(new_owner, true);

  } else {
    // the thread is releasing this lock

    Thread* old_owner = owner();
    _last_owner = old_owner;
    _skip_rank_check = false;

    assert(old_owner != NULL, "removing the owner thread of an unowned mutex");
    assert(old_owner == Thread::current(), "removing the owner thread of an unowned mutex");

    raw_set_owner(NULL); // set the owner

    Mutex* locks = old_owner->owned_locks();

    // remove "this" from the owned locks list

    Mutex* prev = NULL;
    bool found = false;
    for (; locks != NULL; prev = locks, locks = locks->next()) {
      if (locks == this) {
        found = true;
        break;
      }
    }
    assert(found, "Removing a lock not owned");
    if (prev == NULL) {
      old_owner->_owned_locks = _next;
    } else {
      prev->_next = _next;
    }
    _next = NULL;

    // ~NSV implied with locking allow_vm_block flag.
    no_safepoint_verifier(old_owner, false);
  }
}
#endif // ASSERT
