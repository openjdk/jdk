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

#ifndef SHARE_SERVICES_FINALIZERTABLE_HPP
#define SHARE_SERVICES_FINALIZERTABLE_HPP

#include "memory/allocation.hpp"

class instanceHandle;
class InstanceKlass;
class JavaThread;
class Thread;

class FinalizerEntry : public CHeapObj<mtClass> {
 private:
  const InstanceKlass* const _ik;
  uint64_t _completed;
  uint64_t _registered;
 public:
  FinalizerEntry(const InstanceKlass* ik) : _ik(ik), _completed(0), _registered(0) {}
  const InstanceKlass* klass() const NOT_MANAGEMENT_RETURN_(nullptr);
  uint64_t completed() const NOT_MANAGEMENT_RETURN_(0L);
  uint64_t registered() const NOT_MANAGEMENT_RETURN_(0L);
  void on_register() NOT_MANAGEMENT_RETURN;
  void on_complete() NOT_MANAGEMENT_RETURN;
};

class FinalizerEntryClosure : public StackObj {
 public:
  virtual bool do_entry(const FinalizerEntry* fe) = 0;
};

class FinalizerTable : AllStatic {
  friend class ServiceThread;
 private:
  static bool has_work() NOT_MANAGEMENT_RETURN_(false);
  static void do_concurrent_work(JavaThread* service_thread) NOT_MANAGEMENT_RETURN;;
 public:
  static bool create_table() NOT_MANAGEMENT_RETURN_(false);
  static void rehash_table() NOT_MANAGEMENT_RETURN;
  static bool needs_rehashing() NOT_MANAGEMENT_RETURN_(false);
  static void purge_unloaded() NOT_MANAGEMENT_RETURN;
  static void on_complete(const instanceHandle& i, JavaThread* finalizerThread) NOT_MANAGEMENT_RETURN;
  static void on_register(const instanceHandle& i, Thread* thread) NOT_MANAGEMENT_RETURN;
  static void do_entries(FinalizerEntryClosure* closure, Thread* thread) NOT_MANAGEMENT_RETURN;
  static const FinalizerEntry* lookup(const InstanceKlass* ik, Thread* thread) NOT_MANAGEMENT_RETURN_(nullptr);
};

#endif // SHARE_SERVICES_FINALIZERTABLE_HPP
