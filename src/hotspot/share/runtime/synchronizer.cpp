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
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/padded.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/preserveException.hpp"

// The "core" versions of monitor enter and exit reside in this file.
// The interpreter and compilers contain specialized transliterated
// variants of the enter-exit fast-path operations.  See i486.ad fast_lock(),
// for instance.  If you make changes here, make sure to modify the
// interpreter, and both C1 and C2 fast-path inline locking code emission.
//
// -----------------------------------------------------------------------------

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available
// TODO-FIXME: probes should not fire when caller is _blocked.  assert() accordingly.

#define DTRACE_MONITOR_PROBE_COMMON(obj, thread)                           \
  char* bytes = NULL;                                                      \
  int len = 0;                                                             \
  jlong jtid = SharedRuntime::get_java_tid(thread);                        \
  Symbol* klassname = ((oop)(obj))->klass()->name();                       \
  if (klassname != NULL) {                                                 \
    bytes = (char*)klassname->bytes();                                     \
    len = klassname->utf8_length();                                        \
  }

#define DTRACE_MONITOR_WAIT_PROBE(monitor, obj, thread, millis)            \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_WAIT(jtid,                                           \
                           (uintptr_t)(monitor), bytes, len, (millis));    \
    }                                                                      \
  }

#define HOTSPOT_MONITOR_PROBE_notify HOTSPOT_MONITOR_NOTIFY
#define HOTSPOT_MONITOR_PROBE_notifyAll HOTSPOT_MONITOR_NOTIFYALL
#define HOTSPOT_MONITOR_PROBE_waited HOTSPOT_MONITOR_WAITED

#define DTRACE_MONITOR_PROBE(probe, monitor, obj, thread)                  \
  {                                                                        \
    if (DTraceMonitorProbes) {                                             \
      DTRACE_MONITOR_PROBE_COMMON(obj, thread);                            \
      HOTSPOT_MONITOR_PROBE_##probe(jtid, /* probe = waited */             \
                                    (uintptr_t)(monitor), bytes, len);     \
    }                                                                      \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_MONITOR_WAIT_PROBE(obj, thread, millis, mon)    {;}
#define DTRACE_MONITOR_PROBE(probe, obj, thread, mon)          {;}

#endif // ndef DTRACE_ENABLED

// This exists only as a workaround of dtrace bug 6254741
int dtrace_waited_probe(ObjectMonitor* monitor, Handle obj, Thread* thr) {
  DTRACE_MONITOR_PROBE(waited, monitor, obj(), thr);
  return 0;
}

#define NINFLATIONLOCKS 256
static volatile intptr_t gInflationLocks[NINFLATIONLOCKS];

// global list of blocks of monitors
PaddedObjectMonitor* ObjectSynchronizer::g_block_list = NULL;
bool volatile ObjectSynchronizer::_is_async_deflation_requested = false;
bool volatile ObjectSynchronizer::_is_final_audit = false;
jlong ObjectSynchronizer::_last_async_deflation_time_ns = 0;

struct ObjectMonitorListGlobals {
  char         _pad_prefix[OM_CACHE_LINE_SIZE];
  // These are highly shared list related variables.
  // To avoid false-sharing they need to be the sole occupants of a cache line.

  // Global ObjectMonitor free list. Newly allocated and deflated
  // ObjectMonitors are prepended here.
  ObjectMonitor* _free_list;
  DEFINE_PAD_MINUS_SIZE(1, OM_CACHE_LINE_SIZE, sizeof(ObjectMonitor*));

  // Global ObjectMonitor in-use list. When a JavaThread is exiting,
  // ObjectMonitors on its per-thread in-use list are prepended here.
  ObjectMonitor* _in_use_list;
  DEFINE_PAD_MINUS_SIZE(2, OM_CACHE_LINE_SIZE, sizeof(ObjectMonitor*));

  // Global ObjectMonitor wait list. Deflated ObjectMonitors wait on
  // this list until after a handshake or a safepoint for platforms
  // that don't support handshakes. After the handshake or safepoint,
  // the deflated ObjectMonitors are prepended to free_list.
  ObjectMonitor* _wait_list;
  DEFINE_PAD_MINUS_SIZE(3, OM_CACHE_LINE_SIZE, sizeof(ObjectMonitor*));

  int _free_count;    // # on free_list
  DEFINE_PAD_MINUS_SIZE(4, OM_CACHE_LINE_SIZE, sizeof(int));

  int _in_use_count;  // # on in_use_list
  DEFINE_PAD_MINUS_SIZE(5, OM_CACHE_LINE_SIZE, sizeof(int));

  int _population;    // # Extant -- in circulation
  DEFINE_PAD_MINUS_SIZE(6, OM_CACHE_LINE_SIZE, sizeof(int));

  int _wait_count;    // # on wait_list
  DEFINE_PAD_MINUS_SIZE(7, OM_CACHE_LINE_SIZE, sizeof(int));
};
static ObjectMonitorListGlobals om_list_globals;


// =====================> Spin-lock functions

// ObjectMonitors are not lockable outside of this file. We use spin-locks
// implemented using a bit in the _next_om field instead of the heavier
// weight locking mechanisms for faster list management.

#define OM_LOCK_BIT 0x1

// Return true if the ObjectMonitor is locked.
// Otherwise returns false.
static bool is_locked(ObjectMonitor* om) {
  return ((intptr_t)om->next_om_acquire() & OM_LOCK_BIT) == OM_LOCK_BIT;
}

// Mark an ObjectMonitor* with OM_LOCK_BIT and return it.
static ObjectMonitor* mark_om_ptr(ObjectMonitor* om) {
  return (ObjectMonitor*)((intptr_t)om | OM_LOCK_BIT);
}

// Return the unmarked next field in an ObjectMonitor. Note: the next
// field may or may not have been marked with OM_LOCK_BIT originally.
static ObjectMonitor* unmarked_next(ObjectMonitor* om) {
  return (ObjectMonitor*)((intptr_t)om->next_om() & ~OM_LOCK_BIT);
}

// Try to lock an ObjectMonitor. Returns true if locking was successful.
// Otherwise returns false.
static bool try_om_lock(ObjectMonitor* om) {
  // Get current next field without any OM_LOCK_BIT value.
  ObjectMonitor* next = unmarked_next(om);
  if (om->try_set_next_om(next, mark_om_ptr(next)) != next) {
    return false;  // Cannot lock the ObjectMonitor.
  }
  return true;
}

// Lock an ObjectMonitor.
static void om_lock(ObjectMonitor* om) {
  while (true) {
    if (try_om_lock(om)) {
      return;
    }
  }
}

// Unlock an ObjectMonitor.
static void om_unlock(ObjectMonitor* om) {
  ObjectMonitor* next = om->next_om();
  guarantee(((intptr_t)next & OM_LOCK_BIT) == OM_LOCK_BIT, "next=" INTPTR_FORMAT
            " must have OM_LOCK_BIT=%x set.", p2i(next), OM_LOCK_BIT);

  next = (ObjectMonitor*)((intptr_t)next & ~OM_LOCK_BIT);  // Clear OM_LOCK_BIT.
  om->release_set_next_om(next);
}

// Get the list head after locking it. Returns the list head or NULL
// if the list is empty.
static ObjectMonitor* get_list_head_locked(ObjectMonitor** list_p) {
  while (true) {
    // Acquire semantics not needed on this list load since we're
    // checking for NULL here or following up with a cmpxchg() via
    // try_om_lock() below and we retry on cmpxchg() failure.
    ObjectMonitor* mid = Atomic::load(list_p);
    if (mid == NULL) {
      return NULL;  // The list is empty.
    }
    if (try_om_lock(mid)) {
      // Acquire semantics not needed on this list load since memory is
      // already consistent due to the cmpxchg() via try_om_lock() above.
      if (Atomic::load(list_p) != mid) {
        // The list head changed before we could lock it so we have to retry.
        om_unlock(mid);
        continue;
      }
      return mid;
    }
  }
}

#undef OM_LOCK_BIT


// =====================> List Management functions

// Prepend a list of ObjectMonitors to the specified *list_p. 'tail' is
// the last ObjectMonitor in the list and there are 'count' on the list.
// Also updates the specified *count_p.
static void prepend_list_to_common(ObjectMonitor* list, ObjectMonitor* tail,
                                   int count, ObjectMonitor** list_p,
                                   int* count_p) {
  while (true) {
    // Acquire semantics not needed on this list load since we're
    // following up with a cmpxchg() via try_om_lock() below and we
    // retry on cmpxchg() failure.
    ObjectMonitor* cur = Atomic::load(list_p);
    // Prepend list to *list_p.
    if (!try_om_lock(tail)) {
      // Failed to lock tail due to a list walker so try it all again.
      continue;
    }
    // Release semantics not needed on this "unlock" since memory is
    // already consistent due to the cmpxchg() via try_om_lock() above.
    tail->set_next_om(cur);  // tail now points to cur (and unlocks tail)
    if (cur == NULL) {
      // No potential race with takers or other prependers since
      // *list_p is empty.
      if (Atomic::cmpxchg(list_p, cur, list) == cur) {
        // Successfully switched *list_p to the list value.
        Atomic::add(count_p, count);
        break;
      }
      // Implied else: try it all again
    } else {
      if (!try_om_lock(cur)) {
        continue;  // failed to lock cur so try it all again
      }
      // We locked cur so try to switch *list_p to the list value.
      if (Atomic::cmpxchg(list_p, cur, list) != cur) {
        // The list head has changed so unlock cur and try again:
        om_unlock(cur);
        continue;
      }
      Atomic::add(count_p, count);
      om_unlock(cur);
      break;
    }
  }
}

// Prepend a newly allocated block of ObjectMonitors to g_block_list and
// om_list_globals._free_list. Also updates om_list_globals._population
// and om_list_globals._free_count.
void ObjectSynchronizer::prepend_block_to_lists(PaddedObjectMonitor* new_blk) {
  // First we handle g_block_list:
  while (true) {
    PaddedObjectMonitor* cur = Atomic::load(&g_block_list);
    // Prepend new_blk to g_block_list. The first ObjectMonitor in
    // a block is reserved for use as linkage to the next block.
    new_blk[0].set_next_om(cur);
    if (Atomic::cmpxchg(&g_block_list, cur, new_blk) == cur) {
      // Successfully switched g_block_list to the new_blk value.
      Atomic::add(&om_list_globals._population, _BLOCKSIZE - 1);
      break;
    }
    // Implied else: try it all again
  }

  // Second we handle om_list_globals._free_list:
  prepend_list_to_common(new_blk + 1, &new_blk[_BLOCKSIZE - 1], _BLOCKSIZE - 1,
                         &om_list_globals._free_list, &om_list_globals._free_count);
}

// Prepend a list of ObjectMonitors to om_list_globals._free_list.
// 'tail' is the last ObjectMonitor in the list and there are 'count'
// on the list. Also updates om_list_globals._free_count.
static void prepend_list_to_global_free_list(ObjectMonitor* list,
                                             ObjectMonitor* tail, int count) {
  prepend_list_to_common(list, tail, count, &om_list_globals._free_list,
                         &om_list_globals._free_count);
}

// Prepend a list of ObjectMonitors to om_list_globals._wait_list.
// 'tail' is the last ObjectMonitor in the list and there are 'count'
// on the list. Also updates om_list_globals._wait_count.
static void prepend_list_to_global_wait_list(ObjectMonitor* list,
                                             ObjectMonitor* tail, int count) {
  prepend_list_to_common(list, tail, count, &om_list_globals._wait_list,
                         &om_list_globals._wait_count);
}

// Prepend a list of ObjectMonitors to om_list_globals._in_use_list.
// 'tail' is the last ObjectMonitor in the list and there are 'count'
// on the list. Also updates om_list_globals._in_use_list.
static void prepend_list_to_global_in_use_list(ObjectMonitor* list,
                                               ObjectMonitor* tail, int count) {
  prepend_list_to_common(list, tail, count, &om_list_globals._in_use_list,
                         &om_list_globals._in_use_count);
}

// Prepend an ObjectMonitor to the specified list. Also updates
// the specified counter.
static void prepend_to_common(ObjectMonitor* m, ObjectMonitor** list_p,
                              int* count_p) {
  while (true) {
    om_lock(m);  // Lock m so we can safely update its next field.
    ObjectMonitor* cur = NULL;
    // Lock the list head to guard against races with a list walker
    // or async deflater thread (which only races in om_in_use_list):
    if ((cur = get_list_head_locked(list_p)) != NULL) {
      // List head is now locked so we can safely switch it. Release
      // semantics not needed on this "unlock" since memory is already
      // consistent due to the cmpxchg() via get_list_head_locked() above.
      m->set_next_om(cur);  // m now points to cur (and unlocks m)
      OrderAccess::storestore();  // Make sure set_next_om() is seen first.
      Atomic::store(list_p, m);  // Switch list head to unlocked m.
      om_unlock(cur);
      break;
    }
    // The list is empty so try to set the list head.
    assert(cur == NULL, "cur must be NULL: cur=" INTPTR_FORMAT, p2i(cur));
    // Release semantics not needed on this "unlock" since memory
    // is already consistent.
    m->set_next_om(cur);  // m now points to NULL (and unlocks m)
    if (Atomic::cmpxchg(list_p, cur, m) == cur) {
      // List head is now unlocked m.
      break;
    }
    // Implied else: try it all again
  }
  Atomic::inc(count_p);
}

// Prepend an ObjectMonitor to a per-thread om_free_list.
// Also updates the per-thread om_free_count.
static void prepend_to_om_free_list(Thread* self, ObjectMonitor* m) {
  prepend_to_common(m, &self->om_free_list, &self->om_free_count);
}

// Prepend an ObjectMonitor to a per-thread om_in_use_list.
// Also updates the per-thread om_in_use_count.
static void prepend_to_om_in_use_list(Thread* self, ObjectMonitor* m) {
  prepend_to_common(m, &self->om_in_use_list, &self->om_in_use_count);
}

// Take an ObjectMonitor from the start of the specified list. Also
// decrements the specified counter. Returns NULL if none are available.
static ObjectMonitor* take_from_start_of_common(ObjectMonitor** list_p,
                                                int* count_p) {
  ObjectMonitor* take = NULL;
  // Lock the list head to guard against races with a list walker
  // or async deflater thread (which only races in om_list_globals._free_list):
  if ((take = get_list_head_locked(list_p)) == NULL) {
    return NULL;  // None are available.
  }
  ObjectMonitor* next = unmarked_next(take);
  // Switch locked list head to next (which unlocks the list head, but
  // leaves take locked). Release semantics not needed on this "unlock"
  // since memory is already consistent due to the cmpxchg() via
  // get_list_head_locked() above.
  Atomic::store(list_p, next);
  Atomic::dec(count_p);
  // Unlock take, but leave the next value for any lagging list
  // walkers. It will get cleaned up when take is prepended to
  // the in-use list:
  om_unlock(take);
  return take;
}

// Take an ObjectMonitor from the start of the om_list_globals._free_list.
// Also updates om_list_globals._free_count. Returns NULL if none are
// available.
static ObjectMonitor* take_from_start_of_global_free_list() {
  return take_from_start_of_common(&om_list_globals._free_list,
                                   &om_list_globals._free_count);
}

// Take an ObjectMonitor from the start of a per-thread free-list.
// Also updates om_free_count. Returns NULL if none are available.
static ObjectMonitor* take_from_start_of_om_free_list(Thread* self) {
  return take_from_start_of_common(&self->om_free_list, &self->om_free_count);
}


// =====================> Quick functions

// The quick_* forms are special fast-path variants used to improve
// performance.  In the simplest case, a "quick_*" implementation could
// simply return false, in which case the caller will perform the necessary
// state transitions and call the slow-path form.
// The fast-path is designed to handle frequently arising cases in an efficient
// manner and is just a degenerate "optimistic" variant of the slow-path.
// returns true  -- to indicate the call was satisfied.
// returns false -- to indicate the call needs the services of the slow-path.
// A no-loitering ordinance is in effect for code in the quick_* family
// operators: safepoints or indefinite blocking (blocking that might span a
// safepoint) are forbidden. Generally the thread_state() is _in_Java upon
// entry.
//
// Consider: An interesting optimization is to have the JIT recognize the
// following common idiom:
//   synchronized (someobj) { .... ; notify(); }
// That is, we find a notify() or notifyAll() call that immediately precedes
// the monitorexit operation.  In that case the JIT could fuse the operations
// into a single notifyAndExit() runtime primitive.

bool ObjectSynchronizer::quick_notify(oopDesc* obj, Thread* self, bool all) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->as_Java_thread()->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;  // slow-path for invalid obj
  const markWord mark = obj->mark();

  if (mark.has_locker() && self->is_lock_owned((address)mark.locker())) {
    // Degenerate notify
    // stack-locked by caller so by definition the implied waitset is empty.
    return true;
  }

  if (mark.has_monitor()) {
    ObjectMonitor* const mon = mark.monitor();
    assert(mon->object() == oop(obj), "invariant");
    if (mon->owner() != self) return false;  // slow-path for IMS exception

    if (mon->first_waiter() != NULL) {
      // We have one or more waiters. Since this is an inflated monitor
      // that we own, we can transfer one or more threads from the waitset
      // to the entrylist here and now, avoiding the slow-path.
      if (all) {
        DTRACE_MONITOR_PROBE(notifyAll, mon, obj, self);
      } else {
        DTRACE_MONITOR_PROBE(notify, mon, obj, self);
      }
      int free_count = 0;
      do {
        mon->INotify(self);
        ++free_count;
      } while (mon->first_waiter() != NULL && all);
      OM_PERFDATA_OP(Notifications, inc(free_count));
    }
    return true;
  }

  // biased locking and any other IMS exception states take the slow-path
  return false;
}


// The LockNode emitted directly at the synchronization site would have
// been too big if it were to have included support for the cases of inflated
// recursive enter and exit, so they go here instead.
// Note that we can't safely call AsyncPrintJavaStack() from within
// quick_enter() as our thread state remains _in_Java.

bool ObjectSynchronizer::quick_enter(oop obj, Thread* self,
                                     BasicLock * lock) {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->as_Java_thread()->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == NULL) return false;       // Need to throw NPE

  if (DiagnoseSyncOnPrimitiveWrappers != 0 && obj->klass()->is_box()) {
    return false;
  }

  const markWord mark = obj->mark();

  if (mark.has_monitor()) {
    ObjectMonitor* const m = mark.monitor();
    // An async deflation or GC can race us before we manage to make
    // the ObjectMonitor busy by setting the owner below. If we detect
    // that race we just bail out to the slow-path here.
    if (m->object_peek() == NULL) {
      return false;
    }
    Thread* const owner = (Thread *) m->_owner;

    // Lock contention and Transactional Lock Elision (TLE) diagnostics
    // and observability
    // Case: light contention possibly amenable to TLE
    // Case: TLE inimical operations such as nested/recursive synchronization

    if (owner == self) {
      m->_recursions++;
      return true;
    }

    // This Java Monitor is inflated so obj's header will never be
    // displaced to this thread's BasicLock. Make the displaced header
    // non-NULL so this BasicLock is not seen as recursive nor as
    // being locked. We do this unconditionally so that this thread's
    // BasicLock cannot be mis-interpreted by any stack walkers. For
    // performance reasons, stack walkers generally first check for
    // Biased Locking in the object's header, the second check is for
    // stack-locking in the object's header, the third check is for
    // recursive stack-locking in the displaced header in the BasicLock,
    // and last are the inflated Java Monitor (ObjectMonitor) checks.
    lock->set_displaced_header(markWord::unused_mark());

    if (owner == NULL && m->try_set_owner_from(NULL, self) == NULL) {
      assert(m->_recursions == 0, "invariant");
      return true;
    }
  }

  // Note that we could inflate in quick_enter.
  // This is likely a useful optimization
  // Critically, in quick_enter() we must not:
  // -- perform bias revocation, or
  // -- block indefinitely, or
  // -- reach a safepoint

  return false;        // revert to slow-path
}

// Handle notifications when synchronizing on primitive wrappers
void ObjectSynchronizer::handle_sync_on_primitive_wrapper(Handle obj, Thread* current) {
  JavaThread* self = current->as_Java_thread();

  frame last_frame = self->last_frame();
  if (last_frame.is_interpreted_frame()) {
    // adjust bcp to point back to monitorenter so that we print the correct line numbers
    last_frame.interpreter_frame_set_bcp(last_frame.interpreter_frame_bcp() - 1);
  }

  if (DiagnoseSyncOnPrimitiveWrappers == FATAL_EXIT) {
    ResourceMark rm(self);
    stringStream ss;
    self->print_stack_on(&ss);
    char* base = (char*)strstr(ss.base(), "at");
    char* newline = (char*)strchr(ss.base(), '\n');
    if (newline != NULL) {
      *newline = '\0';
    }
    fatal("Synchronizing on object " INTPTR_FORMAT " of klass %s %s", p2i(obj()), obj->klass()->external_name(), base);
  } else {
    assert(DiagnoseSyncOnPrimitiveWrappers == LOG_WARNING, "invalid value for DiagnoseSyncOnPrimitiveWrappers");
    ResourceMark rm(self);
    Log(primitivewrappers) pwlog;

    pwlog.info("Synchronizing on object " INTPTR_FORMAT " of klass %s", p2i(obj()), obj->klass()->external_name());
    if (self->has_last_Java_frame()) {
      LogStream info_stream(pwlog.info());
      self->print_stack_on(&info_stream);
    } else {
      pwlog.info("Cannot find the last Java frame");
    }

    EventSyncOnPrimitiveWrapper event;
    if (event.should_commit()) {
      event.set_boxClass(obj->klass());
      event.commit();
    }
  }

  if (last_frame.is_interpreted_frame()) {
    last_frame.interpreter_frame_set_bcp(last_frame.interpreter_frame_bcp() + 1);
  }
}

// -----------------------------------------------------------------------------
// Monitor Enter/Exit
// The interpreter and compiler assembly code tries to lock using the fast path
// of this algorithm. Make sure to update that code if the following function is
// changed. The implementation is extremely sensitive to race condition. Be careful.

void ObjectSynchronizer::enter(Handle obj, BasicLock* lock, TRAPS) {
  if (DiagnoseSyncOnPrimitiveWrappers != 0 && obj->klass()->is_box()) {
    handle_sync_on_primitive_wrapper(obj, THREAD);
  }

  if (UseBiasedLocking) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::revoke(obj, THREAD);
    } else {
      BiasedLocking::revoke_at_safepoint(obj);
    }
  }

  markWord mark = obj->mark();
  assert(!mark.has_bias_pattern(), "should not see bias pattern here");

  if (mark.is_neutral()) {
    // Anticipate successful CAS -- the ST of the displaced mark must
    // be visible <= the ST performed by the CAS.
    lock->set_displaced_header(mark);
    if (mark == obj()->cas_set_mark(markWord::from_pointer(lock), mark)) {
      return;
    }
    // Fall through to inflate() ...
  } else if (mark.has_locker() &&
             THREAD->is_lock_owned((address)mark.locker())) {
    assert(lock != mark.locker(), "must not re-lock the same lock");
    assert(lock != (BasicLock*)obj->mark().value(), "don't relock with same BasicLock");
    lock->set_displaced_header(markWord::from_pointer(NULL));
    return;
  }

  // The object header will never be displaced to this lock,
  // so it does not matter what the value is, except that it
  // must be non-zero to avoid looking like a re-entrant lock,
  // and must not look locked either.
  lock->set_displaced_header(markWord::unused_mark());
  // An async deflation can race after the inflate() call and before
  // enter() can make the ObjectMonitor busy. enter() returns false if
  // we have lost the race to async deflation and we simply try again.
  while (true) {
    ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_monitor_enter);
    if (monitor->enter(THREAD)) {
      return;
    }
  }
}

void ObjectSynchronizer::exit(oop object, BasicLock* lock, TRAPS) {
  markWord mark = object->mark();
  // We cannot check for Biased Locking if we are racing an inflation.
  assert(mark == markWord::INFLATING() ||
         !mark.has_bias_pattern(), "should not see bias pattern here");

  markWord dhw = lock->displaced_header();
  if (dhw.value() == 0) {
    // If the displaced header is NULL, then this exit matches up with
    // a recursive enter. No real work to do here except for diagnostics.
#ifndef PRODUCT
    if (mark != markWord::INFLATING()) {
      // Only do diagnostics if we are not racing an inflation. Simply
      // exiting a recursive enter of a Java Monitor that is being
      // inflated is safe; see the has_monitor() comment below.
      assert(!mark.is_neutral(), "invariant");
      assert(!mark.has_locker() ||
             THREAD->is_lock_owned((address)mark.locker()), "invariant");
      if (mark.has_monitor()) {
        // The BasicLock's displaced_header is marked as a recursive
        // enter and we have an inflated Java Monitor (ObjectMonitor).
        // This is a special case where the Java Monitor was inflated
        // after this thread entered the stack-lock recursively. When a
        // Java Monitor is inflated, we cannot safely walk the Java
        // Monitor owner's stack and update the BasicLocks because a
        // Java Monitor can be asynchronously inflated by a thread that
        // does not own the Java Monitor.
        ObjectMonitor* m = mark.monitor();
        assert(m->object()->mark() == mark, "invariant");
        assert(m->is_entered(THREAD), "invariant");
      }
    }
#endif
    return;
  }

  if (mark == markWord::from_pointer(lock)) {
    // If the object is stack-locked by the current thread, try to
    // swing the displaced header from the BasicLock back to the mark.
    assert(dhw.is_neutral(), "invariant");
    if (object->cas_set_mark(dhw, mark) == mark) {
      return;
    }
  }

  // We have to take the slow-path of possible inflation and then exit.
  // The ObjectMonitor* can't be async deflated until ownership is
  // dropped inside exit() and the ObjectMonitor* must be !is_busy().
  ObjectMonitor* monitor = inflate(THREAD, object, inflate_cause_vm_internal);
  monitor->exit(true, THREAD);
}

// -----------------------------------------------------------------------------
// Class Loader  support to workaround deadlocks on the class loader lock objects
// Also used by GC
// complete_exit()/reenter() are used to wait on a nested lock
// i.e. to give up an outer lock completely and then re-enter
// Used when holding nested locks - lock acquisition order: lock1 then lock2
//  1) complete_exit lock1 - saving recursion count
//  2) wait on lock2
//  3) when notified on lock2, unlock lock2
//  4) reenter lock1 with original recursion count
//  5) lock lock2
// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
intx ObjectSynchronizer::complete_exit(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  // The ObjectMonitor* can't be async deflated until ownership is
  // dropped inside exit() and the ObjectMonitor* must be !is_busy().
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_vm_internal);
  intptr_t ret_code = monitor->complete_exit(THREAD);
  return ret_code;
}

// NOTE: must use heavy weight monitor to handle complete_exit/reenter()
void ObjectSynchronizer::reenter(Handle obj, intx recursions, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  // An async deflation can race after the inflate() call and before
  // reenter() -> enter() can make the ObjectMonitor busy. reenter() ->
  // enter() returns false if we have lost the race to async deflation
  // and we simply try again.
  while (true) {
    ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_vm_internal);
    if (monitor->reenter(recursions, THREAD)) {
      return;
    }
  }
}

// -----------------------------------------------------------------------------
// JNI locks on java objects
// NOTE: must use heavy weight monitor to handle jni monitor enter
void ObjectSynchronizer::jni_enter(Handle obj, TRAPS) {
  if (DiagnoseSyncOnPrimitiveWrappers != 0 && obj->klass()->is_box()) {
    handle_sync_on_primitive_wrapper(obj, THREAD);
  }

  // the current locking is from JNI instead of Java code
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  THREAD->set_current_pending_monitor_is_from_java(false);
  // An async deflation can race after the inflate() call and before
  // enter() can make the ObjectMonitor busy. enter() returns false if
  // we have lost the race to async deflation and we simply try again.
  while (true) {
    ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_jni_enter);
    if (monitor->enter(THREAD)) {
      break;
    }
  }
  THREAD->set_current_pending_monitor_is_from_java(true);
}

// NOTE: must use heavy weight monitor to handle jni monitor exit
void ObjectSynchronizer::jni_exit(oop obj, Thread* THREAD) {
  if (UseBiasedLocking) {
    Handle h_obj(THREAD, obj);
    BiasedLocking::revoke(h_obj, THREAD);
    obj = h_obj();
  }
  assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");

  // The ObjectMonitor* can't be async deflated until ownership is
  // dropped inside exit() and the ObjectMonitor* must be !is_busy().
  ObjectMonitor* monitor = inflate(THREAD, obj, inflate_cause_jni_exit);
  // If this thread has locked the object, exit the monitor. We
  // intentionally do not use CHECK here because we must exit the
  // monitor even if an exception is pending.
  if (monitor->check_owner(THREAD)) {
    monitor->exit(true, THREAD);
  }
}

// -----------------------------------------------------------------------------
// Internal VM locks on java objects
// standard constructor, allows locking failures
ObjectLocker::ObjectLocker(Handle obj, Thread* thread, bool do_lock) {
  _dolock = do_lock;
  _thread = thread;
  _thread->check_for_valid_safepoint_state();
  _obj = obj;

  if (_dolock) {
    ObjectSynchronizer::enter(_obj, &_lock, _thread);
  }
}

ObjectLocker::~ObjectLocker() {
  if (_dolock) {
    ObjectSynchronizer::exit(_obj(), &_lock, _thread);
  }
}


// -----------------------------------------------------------------------------
//  Wait/Notify/NotifyAll
// NOTE: must use heavy weight monitor to handle wait()
int ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  // The ObjectMonitor* can't be async deflated because the _waiters
  // field is incremented before ownership is dropped and decremented
  // after ownership is regained.
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_wait);

  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), THREAD, millis);
  monitor->wait(millis, true, THREAD);

  // This dummy call is in place to get around dtrace bug 6254741.  Once
  // that's fixed we can uncomment the following line, remove the call
  // and change this function back into a "void" func.
  // DTRACE_MONITOR_PROBE(waited, monitor, obj(), THREAD);
  int ret_code = dtrace_waited_probe(monitor, obj, THREAD);
  return ret_code;
}

void ObjectSynchronizer::wait_uninterruptibly(Handle obj, jlong millis, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }
  if (millis < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }
  // The ObjectMonitor* can't be async deflated because the _waiters
  // field is incremented before ownership is dropped and decremented
  // after ownership is regained.
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_wait);
  monitor->wait(millis, false, THREAD);
}

void ObjectSynchronizer::notify(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  markWord mark = obj->mark();
  if (mark.has_locker() && THREAD->is_lock_owned((address)mark.locker())) {
    return;
  }
  // The ObjectMonitor* can't be async deflated until ownership is
  // dropped by the calling thread.
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_notify);
  monitor->notify(THREAD);
}

// NOTE: see comment of notify()
void ObjectSynchronizer::notifyall(Handle obj, TRAPS) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(obj, THREAD);
    assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  markWord mark = obj->mark();
  if (mark.has_locker() && THREAD->is_lock_owned((address)mark.locker())) {
    return;
  }
  // The ObjectMonitor* can't be async deflated until ownership is
  // dropped by the calling thread.
  ObjectMonitor* monitor = inflate(THREAD, obj(), inflate_cause_notify);
  monitor->notifyAll(THREAD);
}

// -----------------------------------------------------------------------------
// Hash Code handling
//
// Performance concern:
// OrderAccess::storestore() calls release() which at one time stored 0
// into the global volatile OrderAccess::dummy variable. This store was
// unnecessary for correctness. Many threads storing into a common location
// causes considerable cache migration or "sloshing" on large SMP systems.
// As such, I avoided using OrderAccess::storestore(). In some cases
// OrderAccess::fence() -- which incurs local latency on the executing
// processor -- is a better choice as it scales on SMP systems.
//
// See http://blogs.oracle.com/dave/entry/biased_locking_in_hotspot for
// a discussion of coherency costs. Note that all our current reference
// platforms provide strong ST-ST order, so the issue is moot on IA32,
// x64, and SPARC.
//
// As a general policy we use "volatile" to control compiler-based reordering
// and explicit fences (barriers) to control for architectural reordering
// performed by the CPU(s) or platform.

struct SharedGlobals {
  char         _pad_prefix[OM_CACHE_LINE_SIZE];
  // This is a highly shared mostly-read variable.
  // To avoid false-sharing it needs to be the sole occupant of a cache line.
  volatile int stw_random;
  DEFINE_PAD_MINUS_SIZE(1, OM_CACHE_LINE_SIZE, sizeof(volatile int));
  // Hot RW variable -- Sequester to avoid false-sharing
  volatile int hc_sequence;
  DEFINE_PAD_MINUS_SIZE(2, OM_CACHE_LINE_SIZE, sizeof(volatile int));
};

static SharedGlobals GVars;

static markWord read_stable_mark(oop obj) {
  markWord mark = obj->mark();
  if (!mark.is_being_inflated()) {
    return mark;       // normal fast-path return
  }

  int its = 0;
  for (;;) {
    markWord mark = obj->mark();
    if (!mark.is_being_inflated()) {
      return mark;    // normal fast-path return
    }

    // The object is being inflated by some other thread.
    // The caller of read_stable_mark() must wait for inflation to complete.
    // Avoid live-lock
    // TODO: consider calling SafepointSynchronize::do_call_back() while
    // spinning to see if there's a safepoint pending.  If so, immediately
    // yielding or blocking would be appropriate.  Avoid spinning while
    // there is a safepoint pending.
    // TODO: add inflation contention performance counters.
    // TODO: restrict the aggregate number of spinners.

    ++its;
    if (its > 10000 || !os::is_MP()) {
      if (its & 1) {
        os::naked_yield();
      } else {
        // Note that the following code attenuates the livelock problem but is not
        // a complete remedy.  A more complete solution would require that the inflating
        // thread hold the associated inflation lock.  The following code simply restricts
        // the number of spinners to at most one.  We'll have N-2 threads blocked
        // on the inflationlock, 1 thread holding the inflation lock and using
        // a yield/park strategy, and 1 thread in the midst of inflation.
        // A more refined approach would be to change the encoding of INFLATING
        // to allow encapsulation of a native thread pointer.  Threads waiting for
        // inflation to complete would use CAS to push themselves onto a singly linked
        // list rooted at the markword.  Once enqueued, they'd loop, checking a per-thread flag
        // and calling park().  When inflation was complete the thread that accomplished inflation
        // would detach the list and set the markword to inflated with a single CAS and
        // then for each thread on the list, set the flag and unpark() the thread.
        // This is conceptually similar to muxAcquire-muxRelease, except that muxRelease
        // wakes at most one thread whereas we need to wake the entire list.
        int ix = (cast_from_oop<intptr_t>(obj) >> 5) & (NINFLATIONLOCKS-1);
        int YieldThenBlock = 0;
        assert(ix >= 0 && ix < NINFLATIONLOCKS, "invariant");
        assert((NINFLATIONLOCKS & (NINFLATIONLOCKS-1)) == 0, "invariant");
        Thread::muxAcquire(gInflationLocks + ix, "gInflationLock");
        while (obj->mark() == markWord::INFLATING()) {
          // Beware: NakedYield() is advisory and has almost no effect on some platforms
          // so we periodically call self->_ParkEvent->park(1).
          // We use a mixed spin/yield/block mechanism.
          if ((YieldThenBlock++) >= 16) {
            Thread::current()->_ParkEvent->park(1);
          } else {
            os::naked_yield();
          }
        }
        Thread::muxRelease(gInflationLocks + ix);
      }
    } else {
      SpinPause();       // SMP-polite spinning
    }
  }
}

// hashCode() generation :
//
// Possibilities:
// * MD5Digest of {obj,stw_random}
// * CRC32 of {obj,stw_random} or any linear-feedback shift register function.
// * A DES- or AES-style SBox[] mechanism
// * One of the Phi-based schemes, such as:
//   2654435761 = 2^32 * Phi (golden ratio)
//   HashCodeValue = ((uintptr_t(obj) >> 3) * 2654435761) ^ GVars.stw_random ;
// * A variation of Marsaglia's shift-xor RNG scheme.
// * (obj ^ stw_random) is appealing, but can result
//   in undesirable regularity in the hashCode values of adjacent objects
//   (objects allocated back-to-back, in particular).  This could potentially
//   result in hashtable collisions and reduced hashtable efficiency.
//   There are simple ways to "diffuse" the middle address bits over the
//   generated hashCode values:

static inline intptr_t get_next_hash(Thread* self, oop obj) {
  intptr_t value = 0;
  if (hashCode == 0) {
    // This form uses global Park-Miller RNG.
    // On MP system we'll have lots of RW access to a global, so the
    // mechanism induces lots of coherency traffic.
    value = os::random();
  } else if (hashCode == 1) {
    // This variation has the property of being stable (idempotent)
    // between STW operations.  This can be useful in some of the 1-0
    // synchronization schemes.
    intptr_t addr_bits = cast_from_oop<intptr_t>(obj) >> 3;
    value = addr_bits ^ (addr_bits >> 5) ^ GVars.stw_random;
  } else if (hashCode == 2) {
    value = 1;            // for sensitivity testing
  } else if (hashCode == 3) {
    value = ++GVars.hc_sequence;
  } else if (hashCode == 4) {
    value = cast_from_oop<intptr_t>(obj);
  } else {
    // Marsaglia's xor-shift scheme with thread-specific state
    // This is probably the best overall implementation -- we'll
    // likely make this the default in future releases.
    unsigned t = self->_hashStateX;
    t ^= (t << 11);
    self->_hashStateX = self->_hashStateY;
    self->_hashStateY = self->_hashStateZ;
    self->_hashStateZ = self->_hashStateW;
    unsigned v = self->_hashStateW;
    v = (v ^ (v >> 19)) ^ (t ^ (t >> 8));
    self->_hashStateW = v;
    value = v;
  }

  value &= markWord::hash_mask;
  if (value == 0) value = 0xBAD;
  assert(value != markWord::no_hash, "invariant");
  return value;
}

intptr_t ObjectSynchronizer::FastHashCode(Thread* self, oop obj) {
  if (UseBiasedLocking) {
    // NOTE: many places throughout the JVM do not expect a safepoint
    // to be taken here. However, we only ever bias Java instances and all
    // of the call sites of identity_hash that might revoke biases have
    // been checked to make sure they can handle a safepoint. The
    // added check of the bias pattern is to avoid useless calls to
    // thread-local storage.
    if (obj->mark().has_bias_pattern()) {
      // Handle for oop obj in case of STW safepoint
      Handle hobj(self, obj);
      if (SafepointSynchronize::is_at_safepoint()) {
        BiasedLocking::revoke_at_safepoint(hobj);
      } else {
        BiasedLocking::revoke(hobj, self);
      }
      obj = hobj();
      assert(!obj->mark().has_bias_pattern(), "biases should be revoked by now");
    }
  }

  while (true) {
    ObjectMonitor* monitor = NULL;
    markWord temp, test;
    intptr_t hash;
    markWord mark = read_stable_mark(obj);

    // object should remain ineligible for biased locking
    assert(!mark.has_bias_pattern(), "invariant");

    if (mark.is_neutral()) {            // if this is a normal header
      hash = mark.hash();
      if (hash != 0) {                  // if it has a hash, just return it
        return hash;
      }
      hash = get_next_hash(self, obj);  // get a new hash
      temp = mark.copy_set_hash(hash);  // merge the hash into header
                                        // try to install the hash
      test = obj->cas_set_mark(temp, mark);
      if (test == mark) {               // if the hash was installed, return it
        return hash;
      }
      // Failed to install the hash. It could be that another thread
      // installed the hash just before our attempt or inflation has
      // occurred or... so we fall thru to inflate the monitor for
      // stability and then install the hash.
    } else if (mark.has_monitor()) {
      monitor = mark.monitor();
      temp = monitor->header();
      assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
      hash = temp.hash();
      if (hash != 0) {
        // It has a hash.

        // Separate load of dmw/header above from the loads in
        // is_being_async_deflated().

        // dmw/header and _contentions may get written by different threads.
        // Make sure to observe them in the same order when having several observers.
        OrderAccess::loadload_for_IRIW();

        if (monitor->is_being_async_deflated()) {
          // But we can't safely use the hash if we detect that async
          // deflation has occurred. So we attempt to restore the
          // header/dmw to the object's header so that we only retry
          // once if the deflater thread happens to be slow.
          monitor->install_displaced_markword_in_object(obj);
          continue;
        }
        return hash;
      }
      // Fall thru so we only have one place that installs the hash in
      // the ObjectMonitor.
    } else if (self->is_lock_owned((address)mark.locker())) {
      // This is a stack lock owned by the calling thread so fetch the
      // displaced markWord from the BasicLock on the stack.
      temp = mark.displaced_mark_helper();
      assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
      hash = temp.hash();
      if (hash != 0) {                  // if it has a hash, just return it
        return hash;
      }
      // WARNING:
      // The displaced header in the BasicLock on a thread's stack
      // is strictly immutable. It CANNOT be changed in ANY cases.
      // So we have to inflate the stack lock into an ObjectMonitor
      // even if the current thread owns the lock. The BasicLock on
      // a thread's stack can be asynchronously read by other threads
      // during an inflate() call so any change to that stack memory
      // may not propagate to other threads correctly.
    }

    // Inflate the monitor to set the hash.

    // An async deflation can race after the inflate() call and before we
    // can update the ObjectMonitor's header with the hash value below.
    monitor = inflate(self, obj, inflate_cause_hash_code);
    // Load ObjectMonitor's header/dmw field and see if it has a hash.
    mark = monitor->header();
    assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
    hash = mark.hash();
    if (hash == 0) {                    // if it does not have a hash
      hash = get_next_hash(self, obj);  // get a new hash
      temp = mark.copy_set_hash(hash);  // merge the hash into header
      assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
      uintptr_t v = Atomic::cmpxchg((volatile uintptr_t*)monitor->header_addr(), mark.value(), temp.value());
      test = markWord(v);
      if (test != mark) {
        // The attempt to update the ObjectMonitor's header/dmw field
        // did not work. This can happen if another thread managed to
        // merge in the hash just before our cmpxchg().
        // If we add any new usages of the header/dmw field, this code
        // will need to be updated.
        hash = test.hash();
        assert(test.is_neutral(), "invariant: header=" INTPTR_FORMAT, test.value());
        assert(hash != 0, "should only have lost the race to a thread that set a non-zero hash");
      }
      if (monitor->is_being_async_deflated()) {
        // If we detect that async deflation has occurred, then we
        // attempt to restore the header/dmw to the object's header
        // so that we only retry once if the deflater thread happens
        // to be slow.
        monitor->install_displaced_markword_in_object(obj);
        continue;
      }
    }
    // We finally get the hash.
    return hash;
  }
}

// Deprecated -- use FastHashCode() instead.

intptr_t ObjectSynchronizer::identity_hash_value_for(Handle obj) {
  return FastHashCode(Thread::current(), obj());
}


bool ObjectSynchronizer::current_thread_holds_lock(JavaThread* thread,
                                                   Handle h_obj) {
  if (UseBiasedLocking) {
    BiasedLocking::revoke(h_obj, thread);
    assert(!h_obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  assert(thread == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();

  markWord mark = read_stable_mark(obj);

  // Uncontended case, header points to stack
  if (mark.has_locker()) {
    return thread->is_lock_owned((address)mark.locker());
  }
  // Contended case, header points to ObjectMonitor (tagged pointer)
  if (mark.has_monitor()) {
    // The first stage of async deflation does not affect any field
    // used by this comparison so the ObjectMonitor* is usable here.
    ObjectMonitor* monitor = mark.monitor();
    return monitor->is_entered(thread) != 0;
  }
  // Unlocked case, header in place
  assert(mark.is_neutral(), "sanity check");
  return false;
}

// Be aware of this method could revoke bias of the lock object.
// This method queries the ownership of the lock handle specified by 'h_obj'.
// If the current thread owns the lock, it returns owner_self. If no
// thread owns the lock, it returns owner_none. Otherwise, it will return
// owner_other.
ObjectSynchronizer::LockOwnership ObjectSynchronizer::query_lock_ownership
(JavaThread *self, Handle h_obj) {
  // The caller must beware this method can revoke bias, and
  // revocation can result in a safepoint.
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(self->thread_state() != _thread_blocked, "invariant");

  // Possible mark states: neutral, biased, stack-locked, inflated

  if (UseBiasedLocking && h_obj()->mark().has_bias_pattern()) {
    // CASE: biased
    BiasedLocking::revoke(h_obj, self);
    assert(!h_obj->mark().has_bias_pattern(),
           "biases should be revoked by now");
  }

  assert(self == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();
  markWord mark = read_stable_mark(obj);

  // CASE: stack-locked.  Mark points to a BasicLock on the owner's stack.
  if (mark.has_locker()) {
    return self->is_lock_owned((address)mark.locker()) ?
      owner_self : owner_other;
  }

  // CASE: inflated. Mark (tagged pointer) points to an ObjectMonitor.
  if (mark.has_monitor()) {
    // The first stage of async deflation does not affect any field
    // used by this comparison so the ObjectMonitor* is usable here.
    ObjectMonitor* monitor = mark.monitor();
    void* owner = monitor->owner();
    if (owner == NULL) return owner_none;
    return (owner == self ||
            self->is_lock_owned((address)owner)) ? owner_self : owner_other;
  }

  // CASE: neutral
  assert(mark.is_neutral(), "sanity check");
  return owner_none;           // it's unlocked
}

// FIXME: jvmti should call this
JavaThread* ObjectSynchronizer::get_lock_owner(ThreadsList * t_list, Handle h_obj) {
  if (UseBiasedLocking) {
    if (SafepointSynchronize::is_at_safepoint()) {
      BiasedLocking::revoke_at_safepoint(h_obj);
    } else {
      BiasedLocking::revoke(h_obj, JavaThread::current());
    }
    assert(!h_obj->mark().has_bias_pattern(), "biases should be revoked by now");
  }

  oop obj = h_obj();
  address owner = NULL;

  markWord mark = read_stable_mark(obj);

  // Uncontended case, header points to stack
  if (mark.has_locker()) {
    owner = (address) mark.locker();
  }

  // Contended case, header points to ObjectMonitor (tagged pointer)
  else if (mark.has_monitor()) {
    // The first stage of async deflation does not affect any field
    // used by this comparison so the ObjectMonitor* is usable here.
    ObjectMonitor* monitor = mark.monitor();
    assert(monitor != NULL, "monitor should be non-null");
    owner = (address) monitor->owner();
  }

  if (owner != NULL) {
    // owning_thread_from_monitor_owner() may also return NULL here
    return Threads::owning_thread_from_monitor_owner(t_list, owner);
  }

  // Unlocked case, header in place
  // Cannot have assertion since this object may have been
  // locked by another thread when reaching here.
  // assert(mark.is_neutral(), "sanity check");

  return NULL;
}

// Visitors ...

void ObjectSynchronizer::monitors_iterate(MonitorClosure* closure) {
  PaddedObjectMonitor* block = Atomic::load(&g_block_list);
  while (block != NULL) {
    assert(block->is_chainmarker(), "must be a block header");
    for (int i = _BLOCKSIZE - 1; i > 0; i--) {
      ObjectMonitor* mid = (ObjectMonitor *)(block + i);
      if (mid->object_peek() != NULL) {
        // Only process with closure if the object is set.

        // monitors_iterate() is only called at a safepoint or when the
        // target thread is suspended or when the target thread is
        // operating on itself. The current closures in use today are
        // only interested in an owned ObjectMonitor and ownership
        // cannot be dropped under the calling contexts so the
        // ObjectMonitor cannot be async deflated.
        closure->do_monitor(mid);
      }
    }
    // unmarked_next() is not needed with g_block_list (no locking
    // used with block linkage _next_om fields).
    block = (PaddedObjectMonitor*)block->next_om();
  }
}

static bool monitors_used_above_threshold() {
  int population = Atomic::load(&om_list_globals._population);
  if (population == 0) {
    return false;
  }
  if (MonitorUsedDeflationThreshold > 0) {
    int monitors_used = population - Atomic::load(&om_list_globals._free_count) -
                        Atomic::load(&om_list_globals._wait_count);
    int monitor_usage = (monitors_used * 100LL) / population;
    return monitor_usage > MonitorUsedDeflationThreshold;
  }
  return false;
}

bool ObjectSynchronizer::is_async_deflation_needed() {
  if (is_async_deflation_requested()) {
    // Async deflation request.
    return true;
  }
  if (AsyncDeflationInterval > 0 &&
      time_since_last_async_deflation_ms() > AsyncDeflationInterval &&
      monitors_used_above_threshold()) {
    // It's been longer than our specified deflate interval and there
    // are too many monitors in use. We don't deflate more frequently
    // than AsyncDeflationInterval (unless is_async_deflation_requested)
    // in order to not swamp the ServiceThread.
    return true;
  }
  return false;
}

bool ObjectSynchronizer::request_deflate_idle_monitors() {
  Thread* self = Thread::current();
  bool ret_code = false;

  jlong last_time = last_async_deflation_time_ns();
  set_is_async_deflation_requested(true);
  {
    MonitorLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    ml.notify_all();
  }
  const int N_CHECKS = 5;
  for (int i = 0; i < N_CHECKS; i++) {  // sleep for at most 5 seconds
    if (last_async_deflation_time_ns() > last_time) {
      log_info(monitorinflation)("Async Deflation happened after %d check(s).", i);
      ret_code = true;
      break;
    }
    if (self->is_Java_thread()) {
      // JavaThread has to honor the blocking protocol.
      ThreadBlockInVM tbivm(self->as_Java_thread());
      os::naked_short_sleep(999);  // sleep for almost 1 second
    } else {
      os::naked_short_sleep(999);  // sleep for almost 1 second
    }
  }
  if (!ret_code) {
    log_info(monitorinflation)("Async Deflation DID NOT happen after %d checks.", N_CHECKS);
  }

  return ret_code;
}

jlong ObjectSynchronizer::time_since_last_async_deflation_ms() {
  return (os::javaTimeNanos() - last_async_deflation_time_ns()) / (NANOUNITS / MILLIUNITS);
}


// -----------------------------------------------------------------------------
// ObjectMonitor Lifecycle
// -----------------------
// Inflation unlinks monitors from om_list_globals._free_list or a per-thread
// free list and associates them with objects. Async deflation disassociates
// idle monitors from objects. Such scavenged monitors are returned to the
// om_list_globals._free_list.
//
// ObjectMonitors reside in type-stable memory (TSM) and are immortal.
//
// Lifecycle:
// --   unassigned and on the om_list_globals._free_list
// --   unassigned and on a per-thread free list
// --   assigned to an object.  The object is inflated and the mark refers
//      to the ObjectMonitor.

ObjectMonitor* ObjectSynchronizer::om_alloc(Thread* self) {
  // A large MAXPRIVATE value reduces both list lock contention
  // and list coherency traffic, but also tends to increase the
  // number of ObjectMonitors in circulation as well as the
  // scavenge costs.  As usual, we lean toward time in space-time
  // tradeoffs.
  const int MAXPRIVATE = 1024;
  NoSafepointVerifier nsv;

  for (;;) {
    ObjectMonitor* m;

    // 1: try to allocate from the thread's local om_free_list.
    // Threads will attempt to allocate first from their local list, then
    // from the global list, and only after those attempts fail will the
    // thread attempt to instantiate new monitors. Thread-local free lists
    // improve allocation latency, as well as reducing coherency traffic
    // on the shared global list.
    m = take_from_start_of_om_free_list(self);
    if (m != NULL) {
      guarantee(m->object_peek() == NULL, "invariant");
      m->set_allocation_state(ObjectMonitor::New);
      prepend_to_om_in_use_list(self, m);
      return m;
    }

    // 2: try to allocate from the global om_list_globals._free_list
    // If we're using thread-local free lists then try
    // to reprovision the caller's free list.
    // Acquire semantics not needed on this list load since memory
    // is already consistent due to the cmpxchg() via
    // take_from_start_of_om_free_list() above.
    if (Atomic::load(&om_list_globals._free_list) != NULL) {
      // Reprovision the thread's om_free_list.
      // Use bulk transfers to reduce the allocation rate and heat
      // on various locks.
      for (int i = self->om_free_provision; --i >= 0;) {
        ObjectMonitor* take = take_from_start_of_global_free_list();
        if (take == NULL) {
          break;  // No more are available.
        }
        guarantee(take->object_peek() == NULL, "invariant");
        // We allowed 3 field values to linger during async deflation.
        // Clear or restore them as appropriate.
        take->set_header(markWord::zero());
        // DEFLATER_MARKER is the only non-NULL value we should see here.
        take->try_set_owner_from(DEFLATER_MARKER, NULL);
        if (take->contentions() < 0) {
          // Add back max_jint to restore the contentions field to its
          // proper value.
          take->add_to_contentions(max_jint);

#ifdef ASSERT
          jint l_contentions = take->contentions();
          assert(l_contentions >= 0, "must not be negative: l_contentions=%d, contentions=%d",
                 l_contentions, take->contentions());
#endif
        }
        take->Recycle();
        // Since we're taking from the global free-list, take must be Free.
        // om_release() also sets the allocation state to Free because it
        // is called from other code paths.
        assert(take->is_free(), "invariant");
        om_release(self, take, false);
      }
      self->om_free_provision += 1 + (self->om_free_provision / 2);
      if (self->om_free_provision > MAXPRIVATE) self->om_free_provision = MAXPRIVATE;
      continue;
    }

    // 3: allocate a block of new ObjectMonitors
    // Both the local and global free lists are empty -- resort to malloc().
    // In the current implementation ObjectMonitors are TSM - immortal.
    // Ideally, we'd write "new ObjectMonitor[_BLOCKSIZE], but we want
    // each ObjectMonitor to start at the beginning of a cache line,
    // so we use align_up().
    // A better solution would be to use C++ placement-new.
    // BEWARE: As it stands currently, we don't run the ctors!
    assert(_BLOCKSIZE > 1, "invariant");
    size_t neededsize = sizeof(PaddedObjectMonitor) * _BLOCKSIZE;
    PaddedObjectMonitor* temp;
    size_t aligned_size = neededsize + (OM_CACHE_LINE_SIZE - 1);
    void* real_malloc_addr = NEW_C_HEAP_ARRAY(char, aligned_size, mtInternal);
    temp = (PaddedObjectMonitor*)align_up(real_malloc_addr, OM_CACHE_LINE_SIZE);
    (void)memset((void *) temp, 0, neededsize);

    // Format the block.
    // initialize the linked list, each monitor points to its next
    // forming the single linked free list, the very first monitor
    // will points to next block, which forms the block list.
    // The trick of using the 1st element in the block as g_block_list
    // linkage should be reconsidered.  A better implementation would
    // look like: class Block { Block * next; int N; ObjectMonitor Body [N] ; }

    for (int i = 1; i < _BLOCKSIZE; i++) {
      temp[i].set_next_om((ObjectMonitor*)&temp[i + 1]);
      assert(temp[i].is_free(), "invariant");
    }

    // terminate the last monitor as the end of list
    temp[_BLOCKSIZE - 1].set_next_om((ObjectMonitor*)NULL);

    // Element [0] is reserved for global list linkage
    temp[0].set_allocation_state(ObjectMonitor::ChainMarker);

    // Consider carving out this thread's current request from the
    // block in hand.  This avoids some lock traffic and redundant
    // list activity.

    prepend_block_to_lists(temp);
  }
}

// Place "m" on the caller's private per-thread om_free_list.
// In practice there's no need to clamp or limit the number of
// monitors on a thread's om_free_list as the only non-allocation time
// we'll call om_release() is to return a monitor to the free list after
// a CAS attempt failed. This doesn't allow unbounded #s of monitors to
// accumulate on a thread's free list.
//
// Key constraint: all ObjectMonitors on a thread's free list and the global
// free list must have their object field set to null. This prevents the
// scavenger -- deflate_monitor_list() -- from reclaiming them
// while we are trying to release them.

void ObjectSynchronizer::om_release(Thread* self, ObjectMonitor* m,
                                    bool from_per_thread_alloc) {
  guarantee(m->header().value() == 0, "invariant");
  guarantee(m->object_peek() == NULL, "invariant");
  NoSafepointVerifier nsv;

  if ((m->is_busy() | m->_recursions) != 0) {
    stringStream ss;
    fatal("freeing in-use monitor: %s, recursions=" INTX_FORMAT,
          m->is_busy_to_string(&ss), m->_recursions);
  }
  m->set_allocation_state(ObjectMonitor::Free);
  // _next_om is used for both per-thread in-use and free lists so
  // we have to remove 'm' from the in-use list first (as needed).
  if (from_per_thread_alloc) {
    // Need to remove 'm' from om_in_use_list.
    ObjectMonitor* mid = NULL;
    ObjectMonitor* next = NULL;

    // This list walk can race with another list walker or with async
    // deflation so we have to worry about an ObjectMonitor being
    // removed from this list while we are walking it.

    // Lock the list head to avoid racing with another list walker
    // or with async deflation.
    if ((mid = get_list_head_locked(&self->om_in_use_list)) == NULL) {
      fatal("thread=" INTPTR_FORMAT " in-use list must not be empty.", p2i(self));
    }
    next = unmarked_next(mid);
    if (m == mid) {
      // First special case:
      // 'm' matches mid, is the list head and is locked. Switch the list
      // head to next which unlocks the list head, but leaves the extracted
      // mid locked. Release semantics not needed on this "unlock" since
      // memory is already consistent due to the get_list_head_locked()
      // above.
      Atomic::store(&self->om_in_use_list, next);
    } else if (m == next) {
      // Second special case:
      // 'm' matches next after the list head and we already have the list
      // head locked so set mid to what we are extracting:
      mid = next;
      // Lock mid to prevent races with a list walker or an async
      // deflater thread that's ahead of us. The locked list head
      // prevents races from behind us.
      om_lock(mid);
      // Update next to what follows mid (if anything):
      next = unmarked_next(mid);
      // Switch next after the list head to new next which unlocks the
      // list head, but leaves the extracted mid locked. Release semantics
      // not needed on this "unlock" since memory is already consistent
      // due to the get_list_head_locked() above.
      self->om_in_use_list->set_next_om(next);
    } else {
      // We have to search the list to find 'm'.
      guarantee(next != NULL, "thread=" INTPTR_FORMAT ": om_in_use_list=" INTPTR_FORMAT
                " is too short.", p2i(self), p2i(self->om_in_use_list));
      // Our starting anchor is next after the list head which is the
      // last ObjectMonitor we checked:
      ObjectMonitor* anchor = next;
      // Lock anchor to prevent races with a list walker or an async
      // deflater thread that's ahead of us. The locked list head
      // prevents races from behind us.
      om_lock(anchor);
      om_unlock(mid);  // Unlock the list head now that anchor is locked.
      while ((mid = unmarked_next(anchor)) != NULL) {
        if (m == mid) {
          // We found 'm' on the per-thread in-use list so extract it.
          // Update next to what follows mid (if anything):
          next = unmarked_next(mid);
          // Switch next after the anchor to new next which unlocks the
          // anchor, but leaves the extracted mid locked. Release semantics
          // not needed on this "unlock" since memory is already consistent
          // due to the om_unlock() above before entering the loop or the
          // om_unlock() below before looping again.
          anchor->set_next_om(next);
          break;
        } else {
          // Lock the next anchor to prevent races with a list walker
          // or an async deflater thread that's ahead of us. The locked
          // current anchor prevents races from behind us.
          om_lock(mid);
          // Unlock current anchor now that next anchor is locked:
          om_unlock(anchor);
          anchor = mid;  // Advance to new anchor and try again.
        }
      }
    }

    if (mid == NULL) {
      // Reached end of the list and didn't find 'm' so:
      fatal("thread=" INTPTR_FORMAT " must find m=" INTPTR_FORMAT "on om_in_use_list="
            INTPTR_FORMAT, p2i(self), p2i(m), p2i(self->om_in_use_list));
    }

    // At this point mid is disconnected from the in-use list so
    // its lock no longer has any effects on the in-use list.
    Atomic::dec(&self->om_in_use_count);
    // Unlock mid, but leave the next value for any lagging list
    // walkers. It will get cleaned up when mid is prepended to
    // the thread's free list:
    om_unlock(mid);
  }

  prepend_to_om_free_list(self, m);
  guarantee(m->is_free(), "invariant");
}

// Return ObjectMonitors on a moribund thread's free and in-use
// lists to the appropriate global lists. The ObjectMonitors on the
// per-thread in-use list may still be in use by other threads.
//
// We currently call om_flush() from Threads::remove() before the
// thread has been excised from the thread list and is no longer a
// mutator.
//
// deflate_global_idle_monitors() and deflate_per_thread_idle_monitors()
// (in another thread) can run at the same time as om_flush() so we have
// to follow a careful protocol to prevent list corruption.

void ObjectSynchronizer::om_flush(Thread* self) {
  // Process the per-thread in-use list first to be consistent.
  int in_use_count = 0;
  ObjectMonitor* in_use_list = NULL;
  ObjectMonitor* in_use_tail = NULL;
  NoSafepointVerifier nsv;

  // This function can race with a list walker or with an async
  // deflater thread so we lock the list head to prevent confusion.
  // An async deflater thread checks to see if the target thread
  // is exiting, but if it has made it past that check before we
  // started exiting, then it is racing to get to the in-use list.
  if ((in_use_list = get_list_head_locked(&self->om_in_use_list)) != NULL) {
    // At this point, we have locked the in-use list head so a racing
    // thread cannot come in after us. However, a racing thread could
    // be ahead of us; we'll detect that and delay to let it finish.
    //
    // The thread is going away, however the ObjectMonitors on the
    // om_in_use_list may still be in-use by other threads. Link
    // them to in_use_tail, which will be linked into the global
    // in-use list (om_list_globals._in_use_list) below.
    //
    // Account for the in-use list head before the loop since it is
    // already locked (by this thread):
    in_use_tail = in_use_list;
    in_use_count++;
    for (ObjectMonitor* cur_om = unmarked_next(in_use_list); cur_om != NULL;) {
      if (is_locked(cur_om)) {
        // cur_om is locked so there must be a racing walker or async
        // deflater thread ahead of us so we'll give it a chance to finish.
        while (is_locked(cur_om)) {
          os::naked_short_sleep(1);
        }
        // Refetch the possibly changed next field and try again.
        cur_om = unmarked_next(in_use_tail);
        continue;
      }
      if (cur_om->object_peek() == NULL) {
        // Two reasons for object() to be NULL here:
        // 1) cur_om was deflated and the object ref was cleared while it
        // was locked. We happened to see it just after it was unlocked
        // (and added to the free list).
        // 2) The object has been GC'ed so the association with object is
        // already broken, but we don't want to do the deflation work now.

        // Refetch the possibly changed next field:
        ObjectMonitor* in_use_next = unmarked_next(in_use_tail);
        if (cur_om != in_use_next) {
          // The NULL is because of async deflation so try again:
          cur_om = in_use_next;
          continue;
        }
        // Implied else: The NULL is because of GC, but we leave the
        // node on the in-use list to be deflated after it has been
        // moved to the global in-use list.
      }
      in_use_tail = cur_om;
      in_use_count++;
      cur_om = unmarked_next(cur_om);
    }
    guarantee(in_use_tail != NULL, "invariant");
#ifdef ASSERT
    int l_om_in_use_count = Atomic::load(&self->om_in_use_count);
    assert(l_om_in_use_count == in_use_count, "in-use counts don't match: "
           "l_om_in_use_count=%d, in_use_count=%d", l_om_in_use_count, in_use_count);
#endif
    Atomic::store(&self->om_in_use_count, 0);
    OrderAccess::storestore();  // Make sure counter update is seen first.
    // Clear the in-use list head (which also unlocks it):
    Atomic::store(&self->om_in_use_list, (ObjectMonitor*)NULL);
    om_unlock(in_use_list);
  }

  int free_count = 0;
  ObjectMonitor* free_list = NULL;
  ObjectMonitor* free_tail = NULL;
  // This function can race with a list walker thread so we lock the
  // list head to prevent confusion.
  if ((free_list = get_list_head_locked(&self->om_free_list)) != NULL) {
    // At this point, we have locked the free list head so a racing
    // thread cannot come in after us. However, a racing thread could
    // be ahead of us; we'll detect that and delay to let it finish.
    //
    // The thread is going away. Set 'free_tail' to the last per-thread free
    // monitor which will be linked to om_list_globals._free_list below.
    //
    // Account for the free list head before the loop since it is
    // already locked (by this thread):
    free_tail = free_list;
    free_count++;
    for (ObjectMonitor* s = unmarked_next(free_list); s != NULL; s = unmarked_next(s)) {
      if (is_locked(s)) {
        // s is locked so there must be a racing walker thread ahead
        // of us so we'll give it a chance to finish.
        while (is_locked(s)) {
          os::naked_short_sleep(1);
        }
      }
      free_tail = s;
      free_count++;
      guarantee(s->object_peek() == NULL, "invariant");
      if (s->is_busy()) {
        stringStream ss;
        fatal("must be !is_busy: %s", s->is_busy_to_string(&ss));
      }
    }
    guarantee(free_tail != NULL, "invariant");
#ifdef ASSERT
    int l_om_free_count = Atomic::load(&self->om_free_count);
    assert(l_om_free_count == free_count, "free counts don't match: "
           "l_om_free_count=%d, free_count=%d", l_om_free_count, free_count);
#endif
    Atomic::store(&self->om_free_count, 0);
    OrderAccess::storestore();  // Make sure counter update is seen first.
    Atomic::store(&self->om_free_list, (ObjectMonitor*)NULL);
    om_unlock(free_list);
  }

  if (free_tail != NULL) {
    prepend_list_to_global_free_list(free_list, free_tail, free_count);
  }

  if (in_use_tail != NULL) {
    prepend_list_to_global_in_use_list(in_use_list, in_use_tail, in_use_count);
  }

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStream* ls = NULL;
  if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if ((free_count != 0 || in_use_count != 0) &&
             log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  if (ls != NULL) {
    ls->print_cr("om_flush: jt=" INTPTR_FORMAT ", free_count=%d"
                 ", in_use_count=%d" ", om_free_provision=%d",
                 p2i(self), free_count, in_use_count, self->om_free_provision);
  }
}

static void post_monitor_inflate_event(EventJavaMonitorInflate* event,
                                       const oop obj,
                                       ObjectSynchronizer::InflateCause cause) {
  assert(event != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_monitorClass(obj->klass());
  event->set_address((uintptr_t)(void*)obj);
  event->set_cause((u1)cause);
  event->commit();
}

// Fast path code shared by multiple functions
void ObjectSynchronizer::inflate_helper(oop obj) {
  markWord mark = obj->mark();
  if (mark.has_monitor()) {
    ObjectMonitor* monitor = mark.monitor();
    assert(ObjectSynchronizer::verify_objmon_isinpool(monitor), "monitor=" INTPTR_FORMAT " is invalid", p2i(monitor));
    markWord dmw = monitor->header();
    assert(dmw.is_neutral(), "sanity check: header=" INTPTR_FORMAT, dmw.value());
    return;
  }
  (void)inflate(Thread::current(), obj, inflate_cause_vm_internal);
}

ObjectMonitor* ObjectSynchronizer::inflate(Thread* self, oop object,
                                           const InflateCause cause) {
  EventJavaMonitorInflate event;

  for (;;) {
    const markWord mark = object->mark();
    assert(!mark.has_bias_pattern(), "invariant");

    // The mark can be in one of the following states:
    // *  Inflated     - just return
    // *  Stack-locked - coerce it to inflated
    // *  INFLATING    - busy wait for conversion to complete
    // *  Neutral      - aggressively inflate the object.
    // *  BIASED       - Illegal.  We should never see this

    // CASE: inflated
    if (mark.has_monitor()) {
      ObjectMonitor* inf = mark.monitor();
      markWord dmw = inf->header();
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());
      assert(ObjectSynchronizer::verify_objmon_isinpool(inf), "monitor is invalid");
      return inf;
    }

    // CASE: inflation in progress - inflating over a stack-lock.
    // Some other thread is converting from stack-locked to inflated.
    // Only that thread can complete inflation -- other threads must wait.
    // The INFLATING value is transient.
    // Currently, we spin/yield/park and poll the markword, waiting for inflation to finish.
    // We could always eliminate polling by parking the thread on some auxiliary list.
    if (mark == markWord::INFLATING()) {
      read_stable_mark(object);
      continue;
    }

    // CASE: stack-locked
    // Could be stack-locked either by this thread or by some other thread.
    //
    // Note that we allocate the ObjectMonitor speculatively, _before_ attempting
    // to install INFLATING into the mark word.  We originally installed INFLATING,
    // allocated the ObjectMonitor, and then finally STed the address of the
    // ObjectMonitor into the mark.  This was correct, but artificially lengthened
    // the interval in which INFLATING appeared in the mark, thus increasing
    // the odds of inflation contention.
    //
    // We now use per-thread private ObjectMonitor free lists.
    // These list are reprovisioned from the global free list outside the
    // critical INFLATING...ST interval.  A thread can transfer
    // multiple ObjectMonitors en-mass from the global free list to its local free list.
    // This reduces coherency traffic and lock contention on the global free list.
    // Using such local free lists, it doesn't matter if the om_alloc() call appears
    // before or after the CAS(INFLATING) operation.
    // See the comments in om_alloc().

    LogStreamHandle(Trace, monitorinflation) lsh;

    if (mark.has_locker()) {
      ObjectMonitor* m = om_alloc(self);
      // Optimistically prepare the ObjectMonitor - anticipate successful CAS
      // We do this before the CAS in order to minimize the length of time
      // in which INFLATING appears in the mark.
      m->Recycle();
      m->_Responsible  = NULL;
      m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;   // Consider: maintain by type/class

      markWord cmp = object->cas_set_mark(markWord::INFLATING(), mark);
      if (cmp != mark) {
        // om_release() will reset the allocation state from New to Free.
        om_release(self, m, true);
        continue;       // Interference -- just retry
      }

      // We've successfully installed INFLATING (0) into the mark-word.
      // This is the only case where 0 will appear in a mark-word.
      // Only the singular thread that successfully swings the mark-word
      // to 0 can perform (or more precisely, complete) inflation.
      //
      // Why do we CAS a 0 into the mark-word instead of just CASing the
      // mark-word from the stack-locked value directly to the new inflated state?
      // Consider what happens when a thread unlocks a stack-locked object.
      // It attempts to use CAS to swing the displaced header value from the
      // on-stack BasicLock back into the object header.  Recall also that the
      // header value (hash code, etc) can reside in (a) the object header, or
      // (b) a displaced header associated with the stack-lock, or (c) a displaced
      // header in an ObjectMonitor.  The inflate() routine must copy the header
      // value from the BasicLock on the owner's stack to the ObjectMonitor, all
      // the while preserving the hashCode stability invariants.  If the owner
      // decides to release the lock while the value is 0, the unlock will fail
      // and control will eventually pass from slow_exit() to inflate.  The owner
      // will then spin, waiting for the 0 value to disappear.   Put another way,
      // the 0 causes the owner to stall if the owner happens to try to
      // drop the lock (restoring the header from the BasicLock to the object)
      // while inflation is in-progress.  This protocol avoids races that might
      // would otherwise permit hashCode values to change or "flicker" for an object.
      // Critically, while object->mark is 0 mark.displaced_mark_helper() is stable.
      // 0 serves as a "BUSY" inflate-in-progress indicator.


      // fetch the displaced mark from the owner's stack.
      // The owner can't die or unwind past the lock while our INFLATING
      // object is in the mark.  Furthermore the owner can't complete
      // an unlock on the object, either.
      markWord dmw = mark.displaced_mark_helper();
      // Catch if the object's header is not neutral (not locked and
      // not marked is what we care about here).
      assert(dmw.is_neutral(), "invariant: header=" INTPTR_FORMAT, dmw.value());

      // Setup monitor fields to proper values -- prepare the monitor
      m->set_header(dmw);

      // Optimization: if the mark.locker stack address is associated
      // with this thread we could simply set m->_owner = self.
      // Note that a thread can inflate an object
      // that it has stack-locked -- as might happen in wait() -- directly
      // with CAS.  That is, we can avoid the xchg-NULL .... ST idiom.
      m->set_owner_from(NULL, DEFLATER_MARKER, mark.locker());
      m->set_object(object);
      // TODO-FIXME: assert BasicLock->dhw != 0.

      // Must preserve store ordering. The monitor state must
      // be stable at the time of publishing the monitor address.
      guarantee(object->mark() == markWord::INFLATING(), "invariant");
      // Release semantics so that above set_object() is seen first.
      object->release_set_mark(markWord::encode(m));

      // Once ObjectMonitor is configured and the object is associated
      // with the ObjectMonitor, it is safe to allow async deflation:
      assert(m->is_new(), "freshly allocated monitor must be new");
      // Release semantics needed to keep allocation_state from floating up.
      m->release_set_allocation_state(ObjectMonitor::Old);

      // Hopefully the performance counters are allocated on distinct cache lines
      // to avoid false sharing on MP systems ...
      OM_PERFDATA_OP(Inflations, inc());
      if (log_is_enabled(Trace, monitorinflation)) {
        ResourceMark rm(self);
        lsh.print_cr("inflate(has_locker): object=" INTPTR_FORMAT ", mark="
                     INTPTR_FORMAT ", type='%s'", p2i(object),
                     object->mark().value(), object->klass()->external_name());
      }
      if (event.should_commit()) {
        post_monitor_inflate_event(&event, object, cause);
      }
      return m;
    }

    // CASE: neutral
    // TODO-FIXME: for entry we currently inflate and then try to CAS _owner.
    // If we know we're inflating for entry it's better to inflate by swinging a
    // pre-locked ObjectMonitor pointer into the object header.   A successful
    // CAS inflates the object *and* confers ownership to the inflating thread.
    // In the current implementation we use a 2-step mechanism where we CAS()
    // to inflate and then CAS() again to try to swing _owner from NULL to self.
    // An inflateTry() method that we could call from enter() would be useful.

    // Catch if the object's header is not neutral (not locked and
    // not marked is what we care about here).
    assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
    ObjectMonitor* m = om_alloc(self);
    // prepare m for installation - set monitor to initial state
    m->Recycle();
    m->set_header(mark);
    // DEFLATER_MARKER is the only non-NULL value we should see here.
    m->try_set_owner_from(DEFLATER_MARKER, NULL);
    m->set_object(object);
    m->_Responsible  = NULL;
    m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;       // consider: keep metastats by type/class

    if (object->cas_set_mark(markWord::encode(m), mark) != mark) {
      m->set_header(markWord::zero());
      m->set_object(NULL);
      m->Recycle();
      // om_release() will reset the allocation state from New to Free.
      om_release(self, m, true);
      m = NULL;
      continue;
      // interference - the markword changed - just retry.
      // The state-transitions are one-way, so there's no chance of
      // live-lock -- "Inflated" is an absorbing state.
    }

    // Once the ObjectMonitor is configured and object is associated
    // with the ObjectMonitor, it is safe to allow async deflation:
    assert(m->is_new(), "freshly allocated monitor must be new");
    // Release semantics are not needed to keep allocation_state from
    // floating up since cas_set_mark() takes care of it.
    m->set_allocation_state(ObjectMonitor::Old);

    // Hopefully the performance counters are allocated on distinct
    // cache lines to avoid false sharing on MP systems ...
    OM_PERFDATA_OP(Inflations, inc());
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm(self);
      lsh.print_cr("inflate(neutral): object=" INTPTR_FORMAT ", mark="
                   INTPTR_FORMAT ", type='%s'", p2i(object),
                   object->mark().value(), object->klass()->external_name());
    }
    if (event.should_commit()) {
      post_monitor_inflate_event(&event, object, cause);
    }
    return m;
  }
}


// An async deflation request is registered with the ServiceThread
// and it is notified.
void ObjectSynchronizer::do_safepoint_work() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  log_debug(monitorinflation)("requesting async deflation of idle monitors.");
  // Request deflation of idle monitors by the ServiceThread:
  set_is_async_deflation_requested(true);
  MonitorLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  ml.notify_all();

  if (log_is_enabled(Debug, monitorinflation)) {
    // The VMThread calls do_final_audit_and_print_stats() which calls
    // audit_and_print_stats() at the Info level at VM exit time.
    ObjectSynchronizer::audit_and_print_stats(false /* on_exit */);
  }
}

// Deflate the specified ObjectMonitor if not in-use. Returns true if it
// was deflated and false otherwise.
//
// The async deflation protocol sets owner to DEFLATER_MARKER and
// makes contentions negative as signals to contending threads that
// an async deflation is in progress. There are a number of checks
// as part of the protocol to make sure that the calling thread has
// not lost the race to a contending thread.
//
// The ObjectMonitor has been successfully async deflated when:
//   (contentions < 0)
// Contending threads that see that condition know to retry their operation.
//
bool ObjectSynchronizer::deflate_monitor(ObjectMonitor* mid,
                                         ObjectMonitor** free_head_p,
                                         ObjectMonitor** free_tail_p) {
  // A newly allocated ObjectMonitor should not be seen here so we
  // avoid an endless inflate/deflate cycle.
  assert(mid->is_old(), "must be old: allocation_state=%d",
         (int) mid->allocation_state());

  if (mid->is_busy()) {
    // Easy checks are first - the ObjectMonitor is busy so no deflation.
    return false;
  }

  const oop obj = mid->object_peek();

  if (obj == NULL) {
    // If the object died, we can recycle the monitor without racing with
    // Java threads. The GC already broke the association with the object.
    mid->set_owner_from(NULL, DEFLATER_MARKER);
    mid->_contentions = -max_jint;
  } else {
    // Set a NULL owner to DEFLATER_MARKER to force any contending thread
    // through the slow path. This is just the first part of the async
    // deflation dance.
    if (mid->try_set_owner_from(NULL, DEFLATER_MARKER) != NULL) {
      // The owner field is no longer NULL so we lost the race since the
      // ObjectMonitor is now busy.
      return false;
    }

    if (mid->contentions() > 0 || mid->_waiters != 0) {
      // Another thread has raced to enter the ObjectMonitor after
      // mid->is_busy() above or has already entered and waited on
      // it which makes it busy so no deflation. Restore owner to
      // NULL if it is still DEFLATER_MARKER.
      if (mid->try_set_owner_from(DEFLATER_MARKER, NULL) != DEFLATER_MARKER) {
        // Deferred decrement for the JT EnterI() that cancelled the async deflation.
        mid->add_to_contentions(-1);
      }
      return false;
    }

    // Make a zero contentions field negative to force any contending threads
    // to retry. This is the second part of the async deflation dance.
    if (Atomic::cmpxchg(&mid->_contentions, (jint)0, -max_jint) != 0) {
      // Contentions was no longer 0 so we lost the race since the
      // ObjectMonitor is now busy. Restore owner to NULL if it is
      // still DEFLATER_MARKER:
      if (mid->try_set_owner_from(DEFLATER_MARKER, NULL) != DEFLATER_MARKER) {
        // Deferred decrement for the JT EnterI() that cancelled the async deflation.
        mid->add_to_contentions(-1);
      }
      return false;
    }
  }

  // Sanity checks for the races:
  guarantee(mid->owner_is_DEFLATER_MARKER(), "must be deflater marker");
  guarantee(mid->contentions() < 0, "must be negative: contentions=%d",
            mid->contentions());
  guarantee(mid->_waiters == 0, "must be 0: waiters=%d", mid->_waiters);
  guarantee(mid->_cxq == NULL, "must be no contending threads: cxq="
            INTPTR_FORMAT, p2i(mid->_cxq));
  guarantee(mid->_EntryList == NULL,
            "must be no entering threads: EntryList=" INTPTR_FORMAT,
            p2i(mid->_EntryList));

  if (obj != NULL) {
    if (log_is_enabled(Trace, monitorinflation)) {
      ResourceMark rm;
      log_trace(monitorinflation)("deflate_monitor: object=" INTPTR_FORMAT
                                  ", mark=" INTPTR_FORMAT ", type='%s'",
                                  p2i(obj), obj->mark().value(),
                                  obj->klass()->external_name());
    }

    // Install the old mark word if nobody else has already done it.
    mid->install_displaced_markword_in_object(obj);
  }
  mid->clear_common();

  assert(mid->object_peek() == NULL, "must be NULL: object=" INTPTR_FORMAT,
         p2i(mid->object_peek()));
  assert(mid->is_free(), "must be free: allocation_state=%d",
         (int)mid->allocation_state());

  // Move the deflated ObjectMonitor to the working free list
  // defined by free_head_p and free_tail_p.
  if (*free_head_p == NULL) {
    // First one on the list.
    *free_head_p = mid;
  }
  if (*free_tail_p != NULL) {
    // We append to the list so the caller can use mid->_next_om
    // to fix the linkages in its context.
    ObjectMonitor* prevtail = *free_tail_p;
    // prevtail should have been cleaned up by the caller:
#ifdef ASSERT
    ObjectMonitor* l_next_om = unmarked_next(prevtail);
    assert(l_next_om == NULL, "must be NULL: _next_om=" INTPTR_FORMAT, p2i(l_next_om));
#endif
    om_lock(prevtail);
    prevtail->set_next_om(mid);  // prevtail now points to mid (and is unlocked)
  }
  *free_tail_p = mid;

  // At this point, mid->_next_om still refers to its current
  // value and another ObjectMonitor's _next_om field still
  // refers to this ObjectMonitor. Those linkages have to be
  // cleaned up by the caller who has the complete context.

  // We leave owner == DEFLATER_MARKER and contentions < 0
  // to force any racing threads to retry.
  return true;  // Success, ObjectMonitor has been deflated.
}

// Walk a given ObjectMonitor list and deflate idle ObjectMonitors.
// Returns the number of deflated ObjectMonitors. The given
// list could be a per-thread in-use list or the global in-use list.
// If self is a JavaThread and a safepoint has started, then we save state
// via saved_mid_in_use_p and return to the caller to honor the safepoint.
//
int ObjectSynchronizer::deflate_monitor_list(Thread* self,
                                             ObjectMonitor** list_p,
                                             int* count_p,
                                             ObjectMonitor** free_head_p,
                                             ObjectMonitor** free_tail_p,
                                             ObjectMonitor** saved_mid_in_use_p) {
  ObjectMonitor* cur_mid_in_use = NULL;
  ObjectMonitor* mid = NULL;
  ObjectMonitor* next = NULL;
  ObjectMonitor* next_next = NULL;
  int deflated_count = 0;
  NoSafepointVerifier nsv;

  // We use the more complicated lock-cur_mid_in_use-and-mid-as-we-go
  // protocol because om_release() can do list deletions in parallel;
  // this also prevents races with a list walker thread. We also
  // lock-next-next-as-we-go to prevent an om_flush() that is behind
  // this thread from passing us.
  if (*saved_mid_in_use_p == NULL) {
    // No saved state so start at the beginning.
    // Lock the list head so we can possibly deflate it:
    if ((mid = get_list_head_locked(list_p)) == NULL) {
      return 0;  // The list is empty so nothing to deflate.
    }
    next = unmarked_next(mid);
  } else {
    // We're restarting after a safepoint so restore the necessary state
    // before we resume.
    cur_mid_in_use = *saved_mid_in_use_p;
    // Lock cur_mid_in_use so we can possibly update its
    // next field to extract a deflated ObjectMonitor.
    om_lock(cur_mid_in_use);
    mid = unmarked_next(cur_mid_in_use);
    if (mid == NULL) {
      om_unlock(cur_mid_in_use);
      *saved_mid_in_use_p = NULL;
      return 0;  // The remainder is empty so nothing more to deflate.
    }
    // Lock mid so we can possibly deflate it:
    om_lock(mid);
    next = unmarked_next(mid);
  }

  while (true) {
    // The current mid is locked at this point. If we have a
    // cur_mid_in_use, then it is also locked at this point.

    if (next != NULL) {
      // We lock next so that an om_flush() thread that is behind us
      // cannot pass us when we unlock the current mid.
      om_lock(next);
      next_next = unmarked_next(next);
    }

    // Only try to deflate if mid is old (is not newly allocated and
    // is not newly freed).
    if (mid->is_old() && deflate_monitor(mid, free_head_p, free_tail_p)) {
      // Deflation succeeded and already updated free_head_p and
      // free_tail_p as needed. Finish the move to the local free list
      // by unlinking mid from the global or per-thread in-use list.
      if (cur_mid_in_use == NULL) {
        // mid is the list head and it is locked. Switch the list head
        // to next which is also locked (if not NULL) and also leave
        // mid locked. Release semantics needed since not all code paths
        // in deflate_monitor() ensure memory consistency.
        Atomic::release_store(list_p, next);
      } else {
        ObjectMonitor* locked_next = mark_om_ptr(next);
        // mid and cur_mid_in_use are locked. Switch cur_mid_in_use's
        // next field to locked_next and also leave mid locked.
        // Release semantics needed since not all code paths in
        // deflate_monitor() ensure memory consistency.
        cur_mid_in_use->release_set_next_om(locked_next);
      }
      // At this point mid is disconnected from the in-use list so
      // its lock longer has any effects on in-use list.
      deflated_count++;
      Atomic::dec(count_p);
      // mid is current tail in the free_head_p list so NULL terminate
      // it (which also unlocks it). No release semantics needed since
      // Atomic::dec() already provides it.
      mid->set_next_om(NULL);

      // All the list management is done so move on to the next one:
      mid = next;  // mid keeps non-NULL next's locked state
      next = next_next;
    } else {
      // mid is considered in-use if mid is not old or deflation did not
      // succeed. A mid->is_new() node can be seen here when it is freshly
      // returned by om_alloc() (and skips the deflation code path).
      // A mid->is_old() node can be seen here when deflation failed.
      // A mid->is_free() node can be seen here when a fresh node from
      // om_alloc() is released by om_release() due to losing the race
      // in inflate().

      // All the list management is done so move on to the next one:
      if (cur_mid_in_use != NULL) {
        om_unlock(cur_mid_in_use);
      }
      // The next cur_mid_in_use keeps mid's lock state so
      // that it is stable for a possible next field change. It
      // cannot be modified by om_release() while it is locked.
      cur_mid_in_use = mid;
      mid = next;  // mid keeps non-NULL next's locked state
      next = next_next;

      if (self->is_Java_thread() &&
          SafepointMechanism::should_process(self->as_Java_thread()) &&
          // Acquire semantics are not needed on this list load since
          // it is not dependent on the following load which does have
          // acquire semantics.
          cur_mid_in_use != Atomic::load(list_p) && cur_mid_in_use->is_old()) {
        // If a safepoint has started and cur_mid_in_use is not the list
        // head and is old, then it is safe to use as saved state. Return
        // to the caller before blocking.
        *saved_mid_in_use_p = cur_mid_in_use;
        om_unlock(cur_mid_in_use);
        if (mid != NULL) {
          om_unlock(mid);
        }
        return deflated_count;
      }
    }
    if (mid == NULL) {
      if (cur_mid_in_use != NULL) {
        om_unlock(cur_mid_in_use);
      }
      break;  // Reached end of the list so nothing more to deflate.
    }

    // The current mid's next field is locked at this point. If we have
    // a cur_mid_in_use, then it is also locked at this point.
  }
  // We finished the list without a safepoint starting so there's
  // no need to save state.
  *saved_mid_in_use_p = NULL;
  return deflated_count;
}

class HandshakeForDeflation : public HandshakeClosure {
 public:
  HandshakeForDeflation() : HandshakeClosure("HandshakeForDeflation") {}

  void do_thread(Thread* thread) {
    log_trace(monitorinflation)("HandshakeForDeflation::do_thread: thread="
                                INTPTR_FORMAT, p2i(thread));
  }
};

// This function is called by the ServiceThread to deflate monitors.
// It is also called by do_final_audit_and_print_stats() by the VMThread.
void ObjectSynchronizer::deflate_idle_monitors() {
  Thread* self = Thread::current();
  // Deflate any global idle monitors.
  deflate_global_idle_monitors(self);

  int count = 0;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    if (Atomic::load(&jt->om_in_use_count) > 0 && !jt->is_exiting()) {
      // This JavaThread is using ObjectMonitors so deflate any that
      // are idle unless this JavaThread is exiting; do not race with
      // ObjectSynchronizer::om_flush().
      deflate_per_thread_idle_monitors(self, jt);
      count++;
    }
  }
  if (count > 0) {
    log_debug(monitorinflation)("did async deflation of idle monitors for %d thread(s).", count);
  }

  log_info(monitorinflation)("async global_population=%d, global_in_use_count=%d, "
                             "global_free_count=%d, global_wait_count=%d",
                             Atomic::load(&om_list_globals._population),
                             Atomic::load(&om_list_globals._in_use_count),
                             Atomic::load(&om_list_globals._free_count),
                             Atomic::load(&om_list_globals._wait_count));

  GVars.stw_random = os::random();

  if (self->is_Java_thread()) {
    // The async deflation request has been processed.
    _last_async_deflation_time_ns = os::javaTimeNanos();
    set_is_async_deflation_requested(false);
  }

  if (Atomic::load(&om_list_globals._wait_count) > 0) {
    // There are deflated ObjectMonitors waiting for a handshake
    // (or a safepoint) for safety.

    ObjectMonitor* list = Atomic::load(&om_list_globals._wait_list);
    assert(list != NULL, "om_list_globals._wait_list must not be NULL");
    int count = Atomic::load(&om_list_globals._wait_count);
    Atomic::store(&om_list_globals._wait_count, 0);
    OrderAccess::storestore();  // Make sure counter update is seen first.
    Atomic::store(&om_list_globals._wait_list, (ObjectMonitor*)NULL);

    // Find the tail for prepend_list_to_common(). No need to mark
    // ObjectMonitors for this list walk since only the deflater
    // thread manages the wait list.
#ifdef ASSERT
    int l_count = 0;
#endif
    ObjectMonitor* tail = NULL;
    for (ObjectMonitor* n = list; n != NULL; n = unmarked_next(n)) {
      tail = n;
#ifdef ASSERT
      l_count++;
#endif
    }
    assert(count == l_count, "count=%d != l_count=%d", count, l_count);

    if (self->is_Java_thread()) {
      // A JavaThread needs to handshake in order to safely free the
      // monitors that were deflated in this cycle.
      HandshakeForDeflation hfd_hc;
      Handshake::execute(&hfd_hc);
   }

    prepend_list_to_common(list, tail, count, &om_list_globals._free_list,
                           &om_list_globals._free_count);

    log_info(monitorinflation)("moved %d idle monitors from global waiting list to global free list", count);
  }
}

// Deflate global idle ObjectMonitors.
//
void ObjectSynchronizer::deflate_global_idle_monitors(Thread* self) {
  deflate_common_idle_monitors(self, true /* is_global */, NULL /* target */);
}

// Deflate the specified JavaThread's idle ObjectMonitors.
//
void ObjectSynchronizer::deflate_per_thread_idle_monitors(Thread* self,
                                                          JavaThread* target) {
  deflate_common_idle_monitors(self, false /* !is_global */, target);
}

// Deflate global or per-thread idle ObjectMonitors.
//
void ObjectSynchronizer::deflate_common_idle_monitors(Thread* self,
                                                      bool is_global,
                                                      JavaThread* target) {
  int deflated_count = 0;
  ObjectMonitor* free_head_p = NULL;  // Local SLL of scavenged ObjectMonitors
  ObjectMonitor* free_tail_p = NULL;
  ObjectMonitor* saved_mid_in_use_p = NULL;
  elapsedTimer timer;

  if (log_is_enabled(Info, monitorinflation)) {
    timer.start();
  }

  if (is_global) {
    OM_PERFDATA_OP(MonExtant, set_value(Atomic::load(&om_list_globals._in_use_count)));
  } else {
    OM_PERFDATA_OP(MonExtant, inc(Atomic::load(&target->om_in_use_count)));
  }

  do {
    int local_deflated_count;
    if (is_global) {
      local_deflated_count =
          deflate_monitor_list(self, &om_list_globals._in_use_list,
                               &om_list_globals._in_use_count,
                               &free_head_p, &free_tail_p,
                               &saved_mid_in_use_p);
    } else {
      local_deflated_count =
          deflate_monitor_list(self, &target->om_in_use_list,
                               &target->om_in_use_count, &free_head_p,
                               &free_tail_p, &saved_mid_in_use_p);
    }
    deflated_count += local_deflated_count;

    if (free_head_p != NULL) {
      // Move the deflated ObjectMonitors to the global free list.
      guarantee(free_tail_p != NULL && local_deflated_count > 0, "free_tail_p=" INTPTR_FORMAT ", local_deflated_count=%d", p2i(free_tail_p), local_deflated_count);
      // Note: The target thread can be doing an om_alloc() that
      // is trying to prepend an ObjectMonitor on its in-use list
      // at the same time that we have deflated the current in-use
      // list head and put it on the local free list. prepend_to_common()
      // will detect the race and retry which avoids list corruption,
      // but the next field in free_tail_p can flicker to marked
      // and then unmarked while prepend_to_common() is sorting it
      // all out.
#ifdef ASSERT
      ObjectMonitor* l_next_om = unmarked_next(free_tail_p);
      assert(l_next_om == NULL, "must be NULL: _next_om=" INTPTR_FORMAT, p2i(l_next_om));
#endif

      prepend_list_to_global_wait_list(free_head_p, free_tail_p, local_deflated_count);

      OM_PERFDATA_OP(Deflations, inc(local_deflated_count));
    }

    if (saved_mid_in_use_p != NULL) {
      // deflate_monitor_list() detected a safepoint starting.
      timer.stop();
      {
        if (is_global) {
          log_debug(monitorinflation)("pausing deflation of global idle monitors for a safepoint.");
        } else {
          log_debug(monitorinflation)("jt=" INTPTR_FORMAT ": pausing deflation of per-thread idle monitors for a safepoint.", p2i(target));
        }
        assert(self->is_Java_thread() &&
               SafepointMechanism::should_process(self->as_Java_thread()),
               "sanity check");
        ThreadBlockInVM blocker(self->as_Java_thread());
      }
      // Prepare for another loop after the safepoint.
      free_head_p = NULL;
      free_tail_p = NULL;
      if (log_is_enabled(Info, monitorinflation)) {
        timer.start();
      }
    }
  } while (saved_mid_in_use_p != NULL);
  timer.stop();

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStream* ls = NULL;
  if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if (deflated_count != 0 && log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  if (ls != NULL) {
    if (is_global) {
      ls->print_cr("async-deflating global idle monitors, %3.7f secs, %d monitors", timer.seconds(), deflated_count);
    } else {
      ls->print_cr("jt=" INTPTR_FORMAT ": async-deflating per-thread idle monitors, %3.7f secs, %d monitors", p2i(target), timer.seconds(), deflated_count);
    }
  }
}

// Monitor cleanup on JavaThread::exit

// Iterate through monitor cache and attempt to release thread's monitors
// Gives up on a particular monitor if an exception occurs, but continues
// the overall iteration, swallowing the exception.
class ReleaseJavaMonitorsClosure: public MonitorClosure {
 private:
  TRAPS;

 public:
  ReleaseJavaMonitorsClosure(Thread* thread) : THREAD(thread) {}
  void do_monitor(ObjectMonitor* mid) {
    if (mid->owner() == THREAD) {
      (void)mid->complete_exit(CHECK);
    }
  }
};

// Release all inflated monitors owned by THREAD.  Lightweight monitors are
// ignored.  This is meant to be called during JNI thread detach which assumes
// all remaining monitors are heavyweight.  All exceptions are swallowed.
// Scanning the extant monitor list can be time consuming.
// A simple optimization is to add a per-thread flag that indicates a thread
// called jni_monitorenter() during its lifetime.
//
// Instead of NoSafepointVerifier it might be cheaper to
// use an idiom of the form:
//   auto int tmp = SafepointSynchronize::_safepoint_counter ;
//   <code that must not run at safepoint>
//   guarantee (((tmp ^ _safepoint_counter) | (tmp & 1)) == 0) ;
// Since the tests are extremely cheap we could leave them enabled
// for normal product builds.

void ObjectSynchronizer::release_monitors_owned_by_thread(TRAPS) {
  assert(THREAD == JavaThread::current(), "must be current Java thread");
  NoSafepointVerifier nsv;
  ReleaseJavaMonitorsClosure rjmc(THREAD);
  ObjectSynchronizer::monitors_iterate(&rjmc);
  THREAD->clear_pending_exception();
}

const char* ObjectSynchronizer::inflate_cause_name(const InflateCause cause) {
  switch (cause) {
    case inflate_cause_vm_internal:    return "VM Internal";
    case inflate_cause_monitor_enter:  return "Monitor Enter";
    case inflate_cause_wait:           return "Monitor Wait";
    case inflate_cause_notify:         return "Monitor Notify";
    case inflate_cause_hash_code:      return "Monitor Hash Code";
    case inflate_cause_jni_enter:      return "JNI Monitor Enter";
    case inflate_cause_jni_exit:       return "JNI Monitor Exit";
    default:
      ShouldNotReachHere();
  }
  return "Unknown";
}

//------------------------------------------------------------------------------
// Debugging code

u_char* ObjectSynchronizer::get_gvars_addr() {
  return (u_char*)&GVars;
}

u_char* ObjectSynchronizer::get_gvars_hc_sequence_addr() {
  return (u_char*)&GVars.hc_sequence;
}

size_t ObjectSynchronizer::get_gvars_size() {
  return sizeof(SharedGlobals);
}

u_char* ObjectSynchronizer::get_gvars_stw_random_addr() {
  return (u_char*)&GVars.stw_random;
}

// This function can be called at a safepoint or it can be called when
// we are trying to exit the VM. When we are trying to exit the VM, the
// list walker functions can run in parallel with the other list
// operations so spin-locking is used for safety.
//
// Calls to this function can be added in various places as a debugging
// aid; pass 'true' for the 'on_exit' parameter to have in-use monitor
// details logged at the Info level and 'false' for the 'on_exit'
// parameter to have in-use monitor details logged at the Trace level.
//
void ObjectSynchronizer::audit_and_print_stats(bool on_exit) {
  assert(on_exit || SafepointSynchronize::is_at_safepoint(), "invariant");

  LogStreamHandle(Debug, monitorinflation) lsh_debug;
  LogStreamHandle(Info, monitorinflation) lsh_info;
  LogStreamHandle(Trace, monitorinflation) lsh_trace;
  LogStream* ls = NULL;
  if (log_is_enabled(Trace, monitorinflation)) {
    ls = &lsh_trace;
  } else if (log_is_enabled(Debug, monitorinflation)) {
    ls = &lsh_debug;
  } else if (log_is_enabled(Info, monitorinflation)) {
    ls = &lsh_info;
  }
  assert(ls != NULL, "sanity check");

  // Log counts for the global and per-thread monitor lists:
  int chk_om_population = log_monitor_list_counts(ls);
  int error_cnt = 0;

  ls->print_cr("Checking global lists:");

  // Check om_list_globals._population:
  if (Atomic::load(&om_list_globals._population) == chk_om_population) {
    ls->print_cr("global_population=%d equals chk_om_population=%d",
                 Atomic::load(&om_list_globals._population), chk_om_population);
  } else {
    // With fine grained locks on the monitor lists, it is possible for
    // log_monitor_list_counts() to return a value that doesn't match
    // om_list_globals._population. So far a higher value has been
    // seen in testing so something is being double counted by
    // log_monitor_list_counts().
    ls->print_cr("WARNING: global_population=%d is not equal to "
                 "chk_om_population=%d",
                 Atomic::load(&om_list_globals._population), chk_om_population);
  }

  // Check om_list_globals._in_use_list and om_list_globals._in_use_count:
  chk_global_in_use_list_and_count(ls, &error_cnt);

  // Check om_list_globals._free_list and om_list_globals._free_count:
  chk_global_free_list_and_count(ls, &error_cnt);

  // Check om_list_globals._wait_list and om_list_globals._wait_count:
  chk_global_wait_list_and_count(ls, &error_cnt);

  ls->print_cr("Checking per-thread lists:");

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    // Check om_in_use_list and om_in_use_count:
    chk_per_thread_in_use_list_and_count(jt, ls, &error_cnt);

    // Check om_free_list and om_free_count:
    chk_per_thread_free_list_and_count(jt, ls, &error_cnt);
  }

  if (error_cnt == 0) {
    ls->print_cr("No errors found in monitor list checks.");
  } else {
    log_error(monitorinflation)("found monitor list errors: error_cnt=%d", error_cnt);
  }

  if ((on_exit && log_is_enabled(Info, monitorinflation)) ||
      (!on_exit && log_is_enabled(Trace, monitorinflation))) {
    // When exiting this log output is at the Info level. When called
    // at a safepoint, this log output is at the Trace level since
    // there can be a lot of it.
    log_in_use_monitor_details(ls);
  }

  ls->flush();

  guarantee(error_cnt == 0, "ERROR: found monitor list errors: error_cnt=%d", error_cnt);
}

// Check a free monitor entry; log any errors.
void ObjectSynchronizer::chk_free_entry(JavaThread* jt, ObjectMonitor* n,
                                        outputStream * out, int *error_cnt_p) {
  stringStream ss;
  if (n->is_busy()) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must not be busy: %s", p2i(jt),
                    p2i(n), n->is_busy_to_string(&ss));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": free global monitor "
                    "must not be busy: %s", p2i(n), n->is_busy_to_string(&ss));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  if (n->header().value() != 0) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must have NULL _header "
                    "field: _header=" INTPTR_FORMAT, p2i(jt), p2i(n),
                    n->header().value());
      *error_cnt_p = *error_cnt_p + 1;
    }
  }
  if (n->object_peek() != NULL) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": free per-thread monitor must have NULL _object "
                    "field: _object=" INTPTR_FORMAT, p2i(jt), p2i(n),
                    p2i(n->object_peek()));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": free global monitor "
                    "must have NULL _object field: _object=" INTPTR_FORMAT,
                    p2i(n), p2i(n->object_peek()));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Lock the next ObjectMonitor for traversal and unlock the current
// ObjectMonitor. Returns the next ObjectMonitor if there is one.
// Otherwise returns NULL (after unlocking the current ObjectMonitor).
// This function is used by the various list walker functions to
// safely walk a list without allowing an ObjectMonitor to be moved
// to another list in the middle of a walk.
static ObjectMonitor* lock_next_for_traversal(ObjectMonitor* cur) {
  assert(is_locked(cur), "cur=" INTPTR_FORMAT " must be locked", p2i(cur));
  ObjectMonitor* next = unmarked_next(cur);
  if (next == NULL) {  // Reached the end of the list.
    om_unlock(cur);
    return NULL;
  }
  om_lock(next);   // Lock next before unlocking current to keep
  om_unlock(cur);  // from being by-passed by another thread.
  return next;
}

// Check the global free list and count; log the results of the checks.
void ObjectSynchronizer::chk_global_free_list_and_count(outputStream * out,
                                                        int *error_cnt_p) {
  int chk_om_free_count = 0;
  ObjectMonitor* cur = NULL;
  if ((cur = get_list_head_locked(&om_list_globals._free_list)) != NULL) {
    // Marked the global free list head so process the list.
    while (true) {
      chk_free_entry(NULL /* jt */, cur, out, error_cnt_p);
      chk_om_free_count++;

      cur = lock_next_for_traversal(cur);
      if (cur == NULL) {
        break;
      }
    }
  }
  int l_free_count = Atomic::load(&om_list_globals._free_count);
  if (l_free_count == chk_om_free_count) {
    out->print_cr("global_free_count=%d equals chk_om_free_count=%d",
                  l_free_count, chk_om_free_count);
  } else {
    // With fine grained locks on om_list_globals._free_list, it
    // is possible for an ObjectMonitor to be prepended to
    // om_list_globals._free_list after we started calculating
    // chk_om_free_count so om_list_globals._free_count may not
    // match anymore.
    out->print_cr("WARNING: global_free_count=%d is not equal to "
                  "chk_om_free_count=%d", l_free_count, chk_om_free_count);
  }
}

// Check the global wait list and count; log the results of the checks.
void ObjectSynchronizer::chk_global_wait_list_and_count(outputStream * out,
                                                        int *error_cnt_p) {
  int chk_om_wait_count = 0;
  ObjectMonitor* cur = NULL;
  if ((cur = get_list_head_locked(&om_list_globals._wait_list)) != NULL) {
    // Marked the global wait list head so process the list.
    while (true) {
      // Rules for om_list_globals._wait_list are the same as for
      // om_list_globals._free_list:
      chk_free_entry(NULL /* jt */, cur, out, error_cnt_p);
      chk_om_wait_count++;

      cur = lock_next_for_traversal(cur);
      if (cur == NULL) {
        break;
      }
    }
  }
  if (Atomic::load(&om_list_globals._wait_count) == chk_om_wait_count) {
    out->print_cr("global_wait_count=%d equals chk_om_wait_count=%d",
                  Atomic::load(&om_list_globals._wait_count), chk_om_wait_count);
  } else {
    out->print_cr("ERROR: global_wait_count=%d is not equal to "
                  "chk_om_wait_count=%d",
                  Atomic::load(&om_list_globals._wait_count), chk_om_wait_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the global in-use list and count; log the results of the checks.
void ObjectSynchronizer::chk_global_in_use_list_and_count(outputStream * out,
                                                          int *error_cnt_p) {
  int chk_om_in_use_count = 0;
  ObjectMonitor* cur = NULL;
  if ((cur = get_list_head_locked(&om_list_globals._in_use_list)) != NULL) {
    // Marked the global in-use list head so process the list.
    while (true) {
      chk_in_use_entry(NULL /* jt */, cur, out, error_cnt_p);
      chk_om_in_use_count++;

      cur = lock_next_for_traversal(cur);
      if (cur == NULL) {
        break;
      }
    }
  }
  int l_in_use_count = Atomic::load(&om_list_globals._in_use_count);
  if (l_in_use_count == chk_om_in_use_count) {
    out->print_cr("global_in_use_count=%d equals chk_om_in_use_count=%d",
                  l_in_use_count, chk_om_in_use_count);
  } else {
    // With fine grained locks on the monitor lists, it is possible for
    // an exiting JavaThread to put its in-use ObjectMonitors on the
    // global in-use list after chk_om_in_use_count is calculated above.
    out->print_cr("WARNING: global_in_use_count=%d is not equal to chk_om_in_use_count=%d",
                  l_in_use_count, chk_om_in_use_count);
  }
}

// Check an in-use monitor entry; log any errors.
void ObjectSynchronizer::chk_in_use_entry(JavaThread* jt, ObjectMonitor* n,
                                          outputStream * out, int *error_cnt_p) {
  if (n->header().value() == 0) {
    if (jt != NULL) {
      out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                    ": in-use per-thread monitor must have non-NULL _header "
                    "field.", p2i(jt), p2i(n));
    } else {
      out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global monitor "
                    "must have non-NULL _header field.", p2i(n));
    }
    *error_cnt_p = *error_cnt_p + 1;
  }
  const oop obj = n->object_peek();
  if (obj != NULL) {
    const markWord mark = obj->mark();
    if (!mark.has_monitor()) {
      if (jt != NULL) {
        out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                      ": in-use per-thread monitor's object does not think "
                      "it has a monitor: obj=" INTPTR_FORMAT ", mark="
                      INTPTR_FORMAT,  p2i(jt), p2i(n), p2i(obj), mark.value());
      } else {
        out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global "
                      "monitor's object does not think it has a monitor: obj="
                      INTPTR_FORMAT ", mark=" INTPTR_FORMAT, p2i(n),
                      p2i(obj), mark.value());
      }
      *error_cnt_p = *error_cnt_p + 1;
    }
    ObjectMonitor* const obj_mon = mark.monitor();
    if (n != obj_mon) {
      if (jt != NULL) {
        out->print_cr("ERROR: jt=" INTPTR_FORMAT ", monitor=" INTPTR_FORMAT
                      ": in-use per-thread monitor's object does not refer "
                      "to the same monitor: obj=" INTPTR_FORMAT ", mark="
                      INTPTR_FORMAT ", obj_mon=" INTPTR_FORMAT, p2i(jt),
                      p2i(n), p2i(obj), mark.value(), p2i(obj_mon));
      } else {
        out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use global "
                      "monitor's object does not refer to the same monitor: obj="
                      INTPTR_FORMAT ", mark=" INTPTR_FORMAT ", obj_mon="
                      INTPTR_FORMAT, p2i(n), p2i(obj), mark.value(), p2i(obj_mon));
      }
      *error_cnt_p = *error_cnt_p + 1;
    }
  }
}

// Check the thread's free list and count; log the results of the checks.
void ObjectSynchronizer::chk_per_thread_free_list_and_count(JavaThread *jt,
                                                            outputStream * out,
                                                            int *error_cnt_p) {
  int chk_om_free_count = 0;
  ObjectMonitor* cur = NULL;
  if ((cur = get_list_head_locked(&jt->om_free_list)) != NULL) {
    // Marked the per-thread free list head so process the list.
    while (true) {
      chk_free_entry(jt, cur, out, error_cnt_p);
      chk_om_free_count++;

      cur = lock_next_for_traversal(cur);
      if (cur == NULL) {
        break;
      }
    }
  }
  int l_om_free_count = Atomic::load(&jt->om_free_count);
  if (l_om_free_count == chk_om_free_count) {
    out->print_cr("jt=" INTPTR_FORMAT ": om_free_count=%d equals "
                  "chk_om_free_count=%d", p2i(jt), l_om_free_count, chk_om_free_count);
  } else {
    out->print_cr("ERROR: jt=" INTPTR_FORMAT ": om_free_count=%d is not "
                  "equal to chk_om_free_count=%d", p2i(jt), l_om_free_count,
                  chk_om_free_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Check the thread's in-use list and count; log the results of the checks.
void ObjectSynchronizer::chk_per_thread_in_use_list_and_count(JavaThread *jt,
                                                              outputStream * out,
                                                              int *error_cnt_p) {
  int chk_om_in_use_count = 0;
  ObjectMonitor* cur = NULL;
  if ((cur = get_list_head_locked(&jt->om_in_use_list)) != NULL) {
    // Marked the per-thread in-use list head so process the list.
    while (true) {
      chk_in_use_entry(jt, cur, out, error_cnt_p);
      chk_om_in_use_count++;

      cur = lock_next_for_traversal(cur);
      if (cur == NULL) {
        break;
      }
    }
  }
  int l_om_in_use_count = Atomic::load(&jt->om_in_use_count);
  if (l_om_in_use_count == chk_om_in_use_count) {
    out->print_cr("jt=" INTPTR_FORMAT ": om_in_use_count=%d equals "
                  "chk_om_in_use_count=%d", p2i(jt), l_om_in_use_count,
                  chk_om_in_use_count);
  } else {
    out->print_cr("ERROR: jt=" INTPTR_FORMAT ": om_in_use_count=%d is not "
                  "equal to chk_om_in_use_count=%d", p2i(jt), l_om_in_use_count,
                  chk_om_in_use_count);
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Do the final audit and print of ObjectMonitor stats; must be done
// by the VMThread (at VM exit time).
void ObjectSynchronizer::do_final_audit_and_print_stats() {
  assert(Thread::current()->is_VM_thread(), "sanity check");

  if (is_final_audit()) {  // Only do the audit once.
    return;
  }
  set_is_final_audit();

  if (log_is_enabled(Info, monitorinflation)) {
    // Do a deflation in order to reduce the in-use monitor population
    // that is reported by ObjectSynchronizer::log_in_use_monitor_details()
    // which is called by ObjectSynchronizer::audit_and_print_stats().
    ObjectSynchronizer::deflate_idle_monitors();
    // The other audit_and_print_stats() call is done at the Debug
    // level at a safepoint in ObjectSynchronizer::do_safepoint_work().
    ObjectSynchronizer::audit_and_print_stats(true /* on_exit */);
  }
}

// Log details about ObjectMonitors on the in-use lists. The 'BHL'
// flags indicate why the entry is in-use, 'object' and 'object type'
// indicate the associated object and its type.
void ObjectSynchronizer::log_in_use_monitor_details(outputStream * out) {
  stringStream ss;
  if (Atomic::load(&om_list_globals._in_use_count) > 0) {
    out->print_cr("In-use global monitor info:");
    out->print_cr("(B -> is_busy, H -> has hash code, L -> lock status)");
    out->print_cr("%18s  %s  %18s  %18s",
                  "monitor", "BHL", "object", "object type");
    out->print_cr("==================  ===  ==================  ==================");
    ObjectMonitor* cur = NULL;
    if ((cur = get_list_head_locked(&om_list_globals._in_use_list)) != NULL) {
      // Marked the global in-use list head so process the list.
      while (true) {
        const oop obj = cur->object_peek();
        const markWord mark = cur->header();
        ResourceMark rm;
        out->print(INTPTR_FORMAT "  %d%d%d  " INTPTR_FORMAT "  %s", p2i(cur),
                   cur->is_busy() != 0, mark.hash() != 0, cur->owner() != NULL,
                   p2i(obj), obj == NULL ? "" : obj->klass()->external_name());
        if (cur->is_busy() != 0) {
          out->print(" (%s)", cur->is_busy_to_string(&ss));
          ss.reset();
        }
        out->cr();

        cur = lock_next_for_traversal(cur);
        if (cur == NULL) {
          break;
        }
      }
    }
  }

  out->print_cr("In-use per-thread monitor info:");
  out->print_cr("(B -> is_busy, H -> has hash code, L -> lock status)");
  out->print_cr("%18s  %18s  %s  %18s  %18s",
                "jt", "monitor", "BHL", "object", "object type");
  out->print_cr("==================  ==================  ===  ==================  ==================");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    ObjectMonitor* cur = NULL;
    if ((cur = get_list_head_locked(&jt->om_in_use_list)) != NULL) {
      // Marked the global in-use list head so process the list.
      while (true) {
        const oop obj = cur->object_peek();
        const markWord mark = cur->header();
        ResourceMark rm;
        out->print(INTPTR_FORMAT "  " INTPTR_FORMAT "  %d%d%d  " INTPTR_FORMAT
                   "  %s", p2i(jt), p2i(cur), cur->is_busy() != 0,
                   mark.hash() != 0, cur->owner() != NULL, p2i(obj),
                   obj == NULL ? "" : obj->klass()->external_name());
        if (cur->is_busy() != 0) {
          out->print(" (%s)", cur->is_busy_to_string(&ss));
          ss.reset();
        }
        out->cr();

        cur = lock_next_for_traversal(cur);
        if (cur == NULL) {
          break;
        }
      }
    }
  }

  out->flush();
}

// Log counts for the global and per-thread monitor lists and return
// the population count.
int ObjectSynchronizer::log_monitor_list_counts(outputStream * out) {
  int pop_count = 0;
  out->print_cr("%18s  %10s  %10s  %10s  %10s",
                "Global Lists:", "InUse", "Free", "Wait", "Total");
  out->print_cr("==================  ==========  ==========  ==========  ==========");
  int l_in_use_count = Atomic::load(&om_list_globals._in_use_count);
  int l_free_count = Atomic::load(&om_list_globals._free_count);
  int l_wait_count = Atomic::load(&om_list_globals._wait_count);
  out->print_cr("%18s  %10d  %10d  %10d  %10d", "", l_in_use_count,
                l_free_count, l_wait_count,
                Atomic::load(&om_list_globals._population));
  pop_count += l_in_use_count + l_free_count + l_wait_count;

  out->print_cr("%18s  %10s  %10s  %10s",
                "Per-Thread Lists:", "InUse", "Free", "Provision");
  out->print_cr("==================  ==========  ==========  ==========");

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    int l_om_in_use_count = Atomic::load(&jt->om_in_use_count);
    int l_om_free_count = Atomic::load(&jt->om_free_count);
    out->print_cr(INTPTR_FORMAT "  %10d  %10d  %10d", p2i(jt),
                  l_om_in_use_count, l_om_free_count, jt->om_free_provision);
    pop_count += l_om_in_use_count + l_om_free_count;
  }
  return pop_count;
}

#ifndef PRODUCT

// Check if monitor belongs to the monitor cache
// The list is grow-only so it's *relatively* safe to traverse
// the list of extant blocks without taking a lock.

int ObjectSynchronizer::verify_objmon_isinpool(ObjectMonitor *monitor) {
  PaddedObjectMonitor* block = Atomic::load(&g_block_list);
  while (block != NULL) {
    assert(block->is_chainmarker(), "must be a block header");
    if (monitor > &block[0] && monitor < &block[_BLOCKSIZE]) {
      address mon = (address)monitor;
      address blk = (address)block;
      size_t diff = mon - blk;
      assert((diff % sizeof(PaddedObjectMonitor)) == 0, "must be aligned");
      return 1;
    }
    // unmarked_next() is not needed with g_block_list (no locking
    // used with block linkage _next_om fields).
    block = (PaddedObjectMonitor*)block->next_om();
  }
  return 0;
}

#endif
