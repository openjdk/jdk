/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/padded.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/basicLock.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/lightweightSynchronizer.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.inline.hpp"
#include "runtime/threads.hpp"
#include "runtime/timer.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/linkedlist.hpp"
#include "utilities/preserveException.hpp"

class ObjectMonitorDeflationLogging;

void MonitorList::add(ObjectMonitor* m) {
  ObjectMonitor* head;
  do {
    head = Atomic::load(&_head);
    m->set_next_om(head);
  } while (Atomic::cmpxchg(&_head, head, m) != head);

  size_t count = Atomic::add(&_count, 1u, memory_order_relaxed);
  size_t old_max;
  do {
    old_max = Atomic::load(&_max);
    if (count <= old_max) {
      break;
    }
  } while (Atomic::cmpxchg(&_max, old_max, count, memory_order_relaxed) != old_max);
}

size_t MonitorList::count() const {
  return Atomic::load(&_count);
}

size_t MonitorList::max() const {
  return Atomic::load(&_max);
}

class ObjectMonitorDeflationSafepointer : public StackObj {
  JavaThread* const                    _current;
  ObjectMonitorDeflationLogging* const _log;

public:
  ObjectMonitorDeflationSafepointer(JavaThread* current, ObjectMonitorDeflationLogging* log)
    : _current(current), _log(log) {}

  void block_for_safepoint(const char* op_name, const char* count_name, size_t counter);
};

// Walk the in-use list and unlink deflated ObjectMonitors.
// Returns the number of unlinked ObjectMonitors.
size_t MonitorList::unlink_deflated(size_t deflated_count,
                                    GrowableArray<ObjectMonitor*>* unlinked_list,
                                    ObjectMonitorDeflationSafepointer* safepointer) {
  size_t unlinked_count = 0;
  ObjectMonitor* prev = nullptr;
  ObjectMonitor* m = Atomic::load_acquire(&_head);

  while (m != nullptr) {
    if (m->is_being_async_deflated()) {
      // Find next live ObjectMonitor. Batch up the unlinkable monitors, so we can
      // modify the list once per batch. The batch starts at "m".
      size_t unlinked_batch = 0;
      ObjectMonitor* next = m;
      // Look for at most MonitorUnlinkBatch monitors, or the number of
      // deflated and not unlinked monitors, whatever comes first.
      assert(deflated_count >= unlinked_count, "Sanity: underflow");
      size_t unlinked_batch_limit = MIN2<size_t>(deflated_count - unlinked_count, MonitorUnlinkBatch);
      do {
        ObjectMonitor* next_next = next->next_om();
        unlinked_batch++;
        unlinked_list->append(next);
        next = next_next;
        if (unlinked_batch >= unlinked_batch_limit) {
          // Reached the max batch, so bail out of the gathering loop.
          break;
        }
        if (prev == nullptr && Atomic::load(&_head) != m) {
          // Current batch used to be at head, but it is not at head anymore.
          // Bail out and figure out where we currently are. This avoids long
          // walks searching for new prev during unlink under heavy list inserts.
          break;
        }
      } while (next != nullptr && next->is_being_async_deflated());

      // Unlink the found batch.
      if (prev == nullptr) {
        // The current batch is the first batch, so there is a chance that it starts at head.
        // Optimistically assume no inserts happened, and try to unlink the entire batch from the head.
        ObjectMonitor* prev_head = Atomic::cmpxchg(&_head, m, next);
        if (prev_head != m) {
          // Something must have updated the head. Figure out the actual prev for this batch.
          for (ObjectMonitor* n = prev_head; n != m; n = n->next_om()) {
            prev = n;
          }
          assert(prev != nullptr, "Should have found the prev for the current batch");
          prev->set_next_om(next);
        }
      } else {
        // The current batch is preceded by another batch. This guarantees the current batch
        // does not start at head. Unlink the entire current batch without updating the head.
        assert(Atomic::load(&_head) != m, "Sanity");
        prev->set_next_om(next);
      }

      unlinked_count += unlinked_batch;
      if (unlinked_count >= deflated_count) {
        // Reached the max so bail out of the searching loop.
        // There should be no more deflated monitors left.
        break;
      }
      m = next;
    } else {
      prev = m;
      m = m->next_om();
    }

    // Must check for a safepoint/handshake and honor it.
    safepointer->block_for_safepoint("unlinking", "unlinked_count", unlinked_count);
  }

#ifdef ASSERT
  // Invariant: the code above should unlink all deflated monitors.
  // The code that runs after this unlinking does not expect deflated monitors.
  // Notably, attempting to deflate the already deflated monitor would break.
  {
    ObjectMonitor* m = Atomic::load_acquire(&_head);
    while (m != nullptr) {
      assert(!m->is_being_async_deflated(), "All deflated monitors should be unlinked");
      m = m->next_om();
    }
  }
#endif

  Atomic::sub(&_count, unlinked_count);
  return unlinked_count;
}

MonitorList::Iterator MonitorList::iterator() const {
  return Iterator(Atomic::load_acquire(&_head));
}

ObjectMonitor* MonitorList::Iterator::next() {
  ObjectMonitor* current = _current;
  _current = current->next_om();
  return current;
}

// The "core" versions of monitor enter and exit reside in this file.
// The interpreter and compilers contain specialized transliterated
// variants of the enter-exit fast-path operations.  See c2_MacroAssembler_x86.cpp
// fast_lock(...) for instance.  If you make changes here, make sure to modify the
// interpreter, and both C1 and C2 fast-path inline locking code emission.
//
// -----------------------------------------------------------------------------

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available
// TODO-FIXME: probes should not fire when caller is _blocked.  assert() accordingly.

#define DTRACE_MONITOR_PROBE_COMMON(obj, thread)                           \
  char* bytes = nullptr;                                                      \
  int len = 0;                                                             \
  jlong jtid = SharedRuntime::get_java_tid(thread);                        \
  Symbol* klassname = obj->klass()->name();                                \
  if (klassname != nullptr) {                                                 \
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
static int dtrace_waited_probe(ObjectMonitor* monitor, Handle obj, JavaThread* thr) {
  DTRACE_MONITOR_PROBE(waited, monitor, obj(), thr);
  return 0;
}

static constexpr size_t inflation_lock_count() {
  return 256;
}

// Static storage for an array of PlatformMutex.
alignas(PlatformMutex) static uint8_t _inflation_locks[inflation_lock_count()][sizeof(PlatformMutex)];

static inline PlatformMutex* inflation_lock(size_t index) {
  return reinterpret_cast<PlatformMutex*>(_inflation_locks[index]);
}

void ObjectSynchronizer::initialize() {
  for (size_t i = 0; i < inflation_lock_count(); i++) {
    ::new(static_cast<void*>(inflation_lock(i))) PlatformMutex();
  }
  // Start the ceiling with the estimate for one thread.
  set_in_use_list_ceiling(AvgMonitorsPerThreadEstimate);

  // Start the timer for deflations, so it does not trigger immediately.
  _last_async_deflation_time_ns = os::javaTimeNanos();

  LightweightSynchronizer::initialize();
}

MonitorList ObjectSynchronizer::_in_use_list;
// monitors_used_above_threshold() policy is as follows:
//
// The ratio of the current _in_use_list count to the ceiling is used
// to determine if we are above MonitorUsedDeflationThreshold and need
// to do an async monitor deflation cycle. The ceiling is increased by
// AvgMonitorsPerThreadEstimate when a thread is added to the system
// and is decreased by AvgMonitorsPerThreadEstimate when a thread is
// removed from the system.
//
// Note: If the _in_use_list max exceeds the ceiling, then
// monitors_used_above_threshold() will use the in_use_list max instead
// of the thread count derived ceiling because we have used more
// ObjectMonitors than the estimated average.
//
// Note: If deflate_idle_monitors() has NoAsyncDeflationProgressMax
// no-progress async monitor deflation cycles in a row, then the ceiling
// is adjusted upwards by monitors_used_above_threshold().
//
// Start the ceiling with the estimate for one thread in initialize()
// which is called after cmd line options are processed.
static size_t _in_use_list_ceiling = 0;
bool volatile ObjectSynchronizer::_is_async_deflation_requested = false;
bool volatile ObjectSynchronizer::_is_final_audit = false;
jlong ObjectSynchronizer::_last_async_deflation_time_ns = 0;
static uintx _no_progress_cnt = 0;
static bool _no_progress_skip_increment = false;

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

bool ObjectSynchronizer::quick_notify(oopDesc* obj, JavaThread* current, bool all) {
  assert(current->thread_state() == _thread_in_Java, "invariant");
  NoSafepointVerifier nsv;
  if (obj == nullptr) return false;  // slow-path for invalid obj
  const markWord mark = obj->mark();

  if (mark.is_fast_locked() && current->lock_stack().contains(cast_to_oop(obj))) {
    // Degenerate notify
    // fast-locked by caller so by definition the implied waitset is empty.
    return true;
  }

  if (mark.has_monitor()) {
    ObjectMonitor* const mon = read_monitor(current, obj, mark);
    if (mon == nullptr) {
      // Racing with inflation/deflation go slow path
      return false;
    }
    assert(mon->object() == oop(obj), "invariant");
    if (!mon->has_owner(current)) return false;  // slow-path for IMS exception

    if (mon->first_waiter() != nullptr) {
      // We have one or more waiters. Since this is an inflated monitor
      // that we own, we quickly notify them here and now, avoiding the slow-path.
      if (all) {
        mon->quick_notifyAll(current);
      } else {
        mon->quick_notify(current);
      }
    }
    return true;
  }

  // other IMS exception states take the slow-path
  return false;
}

// Handle notifications when synchronizing on value based classes
void ObjectSynchronizer::handle_sync_on_value_based_class(Handle obj, JavaThread* locking_thread) {
  assert(locking_thread == Thread::current() || locking_thread->is_obj_deopt_suspend(), "must be");
  frame last_frame = locking_thread->last_frame();
  bool bcp_was_adjusted = false;
  // Don't decrement bcp if it points to the frame's first instruction.  This happens when
  // handle_sync_on_value_based_class() is called because of a synchronized method.  There
  // is no actual monitorenter instruction in the byte code in this case.
  if (last_frame.is_interpreted_frame() &&
      (last_frame.interpreter_frame_method()->code_base() < last_frame.interpreter_frame_bcp())) {
    // adjust bcp to point back to monitorenter so that we print the correct line numbers
    last_frame.interpreter_frame_set_bcp(last_frame.interpreter_frame_bcp() - 1);
    bcp_was_adjusted = true;
  }

  if (DiagnoseSyncOnValueBasedClasses == FATAL_EXIT) {
    ResourceMark rm;
    stringStream ss;
    locking_thread->print_active_stack_on(&ss);
    char* base = (char*)strstr(ss.base(), "at");
    char* newline = (char*)strchr(ss.base(), '\n');
    if (newline != nullptr) {
      *newline = '\0';
    }
    fatal("Synchronizing on object " INTPTR_FORMAT " of klass %s %s", p2i(obj()), obj->klass()->external_name(), base);
  } else {
    assert(DiagnoseSyncOnValueBasedClasses == LOG_WARNING, "invalid value for DiagnoseSyncOnValueBasedClasses");
    ResourceMark rm;
    Log(valuebasedclasses) vblog;

    vblog.info("Synchronizing on object " INTPTR_FORMAT " of klass %s", p2i(obj()), obj->klass()->external_name());
    if (locking_thread->has_last_Java_frame()) {
      LogStream info_stream(vblog.info());
      locking_thread->print_active_stack_on(&info_stream);
    } else {
      vblog.info("Cannot find the last Java frame");
    }

    EventSyncOnValueBasedClass event;
    if (event.should_commit()) {
      event.set_valueBasedClass(obj->klass());
      event.commit();
    }
  }

  if (bcp_was_adjusted) {
    last_frame.interpreter_frame_set_bcp(last_frame.interpreter_frame_bcp() + 1);
  }
}

// -----------------------------------------------------------------------------
// Monitor Enter/Exit

void ObjectSynchronizer::enter_for(Handle obj, BasicLock* lock, JavaThread* locking_thread) {
  // When called with locking_thread != Thread::current() some mechanism must synchronize
  // the locking_thread with respect to the current thread. Currently only used when
  // deoptimizing and re-locking locks. See Deoptimization::relock_objects
  assert(locking_thread == Thread::current() || locking_thread->is_obj_deopt_suspend(), "must be");
  return LightweightSynchronizer::enter_for(obj, lock, locking_thread);
}

// -----------------------------------------------------------------------------
// JNI locks on java objects
// NOTE: must use heavy weight monitor to handle jni monitor enter
void ObjectSynchronizer::jni_enter(Handle obj, JavaThread* current) {
  // Top native frames in the stack will not be seen if we attempt
  // preemption, since we start walking from the last Java anchor.
  NoPreemptMark npm(current);

  if (obj->klass()->is_value_based()) {
    handle_sync_on_value_based_class(obj, current);
  }

  // the current locking is from JNI instead of Java code
  current->set_current_pending_monitor_is_from_java(false);
  // An async deflation can race after the inflate() call and before
  // enter() can make the ObjectMonitor busy. enter() returns false if
  // we have lost the race to async deflation and we simply try again.
  while (true) {
    BasicLock lock;
    if (LightweightSynchronizer::inflate_and_enter(obj(), &lock, inflate_cause_jni_enter, current, current) != nullptr) {
      current->inc_held_monitor_count(1, true);
      break;
    }
  }
  current->set_current_pending_monitor_is_from_java(true);
}

// NOTE: must use heavy weight monitor to handle jni monitor exit
void ObjectSynchronizer::jni_exit(oop obj, TRAPS) {
  JavaThread* current = THREAD;

  ObjectMonitor* monitor;
  monitor = LightweightSynchronizer::inflate_locked_or_imse(obj, inflate_cause_jni_exit, CHECK);
  // If this thread has locked the object, exit the monitor. We
  // intentionally do not use CHECK on check_owner because we must exit the
  // monitor even if an exception was already pending.
  if (monitor->check_owner(THREAD)) {
    monitor->exit(current);
    current->dec_held_monitor_count(1, true);
  }
}

// -----------------------------------------------------------------------------
// Internal VM locks on java objects
// standard constructor, allows locking failures
ObjectLocker::ObjectLocker(Handle obj, JavaThread* thread) : _npm(thread) {
  _thread = thread;
  _thread->check_for_valid_safepoint_state();
  _obj = obj;

  if (_obj() != nullptr) {
    ObjectSynchronizer::enter(_obj, &_lock, _thread);
  }
}

ObjectLocker::~ObjectLocker() {
  if (_obj() != nullptr) {
    ObjectSynchronizer::exit(_obj(), &_lock, _thread);
  }
}


// -----------------------------------------------------------------------------
//  Wait/Notify/NotifyAll
// NOTE: must use heavy weight monitor to handle wait()

int ObjectSynchronizer::wait(Handle obj, jlong millis, TRAPS) {
  JavaThread* current = THREAD;
  if (millis < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }

  ObjectMonitor* monitor;
  monitor = LightweightSynchronizer::inflate_locked_or_imse(obj(), inflate_cause_wait, CHECK_0);

  DTRACE_MONITOR_WAIT_PROBE(monitor, obj(), current, millis);
  monitor->wait(millis, true, THREAD); // Not CHECK as we need following code

  // This dummy call is in place to get around dtrace bug 6254741.  Once
  // that's fixed we can uncomment the following line, remove the call
  // and change this function back into a "void" func.
  // DTRACE_MONITOR_PROBE(waited, monitor, obj(), THREAD);
  int ret_code = dtrace_waited_probe(monitor, obj, THREAD);
  return ret_code;
}

void ObjectSynchronizer::waitUninterruptibly(Handle obj, jlong millis, TRAPS) {
  if (millis < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "timeout value is negative");
  }

  ObjectMonitor* monitor;
  monitor = LightweightSynchronizer::inflate_locked_or_imse(obj(), inflate_cause_wait, CHECK);
  monitor->wait(millis, false, THREAD);
}


void ObjectSynchronizer::notify(Handle obj, TRAPS) {
  JavaThread* current = THREAD;

  markWord mark = obj->mark();
  if ((mark.is_fast_locked() && current->lock_stack().contains(obj()))) {
    // Not inflated so there can't be any waiters to notify.
    return;
  }
  ObjectMonitor* monitor = LightweightSynchronizer::inflate_locked_or_imse(obj(), inflate_cause_notify, CHECK);
  monitor->notify(CHECK);
}

// NOTE: see comment of notify()
void ObjectSynchronizer::notifyall(Handle obj, TRAPS) {
  JavaThread* current = THREAD;

  markWord mark = obj->mark();
  if ((mark.is_fast_locked() && current->lock_stack().contains(obj()))) {
    // Not inflated so there can't be any waiters to notify.
    return;
  }

  ObjectMonitor* monitor = LightweightSynchronizer::inflate_locked_or_imse(obj(), inflate_cause_notify, CHECK);
  monitor->notifyAll(CHECK);
}

// -----------------------------------------------------------------------------
// Hash Code handling

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

static intptr_t get_next_hash(Thread* current, oop obj) {
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
    unsigned t = current->_hashStateX;
    t ^= (t << 11);
    current->_hashStateX = current->_hashStateY;
    current->_hashStateY = current->_hashStateZ;
    current->_hashStateZ = current->_hashStateW;
    unsigned v = current->_hashStateW;
    v = (v ^ (v >> 19)) ^ (t ^ (t >> 8));
    current->_hashStateW = v;
    value = v;
  }

  value &= markWord::hash_mask;
  if (value == 0) value = 0xBAD;
  assert(value != markWord::no_hash, "invariant");
  return value;
}

static intptr_t install_hash_code(Thread* current, oop obj) {
  assert(UseObjectMonitorTable, "must be");

  markWord mark = obj->mark_acquire();
  for (;;) {
    intptr_t hash = mark.hash();
    if (hash != 0) {
      return hash;
    }

    hash = get_next_hash(current, obj);
    const markWord old_mark = mark;
    const markWord new_mark = old_mark.copy_set_hash(hash);

    mark = obj->cas_set_mark(new_mark, old_mark);
    if (old_mark == mark) {
      return hash;
    }
  }
}

intptr_t ObjectSynchronizer::FastHashCode(Thread* current, oop obj) {
  if (UseObjectMonitorTable) {
    // Since the monitor isn't in the object header, the hash can simply be
    // installed in the object header.
    return install_hash_code(current, obj);
  }

  while (true) {
    ObjectMonitor* monitor = nullptr;
    markWord temp, test;
    intptr_t hash;
    markWord mark = obj->mark_acquire();
    if (mark.is_unlocked() || mark.is_fast_locked()) {
      hash = mark.hash();
      if (hash != 0) {                     // if it has a hash, just return it
        return hash;
      }
      hash = get_next_hash(current, obj);  // get a new hash
      temp = mark.copy_set_hash(hash);     // merge the hash into header
                                           // try to install the hash
      test = obj->cas_set_mark(temp, mark);
      if (test == mark) {                  // if the hash was installed, return it
        return hash;
      }
      // CAS failed, retry
      continue;

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
    }

    // NOTE: an async deflation can race after we get the monitor and
    // before we can update the ObjectMonitor's header with the hash
    // value below.
    assert(mark.has_monitor(), "must be");
    monitor = mark.monitor();

    // Load ObjectMonitor's header/dmw field and see if it has a hash.
    mark = monitor->header();
    assert(mark.is_neutral(), "invariant: header=" INTPTR_FORMAT, mark.value());
    hash = mark.hash();
    if (hash == 0) {                       // if it does not have a hash
      hash = get_next_hash(current, obj);  // get a new hash
      temp = mark.copy_set_hash(hash)   ;  // merge the hash into header
      assert(temp.is_neutral(), "invariant: header=" INTPTR_FORMAT, temp.value());
      uintptr_t v = Atomic::cmpxchg(monitor->metadata_addr(), mark.value(), temp.value());
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
      if (monitor->is_being_async_deflated() && !UseObjectMonitorTable) {
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

bool ObjectSynchronizer::current_thread_holds_lock(JavaThread* current,
                                                   Handle h_obj) {
  assert(current == JavaThread::current(), "Can only be called on current thread");
  oop obj = h_obj();

  markWord mark = obj->mark_acquire();

  if (mark.is_fast_locked()) {
    // fast-locking case, see if lock is in current's lock stack
    return current->lock_stack().contains(h_obj());
  }

  while (mark.has_monitor()) {
    ObjectMonitor* monitor = read_monitor(current, obj, mark);
    if (monitor != nullptr) {
      return monitor->is_entered(current) != 0;
    }
    // Racing with inflation/deflation, retry
    mark = obj->mark_acquire();

    if (mark.is_fast_locked()) {
      // Some other thread fast_locked, current could not have held the lock
      return false;
    }
  }

  // Unlocked case, header in place
  assert(mark.is_unlocked(), "sanity check");
  return false;
}

JavaThread* ObjectSynchronizer::get_lock_owner(ThreadsList * t_list, Handle h_obj) {
  oop obj = h_obj();
  markWord mark = obj->mark_acquire();

  if (mark.is_fast_locked()) {
    // fast-locked so get owner from the object.
    // owning_thread_from_object() may also return null here:
    return Threads::owning_thread_from_object(t_list, h_obj());
  }

  while (mark.has_monitor()) {
    ObjectMonitor* monitor = read_monitor(Thread::current(), obj, mark);
    if (monitor != nullptr) {
      return Threads::owning_thread_from_monitor(t_list, monitor);
    }
    // Racing with inflation/deflation, retry
    mark = obj->mark_acquire();

    if (mark.is_fast_locked()) {
      // Some other thread fast_locked
      return Threads::owning_thread_from_object(t_list, h_obj());
    }
  }

  // Unlocked case, header in place
  // Cannot have assertion since this object may have been
  // locked by another thread when reaching here.
  // assert(mark.is_unlocked(), "sanity check");

  return nullptr;
}

// Visitors ...

// Iterate over all ObjectMonitors.
template <typename Function>
void ObjectSynchronizer::monitors_iterate(Function function) {
  MonitorList::Iterator iter = _in_use_list.iterator();
  while (iter.has_next()) {
    ObjectMonitor* monitor = iter.next();
    function(monitor);
  }
}

// Iterate ObjectMonitors owned by any thread and where the owner `filter`
// returns true.
template <typename OwnerFilter>
void ObjectSynchronizer::owned_monitors_iterate_filtered(MonitorClosure* closure, OwnerFilter filter) {
  monitors_iterate([&](ObjectMonitor* monitor) {
    // This function is only called at a safepoint or when the
    // target thread is suspended or when the target thread is
    // operating on itself. The current closures in use today are
    // only interested in an owned ObjectMonitor and ownership
    // cannot be dropped under the calling contexts so the
    // ObjectMonitor cannot be async deflated.
    if (monitor->has_owner() && filter(monitor)) {
      assert(!monitor->is_being_async_deflated(), "Owned monitors should not be deflating");

      closure->do_monitor(monitor);
    }
  });
}

// Iterate ObjectMonitors where the owner == thread; this does NOT include
// ObjectMonitors where owner is set to a stack-lock address in thread.
void ObjectSynchronizer::owned_monitors_iterate(MonitorClosure* closure, JavaThread* thread) {
  int64_t key = ObjectMonitor::owner_id_from(thread);
  auto thread_filter = [&](ObjectMonitor* monitor) { return monitor->owner() == key; };
  return owned_monitors_iterate_filtered(closure, thread_filter);
}

void ObjectSynchronizer::owned_monitors_iterate(MonitorClosure* closure, oop vthread) {
  int64_t key = ObjectMonitor::owner_id_from(vthread);
  auto thread_filter = [&](ObjectMonitor* monitor) { return monitor->owner() == key; };
  return owned_monitors_iterate_filtered(closure, thread_filter);
}

// Iterate ObjectMonitors owned by any thread.
void ObjectSynchronizer::owned_monitors_iterate(MonitorClosure* closure) {
  auto all_filter = [&](ObjectMonitor* monitor) { return true; };
  return owned_monitors_iterate_filtered(closure, all_filter);
}

static bool monitors_used_above_threshold(MonitorList* list) {
  if (MonitorUsedDeflationThreshold == 0) {  // disabled case is easy
    return false;
  }
  size_t monitors_used = list->count();
  if (monitors_used == 0) {  // empty list is easy
    return false;
  }
  size_t old_ceiling = ObjectSynchronizer::in_use_list_ceiling();
  // Make sure that we use a ceiling value that is not lower than
  // previous, not lower than the recorded max used by the system, and
  // not lower than the current number of monitors in use (which can
  // race ahead of max). The result is guaranteed > 0.
  size_t ceiling = MAX3(old_ceiling, list->max(), monitors_used);

  // Check if our monitor usage is above the threshold:
  size_t monitor_usage = (monitors_used * 100LL) / ceiling;
  if (int(monitor_usage) > MonitorUsedDeflationThreshold) {
    // Deflate monitors if over the threshold percentage, unless no
    // progress on previous deflations.
    bool is_above_threshold = true;

    // Check if it's time to adjust the in_use_list_ceiling up, due
    // to too many async deflation attempts without any progress.
    if (NoAsyncDeflationProgressMax != 0 &&
        _no_progress_cnt >= NoAsyncDeflationProgressMax) {
      double remainder = (100.0 - MonitorUsedDeflationThreshold) / 100.0;
      size_t delta = (size_t)(ceiling * remainder) + 1;
      size_t new_ceiling = (ceiling > SIZE_MAX - delta)
        ? SIZE_MAX         // Overflow, let's clamp new_ceiling.
        : ceiling + delta;

      ObjectSynchronizer::set_in_use_list_ceiling(new_ceiling);
      log_info(monitorinflation)("Too many deflations without progress; "
                                 "bumping in_use_list_ceiling from %zu"
                                 " to %zu", old_ceiling, new_ceiling);
      _no_progress_cnt = 0;
      ceiling = new_ceiling;

      // Check if our monitor usage is still above the threshold:
      monitor_usage = (monitors_used * 100LL) / ceiling;
      is_above_threshold = int(monitor_usage) > MonitorUsedDeflationThreshold;
    }
    log_info(monitorinflation)("monitors_used=%zu, ceiling=%zu"
                               ", monitor_usage=%zu, threshold=%d",
                               monitors_used, ceiling, monitor_usage, MonitorUsedDeflationThreshold);
    return is_above_threshold;
  }

  return false;
}

size_t ObjectSynchronizer::in_use_list_count() {
  return _in_use_list.count();
}

size_t ObjectSynchronizer::in_use_list_max() {
  return _in_use_list.max();
}

size_t ObjectSynchronizer::in_use_list_ceiling() {
  return _in_use_list_ceiling;
}

void ObjectSynchronizer::dec_in_use_list_ceiling() {
  Atomic::sub(&_in_use_list_ceiling, AvgMonitorsPerThreadEstimate);
}

void ObjectSynchronizer::inc_in_use_list_ceiling() {
  Atomic::add(&_in_use_list_ceiling, AvgMonitorsPerThreadEstimate);
}

void ObjectSynchronizer::set_in_use_list_ceiling(size_t new_value) {
  _in_use_list_ceiling = new_value;
}

bool ObjectSynchronizer::is_async_deflation_needed() {
  if (is_async_deflation_requested()) {
    // Async deflation request.
    log_info(monitorinflation)("Async deflation needed: explicit request");
    return true;
  }

  jlong time_since_last = time_since_last_async_deflation_ms();

  if (AsyncDeflationInterval > 0 &&
      time_since_last > AsyncDeflationInterval &&
      monitors_used_above_threshold(&_in_use_list)) {
    // It's been longer than our specified deflate interval and there
    // are too many monitors in use. We don't deflate more frequently
    // than AsyncDeflationInterval (unless is_async_deflation_requested)
    // in order to not swamp the MonitorDeflationThread.
    log_info(monitorinflation)("Async deflation needed: monitors used are above the threshold");
    return true;
  }

  if (GuaranteedAsyncDeflationInterval > 0 &&
      time_since_last > GuaranteedAsyncDeflationInterval) {
    // It's been longer than our specified guaranteed deflate interval.
    // We need to clean up the used monitors even if the threshold is
    // not reached, to keep the memory utilization at bay when many threads
    // touched many monitors.
    log_info(monitorinflation)("Async deflation needed: guaranteed interval (%zd ms) "
                               "is greater than time since last deflation (" JLONG_FORMAT " ms)",
                               GuaranteedAsyncDeflationInterval, time_since_last);

    // If this deflation has no progress, then it should not affect the no-progress
    // tracking, otherwise threshold heuristics would think it was triggered, experienced
    // no progress, and needs to backoff more aggressively. In this "no progress" case,
    // the generic code would bump the no-progress counter, and we compensate for that
    // by telling it to skip the update.
    //
    // If this deflation has progress, then it should let non-progress tracking
    // know about this, otherwise the threshold heuristics would kick in, potentially
    // experience no-progress due to aggressive cleanup by this deflation, and think
    // it is still in no-progress stride. In this "progress" case, the generic code would
    // zero the counter, and we allow it to happen.
    _no_progress_skip_increment = true;

    return true;
  }

  return false;
}

void ObjectSynchronizer::request_deflate_idle_monitors() {
  MonitorLocker ml(MonitorDeflation_lock, Mutex::_no_safepoint_check_flag);
  set_is_async_deflation_requested(true);
  ml.notify_all();
}

bool ObjectSynchronizer::request_deflate_idle_monitors_from_wb() {
  JavaThread* current = JavaThread::current();
  bool ret_code = false;

  jlong last_time = last_async_deflation_time_ns();

  request_deflate_idle_monitors();

  const int N_CHECKS = 5;
  for (int i = 0; i < N_CHECKS; i++) {  // sleep for at most 5 seconds
    if (last_async_deflation_time_ns() > last_time) {
      log_info(monitorinflation)("Async Deflation happened after %d check(s).", i);
      ret_code = true;
      break;
    }
    {
      // JavaThread has to honor the blocking protocol.
      ThreadBlockInVM tbivm(current);
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

// Walk the in-use list and deflate (at most MonitorDeflationMax) idle
// ObjectMonitors. Returns the number of deflated ObjectMonitors.
//
size_t ObjectSynchronizer::deflate_monitor_list(ObjectMonitorDeflationSafepointer* safepointer) {
  MonitorList::Iterator iter = _in_use_list.iterator();
  size_t deflated_count = 0;
  Thread* current = Thread::current();

  while (iter.has_next()) {
    if (deflated_count >= (size_t)MonitorDeflationMax) {
      break;
    }
    ObjectMonitor* mid = iter.next();
    if (mid->deflate_monitor(current)) {
      deflated_count++;
    }

    // Must check for a safepoint/handshake and honor it.
    safepointer->block_for_safepoint("deflation", "deflated_count", deflated_count);
  }

  return deflated_count;
}

class DeflationHandshakeClosure : public HandshakeClosure {
 public:
  DeflationHandshakeClosure() : HandshakeClosure("DeflationHandshakeClosure") {}

  void do_thread(Thread* thread) {
    log_trace(monitorinflation)("DeflationHandshakeClosure::do_thread: thread="
                                INTPTR_FORMAT, p2i(thread));
    if (thread->is_Java_thread()) {
      // Clear OM cache
      JavaThread* jt = JavaThread::cast(thread);
      jt->om_clear_monitor_cache();
    }
  }
};

class VM_RendezvousGCThreads : public VM_Operation {
public:
  bool evaluate_at_safepoint() const override { return false; }
  VMOp_Type type() const override { return VMOp_RendezvousGCThreads; }
  void doit() override {
    Universe::heap()->safepoint_synchronize_begin();
    Universe::heap()->safepoint_synchronize_end();
  };
};

static size_t delete_monitors(GrowableArray<ObjectMonitor*>* delete_list,
                              ObjectMonitorDeflationSafepointer* safepointer) {
  NativeHeapTrimmer::SuspendMark sm("monitor deletion");
  size_t deleted_count = 0;
  for (ObjectMonitor* monitor: *delete_list) {
    delete monitor;
    deleted_count++;
    // A JavaThread must check for a safepoint/handshake and honor it.
    safepointer->block_for_safepoint("deletion", "deleted_count", deleted_count);
  }
  return deleted_count;
}

class ObjectMonitorDeflationLogging: public StackObj {
  LogStreamHandle(Debug, monitorinflation) _debug;
  LogStreamHandle(Info, monitorinflation)  _info;
  LogStream*                               _stream;
  elapsedTimer                             _timer;

  size_t ceiling() const { return ObjectSynchronizer::in_use_list_ceiling(); }
  size_t count() const   { return ObjectSynchronizer::in_use_list_count(); }
  size_t max() const     { return ObjectSynchronizer::in_use_list_max(); }

public:
  ObjectMonitorDeflationLogging()
    : _debug(), _info(), _stream(nullptr) {
    if (_debug.is_enabled()) {
      _stream = &_debug;
    } else if (_info.is_enabled()) {
      _stream = &_info;
    }
  }

  void begin() {
    if (_stream != nullptr) {
      _stream->print_cr("begin deflating: in_use_list stats: ceiling=%zu, count=%zu, max=%zu",
                        ceiling(), count(), max());
      _timer.start();
    }
  }

  void before_handshake(size_t unlinked_count) {
    if (_stream != nullptr) {
      _timer.stop();
      _stream->print_cr("before handshaking: unlinked_count=%zu"
                        ", in_use_list stats: ceiling=%zu, count="
                        "%zu, max=%zu",
                        unlinked_count, ceiling(), count(), max());
    }
  }

  void after_handshake() {
    if (_stream != nullptr) {
      _stream->print_cr("after handshaking: in_use_list stats: ceiling="
                        "%zu, count=%zu, max=%zu",
                        ceiling(), count(), max());
      _timer.start();
    }
  }

  void end(size_t deflated_count, size_t unlinked_count) {
    if (_stream != nullptr) {
      _timer.stop();
      if (deflated_count != 0 || unlinked_count != 0 || _debug.is_enabled()) {
        _stream->print_cr("deflated_count=%zu, {unlinked,deleted}_count=%zu monitors in %3.7f secs",
                          deflated_count, unlinked_count, _timer.seconds());
      }
      _stream->print_cr("end deflating: in_use_list stats: ceiling=%zu, count=%zu, max=%zu",
                        ceiling(), count(), max());
    }
  }

  void before_block_for_safepoint(const char* op_name, const char* cnt_name, size_t cnt) {
    if (_stream != nullptr) {
      _timer.stop();
      _stream->print_cr("pausing %s: %s=%zu, in_use_list stats: ceiling="
                        "%zu, count=%zu, max=%zu",
                        op_name, cnt_name, cnt, ceiling(), count(), max());
    }
  }

  void after_block_for_safepoint(const char* op_name) {
    if (_stream != nullptr) {
      _stream->print_cr("resuming %s: in_use_list stats: ceiling=%zu"
                        ", count=%zu, max=%zu", op_name,
                        ceiling(), count(), max());
      _timer.start();
    }
  }
};

void ObjectMonitorDeflationSafepointer::block_for_safepoint(const char* op_name, const char* count_name, size_t counter) {
  if (!SafepointMechanism::should_process(_current)) {
    return;
  }

  // A safepoint/handshake has started.
  _log->before_block_for_safepoint(op_name, count_name, counter);

  {
    // Honor block request.
    ThreadBlockInVM tbivm(_current);
  }

  _log->after_block_for_safepoint(op_name);
}

// This function is called by the MonitorDeflationThread to deflate
// ObjectMonitors.
size_t ObjectSynchronizer::deflate_idle_monitors() {
  JavaThread* current = JavaThread::current();
  assert(current->is_monitor_deflation_thread(), "The only monitor deflater");

  // The async deflation request has been processed.
  _last_async_deflation_time_ns = os::javaTimeNanos();
  set_is_async_deflation_requested(false);

  ObjectMonitorDeflationLogging log;
  ObjectMonitorDeflationSafepointer safepointer(current, &log);

  log.begin();

  // Deflate some idle ObjectMonitors.
  size_t deflated_count = deflate_monitor_list(&safepointer);

  // Unlink the deflated ObjectMonitors from the in-use list.
  size_t unlinked_count = 0;
  size_t deleted_count = 0;
  if (deflated_count > 0) {
    ResourceMark rm(current);
    GrowableArray<ObjectMonitor*> delete_list((int)deflated_count);
    unlinked_count = _in_use_list.unlink_deflated(deflated_count, &delete_list, &safepointer);

#ifdef ASSERT
    if (UseObjectMonitorTable) {
      for (ObjectMonitor* monitor : delete_list) {
        assert(!LightweightSynchronizer::contains_monitor(current, monitor), "Should have been removed");
      }
    }
#endif

    log.before_handshake(unlinked_count);

    // A JavaThread needs to handshake in order to safely free the
    // ObjectMonitors that were deflated in this cycle.
    DeflationHandshakeClosure dhc;
    Handshake::execute(&dhc);
    // Also, we sync and desync GC threads around the handshake, so that they can
    // safely read the mark-word and look-through to the object-monitor, without
    // being afraid that the object-monitor is going away.
    VM_RendezvousGCThreads sync_gc;
    VMThread::execute(&sync_gc);

    log.after_handshake();

    // After the handshake, safely free the ObjectMonitors that were
    // deflated and unlinked in this cycle.

    // Delete the unlinked ObjectMonitors.
    deleted_count = delete_monitors(&delete_list, &safepointer);
    assert(unlinked_count == deleted_count, "must be");
  }

  log.end(deflated_count, unlinked_count);

  GVars.stw_random = os::random();

  if (deflated_count != 0) {
    _no_progress_cnt = 0;
  } else if (_no_progress_skip_increment) {
    _no_progress_skip_increment = false;
  } else {
    _no_progress_cnt++;
  }

  return deflated_count;
}

// Monitor cleanup on JavaThread::exit

// Iterate through monitor cache and attempt to release thread's monitors
class ReleaseJavaMonitorsClosure: public MonitorClosure {
 private:
  JavaThread* _thread;

 public:
  ReleaseJavaMonitorsClosure(JavaThread* thread) : _thread(thread) {}
  void do_monitor(ObjectMonitor* mid) {
    intx rec = mid->complete_exit(_thread);
    _thread->dec_held_monitor_count(rec + 1);
  }
};

// Release all inflated monitors owned by current thread.  Lightweight monitors are
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

void ObjectSynchronizer::release_monitors_owned_by_thread(JavaThread* current) {
  assert(current == JavaThread::current(), "must be current Java thread");
  NoSafepointVerifier nsv;
  ReleaseJavaMonitorsClosure rjmc(current);
  ObjectSynchronizer::owned_monitors_iterate(&rjmc, current);
  assert(!current->has_pending_exception(), "Should not be possible");
  current->clear_pending_exception();
  assert(current->held_monitor_count() == 0, "Should not be possible");
  // All monitors (including entered via JNI) have been unlocked above, so we need to clear jni count.
  current->clear_jni_monitor_count();
}

const char* ObjectSynchronizer::inflate_cause_name(const InflateCause cause) {
  switch (cause) {
    case inflate_cause_vm_internal:    return "VM Internal";
    case inflate_cause_monitor_enter:  return "Monitor Enter";
    case inflate_cause_wait:           return "Monitor Wait";
    case inflate_cause_notify:         return "Monitor Notify";
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

// Do the final audit and print of ObjectMonitor stats; must be done
// by the VMThread at VM exit time.
void ObjectSynchronizer::do_final_audit_and_print_stats() {
  assert(Thread::current()->is_VM_thread(), "sanity check");

  if (is_final_audit()) {  // Only do the audit once.
    return;
  }
  set_is_final_audit();
  log_info(monitorinflation)("Starting the final audit.");

  if (log_is_enabled(Info, monitorinflation)) {
    LogStreamHandle(Info, monitorinflation) ls;
    audit_and_print_stats(&ls, true /* on_exit */);
  }
}

// This function can be called by the MonitorDeflationThread or it can be called when
// we are trying to exit the VM. The list walker functions can run in parallel with
// the other list operations.
// Calls to this function can be added in various places as a debugging
// aid.
//
void ObjectSynchronizer::audit_and_print_stats(outputStream* ls, bool on_exit) {
  int error_cnt = 0;

  ls->print_cr("Checking in_use_list:");
  chk_in_use_list(ls, &error_cnt);

  if (error_cnt == 0) {
    ls->print_cr("No errors found in in_use_list checks.");
  } else {
    log_error(monitorinflation)("found in_use_list errors: error_cnt=%d", error_cnt);
  }

  // When exiting, only log the interesting entries at the Info level.
  // When called at intervals by the MonitorDeflationThread, log output
  // at the Trace level since there can be a lot of it.
  if (!on_exit && log_is_enabled(Trace, monitorinflation)) {
    LogStreamHandle(Trace, monitorinflation) ls_tr;
    log_in_use_monitor_details(&ls_tr, true /* log_all */);
  } else if (on_exit) {
    log_in_use_monitor_details(ls, false /* log_all */);
  }

  ls->flush();

  guarantee(error_cnt == 0, "ERROR: found monitor list errors: error_cnt=%d", error_cnt);
}

// Check the in_use_list; log the results of the checks.
void ObjectSynchronizer::chk_in_use_list(outputStream* out, int *error_cnt_p) {
  size_t l_in_use_count = _in_use_list.count();
  size_t l_in_use_max = _in_use_list.max();
  out->print_cr("count=%zu, max=%zu", l_in_use_count,
                l_in_use_max);

  size_t ck_in_use_count = 0;
  MonitorList::Iterator iter = _in_use_list.iterator();
  while (iter.has_next()) {
    ObjectMonitor* mid = iter.next();
    chk_in_use_entry(mid, out, error_cnt_p);
    ck_in_use_count++;
  }

  if (l_in_use_count == ck_in_use_count) {
    out->print_cr("in_use_count=%zu equals ck_in_use_count=%zu",
                  l_in_use_count, ck_in_use_count);
  } else {
    out->print_cr("WARNING: in_use_count=%zu is not equal to "
                  "ck_in_use_count=%zu", l_in_use_count,
                  ck_in_use_count);
  }

  size_t ck_in_use_max = _in_use_list.max();
  if (l_in_use_max == ck_in_use_max) {
    out->print_cr("in_use_max=%zu equals ck_in_use_max=%zu",
                  l_in_use_max, ck_in_use_max);
  } else {
    out->print_cr("WARNING: in_use_max=%zu is not equal to "
                  "ck_in_use_max=%zu", l_in_use_max, ck_in_use_max);
  }
}

// Check an in-use monitor entry; log any errors.
void ObjectSynchronizer::chk_in_use_entry(ObjectMonitor* n, outputStream* out,
                                          int* error_cnt_p) {
  if (n->owner_is_DEFLATER_MARKER()) {
    // This could happen when monitor deflation blocks for a safepoint.
    return;
  }


  if (n->metadata() == 0) {
    out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use monitor must "
                  "have non-null _metadata (header/hash) field.", p2i(n));
    *error_cnt_p = *error_cnt_p + 1;
  }

  const oop obj = n->object_peek();
  if (obj == nullptr) {
    return;
  }

  const markWord mark = obj->mark();
  if (!mark.has_monitor()) {
    out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use monitor's "
                  "object does not think it has a monitor: obj="
                  INTPTR_FORMAT ", mark=" INTPTR_FORMAT, p2i(n),
                  p2i(obj), mark.value());
    *error_cnt_p = *error_cnt_p + 1;
    return;
  }

  ObjectMonitor* const obj_mon = read_monitor(Thread::current(), obj, mark);
  if (n != obj_mon) {
    out->print_cr("ERROR: monitor=" INTPTR_FORMAT ": in-use monitor's "
                  "object does not refer to the same monitor: obj="
                  INTPTR_FORMAT ", mark=" INTPTR_FORMAT ", obj_mon="
                  INTPTR_FORMAT, p2i(n), p2i(obj), mark.value(), p2i(obj_mon));
    *error_cnt_p = *error_cnt_p + 1;
  }
}

// Log details about ObjectMonitors on the in_use_list. The 'BHL'
// flags indicate why the entry is in-use, 'object' and 'object type'
// indicate the associated object and its type.
void ObjectSynchronizer::log_in_use_monitor_details(outputStream* out, bool log_all) {
  if (_in_use_list.count() > 0) {
    stringStream ss;
    out->print_cr("In-use monitor info%s:", log_all ? "" : " (eliding idle monitors)");
    out->print_cr("(B -> is_busy, H -> has hash code, L -> lock status)");
    out->print_cr("%18s  %s  %18s  %18s",
                  "monitor", "BHL", "object", "object type");
    out->print_cr("==================  ===  ==================  ==================");

    auto is_interesting = [&](ObjectMonitor* monitor) {
      return log_all || monitor->has_owner() || monitor->is_busy();
    };

    monitors_iterate([&](ObjectMonitor* monitor) {
      if (is_interesting(monitor)) {
        const oop obj = monitor->object_peek();
        const intptr_t hash = UseObjectMonitorTable ? monitor->hash() : monitor->header().hash();
        ResourceMark rm;
        out->print(INTPTR_FORMAT "  %d%d%d  " INTPTR_FORMAT "  %s", p2i(monitor),
                   monitor->is_busy(), hash != 0, monitor->has_owner(),
                   p2i(obj), obj == nullptr ? "" : obj->klass()->external_name());
        if (monitor->is_busy()) {
          out->print(" (%s)", monitor->is_busy_to_string(&ss));
          ss.reset();
        }
        out->cr();
      }
    });
  }

  out->flush();
}
