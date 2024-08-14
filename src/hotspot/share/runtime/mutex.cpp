/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"

class InFlightMutexRelease {
 private:
  Mutex* _in_flight_mutex;
 public:
  InFlightMutexRelease(Mutex* in_flight_mutex) : _in_flight_mutex(in_flight_mutex) {
    assert(in_flight_mutex != nullptr, "must be");
  }
  void operator()(JavaThread* current) {
    _in_flight_mutex->release_for_safepoint();
    _in_flight_mutex = nullptr;
  }
  bool not_released() { return _in_flight_mutex != nullptr; }
};

#ifdef ASSERT
void Mutex::check_block_state(Thread* thread) {
  if (!_allow_vm_block && thread->is_VM_thread()) {
    // JavaThreads are checked to make sure that they do not hold _allow_vm_block locks during operations
    // that could safepoint.  Make sure the vm thread never uses locks with _allow_vm_block == false.
    fatal("VM thread could block on lock that may be held by a JavaThread during safepoint: %s", name());
  }

  assert(!ThreadCrashProtection::is_crash_protected(thread),
         "locking not allowed when crash protection is set");
}

void Mutex::check_safepoint_state(Thread* thread) {
  check_block_state(thread);

  // If the lock acquisition checks for safepoint, verify that the lock was created with rank that
  // has safepoint checks. Technically this doesn't affect NonJavaThreads since they won't actually
  // check for safepoint, but let's make the rule unconditional unless there's a good reason not to.
  assert(_rank > nosafepoint,
         "This lock should not be taken with a safepoint check: %s", name());

  if (thread->is_active_Java_thread()) {
    // Also check NoSafepointVerifier, and thread state is _thread_in_vm
    JavaThread::cast(thread)->check_for_valid_safepoint_state();
  }
}

void Mutex::check_no_safepoint_state(Thread* thread) {
  check_block_state(thread);
  assert(!thread->is_active_Java_thread() || _rank <= nosafepoint,
         "This lock should always have a safepoint check for Java threads: %s",
         name());
}
#endif // ASSERT

void Mutex::lock_contended(Thread* self) {
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
      InFlightMutexRelease ifmr(this);
      assert(rank() > Mutex::nosafepoint, "Potential deadlock with nosafepoint or lesser rank mutex");
      {
        ThreadBlockInVMPreprocess<InFlightMutexRelease> tbivmdc(JavaThread::cast(self), ifmr);
        _lock.lock();
      }
      if (ifmr.not_released()) {
        // Not unlocked by ~ThreadBlockInVMPreprocess
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

  assert_owner(nullptr);
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
  assert_owner(nullptr);
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
  // Some safepoint checking locks use try_lock, so cannot check
  // safepoint state, but can check blocking state.
  check_block_state(self);

  if (_lock.try_lock()) {
    assert_owner(nullptr);
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
  assert_owner(nullptr);
  _lock.unlock();
}

void Mutex::unlock() {
  DEBUG_ONLY(assert_owner(Thread::current()));
  set_owner(nullptr);
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

// timeout is in milliseconds - with zero meaning never timeout
bool Monitor::wait_without_safepoint_check(uint64_t timeout) {
  Thread* const self = Thread::current();

  assert_owner(self);
  check_rank(self);

  // conceptually set the owner to null in anticipation of
  // abdicating the lock in wait
  set_owner(nullptr);

  // Check safepoint state after resetting owner and possible NSV.
  check_no_safepoint_state(self);

  int wait_status = _lock.wait(timeout);
  set_owner(self);
  return wait_status != 0;          // return true IFF timeout
}

// timeout is in milliseconds - with zero meaning never timeout
bool Monitor::wait(uint64_t timeout) {
  JavaThread* const self = JavaThread::current();
  // Safepoint checking logically implies an active JavaThread.
  assert(self->is_active_Java_thread(), "invariant");

  assert_owner(self);
  check_rank(self);

  // conceptually set the owner to null in anticipation of
  // abdicating the lock in wait
  set_owner(nullptr);

  // Check safepoint state after resetting owner and possible NSV.
  check_safepoint_state(self);

  int wait_status;
  InFlightMutexRelease ifmr(this);

  {
    ThreadBlockInVMPreprocess<InFlightMutexRelease> tbivmdc(self, ifmr);
    OSThreadWaitState osts(self->osthread(), false /* not Object.wait() */);

    wait_status = _lock.wait(timeout);
  }

  if (ifmr.not_released()) {
    // Not unlocked by ~ThreadBlockInVMPreprocess
    assert_owner(nullptr);
    // Conceptually reestablish ownership of the lock.
    set_owner(self);
  } else {
    lock(self);
  }

  return wait_status != 0;          // return true IFF timeout
}

Mutex::~Mutex() {
  assert_owner(nullptr);
  os::free(const_cast<char*>(_name));
}

Mutex::Mutex(Rank rank, const char * name, bool allow_vm_block) : _owner(nullptr) {
  assert(os::mutex_init_done(), "Too early!");
  assert(name != nullptr, "Mutex requires a name");
  _name = os::strdup(name, mtInternal);
#ifdef ASSERT
  _allow_vm_block  = allow_vm_block;
  _rank            = rank;
  _skip_rank_check = false;

  assert(_rank >= static_cast<Rank>(0) && _rank <= safepoint, "Bad lock rank %s: %s", rank_name(), name);

  // The allow_vm_block also includes allowing other non-Java threads to block or
  // allowing Java threads to block in native.
  assert(_rank > nosafepoint || _allow_vm_block,
         "Locks that don't check for safepoint should always allow the vm to block: %s", name);
#endif
}

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
//
#ifdef ASSERT
static Mutex::Rank _ranks[] = { Mutex::event, Mutex::service, Mutex::stackwatermark, Mutex::tty, Mutex::oopstorage,
                                Mutex::nosafepoint, Mutex::safepoint };

static const char* _rank_names[] = { "event", "service", "stackwatermark", "tty", "oopstorage",
                                     "nosafepoint", "safepoint" };

static const int _num_ranks = 7;

static const char* rank_name_internal(Mutex::Rank r) {
  // Find closest rank and print out the name
  stringStream st;
  for (int i = 0; i < _num_ranks; i++) {
    if (r == _ranks[i]) {
      return _rank_names[i];
    } else if (r  > _ranks[i] && (i < _num_ranks-1 && r < _ranks[i+1])) {
      int delta = static_cast<int>(_ranks[i+1]) - static_cast<int>(r);
      st.print("%s-%d", _rank_names[i+1], delta);
      return st.as_string();
    }
  }
  return "fail";
}

const char* Mutex::rank_name() const {
  return rank_name_internal(_rank);
}


void Mutex::assert_no_overlap(Rank orig, Rank adjusted, int adjust) {
  int i = 0;
  while (_ranks[i] < orig) i++;
  // underflow is caught in constructor
  if (i != 0 && adjusted > event && adjusted <= _ranks[i-1]) {
    ResourceMark rm;
    assert(adjusted > _ranks[i-1],
           "Rank %s-%d overlaps with %s",
           rank_name_internal(orig), adjust, rank_name_internal(adjusted));
  }
}
#endif // ASSERT

#ifndef PRODUCT
void Mutex::print_on(outputStream* st) const {
  st->print("Mutex: [" PTR_FORMAT "] %s - owner: " PTR_FORMAT,
            p2i(this), _name, p2i(owner()));
  if (_allow_vm_block) {
    st->print("%s", " allow_vm_block");
  }
  DEBUG_ONLY(st->print(" %s", rank_name()));
  st->cr();
}

void Mutex::print() const {
  print_on(::tty);
}
#endif // PRODUCT

#ifdef ASSERT
void Mutex::assert_owner(Thread * expected) {
  const char* msg = "invalid owner";
  if (expected == nullptr) {
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
  for (res = tmp = locks; tmp != nullptr; tmp = tmp->next()) {
    if (tmp->rank() < res->rank()) {
      res = tmp;
    }
  }
  return res;
}

Mutex* Mutex::get_least_ranked_lock_besides_this(Mutex* locks) {
  Mutex *res, *tmp;
  for (res = nullptr, tmp = locks; tmp != nullptr; tmp = tmp->next()) {
    if (tmp != this && (res == nullptr || tmp->rank() < res->rank())) {
      res = tmp;
    }
  }
  assert(res != this, "invariant");
  return res;
}

// Tests for rank violations that might indicate exposure to deadlock.
void Mutex::check_rank(Thread* thread) {
  Mutex* locks_owned = thread->owned_locks();

  // We expect the locks already acquired to be in increasing rank order,
  // modulo locks acquired in try_lock_without_rank_check()
  for (Mutex* tmp = locks_owned; tmp != nullptr; tmp = tmp->next()) {
    if (tmp->next() != nullptr) {
      assert(tmp->rank() < tmp->next()->rank()
             || tmp->skip_rank_check(), "mutex rank anomaly?");
    }
  }

  if (owned_by_self()) {
    // wait() case
    Mutex* least = get_least_ranked_lock_besides_this(locks_owned);
    // For JavaThreads, we enforce not holding locks of rank nosafepoint or lower while waiting
    // because the held lock has a NoSafepointVerifier so waiting on a lower ranked lock will not be
    // able to check for safepoints first with a TBIVM.
    // For all threads, we enforce not holding the tty lock or below, since this could block progress also.
    // Also "this" should be the monitor with lowest rank owned by this thread.
    if (least != nullptr && ((least->rank() <= Mutex::nosafepoint && thread->is_Java_thread()) ||
                           least->rank() <= Mutex::tty ||
                           least->rank() <= this->rank())) {
      ResourceMark rm(thread);
      assert(false, "Attempting to wait on monitor %s/%s while holding lock %s/%s -- "
             "possible deadlock. %s", name(), rank_name(), least->name(), least->rank_name(),
             least->rank() <= this->rank() ?
              "Should wait on the least ranked monitor from all owned locks." :
             thread->is_Java_thread() ?
              "Should not block(wait) while holding a lock of rank nosafepoint or below." :
              "Should not block(wait) while holding a lock of rank tty or below.");
    }
  } else {
    // lock()/lock_without_safepoint_check()/try_lock() case
    Mutex* least = get_least_ranked_lock(locks_owned);
    // Deadlock prevention rules require us to acquire Mutexes only in
    // a global total order. For example, if m1 is the lowest ranked mutex
    // that the thread holds and m2 is the mutex the thread is trying
    // to acquire, then deadlock prevention rules require that the rank
    // of m2 be less than the rank of m1. This prevents circular waits.
    if (least != nullptr && least->rank() <= this->rank()) {
      ResourceMark rm(thread);
      if (least->rank() > Mutex::tty) {
        // Printing owned locks acquires tty lock. If the least rank was below or equal
        // tty, then deadlock detection code would circle back here, until we run
        // out of stack and crash hard. Print locks only when it is safe.
        thread->print_owned_locks();
      }
      assert(false, "Attempting to acquire lock %s/%s out of order with lock %s/%s -- "
             "possible deadlock", this->name(), this->rank_name(), least->name(), least->rank_name());
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
  // It is illegal to set the mutex from one non-null
  // owner to another--it must be owned by null as an
  // intermediate state.

  if (new_owner != nullptr) {
    // the thread is acquiring this lock

    assert(new_owner == Thread::current(), "Should I be doing this?");
    assert(owner() == nullptr, "setting the owner thread of an already owned mutex");
    raw_set_owner(new_owner); // set the owner

    // link "this" into the owned locks list
    this->_next = new_owner->_owned_locks;
    new_owner->_owned_locks = this;

    // NSV implied with locking allow_vm_block flag.
    // The tty_lock is special because it is released for the safepoint by
    // the safepoint mechanism.
    if (new_owner->is_Java_thread() && _allow_vm_block && this != tty_lock) {
      JavaThread::cast(new_owner)->inc_no_safepoint_count();
    }

  } else {
    // the thread is releasing this lock

    Thread* old_owner = owner();
    _last_owner = old_owner;
    _skip_rank_check = false;

    assert(old_owner != nullptr, "removing the owner thread of an unowned mutex");
    assert(old_owner == Thread::current(), "removing the owner thread of an unowned mutex");

    raw_set_owner(nullptr); // set the owner

    Mutex* locks = old_owner->owned_locks();

    // remove "this" from the owned locks list

    Mutex* prev = nullptr;
    bool found = false;
    for (; locks != nullptr; prev = locks, locks = locks->next()) {
      if (locks == this) {
        found = true;
        break;
      }
    }
    assert(found, "Removing a lock not owned");
    if (prev == nullptr) {
      old_owner->_owned_locks = _next;
    } else {
      prev->_next = _next;
    }
    _next = nullptr;

    // ~NSV implied with locking allow_vm_block flag.
    if (old_owner->is_Java_thread() && _allow_vm_block && this != tty_lock) {
      JavaThread::cast(old_owner)->dec_no_safepoint_count();
    }
  }
}
#endif // ASSERT


RecursiveMutex::RecursiveMutex() : _sem(1), _owner(nullptr), _recursions(0) {}

void RecursiveMutex::lock(Thread* current) {
  assert(current == Thread::current(), "must be current thread");
  if (current == _owner) {
    _recursions++;
  } else {
    // can be called by jvmti by VMThread.
    if (current->is_Java_thread()) {
      _sem.wait_with_safepoint_check(JavaThread::cast(current));
    } else {
      _sem.wait();
    }
    _recursions++;
    assert(_recursions == 1, "should be");
    _owner = current;
  }
}

void RecursiveMutex::unlock(Thread* current) {
  assert(current == Thread::current(), "must be current thread");
  assert(current == _owner, "must be owner");
  _recursions--;
  if (_recursions == 0) {
    _owner = nullptr;
    _sem.signal();
  }
}
