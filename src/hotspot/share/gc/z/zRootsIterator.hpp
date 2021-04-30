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

#include "gc/shared/oopStorageSetParState.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "runtime/threadSMR.hpp"

template <typename Iterator>
class ZParallelApply {
private:
  Iterator      _iter;
  volatile bool _completed;

public:
  ZParallelApply() :
      _iter(),
      _completed(false) {}

  template <typename ClosureType>
  void apply(ClosureType* cl);

  Iterator& iter() {
    return _iter;
  }
};

class ZOopStorageSetIteratorStrong {
  OopStorageSetStrongParState<true /* concurrent */, false /* is_const */> _iter;

public:
  ZOopStorageSetIteratorStrong();

  void apply(OopClosure* cl);
};

class ZOopStorageSetIteratorWeak {
private:
  OopStorageSetWeakParState<true /* concurrent */, false /* is_const */> _iter;

public:
  ZOopStorageSetIteratorWeak();

  void apply(OopClosure* cl);

  void report_num_dead();
};

class ZCLDsIteratorStrong {
public:
  void apply(CLDClosure* cl);
};

class ZCLDsIteratorWeak {
public:
  void apply(CLDClosure* cl);
};

class ZCLDsIteratorAll {
public:
  void apply(CLDClosure* cl);
};

class ZJavaThreadsIterator {
private:
  ThreadsListHandle _threads;
  volatile uint     _claimed;

  uint claim();

public:
  ZJavaThreadsIterator();

  void apply(ThreadClosure* cl);
};

class ZNMethodsIteratorImpl {
private:
  const bool _enabled;
  const bool _secondary;

protected:
  ZNMethodsIteratorImpl(bool enabled, bool secondary);
  ~ZNMethodsIteratorImpl();

public:
  void apply(NMethodClosure* cl);
};

class ZNMethodsIteratorStrong : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorStrong() :
      ZNMethodsIteratorImpl(!ClassUnloading /* enabled */, false /* secondary */) {}
};

class ZNMethodsIteratorWeak : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorWeak() :
      ZNMethodsIteratorImpl(true /* enabled */, true /* secondary */) {}
};

class ZNMethodsIteratorAll : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorAll() :
      ZNMethodsIteratorImpl(true /* enabled */, true /* secondary */) {}
};

class ZRootsIteratorStrongUncolored {
private:
  ZParallelApply<ZJavaThreadsIterator>    _java_threads;
  ZParallelApply<ZNMethodsIteratorStrong> _nmethods_strong;

public:
  void apply(ThreadClosure* thread_cl,
             NMethodClosure* nm_cl);
};

class ZRootsIteratorWeakUncolored {
private:
  ZParallelApply<ZNMethodsIteratorWeak> _nmethods_weak;

public:
  void apply(NMethodClosure* nm_cl);
};

class ZRootsIteratorAllUncolored {
private:
  ZParallelApply<ZJavaThreadsIterator> _java_threads;
  ZParallelApply<ZNMethodsIteratorAll> _nmethods_all;

public:
  void apply(ThreadClosure* thread_cl,
             NMethodClosure* nm_cl);
};

class ZRootsIteratorStrongColored {
private:
  ZParallelApply<ZOopStorageSetIteratorStrong> _oop_storage_set_strong;
  ZParallelApply<ZCLDsIteratorStrong>          _clds_strong;

public:
  void apply(OopClosure* cl,
             CLDClosure* cld_cl);
};

class ZRootsIteratorWeakColored {
private:
  ZParallelApply<ZOopStorageSetIteratorWeak> _oop_storage_set_weak;

public:
  void apply(OopClosure* cl);

  void report_num_dead();
};

class ZRootsIteratorAllColored {
private:
  ZParallelApply<ZOopStorageSetIteratorStrong> _oop_storage_set_strong;
  ZParallelApply<ZOopStorageSetIteratorWeak>   _oop_storage_set_weak;
  ZParallelApply<ZCLDsIteratorAll>             _clds_all;

public:
  void apply(OopClosure* cl,
             CLDClosure* cld_cl);
};

#endif // SHARE_GC_Z_ZROOTSITERATOR_HPP
