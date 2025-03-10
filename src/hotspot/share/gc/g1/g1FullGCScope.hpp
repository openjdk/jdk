/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCSCOPE_HPP
#define SHARE_GC_G1_G1FULLGCSCOPE_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapTransition.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "services/memoryService.hpp"

class GCMemoryManager;

class G1FullGCJFRTracerMark : public G1JFRTracerMark {
public:

  G1FullGCJFRTracerMark(STWGCTimer* timer, GCTracer* tracer);
  ~G1FullGCJFRTracerMark();
};

// Class used to group scoped objects used in the Full GC together.
class G1FullGCScope : public StackObj {
  ResourceMark            _rm;
  bool                    _do_maximal_compaction;
  G1CollectedHeap*        _g1h;
  SvcGCMarker             _svc_marker;
  STWGCTimer              _timer;
  G1FullGCTracer*         _tracer;
  IsSTWGCActiveMark       _active;
  G1FullGCJFRTracerMark   _tracer_mark;
  ClearedAllSoftRefs      _soft_refs;
  G1FullGCMonitoringScope _monitoring_scope;
  G1HeapPrinterMark       _heap_printer;
  size_t                  _region_compaction_threshold;

public:
  G1FullGCScope(G1MonitoringSupport* monitoring_support,
                bool clear_soft,
                bool do_maximal_compaction,
                G1FullGCTracer* tracer);

  bool should_clear_soft_refs();
  bool do_maximal_compaction() { return _do_maximal_compaction; }

  STWGCTimer* timer();
  G1FullGCTracer* tracer();
  size_t region_compaction_threshold() const;
};

#endif // SHARE_GC_G1_G1FULLGCSCOPE_HPP
