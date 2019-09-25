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
           "This lock should %s have a safepoint check for Java threads: %s",
           _safepoint_check_required ? "always" : "never", name());

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
         "This lock should %s have a safepoint check for Java threads: %s",
         _safepoint_check_required ? "always" : "never", name());
}
#endif // ASSERT

void Mutex::lock(Thread* self) {
  check_safepoint_state(self);

  assert(_owner != self, "invariant");

  Mutex* in_flight_mutex = NULL;
  DEBUG_ONLY(int retry_cnt = 0;)
  bool is_active_Java_thread = self->is_active_Java_thread();
  while (!_lock.try_lock()) {
    // The lock is contended

  #ifdef ASSERT
    if (retry_cnt++ > 3) {
      log_trace(vmmutex)("JavaThread " INTPTR_FORMAT " on %d attempt trying to acquire vmmutex %s", p2i(self), retry_cnt, _name);
    }
  #endif // ASSERT

    // Is it a JavaThread participating in the safepoint protocol.
    if (is_active_Java_thread) {
      assert(rank() > Mutex::special, "Potential deadlock with special or lesser rank mutex");
      { ThreadBlockInVMWithDeadlockCheck tbivmdc((JavaThread *) self, &in_flight_mutex);
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
  }

  assert_owner(NULL);
  set_owner(self);
}

void Mutex::lock() {
  this->lock(Thread::current());
}

// Lock without safepoint check - a degenerate variant of lock() for use by
// JavaThreads when it is known to be safe to not check for a safepoint when
// acquiring this lock. If the thread blocks acquiring the lock it is not
// safepoint-safe and so will prevent a safepoint from being reached. If used
// in the wrong way this can lead to a deadlock with the safepoint code.

void Mutex::lock_without_safepoint_check(Thread * self) {
  check_no_safepoint_state(self);
  assert(_owner != self, "invariant");
  _lock.lock();
  assert_owner(NULL);
  set_owner(self);
}

void Mutex::lock_without_safepoint_check() {
  lock_without_safepoint_check(Thread::current());
}


// Returns true if thread succeeds in grabbing the lock, otherwise false.

bool Mutex::try_lock() {
  Thread * const self = Thread::current();
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

void Mutex::release_for_safepoint() {
  assert_owner(NULL);
  _lock.unlock();
}

void Mutex::unlock() {
  assert_owner(Thread::current());
  set_owner(NULL);
  _lock.unlock();
}

void Monitor::notify() {
  assert_owner(Thread::current());
  _lock.notify();
}

void Monitor::notify_all() {
  assert_owner(Thread::current());
  _lock.notify_all();
}

#ifdef ASSERT
void Monitor::assert_wait_lock_state(Thread* self) {
  Mutex* least = get_least_ranked_lock_besides_this(self->owned_locks());
  assert(least != this, "Specification of get_least_... call above");
  if (least != NULL && least->rank() <= special) {
    ::tty->print("Attempting to wait on monitor %s/%d while holding"
               " lock %s/%d -- possible deadlock",
               name(), rank(), least->name(), least->rank());
    assert(false, "Shouldn't block(wait) while holding a lock of rank special");
  }
}
#endif // ASSERT

bool Monitor::wait_without_safepoint_check(long timeout) {
  Thread* const self = Thread::current();

  // timeout is in milliseconds - with zero meaning never timeout
  assert(timeout >= 0, "negative timeout");

  assert_owner(self);
  assert_wait_lock_state(self);

  // conceptually set the owner to NULL in anticipation of
  // abdicating the lock in wait
  set_owner(NULL);
  // Check safepoint state after resetting owner and possible NSV.
  check_no_safepoint_state(self);

  int wait_status = _lock.wait(timeout);
  set_owner(self);
  return wait_status != 0;          // return true IFF timeout
}

bool Monitor::wait(long timeout, bool as_suspend_equivalent) {
  Thread* const self = Thread::current();

  // timeout is in milliseconds - with zero meaning never timeout
  assert(timeout >= 0, "negative timeout");

  assert_owner(self);

  // Safepoint checking logically implies an active JavaThread.
  guarantee(self->is_active_Java_thread(), "invariant");
  assert_wait_lock_state(self);

  int wait_status;
  // conceptually set the owner to NULL in anticipation of
  // abdicating the lock in wait
  set_owner(NULL);
  // Check safepoint state after resetting owner and possible NSV.
  check_safepoint_state(self);
  JavaThread *jt = (JavaThread *)self;
  Mutex* in_flight_mutex = NULL;

  {
    ThreadBlockInVMWithDeadlockCheck tbivmdc(jt, &in_flight_mutex);
    OSThreadWaitState osts(self->osthread(), false /* not Object.wait() */);
    if (as_suspend_equivalent) {
      jt->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or
      // java_suspend_self()
    }

    wait_status = _lock.wait(timeout);
    in_flight_mutex = this;  // save for ~ThreadBlockInVMWithDeadlockCheck

    // were we externally suspended while we were waiting?
    if (as_suspend_equivalent && jt->handle_special_suspend_equivalent_condition()) {
      // Our event wait has finished and we own the lock, but
      // while we were waiting another thread suspended us. We don't
      // want to hold the lock while suspended because that
      // would surprise the thread that suspended us.
      _lock.unlock();
      jt->java_suspend_self();
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

  assert(_safepoint_check_required != _safepoint_check_sometimes || is_sometimes_ok(name),
         "Lock has _safepoint_check_sometimes %s", name);
#endif
}

Monitor::Monitor(int Rank, const char * name, bool allow_vm_block,
             SafepointCheckRequired safepoint_check_required) :
  Mutex(Rank, name, allow_vm_block, safepoint_check_required) {}

bool Mutex::owned_by_self() const {
  return _owner == Thread::current();
}

void Mutex::print_on_error(outputStream* st) const {
  st->print("[" PTR_FORMAT, p2i(this));
  st->print("] %s", _name);
  st->print(" - owner thread: " PTR_FORMAT, p2i(_owner));
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
            p2i(this), _name, p2i(_owner));
  if (_allow_vm_block) {
    st->print("%s", " allow_vm_block");
  }
  st->print(" %s", print_safepoint_check(_safepoint_check_required));
  st->cr();
}
#endif

#ifdef ASSERT
void Mutex::assert_owner(Thread * expected) {
  const char* msg = "invalid owner";
  if (expected == NULL) {
    msg = "should be un-owned";
  }
  else if (expected == Thread::current()) {
    msg = "should be owned by current thread";
  }
  assert(_owner == expected,
         "%s: owner=" INTPTR_FORMAT ", should be=" INTPTR_FORMAT,
         msg, p2i(_owner), p2i(expected));
}

Mutex* Mutex::get_least_ranked_lock(Mutex* locks) {
  Mutex *res, *tmp;
  for (res = tmp = locks; tmp != NULL; tmp = tmp->next()) {
    if (tmp->rank() < res->rank()) {
      res = tmp;
    }
  }
  if (!SafepointSynchronize::is_at_safepoint()) {
    // In this case, we expect the held locks to be
    // in increasing rank order (modulo any native ranks)
    for (tmp = locks; tmp != NULL; tmp = tmp->next()) {
      if (tmp->next() != NULL) {
        assert(tmp->rank() == Mutex::native ||
               tmp->rank() <= tmp->next()->rank(), "mutex rank anomaly?");
      }
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
  if (!SafepointSynchronize::is_at_safepoint()) {
    // In this case, we expect the held locks to be
    // in increasing rank order (modulo any native ranks)
    for (tmp = locks; tmp != NULL; tmp = tmp->next()) {
      if (tmp->next() != NULL) {
        assert(tmp->rank() == Mutex::native ||
               tmp->rank() <= tmp->next()->rank(), "mutex rank anomaly?");
      }
    }
  }
  return res;
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
  // Threads_lock is special, since the safepoint synchronization will not start before this is
  // acquired. Hence, a JavaThread cannot be holding it at a safepoint. So is VMOperationRequest_lock,
  // since it is used to transfer control between JavaThreads and the VMThread
  // Do not *exclude* any locks unless you are absolutely sure it is correct. Ask someone else first!
  if ((_allow_vm_block &&
       this != Threads_lock &&
       this != Compile_lock &&           // Temporary: should not be necessary when we get separate compilation
       this != tty_lock &&               // The tty_lock is released for the safepoint.
       this != VMOperationRequest_lock &&
       this != VMOperationQueue_lock) ||
       rank() == Mutex::special) {
    if (enable) {
      thread->_no_safepoint_count++;
    } else {
      thread->_no_safepoint_count--;
    }
  }
}

// Called immediately after lock acquisition or release as a diagnostic
// to track the lock-set of the thread and test for rank violations that
// might indicate exposure to deadlock.
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
    assert(_owner == NULL, "setting the owner thread of an already owned mutex");
    _owner = new_owner; // set the owner

    // link "this" into the owned locks list

    Mutex* locks = get_least_ranked_lock(new_owner->owned_locks());
    // Mutex::set_owner_implementation is a friend of Thread

    assert(this->rank() >= 0, "bad lock rank");

    // Deadlock avoidance rules require us to acquire Mutexes only in
    // a global total order. For example m1 is the lowest ranked mutex
    // that the thread holds and m2 is the mutex the thread is trying
    // to acquire, then deadlock avoidance rules require that the rank
    // of m2 be less than the rank of m1.
    // The rank Mutex::native  is an exception in that it is not subject
    // to the verification rules.
    if (this->rank() != Mutex::native &&
        this->rank() != Mutex::suspend_resume &&
        locks != NULL && locks->rank() <= this->rank() &&
        !SafepointSynchronize::is_at_safepoint()) {
      new_owner->print_owned_locks();
      fatal("acquiring lock %s/%d out of order with lock %s/%d -- "
            "possible deadlock", this->name(), this->rank(),
            locks->name(), locks->rank());
    }

    this->_next = new_owner->_owned_locks;
    new_owner->_owned_locks = this;

    // NSV implied with locking allow_vm_block flag.
    no_safepoint_verifier(new_owner, true);

  } else {
    // the thread is releasing this lock

    Thread* old_owner = _owner;
    _last_owner = old_owner;

    assert(old_owner != NULL, "removing the owner thread of an unowned mutex");
    assert(old_owner == Thread::current(), "removing the owner thread of an unowned mutex");

    _owner = NULL; // set the owner

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
