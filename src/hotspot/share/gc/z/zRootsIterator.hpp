/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZROOTSITERATOR_HPP
#define SHARE_GC_Z_ZROOTSITERATOR_HPP

#include "gc/shared/oopStorageParState.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalDefinitions.hpp"

class ZRootsIteratorClosure : public OopClosure {
public:
  virtual void do_thread(Thread* thread) {}
};

typedef OopStorage::ParState<true /* concurrent */, false /* is_const */> ZOopStorageIterator;

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
class ZSerialOopsDo {
private:
  T* const      _iter;
  volatile bool _claimed;

public:
  ZSerialOopsDo(T* iter);
  void oops_do(ZRootsIteratorClosure* cl);
};

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
class ZParallelOopsDo {
private:
  T* const      _iter;
  volatile bool _completed;

public:
  ZParallelOopsDo(T* iter);
  void oops_do(ZRootsIteratorClosure* cl);
};

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
class ZSerialWeakOopsDo {
private:
  T* const      _iter;
  volatile bool _claimed;

public:
  ZSerialWeakOopsDo(T* iter);
  void weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);
};

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
class ZParallelWeakOopsDo {
private:
  T* const      _iter;
  volatile bool _completed;

public:
  ZParallelWeakOopsDo(T* iter);
  void weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);
};

class ZRootsIterator {
private:
  void do_universe(ZRootsIteratorClosure* cl);
  void do_object_synchronizer(ZRootsIteratorClosure* cl);
  void do_management(ZRootsIteratorClosure* cl);
  void do_jvmti_export(ZRootsIteratorClosure* cl);
  void do_jvmti_weak_export(ZRootsIteratorClosure* cl);
  void do_system_dictionary(ZRootsIteratorClosure* cl);
  void do_threads(ZRootsIteratorClosure* cl);
  void do_code_cache(ZRootsIteratorClosure* cl);

  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_universe>            _universe;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_object_synchronizer> _object_synchronizer;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_management>          _management;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_jvmti_export>        _jvmti_export;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_jvmti_weak_export>   _jvmti_weak_export;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_system_dictionary>   _system_dictionary;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_threads>           _threads;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_code_cache>        _code_cache;

public:
  ZRootsIterator();
  ~ZRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl, bool visit_jvmti_weak_export = false);
};

class ZConcurrentRootsIterator {
private:
  const bool                 _marking;
  SuspendibleThreadSetJoiner _sts_joiner;
  ZOopStorageIterator        _jni_handles_iter;

  void do_jni_handles(ZRootsIteratorClosure* cl);
  void do_class_loader_data_graph(ZRootsIteratorClosure* cl);

  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_jni_handles>             _jni_handles;
  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_class_loader_data_graph> _class_loader_data_graph;

public:
  ZConcurrentRootsIterator(bool marking = false);
  ~ZConcurrentRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl);
};

class ZWeakRootsIterator {
private:
  void do_jvmti_weak_export(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);
  void do_jfr_weak(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);

  ZSerialWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jvmti_weak_export>  _jvmti_weak_export;
  ZSerialWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jfr_weak>           _jfr_weak;

public:
  ZWeakRootsIterator();
  ~ZWeakRootsIterator();

  void weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);
  void oops_do(ZRootsIteratorClosure* cl);
};

class ZConcurrentWeakRootsIterator {
private:
  ZOopStorageIterator _vm_weak_handles_iter;
  ZOopStorageIterator _jni_weak_handles_iter;
  ZOopStorageIterator _string_table_iter;

  void do_vm_weak_handles(ZRootsIteratorClosure* cl);
  void do_jni_weak_handles(ZRootsIteratorClosure* cl);
  void do_string_table(ZRootsIteratorClosure* cl);

  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_vm_weak_handles>  _vm_weak_handles;
  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_jni_weak_handles> _jni_weak_handles;
  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_string_table>     _string_table;

public:
  ZConcurrentWeakRootsIterator();
  ~ZConcurrentWeakRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl);
};

class ZThreadRootsIterator {
private:
  void do_threads(ZRootsIteratorClosure* cl);

  ZParallelOopsDo<ZThreadRootsIterator, &ZThreadRootsIterator::do_threads> _threads;

public:
  ZThreadRootsIterator();
  ~ZThreadRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl);
};

#endif // SHARE_GC_Z_ZROOTSITERATOR_HPP
