/*
 * Copyright (c) 2002, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_GCTASKTHREAD_HPP
#define SHARE_GC_PARALLEL_GCTASKTHREAD_HPP

#include "runtime/thread.hpp"

// Forward declarations of classes defined here.
class GCTaskThread;
class GCTaskTimeStamp;

// Declarations of classes referenced in this file via pointer.
class GCTaskManager;

class GCTaskThread : public WorkerThread {
  friend class GCTaskManager;
private:
  // Instance state.
  GCTaskManager* _manager;              // Manager for worker.
  const uint     _processor_id;         // Which processor the worker is on.

  GCTaskTimeStamp* _time_stamps;
  uint _time_stamp_index;

  GCTaskTimeStamp* time_stamp_at(uint index);
  void add_task_timestamp(const char* name, jlong t_entry, jlong t_exit);

  // Factory create and destroy methods.
  static GCTaskThread* create(GCTaskManager* manager,
                              uint           which,
                              uint           processor_id) {
    return new GCTaskThread(manager, which, processor_id);
  }
 public:

  static void destroy(GCTaskThread* manager) {
    if (manager != NULL) {
      delete manager;
    }
  }
  // Methods from Thread.
  bool is_GC_task_thread() const {
    return true;
  }
  virtual void run();

  void print_task_time_stamps();

protected:
  // Constructor.  Clients use factory, but there could be subclasses.
  GCTaskThread(GCTaskManager* manager, uint which, uint processor_id);
  // Destructor: virtual destructor because of virtual methods.
  virtual ~GCTaskThread();
  // Accessors.
  GCTaskManager* manager() const {
    return _manager;
  }
  uint which() const {
    return id();
  }
  uint processor_id() const {
    return _processor_id;
  }
};

class GCTaskTimeStamp : public CHeapObj<mtGC>
{
 private:
  jlong  _entry_time;
  jlong  _exit_time;
  const char*  _name;

 public:
  jlong entry_time()              { return _entry_time; }
  jlong exit_time()               { return _exit_time; }
  const char* name() const        { return _name; }

  void set_entry_time(jlong time) { _entry_time = time; }
  void set_exit_time(jlong time)  { _exit_time = time; }
  void set_name(const char* name) { _name = name; }
};

#endif // SHARE_GC_PARALLEL_GCTASKTHREAD_HPP
