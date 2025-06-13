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

#ifndef SHARE_RUNTIME_LIGHTWEIGHTSYNCHRONIZER_HPP
#define SHARE_RUNTIME_LIGHTWEIGHTSYNCHRONIZER_HPP

#include "memory/allStatic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/synchronizer.hpp"

class ObjectMonitorTable;

class LightweightSynchronizer : AllStatic {
 private:
  static ObjectMonitor* get_or_insert_monitor_from_table(oop object, JavaThread* current, bool* inserted);
  static ObjectMonitor* get_or_insert_monitor(oop object, JavaThread* current, ObjectSynchronizer::InflateCause cause);

  static ObjectMonitor* add_monitor(JavaThread* current, ObjectMonitor* monitor, oop obj);
  static bool remove_monitor(Thread* current, ObjectMonitor* monitor, oop obj);

  static void deflate_mark_word(oop object);

  static void ensure_lock_stack_space(JavaThread* current);

  class CacheSetter;
  class LockStackInflateContendedLocks;
  class VerifyThreadState;

 public:
  static void initialize();

  static bool needs_resize();
  static bool resize_table(JavaThread* current);

 private:
  static inline bool fast_lock_try_enter(oop obj, LockStack& lock_stack, JavaThread* current);
  static bool fast_lock_spin_enter(oop obj, LockStack& lock_stack, JavaThread* current, bool observed_deflation);

 public:
  static void enter_for(Handle obj, BasicLock* lock, JavaThread* locking_thread);
  static void enter(Handle obj, BasicLock* lock, JavaThread* current);
  static void exit(oop object, BasicLock* lock, JavaThread* current);

  static ObjectMonitor* inflate_into_object_header(oop object, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, Thread* current);
  static ObjectMonitor* inflate_locked_or_imse(oop object, ObjectSynchronizer::InflateCause cause, TRAPS);
  static ObjectMonitor* inflate_fast_locked_object(oop object, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, JavaThread* current);
  static ObjectMonitor* inflate_and_enter(oop object, BasicLock* lock, ObjectSynchronizer::InflateCause cause, JavaThread* locking_thread, JavaThread* current);

  static void deflate_monitor(Thread* current, oop obj, ObjectMonitor* monitor);

  static ObjectMonitor* get_monitor_from_table(Thread* current, oop obj);

  static bool contains_monitor(Thread* current, ObjectMonitor* monitor);

  static bool quick_enter(oop obj, BasicLock* Lock, JavaThread* current);
};

#endif // SHARE_RUNTIME_LIGHTWEIGHTSYNCHRONIZER_HPP
