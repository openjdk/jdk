/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_LEAKPROFILER_EMITEVENTOPERATION_HPP
#define SHARE_VM_LEAKPROFILER_EMITEVENTOPERATION_HPP

#include "runtime/vmOperations.hpp"

class BFSClosure;
class EdgeStore;
class EdgeQueue;
class JfrThreadData;
class ObjectSample;
class ObjectSampler;

// Safepoint operation for emitting object sample events
class EmitEventOperation : public VM_Operation {
 private:
  jlong _cutoff_ticks;
  bool _emit_all;
  VMThread* _vm_thread;
  JfrThreadLocal* _vm_thread_local;
  ObjectSampler* _object_sampler;

  void write_event(const ObjectSample* sample, EdgeStore* edge_store);
  int write_events(EdgeStore* edge_store);

 public:
  EmitEventOperation(jlong cutoff_ticks, bool emit_all) :
    _cutoff_ticks(cutoff_ticks),
    _emit_all(emit_all),
    _vm_thread(NULL),
    _vm_thread_local(NULL),
    _object_sampler(NULL) {
  }

  VMOp_Type type() const {
    return VMOp_GC_HeapInspection;
  }

  Mode evaluation_mode() const {
    return _safepoint;
  }

  virtual void doit();
};

#endif // SHARE_VM_LEAKPROFILER_EMITEVENTOPERATION_HPP
