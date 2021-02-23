/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCREFERENCEPROCESSOREXECUTOR_HPP
#define SHARE_GC_G1_G1FULLGCREFERENCEPROCESSOREXECUTOR_HPP

#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1FullGCScope.hpp"
#include "gc/g1/g1FullGCTask.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/heapRegionManager.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/taskqueue.hpp"
#include "utilities/ticks.hpp"

class G1FullGCTracer;
class STWGCTimer;

class G1FullGCReferenceProcessingExecutor: public AbstractRefProcTaskExecutor {
  G1FullCollector*    _collector;
  ReferenceProcessor* _reference_processor;
  uint                _old_mt_degree;

public:
  G1FullGCReferenceProcessingExecutor(G1FullCollector* collector);
  ~G1FullGCReferenceProcessingExecutor();

  // Do reference processing.
  void execute(STWGCTimer* timer, G1FullGCTracer* tracer);

  // Executes the given task using concurrent marking worker threads.
  virtual void execute(ProcessTask& task, uint ergo_workers);

private:
  void run_task(AbstractGangTask* task);
  void run_task(AbstractGangTask* task, uint workers);

  class G1RefProcTaskProxy : public AbstractGangTask {
    typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
    ProcessTask&                  _proc_task;
    G1FullCollector*              _collector;

  public:
    G1RefProcTaskProxy(ProcessTask& proc_task,
                       G1FullCollector* scope);

    virtual void work(uint worker_id);
  };
};

#endif // SHARE_GC_G1_G1FULLGCREFERENCEPROCESSOREXECUTOR_HPP
