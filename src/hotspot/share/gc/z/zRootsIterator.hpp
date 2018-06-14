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
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/globalDefinitions.hpp"

typedef OopStorage::ParState<false /* concurrent */, false /* is_const */> ZOopStorageIterator;
typedef OopStorage::ParState<true /* concurrent */, false /* is_const */>  ZConcurrentOopStorageIterator;

template <typename T, void (T::*F)(OopClosure*)>
class ZSerialOopsDo {
private:
  T* const      _iter;
  volatile bool _claimed;

public:
  ZSerialOopsDo(T* iter);
  void oops_do(OopClosure* cl);
};

template <typename T, void (T::*F)(OopClosure*)>
class ZParallelOopsDo {
private:
  T* const      _iter;
  volatile bool _completed;

public:
  ZParallelOopsDo(T* iter);
  void oops_do(OopClosure* cl);
};

template <typename T, void (T::*F)(BoolObjectClosure*, OopClosure*)>
class ZSerialWeakOopsDo {
private:
  T* const      _iter;
  volatile bool _claimed;

public:
  ZSerialWeakOopsDo(T* iter);
  void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* cl);
};

template <typename T, void (T::*F)(BoolObjectClosure*, OopClosure*)>
class ZParallelWeakOopsDo {
private:
  T* const      _iter;
  volatile bool _completed;

public:
  ZParallelWeakOopsDo(T* iter);
  void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* cl);
};

class ZRootsIterator {
private:
  ZOopStorageIterator _vm_weak_handles_iter;
  ZOopStorageIterator _jni_handles_iter;
  ZOopStorageIterator _jni_weak_handles_iter;
  ZOopStorageIterator _string_table_iter;

  void do_universe(OopClosure* cl);
  void do_vm_weak_handles(OopClosure* cl);
  void do_jni_handles(OopClosure* cl);
  void do_jni_weak_handles(OopClosure* cl);
  void do_object_synchronizer(OopClosure* cl);
  void do_management(OopClosure* cl);
  void do_jvmti_export(OopClosure* cl);
  void do_jvmti_weak_export(OopClosure* cl);
  void do_jfr_weak(OopClosure* cl);
  void do_system_dictionary(OopClosure* cl);
  void do_class_loader_data_graph(OopClosure* cl);
  void do_threads(OopClosure* cl);
  void do_code_cache(OopClosure* cl);
  void do_string_table(OopClosure* cl);

  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_universe>                  _universe;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_object_synchronizer>       _object_synchronizer;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_management>                _management;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_jvmti_export>              _jvmti_export;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_jvmti_weak_export>         _jvmti_weak_export;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_jfr_weak>                  _jfr_weak;
  ZSerialOopsDo<ZRootsIterator, &ZRootsIterator::do_system_dictionary>         _system_dictionary;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_vm_weak_handles>         _vm_weak_handles;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_jni_handles>             _jni_handles;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_jni_weak_handles>        _jni_weak_handles;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_class_loader_data_graph> _class_loader_data_graph;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_threads>                 _threads;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_code_cache>              _code_cache;
  ZParallelOopsDo<ZRootsIterator, &ZRootsIterator::do_string_table>            _string_table;

public:
  ZRootsIterator();
  ~ZRootsIterator();

  void oops_do(OopClosure* cl, bool visit_jvmti_weak_export = false);
};

class ZWeakRootsIterator {
private:
  ZOopStorageIterator _vm_weak_handles_iter;
  ZOopStorageIterator _jni_weak_handles_iter;
  ZOopStorageIterator _string_table_iter;

  void do_vm_weak_handles(BoolObjectClosure* is_alive, OopClosure* cl);
  void do_jni_weak_handles(BoolObjectClosure* is_alive, OopClosure* cl);
  void do_jvmti_weak_export(BoolObjectClosure* is_alive, OopClosure* cl);
  void do_jfr_weak(BoolObjectClosure* is_alive, OopClosure* cl);
  void do_symbol_table(BoolObjectClosure* is_alive, OopClosure* cl);
  void do_string_table(BoolObjectClosure* is_alive, OopClosure* cl);

  ZSerialWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jvmti_weak_export>  _jvmti_weak_export;
  ZSerialWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jfr_weak>           _jfr_weak;
  ZParallelWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_vm_weak_handles>  _vm_weak_handles;
  ZParallelWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jni_weak_handles> _jni_weak_handles;
  ZParallelWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_symbol_table>     _symbol_table;
  ZParallelWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_string_table>     _string_table;

public:
  ZWeakRootsIterator();
  ~ZWeakRootsIterator();

  void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* cl);
  void oops_do(OopClosure* cl);
};

class ZConcurrentWeakRootsIterator {
private:
  ZConcurrentOopStorageIterator _vm_weak_handles_iter;
  ZConcurrentOopStorageIterator _jni_weak_handles_iter;
  ZConcurrentOopStorageIterator _string_table_iter;

  void do_vm_weak_handles(OopClosure* cl);
  void do_jni_weak_handles(OopClosure* cl);
  void do_string_table(OopClosure* cl);

  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_vm_weak_handles>  _vm_weak_handles;
  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_jni_weak_handles> _jni_weak_handles;
  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_string_table>     _string_table;

public:
  ZConcurrentWeakRootsIterator();
  ~ZConcurrentWeakRootsIterator();

  void oops_do(OopClosure* cl);
};

class ZThreadRootsIterator {
private:
  void do_threads(OopClosure* cl);

  ZParallelOopsDo<ZThreadRootsIterator, &ZThreadRootsIterator::do_threads> _threads;

public:
  ZThreadRootsIterator();
  ~ZThreadRootsIterator();

  void oops_do(OopClosure* cl);
};

#endif // SHARE_GC_Z_ZROOTSITERATOR_HPP
