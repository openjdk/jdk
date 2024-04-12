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

#ifndef SHARE_GC_X_XROOTSITERATOR_HPP
#define SHARE_GC_X_XROOTSITERATOR_HPP

#include "gc/shared/oopStorageSetParState.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "runtime/threadSMR.hpp"

template <typename Iterator>
class XParallelApply {
private:
  Iterator      _iter;
  volatile bool _completed;

public:
  XParallelApply() :
      _iter(),
      _completed(false) {}

  template <typename ClosureType>
  void apply(ClosureType* cl);

  Iterator& iter() {
    return _iter;
  }
};

class XStrongOopStorageSetIterator {
  OopStorageSetStrongParState<true /* concurrent */, false /* is_const */> _iter;

public:
  XStrongOopStorageSetIterator();

  void apply(OopClosure* cl);
};

class XStrongCLDsIterator {
public:
  void apply(CLDClosure* cl);
};

class XJavaThreadsIterator {
private:
  ThreadsListHandle _threads;
  volatile uint     _claimed;

  uint claim();

public:
  XJavaThreadsIterator();

  void apply(ThreadClosure* cl);
};

class XNMethodsIterator {
public:
  XNMethodsIterator();
  ~XNMethodsIterator();

  void apply(NMethodClosure* cl);
};

class XRootsIterator {
private:
  XParallelApply<XStrongOopStorageSetIterator> _oop_storage_set;
  XParallelApply<XStrongCLDsIterator>          _class_loader_data_graph;
  XParallelApply<XJavaThreadsIterator>         _java_threads;
  XParallelApply<XNMethodsIterator>            _nmethods;

public:
  XRootsIterator(int cld_claim);

  void apply(OopClosure* cl,
             CLDClosure* cld_cl,
             ThreadClosure* thread_cl,
             NMethodClosure* nm_cl);
};

class XWeakOopStorageSetIterator {
private:
  OopStorageSetWeakParState<true /* concurrent */, false /* is_const */> _iter;

public:
  XWeakOopStorageSetIterator();

  void apply(OopClosure* cl);

  void report_num_dead();
};

class XWeakRootsIterator {
private:
  XParallelApply<XWeakOopStorageSetIterator> _oop_storage_set;

public:
  void apply(OopClosure* cl);

  void report_num_dead();
};

#endif // SHARE_GC_X_XROOTSITERATOR_HPP
