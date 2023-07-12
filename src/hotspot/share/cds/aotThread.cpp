/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotStreamedHeapLoader.hpp"
#include "cds/aotThread.hpp"
#include "cds/heapShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jfr.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.hpp"
#include "runtime/threads.hpp"

AOTThread* AOTThread::_aot_thread;

// Starting the AOTThread is tricky. We wish to start it as early as possible, as
// that increases the amount of curling this thread can do for the application thread
// that is concurrently starting. But there are complications starting a thread this
// early. The java.lang.Thread class is not initialized and we may not execute any
// Java bytecodes yet. This is an internal thread, so we try to keep the bookkeeping
// minimal and use a logical ThreadIdentifier for JFR and monitor identity. The real
// thread object is created just after the main thread creates its Thread object, after
// the Thread class has been initialized.
void AOTThread::initialize() {
#if INCLUDE_CDS_JAVA_HEAP
  EXCEPTION_MARK;

  // Spin up a thread without thread oop, because the java.lang classes
  // have not yet been initialized, and hence we can't allocate the Thread
  // object yet.
  _aot_thread = new AOTThread(&aot_thread_entry);
  JavaThread::vm_exit_on_osthread_failure(_aot_thread);

  // Note that the Thread class is not initialized yet at this point. We
  // can run a bit concurrently until the Thread class is initialized; then
  // materialize_thread_object is called to inflate the thread object.

  // The thread needs an identifier. This thread is fine with a temporary ID
  // assignment; it will terminate soon anyway.
  int64_t tid = ThreadIdentifier::next();
  _aot_thread->set_monitor_owner_id(tid);

  {
    MutexLocker mu(JavaThread::current(), Threads_lock);
    Threads::add(_aot_thread);
  }

  JFR_ONLY(Jfr::on_java_thread_start(JavaThread::current(), _aot_thread);)

  os::start_thread(_aot_thread);
#endif
}

void AOTThread::materialize_thread_object() {
#if INCLUDE_CDS_JAVA_HEAP
  if (_aot_thread == nullptr) {
    // No thread object to materialize
    return;
  }

  EXCEPTION_MARK;

  HandleMark hm(JavaThread::current());
  Handle thread_oop = JavaThread::create_system_thread_object("AOTThread", JavaThread::current());

  java_lang_Thread::release_set_thread(thread_oop(), _aot_thread);
  _aot_thread->set_threadOopHandles(thread_oop());
#endif
}

void AOTThread::aot_thread_entry(JavaThread* jt, TRAPS) {
#if INCLUDE_CDS_JAVA_HEAP
  AOTStreamedHeapLoader::materialize_objects();
#endif
}
