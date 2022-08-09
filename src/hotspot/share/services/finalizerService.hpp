/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_FINALIZERSERVICE_HPP
#define SHARE_SERVICES_FINALIZERSERVICE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

class InstanceKlass;
class JavaThread;
class Thread;

class FinalizerEntry : public CHeapObj<mtServiceability> {
 private:
  const InstanceKlass* const _ik;
  const char* _codesource;
  uintptr_t _objects_on_heap;
  uintptr_t _total_finalizers_run;
 public:
  FinalizerEntry(const InstanceKlass* ik);
  ~FinalizerEntry();
  const InstanceKlass* klass() const NOT_MANAGEMENT_RETURN_(nullptr);
  const char* codesource() const NOT_MANAGEMENT_RETURN_(nullptr);
  uintptr_t objects_on_heap() const NOT_MANAGEMENT_RETURN_(0L);
  uintptr_t total_finalizers_run() const NOT_MANAGEMENT_RETURN_(0L);
  void on_register() NOT_MANAGEMENT_RETURN;
  void on_complete() NOT_MANAGEMENT_RETURN;
};

class FinalizerEntryClosure : public StackObj {
 public:
  virtual bool do_entry(const FinalizerEntry* fe) = 0;
};

class FinalizerService : AllStatic {
  friend class ServiceThread;
 private:
  static bool has_work() NOT_MANAGEMENT_RETURN_(false);
  static void do_concurrent_work(JavaThread* service_thread) NOT_MANAGEMENT_RETURN;
 public:
  static void init() NOT_MANAGEMENT_RETURN;
  static void purge_unloaded() NOT_MANAGEMENT_RETURN;
  static void on_register(oop finalizee, Thread* thread) NOT_MANAGEMENT_RETURN;
  static void on_complete(oop finalizee, JavaThread* finalizer_thread) NOT_MANAGEMENT_RETURN;
  static void do_entries(FinalizerEntryClosure* closure, Thread* thread) NOT_MANAGEMENT_RETURN;
  static const FinalizerEntry* lookup(const InstanceKlass* ik, Thread* thread) NOT_MANAGEMENT_RETURN_(nullptr);
};

#endif // SHARE_SERVICES_FINALIZERSERVICE_HPP
