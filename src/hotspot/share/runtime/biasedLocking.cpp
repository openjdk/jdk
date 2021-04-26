/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/task.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"


static bool _biased_locking_enabled = false;
BiasedLockingCounters BiasedLocking::_counters;

static GrowableArray<Handle>*   _preserved_oop_stack  = NULL;
static GrowableArray<markWord>* _preserved_mark_stack = NULL;

static void enable_biased_locking(InstanceKlass* k) {
  k->set_prototype_header(markWord::biased_locking_prototype());
}

static void enable_biased_locking() {
  _biased_locking_enabled = true;
  log_info(biasedlocking)("Biased locking enabled");
}

class VM_EnableBiasedLocking: public VM_Operation {
 public:
  VM_EnableBiasedLocking() {}
  VMOp_Type type() const          { return VMOp_EnableBiasedLocking; }

  void doit() {
    // Iterate the class loader data dictionaries enabling biased locking for all
    // currently loaded classes.
    ClassLoaderDataGraph::dictionary_classes_do(enable_biased_locking);
    // Indicate that future instances should enable it as well
    enable_biased_locking();
  }

  bool allow_nested_vm_operations() const        { return false; }
};


// One-shot PeriodicTask subclass for enabling biased locking
class EnableBiasedLockingTask : public PeriodicTask {
 public:
  EnableBiasedLockingTask(size_t interval_time) : PeriodicTask(interval_time) {}

  virtual void task() {
    VM_EnableBiasedLocking op;
    VMThread::execute(&op);

    // Reclaim our storage and disenroll ourself
    delete this;
  }
};


void BiasedLocking::init() {
  // If biased locking is enabled and BiasedLockingStartupDelay is set,
  // schedule a task to fire after the specified delay which turns on
  // biased locking for all currently loaded classes as well as future
  // ones. This could be a workaround for startup time regressions
  // due to large number of safepoints being taken during VM startup for
  // bias revocation.
  if (UseBiasedLocking) {
    if (BiasedLockingStartupDelay > 0) {
      EnableBiasedLockingTask* task = new EnableBiasedLockingTask(BiasedLockingStartupDelay);
      task->enroll();
    } else {
      enable_biased_locking();
    }
  }
}


bool BiasedLocking::enabled() {
  assert(UseBiasedLocking, "precondition");
  // We check "BiasedLockingStartupDelay == 0" here to cover the
  // possibility of calls to BiasedLocking::enabled() before
  // BiasedLocking::init().
  return _biased_locking_enabled || BiasedLockingStartupDelay == 0;
}


// Returns MonitorInfos for all objects locked on this thread in youngest to oldest order
static GrowableArray<MonitorInfo*>* get_or_compute_monitor_info(JavaThread* thread) {
  GrowableArray<MonitorInfo*>* info = thread->cached_monitor_info();
  if (info != NULL) {
    return info;
  }

  info = new GrowableArray<MonitorInfo*>();

  // It's possible for the thread to not have any Java frames on it,
  // i.e., if it's the main thread and it's already returned from main()
  if (thread->has_last_Java_frame()) {
    RegisterMap rm(thread);
    for (javaVFrame* vf = thread->last_java_vframe(&rm); vf != NULL; vf = vf->java_sender()) {
      GrowableArray<MonitorInfo*> *monitors = vf->monitors();
      if (monitors != NULL) {
        int len = monitors->length();
        // Walk monitors youngest to oldest
        for (int i = len - 1; i >= 0; i--) {
          MonitorInfo* mon_info = monitors->at(i);
          if (mon_info->eliminated()) continue;
          oop owner = mon_info->owner();
          if (owner != NULL) {
            info->append(mon_info);
          }
        }
      }
    }
  }

  thread->set_cached_monitor_info(info);
  return info;
}


// After the call, *biased_locker will be set to obj->mark()->biased_locker() if biased_locker != NULL,
// AND it is a living thread. Otherwise it will not be updated, (i.e. the caller is responsible for initialization).
void BiasedLocking::single_revoke_at_safepoint(oop obj, bool is_bulk, JavaThread* requesting_thread, JavaThread** biased_locker) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be done at safepoint");
  assert(Thread::current()->is_VM_thread(), "must be VMThread");

  markWord mark = obj->mark();
  if (!mark.has_bias_pattern()) {
    if (log_is_enabled(Info, biasedlocking)) {
      ResourceMark rm;
      log_info(biasedlocking)("  (Skipping revocation of object " INTPTR_FORMAT
                              ", mark " INTPTR_FORMAT ", type %s"
                              ", requesting thread " INTPTR_FORMAT
                              " because it's no longer biased)",
                              p2i((void *)obj), mark.value(),
                              obj->klass()->external_name(),
                              (intptr_t) requesting_thread);
    }
    return;
  }

  uint age = mark.age();
  markWord unbiased_prototype = markWord::prototype().set_age(age);

  // Log at "info" level if not bulk, else "trace" level
  if (!is_bulk) {
    ResourceMark rm;
    log_info(biasedlocking)("Revoking bias of object " INTPTR_FORMAT ", mark "
                            INTPTR_FORMAT ", type %s, prototype header " INTPTR_FORMAT
                            ", requesting thread " INTPTR_FORMAT,
                            p2i((void *)obj),
                            mark.value(),
                            obj->klass()->external_name(),
                            obj->klass()->prototype_header().value(),
                            (intptr_t) requesting_thread);
  } else {
    ResourceMark rm;
    log_trace(biasedlocking)("Revoking bias of object " INTPTR_FORMAT " , mark "
                             INTPTR_FORMAT " , type %s , prototype header " INTPTR_FORMAT
                             " , requesting thread " INTPTR_FORMAT,
                             p2i((void *)obj),
                             mark.value(),
                             obj->klass()->external_name(),
                             obj->klass()->prototype_header().value(),
                             (intptr_t) requesting_thread);
  }

  JavaThread* biased_thread = mark.biased_locker();
  if (biased_thread == NULL) {
    // Object is anonymously biased. We can get here if, for
    // example, we revoke the bias due to an identity hash code
    // being computed for an object.
    obj->set_mark(unbiased_prototype);

    // Log at "info" level if not bulk, else "trace" level
    if (!is_bulk) {
      log_info(biasedlocking)("  Revoked bias of anonymously-biased object");
    } else {
      log_trace(biasedlocking)("  Revoked bias of anonymously-biased object");
    }
    return;
  }

  // Handle case where the thread toward which the object was biased has exited
  bool thread_is_alive = false;
  if (requesting_thread == biased_thread) {
    thread_is_alive = true;
  } else {
    ThreadsListHandle tlh;
    thread_is_alive = tlh.includes(biased_thread);
  }
  if (!thread_is_alive) {
    obj->set_mark(unbiased_prototype);
    // Log at "info" level if not bulk, else "trace" level
    if (!is_bulk) {
      log_info(biasedlocking)("  Revoked bias of object biased toward dead thread ("
                              PTR_FORMAT ")", p2i(biased_thread));
    } else {
      log_trace(biasedlocking)("  Revoked bias of object biased toward dead thread ("
                               PTR_FORMAT ")", p2i(biased_thread));
    }
    return;
  }

  // Log at "info" level if not bulk, else "trace" level
  if (!is_bulk) {
    log_info(biasedlocking)("  Revoked bias of object biased toward live thread ("
                            PTR_FORMAT ")", p2i(biased_thread));
  } else {
    log_trace(biasedlocking)("  Revoked bias of object biased toward live thread ("
                               PTR_FORMAT ")", p2i(biased_thread));
  }

  // Thread owning bias is alive.
  // Check to see whether it currently owns the lock and, if so,
  // write down the needed displaced headers to the thread's stack.
  // Otherwise, restore the object's header either to the unlocked
  // or unbiased state.
  GrowableArray<MonitorInfo*>* cached_monitor_info = get_or_compute_monitor_info(biased_thread);
  BasicLock* highest_lock = NULL;
  for (int i = 0; i < cached_monitor_info->length(); i++) {
    MonitorInfo* mon_info = cached_monitor_info->at(i);
    if (mon_info->owner() == obj) {
      log_trace(biasedlocking)("   mon_info->owner (" PTR_FORMAT ") == obj (" PTR_FORMAT ")",
                               p2i((void *) mon_info->owner()),
                               p2i((void *) obj));
      // Assume recursive case and fix up highest lock below
      markWord mark = markWord::encode((BasicLock*) NULL);
      highest_lock = mon_info->lock();
      highest_lock->set_displaced_header(mark);
    } else {
      log_trace(biasedlocking)("   mon_info->owner (" PTR_FORMAT ") != obj (" PTR_FORMAT ")",
                               p2i((void *) mon_info->owner()),
                               p2i((void *) obj));
    }
  }
  if (highest_lock != NULL) {
    // Fix up highest lock to contain displaced header and point
    // object at it
    highest_lock->set_displaced_header(unbiased_prototype);
    // Reset object header to point to displaced mark.
    // Must release store the lock address for platforms without TSO
    // ordering (e.g. ppc).
    obj->release_set_mark(markWord::encode(highest_lock));
    assert(!obj->mark().has_bias_pattern(), "illegal mark state: stack lock used bias bit");
    // Log at "info" level if not bulk, else "trace" level
    if (!is_bulk) {
      log_info(biasedlocking)("  Revoked bias of currently-locked object");
    } else {
      log_trace(biasedlocking)("  Revoked bias of currently-locked object");
    }
  } else {
    // Log at "info" level if not bulk, else "trace" level
    if (!is_bulk) {
      log_info(biasedlocking)("  Revoked bias of currently-unlocked object");
    } else {
      log_trace(biasedlocking)("  Revoked bias of currently-unlocked object");
    }
    // Store the unlocked value into the object's header.
    obj->set_mark(unbiased_prototype);
  }

  // If requested, return information on which thread held the bias
  if (biased_locker != NULL) {
    *biased_locker = biased_thread;
  }
}


enum HeuristicsResult {
  HR_NOT_BIASED    = 1,
  HR_SINGLE_REVOKE = 2,
  HR_BULK_REBIAS   = 3,
  HR_BULK_REVOKE   = 4
};


static HeuristicsResult update_heuristics(oop o) {
  markWord mark = o->mark();
  if (!mark.has_bias_pattern()) {
    return HR_NOT_BIASED;
  }

  // Heuristics to attempt to throttle the number of revocations.
  // Stages:
  // 1. Revoke the biases of all objects in the heap of this type,
  //    but allow rebiasing of those objects if unlocked.
  // 2. Revoke the biases of all objects in the heap of this type
  //    and don't allow rebiasing of these objects. Disable
  //    allocation of objects of that type with the bias bit set.
  Klass* k = o->klass();
  jlong cur_time = nanos_to_millis(os::javaTimeNanos());
  jlong last_bulk_revocation_time = k->last_biased_lock_bulk_revocation_time();
  int revocation_count = k->biased_lock_revocation_count();
  if ((revocation_count >= BiasedLockingBulkRebiasThreshold) &&
      (revocation_count <  BiasedLockingBulkRevokeThreshold) &&
      (last_bulk_revocation_time != 0) &&
      (cur_time - last_bulk_revocation_time >= BiasedLockingDecayTime)) {
    // This is the first revocation we've seen in a while of an
    // object of this type since the last time we performed a bulk
    // rebiasing operation. The application is allocating objects in
    // bulk which are biased toward a thread and then handing them
    // off to another thread. We can cope with this allocation
    // pattern via the bulk rebiasing mechanism so we reset the
    // klass's revocation count rather than allow it to increase
    // monotonically. If we see the need to perform another bulk
    // rebias operation later, we will, and if subsequently we see
    // many more revocation operations in a short period of time we
    // will completely disable biasing for this type.
    k->set_biased_lock_revocation_count(0);
    revocation_count = 0;
  }

  // Make revocation count saturate just beyond BiasedLockingBulkRevokeThreshold
  if (revocation_count <= BiasedLockingBulkRevokeThreshold) {
    revocation_count = k->atomic_incr_biased_lock_revocation_count();
  }

  if (revocation_count == BiasedLockingBulkRevokeThreshold) {
    return HR_BULK_REVOKE;
  }

  if (revocation_count == BiasedLockingBulkRebiasThreshold) {
    return HR_BULK_REBIAS;
  }

  return HR_SINGLE_REVOKE;
}


void BiasedLocking::bulk_revoke_at_safepoint(oop o, bool bulk_rebias, JavaThread* requesting_thread) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be done at safepoint");
  assert(Thread::current()->is_VM_thread(), "must be VMThread");

  log_info(biasedlocking)("* Beginning bulk revocation (kind == %s) because of object "
                          INTPTR_FORMAT " , mark " INTPTR_FORMAT " , type %s",
                          (bulk_rebias ? "rebias" : "revoke"),
                          p2i((void *) o),
                          o->mark().value(),
                          o->klass()->external_name());

  jlong cur_time = nanos_to_millis(os::javaTimeNanos());
  o->klass()->set_last_biased_lock_bulk_revocation_time(cur_time);

  Klass* k_o = o->klass();
  Klass* klass = k_o;

  {
    JavaThreadIteratorWithHandle jtiwh;

    if (bulk_rebias) {
      // Use the epoch in the klass of the object to implicitly revoke
      // all biases of objects of this data type and force them to be
      // reacquired. However, we also need to walk the stacks of all
      // threads and update the headers of lightweight locked objects
      // with biases to have the current epoch.

      // If the prototype header doesn't have the bias pattern, don't
      // try to update the epoch -- assume another VM operation came in
      // and reset the header to the unbiased state, which will
      // implicitly cause all existing biases to be revoked
      if (klass->prototype_header().has_bias_pattern()) {
        int prev_epoch = klass->prototype_header().bias_epoch();
        klass->set_prototype_header(klass->prototype_header().incr_bias_epoch());
        int cur_epoch = klass->prototype_header().bias_epoch();

        // Now walk all threads' stacks and adjust epochs of any biased
        // and locked objects of this data type we encounter
        for (; JavaThread *thr = jtiwh.next(); ) {
          GrowableArray<MonitorInfo*>* cached_monitor_info = get_or_compute_monitor_info(thr);
          for (int i = 0; i < cached_monitor_info->length(); i++) {
            MonitorInfo* mon_info = cached_monitor_info->at(i);
            oop owner = mon_info->owner();
            markWord mark = owner->mark();
            if ((owner->klass() == k_o) && mark.has_bias_pattern()) {
              // We might have encountered this object already in the case of recursive locking
              assert(mark.bias_epoch() == prev_epoch || mark.bias_epoch() == cur_epoch, "error in bias epoch adjustment");
              owner->set_mark(mark.set_bias_epoch(cur_epoch));
            }
          }
        }
      }

      // At this point we're done. All we have to do is potentially
      // adjust the header of the given object to revoke its bias.
      single_revoke_at_safepoint(o, true, requesting_thread, NULL);
    } else {
      if (log_is_enabled(Info, biasedlocking)) {
        ResourceMark rm;
        log_info(biasedlocking)("* Disabling biased locking for type %s", klass->external_name());
      }

      // Disable biased locking for this data type. Not only will this
      // cause future instances to not be biased, but existing biased
      // instances will notice that this implicitly caused their biases
      // to be revoked.
      klass->set_prototype_header(markWord::prototype());

      // Now walk all threads' stacks and forcibly revoke the biases of
      // any locked and biased objects of this data type we encounter.
      for (; JavaThread *thr = jtiwh.next(); ) {
        GrowableArray<MonitorInfo*>* cached_monitor_info = get_or_compute_monitor_info(thr);
        for (int i = 0; i < cached_monitor_info->length(); i++) {
          MonitorInfo* mon_info = cached_monitor_info->at(i);
          oop owner = mon_info->owner();
          markWord mark = owner->mark();
          if ((owner->klass() == k_o) && mark.has_bias_pattern()) {
            single_revoke_at_safepoint(owner, true, requesting_thread, NULL);
          }
        }
      }

      // Must force the bias of the passed object to be forcibly revoked
      // as well to ensure guarantees to callers
      single_revoke_at_safepoint(o, true, requesting_thread, NULL);
    }
  } // ThreadsListHandle is destroyed here.

  log_info(biasedlocking)("* Ending bulk revocation");

  assert(!o->mark().has_bias_pattern(), "bug in bulk bias revocation");
}


static void clean_up_cached_monitor_info(JavaThread* thread = NULL) {
  if (thread != NULL) {
    thread->set_cached_monitor_info(NULL);
  } else {
    // Walk the thread list clearing out the cached monitors
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thr = jtiwh.next(); ) {
      thr->set_cached_monitor_info(NULL);
    }
  }
}


class VM_BulkRevokeBias : public VM_Operation {
private:
  Handle* _obj;
  JavaThread* _requesting_thread;
  bool _bulk_rebias;
  uint64_t _safepoint_id;

public:
  VM_BulkRevokeBias(Handle* obj, JavaThread* requesting_thread,
                    bool bulk_rebias)
    : _obj(obj)
    , _requesting_thread(requesting_thread)
    , _bulk_rebias(bulk_rebias)
    , _safepoint_id(0) {}

  virtual VMOp_Type type() const { return VMOp_BulkRevokeBias; }

  virtual void doit() {
    BiasedLocking::bulk_revoke_at_safepoint((*_obj)(), _bulk_rebias, _requesting_thread);
    _safepoint_id = SafepointSynchronize::safepoint_id();
    clean_up_cached_monitor_info();
  }

  bool is_bulk_rebias() const {
    return _bulk_rebias;
  }

  uint64_t safepoint_id() const {
    return _safepoint_id;
  }
};


class RevokeOneBias : public HandshakeClosure {
protected:
  Handle _obj;
  JavaThread* _requesting_thread;
  JavaThread* _biased_locker;
  BiasedLocking::Condition _status_code;
  traceid _biased_locker_id;
  bool _executed;

public:
  RevokeOneBias(Handle obj, JavaThread* requesting_thread, JavaThread* biased_locker)
    : HandshakeClosure("RevokeOneBias")
    , _obj(obj)
    , _requesting_thread(requesting_thread)
    , _biased_locker(biased_locker)
    , _status_code(BiasedLocking::NOT_BIASED)
    , _biased_locker_id(0)
    , _executed(false) {}

  bool executed() { return _executed; }

  void do_thread(Thread* target) {
    assert(target == _biased_locker, "Wrong thread");
    _executed = true;

    oop o = _obj();
    markWord mark = o->mark();

    if (!mark.has_bias_pattern()) {
      return;
    }

    markWord prototype = o->klass()->prototype_header();
    if (!prototype.has_bias_pattern()) {
      // This object has a stale bias from before the handshake
      // was requested. If we fail this race, the object's bias
      // has been revoked by another thread so we simply return.
      markWord biased_value = mark;
      mark = o->cas_set_mark(markWord::prototype().set_age(mark.age()), mark);
      assert(!o->mark().has_bias_pattern(), "even if we raced, should still be revoked");
      if (biased_value == mark) {
        _status_code = BiasedLocking::BIAS_REVOKED;
      }
      return;
    }

    if (_biased_locker == mark.biased_locker()) {
      if (mark.bias_epoch() == prototype.bias_epoch()) {
        // Epoch is still valid. This means biaser could be currently
        // synchronized on this object. We must walk its stack looking
        // for monitor records associated with this object and change
        // them to be stack locks if any are found.
        ResourceMark rm;
        BiasedLocking::walk_stack_and_revoke(o, _biased_locker);
        _biased_locker->set_cached_monitor_info(NULL);
        assert(!o->mark().has_bias_pattern(), "invariant");
        _biased_locker_id = JFR_THREAD_ID(_biased_locker);
        _status_code = BiasedLocking::BIAS_REVOKED;
        return;
      } else {
        markWord biased_value = mark;
        mark = o->cas_set_mark(markWord::prototype().set_age(mark.age()), mark);
        if (mark == biased_value || !mark.has_bias_pattern()) {
          assert(!o->mark().has_bias_pattern(), "should be revoked");
          _status_code = (biased_value == mark) ? BiasedLocking::BIAS_REVOKED : BiasedLocking::NOT_BIASED;
          return;
        }
      }
    }

    _status_code = BiasedLocking::NOT_REVOKED;
  }

  BiasedLocking::Condition status_code() const {
    return _status_code;
  }

  traceid biased_locker() const {
    return _biased_locker_id;
  }
};


static void post_self_revocation_event(EventBiasedLockSelfRevocation* event, Klass* k) {
  assert(event != NULL, "invariant");
  assert(k != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_lockClass(k);
  event->commit();
}

static void post_revocation_event(EventBiasedLockRevocation* event, Klass* k, RevokeOneBias* op) {
  assert(event != NULL, "invariant");
  assert(k != NULL, "invariant");
  assert(op != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_lockClass(k);
  event->set_safepointId(0);
  event->set_previousOwner(op->biased_locker());
  event->commit();
}

static void post_class_revocation_event(EventBiasedLockClassRevocation* event, Klass* k, VM_BulkRevokeBias* op) {
  assert(event != NULL, "invariant");
  assert(k != NULL, "invariant");
  assert(op != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_revokedClass(k);
  event->set_disableBiasing(!op->is_bulk_rebias());
  event->set_safepointId(op->safepoint_id());
  event->commit();
}


BiasedLocking::Condition BiasedLocking::single_revoke_with_handshake(Handle obj, JavaThread *requester, JavaThread *biaser) {

  EventBiasedLockRevocation event;
  if (PrintBiasedLockingStatistics) {
    Atomic::inc(handshakes_count_addr());
  }
  log_info(biasedlocking, handshake)("JavaThread " INTPTR_FORMAT " handshaking JavaThread "
                                     INTPTR_FORMAT " to revoke object " INTPTR_FORMAT, p2i(requester),
                                     p2i(biaser), p2i(obj()));

  RevokeOneBias revoke(obj, requester, biaser);
  Handshake::execute(&revoke, biaser);
  if (revoke.status_code() == NOT_REVOKED) {
    return NOT_REVOKED;
  }
  if (revoke.executed()) {
    log_info(biasedlocking, handshake)("Handshake revocation for object " INTPTR_FORMAT " succeeded. Bias was %srevoked",
                                       p2i(obj()), (revoke.status_code() == BIAS_REVOKED ? "" : "already "));
    if (event.should_commit() && revoke.status_code() == BIAS_REVOKED) {
      post_revocation_event(&event, obj->klass(), &revoke);
    }
    assert(!obj->mark().has_bias_pattern(), "invariant");
    return revoke.status_code();
  } else {
    // Thread was not alive.
    // Grab Threads_lock before manually trying to revoke bias. This avoids race with a newly
    // created JavaThread (that happens to get the same memory address as biaser) synchronizing
    // on this object.
    {
      MutexLocker ml(Threads_lock);
      markWord mark = obj->mark();
      // Check if somebody else was able to revoke it before biased thread exited.
      if (!mark.has_bias_pattern()) {
        return NOT_BIASED;
      }
      ThreadsListHandle tlh;
      markWord prototype = obj->klass()->prototype_header();
      if (!prototype.has_bias_pattern() || (!tlh.includes(biaser) && biaser == mark.biased_locker() &&
                                            prototype.bias_epoch() == mark.bias_epoch())) {
        obj->cas_set_mark(markWord::prototype().set_age(mark.age()), mark);
        if (event.should_commit()) {
          post_revocation_event(&event, obj->klass(), &revoke);
        }
        assert(!obj->mark().has_bias_pattern(), "bias should be revoked by now");
        return BIAS_REVOKED;
      }
    }
  }

  return NOT_REVOKED;
}


// Caller should have instantiated a ResourceMark object before calling this method
void BiasedLocking::walk_stack_and_revoke(oop obj, JavaThread* biased_locker) {
  Thread* cur = Thread::current();
  assert(!SafepointSynchronize::is_at_safepoint(), "this should always be executed outside safepoints");
  assert(biased_locker->is_handshake_safe_for(cur), "wrong thread");

  markWord mark = obj->mark();
  assert(mark.biased_locker() == biased_locker &&
         obj->klass()->prototype_header().bias_epoch() == mark.bias_epoch(), "invariant");

  log_trace(biasedlocking)("JavaThread(" INTPTR_FORMAT ") revoking object " INTPTR_FORMAT ", mark "
                           INTPTR_FORMAT ", type %s, prototype header " INTPTR_FORMAT
                           ", biaser " INTPTR_FORMAT " %s",
                           p2i(cur),
                           p2i(obj),
                           mark.value(),
                           obj->klass()->external_name(),
                           obj->klass()->prototype_header().value(),
                           p2i(biased_locker),
                           cur != biased_locker ? "" : "(walking own stack)");

  markWord unbiased_prototype = markWord::prototype().set_age(obj->mark().age());

  GrowableArray<MonitorInfo*>* cached_monitor_info = get_or_compute_monitor_info(biased_locker);
  BasicLock* highest_lock = NULL;
  for (int i = 0; i < cached_monitor_info->length(); i++) {
    MonitorInfo* mon_info = cached_monitor_info->at(i);
    if (mon_info->owner() == obj) {
      log_trace(biasedlocking)("   mon_info->owner (" PTR_FORMAT ") == obj (" PTR_FORMAT ")",
                               p2i(mon_info->owner()),
                               p2i(obj));
      // Assume recursive case and fix up highest lock below
      markWord mark = markWord::encode((BasicLock*) NULL);
      highest_lock = mon_info->lock();
      highest_lock->set_displaced_header(mark);
    } else {
      log_trace(biasedlocking)("   mon_info->owner (" PTR_FORMAT ") != obj (" PTR_FORMAT ")",
                               p2i(mon_info->owner()),
                               p2i(obj));
    }
  }
  if (highest_lock != NULL) {
    // Fix up highest lock to contain displaced header and point
    // object at it
    highest_lock->set_displaced_header(unbiased_prototype);
    // Reset object header to point to displaced mark.
    // Must release store the lock address for platforms without TSO
    // ordering (e.g. ppc).
    obj->release_set_mark(markWord::encode(highest_lock));
    assert(!obj->mark().has_bias_pattern(), "illegal mark state: stack lock used bias bit");
    log_info(biasedlocking)("  Revoked bias of currently-locked object");
  } else {
    log_info(biasedlocking)("  Revoked bias of currently-unlocked object");
    // Store the unlocked value into the object's header.
    obj->set_mark(unbiased_prototype);
  }

  assert(!obj->mark().has_bias_pattern(), "must not be biased");
}

void BiasedLocking::revoke_own_lock(JavaThread* current, Handle obj) {
  markWord mark = obj->mark();

  if (!mark.has_bias_pattern()) {
    return;
  }

  Klass *k = obj->klass();
  assert(mark.biased_locker() == current &&
         k->prototype_header().bias_epoch() == mark.bias_epoch(), "Revoke failed, unhandled biased lock state");
  ResourceMark rm(current);
  log_info(biasedlocking)("Revoking bias by walking my own stack:");
  EventBiasedLockSelfRevocation event;
  BiasedLocking::walk_stack_and_revoke(obj(), current);
  current->set_cached_monitor_info(NULL);
  assert(!obj->mark().has_bias_pattern(), "invariant");
  if (event.should_commit()) {
    post_self_revocation_event(&event, k);
  }
}

void BiasedLocking::revoke(JavaThread* current, Handle obj) {
  assert(!SafepointSynchronize::is_at_safepoint(), "must not be called while at safepoint");

  while (true) {
    // We can revoke the biases of anonymously-biased objects
    // efficiently enough that we should not cause these revocations to
    // update the heuristics because doing so may cause unwanted bulk
    // revocations (which are expensive) to occur.
    markWord mark = obj->mark();

    if (!mark.has_bias_pattern()) {
      return;
    }

    if (mark.is_biased_anonymously()) {
      // We are probably trying to revoke the bias of this object due to
      // an identity hash code computation. Try to revoke the bias
      // without a safepoint. This is possible if we can successfully
      // compare-and-exchange an unbiased header into the mark word of
      // the object, meaning that no other thread has raced to acquire
      // the bias of the object.
      markWord biased_value       = mark;
      markWord unbiased_prototype = markWord::prototype().set_age(mark.age());
      markWord res_mark = obj->cas_set_mark(unbiased_prototype, mark);
      if (res_mark == biased_value) {
        return;
      }
      mark = res_mark;  // Refresh mark with the latest value.
    } else {
      Klass* k = obj->klass();
      markWord prototype_header = k->prototype_header();
      if (!prototype_header.has_bias_pattern()) {
        // This object has a stale bias from before the bulk revocation
        // for this data type occurred. It's pointless to update the
        // heuristics at this point so simply update the header with a
        // CAS. If we fail this race, the object's bias has been revoked
        // by another thread so we simply return and let the caller deal
        // with it.
        obj->cas_set_mark(prototype_header.set_age(mark.age()), mark);
        assert(!obj->mark().has_bias_pattern(), "even if we raced, should still be revoked");
        return;
      } else if (prototype_header.bias_epoch() != mark.bias_epoch()) {
        // The epoch of this biasing has expired indicating that the
        // object is effectively unbiased. We can revoke the bias of this
        // object efficiently enough with a CAS that we shouldn't update the
        // heuristics. This is normally done in the assembly code but we
        // can reach this point due to various points in the runtime
        // needing to revoke biases.
        markWord res_mark;
        markWord biased_value       = mark;
        markWord unbiased_prototype = markWord::prototype().set_age(mark.age());
        res_mark = obj->cas_set_mark(unbiased_prototype, mark);
        if (res_mark == biased_value) {
          return;
        }
        mark = res_mark;  // Refresh mark with the latest value.
      }
    }

    HeuristicsResult heuristics = update_heuristics(obj());
    if (heuristics == HR_NOT_BIASED) {
      return;
    } else if (heuristics == HR_SINGLE_REVOKE) {
      JavaThread *blt = mark.biased_locker();
      assert(blt != NULL, "invariant");
      if (blt == current) {
        // A thread is trying to revoke the bias of an object biased
        // toward it, again likely due to an identity hash code
        // computation. We can again avoid a safepoint/handshake in this case
        // since we are only going to walk our own stack. There are no
        // races with revocations occurring in other threads because we
        // reach no safepoints in the revocation path.
        EventBiasedLockSelfRevocation event;
        ResourceMark rm(current);
        walk_stack_and_revoke(obj(), blt);
        blt->set_cached_monitor_info(NULL);
        assert(!obj->mark().has_bias_pattern(), "invariant");
        if (event.should_commit()) {
          post_self_revocation_event(&event, obj->klass());
        }
        return;
      } else {
        BiasedLocking::Condition cond = single_revoke_with_handshake(obj, current, blt);
        if (cond != NOT_REVOKED) {
          return;
        }
      }
    } else {
      assert((heuristics == HR_BULK_REVOKE) ||
         (heuristics == HR_BULK_REBIAS), "?");
      EventBiasedLockClassRevocation event;
      VM_BulkRevokeBias bulk_revoke(&obj, current, (heuristics == HR_BULK_REBIAS));
      VMThread::execute(&bulk_revoke);
      if (event.should_commit()) {
        post_class_revocation_event(&event, obj->klass(), &bulk_revoke);
      }
      return;
    }
  }
}

// All objects in objs should be locked by biaser
void BiasedLocking::revoke(GrowableArray<Handle>* objs, JavaThread *biaser) {
  bool clean_my_cache = false;
  for (int i = 0; i < objs->length(); i++) {
    oop obj = (objs->at(i))();
    markWord mark = obj->mark();
    if (mark.has_bias_pattern()) {
      walk_stack_and_revoke(obj, biaser);
      clean_my_cache = true;
    }
  }
  if (clean_my_cache) {
    clean_up_cached_monitor_info(biaser);
  }
}


void BiasedLocking::revoke_at_safepoint(Handle h_obj) {
  assert(SafepointSynchronize::is_at_safepoint(), "must only be called while at safepoint");
  oop obj = h_obj();
  HeuristicsResult heuristics = update_heuristics(obj);
  if (heuristics == HR_SINGLE_REVOKE) {
    JavaThread* biased_locker = NULL;
    single_revoke_at_safepoint(obj, false, NULL, &biased_locker);
    if (biased_locker) {
      clean_up_cached_monitor_info(biased_locker);
    }
  } else if ((heuristics == HR_BULK_REBIAS) ||
             (heuristics == HR_BULK_REVOKE)) {
    bulk_revoke_at_safepoint(obj, (heuristics == HR_BULK_REBIAS), NULL);
    clean_up_cached_monitor_info();
  }
}


void BiasedLocking::preserve_marks() {
  if (!UseBiasedLocking)
    return;

  assert(SafepointSynchronize::is_at_safepoint(), "must only be called while at safepoint");

  assert(_preserved_oop_stack  == NULL, "double initialization");
  assert(_preserved_mark_stack == NULL, "double initialization");

  // In order to reduce the number of mark words preserved during GC
  // due to the presence of biased locking, we reinitialize most mark
  // words to the class's prototype during GC -- even those which have
  // a currently valid bias owner. One important situation where we
  // must not clobber a bias is when a biased object is currently
  // locked. To handle this case we iterate over the currently-locked
  // monitors in a prepass and, if they are biased, preserve their
  // mark words here. This should be a relatively small set of objects
  // especially compared to the number of objects in the heap.
  _preserved_mark_stack = new (ResourceObj::C_HEAP, mtGC) GrowableArray<markWord>(10, mtGC);
  _preserved_oop_stack = new (ResourceObj::C_HEAP, mtGC) GrowableArray<Handle>(10, mtGC);

  Thread* cur = Thread::current();
  ResourceMark rm(cur);

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thread = jtiwh.next(); ) {
    if (thread->has_last_Java_frame()) {
      RegisterMap rm(thread);
      for (javaVFrame* vf = thread->last_java_vframe(&rm); vf != NULL; vf = vf->java_sender()) {
        GrowableArray<MonitorInfo*> *monitors = vf->monitors();
        if (monitors != NULL) {
          int len = monitors->length();
          // Walk monitors youngest to oldest
          for (int i = len - 1; i >= 0; i--) {
            MonitorInfo* mon_info = monitors->at(i);
            if (mon_info->owner_is_scalar_replaced()) continue;
            oop owner = mon_info->owner();
            if (owner != NULL) {
              markWord mark = owner->mark();
              if (mark.has_bias_pattern()) {
                _preserved_oop_stack->push(Handle(cur, owner));
                _preserved_mark_stack->push(mark);
              }
            }
          }
        }
      }
    }
  }
}


void BiasedLocking::restore_marks() {
  if (!UseBiasedLocking)
    return;

  assert(_preserved_oop_stack  != NULL, "double free");
  assert(_preserved_mark_stack != NULL, "double free");

  int len = _preserved_oop_stack->length();
  for (int i = 0; i < len; i++) {
    Handle owner = _preserved_oop_stack->at(i);
    markWord mark = _preserved_mark_stack->at(i);
    owner->set_mark(mark);
  }

  delete _preserved_oop_stack;
  _preserved_oop_stack = NULL;
  delete _preserved_mark_stack;
  _preserved_mark_stack = NULL;
}


int* BiasedLocking::total_entry_count_addr()                   { return _counters.total_entry_count_addr(); }
int* BiasedLocking::biased_lock_entry_count_addr()             { return _counters.biased_lock_entry_count_addr(); }
int* BiasedLocking::anonymously_biased_lock_entry_count_addr() { return _counters.anonymously_biased_lock_entry_count_addr(); }
int* BiasedLocking::rebiased_lock_entry_count_addr()           { return _counters.rebiased_lock_entry_count_addr(); }
int* BiasedLocking::revoked_lock_entry_count_addr()            { return _counters.revoked_lock_entry_count_addr(); }
int* BiasedLocking::handshakes_count_addr()                    { return _counters.handshakes_count_addr(); }
int* BiasedLocking::fast_path_entry_count_addr()               { return _counters.fast_path_entry_count_addr(); }
int* BiasedLocking::slow_path_entry_count_addr()               { return _counters.slow_path_entry_count_addr(); }


// BiasedLockingCounters

int BiasedLockingCounters::slow_path_entry_count() const {
  if (_slow_path_entry_count != 0) {
    return _slow_path_entry_count;
  }
  int sum = _biased_lock_entry_count   + _anonymously_biased_lock_entry_count +
            _rebiased_lock_entry_count + _revoked_lock_entry_count +
            _fast_path_entry_count;

  return _total_entry_count - sum;
}

void BiasedLockingCounters::print_on(outputStream* st) const {
  tty->print_cr("# total entries: %d", _total_entry_count);
  tty->print_cr("# biased lock entries: %d", _biased_lock_entry_count);
  tty->print_cr("# anonymously biased lock entries: %d", _anonymously_biased_lock_entry_count);
  tty->print_cr("# rebiased lock entries: %d", _rebiased_lock_entry_count);
  tty->print_cr("# revoked lock entries: %d", _revoked_lock_entry_count);
  tty->print_cr("# handshakes entries: %d", _handshakes_count);
  tty->print_cr("# fast path lock entries: %d", _fast_path_entry_count);
  tty->print_cr("# slow path lock entries: %d", slow_path_entry_count());
}

void BiasedLockingCounters::print() const { print_on(tty); }
