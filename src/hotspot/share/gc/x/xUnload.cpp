/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeBehaviours.hpp"
#include "code/codeCache.hpp"
#include "code/dependencyContext.hpp"
#include "gc/shared/gcBehaviours.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xLock.inline.hpp"
#include "gc/x/xNMethod.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xUnload.hpp"
#include "memory/metaspaceUtils.hpp"
#include "oops/access.inline.hpp"

static const XStatSubPhase XSubPhaseConcurrentClassesUnlink("Concurrent Classes Unlink");
static const XStatSubPhase XSubPhaseConcurrentClassesPurge("Concurrent Classes Purge");

class XPhantomIsAliveObjectClosure : public BoolObjectClosure {
public:
  virtual bool do_object_b(oop o) {
    return XBarrier::is_alive_barrier_on_phantom_oop(o);
  }
};

class XIsUnloadingOopClosure : public OopClosure {
private:
  XPhantomIsAliveObjectClosure _is_alive;
  bool                         _is_unloading;

public:
  XIsUnloadingOopClosure() :
      _is_alive(),
      _is_unloading(false) {}

  virtual void do_oop(oop* p) {
    const oop o = RawAccess<>::oop_load(p);
    if (o != nullptr && !_is_alive.do_object_b(o)) {
      _is_unloading = true;
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  bool is_unloading() const {
    return _is_unloading;
  }
};

class XIsUnloadingBehaviour : public IsUnloadingBehaviour {
public:
  virtual bool has_dead_oop(CompiledMethod* method) const {
    nmethod* const nm = method->as_nmethod();
    XReentrantLock* const lock = XNMethod::lock_for_nmethod(nm);
    XLocker<XReentrantLock> locker(lock);
    XIsUnloadingOopClosure cl;
    XNMethod::nmethod_oops_do_inner(nm, &cl);
    return cl.is_unloading();
  }
};

class XCompiledICProtectionBehaviour : public CompiledICProtectionBehaviour {
public:
  virtual bool lock(CompiledMethod* method) {
    nmethod* const nm = method->as_nmethod();
    XReentrantLock* const lock = XNMethod::lock_for_nmethod(nm);
    lock->lock();
    return true;
  }

  virtual void unlock(CompiledMethod* method) {
    nmethod* const nm = method->as_nmethod();
    XReentrantLock* const lock = XNMethod::lock_for_nmethod(nm);
    lock->unlock();
  }

  virtual bool is_safe(CompiledMethod* method) {
    if (SafepointSynchronize::is_at_safepoint() || method->is_unloading()) {
      return true;
    }

    nmethod* const nm = method->as_nmethod();
    XReentrantLock* const lock = XNMethod::lock_for_nmethod(nm);
    return lock->is_owned();
  }
};

XUnload::XUnload(XWorkers* workers) :
    _workers(workers) {

  if (!ClassUnloading) {
    return;
  }

  static XIsUnloadingBehaviour is_unloading_behaviour;
  IsUnloadingBehaviour::set_current(&is_unloading_behaviour);

  static XCompiledICProtectionBehaviour ic_protection_behaviour;
  CompiledICProtectionBehaviour::set_current(&ic_protection_behaviour);
}

void XUnload::prepare() {
  if (!ClassUnloading) {
    return;
  }

  CodeCache::increment_unloading_cycle();
  DependencyContext::cleaning_start();
}

void XUnload::unlink() {
  if (!ClassUnloading) {
    return;
  }

  XStatTimer timer(XSubPhaseConcurrentClassesUnlink);
  SuspendibleThreadSetJoiner sts;
  bool unloading_occurred;

  {
    MutexLocker ml(ClassLoaderDataGraph_lock);
    unloading_occurred = SystemDictionary::do_unloading(XStatPhase::timer());
  }

  Klass::clean_weak_klass_links(unloading_occurred);
  XNMethod::unlink(_workers, unloading_occurred);
  DependencyContext::cleaning_end();
}

void XUnload::purge() {
  if (!ClassUnloading) {
    return;
  }

  XStatTimer timer(XSubPhaseConcurrentClassesPurge);

  {
    SuspendibleThreadSetJoiner sts;
    XNMethod::purge();
  }

  ClassLoaderDataGraph::purge(/*at_safepoint*/false);
  CodeCache::purge_exception_caches();
}

void XUnload::finish() {
  // Resize and verify metaspace
  MetaspaceGC::compute_new_size();
  DEBUG_ONLY(MetaspaceUtils::verify();)
}
