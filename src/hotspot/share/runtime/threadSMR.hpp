/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_THREADSMR_HPP
#define SHARE_VM_RUNTIME_THREADSMR_HPP

#include "memory/allocation.hpp"
#include "runtime/timer.hpp"

// Thread Safe Memory Reclamation (Thread-SMR) support.
//
// ThreadsListHandles are used to safely perform operations on one or more
// threads without the risk of the thread or threads exiting during the
// operation. It is no longer necessary to hold the Threads_lock to safely
// perform an operation on a target thread.
//
// There are several different ways to refer to java.lang.Thread objects
// so we have a few ways to get a protected JavaThread *:
//
// JNI jobject example:
//   jobject jthread = ...;
//   :
//   ThreadsListHandle tlh;
//   JavaThread* jt = NULL;
//   bool is_alive = tlh.cv_internal_thread_to_JavaThread(jthread, &jt, NULL);
//   if (is_alive) {
//     :  // do stuff with 'jt'...
//   }
//
// JVM/TI jthread example:
//   jthread thread = ...;
//   :
//   JavaThread* jt = NULL;
//   ThreadsListHandle tlh;
//   jvmtiError err = JvmtiExport::cv_external_thread_to_JavaThread(tlh.list(), thread, &jt, NULL);
//   if (err != JVMTI_ERROR_NONE) {
//     return err;
//   }
//   :  // do stuff with 'jt'...
//
// JVM/TI oop example (this one should be very rare):
//   oop thread_obj = ...;
//   :
//   JavaThread *jt = NULL;
//   ThreadsListHandle tlh;
//   jvmtiError err = JvmtiExport::cv_oop_to_JavaThread(tlh.list(), thread_obj, &jt);
//   if (err != JVMTI_ERROR_NONE) {
//     return err;
//   }
//   :  // do stuff with 'jt'...
//
// A JavaThread * that is included in the ThreadsList that is held by
// a ThreadsListHandle is protected as long as the ThreadsListHandle
// remains in scope. The target JavaThread * may have logically exited,
// but that target JavaThread * will not be deleted until it is no
// longer protected by a ThreadsListHandle.


// A fast list of JavaThreads.
//
class ThreadsList : public CHeapObj<mtThread> {
  friend class ScanHazardPtrGatherProtectedThreadsClosure;
  friend class Threads;

  const uint _length;
  ThreadsList* _next_list;
  JavaThread *const *const _threads;

  template <class T>
  void threads_do_dispatch(T *cl, JavaThread *const thread) const;

  ThreadsList *next_list() const        { return _next_list; }
  void set_next_list(ThreadsList *list) { _next_list = list; }

public:
  ThreadsList(int entries);
  ~ThreadsList();

  template <class T>
  void threads_do(T *cl) const;

  uint length() const                       { return _length; }

  JavaThread *const thread_at(uint i) const { return _threads[i]; }

  JavaThread *const *threads() const        { return _threads; }

  // Returns -1 if target is not found.
  int find_index_of_JavaThread(JavaThread* target);
  JavaThread* find_JavaThread_from_java_tid(jlong java_tid) const;
  bool includes(const JavaThread * const p) const;

  static ThreadsList* add_thread(ThreadsList* list, JavaThread* java_thread);
  static ThreadsList* remove_thread(ThreadsList* list, JavaThread* java_thread);
};

// Linked list of ThreadsLists to support nested ThreadsListHandles.
class NestedThreadsList : public CHeapObj<mtThread> {
  ThreadsList*const _t_list;
  NestedThreadsList* _next;

public:
  NestedThreadsList(ThreadsList* t_list) : _t_list(t_list) {
    assert(Threads_lock->owned_by_self(),
           "must own Threads_lock for saved t_list to be valid.");
  }

  ThreadsList* t_list() { return _t_list; }
  NestedThreadsList* next() { return _next; }
  void set_next(NestedThreadsList* value) { _next = value; }
};

// A helper to optionally set the hazard ptr in ourself. This helper can
// be used by ourself or by another thread. If the hazard ptr is set(),
// then the destructor will release it.
//
class ThreadsListSetter : public StackObj {
private:
  bool _target_needs_release;  // needs release only when set()
  Thread * _target;

public:
  ThreadsListSetter() : _target_needs_release(false), _target(Thread::current()) {
  }
  ~ThreadsListSetter();
  ThreadsList* list();
  void set();
  bool target_needs_release() { return _target_needs_release; }
};

// This stack allocated ThreadsListHandle keeps all JavaThreads in the
// ThreadsList from being deleted until it is safe.
//
class ThreadsListHandle : public StackObj {
  ThreadsList * _list;
  Thread *const _self;
  elapsedTimer _timer;  // Enabled via -XX:+EnableThreadSMRStatistics.

public:
  ThreadsListHandle(Thread *self = Thread::current());
  ~ThreadsListHandle();

  ThreadsList *list() const {
    return _list;
  }

  template <class T>
  void threads_do(T *cl) const {
    return _list->threads_do(cl);
  }

  bool cv_internal_thread_to_JavaThread(jobject jthread, JavaThread ** jt_pp, oop * thread_oop_p);

  bool includes(JavaThread* p) {
    return _list->includes(p);
  }

  uint length() const {
    return _list->length();
  }
};

// This stack allocated JavaThreadIterator is used to walk the
// specified ThreadsList using the following style:
//
//   JavaThreadIterator jti(t_list);
//   for (JavaThread *jt = jti.first(); jt != NULL; jt = jti.next()) {
//     ...
//   }
//
class JavaThreadIterator : public StackObj {
  ThreadsList * _list;
  uint _index;

public:
  JavaThreadIterator(ThreadsList *list) : _list(list), _index(0) {
    assert(list != NULL, "ThreadsList must not be NULL.");
  }

  JavaThread *first() {
    _index = 0;
    return _list->thread_at(_index);
  }

  uint length() const {
    return _list->length();
  }

  ThreadsList *list() const {
    return _list;
  }

  JavaThread *next() {
    if (++_index >= length()) {
      return NULL;
    }
    return _list->thread_at(_index);
  }
};

// This stack allocated ThreadsListHandle and JavaThreadIterator combo
// is used to walk the ThreadsList in the included ThreadsListHandle
// using the following style:
//
//   for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
//     ...
//   }
//
class JavaThreadIteratorWithHandle : public StackObj {
  ThreadsListHandle _tlh;
  uint _index;

public:
  JavaThreadIteratorWithHandle() : _index(0) {}

  uint length() const {
    return _tlh.length();
  }

  ThreadsList *list() const {
    return _tlh.list();
  }

  JavaThread *next() {
    if (_index >= length()) {
      return NULL;
    }
    return _tlh.list()->thread_at(_index++);
  }

  void rewind() {
    _index = 0;
  }
};

#endif // SHARE_VM_RUNTIME_THREADSMR_HPP
