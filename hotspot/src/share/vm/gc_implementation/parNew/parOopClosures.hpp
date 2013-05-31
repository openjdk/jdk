/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARNEW_PAROOPCLOSURES_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARNEW_PAROOPCLOSURES_HPP

#include "memory/genOopClosures.hpp"
#include "memory/padded.hpp"

// Closures for ParNewGeneration

class ParScanThreadState;
class ParNewGeneration;
typedef Padded<OopTaskQueue> ObjToScanQueue;
typedef GenericTaskQueueSet<ObjToScanQueue, mtGC> ObjToScanQueueSet;
class ParallelTaskTerminator;

class ParScanClosure: public OopsInKlassOrGenClosure {
 protected:
  ParScanThreadState* _par_scan_state;
  ParNewGeneration*   _g;
  HeapWord*           _boundary;
  template <class T> void inline par_do_barrier(T* p);
  template <class T> void inline do_oop_work(T* p,
                                             bool gc_barrier,
                                             bool root_scan);
 public:
  ParScanClosure(ParNewGeneration* g, ParScanThreadState* par_scan_state);
};

class ParScanWithBarrierClosure: public ParScanClosure {
 public:
  ParScanWithBarrierClosure(ParNewGeneration* g,
                            ParScanThreadState* par_scan_state) :
    ParScanClosure(g, par_scan_state) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
};

class ParScanWithoutBarrierClosure: public ParScanClosure {
 public:
  ParScanWithoutBarrierClosure(ParNewGeneration* g,
                               ParScanThreadState* par_scan_state) :
    ParScanClosure(g, par_scan_state) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
};

class ParRootScanWithBarrierTwoGensClosure: public ParScanClosure {
 public:
  ParRootScanWithBarrierTwoGensClosure(ParNewGeneration* g,
                                       ParScanThreadState* par_scan_state) :
    ParScanClosure(g, par_scan_state) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

class ParRootScanWithoutBarrierClosure: public ParScanClosure {
 public:
  ParRootScanWithoutBarrierClosure(ParNewGeneration* g,
                                   ParScanThreadState* par_scan_state) :
    ParScanClosure(g, par_scan_state) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

class ParScanWeakRefClosure: public ScanWeakRefClosure {
 protected:
  ParScanThreadState* _par_scan_state;
  template <class T> inline void do_oop_work(T* p);
 public:
  ParScanWeakRefClosure(ParNewGeneration* g,
                        ParScanThreadState* par_scan_state);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
};

class ParEvacuateFollowersClosure: public VoidClosure {
 private:
  ParScanThreadState* _par_scan_state;
  ParScanThreadState* par_scan_state() { return _par_scan_state; }

  // We want to preserve the specific types here (rather than "OopClosure")
  // for later de-virtualization of do_oop calls.
  ParScanWithoutBarrierClosure* _to_space_closure;
  ParScanWithoutBarrierClosure* to_space_closure() {
    return _to_space_closure;
  }
  ParRootScanWithoutBarrierClosure* _to_space_root_closure;
  ParRootScanWithoutBarrierClosure* to_space_root_closure() {
    return _to_space_root_closure;
  }

  ParScanWithBarrierClosure* _old_gen_closure;
  ParScanWithBarrierClosure* old_gen_closure () {
    return _old_gen_closure;
  }
  ParRootScanWithBarrierTwoGensClosure* _old_gen_root_closure;
  ParRootScanWithBarrierTwoGensClosure* old_gen_root_closure () {
    return _old_gen_root_closure;
  }

  ParNewGeneration* _par_gen;
  ParNewGeneration* par_gen() { return _par_gen; }

  ObjToScanQueueSet*  _task_queues;
  ObjToScanQueueSet*  task_queues() { return _task_queues; }

  ParallelTaskTerminator* _terminator;
  ParallelTaskTerminator* terminator() { return _terminator; }
 public:
  ParEvacuateFollowersClosure(
    ParScanThreadState* par_scan_state_,
    ParScanWithoutBarrierClosure* to_space_closure_,
    ParScanWithBarrierClosure* old_gen_closure_,
    ParRootScanWithoutBarrierClosure* to_space_root_closure_,
    ParNewGeneration* par_gen_,
    ParRootScanWithBarrierTwoGensClosure* old_gen_root_closure_,
    ObjToScanQueueSet* task_queues_,
    ParallelTaskTerminator* terminator_);
  virtual void do_void();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARNEW_PAROOPCLOSURES_HPP
