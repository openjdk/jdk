/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_NONJAVATHREAD_HPP
#define SHARE_RUNTIME_NONJAVATHREAD_HPP

#include "runtime/thread.hpp"

class NonJavaThread: public Thread {
  friend class VMStructs;

  NonJavaThread* volatile _next;

  class List;
  static List _the_list;

  void add_to_the_list();
  void remove_from_the_list();

 protected:
  virtual void pre_run();
  virtual void post_run();

 public:
  NonJavaThread();
  ~NonJavaThread();
  static int count();

  class Iterator;
};

// Provides iteration over the list of NonJavaThreads.
// List addition occurs in pre_run(), and removal occurs in post_run(),
// so that only live fully-initialized threads can be found in the list.
// Threads created after an iterator is constructed will not be visited
// by the iterator. The scope of an iterator is a critical section; there
// must be no safepoint checks in that scope.
class NonJavaThread::Iterator : public StackObj {
  uint _protect_enter;
  NonJavaThread* _current;

  NONCOPYABLE(Iterator);

public:
  Iterator();
  ~Iterator();

  bool end() const { return _current == nullptr; }
  NonJavaThread* current() const { return _current; }
  void step();
};

// A base class for non-JavaThread subclasses with multiple
// uniquely named instances. NamedThreads also provide a common
// location to store GC information needed by GC threads
// and the VMThread.
class NamedThread: public NonJavaThread {
  friend class VMStructs;
  enum {
    max_name_len = 64
  };
 private:
  char* _name;
  // log Thread being processed by oops_do
  Thread* _processed_thread;
  uint _gc_id; // The current GC id when a thread takes part in GC

 public:
  NamedThread();
  ~NamedThread();
  // May only be called once per thread.
  void set_name(const char* format, ...)  ATTRIBUTE_PRINTF(2, 3);
  virtual bool is_Named_thread() const { return true; }
  virtual const char* name() const { return _name == nullptr ? "Unknown Thread" : _name; }
  virtual const char* type_name() const { return "NamedThread"; }
  Thread *processed_thread() { return _processed_thread; }
  void set_processed_thread(Thread *thread) { _processed_thread = thread; }
  virtual void print_on(outputStream* st) const;

  void set_gc_id(uint gc_id) { _gc_id = gc_id; }
  uint gc_id() { return _gc_id; }
};

// A single WatcherThread is used for simulating timer interrupts.
class WatcherThread: public NonJavaThread {
  friend class VMStructs;
 protected:
  virtual void run();

 private:
  static WatcherThread* _watcher_thread;

  static bool _run_all_tasks;
  // volatile due to at least one lock-free read
  volatile static bool _should_terminate;
 public:
  // Constructor
  WatcherThread();

  // No destruction allowed
  ~WatcherThread() {
    guarantee(false, "WatcherThread deletion must fix the race with VM termination");
  }

  // Tester
  bool is_Watcher_thread() const                 { return true; }

  // Printing
  const char* name() const { return "VM Periodic Task Thread"; }
  const char* type_name() const { return "WatcherThread"; }
  void print_on(outputStream* st) const;
  void unpark();

  // Returns the single instance of WatcherThread
  static WatcherThread* watcher_thread()         { return _watcher_thread; }

  // Create and start the single instance of WatcherThread, or stop it on shutdown
  static void start();
  static void stop();
  // Allow executing registered tasks once the VM is sufficiently
  // initialized. Meanwhile only error reporting will be checked.
  static void run_all_tasks();
 private:
  int sleep() const;
};

#endif // SHARE_RUNTIME_NONJAVATHREAD_HPP
