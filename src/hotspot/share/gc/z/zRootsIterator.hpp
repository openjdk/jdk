/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/oopStorageSetParState.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "runtime/threadSMR.hpp"

class ZRootsIteratorClosure;

typedef OopStorageSetStrongParState<true /* concurrent */, false /* is_const */> ZOopStorageSetStrongIterator;
typedef OopStorageSetWeakParState<true /* concurrent */, false /* is_const */> ZOopStorageSetWeakIterator;

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

class ZRootsIteratorClosure : public OopClosure {
public:
  virtual void do_thread(Thread* thread) {}

  virtual bool should_disarm_nmethods() const {
    return false;
  }
};

class ZJavaThreadsIterator {
private:
  ThreadsListHandle _threads;
  volatile uint     _claimed;

  uint claim();

public:
  ZJavaThreadsIterator();

  void threads_do(ThreadClosure* cl);
};

class ZRelocateRoots : public AllStatic {
public:
  static void oops_do(OopClosure* cl);
};

class ZConcurrentRootsIterator {
private:
  ZOopStorageSetStrongIterator _oop_storage_set_iter;
  ZJavaThreadsIterator         _java_threads_iter;
  const int                    _cld_claim;

  void do_oop_storage_set(ZRootsIteratorClosure* cl);
  void do_java_threads(ZRootsIteratorClosure* cl);
  void do_class_loader_data_graph(ZRootsIteratorClosure* cl);
  void do_code_cache(ZRootsIteratorClosure* cl);

  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_oop_storage_set>         _oop_storage_set;
  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_class_loader_data_graph> _class_loader_data_graph;
  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_java_threads>            _java_threads;
  ZParallelOopsDo<ZConcurrentRootsIterator, &ZConcurrentRootsIterator::do_code_cache>              _code_cache;

public:
  ZConcurrentRootsIterator(int cld_claim);
  ~ZConcurrentRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl);
};

class ZConcurrentRootsIteratorClaimStrong : public ZConcurrentRootsIterator {
public:
  ZConcurrentRootsIteratorClaimStrong() :
      ZConcurrentRootsIterator(ClassLoaderData::_claim_strong) {}
};

class ZConcurrentRootsIteratorClaimOther : public ZConcurrentRootsIterator {
public:
  ZConcurrentRootsIteratorClaimOther() :
      ZConcurrentRootsIterator(ClassLoaderData::_claim_other) {}
};

class ZConcurrentRootsIteratorClaimNone : public ZConcurrentRootsIterator {
public:
  ZConcurrentRootsIteratorClaimNone() :
      ZConcurrentRootsIterator(ClassLoaderData::_claim_none) {}
};

class ZWeakRootsIterator {
private:
  void do_jvmti_weak_export(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);

  ZSerialWeakOopsDo<ZWeakRootsIterator, &ZWeakRootsIterator::do_jvmti_weak_export> _jvmti_weak_export;

public:
  ZWeakRootsIterator();

  void weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl);
  void oops_do(ZRootsIteratorClosure* cl);
};

class ZConcurrentWeakRootsIterator {
private:
  ZOopStorageSetWeakIterator _oop_storage_set_iter;

  void do_oop_storage_set(ZRootsIteratorClosure* cl);

  ZParallelOopsDo<ZConcurrentWeakRootsIterator, &ZConcurrentWeakRootsIterator::do_oop_storage_set> _oop_storage_set;

public:
  ZConcurrentWeakRootsIterator();

  void oops_do(ZRootsIteratorClosure* cl);

  void report_num_dead();
};

#endif // SHARE_GC_Z_ZROOTSITERATOR_HPP
