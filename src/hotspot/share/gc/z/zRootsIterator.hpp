/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGenerationId.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "runtime/threadSMR.hpp"

template <typename Iterator>
class ZParallelApply {
private:
  Iterator      _iter;
  volatile bool _completed;

public:
  ZParallelApply(ZGenerationIdOptional generation)
    : _iter(generation),
      _completed(false) {}

  template <typename ClosureType>
  void apply(ClosureType* cl);

  Iterator& iter() {
    return _iter;
  }
};

class ZOopStorageSetIteratorStrong {
private:
  OopStorageSetStrongParState<true /* concurrent */, false /* is_const */> _iter;
  const ZGenerationIdOptional _generation;

public:
  ZOopStorageSetIteratorStrong(ZGenerationIdOptional generation)
    : _iter(),
      _generation(generation) {}

  void apply(OopClosure* cl);
};

class ZOopStorageSetIteratorWeak {
private:
  OopStorageSetWeakParState<true /* concurrent */, false /* is_const */> _iter;
  const ZGenerationIdOptional _generation;

public:
  ZOopStorageSetIteratorWeak(ZGenerationIdOptional generation)
    : _iter(),
      _generation(generation) {}

  void apply(OopClosure* cl);

  void report_num_dead();
};

class ZCLDsIteratorStrong {
private:
  const ZGenerationIdOptional _generation;

public:
  ZCLDsIteratorStrong(ZGenerationIdOptional generation)
    : _generation(generation) {}

  void apply(CLDClosure* cl);
};

class ZCLDsIteratorWeak {
private:
  const ZGenerationIdOptional _generation;

public:
  ZCLDsIteratorWeak(ZGenerationIdOptional generation)
    : _generation(generation) {}

  void apply(CLDClosure* cl);
};

class ZCLDsIteratorAll {
private:
  const ZGenerationIdOptional _generation;

public:
  ZCLDsIteratorAll(ZGenerationIdOptional generation)
    : _generation(generation) {}

  void apply(CLDClosure* cl);
};

class ZJavaThreadsIterator {
private:
  ThreadsListHandle           _threads;
  volatile uint               _claimed;
  const ZGenerationIdOptional _generation;

  uint claim();

public:
  ZJavaThreadsIterator(ZGenerationIdOptional generation)
    : _threads(),
      _claimed(0),
      _generation(generation) {}

  void apply(ThreadClosure* cl);
};

class ZNMethodsIteratorImpl {
private:
  const bool                  _enabled;
  const bool                  _secondary;
  const ZGenerationIdOptional _generation;

protected:
  ZNMethodsIteratorImpl(ZGenerationIdOptional generation, bool enabled, bool secondary);
  ~ZNMethodsIteratorImpl();

public:
  void apply(NMethodClosure* cl);
};

class ZNMethodsIteratorStrong : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorStrong(ZGenerationIdOptional generation)
    : ZNMethodsIteratorImpl(generation, !ClassUnloading /* enabled */, false /* secondary */) {}
};

class ZNMethodsIteratorWeak : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorWeak(ZGenerationIdOptional generation)
    : ZNMethodsIteratorImpl(generation, true /* enabled */, true /* secondary */) {}
};

class ZNMethodsIteratorAll : public ZNMethodsIteratorImpl {
public:
  ZNMethodsIteratorAll(ZGenerationIdOptional generation)
    : ZNMethodsIteratorImpl(generation, true /* enabled */, true /* secondary */) {}
};

class ZRootsIteratorStrongUncolored {
private:
  ZParallelApply<ZJavaThreadsIterator>    _java_threads;
  ZParallelApply<ZNMethodsIteratorStrong> _nmethods_strong;

public:
  ZRootsIteratorStrongUncolored(ZGenerationIdOptional generation)
    : _java_threads(generation),
      _nmethods_strong(generation) {}

  void apply(ThreadClosure* thread_cl,
             NMethodClosure* nm_cl);
};

class ZRootsIteratorWeakUncolored {
private:
  ZParallelApply<ZNMethodsIteratorWeak> _nmethods_weak;

public:
  ZRootsIteratorWeakUncolored(ZGenerationIdOptional generation)
    : _nmethods_weak(generation) {}

  void apply(NMethodClosure* nm_cl);
};

class ZRootsIteratorAllUncolored {
private:
  ZParallelApply<ZJavaThreadsIterator> _java_threads;
  ZParallelApply<ZNMethodsIteratorAll> _nmethods_all;

public:
  ZRootsIteratorAllUncolored(ZGenerationIdOptional generation)
    : _java_threads(generation),
      _nmethods_all(generation) {}

  void apply(ThreadClosure* thread_cl,
             NMethodClosure* nm_cl);
};

class ZRootsIteratorStrongColored {
private:
  ZParallelApply<ZOopStorageSetIteratorStrong> _oop_storage_set_strong;
  ZParallelApply<ZCLDsIteratorStrong>          _clds_strong;

public:
  ZRootsIteratorStrongColored(ZGenerationIdOptional generation)
    : _oop_storage_set_strong(generation),
      _clds_strong(generation) {}

  void apply(OopClosure* cl,
             CLDClosure* cld_cl);
};

class ZRootsIteratorWeakColored {
private:
  ZParallelApply<ZOopStorageSetIteratorWeak> _oop_storage_set_weak;

public:
  ZRootsIteratorWeakColored(ZGenerationIdOptional generation)
    : _oop_storage_set_weak(generation) {}

  void apply(OopClosure* cl);

  void report_num_dead();
};

class ZRootsIteratorAllColored {
private:
  ZParallelApply<ZOopStorageSetIteratorStrong> _oop_storage_set_strong;
  ZParallelApply<ZOopStorageSetIteratorWeak>   _oop_storage_set_weak;
  ZParallelApply<ZCLDsIteratorAll>             _clds_all;

public:
  ZRootsIteratorAllColored(ZGenerationIdOptional generation)
    : _oop_storage_set_strong(generation),
      _oop_storage_set_weak(generation),
      _clds_all(generation) {}

  void apply(OopClosure* cl,
             CLDClosure* cld_cl);
};

#endif // SHARE_GC_Z_ZROOTSITERATOR_HPP
