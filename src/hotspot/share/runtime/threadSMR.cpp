/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/vm_operations.hpp"
#include "services/threadService.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/resourceHash.hpp"
#include "utilities/vmError.hpp"

Monitor*              ThreadsSMRSupport::_delete_lock =
                          new Monitor(Monitor::special, "Thread_SMR_delete_lock",
                                      false /* allow_vm_block */,
                                      Monitor::_safepoint_check_never);
// The '_cnt', '_max' and '_times" fields are enabled via
// -XX:+EnableThreadSMRStatistics:

// # of parallel threads in _delete_lock->wait().
// Impl note: Hard to imagine > 64K waiting threads so this could be 16-bit,
// but there is no nice 16-bit _FORMAT support.
uint                  ThreadsSMRSupport::_delete_lock_wait_cnt = 0;

// Max # of parallel threads in _delete_lock->wait().
// Impl note: See _delete_lock_wait_cnt note.
uint                  ThreadsSMRSupport::_delete_lock_wait_max = 0;

// Flag to indicate when an _delete_lock->notify() is needed.
// Impl note: See _delete_lock_wait_cnt note.
volatile uint         ThreadsSMRSupport::_delete_notify = 0;

// # of threads deleted over VM lifetime.
// Impl note: Atomically incremented over VM lifetime so use unsigned for more
// range. Unsigned 64-bit would be more future proof, but 64-bit atomic inc
// isn't available everywhere (or is it?).
volatile uint         ThreadsSMRSupport::_deleted_thread_cnt = 0;

// Max time in millis to delete a thread.
// Impl note: 16-bit might be too small on an overloaded machine. Use
// unsigned since this is a time value. Set via Atomic::cmpxchg() in a
// loop for correctness.
volatile uint         ThreadsSMRSupport::_deleted_thread_time_max = 0;

// Cumulative time in millis to delete threads.
// Impl note: Atomically added to over VM lifetime so use unsigned for more
// range. Unsigned 64-bit would be more future proof, but 64-bit atomic inc
// isn't available everywhere (or is it?).
volatile uint         ThreadsSMRSupport::_deleted_thread_times = 0;

ThreadsList* volatile ThreadsSMRSupport::_java_thread_list = new ThreadsList(0);

// # of ThreadsLists allocated over VM lifetime.
// Impl note: We allocate a new ThreadsList for every thread create and
// every thread delete so we need a bigger type than the
// _deleted_thread_cnt field.
uint64_t              ThreadsSMRSupport::_java_thread_list_alloc_cnt = 1;

// # of ThreadsLists freed over VM lifetime.
// Impl note: See _java_thread_list_alloc_cnt note.
uint64_t              ThreadsSMRSupport::_java_thread_list_free_cnt = 0;

// Max size ThreadsList allocated.
// Impl note: Max # of threads alive at one time should fit in unsigned 32-bit.
uint                  ThreadsSMRSupport::_java_thread_list_max = 0;

// Max # of nested ThreadsLists for a thread.
// Impl note: Hard to imagine > 64K nested ThreadsLists so this could be
// 16-bit, but there is no nice 16-bit _FORMAT support.
uint                  ThreadsSMRSupport::_nested_thread_list_max = 0;

// # of ThreadsListHandles deleted over VM lifetime.
// Impl note: Atomically incremented over VM lifetime so use unsigned for
// more range. There will be fewer ThreadsListHandles than threads so
// unsigned 32-bit should be fine.
volatile uint         ThreadsSMRSupport::_tlh_cnt = 0;

// Max time in millis to delete a ThreadsListHandle.
// Impl note: 16-bit might be too small on an overloaded machine. Use
// unsigned since this is a time value. Set via Atomic::cmpxchg() in a
// loop for correctness.
volatile uint         ThreadsSMRSupport::_tlh_time_max = 0;

// Cumulative time in millis to delete ThreadsListHandles.
// Impl note: Atomically added to over VM lifetime so use unsigned for more
// range. Unsigned 64-bit would be more future proof, but 64-bit atomic inc
// isn't available everywhere (or is it?).
volatile uint         ThreadsSMRSupport::_tlh_times = 0;

ThreadsList*          ThreadsSMRSupport::_to_delete_list = NULL;

// # of parallel ThreadsLists on the to-delete list.
// Impl note: Hard to imagine > 64K ThreadsLists needing to be deleted so
// this could be 16-bit, but there is no nice 16-bit _FORMAT support.
uint                  ThreadsSMRSupport::_to_delete_list_cnt = 0;

// Max # of parallel ThreadsLists on the to-delete list.
// Impl note: See _to_delete_list_cnt note.
uint                  ThreadsSMRSupport::_to_delete_list_max = 0;


// 'inline' functions first so the definitions are before first use:

inline void ThreadsSMRSupport::add_deleted_thread_times(uint add_value) {
  Atomic::add(add_value, &_deleted_thread_times);
}

inline void ThreadsSMRSupport::inc_deleted_thread_cnt() {
  Atomic::inc(&_deleted_thread_cnt);
}

inline void ThreadsSMRSupport::inc_java_thread_list_alloc_cnt() {
  _java_thread_list_alloc_cnt++;
}

inline void ThreadsSMRSupport::update_deleted_thread_time_max(uint new_value) {
  while (true) {
    uint cur_value = _deleted_thread_time_max;
    if (new_value <= cur_value) {
      // No need to update max value so we're done.
      break;
    }
    if (Atomic::cmpxchg(new_value, &_deleted_thread_time_max, cur_value) == cur_value) {
      // Updated max value so we're done. Otherwise try it all again.
      break;
    }
  }
}

inline void ThreadsSMRSupport::update_java_thread_list_max(uint new_value) {
  if (new_value > _java_thread_list_max) {
    _java_thread_list_max = new_value;
  }
}

inline ThreadsList* ThreadsSMRSupport::xchg_java_thread_list(ThreadsList* new_list) {
  return (ThreadsList*)Atomic::xchg(new_list, &_java_thread_list);
}


// Hash table of pointers found by a scan. Used for collecting hazard
// pointers (ThreadsList references). Also used for collecting JavaThreads
// that are indirectly referenced by hazard ptrs. An instance of this
// class only contains one type of pointer.
//
class ThreadScanHashtable : public CHeapObj<mtThread> {
 private:
  static bool ptr_equals(void * const& s1, void * const& s2) {
    return s1 == s2;
  }

  static unsigned int ptr_hash(void * const& s1) {
    // 2654435761 = 2^32 * Phi (golden ratio)
    return (unsigned int)(((uint32_t)(uintptr_t)s1) * 2654435761u);
  }

  int _table_size;
  // ResourceHashtable SIZE is specified at compile time so our
  // dynamic _table_size is unused for now; 1031 is the first prime
  // after 1024.
  typedef ResourceHashtable<void *, int, &ThreadScanHashtable::ptr_hash,
                            &ThreadScanHashtable::ptr_equals, 1031,
                            ResourceObj::C_HEAP, mtThread> PtrTable;
  PtrTable * _ptrs;

 public:
  // ResourceHashtable is passed to various functions and populated in
  // different places so we allocate it using C_HEAP to make it immune
  // from any ResourceMarks that happen to be in the code paths.
  ThreadScanHashtable(int table_size) : _table_size(table_size), _ptrs(new (ResourceObj::C_HEAP, mtThread) PtrTable()) {}

  ~ThreadScanHashtable() { delete _ptrs; }

  bool has_entry(void *pointer) {
    int *val_ptr = _ptrs->get(pointer);
    return val_ptr != NULL && *val_ptr == 1;
  }

  void add_entry(void *pointer) {
    _ptrs->put(pointer, 1);
  }
};

// Closure to gather JavaThreads indirectly referenced by hazard ptrs
// (ThreadsList references) into a hash table. This closure handles part 2
// of the dance - adding all the JavaThreads referenced by the hazard
// pointer (ThreadsList reference) to the hash table.
//
class AddThreadHazardPointerThreadClosure : public ThreadClosure {
 private:
  ThreadScanHashtable *_table;

 public:
  AddThreadHazardPointerThreadClosure(ThreadScanHashtable *table) : _table(table) {}

  virtual void do_thread(Thread *thread) {
    if (!_table->has_entry((void*)thread)) {
      // The same JavaThread might be on more than one ThreadsList or
      // more than one thread might be using the same ThreadsList. In
      // either case, we only need a single entry for a JavaThread.
      _table->add_entry((void*)thread);
    }
  }
};

// Closure to gather JavaThreads indirectly referenced by hazard ptrs
// (ThreadsList references) into a hash table. This closure handles part 1
// of the dance - hazard ptr chain walking and dispatch to another
// closure.
//
class ScanHazardPtrGatherProtectedThreadsClosure : public ThreadClosure {
 private:
  ThreadScanHashtable *_table;
 public:
  ScanHazardPtrGatherProtectedThreadsClosure(ThreadScanHashtable *table) : _table(table) {}

  virtual void do_thread(Thread *thread) {
    assert_locked_or_safepoint(Threads_lock);

    if (thread == NULL) return;

    // This code races with ThreadsSMRSupport::acquire_stable_list() which
    // is lock-free so we have to handle some special situations.
    //
    ThreadsList *current_list = NULL;
    while (true) {
      current_list = thread->get_threads_hazard_ptr();
      // No hazard ptr so nothing more to do.
      if (current_list == NULL) {
        assert(thread->get_nested_threads_hazard_ptr() == NULL,
               "cannot have a nested hazard ptr with a NULL regular hazard ptr");
        return;
      }

      // If the hazard ptr is verified as stable (since it is not tagged),
      // then it is safe to use.
      if (!Thread::is_hazard_ptr_tagged(current_list)) break;

      // The hazard ptr is tagged as not yet verified as being stable
      // so we are racing with acquire_stable_list(). This exchange
      // attempts to invalidate the hazard ptr. If we win the race,
      // then we can ignore this unstable hazard ptr and the other
      // thread will retry the attempt to publish a stable hazard ptr.
      // If we lose the race, then we retry our attempt to look at the
      // hazard ptr.
      if (thread->cmpxchg_threads_hazard_ptr(NULL, current_list) == current_list) return;
    }

    // The current JavaThread has a hazard ptr (ThreadsList reference)
    // which might be _java_thread_list or it might be an older
    // ThreadsList that has been removed but not freed. In either case,
    // the hazard ptr is protecting all the JavaThreads on that
    // ThreadsList.
    AddThreadHazardPointerThreadClosure add_cl(_table);
    current_list->threads_do(&add_cl);

    // Any NestedThreadsLists are also protecting JavaThreads so
    // gather those also; the ThreadsLists may be different.
    for (NestedThreadsList* node = thread->get_nested_threads_hazard_ptr();
         node != NULL; node = node->next()) {
      node->t_list()->threads_do(&add_cl);
    }
  }
};

// Closure to gather hazard ptrs (ThreadsList references) into a hash table.
//
class ScanHazardPtrGatherThreadsListClosure : public ThreadClosure {
 private:
  ThreadScanHashtable *_table;
 public:
  ScanHazardPtrGatherThreadsListClosure(ThreadScanHashtable *table) : _table(table) {}

  virtual void do_thread(Thread* thread) {
    assert_locked_or_safepoint(Threads_lock);

    if (thread == NULL) return;
    ThreadsList *threads = thread->get_threads_hazard_ptr();
    if (threads == NULL) {
      assert(thread->get_nested_threads_hazard_ptr() == NULL,
             "cannot have a nested hazard ptr with a NULL regular hazard ptr");
      return;
    }
    // In this closure we always ignore the tag that might mark this
    // hazard ptr as not yet verified. If we happen to catch an
    // unverified hazard ptr that is subsequently discarded (not
    // published), then the only side effect is that we might keep a
    // to-be-deleted ThreadsList alive a little longer.
    threads = Thread::untag_hazard_ptr(threads);
    if (!_table->has_entry((void*)threads)) {
      _table->add_entry((void*)threads);
    }

    // Any NestedThreadsLists are also protecting JavaThreads so
    // gather those also; the ThreadsLists may be different.
    for (NestedThreadsList* node = thread->get_nested_threads_hazard_ptr();
         node != NULL; node = node->next()) {
      threads = node->t_list();
      if (!_table->has_entry((void*)threads)) {
        _table->add_entry((void*)threads);
      }
    }
  }
};

// Closure to print JavaThreads that have a hazard ptr (ThreadsList
// reference) that contains an indirect reference to a specific JavaThread.
//
class ScanHazardPtrPrintMatchingThreadsClosure : public ThreadClosure {
 private:
  JavaThread *_thread;
 public:
  ScanHazardPtrPrintMatchingThreadsClosure(JavaThread *thread) : _thread(thread) {}

  virtual void do_thread(Thread *thread) {
    assert_locked_or_safepoint(Threads_lock);

    if (thread == NULL) return;
    ThreadsList *current_list = thread->get_threads_hazard_ptr();
    if (current_list == NULL) {
      assert(thread->get_nested_threads_hazard_ptr() == NULL,
             "cannot have a nested hazard ptr with a NULL regular hazard ptr");
      return;
    }
    // If the hazard ptr is unverified, then ignore it.
    if (Thread::is_hazard_ptr_tagged(current_list)) return;

    // The current JavaThread has a hazard ptr (ThreadsList reference)
    // which might be _java_thread_list or it might be an older
    // ThreadsList that has been removed but not freed. In either case,
    // the hazard ptr is protecting all the JavaThreads on that
    // ThreadsList, but we only care about matching a specific JavaThread.
    JavaThreadIterator jti(current_list);
    for (JavaThread *p = jti.first(); p != NULL; p = jti.next()) {
      if (p == _thread) {
        log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::smr_delete: thread1=" INTPTR_FORMAT " has a hazard pointer for thread2=" INTPTR_FORMAT, os::current_thread_id(), p2i(thread), p2i(_thread));
        break;
      }
    }

    // Any NestedThreadsLists are also protecting JavaThreads so
    // check those also; the ThreadsLists may be different.
    for (NestedThreadsList* node = thread->get_nested_threads_hazard_ptr();
         node != NULL; node = node->next()) {
      JavaThreadIterator jti(node->t_list());
      for (JavaThread *p = jti.first(); p != NULL; p = jti.next()) {
        if (p == _thread) {
          log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::smr_delete: thread1=" INTPTR_FORMAT " has a nested hazard pointer for thread2=" INTPTR_FORMAT, os::current_thread_id(), p2i(thread), p2i(_thread));
          return;
        }
      }
    }
  }
};


// 'entries + 1' so we always have at least one entry.
ThreadsList::ThreadsList(int entries) : _length(entries), _threads(NEW_C_HEAP_ARRAY(JavaThread*, entries + 1, mtThread)), _next_list(NULL) {
  *(JavaThread**)(_threads + entries) = NULL;  // Make sure the extra entry is NULL.
}

ThreadsList::~ThreadsList() {
  FREE_C_HEAP_ARRAY(JavaThread*, _threads);
}

// Add a JavaThread to a ThreadsList. The returned ThreadsList is a
// new copy of the specified ThreadsList with the specified JavaThread
// appended to the end.
ThreadsList *ThreadsList::add_thread(ThreadsList *list, JavaThread *java_thread) {
  const uint index = list->_length;
  const uint new_length = index + 1;
  const uint head_length = index;
  ThreadsList *const new_list = new ThreadsList(new_length);

  if (head_length > 0) {
    Copy::disjoint_words((HeapWord*)list->_threads, (HeapWord*)new_list->_threads, head_length);
  }
  *(JavaThread**)(new_list->_threads + index) = java_thread;

  return new_list;
}

int ThreadsList::find_index_of_JavaThread(JavaThread *target) {
  if (target == NULL) {
    return -1;
  }
  for (uint i = 0; i < length(); i++) {
    if (target == thread_at(i)) {
      return (int)i;
    }
  }
  return -1;
}

JavaThread* ThreadsList::find_JavaThread_from_java_tid(jlong java_tid) const {
  for (uint i = 0; i < length(); i++) {
    JavaThread* thread = thread_at(i);
    oop tobj = thread->threadObj();
    // Ignore the thread if it hasn't run yet, has exited
    // or is starting to exit.
    if (tobj != NULL && !thread->is_exiting() &&
        java_tid == java_lang_Thread::thread_id(tobj)) {
      // found a match
      return thread;
    }
  }
  return NULL;
}

bool ThreadsList::includes(const JavaThread * const p) const {
  if (p == NULL) {
    return false;
  }
  for (uint i = 0; i < length(); i++) {
    if (thread_at(i) == p) {
      return true;
    }
  }
  return false;
}

// Remove a JavaThread from a ThreadsList. The returned ThreadsList is a
// new copy of the specified ThreadsList with the specified JavaThread
// removed.
ThreadsList *ThreadsList::remove_thread(ThreadsList* list, JavaThread* java_thread) {
  assert(list->_length > 0, "sanity");

  uint i = (uint)list->find_index_of_JavaThread(java_thread);
  assert(i < list->_length, "did not find JavaThread on the list");
  const uint index = i;
  const uint new_length = list->_length - 1;
  const uint head_length = index;
  const uint tail_length = (new_length >= index) ? (new_length - index) : 0;
  ThreadsList *const new_list = new ThreadsList(new_length);

  if (head_length > 0) {
    Copy::disjoint_words((HeapWord*)list->_threads, (HeapWord*)new_list->_threads, head_length);
  }
  if (tail_length > 0) {
    Copy::disjoint_words((HeapWord*)list->_threads + index + 1, (HeapWord*)new_list->_threads + index, tail_length);
  }

  return new_list;
}

ThreadsListHandle::ThreadsListHandle(Thread *self) : _list(ThreadsSMRSupport::acquire_stable_list(self, /* is_ThreadsListSetter */ false)), _self(self) {
  assert(self == Thread::current(), "sanity check");
  if (EnableThreadSMRStatistics) {
    _timer.start();
  }
}

ThreadsListHandle::~ThreadsListHandle() {
  ThreadsSMRSupport::release_stable_list(_self);
  if (EnableThreadSMRStatistics) {
    _timer.stop();
    uint millis = (uint)_timer.milliseconds();
    ThreadsSMRSupport::update_tlh_stats(millis);
  }
}

// Convert an internal thread reference to a JavaThread found on the
// associated ThreadsList. This ThreadsListHandle "protects" the
// returned JavaThread *.
//
// If thread_oop_p is not NULL, then the caller wants to use the oop
// after this call so the oop is returned. On success, *jt_pp is set
// to the converted JavaThread * and true is returned. On error,
// returns false.
//
bool ThreadsListHandle::cv_internal_thread_to_JavaThread(jobject jthread,
                                                         JavaThread ** jt_pp,
                                                         oop * thread_oop_p) {
  assert(this->list() != NULL, "must have a ThreadsList");
  assert(jt_pp != NULL, "must have a return JavaThread pointer");
  // thread_oop_p is optional so no assert()

  // The JVM_* interfaces don't allow a NULL thread parameter; JVM/TI
  // allows a NULL thread parameter to signify "current thread" which
  // allows us to avoid calling cv_external_thread_to_JavaThread().
  // The JVM_* interfaces have no such leeway.

  oop thread_oop = JNIHandles::resolve_non_null(jthread);
  // Looks like an oop at this point.
  if (thread_oop_p != NULL) {
    // Return the oop to the caller; the caller may still want
    // the oop even if this function returns false.
    *thread_oop_p = thread_oop;
  }

  JavaThread *java_thread = java_lang_Thread::thread(thread_oop);
  if (java_thread == NULL) {
    // The java.lang.Thread does not contain a JavaThread * so it has
    // not yet run or it has died.
    return false;
  }
  // Looks like a live JavaThread at this point.

  if (java_thread != JavaThread::current()) {
    // jthread is not for the current JavaThread so have to verify
    // the JavaThread * against the ThreadsList.
    if (EnableThreadSMRExtraValidityChecks && !includes(java_thread)) {
      // Not on the JavaThreads list so it is not alive.
      return false;
    }
  }

  // Return a live JavaThread that is "protected" by the
  // ThreadsListHandle in the caller.
  *jt_pp = java_thread;
  return true;
}

ThreadsListSetter::~ThreadsListSetter() {
  if (_target_needs_release) {
    // The hazard ptr in the target needs to be released.
    ThreadsSMRSupport::release_stable_list(_target);
  }
}

// Closure to determine if the specified JavaThread is found by
// threads_do().
//
class VerifyHazardPointerThreadClosure : public ThreadClosure {
 private:
  bool _found;
  Thread *_self;

 public:
  VerifyHazardPointerThreadClosure(Thread *self) : _found(false), _self(self) {}

  bool found() const { return _found; }

  virtual void do_thread(Thread *thread) {
    if (thread == _self) {
      _found = true;
    }
  }
};

// Apply the closure to all threads in the system, with a snapshot of
// all JavaThreads provided by the list parameter.
void ThreadsSMRSupport::threads_do(ThreadClosure *tc, ThreadsList *list) {
  list->threads_do(tc);
  Threads::non_java_threads_do(tc);
}

// Apply the closure to all threads in the system.
void ThreadsSMRSupport::threads_do(ThreadClosure *tc) {
  threads_do(tc, _java_thread_list);
}

// Verify that the stable hazard pointer used to safely keep threads
// alive is scanned by threads_do() which is a key piece of honoring
// the Thread-SMR protocol.
void ThreadsSMRSupport::verify_hazard_pointer_scanned(Thread *self, ThreadsList *threads) {
#ifdef ASSERT
  assert(threads != NULL, "threads must not be NULL");

  // The closure will attempt to verify that the calling thread can
  // be found by threads_do() on the specified ThreadsList. If it
  // is successful, then the specified ThreadsList was acquired as
  // a stable hazard pointer by the calling thread in a way that
  // honored the Thread-SMR protocol.
  //
  // If the calling thread cannot be found by threads_do() and if
  // it is not the shutdown thread, then the calling thread is not
  // honoring the Thread-SMR ptotocol. This means that the specified
  // ThreadsList is not a stable hazard pointer and can be freed
  // by another thread from the to-be-deleted list at any time.
  //
  // Note: The shutdown thread has removed itself from the Threads
  // list and is safe to have a waiver from this check because
  // VM_Exit::_shutdown_thread is not set until after the VMThread
  // has started the final safepoint which holds the Threads_lock
  // for the remainder of the VM's life.
  //
  VerifyHazardPointerThreadClosure cl(self);
  threads_do(&cl, threads);

  // If the calling thread is not honoring the Thread-SMR protocol,
  // then we will either crash in threads_do() above because 'threads'
  // was freed by another thread or we will fail the assert() below.
  // In either case, we won't get past this point with a badly placed
  // ThreadsListHandle.

  assert(cl.found() || self == VM_Exit::shutdown_thread(), "Acquired a ThreadsList snapshot from a thread not recognized by the Thread-SMR protocol.");
#endif
}

void ThreadsListSetter::set() {
  assert(_target->get_threads_hazard_ptr() == NULL, "hazard ptr should not already be set");
  (void) ThreadsSMRSupport::acquire_stable_list(_target, /* is_ThreadsListSetter */ true);
  _target_needs_release = true;
}

// Acquire a stable ThreadsList.
//
ThreadsList *ThreadsSMRSupport::acquire_stable_list(Thread *self, bool is_ThreadsListSetter) {
  assert(self != NULL, "sanity check");
  // acquire_stable_list_nested_path() will grab the Threads_lock
  // so let's make sure the ThreadsListHandle is in a safe place.
  // ThreadsListSetter cannot make this check on this code path.
  debug_only(if (!is_ThreadsListSetter && StrictSafepointChecks) self->check_for_valid_safepoint_state(/* potential_vm_operation */ false);)

  if (self->get_threads_hazard_ptr() == NULL) {
    // The typical case is first.
    return acquire_stable_list_fast_path(self);
  }

  // The nested case is rare.
  return acquire_stable_list_nested_path(self);
}

// Fast path (and lock free) way to acquire a stable ThreadsList.
//
ThreadsList *ThreadsSMRSupport::acquire_stable_list_fast_path(Thread *self) {
  assert(self != NULL, "sanity check");
  assert(self->get_threads_hazard_ptr() == NULL, "sanity check");
  assert(self->get_nested_threads_hazard_ptr() == NULL,
         "cannot have a nested hazard ptr with a NULL regular hazard ptr");

  ThreadsList* threads;

  // Stable recording of a hazard ptr for SMR. This code does not use
  // locks so its use of the _java_thread_list & _threads_hazard_ptr
  // fields is racy relative to code that uses those fields with locks.
  // OrderAccess and Atomic functions are used to deal with those races.
  //
  while (true) {
    threads = get_java_thread_list();

    // Publish a tagged hazard ptr to denote that the hazard ptr is not
    // yet verified as being stable. Due to the fence after the hazard
    // ptr write, it will be sequentially consistent w.r.t. the
    // sequentially consistent writes of the ThreadsList, even on
    // non-multiple copy atomic machines where stores can be observed
    // in different order from different observer threads.
    ThreadsList* unverified_threads = Thread::tag_hazard_ptr(threads);
    self->set_threads_hazard_ptr(unverified_threads);

    // If _java_thread_list has changed, we have lost a race with
    // Threads::add() or Threads::remove() and have to try again.
    if (get_java_thread_list() != threads) {
      continue;
    }

    // We try to remove the tag which will verify the hazard ptr as
    // being stable. This exchange can race with a scanning thread
    // which might invalidate the tagged hazard ptr to keep it from
    // being followed to access JavaThread ptrs. If we lose the race,
    // we simply retry. If we win the race, then the stable hazard
    // ptr is officially published.
    if (self->cmpxchg_threads_hazard_ptr(threads, unverified_threads) == unverified_threads) {
      break;
    }
  }

  // A stable hazard ptr has been published letting other threads know
  // that the ThreadsList and the JavaThreads reachable from this list
  // are protected and hence they should not be deleted until everyone
  // agrees it is safe to do so.

  verify_hazard_pointer_scanned(self, threads);

  return threads;
}

// Acquire a nested stable ThreadsList; this is rare so it uses
// Threads_lock.
//
ThreadsList *ThreadsSMRSupport::acquire_stable_list_nested_path(Thread *self) {
  assert(self != NULL, "sanity check");
  assert(self->get_threads_hazard_ptr() != NULL,
         "cannot have a NULL regular hazard ptr when acquiring a nested hazard ptr");

  // The thread already has a hazard ptr (ThreadsList ref) so we need
  // to create a nested ThreadsListHandle with the current ThreadsList
  // since it might be different than our current hazard ptr. The need
  // for a nested ThreadsListHandle is rare so we do this while holding
  // the Threads_lock so we don't race with the scanning code; the code
  // is so much simpler this way.

  NestedThreadsList* node;
  {
    // Only grab the Threads_lock if we don't already own it.
    MutexLockerEx ml(Threads_lock->owned_by_self() ? NULL : Threads_lock);
    node = new NestedThreadsList(get_java_thread_list());
    // We insert at the front of the list to match up with the delete
    // in release_stable_list().
    node->set_next(self->get_nested_threads_hazard_ptr());
    self->set_nested_threads_hazard_ptr(node);
    if (EnableThreadSMRStatistics) {
      self->inc_nested_threads_hazard_ptr_cnt();
      if (self->nested_threads_hazard_ptr_cnt() > _nested_thread_list_max) {
        _nested_thread_list_max = self->nested_threads_hazard_ptr_cnt();
      }
    }
  }
  log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::acquire_stable_list: add NestedThreadsList node containing ThreadsList=" INTPTR_FORMAT, os::current_thread_id(), p2i(node->t_list()));

  verify_hazard_pointer_scanned(self, node->t_list());

  return node->t_list();
}

void ThreadsSMRSupport::add_thread(JavaThread *thread){
  ThreadsList *new_list = ThreadsList::add_thread(ThreadsSMRSupport::get_java_thread_list(), thread);
  if (EnableThreadSMRStatistics) {
    ThreadsSMRSupport::inc_java_thread_list_alloc_cnt();
    ThreadsSMRSupport::update_java_thread_list_max(new_list->length());
  }
  // Initial _java_thread_list will not generate a "Threads::add" mesg.
  log_debug(thread, smr)("tid=" UINTX_FORMAT ": Threads::add: new ThreadsList=" INTPTR_FORMAT, os::current_thread_id(), p2i(new_list));

  ThreadsList *old_list = ThreadsSMRSupport::xchg_java_thread_list(new_list);
  ThreadsSMRSupport::free_list(old_list);
}

// set_delete_notify() and clear_delete_notify() are called
// under the protection of the delete_lock, but we also use an
// Atomic operation to ensure the memory update is seen earlier than
// when the delete_lock is dropped.
//
void ThreadsSMRSupport::clear_delete_notify() {
  Atomic::dec(&_delete_notify);
}

bool ThreadsSMRSupport::delete_notify() {
  // Use load_acquire() in order to see any updates to _delete_notify
  // earlier than when delete_lock is grabbed.
  return (OrderAccess::load_acquire(&_delete_notify) != 0);
}

// Safely free a ThreadsList after a Threads::add() or Threads::remove().
// The specified ThreadsList may not get deleted during this call if it
// is still in-use (referenced by a hazard ptr). Other ThreadsLists
// in the chain may get deleted by this call if they are no longer in-use.
void ThreadsSMRSupport::free_list(ThreadsList* threads) {
  assert_locked_or_safepoint(Threads_lock);

  threads->set_next_list(_to_delete_list);
  _to_delete_list = threads;
  if (EnableThreadSMRStatistics) {
    _to_delete_list_cnt++;
    if (_to_delete_list_cnt > _to_delete_list_max) {
      _to_delete_list_max = _to_delete_list_cnt;
    }
  }

  // Hash table size should be first power of two higher than twice the length of the ThreadsList
  int hash_table_size = MIN2((int)get_java_thread_list()->length(), 32) << 1;
  hash_table_size--;
  hash_table_size |= hash_table_size >> 1;
  hash_table_size |= hash_table_size >> 2;
  hash_table_size |= hash_table_size >> 4;
  hash_table_size |= hash_table_size >> 8;
  hash_table_size |= hash_table_size >> 16;
  hash_table_size++;

  // Gather a hash table of the current hazard ptrs:
  ThreadScanHashtable *scan_table = new ThreadScanHashtable(hash_table_size);
  ScanHazardPtrGatherThreadsListClosure scan_cl(scan_table);
  threads_do(&scan_cl);

  // Walk through the linked list of pending freeable ThreadsLists
  // and free the ones that are not referenced from hazard ptrs.
  ThreadsList* current = _to_delete_list;
  ThreadsList* prev = NULL;
  ThreadsList* next = NULL;
  bool threads_is_freed = false;
  while (current != NULL) {
    next = current->next_list();
    if (!scan_table->has_entry((void*)current)) {
      // This ThreadsList is not referenced by a hazard ptr.
      if (prev != NULL) {
        prev->set_next_list(next);
      }
      if (_to_delete_list == current) {
        _to_delete_list = next;
      }

      log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::free_list: threads=" INTPTR_FORMAT " is freed.", os::current_thread_id(), p2i(current));
      if (current == threads) threads_is_freed = true;
      delete current;
      if (EnableThreadSMRStatistics) {
        _java_thread_list_free_cnt++;
        _to_delete_list_cnt--;
      }
    } else {
      prev = current;
    }
    current = next;
  }

  if (!threads_is_freed) {
    // Only report "is not freed" on the original call to
    // free_list() for this ThreadsList.
    log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::free_list: threads=" INTPTR_FORMAT " is not freed.", os::current_thread_id(), p2i(threads));
  }

  delete scan_table;
}

// Return true if the specified JavaThread is protected by a hazard
// pointer (ThreadsList reference). Otherwise, returns false.
//
bool ThreadsSMRSupport::is_a_protected_JavaThread(JavaThread *thread) {
  assert_locked_or_safepoint(Threads_lock);

  // Hash table size should be first power of two higher than twice
  // the length of the Threads list.
  int hash_table_size = MIN2((int)get_java_thread_list()->length(), 32) << 1;
  hash_table_size--;
  hash_table_size |= hash_table_size >> 1;
  hash_table_size |= hash_table_size >> 2;
  hash_table_size |= hash_table_size >> 4;
  hash_table_size |= hash_table_size >> 8;
  hash_table_size |= hash_table_size >> 16;
  hash_table_size++;

  // Gather a hash table of the JavaThreads indirectly referenced by
  // hazard ptrs.
  ThreadScanHashtable *scan_table = new ThreadScanHashtable(hash_table_size);
  ScanHazardPtrGatherProtectedThreadsClosure scan_cl(scan_table);
  threads_do(&scan_cl);

  bool thread_is_protected = false;
  if (scan_table->has_entry((void*)thread)) {
    thread_is_protected = true;
  }
  delete scan_table;
  return thread_is_protected;
}

// Release a stable ThreadsList.
//
void ThreadsSMRSupport::release_stable_list(Thread *self) {
  assert(self != NULL, "sanity check");
  // release_stable_list_nested_path() will grab the Threads_lock
  // so let's make sure the ThreadsListHandle is in a safe place.
  debug_only(if (StrictSafepointChecks) self->check_for_valid_safepoint_state(/* potential_vm_operation */ false);)

  if (self->get_nested_threads_hazard_ptr() == NULL) {
    // The typical case is first.
    release_stable_list_fast_path(self);
    return;
  }

  // The nested case is rare.
  release_stable_list_nested_path(self);
}

// Fast path way to release a stable ThreadsList. The release portion
// is lock-free, but the wake up portion is not.
//
void ThreadsSMRSupport::release_stable_list_fast_path(Thread *self) {
  assert(self != NULL, "sanity check");
  assert(self->get_threads_hazard_ptr() != NULL, "sanity check");
  assert(self->get_nested_threads_hazard_ptr() == NULL,
         "cannot have a nested hazard ptr when releasing a regular hazard ptr");

  // After releasing the hazard ptr, other threads may go ahead and
  // free up some memory temporarily used by a ThreadsList snapshot.
  self->set_threads_hazard_ptr(NULL);

  // We use double-check locking to reduce traffic on the system
  // wide Thread-SMR delete_lock.
  if (ThreadsSMRSupport::delete_notify()) {
    // An exiting thread might be waiting in smr_delete(); we need to
    // check with delete_lock to be sure.
    release_stable_list_wake_up((char *) "regular hazard ptr");
  }
}

// Release a nested stable ThreadsList; this is rare so it uses
// Threads_lock.
//
void ThreadsSMRSupport::release_stable_list_nested_path(Thread *self) {
  assert(self != NULL, "sanity check");
  assert(self->get_nested_threads_hazard_ptr() != NULL, "sanity check");
  assert(self->get_threads_hazard_ptr() != NULL,
         "must have a regular hazard ptr to have nested hazard ptrs");

  // We have a nested ThreadsListHandle so we have to release it first.
  // The need for a nested ThreadsListHandle is rare so we do this while
  // holding the Threads_lock so we don't race with the scanning code;
  // the code is so much simpler this way.

  NestedThreadsList *node;
  {
    // Only grab the Threads_lock if we don't already own it.
    MutexLockerEx ml(Threads_lock->owned_by_self() ? NULL : Threads_lock);
    // We remove from the front of the list to match up with the insert
    // in acquire_stable_list().
    node = self->get_nested_threads_hazard_ptr();
    self->set_nested_threads_hazard_ptr(node->next());
    if (EnableThreadSMRStatistics) {
      self->dec_nested_threads_hazard_ptr_cnt();
    }
  }

  // An exiting thread might be waiting in smr_delete(); we need to
  // check with delete_lock to be sure.
  release_stable_list_wake_up((char *) "nested hazard ptr");

  log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::release_stable_list: delete NestedThreadsList node containing ThreadsList=" INTPTR_FORMAT, os::current_thread_id(), p2i(node->t_list()));

  delete node;
}

// Wake up portion of the release stable ThreadsList protocol;
// uses the delete_lock().
//
void ThreadsSMRSupport::release_stable_list_wake_up(char *log_str) {
  assert(log_str != NULL, "sanity check");

  // Note: delete_lock is held in smr_delete() for the entire
  // hazard ptr search so that we do not lose this notify() if
  // the exiting thread has to wait. That code path also holds
  // Threads_lock (which was grabbed before delete_lock) so that
  // threads_do() can be called. This means the system can't start a
  // safepoint which means this thread can't take too long to get to
  // a safepoint because of being blocked on delete_lock.
  //
  MonitorLockerEx ml(ThreadsSMRSupport::delete_lock(), Monitor::_no_safepoint_check_flag);
  if (ThreadsSMRSupport::delete_notify()) {
    // Notify any exiting JavaThreads that are waiting in smr_delete()
    // that we've released a ThreadsList.
    ml.notify_all();
    log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::release_stable_list notified %s", os::current_thread_id(), log_str);
  }
}

void ThreadsSMRSupport::remove_thread(JavaThread *thread) {
  ThreadsList *new_list = ThreadsList::remove_thread(ThreadsSMRSupport::get_java_thread_list(), thread);
  if (EnableThreadSMRStatistics) {
    ThreadsSMRSupport::inc_java_thread_list_alloc_cnt();
    // This list is smaller so no need to check for a "longest" update.
  }

  // Final _java_thread_list will not generate a "Threads::remove" mesg.
  log_debug(thread, smr)("tid=" UINTX_FORMAT ": Threads::remove: new ThreadsList=" INTPTR_FORMAT, os::current_thread_id(), p2i(new_list));

  ThreadsList *old_list = ThreadsSMRSupport::xchg_java_thread_list(new_list);
  ThreadsSMRSupport::free_list(old_list);
}

// See note for clear_delete_notify().
//
void ThreadsSMRSupport::set_delete_notify() {
  Atomic::inc(&_delete_notify);
}

// Safely delete a JavaThread when it is no longer in use by a
// ThreadsListHandle.
//
void ThreadsSMRSupport::smr_delete(JavaThread *thread) {
  assert(!Threads_lock->owned_by_self(), "sanity");

  bool has_logged_once = false;
  elapsedTimer timer;
  if (EnableThreadSMRStatistics) {
    timer.start();
  }

  while (true) {
    {
      // No safepoint check because this JavaThread is not on the
      // Threads list.
      MutexLockerEx ml(Threads_lock, Mutex::_no_safepoint_check_flag);
      // Cannot use a MonitorLockerEx helper here because we have
      // to drop the Threads_lock first if we wait.
      ThreadsSMRSupport::delete_lock()->lock_without_safepoint_check();
      // Set the delete_notify flag after we grab delete_lock
      // and before we scan hazard ptrs because we're doing
      // double-check locking in release_stable_list().
      ThreadsSMRSupport::set_delete_notify();

      if (!is_a_protected_JavaThread(thread)) {
        // This is the common case.
        ThreadsSMRSupport::clear_delete_notify();
        ThreadsSMRSupport::delete_lock()->unlock();
        break;
      }
      if (!has_logged_once) {
        has_logged_once = true;
        log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::smr_delete: thread=" INTPTR_FORMAT " is not deleted.", os::current_thread_id(), p2i(thread));
        if (log_is_enabled(Debug, os, thread)) {
          ScanHazardPtrPrintMatchingThreadsClosure scan_cl(thread);
          threads_do(&scan_cl);
        }
      }
    } // We have to drop the Threads_lock to wait or delete the thread

    if (EnableThreadSMRStatistics) {
      _delete_lock_wait_cnt++;
      if (_delete_lock_wait_cnt > _delete_lock_wait_max) {
        _delete_lock_wait_max = _delete_lock_wait_cnt;
      }
    }
    // Wait for a release_stable_list() call before we check again. No
    // safepoint check, no timeout, and not as suspend equivalent flag
    // because this JavaThread is not on the Threads list.
    ThreadsSMRSupport::delete_lock()->wait(Mutex::_no_safepoint_check_flag, 0,
                                     !Mutex::_as_suspend_equivalent_flag);
    if (EnableThreadSMRStatistics) {
      _delete_lock_wait_cnt--;
    }

    ThreadsSMRSupport::clear_delete_notify();
    ThreadsSMRSupport::delete_lock()->unlock();
    // Retry the whole scenario.
  }

  if (ThreadLocalHandshakes) {
    // The thread is about to be deleted so cancel any handshake.
    thread->cancel_handshake();
  }

  delete thread;
  if (EnableThreadSMRStatistics) {
    timer.stop();
    uint millis = (uint)timer.milliseconds();
    ThreadsSMRSupport::inc_deleted_thread_cnt();
    ThreadsSMRSupport::add_deleted_thread_times(millis);
    ThreadsSMRSupport::update_deleted_thread_time_max(millis);
  }

  log_debug(thread, smr)("tid=" UINTX_FORMAT ": ThreadsSMRSupport::smr_delete: thread=" INTPTR_FORMAT " is deleted.", os::current_thread_id(), p2i(thread));
}


// Debug, logging, and printing stuff at the end:

// Log Threads class SMR info.
void ThreadsSMRSupport::log_statistics() {
  LogTarget(Info, thread, smr) log;
  if (log.is_enabled()) {
    LogStream out(log);
    print_info_on(&out);
  }
}

// Print Threads class SMR info.
void ThreadsSMRSupport::print_info_on(outputStream* st) {
  // Only grab the Threads_lock if we don't already own it
  // and if we are not reporting an error.
  MutexLockerEx ml((Threads_lock->owned_by_self() || VMError::is_error_reported()) ? NULL : Threads_lock);

  st->print_cr("Threads class SMR info:");
  st->print_cr("_java_thread_list=" INTPTR_FORMAT ", length=%u, "
               "elements={", p2i(_java_thread_list),
               _java_thread_list->length());
  print_info_elements_on(st, _java_thread_list);
  st->print_cr("}");
  if (_to_delete_list != NULL) {
    st->print_cr("_to_delete_list=" INTPTR_FORMAT ", length=%u, "
                 "elements={", p2i(_to_delete_list),
                 _to_delete_list->length());
    print_info_elements_on(st, _to_delete_list);
    st->print_cr("}");
    for (ThreadsList *t_list = _to_delete_list->next_list();
         t_list != NULL; t_list = t_list->next_list()) {
      st->print("next-> " INTPTR_FORMAT ", length=%u, "
                "elements={", p2i(t_list), t_list->length());
      print_info_elements_on(st, t_list);
      st->print_cr("}");
    }
  }
  if (!EnableThreadSMRStatistics) {
    return;
  }
  st->print_cr("_java_thread_list_alloc_cnt=" UINT64_FORMAT ","
               "_java_thread_list_free_cnt=" UINT64_FORMAT ","
               "_java_thread_list_max=%u, "
               "_nested_thread_list_max=%u",
               _java_thread_list_alloc_cnt,
               _java_thread_list_free_cnt,
               _java_thread_list_max,
               _nested_thread_list_max);
  if (_tlh_cnt > 0) {
    st->print_cr("_tlh_cnt=%u"
                 ", _tlh_times=%u"
                 ", avg_tlh_time=%0.2f"
                 ", _tlh_time_max=%u",
                 _tlh_cnt, _tlh_times,
                 ((double) _tlh_times / _tlh_cnt),
                 _tlh_time_max);
  }
  if (_deleted_thread_cnt > 0) {
    st->print_cr("_deleted_thread_cnt=%u"
                 ", _deleted_thread_times=%u"
                 ", avg_deleted_thread_time=%0.2f"
                 ", _deleted_thread_time_max=%u",
                 _deleted_thread_cnt, _deleted_thread_times,
                 ((double) _deleted_thread_times / _deleted_thread_cnt),
                 _deleted_thread_time_max);
  }
  st->print_cr("_delete_lock_wait_cnt=%u, _delete_lock_wait_max=%u",
               _delete_lock_wait_cnt, _delete_lock_wait_max);
  st->print_cr("_to_delete_list_cnt=%u, _to_delete_list_max=%u",
               _to_delete_list_cnt, _to_delete_list_max);
}

// Print ThreadsList elements (4 per line).
void ThreadsSMRSupport::print_info_elements_on(outputStream* st, ThreadsList* t_list) {
  uint cnt = 0;
  JavaThreadIterator jti(t_list);
  for (JavaThread *jt = jti.first(); jt != NULL; jt = jti.next()) {
    st->print(INTPTR_FORMAT, p2i(jt));
    if (cnt < t_list->length() - 1) {
      // Separate with comma or comma-space except for the last one.
      if (((cnt + 1) % 4) == 0) {
        // Four INTPTR_FORMAT fit on an 80 column line so end the
        // current line with just a comma.
        st->print_cr(",");
      } else {
        // Not the last one on the current line so use comma-space:
        st->print(", ");
      }
    } else {
      // Last one so just end the current line.
      st->cr();
    }
    cnt++;
  }
}
